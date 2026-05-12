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

import java.io.File

class VdCommandLineOptions {

  var inputFile: File? = null
    private set

  var outputDir: File? = null
    private set

  /** Parse the command line options. Returns null on success, or an error message. */
  fun parse(args: Array<String>?): String? {
    outputDir = null

    if (args == null || args.isEmpty()) {
      return "ERROR: empty arguments"
    }
    if (args[0].startsWith("-")) {
      return "ERROR: first argument must be the input .svg file, got: ${args[0]}"
    }
    val argIn = File(args[0])
    println("input parsed ${argIn.absolutePath}")

    var index = 1
    while (index < args.size) {
      val currentArg = args[index]
      if (OPTION_OUT.equals(currentArg, ignoreCase = true)) {
        if (index + 1 < args.size) {
          outputDir = File(expandTilde(args[index + 1]))
          println("$OPTION_OUT parsed ${outputDir!!.absolutePath}")
          index++
        }
      } else {
        return "ERROR: unrecognized option $currentArg"
      }
      index++
    }

    if (!argIn.isFile) {
      return "ERROR: input is not a file: ${argIn.absolutePath}"
    }
    inputFile = argIn
    if (outputDir == null) {
      outputDir = argIn.parentFile
    }
    val outDir = outputDir
    if (outDir == null || !outDir.isDirectory) {
      return "ERROR: Output directory ${outDir?.absolutePath} doesn't exist or isn't a valid directory"
    }

    return null
  }

  companion object {
    private const val OPTION_OUT = "-out"

    const val COMMAND_LINE_OPTION: String =
      "Converts an SVG file to a VectorDrawable XML file.\n" +
        "Usage: <file.svg> [-out <directory>]\n" +
        "Options:\n" +
        "  <file.svg>        The .svg file to convert. Must be the first argument.\n" +
        "  -out <directory>  If specified, write the converted file to the given directory,\n" +
        "                    which must exist. If not specified the converted file will be\n" +
        "                    written to the directory containing the input file.\n" +
        "Example:\n" +
        "  vd-tool file.svg\n"

    private fun expandTilde(path: String): String =
      if (path.startsWith("~")) System.getProperty("user.home") + path.substring(1) else path
  }
}
