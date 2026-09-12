#!/usr/bin/env bash
# Exercise the complete chemical registry/map/tank/snapshot vertical with one real server and one reconnecting real client JVM.
set -euo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
TIMEOUT=${FABRIC_CHEMICAL_RUNTIME_TIMEOUT:-300}
KEEP_RUN=${FABRIC_CHEMICAL_RUNTIME_KEEP_RUN:-0}
LOG_ROOT=${FABRIC_CHEMICAL_RUNTIME_LOG_DIR:-$ROOT/build/fabric-chemical-runtime-smoke}
USERNAME=${FABRIC_CHEMICAL_RUNTIME_USERNAME:-MekChemical}
RUN_ID=$(date -u +%Y%m%dT%H%M%SZ)-$$
RUN_RELATIVE="run/chemical-runtime-$RUN_ID"
RUN_ROOT="$ROOT/fabric/$RUN_RELATIVE"
SERVER_DIR="$RUN_ROOT/server"
CLIENT_DIR="$RUN_ROOT/client"
CONTROL="$RUN_ROOT/control"
DIAGNOSTIC_DIR="$LOG_ROOT/$RUN_ID"
SCOPE_PREFIX="mekanism-chemical-$RUN_ID"
SERVER_SCOPE="$SCOPE_PREFIX-server"
CLIENT_SCOPE="$SCOPE_PREFIX-client"
SERVER_PID=
CLIENT_PID=
RESULT=1

for command in flock java python3 systemctl systemd-run xvfb-run Xvfb; do
  command -v "$command" >/dev/null 2>&1 || { echo "fabric-chemical-runtime-smoke: required command not found: $command" >&2; exit 2; }
done
systemctl --user show-environment >/dev/null 2>&1 || { echo 'fabric-chemical-runtime-smoke: a running systemd user manager is required' >&2; exit 2; }
[[ "$TIMEOUT" =~ ^[1-9][0-9]*$ ]] || { echo 'fabric-chemical-runtime-smoke: timeout must be a positive integer' >&2; exit 2; }
[[ "$USERNAME" =~ ^[A-Za-z0-9_]{1,16}$ ]] || { echo 'fabric-chemical-runtime-smoke: invalid username' >&2; exit 2; }

mkdir -p "$ROOT/fabric/build" "$SERVER_DIR" "$CLIENT_DIR" "$CONTROL" "$DIAGNOSTIC_DIR"
exec 9>"$ROOT/fabric/build/fabric-real-client-smoke.lock"
flock -n 9 || { echo 'fabric-chemical-runtime-smoke: another real-client smoke owns the Loom outputs' >&2; exit 2; }

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
    echo "fabric-chemical-runtime-smoke: retained run directory: $RUN_ROOT" >&2
  else
    rm -rf "$RUN_ROOT"
  fi
  echo "fabric-chemical-runtime-smoke: diagnostics: $DIAGNOSTIC_DIR" >&2
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
  echo "fabric-chemical-runtime-smoke: $1" >&2
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
motd=Mekanism chemical runtime smoke
max-players=1
view-distance=2
simulation-distance=2
sync-chunk-writes=false
EOF
printf 'onboardAccessibility:false\n' > "$CLIENT_DIR/options.txt"
printf '%s\n' "port=$PORT" "username=$USERNAME" "timeout=$TIMEOUT" > "$DIAGNOSTIC_DIR/run-info.txt"

printf 'fabric-chemical-runtime-smoke: starting isolated server on 127.0.0.1:%s\n' "$PORT"
systemd-run --user --scope --collect --quiet --unit="$SERVER_SCOPE" \
  "$ROOT/gradlew" -p "$ROOT/fabric" --no-daemon --console=plain \
  -Dmekanism.chemical.serverDir="$RUN_RELATIVE/server" \
  -Dmekanism.chemical.runtimeRoot="$CONTROL" \
  runChemicalRuntimeServer >"$RUN_ROOT/server-console.log" 2>&1 &
SERVER_PID=$!
wait_log "$SERVER_DIR/logs/latest.log" 'Done (' 'server readiness'
wait_marker 'server-initial.complete' 'server initial chemical proof'

printf 'fabric-chemical-runtime-smoke: starting physical client JVM\n'
systemd-run --user --scope --collect --quiet --unit="$CLIENT_SCOPE" \
  xvfb-run -a "$ROOT/gradlew" -p "$ROOT/fabric" --no-daemon --console=plain \
  -Dmekanism.chemical.clientDir="$RUN_RELATIVE/client" \
  -Dmekanism.chemical.runtimeRoot="$CONTROL" \
  -Dmekanism.chemical.address="127.0.0.1:$PORT" \
  -Dmekanism.chemical.username="$USERNAME" \
  runChemicalRuntimeClient >"$RUN_ROOT/client-console.log" 2>&1 &
CLIENT_PID=$!
wait_marker 'client-initial.complete' 'initial client authoritative snapshot proof'
wait_marker 'client-disconnect.complete' 'physical-client disconnect cleanup'

PACK="$SERVER_DIR/world/datapacks/chemical-runtime"
mkdir -p "$PACK/data/mekanism/data_maps/mekanism/chemical"
printf '%s\n' '{"pack":{"pack_format":48,"description":"Mekanism chemical runtime removal"}}' > "$PACK/pack.mcmeta"
printf '%s\n' '{"replace":true,"values":{}}' > "$PACK/data/mekanism/data_maps/mekanism/chemical/chemical_attribute_radioactivity.json"
printf 'reload\n' > "$CONTROL/reload.trigger"
wait_marker 'reload.complete' 'authoritative chemical removal reload'
printf 'reconnect\n' > "$CONTROL/reconnect.signal"
wait_marker 'client-reconnect.complete' 'same-process second configuration snapshot and join'

wait_log "$SERVER_DIR/logs/latest.log" 'MEKANISM_CHEMICAL_RUNTIME_SERVER phase=initial epoch=1 entries=65 registration_order=true tank_rejected=true nbt_long=true stream_long=true tooltip=true unknown_snapshot_rejected=true' 'server initial vertical marker'
wait_log "$SERVER_DIR/logs/latest.log" 'MEKANISM_CHEMICAL_RUNTIME_SERVER phase=reload epoch=2 removal=true tank_accepted=true tooltip_removed=true' 'server reload marker'
wait_log "$CLIENT_DIR/logs/latest.log" 'MEKANISM_CHEMICAL_RUNTIME_CLIENT phase=disconnect context_cleared=true' 'client cleanup marker'
wait_log "$CLIENT_DIR/logs/latest.log" 'MEKANISM_CHEMICAL_RUNTIME_CLIENT phase=reconnect epoch=2 same_process=true cleanup=true tank_accepted=true tooltip_removed=true owner_thread_isolated=true' 'client reconnect marker'

mapfile -t sessions < <(sed -n 's/.*MEKANISM_FABRIC_CHEMICAL_ACK session=\([^ ]*\) epoch=.*/\1/p' "$SERVER_DIR/logs/latest.log")
[[ ${#sessions[@]} -eq 2 && "${sessions[0]}" != "${sessions[1]}" ]] || fail 'expected two distinct acknowledged chemical sessions in one client JVM'
[[ $(grep -Fc "$USERNAME joined the game" "$SERVER_DIR/logs/latest.log") -eq 2 ]] || fail 'client did not join twice'
[[ $(grep -Fc "$USERNAME left the game" "$SERVER_DIR/logs/latest.log") -ge 1 ]] || fail 'first connection did not leave before reconnect'

RESULT=0
printf 'fabric-chemical-runtime-smoke: PASS (registry order/remap, initial+removed attributes, tank true-long codecs, tooltip sync, unknown rejection, disconnect and same-JVM reconnect passed)\n'
grep -E 'MEKANISM_(FABRIC_CHEMICAL_ACK|CHEMICAL_RUNTIME_)' "$SERVER_DIR/logs/latest.log" "$CLIENT_DIR/logs/latest.log" | tail -n 12
