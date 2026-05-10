pluginManagement {
  repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
  }
}

dependencyResolutionManagement {
  repositories {
    google()
    mavenCentral()
  }
}

if (file("tools/base/vector-drawable-tool").isDirectory) {
  include(":tools:base:vector-drawable-tool")
}

include(":prototype:layoutlib-renderer")
