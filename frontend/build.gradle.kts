plugins {
  alias(libs.plugins.kotlin.jvm)
  application
}

application {
  mainClass.set("dev.vdtool.frontend.MainKt")
  // ByteBuddy's dynamic agent install (used by PaparazziSdk for AppCompat/EditMode
  // interception) prints a warning on JDK 21 and is disallowed on JDK 24+ without this.
  applicationDefaultJvmArgs = listOf("-XX:+EnableDynamicAgentLoading")
  applicationName = "frontend"
}

// The Paparazzi runtime needs two artifacts unpacked at startup: layoutlib-runtime
// (native libs + fonts + ICU, OS-specific) and layoutlib-resources (framework res).
// Resolve them ourselves and bake their paths into the installDist launcher so we
// don't need Gradle present at runtime.
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
  implementation(libs.javalin)
  implementation(libs.slf4j.simple)
  // For Svg2Vector.parseSvgToXml — same artifact :svg-to-xml uses.
  implementation(libs.com.android.tools.sdkCommon)
  implementation(project(":xml-to-png-direct"))

  layoutlibRuntimeJar(
    "com.android.tools.layoutlib:layoutlib-runtime:16.1.1:$nativeClassifier",
  )
  layoutlibResourcesJar(
    "com.android.tools.layoutlib:layoutlib-resources:16.1.1",
  )
}

// Stage the resolved layoutlib jars into the installDist output so the launcher script
// can pass their paths via -D system properties without depending on Gradle at runtime.
val stageLayoutlibJars = tasks.register<Copy>("stageLayoutlibJars") {
  from(layoutlibRuntimeJar) { rename { "layoutlib-runtime.jar" } }
  from(layoutlibResourcesJar) { rename { "layoutlib-resources.jar" } }
  into(layout.buildDirectory.dir("layoutlib"))
}

distributions {
  named("main") {
    contents {
      from(stageLayoutlibJars) {
        into("layoutlib")
      }
    }
  }
}

// The installDist launcher needs to point Java at <APP_HOME>/layoutlib/*.jar — but
// Gradle's CreateStartScripts shell-quotes defaultJvmOpts so a literal "$APP_HOME"
// doesn't expand at runtime. We inject `export LAYOUTLIB_*_JAR` lines into the
// generated bash launcher after APP_HOME is computed; Kotlin reads those env vars
// and promotes them to system properties before DirectRenderer starts.
tasks.named<CreateStartScripts>("startScripts") {
  dependsOn(stageLayoutlibJars)
  doLast {
    val unix = unixScript
    val text = unix.readText()
    val exportLines = """
      |export LAYOUTLIB_RUNTIME_JAR="${'$'}APP_HOME/layoutlib/layoutlib-runtime.jar"
      |export LAYOUTLIB_RESOURCES_JAR="${'$'}APP_HOME/layoutlib/layoutlib-resources.jar"
      |
    """.trimMargin()
    // Insert exports right after the `APP_HOME=$( cd ...` line that computes APP_HOME.
    val appHomeMarker = "APP_HOME=\$( cd -P"
    val anchorEnd = text.indexOf('\n', text.indexOf(appHomeMarker))
    check(anchorEnd > 0) { "Could not find APP_HOME computation line in start script" }
    unix.writeText(text.substring(0, anchorEnd + 1) + "\n" + exportLines + text.substring(anchorEnd + 1))

    val win = windowsScript
    val winText = win.readText()
    val winExports = """
      set LAYOUTLIB_RUNTIME_JAR=%APP_HOME%\layoutlib\layoutlib-runtime.jar
      set LAYOUTLIB_RESOURCES_JAR=%APP_HOME%\layoutlib\layoutlib-resources.jar

    """.trimIndent()
    val winAnchor = "set APP_HOME="
    val winEnd = winText.indexOf('\n', winText.indexOf(winAnchor))
    if (winEnd > 0) {
      win.writeText(winText.substring(0, winEnd + 1) + winExports + winText.substring(winEnd + 1))
    }
  }
}

tasks.named<JavaExec>("run") {
  // For local `./gradlew :frontend:run`, point at the resolved jars directly.
  dependsOn(stageLayoutlibJars)
  doFirst {
    val stageDir = stageLayoutlibJars.get().outputs.files.singleFile
    systemProperty("layoutlib.runtime.jar", File(stageDir, "layoutlib-runtime.jar").absolutePath)
    systemProperty("layoutlib.resources.jar", File(stageDir, "layoutlib-resources.jar").absolutePath)
  }
}
