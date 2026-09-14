#!/usr/bin/env bash
# Focused Task052 check for the transmitter model owner. Plain javac/java against the pinned Loom-resolved
# classpath; no Minecraft client runtime, no fixtures, no server.
#
#   scripts/task052-transmitter-check.sh
#
# It runs three real checks:
#   1. Pure-Java OBJ/MTL parse of the shipped transmitter_small/large assets (groups/materials/flip_v/glass override).
#   2. Face immutability: the parser's own face corner lists must reject mutation.
#   3. Real model chain against the shipped resource pack: discovers transmitter models, reads the OBJ/MTL through a
#      resource manager, resolves tier textures and actual pinned vanilla parent display values, and checks
#      model-vs-resource dependencies. Sprite-free: parses geometry and compiles the baker, but DOES NOT bake quads.
set -euo pipefail
cd "$(dirname "$0")/.."

ASSETS="src/main/resources/assets/mekanism/models/block"
OUT="$(mktemp -d /tmp/t052-transmitter-check.XXXXXX)"
trap 'rm -rf "$OUT"' EXIT

# The classpath is the pinned Loom-resolved (remapped mojmap Minecraft + remapped Fabric API) main compile classpath.
CP_FILE="fabric/build/task052-main-compile-classpath.txt"
if [ ! -f "$CP_FILE" ]; then
  echo "missing $CP_FILE; generate it with:" >&2
  echo "  ./gradlew -p fabric -I \"\$PWD/scripts/task052-renderer-classpath.init.gradle\" printMainCompileClasspath --no-configuration-cache -q" >&2
  exit 2
fi
CP="$(cat "$CP_FILE")"

echo "== check 1+2: shipped OBJ/MTL parse + Face immutability =="
# The parser is Minecraft-free but uses org.jetbrains.annotations; take that one jar from the pinned classpath so the
# command is self-contained (REVIEW16: the header previously omitted the annotations classpath).
ANNOTATIONS="$(tr ':' '\n' <<< "$CP" | grep -m1 'annotations-.*\.jar$' || true)"
javac -proc:none -nowarn -d "$OUT" -cp "$CP" \
  scripts/TransmitterObjGeometryCheck.java \
  fabric/src/main/java/mekanism/fabric/client/render/obj/TransmitterObjGeometry.java
java -cp "$OUT${ANNOTATIONS:+:$ANNOTATIONS}" TransmitterObjGeometryCheck "$ASSETS/"

echo "== check 3: real model chain over the shipped resource pack =="
javac -proc:none -nowarn -d "$OUT" -cp "$CP" \
  -sourcepath "src/api/java:src/main/java:fabric/src/main/java" \
  scripts/TransmitterModelChainCheck.java
java -cp "$OUT:$CP" mekanism.fabric.client.render.obj.TransmitterModelChainCheck
