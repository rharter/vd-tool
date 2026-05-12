# Paparazzi 2.0-alpha04 ships its layoutlib native library (.so) only for
# linux/amd64. Pin the platform so builds on Apple Silicon (or other arm64
# hosts) still produce a working amd64 image (run under QEMU locally; Cloud
# Run's default architecture is amd64).
FROM --platform=linux/amd64 eclipse-temurin:21-jdk-jammy

ENV ANDROID_HOME=/opt/android-sdk \
    PATH=/opt/android-sdk/cmdline-tools/latest/bin:/opt/android-sdk/platform-tools:$PATH \
    DEBIAN_FRONTEND=noninteractive

# Native libs LayoutLib needs at render time, plus tools for the Android SDK install.
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        curl unzip ca-certificates \
        libfreetype6 fontconfig libxrender1 && \
    rm -rf /var/lib/apt/lists/*

# Android SDK: cmdline-tools, platform 36, build-tools 36 (matches xml-to-png compileSdk).
# Update the cmdline-tools URL if Google publishes a newer one; the version in the path
# changes but the API stays stable.
RUN mkdir -p $ANDROID_HOME/cmdline-tools && \
    curl -fsSL https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip \
        -o /tmp/clt.zip && \
    unzip -q /tmp/clt.zip -d $ANDROID_HOME/cmdline-tools && \
    mv $ANDROID_HOME/cmdline-tools/cmdline-tools $ANDROID_HOME/cmdline-tools/latest && \
    rm /tmp/clt.zip && \
    yes | sdkmanager --licenses > /dev/null && \
    sdkmanager --install "platforms;android-36" "build-tools;36.0.0" > /dev/null

WORKDIR /app
COPY . .

# Pre-warm Gradle deps and AGP/Paparazzi transforms by doing one full render at build
# time. Bakes the Gradle cache and layoutlib-runtime artifact into an image layer so
# the first render in a fresh container doesn't pay the download cost.
RUN echo "sdk.dir=$ANDROID_HOME" > local.properties && \
    printf '%s\n' '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100"><rect width="100" height="100" fill="red"/></svg>' \
        > /tmp/warmup.svg && \
    ./gradlew --no-daemon render -Pinput=/tmp/warmup.svg -Poutput=/tmp/warmup.png --stacktrace && \
    rm /tmp/warmup.svg /tmp/warmup.png

# Build the HTTP frontend (Javalin app).
RUN ./gradlew --no-daemon :frontend:installDist > /dev/null

ENTRYPOINT ["/app/frontend/build/install/frontend/bin/frontend"]
