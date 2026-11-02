#!/usr/bin/env bash
# Energy interop against a real third-party tech mod (W-99).
#
# Loads Tech Reborn and Reborn Core next to the release jars in a disposable dedicated server and checks both directions:
# a Mekanism energy cube pushes through a universal cable into a Tech Reborn grinder, and a Tech Reborn solid fuel
# generator pushes back through a universal cable into an energized smelter. The two mods are a test-runtime input only -
# they are downloaded into the smoke cache and never enter the release jars (the packaged static gate asserts that).
#
# Prerequisite: a remapped release jar (`./build.sh`). Requires only a JRE.
# Drive real Mekanism machines in a disposable Fabric dedicated server.
#
# Places an enrichment chamber and an energized smelter, injects energy and input
# through the vanilla `data merge block` command, lets the server tick, and asserts
# the processed output appears. This is the end-to-end check for the machine
# ownership closure: it exercises block entity ticking, the real recipe lookup,
# energy accounting, container persistence and the item output path.
#
# Prerequisite: a remapped release jar (`./fabric-build.sh remapJar` or `./build.sh`).
# Requires only a JRE; the launcher and Fabric API jars are cached or downloaded.
set -euo pipefail

ROOT=$HOME/server-v2/develop/mekanism-fabric
PROPERTIES="$ROOT/fabric/gradle.properties"
CACHE_DIR=${FABRIC_SMOKE_CACHE_DIR:-${XDG_CACHE_HOME:-$HOME/.cache}/mekanism-fabric-smoke}
TIMEOUT=${FABRIC_ENERGY_SMOKE_TIMEOUT:-240}
KEEP_SERVER=${FABRIC_SMOKE_KEEP_SERVER:-0}
SERVER_DIR=
LOG_FILE=

property() {
  local key=$1 value
  value=$(awk -F= -v key="$key" '$1 == key { sub(/^[^=]*=/, ""); print; exit }' "$PROPERTIES")
  if [[ -z "$value" ]]; then
    echo "fabric-third-party-energy-smoke: missing $key in $PROPERTIES" >&2
    exit 2
  fi
  printf '%s\n' "$value"
}

MINECRAFT_VERSION=$(property minecraft_version)
LOADER_VERSION=$(property loader_version)
FABRIC_API_VERSION=$(property fabric_api_version)
TEAM_REBORN_ENERGY_VERSION=$(property team_reborn_energy_version)
#Pinned third-party test-runtime mods; they never become release inputs.
TECH_REBORN_VERSION=5.11.19
REBORN_CORE_VERSION=5.11.19
REBORN_CORE_FILE_ID=QtkC5plA
INSTALLER_VERSION=${FABRIC_INSTALLER_VERSION:-1.1.2}

cleanup() {
  local status=$?
  trap - EXIT INT TERM
  if [[ -n "$SERVER_DIR" && -d "$SERVER_DIR" && "$KEEP_SERVER" != 1 ]]; then
    rm -rf "$SERVER_DIR"
  elif [[ -n "$SERVER_DIR" ]]; then
    printf 'fabric-third-party-energy-smoke: retained server: %s\n' "$SERVER_DIR" >&2
  fi
  exit "$status"
}
trap cleanup EXIT INT TERM

fetch() {
  local url=$1 destination=$2 temporary="${2}.part.$$"
  if [[ -s "$destination" ]] && jar tf "$destination" >/dev/null 2>&1; then
    return
  fi
  rm -f "$destination" "$temporary"
  printf 'fabric-third-party-energy-smoke: downloading %s\n' "$url" >&2
  if ! curl --fail --location --retry 3 --retry-all-errors --connect-timeout 15 \
      --max-time 300 --output "$temporary" "$url"; then
    rm -f "$temporary"
    echo "fabric-third-party-energy-smoke: download failed: $url" >&2
    exit 2
  fi
  mv "$temporary" "$destination"
}

#Newest artifact wins: a stale dist/ jar from an earlier release build must not mask the current remap output.
MOD_JAR=
for candidate in "$ROOT/dist/"Mekanism-Fabric-*.jar "$ROOT/fabric/build/libs/"*.jar; do
  [[ -f "$candidate" ]] || continue
  [[ "$candidate" == *-sources.jar || "$candidate" == *-dev.jar || "$candidate" == *fixture* ]] && continue
  if [[ -z "$MOD_JAR" || "$candidate" -nt "$MOD_JAR" ]]; then MOD_JAR=$candidate; fi
done
if [[ -z "$MOD_JAR" ]]; then
  echo "fabric-third-party-energy-smoke: no packaged jar; run ./fabric-build.sh remapJar first" >&2
  exit 2
fi

mkdir -p "$CACHE_DIR"
LAUNCHER_JAR="$CACHE_DIR/fabric-server-${MINECRAFT_VERSION}-${LOADER_VERSION}-${INSTALLER_VERSION}.jar"
FABRIC_API_JAR="$CACHE_DIR/fabric-api-${FABRIC_API_VERSION}.jar"
ENERGY_JAR="$CACHE_DIR/energy-${TEAM_REBORN_ENERGY_VERSION}.jar"
fetch "https://meta.fabricmc.net/v2/versions/loader/${MINECRAFT_VERSION}/${LOADER_VERSION}/${INSTALLER_VERSION}/server/jar" "$LAUNCHER_JAR"
fetch "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/${FABRIC_API_VERSION}/fabric-api-${FABRIC_API_VERSION}.jar" "$FABRIC_API_JAR"
fetch "https://maven.fabricmc.net/teamreborn/energy/${TEAM_REBORN_ENERGY_VERSION}/energy-${TEAM_REBORN_ENERGY_VERSION}.jar" "$ENERGY_JAR"

TECH_REBORN_JAR="$CACHE_DIR/TechReborn-${TECH_REBORN_VERSION}.jar"
REBORN_CORE_JAR="$CACHE_DIR/RebornCore-${REBORN_CORE_VERSION}.jar"
fetch "https://cdn.modrinth.com/data/3eMENr4V/versions/CNwZZ4FD/TechReborn-${TECH_REBORN_VERSION}.jar" "$TECH_REBORN_JAR"
fetch "https://cdn.modrinth.com/data/3NCrJdj3/versions/${REBORN_CORE_FILE_ID}/RebornCore-${REBORN_CORE_VERSION}.jar" "$REBORN_CORE_JAR"

SERVER_DIR=$(mktemp -d "${TMPDIR:-/tmp}/mekanism-third-party-energy-smoke.XXXXXX")
LOG_FILE="$SERVER_DIR/server.log"
mkdir -p "$SERVER_DIR/mods"
cp "$LAUNCHER_JAR" "$SERVER_DIR/fabric-server-launch.jar"
# Reuse only cached runtime binaries when installer metadata is unavailable; each test still gets a fresh world/mod set.
if [[ -n ${FABRIC_SMOKE_RUNTIME_CACHE_DIR:-} ]]; then
  for part in libraries versions .fabric/server; do
    [[ -d "$FABRIC_SMOKE_RUNTIME_CACHE_DIR/$part" ]] || continue
    mkdir -p "$(dirname "$SERVER_DIR/$part")"
    cp -a --reflink=auto "$FABRIC_SMOKE_RUNTIME_CACHE_DIR/$part" "$SERVER_DIR/$part"
  done
fi
cp "$FABRIC_API_JAR" "$ENERGY_JAR" "$MOD_JAR" "$SERVER_DIR/mods/"
cp "$TECH_REBORN_JAR" "$REBORN_CORE_JAR" "$SERVER_DIR/mods/"
printf 'eula=true\n' > "$SERVER_DIR/eula.txt"
printf 'online-mode=false\nserver-port=0\nspawn-protection=0\nmax-players=1\nview-distance=4\nsync-chunk-writes=false\n' > "$SERVER_DIR/server.properties"
if [[ ${FABRIC_MACHINE_BLACKLIST_FE:-0} == 1 ]]; then
  mkdir -p "$SERVER_DIR/world/serverconfig/Mekanism"
  printf '[energy_conversion]\nblacklistForge = true\n' > "$SERVER_DIR/world/serverconfig/Mekanism/general.toml"
fi

cd "$SERVER_DIR"
(
  for _ in $(seq 1 180); do
    grep -q 'Done (' server.log 2>/dev/null && break
    sleep 1
  done
  sleep 1
  echo "forceload add 0 0"
  echo "forceload add 16 0"
  #Direction A: Mekanism energy cube -> Mekanism universal cable -> Tech Reborn grinder. The cable is the bridge that
  #pushes Mekanism energy into a foreign Team Reborn EnergyStorage.
  echo "setblock 0 100 0 mekanism:basic_energy_cube"
  echo 'data merge block 0 100 0 {energy_containers:[{container:0b,stored:60000L}], component_config:{config0:[I;4,4,4,4,4,4]}}'
  sleep 2
  echo "setblock 1 100 0 mekanism:basic_universal_cable"
  echo 'data merge block 1 100 0 {connection:[I;3,3,3,3,2,1]}'
  echo "setblock 2 100 0 techreborn:grinder"
  sleep 4
  echo "data get block 2 100 0"
  echo "tick sprint 100"
  sleep 2
  echo "data get block 2 100 0"
  #Direction B: Tech Reborn solid fuel generator -> Mekanism universal cable -> energized smelter. The cable pulls from a
  #foreign EnergyStorage, which is the reverse direction.
  echo "setblock 20 100 0 techreborn:solid_fuel_generator"
  echo "item replace block 20 100 0 container.0 with minecraft:coal 8"
  sleep 2
  echo "setblock 21 100 0 mekanism:basic_universal_cable"
  echo 'data merge block 21 100 0 {connection:[I;3,3,3,3,2,1]}'
  echo "setblock 22 100 0 mekanism:energized_smelter"
  echo 'data merge block 22 100 0 {component_config:{config0:[I;1,1,1,1,1,1]}}'
  sleep 4
  echo "tick sprint 100"
  sleep 3
  echo "data get block 20 100 0"
  echo "data get block 22 100 0"
  echo "stop"
  sleep 5
) | timeout "$TIMEOUT" java -Xms512M -Xmx2G -jar fabric-server-launch.jar nogui > server.log 2>&1 || true

failures=0
check() {
  local description=$1 pattern=$2
  if grep -Eq "$pattern" "$LOG_FILE"; then
    printf 'PASS %s\n' "$description"
  else
    printf 'FAIL %s\n' "$description"
    failures=$((failures + 1))
  fi
}

check "dedicated server reached Done" 'Done \([0-9.]+s\)!'
check "tech reborn grinder was charged through a mekanism cable" 'INFO\]: 2, 100, 0 has the following block data.*PowerAcceptor: \{energy: [1-9]'
check "mekanism smelter was charged from the tech reborn generator" 'INFO\]: 22, 100, 0 has the following block data.*energy_containers.*stored: [1-9]'
if (( failures > 0 )); then
  echo "fabric-third-party-energy-smoke: FAILED ($failures checks)" >&2
  exit 1
fi
echo "fabric-third-party-energy-smoke: PASSED"
