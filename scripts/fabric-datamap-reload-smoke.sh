#!/usr/bin/env bash
# Exercise packaged MekaSuit absorption startup, layered reload, failure, repair, and stop lifecycle.
set -euo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
PROPERTIES="$ROOT/fabric/gradle.properties"
CACHE_DIR=${FABRIC_DATAMAP_SMOKE_CACHE_DIR:-${XDG_CACHE_HOME:-$HOME/.cache}/mekanism-fabric-smoke}
LOG_ROOT=${FABRIC_DATAMAP_SMOKE_LOG_DIR:-$ROOT/build/fabric-datamap-reload-smoke}
TIMEOUT=${FABRIC_DATAMAP_SMOKE_TIMEOUT:-240}
KEEP_SERVER=${FABRIC_DATAMAP_SMOKE_KEEP_SERVER:-0}
SERVER_DIR=
SERVER_PID=
CONSOLE_FD_OPEN=0
RESULT=1
LOG_CHECKPOINT=0
ACCOUNTED_ERROR_LINES=0

property() {
  local key=$1 value
  value=$(awk -F= -v key="$key" '$1 == key { sub(/^[^=]*=/, ""); print; exit }' "$PROPERTIES")
  [[ -n "$value" ]] || { echo "fabric-datamap-reload-smoke: missing $key" >&2; exit 2; }
  printf '%s\n' "$value"
}

MINECRAFT_VERSION=$(property minecraft_version)
LOADER_VERSION=$(property loader_version)
FABRIC_API_VERSION=$(property fabric_api_version)
TEAM_REBORN_ENERGY_VERSION=$(property team_reborn_energy_version)
INSTALLER_VERSION=$(property fabric_installer_version)
mkdir -p "$CACHE_DIR" "$LOG_ROOT"
RUN_ID=$(date -u +%Y%m%dT%H%M%SZ)-$$
DIAGNOSTIC_DIR="$LOG_ROOT/$RUN_ID"
mkdir -p "$DIAGNOSTIC_DIR"

finish() {
  local status=$?
  trap - EXIT INT TERM
  if (( CONSOLE_FD_OPEN )); then exec 3>&- || true; fi
  if [[ -n "$SERVER_PID" ]] && kill -0 "$SERVER_PID" 2>/dev/null; then
    kill -TERM -- "-$SERVER_PID" 2>/dev/null || kill -TERM "$SERVER_PID" 2>/dev/null || true
    for _ in {1..40}; do kill -0 "$SERVER_PID" 2>/dev/null || break; sleep 0.25; done
    kill -KILL -- "-$SERVER_PID" 2>/dev/null || kill -KILL "$SERVER_PID" 2>/dev/null || true
  fi
  [[ -n "$SERVER_PID" ]] && wait "$SERVER_PID" 2>/dev/null || true
  if [[ -n "$SERVER_DIR" && -d "$SERVER_DIR" ]]; then
    [[ -f "$SERVER_DIR/logs/latest.log" ]] && cp "$SERVER_DIR/logs/latest.log" "$DIAGNOSTIC_DIR/latest.log"
    [[ -f "$SERVER_DIR/console.log" ]] && cp "$SERVER_DIR/console.log" "$DIAGNOSTIC_DIR/console.log"
    if [[ "$KEEP_SERVER" == 1 ]]; then
      printf 'fabric-datamap-reload-smoke: retained server: %s\n' "$SERVER_DIR" >&2
    else
      rm -rf "$SERVER_DIR"
    fi
  fi
  printf 'fabric-datamap-reload-smoke: diagnostics: %s\n' "$DIAGNOSTIC_DIR" >&2
  [[ $RESULT -eq 0 && $status -eq 0 ]] && exit 0
  (( status == 0 )) && status=1
  exit "$status"
}
trap finish EXIT INT TERM

fetch() {
  local url=$1 destination=$2 temporary="${2}.part.$$"
  if [[ -s "$destination" ]] && jar tf "$destination" >/dev/null 2>&1; then return; fi
  rm -f "$destination" "$temporary"
  curl --fail --location --retry 3 --retry-all-errors --connect-timeout 15 --max-time 300 --output "$temporary" "$url"
  jar tf "$temporary" >/dev/null
  mv "$temporary" "$destination"
}

fail() {
  printf 'fabric-datamap-reload-smoke: %s\n' "$1" >&2
  [[ -f "$SERVER_DIR/logs/latest.log" ]] && tail -n 100 "$SERVER_DIR/logs/latest.log" >&2 || true
  exit 1
}

assert_alive_clean() {
  kill -0 "$SERVER_PID" 2>/dev/null || fail 'server exited unexpectedly'
  if grep -Eiq '(Exception in thread|Could not execute entrypoint|Failed to start the minecraft server|---- Minecraft Crash Report ----|NoClassDefFoundError|ClassNotFoundException)' "$SERVER_DIR/logs/latest.log"; then
    fail 'crash signature detected'
  fi
}

wait_for() {
  local pattern=$1 description=$2 deadline=$((SECONDS + TIMEOUT))
  while (( SECONDS < deadline )); do
    grep -Fq "$pattern" "$SERVER_DIR/logs/latest.log" 2>/dev/null && return 0
    assert_alive_clean
    sleep 0.5
  done
  fail "timed out waiting for $description"
}

send_command() {
  printf '%s\n' "$1" >&3
}

log_line_count() {
  wc -l < "$SERVER_DIR/logs/latest.log"
}

log_errors_between() {
  local first=$(( $1 + 1 )) last=$2
  awk -v first="$first" -v last="$last" 'NR >= first && NR <= last && /\[[^]]*ERROR\]/ { print }' "$SERVER_DIR/logs/latest.log"
}

assert_no_new_errors() {
  local phase=$1 end
  local -a errors=()
  end=$(log_line_count)
  mapfile -t errors < <(log_errors_between "$LOG_CHECKPOINT" "$end")
  if (( ${#errors[@]} != 0 )); then
    printf 'fabric-datamap-reload-smoke: unexpected ERROR during %s:\n' "$phase" >&2
    printf '%s\n' "${errors[@]}" >&2
    fail "unexpected ERROR output during $phase"
  fi
  LOG_CHECKPOINT=$end
}

assert_malformed_map_errors() {
  local end expected
  local -a errors=()
  expected='Skipping invalid data-map layer mekanism:data_maps/damage_type/mekasuit_absorption.json from pack file/datamap-high'
  end=$(log_line_count)
  mapfile -t errors < <(log_errors_between "$LOG_CHECKPOINT" "$end")
  if (( ${#errors[@]} != 1 )) || [[ "${errors[0]:-}" != *"$expected" ]]; then
    printf 'fabric-datamap-reload-smoke: malformed-map ERROR window was not the exact expected event:\n' >&2
    printf '%s\n' "${errors[@]:-<none>}" >&2
    fail 'unexpected ERROR output during malformed-map reload'
  fi
  ACCOUNTED_ERROR_LINES=$((ACCOUNTED_ERROR_LINES + 1))
  LOG_CHECKPOINT=$end
}

assert_vanilla_failure_errors() {
  local end line
  local -a errors=()
  end=$(log_line_count)
  mapfile -t errors < <(log_errors_between "$LOG_CHECKPOINT" "$end")
  (( ${#errors[@]} > 0 )) || fail 'vanilla-failure window contained no ERROR diagnostic'
  for line in "${errors[@]}"; do
    if [[ "$line" != *reload_future_failure* && "$line" != *'Failed to execute reload'* ]]; then
      printf 'fabric-datamap-reload-smoke: unexpected vanilla-failure ERROR:\n%s\n' "$line" >&2
      fail 'unexpected ERROR output during deliberately broken vanilla reload'
    fi
  done
  ACCOUNTED_ERROR_LINES=$((ACCOUNTED_ERROR_LINES + ${#errors[@]}))
  LOG_CHECKPOINT=$end
}

assert_all_errors_accounted() {
  local total
  total=$(awk '/\[[^]]*ERROR\]/ { count++ } END { print count + 0 }' "$SERVER_DIR/logs/latest.log")
  (( total == ACCOUNTED_ERROR_LINES )) || fail "ERROR accounting mismatch: total=$total accounted=$ACCOUNTED_ERROR_LINES"
}

write_pack_metadata() {
  mkdir -p "$1"
  printf '%s\n' '{"pack":{"pack_format":48,"description":"Mekanism data-map reload smoke"}}' > "$1/pack.mcmeta"
}

printf 'fabric-datamap-reload-smoke: building production and isolated fixture artifacts\n'
FABRIC_SMOKE_STATIC_ONLY=1 "$ROOT/scripts/fabric-packaged-smoke.sh"
"$ROOT/fabric-build.sh" remapDataMapReloadFixtureJar verifyDataMapReloadFixtureIsolation
mapfile -t MOD_JARS < <(find "$ROOT/fabric/build/libs" -maxdepth 1 -type f -name '*.jar' ! -name '*-sources.jar' ! -name '*-dev.jar' | sort)
mapfile -t FIXTURE_JARS < <(find "$ROOT/fabric/build/datamap-reload-fixture/remapped" -maxdepth 1 -type f -name '*.jar' | sort)
[[ ${#MOD_JARS[@]} -eq 1 ]] || fail "expected one production JAR, found ${#MOD_JARS[@]}"
[[ ${#FIXTURE_JARS[@]} -eq 1 ]] || fail "expected one fixture JAR, found ${#FIXTURE_JARS[@]}"
if jar tf "${MOD_JARS[0]}" | grep -Fq 'mekanism/fabric/datamaps/fixture/'; then fail 'fixture leaked into production JAR'; fi
if ! jar tf "${FIXTURE_JARS[0]}" | grep -Fxq 'mekanism/fabric/datamaps/fixture/DataMapReloadFixture.class'; then fail 'fixture JAR is missing its probe'; fi

LAUNCHER="$CACHE_DIR/fabric-server-${MINECRAFT_VERSION}-${LOADER_VERSION}-${INSTALLER_VERSION}.jar"
API_JAR="$CACHE_DIR/fabric-api-${FABRIC_API_VERSION}.jar"
ENERGY_JAR="$CACHE_DIR/energy-${TEAM_REBORN_ENERGY_VERSION}.jar"
fetch "https://meta.fabricmc.net/v2/versions/loader/${MINECRAFT_VERSION}/${LOADER_VERSION}/${INSTALLER_VERSION}/server/jar" "$LAUNCHER"
fetch "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/${FABRIC_API_VERSION}/fabric-api-${FABRIC_API_VERSION}.jar" "$API_JAR"
fetch "https://maven.fabricmc.net/teamreborn/energy/${TEAM_REBORN_ENERGY_VERSION}/energy-${TEAM_REBORN_ENERGY_VERSION}.jar" "$ENERGY_JAR"

SERVER_DIR=$(mktemp -d "${TMPDIR:-/tmp}/mekanism-fabric-datamap-smoke.XXXXXX")
mkdir -p "$SERVER_DIR/mods"
printf 'eula=true\n' > "$SERVER_DIR/eula.txt"
printf 'online-mode=false\nserver-port=0\n' > "$SERVER_DIR/server.properties"
cp "$LAUNCHER" "$SERVER_DIR/fabric-server-launch.jar"
cp "$API_JAR" "$ENERGY_JAR" "${MOD_JARS[0]}" "${FIXTURE_JARS[0]}" "$SERVER_DIR/mods/"
mkfifo "$SERVER_DIR/console.in"
exec 3<>"$SERVER_DIR/console.in"
CONSOLE_FD_OPEN=1
(
  cd "$SERVER_DIR"
  exec setsid java -Xms512M -Xmx2G -jar fabric-server-launch.jar nogui <console.in
) >"$SERVER_DIR/console.log" 2>&1 &
SERVER_PID=$!

wait_for 'MEKANISM_FABRIC_DATAMAP_PROOF phase=startup epoch=1 published=21 skipped=0' 'startup publication'
wait_for 'Done (' 'server readiness'
send_command 'mekanism_datamap_probe'
wait_for 'MEKANISM_DATAMAP_FIXTURE_PROBE epoch=1 entries=21 ones=20 sonic=0.75 session=true' 'epoch 1 query proof'
assert_no_new_errors 'startup'

PACK="$SERVER_DIR/world/datapacks/datamap-high"
write_pack_metadata "$PACK"
MAP="$PACK/data/mekanism/data_maps/damage_type/mekasuit_absorption.json"
mkdir -p "$(dirname "$MAP")"
printf '%s\n' '{"replace":true,"values":{"#mekanism:mekasuit_always_supported":0.5,"minecraft:sonic_boom":{"value":0.25,"replace":true}},"remove":["minecraft:cactus"]}' > "$MAP"
send_command 'reload'
wait_for 'MEKANISM_FABRIC_DATAMAP_PROOF phase=reload epoch=2 published=20 skipped=0' 'two-layer epoch 2 publication'
send_command 'mekanism_datamap_probe'
wait_for 'MEKANISM_DATAMAP_FIXTURE_PROBE epoch=2 entries=20 ones=0 sonic=0.25 session=true' 'epoch 2 query proof'
assert_no_new_errors 'valid high-pack reload'

printf '%s\n' '{"replace":true,"remove":[]}' > "$MAP"
send_command 'reload'
wait_for 'MEKANISM_FABRIC_DATAMAP_PROOF phase=reload epoch=3 published=21 skipped=1' 'malformed-high epoch 3 publication'
wait_for 'Skipping invalid data-map layer mekanism:data_maps/damage_type/mekasuit_absorption.json' 'bounded expected malformed-layer error'
send_command 'mekanism_datamap_probe'
wait_for 'MEKANISM_DATAMAP_FIXTURE_PROBE epoch=3 entries=21 ones=20 sonic=0.75 session=true' 'epoch 3 fresh-low query proof'
assert_malformed_map_errors

BROKEN_REGISTRY="$PACK/data/mekanism/damage_type/reload_future_failure.json"
mkdir -p "$(dirname "$BROKEN_REGISTRY")"
printf '%s\n' '{"message_id":"broken"' > "$BROKEN_REGISTRY"
send_command 'reload'
wait_for 'MEKANISM_FABRIC_DATAMAP_RELOAD_REJECTED success=false epoch_unchanged=3' 'vanilla failed-reload callback'
send_command 'mekanism_datamap_probe'
wait_for 'MEKANISM_DATAMAP_FIXTURE_PROBE epoch=3 entries=21 ones=20 sonic=0.75 session=true' 'failed reload epoch stability'
assert_vanilla_failure_errors

rm -f "$BROKEN_REGISTRY"
printf '%s\n' '{"values":{"#mekanism:mekasuit_always_supported":{"absorption":1.0},"minecraft:sonic_boom":0.75}}' > "$MAP"
send_command 'reload'
wait_for 'MEKANISM_FABRIC_DATAMAP_PROOF phase=reload epoch=4 published=21 skipped=0' 'repaired epoch 4 publication'
send_command 'mekanism_datamap_probe'
wait_for 'MEKANISM_DATAMAP_FIXTURE_PROBE epoch=4 entries=21 ones=20 sonic=0.75 session=true' 'epoch 4 query proof'
assert_no_new_errors 'repaired reload'

send_command 'stop'
wait_for 'MEKANISM_DATAMAP_FIXTURE_STOP session_removed=true' 'owner stop cleanup proof'
exec 3>&-
CONSOLE_FD_OPEN=0
for _ in {1..80}; do kill -0 "$SERVER_PID" 2>/dev/null || break; sleep 0.25; done
kill -0 "$SERVER_PID" 2>/dev/null && fail 'server did not stop normally'
wait "$SERVER_PID"
SERVER_PID=
assert_no_new_errors 'normal stop'
assert_all_errors_accounted
RESULT=0
printf 'fabric-datamap-reload-smoke: PASS (epochs 1-4, layered failure semantics, vanilla failure stability, and stop cleanup)\n'
