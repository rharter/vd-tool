package dev.vdtool.direct

import android.view.View
import android.widget.ImageView
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Environment
import app.cash.paparazzi.PaparazziSdk
import com.android.ide.common.rendering.api.SessionParams.RenderingMode
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile
import javax.imageio.ImageIO

/**
 * Direct-LayoutLib (via the Paparazzi runtime JAR only) VectorDrawable → PNG renderer.
 *
 * No AGP, no Paparazzi Gradle plugin, no JUnit. We depend on:
 *   - app.cash.paparazzi:paparazzi  (the published JAR — `PaparazziSdk` is public)
 *   - com.android.tools.layoutlib:layoutlib-runtime:<classifier>  (zipped native libs + fonts + ICU)
 *   - com.android.tools.layoutlib:layoutlib-resources              (zipped framework `res/`)
 *
 * The "runtime" and "resources" Maven artifacts are themselves zip archives that the Paparazzi
 * Gradle plugin normally unpacks via Gradle's `UnzipTransform`. We do that ourselves on first run.
 *
 * Usage:  vd-direct <input.xml> <output.png> [size]
 *
 * Size: if omitted, the drawable's intrinsic `android:width / android:height` dimensions are used,
 * matching the existing Paparazzi-driven pipeline.
 */
fun main(args: Array<String>) {
  if (args.firstOrNull() == "--bench") {
    runBenchmark(args.drop(1))
    return
  }
  require(args.size in 2..3) {
    "Usage: vd-direct <input.xml> <output.png> [size]\n" +
      "       vd-direct --bench <input.xml> [iterations]"
  }
  val inputXml = File(args[0]).absoluteFile
  val outputPng = File(args[1]).absoluteFile
  val sizeOverride = args.getOrNull(2)?.toInt()

  require(inputXml.isFile) { "Input not found: $inputXml" }
  outputPng.parentFile?.mkdirs()

  val renderer = DirectRenderer()
  val warmupStart = System.nanoTime()
  renderer.prepare()
  val warmupMillis = (System.nanoTime() - warmupStart) / 1_000_000
  println("Bridge init took ${warmupMillis}ms")

  val renderStart = System.nanoTime()
  val image = renderer.render(inputXml, sizeOverride)
  val renderMillis = (System.nanoTime() - renderStart) / 1_000_000
  println("Render took ${renderMillis}ms (${image.width}x${image.height})")

  ImageIO.write(image, "png", outputPng)
  println("Wrote $outputPng")

  // Second render to measure warm-cycle cost (informational; we throw it away).
  val warm2Start = System.nanoTime()
  renderer.render(inputXml, sizeOverride)
  println("Warm second render took ${(System.nanoTime() - warm2Start) / 1_000_000}ms")

  renderer.close()
}

private fun runBenchmark(args: List<String>) {
  require(args.isNotEmpty()) { "Usage: vd-direct --bench <input.xml> [iterations]" }
  val inputXml = File(args[0]).absoluteFile
  val iterations = args.getOrNull(1)?.toInt() ?: 50
  require(inputXml.isFile) { "Input not found: $inputXml" }

  val renderer = DirectRenderer()
  val initStart = System.nanoTime()
  renderer.prepare()
  println("Bridge init: ${(System.nanoTime() - initStart) / 1_000_000}ms")

  // Single warm-up render to exclude JIT warmup from the steady-state numbers.
  renderer.render(inputXml, null)

  val timesMs = LongArray(iterations)
  for (i in 0 until iterations) {
    val t = System.nanoTime()
    renderer.render(inputXml, null)
    timesMs[i] = (System.nanoTime() - t) / 1_000_000
  }
  timesMs.sort()
  val total = timesMs.sum()
  val mean = total / iterations.toDouble()
  println(
    "Over $iterations warm renders: " +
      "mean ${"%.1f".format(mean)}ms, " +
      "p50 ${timesMs[iterations / 2]}ms, " +
      "p95 ${timesMs[(iterations * 95) / 100]}ms, " +
      "min ${timesMs.first()}ms, max ${timesMs.last()}ms",
  )

  renderer.close()
}

/**
 * Wraps PaparazziSdk for one-shot drawable rendering. Holds the staged-resource workspace and the
 * SDK instance across calls so warm renders skip Bridge init.
 */
class DirectRenderer : AutoCloseable {
  private val workspace: File = Files.createTempDirectory("vd-direct-").toFile()
  private val resDir: File = File(workspace, "res").apply { mkdirs() }
  private val drawableDir: File = File(resDir, "drawable").apply { mkdirs() }
  private val stagedXml: File = File(drawableDir, "render_input.xml")
  private val runtimeDir: File
  private val resourcesDir: File
  private lateinit var sdk: PaparazziSdk

  init {
    val runtimeJar = systemProperty("layoutlib.runtime.jar")
    val resourcesJar = systemProperty("layoutlib.resources.jar")
    val cacheRoot = File(System.getProperty("user.home"), ".cache/vd-direct")
    runtimeDir = extractZipCached(File(runtimeJar), File(cacheRoot, "layoutlib-runtime"))
    resourcesDir = extractZipCached(File(resourcesJar), File(cacheRoot, "layoutlib-resources"))

    // Paparazzi reads these as system properties from inside Renderer.kt.
    System.setProperty("paparazzi.layoutlib.runtime.root", runtimeDir.absolutePath)
    System.setProperty("paparazzi.layoutlib.resources.root", resourcesDir.absolutePath)
  }

  fun prepare() {
    val packageName = "dev.vdtool.direct"
    val environment = Environment(
      appTestDir = workspace.absolutePath,
      packageName = packageName,
      compileSdkVersion = 36,
      resourcePackageNames = listOf(packageName),
      localResourceDirs = listOf(resDir.absolutePath),
      moduleResourceDirs = emptyList(),
      libraryResourceDirs = emptyList(),
      allModuleAssetDirs = emptyList(),
      libraryAssetDirs = emptyList(),
    )

    // PaparazziCallback.initResources reads from <packageName>.R — but it needs at least one
    // resource (render_input) registered so we can find it. Stage a placeholder XML so the
    // initial resource scan (done at SDK prepare() time) sees `drawable/render_input.xml`.
    if (!stagedXml.exists()) {
      stagedXml.writeText(PLACEHOLDER_VECTOR)
    }

    sdk = PaparazziSdk(
      environment = environment,
      deviceConfig = DeviceConfig.PIXEL_5,
      // SHRINK matches the existing Paparazzi-based pipeline (`VdLayoutLibRenderTest`):
      // the root FrameLayout shrinks to fit the ImageView, which holds the drawable at its
      // intrinsic size.
      renderingMode = RenderingMode.SHRINK,
      appCompatEnabled = false,
      showSystemUi = false,
      onNewFrame = { capturedFrame = it },
    )
    sdk.setup()
    sdk.prepare()
    prepared = true
  }

  private var capturedFrame: BufferedImage? = null
  private var prepared: Boolean = false

  fun render(inputXml: File, sizeOverride: Int?): BufferedImage {
    // Replace the staged file's contents with the requested XML. This is the same "stage it as
    // a resource" hack the existing pipeline uses, just done at runtime in a temp dir rather
    // than at Gradle config time in `src/main/res`.
    inputXml.copyTo(stagedXml, overwrite = true)

    // Tear down and re-prepare the SDK between renders. PaparazziSdk.prepare() builds a fresh
    // RenderSession + Resources/Context (so the per-resource Drawable cache is invalidated),
    // but the expensive native Bridge.init is skipped via PaparazziSdk's internal
    // `isInitialized` guard. Without this, the second render returns the first request's
    // cached drawable.
    if (prepared) {
      try {
        sdk.teardown()
      } catch (_: Throwable) {
        // PaparazziLogger.assertNoErrors() can throw on benign info-level entries; safe to ignore.
      }
      sdk.prepare()
    }

    val context = sdk.context
    val drawable = context.getDrawable(R.drawable.render_input)
      ?: error("Failed to load R.drawable.render_input from staged file ${stagedXml.absolutePath}")

    val (w, h) = if (sizeOverride != null) {
      val intrinsicW = drawable.intrinsicWidth.coerceAtLeast(1)
      val intrinsicH = drawable.intrinsicHeight.coerceAtLeast(1)
      val ratio = intrinsicW.toDouble() / intrinsicH.toDouble()
      if (ratio >= 1.0) sizeOverride to (sizeOverride / ratio).toInt().coerceAtLeast(1)
      else (sizeOverride * ratio).toInt().coerceAtLeast(1) to sizeOverride
    } else {
      drawable.intrinsicWidth.coerceAtLeast(1) to drawable.intrinsicHeight.coerceAtLeast(1)
    }

    val view = ImageView(context).apply {
      setImageDrawable(drawable)
      layoutParams = android.view.ViewGroup.LayoutParams(w, h)
      measure(
        View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY),
      )
      layout(0, 0, w, h)
    }

    capturedFrame = null
    sdk.snapshot(view)
    return checkNotNull(capturedFrame) { "Paparazzi onNewFrame was not invoked" }
  }

  override fun close() {
    if (::sdk.isInitialized) {
      try {
        sdk.teardown()
      } catch (_: Throwable) {
        // Suppress: teardown asserts no logger errors; for a one-shot CLI we just want to exit.
      }
    }
    workspace.deleteRecursively()
  }

  private companion object {
    private val PLACEHOLDER_VECTOR = """
      <vector xmlns:android="http://schemas.android.com/apk/res/android"
          android:width="24dp" android:height="24dp"
          android:viewportWidth="24" android:viewportHeight="24">
        <path android:pathData="M0,0L24,0L24,24L0,24Z" android:fillColor="#ffffff"/>
      </vector>
    """.trimIndent()
  }

  private fun systemProperty(name: String): String =
    System.getProperty(name) ?: error("Missing -D$name; the Gradle :run task should set this.")

  /**
   * Extract a zip-jar to [targetDir] (idempotent — if the marker file already exists, skip).
   * Paparazzi's `Renderer.prepare()` expects to find:
   *  - `<runtime>/build.prop`
   *  - `<runtime>/data/fonts/`, `data/icu/icudt76l.dat`, `data/keyboards/Generic.kcm`,
   *    `data/<os>/lib64/`, `data/hyphen-data/`
   *  - `<resources>/res/values/attrs.xml` etc.
   */
  private fun extractZipCached(jar: File, targetDir: File): File {
    val marker = File(targetDir, ".extracted-${jar.name}")
    if (marker.exists()) return targetDir
    targetDir.deleteRecursively()
    targetDir.mkdirs()
    ZipFile(jar).use { zip ->
      val entries = zip.entries()
      while (entries.hasMoreElements()) {
        val entry = entries.nextElement()
        if (entry.isDirectory) continue
        val out = File(targetDir, entry.name)
        out.parentFile?.mkdirs()
        zip.getInputStream(entry).use { input ->
          out.outputStream().use { output -> input.copyTo(output) }
        }
      }
    }
    marker.writeText("ok")
    return targetDir
  }
}
