package dev.vdtool.frontend

import com.android.ide.common.vectordrawable.Svg2Vector
import dev.vdtool.direct.DirectRenderer
import io.javalin.Javalin
import io.javalin.http.Context
import io.javalin.http.HttpStatus
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.concurrent.Executors
import javax.imageio.ImageIO
import kotlin.io.path.deleteRecursively

private val indexHtml: String =
  checkNotNull(Thread.currentThread().contextClassLoader.getResource("index.html")) {
    "index.html missing from resources"
  }.readText()
private val sizeRegex = Regex("\\d{1,5}")

// LayoutLib's Bridge installs a per-thread Looper during prepare() and snapshot() must
// run on that same thread. Javalin's request handlers run on Jetty's worker pool, so we
// pin all renderer work — prepare() and every render() — to one dedicated single-thread
// executor. That executor also serializes access to the renderer's fixed workspace.
private val renderThread = Executors.newSingleThreadExecutor { r ->
  Thread(r, "vd-render").apply { isDaemon = true }
}
private lateinit var renderer: DirectRenderer

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

  val port = (System.getenv("PORT") ?: "8080").toInt()

  val warmupStart = System.nanoTime()
  renderThread.submit {
    renderer = DirectRenderer()
    renderer.prepare()
  }.get()
  println("DirectRenderer ready in ${(System.nanoTime() - warmupStart) / 1_000_000}ms")

  Runtime.getRuntime().addShutdownHook(Thread {
    runCatching { renderThread.submit { renderer.close() }.get() }
    renderThread.shutdown()
  })

  Javalin.create()
    .get("/") { it.contentType("text/html").result(indexHtml) }
    .get("/healthz") { it.result("ok") }
    .post("/render", ::handleRender)
    .start(port)
}

@OptIn(kotlin.io.path.ExperimentalPathApi::class)
private fun handleRender(ctx: Context) {
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
    upload.content().use { input -> Files.copy(input, svgPath) }

    val xmlBytes = ByteArrayOutputStream().use { out ->
      val error = Svg2Vector.parseSvgToXml(svgPath, out)
      if (error.isNotEmpty()) {
        // Svg2Vector reports unsupported elements as errors but still emits valid XML;
        // log to stderr and proceed. Hard failures throw, caught below.
        System.err.println("svg2vector warnings: $error")
      }
      out.toByteArray()
    }
    Files.write(xmlPath, xmlBytes)

    val image = renderThread.submit<java.awt.image.BufferedImage> {
      renderer.render(xmlPath.toFile(), sizeInt)
    }.get()
    val pngBytes = ByteArrayOutputStream().use { out ->
      ImageIO.write(image, "png", out)
      out.toByteArray()
    }

    val downloadName = upload.filename().substringBeforeLast('.') + ".png"
    ctx.header("Content-Disposition", "attachment; filename=\"$downloadName\"")
      .contentType("image/png")
      .result(pngBytes)
  } catch (t: Throwable) {
    t.printStackTrace()
    ctx.status(HttpStatus.INTERNAL_SERVER_ERROR)
      .contentType("text/plain")
      .result("Render failed: ${t.message ?: t.javaClass.simpleName}")
  } finally {
    tmpDir.deleteRecursively()
  }
}
