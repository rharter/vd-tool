# Vector Drawable Tools

Two Gradle modules:

- `:svg-to-xml` — Java CLI that converts a single `.svg` file to an Android
  `VectorDrawable` XML file. Forked from AOSP's
  [vector-drawable-tool](https://android.googlesource.com/platform/tools/base/+/refs/heads/mirror-goog-studio-main/vector-drawable-tool/),
  with the rendering path removed.
- `:xml-to-png` — Renders a `VectorDrawable` XML file to a PNG via
  Android Studio's LayoutLib (through Paparazzi), matching the rendering
  shown in the IDE's drawable preview pane.

## Usage

### SVG → VectorDrawable XML

```shell
./gradlew :svg-to-xml:run --args="path/to/file.svg [-out output/dir]"
```

If `-out` is omitted, the XML is written next to the input `.svg`. Distribution
zip is at `svg-to-xml/build/distributions/svg-to-xml.zip` after running
`./gradlew :svg-to-xml:assembleDist`.

### VectorDrawable XML → PNG

```shell
./gradlew :xml-to-png:testDebugUnitTest \
  -PvdInput=path/to/vector.xml \
  -PvdOutput=path/to/out.png \
  [-PvdSize=1024]
```

If `-PvdSize` is omitted, the drawable's intrinsic size is used. Output PNG
matches the LayoutLib/Skia rendering used in Android Studio's editor preview.

## License

    Copyright (c) 2022 Ryan Harter

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
