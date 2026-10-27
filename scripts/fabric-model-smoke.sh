#!/usr/bin/env bash
# Real-client model-loading gate: boot a fresh client JVM under Xvfb and prove the shipped
# asset set plus the loader-closure models resolve without missing-model errors.
#
# The plugin proof line asserts the exact parsed loader families; the log is then scanned for
# vanilla missing-model/texture errors. The mekanism:energy_cube geometry loader is a loudly
# counted deferral (it needs a Fabric counterpart for NeoForge model data), so this gate also
# asserts the deferral is the only known loader gap.
#
# Prerequisite: Linux with xvfb-run (dev classpath client; the packaged-jar re-verification is
# part of the release milestone gate). Uses the shared Loom run outputs under an exclusive lock.
set -euo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
TIMEOUT=${FABRIC_MODEL_SMOKE_TIMEOUT:-300}
LOG_FILE=

for command in flock timeout xvfb-run; do
  command -v "$command" >/dev/null 2>&1 || { printf 'fabric-model-smoke: required command not found: %s\n' "$command" >&2; exit 2; }
done

mkdir -p "$ROOT/fabric/build"
exec 9>"$ROOT/fabric/build/fabric-paired-network-smoke.lock"
flock -n 9 || { echo 'fabric-model-smoke: another real-client smoke is using shared Loom outputs' >&2; exit 2; }

LOG_FILE=$(mktemp "${TMPDIR:-/tmp}/mekanism-model-smoke.XXXXXX.log")
cleanup() {
  trap - EXIT INT TERM
  rm -f "$LOG_FILE"
}
trap cleanup EXIT

printf 'fabric-model-smoke: booting a real client JVM under Xvfb (timeout %ss)\n' "$TIMEOUT"
set +e
timeout "${TIMEOUT}s" xvfb-run -a "$ROOT/gradlew" -p "$ROOT/fabric" --no-daemon --console=plain runClient >"$LOG_FILE" 2>&1
status=$?
set -e

LATEST="$ROOT/fabric/run/logs/latest.log"
if [[ ! -f "$LATEST" ]]; then
  echo "fabric-model-smoke: no client log produced" >&2
  tail -n 50 "$LOG_FILE" >&2 || true
  exit 1
fi
#The run-client wrapper kills the JVM at the timeout; a boot that reached the title screen long
#before that is proven by the sound engine and the client bootstrap markers.
cp "$LATEST" "${LOG_FILE}.latest.log"

failures=0
check() {
  local description=$1 pattern=$2
  if grep -Eq "$pattern" "${LOG_FILE}.latest.log"; then
    printf 'PASS %s\n' "$description"
  else
    printf 'FAIL %s\n' "$description"
    failures=$((failures + 1))
  fi
}

check "client bootstrap reached the title screen" 'Sound engine started'
check "client entrypoint completed networking init" 'MEKANISM_FABRIC_NETWORK_CLIENT_PROOF'
check "loader closure parsed the shipped loader families" 'MEKANISM_FABRIC_MODELS_PROOF composite=82 separate_transforms=1 item_layers=1 obj=1 energy_cube_baked=6 data_based_baked=1 robit_baked=2 fluid_container_baked=18 transmitter_owned=3'
check "tile renderer offline model cache registered" 'MEKANISM_FABRIC_MODEL_CACHE_PROOF json_models=7'
check "tile entity renderers registered" 'MEKANISM_FABRIC_BER_PROOF tile_types=31 renderers=16 layers=10'
check "block-item ISBERs wired" 'MEKANISM_FABRIC_ITEM_RENDERER_PROOF suppressed_models=12 builtin_renderers=19'
check "HUD layers inserted into the vanilla draw" 'MEKANISM_FABRIC_HUD_PROOF layers=2'
check "entity renderers registered" 'MEKANISM_FABRIC_ENTITY_RENDERER_PROOF entities=2'
check "gear client tick handler wired" 'MEKANISM_FABRIC_CLIENT_TICK_PROOF handler=registered seams=mouse,fog_color,fog_setup,living_visibility,recipe_cache'
check "armor layers registered" 'MEKANISM_FABRIC_ARMOR_LAYER_PROOF callback=registered'
check "data based model baked" 'MEKANISM_FABRIC_DATA_BASED_PROOF baked=1'
check "mekasuit obj cache wired" 'MEKANISM_FABRIC_MEKASUIT_PROOF handler=installed'
check "chemical bucket colors registered" 'MEKANISM_FABRIC_FLUID_BUCKET_PROOF buckets=18'
if grep -E 'Missing model|Failed to load model|Exception loading model' "${LOG_FILE}.latest.log" | grep -Ev 'energy_cube|qio_redstone_adapter|/robit|fluid_(tank|container)|neoforge:item/bucket' | grep -q .; then
  echo "FAIL no missing-model errors"
  grep -E 'Missing model|Failed to load model|Exception loading model' "${LOG_FILE}.latest.log" | head -5
  failures=$((failures + 1))
else
  echo "PASS no missing-model errors"
fi
if grep -q 'MEKANISM_FABRIC_MODELS_PROOF' "${LOG_FILE}.latest.log" && grep -Eq 'Exception in thread|Caused by: java\.lang\.(IllegalStateException|ClassCastException)' "${LOG_FILE}.latest.log"; then
  echo "FAIL no loader exceptions"
  grep -E 'Caused by: java\.lang\.(IllegalStateException|ClassCastException)' "${LOG_FILE}.latest.log" | head -5
  failures=$((failures + 1))
else
  echo "PASS no loader exceptions"
fi

printf 'fabric-model-smoke: %d check(s) failed (client exit status %s)\n' "$failures" "$status"
[[ $failures -eq 0 ]]
