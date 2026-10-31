#!/usr/bin/env bash
# Release-JAR client gate: a clean four-JAR server plus a production (intermediary) client that must apply the whole
# release SERVER snapshot and join play. Everything is assembled offline from the Loom/Gradle caches and dist/.
#
# Usage: scripts/fabric-packaged-client-smoke.sh
# Env:   FABRIC_CLIENT_SMOKE_PORT (default 25599), FABRIC_CLIENT_SMOKE_TIMEOUT (default 240)
set -uo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
PORT=${FABRIC_CLIENT_SMOKE_PORT:-25599}
TIMEOUT=${FABRIC_CLIENT_SMOKE_TIMEOUT:-240}
WORK=${FABRIC_CLIENT_SMOKE_WORK:-/tmp/mekanism-packaged-client}
CACHE=${FABRIC_SMOKE_CACHE_DIR:-${XDG_CACHE_HOME:-$HOME/.cache}/mekanism-fabric-smoke}
GAME=1.21.1
ASSET_INDEX=1.21.1-17
LOOM_CACHE=$HOME/.gradle/caches/fabric-loom
GRADLE_CACHE=$HOME/.gradle/caches/modules-2/files-2.1
ASSETS=$LOOM_CACHE/assets
EXPECTED_VALUES=458

fail() {
  printf 'fabric-packaged-client-smoke: %s\n' "$1" >&2
  [[ -f "$WORK/server.log" ]] && tail -n 40 "$WORK/server.log" >&2
  [[ -f "$WORK/client.log" ]] && tail -n 40 "$WORK/client.log" >&2
  exit 1
}

for command in java python3 xvfb-run jar; do
  command -v "$command" >/dev/null 2>&1 || fail "required command not found: $command"
done
[[ $(ls "$ROOT"/dist/*.jar 2>/dev/null | wc -l) -eq 4 ]] || fail 'dist/ must hold the four release JARs (run ./build.sh first)'
SERVER_LAUNCHER=$(ls "$CACHE"/fabric-server-*.jar 2>/dev/null | head -1)
FABRIC_API=$(ls "$CACHE"/fabric-api-*.jar 2>/dev/null | head -1)
ENERGY_API=$(ls "$CACHE"/energy-*.jar 2>/dev/null | head -1)
[[ -n $SERVER_LAUNCHER && -n $FABRIC_API && -n $ENERGY_API ]] || fail "missing cached server launcher / fabric-api / energy API in $CACHE"

rm -rf "$WORK"
mkdir -p "$WORK/server/mods" "$WORK/client/mods" "$WORK/natives"
cp "$SERVER_LAUNCHER" "$WORK/server/fabric-server-launch.jar"
cp "$FABRIC_API" "$ENERGY_API" "$WORK/server/mods/"
cp "$ROOT"/dist/*.jar "$WORK/server/mods/"
cp "$FABRIC_API" "$ENERGY_API" "$ROOT"/dist/*.jar "$WORK/client/mods/"
printf 'eula=true\n' > "$WORK/server/eula.txt"
printf 'online-mode=false\nserver-ip=127.0.0.1\nserver-port=%s\nmotd=packaged client smoke\nmax-players=2\nview-distance=2\nsimulation-distance=2\n' \
  "$PORT" > "$WORK/server/server.properties"
printf 'onboardAccessibility:false\n' > "$WORK/client/options.txt"

# Assemble the production client classpath offline: intermediary game jar + Mojang libraries from the Gradle cache +
# loader and its runtime dependencies from the same cache. The release mods stay in mods/ so the loader discovers them.
python3 - "$LOOM_CACHE" "$GRADLE_CACHE" "$WORK" > "$WORK/client-cp.txt" <<'PYEOF'
import glob, json, os, re, sys, zipfile

loom, gradle, work = sys.argv[1], sys.argv[2], sys.argv[3]
info = json.load(open(f"{loom}/1.21.1/mojang_minecraft_info.json"))


def allowed(lib):
    rules = lib.get('rules')
    if not rules:
        return True
    ok = False
    for rule in rules:
        name = rule.get('os', {}).get('name')
        if rule['action'] == 'allow' and (name is None or name == 'linux'):
            ok = True
        elif rule['action'] == 'disallow' and name == 'linux':
            ok = False
    return ok


def resolve(coord):
    parts = coord.split(':')
    group, artifact, version = parts[0], parts[1], parts[2]
    classifier = parts[3] if len(parts) > 3 else None
    base = f"{gradle}/{group}/{artifact}/{version}"
    hits = glob.glob(f"{base}/*/{artifact}-{version}-{classifier}.jar" if classifier else f"{base}/*/{artifact}-{version}.jar")
    return hits[0] if hits else None


entries, natives, missing = [], [], []
for lib in info['libraries']:
    if not allowed(lib):
        continue
    path = resolve(lib['name'])
    if path:
        entries.append(path)
    else:
        missing.append(lib['name'])
if missing:
    print('missing libraries: ' + ', '.join(missing), file=sys.stderr)
    raise SystemExit(1)

game_jars = [p for p in glob.glob(f"{loom}/minecraftMaven/net/minecraft/minecraft-merged-intermediary/1.21.1-*/*.jar") if not p.endswith('.backup')]
if len(game_jars) != 1:
    print(f"expected one intermediary game jar, found {game_jars}", file=sys.stderr)
    raise SystemExit(1)

deps = []
for group, artifact in (('net.fabricmc', 'fabric-loader'), ('net.fabricmc', 'sponge-mixin'),
                        ('net.fabricmc', 'tiny-mappings-parser'),
                        ('io.github.llamalad7', 'mixinextras-fabric'), ('org.ow2.asm', 'asm'),
                        ('org.ow2.asm', 'asm-analysis'), ('org.ow2.asm', 'asm-commons'),
                        ('org.ow2.asm', 'asm-tree'), ('org.ow2.asm', 'asm-util')):
    hits = sorted(p for p in glob.glob(f"{gradle}/{group}/{artifact}/*/*/*.jar") if 'sources' not in p and 'javadoc' not in p)
    if not hits:
        print(f"missing loader dependency: {group}:{artifact}", file=sys.stderr)
        raise SystemExit(1)
    if artifact == 'fabric-loader':
        hits = [p for p in hits if '/0.19.5/' in p] or hits
    deps.append(hits[-1])

natives_jars = glob.glob(f"{gradle}/org.lwjgl/*/3.3.3/*/*-natives-linux.jar")
for jar in natives_jars:
    with zipfile.ZipFile(jar) as archive:
        for name in archive.namelist():
            if name.endswith('.so'):
                with open(os.path.join(f"{work}/natives", os.path.basename(name)), 'wb') as handle:
                    handle.write(archive.read(name))

classpath = game_jars + entries + deps
print(':'.join(classpath))
PYEOF
[[ -s "$WORK/client-cp.txt" ]] || fail 'could not assemble the production client classpath'
NATIVES=$(find "$WORK/natives" -name '*.so' | head -1)
[[ -n $NATIVES ]] || fail 'no LWJGL natives were extracted for the client'

( cd "$WORK/server" && exec setsid java -Xms512M -Xmx2G -jar fabric-server-launch.jar nogui ) > "$WORK/server.log" 2>&1 &
SERVER_PID=$!
cleanup() {
  [[ -n ${CLIENT_PID:-} ]] && kill "$CLIENT_PID" 2>/dev/null
  [[ -n ${SERVER_PID:-} ]] && kill "$SERVER_PID" 2>/dev/null
  wait 2>/dev/null
}
trap cleanup EXIT INT TERM

DEADLINE=$((SECONDS + TIMEOUT))
server_ready=0
while (( SECONDS < DEADLINE )); do
  left=$(( DEADLINE - SECONDS )); (( left > 60 )) && left=60
  "$ROOT/scripts/wait-for-log-line.sh" -q -t "$left" -p "$SERVER_PID" --fail-on 'Failed to start' 'Done \(' "$WORK/server.log" >/dev/null
  status=$?
  (( status == 0 )) && { server_ready=1; break; }
  (( status == 4 || status == 5 )) && fail 'server exited or failed before readiness'
done
(( server_ready == 1 )) || fail "server was not ready within ${TIMEOUT}s"

xvfb-run -a java -Xmx2G -Djava.library.path="$WORK/natives" \
  -Dfabric.runtimeMappingNamespace=intermediary \
  -Dfabric.mappingPath="$LOOM_CACHE/1.21.1/intermediary-v2.tiny" \
  -cp "$(cat "$WORK/client-cp.txt")" net.fabricmc.loader.impl.launch.knot.KnotClient \
  --gameDir "$WORK/client" --assetsDir "$ASSETS" --assetIndex "$ASSET_INDEX" \
  --username MekProd --uuid 00000000000000000000000000000001 --accessToken 0 --version "$GAME" \
  --quickPlayMultiplayer "127.0.0.1:$PORT" > "$WORK/client.log" 2>&1 &
CLIENT_PID=$!

applied=0
while (( SECONDS < DEADLINE )); do
  left=$(( DEADLINE - SECONDS )); (( left > 60 )) && left=60
  "$ROOT/scripts/wait-for-log-line.sh" -q -t "$left" --fail-on 'Client disconnected with reason' \
    "phase=client_applied session=[^ ]+ values=$EXPECTED_VALUES\$" "$WORK/client.log" >/dev/null
  status=$?
  (( status == 0 )) && { applied=1; break; }
  (( status == 5 )) && fail 'client was disconnected instead of applying the release snapshot'
  kill -0 "$CLIENT_PID" 2>/dev/null || fail 'client exited before applying the release snapshot'
done
(( applied == 1 )) || fail "client did not apply values=$EXPECTED_VALUES within ${TIMEOUT}s"

grep -Fq "phase=send session=" "$WORK/server.log" || fail 'server did not record the snapshot send'
joined=0
while (( SECONDS < DEADLINE )); do
  left=$(( DEADLINE - SECONDS )); (( left > 60 )) && left=60
  "$ROOT/scripts/wait-for-log-line.sh" -q -t "$left" -p "$SERVER_PID" 'joined the game' "$WORK/server.log" >/dev/null
  status=$?
  (( status == 0 )) && { joined=1; break; }
  (( status == 4 )) && fail 'server exited before the client joined play'
done
(( joined == 1 )) || fail 'client did not join play'
printf 'fabric-packaged-client-smoke: PASSED (release JARs: client applied %s SERVER values and joined play)\n' "$EXPECTED_VALUES"
grep -hE "phase=(send|client_applied)" "$WORK/server.log" "$WORK/client.log" | tail -n 2
