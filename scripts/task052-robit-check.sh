#!/usr/bin/env bash
# Focused actual robit prerequisite compilation/checks; does NOT execute the loader mixin or claim full-slice success.
set -euo pipefail
cd "$(dirname "$0")/.."
./gradlew -p fabric -I "$PWD/scripts/task052-renderer-classpath.init.gradle" printMainCompileClasspath --no-configuration-cache -q
CP=$(< fabric/build/task052-main-compile-classpath.txt)
OUT="$PWD/fabric/build/task052-robit-classes"
mkdir -p "$OUT"
javac -proc:none -cp "$CP" -sourcepath "$PWD/src/api/java:$PWD/src/main/java:$PWD/fabric/src/main/java" -d "$OUT" \
  src/api/java/mekanism/api/robit/*.java \
  src/main/java/mekanism/common/registries/MekanismRobitSkins.java \
  fabric/src/main/java/mekanism/fabric/robit/*.java \
  fabric/src/main/java/mekanism/fabric/mixin/robit/RegistryDataMixin.java \
  scripts/Task052RobitCheck.java
java -cp "$OUT:$CP" Task052RobitCheck "$PWD/src/datagen/generated/mekanism/data/mekanism/mekanism/robit_skin"
