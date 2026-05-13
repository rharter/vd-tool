# `:frontend`

Kotlin/Javalin HTTP service that wraps the SVG → PNG pipeline behind an upload
UI. SVG → VectorDrawable XML happens in-process via `Svg2Vector`; XML → PNG
happens in-process via the `:xml-to-png-direct` renderer (LayoutLib `Bridge` +
`RenderSession`, no Gradle, no AGP, no test runner).

The container initializes LayoutLib's native `Bridge` once at startup (~1.5 s)
and reuses it across requests. Each render runs `RenderSession`
teardown/prepare to invalidate the per-resource `Drawable` cache, so two
sequential requests against different SVGs return their own outputs.

## Service contract

- `GET /` — upload UI (single-page HTML, served from `resources/index.html`).
- `POST /render` — multipart form: `svg` (file, required), `size` (int px, optional). Response is `image/png` with `Content-Disposition: attachment` and the input filename re-suffixed `.png`.
- `GET /healthz` — liveness probe target (returns `ok`). Used as Cloud Run's startup probe path.

## Threading model

LayoutLib's `Bridge` installs a per-thread `Looper` at `prepare()`; subsequent
`RenderSession` use must happen on that same thread. The server pins all
rendering work — initial `Bridge.init` and every `/render` — to a single
dedicated `vd-render` thread (a one-thread executor). Cloud Run is deployed
with `--concurrency=1` so requests don't queue serially within one container,
but the executor would serialize them safely if concurrency were raised.

## Building locally

From the repo root:

```sh
./gradlew :frontend:installDist
PORT=8080 ./frontend/build/install/frontend/bin/frontend
```

The launcher script exports `LAYOUTLIB_RUNTIME_JAR` and `LAYOUTLIB_RESOURCES_JAR`
pointing at the bundled layoutlib artifacts in `frontend/build/install/frontend/layoutlib/`.
`Main.kt` reads those env vars and promotes them to `-D` system properties before
initializing the renderer.

## Cloud Run deployment

Run from the repo root (the Dockerfile lives there). `gcloud run deploy --source .`
invokes Cloud Build under the hood, builds the image natively on amd64, auto-creates
an Artifact Registry repo on first deploy, and deploys in one step.

```sh
gcloud run deploy svg-to-png --source . \
    --region us-central1 \
    --memory 2Gi \
    --cpu 1 \
    --concurrency 1 \
    --timeout 60 \
    --cpu-boost \
    --startup-probe="httpGet.path=/healthz,httpGet.port=8080,initialDelaySeconds=0,periodSeconds=2,timeoutSeconds=2,failureThreshold=15" \
    --allow-unauthenticated
```

<details>
<summary>First-time project setup</summary>

```sh
gcloud services enable run.googleapis.com artifactregistry.googleapis.com cloudbuild.googleapis.com
```
</details>

Flag notes:

- **`--cpu-boost`** boosts CPU during the first 10 s of container startup; trims
  ~0.5 s off the cold-start `Bridge.init` cost.
- **`--startup-probe`** points at `/healthz`. The probe target only responds
  after `Bridge.init` is complete, so a failed init surfaces as a deploy failure
  rather than a 500 on the first user request.
- **`--allow-unauthenticated`** makes the URL public. Drop the flag and front
  the service with IAP / an HTTPS load balancer for auth.
- **`--concurrency=1`** isn't strictly required for correctness (the in-process
  `renderThread` serializes), but it bounds tail latency. Raise to 4–8 if traffic
  is bursty and lower instance count matters more than p99.

## Image architecture

`Dockerfile` is multi-stage:

1. **Builder** — `eclipse-temurin:21-jdk-jammy`, runs `./gradlew :frontend:installDist`,
   then `jlink`s a stripped JRE with just the modules our code needs into `/opt/jre`.
2. **Runtime** — `debian:bookworm-slim` + `libfreetype6 fontconfig libxrender1`
   (the native libs LayoutLib calls into), plus the jlink JRE and the
   `installDist` output. No JDK, no Android SDK, no Gradle at runtime.

Pinned `linux/amd64` because Paparazzi's `layoutlib-runtime` artifact ships an
amd64-only `layoutlib_jni.so`. Apple Silicon hosts run the image under QEMU
emulation locally; Cloud Run runs it natively.

## Tail logs

```sh
gcloud run services logs read svg-to-png --region us-central1
```
