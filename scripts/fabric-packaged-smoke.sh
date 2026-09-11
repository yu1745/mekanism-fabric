#!/usr/bin/env bash
# Start the remapped Fabric artifact in a clean, disposable dedicated server.
set -euo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
PROPERTIES="$ROOT/fabric/gradle.properties"
CACHE_DIR=${FABRIC_SMOKE_CACHE_DIR:-${XDG_CACHE_HOME:-$HOME/.cache}/mekanism-fabric-smoke}
LOG_ROOT=${FABRIC_SMOKE_LOG_DIR:-$ROOT/build/fabric-packaged-smoke}
TIMEOUT=${FABRIC_SMOKE_TIMEOUT:-180}
KEEP_SERVER=${FABRIC_SMOKE_KEEP_SERVER:-0}
SERVER_DIR=
SERVER_PID=
LOG_FILE=
RESULT=1

property() {
  local key=$1 value
  value=$(awk -F= -v key="$key" '$1 == key { sub(/^[^=]*=/, ""); print; exit }' "$PROPERTIES")
  if [[ -z "$value" ]]; then
    echo "fabric-packaged-smoke: missing $key in $PROPERTIES" >&2
    exit 2
  fi
  printf '%s\n' "$value"
}

MINECRAFT_VERSION=$(property minecraft_version)
LOADER_VERSION=$(property loader_version)
FABRIC_API_VERSION=$(property fabric_api_version)
NIGHT_CONFIG_VERSION=$(property night_config_version)
INSTALLER_VERSION=${FABRIC_INSTALLER_VERSION:-$(property fabric_installer_version)}

mkdir -p "$CACHE_DIR" "$LOG_ROOT"
RUN_ID=$(date -u +%Y%m%dT%H%M%SZ)-$$
DIAGNOSTIC_DIR="$LOG_ROOT/$RUN_ID"
mkdir -p "$DIAGNOSTIC_DIR"

finish() {
  local status=$?
  trap - EXIT INT TERM
  if [[ -n "$SERVER_PID" ]] && kill -0 "$SERVER_PID" 2>/dev/null; then
    kill -TERM -- "-$SERVER_PID" 2>/dev/null || kill -TERM "$SERVER_PID" 2>/dev/null || true
    for _ in {1..20}; do
      kill -0 "$SERVER_PID" 2>/dev/null || break
      sleep 0.25
    done
    kill -KILL -- "-$SERVER_PID" 2>/dev/null || kill -KILL "$SERVER_PID" 2>/dev/null || true
  fi
  [[ -n "$SERVER_PID" ]] && wait "$SERVER_PID" 2>/dev/null || true
  if [[ -n "$LOG_FILE" && -f "$LOG_FILE" ]]; then
    cp "$LOG_FILE" "$DIAGNOSTIC_DIR/latest.log"
  fi
  if [[ -n "$SERVER_DIR" && -d "$SERVER_DIR" ]]; then
    if [[ "$KEEP_SERVER" == 1 ]]; then
      printf 'fabric-packaged-smoke: retained server: %s\n' "$SERVER_DIR" >&2
    else
      rm -rf "$SERVER_DIR"
    fi
  fi
  printf 'fabric-packaged-smoke: diagnostics: %s\n' "$DIAGNOSTIC_DIR" >&2
  if [[ $RESULT -eq 0 && $status -eq 0 ]]; then
    exit 0
  fi
  (( status == 0 )) && status=1
  exit "$status"
}
trap finish EXIT INT TERM

fetch() {
  local url=$1 destination=$2 temporary="${2}.part.$$"
  if [[ -s "$destination" ]] && jar tf "$destination" >/dev/null 2>&1; then
    printf 'fabric-packaged-smoke: cache hit: %s\n' "$destination"
    return
  fi
  rm -f "$destination" "$temporary"
  printf 'fabric-packaged-smoke: downloading %s\n' "$url"
  if ! curl --fail --location --retry 3 --retry-all-errors --connect-timeout 15 \
      --max-time 300 --output "$temporary" "$url"; then
    rm -f "$temporary"
    echo "fabric-packaged-smoke: download failed: $url" >&2
    return 1
  fi
  if ! jar tf "$temporary" >/dev/null 2>&1; then
    rm -f "$temporary"
    echo "fabric-packaged-smoke: downloaded file is not a valid jar: $url" >&2
    return 1
  fi
  mv "$temporary" "$destination"
}

printf 'fabric-packaged-smoke: building remapped artifact\n'
"$ROOT/fabric-build.sh" remapJar
mapfile -t MOD_JARS < <(find "$ROOT/fabric/build/libs" -maxdepth 1 -type f -name '*.jar' \
  ! -name '*-sources.jar' ! -name '*-dev.jar' | sort)
if [[ ${#MOD_JARS[@]} -ne 1 ]]; then
  printf 'fabric-packaged-smoke: expected exactly one remapped jar, found %d\n' "${#MOD_JARS[@]}" >&2
  printf '  %s\n' "${MOD_JARS[@]:-<none>}" >&2
  exit 1
fi
if ! python3 - "${MOD_JARS[0]}" <<'PYEOF'
import json, sys, zipfile
with zipfile.ZipFile(sys.argv[1]) as artifact:
    metadata = json.loads(artifact.read("fabric.mod.json"))
    access_widener = artifact.read("mekanism.accesswidener").decode().splitlines()
expected = {
    "main": ["mekanism.fabric.MekanismFabricBootstrap"],
    "client": ["mekanism.fabric.MekanismFabricClientBootstrap"],
}
if (metadata.get("id") != "mekanism" or metadata.get("entrypoints") != expected
        or metadata.get("accessWidener") != "mekanism.accesswidener"):
    raise SystemExit(
        f"unexpected Fabric metadata: id={metadata.get('id')!r} "
        f"entrypoints={metadata.get('entrypoints')!r} accessWidener={metadata.get('accessWidener')!r}"
    )
expected_access_widener = [
    "accessWidener\tv2\tintermediary",
    "accessible\tmethod\tnet/minecraft/class_1959\tmethod_21740\t(Lnet/minecraft/class_2338;)F",
]
if access_widener != expected_access_widener:
    raise SystemExit(f"unexpected remapped access widener: {access_widener!r}")
PYEOF
then
  echo "fabric-packaged-smoke: artifact metadata or access widener does not match the expected Mekanism contract" >&2
  exit 1
fi
if jar tf "${MOD_JARS[0]}" | grep -Eq '^net/neoforged/'; then
  echo "fabric-packaged-smoke: artifact unexpectedly contains NeoForge classes" >&2
  exit 1
fi
if jar tf "${MOD_JARS[0]}" | grep -Eq '^mekanism/fixture/'; then
  echo "fabric-packaged-smoke: artifact unexpectedly contains handshake fixture classes" >&2
  exit 1
fi
if ! python3 - "${MOD_JARS[0]}" <<'PYEOF'
import sys, zipfile

required = {
    "mekanism/common/util/EmitUtils.class",
    "mekanism/common/lib/distribution/SplitInfo.class",
    "mekanism/common/lib/distribution/IntegerSplitInfo.class",
    "mekanism/common/lib/distribution/LongSplitInfo.class",
    "mekanism/common/lib/distribution/Target.class",
    "mekanism/common/lib/distribution/Target$HandlerType.class",
}
with zipfile.ZipFile(sys.argv[1]) as artifact:
    entries = artifact.namelist()
expected_api_codec = {"mekanism/api/codec/DependentMapCodec.class"}
actual_api_codec = {
    entry for entry in entries
    if entry.startswith("mekanism/api/codec/") and entry.endswith(".class")
}
if actual_api_codec != expected_api_codec or any(entries.count(entry) != 1 for entry in expected_api_codec):
    raise SystemExit(f"unexpected API codec class family: {sorted(actual_api_codec)!r}")
expected_common_codec = {
    "mekanism/common/lib/codec/DroppingUnboundedMapCodec.class",
    "mekanism/common/lib/codec/SequencedCollectionCodec.class",
    "mekanism/common/lib/codec/SequencedCollectionCodec$DecoderState.class",
}
actual_common_codec = {
    entry for entry in entries
    if entry.startswith("mekanism/common/lib/codec/") and entry.endswith(".class")
}
if actual_common_codec != expected_common_codec or any(entries.count(entry) != 1 for entry in expected_common_codec):
    raise SystemExit(f"unexpected common codec class family: {sorted(actual_common_codec)!r}")
expected_collection = {
    "mekanism/common/lib/collection/BiLongMultimap.class",
    "mekanism/common/lib/collection/FilterTransformIterator.class",
    "mekanism/common/lib/collection/IndexedCuboidMap.class",
    "mekanism/common/lib/collection/IndexedCuboidMap$1.class",
    "mekanism/common/lib/collection/IndexedCuboidMap$2.class",
    "mekanism/common/lib/collection/IndexedCuboidMap$CenteredBoundingBox.class",
}
actual_collection = [
    entry for entry in entries
    if entry.startswith("mekanism/common/lib/collection/") and entry.endswith(".class")
]
if set(actual_collection) != expected_collection or any(entries.count(entry) != 1 for entry in expected_collection):
    raise SystemExit(f"unexpected collection class family: {sorted(actual_collection)!r}")
for entry in required:
    if entries.count(entry) != 1:
        raise SystemExit(f"distribution class must be packaged exactly once: {entry}")
allowed_distribution = required - {"mekanism/common/util/EmitUtils.class"}
unexpected = sorted(
    entry for entry in entries
    if entry.startswith("mekanism/common/lib/distribution/")
    and entry.endswith(".class") and entry not in allowed_distribution
)
if unexpected:
    raise SystemExit(
        "packaged distribution test/helper classes are forbidden: " + ", ".join(unexpected)
    )
if entries.count("mekanism/common/util/ChunkUtils.class") != 1:
    raise SystemExit("mekanism/common/util/ChunkUtils.class must be packaged exactly once")
unexpected_chunk_utils = sorted(
    entry for entry in entries
    if entry.startswith("mekanism/common/util/ChunkUtils") and entry.endswith(".class")
    and entry != "mekanism/common/util/ChunkUtils.class"
)
if unexpected_chunk_utils:
    raise SystemExit(
        "packaged ChunkUtils test classes are forbidden: " + ", ".join(unexpected_chunk_utils)
    )
if entries.count("mekanism/common/util/RegistryUtils.class") != 1:
    raise SystemExit("mekanism/common/util/RegistryUtils.class must be packaged exactly once")
unexpected_registry_utils = sorted(
    entry for entry in entries
    if entry.startswith("mekanism/common/util/RegistryUtils") and entry.endswith(".class")
    and entry != "mekanism/common/util/RegistryUtils.class"
)
if unexpected_registry_utils:
    raise SystemExit(
        "unexpected packaged RegistryUtils class: " + ", ".join(unexpected_registry_utils)
    )
expected_lambda_meta_factory = {
    "mekanism/common/util/LambdaMetaFactoryUtil.class",
    "mekanism/common/util/LambdaMetaFactoryUtil$CallSiteInfo.class",
    "mekanism/common/util/LambdaMetaFactoryUtil$BooleanSetter.class",
    "mekanism/common/util/LambdaMetaFactoryUtil$ByteSetter.class",
    "mekanism/common/util/LambdaMetaFactoryUtil$CharSetter.class",
    "mekanism/common/util/LambdaMetaFactoryUtil$FloatSetter.class",
    "mekanism/common/util/LambdaMetaFactoryUtil$ShortSetter.class",
}
for entry in expected_lambda_meta_factory:
    if entries.count(entry) != 1:
        raise SystemExit(f"LambdaMetaFactoryUtil production class must be packaged exactly once: {entry}")
unexpected_lambda_meta_factory = sorted(
    entry for entry in entries
    if entry.startswith("mekanism/common/util/LambdaMetaFactoryUtil")
    and entry.endswith(".class") and entry not in expected_lambda_meta_factory
)
if unexpected_lambda_meta_factory:
    raise SystemExit(
        "unexpected packaged LambdaMetaFactoryUtil class: " + ", ".join(unexpected_lambda_meta_factory)
    )
expected_long_transfer = {
    "mekanism/api/container/InContainerGetterLong.class",
    "mekanism/api/container/LongToLongContainerInteraction.class",
    "mekanism/api/math/LongTransferUtils.class",
}
for entry in expected_long_transfer:
    if entries.count(entry) != 1:
        raise SystemExit(f"long-transfer production class must be packaged exactly once: {entry}")
unexpected_long_transfer = sorted(
    entry for entry in entries
    if entry.startswith("mekanism/api/math/LongTransferUtils")
    and entry.endswith(".class") and entry not in expected_long_transfer
)
if unexpected_long_transfer:
    raise SystemExit(
        "packaged LongTransferUtils test/helper classes are forbidden: " + ", ".join(unexpected_long_transfer)
    )
expected_dynamic_handlers = {
    "mekanism/common/capabilities/SimpleDynamicHandler.class",
    "mekanism/common/capabilities/DynamicHandler.class",
}
actual_dynamic_handlers = {
    entry for entry in entries
    if entry.startswith("mekanism/common/capabilities/")
    and "/" not in entry[len("mekanism/common/capabilities/"):]
    and entry.endswith(".class")
}
if actual_dynamic_handlers != expected_dynamic_handlers or any(entries.count(entry) != 1 for entry in expected_dynamic_handlers):
    raise SystemExit(f"unexpected root dynamic-handler class family: {sorted(actual_dynamic_handlers)!r}")
expected_common_energy = {
    "mekanism/common/capabilities/energy/BasicEnergyContainer.class",
    "mekanism/common/capabilities/energy/DynamicStrictEnergyHandler.class",
}
actual_common_energy = {
    entry for entry in entries
    if entry.startswith("mekanism/common/capabilities/energy/")
    and "/" not in entry[len("mekanism/common/capabilities/energy/"):]
    and entry.endswith(".class")
}
if actual_common_energy != expected_common_energy or any(entries.count(entry) != 1 for entry in expected_common_energy):
    raise SystemExit(f"unexpected common energy class family: {sorted(actual_common_energy)!r}")
expected_energy_handlers = {
    "mekanism/api/energy/IStrictEnergyHandler.class",
    "mekanism/api/energy/ISidedStrictEnergyHandler.class",
    "mekanism/api/energy/IMekanismStrictEnergyHandler.class",
}
for entry in expected_energy_handlers:
    if entries.count(entry) != 1:
        raise SystemExit(f"strict energy-handler interface must be packaged exactly once: {entry}")
expected_energy_api = expected_energy_handlers | {"mekanism/api/energy/IEnergyContainer.class"}
unexpected_energy_api = sorted(
    entry for entry in entries
    if entry.startswith("mekanism/api/energy/") and entry.endswith(".class")
    and entry not in expected_energy_api
)
if unexpected_energy_api:
    raise SystemExit(
        "unexpected packaged energy API class: " + ", ".join(unexpected_energy_api)
    )
PYEOF
then
  echo "fabric-packaged-smoke: codec/collection/distribution/ChunkUtils/RegistryUtils/LambdaMetaFactoryUtil/long-transfer/dynamic-strict-energy production/test packaging contract failed" >&2
  exit 1
fi
for required_entry in \
    mekanism/api/RelativeSide.class \
    mekanism/api/container/InContainerGetterLong.class \
    mekanism/api/container/LongToLongContainerInteraction.class \
    mekanism/api/math/LongTransferUtils.class \
    mekanism/api/energy/IEnergyContainer.class \
    mekanism/api/heat/HeatAPI.class \
    mekanism/api/heat/IHeatCapacitor.class \
    mekanism/api/heat/IHeatHandler.class \
    mekanism/api/heat/IMekanismHeatHandler.class \
    mekanism/api/heat/ISidedHeatHandler.class \
    'mekanism/api/text/IHasTranslationKey$IHasEnumNameTranslationKey.class' \
    mekanism/common/capabilities/SimpleDynamicHandler.class \
    mekanism/common/capabilities/DynamicHandler.class \
    mekanism/common/capabilities/energy/BasicEnergyContainer.class \
    mekanism/common/capabilities/energy/DynamicStrictEnergyHandler.class \
    mekanism/common/capabilities/heat/BasicHeatCapacitor.class \
    mekanism/common/capabilities/heat/VariableHeatCapacitor.class \
    mekanism/common/capabilities/holder/BasicHolder.class \
    mekanism/common/capabilities/holder/IHolder.class \
    mekanism/common/capabilities/holder/energy/EnergyContainerHolder.class \
    mekanism/common/capabilities/holder/energy/IEnergyContainerHolder.class \
    mekanism/common/capabilities/holder/heat/HeatCapacitorHolder.class \
    mekanism/common/capabilities/holder/heat/IHeatCapacitorHolder.class \
    mekanism/common/lib/WildcardMatcher.class \
    mekanism/common/util/ChunkUtils.class \
    mekanism/common/util/EmitUtils.class \
    mekanism/common/lib/distribution/SplitInfo.class \
    mekanism/common/lib/distribution/IntegerSplitInfo.class \
    mekanism/common/lib/distribution/LongSplitInfo.class \
    mekanism/common/lib/distribution/Target.class \
    'mekanism/common/lib/distribution/Target$HandlerType.class' \
    mekanism/fabric/MekanismFabricBootstrap.class \
    mekanism/fabric/MekanismFabricClientBootstrap.class \
    mekanism/fabric/network/FabricMekanismNetworking.class \
    mekanism/fabric/network/FabricMekanismClientNetworking.class \
    mekanism.accesswidener \
    "META-INF/jars/core-${NIGHT_CONFIG_VERSION}.jar" \
    "META-INF/jars/toml-${NIGHT_CONFIG_VERSION}.jar" \
    META-INF/licenses/night-config-LGPL-3.0.txt \
    META-INF/licenses/night-config-NOTICE.txt; do
  if ! jar tf "${MOD_JARS[0]}" | grep -Fxq "$required_entry"; then
    echo "fabric-packaged-smoke: artifact is missing $required_entry" >&2
    exit 1
  fi
done

LAUNCHER="$CACHE_DIR/fabric-server-${MINECRAFT_VERSION}-${LOADER_VERSION}-${INSTALLER_VERSION}.jar"
API_JAR="$CACHE_DIR/fabric-api-${FABRIC_API_VERSION}.jar"
fetch "https://meta.fabricmc.net/v2/versions/loader/${MINECRAFT_VERSION}/${LOADER_VERSION}/${INSTALLER_VERSION}/server/jar" "$LAUNCHER"
fetch "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/${FABRIC_API_VERSION}/fabric-api-${FABRIC_API_VERSION}.jar" "$API_JAR"

SERVER_DIR=$(mktemp -d "${TMPDIR:-/tmp}/mekanism-fabric-smoke.XXXXXX")
LOG_FILE="$SERVER_DIR/latest.log"
mkdir -p "$SERVER_DIR/mods"
printf 'eula=true\n' > "$SERVER_DIR/eula.txt"
printf 'online-mode=false\nserver-port=0\n' > "$SERVER_DIR/server.properties"
cp "$LAUNCHER" "$SERVER_DIR/fabric-server-launch.jar"
cp "$API_JAR" "$SERVER_DIR/mods/"
cp "${MOD_JARS[0]}" "$SERVER_DIR/mods/"
printf '%s\n' "artifact=${MOD_JARS[0]}" "minecraft=$MINECRAFT_VERSION" "loader=$LOADER_VERSION" \
  "fabric_api=$FABRIC_API_VERSION" "night_config=$NIGHT_CONFIG_VERSION" "installer=$INSTALLER_VERSION" > "$DIAGNOSTIC_DIR/run-info.txt"

printf 'fabric-packaged-smoke: starting clean server in %s\n' "$SERVER_DIR"
(
  cd "$SERVER_DIR"
  exec setsid java -Xms512M -Xmx2G -jar fabric-server-launch.jar nogui
) >"$LOG_FILE" 2>&1 &
SERVER_PID=$!

deadline=$((SECONDS + TIMEOUT))
mod_loaded=0
config_proof=0
network_proof=0
registry_proof=0
heat_proof=0
done_loaded=0
crash_pattern='(Exception in thread|A mod crashed on startup|Could not execute entrypoint|Failed to start the minecraft server|---- Minecraft Crash Report ----|NoClassDefFoundError|ClassNotFoundException|[[:space:]]ERROR[[:space:]])'
while (( SECONDS < deadline )); do
  if grep -Eq '(^|[[:space:]])- mekanism([[:space:]]|$)' "$LOG_FILE"; then mod_loaded=1; fi
  if grep -Fq 'MEKANISM_FABRIC_CONFIG_PROOF file=startup.toml values=12 loaded=true created=true corrected=12' "$LOG_FILE"; then config_proof=1; fi
  if grep -Fq 'MEKANISM_FABRIC_NETWORK_PROOF protocol=10.7.19 phase=configuration codecs=2 server_receiver=true client_required=true' "$LOG_FILE"; then network_proof=1; fi
  if grep -Fq 'MEKANISM_FABRIC_NETWORK_CLIENT_PROOF' "$LOG_FILE"; then
    echo 'fabric-packaged-smoke: client networking entrypoint loaded on a dedicated server' >&2
    exit 1
  fi
  if grep -Fq 'MEKANISM_FABRIC_REGISTRY_PROOF key=mekanism:seismic_vibration canonical_identity=true entries=4' "$LOG_FILE"; then registry_proof=1; fi
  if grep -Fq 'MEKANISM_FABRIC_HEAT_PROOF positional=true ambient_finite=true' "$LOG_FILE"; then heat_proof=1; fi
  if grep -Eq 'Done \(' "$LOG_FILE"; then done_loaded=1; fi
  if grep -Eiq "$crash_pattern" "$LOG_FILE"; then
    echo 'fabric-packaged-smoke: crash signature detected' >&2
    grep -Ei "$crash_pattern" "$LOG_FILE" | tail -n 10 >&2 || true
    exit 1
  fi
  if (( mod_loaded && config_proof && network_proof && registry_proof && heat_proof && done_loaded )); then
    STARTUP_CONFIG="$SERVER_DIR/config/Mekanism/startup.toml"
    if [[ ! -s "$STARTUP_CONFIG" ]]; then
      echo 'fabric-packaged-smoke: startup config proof logged but startup.toml is missing or empty' >&2
      exit 1
    fi
    if ! python3 - "$STARTUP_CONFIG" <<'PYEOF'
import pathlib, sys, tomllib
actual = tomllib.loads(pathlib.Path(sys.argv[1]).read_text())
expected = {
    "gear": {
        "armored_free_runner": {"armor": 3, "toughness": 2.0, "knockbackResistance": 0.0},
        "armored_jetpack": {"armor": 8, "toughness": 2.0, "knockbackResistance": 0.0},
        "mekasuit": {
            "helmetArmor": 3, "bodyArmorArmor": 8, "pantsArmor": 6, "bootsArmor": 3,
            # Vanilla exposes this as float; the config spec stores the widened double, like NeoForge.
            "toughness": 3.0, "knockbackResistance": 0.10000000149011612,
        },
    },
}
if actual != expected:
    raise SystemExit(f"unexpected startup config: {actual!r}")
PYEOF
    then
      echo 'fabric-packaged-smoke: startup.toml does not match the official clean defaults' >&2
      exit 1
    fi
    RESULT=0
    printf 'fabric-packaged-smoke: PASS (mekanism loaded, config/network/registry/heat proofs passed, and server reached Done)\n'
    grep -E '(^|[[:space:]])- mekanism([[:space:]]|$)|MEKANISM_FABRIC_(CONFIG|NETWORK|REGISTRY|HEAT)_PROOF|Done \(' "$LOG_FILE" | tail -n 10
    exit 0
  fi
  if ! kill -0 "$SERVER_PID" 2>/dev/null; then
    wait "$SERVER_PID" || true
    SERVER_PID=
    echo 'fabric-packaged-smoke: server exited before readiness' >&2
    tail -n 80 "$LOG_FILE" >&2
    exit 1
  fi
  sleep 1
done

echo "fabric-packaged-smoke: timed out after ${TIMEOUT}s (mod_loaded=$mod_loaded config_proof=$config_proof network_proof=$network_proof registry_proof=$registry_proof heat_proof=$heat_proof done=$done_loaded)" >&2
tail -n 80 "$LOG_FILE" >&2
exit 1
