# Multi-stage build. Builder produces :frontend:installDist (a self-contained launcher
# + jars); runtime is a small JRE + that dist + the native libs LayoutLib calls into
# (freetype, fontconfig, libxrender).
#
# Pinned to linux/amd64 because Paparazzi's layoutlib-runtime ships an amd64-only
# layoutlib_jni.so. Cloud Run defaults to amd64; local Apple Silicon runs under QEMU.

FROM --platform=linux/amd64 eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /app
COPY . .
RUN ./gradlew --no-daemon :frontend:installDist

FROM --platform=linux/amd64 eclipse-temurin:21-jre-jammy
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        libfreetype6 fontconfig libxrender1 && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=builder /app/frontend/build/install/frontend ./
EXPOSE 8080
ENTRYPOINT ["./bin/frontend"]
