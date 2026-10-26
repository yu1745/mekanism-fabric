#!/usr/bin/env bash
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

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
PROPERTIES="$ROOT/fabric/gradle.properties"
CACHE_DIR=${FABRIC_SMOKE_CACHE_DIR:-${XDG_CACHE_HOME:-$HOME/.cache}/mekanism-fabric-smoke}
TIMEOUT=${FABRIC_MACHINE_SMOKE_TIMEOUT:-240}
KEEP_SERVER=${FABRIC_SMOKE_KEEP_SERVER:-0}
SERVER_DIR=
LOG_FILE=

property() {
  local key=$1 value
  value=$(awk -F= -v key="$key" '$1 == key { sub(/^[^=]*=/, ""); print; exit }' "$PROPERTIES")
  if [[ -z "$value" ]]; then
    echo "fabric-machine-smoke: missing $key in $PROPERTIES" >&2
    exit 2
  fi
  printf '%s\n' "$value"
}

MINECRAFT_VERSION=$(property minecraft_version)
LOADER_VERSION=$(property loader_version)
FABRIC_API_VERSION=$(property fabric_api_version)
TEAM_REBORN_ENERGY_VERSION=$(property team_reborn_energy_version)
INSTALLER_VERSION=${FABRIC_INSTALLER_VERSION:-1.1.2}

cleanup() {
  local status=$?
  trap - EXIT INT TERM
  if [[ -n "$SERVER_DIR" && -d "$SERVER_DIR" && "$KEEP_SERVER" != 1 ]]; then
    rm -rf "$SERVER_DIR"
  elif [[ -n "$SERVER_DIR" ]]; then
    printf 'fabric-machine-smoke: retained server: %s\n' "$SERVER_DIR" >&2
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
  printf 'fabric-machine-smoke: downloading %s\n' "$url" >&2
  if ! curl --fail --location --retry 3 --retry-all-errors --connect-timeout 15 \
      --max-time 300 --output "$temporary" "$url"; then
    rm -f "$temporary"
    echo "fabric-machine-smoke: download failed: $url" >&2
    exit 2
  fi
  mv "$temporary" "$destination"
}

#Newest artifact wins: a stale dist/ jar from an earlier release build must not mask the current remap output.
MOD_JAR=$(find "$ROOT/dist" "$ROOT/fabric/build/libs" -maxdepth 1 -name '*.jar' \
  ! -name '*-sources.jar' ! -name '*-dev.jar' ! -name '*fixture*' -printf '%T@ %p\n' 2>/dev/null \
  | sort -rn | head -1 | cut -d' ' -f2-)
if [[ -z "$MOD_JAR" ]]; then
  echo "fabric-machine-smoke: no packaged jar; run ./fabric-build.sh remapJar first" >&2
  exit 2
fi

mkdir -p "$CACHE_DIR"
LAUNCHER_JAR="$CACHE_DIR/fabric-server-${MINECRAFT_VERSION}-${LOADER_VERSION}-${INSTALLER_VERSION}.jar"
FABRIC_API_JAR="$CACHE_DIR/fabric-api-${FABRIC_API_VERSION}.jar"
ENERGY_JAR="$CACHE_DIR/energy-${TEAM_REBORN_ENERGY_VERSION}.jar"
fetch "https://meta.fabricmc.net/v2/versions/loader/${MINECRAFT_VERSION}/${LOADER_VERSION}/${INSTALLER_VERSION}/server/jar" "$LAUNCHER_JAR"
fetch "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/${FABRIC_API_VERSION}/fabric-api-${FABRIC_API_VERSION}.jar" "$FABRIC_API_JAR"
fetch "https://maven.fabricmc.net/teamreborn/energy/${TEAM_REBORN_ENERGY_VERSION}/energy-${TEAM_REBORN_ENERGY_VERSION}.jar" "$ENERGY_JAR"

SERVER_DIR=$(mktemp -d "${TMPDIR:-/tmp}/mekanism-machine-smoke.XXXXXX")
LOG_FILE="$SERVER_DIR/server.log"
mkdir -p "$SERVER_DIR/mods"
cp "$LAUNCHER_JAR" "$SERVER_DIR/fabric-server-launch.jar"
cp "$FABRIC_API_JAR" "$ENERGY_JAR" "$MOD_JAR" "$SERVER_DIR/mods/"
printf 'eula=true\n' > "$SERVER_DIR/eula.txt"
printf 'online-mode=false\nserver-port=0\nspawn-protection=0\nmax-players=1\nview-distance=4\nsync-chunk-writes=false\n' > "$SERVER_DIR/server.properties"

cd "$SERVER_DIR"
(
  for _ in $(seq 1 180); do
    grep -q 'Done (' server.log 2>/dev/null && break
    sleep 1
  done
  sleep 1
  #Force the three chunks the scenarios span: spawn-chunk coverage alone does not reliably include chunk 2.
  echo "forceload add 0 0"
  echo "forceload add 16 0"
  echo "forceload add 32 0"
  echo "setblock 0 100 0 mekanism:enrichment_chamber"
  echo "setblock 3 100 0 mekanism:energized_smelter"
  echo "setblock 6 100 0 mekanism:basic_universal_cable"
  echo "setblock 9 100 0 mekanism:qio_dashboard"
  #Energy network scenario: a storage cube ejects through a universal cable into a second chamber. The side configs are what a
  #placed block item applies; data merge is used because /setblock places no item components.
  echo "setblock 12 100 0 mekanism:enrichment_chamber"
  echo "setblock 14 100 0 mekanism:basic_energy_cube"
  #Transport scenarios: chests + logistical transporter (items), fluid tanks + mechanical pipe (fluids), chemical tanks +
  #pressurized tube (chemicals). The transmitters are placed after their endpoints so their first refresh finds the acceptors,
  #then their configured PULL/PUSH sides are written; the PULL side re-resolves its acceptor every attempt.
  echo "setblock 20 100 0 minecraft:chest"
  echo "setblock 22 100 0 minecraft:chest"
  echo "item replace block 20 100 0 container.0 with minecraft:iron_ore 8"
  echo "setblock 24 100 0 mekanism:basic_fluid_tank"
  echo "setblock 26 100 0 mekanism:basic_fluid_tank"
  echo "setblock 28 100 0 mekanism:basic_chemical_tank"
  echo "setblock 30 100 0 mekanism:basic_chemical_tank"
  #Cross-system scenarios: a transporter feeding a machine input and a pipe feeding a machine fluid input.
  echo "setblock 32 100 0 minecraft:chest"
  echo "item replace block 32 100 0 container.0 with minecraft:iron_ore 4"
  echo "setblock 34 100 0 mekanism:enrichment_chamber"
  echo "setblock 36 100 0 mekanism:basic_fluid_tank"
  echo "setblock 38 100 0 mekanism:electrolytic_separator"
  sleep 2
  echo 'data merge block 0 100 0 {energy_containers:[{container:0b,stored:20000L}], items:[{slot:0b,item:{id:"minecraft:iron_ore",count:2}}]}'
  echo 'data merge block 3 100 0 {energy_containers:[{container:0b,stored:20000L}], items:[{slot:0b,item:{id:"minecraft:iron_ore",count:2}}]}'
  echo 'data merge block 12 100 0 {component_config:{config0:[I;1,1,1,1,1,1]}, items:[{slot:0b,item:{id:"minecraft:iron_ore",count:2}}]}'
  echo 'data merge block 14 100 0 {component_config:{eject0:1b, config0:[I;4,4,4,4,4,4]}, energy_containers:[{container:0b,stored:100000L}]}'
  echo 'data merge block 24 100 0 {component_config:{config1:[I;4,4,4,4,4,4]}, fluid_tanks:[{tank:0b,stored:{id:"minecraft:water",amount:8000}}]}'
  echo 'data merge block 26 100 0 {component_config:{config1:[I;1,1,1,1,1,1]}}'
  echo 'data merge block 28 100 0 {component_config:{config2:[I;4,4,4,4,4,4]}, chemical_tanks:[{tank:0b,stored:{id:"mekanism:oxygen",amount:8000}}]}'
  echo 'data merge block 30 100 0 {component_config:{config2:[I;1,1,1,1,1,1]}}'
  echo 'data merge block 34 100 0 {component_config:{config0:[I;1,1,1,1,1,1], config3:[I;1,1,1,1,1,1]}, energy_containers:[{container:0b,stored:20000L}]}'
  echo 'data merge block 36 100 0 {component_config:{config1:[I;4,4,4,4,4,4]}, fluid_tanks:[{tank:0b,stored:{id:"minecraft:water",amount:8000}}]}'
  echo 'data merge block 38 100 0 {component_config:{config0:[I;1,1,1,1,1,1], config1:[I;1,1,1,1,1,1], config2:[I;4,4,4,4,4,4]}, energy_containers:[{container:0b,stored:20000L}]}'
  sleep 3
  echo "setblock 13 100 0 mekanism:basic_universal_cable"
  echo "setblock 21 100 0 mekanism:basic_logistical_transporter"
  echo "setblock 25 100 0 mekanism:basic_mechanical_pipe"
  echo "setblock 29 100 0 mekanism:basic_pressurized_tube"
  echo "setblock 33 100 0 mekanism:basic_logistical_transporter"
  echo "setblock 37 100 0 mekanism:basic_mechanical_pipe"
  sleep 3
  echo 'data merge block 21 100 0 {connection:[I;3,3,3,3,2,1]}'
  echo 'data merge block 25 100 0 {connection:[I;3,3,3,3,2,1]}'
  echo 'data merge block 29 100 0 {connection:[I;3,3,3,3,2,1]}'
  echo 'data merge block 33 100 0 {connection:[I;3,3,3,3,2,1]}'
  echo 'data merge block 37 100 0 {connection:[I;3,3,3,3,2,1]}'
  #30 s covers two full machine operations plus network latency under startup chunk-load load.
  sleep 30
  echo "data get block 0 100 0"
  echo "data get block 3 100 0"
  echo "data get block 9 100 0"
  echo "data get block 12 100 0"
  echo "data get block 14 100 0"
  echo "data get block 20 100 0"
  echo "data get block 22 100 0"
  echo "data get block 24 100 0"
  echo "data get block 26 100 0"
  echo "data get block 28 100 0"
  echo "data get block 30 100 0"
  echo "data get block 32 100 0"
  echo "data get block 34 100 0"
  echo "data get block 36 100 0"
  echo "data get block 38 100 0"
  echo "data get block 33 100 0"
  echo "data get block 37 100 0"
  echo "data get block 21 100 0"
  sleep 3
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
check "server play channel registered all 25 payloads" 'MEKANISM_FABRIC_SERVER_PLAY_PROOF payloads=25'
if grep -q 'Parsing error' "$LOG_FILE"; then
  printf 'FAIL no recipe parsing errors\n'
  failures=$((failures + 1))
else
  printf 'PASS no recipe parsing errors\n'
fi
check "enrichment chamber produced iron dust" '0, 100, 0 has the following block data.*mekanism:dust_iron'
check "energized smelter produced an iron ingot" '3, 100, 0 has the following block data.*minecraft:iron_ingot'
check "cable-powered chamber produced iron dust" '12, 100, 0 has the following block data.*mekanism:dust_iron'
check "logistical transporter moved the item stack" '22, 100, 0 has the following block data.*minecraft:iron_ore'
check "source chest was emptied" '20, 100, 0 has the following block data.*Items: \[\]'
check "mechanical pipe moved the fluid" '26, 100, 0 has the following block data.*fluid_tanks.*minecraft:water'
if grep -Eq '24, 100, 0 has the following block data.*stored: ' "$LOG_FILE"; then
  printf 'FAIL fluid source tank was emptied\n'
  failures=$((failures + 1))
else
  printf 'PASS fluid source tank was emptied\n'
fi
check "pressurized tube moved the chemical" '30, 100, 0 has the following block data.*chemical_tanks.*mekanism:oxygen'
check "transporter fed the machine input" '34, 100, 0 has the following block data.*mekanism:dust_iron'
check "pipe fed the machine fluid input" '38, 100, 0 has the following block data.*chemical_tanks.*mekanism:hydrogen'
if grep -Eq '28, 100, 0 has the following block data.*stored: ' "$LOG_FILE"; then
  printf 'FAIL chemical source tank was emptied\n'
  failures=$((failures + 1))
else
  printf 'PASS chemical source tank was emptied\n'
fi
if grep -Eq '14, 100, 0 has the following block data.*stored: ' "$LOG_FILE" \
      && ! grep -Eq '14, 100, 0 has the following block data.*stored: 100000L' "$LOG_FILE"; then
  printf 'PASS energy cube discharged into the cable network\n'
else
  printf 'FAIL energy cube discharged into the cable network\n'
  failures=$((failures + 1))
fi
if grep -q 'Encountered an unexpected exception' "$LOG_FILE"; then
  printf 'FAIL no uncaught server exception\n'
  failures=$((failures + 1))
else
  printf 'PASS no uncaught server exception\n'
fi
if compgen -G "$SERVER_DIR/crash-reports/*.txt" > /dev/null; then
  printf 'FAIL no crash report\n'
  failures=$((failures + 1))
else
  printf 'PASS no crash report\n'
fi

printf 'fabric-machine-smoke: log: %s\n' "$LOG_FILE"
if (( failures > 0 )); then
  echo "fabric-machine-smoke: FAILED ($failures checks)" >&2
  exit 1
fi
echo "fabric-machine-smoke: PASSED"
