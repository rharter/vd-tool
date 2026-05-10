import de.undercouch.gradle.tasks.download.Download

plugins {
  alias(libs.plugins.download)
}

val downloadDest = layout.buildDirectory.file("downloads/vector-drawable-tool.tar.gz")

val downloadTask = tasks.register<Download>("downloadSource") {
  src("https://android.googlesource.com/platform/tools/base/+archive/refs/heads/mirror-goog-studio-main/vector-drawable-tool.tar.gz")
  dest(downloadDest)
  overwrite(false)
}

val rowWidth = 500
val rowHeight = 500

val extractTask = tasks.register<Copy>("extractSource") {
  dependsOn(downloadTask)
  from(tarTree(resources.gzip(downloadDest.get().asFile)))
  into(layout.projectDirectory.dir("tools/base/vector-drawable-tool"))
  filesMatching("**/vector-drawable-tool/build.gradle") {
    filter { line ->
      line
        .replace(
          "mainClass.set(\"com.android.ide.common.vectordrawable.VdCommandLineTool\")",
          "mainClass.set(\"com.android.ide.common.vectordrawable.VdCommandLineTool\")\n}\n\nrun {\n    workingDir = System.getProperty(\"user.dir\")"
        )
        .replace(
          "implementation libs.com.android.tools.sdkCommon",
          "implementation files(\"\${rootDir}/libs/sdk-common-as-bundled.jar\")"
        )
    }
  }
}

tasks.register("fetchSources") {
  group = "build setup"
  description = "Downloads and extracts the vector drawable tool source from upstream."
  dependsOn(extractTask)
}
