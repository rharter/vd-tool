# Vector Drawable Tools

Three Gradle modules:

- `:svg-to-xml` — Java CLI that converts a single `.svg` file to an Android
  `VectorDrawable` XML file. Forked from AOSP's
  [vector-drawable-tool](https://android.googlesource.com/platform/tools/base/+/refs/heads/mirror-goog-studio-main/vector-drawable-tool/),
  with the rendering path removed.
- `:xml-to-png` — Renders a `VectorDrawable` XML file to a PNG via
  Android Studio's LayoutLib (through Paparazzi), matching the rendering
  shown in the IDE's drawable preview pane.
- `:frontend` — Kotlin/Javalin HTTP server that wraps the pipeline behind an
  upload UI. Designed for Cloud Run; see [`cloud-run/`](frontend/README.md).

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
  -Pinput=path/to/vector.xml \
  [-Poutput=path/to/out.png] \
  [-Psize=1024]
```

PNG output matches the LayoutLib/Skia rendering used in Android Studio's editor
preview pane.

## Local web frontend

The `:frontend` module is the same Cloud Run service, runnable locally. Use it
to iterate on the upload UI or end-to-end-test the SVG → PNG path through HTTP.

Build and start the server:

```shell
./gradlew :frontend:installDist
REPO_ROOT=$(pwd) PORT=8080 ./frontend/build/install/frontend/bin/frontend
```

`REPO_ROOT` is the directory the server `cd`s into to run `./gradlew render` —
the repo root. `PORT` defaults to `8080`.

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
- Gradle render failure → `500` with the tail of the Gradle output.

After code changes to either the Kotlin server or `index.html`, rerun
`./gradlew :frontend:installDist` and restart the binary. Concurrency=1: the
Gradle staging task writes to a fixed path, so the server is not safe for
parallel renders within one process — match the Cloud Run setting locally by
only firing one `/render` at a time.
