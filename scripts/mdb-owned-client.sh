#!/usr/bin/env bash
# Shared ownership for existing handshake smoke clients. Source after ROOT and RUN_ROOT are set.
# Xvfb supplies a display only; mdb is the sole game client launcher and process owner.
MDB_CLIENT_SESSION=
MDB_X_PID=
MDB_CREATED_WRAPPER=0
MDB_CREATED_GRADLE_DIR=0
if command -v mdb >/dev/null 2>&1; then
  MDB_COMMAND=(mdb)
elif command -v mcdebug >/dev/null 2>&1; then
  MDB_COMMAND=(mcdebug)
else
  MDB_COMMAND=(java -jar "${MCDEBUG_CLI_JAR:-/home/wangyu/server-v2/develop/mcdebug-wt-121/mcdebug-cli.jar}")
fi

mdb_owned_prepare() {
  if [[ ! -e "$ROOT/fabric/gradlew" ]]; then
    ln -s ../gradlew "$ROOT/fabric/gradlew"
    MDB_CREATED_WRAPPER=1
  fi
  if [[ ! -e "$ROOT/fabric/gradle" ]]; then
    ln -s ../gradle "$ROOT/fabric/gradle"
    MDB_CREATED_GRADLE_DIR=1
  fi
  setsid Xvfb -displayfd 1 -screen 0 854x480x24 -nolisten tcp >"$RUN_ROOT/display-number" 2>"$RUN_ROOT/xvfb.log" &
  MDB_X_PID=$!
  "$ROOT/scripts/wait-for-log-line.sh" -q -t 15 -p "$MDB_X_PID" '^[0-9]+$' "$RUN_ROOT/display-number"
  MDB_DISPLAY=":$(head -n 1 "$RUN_ROOT/display-number")"
}

mdb_owned_start() {
  local preset=$1 client_dir=$2 session=$3
  shift 3
  mkdir -p "$client_dir/mods"
  "${MDB_COMMAND[@]}" jar --output "$client_dir/mods" >/dev/null
  local kotlin=() jar options=${JAVA_TOOL_OPTIONS:-}
  for jar in "$ROOT"/fabric/run/mods/fabric-language-kotlin-*.jar; do
    [[ -f "$jar" ]] && kotlin+=("$jar")
  done
  [[ ${#kotlin[@]} -eq 1 ]] || { echo 'mdb smoke requires one matching Fabric Language Kotlin runtime in fabric/run/mods' >&2; return 1; }
  cp "${kotlin[0]}" "$client_dir/mods/"
  for jar in "$@"; do options+=" $jar"; done
  MDB_CLIENT_SESSION=$session
  env JAVA_TOOL_OPTIONS="$options" ORG_GRADLE_PROJECT_devModule=core ORG_GRADLE_PROJECT_devClientRun="$preset" \
    "${MDB_COMMAND[@]}" session start "$session" --project "$ROOT/fabric" --game-dir "$client_dir" \
    --display "$MDB_DISPLAY" --wait-seconds 120 >"$client_dir/session-start.json"
  CLIENT_PID=$(python3 - "$client_dir/session-start.json" <<'PY'
import json,sys
record=json.load(open(sys.argv[1]))
assert record['owned'] and record['state'] in ('client_ready','world_ready'), record
print(record['process']['pid'])
PY
)
}

mdb_owned_stop() {
  if [[ -n "$MDB_CLIENT_SESSION" ]]; then
    "${MDB_COMMAND[@]}" session stop "$MDB_CLIENT_SESSION" >/dev/null ||
      "${MDB_COMMAND[@]}" session stop "$MDB_CLIENT_SESSION" --force >/dev/null || return 1
    MDB_CLIENT_SESSION=
  fi
  CLIENT_PID=
}

mdb_owned_cleanup() {
  local result=0
  mdb_owned_stop || result=1
  if [[ -n "$MDB_X_PID" ]]; then
    kill -TERM -- "-$MDB_X_PID" 2>/dev/null || true
    wait "$MDB_X_PID" 2>/dev/null || true
    if ps -eo pgid= | awk -v group="$MDB_X_PID" '$1 == group { found=1 } END { exit !found }'; then
      echo "mdb smoke left display process group $MDB_X_PID" >&2
      result=1
    fi
    MDB_X_PID=
  fi
  (( MDB_CREATED_WRAPPER == 0 )) || rm "$ROOT/fabric/gradlew"
  (( MDB_CREATED_GRADLE_DIR == 0 )) || rm "$ROOT/fabric/gradle"
  return "$result"
}
