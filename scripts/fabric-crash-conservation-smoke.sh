#!/usr/bin/env bash
# Item conservation across a hard crash.
#
# Moves a known number of stacks between two chests through a logistical transporter, kills the server with SIGKILL in
# the middle of the transfer, restarts it on the same world and counts both chests plus in-flight stacks.
# All three blocks are in one chunk. This is a transporter check, not a QIO or cross-chunk crash guarantee.
#
# Prerequisite: four release jars (`./build.sh`). For the same rig on the official cached runtime,
# set FABRIC_CONSERVATION_REFERENCE_SERVER to its server directory; its sample world is never used.
set -euo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
PROPERTIES="$ROOT/fabric/gradle.properties"
CACHE_DIR=${FABRIC_SMOKE_CACHE_DIR:-${XDG_CACHE_HOME:-$HOME/.cache}/mekanism-fabric-smoke}
TIMEOUT=${FABRIC_CONSERVATION_TIMEOUT:-60}
KEEP_SERVER=${FABRIC_SMOKE_KEEP_SERVER:-0}
REFERENCE_SERVER=${FABRIC_CONSERVATION_REFERENCE_SERVER:-}
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
      echo "fabric-conservation-smoke: residual process group $SERVER_PID" >&2
      status=1
    fi
  fi
  if [[ -n "$SERVER_DIR" && -d "$SERVER_DIR" ]]; then
    if [[ "$KEEP_SERVER" == 1 || "$status" != 0 ]]; then
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

if [[ -z "$REFERENCE_SERVER" ]]; then
mkdir -p "$CACHE_DIR"
LAUNCHER_JAR="$CACHE_DIR/fabric-server-launch.jar"
FABRIC_API_JAR="$CACHE_DIR/fabric-api-${FABRIC_API_VERSION}.jar"
ENERGY_JAR="$CACHE_DIR/energy-${TEAM_REBORN_ENERGY_VERSION}.jar"
fetch "https://meta.fabricmc.net/v2/versions/loader/${MINECRAFT_VERSION}/${LOADER_VERSION}/${INSTALLER_VERSION}/server/jar" "$LAUNCHER_JAR"
fetch "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/${FABRIC_API_VERSION}/fabric-api-${FABRIC_API_VERSION}.jar" "$FABRIC_API_JAR"
fetch "https://maven.fabricmc.net/teamreborn/energy/${TEAM_REBORN_ENERGY_VERSION}/energy-${TEAM_REBORN_ENERGY_VERSION}.jar" "$ENERGY_JAR"

MOD_JARS=()
while IFS= read -r jar; do MOD_JARS+=("$jar"); done < <(printf '%s\n' "$ROOT"/dist/*.jar | sort)
if [[ ${#MOD_JARS[@]} -ne 4 ]]; then
  printf 'fabric-conservation-smoke: expected four release jars, found %d\n' "${#MOD_JARS[@]}" >&2
  exit 2
fi

fi

setup_server_dir() {
  SERVER_DIR=$(mktemp -d "${TMPDIR:-/tmp}/mekanism-conservation-smoke.XXXXXX")
  LOG_FILE="$SERVER_DIR/server.log"
  mkdir -p "$SERVER_DIR/mods"
  if [[ -z "$REFERENCE_SERVER" ]]; then
    cp "$LAUNCHER_JAR" "$SERVER_DIR/fabric-server-launch.jar"
    cp "$FABRIC_API_JAR" "$ENERGY_JAR" "${MOD_JARS[@]}" "$SERVER_DIR/mods/"
  else
    # Only copy runtime files; never alter the resident official sample world or its configuration.
    cp -a --reflink=auto "$REFERENCE_SERVER/libraries" "$REFERENCE_SERVER/run.sh" \
      "$REFERENCE_SERVER/user_jvm_args.txt" "$SERVER_DIR/"
    cp "$REFERENCE_SERVER/mods/Mekanism-1.21.1-10.7.19.85.jar" "$SERVER_DIR/mods/"
    printf '004dbc9f3106f4d192aeaa1ee1190dd16ec9ca8059ed3d093b80034f4c574f43  %s\n' \
      "$SERVER_DIR/mods/Mekanism-1.21.1-10.7.19.85.jar" | sha256sum -c -
  fi
  printf 'eula=true\n' > "$SERVER_DIR/eula.txt"
  printf 'online-mode=false\nserver-port=0\nspawn-protection=0\nmax-players=1\nview-distance=4\n' > "$SERVER_DIR/server.properties"
}

# Every launch has its own process group and the same world; no pipeline writer or timeout shell survives it.
start_server() {
  LOG_FILE="$SERVER_DIR/$1.log"
  : > "$LOG_FILE"
  rm -f "$SERVER_DIR/console"
  mkfifo "$SERVER_DIR/console"
  exec 9<>"$SERVER_DIR/console"
  local launch=(java -Xms512M -Xmx2G -jar fabric-server-launch.jar nogui)
  [[ -z "$REFERENCE_SERVER" ]] || launch=(java @user_jvm_args.txt @libraries/net/neoforged/neoforge/21.1.200/unix_args.txt nogui)
  (cd "$SERVER_DIR" && exec setsid "${launch[@]}" < console > "$LOG_FILE" 2>&1) &
  SERVER_PID=$!
  wait_line 'Done \('
}

wait_line() {
  "$ROOT/scripts/wait-for-log-line.sh" -t "$TIMEOUT" -p "$SERVER_PID" \
    --fail-on 'Encountered an unexpected exception|FAILED TO BIND|Exception in thread' "$1" "$LOG_FILE"
}

command() { printf '%s\n' "$1" >&9; }
barrier() { command "say $1"; wait_line "\[Server\] $1"; }
# Each sprint uses a fresh log segment to prevent matching a previous completion marker.
advance() {
  local label=$1 ticks=$2
  command "say BEGIN_$label"
  wait_line "\[Server\] BEGIN_$label"
  local line
  line=$(wc -l < "$LOG_FILE")
  command "tick unfreeze"
  command "tick sprint ${ticks}t"
  local segment="$SERVER_DIR/sprint-$label.log"
  # The helper watches the real JVM; tail follows only the new part of the existing log.
  tail -n +"$((line + 1))" -f --pid="$SERVER_PID" "$LOG_FILE" > "$segment" &
  local follower=$!
  local result=0
  "$ROOT/scripts/wait-for-log-line.sh" -t "$TIMEOUT" -p "$SERVER_PID" \
    --fail-on 'Encountered an unexpected exception' 'Sprint completed with' "$segment" || result=$?
  kill "$follower" 2>/dev/null || true
  wait "$follower" 2>/dev/null || true
  command "tick freeze"
  return "$result"
}

#Vanilla execute-if-loaded requires ENTITY_TICKING plus loaded entities, rather than mere data-get access.
#Chunk IO/activation is asynchronous even when a very short sprint has already completed.
await_ticking_chunk() {
  local phase=$1 attempt
  for attempt in {1..20}; do
    command "execute unless loaded 1 100 0 run say WAITING_CHUNK_${phase}_$attempt"
    command "execute if loaded 1 100 0 run say TICKING_CHUNK_$phase"
    barrier "CHUNK_CHECK_${phase}_$attempt"
    if grep -q "\\[Server\\] TICKING_CHUNK_$phase" "$LOG_FILE"; then
      return
    fi
    advance "activation_${phase}_$attempt" 20
  done
  echo 'fabric-conservation-smoke: chunk did not reach ENTITY_TICKING' >&2
  return 1
}

snapshot() {
  command 'data get block 1 100 0'
  command 'data get block 2 100 0'
  command 'data get block 3 100 0'
  barrier "$1"
}

count_snapshot() {
  python3 - "$LOG_FILE" "$ITEM_COUNT" <<'PYCOUNT'
import json, re, sys
lines = open(sys.argv[1], encoding='utf-8', errors='replace').read().splitlines()
counts = []
for x in (1, 2, 3):
    marker = f'{x}, 100, 0 has the following block data: '
    dumps = [line.split(marker, 1)[1] for line in lines if marker in line]
    if not dumps:
        raise SystemExit(f'missing block dump: {marker}')
    # Each block dump occupies one log line. Never consume the following block dump.
    # Chest and transported stacks both serialize their vanilla ItemStack in a leaf compound.
    entries = re.findall(r'\{[^{}]*\}', dumps[-1])
    total = 0
    for entry in entries:
        if re.search(r'id: "minecraft:iron_ore"', entry):
            count = re.search(r'count: (\d+)', entry)
            if count is None:
                raise SystemExit(f'unreadable iron stack: {entry}')
            total += int(count.group(1))
    counts.append(total)
expected = int(sys.argv[2]) * 27
print(json.dumps(dict(source=counts[0], transit=counts[1], destination=counts[2], total=sum(counts), expected=expected)))
if sum(counts) != expected:
    raise SystemExit('item conservation failed')
if counts[2] <= 0:
    raise SystemExit('no items reached destination')
PYCOUNT
}

setup_server_dir
start_server before-crash
command 'tick freeze'
command 'forceload add 0 0'
await_ticking_chunk initial
command 'setblock 1 100 0 minecraft:chest'
command 'setblock 2 100 0 mekanism:basic_logistical_transporter'
command 'setblock 3 100 0 minecraft:chest'
for slot in $(seq 0 26); do
  command "item replace block 1 100 0 container.$slot with minecraft:iron_ore $ITEM_COUNT"
done
command 'data merge block 2 100 0 {connection:[I;3,3,3,3,2,1]}'
advance initial 201
snapshot CONSERVATION_BEFORE_SAVE
if ! before=$(count_snapshot); then
  printf 'FAIL before save: %s\n' "$before" >&2
  exit 1
fi
printf 'PASS before save: %s\n' "$before"
# Save an actual running transport network, including its in-flight stacks, then run further without saving.
command 'save-all flush'
barrier CONSERVATION_SAVED
advance unsaved 200
snapshot CONSERVATION_BEFORE_CRASH
count_snapshot
kill -KILL -- "-$SERVER_PID"
wait "$SERVER_PID" 2>/dev/null || true
SERVER_PID=
exec 9>&-

start_server after-crash
command 'tick freeze'
snapshot CONSERVATION_RELOADED
reloaded=$(count_snapshot)
printf 'PASS after hard crash: %s\n' "$reloaded"
await_ticking_chunk resumed
advance resumed 201
snapshot CONSERVATION_RESUMED
resumed=$(count_snapshot)
python3 - "$reloaded" "$resumed" <<'PYCOMPARE'
import json, sys
before, after = map(json.loads, sys.argv[1:])
assert after['destination'] > before['destination'], (before, after)
PYCOMPARE
printf 'PASS resumed transfer: %s\n' "$resumed"
command stop
wait "$SERVER_PID"
if ps -eo pgid= | awk -v group="$SERVER_PID" '$1 == group { found=1 } END { exit !found }'; then
  echo "smoke: residual process group $SERVER_PID after shutdown" >&2
  exit 1
fi
SERVER_PID=
exec 9>&-
echo 'fabric-conservation-smoke: PASSED'
