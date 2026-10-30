#!/usr/bin/env bash
# Prove incompatible, explicitly rejecting, and missing-receiver clients are rejected during configuration.
set -euo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
TIMEOUT=${FABRIC_REJECTION_SMOKE_TIMEOUT:-240}
KEEP_RUN=${FABRIC_REJECTION_SMOKE_KEEP_RUN:-0}
LOG_ROOT=${FABRIC_REJECTION_SMOKE_LOG_DIR:-$ROOT/build/fabric-configuration-rejection-smoke}
MISMATCH_USERNAME=${FABRIC_REJECTION_MISMATCH_USERNAME:-MekaMismatch}
REJECTED_USERNAME=${FABRIC_REJECTION_CLIENT_REJECTED_USERNAME:-MekaRejected}
MISSING_USERNAME=${FABRIC_REJECTION_MISSING_RECEIVER_USERNAME:-MekaMissing}
RUN_ROOT= RUN_RELATIVE= SERVER_DIR= SERVER_PID= SERVER_SCOPE= CLIENT_PID= CLIENT_SCOPE= CLIENT_KIND=
ACTIVE_SERVER_CONSOLE=
RESULT=1

for command in flock java python3 systemctl systemd-run timeout xvfb-run Xvfb; do
  command -v "$command" >/dev/null 2>&1 || { printf 'fabric-configuration-rejection-smoke: required command not found: %s\n' "$command" >&2; exit 2; }
done
systemctl --user show-environment >/dev/null 2>&1 || { echo 'fabric-configuration-rejection-smoke: a running systemd user manager is required' >&2; exit 2; }
[[ "$TIMEOUT" =~ ^[1-9][0-9]*$ ]] || { echo 'fabric-configuration-rejection-smoke: FABRIC_REJECTION_SMOKE_TIMEOUT must be a positive integer' >&2; exit 2; }
for username in "$MISMATCH_USERNAME" "$REJECTED_USERNAME" "$MISSING_USERNAME"; do
  [[ "$username" =~ ^[A-Za-z0-9_]{1,16}$ ]] || { echo 'fabric-configuration-rejection-smoke: usernames must match [A-Za-z0-9_]{1,16}' >&2; exit 2; }
done
[[ $(printf '%s\n' "$MISMATCH_USERNAME" "$REJECTED_USERNAME" "$MISSING_USERNAME" | sort -u | wc -l) -eq 3 ]] || { echo 'fabric-configuration-rejection-smoke: fixture usernames must be distinct' >&2; exit 2; }

mkdir -p "$ROOT/fabric/build" "$LOG_ROOT"
exec 9>"$ROOT/fabric/build/fabric-paired-network-smoke.lock"
flock -n 9 || { echo 'fabric-configuration-rejection-smoke: another handshake smoke is using the shared Loom outputs' >&2; exit 2; }
RUN_ID=$(date -u +%Y%m%dT%H%M%SZ)-$$
SCOPE_PREFIX="mekanism-rejection-$RUN_ID"
DIAGNOSTIC_DIR="$LOG_ROOT/$RUN_ID"
mkdir -p "$DIAGNOSTIC_DIR"
DEADLINE=$((SECONDS + TIMEOUT))

remaining_seconds() { local left=$((DEADLINE - SECONDS)); (( left > 0 )) || return 1; printf '%s\n' "$left"; }
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
copy_diagnostic() { local source=$1 destination=$2; [[ -f "$source" ]] || return 0; cp "$source" "$destination" || printf 'fabric-configuration-rejection-smoke: warning: failed to copy %s\n' "$source" >&2; }
finish() {
  local status=$?
  trap - EXIT INT TERM
  terminate_scope "$CLIENT_SCOPE" "$CLIENT_PID"
  terminate_scope "$SERVER_SCOPE" "$SERVER_PID"
  if [[ -n "$RUN_ROOT" && -d "$RUN_ROOT" ]]; then
    for console in "$RUN_ROOT"/server-console-attempt-*.log; do [[ -f "$console" ]] && copy_diagnostic "$console" "$DIAGNOSTIC_DIR/$(basename "$console")"; done
    copy_diagnostic "$SERVER_DIR/logs/latest.log" "$DIAGNOSTIC_DIR/server-latest.log"
    for kind in mismatch client_rejected missing_receiver; do
      copy_diagnostic "$RUN_ROOT/$kind-client-console.log" "$DIAGNOSTIC_DIR/$kind-client-console.log"
      copy_diagnostic "$RUN_ROOT/$kind-client/logs/latest.log" "$DIAGNOSTIC_DIR/$kind-client-latest.log"
    done
    if [[ "$KEEP_RUN" == 1 ]]; then printf 'fabric-configuration-rejection-smoke: retained run directories: %s\n' "$RUN_ROOT" >&2; else rm -rf "$RUN_ROOT" || true; fi
  fi
  printf 'fabric-configuration-rejection-smoke: diagnostics: %s\n' "$DIAGNOSTIC_DIR" >&2
  [[ $RESULT -eq 0 && $status -eq 0 ]] && exit 0
  (( status == 0 )) && status=1
  exit "$status"
}
trap finish EXIT INT TERM

fail_with_logs() {
  printf 'fabric-configuration-rejection-smoke: %s\n' "$1" >&2
  [[ -n "$ACTIVE_SERVER_CONSOLE" && -f "$ACTIVE_SERVER_CONSOLE" ]] && { echo '--- server console ---' >&2; tail -n 100 "$ACTIVE_SERVER_CONSOLE" >&2; }
  [[ -f "$SERVER_DIR/logs/latest.log" ]] && { echo '--- server latest.log ---' >&2; tail -n 100 "$SERVER_DIR/logs/latest.log" >&2; }
  if [[ -n "$CLIENT_KIND" ]]; then
    [[ -f "$RUN_ROOT/$CLIENT_KIND-client/logs/latest.log" ]] && tail -n 80 "$RUN_ROOT/$CLIENT_KIND-client/logs/latest.log" >&2
    [[ -f "$RUN_ROOT/$CLIENT_KIND-client-console.log" ]] && tail -n 40 "$RUN_ROOT/$CLIENT_KIND-client-console.log" >&2
  fi
  exit 1
}
# Match a complete message after the variable logging prefix, never a substring.
marker_count() { python3 - "$1" "$2" <<'PY'
import pathlib, sys
p, marker = pathlib.Path(sys.argv[1]), sys.argv[2]
if not p.is_file(): print(0); raise SystemExit
print(sum(line.rstrip('\r\n') == marker or line.rstrip('\r\n').endswith(') ' + marker) for line in p.open(errors='replace')))
PY
}
marker_line() { python3 - "$1" "$2" <<'PY'
import pathlib, sys
p, marker = pathlib.Path(sys.argv[1]), sys.argv[2]
if p.is_file():
  for n,line in enumerate(p.open(errors='replace'),1):
    line=line.rstrip('\r\n')
    if line == marker or line.endswith(') '+marker): print(n); raise SystemExit
raise SystemExit(1)
PY
}
require_order() { local file=$1 first=$2 second=$3 label=$4 a b; a=$(marker_line "$file" "$first") || fail_with_logs "$label missing first marker"; b=$(marker_line "$file" "$second") || fail_with_logs "$label missing second marker"; (( a < b )) || fail_with_logs "$label markers are out of order"; }
require_username_disconnect() {
  local log=$1 username=$2 reason=$3 label=$4 count
  count=$(python3 - "$log" "$username" "$reason" <<'PY'
import pathlib,re,sys
p,user,reason=pathlib.Path(sys.argv[1]),re.escape(sys.argv[2]),re.escape(sys.argv[3])
if not p.is_file(): print(0); raise SystemExit
pattern=re.compile(r'^.*\) com\.mojang\.authlib\.GameProfile@[^[]+\[[^]]*name='+user+r',[^]]*\] lost connection: '+reason+r'$')
print(sum(bool(pattern.fullmatch(line.rstrip('\r\n'))) for line in p.open(errors='replace')))
PY
)
  [[ $count -eq 1 ]] || fail_with_logs "$label"
}
server_session_start_line() { python3 - "$SERVER_LOG" "$1" <<'PY'
import pathlib,re,sys
p,session=pathlib.Path(sys.argv[1]),re.escape(sys.argv[2])
if p.is_file():
  for n,line in enumerate(p.open(errors='replace'),1):
    if re.search(r'\) MEKANISM_FABRIC_NETWORK_HANDSHAKE_START session='+session+r' protocol=10\.7\.19 thread=.+$',line.rstrip()): print(n); raise SystemExit
raise SystemExit(1)
PY
}
select_port() { python3 - <<'PY'
import socket
with socket.socket() as sock: sock.bind(('127.0.0.1',0)); print(sock.getsockname()[1])
PY
}
wait_for_server() {
  local log=$1
  while (( SECONDS < DEADLINE )); do
    grep -Eq 'Done \(' "$log" 2>/dev/null && return 0
    grep -Eiq '(FAILED TO BIND TO PORT|Address already in use)' "$ACTIVE_SERVER_CONSOLE" "$log" 2>/dev/null && return 2
    kill -0 "$SERVER_PID" 2>/dev/null || return 1
    grep -Eiq '(Exception in thread|Could not execute entrypoint|Failed to start the minecraft server|---- Minecraft Crash Report ----)' "$ACTIVE_SERVER_CONSOLE" "$log" 2>/dev/null && return 1
    sleep 0.5
  done
  return 3
}
start_client() {
  local kind=$1 username=$2 property task mode=
  local -a mode_args=()
  CLIENT_KIND=$kind; CLIENT_SCOPE="$SCOPE_PREFIX-$kind-client"; CLIENT_PID=
  mkdir -p "$RUN_ROOT/$kind-client"; printf 'onboardAccessibility:false\n' > "$RUN_ROOT/$kind-client/options.txt"
  case "$kind" in
    mismatch) property=mekanism.handshake.mismatchClientDir; task=runHandshakeMismatchClient; mode=version_mismatch ;;
    client_rejected) property=mekanism.handshake.mismatchClientDir; task=runHandshakeMismatchClient; mode=same_version_reject ;;
    missing_receiver) property=mekanism.handshake.missingReceiverClientDir; task=runHandshakeMissingReceiverClient ;;
    *) fail_with_logs "unknown client fixture: $kind" ;;
  esac
  [[ -z "$mode" ]] || mode_args+=("-Dmekanism.handshake.fixtureMode=$mode")
  printf 'fabric-configuration-rejection-smoke: starting %s client\n' "$kind"
  systemd-run --user --scope --collect --quiet --unit="$CLIENT_SCOPE" xvfb-run -a "$ROOT/gradlew" -p "$ROOT/fabric" --no-daemon --console=plain \
    -D"$property=$RUN_RELATIVE/$kind-client" -Dmekanism.handshake.address="127.0.0.1:$PORT" -Dmekanism.handshake.username="$username" \
    "${mode_args[@]}" "$task" >"$RUN_ROOT/$kind-client-console.log" 2>&1 &
  CLIENT_PID=$!
}
wait_for_evidence() {
  local server_marker=$1 client_marker=$2 server_log="$SERVER_DIR/logs/latest.log" client_log="$RUN_ROOT/$CLIENT_KIND-client/logs/latest.log"
  while (( SECONDS < DEADLINE )); do
    if [[ $(marker_count "$server_log" "$server_marker") -eq 1 && $(marker_count "$client_log" "$client_marker") -eq 1 ]]; then return 0; fi
    kill -0 "$SERVER_PID" 2>/dev/null || fail_with_logs 'server exited while waiting for configuration rejection'
    kill -0 "$CLIENT_PID" 2>/dev/null || fail_with_logs "$CLIENT_KIND client exited before its rejection evidence was complete"
    sleep 0.5
  done
  fail_with_logs "overall ${TIMEOUT}s timeout expired while waiting for $CLIENT_KIND rejection"
}

printf 'fabric-configuration-rejection-smoke: verifying fixture isolation and compiling both fixtures\n'
remaining=$(remaining_seconds) || fail_with_logs "overall ${TIMEOUT}s timeout expired before fixture preflight"
timeout "${remaining}s" "$ROOT/gradlew" -p "$ROOT/fabric" --no-daemon --console=plain verifyHandshakeFixtureIsolation compileHandshakeMismatchFixtureJava compileHandshakeMissingReceiverFixtureJava || fail_with_logs 'fixture preflight failed or exceeded the overall deadline'

RUN_RELATIVE="run/rejection-$RUN_ID"; RUN_ROOT="$ROOT/fabric/$RUN_RELATIVE"; SERVER_DIR="$RUN_ROOT/server"; mkdir -p "$SERVER_DIR"
server_ready=0
for attempt in 1 2 3; do
  PORT=$(select_port); rm -rf "$SERVER_DIR/logs" "$SERVER_DIR/world"; printf 'eula=true\n' > "$SERVER_DIR/eula.txt"
  printf '%s\n' 'online-mode=false' 'server-ip=127.0.0.1' "server-port=$PORT" 'motd=Mekanism Fabric configuration rejection smoke' 'max-players=2' 'view-distance=2' 'simulation-distance=2' 'sync-chunk-writes=false' > "$SERVER_DIR/server.properties"
  SERVER_SCOPE="$SCOPE_PREFIX-server-$attempt"; ACTIVE_SERVER_CONSOLE="$RUN_ROOT/server-console-attempt-$attempt.log"
  printf 'fabric-configuration-rejection-smoke: starting isolated server on 127.0.0.1:%s (attempt %d)\n' "$PORT" "$attempt"
  systemd-run --user --scope --collect --quiet --unit="$SERVER_SCOPE" "$ROOT/gradlew" -p "$ROOT/fabric" --no-daemon --console=plain -Dmekanism.handshake.serverDir="$RUN_RELATIVE/server" runHandshakeServer >"$ACTIVE_SERVER_CONSOLE" 2>&1 & SERVER_PID=$!
  if wait_for_server "$SERVER_DIR/logs/latest.log"; then server_ready=1; break; else wait_status=$?; fi
  terminate_scope "$SERVER_SCOPE" "$SERVER_PID"; SERVER_SCOPE=; SERVER_PID=
  [[ $wait_status -eq 3 ]] && fail_with_logs "overall ${TIMEOUT}s timeout expired during server startup"
  [[ $wait_status -eq 2 ]] || fail_with_logs 'server exited before readiness'
  echo 'fabric-configuration-rejection-smoke: selected port was claimed before server bind; retrying' >&2
done
[[ $server_ready -eq 1 ]] || fail_with_logs 'failed to bind a loopback port after three attempts'
printf '%s\n' "port=$PORT" "overall_timeout=$TIMEOUT" "mismatch_username=$MISMATCH_USERNAME" "client_rejected_username=$REJECTED_USERNAME" "missing_receiver_username=$MISSING_USERNAME" > "$DIAGNOSTIC_DIR/run-info.txt"

MISMATCH_READY='MEKANISM_FIXTURE_MISMATCH_READY mode=version_mismatch local=fixture-mismatch-0 registry_entries=4'
MISMATCH_RESPONSE='MEKANISM_FIXTURE_MISMATCH_RESPONSE mode=version_mismatch local=fixture-mismatch-0 remote=10.7.19 accepted=false'
MISMATCH_REASON='Mekanism protocol mismatch: local 10.7.19, remote fixture-mismatch-0'
MISMATCH_DISCONNECT="Client disconnected with reason: $MISMATCH_REASON"
MISMATCH_START='MEKANISM_FABRIC_NETWORK_HANDSHAKE_START session=1 protocol=10.7.19 thread=Netty Server IO #1'
MISMATCH_REJECT="MEKANISM_FABRIC_NETWORK_HANDSHAKE_REJECT kind=version_mismatch player=$MISMATCH_USERNAME session=1 local=10.7.19 remote=fixture-mismatch-0 reason=$MISMATCH_REASON"
start_client mismatch "$MISMATCH_USERNAME"
wait_for_evidence "$MISMATCH_REJECT" "$MISMATCH_DISCONNECT"
MISMATCH_CLIENT_LOG="$RUN_ROOT/mismatch-client/logs/latest.log"; SERVER_LOG="$SERVER_DIR/logs/latest.log"
[[ $(marker_count "$MISMATCH_CLIENT_LOG" "$MISMATCH_RESPONSE") -eq 1 ]] || fail_with_logs 'mismatch fixture response marker was not exact and unique'
require_order "$MISMATCH_CLIENT_LOG" "$MISMATCH_READY" "$MISMATCH_RESPONSE" 'mismatch client READY -> RESPONSE'
require_order "$MISMATCH_CLIENT_LOG" "$MISMATCH_RESPONSE" "$MISMATCH_DISCONNECT" 'mismatch client RESPONSE -> DISCONNECT'
# Server thread suffix is variable, so bind order to exact semantic markers found in this process only.
start_line=$(server_session_start_line 1) || fail_with_logs 'missing mismatch server START'
reject_line=$(marker_line "$SERVER_LOG" "$MISMATCH_REJECT") || fail_with_logs 'missing exact mismatch server rejection'
[[ $start_line -lt $reject_line ]] || fail_with_logs 'server START -> mismatch REJECT order was not proven'
require_username_disconnect "$SERVER_LOG" "$MISMATCH_USERNAME" "$MISMATCH_REASON" 'server disconnect was not bound to the mismatch username and exact reason'
[[ $(marker_count "$MISMATCH_CLIENT_LOG" 'MEKANISM_FABRIC_NETWORK_CLIENT_PROOF') -eq 0 ]] || fail_with_logs 'mismatch client leaked production entrypoint'
terminate_scope "$CLIENT_SCOPE" "$CLIENT_PID"; CLIENT_SCOPE=; CLIENT_PID=

REJECTED_READY='MEKANISM_FIXTURE_MISMATCH_READY mode=same_version_reject local=10.7.19 registry_entries=4'
REJECTED_RESPONSE='MEKANISM_FIXTURE_MISMATCH_RESPONSE mode=same_version_reject local=10.7.19 remote=10.7.19 accepted=false'
REJECTED_REASON='Remote client rejected Mekanism protocol 10.7.19'
REJECTED_DISCONNECT="Client disconnected with reason: $REJECTED_REASON"
REJECTED_REJECT="MEKANISM_FABRIC_NETWORK_HANDSHAKE_REJECT kind=client_rejected player=$REJECTED_USERNAME session=2 local=10.7.19 remote=10.7.19 reason=$REJECTED_REASON"
start_client client_rejected "$REJECTED_USERNAME"
wait_for_evidence "$REJECTED_REJECT" "$REJECTED_DISCONNECT"
REJECTED_CLIENT_LOG="$RUN_ROOT/client_rejected-client/logs/latest.log"
[[ $(marker_count "$REJECTED_CLIENT_LOG" "$REJECTED_RESPONSE") -eq 1 ]] || fail_with_logs 'same-version rejection fixture response marker was not exact and unique'
require_order "$REJECTED_CLIENT_LOG" "$REJECTED_READY" "$REJECTED_RESPONSE" 'same-version rejection client READY -> RESPONSE'
require_order "$REJECTED_CLIENT_LOG" "$REJECTED_RESPONSE" "$REJECTED_DISCONNECT" 'same-version rejection client RESPONSE -> DISCONNECT'
start_line=$(server_session_start_line 2) || fail_with_logs 'missing same-version rejection server START'
reject_line=$(marker_line "$SERVER_LOG" "$REJECTED_REJECT") || fail_with_logs 'missing exact same-version client rejection'
[[ $start_line -lt $reject_line ]] || fail_with_logs 'server START -> same-version REJECT order was not proven'
require_username_disconnect "$SERVER_LOG" "$REJECTED_USERNAME" "$REJECTED_REASON" 'server disconnect was not bound to the same-version rejection username and exact reason'
[[ $(marker_count "$REJECTED_CLIENT_LOG" 'MEKANISM_FABRIC_NETWORK_CLIENT_PROOF') -eq 0 ]] || fail_with_logs 'same-version rejection client leaked production entrypoint'
terminate_scope "$CLIENT_SCOPE" "$CLIENT_PID"; CLIENT_SCOPE=; CLIENT_PID=

MISSING_READY='MEKANISM_FIXTURE_MISSING_RECEIVER_READY registry_entries=4 payloads=0'
MISSING_REASON='This server requires Mekanism protocol 10.7.19'
MISSING_DISCONNECT="Client disconnected with reason: $MISSING_REASON"
MISSING_REJECT="MEKANISM_FABRIC_NETWORK_HANDSHAKE_REJECT kind=missing_receiver player=$MISSING_USERNAME protocol=10.7.19 reason=$MISSING_REASON"
start_client missing_receiver "$MISSING_USERNAME"
wait_for_evidence "$MISSING_REJECT" "$MISSING_DISCONNECT"
MISSING_CLIENT_LOG="$RUN_ROOT/missing_receiver-client/logs/latest.log"
require_order "$MISSING_CLIENT_LOG" "$MISSING_READY" "$MISSING_DISCONNECT" 'missing receiver client READY -> DISCONNECT'
require_username_disconnect "$SERVER_LOG" "$MISSING_USERNAME" "$MISSING_REASON" 'server disconnect was not bound to the missing-receiver username and exact reason'
terminate_scope "$CLIENT_SCOPE" "$CLIENT_PID"; CLIENT_SCOPE=; CLIENT_PID=

[[ $(marker_count "$SERVER_LOG" "$MISMATCH_REJECT") -eq 1 ]] || fail_with_logs 'expected one exact version-mismatch rejection'
[[ $(marker_count "$SERVER_LOG" "$REJECTED_REJECT") -eq 1 ]] || fail_with_logs 'expected one exact same-version client rejection'
[[ $(marker_count "$SERVER_LOG" "$MISSING_REJECT") -eq 1 ]] || fail_with_logs 'expected one exact missing-receiver rejection'
[[ $(grep -Fc 'MEKANISM_FABRIC_NETWORK_HANDSHAKE_COMPLETE' "$SERVER_LOG" || true) -eq 0 ]] || fail_with_logs 'a rejected handshake unexpectedly completed'
[[ $(grep -Fc "name=$MISMATCH_USERNAME," "$SERVER_LOG" | tr -d ' ') -ge 1 ]] || fail_with_logs 'mismatch username never appeared server-side'
[[ $(grep -Fc "$MISMATCH_USERNAME joined the game" "$SERVER_LOG" || true) -eq 0 ]] || fail_with_logs 'mismatch client reached play'
[[ $(grep -Fc "$REJECTED_USERNAME joined the game" "$SERVER_LOG" || true) -eq 0 ]] || fail_with_logs 'same-version rejection client reached play'
[[ $(grep -Fc "$MISSING_USERNAME joined the game" "$SERVER_LOG" || true) -eq 0 ]] || fail_with_logs 'missing-receiver client reached play'
kill -0 "$SERVER_PID" 2>/dev/null || fail_with_logs 'server exited after rejecting clients'
RESULT=0
printf 'fabric-configuration-rejection-smoke: PASSED (version mismatch, same-version client rejection, and missing receiver were rejected before play)\n'
grep -E 'MEKANISM_FABRIC_NETWORK_HANDSHAKE_(START|REJECT)' "$SERVER_LOG" | tail -n 6
