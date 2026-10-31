#!/usr/bin/env bash
# W-70: one command that runs the server-side release gates in order (clean build, four-JAR static checks, packaged
# server, paired network, configuration rejection, machine runtime). It adds no checks of its own: every step is an
# existing script. The release-JAR client path is NOT re-run here - that has one-off evidence only (W-69).
#
# Usage: scripts/release-check.sh [--skip-build]
# Exit code: 0 only when every step passed; on the first failure it prints the step, its log path and the first
# error line, then stops.
set -uo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
LOG_DIR=${RELEASE_CHECK_LOG_DIR:-/tmp/release-check}
SKIP_BUILD=0
[[ ${1:-} == '--skip-build' ]] && SKIP_BUILD=1
mkdir -p "$LOG_DIR"

fail() {
  printf 'release-check: FAILED at %s\n' "$1" >&2
  printf 'release-check: log %s\n' "$2" >&2
  local first
  first=$(grep -m 1 -E 'ERROR|error:|Exception|FAILED|failed|: expected|: cannot|leaked|mismatch' "$2" 2>/dev/null)
  [[ -n $first ]] && printf 'release-check: first error: %s\n' "$first" >&2 ||
    printf 'release-check: first error: (no matching line; see the log)\n' >&2
  exit 1
}

STEP=0
run_step() {
  local name=$1
  shift
  STEP=$((STEP + 1))
  local log=$LOG_DIR/$(printf '%02d' "$STEP")-$(printf '%s' "$name" | tr ' /' '__').log
  printf 'release-check: [%d] %s\n' "$STEP" "$name"
  local start=$SECONDS
  "$@" >"$log" 2>&1
  local status=$?
  printf 'release-check: [%d] %s -> exit %d in %ss\n' "$STEP" "$name" "$status" "$((SECONDS - start))"
  (( status == 0 )) || fail "$name" "$log"
}

(( SKIP_BUILD )) || run_step 'clean four-JAR build' "$ROOT/build.sh" --clean
run_step 'packaged static four-JAR gates' env FABRIC_SMOKE_STATIC_ONLY=1 "$ROOT/scripts/fabric-packaged-smoke.sh"
run_step 'packaged server (50 mods, 458 values)' "$ROOT/scripts/fabric-packaged-smoke.sh"
run_step 'paired network (two clients, handshake)' "$ROOT/scripts/fabric-paired-network-smoke.sh"
run_step 'configuration rejection (three clients rejected)' "$ROOT/scripts/fabric-configuration-rejection-smoke.sh"
run_step 'machine runtime smoke' "$ROOT/scripts/fabric-machine-smoke.sh"

printf 'release-check: PASSED (%d steps, logs in %s)\n' "$STEP" "$LOG_DIR"
