#!/usr/bin/env bash
# Exercise the configuration handshake with two disposable Loom client joins.
set -euo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
TIMEOUT=${FABRIC_PAIRED_SMOKE_TIMEOUT:-180}
KEEP_RUN=${FABRIC_PAIRED_SMOKE_KEEP_RUN:-0}
LOG_ROOT=${FABRIC_PAIRED_SMOKE_LOG_DIR:-$ROOT/build/fabric-paired-network-smoke}
USERNAME=${FABRIC_PAIRED_SMOKE_USERNAME:-MekanismSmoke}
RUN_ROOT=
RUN_RELATIVE=
SERVER_DIR=
SERVER_PID=
SERVER_SCOPE=
CLIENT_PID=
CLIENT_SCOPE=
CLIENT_NUMBER=0
RESULT=1
DEADLINE=0

for command in flock java python3 systemctl systemd-run xvfb-run Xvfb; do
  if ! command -v "$command" >/dev/null 2>&1; then
    printf 'fabric-paired-network-smoke: required command not found: %s\n' "$command" >&2
    exit 2
  fi
done
if ! systemctl --user show-environment >/dev/null 2>&1; then
  echo 'fabric-paired-network-smoke: a running systemd user manager is required' >&2
  exit 2
fi
if [[ ! "$TIMEOUT" =~ ^[1-9][0-9]*$ ]]; then
  echo 'fabric-paired-network-smoke: FABRIC_PAIRED_SMOKE_TIMEOUT must be a positive integer' >&2
  exit 2
fi
if [[ ! "$USERNAME" =~ ^[A-Za-z0-9_]{1,16}$ ]]; then
  echo 'fabric-paired-network-smoke: username must match [A-Za-z0-9_]{1,16}' >&2
  exit 2
fi

mkdir -p "$ROOT/fabric/build" "$LOG_ROOT"
exec 9>"$ROOT/fabric/build/fabric-paired-network-smoke.lock"
if ! flock -n 9; then
  echo 'fabric-paired-network-smoke: another paired smoke is already using the shared Loom outputs' >&2
  exit 2
fi

RUN_ID=$(date -u +%Y%m%dT%H%M%SZ)-$$
SCOPE_PREFIX="mekanism-paired-$RUN_ID"
DIAGNOSTIC_DIR="$LOG_ROOT/$RUN_ID"
mkdir -p "$DIAGNOSTIC_DIR"

terminate_scope() {
  local scope=${1:-} pid=${2:-}
  if [[ -n "$scope" ]]; then
    systemctl --user stop "$scope.scope" >/dev/null 2>&1 || true
    for _ in {1..40}; do
      systemctl --user is-active --quiet "$scope.scope" 2>/dev/null || break
      sleep 0.25
    done
  fi
  if [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null; then
    kill -TERM "$pid" 2>/dev/null || true
    for _ in {1..20}; do
      kill -0 "$pid" 2>/dev/null || break
      sleep 0.25
    done
    kill -KILL "$pid" 2>/dev/null || true
  fi
  [[ -n "$pid" ]] && wait "$pid" 2>/dev/null || true
}

copy_diagnostic() {
  local source=$1 destination=$2
  [[ -f "$source" ]] || return 0
  if ! cp "$source" "$destination"; then
    printf 'fabric-paired-network-smoke: warning: failed to copy diagnostic %s\n' "$source" >&2
  fi
}

finish() {
  local status=$?
  trap - EXIT INT TERM
  terminate_scope "$CLIENT_SCOPE" "$CLIENT_PID"
  terminate_scope "$SERVER_SCOPE" "$SERVER_PID"
  if [[ -n "$RUN_ROOT" && -d "$RUN_ROOT" ]]; then
    copy_diagnostic "$RUN_ROOT/server-console.log" "$DIAGNOSTIC_DIR/server-console.log"
    for console in "$RUN_ROOT"/client-*-console.log; do
      [[ -f "$console" ]] || continue
      copy_diagnostic "$console" "$DIAGNOSTIC_DIR/$(basename "$console")"
    done
    copy_diagnostic "$SERVER_DIR/logs/latest.log" "$DIAGNOSTIC_DIR/server-latest.log"
    for client_dir in "$RUN_ROOT"/client-*; do
      [[ -f "$client_dir/logs/latest.log" ]] || continue
      copy_diagnostic "$client_dir/logs/latest.log" "$DIAGNOSTIC_DIR/$(basename "$client_dir")-latest.log"
    done
    if [[ "$KEEP_RUN" == 1 ]]; then
      printf 'fabric-paired-network-smoke: retained run directories: %s\n' "$RUN_ROOT" >&2
    elif ! rm -rf "$RUN_ROOT"; then
      printf 'fabric-paired-network-smoke: warning: failed to remove %s\n' "$RUN_ROOT" >&2
    fi
  fi
  printf 'fabric-paired-network-smoke: diagnostics: %s\n' "$DIAGNOSTIC_DIR" >&2
  if [[ $RESULT -eq 0 && $status -eq 0 ]]; then
    exit 0
  fi
  (( status == 0 )) && status=1
  exit "$status"
}
trap finish EXIT INT TERM

count_matches() {
  local file=$1 pattern=$2
  [[ -f "$file" ]] || { printf '0\n'; return; }
  grep -Fc "$pattern" "$file" 2>/dev/null || true
}

fail_with_logs() {
  local message=$1
  printf 'fabric-paired-network-smoke: %s\n' "$message" >&2
  [[ -f "$SERVER_DIR/logs/latest.log" ]] && tail -n 80 "$SERVER_DIR/logs/latest.log" >&2 || true
  if [[ $CLIENT_NUMBER -gt 0 ]]; then
    local client_dir="$RUN_ROOT/client-$CLIENT_NUMBER"
    [[ -f "$client_dir/logs/latest.log" ]] && tail -n 80 "$client_dir/logs/latest.log" >&2 || true
    [[ -f "$RUN_ROOT/client-$CLIENT_NUMBER-console.log" ]] && tail -n 40 "$RUN_ROOT/client-$CLIENT_NUMBER-console.log" >&2 || true
  fi
  exit 1
}

select_port() {
  python3 - <<'PY'
import socket
with socket.socket() as sock:
    sock.bind(("127.0.0.1", 0))
    print(sock.getsockname()[1])
PY
}

wait_for_server() {
  local log=$1
  while (( SECONDS < DEADLINE )); do
    grep -Eq 'Done \(' "$log" 2>/dev/null && return 0
    if grep -Eiq '(FAILED TO BIND TO PORT|Address already in use)' "$log" 2>/dev/null; then
      return 2
    fi
    if ! kill -0 "$SERVER_PID" 2>/dev/null; then
      grep -Eiq '(FAILED TO BIND TO PORT|Address already in use)' "$log" 2>/dev/null && return 2
      return 1
    fi
    if grep -Eiq '(Exception in thread|Could not execute entrypoint|Failed to start the minecraft server|---- Minecraft Crash Report ----)' "$log" 2>/dev/null; then
      return 1
    fi
    sleep 0.5
  done
  return 3
}

wait_for_join_count() {
  local target=$1 server_log="$SERVER_DIR/logs/latest.log"
  while (( SECONDS < DEADLINE )); do
    if (( $(count_matches "$server_log" "$USERNAME joined the game") >= target )); then
      return 0
    fi
    kill -0 "$SERVER_PID" 2>/dev/null || fail_with_logs 'server exited while waiting for client join'
    kill -0 "$CLIENT_PID" 2>/dev/null || fail_with_logs "client $CLIENT_NUMBER exited before joining"
    sleep 0.5
  done
  fail_with_logs "overall ${TIMEOUT}s timeout expired while waiting for client $CLIENT_NUMBER"
}

wait_for_leave_count() {
  local target=$1 server_log="$SERVER_DIR/logs/latest.log"
  while (( SECONDS < DEADLINE )); do
    if (( $(count_matches "$server_log" "$USERNAME left the game") >= target )); then
      return 0
    fi
    kill -0 "$SERVER_PID" 2>/dev/null || fail_with_logs 'server exited while waiting for client disconnect'
    sleep 0.5
  done
  fail_with_logs "overall ${TIMEOUT}s timeout expired while waiting for client $CLIENT_NUMBER disconnect"
}

start_client() {
  CLIENT_NUMBER=$1
  local client_dir="$RUN_ROOT/client-$CLIENT_NUMBER"
  CLIENT_SCOPE="$SCOPE_PREFIX-client-$CLIENT_NUMBER"
  mkdir -p "$client_dir"
  # Fresh 1.21 clients otherwise stop at the accessibility onboarding screen before Quick Play runs.
  printf 'onboardAccessibility:false\n' > "$client_dir/options.txt"
  printf 'fabric-paired-network-smoke: starting client %d\n' "$CLIENT_NUMBER"
  systemd-run --user --scope --collect --quiet --unit="$CLIENT_SCOPE" \
    xvfb-run -a "$ROOT/gradlew" -p "$ROOT/fabric" --no-daemon --console=plain \
    -Dmekanism.handshake.clientDir="$RUN_RELATIVE/client-$CLIENT_NUMBER" \
    -Dmekanism.handshake.address="127.0.0.1:$PORT" \
    -Dmekanism.handshake.username="$USERNAME" \
    runHandshakeClient >"$RUN_ROOT/client-$CLIENT_NUMBER-console.log" 2>&1 &
  CLIENT_PID=$!
}

RUN_RELATIVE="run/paired-$RUN_ID"
RUN_ROOT="$ROOT/fabric/$RUN_RELATIVE"
SERVER_DIR="$RUN_ROOT/server"
mkdir -p "$SERVER_DIR"
DEADLINE=$((SECONDS + TIMEOUT))
server_ready=0
for attempt in 1 2 3; do
  PORT=$(select_port)
  rm -rf "$SERVER_DIR/logs" "$SERVER_DIR/world"
  printf 'eula=true\n' > "$SERVER_DIR/eula.txt"
  cat > "$SERVER_DIR/server.properties" <<EOF
online-mode=false
server-ip=127.0.0.1
server-port=$PORT
motd=Mekanism Fabric paired network smoke
max-players=2
view-distance=2
simulation-distance=2
sync-chunk-writes=false
EOF
  SERVER_SCOPE="$SCOPE_PREFIX-server-$attempt"
  printf 'fabric-paired-network-smoke: starting isolated server on 127.0.0.1:%s (attempt %d)\n' "$PORT" "$attempt"
  systemd-run --user --scope --collect --quiet --unit="$SERVER_SCOPE" \
    "$ROOT/gradlew" -p "$ROOT/fabric" --no-daemon --console=plain \
    -Dmekanism.handshake.serverDir="$RUN_RELATIVE/server" \
    runHandshakeServer >"$RUN_ROOT/server-console.log" 2>&1 &
  SERVER_PID=$!
  if wait_for_server "$SERVER_DIR/logs/latest.log"; then
    server_ready=1
    break
  else
    wait_status=$?
  fi
  terminate_scope "$SERVER_SCOPE" "$SERVER_PID"
  SERVER_SCOPE=
  SERVER_PID=
  if [[ $wait_status -eq 3 ]]; then
    fail_with_logs "overall ${TIMEOUT}s timeout expired during server startup"
  elif [[ $wait_status -ne 2 ]]; then
    fail_with_logs 'server exited before readiness'
  fi
  echo 'fabric-paired-network-smoke: selected port was claimed before server bind; retrying' >&2
done
[[ $server_ready -eq 1 ]] || fail_with_logs 'failed to bind a loopback port after three attempts'
printf '%s\n' "port=$PORT" "username=$USERNAME" "overall_timeout=$TIMEOUT" > "$DIAGNOSTIC_DIR/run-info.txt"

start_client 1
wait_for_join_count 1
if ! grep -Fq 'MEKANISM_FABRIC_NETWORK_CLIENT_PROOF protocol=10.7.19 receiver=true' "$RUN_ROOT/client-1/logs/latest.log"; then
  fail_with_logs 'first client did not initialize the network receiver'
fi
terminate_scope "$CLIENT_SCOPE" "$CLIENT_PID"
CLIENT_SCOPE=
CLIENT_PID=
wait_for_leave_count 1

start_client 2
wait_for_join_count 2
if ! grep -Fq 'MEKANISM_FABRIC_NETWORK_CLIENT_PROOF protocol=10.7.19 receiver=true' "$RUN_ROOT/client-2/logs/latest.log"; then
  fail_with_logs 'second client did not initialize the network receiver'
fi
for session in 1 2; do
  if ! grep -Fq "MEKANISM_FABRIC_NETWORK_HANDSHAKE_START session=$session protocol=10.7.19 thread=Netty" \
      "$SERVER_DIR/logs/latest.log"; then
    fail_with_logs "session $session did not start on a Netty event-loop thread"
  fi
  if ! grep -Fq "MEKANISM_FABRIC_NETWORK_HANDSHAKE_COMPLETE session=$session protocol=10.7.19 thread=Netty" \
      "$SERVER_DIR/logs/latest.log"; then
    fail_with_logs "session $session did not complete on a Netty event-loop thread"
  fi
done

RESULT=0
printf 'fabric-paired-network-smoke: PASS (two fresh clients completed configuration negotiation and joined play)\n'
grep -E 'MEKANISM_FABRIC_NETWORK_HANDSHAKE_(START|COMPLETE)' "$SERVER_DIR/logs/latest.log" | tail -n 8
grep -F "$USERNAME " "$SERVER_DIR/logs/latest.log" | grep -E 'joined|left' | tail -n 4
