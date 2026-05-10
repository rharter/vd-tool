// Single-shot SVG -> PNG pipeline.
//   ./gradlew render -Pinput=foo.svg [-Poutput=foo.png] [-Psize=1024]
// Or skip the SVG conversion and render an existing VectorDrawable XML:
//   ./gradlew :xml-to-png:testDebugUnitTest -PvdInput=foo.xml [-PvdOutput=foo.png] [-PvdSize=1024]
// Output defaults to <input-without-ext>.png next to the input.

val svgInputPath = project.findProperty("input") as String?
val xmlInputPath = project.findProperty("vdInput") as String?
val outputProp = (project.findProperty("output") ?: project.findProperty("vdOutput")) as String?
val sizeProp = (project.findProperty("size") ?: project.findProperty("vdSize")) as String?

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
      if (sizeProp != null) inputs.property("vd.size", sizeProp)
    }
  }
}

if (svgInputPath != null) {
  val svgFile = file(svgInputPath)
  val xmlDir = layout.buildDirectory.dir("render").get().asFile
  val xmlFile = File(xmlDir, "${svgFile.nameWithoutExtension}.xml")
  val pngFile = outputProp?.let { file(it) }
    ?: svgFile.parentFile.resolve("${svgFile.nameWithoutExtension}.png")

  project(":svg-to-xml").afterEvaluate {
    tasks.named<JavaExec>("run").configure {
      args = listOf(svgFile.absolutePath, "-out", xmlDir.absolutePath)
      doFirst { xmlDir.mkdirs() }
    }
  }

  configureRender(xmlFile, pngFile, needsSvgConversion = true)

  tasks.register("render") {
    group = "build"
    description = "Convert -Pinput.svg to -Poutput.png via :svg-to-xml + :xml-to-png"
    dependsOn(":xml-to-png:testDebugUnitTest")
  }
} else if (xmlInputPath != null) {
  val xmlFile = file(xmlInputPath)
  val pngFile = outputProp?.let { file(it) }
    ?: xmlFile.parentFile.resolve("${xmlFile.nameWithoutExtension}.png")

  configureRender(xmlFile, pngFile, needsSvgConversion = false)
}
