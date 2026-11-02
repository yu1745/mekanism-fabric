#!/usr/bin/env bash
# Save round-trip for the ore chains and a factory's sorting state.
#
# Phase one builds the chain the way a player would leave it - configured sides, a filled chemical tank, a factory with
# sorting switched on and a machine mid-recipe - then saves and stops the server cleanly. Phase two starts the same world
# again and dumps every block, and the script compares the two dumps field by field. Anything that survives only in
# memory shows up as a difference.
#
# Prerequisite: a remapped release jar (`./build.sh`). Requires only a JRE.
set -euo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
PROPERTIES="$ROOT/fabric/gradle.properties"
CACHE_DIR=${FABRIC_SMOKE_CACHE_DIR:-${XDG_CACHE_HOME:-$HOME/.cache}/mekanism-fabric-smoke}
KEEP_SERVER=${FABRIC_SMOKE_KEEP_SERVER:-0}

SERVER_DIR=
LOG_FILE=

property() {
  local key=$1 value
  value=$(awk -F= -v key="$key" '$1 == key { sub(/^[^=]*=/, ""); print; exit }' "$PROPERTIES")
  if [[ -z "$value" ]]; then
    echo "fabric-save-roundtrip-smoke: missing $key in $PROPERTIES" >&2
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
      echo "fabric-save-roundtrip-smoke: residual process group $SERVER_PID" >&2
      status=1
    fi
  fi
  if [[ -n "$SERVER_DIR" && -d "$SERVER_DIR" ]]; then
    if [[ "$KEEP_SERVER" == 1 || "$status" != 0 ]]; then
      printf 'fabric-save-roundtrip-smoke: retained server: %s\n' "$SERVER_DIR" >&2
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
  printf 'fabric-save-roundtrip-smoke: downloading %s\n' "$url" >&2
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
  printf 'fabric-save-roundtrip-smoke: expected four release jars, found %d\n' "${#MOD_JARS[@]}" >&2
  exit 2
fi

SERVER_DIR=$(mktemp -d "${TMPDIR:-/tmp}/mekanism-save-roundtrip.XXXXXX")
LOG_FILE="$SERVER_DIR/server.log"
mkdir -p "$SERVER_DIR/mods"
cp "$LAUNCHER_JAR" "$SERVER_DIR/fabric-server-launch.jar"
cp "$FABRIC_API_JAR" "$ENERGY_JAR" "${MOD_JARS[@]}" "$SERVER_DIR/mods/"
printf 'eula=true\n' > "$SERVER_DIR/eula.txt"
printf 'online-mode=false\nserver-port=0\nspawn-protection=0\nmax-players=1\nview-distance=4\n' > "$SERVER_DIR/server.properties"

mkfifo "$SERVER_DIR/console"
command() { printf '%s\n' "$1" >&9; }
wait_line() {
  "$ROOT/scripts/wait-for-log-line.sh" -t 60 -p "$SERVER_PID" \
    --fail-on 'Encountered an unexpected exception|FAILED TO BIND|Exception in thread|Stack size changed by a different amount|Unknown block type' "$@" "$LOG_FILE"
}
run_phase() {
  local phase=$1 commands=$2 line sprints=0
  LOG_FILE="$SERVER_DIR/phase-$phase.log"
  exec 9<>"$SERVER_DIR/console"
  (cd "$SERVER_DIR" && exec setsid java -Xms512M -Xmx2G -jar fabric-server-launch.jar nogui < console > "$LOG_FILE" 2>&1) &
  SERVER_PID=$!
  wait_line 'Done \('
  command 'tick freeze'
  command 'forceload add 48 0'
  command 'forceload add 64 0'
  #Advance chunk activation before placing or reading the four saved machines.
  command 'tick unfreeze'
  command 'tick sprint 20t'
  sprints=1
  wait_line -q -n "$sprints" 'Sprint completed with'
  command 'tick freeze'
  while IFS= read -r line; do
    [[ -z "$line" ]] && continue
    if [[ "$line" == 'tick sprint '* ]]; then
      command 'tick unfreeze'
      command "$line"
      sprints=$((sprints + 1))
      wait_line -q -n "$sprints" 'Sprint completed with'
      command 'tick freeze'
    else
      command "$line"
    fi
  done <<<"$commands"
  command "say ROUNDTRIP_${phase}_COMPLETE"
  wait_line "\\[Server\\] ROUNDTRIP_${phase}_COMPLETE"
  command stop
  wait "$SERVER_PID"
  if ps -eo pgid= | awk -v group="$SERVER_PID" '$1 == group { found=1 } END { exit !found }'; then
    echo "fabric-save-roundtrip-smoke: residual process group $SERVER_PID after shutdown" >&2
    exit 1
  fi
  SERVER_PID=
  exec 9>&-
}

#Phase one leaves the world in a state worth comparing: sides configured, a tank with a known amount, a factory with
#sorting on, and a machine that has started a recipe.
PHASE_ONE=$(cat <<'EOF'
setblock 58 100 0 mekanism:chemical_injection_chamber
data merge block 58 100 0 {energy_containers:[{container:0b,stored:40000L}], items:[{slot:0b,item:{id:"mekanism:crystal_iron",count:4}}], chemical_tanks:[{tank:0b,stored:{id:"mekanism:hydrogen_chloride",amount:210}}], component_config:{config2:[I;1,1,1,1,1,1], config3:[I;7,7,7,7,7,7]}}
setblock 60 100 0 mekanism:basic_chemical_tank
data merge block 60 100 0 {chemical_tanks:[{tank:0b,stored:{id:"mekanism:hydrogen_chloride",amount:2000}}], component_config:{config2:[I;7,7,7,7,7,7]}}
setblock 62 100 0 mekanism:electrolytic_separator
data merge block 62 100 0 {energy_containers:[{container:0b,stored:40000L}], component_config:{config3:[I;7,7,7,7,7,7]}}
setblock 64 100 0 mekanism:elite_smelting_factory
data merge block 64 100 0 {energy_containers:[{container:0b,stored:40000L}], items:[{slot:0b,item:{id:"mekanism:dirty_dust_iron",count:8}}], sorting:1b, component_config:{config0:[I;1,1,1,1,1,1], config3:[I;7,7,7,7,7,7]}}
tick sprint 120
data get block 58 100 0
data get block 60 100 0
data get block 62 100 0
data get block 64 100 0
save-all flush
EOF
)

PHASE_TWO=$(cat <<'EOF'
data get block 58 100 0
data get block 60 100 0
data get block 62 100 0
data get block 64 100 0
EOF
)

run_phase one "$PHASE_ONE"
run_phase two "$PHASE_TWO"

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

compare() {
  #Configuration has to come back exactly. Consumables legitimately change while the server is running - a machine eats
  #its input and the injector drinks its HCl - so for those the check is that the contents came back at all and still
  #hold the same item and chemical, not that the amounts match.
  python3 - "$SERVER_DIR/phase-one.log" "$SERVER_DIR/phase-two.log" <<'PY'
import re, sys

POSITIONS = ("58, 100, 0", "60, 100, 0", "62, 100, 0", "64, 100, 0")

def dumps(path):
    text = open(path, encoding="utf-8", errors="replace").read()
    result = {}
    for position in POSITIONS:
        found = re.findall(re.escape(position) + r" has the following block data: (\{.*)", text)
        if found:
            result[position] = found[-1]
    return result

before, after = dumps(sys.argv[1]), dumps(sys.argv[2])
missing = [p for p in POSITIONS if p not in before or p not in after]
if missing:
    print("missing_dump " + ",".join(missing))
    raise SystemExit(0)

def field(blob, key):
    match = re.search(r"(?:^|[,{]\s*)" + re.escape(key) + r": ", blob)
    if not match:
        return None
    start = match.end()
    depth = 0
    quoted = escaped = False
    for offset in range(start, len(blob)):
        char = blob[offset]
        if quoted:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == '"':
                quoted = False
        elif char == '"':
            quoted = True
        elif char in "[{":
            depth += 1
        elif char in "]}":
            if depth == 0:
                return blob[start:offset].strip()
            depth -= 1
        elif char == "," and depth == 0:
            return blob[start:offset].strip()
    return blob[start:].strip()

def ids(blob, prefix):
    return sorted(set(re.findall(r'id: "([a-z0-9_:]+)"', blob))) if prefix else []

problems = []
for position in POSITIONS:
    old, new = before[position], after[position]
    for key in ("component_config", "sorting"):
        if field(old, key) != field(new, key):
            problems.append(f"{position}:{key}")
    for key in ("items", "chemical_tanks"):
        if (field(old, key) is None) != (field(new, key) is None):
            problems.append(f"{position}:{key}-presence")
        elif field(old, key) is not None and ids(field(old, key), True) != ids(field(new, key), True):
            problems.append(f"{position}:{key}-contents")
print("ok" if not problems else "changed " + ",".join(problems))
PY
}

result=$(compare)
printf 'fabric-save-roundtrip-smoke: %s\n' "$result"
check "every machine came back after the restart" $([[ "$result" == missing_dump* ]] && echo 1 || echo 0)
check "side configs, tank contents, items and sorting survived" $([[ "$result" == "ok" ]] && echo 0 || echo 1)
if (( failures > 0 )); then
  echo "fabric-save-roundtrip-smoke: FAILED ($failures checks)" >&2
  exit 1
fi
echo "fabric-save-roundtrip-smoke: PASSED"
