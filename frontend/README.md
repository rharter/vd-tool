# Cloud Run deployment

Deploys the `:frontend` Kotlin/Javalin app as a Cloud Run **Service**: HTTP
frontend with an upload UI; POST an SVG, get the PNG streamed back. The
container keeps a Gradle daemon warm between requests.

The container pre-warms Gradle and Paparazzi at image build time, so the first
render in a fresh container skips dependency download but still pays JVM +
Gradle startup (~30-60s). Subsequent renders within the same container reuse
the daemon (~3-5s).

## Service contract

- `GET /` — upload UI
- `POST /render` — multipart form: `svg` (file, required), `size` (int px, optional). Response is `image/png` with `Content-Disposition: attachment`.
- `GET /healthz` — liveness

## One-time setup

Replace `PROJECT_ID`, `REGION`, `REPO` with your values.

```sh
PROJECT_ID=your-gcp-project
REGION=us-central1
REPO=vd-tool
IMAGE="$REGION-docker.pkg.dev/$PROJECT_ID/$REPO/svg-to-png:latest"

gcloud services enable run.googleapis.com artifactregistry.googleapis.com
gcloud artifacts repositories create $REPO \
    --repository-format=docker --location=$REGION
```

## Build and push

From the repo root:

```sh
gcloud builds submit --tag "$IMAGE" .
```

(Or `docker build -t "$IMAGE" . && docker push "$IMAGE"` if building locally.)

## Deploy

```sh
gcloud run deploy svg-to-png \
    --image "$IMAGE" \
    --region $REGION \
    --cpu 2 \
    --memory 4Gi \
    --concurrency 1 \
    --timeout 300 \
    --allow-unauthenticated
```

`--concurrency 1` is required: the Gradle staging task writes to a fixed path
in the resources tree, so two simultaneous renders in one container would
collide. Cloud Run will spin up more containers under load.

`--allow-unauthenticated` makes the URL public. Drop the flag and front it with
IAP / a load balancer if you need auth.

The deploy prints a `https://svg-to-png-…run.app` URL — open it in a browser
and upload an SVG.

## Tail logs

```sh
gcloud run services logs read svg-to-png --region $REGION
```
