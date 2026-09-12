#!/usr/bin/env bash
# Prove client-window persistence and physical-client shutdown with two fresh real client JVMs.
set -euo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
TIMEOUT=${FABRIC_CLIENT_WINDOW_SMOKE_TIMEOUT:-120}
KEEP_RUN=${FABRIC_CLIENT_WINDOW_SMOKE_KEEP_RUN:-0}
LOG_ROOT=${FABRIC_CLIENT_WINDOW_SMOKE_LOG_DIR:-$ROOT/build/fabric-client-window-smoke}
RESULT=1
RUN_ROOT=

for command in flock python3 timeout xvfb-run Xvfb; do
  command -v "$command" >/dev/null 2>&1 || { printf 'fabric-client-window-smoke: required command not found: %s\n' "$command" >&2; exit 2; }
done
[[ "$TIMEOUT" =~ ^[1-9][0-9]*$ ]] || { echo 'fabric-client-window-smoke: timeout must be a positive integer' >&2; exit 2; }
mkdir -p "$ROOT/fabric/build" "$LOG_ROOT"
exec 9>"$ROOT/fabric/build/fabric-paired-network-smoke.lock"
flock -n 9 || { echo 'fabric-client-window-smoke: another real-client smoke is using shared Loom outputs' >&2; exit 2; }

RUN_ID=$(date -u +%Y%m%dT%H%M%SZ)-$$
RUN_RELATIVE="run/client-window-$RUN_ID"
RUN_ROOT="$ROOT/fabric/$RUN_RELATIVE"
DIAGNOSTIC_DIR="$LOG_ROOT/$RUN_ID"
mkdir -p "$RUN_ROOT/config/Mekanism" "$DIAGNOSTIC_DIR"
printf 'onboardAccessibility:false\n' > "$RUN_ROOT/options.txt"
cat > "$RUN_ROOT/config/Mekanism/client.toml" <<'EOF'
fixture_unknown = "retained"

[future_section]
owner = "future-client-schema"
EOF

finish() {
  local status=$?
  trap - EXIT INT TERM
  [[ -f "$RUN_ROOT/config/Mekanism/client.toml" ]] && cp "$RUN_ROOT/config/Mekanism/client.toml" "$DIAGNOSTIC_DIR/client.toml" || true
  for phase in 1 2; do
    [[ -f "$RUN_ROOT/phase-$phase.log" ]] && cp "$RUN_ROOT/phase-$phase.log" "$DIAGNOSTIC_DIR/phase-$phase.log" || true
  done
  if [[ "$KEEP_RUN" == 1 ]]; then
    printf 'fabric-client-window-smoke: retained run directory: %s\n' "$RUN_ROOT" >&2
  else
    rm -rf "$RUN_ROOT" || true
  fi
  printf 'fabric-client-window-smoke: diagnostics: %s\n' "$DIAGNOSTIC_DIR" >&2
  [[ $RESULT -eq 0 && $status -eq 0 ]] && exit 0
  (( status == 0 )) && status=1
  exit "$status"
}
trap finish EXIT INT TERM

run_phase() {
  local phase=$1
  printf 'fabric-client-window-smoke: starting fresh client JVM phase %s\n' "$phase"
  timeout "${TIMEOUT}s" xvfb-run -a "$ROOT/gradlew" -p "$ROOT/fabric" --no-daemon --console=plain \
    -Dmekanism.window.clientDir="$RUN_RELATIVE" -Dmekanism.window.fixturePhase="$phase" \
    runClientWindowFixture >"$RUN_ROOT/phase-$phase.log" 2>&1
  local latest="$RUN_ROOT/logs/latest.log"
  grep -Fq "MEKANISM_CLIENT_WINDOW_FIXTURE phase=$phase owner_across_connection_boundaries=true" "$latest" \
    || { tail -n 100 "$latest" >&2 || true; echo "fabric-client-window-smoke: missing phase $phase owner marker" >&2; exit 1; }
  grep -Fq "MEKANISM_CLIENT_WINDOW_FIXTURE_SHUTDOWN phase=$phase flushed=true owner_closed=true" "$latest" \
    || { tail -n 100 "$latest" >&2 || true; echo "fabric-client-window-smoke: missing phase $phase shutdown marker" >&2; exit 1; }
  cp "$latest" "$DIAGNOSTIC_DIR/phase-$phase-latest.log"
}

run_phase 1
CONFIG="$RUN_ROOT/config/Mekanism/client.toml"
FIRST_HASH=$(sha256sum "$CONFIG" | cut -d' ' -f1)
python3 - "$CONFIG" <<'PY'
import pathlib, sys, tomllib
path = pathlib.Path(sys.argv[1])
data = tomllib.loads(path.read_text())
assert data['fixture_unknown'] == 'retained'
assert data['future_section']['owner'] == 'future-client-schema'
assert data['window']['side_config'] == {'x': -39, 'y': 117, 'pinned': True}
assert data['window']['color'] == {'x': 7, 'y': -8}
records = data['window']
assert len(records) == 11, records.keys()
assert sum(len(value) for value in records.values()) == 28
PY

run_phase 2
SECOND_HASH=$(sha256sum "$CONFIG" | cut -d' ' -f1)
[[ "$FIRST_HASH" == "$SECOND_HASH" ]] || { echo 'fabric-client-window-smoke: unchanged second client rewrote client.toml' >&2; exit 1; }

RESULT=0
printf 'fabric-client-window-smoke: PASS (two fresh client JVMs, 11/28 schema, persistence, unknown retention, unchanged update, shutdown flush)\n'
