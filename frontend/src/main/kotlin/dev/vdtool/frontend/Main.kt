package dev.vdtool.frontend

import io.javalin.Javalin
import io.javalin.http.Context
import io.javalin.http.HttpStatus
import java.io.File
import java.nio.file.Files
import kotlin.io.path.deleteRecursively

private val repoRoot: File = File(System.getenv("REPO_ROOT") ?: "/app")
private val indexHtml: String =
  checkNotNull(Thread.currentThread().contextClassLoader.getResource("index.html")) {
    "index.html missing from resources"
  }.readText()
private val sizeRegex = Regex("\\d{1,5}")

fun main() {
  val port = (System.getenv("PORT") ?: "8080").toInt()
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
  if (size.isNotEmpty() && !sizeRegex.matches(size)) {
    ctx.status(HttpStatus.BAD_REQUEST).result("Invalid size.")
    return
  }

  val tmpDir = Files.createTempDirectory("render-")
  try {
    val svgPath = tmpDir.resolve("input.svg").toFile()
    val pngPath = tmpDir.resolve("output.png").toFile()
    upload.content().use { input -> svgPath.outputStream().use { input.copyTo(it) } }

    val cmd = buildList {
      add("./gradlew")
      // Workaround for AGP 9.x: when render_input.xml is rewritten between
      // builds, the resource-merge incremental state errors with "no data file
      // for changedFile". A per-request clean of the xml-to-png build dir
      // resets that state without invalidating the Gradle dep cache.
      add(":xml-to-png:clean")
      add("render")
      add("-Pinput=${svgPath.absolutePath}")
      add("-Poutput=${pngPath.absolutePath}")
      if (size.isNotEmpty()) add("-Psize=$size")
    }

    val process = ProcessBuilder(cmd)
      .directory(repoRoot)
      .redirectErrorStream(true)
      .start()
    val output = process.inputStream.bufferedReader().readText()
    val exit = process.waitFor()
    if (exit != 0) {
      ctx.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .contentType("text/plain")
        .result("Render failed.\n\n${output.takeLast(2000)}")
      return
    }

    val downloadName = upload.filename().substringBeforeLast('.') + ".png"
    ctx.header("Content-Disposition", "attachment; filename=\"$downloadName\"")
      .contentType("image/png")
      .result(pngPath.readBytes())
  } finally {
    tmpDir.deleteRecursively()
  }
}
