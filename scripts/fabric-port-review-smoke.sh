#!/usr/bin/env bash
# Eight focused regression groups in a disposable packaged Fabric server.
set -euo pipefail
ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
CACHE_DIR=${FABRIC_SMOKE_CACHE_DIR:-${XDG_CACHE_HOME:-$HOME/.cache}/mekanism-fabric-smoke}
property() { awk -F= -v key="$1" '$1 == key { sub(/^[^=]*=/, ""); print; exit }' "$ROOT/fabric/gradle.properties"; }
MC=$(property minecraft_version)
LOADER=$(property loader_version)
API=$(property fabric_api_version)
ENERGY=$(property team_reborn_energy_version)
INSTALLER=${FABRIC_INSTALLER_VERSION:-1.1.2}
LAUNCHER="$CACHE_DIR/fabric-server-$MC-$LOADER-$INSTALLER.jar"
API_JAR="$CACHE_DIR/fabric-api-$API.jar"
ENERGY_JAR="$CACHE_DIR/energy-$ENERGY.jar"
FIXTURE="$ROOT/fabric/build/port-review-fixture/remapped/mekanism-port-review-fixture.jar"
MOD_JAR=
for candidate in "$ROOT/fabric/build/libs/"*.jar; do
    [[ -f "$candidate" ]] || continue
    [[ "$candidate" == *-sources.jar || "$candidate" == *-dev.jar ]] && continue
    if [[ -z "$MOD_JAR" || "$candidate" -nt "$MOD_JAR" ]]; then MOD_JAR=$candidate; fi
done
for required in "$LAUNCHER" "$API_JAR" "$ENERGY_JAR" "$FIXTURE" "$MOD_JAR"; do
    [[ -s "$required" ]] || { echo "Missing prerequisite: $required; build remapJar/remapPortReviewFixtureJar and cache the normal machine smoke dependencies." >&2; exit 2; }
done
RUN_DIR=$(mktemp -d "${TMPDIR:-/tmp}/mekanism-port-review.XXXXXX")
cleanup() {
    local status=$?
    if [[ ${FABRIC_SMOKE_KEEP_SERVER:-0} == 1 || $status != 0 ]]; then
        echo "port-review-smoke: retained $RUN_DIR" >&2
    else
        rm -rf "$RUN_DIR"
    fi
}
trap cleanup EXIT
mkdir -p "$RUN_DIR/mods"
cp "$API_JAR" "$ENERGY_JAR" "$FIXTURE" "$MOD_JAR" "$RUN_DIR/mods/"
cp "$LAUNCHER" "$RUN_DIR/fabric-server-launch.jar"
# Optional pinned development runtime cache; copy only binaries, never worlds, mods or configuration.
if [[ -n ${FABRIC_SMOKE_RUNTIME_CACHE_DIR:-} ]]; then
    for part in libraries versions .fabric/server; do
        [[ -d "$FABRIC_SMOKE_RUNTIME_CACHE_DIR/$part" ]] || continue
        mkdir -p "$(dirname "$RUN_DIR/$part")"
        cp -a --reflink=auto "$FABRIC_SMOKE_RUNTIME_CACHE_DIR/$part" "$RUN_DIR/$part"
    done
fi
printf 'eula=true\n' > "$RUN_DIR/eula.txt"
printf 'online-mode=false\nserver-port=0\nspawn-protection=0\nview-distance=2\nsimulation-distance=2\nsync-chunk-writes=false\n' > "$RUN_DIR/server.properties"
(
    cd "$RUN_DIR"
    timeout "${FABRIC_PORT_REVIEW_TIMEOUT:-180}" java -Xmx2G -Dmekanism.portReview.result="$RUN_DIR/result.txt" \
        -jar fabric-server-launch.jar nogui > server.log 2>&1
) || { tail -n 100 "$RUN_DIR/server.log" >&2; exit 1; }
[[ -f "$RUN_DIR/result.txt" ]] && rg -q '^PASS groups=8$' "$RUN_DIR/result.txt" || {
    tail -n 100 "$RUN_DIR/server.log" >&2
    exit 1
}
if rg -q 'PORT_REVIEW_FAIL|Encountered an unexpected exception|Mixin apply failed' "$RUN_DIR/server.log"; then
    tail -n 100 "$RUN_DIR/server.log" >&2
    exit 1
fi
rg 'PORT_REVIEW_PASS' "$RUN_DIR/server.log"
cat "$RUN_DIR/result.txt"
