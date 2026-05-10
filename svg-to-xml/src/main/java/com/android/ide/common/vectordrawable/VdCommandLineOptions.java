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

import java.io.File;

public class VdCommandLineOptions {
    // Support a command line tool to convert a single SVG to a VectorDrawable XML.

    private static final String OPTION_OUT = "-out";

    public static final String COMMAND_LINE_OPTION = "Converts an SVG file to a VectorDrawable XML file.\n"
            + "Usage: <file.svg> [-out <directory>]\n"
            + "Options:\n"
            + "  <file.svg>        The .svg file to convert. Must be the first argument.\n"
            + "  -out <directory>  If specified, write the converted file to the given directory,\n"
            + "                    which must exist. If not specified the converted file will be\n"
            + "                    written to the directory containing the input file.\n"
            + "Example:\n"
            + "  vd-tool file.svg\n"
            ;

    private File mInputFile;

    private File mOutputDir;

    public File getInputFile() {
        return mInputFile;
    }

    public File getOutputDir() {
        return mOutputDir;
    }

    /**
     * Parse the command line options.
     *
     * @param args the incoming command line options
     * @return null if no critical error happens, otherwise the error message.
     */
    public String parse(String[] args) {
        mOutputDir = null;

        if (args == null || args.length == 0) {
            return "ERROR: empty arguments";
        }
        if (args[0].startsWith("-")) {
            return "ERROR: first argument must be the input .svg file, got: " + args[0];
        }
        File argIn = new File(args[0]);
        System.out.println("input parsed " + argIn.getAbsolutePath());

        int index = 1;
        while (index < args.length) {
            String currentArg = args[index];
            if (OPTION_OUT.equalsIgnoreCase(currentArg)) {
                if ((index + 1) < args.length) {
                    mOutputDir = new File(args[index + 1]
                            .replaceFirst("^~", System.getProperty("user.home")));
                    System.out.println(OPTION_OUT + " parsed " + mOutputDir.getAbsolutePath());
                    index++;
                }
            } else {
                return "ERROR: unrecognized option " + currentArg;
            }
            index++;
        }

        if (!argIn.isFile()) {
            return "ERROR: input is not a file: " + argIn.getAbsolutePath();
        }
        mInputFile = argIn;
        if (mOutputDir == null) {
            mOutputDir = argIn.getParentFile();
        }
        if (!mOutputDir.isDirectory()) {
            return "ERROR: Output directory " + mOutputDir.getAbsolutePath()
                    + " doesn't exist or isn't a valid directory";
        }

        return null;
    }
}
