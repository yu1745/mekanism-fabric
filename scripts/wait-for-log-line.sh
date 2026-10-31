#!/usr/bin/env bash
# Block until a magic line appears in a log (or a count of lines), instead of sleeping and re-checking.
#
# Event-driven: `tail --follow=name --retry` writes into a FIFO and this script reads it line by line, so the caller
# never polls on a fixed interval. A match returns immediately; the deadline is a single `timeout` on the follower, not
# a repeated sleep. The exit status distinguishes the four outcomes a gate needs.
#
# Usage:
#   scripts/wait-for-log-line.sh [options] PATTERN [FILE...]
#
#   PATTERN            extended regular expression matched against every new line (grep -E syntax)
#   FILE...            logs to follow; each may be missing at start (tail --retry waits for it to appear)
#
# Options:
#   -t, --timeout SEC  deadline in seconds (default 60, hard maximum 60); exit status 3 means it expired
#   -n, --count N      required number of matching lines (default 1)
#   -p, --pid PID      stop early when that process is gone (exit 4) instead of waiting for the deadline
#       --fail-on RE   exit 5 as soon as a line matches this failure expression (crash/port/refusal signatures)
#   -F, --fixed        treat PATTERN as a literal string (like grep -F) instead of an ERE
#   -q, --quiet        do not echo matching lines
#   -h, --help         this text
#
# Exit status: 0 matched, 3 timeout, 4 watched process exited, 5 --fail-on matched, 2 usage error.
#
# Fallback (never waits forever): the deadline is always armed (default 60 s) and can never exceed 60 s per
# invocation. For a longer budget the caller loops over 3 (timeout) in slices and enforces its own overall deadline. A caller must treat 3, 4 and 5 as FAILURE, never as a
# pass, and should pass --pid for the process that writes the log so a crash returns at once with 4.
set -uo pipefail

TIMEOUT=60
MAX_TIMEOUT=60
COUNT=1
WATCH_PID=
FAIL_PATTERN=
QUIET=0
FIXED=0

usage() {
  sed -n '2,26p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
}

while (( $# > 0 )); do
  case $1 in
    -t|--timeout) TIMEOUT=${2:?--timeout needs a value}; shift 2 ;;
    -n|--count) COUNT=${2:?--count needs a value}; shift 2 ;;
    -p|--pid) WATCH_PID=${2:?--pid needs a value}; shift 2 ;;
    --fail-on) FAIL_PATTERN=${2:?--fail-on needs a value}; shift 2 ;;
    -q|--quiet) QUIET=1; shift ;;
    -F|--fixed) FIXED=1; shift ;;
    -h|--help) usage; exit 0 ;;
    --) shift; break ;;
    -*) echo "wait-for-log-line: unknown option: $1" >&2; usage >&2; exit 2 ;;
    *) break ;;
  esac
done

if (( $# < 1 )); then
  echo 'wait-for-log-line: missing PATTERN' >&2
  exit 2
fi
PATTERN=$1
shift
if (( $# < 1 )); then
  echo 'wait-for-log-line: missing FILE' >&2
  exit 2
fi
if [[ ! $TIMEOUT =~ ^[1-9][0-9]*$ ]] || (( TIMEOUT > MAX_TIMEOUT )); then
  echo "wait-for-log-line: --timeout must be an integer from 1 to $MAX_TIMEOUT (re-invoke in slices for longer budgets)" >&2
  exit 2
fi
if [[ ! $COUNT =~ ^[1-9][0-9]*$ ]]; then
  echo 'wait-for-log-line: --count must be a positive integer' >&2
  exit 2
fi
if [[ -n $WATCH_PID ]] && ! [[ $WATCH_PID =~ ^[0-9]+$ ]]; then
  echo 'wait-for-log-line: --pid must be a process id' >&2
  exit 2
fi

FILES=("$@")
FOLLOW=(-n +1 -F)
for file in "${FILES[@]}"; do
  if [[ ! -e $file ]]; then
    # GNU tail --retry keeps the follow alive until the log is created; other tails cannot, so fail loudly there.
    if tail --help 2>/dev/null | grep -q -- '--retry'; then
      FOLLOW+=(--retry)
    else
      echo "wait-for-log-line: file does not exist yet and this tail has no --retry: $file" >&2
      exit 2
    fi
    break
  fi
done
# GNU tail ends the follow itself when the watched process exits, so a dead process returns at once, not at the deadline.
if [[ -n $WATCH_PID ]] && tail --help 2>/dev/null | grep -q -- '--pid'; then
  FOLLOW+=(--pid="$WATCH_PID")
fi

WORK_DIR=$(mktemp -d "${TMPDIR:-/tmp}/wait-for-log-line.XXXXXX")
FIFO="$WORK_DIR/follow"
TAIL_PID=
cleanup() {
  if [[ -n $TAIL_PID ]]; then
    kill "$TAIL_PID" 2>/dev/null
    wait "$TAIL_PID" 2>/dev/null
    TAIL_PID=
  fi
  rm -rf "$WORK_DIR"
}
trap cleanup EXIT INT TERM

mkfifo "$FIFO" || exit 2
timeout --signal=TERM "${TIMEOUT}s" tail "${FOLLOW[@]}" "${FILES[@]}" >"$FIFO" 2>/dev/null &
TAIL_PID=$!

matched=0
failed=0
# `|| [[ -n $line ]]` keeps a last line that was written without a trailing newline before the follower ended.
while IFS= read -r line || [[ -n $line ]]; do
  if [[ -n $FAIL_PATTERN && $line =~ $FAIL_PATTERN ]]; then
    printf 'wait-for-log-line: failure marker: %s\n' "$line" >&2
    failed=1
    break
  fi
  if (( FIXED )); then
    [[ $line == *"$PATTERN"* ]] || continue
  else
    [[ $line =~ $PATTERN ]] || continue
  fi
  matched=$((matched + 1))
  (( QUIET )) || printf '%s\n' "$line"
  (( matched >= COUNT )) && break
done <"$FIFO"

cleanup
trap - EXIT INT TERM

(( failed )) && exit 5
(( matched >= COUNT )) && exit 0
if [[ -n $WATCH_PID ]] && ! kill -0 "$WATCH_PID" 2>/dev/null; then
  exit 4
fi
exit 3
