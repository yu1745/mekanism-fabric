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
MOD_JAR=
for candidate in "$ROOT/dist/"Mekanism-Fabric-*.jar "$ROOT/fabric/build/libs/"*.jar; do
  [[ -f "$candidate" ]] || continue
  [[ "$candidate" == *-sources.jar || "$candidate" == *-dev.jar || "$candidate" == *fixture* ]] && continue
  if [[ -z "$MOD_JAR" || "$candidate" -nt "$MOD_JAR" ]]; then MOD_JAR=$candidate; fi
done
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
# Reuse only cached runtime binaries when installer metadata is unavailable; each test still gets a fresh world/mod set.
if [[ -n ${FABRIC_SMOKE_RUNTIME_CACHE_DIR:-} ]]; then
  for part in libraries versions .fabric/server; do
    [[ -d "$FABRIC_SMOKE_RUNTIME_CACHE_DIR/$part" ]] || continue
    mkdir -p "$(dirname "$SERVER_DIR/$part")"
    cp -a --reflink=auto "$FABRIC_SMOKE_RUNTIME_CACHE_DIR/$part" "$SERVER_DIR/$part"
  done
fi
cp "$FABRIC_API_JAR" "$ENERGY_JAR" "$MOD_JAR" "$SERVER_DIR/mods/"
printf 'eula=true\n' > "$SERVER_DIR/eula.txt"
printf 'online-mode=false\nserver-port=0\nspawn-protection=0\nmax-players=1\nview-distance=4\nsync-chunk-writes=false\n' > "$SERVER_DIR/server.properties"
if [[ ${FABRIC_MACHINE_BLACKLIST_FE:-0} == 1 ]]; then
  mkdir -p "$SERVER_DIR/world/serverconfig/Mekanism"
  printf '[energy_conversion]\nblacklistForge = true\n' > "$SERVER_DIR/world/serverconfig/Mekanism/general.toml"
fi

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
  #The x4/x5 machines sit at x=58..68, i.e. chunk 4, beyond the spawn-chunk radius the earlier scenarios rely on.
  echo "forceload add 64 0"
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
  #Chemical auto-eject destination: the separator ejects its right (oxygen) tank upward (TOP side).
  echo "setblock 38 101 0 mekanism:basic_chemical_tank"
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
  echo 'data merge block 38 100 0 {component_config:{eject2:1b, config0:[I;1,1,1,1,1,1], config1:[I;1,1,1,1,1,1], config2:[I;0,0,0,0,6,0]}, energy_containers:[{container:0b,stored:20000L}]}'
  #The eject destination accepts chemical input on every side; the separator's TOP chemical side is OUTPUT_2 (ordinal 6) --
  #its chemical config only exposes OUTPUT_1/OUTPUT_2 (left=hydrogen, right=oxygen), so the plain OUTPUT ordinal (4)
  #would silently eject nothing.
  echo 'data merge block 38 101 0 {component_config:{config2:[I;1,1,1,1,1,1]}}'
  #Hopper automation scenario: the facing hopper pushes ore into the chamber's LEFT item side (facing north: east->LEFT);
  #the chamber's BOTTOM item side is OUTPUT so the hopper below can pull the dust back out. The bridge only claims
  #Mekanism storage views, so vanilla hopper behavior elsewhere is untouched.
  echo "setblock 41 100 0 mekanism:enrichment_chamber"
  echo "setblock 42 100 0 minecraft:hopper[facing=west]"
  echo "setblock 41 99 0 minecraft:hopper[facing=down]"
  #Chemical chain scenario (W-111): a chemical infuser turns seeded sulfur trioxide + water vapour into sulfuric acid,
  #a pressurized tube carries it into a chemical dissolution chamber, and the chamber dissolves iron ore into dirty
  #iron slurry. This is the entry stage of the x3/x4/x5 ore path - the stage that needs a real chemical feed rather
  #than an item one.
  echo "setblock 50 100 0 mekanism:chemical_infuser"
  #Chunk-unload scenario (W-104): vanilla refuses setblock in an unloaded chunk, so the far machine at x=2000 is built
  #while its chunk is force-loaded and the forceload is released afterwards. Unloaded, it must not tick; force-loading
  #again must let it process, with the inventory intact across the unload.
  echo "forceload add 2000 0"
  echo "setblock 2000 100 0 mekanism:enrichment_chamber"
  echo "setblock 52 100 0 mekanism:chemical_dissolution_chamber"
  #x3 continuation (W-111/W-115). Side arrays index RelativeSide (FRONT, LEFT, RIGHT, BACK, TOP, BOTTOM), so the
  #chain configures "every side" with the right type instead of guessing which index faces the neighbour.
  echo "setblock 53 100 0 mekanism:basic_chemical_tank"
  echo "setblock 55 100 0 mekanism:chemical_washer"
  echo "setblock 56 100 0 mekanism:chemical_crystallizer"
  #x4 stage (W-111). Each new machine's chemistry is asserted on its own tank: the separator's chlorine lives in its
  #right (OUTPUT_2) tank so its chemical sides are 6, the infuser joins hydrogen and chlorine into hydrogen chloride and
  #holds it, and the injection chamber turns a crystal plus hydrogen chloride into a shard.
  echo "setblock 58 100 0 mekanism:chemical_injection_chamber"
  echo "setblock 60 100 0 mekanism:basic_chemical_tank"
  echo "setblock 61 100 0 mekanism:chemical_infuser"
  echo "setblock 62 100 0 mekanism:electrolytic_separator"
  #x5 stage (W-111): shard plus oxygen purifies into a clump, crushing the clump gives dirty dust, and enriching the
  #dirty dust gives the finished dust - the last three conversions of the ore path.
  echo "setblock 58 100 2 mekanism:purification_chamber"
  echo "setblock 58 100 4 mekanism:crusher"
  echo "setblock 58 100 6 mekanism:enrichment_chamber"
  echo 'data merge block 41 100 0 {component_config:{config3:[I;1,1,1,1,1,4]}, energy_containers:[{container:0b,stored:40000L}]}'
  echo 'item replace block 42 100 0 container.0 with minecraft:iron_ore 4'
  echo 'data merge block 50 100 0 {energy_containers:[{container:0b,stored:40000L}], component_config:{config2:[I;4,4,4,4,4,4]}, chemical_tanks:[{tank:0b,stored:{id:"mekanism:sulfur_trioxide",amount:2000}},{tank:1b,stored:{id:"mekanism:water_vapor",amount:2000}}]}'
  echo 'data merge block 2000 100 0 {energy_containers:[{container:0b,stored:20000L}], items:[{slot:0b,item:{id:"minecraft:iron_ore",count:4}}]}'
  echo "forceload remove 2000 0"
  #Slot layout: 0 is the chemical input slot, 1 is the item input, 2 the chemical output, 3 the energy slot - so the
  #ore goes in slot 1. The chamber needs a full recipe's worth of acid (1000 mB per ore) and enough energy for the operation; the infuser
  #only ever produces one 200 mB batch per 40k buffer, so the acid it makes arrives as extra input on top of this seed.
  echo 'data merge block 52 100 0 {energy_containers:[{container:0b,stored:40000L}], items:[{slot:1b,item:{id:"minecraft:iron_ore",count:4}}], chemical_tanks:[{tank:0b,stored:{id:"mekanism:sulfuric_acid",amount:4000}}], component_config:{eject2:1b, config2:[I;7,7,7,7,7,7], config3:[I;1,1,1,1,1,1]}}'
  echo 'data merge block 53 100 0 {component_config:{config2:[I;7,7,7,7,7,7]}}'
  #The washer both takes dirty slurry and emits clean slurry, so INPUT_OUTPUT plus eject is what it needs; it also
  #needs a water fill and enough energy for one 1000 mB washing operation.
  echo 'data merge block 55 100 0 {energy_containers:[{container:0b,stored:40000L}], fluid_tanks:[{tank:0b,stored:{id:"minecraft:water",amount:8000}}], component_config:{eject2:1b, config1:[I;1,1,1,1,1,1], config2:[I;7,7,7,7,7,7]}}'
  echo 'data merge block 56 100 0 {energy_containers:[{container:0b,stored:40000L}], component_config:{config2:[I;1,1,1,1,1,1]}}'
  echo 'data merge block 58 100 0 {energy_containers:[{container:0b,stored:40000L}], items:[{slot:0b,item:{id:"mekanism:crystal_iron",count:4}}], chemical_tanks:[{tank:0b,stored:{id:"mekanism:hydrogen_chloride",amount:2000}}], component_config:{eject3:1b, config2:[I;1,1,1,1,1,1], config3:[I;4,4,4,4,4,4]}}'
  echo 'data merge block 60 100 0 {component_config:{config2:[I;7,7,7,7,7,7]}}'
  echo 'data merge block 61 100 0 {energy_containers:[{container:0b,stored:40000L}], chemical_tanks:[{tank:0b,stored:{id:"mekanism:hydrogen",amount:2000}}], component_config:{eject2:1b, config2:[I;7,7,7,7,7,7]}}'
  echo 'data merge block 62 100 0 {energy_containers:[{container:0b,stored:40000L}], fluid_tanks:[{tank:0b,stored:{id:"mekanism:brine",amount:8000}}], component_config:{eject2:1b, config2:[I;6,6,6,6,6,6]}}'
  #No item seeds: the shard has to arrive from the injection chamber through three transporters.
  echo 'data merge block 58 100 2 {energy_containers:[{container:0b,stored:40000L}], chemical_tanks:[{tank:0b,stored:{id:"mekanism:oxygen",amount:4000}}], component_config:{config2:[I;1,1,1,1,1,1], config3:[I;7,7,7,7,7,7]}}'
  echo 'data merge block 58 100 4 {energy_containers:[{container:0b,stored:40000L}], component_config:{config3:[I;7,7,7,7,7,7]}}'
  echo 'data merge block 58 100 6 {energy_containers:[{container:0b,stored:40000L}], component_config:{config3:[I;1,1,1,1,1,1]}}'
  sleep 3
  echo "setblock 13 100 0 mekanism:basic_universal_cable"
  echo "setblock 21 100 0 mekanism:basic_logistical_transporter"
  echo "setblock 25 100 0 mekanism:basic_mechanical_pipe"
  echo "setblock 29 100 0 mekanism:basic_pressurized_tube"
  echo "setblock 33 100 0 mekanism:basic_logistical_transporter"
  echo "setblock 37 100 0 mekanism:basic_mechanical_pipe"
  echo "setblock 51 100 0 mekanism:basic_pressurized_tube"
  echo "setblock 54 100 0 mekanism:basic_pressurized_tube"
  echo "setblock 58 100 1 mekanism:basic_logistical_transporter"
  echo "setblock 58 100 3 mekanism:basic_logistical_transporter"
  echo "setblock 58 100 5 mekanism:basic_logistical_transporter"
  echo "setblock 59 100 0 mekanism:basic_pressurized_tube"
  sleep 3
  echo 'data merge block 21 100 0 {connection:[I;3,3,3,3,2,1]}'
  echo 'data merge block 25 100 0 {connection:[I;3,3,3,3,2,1]}'
  echo 'data merge block 29 100 0 {connection:[I;3,3,3,3,2,1]}'
  echo 'data merge block 33 100 0 {connection:[I;3,3,3,3,2,1]}'
  echo 'data merge block 37 100 0 {connection:[I;3,3,3,3,2,1]}'
  echo 'data merge block 51 100 0 {connection:[I;3,3,3,3,2,1]}'
  echo 'data merge block 54 100 0 {connection:[I;3,3,3,3,2,1]}'
  #Pull from the north (the stage behind) and push south (the stage ahead).
  echo 'data merge block 58 100 1 {connection:[I;3,3,2,1,3,3]}'
  echo 'data merge block 58 100 3 {connection:[I;3,3,2,1,3,3]}'
  echo 'data merge block 58 100 5 {connection:[I;3,3,2,1,3,3]}'
  #Pulls hydrogen chloride from the tank and pushes it into the injection chamber.
  echo 'data merge block 59 100 0 {connection:[I;3,3,3,3,1,2]}'
  #900 ticks cover four hopper-fed operations (hopper cadence is one item per 8 game ticks) plus extraction, and they
  #are fast-forwarded rather than slept through.
  echo "tick sprint 900"
  sleep 2
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
  echo "data get block 38 101 0"
  echo "data get block 33 100 0"
  echo "data get block 37 100 0"
  echo "data get block 21 100 0"
  echo "data get block 41 100 0"
  echo "data get block 42 100 0"
  echo "data get block 41 99 0"
  echo "data get block 50 100 0"
  #Prove the chunk really is unloaded rather than merely quiet: the marker only prints when the position is not loaded.
  echo "execute unless loaded 2000 100 0 run say farchunk-unloaded"
  echo "data get block 2000 100 0"
  #Reload it and let the server run 300 ticks as fast as it can; the same machine must process its ore.
  echo "forceload add 2000 0"
  sleep 2
  echo "execute if loaded 2000 100 0 run say farchunk-reloaded"
  echo "tick sprint 300"
  sleep 3
  echo "data get block 2000 100 0"
  #The crystallizer starts only after the washer has fed it, by which point its 40k buffer has already drained; top it
  #up (and the washer with it) so the crystal can finish.
  echo "tick sprint 200"
  sleep 2
  echo 'data merge block 56 100 0 {energy_containers:[{container:0b,stored:40000L}]}'
  echo 'data merge block 55 100 0 {energy_containers:[{container:0b,stored:40000L}]}'
  echo 'data merge block 58 100 0 {energy_containers:[{container:0b,stored:40000L}]}'
  echo 'data merge block 58 100 2 {energy_containers:[{container:0b,stored:40000L}]}'
  echo 'data merge block 58 100 4 {energy_containers:[{container:0b,stored:40000L}]}'
  echo 'data merge block 58 100 6 {energy_containers:[{container:0b,stored:40000L}]}'
  echo "tick sprint 300"
  sleep 2
  #A second top-up right before the dump: the chained stages burn their 40k buffers faster than one refill covers.
  echo 'data merge block 58 100 2 {energy_containers:[{container:0b,stored:40000L}]}'
  echo 'data merge block 58 100 4 {energy_containers:[{container:0b,stored:40000L}]}'
  echo 'data merge block 58 100 6 {energy_containers:[{container:0b,stored:40000L}]}'
  #The enrichment chamber starts its last craft only once the dirty dust arrives, so give it a full operation of ticks.
  echo "tick sprint 600"
  sleep 2
  echo "data get block 52 100 0"
  echo "data get block 53 100 0"
  echo "data get block 55 100 0"
  echo "data get block 56 100 0"
  echo "data get block 58 100 0"
  echo "data get block 60 100 0"
  echo "data get block 61 100 0"
  echo "data get block 62 100 0"
  echo "data get block 58 100 2"
  echo "data get block 58 100 4"
  echo "data get block 58 100 6"
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
check "server play channel registered all 28 payloads" 'MEKANISM_FABRIC_SERVER_PLAY_PROOF payloads=28'
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
check "separator auto-ejected oxygen to the adjacent tank" '38, 101, 0 has the following block data.*chemical_tanks.*mekanism:oxygen'
check "hopper pushed ore into the machine" '42, 100, 0 has the following block data.*Items: \[\]'
check "machine output was pulled into the hopper" '41, 99, 0 has the following block data.*mekanism:dust_iron'
check "far chunk was genuinely unloaded" 'farchunk-unloaded'
check "far chunk reloaded when force-loaded again" 'farchunk-reloaded'
check "far chunk machine processed once its chunk was loaded" '2000, 100, 0 has the following block data.*mekanism:dust_iron'
check "chemical infuser ran and its acid reached the dissolution chamber" '52, 100, 0 has the following block data.*chemical_tanks.*mekanism:sulfuric_acid'
check "chemical infuser consumed its seeded inputs" '50, 100, 0 has the following block data.*sulfur_trioxide.*amount: 1[0-9]{3}L'
check "washer fed clean slurry to the crystallizer" '56, 100, 0 has the following block data.*chemical_tanks.*mekanism:clean_iron'
check "crystallizer produced an iron crystal" '56, 100, 0 has the following block data.*mekanism:crystal_iron'
check "ore path ran end to end: crystal in at the chamber, dust out at the enrichment chamber" '58, 100, 6 has the following block data.*"mekanism:dust_iron"'
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
