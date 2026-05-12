FROM eclipse-temurin:21-jdk-jammy

ENV ANDROID_HOME=/opt/android-sdk \
    PATH=/opt/android-sdk/cmdline-tools/latest/bin:/opt/android-sdk/platform-tools:$PATH \
    DEBIAN_FRONTEND=noninteractive

# Native libs LayoutLib needs at render time, plus tools for SDK install / gcloud.
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        curl unzip ca-certificates gnupg \
        libfreetype6 fontconfig libxrender1 && \
    curl -fsSL https://packages.cloud.google.com/apt/doc/apt-key.gpg \
        | gpg --dearmor -o /usr/share/keyrings/cloud.google.gpg && \
    echo "deb [signed-by=/usr/share/keyrings/cloud.google.gpg] https://packages.cloud.google.com/apt cloud-sdk main" \
        > /etc/apt/sources.list.d/google-cloud-sdk.list && \
    apt-get update && \
    apt-get install -y --no-install-recommends google-cloud-cli && \
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
# the first job execution doesn't pay the download cost.
RUN echo "sdk.dir=$ANDROID_HOME" > local.properties && \
    printf '%s\n' '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100"><rect width="100" height="100" fill="red"/></svg>' \
        > /tmp/warmup.svg && \
    ./gradlew --no-daemon render -Pinput=/tmp/warmup.svg -Poutput=/tmp/warmup.png > /dev/null && \
    rm /tmp/warmup.svg /tmp/warmup.png

COPY cloud-run/entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod +x /usr/local/bin/entrypoint.sh

ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
