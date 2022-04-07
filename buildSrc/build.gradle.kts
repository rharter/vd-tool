plugins {
  `java-gradle-plugin`
}

gradlePlugin {
  plugins {
    create("android-java-library") {
      id = "com.android.tools.java-library"
      implementationClass = "org.gradle.api.plugins.JavaLibraryPlugin"
    }
  }
}

