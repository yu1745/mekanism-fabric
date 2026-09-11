#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════════════
# wait-rcon.sh — blocks until the Minecraft server log shows the server is
# up and RCON is listening, or until the timeout expires.
#
# Instead of probing the RCON TCP port directly, this polls the server log
# file for the marker lines printed by the server itself:
#   - "RCON running on"  (RCON listener started)
#   - "Done ("           (server fully started)
#   - "Thread RCON Listener started"
#
# Usage: ./wait-rcon.sh <logfile> [timeout_seconds]
#   logfile  path to the server log (e.g. /tmp/tc_runserver.log)
#   timeout  default 300
#
# Exit codes:
#   0  server ready + RCON listening
#   1  timeout
#   2  usage error
# ═══════════════════════════════════════════════════════════════════════

set -u

LOGFILE="${1:-}"
TIMEOUT="${2:-300}"

if [ -z "$LOGFILE" ]; then
  echo "ERROR: usage: $0 <logfile> [timeout_seconds]" >&2
  exit 2
fi

START=$(date +%s)

echo "[wait-rcon] waiting up to ${TIMEOUT}s for server log ${LOGFILE} to show RCON ready ..."

while :; do
  NOW=$(date +%s)
  ELAPSED=$((NOW - START))
  if [ "$ELAPSED" -ge "$TIMEOUT" ]; then
    echo "[wait-rcon] TIMEOUT after ${TIMEOUT}s, RCON never became available (log: ${LOGFILE})" >&2
    echo "[wait-rcon] ---- last log lines ----" >&2
    tail -20 "$LOGFILE" 2>/dev/null | grep -vE "^\s+at " >&2 || true
    exit 1
  fi

  if [ -f "$LOGFILE" ]; then
    # RCON listener is the definitive marker; also accept "Done (" and the
    # RCON thread line as fallbacks since they appear around the same time.
    if grep -qE "RCON running on|Thread RCON Listener started|Done \(" "$LOGFILE" 2>/dev/null; then
      echo "[wait-rcon] RCON is READY after ${ELAPSED}s (log: ${LOGFILE})"
      exit 0
    fi

    # If the server crashed, fail fast instead of waiting out the timeout.
    if grep -qE "Failed to load datapacks|Failed to start the minecraft server|Failed to initialize server|Perhaps a server is already running|BUILD FAILED|Exception in thread \"main\"" "$LOGFILE" 2>/dev/null; then
      echo "[wait-rcon] server FAILED to start (see log: ${LOGFILE})" >&2
      tail -20 "$LOGFILE" | grep -vE "^\s+at " >&2 || true
      exit 1
    fi
  fi

  sleep 3
done
