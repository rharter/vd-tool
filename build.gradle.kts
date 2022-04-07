import de.undercouch.gradle.tasks.download.Download

plugins {
  alias(libs.plugins.download)
}

val downloadTask = tasks.create<Download>("downloadSource") {
  src("https://android.googlesource.com/platform/tools/base/+archive/refs/heads/mirror-goog-studio-main/vector-drawable-tool.tar.gz")
  dest(project.layout.buildDirectory.file("downloads/vector-drawable-tool.tar.gz"))
}

val extractTask = tasks.create<Copy>("extractSource") {
  dependsOn(downloadTask)
  from(tarTree(resources.gzip(downloadTask.dest)))
  into(project.layout.projectDirectory.dir("tools/base/vector-drawable-tool"))
}

tasks.create("fetchSources") {
  dependsOn(downloadTask, extractTask)
}