plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.paparazzi)
}

android {
  namespace = "dev.vdtool.layoutlib"
  compileSdk = 36

  defaultConfig {
    minSdk = 21
  }

  testOptions {
    unitTests.all {
      // Paparazzi 2.0-alpha04's custom HTML reporter (ClassPageRenderer) calls a
      // Gradle TestResultsProvider.hasOutput signature that changed in Gradle 9.x.
      // Skip the HTML report so the build status reflects the actual test outcome.
      it.reports.html.required.set(false)

      it.testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = true
      }
    }
  }
}

dependencies {
  testImplementation(libs.junit)
}
