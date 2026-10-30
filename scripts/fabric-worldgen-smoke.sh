#!/usr/bin/env bash
# End-to-end worldgen gate: prove Mekanism ores actually generate in a fresh world.
#
# Boots the newest packaged jar on a disposable Fabric dedicated server with a fixed seed,
# force-loads 512 chunks around the origin, stops the server cleanly, then scans the saved
# region files for the official ore/salt block palette names. This is the acceptance check
# for the worldgen closure: packaged configured/placed features, the custom feature/provider
# codecs, the per-world world.toml owner and the official neoforge:add_features biome
# modifiers applied through Fabric BiomeModifications.
#
# Prerequisite: a remapped release jar (`./fabric-build.sh remapJar`). Requires python3 (stdlib only).
set -euo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
PROPERTIES="$ROOT/fabric/gradle.properties"
CACHE_DIR=${FABRIC_SMOKE_CACHE_DIR:-${XDG_CACHE_HOME:-$HOME/.cache}/mekanism-fabric-smoke}
TIMEOUT=${FABRIC_WORLDGEN_SMOKE_TIMEOUT:-420}
KEEP_SERVER=${FABRIC_SMOKE_KEEP_SERVER:-0}
SERVER_DIR=
LOG_FILE=

property() {
  local key=$1 value
  value=$(awk -F= -v key="$key" '$1 == key { sub(/^[^=]*=/, ""); print; exit }' "$PROPERTIES")
  if [[ -z "$value" ]]; then
    echo "fabric-worldgen-smoke: missing $key in $PROPERTIES" >&2
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
    printf 'fabric-worldgen-smoke: retained server: %s\n' "$SERVER_DIR" >&2
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
  printf 'fabric-worldgen-smoke: downloading %s\n' "$url" >&2
  if ! curl --fail --location --retry 3 --retry-all-errors --connect-timeout 15 \
      --max-time 300 --output "$temporary" "$url"; then
    rm -f "$temporary"
    echo "fabric-worldgen-smoke: download failed: $url" >&2
    exit 2
  fi
  mv "$temporary" "$destination"
}

#Newest artifact wins: a stale dist/ jar from an earlier release build must not mask the current remap output.
MOD_JAR=
for candidate in "$ROOT/dist/"*-fabric-bootstrap.jar "$ROOT/fabric/build/libs/"*.jar; do
  [[ -f "$candidate" ]] || continue
  [[ "$candidate" == *-sources.jar || "$candidate" == *-dev.jar || "$candidate" == *fixture* ]] && continue
  if [[ -z "$MOD_JAR" || "$candidate" -nt "$MOD_JAR" ]]; then MOD_JAR=$candidate; fi
done
if [[ -z "$MOD_JAR" ]]; then
  echo "fabric-worldgen-smoke: no packaged jar; run ./fabric-build.sh remapJar first" >&2
  exit 2
fi

mkdir -p "$CACHE_DIR"
LAUNCHER_JAR="$CACHE_DIR/fabric-server-${MINECRAFT_VERSION}-${LOADER_VERSION}-${INSTALLER_VERSION}.jar"
FABRIC_API_JAR="$CACHE_DIR/fabric-api-${FABRIC_API_VERSION}.jar"
ENERGY_JAR="$CACHE_DIR/energy-${TEAM_REBORN_ENERGY_VERSION}.jar"
fetch "https://meta.fabricmc.net/v2/versions/loader/${MINECRAFT_VERSION}/${LOADER_VERSION}/${INSTALLER_VERSION}/server/jar" "$LAUNCHER_JAR"
fetch "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/${FABRIC_API_VERSION}/fabric-api-${FABRIC_API_VERSION}.jar" "$FABRIC_API_JAR"
fetch "https://maven.fabricmc.net/teamreborn/energy/${TEAM_REBORN_ENERGY_VERSION}/energy-${TEAM_REBORN_ENERGY_VERSION}.jar" "$ENERGY_JAR"

SERVER_DIR=$(mktemp -d "${TMPDIR:-/tmp}/mekanism-worldgen-smoke.XXXXXX")
LOG_FILE="$SERVER_DIR/server.log"
mkdir -p "$SERVER_DIR/mods"
cp "$LAUNCHER_JAR" "$SERVER_DIR/fabric-server-launch.jar"
if [[ -n ${FABRIC_SMOKE_RUNTIME_CACHE_DIR:-} ]]; then
  for part in libraries versions .fabric/server; do
    [[ -d "$FABRIC_SMOKE_RUNTIME_CACHE_DIR/$part" ]] || continue
    mkdir -p "$(dirname "$SERVER_DIR/$part")"
    cp -a --reflink=auto "$FABRIC_SMOKE_RUNTIME_CACHE_DIR/$part" "$SERVER_DIR/$part"
  done
fi
cp "$FABRIC_API_JAR" "$ENERGY_JAR" "$MOD_JAR" "$SERVER_DIR/mods/"
printf 'eula=true\n' > "$SERVER_DIR/eula.txt"
#Fixed seed keeps the generated area deterministic across runs and machines.
printf 'online-mode=false\nserver-port=0\nspawn-protection=0\nmax-players=1\nview-distance=4\nsync-chunk-writes=false\nlevel-seed=635813247\n' > "$SERVER_DIR/server.properties"

cd "$SERVER_DIR"
(
  for _ in $(seq 1 180); do
    grep -q 'Done (' server.log 2>/dev/null && break
    sleep 1
  done
  sleep 1
  #512 chunks in two commands (vanilla caps one forceload command at 256 chunks); full ticket level
  #forces complete generation including underground_ores decoration.
  echo "forceload add 0 0 255 255"
  echo "forceload add -256 0 -1 255"
  sleep 90
  echo "stop"
  sleep 10
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

check "worldgen registry proof (features/providers registered)" 'MEKANISM_FABRIC_WORLDGEN_REGISTRY_PROOF features=3 int_provider_types=2 height_provider_types=1 placement_modifier_types=1'
check "biome modifier proof (6 official modifiers, 11 placed features)" 'MEKANISM_FABRIC_WORLDGEN_PROOF modifiers=6 features=11'
if grep -q 'Failed to retrieve placed feature' "$LOG_FILE"; then
  echo "FAIL no missing placed-feature lookups"
  failures=$((failures + 1))
fi
if grep -q 'Errors in currently selected datapacks' "$LOG_FILE"; then
  echo "FAIL datapack loads without errors"
  failures=$((failures + 1))
else
  echo "PASS datapack loads without errors"
fi
if [[ -f "$SERVER_DIR/world/serverconfig/Mekanism/world.toml" ]] && grep -q '\[salt\]' "$SERVER_DIR/world/serverconfig/Mekanism/world.toml"; then
  echo "PASS per-world world.toml owner loaded"
else
  echo "FAIL per-world world.toml owner loaded"
  failures=$((failures + 1))
fi

if [[ ! -d "$SERVER_DIR/world/region" ]]; then
  echo "FAIL region files exist"
  failures=$((failures + 1))
  printf 'fabric-worldgen-smoke: %d check(s) failed\n' "$failures"
  exit 1
fi

SCAN=$(SERVER_DIR="$SERVER_DIR" python3 - <<'PYEOF'
import os, struct, zlib, gzip, io, sys

server = os.environ["SERVER_DIR"]
targets = [
    "mekanism:osmium_ore", "mekanism:deepslate_osmium_ore",
    "mekanism:tin_ore", "mekanism:deepslate_tin_ore",
    "mekanism:lead_ore", "mekanism:deepslate_lead_ore",
    "mekanism:uranium_ore", "mekanism:deepslate_uranium_ore",
    "mekanism:fluorite_ore", "mekanism:deepslate_fluorite_ore",
    "mekanism:block_salt",
]
counts = {t: 0 for t in targets}
chunks = 0
region_dir = os.path.join(server, "world", "region")
for name in sorted(os.listdir(region_dir)):
    if not (name.startswith("r.") and name.endswith(".mca")):
        continue
    with open(os.path.join(region_dir, name), "rb") as handle:
        data = handle.read()
    if len(data) < 8192:
        continue
    for index in range(1024):
        entry = struct.unpack_from(">I", data, index * 4)[0]
        offset, sectors = entry >> 8, entry & 0xFF
        if offset == 0 or sectors == 0:
            continue
        start = offset * 4096
        length = struct.unpack_from(">I", data, start)[0]
        compression = data[start + 4]
        payload = data[start + 5:start + 4 + length]
        try:
            if compression == 2:
                decoded = zlib.decompress(payload)
            elif compression == 1:
                decoded = gzip.decompress(payload)
            else:
                continue
        except (zlib.error, OSError):
            continue
        chunks += 1
        for target in targets:
            if target.encode() in decoded:
                counts[target] += 1
print(f"chunks_scanned={chunks}")
for target in targets:
    print(f"{target}={counts[target]}")
PYEOF
)
printf '%s\n' "$SCAN"

scan_failures=0
for target in mekanism:osmium_ore mekanism:tin_ore mekanism:lead_ore mekanism:uranium_ore mekanism:fluorite_ore mekanism:block_salt; do
  if printf '%s\n' "$SCAN" | grep -Eq "^${target}=[1-9]"; then
    :
  else
    scan_failures=$((scan_failures + 1))
  fi
done
if [[ $scan_failures -eq 0 ]]; then
  echo "PASS all six official worldgen targets present in generated chunks"
else
  echo "FAIL $scan_failures official worldgen target(s) absent from generated chunks"
  failures=$((failures + scan_failures))
fi

printf 'fabric-worldgen-smoke: %d check(s) failed\n' "$failures"
exit $((failures > 0 ? 1 : 0))
