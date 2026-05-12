// SVG -> PNG (via :svg-to-xml + :xml-to-png):
//   ./gradlew render -Pinput=foo.svg [-Poutput=foo.png] [-Psize=1024]
// XML -> PNG (just :xml-to-png):
//   ./gradlew :xml-to-png:testDebugUnitTest -Pinput=foo.xml [-Poutput=foo.png] [-Psize=1024]
// Output defaults to <input-without-ext>.png next to the input.

val inputPath = project.findProperty("input") as String?
val outputProp = project.findProperty("output") as String?
val sizeProp = project.findProperty("size") as String?
val svgInputPath = inputPath?.takeIf { it.endsWith(".svg", ignoreCase = true) }
val xmlInputPath = inputPath?.takeIf { it.endsWith(".xml", ignoreCase = true) }

fun defaultPngFor(srcFile: File): File =
  outputProp?.let { file(it) } ?: srcFile.parentFile.resolve("${srcFile.nameWithoutExtension}.png")

fun configureRender(xmlFile: File, pngFile: File, needsSvgConversion: Boolean) {
  project(":xml-to-png").afterEvaluate {
    val drawableDir = file("src/main/res/drawable")

    // Stage the rendered XML into xml-to-png's resources as render_input.xml so it can
    // be loaded via R.drawable.render_input. Must happen at execution time — at config
    // time the XML produced by :svg-to-xml:run doesn't exist yet on a first-run SVG.
    val stageRenderInput = tasks.register<Copy>("stageRenderInput") {
      if (needsSvgConversion) dependsOn(":svg-to-xml:run")
      from(xmlFile)
      rename { "render_input.xml" }
      into(drawableDir)
    }
    tasks.named("preBuild").configure { dependsOn(stageRenderInput) }

    tasks.withType<Test>().configureEach {
      systemProperty("vd.output", pngFile.absolutePath)
      systemProperty("vd.size", sizeProp ?: "")
      inputs.file(xmlFile)
      outputs.file(pngFile)
    }
  }
}

if (svgInputPath != null) {
  val svgFile = file(svgInputPath)
  val xmlDir = layout.buildDirectory.dir("render").get().asFile
  val xmlFile = File(xmlDir, "${svgFile.nameWithoutExtension}.xml")

  project(":svg-to-xml").afterEvaluate {
    tasks.named<JavaExec>("run").configure {
      args = listOf(svgFile.absolutePath, "-out", xmlDir.absolutePath)
      doFirst { xmlDir.mkdirs() }
    }
  }

  configureRender(xmlFile, defaultPngFor(svgFile), needsSvgConversion = true)

  tasks.register("render") {
    group = "build"
    description = "Convert -Pinput.svg to -Poutput.png via :svg-to-xml + :xml-to-png"
    dependsOn(":xml-to-png:testDebugUnitTest")
  }
} else if (xmlInputPath != null) {
  val xmlFile = file(xmlInputPath)
  configureRender(xmlFile, defaultPngFor(xmlFile), needsSvgConversion = false)
}
