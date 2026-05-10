dependencyResolutionManagement {
  repositories {
    mavenCentral()
    google()
  }
}

if (file("tools/base/vector-drawable-tool").isDirectory) {
  include(":tools:base:vector-drawable-tool")
}
