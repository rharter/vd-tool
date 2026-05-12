// SVG/XML → PNG single-shot CLI, routed through the in-process LayoutLib renderer:
//   ./gradlew render -Pinput=foo.svg [-Poutput=foo.png] [-Psize=1024]
//   ./gradlew render -Pinput=foo.xml [-Poutput=foo.png] [-Psize=1024]
// (For HTTP/web use, the :frontend module is the same renderer behind a server.)

val inputPath = project.findProperty("input") as String?
val outputProp = project.findProperty("output") as String?
val sizeProp = project.findProperty("size") as String?

if (inputPath != null) {
  val inputFile = file(inputPath)
  val isSvg = inputPath.endsWith(".svg", ignoreCase = true)
  val pngFile = outputProp?.let { file(it) }
    ?: inputFile.parentFile.resolve("${inputFile.nameWithoutExtension}.png")

  val xmlFile: File = if (isSvg) {
    val xmlDir = layout.buildDirectory.dir("render").get().asFile
    val staged = File(xmlDir, "${inputFile.nameWithoutExtension}.xml")
    project(":svg-to-xml").afterEvaluate {
      tasks.named<JavaExec>("run").configure {
        args = listOf(inputFile.absolutePath, "-out", xmlDir.absolutePath)
        doFirst { xmlDir.mkdirs() }
      }
    }
    staged
  } else {
    inputFile
  }

  project(":xml-to-png-direct").afterEvaluate {
    tasks.named<JavaExec>("run").configure {
      if (isSvg) dependsOn(":svg-to-xml:run")
      args = buildList {
        add(xmlFile.absolutePath)
        add(pngFile.absolutePath)
        if (sizeProp != null) add(sizeProp)
      }
    }
  }

  tasks.register("render") {
    group = "build"
    description = "Convert -Pinput (svg or xml) to -Poutput png"
    dependsOn(":xml-to-png-direct:run")
  }
}
