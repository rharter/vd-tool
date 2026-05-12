# Vector Drawable Tools

Three Gradle modules:

- `:svg-to-xml` — Java CLI that converts a single `.svg` file to an Android
  `VectorDrawable` XML file. Forked from AOSP's
  [vector-drawable-tool](https://android.googlesource.com/platform/tools/base/+/refs/heads/mirror-goog-studio-main/vector-drawable-tool/),
  with the rendering path removed.
- `:xml-to-png-direct` — Renders a `VectorDrawable` XML file to a PNG by
  driving Android Studio's LayoutLib directly (the same `Bridge` /
  `RenderSession` that backs the IDE's drawable preview). Plain Kotlin/JVM
  module — no Android Gradle plugin, no test runner, no Gradle subprocess at
  render time. Output is byte-identical to the Paparazzi-driven path.
- `:frontend` — Kotlin/Javalin HTTP server that wraps the pipeline behind an
  upload UI. Initializes the LayoutLib `Bridge` once at startup and renders
  in-process per request. Designed for Cloud Run; see
  [`cloud-run/`](cloud-run/README.md).

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
./gradlew :svg-to-xml:run --args="path/to/file.svg -out output/dir"
```

VectorDrawable XML → PNG:

```shell
./gradlew render -Pinput=path/to/vector.xml [-Poutput=path/to/out.png] [-Psize=1024]
```

PNG output matches the LayoutLib/Skia rendering used in Android Studio's editor
preview pane.

## Local web frontend

The `:frontend` module is the same Cloud Run service, runnable locally. Use it
to iterate on the upload UI or end-to-end-test the SVG → PNG path through HTTP.

Build and start the server:

```shell
./gradlew :frontend:installDist
PORT=8080 ./frontend/build/install/frontend/bin/frontend
```

`PORT` defaults to `8080`. The launcher script sets the `LAYOUTLIB_*_JAR`
environment variables to the bundled layoutlib artifacts.

Open <http://localhost:8080> in a browser and upload an SVG, or hit the
endpoints directly:

```shell
# Returns the PNG, with the right filename via Content-Disposition.
curl -fOJ -F svg=@examples/widget-1.svg http://localhost:8080/render

# Optional max-dimension override.
curl -fOJ -F svg=@examples/widget-1.svg -F size=512 http://localhost:8080/render

# Liveness.
curl http://localhost:8080/healthz   # → ok
```

Error cases return a `4xx` with a `text/plain` reason:

- No `svg` field → `400 No SVG uploaded.`
- Non-`.svg` filename → `400 Expected a .svg file.`
- Non-numeric `size` → `400 Invalid size.`
- Renderer exception → `500` with the exception message.

After code changes to either the Kotlin server or `index.html`, rerun
`./gradlew :frontend:installDist` and restart the binary. Renders are
serialized on a dedicated thread (LayoutLib's Bridge installs a per-thread
Looper) — Cloud Run's `--concurrency 1` matches this; for local testing,
fire one `/render` at a time.
