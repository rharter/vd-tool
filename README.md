# Vector Drawable Tools

Two Gradle modules:

- `:svg-to-xml` — Java CLI that converts a single `.svg` file to an Android
  `VectorDrawable` XML file. Forked from AOSP's
  [vector-drawable-tool](https://android.googlesource.com/platform/tools/base/+/refs/heads/mirror-goog-studio-main/vector-drawable-tool/),
  with the rendering path removed.
- `:xml-to-png` — Renders a `VectorDrawable` XML file to a PNG via
  Android Studio's LayoutLib (through Paparazzi), matching the rendering
  shown in the IDE's drawable preview pane.

## Usage

### SVG → PNG (single shot)

```shell
./gradlew render -Pinput=path/to/file.svg [-Poutput=path/to/out.png] [-Psize=1024]
```

Defaults: `-Poutput` lands `<input-without-ext>.png` next to the SVG;
`-Psize` falls back to the drawable's intrinsic size.

### Each stage on its own

SVG → VectorDrawable XML:

```shell
./gradlew :svg-to-xml:run path/to/file.svg [-out output/dir]
```

VectorDrawable XML → PNG:

```shell
./gradlew :xml-to-png:testDebugUnitTest \
  -PvdInput=path/to/vector.xml \
  -PvdOutput=path/to/out.png \
  [-PvdSize=1024]
```

PNG output matches the LayoutLib/Skia rendering used in Android Studio's editor
preview pane.
