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

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  testOptions {
    unitTests.all {
      it.systemProperty("vd.input", project.findProperty("vdInput") ?: "")
      it.systemProperty("vd.output", project.findProperty("vdOutput") ?: "")
      it.systemProperty("vd.size", project.findProperty("vdSize") ?: "")
      // Paparazzi 2.0-alpha04's custom HTML reporter (ClassPageRenderer) calls a
      // Gradle TestResultsProvider.hasOutput signature that changed in Gradle 9.x.
      // Skip the HTML report so the build status reflects the actual test outcome.
      it.reports.html.required.set(false)
    }
  }
}

dependencies {
  testImplementation(libs.junit)
}

// Stage the user's input XML into res/drawable/ as render_input.xml so it can be loaded
// via R.drawable.render_input. This runs at configuration time, before AGP's resource
// processing tasks read the source set.
val vdInputProp = project.findProperty("vdInput") as String?
if (!vdInputProp.isNullOrEmpty()) {
  val src = File(vdInputProp)
  val dest = layout.projectDirectory.file("src/main/res/drawable/render_input.xml").asFile
  if (src.exists()) {
    dest.parentFile.mkdirs()
    src.copyTo(dest, overwrite = true)
  }
}