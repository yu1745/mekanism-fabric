#!/usr/bin/env bash
# Exercise the base-tile reduced-update lifecycle with one real dedicated server and one reconnecting real client JVM.
#
# The gate proves all three real dispatch paths: the initial chunk tag, the vanilla ClientboundBlockEntityDataPacket path, and the custom mekanism:update_tile payload.
# For every path the client must apply the reduced value without running the fixture subclass full reader and without clearing its save-only sentinel, and ordinary
# vanilla block entities must keep their own handling. A real reconnect then proves the reduced updates resolve the current owner world.
set -euo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
TIMEOUT=${FABRIC_TILE_RUNTIME_TIMEOUT:-300}
KEEP_RUN=${FABRIC_TILE_RUNTIME_KEEP_RUN:-0}
LOG_ROOT=${FABRIC_TILE_RUNTIME_LOG_DIR:-$ROOT/build/fabric-tile-runtime-smoke}
USERNAME=${FABRIC_TILE_RUNTIME_USERNAME:-MekTile}
RUN_ID=$(date -u +%Y%m%dT%H%M%SZ)-$$
RUN_RELATIVE="run/tile-runtime-$RUN_ID"
RUN_ROOT="$ROOT/fabric/$RUN_RELATIVE"
SERVER_DIR="$RUN_ROOT/server"
CLIENT_DIR="$RUN_ROOT/client"
CONTROL="$RUN_ROOT/control"
DIAGNOSTIC_DIR="$LOG_ROOT/$RUN_ID"
SCOPE_PREFIX="mekanism-tile-$RUN_ID"
SERVER_SCOPE="$SCOPE_PREFIX-server"
CLIENT_SCOPE="$SCOPE_PREFIX-client"
SERVER_PID=
CLIENT_PID=
RESULT=1

for command in flock java python3 systemctl systemd-run xvfb-run Xvfb; do
  command -v "$command" >/dev/null 2>&1 || { echo "fabric-tile-runtime-smoke: required command not found: $command" >&2; exit 2; }
done
systemctl --user show-environment >/dev/null 2>&1 || { echo 'fabric-tile-runtime-smoke: a running systemd user manager is required' >&2; exit 2; }
[[ "$TIMEOUT" =~ ^[1-9][0-9]*$ ]] || { echo 'fabric-tile-runtime-smoke: timeout must be a positive integer' >&2; exit 2; }
[[ "$USERNAME" =~ ^[A-Za-z0-9_]{1,16}$ ]] || { echo 'fabric-tile-runtime-smoke: invalid username' >&2; exit 2; }

mkdir -p "$ROOT/fabric/build" "$SERVER_DIR" "$CLIENT_DIR" "$CONTROL" "$DIAGNOSTIC_DIR"
exec 9>"$ROOT/fabric/build/fabric-real-client-smoke.lock"
flock -n 9 || { echo 'fabric-tile-runtime-smoke: another real-client smoke owns the Loom outputs' >&2; exit 2; }

"$ROOT/gradlew" -p "$ROOT/fabric" --no-daemon --console=plain remapJar verifyTileUpdateRuntimeFixtureIsolation

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
    echo "fabric-tile-runtime-smoke: retained run directory: $RUN_ROOT" >&2
  else
    rm -rf "$RUN_ROOT"
  fi
  echo "fabric-tile-runtime-smoke: diagnostics: $DIAGNOSTIC_DIR" >&2
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
  echo "fabric-tile-runtime-smoke: $1" >&2
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
motd=Mekanism tile update runtime smoke
max-players=1
view-distance=2
simulation-distance=2
sync-chunk-writes=false
EOF
printf 'onboardAccessibility:false\n' > "$CLIENT_DIR/options.txt"
printf '%s\n' "port=$PORT" "username=$USERNAME" "timeout=$TIMEOUT" > "$DIAGNOSTIC_DIR/run-info.txt"

printf 'fabric-tile-runtime-smoke: starting isolated server on 127.0.0.1:%s\n' "$PORT"
systemd-run --user --scope --collect --quiet --unit="$SERVER_SCOPE" \
  "$ROOT/gradlew" -p "$ROOT/fabric" --no-daemon --console=plain \
  -Dmekanism.tile.serverDir="$RUN_RELATIVE/server" \
  -Dmekanism.tile.runtimeRoot="$CONTROL" \
  runTileUpdateRuntimeServer >"$RUN_ROOT/server-console.log" 2>&1 &
SERVER_PID=$!
wait_log "$SERVER_DIR/logs/latest.log" 'Done (' 'server readiness'
wait_marker 'server-prepared.complete' 'server full-load restoration proof'

printf 'fabric-tile-runtime-smoke: starting physical client JVM\n'
systemd-run --user --scope --collect --quiet --unit="$CLIENT_SCOPE" \
  xvfb-run -a "$ROOT/gradlew" -p "$ROOT/fabric" --no-daemon --console=plain \
  -Dmekanism.tile.clientDir="$RUN_RELATIVE/client" \
  -Dmekanism.tile.runtimeRoot="$CONTROL" \
  -Dmekanism.tile.address="127.0.0.1:$PORT" \
  -Dmekanism.tile.username="$USERNAME" \
  runTileUpdateRuntimeClient >"$RUN_ROOT/client-console.log" 2>&1 &
CLIENT_PID=$!
wait_log "$SERVER_DIR/logs/latest.log" "$USERNAME joined the game" 'client play join'
wait_marker 'server-player-joined.complete' 'server tracking proof'
wait_marker 'client-chunk.complete' 'initial chunk reduced-tag dispatch'
wait_marker 'client-vanilla.complete' 'vanilla block-entity packet reduced dispatch'
wait_marker 'client-custom.complete' 'custom update_tile payload dispatch'
wait_marker 'server-disconnected.complete' 'server forced disconnect for reconnect proof'
wait_marker 'client-reconnect-request.complete' 'client observed the disconnect'
printf 'reconnect\n' > "$CONTROL/reconnect.signal"
wait_marker 'client-reconnect.complete' 'same-JVM reconnect reduced dispatch'
wait_marker 'client-reconnect-payload.complete' 'post-reconnect custom payload dispatch'
wait_marker 'server-reconnect.complete' 'server reconnect verification'

wait_log "$SERVER_DIR/logs/latest.log" 'MEKANISM_TILE_RUNTIME_SERVER phase=prepared tile=BlockPos{x=0, y=100, z=0} full_restore=true save_only_restored=true' 'server full restoration marker'
wait_log "$SERVER_DIR/logs/latest.log" 'MEKANISM_TILE_RUNTIME_SERVER phase=vanilla_packet_sent reduced=11' 'server vanilla packet marker'
wait_log "$SERVER_DIR/logs/latest.log" 'MEKANISM_TILE_RUNTIME_SERVER phase=custom_payload_sent reduced=22' 'server custom payload marker'
wait_log "$SERVER_DIR/logs/latest.log" 'MEKANISM_TILE_RUNTIME_SERVER phase=reconnect_verified' 'server reconnect marker'
wait_log "$CLIENT_DIR/logs/latest.log" 'MEKANISM_TILE_RUNTIME_CLIENT phase=initial_chunk reduced=3 full_loads=0' 'client initial chunk marker'
wait_log "$CLIENT_DIR/logs/latest.log" 'MEKANISM_TILE_RUNTIME_CLIENT phase=vanilla_packet reduced=11 full_loads=0' 'client vanilla packet marker'
wait_log "$CLIENT_DIR/logs/latest.log" 'MEKANISM_TILE_RUNTIME_CLIENT phase=custom_payload reduced=22 full_loads=0' 'client custom payload marker'
wait_log "$CLIENT_DIR/logs/latest.log" 'MEKANISM_TILE_RUNTIME_CLIENT phase=reconnect reduced=22 full_loads=0 owner_world_current=true' 'client reconnect marker'
wait_log "$CLIENT_DIR/logs/latest.log" 'MEKANISM_TILE_RUNTIME_CLIENT phase=reconnect_payload reduced=33 full_loads=0 owner_world_current=true' 'client post-reconnect payload marker'

[[ $(grep -Fc "$USERNAME joined the game" "$SERVER_DIR/logs/latest.log") -eq 2 ]] || fail 'client did not join twice across the reconnect'
[[ $(grep -Fc "$USERNAME left the game" "$SERVER_DIR/logs/latest.log") -ge 1 ]] || fail 'the first connection did not leave before the reconnect'

# The fixture block/entity, its type id, and its three classes must be absent from the release JAR.
RELEASE_JAR=$(find "$ROOT/fabric/build/libs" -maxdepth 1 -type f -name '*.jar' ! -name '*-sources.jar' ! -name '*-dev.jar' | sort)
[[ $(printf '%s\n' "$RELEASE_JAR" | wc -l) -eq 1 ]] || fail 'expected exactly one release jar'
if jar tf "$RELEASE_JAR" | grep -Eq 'mekanism_tile_fixture|mekanism/fabric/tile/fixture/'; then
  fail 'fixture tile class family leaked into the release jar'
fi
for required in \
    'mekanism/common/tile/base/TileEntityUpdateable.class' \
    'mekanism/common/tile/interfaces/ITileWrapper.class' \
    'mekanism/common/network/to_client/PacketUpdateTile.class' \
    'mekanism/fabric/network/FabricTileUpdateNetworking.class' \
    'mekanism/fabric/network/FabricTileUpdateHandler.class' \
    'mekanism/fabric/mixin/tile/LevelChunkMixin.class' \
    'mekanism/fabric/mixin/tile/ClientPacketListenerTileMixin.class'; do
  jar tf "$RELEASE_JAR" | grep -Fxq "$required" || fail "release jar is missing $required"
done

RESULT=0
printf 'fabric-tile-runtime-smoke: PASS (initial chunk reduced tag, vanilla BE packet dispatched through onDataPacket not bypassing it, custom update_tile payload through its own receiver, full-load separation, vanilla control block entity, owner-world reconnect, fixture isolation)\n'
grep -E 'MEKANISM_TILE_RUNTIME_' "$SERVER_DIR/logs/latest.log" "$CLIENT_DIR/logs/latest.log" | tail -n 16
