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
      val vdInput = project.findProperty("vdInput") as String?
      val vdOutput = project.findProperty("vdOutput") as String?
      val vdSize = project.findProperty("vdSize") as String?
      it.systemProperty("vd.input", vdInput ?: "")
      it.systemProperty("vd.output", vdOutput ?: "")
      it.systemProperty("vd.size", vdSize ?: "")
      // Paparazzi 2.0-alpha04's custom HTML reporter (ClassPageRenderer) calls a
      // Gradle TestResultsProvider.hasOutput signature that changed in Gradle 9.x.
      // Skip the HTML report so the build status reflects the actual test outcome.
      it.reports.html.required.set(false)
      // Make Gradle's up-to-date check aware of the real input/output files behind
      // the property values, so a re-render fires when the input XML's contents
      // change at the same path or when the output PNG is missing.
      if (!vdInput.isNullOrEmpty()) it.inputs.file(vdInput)
      if (!vdOutput.isNullOrEmpty()) it.outputs.file(vdOutput)
      if (!vdSize.isNullOrEmpty()) it.inputs.property("vd.size", vdSize)
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