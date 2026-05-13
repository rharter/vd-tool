package dev.vdtool.frontend

import com.android.ide.common.vectordrawable.Svg2Vector
import dev.vdtool.direct.DirectRenderer
import io.javalin.Javalin
import io.javalin.config.SizeUnit
import io.javalin.http.Context
import io.javalin.http.HttpStatus
import jakarta.servlet.ServletException
import net.logstash.logback.argument.StructuredArguments.kv
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.imageio.ImageIO
import kotlin.io.path.deleteRecursively

private val log = LoggerFactory.getLogger("dev.vdtool.frontend")

private val indexHtml: String =
  checkNotNull(Thread.currentThread().contextClassLoader.getResource("index.html")) {
    "index.html missing from resources"
  }.readText()
private val sizeRegex = Regex("\\d{1,5}")

// LayoutLib's Bridge installs a per-thread Looper during prepare() and snapshot() must
// run on that same thread. Javalin's request handlers run on Jetty's worker pool, so we
// pin all renderer work — prepare() and every render() — to one dedicated single-thread
// executor. The executor also serializes access to the renderer's fixed workspace.
//
// The queue is bounded: once `RENDER_QUEUE_DEPTH` requests are waiting we reject new
// ones with 503 rather than letting slow clients pile up holding temp dirs and memory.
private val renderQueueDepth = envInt("RENDER_QUEUE_DEPTH", default = 8)
private val renderTimeoutMs = envInt("RENDER_TIMEOUT_MS", default = 30_000).toLong()
private val renderExecutor = ThreadPoolExecutor(
  1, 1, 0L, TimeUnit.MILLISECONDS,
  ArrayBlockingQueue(renderQueueDepth),
  { r -> Thread(r, "vd-render").apply { isDaemon = true } },
  ThreadPoolExecutor.AbortPolicy(),
)
private lateinit var renderer: DirectRenderer

private const val MAX_UPLOAD_BYTES = 5L * 1024 * 1024

fun main() {
  // Promote installDist's LAYOUTLIB_*_JAR env vars to system properties. DirectRenderer
  // reads -Dlayoutlib.runtime.jar / -Dlayoutlib.resources.jar in its constructor. Local
  // `./gradlew :frontend:run` sets these as -D directly; installDist sets env vars
  // because Gradle's start-script generator can't substitute $APP_HOME inside -D args.
  listOf(
    "LAYOUTLIB_RUNTIME_JAR" to "layoutlib.runtime.jar",
    "LAYOUTLIB_RESOURCES_JAR" to "layoutlib.resources.jar",
  ).forEach { (env, prop) ->
    if (System.getProperty(prop) == null) {
      System.getenv(env)?.let { System.setProperty(prop, it) }
    }
  }

  val port = envInt("PORT", default = 8080)

  val warmupStart = System.nanoTime()
  renderExecutor.submit {
    renderer = DirectRenderer()
    renderer.prepare()
  }.get()
  log.atInfo()
    .addKeyValue("warmup_ms", (System.nanoTime() - warmupStart) / 1_000_000)
    .log("DirectRenderer ready")

  Runtime.getRuntime().addShutdownHook(Thread {
    runCatching {
      renderExecutor.submit { renderer.close() }.get(5, TimeUnit.SECONDS)
    }
    renderExecutor.shutdown()
    renderExecutor.awaitTermination(5, TimeUnit.SECONDS)
  })

  Javalin.create { config ->
    // `maxRequestSize` covers raw request bodies; multipart uploads are gated separately
    // by Jetty's MultipartConfigElement. Set both so neither path can ingest > 5MB.
    config.http.maxRequestSize = MAX_UPLOAD_BYTES
    config.jetty.multipartConfig.maxFileSize(MAX_UPLOAD_BYTES, SizeUnit.BYTES)
    config.jetty.multipartConfig.maxTotalRequestSize(MAX_UPLOAD_BYTES, SizeUnit.BYTES)
    // Figma plugins run in a sandboxed iframe and send `Origin: null`. `anyHost()`
    // responds with `Access-Control-Allow-Origin: *` and handles OPTIONS preflight.
    config.bundledPlugins.enableCors { cors ->
      cors.addRule { it.anyHost() }
    }
    config.routes
      // Jetty rejects oversized multipart with IllegalStateException("max length exceeded ...")
      // wrapped in ServletException, thrown from ctx.uploadedFile() before our handler can
      // see it. Map to 413 so clients get an actionable status.
      .exception(ServletException::class.java) { e, ctx ->
        val isOversize = generateSequence<Throwable>(e) { it.cause }
          .any { it.message?.contains("max length exceeded") == true }
        if (isOversize) {
          log.atWarn().log("upload rejected: too large")
          ctx.status(HttpStatus.CONTENT_TOO_LARGE)
            .result("Upload too large (max ${MAX_UPLOAD_BYTES / 1024 / 1024}MB)")
        } else {
          log.atError().setCause(e).log("servlet exception")
          ctx.status(HttpStatus.BAD_REQUEST).result("Bad request")
        }
      }
      .get("/") { it.contentType("text/html").result(indexHtml) }
      .get("/healthz") { it.result("ok") }
      .post("/render", ::handleRender)
  }.start(port)

  log.atInfo()
    .addKeyValue("port", port)
    .log("listening")
}

@OptIn(kotlin.io.path.ExperimentalPathApi::class)
private fun handleRender(ctx: Context) {
  val started = System.nanoTime()

  val upload = ctx.uploadedFile("svg")
  if (upload == null) {
    ctx.status(HttpStatus.BAD_REQUEST).result("No SVG uploaded.")
    return
  }
  if (!upload.filename().lowercase().endsWith(".svg")) {
    ctx.status(HttpStatus.BAD_REQUEST).result("Expected a .svg file.")
    return
  }

  val size = ctx.formParam("size")?.trim().orEmpty()
  val sizeInt = if (size.isEmpty()) {
    null
  } else if (!sizeRegex.matches(size)) {
    ctx.status(HttpStatus.BAD_REQUEST).result("Invalid size.")
    return
  } else {
    size.toInt()
  }

  val tmpDir = Files.createTempDirectory("render-")
  try {
    val svgPath = tmpDir.resolve("input.svg")
    val xmlPath = tmpDir.resolve("input.xml")
    val bytesIn = upload.size()
    upload.content().use { input -> Files.copy(input, svgPath) }

    val xmlBytes = ByteArrayOutputStream().use { out ->
      val error = Svg2Vector.parseSvgToXml(svgPath, out)
      if (error.isNotEmpty()) {
        // Svg2Vector reports unsupported elements as errors but still emits valid XML;
        // log and proceed. Hard failures throw, caught below.
        log.atWarn().addKeyValue("warnings", error).log("svg2vector warnings")
      }
      out.toByteArray()
    }
    Files.write(xmlPath, xmlBytes)

    val image = try {
      renderExecutor.submit<java.awt.image.BufferedImage> {
        renderer.render(xmlPath.toFile(), sizeInt)
      }.get(renderTimeoutMs, TimeUnit.MILLISECONDS)
    } catch (e: RejectedExecutionException) {
      log.atWarn().log("render queue full")
      ctx.status(HttpStatus.SERVICE_UNAVAILABLE)
        .header("Retry-After", "1")
        .result("Server busy")
      return
    } catch (e: TimeoutException) {
      log.atError().log("render timeout")
      ctx.status(HttpStatus.GATEWAY_TIMEOUT).result("Render timed out")
      return
    }
    val pngBytes = ByteArrayOutputStream().use { out ->
      ImageIO.write(image, "png", out)
      out.toByteArray()
    }

    val downloadName = sanitizeAttachmentFilename(
      upload.filename().substringBeforeLast('.') + ".png",
    )
    ctx.header("Content-Disposition", "attachment; filename=\"$downloadName\"")
      .contentType("image/png")
      .result(pngBytes)

    log.atInfo()
      .addArgument(kv("bytes_in", bytesIn))
      .addArgument(kv("bytes_out", pngBytes.size))
      .addArgument(kv("width", image.width))
      .addArgument(kv("height", image.height))
      .addArgument(kv("duration_ms", (System.nanoTime() - started) / 1_000_000))
      .log("render complete")
  } catch (t: Throwable) {
    log.atError()
      .setCause(t)
      .log("render failed")
    ctx.status(HttpStatus.INTERNAL_SERVER_ERROR)
      .contentType("text/plain")
      .result("Render failed")
  } finally {
    tmpDir.deleteRecursively()
  }
}

private fun envInt(name: String, default: Int): Int =
  System.getenv(name)?.toIntOrNull() ?: default
