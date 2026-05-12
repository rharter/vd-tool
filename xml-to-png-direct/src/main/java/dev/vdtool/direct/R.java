package dev.vdtool.direct;

/**
 * Fake R class mirroring what AGP would have generated for an Android module with one drawable.
 * Paparazzi's {@code PaparazziCallback.initResources} loads {@code <packageName>.R} reflectively
 * and walks its inner classes/fields; the values are arbitrary opaque ints (the callback
 * builds its own map from them to {@code ResourceReference}s).
 *
 * We expose a single drawable, {@code render_input}, which the renderer stages into a temp
 * {@code res/drawable/render_input.xml} before each render.
 */
public final class R {
  private R() {}

  public static final class drawable {
    private drawable() {}
    /** Opaque resource id. Value doesn't matter — Paparazzi maps int↔ResourceReference itself. */
    public static final int render_input = 0x7f010001;
  }
}
