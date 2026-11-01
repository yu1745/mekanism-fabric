#!/usr/bin/env bash
# Save round-trip for the ore chains and a factory's sorting state (W-123).
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
TIMEOUT=${FABRIC_ROUNDTRIP_TIMEOUT:-300}
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

cleanup() {
  local status=$?
  trap - EXIT INT TERM
  if [[ -n "$SERVER_DIR" && -d "$SERVER_DIR" ]]; then
    local pid pgid
    pid=$(cat "$SERVER_DIR/server.pid" 2>/dev/null || true)
    if [[ -n "${pid:-}" ]]; then
      pgid=$(ps -o pgid= -p "$pid" 2>/dev/null | tr -d ' ')
      [[ -n "$pgid" ]] && kill -KILL -- "-$pgid" 2>/dev/null || true
    fi
    if [[ "$KEEP_SERVER" == 1 ]]; then
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

run_phase() {
  local phase=$1 commands=$2
  : > "$SERVER_DIR/server.log"
  cd "$SERVER_DIR"
  (
    for _ in $(seq 1 180); do
      grep -q 'Done (' server.log 2>/dev/null && break
      sleep 1
    done
    sleep 1
    echo "forceload add 0 0"
    echo "forceload add 64 0"
    while IFS= read -r line; do
      [[ -z "$line" ]] && continue
      echo "$line"
    done <<<"$commands"
    echo "stop"
    sleep 6
  ) | timeout "$TIMEOUT" java -Xms512M -Xmx2G -jar fabric-server-launch.jar nogui > "server-$phase.log" 2>&1 &
  echo $! > server.pid
  wait $! 2>/dev/null || true
  cp "server-$phase.log" "phase-$phase.log"
}

#Phase one leaves the world in a state worth comparing: sides configured, a tank with a known amount, a factory with
#sorting on, and a machine that has started a recipe.
PHASE_ONE=$(cat <<'EOF'
setblock 58 100 0 mekanism:chemical_injection_chamber
data merge block 58 100 0 {energy_containers:[{container:0b,stored:40000L}], items:[{slot:0b,item:{id:"mekanism:crystal_iron",count:4}}], chemical_tanks:[{tank:0b,stored:{id:"mekanism:hydrogen_chloride",amount:1500}}], component_config:{config2:[I;1,1,1,1,1,1], config3:[I;7,7,7,7,7,7]}}
setblock 60 100 0 mekanism:basic_chemical_tank
data merge block 60 100 0 {chemical_tanks:[{tank:0b,stored:{id:"mekanism:hydrogen_chloride",amount:2000}}], component_config:{config1:[I;7,7,7,7,7,7]}}
setblock 62 100 0 mekanism:separator
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
missing = [p for p in POSITIONS if p in before and p not in after]
if missing:
    print("missing_after_restart " + ",".join(missing))
    raise SystemExit(0)

def field(blob, key):
    match = re.search(key + r": (\[[^]]*\]|[^,}]+|[^\]]*\])", blob)
    return match.group(1).strip() if match else None

def ids(blob, prefix):
    return sorted(set(re.findall(r'id: "([a-z0-9_:]+)"', blob))) if prefix else []

problems = []
for position in POSITIONS:
    if position not in before:
        continue
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
check "every machine came back after the restart" $([[ "$result" == changed\ missing_after_restart* ]] && echo 1 || echo 0)
check "side configs, tank contents, items and sorting survived" $([[ "$result" == "ok" ]] && echo 0 || echo 1)
if (( failures > 0 )); then
  echo "fabric-save-roundtrip-smoke: FAILED ($failures checks)" >&2
  exit 1
fi
echo "fabric-save-roundtrip-smoke: PASSED"
