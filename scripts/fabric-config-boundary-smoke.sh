#!/usr/bin/env bash
# Where a config change takes effect (W-124).
#
# The observable is the solar generator's generation rate, because it is a single number: the config value in FE per tick.
# The run measures the rate, edits the config file while the server keeps running to see whether the change is hot, and
# then restarts on the edited file to see whether it applies at startup.
#
# Prerequisite: a remapped release jar (`./build.sh`). Requires only a JRE.
set -euo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
PROPERTIES="$ROOT/fabric/gradle.properties"
CACHE_DIR=${FABRIC_SMOKE_CACHE_DIR:-${XDG_CACHE_HOME:-$HOME/.cache}/mekanism-fabric-smoke}
TIMEOUT=${FABRIC_CONFIG_TIMEOUT:-300}
KEEP_SERVER=${FABRIC_SMOKE_KEEP_SERVER:-0}
SPRINT=${FABRIC_CONFIG_SPRINT:-100}

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
  if [[ -n "$SERVER_DIR" && -d "$SERVER_DIR" ]]; then
    local pid pgid
    pid=$(cat "$SERVER_DIR/server.pid" 2>/dev/null || true)
    if [[ -n "${pid:-}" ]]; then
      pgid=$(ps -o pgid= -p "$pid" 2>/dev/null | tr -d ' ' || true)
      [[ -n "$pgid" ]] && kill -KILL -- "-$pgid" 2>/dev/null || true
    fi
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
  printf 'fabric-config-boundary-smoke: expected four release jars, found %d\n' "${#MOD_JARS[@]}" >&2
  exit 2
fi

SERVER_DIR=$(mktemp -d "${TMPDIR:-/tmp}/mekanism-config-boundary.XXXXXX")
LOG_FILE="$SERVER_DIR/server.log"
mkdir -p "$SERVER_DIR/mods"
cp "$LAUNCHER_JAR" "$SERVER_DIR/fabric-server-launch.jar"
cp "$FABRIC_API_JAR" "$ENERGY_JAR" "${MOD_JARS[@]}" "$SERVER_DIR/mods/"
printf 'eula=true\n' > "$SERVER_DIR/eula.txt"
printf 'online-mode=false\nserver-port=0\nspawn-protection=0\nmax-players=1\nview-distance=4\n' > "$SERVER_DIR/server.properties"

GENERATOR_CONFIG="$SERVER_DIR/world/serverconfig/Mekanism/generators.toml"

set_generation() {
  local value=$1
  if [[ -f "$GENERATOR_CONFIG" ]]; then
    sed -i -E "s/^([[:space:]]*solarGeneration[[:space:]]*=[[:space:]]*)[0-9]+/\1$value/" "$GENERATOR_CONFIG"
    grep -q "solarGeneration = $value" "$GENERATOR_CONFIG" || printf '\n[solar_generator]\n    solarGeneration = %s\n' "$value" >> "$GENERATOR_CONFIG"
  else
    printf '[solar_generator]\n    solarGeneration = %s\n' "$value" > "$GENERATOR_CONFIG"
  fi
}

measure() {
  #Reset the generator's buffer, sprint a fixed number of ticks and read what it produced. Everything is echoed into the
  #server's stdin, which is what the command pipeline reads.
  echo "data merge block 0 100 0 {energy_containers:[{container:0b,stored:0L}]}"
  echo "tick sprint $SPRINT"
  sleep 2
  echo "data get block 0 100 0"
}

#Phase one: let the server write its default config and measure the default rate.
run_phase_one() {
  cd "$SERVER_DIR"
  (
    for _ in $(seq 1 180); do grep -q 'Done (' server.log 2>/dev/null && break; sleep 1; done
    sleep 1
    echo "forceload add 0 0"
    echo "setblock 0 100 0 mekanismgenerators:solar_generator"
    sleep 2
    measure
    sleep 3
    echo "stop"
    sleep 6
  ) | timeout "$TIMEOUT" java -Xms512M -Xmx2G -jar fabric-server-launch.jar nogui > server.log 2>&1 &
  echo $! > server.pid
  wait $! 2>/dev/null || true
  cp server.log phase-one.log
  [[ -f "$GENERATOR_CONFIG" ]] || { echo "fabric-config-boundary-smoke: the server wrote no generators config" >&2; exit 1; }
}

#Phase two: run with the edited value, then edit it again mid-run to see whether it is picked up live.
run_phase_two() {
  cd "$SERVER_DIR"
  {
    for _ in $(seq 1 180); do grep -q 'Done (' server.log 2>/dev/null && break; sleep 1; done
    sleep 1
    echo "forceload add 0 0"
    measure
    sleep 2
    set_generation 400
    measure
    sleep 3
    echo "stop"
    sleep 6
  } | timeout "$TIMEOUT" java -Xms512M -Xmx2G -jar fabric-server-launch.jar nogui > server.log 2>&1 &
  echo $! > server.pid
  wait $! 2>/dev/null || true
  cp server.log phase-two.log
}

run_phase_one
set_generation 200
run_phase_two

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

printf 'fabric-config-boundary-smoke: default ~%s FE/tick, after restart with solarGeneration=200 ~%s FE/tick, after editing to 400 mid-run ~%s FE/tick\n' \
  "$default_rate" "$edited_rate" "$hot_rate"
check "the default config produced a rate" $([[ "$default_rate" != "unreadable" && "$default_rate" -gt 0 ]] && echo 0 || echo 1)
check "an edited config takes effect after a restart" $([[ "$edited_rate" != "unreadable" && "$default_rate" != "unreadable" && "$edited_rate" -gt "$default_rate" ]] && echo 0 || echo 1)
check "editing the config while running changes nothing (not hot)" $([[ "$hot_rate" != "unreadable" && "$edited_rate" != "unreadable" ]] && #The measurement carries roughly ten percent of noise from ticks outside the sprint, so the test is whether the run sits near the 200 rate or has jumped towards the 400 one.
[[ $(( hot_rate - edited_rate )) -lt 100 && $(( edited_rate - hot_rate )) -lt 100 ]] && echo 0 || echo 1)
if (( failures > 0 )); then
  echo "fabric-config-boundary-smoke: FAILED ($failures checks)" >&2
  exit 1
fi
echo "fabric-config-boundary-smoke: PASSED"
