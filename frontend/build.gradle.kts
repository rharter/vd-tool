plugins {
  alias(libs.plugins.kotlin.jvm)
  application
}

application {
  mainClass.set("dev.vdtool.frontend.MainKt")
}

dependencies {
  implementation(libs.javalin)
  implementation(libs.slf4j.simple)
}
