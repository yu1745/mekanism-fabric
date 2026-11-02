#!/usr/bin/env bash
# M-parity: disposable dedicated Fabric server running the four RELEASE jars (dist/) with mdb's server plane, as the Fabric side of the
# functional probes. One process loads core+generators+additions+tools together, matching the official dev client that loads all modules.
# Usage: scripts/parity/fab_server.sh start|stop|status      (dir: $PARITY_FAB_SERVER_DIR, default /tmp/parity-fab-server)
set -uo pipefail
ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
DIR=${PARITY_FAB_SERVER_DIR:-/tmp/parity-fab-server}
CACHE=${FABRIC_SMOKE_CACHE_DIR:-$HOME/.cache/mekanism-fabric-smoke}
MDB_JAR=${MDB_JAR:-$HOME/server-v2/develop/mcdebug-wt-121/mcdebug-cli.jar}
fail() { printf 'fab_server: %s\n' "$1" >&2; exit 1; }
pidfile=$DIR/server.pid

alive() { [[ -f $pidfile ]] && kill -0 "$(cat "$pidfile")" 2>/dev/null; }

start() {
  alive && { echo "fab_server: already running (pid $(cat "$pidfile"))"; return 0; }
  jars=$(ls "$ROOT"/dist/*.jar 2>/dev/null | wc -l); [[ $jars -eq 4 ]] || fail "expected four release jars in dist/ (found $jars)"
  for f in fabric-server-launch.jar fabric-api-*.jar energy-*.jar; do ls "$CACHE"/$f >/dev/null 2>&1 || fail "missing $CACHE/$f (run scripts/fabric-energy-chain-smoke.sh once to populate the cache)"; done
  rm -rf "$DIR"; mkdir -p "$DIR/mods"
  cp "$CACHE"/fabric-server-launch.jar "$DIR/"
  for d in libraries versions .fabric; do [[ -d $CACHE/$d ]] && cp -a "$CACHE/$d" "$DIR/$d"; done
  cp "$CACHE"/fabric-api-*.jar "$CACHE"/energy-*.jar "$ROOT"/dist/*.jar "$DIR/mods/"
  cp "$ROOT"/fabric/run/mods/fabric-language-kotlin-*.jar "$DIR/mods/"
  java -jar "$MDB_JAR" jar --output "$DIR/mods" >/dev/null 2>&1 || fail "cannot install the mdb fabric jar"
  # PARITY_FAB_WORLD_SRC: seed the server with a copy of an existing save (e.g. the official NeoForge MekParity world).
  if [[ -n ${PARITY_FAB_WORLD_SRC:-} ]]; then
    [[ -f $PARITY_FAB_WORLD_SRC/level.dat ]] || fail "PARITY_FAB_WORLD_SRC has no level.dat: $PARITY_FAB_WORLD_SRC"
    cp -a "$PARITY_FAB_WORLD_SRC" "$DIR/world"
  fi
  # Optional: seed the world from another save (PARITY_FAB_WORLD_SRC), e.g. one written by the official NeoForge client.
  if [[ -n ${PARITY_FAB_WORLD_SRC:-} ]]; then
    [[ -d $PARITY_FAB_WORLD_SRC ]] || fail "PARITY_FAB_WORLD_SRC is not a directory: $PARITY_FAB_WORLD_SRC"
    cp -a "$PARITY_FAB_WORLD_SRC" "$DIR/world"
    rm -f "$DIR/world/session.lock"
  fi
  printf 'eula=true\n' > "$DIR/eula.txt"
  printf 'online-mode=false\nserver-port=%s\nspawn-protection=0\nmax-players=1\nview-distance=4\nsync-chunk-writes=false\nlevel-type=minecraft:flat\ngamemode=creative\nenable-command-block=true\nlevel-name=world\n' "${PARITY_FAB_PORT:-25598}" > "$DIR/server.properties"
  mkfifo "$DIR/console"; exec 9<>"$DIR/console"
  (cd "$DIR" && exec setsid java -Xms512M -Xmx3G -jar fabric-server-launch.jar nogui < console > "$DIR/server.log" 2>&1) &
  echo $! > "$pidfile"
  "$ROOT/scripts/wait-for-log-line.sh" -t 55 -p "$(cat "$pidfile")" --fail-on 'Encountered an unexpected exception|FAILED TO BIND|Exception in thread' 'Done \(' "$DIR/server.log" >/dev/null
  rc=$?
  case $rc in
    0) echo "fab_server: ready (pid $(cat "$pidfile"), dir $DIR)" ;;
    3) fail "not ready within 55s; call start again to keep waiting is not supported - see $DIR/server.log" ;;
    *) fail "server exited or failed (rc=$rc); see $DIR/server.log" ;;
  esac
}

stop() {
  if alive; then
    pid=$(cat "$pidfile")
    printf 'stop\n' > "$DIR/console" 2>/dev/null || true
    for _ in $(seq 1 30); do kill -0 "$pid" 2>/dev/null || break; sleep 1; done
    kill -TERM -- "-$pid" 2>/dev/null || true; sleep 2; kill -KILL -- "-$pid" 2>/dev/null || true
    echo "fab_server: stopped"
  else
    echo "fab_server: not running"
  fi
  rm -f "$pidfile"
}

case ${1:-status} in
  start) start ;;
  stop) stop ;;
  status) alive && echo "running pid=$(cat "$pidfile") dir=$DIR" || echo "stopped dir=$DIR" ;;
  *) fail "usage: fab_server.sh start|stop|status" ;;
esac
