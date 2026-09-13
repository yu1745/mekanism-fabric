#!/usr/bin/env bash
# Exercise the recipe ingredient/cache/reload vertical with one real dedicated server and one reconnecting real client JVM.
set -euo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
TIMEOUT=${FABRIC_RECIPE_RUNTIME_TIMEOUT:-300}
KEEP_RUN=${FABRIC_RECIPE_RUNTIME_KEEP_RUN:-0}
LOG_ROOT=${FABRIC_RECIPE_RUNTIME_LOG_DIR:-$ROOT/build/fabric-recipe-runtime-smoke}
USERNAME=${FABRIC_RECIPE_RUNTIME_USERNAME:-MekRecipe}
RUN_ID=$(date -u +%Y%m%dT%H%M%SZ)-$$
RUN_RELATIVE="run/recipe-runtime-$RUN_ID"
RUN_ROOT="$ROOT/fabric/$RUN_RELATIVE"
SERVER_DIR="$RUN_ROOT/server"
CLIENT_DIR="$RUN_ROOT/client"
CONTROL="$RUN_ROOT/control"
DIAGNOSTIC_DIR="$LOG_ROOT/$RUN_ID"
SCOPE_PREFIX="mekanism-recipe-$RUN_ID"
SERVER_SCOPE="$SCOPE_PREFIX-server"
CLIENT_SCOPE="$SCOPE_PREFIX-client"
SERVER_PID=
CLIENT_PID=
RESULT=1

for command in flock java python3 systemctl systemd-run xvfb-run Xvfb; do
  command -v "$command" >/dev/null 2>&1 || { echo "fabric-recipe-runtime-smoke: required command not found: $command" >&2; exit 2; }
done
systemctl --user show-environment >/dev/null 2>&1 || { echo 'fabric-recipe-runtime-smoke: a running systemd user manager is required' >&2; exit 2; }
[[ "$TIMEOUT" =~ ^[1-9][0-9]*$ ]] || { echo 'fabric-recipe-runtime-smoke: timeout must be a positive integer' >&2; exit 2; }
[[ "$USERNAME" =~ ^[A-Za-z0-9_]{1,16}$ ]] || { echo 'fabric-recipe-runtime-smoke: invalid username' >&2; exit 2; }

mkdir -p "$ROOT/fabric/build" "$SERVER_DIR" "$CLIENT_DIR" "$CONTROL" "$DIAGNOSTIC_DIR"
exec 9>"$ROOT/fabric/build/fabric-real-client-smoke.lock"
flock -n 9 || { echo 'fabric-recipe-runtime-smoke: another real-client smoke owns the Loom outputs' >&2; exit 2; }

terminate_scope() {
  local scope=${1:-} pid=${2:-}
  if [[ -n "$scope" ]]; then
    systemctl --user stop "$scope.scope" >/dev/null 2>&1 || true
    for _ in {1..40}; do systemctl --user is-active --quiet "$scope.scope" 2>/dev/null || break; sleep 0.25; done
  fi
  if [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null; then
    kill -TERM "$pid" 2>/dev/null || true
    for _ in {1..20}; do kill -0 "$pid" 2>/dev/null || break; sleep 0.25; done
    kill -KILL "$pid" 2>/dev/null || true
  fi
  [[ -n "$pid" ]] && wait "$pid" 2>/dev/null || true
}

finish() {
  local status=$?
  trap - EXIT INT TERM
  terminate_scope "$CLIENT_SCOPE" "$CLIENT_PID"
  terminate_scope "$SERVER_SCOPE" "$SERVER_PID"
  [[ -f "$SERVER_DIR/logs/latest.log" ]] && cp "$SERVER_DIR/logs/latest.log" "$DIAGNOSTIC_DIR/server-latest.log" || true
  [[ -f "$CLIENT_DIR/logs/latest.log" ]] && cp "$CLIENT_DIR/logs/latest.log" "$DIAGNOSTIC_DIR/client-latest.log" || true
  [[ -f "$RUN_ROOT/server-console.log" ]] && cp "$RUN_ROOT/server-console.log" "$DIAGNOSTIC_DIR/" || true
  [[ -f "$RUN_ROOT/client-console.log" ]] && cp "$RUN_ROOT/client-console.log" "$DIAGNOSTIC_DIR/" || true
  if [[ "$KEEP_RUN" == 1 ]]; then
    echo "fabric-recipe-runtime-smoke: retained run directory: $RUN_ROOT" >&2
  else
    rm -rf "$RUN_ROOT"
  fi
  echo "fabric-recipe-runtime-smoke: diagnostics: $DIAGNOSTIC_DIR" >&2
  [[ $RESULT -eq 0 && $status -eq 0 ]] && exit 0
  (( status == 0 )) && status=1
  exit "$status"
}
trap finish EXIT INT TERM

select_port() {
  python3 - <<'PY'
import socket
with socket.socket() as sock:
    sock.bind(("127.0.0.1", 0))
    print(sock.getsockname()[1])
PY
}

fail() {
  echo "fabric-recipe-runtime-smoke: $1" >&2
  [[ -f "$SERVER_DIR/logs/latest.log" ]] && tail -n 100 "$SERVER_DIR/logs/latest.log" >&2 || true
  [[ -f "$CLIENT_DIR/logs/latest.log" ]] && tail -n 100 "$CLIENT_DIR/logs/latest.log" >&2 || true
  exit 1
}

assert_alive_clean() {
  kill -0 "$SERVER_PID" 2>/dev/null || fail 'server exited unexpectedly'
  if [[ -n "$CLIENT_PID" ]]; then kill -0 "$CLIENT_PID" 2>/dev/null || fail 'client exited unexpectedly'; fi
  local pattern='(\[[^]]+/ERROR\]|Exception in thread|Could not execute entrypoint|Failed to start the minecraft server|---- Minecraft Crash Report ----|NoClassDefFoundError|ClassNotFoundException)'
  grep -Eiq "$pattern" "$SERVER_DIR/logs/latest.log" 2>/dev/null && fail 'server crash signature detected'
  grep -Eiq "$pattern" "$CLIENT_DIR/logs/latest.log" 2>/dev/null && fail 'client crash signature detected'
  return 0
}

wait_log() {
  local file=$1 pattern=$2 description=$3 deadline=$((SECONDS + TIMEOUT))
  while (( SECONDS < deadline )); do
    grep -Fq "$pattern" "$file" 2>/dev/null && return 0
    assert_alive_clean
    sleep 0.5
  done
  fail "timed out waiting for $description"
}

wait_marker() {
  local marker=$1 description=$2 deadline=$((SECONDS + TIMEOUT))
  while (( SECONDS < deadline )); do
    [[ -f "$CONTROL/$marker" ]] && return 0
    assert_alive_clean
    sleep 0.5
  done
  fail "timed out waiting for $description"
}

PORT=$(select_port)
printf 'eula=true\n' > "$SERVER_DIR/eula.txt"
cat > "$SERVER_DIR/server.properties" <<EOF
online-mode=false
server-ip=127.0.0.1
server-port=$PORT
motd=Mekanism recipe runtime smoke
max-players=1
view-distance=2
simulation-distance=2
sync-chunk-writes=false
EOF
printf 'onboardAccessibility:false\n' > "$CLIENT_DIR/options.txt"
printf '%s\n' "port=$PORT" "username=$USERNAME" "timeout=$TIMEOUT" > "$DIAGNOSTIC_DIR/run-info.txt"

# Initial fixture recipe: item compound (tag + item), fluid tag, chemical single, loaded by the real recipe manager.
PACK="$SERVER_DIR/world/datapacks/recipe-runtime"
mkdir -p "$PACK/data/mekanism_recipe_fixture/recipe"
printf '%s\n' '{"pack":{"pack_format":48,"description":"Mekanism recipe runtime initial fixture"}}' > "$PACK/pack.mcmeta"
printf '%s\n' '{"type":"mekanism_recipe_fixture:fusion","item_input":{"type":"neoforge:compound","children":[{"tag":"minecraft:planks"},{"item":"minecraft:stick"}],"count":1},"fluid_input":{"amount":100,"tag":"minecraft:water"},"chemical_input":{"amount":10,"chemical":"mekanism:hydrogen"}}' > "$PACK/data/mekanism_recipe_fixture/recipe/initial.json"

printf 'fabric-recipe-runtime-smoke: starting isolated server on 127.0.0.1:%s\n' "$PORT"
systemd-run --user --scope --collect --quiet --unit="$SERVER_SCOPE" \
  "$ROOT/gradlew" -p "$ROOT/fabric" --no-daemon --console=plain \
  -Dmekanism.recipe.serverDir="$RUN_RELATIVE/server" \
  -Dmekanism.recipe.runtimeRoot="$CONTROL" \
  runRecipeRuntimeServer >"$RUN_ROOT/server-console.log" 2>&1 &
SERVER_PID=$!
wait_log "$SERVER_DIR/logs/latest.log" 'Done (' 'server readiness'
wait_marker 'server-initial.complete' 'server initial recipe proof'
wait_log "$SERVER_DIR/logs/latest.log" 'MEKANISM_FABRIC_RECIPE_CACHE_PROOF phase=start epoch=1' 'server lifecycle start marker'
wait_log "$SERVER_DIR/logs/latest.log" 'MEKANISM_FABRIC_INGREDIENT_PROOF' 'server ingredient service proof marker'

printf 'fabric-recipe-runtime-smoke: starting physical client JVM\n'
systemd-run --user --scope --collect --quiet --unit="$CLIENT_SCOPE" \
  xvfb-run -a "$ROOT/gradlew" -p "$ROOT/fabric" --no-daemon --console=plain \
  -Dmekanism.recipe.clientDir="$RUN_RELATIVE/client" \
  -Dmekanism.recipe.runtimeRoot="$CONTROL" \
  -Dmekanism.recipe.address="127.0.0.1:$PORT" \
  -Dmekanism.recipe.username="$USERNAME" \
  runRecipeRuntimeClient >"$RUN_ROOT/client-console.log" 2>&1 &
CLIENT_PID=$!
wait_marker 'client-initial.complete' 'client initial recipe epoch proof'
wait_marker 'client-thread-isolation.complete' 'client thread isolation proof'
wait_log "$SERVER_DIR/logs/latest.log" "$USERNAME joined the game" 'client play join'

# Successful /reload: replacement recipe pack removes the stick compound child and uses iron instead.
REPLACEMENT="$SERVER_DIR/world/datapacks/recipe-runtime-replacement"
mkdir -p "$REPLACEMENT/data/mekanism_recipe_fixture/recipe"
printf '%s\n' '{"pack":{"pack_format":48,"description":"Mekanism recipe runtime replacement fixture"}}' > "$REPLACEMENT/pack.mcmeta"
printf '%s\n' '{"type":"mekanism_recipe_fixture:fusion","item_input":{"type":"neoforge:compound","children":[{"tag":"minecraft:planks"},{"item":"minecraft:iron_ingot"}],"count":1},"fluid_input":{"amount":100,"tag":"minecraft:water"},"chemical_input":{"amount":10,"chemical":"mekanism:hydrogen"}}' > "$REPLACEMENT/data/mekanism_recipe_fixture/recipe/replacement.json"
rm -rf "$PACK"
printf 'reload\n' > "$CONTROL/reload.trigger"
wait_marker 'reload.complete' 'successful recipe reload'
wait_marker 'server-reload-verified.complete' 'server reload verification'
wait_marker 'client-reload.complete' 'client tail-mixin reload epoch'
wait_log "$SERVER_DIR/logs/latest.log" 'MEKANISM_RECIPE_RUNTIME_SERVER phase=reload recipe_epoch=2 old_removed=true replacement_exposed=true' 'server reload marker'

# Failed reload: a fixture-only synchronous reload listener rejects this marker resource. This exercises a real
# END_DATA_PACK_RELOAD success=false path without depending on recipe/dynamic-registry parse errors that vanilla may skip.
FAILURE_MARKER="$REPLACEMENT/data/mekanism_recipe_fixture/reload_failure.marker"
mkdir -p "$(dirname "$FAILURE_MARKER")"
printf '%s\n' 'intentional fixture-only recipe reload failure' > "$FAILURE_MARKER"
printf 'reload\n' > "$CONTROL/fail-reload.trigger"
wait_log "$SERVER_DIR/logs/latest.log" 'MEKANISM_RECIPE_RUNTIME_SERVER phase=failed_reload_trigger marker_present=true' 'failed reload listener trigger'
wait_marker 'fail-reload.complete' 'failed recipe reload'
wait_log "$SERVER_DIR/logs/latest.log" 'MEKANISM_RECIPE_RUNTIME_SERVER phase=failed_reload retained_epoch=2 cache_usable=true' 'failed reload retention marker'

# Client reconnect in the same physical JVM: a fresh manager must start at epoch one again.
wait_marker 'client-disconnect.complete' 'client play disconnect cleanup'
printf 'reconnect\n' > "$CONTROL/reconnect.signal"
wait_marker 'client-reconnect.complete' 'fresh-manager client reconnect proof'
wait_log "$SERVER_DIR/logs/latest.log" 'MEKANISM_RECIPE_RUNTIME_CLIENT phase=reconnect fresh_manager=true epoch_reset=true prior_candidates_unreachable=true' 'client reconnect marker'

wait_log "$SERVER_DIR/logs/latest.log" 'MEKANISM_RECIPE_RUNTIME_SERVER phase=initial recipe_epoch=1 chemical_epoch=1 recipe_loaded=true cache_match=true mismatch_rejected=true chemical_stable=true' 'server initial vertical marker'

# The fixture recipe type, serializer, classes, and JSON must be absent from the release JAR.
RELEASE_JAR=$(find "$ROOT/fabric/build/libs" -maxdepth 1 -type f -name '*.jar' ! -name '*-sources.jar' ! -name '*-dev.jar' | sort)
[[ $(printf '%s\n' "$RELEASE_JAR" | wc -l) -eq 1 ]] || fail 'expected exactly one release jar'
if jar tf "$RELEASE_JAR" | grep -Eq 'mekanism_recipe_fixture|mekanism/fabric/recipe/fixture/|data/mekanism/recipe/'; then
  fail 'fixture recipe type, serializer, classes, or production recipe data leaked into the release jar'
fi
if jar tf "$RELEASE_JAR" | grep -Fq 'mekanism/fabric/recipe/FabricRecipeCacheClientLifecycle.class'; then
  :
else
  fail 'client lifecycle class missing from release jar'
fi

RESULT=0
printf 'fabric-recipe-runtime-smoke: PASS (real recipe load, generic cache, hot-epoch stability, reload advance/retention, client tail-mixin epochs, reconnect freshness, fixture isolation passed)\n'
grep -E 'MEKANISM_(RECIPE_RUNTIME_|FABRIC_RECIPE_CACHE)' "$SERVER_DIR/logs/latest.log" "$CLIENT_DIR/logs/latest.log" | tail -n 14
