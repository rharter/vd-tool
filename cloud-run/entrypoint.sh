#!/usr/bin/env bash
# Cloud Run Jobs entrypoint: SVG -> PNG via the existing :svg-to-xml + :xml-to-png pipeline.
#
# Required env:
#   INPUT_URI   gs://bucket/path/in.svg
#   OUTPUT_URI  gs://bucket/path/out.png
#
# Optional env:
#   SIZE        max dimension in px (passed as -Psize)
set -euo pipefail

: "${INPUT_URI:?INPUT_URI required (gs://bucket/in.svg)}"
: "${OUTPUT_URI:?OUTPUT_URI required (gs://bucket/out.png)}"

WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

echo "[entrypoint] downloading $INPUT_URI"
gcloud storage cp "$INPUT_URI" "$WORK/in.svg"

echo "[entrypoint] rendering"
SIZE_ARG=()
if [ -n "${SIZE:-}" ]; then
  SIZE_ARG+=("-Psize=$SIZE")
fi
./gradlew --no-daemon render \
    -Pinput="$WORK/in.svg" \
    -Poutput="$WORK/out.png" \
    "${SIZE_ARG[@]}"

echo "[entrypoint] uploading to $OUTPUT_URI"
gcloud storage cp "$WORK/out.png" "$OUTPUT_URI"

echo "[entrypoint] done"
