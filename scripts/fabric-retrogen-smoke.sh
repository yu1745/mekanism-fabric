#!/usr/bin/env bash
# Retrogen activation smoke: boots the packaged server twice on the SAME world. The first boot generates the
# world with the default world-gen version (0) and saves it into every chunk; the second boot runs with
# enableRegeneration=true and a bumped userGenVersion, so every loaded chunk is older than the latest version
# and CommonWorldTickHandler marks + regenerates it. The proof is the handler's own "Regenerating ores and
# salt at chunk" log lines plus the regen queue draining without errors.
set -uo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROPERTIES="$ROOT/fabric/gradle.properties"
TIMEOUT=${FABRIC_RETROGEN_SMOKE_TIMEOUT:-420}
SERVER_DIR=
KEEP_SERVER=${FABRIC_RETROGEN_SMOKE_KEEP:-0}

property() { grep -E "^$1=" "$PROPERTIES" | head -1 | cut -d= -f2; }
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
    printf 'fabric-retrogen-smoke: retained server: %s\n' "$SERVER_DIR" >&2
  fi
  exit "$status"
}
trap cleanup EXIT INT TERM

cd "$ROOT"
MOD_JAR=$(ls -t fabric/build/libs/*.jar 2>/dev/null | grep -v sources | grep -v dev | head -1)
[[ -n "$MOD_JAR" ]] || { echo "fabric-retrogen-smoke: missing remapped jar (run ./fabric-build.sh remapJar)" >&2; exit 2; }
CACHE_DIR=${FABRIC_SMOKE_CACHE_DIR:-/tmp/mekanism-fabric-smoke-cache}
mkdir -p "$CACHE_DIR"
FABRIC_API_JAR="$CACHE_DIR/fabric-api-${FABRIC_API_VERSION}.jar"
ENERGY_JAR="$CACHE_DIR/energy-${TEAM_REBORN_ENERGY_VERSION}.jar"

fetch() {
  local url=$1 destination=$2 temporary="${2}.part.$$"
  if [[ -s "$destination" ]] && jar tf "$destination" >/dev/null 2>&1; then
    return
  fi
  rm -f "$destination" "$temporary"
  printf 'fabric-retrogen-smoke: downloading %s\n' "$url" >&2
  if ! curl --fail --location --retry 3 --retry-all-errors --connect-timeout 15 \
      --max-time 300 --output "$temporary" "$url"; then
    rm -f "$temporary"
    echo "fabric-retrogen-smoke: download failed: $url" >&2
    exit 1
  fi
  mv "$temporary" "$destination"
}
LAUNCHER_JAR="$CACHE_DIR/fabric-server-${MINECRAFT_VERSION}-${LOADER_VERSION}-${INSTALLER_VERSION}.jar"
fetch "https://meta.fabricmc.net/v2/versions/loader/${MINECRAFT_VERSION}/${LOADER_VERSION}/${INSTALLER_VERSION}/server/jar" "$LAUNCHER_JAR"
fetch "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/${FABRIC_API_VERSION}/fabric-api-${FABRIC_API_VERSION}.jar" "$FABRIC_API_JAR"
fetch "https://maven.fabricmc.net/teamreborn/energy/${TEAM_REBORN_ENERGY_VERSION}/energy-${TEAM_REBORN_ENERGY_VERSION}.jar" "$ENERGY_JAR"

SERVER_DIR=$(mktemp -d /tmp/mekanism-retrogen-smoke.XXXXXX)
mkdir -p "$SERVER_DIR/mods"
cp "$LAUNCHER_JAR" "$SERVER_DIR/fabric-server-launch.jar"
if [[ -n ${FABRIC_SMOKE_RUNTIME_CACHE_DIR:-} ]]; then
  for part in libraries versions .fabric/server; do
    [[ -d "$FABRIC_SMOKE_RUNTIME_CACHE_DIR/$part" ]] || continue
    mkdir -p "$(dirname "$SERVER_DIR/$part")"
    cp -a --reflink=auto "$FABRIC_SMOKE_RUNTIME_CACHE_DIR/$part" "$SERVER_DIR/$part"
  done
fi
cp "$MOD_JAR" "$FABRIC_API_JAR" "$ENERGY_JAR" "$SERVER_DIR/mods/"
printf 'eula=true\n' > "$SERVER_DIR/eula.txt"
printf 'online-mode=false\nserver-port=0\nspawn-protection=0\nmax-players=1\nview-distance=4\nsync-chunk-writes=false\nlevel-seed=635813247\n' > "$SERVER_DIR/server.properties"

boot() {
  local phase=$1
  cd "$SERVER_DIR"
  (
    for _ in $(seq 1 180); do
      grep -q 'Done (' "server-$phase.log" 2>/dev/null && break
      sleep 1
    done
    sleep 1
    echo "forceload add 0 0 255 255"
    echo "forceload add -256 0 -1 255"
    sleep 60
    echo "stop"
    sleep 10
  ) | timeout "$TIMEOUT" java -Xms512M -Xmx2G -jar fabric-server-launch.jar nogui > "server-$phase.log" 2>&1 || true
}

#Phase one: generate + save chunks at world-gen version 0 (the config default).
boot generate
grep -q 'Done (' "server-generate.log" || { echo "fabric-retrogen-smoke: first boot never reached Done" >&2; exit 1; }

#Flip the config the per-world owner reads: retrogen on, version bumped. The stored chunk versions (0) are
#now older than userGenVersion (1), which is the official mark-and-regenerate trigger.
WORLD_TOML="$SERVER_DIR/world/serverconfig/Mekanism/world.toml"
[[ -f "$WORLD_TOML" ]] || WORLD_TOML="$SERVER_DIR/config/Mekanism/world.toml"
[[ -f "$WORLD_TOML" ]] || { echo "fabric-retrogen-smoke: world.toml missing after first boot" >&2; exit 1; }
python3 - "$WORLD_TOML" <<'PY'
import re, sys
path = sys.argv[1]
text = open(path).read()
text = re.sub(r'(?m)^(\s*enableRegeneration\s*=\s*)false$', r'\1true', text)
text = re.sub(r'(?m)^(\s*userWorldGenVersion\s*=\s*)0$', r'\g<1>1', text)
open(path, 'w').write(text)
PY

#Phase two: reload the same world; loaded chunks retrogen.
boot retrogen
grep -q 'Done (' "server-retrogen.log" || { echo "fabric-retrogen-smoke: second boot never reached Done" >&2; exit 1; }

failures=0
check() {
  local description=$1 pattern=$2
  if grep -Eq "$pattern" "server-retrogen.log"; then
    printf 'PASS %s\n' "$description"
  else
    printf 'FAIL %s\n' "$description"
    failures=$((failures + 1))
  fi
}

check "retrogen marks and regenerates chunks" 'Regenerating ores and salt at chunk'
if grep -qE 'Exception in thread|Caused by: java\.lang\.(IllegalStateException|ClassCastException)' "server-retrogen.log"; then
  echo "FAIL no loader-class exceptions"
  grep -E 'Exception in thread|Caused by: java\.lang\.(IllegalStateException|ClassCastException)' "server-retrogen.log" | head -3
  failures=$((failures + 1))
else
  echo "PASS no loader-class exceptions"
fi

count=$(grep -c 'Regenerating ores and salt at chunk' "server-retrogen.log" || true)
printf 'fabric-retrogen-smoke: regenerated %d chunk(s)\n' "$count"
[[ $failures -eq 0 ]]
