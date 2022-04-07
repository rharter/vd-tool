# Vector Drawable Tool

This repository is simply a repackaging of the [vector drawable tool](https://android.googlesource.com/platform/tools/base/+/refs/heads/mirror-goog-studio-main/vector-drawable-tool/)
from the Android Studio source code. The included Gradle files take care of downloading and extracting the latest
source code, allowing you to build the command line tool, without the need to download the entire AOSP.

## Usage

Download the latest [release](./releases), extract it wherever you'd like, and run the tool.

```shell
curl -L -o /tmp/vd-tool.zip https://github.com/rharter/vd-tool/releases/latest/download/vd-tool.zip
mkdir ~/bin
unzip /tmp/vd-tool.zip -d ~/bin
~/bin/vd-tool/bin/vd-tool --help
```

## Building

Since this repository doesn't contain the actual source code of the tool, you first need to run the
`fetchSources` task, which will download and extract the source. Then you can use standard `run` and 
`assembleDist` tasks to build the project.

```shell
# First fetch the sources
./gradlew fetchSources

# Assemble the distributions
./gradlew assembleDist

# Or simply run the tool directly from Gradle
./gradlew run --args="--help"
```

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
