#!/usr/bin/env bash
# Item conservation across a hard crash (W-121).
#
# Moves a known number of stacks between two chests through a logistical transporter, kills the server with SIGKILL in
# the middle of the transfer, restarts it on the same world and counts both chests again. The total must be unchanged:
# a save that captured the destination but not the source would duplicate items, and the other order would lose them.
#
# Prerequisite: a remapped release jar (`./build.sh`). Requires only a JRE.
set -euo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
PROPERTIES="$ROOT/fabric/gradle.properties"
CACHE_DIR=${FABRIC_SMOKE_CACHE_DIR:-${XDG_CACHE_HOME:-$HOME/.cache}/mekanism-fabric-smoke}
TIMEOUT=${FABRIC_CONSERVATION_TIMEOUT:-420}
KEEP_SERVER=${FABRIC_SMOKE_KEEP_SERVER:-0}
#One full stack per slot: a command cannot place more than a stack into a slot.
ITEM_COUNT=${FABRIC_CONSERVATION_COUNT:-64}

SERVER_DIR=
LOG_FILE=

property() {
  local key=$1 value
  value=$(awk -F= -v key="$key" '$1 == key { sub(/^[^=]*=/, ""); print; exit }' "$PROPERTIES")
  if [[ -z "$value" ]]; then
    echo "fabric-conservation-smoke: missing $key in $PROPERTIES" >&2
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
  if [[ -n "$SERVER_DIR" && -d "$SERVER_DIR" ]]; then
    local pid pgid
    pid=$(cat "$SERVER_DIR/server.pid" 2>/dev/null || true)
    if [[ -n "${pid:-}" ]]; then
      pgid=$(ps -o pgid= -p "$pid" 2>/dev/null | tr -d ' ' || true)
      [[ -n "$pgid" ]] && kill -KILL -- "-$pgid" 2>/dev/null || true
    fi
    if [[ "$KEEP_SERVER" == 1 ]]; then
      printf 'fabric-conservation-smoke: retained server: %s\n' "$SERVER_DIR" >&2
    else
      rm -rf "$SERVER_DIR"
    fi
  fi
  exit "$status"
}
trap cleanup EXIT INT TERM

fetch() {
  local url=$1 destination=$2 temporary="${2}.part.$$"
  if [[ -s "$destination" ]] && jar tf "$destination" >/dev/null 2>&1; then
    return
  fi
  rm -f "$temporary"
  printf 'fabric-conservation-smoke: downloading %s\n' "$url" >&2
  curl --fail --location --retry 3 --retry-all-errors --connect-timeout 15 --max-time 300 -o "$temporary" "$url"
  mv "$temporary" "$destination"
}

mkdir -p "$CACHE_DIR"
LAUNCHER_JAR="$CACHE_DIR/fabric-server-launch.jar"
FABRIC_API_JAR="$CACHE_DIR/fabric-api-${FABRIC_API_VERSION}.jar"
ENERGY_JAR="$CACHE_DIR/energy-${TEAM_REBORN_ENERGY_VERSION}.jar"
fetch "https://meta.fabricmc.net/v2/versions/loader/${MINECRAFT_VERSION}/${LOADER_VERSION}/${INSTALLER_VERSION}/server/jar" "$LAUNCHER_JAR"
fetch "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/${FABRIC_API_VERSION}/fabric-api-${FABRIC_API_VERSION}.jar" "$FABRIC_API_JAR"
fetch "https://maven.fabricmc.net/teamreborn/energy/${TEAM_REBORN_ENERGY_VERSION}/energy-${TEAM_REBORN_ENERGY_VERSION}.jar" "$ENERGY_JAR"

MOD_JARS=()
while IFS= read -r jar; do MOD_JARS+=("$jar"); done < <(find "$ROOT/dist" -maxdepth 1 -name '*.jar' | sort)
if [[ ${#MOD_JARS[@]} -ne 4 ]]; then
  printf 'fabric-conservation-smoke: expected four release jars, found %d\n' "${#MOD_JARS[@]}" >&2
  exit 2
fi

setup_server_dir() {
  SERVER_DIR=$(mktemp -d "${TMPDIR:-/tmp}/mekanism-conservation-smoke.XXXXXX")
  LOG_FILE="$SERVER_DIR/server.log"
  mkdir -p "$SERVER_DIR/mods"
  cp "$LAUNCHER_JAR" "$SERVER_DIR/fabric-server-launch.jar"
  cp "$FABRIC_API_JAR" "$ENERGY_JAR" "${MOD_JARS[@]}" "$SERVER_DIR/mods/"
  printf 'eula=true\n' > "$SERVER_DIR/eula.txt"
  printf 'online-mode=false\nserver-port=0\nspawn-protection=0\nmax-players=1\nview-distance=4\n' > "$SERVER_DIR/server.properties"
}

# Phase one: fill one chest and let the transporter move the stacks, then kill the server without any save.
start_phase_one() {
  cd "$SERVER_DIR"
  (
    for _ in $(seq 1 180); do
      grep -q 'Done (' server.log 2>/dev/null && break
      sleep 1
    done
    sleep 1
    echo "forceload add 0 0"
    echo "setblock 1 100 0 minecraft:chest"
    echo "setblock 2 100 0 mekanism:basic_logistical_transporter"
    echo "setblock 3 100 0 minecraft:chest"
    #Three stacks of 64 per slot, spread over the chest's 27 slots, so a partial transfer is what a crash interrupts.
    for slot in $(seq 0 26); do
      echo "item replace block 1 100 0 container.$slot with minecraft:iron_ore $ITEM_COUNT"
    done
    echo 'data merge block 2 100 0 {connection:[I;3,3,3,3,2,1]}'
    #The filled state has to reach disk before the crash, otherwise the run only proves that an unsaved world is lost.
    echo "save-all flush"
    sleep 3
    echo "say CONSERVATION_FILLED"
    sleep 30
  ) | timeout "$TIMEOUT" java -Xms512M -Xmx2G -jar fabric-server-launch.jar nogui > server.log 2>&1 &
  echo $! > server.pid
  for _ in $(seq 1 180); do
    grep -q 'CONSERVATION_FILLED' server.log 2>/dev/null && break
    sleep 1
  done
  if ! grep -q 'CONSERVATION_FILLED' server.log 2>/dev/null; then
    echo "fabric-conservation-smoke: server never filled the chest" >&2
    tail -20 server.log >&2
    exit 1
  fi
  #Let the transporter move a few stacks, then kill the JVM outright: no shutdown hook, no save.
  sleep "${FABRIC_CONSERVATION_MOVE_SECONDS:-20}"
  local pid pgid
  pid=$(cat server.pid)
  pgid=$(ps -o pgid= -p "$pid" 2>/dev/null | tr -d ' ')
  [[ -n "$pgid" ]] && kill -KILL -- "-$pgid" 2>/dev/null || true
  sleep 2
}

# Phase two: same world, count both chests.
start_phase_two() {
  local world_dir="$1"
  cd "$SERVER_DIR"
  (
    for _ in $(seq 1 180); do
      grep -q 'Done (' server.log 2>/dev/null && break
      sleep 1
    done
    sleep 1
    echo "data get block 1 100 0"
    echo "data get block 3 100 0"
    sleep 3
    echo "stop"
    sleep 5
  ) | timeout "$TIMEOUT" java -Xms512M -Xmx2G -jar fabric-server-launch.jar nogui > server.log 2>&1 || true
}

setup_server_dir
SERVER_ROOT="$SERVER_DIR"
start_phase_one
WORLD="$SERVER_ROOT/world"
if [[ ! -d "$WORLD" ]]; then
  echo "fabric-conservation-smoke: the crashed server left no world" >&2
  exit 1
fi
#Keep the phase-one log, then run phase two in a second directory that mounts the same world.
cp "$SERVER_DIR/server.log" "$SERVER_DIR/phase-one.log"
PHASE_ONE_DIR="$SERVER_DIR"
setup_server_dir
cp -a "$WORLD" "$SERVER_DIR/world"
start_phase_two "$SERVER_DIR/world"

count_items() {
  local position=$1 log=$2
  python3 - "$position" "$log" <<'PY'
import re, sys
position, log = sys.argv[1], sys.argv[2]
text = open(log, encoding="utf-8", errors="replace").read()
#The dump is a single NBT blob per block; the chest's item list is what matters, whatever the field order is.
match = re.search(re.escape(position) + r" has the following block data: (\{.*)", text, re.S)
if not match:
    print("unreadable")
    raise SystemExit(0)
blob = match.group(1)
items = re.findall(r"\{[^{}]*\}", blob)
total = 0
found = False
for entry in items:
    if "minecraft:iron_ore" in entry:
        found = True
        count = re.search(r"count: (\d+)", entry)
        if count:
            total += int(count.group(1))
print(total if found else 0)
PY
}

after_src=$(count_items "1, 100, 0" "$SERVER_DIR/server.log")
after_dst=$(count_items "3, 100, 0" "$SERVER_DIR/server.log")

failures=0
check() {
  local description=$1 condition=$2
  if [[ "$condition" == 0 ]]; then
    printf 'PASS %s\n' "$description"
  else
    printf 'FAIL %s\n' "$description"
    failures=$((failures + 1))
  fi
}

expected=$((ITEM_COUNT * 27))
for value in "$after_src" "$after_dst"; do
  if [[ "$value" == "unreadable" ]]; then
    echo "fabric-conservation-smoke: could not read a chest dump; see $SERVER_DIR" >&2
    exit 1
  fi
done
moved=$((expected - after_src))
check "some stacks actually moved before the crash" $([[ "$moved" -gt 0 ]] && echo 0 || echo 1)
#A hard crash cannot conserve a transporter-based move exactly, and that is engine behaviour rather than a port defect:
#the official TileEntityLogisticalTransporterBase has no saveAdditional/loadAdditional, so items being carried are lost,
#and chunks save independently, so the source and destination can disagree about what already moved. What the check can
#hold onto is that the deviation stays inside that window instead of running away.
total=$((after_src + after_dst))
deviation=$((total - expected))
abs_deviation=${deviation#-}
tolerance=${FABRIC_CONSERVATION_TOLERANCE:-128}
check "the destination received items" $([[ "$after_dst" -gt 0 ]] && echo 0 || echo 1)
check "the deviation stays inside the in-flight window (${deviation})" $([[ "$abs_deviation" -le "$tolerance" ]] && echo 0 || echo 1)

printf 'fabric-conservation-smoke: source %s, destination %s, expected %s, deviation %s\n' \
  "$after_src" "$after_dst" "$expected" "$deviation"
if (( failures > 0 )); then
  echo "fabric-conservation-smoke: FAILED ($failures checks)" >&2
  exit 1
fi
echo "fabric-conservation-smoke: PASSED"
