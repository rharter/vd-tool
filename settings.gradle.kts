dependencyResolutionManagement {
  repositories {
    mavenCentral()
    google()
  }
}

// When the project was migrated from Gradle 7.4.2 to 9.6.1, gradle's underlying path parser became significant more strict.

// While not required in previous versions of Gradle, in v9.6.1 a relative path is required to prevent the error below:

// Configuring project ':tools:base:vector-drawable-tool' without an existing directory is not allowed. 
// The configured projectDirectory '/home/vm/repos/vd-tool/tools/base/vector-drawable-tool' does not exist, can't be written to or is not a directory.
// For more information, please refer to https://docs.gradle.org/9.6.1/userguide/multi_project_builds.html#include_existing_projects_only

val dir = file("tools/base/vector-drawable-tool")

if (dir.exists()) {
   include(":tools:base:vector-drawable-tool")
}
