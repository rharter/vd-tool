# Cloud Run Jobs deployment

Renders one SVG to PNG per job execution. Inputs and outputs are GCS URIs.

## Container contract

Required env:
- `INPUT_URI`  — `gs://bucket/path/in.svg`
- `OUTPUT_URI` — `gs://bucket/path/out.png`

Optional env:
- `SIZE` — max dimension in pixels (defaults to the drawable's intrinsic size)

The container pre-warms Gradle and Paparazzi at image build time, so each job
execution skips the dependency download but still pays JVM + Gradle startup
(~30-60s per render — see top-level discussion for why a service path is
faster per-render but requires a refactor).

## One-time setup

Replace `PROJECT_ID`, `REGION`, `REPO`, `BUCKET` with your values.

```sh
PROJECT_ID=your-gcp-project
REGION=us-central1
REPO=vd-tool
BUCKET=your-render-bucket
IMAGE="$REGION-docker.pkg.dev/$PROJECT_ID/$REPO/svg-to-png:latest"

# Enable services and create the Artifact Registry repo + GCS bucket.
gcloud services enable run.googleapis.com artifactregistry.googleapis.com storage.googleapis.com
gcloud artifacts repositories create $REPO \
    --repository-format=docker --location=$REGION
gcloud storage buckets create gs://$BUCKET --location=$REGION
```

## Build and push

From the repo root:

```sh
gcloud builds submit --tag "$IMAGE" .
```

(Or `docker build -t "$IMAGE" . && docker push "$IMAGE"` if building locally.)

## Create the Job

```sh
gcloud run jobs create svg-to-png \
    --image "$IMAGE" \
    --region $REGION \
    --cpu 2 \
    --memory 4Gi \
    --max-retries 1 \
    --task-timeout 5m
```

## Execute a render

```sh
gcloud run jobs execute svg-to-png \
    --region $REGION \
    --update-env-vars "INPUT_URI=gs://$BUCKET/in.svg,OUTPUT_URI=gs://$BUCKET/out.png,SIZE=1024" \
    --wait
```

The job's service account needs `roles/storage.objectUser` on the bucket. By
default Cloud Run Jobs runs as the Compute Engine default service account; for
production, create a dedicated service account and grant it only that role.

## Tail logs

```sh
gcloud beta run jobs executions logs read EXECUTION_NAME --region $REGION
```
