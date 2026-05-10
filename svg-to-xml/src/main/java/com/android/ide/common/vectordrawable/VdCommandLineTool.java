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

package com.android.ide.common.vectordrawable;

import com.android.SdkConstants;
import com.google.common.io.Files;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintWriter;

/**
 * Support a command line tool to convert a single SVG file to a VectorDrawable.
 */
public class VdCommandLineTool {

    public static final String BROKEN_FILE_EXTENSION = ".broken";

    private static final boolean DBG_COPY_BROKEN_SVG = false;

    private static void exitWithErrorMessage(String message) {
        System.err.println(message);
        System.exit(-1);
    }

    public static void main(String[] args) {
        VdCommandLineOptions options = new VdCommandLineOptions();
        String criticalError = options.parse(args);
        if (criticalError != null) {
            exitWithErrorMessage(criticalError + "\n\n" + VdCommandLineOptions.COMMAND_LINE_OPTION);
        }

        convertSVGToXml(options);
    }

    private static void convertSVGToXml(VdCommandLineOptions options) {
        File inputSVGFile = options.getInputFile();
        File outputDir = options.getOutputDir();
        String svgFilename = inputSVGFile.getName();
        if (!svgFilename.endsWith(SdkConstants.DOT_SVG)) {
            exitWithErrorMessage("Input must be a .svg file: " + svgFilename);
        }
        String svgFilenameWithoutExtension =
                svgFilename.substring(0, svgFilename.lastIndexOf('.'));
        File outputFile =
                new File(outputDir, svgFilenameWithoutExtension + SdkConstants.DOT_XML);

        try {
            ByteArrayOutputStream byteArrayOutStream = new ByteArrayOutputStream();
            String error = Svg2Vector.parseSvgToXml(inputSVGFile.toPath(), byteArrayOutStream);

            if (!error.isEmpty()) {
                System.err.println("error is " + error);
                if (DBG_COPY_BROKEN_SVG) {
                    // Copy the broken svg file in the same directory but with a new extension.
                    String brokenFileName = svgFilename + BROKEN_FILE_EXTENSION;
                    File brokenSvgFile = new File(outputDir, brokenFileName);
                    Files.copy(inputSVGFile, brokenSvgFile);
                }
            }

            try (PrintWriter writer = new PrintWriter(outputFile)) {
                writer.print(byteArrayOutStream);
            }
        } catch (Exception e) {
            System.err.println("exception" + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("Converted " + svgFilename + " -> " + outputFile.getName());
    }
}
