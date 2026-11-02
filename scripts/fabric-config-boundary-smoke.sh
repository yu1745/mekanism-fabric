#!/usr/bin/env bash
# Where a config change takes effect.
#
# The observable is the solar generator's generation rate, because it is a single number: the config value in joules per tick.
# The run measures the rate, edits the config file while the server keeps running to see whether the change is hot, and
# compares default, restarted and live-reloaded NONE values. Optional FABRIC_CONFIG_REFERENCE_SERVER runs the same rig on the fixed official runtime.
#
# Prerequisite: a remapped release jar (`./build.sh`). Requires only a JRE.
set -euo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
PROPERTIES="$ROOT/fabric/gradle.properties"
CACHE_DIR=${FABRIC_SMOKE_CACHE_DIR:-${XDG_CACHE_HOME:-$HOME/.cache}/mekanism-fabric-smoke}
TIMEOUT=${FABRIC_CONFIG_TIMEOUT:-300}
KEEP_SERVER=${FABRIC_SMOKE_KEEP_SERVER:-0}
SPRINT=${FABRIC_CONFIG_SPRINT:-100}
REFERENCE_SERVER=${FABRIC_CONFIG_REFERENCE_SERVER:-}
SERVER_PID=

SERVER_DIR=
LOG_FILE=

property() {
  local key=$1 value
  value=$(awk -F= -v key="$key" '$1 == key { sub(/^[^=]*=/, ""); print; exit }' "$PROPERTIES")
  if [[ -z "$value" ]]; then
    echo "fabric-config-boundary-smoke: missing $key in $PROPERTIES" >&2
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
  if [[ -n "$SERVER_PID" ]]; then
    kill -TERM -- "-$SERVER_PID" 2>/dev/null || true
    if ! timeout 2 tail --pid="$SERVER_PID" -f /dev/null; then
      kill -KILL -- "-$SERVER_PID" 2>/dev/null || true
    fi
    wait "$SERVER_PID" 2>/dev/null || true
    if ps -eo pgid= | awk -v group="$SERVER_PID" '$1 == group { found=1 } END { exit !found }'; then
      echo "fabric-config-boundary-smoke: residual process group $SERVER_PID" >&2
      status=1
    fi
  fi
  if [[ -n "$SERVER_DIR" && -d "$SERVER_DIR" ]]; then
    if [[ "$KEEP_SERVER" == 1 ]]; then
      printf 'fabric-config-boundary-smoke: retained server: %s\n' "$SERVER_DIR" >&2
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
  printf 'fabric-config-boundary-smoke: downloading %s\n' "$url" >&2
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
  printf 'fabric-config-boundary-smoke: expected four release jars, found %d\n' "${#MOD_JARS[@]}" >&2
  exit 2
fi

fi

SERVER_DIR=$(mktemp -d "${TMPDIR:-/tmp}/mekanism-config-boundary.XXXXXX")
LOG_FILE="$SERVER_DIR/server.log"
mkdir -p "$SERVER_DIR/mods"
if [[ -z "$REFERENCE_SERVER" ]]; then
  cp "$LAUNCHER_JAR" "$SERVER_DIR/fabric-server-launch.jar"
  cp "$FABRIC_API_JAR" "$ENERGY_JAR" "${MOD_JARS[@]}" "$SERVER_DIR/mods/"
else
  # Copy runtime only, never the resident reference sample world.
  cp -a --reflink=auto "$REFERENCE_SERVER/libraries" "$REFERENCE_SERVER/user_jvm_args.txt" "$SERVER_DIR/"
  cp "$REFERENCE_SERVER/../official/Mekanism-1.21.1-10.7.19.85.jar" \
    "$REFERENCE_SERVER/../official/MekanismGenerators-1.21.1-10.7.19.85.jar" "$SERVER_DIR/mods/"
fi
printf 'eula=true\n' > "$SERVER_DIR/eula.txt"
printf 'online-mode=false\nserver-port=0\nspawn-protection=0\nmax-players=1\nview-distance=4\nlevel-type=minecraft:flat\ngenerator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains","lakes":false,"features":false}\n' > "$SERVER_DIR/server.properties"

GENERATOR_CONFIG="$SERVER_DIR/world/serverconfig/Mekanism/generators.toml"
GLOBAL_GENERATOR_CONFIG="$SERVER_DIR/config/Mekanism/generators.toml"
# A schema-valid template value proves initialization used defaultconfigs, not merely built-in defaults.
mkdir -p "$SERVER_DIR/defaultconfigs/Mekanism"
printf '[bio_generator]\n    bioGeneration = 777\n' > "$SERVER_DIR/defaultconfigs/Mekanism/generators.toml"

set_generation() {
  local value=$1
  if [[ -f "$GENERATOR_CONFIG" ]]; then
    sed -i -E "s/^([[:space:]]*solarGeneration[[:space:]]*=[[:space:]]*)[0-9]+/\1$value/" "$GENERATOR_CONFIG"
    grep -q "solarGeneration = $value" "$GENERATOR_CONFIG" || printf '\n[solar_generator]\n    solarGeneration = %s\n' "$value" >> "$GENERATOR_CONFIG"
  else
    printf '[solar_generator]\n    solarGeneration = %s\n' "$value" > "$GENERATOR_CONFIG"
  fi
}

command() { printf '%s\n' "$1" >&9; }
wait_line() {
  local left=$((DEADLINE - SECONDS))
  (( left > 0 )) || { echo 'config boundary overall timeout' >&2; return 1; }
  "$ROOT/scripts/wait-for-log-line.sh" -t "$((left > 60 ? 60 : left))" -p "$SERVER_PID" \
    --fail-on 'Encountered an unexpected exception|FAILED TO BIND|Exception in thread' "$@"
}
barrier() { command "say $1"; wait_line "\[Server\] $1" "$LOG_FILE"; }
start_server() {
  LOG_FILE="$SERVER_DIR/$1.log"
  : > "$LOG_FILE"
  rm -f "$SERVER_DIR/console"
  mkfifo "$SERVER_DIR/console"
  exec 9<>"$SERVER_DIR/console"
  local launch=(java -Xms512M -Xmx2G -jar fabric-server-launch.jar nogui)
  [[ -z "$REFERENCE_SERVER" ]] || launch=(java -Xms512M -Xmx2G @user_jvm_args.txt @libraries/net/neoforged/neoforge/21.1.200/unix_args.txt nogui)
  (cd "$SERVER_DIR" && exec setsid "${launch[@]}" < console > "$LOG_FILE" 2>&1) &
  SERVER_PID=$!
  wait_line 'Done \(' "$LOG_FILE"
  command 'tick freeze'
  command 'forceload add 0 0'
  command 'time set noon'
  command 'weather clear'
  command 'gamerule doDaylightCycle false'
  command 'tick unfreeze'
  command 'tick sprint 20t'
  wait_line 'Sprint completed with' "$LOG_FILE"
  command 'tick freeze'
}
stop_server() {
  command stop
  wait "$SERVER_PID"
  if ps -eo pgid= | awk -v group="$SERVER_PID" '$1 == group { found=1 } END { exit !found }'; then
    echo "smoke: residual process group $SERVER_PID after shutdown" >&2
    exit 1
  fi
  SERVER_PID=
  exec 9>&-
}
measure() {
  local label=$1 previous
  previous=$(grep -c 'Sprint completed with' "$LOG_FILE" || true)
  command 'data merge block 0 100 0 {energy_containers:[{container:0b,stored:0L}]}'
  command 'tick unfreeze'
  command "tick sprint ${SPRINT}t"
  wait_line -n "$((previous + 1))" 'Sprint completed with' "$LOG_FILE"
  command 'tick freeze'
  command 'data get block 0 100 0'
  barrier "$label"
}

DEADLINE=$((SECONDS + TIMEOUT))
start_server phase-one
command 'setblock 0 100 0 mekanismgenerators:solar_generator'
measure CONFIG_DEFAULT_MEASURED
stop_server
[[ -f "$GLOBAL_GENERATOR_CONFIG" && ! -f "$GENERATOR_CONFIG" ]] || { echo 'default SERVER config did not select the global file' >&2; exit 1; }
grep -Eq 'bioGeneration[[:space:]]*=[[:space:]]*777' "$GLOBAL_GENERATOR_CONFIG" || { echo 'defaultconfigs template was not applied' >&2; exit 1; }
# Introduce an explicit per-file world override only between owner lifetimes. The global file must remain unchanged.
mkdir -p "$(dirname "$GENERATOR_CONFIG")"
cp "$GLOBAL_GENERATOR_CONFIG" "$GENERATOR_CONFIG"
set_generation 200
start_server phase-two
measure CONFIG_RESTART_MEASURED

reload_log="$LOG_FILE"
reload_pattern='MEKANISM_FABRIC_CONFIG_RELOAD file=.*generators.toml '
if [[ -n "$REFERENCE_SERVER" ]]; then
  reload_log="$SERVER_DIR/logs/debug.log"
  reload_pattern='Config file .*generators.toml changed, re-loading'
fi
previous=$(grep -c "$reload_pattern" "$reload_log" 2>/dev/null || true)
previous=${previous:-0}
set_generation 400
# FileWatcher debounce is real filesystem time; a tick sprint cannot accelerate it. Wait for its actual reload marker.
wait_line -n "$((previous + 1))" "$reload_pattern" "$reload_log"
measure CONFIG_HOT_RELOAD_MEASURED
stop_server
grep -Eq 'solarGeneration[[:space:]]*=[[:space:]]*50' "$GLOBAL_GENERATOR_CONFIG" || { echo 'world override overwrote the global config' >&2; exit 1; }
printf 'PASS global default, defaultconfigs template, per-file world override and global-file preservation\n'

rate_of() {
  #$1 log, $2 sprint ticks, $3 which dump to read counting back from the last one.
  python3 - "$1" "$2" "${3:-0}" <<'PY'
import re, sys
log, sprint, index = sys.argv[1], int(sys.argv[2]), int(sys.argv[3])
text = open(log, encoding="utf-8", errors="replace").read()
found = re.findall(r"0, 100, 0 has the following block data: .*?stored: (\d+)L", text)
if not found or len(found) <= index:
    print("unreadable")
    raise SystemExit(0)
print(round(int(found[-1 - index]) / sprint))
PY
}

default_rate=$(rate_of "$SERVER_DIR/phase-one.log" "$SPRINT")
edited_rate=$(rate_of "$SERVER_DIR/phase-two.log" "$SPRINT" 1)
hot_rate=$(rate_of "$SERVER_DIR/phase-two.log" "$SPRINT" 0)

failures=0
check() {
  local description=$1 condition=$2
  if [[ "$condition" == 0 ]]; then printf 'PASS %s\n' "$description"; else printf 'FAIL %s\n' "$description"; failures=$((failures + 1)); fi
}

printf 'fabric-config-boundary-smoke: default ~%s J/tick, after restart with solarGeneration=200 ~%s J/tick, after editing to 400 mid-run ~%s J/tick\n' \
  "$default_rate" "$edited_rate" "$hot_rate"
check "the default config produced a rate" $([[ "$default_rate" != "unreadable" && "$default_rate" -gt 0 ]] && echo 0 || echo 1)
check "an edited config takes effect after a restart" $([[ "$edited_rate" != "unreadable" && "$default_rate" != "unreadable" && "$edited_rate" -gt "$default_rate" ]] && echo 0 || echo 1)
check "editing NONE config while running updates the real generator" $([[ "$hot_rate" != "unreadable" && "$edited_rate" != "unreadable" ]] && # Some normal ticks pass between unfreeze and freeze; the 200->400 change must clearly exceed that noise.
[[ $(( hot_rate - edited_rate )) -gt 100 ]] && echo 0 || echo 1)

if (( failures > 0 )); then
  echo "fabric-config-boundary-smoke: FAILED ($failures checks)" >&2
  exit 1
fi
printf 'fabric-config-boundary-smoke: wall_seconds=%s requested_game_ticks=%s\n' "$SECONDS" "$((40 + 3 * SPRINT))"
echo "fabric-config-boundary-smoke: PASSED"
