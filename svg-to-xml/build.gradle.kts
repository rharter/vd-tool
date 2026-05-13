plugins {
  `java-library`
  application
  alias(libs.plugins.kotlin.jvm)
}

kotlin {
  jvmToolchain(21)
}

application {
  mainClass.set("com.android.ide.common.vectordrawable.VdCommandLineTool")
}

tasks.named<JavaExec>("run") {
  workingDir = file(System.getProperty("user.dir"))
}

dependencies {
  implementation(libs.com.android.tools.sdkCommon)
  implementation(libs.com.android.tools.common)
  implementation(libs.com.android.tools.annotations)
}
