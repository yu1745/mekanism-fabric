#!/usr/bin/env bash
# solar generator (fixed bottom output) -> energy cube -> cable -> smelter.
# The generator chain starts empty; the separate seeded cube chain retains the existing storage-output check.
# Uses four release jars in a disposable dedicated server. No third-party runtime is required.
set -euo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
PROPERTIES="$ROOT/fabric/gradle.properties"
CACHE_DIR=${FABRIC_SMOKE_CACHE_DIR:-${XDG_CACHE_HOME:-$HOME/.cache}/mekanism-fabric-smoke}
TIMEOUT=${FABRIC_ENERGY_CHAIN_TIMEOUT:-240}
KEEP_SERVER=${FABRIC_SMOKE_KEEP_SERVER:-0}
SERVER_DIR=
LOG_FILE=

property() {
  local key=$1 value
  value=$(awk -F= -v key="$key" '$1 == key { sub(/^[^=]*=/, ""); print; exit }' "$PROPERTIES")
  if [[ -z "$value" ]]; then
    echo "fabric-energy-chain-smoke: missing $key in $PROPERTIES" >&2
    exit 2
  fi
  printf '%s\n' "$value"
}

MINECRAFT_VERSION=$(property minecraft_version)
LOADER_VERSION=$(property loader_version)
FABRIC_API_VERSION=$(property fabric_api_version)
TEAM_REBORN_ENERGY_VERSION=$(property team_reborn_energy_version)
#Pinned third-party test-runtime mods; they never become release inputs.
INSTALLER_VERSION=${FABRIC_INSTALLER_VERSION:-1.1.2}

SERVER_PID=
cleanup() {
  local status=$?
  trap - EXIT INT TERM
  if [[ -n "$SERVER_PID" ]]; then
    kill -TERM -- "-$SERVER_PID" 2>/dev/null || true
    if ! timeout 2 tail --pid="$SERVER_PID" -f /dev/null; then
      kill -KILL -- "-$SERVER_PID" 2>/dev/null || true
    fi
    wait "$SERVER_PID" 2>/dev/null || true
    if ps -eo pgid= | awk -v group="$SERVER_PID" '$1 == group { found=1 } END { exit !found }'; then
      echo "fabric-energy-chain-smoke: residual process group $SERVER_PID" >&2
      status=1
    fi
  fi
  if [[ -n "$SERVER_DIR" && -d "$SERVER_DIR" && "$KEEP_SERVER" != 1 ]]; then
    rm -rf "$SERVER_DIR"
  elif [[ -n "$SERVER_DIR" ]]; then
    printf 'fabric-energy-chain-smoke: retained server: %s\n' "$SERVER_DIR" >&2
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
  printf 'fabric-energy-chain-smoke: downloading %s\n' "$url" >&2
  if ! curl --fail --location --retry 3 --retry-all-errors --connect-timeout 15 \
      --max-time 300 --output "$temporary" "$url"; then
    rm -f "$temporary"
    echo "fabric-energy-chain-smoke: download failed: $url" >&2
    exit 2
  fi
  mv "$temporary" "$destination"
}

# Use the four artifacts from the unified release build.
MOD_JARS=()
while IFS= read -r jar; do MOD_JARS+=("$jar"); done < <(find "$ROOT/dist" -maxdepth 1 -name '*.jar' | sort)
if [[ ${#MOD_JARS[@]} -ne 4 ]]; then
  echo "fabric-energy-chain-smoke: expected four release jars, found ${#MOD_JARS[@]}" >&2
  exit 2
fi

mkdir -p "$CACHE_DIR"
LAUNCHER_JAR="$CACHE_DIR/fabric-server-${MINECRAFT_VERSION}-${LOADER_VERSION}-${INSTALLER_VERSION}.jar"
FABRIC_API_JAR="$CACHE_DIR/fabric-api-${FABRIC_API_VERSION}.jar"
ENERGY_JAR="$CACHE_DIR/energy-${TEAM_REBORN_ENERGY_VERSION}.jar"
fetch "https://meta.fabricmc.net/v2/versions/loader/${MINECRAFT_VERSION}/${LOADER_VERSION}/${INSTALLER_VERSION}/server/jar" "$LAUNCHER_JAR"
fetch "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/${FABRIC_API_VERSION}/fabric-api-${FABRIC_API_VERSION}.jar" "$FABRIC_API_JAR"
fetch "https://maven.fabricmc.net/teamreborn/energy/${TEAM_REBORN_ENERGY_VERSION}/energy-${TEAM_REBORN_ENERGY_VERSION}.jar" "$ENERGY_JAR"


SERVER_DIR=$(mktemp -d "${TMPDIR:-/tmp}/mekanism-energy-chain-smoke.XXXXXX")
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
cp "$FABRIC_API_JAR" "$ENERGY_JAR" "${MOD_JARS[@]}" "$SERVER_DIR/mods/"
printf 'eula=true\n' > "$SERVER_DIR/eula.txt"
printf 'online-mode=false\nserver-port=0\nspawn-protection=0\nmax-players=1\nview-distance=4\nsync-chunk-writes=false\n' > "$SERVER_DIR/server.properties"
if [[ ${FABRIC_MACHINE_BLACKLIST_FE:-0} == 1 ]]; then
  mkdir -p "$SERVER_DIR/world/serverconfig/Mekanism"
  printf '[energy_conversion]\nblacklistForge = true\n' > "$SERVER_DIR/world/serverconfig/Mekanism/general.toml"
fi

mkfifo "$SERVER_DIR/console"
exec 9<>"$SERVER_DIR/console"
(cd "$SERVER_DIR" && exec setsid java -Xms512M -Xmx2G -jar fabric-server-launch.jar nogui < console > "$LOG_FILE" 2>&1) &
SERVER_PID=$!
wait_line() {
  "$ROOT/scripts/wait-for-log-line.sh" -t 60 -p "$SERVER_PID" \
    --fail-on 'Encountered an unexpected exception|FAILED TO BIND|Exception in thread' "$1" "$LOG_FILE"
}
command() { printf '%s\n' "$1" >&9; }
wait_line 'Done \('
command 'tick freeze'
command 'forceload add 0 0'
command 'forceload add 16 0'
command 'time set noon'
command 'weather clear'
command 'gamerule doDaylightCycle false'
command 'setblock 0 100 0 mekanism:basic_energy_cube'
command 'setblock 2 100 0 mekanism:energized_smelter'
command 'data merge block 2 100 0 {component_config:{config0:[I;1,1,1,1,1,1]}}'
command 'data merge block 0 100 0 {component_config:{eject0:1b,config0:[I;4,4,4,4,4,4]},energy_containers:[{container:0b,stored:100000L}]}'
command 'setblock 1 100 0 mekanism:basic_universal_cable'
command 'data merge block 1 100 0 {connection:[I;3,3,3,3,2,1]}'
# Solar output is intrinsically DOWN; it has no configurable side component. Put the empty storage below it.
command 'setblock 20 100 0 mekanism:basic_energy_cube'
command 'data merge block 20 100 0 {component_config:{eject0:1b,config0:[I;7,7,7,7,7,7]}}'
command 'setblock 22 100 0 mekanism:energized_smelter'
command 'data merge block 22 100 0 {component_config:{config0:[I;1,1,1,1,1,1]}}'
command 'setblock 21 100 0 mekanism:basic_universal_cable'
command 'data merge block 21 100 0 {connection:[I;3,3,3,3,2,1]}'
command 'setblock 20 101 0 mekanismgenerators:solar_generator'
command 'tick sprint 800t'
wait_line 'Sprint completed with'
for position in '0 100 0' '2 100 0' '20 100 0' '20 101 0' '22 100 0'; do
  command "data get block $position"
done
command 'say ENERGY_CHAIN_COMPLETE'
wait_line '\[Server\] ENERGY_CHAIN_COMPLETE'
command stop
wait "$SERVER_PID"
if ps -eo pgid= | awk -v group="$SERVER_PID" '$1 == group { found=1 } END { exit !found }'; then
  echo "smoke: residual process group $SERVER_PID after shutdown" >&2
  exit 1
fi
SERVER_PID=
exec 9>&-

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
check "the energy cube still holds a charge after feeding the cable" 'INFO\]: 0, 100, 0 has the following block data.*energy_containers.*stored: [1-9]'
check "the smelter was charged through the cable" 'INFO\]: 2, 100, 0 has the following block data.*energy_containers.*stored: [1-9]'
check "the initially empty cube received solar energy from above" 'INFO\]: 20, 100, 0 has the following block data.*energy_containers.*stored: [1-9]'
check "the unseeded smelter received solar energy via cube and cable" 'INFO\]: 22, 100, 0 has the following block data.*energy_containers.*stored: [1-9]'
if (( failures > 0 )); then
  echo "fabric-energy-chain-smoke: FAILED ($failures checks)" >&2
  exit 1
fi
echo "fabric-energy-chain-smoke: PASSED"
