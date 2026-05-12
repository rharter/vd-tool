# Multi-stage build. Builder produces :frontend:installDist + a jlink-stripped JRE;
# runtime is a tiny debian-slim base + native libs LayoutLib calls into + the custom JRE.
#
# Pinned to linux/amd64 because Paparazzi/LayoutLib's layoutlib-runtime ships an amd64-only
# layoutlib_jni.so. Cloud Run defaults to amd64; local Apple Silicon runs under QEMU.

FROM --platform=linux/amd64 eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /app
COPY . .
RUN ./gradlew --no-daemon :frontend:installDist

# Build a custom JRE. We pull in `java.se` (the Java SE aggregator) rather than
# enumerate individual modules — Paparazzi/LayoutLib reach into the JDK in
# enough reflective ways that targeting individual modules turned into
# whack-a-mole (java.instrument for ByteBuddy, java.compiler for Android
# resource validation, more lurking). java.se + the three JDK-specific bits
# below is still a meaningful win over the full JDK distribution because
# --strip-debug + no man pages + no headers strip the tooling layer.
RUN jlink \
        --add-modules java.se,jdk.crypto.ec,jdk.unsupported,jdk.zipfs \
        --strip-debug --no-man-pages --no-header-files \
        --compress=2 \
        --output /opt/jre

FROM --platform=linux/amd64 debian:bookworm-slim
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        libfreetype6 fontconfig libxrender1 \
        ca-certificates && \
    rm -rf /var/lib/apt/lists/*

COPY --from=builder /opt/jre /opt/jre
ENV PATH=/opt/jre/bin:$PATH \
    JAVA_HOME=/opt/jre

WORKDIR /app
COPY --from=builder /app/frontend/build/install/frontend ./
EXPOSE 8080
ENTRYPOINT ["./bin/frontend"]
