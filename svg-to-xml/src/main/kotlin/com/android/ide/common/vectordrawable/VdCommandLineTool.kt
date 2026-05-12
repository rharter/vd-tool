/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.ide.common.vectordrawable

import com.android.SdkConstants
import com.google.common.io.Files
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintWriter
import kotlin.system.exitProcess

/** Command line tool to convert a single SVG file to a VectorDrawable. */
object VdCommandLineTool {

  const val BROKEN_FILE_EXTENSION: String = ".broken"

  private const val DBG_COPY_BROKEN_SVG = false

  private fun exitWithErrorMessage(message: String): Nothing {
    System.err.println(message)
    exitProcess(-1)
  }

  @JvmStatic
  fun main(args: Array<String>) {
    val options = VdCommandLineOptions()
    val criticalError = options.parse(args)
    if (criticalError != null) {
      exitWithErrorMessage(criticalError + "\n\n" + VdCommandLineOptions.COMMAND_LINE_OPTION)
    }
    convertSVGToXml(options)
  }

  private fun convertSVGToXml(options: VdCommandLineOptions) {
    val inputSVGFile = checkNotNull(options.inputFile) { "inputFile must be set after a successful parse" }
    val outputDir = checkNotNull(options.outputDir) { "outputDir must be set after a successful parse" }
    val svgFilename = inputSVGFile.name
    if (!svgFilename.endsWith(SdkConstants.DOT_SVG)) {
      exitWithErrorMessage("Input must be a .svg file: $svgFilename")
    }
    val svgFilenameWithoutExtension = svgFilename.substring(0, svgFilename.lastIndexOf('.'))
    val outputFile = File(outputDir, svgFilenameWithoutExtension + SdkConstants.DOT_XML)

    try {
      val byteArrayOutStream = ByteArrayOutputStream()
      val error = Svg2Vector.parseSvgToXml(inputSVGFile.toPath(), byteArrayOutStream)

      if (error.isNotEmpty()) {
        System.err.println("error is $error")
        if (DBG_COPY_BROKEN_SVG) {
          val brokenFileName = svgFilename + BROKEN_FILE_EXTENSION
          val brokenSvgFile = File(outputDir, brokenFileName)
          Files.copy(inputSVGFile, brokenSvgFile)
        }
      }

      PrintWriter(outputFile).use { writer ->
        writer.print(byteArrayOutStream)
      }
    } catch (e: Exception) {
      System.err.println("exception${e.message}")
      e.printStackTrace()
    }

    println("Converted $svgFilename -> ${outputFile.name}")
  }
}
