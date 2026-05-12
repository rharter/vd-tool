package dev.vdtool.layoutlib

import android.view.View
import android.widget.ImageView
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.Snapshot
import app.cash.paparazzi.SnapshotHandler
import com.android.ide.common.rendering.api.SessionParams
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import org.junit.Rule
import org.junit.Test

/**
 * Renders a VectorDrawable XML file through LayoutLib (via Paparazzi) and writes a PNG.
 * Driven by Gradle properties:
 *   ./gradlew :xml-to-png:testDebugUnitTest \
 *       -Pinput=/abs/path/to/in.xml [-Poutput=/abs/path/to/out.png] [-Psize=512]
 *
 * If -Psize is omitted, the drawable's intrinsic size (from android:width/height) is used.
 */
class VdLayoutLibRenderTest {

  private val capturedFrames = mutableListOf<BufferedImage>()

  private val captureHandler = object : SnapshotHandler {
    override fun newFrameHandler(
      snapshot: Snapshot,
      frameCount: Int,
      fps: Int,
    ): SnapshotHandler.FrameHandler = object : SnapshotHandler.FrameHandler {
      override fun handle(image: BufferedImage) {
        capturedFrames += image
      }
      override fun close() {}
    }
    override fun close() {}
  }

  @get:Rule
  val paparazzi = Paparazzi(
    deviceConfig = DeviceConfig.PIXEL_5,
    renderingMode = SessionParams.RenderingMode.SHRINK,
    showSystemUi = false,
    snapshotHandler = captureHandler,
  )

  @Test
  fun render() {
    val outputPath = requireNotNull(System.getProperty("vd.output")?.takeIf { it.isNotEmpty() }) {
      "Missing -Poutput=<path-to-output.png>"
    }
    val sizeOverride = System.getProperty("vd.size")?.takeIf { it.isNotEmpty() }?.toInt()

    val drawable = paparazzi.context.getDrawable(R.drawable.render_input)
      ?: error("Failed to load R.drawable.render_input")

    val (w, h) = if (sizeOverride != null) {
      val intrinsicW = drawable.intrinsicWidth.coerceAtLeast(1)
      val intrinsicH = drawable.intrinsicHeight.coerceAtLeast(1)
      val ratio = intrinsicW.toDouble() / intrinsicH.toDouble()
      if (ratio >= 1.0) sizeOverride to (sizeOverride / ratio).toInt().coerceAtLeast(1)
      else (sizeOverride * ratio).toInt().coerceAtLeast(1) to sizeOverride
    } else {
      drawable.intrinsicWidth.coerceAtLeast(1) to drawable.intrinsicHeight.coerceAtLeast(1)
    }

    val view = ImageView(paparazzi.context).apply {
      setImageDrawable(drawable)
      layoutParams = android.view.ViewGroup.LayoutParams(w, h)
      measure(
        View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY),
      )
      layout(0, 0, w, h)
    }

    paparazzi.snapshot(view, name = "render")

    check(capturedFrames.isNotEmpty()) { "Paparazzi did not deliver any frames" }
    val image = capturedFrames.last()

    File(outputPath).parentFile?.mkdirs()
    ImageIO.write(image, "png", File(outputPath))
    println("Wrote ${image.width}x${image.height} PNG to $outputPath")
  }
}
