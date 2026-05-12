plugins {
  `java-library`
  application
  alias(libs.plugins.kotlin.jvm)
}

application {
  mainClass.set("com.android.ide.common.vectordrawable.VdCommandLineTool")
}

tasks.named<JavaExec>("run") {
  workingDir = file(System.getProperty("user.dir"))
}

base.archivesName.set("svg-to-xml")
tasks.distZip { archiveBaseName.set(base.archivesName) }
tasks.startScripts { applicationName = base.archivesName.get() }

dependencies {
  implementation(libs.com.android.tools.sdkCommon)
  implementation(libs.com.android.tools.common)
  implementation(libs.com.android.tools.annotations)
}
