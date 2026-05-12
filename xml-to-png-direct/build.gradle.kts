// Plain Kotlin/JVM module — NO Android Gradle Plugin, NO Paparazzi Gradle plugin.
// Spike to evaluate driving LayoutLib directly (via the Paparazzi runtime JAR only)
// for rendering a single VectorDrawable XML to a PNG without going through Gradle tests.

plugins {
  alias(libs.plugins.kotlin.jvm)
  application
}

// Configurations that pull down the layoutlib native runtime + framework resources.
// These are the same Maven coordinates the Paparazzi Gradle plugin sets up; we just
// resolve them ourselves and pass their on-disk paths into the JVM via system properties.
val layoutlibRuntimeJar by configurations.creating {
  isCanBeConsumed = false
  isCanBeResolved = true
}
val layoutlibResourcesJar by configurations.creating {
  isCanBeConsumed = false
  isCanBeResolved = true
}

val osArch = System.getProperty("os.arch").lowercase()
val osName = System.getProperty("os.name").lowercase()
val nativeClassifier = when {
  osName.startsWith("mac") && osArch.contains("aarch64") -> "mac-arm"
  osName.startsWith("mac") -> "mac"
  osName.startsWith("windows") -> "win"
  else -> "linux"
}

dependencies {
  implementation("app.cash.paparazzi:paparazzi:2.0.0-alpha04")
  // Make sure layoutlib-api 31.13.2 wins on the classpath, not 32.x. Paparazzi depends
  // on 31.13.2 transitively; force in case anything else pulls a newer one.
  implementation("com.android.tools.layoutlib:layoutlib-api:31.13.2")

  layoutlibRuntimeJar(
    "com.android.tools.layoutlib:layoutlib-runtime:16.1.1:$nativeClassifier",
  )
  layoutlibResourcesJar(
    "com.android.tools.layoutlib:layoutlib-resources:16.1.1",
  )
}

application {
  mainClass.set("dev.vdtool.direct.MainKt")
}

// Force consumers to use Java 17+; layoutlib has Java 17 byte code.
kotlin {
  jvmToolchain(21)
}

// Bake the classifier-resolved jar paths into a JVM property file the app reads at runtime.
// (We could also pass them through systemProperty in the run task; using a properties
// file makes the app work outside of Gradle too once the jars are present.)
val writeArtifactPaths = tasks.register("writeArtifactPaths") {
  val out = layout.buildDirectory.file("generated/artifact-paths.properties")
  outputs.file(out)
  // Read the configurations at execution time (lazy).
  doLast {
    val runtimeJar = layoutlibRuntimeJar.singleFile.absolutePath
    val resourcesJar = layoutlibResourcesJar.singleFile.absolutePath
    out.get().asFile.writeText(
      """
      |layoutlib.runtime.jar=$runtimeJar
      |layoutlib.resources.jar=$resourcesJar
      |""".trimMargin(),
    )
  }
}

tasks.named<JavaExec>("run") {
  dependsOn(writeArtifactPaths)
  // Pass the jar paths via -D so the app doesn't need to read its own jar.
  doFirst {
    val props = writeArtifactPaths.get().outputs.files.singleFile.readLines()
      .filter { it.contains("=") }
      .associate { it.substringBefore("=") to it.substringAfter("=") }
    systemProperty("layoutlib.runtime.jar", props.getValue("layoutlib.runtime.jar"))
    systemProperty("layoutlib.resources.jar", props.getValue("layoutlib.resources.jar"))
  }
  // Allow `./gradlew :xml-to-png-direct:run --args="input.xml output.png 512"` shorthand.
  // Without this the args are taken from -PrunArgs.
}

// Add the property-driven shorthand the existing root build.gradle uses for the Paparazzi pipeline.
val directInput: String? = project.findProperty("input") as String?
val directOutput: String? = project.findProperty("output") as String?
val directSize: String? = project.findProperty("size") as String?
if (directInput != null) {
  tasks.named<JavaExec>("run") {
    args = buildList {
      add(directInput)
      val out = directOutput ?: run {
        val f = file(directInput)
        f.parentFile.resolve("${f.nameWithoutExtension}.png").absolutePath
      }
      add(out)
      if (directSize != null) add(directSize)
    }
  }
}
