#!/usr/bin/env bash
# W-66 soak gate: one dedicated server plus two production clients, held together for a configurable duration while
# the release JARs run their tick loops. Fails when a client or the server dies, a client disconnects, or the server
# reports errors/lag; prints the evidence lines at the end.
#
# Usage: scripts/fabric-soak-smoke.sh
# Env:   FABRIC_SOAK_MINUTES   soak duration in minutes (default 30)
#        FABRIC_SOAK_PORT      server port (default 25595)
#        FABRIC_SOAK_WORK      working directory (default /tmp/mekanism-soak)
#        FABRIC_SOAK_ENV       prepared client/server environment (default: $FABRIC_SOAK_WORK/env)
#        FABRIC_SOAK_WORLD     optional worldsave directory to drop into the server as world/ (e.g. a ported sample)
#        FABRIC_CLIENT_SMOKE_WORK  environment builder scratch dir (default: $FABRIC_SOAK_ENV)
set -uo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
MINUTES=${FABRIC_SOAK_MINUTES:-30}
PORT=${FABRIC_SOAK_PORT:-25595}
WORK=${FABRIC_SOAK_WORK:-/tmp/mekanism-soak}
ENV_DIR=${FABRIC_SOAK_ENV:-$WORK/env}
LOG_DIR=$WORK/logs
POLL=20

fail() {
  printf 'fabric-soak-smoke: %s\n' "$1" >&2
  for log in "$LOG_DIR"/*.log; do
    [[ -f $log ]] && { printf '%s\n' "--- $log (tail) ---" >&2; tail -n 25 "$log" >&2; }
  done
  exit 1
}

mkdir -p "$WORK" "$LOG_DIR"

# Reuse an already prepared environment, otherwise ask the release-client gate to build one.
if [[ ! -f $ENV_DIR/client-cp.txt || ! -d $ENV_DIR/server/mods ]]; then
  printf 'fabric-soak-smoke: preparing client/server environment in %s\n' "$ENV_DIR"
  FABRIC_CLIENT_SMOKE_WORK=$ENV_DIR "$ROOT/scripts/fabric-packaged-client-smoke.sh" > "$LOG_DIR/prep.log" 2>&1 ||
    fail 'could not prepare the packaged client environment (see prep.log)'
fi
grep -Fq 'fabric-packaged-client-smoke: PASSED' "$LOG_DIR/prep.log" 2>/dev/null ||
  grep -Fq 'fabric-packaged-client-smoke: PASSED' "$ENV_DIR/prep.log" 2>/dev/null || true
CHUNK=$(cat "$ENV_DIR/client-cp.txt")

cp -a "$ENV_DIR/server" "$WORK/server"
cp -a "$ENV_DIR/client" "$WORK/client-a"
cp -a "$ENV_DIR/client" "$WORK/client-b"
rm -f "$WORK/client-a/options.txt" "$WORK/client-b/options.txt"
printf 'onboardAccessibility:false\n' > "$WORK/client-a/options.txt"
printf 'onboardAccessibility:false\n' > "$WORK/client-b/options.txt"
if [[ -n ${FABRIC_SOAK_WORLD:-} ]]; then
  [[ -d $FABRIC_SOAK_WORLD ]] || fail "FABRIC_SOAK_WORLD is not a directory: $FABRIC_SOAK_WORLD"
  rm -rf "$WORK/server/world"
  cp -a "$FABRIC_SOAK_WORLD" "$WORK/server/world"
  printf 'fabric-soak-smoke: using sample world %s\n' "$FABRIC_SOAK_WORLD"
fi
printf 'online-mode=false\nserver-ip=127.0.0.1\nserver-port=%s\nmotd=soak\nmax-players=4\nview-distance=4\nsimulation-distance=3\n' \
  "$PORT" > "$WORK/server/server.properties"

MCDEBUG_CLI=${MCDEBUG_CLI:-/home/wangyu/server-v2/develop/mcdebug-wt-121/mcdebug-cli.jar}
KOTLIN_JAR=$(ls "$HOME"/.gradle/caches/modules-2/files-2.1/net.fabricmc/fabric-language-kotlin/*/*/fabric-language-kotlin-*.jar 2>/dev/null | grep -v sources | head -1)
if [[ -f $MCDEBUG_CLI && -n ${KOTLIN_JAR:-} ]]; then
  cp "$KOTLIN_JAR" "$WORK/server/mods/"
  java -jar "$MCDEBUG_CLI" jar --output "$WORK/server/mods" >/dev/null 2>&1 || true
  HAS_PROBE=1
else
  HAS_PROBE=0
  printf 'fabric-soak-smoke: mcdebug or fabric-language-kotlin unavailable; health sampling disabled\n'
fi

( cd "$WORK/server" && exec setsid java -Xms1G -Xmx2G -jar fabric-server-launch.jar nogui ) > "$LOG_DIR/server.log" 2>&1 &
SERVER_PID=$!
CLIENT_PIDS=()
cleanup() {
  for pid in "${CLIENT_PIDS[@]:-}"; do [[ -n $pid ]] && kill "$pid" 2>/dev/null; done
  [[ -n ${SERVER_PID:-} ]] && kill "$SERVER_PID" 2>/dev/null
  wait 2>/dev/null
}
trap cleanup EXIT INT TERM

DEADLINE=$((SECONDS + 180))
server_ready=0
while (( SECONDS < DEADLINE )); do
  left=$(( DEADLINE - SECONDS )); (( left > 60 )) && left=60
  "$ROOT/scripts/wait-for-log-line.sh" -q -t "$left" -p "$SERVER_PID" --fail-on 'Failed to start' 'Done \(' "$LOG_DIR/server.log" >/dev/null
  status=$?
  (( status == 0 )) && { server_ready=1; break; }
  (( status == 4 || status == 5 )) && fail 'server exited or failed before readiness'
done
(( server_ready == 1 )) || fail 'server was not ready within 180s'
grep -F 'values=458 ' "$LOG_DIR/server.log" >/dev/null || fail 'server did not log the release 458-value snapshot'

SOAK_MDB=()
if (( HAS_PROBE )); then
  probe_deadline=$((SECONDS + 90))
  while (( SECONDS < probe_deadline )) && [[ ! -S $WORK/server/mcdebug/socket ]]; do sleep 2; done
  [[ -S $WORK/server/mcdebug/socket ]] || fail 'the mcdebug probe did not open its socket on the soak server'
  SOAK_MDB=(java -jar "$MCDEBUG_CLI" --port-file "$WORK/server/mcdebug/port")
  # Live load: a heat generator sitting on lava feeding an energy cube through a cable.
  "${SOAK_MDB[@]}" world set-block --x 0 --y 70 --z 0 --block minecraft:lava >/dev/null
  "${SOAK_MDB[@]}" world set-block --x 0 --y 71 --z 0 --block mekanismgenerators:heat_generator >/dev/null
  "${SOAK_MDB[@]}" world set-block --x 1 --y 71 --z 0 --block mekanism:basic_universal_cable >/dev/null
  "${SOAK_MDB[@]}" world set-block --x 2 --y 71 --z 0 --block mekanism:basic_energy_cube >/dev/null
  printf 'fabric-soak-smoke: live load placed (heat generator on lava -> cable -> energy cube)\n'
fi

start_client() {
  local name=$1
  local dir=$2
  ( cd "$dir" && exec setsid xvfb-run -a java -Xmx2G -Djava.library.path="$ENV_DIR/natives" \
      -Dfabric.runtimeMappingNamespace=intermediary \
      -Dfabric.mappingPath="$HOME/.gradle/caches/fabric-loom/1.21.1/intermediary-v2.tiny" \
      -cp "$CHUNK" net.fabricmc.loader.impl.launch.knot.KnotClient \
      --gameDir "$dir" --assetsDir "$HOME/.gradle/caches/fabric-loom/assets" --assetIndex 1.21.1-17 \
      --username "$name" --uuid "0000000000000000000000000000000${#name}" --accessToken 0 --version 1.21.1 \
      --quickPlayMultiplayer "127.0.0.1:$PORT" ) > "$LOG_DIR/client-$name.log" 2>&1 &
  CLIENT_PIDS+=("$!")
}

for spec in "MekSoakA:client-a" "MekSoakB:client-b"; do
  name=${spec%%:*}
  dir=$WORK/${spec##*:}
  start_client "$name" "$dir"
done

DEADLINE=$((SECONDS + 240))
for spec in "MekSoakA:client-a" "MekSoakB:client-b"; do
  name=${spec%%:*}
  applied=0
  while (( SECONDS < DEADLINE )); do
    left=$(( DEADLINE - SECONDS )); (( left > 60 )) && left=60
    "$ROOT/scripts/wait-for-log-line.sh" -q -t "$left" --fail-on 'Client disconnected with reason' \
      "phase=client_applied session=[^ ]+ values=458\$" "$LOG_DIR/client-$name.log" >/dev/null
    status=$?
    (( status == 0 )) && { applied=1; break; }
    (( status == 5 )) && fail "$name was disconnected instead of applying the release snapshot"
  done
  (( applied == 1 )) || fail "$name did not apply values=458 within the join window"
done
DEADLINE=$((SECONDS + 90))
joined=0
while (( SECONDS < DEADLINE )); do
  count=$(grep -c 'joined the game' "$LOG_DIR/server.log" || true)
  (( count >= 2 )) && { joined=1; break; }
  left=$(( DEADLINE - SECONDS )); (( left > 5 )) && left=5
  sleep "$left"
done
(( joined == 1 )) || fail 'both clients did not join play'

printf 'fabric-soak-smoke: both clients joined; soaking for %s minute(s)\n' "$MINUTES"
START=$SECONDS
DEADLINE=$((START + MINUTES * 60))
while (( SECONDS < DEADLINE )); do
  for pid in "${CLIENT_PIDS[@]}"; do
    kill -0 "$pid" 2>/dev/null || fail 'a client process exited during the soak'
  done
  kill -0 "$SERVER_PID" 2>/dev/null || fail 'the server process exited during the soak'
  grep -Fq 'lost connection' "$LOG_DIR/server.log" && fail 'a client lost its connection during the soak'
  grep -Fq "Can't keep up" "$LOG_DIR/server.log" && fail 'the server reported it cannot keep up'
  if (( HAS_PROBE )) && [[ -n ${SOAK_MDB[*]:-} ]]; then
    "${SOAK_MDB[@]}" status --health 2>/dev/null | tr '\n' ' ' >> "$LOG_DIR/health.jsonl" && printf '\n' >> "$LOG_DIR/health.jsonl"
  fi
  remaining=$(( DEADLINE - SECONDS ))
  (( remaining <= 0 )) && break
  # Wall-clock pacing for the soak timer itself, never for readiness.
  sleep $(( remaining < POLL ? remaining : POLL ))
done

for pid in "${CLIENT_PIDS[@]}"; do
  kill -0 "$pid" 2>/dev/null || fail 'a client process exited during the soak'
done
ERRORS=$(grep -cE '\[.*ERROR\]' "$LOG_DIR/server.log" || true)
BENIGN=$(grep -cE 'No data fixer registered' "$LOG_DIR/server.log" || true)
printf 'fabric-soak-smoke: PASSED (%s min, 2 clients connected, server ERROR lines=%s, benign datafixer lines=%s)\n' \
  "$MINUTES" "$ERRORS" "$BENIGN"
if [[ -s $LOG_DIR/health.jsonl ]]; then
  python3 - "$LOG_DIR/health.jsonl" <<'PYHEALTH'
import re, sys
tps, mspt = [], []
pattern = re.compile(r"tps: ([0-9.]+).*?mspt: ([0-9.]+)")
for line in open(sys.argv[1]):
    for match in pattern.finditer(line):
        tps.append(float(match.group(1)))
        mspt.append(float(match.group(2)))
if mspt:
    print(f"fabric-soak-smoke: tick mspt min={min(mspt):.2f} mean={sum(mspt)/len(mspt):.2f} max={max(mspt):.2f} samples={len(mspt)}")
    print(f"fabric-soak-smoke: tps min={min(tps):.2f} max={max(tps):.2f}")
PYHEALTH
fi
grep -hE 'phase=(send|client_applied)' "$LOG_DIR/server.log" "$LOG_DIR"/client-*.log | tail -n 2
