#!/usr/bin/env bash
# M-parity P0: start/stop the official NeoForge ("neo") and Fabric ("fab") dev clients through mdb owned sessions.
# Both sides get the same display, window size and a superflat creative cheats world named MekParity, so probes are symmetric.
# Usage: [PARITY_MODULE=core|generators|additions|tools] scripts/parity/session.sh start|stop|status neo|fab
set -uo pipefail
ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
MDB_JAR=${MDB_JAR:-$HOME/server-v2/develop/mcdebug-wt-121/mcdebug-cli.jar}
DISPLAY_NAME=${PARITY_DISPLAY:-:140}
WORLD=${PARITY_WORLD:-MekParity}
LOG_DIR=${PARITY_LOG_DIR:-${TMPDIR:-/tmp}/parity-logs}
mdb() { java -jar "$MDB_JAR" "$@"; }

fail() { printf 'parity-session: %s\n' "$1" >&2; exit 1; }

side_vars() {
  case $1 in
    neo) PROJECT=/tmp/mekanism-neoforge; GAME_DIR=$PROJECT/runs/client; TASK=runClient ;;
    fab) PROJECT=$ROOT/fabric; TASK=runClient
         # Fabric dev clients load one module source set at a time: core (run/), or generators|additions|tools (run-<module>/).
         MODULE=${PARITY_MODULE:-core}
         if [[ $MODULE == core ]]; then GAME_DIR=$ROOT/fabric/run; else GAME_DIR=$ROOT/fabric/run-$MODULE; fi
         export ORG_GRADLE_PROJECT_devModule=$MODULE
         # A reused configuration cache keeps the previous module's run classpath; each module needs its own configuration.
         export GRADLE_OPTS="${GRADLE_OPTS:-} -Dorg.gradle.configuration-cache=false" ;;
    *) fail "side must be neo or fab" ;;
  esac
}

ensure_display() {
  if ! xdpyinfo -display "$DISPLAY_NAME" >/dev/null 2>&1; then
    fail "display $DISPLAY_NAME not reachable; start Xvfb: setsid Xvfb $DISPLAY_NAME -screen 0 1920x1080x24 -nolisten tcp &"
  fi
}

ensure_inputs() {
  case $1 in
    neo)
      [[ -d $PROJECT ]] || fail "missing $PROJECT (clone Mekanism tag v1.21.1-10.7.19.85 there)"
      ls "$GAME_DIR"/mods/kotlinforforge-neoforge-*.jar "$GAME_DIR"/mods/mcdebug-neoforge-*.jar >/dev/null 2>&1 ||
        fail "neo needs mcdebug-neoforge and the four kotlinforforge jars in $GAME_DIR/mods"
      ;;
    fab)
      [[ -e $PROJECT/gradlew && -e $PROJECT/gradle ]] || fail "missing fabric/gradlew|gradle symlinks (ln -s ../gradlew ../gradle in fabric/)"
      if ! ls "$GAME_DIR"/mods/mcdebug-*.jar >/dev/null 2>&1; then
        mkdir -p "$GAME_DIR/mods" && mdb jar --output "$GAME_DIR/mods" >/dev/null 2>&1 || fail "cannot install the mdb fabric jar into $GAME_DIR/mods"
      fi
      # The Fabric mdb jar declares a Kotlin entrypoint adapter; borrow the language jar from the core run dir when missing.
      if ! ls "$GAME_DIR"/mods/fabric-language-kotlin-*.jar >/dev/null 2>&1; then
        cp "$ROOT"/fabric/run/mods/fabric-language-kotlin-*.jar "$GAME_DIR/mods/" 2>/dev/null || fail "no fabric-language-kotlin jar to copy into $GAME_DIR/mods"
      fi
      [[ -f $GAME_DIR/eula.txt ]] || printf 'eula=true\n' > "$GAME_DIR/eula.txt"
      ;;
  esac
}

click_retry() {  # click_retry <session> <text> [tries]; true once the click lands
  local s=$1 t=$2 n=${3:-30}
  for _ in $(seq 1 "$n"); do
    mdb --session "$s" gui click --text "$t" >/dev/null 2>&1 && return 0
    sleep 1
  done
  return 1
}

click_until() {  # click_until <session> <widget> <regex>: click a cycle button until the screen shows the wanted state
  local s=$1 w=$2 re=$3
  for _ in 1 2 3 4; do
    mdb --session "$s" gui screen --brief 2>/dev/null | grep -q "$re" && return 0
    mdb --session "$s" gui click --widget "$w" >/dev/null 2>&1
    mdb --session "$s" gui settle >/dev/null 2>&1
  done
  mdb --session "$s" gui screen --brief 2>/dev/null | grep -q "$re"
}

create_world() {
  local s=$1
  # Skip the first-run accessibility onboarding if it is up (a no-op otherwise), then Singleplayer -> Create New World.
  mdb --session "$s" gui click --text Continue >/dev/null 2>&1 || true
  click_retry "$s" Singleplayer || fail "$s: cannot open Singleplayer"
  click_retry "$s" "Create New World" 15 || true   # shown directly only when no world exists yet
  mdb --session "$s" gui screen --brief 2>/dev/null | grep -q 'CreateWorldScreen' || fail "$s: not on the create-world screen"
  mdb --session "$s" gui type --text "$WORLD" --clear >/dev/null 2>&1 || fail "$s: cannot type world name"
  # Game tab widgets are index-stable on both loaders: 7 Game Mode, 9 Allow Commands; tab 1 is "World".
  click_until "$s" 7 'Game Mode: Creative' || fail "$s: game mode is not Creative"
  click_until "$s" 9 'Allow Commands: ON' || fail "$s: commands are not enabled"
  mdb --session "$s" gui click --widget 1 >/dev/null 2>&1; mdb --session "$s" gui settle >/dev/null 2>&1   # World tab
  click_until "$s" 5 'World Type: Superflat' || fail "$s: world type is not Superflat"
  click_retry "$s" "Create New World" 5 || fail "$s: create failed"
}

cmd_start() {
  local s=$1; side_vars "$s"; ensure_display; ensure_inputs "$s"
  mkdir -p "$LOG_DIR"
  mdb session remove "$s" >/dev/null 2>&1 || true
  local args=(session start "$s" --project "$PROJECT" --task "$TASK" --game-dir "$GAME_DIR" --display "$DISPLAY_NAME" --wait-seconds 600)
  if [[ -d $GAME_DIR/saves/$WORLD ]]; then
    args+=(--world "$WORLD")
  fi
  ( cd "$PROJECT" && mdb "${args[@]}" ) > "$LOG_DIR/$s-start.log" 2>&1 || {
    tail -5 "$LOG_DIR/$s-start.log" >&2
    fail "$s: session start failed (see $LOG_DIR/$s-start.log and mdb session logs $s)"
  }
  if [[ ! -d $GAME_DIR/saves/$WORLD ]]; then
    create_world "$s"
  fi
  for _ in $(seq 1 90); do
    mdb --session "$s" status >/dev/null 2>&1 && mdb --session "$s" gui status --brief 2>/dev/null | grep -q 'singleplayer=true' && break
    sleep 1
  done
  mdb --session "$s" gui status --brief 2>/dev/null | grep -q 'singleplayer=true' || fail "$s: not in world"
  printf 'parity-session: %s ready\n' "$s"
}

cmd_stop() {
  local s=$1; side_vars "$s"
  mdb session stop "$s" >/dev/null 2>&1 || mdb session stop "$s" --force >/dev/null 2>&1 || true
  sleep 6
  if pgrep -af "fml.modFolders|KnotClient" >/dev/null; then
    printf 'parity-session: warning: a game JVM is still alive (other sessions may own it):\n' >&2
    pgrep -af "fml.modFolders|KnotClient" | cut -c1-120 >&2
  fi
  printf 'parity-session: %s stopped\n' "$s"
}

case ${1:-} in
  start) cmd_start "${2:-}" ;;
  stop) cmd_stop "${2:-}" ;;
  status) mdb session list 2>&1 | head -c 600 ;;
  *) fail "usage: session.sh start|stop|status neo|fab" ;;
esac
