#!/usr/bin/env bash
# Validate the remapped Fabric artifact, then optionally start it in a clean disposable server.
set -euo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
PROPERTIES="$ROOT/fabric/gradle.properties"
CACHE_DIR=${FABRIC_SMOKE_CACHE_DIR:-${XDG_CACHE_HOME:-$HOME/.cache}/mekanism-fabric-smoke}
LOG_ROOT=${FABRIC_SMOKE_LOG_DIR:-$ROOT/build/fabric-packaged-smoke}
TIMEOUT=${FABRIC_SMOKE_TIMEOUT:-180}
KEEP_SERVER=${FABRIC_SMOKE_KEEP_SERVER:-0}
STATIC_ONLY=${FABRIC_SMOKE_STATIC_ONLY:-0}
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
TEAM_REBORN_ENERGY_VERSION=$(property team_reborn_energy_version)
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
"$ROOT/fabric-build.sh" remapJar remapToolsJar remapAdditionsJar remapGeneratorsJar
mapfile -t MOD_JARS < <(find "$ROOT/fabric/build/libs" -maxdepth 1 -type f -name '*.jar' \
  ! -name '*-sources.jar' ! -name '*-dev.jar' | sort)
if [[ ${#MOD_JARS[@]} -ne 1 ]]; then
  printf 'fabric-packaged-smoke: expected exactly one remapped jar, found %d\n' "${#MOD_JARS[@]}" >&2
  printf '  %s\n' "${MOD_JARS[@]:-<none>}" >&2
  exit 1
fi
mapfile -t ADDON_JARS < <(find "$ROOT/fabric/build/tools-libs" "$ROOT/fabric/build/additions-libs" "$ROOT/fabric/build/generators-libs" \
  -maxdepth 1 -type f -name '*-fabric.jar' | sort)
[[ ${#ADDON_JARS[@]} -eq 3 ]] || { echo 'fabric-packaged-smoke: expected exactly three optional addon JARs' >&2; exit 1; }
# The addon metadata contract is a separate script so the negative probes exercise the same checks.
python3 "$ROOT/scripts/verify-packaged-addons.py" "${MOD_JARS[0]}" "${ADDON_JARS[@]}" || exit 1
# Optional client integrations must stay compile-only: no third-party classes may leak into the release JARs.
python3 - "${MOD_JARS[0]}" "${ADDON_JARS[@]}" <<'PYEOF' || exit 1
import sys, zipfile
for path in sys.argv[1:]:
    with zipfile.ZipFile(path) as archive:
        leaked = [name for name in archive.namelist() if name.startswith(('dev/emi/', 'mezz/jei/', 'snownee/jade/'))]
    if leaked:
        print('fabric-packaged-smoke: third-party integration classes leaked into ' + path + ': ' + ', '.join(leaked[:3]))
        sys.exit(1)
PYEOF
# Compare against the same build's slice-gated dev artifact, including generated classes.
# Early Task043 package subsets are no longer valid once the real machine/client catalogs are admitted.
DEV_JAR="$ROOT/fabric/build/devlibs/$(basename "${MOD_JARS[0]}" .jar)-dev.jar"
if ! python3 - "${MOD_JARS[0]}" "$TEAM_REBORN_ENERGY_VERSION" "$DEV_JAR" "$NIGHT_CONFIG_VERSION" "$ROOT/scripts/fabric-packaged-oracles.json" <<'PYEOF'
from collections import Counter
import hashlib, json, sys, zipfile

expected_entrypoints = {
    "main": ["mekanism.fabric.MekanismFabricBootstrap"],
    "client": ["mekanism.fabric.MekanismFabricClientBootstrap"],
}
# Pinned MC 1.21.1 intermediary entries, checked against the named source and Loom mappings.
# Update deliberately alongside changes to mekanism.accesswidener; never infer this from the tested JAR.
expected_access_widener = [
    'accessWidener\tv2\tintermediary',
    'accessible\tfield\tnet/minecraft/class_310\tfield_1760\tLnet/minecraft/class_325;',
    'accessible\tmethod\tnet/minecraft/class_1959\tmethod_21740\t(Lnet/minecraft/class_2338;)F',
    'accessible\tfield\tnet/minecraft/class_1869\tfield_47320\tLnet/minecraft/class_8957;',
    'accessible\tclass\tnet/minecraft/class_2586$class_9473',
    'accessible\tfield\tnet/minecraft/class_4970$class_2251\tfield_10663\tLjava/util/function/ToIntFunction;',
    'accessible\tmethod\tnet/minecraft/class_1657\tmethod_7346\t()V',
    'accessible\tfield\tnet/minecraft/class_329\tfield_2033\tI',
    'accessible\tfield\tnet/minecraft/class_329\tfield_2041\tI',
    'accessible\tmethod\tnet/minecraft/class_329\tmethod_1744\t(Lnet/minecraft/class_1309;)I',
    'accessible\tmethod\tnet/minecraft/class_329\tmethod_1733\t(I)I',
    'accessible\tfield\tnet/minecraft/class_9080\tfield_47849\tLjava/util/List;',
    'accessible\tmethod\tnet/minecraft/class_1927\tmethod_24023\t(Ljava/util/List;Lnet/minecraft/class_1799;Lnet/minecraft/class_2338;)V',
    'accessible\tmethod\tnet/minecraft/class_1293\tmethod_5588\t()I',
    'accessible\tmethod\tnet/minecraft/class_1293\tmethod_24276\t(Lnet/minecraft/class_1293;)V',
    'accessible\tfield\tnet/minecraft/class_1293\tfield_21830\tLnet/minecraft/class_1293;',
    'accessible\tfield\tnet/minecraft/class_1309\tfield_6285\tZ',
    'accessible\tmethod\tnet/minecraft/class_1309\tmethod_52543\t()V',
    'accessible\tmethod\tnet/minecraft/class_238\tmethod_1007\t(Lnet/minecraft/class_238;Lnet/minecraft/class_243;[DLnet/minecraft/class_2350;DDD)Lnet/minecraft/class_2350;',
    'accessible\tmethod\tnet/minecraft/class_1297\tmethod_5778\t()Z',
    'accessible\tmethod\tnet/minecraft/class_2960\tmethod_29185\t(C)Z',
    'accessible\tfield\tnet/minecraft/class_8673\tfield_45589\tLnet/minecraft/class_2535;',
    'accessible\tfield\tnet/minecraft/class_1743\tfield_7898\tLjava/util/Map;',
    'accessible\tfield\tnet/minecraft/class_1821\tfield_8912\tLjava/util/Map;',
    'accessible\tmethod\tnet/minecraft/class_2358\tmethod_10190\t(Lnet/minecraft/class_2680;)I',
    'accessible\tmethod\tnet/minecraft/class_2530\tmethod_10737\t(Lnet/minecraft/class_1937;Lnet/minecraft/class_2338;Lnet/minecraft/class_1309;)V',
    'accessible\tfield\tnet/minecraft/class_1144\tfield_5590\tLnet/minecraft/class_1140;',
    'accessible\tfield\tnet/minecraft/class_3620\tfield_16006\t[Lnet/minecraft/class_3620;',
    'accessible\tfield\tnet/minecraft/class_3244\tfield_14138\tI',
    'accessible\tmethod\tnet/minecraft/class_1657\tmethod_5712\t(Lnet/minecraft/class_2338;Lnet/minecraft/class_2680;)V',
    'accessible\tmethod\tnet/minecraft/class_4970$class_4971\tmethod_26233\t()Lnet/minecraft/class_2680;',
    'accessible\tfield\tnet/minecraft/class_1140\tfield_5558\tLjava/util/List;',
    'accessible\tfield\tnet/minecraft/class_1140\tfield_18950\tLjava/util/Map;',
    'accessible\tmethod\tnet/minecraft/class_6796\tmethod_39648\t(Lnet/minecraft/class_5444;Lnet/minecraft/class_5819;Lnet/minecraft/class_2338;)Z',
    'accessible\tfield\tnet/minecraft/class_2794\tfield_39412\tLjava/util/function/Supplier;',
    'accessible\tfield\tnet/minecraft/class_1755\tfield_7905\tLnet/minecraft/class_3611;',
    'accessible\tfield\tnet/minecraft/class_304\tfield_1655\tLnet/minecraft/class_3675$class_306;',
    'accessible\tfield\tnet/minecraft/class_1959\tfield_26393\tLnet/minecraft/class_1959$class_5482;',
    'accessible\tclass\tnet/minecraft/class_1959$class_5482',
    'accessible\tfield\tnet/minecraft/class_7708\tfield_40212\tLit/unimi/dsi/fastutil/Hash$Strategy;',
    'accessible\tmethod\tnet/minecraft/class_1703\tmethod_34258\t()V',
    'extendable\tmethod\tnet/minecraft/class_465\tmethod_2386\t(DD)Lnet/minecraft/class_1735;',
    'extendable\tmethod\tnet/minecraft/class_465\tmethod_2387\t(Lnet/minecraft/class_1735;DD)Z',
    'extendable\tmethod\tnet/minecraft/class_465\tmethod_2382\t(Lnet/minecraft/class_332;Lnet/minecraft/class_1799;IILjava/lang/String;)V',
    'extendable\tmethod\tnet/minecraft/class_339\tmethod_25394\t(Lnet/minecraft/class_332;IIF)V',
    'accessible\tfield\tnet/minecraft/class_339\tfield_41095\tLnet/minecraft/class_9110;',
    'accessible\tfield\tnet/minecraft/class_465\tfield_2802\tLnet/minecraft/class_1735;',
    'accessible\tfield\tnet/minecraft/class_465\tfield_2784\tI',
    'accessible\tfield\tnet/minecraft/class_465\tfield_2796\tI',
    'accessible\tfield\tnet/minecraft/class_465\tfield_2795\tJ',
    'accessible\tfield\tnet/minecraft/class_465\tfield_2785\tLnet/minecraft/class_1799;',
    'accessible\tfield\tnet/minecraft/class_465\tfield_2777\tLnet/minecraft/class_1735;',
    'accessible\tfield\tnet/minecraft/class_465\tfield_2789\tZ',
    'accessible\tfield\tnet/minecraft/class_465\tfield_2782\tLnet/minecraft/class_1799;',
    'accessible\tfield\tnet/minecraft/class_465\tfield_2790\tI',
    'extendable\tmethod\tnet/minecraft/class_465\tmethod_2379\t()V',
    'accessible\tfield\tnet/minecraft/class_465\tfield_2783\tZ',
    'accessible\tfield\tnet/minecraft/class_465\tfield_2799\tLnet/minecraft/class_1735;',
    'accessible\tfield\tnet/minecraft/class_465\tfield_2788\tJ',
    'accessible\tfield\tnet/minecraft/class_465\tfield_2786\tI',
    'accessible\tfield\tnet/minecraft/class_465\tfield_2798\tZ',
    'extendable\tmethod\tnet/minecraft/class_465\tmethod_30107\t(I)V',
    'accessible\tfield\tnet/minecraft/class_465\tfield_2791\tLnet/minecraft/class_1799;',
    'accessible\tfield\tnet/minecraft/class_465\tfield_2778\tI',
    'accessible\tfield\tnet/minecraft/class_437\tfield_33816\tLjava/util/List;',
    'accessible\tmethod\tnet/minecraft/class_4069\tmethod_48215\t(Lnet/minecraft/class_8023$class_8026;)Lnet/minecraft/class_8016;',
    'accessible\tmethod\tnet/minecraft/class_4069\tmethod_48214\t(Lnet/minecraft/class_8023$class_8024;)Lnet/minecraft/class_8016;',
    'extendable\tmethod\tnet/minecraft/class_437\tmethod_47413\t(Lnet/minecraft/class_332;IIF)V',
    'accessible\tfield\tnet/minecraft/class_437\tfield_41685\tLnet/minecraft/class_437$class_7998;',
    'accessible\tclass\tnet/minecraft/class_437$class_7998',
    'accessible\tfield\tnet/minecraft/class_342\tfield_2096\tZ',
    'accessible\tfield\tnet/minecraft/class_342\tfield_2101\tI',
    'accessible\tfield\tnet/minecraft/class_342\tfield_2108\tI',
    'accessible\tmethod\tnet/minecraft/class_7919\t<init>\t(Lnet/minecraft/class_2561;Lnet/minecraft/class_2561;)V',
    'accessible\tfield\tnet/minecraft/class_7919\tfield_41103\tLjava/util/List;',
    'accessible\tmethod\tnet/minecraft/class_3898\tmethod_38783\t(Lnet/minecraft/class_1923;)Z',
    'accessible\tmethod\tnet/minecraft/class_5168\tmethod_27162\t(Lnet/minecraft/class_3218;Lnet/minecraft/class_2338;)Z',
    'accessible\tmethod\tnet/minecraft/class_761\tmethod_3291\t(Lnet/minecraft/class_4587;Lnet/minecraft/class_4588;Lnet/minecraft/class_265;DDDFFFF)V',
    'accessible\tfield\tnet/minecraft/class_761\tfield_4073\tI',
    'accessible\tfield\tnet/minecraft/class_310\tfield_1769\tLnet/minecraft/class_761;',
    'accessible\tfield\tnet/minecraft/class_630\tfield_3663\tLjava/util/List;',
    'accessible\tfield\tnet/minecraft/class_630\tfield_3661\tLjava/util/Map;',
    'accessible\tfield\tnet/minecraft/class_630$class_628\tfield_3649\t[Lnet/minecraft/class_630$class_593;',
    'accessible\tfield\tnet/minecraft/class_310\tfield_1760\tLnet/minecraft/class_325;',
    'accessible\tfield\tnet/minecraft/class_329\tfield_2027\tLnet/minecraft/class_359;',
    'accessible\tfield\tnet/minecraft/class_359\tfield_47672\tLjava/util/List;',
    'accessible\tclass\tnet/minecraft/class_359$class_360',
    'accessible\tfield\tnet/minecraft/class_758\tfield_4034\tF',
    'accessible\tfield\tnet/minecraft/class_758\tfield_4033\tF',
    'accessible\tfield\tnet/minecraft/class_758\tfield_4032\tF',
    'accessible\tfield\tnet/minecraft/class_591\tfield_3481\tLnet/minecraft/class_630;',
    'accessible\tfield\tnet/minecraft/class_591\tfield_3485\tLnet/minecraft/class_630;',
    'accessible\tfield\tnet/minecraft/class_551\tfield_27391\tLnet/minecraft/class_630;',
    'accessible\tfield\tnet/minecraft/class_551\tfield_27392\tLnet/minecraft/class_630;',
    'accessible\tfield\tnet/minecraft/class_551\tfield_3313\tLnet/minecraft/class_630;',
    'accessible\tfield\tnet/minecraft/class_793\tfield_4254\tLcom/google/gson/Gson;',
    'accessible\tmethod\tnet/minecraft/class_793\tmethod_3447\t(Lnet/minecraft/class_785;Lnet/minecraft/class_783;Lnet/minecraft/class_1058;Lnet/minecraft/class_2350;Lnet/minecraft/class_3665;)Lnet/minecraft/class_777;',
    'accessible\tfield\tnet/minecraft/class_922\tfield_4738\tLjava/util/List;',
    'accessible\tfield\tnet/minecraft/class_4592\tfield_20918\tF',
    'accessible\tfield\tnet/minecraft/class_4592\tfield_20919\tF',
    'accessible\tfield\tnet/minecraft/class_4592\tfield_20920\tF',
    'accessible\tfield\tnet/minecraft/class_970\tfield_4830\tLnet/minecraft/class_572;',
    'accessible\tfield\tnet/minecraft/class_970\tfield_4831\tLnet/minecraft/class_572;',
    'accessible\tfield\tnet/minecraft/class_4592\tfield_20915\tZ',
    'accessible\tfield\tnet/minecraft/class_4592\tfield_20918\tF',
    'accessible\tfield\tnet/minecraft/class_4592\tfield_20916\tF',
    'accessible\tfield\tnet/minecraft/class_4592\tfield_20917\tF',
    'accessible\tfield\tnet/minecraft/class_979\tfield_4852\tLnet/minecraft/class_563;',
    'accessible\tmethod\tnet/minecraft/class_801\tmethod_3480\t(ILjava/lang/String;Lnet/minecraft/class_7764;)Ljava/util/List;',
    'accessible\tfield\tnet/minecraft/class_1755\tfield_7905\tLnet/minecraft/class_3611;',
    'accessible\tmethod\tnet/minecraft/class_1297\tmethod_51502\t(Lnet/minecraft/class_1937;)V',
    'accessible\tfield\tnet/minecraft/class_1542\tfield_7204\tI',
    'accessible\tmethod\tnet/minecraft/class_687\t<init>\t(Lnet/minecraft/class_638;DDDDDD)V',
    'accessible\tmethod\tnet/minecraft/class_655\t<init>\t(Lnet/minecraft/class_638;DDDDDD)V',
    'accessible\tfield\tnet/minecraft/class_5272\tfield_24448\tLjava/util/Map;',
    'accessible\tmethod\tnet/minecraft/class_4603\tmethod_23069\t(Lnet/minecraft/class_310;Lnet/minecraft/class_4587;)V',
    # Required Additions subclass/builder/beacon access, mapped from the pinned named 1.21.1 declarations.
    'extendable\tmethod\tnet/minecraft/class_1548\tmethod_7006\t()V',
    'accessible\tfield\tnet/minecraft/class_1548\tfield_7225\tI',
    'accessible\tmethod\tnet/minecraft/class_1548\tmethod_7001\t()V',
    'extendable\tmethod\tnet/minecraft/class_1541\tmethod_6971\t()V',
    'accessible\tfield\tnet/minecraft/class_1541\tfield_7198\tLnet/minecraft/class_1309;',
    'accessible\tfield\tnet/minecraft/class_1541\tfield_52318\tZ',
    'accessible\tfield\tnet/minecraft/class_1541\tfield_52317\tLnet/minecraft/class_5362;',
    'accessible\tfield\tnet/minecraft/class_1299\tfield_25355\tLcom/google/common/collect/ImmutableSet;',
    'accessible\tfield\tnet/minecraft/class_9066\tfield_47752\tLjava/util/Map;',
    'accessible\tmethod\tnet/minecraft/class_4719\tmethod_24027\t(Lnet/minecraft/class_4719;)Lnet/minecraft/class_4719;',
    'accessible\tmethod\tnet/minecraft/class_2580$class_2581\tmethod_10942\t()V',
]
with zipfile.ZipFile(sys.argv[1]) as artifact, zipfile.ZipFile(sys.argv[3]) as development:
    entries = artifact.namelist()
    duplicates = [name for name, count in Counter(entries).items() if count != 1]
    if duplicates:
        raise SystemExit(f"duplicate artifact entries: {duplicates!r}")
    metadata = json.loads(artifact.read("fabric.mod.json"))
    if (metadata.get("id") != "mekanism" or metadata.get("entrypoints") != expected_entrypoints
            or metadata.get("accessWidener") != "mekanism.accesswidener"
            or metadata.get("depends", {}).get("team_reborn_energy") != f">={sys.argv[2]}"):
        raise SystemExit("Fabric entrypoint, access-widener or external energy dependency contract changed")
    actual_aw = artifact.read("mekanism.accesswidener").decode().splitlines()
    if actual_aw != expected_access_widener:
        raise SystemExit(f"unexpected remapped access widener: {actual_aw!r}")
    actual = {name for name in entries if not name.endswith("/")}
    dev = {name for name in development.namelist() if not name.endswith("/")}
    nested = {f"META-INF/jars/{name}-{sys.argv[4]}.jar" for name in ("core", "toml")}
    nested.add("META-INF/jars/maven-artifact-3.9.9.jar")
    if actual != dev | nested:
        raise SystemExit(f"artifact contents differ from build: missing={sorted((dev | nested) - actual)!r} "
                         f"extra={sorted(actual - (dev | nested))!r}")
    if {entry["file"] for entry in metadata.get("jars", [])} != nested:
        raise SystemExit("Bundled Night Config/Maven dependencies differ from metadata")
    # Loom changes these three resources; every other resource, including mixin configs,
    # refmap, service providers, official data maps, recipes and assets, must survive unchanged.
    transformed = {"META-INF/MANIFEST.MF", "fabric.mod.json", "mekanism.accesswidener"}
    for name in sorted(dev):
        if "unfreeze" in name.lower():
            raise SystemExit(f"registry-bypass entry leaked into artifact: {name}")
        if not name.endswith(".class"):
            if name not in transformed and artifact.read(name) != development.read(name):
                raise SystemExit(f"resource changed during remapping: {name}")
            continue
        basename = name.rsplit("/", 1)[-1]
        if (name.startswith(("net/neoforged/", "team/reborn/", "net/fabricmc/loader/",
                             "xyz/bluspring/kilt/", "org/sinytra/connector/", "mekanism/fixture/"))
                or "/fixture/" in name or basename.endswith("Test.class") or "Test$" in basename
                or "Fixture" in basename):
            raise SystemExit(f"test, fixture or external implementation leaked into artifact: {name}")
        bytecode = artifact.read(name)
        if any(namespace in bytecode for namespace in (b"net/neoforged/", b"xyz/bluspring", b"org/sinytra")):
            raise SystemExit(f"NeoForge or compatibility-layer reference leaked into bytecode: {name}")
    # Retain the old independent resource/service oracles: equality with the dev JAR alone
    # cannot detect the same wrong source resource being packaged into both artifacts.
    with open(sys.argv[5]) as oracle_file:
        oracles = json.load(oracle_file)
    missing_required = sorted(set(oracles["required_classes"]) - actual)
    if missing_required:
        raise SystemExit(f"previously admitted production classes missing: {missing_required!r}")
    for name, expected_hash in oracles["resources"].items():
        if hashlib.sha256(artifact.read(name)).hexdigest() != expected_hash:
            raise SystemExit(f"official resource oracle changed: {name}")
    for name, expected_content in oracles["services"].items():
        if artifact.read(name) != expected_content.encode():
            raise SystemExit(f"service provider oracle changed: {name}")
    client_mixins = json.loads(artifact.read("mekanism.client.mixins.json"))
    if (client_mixins.get("package") != "mekanism.fabric.mixin" or not isinstance(client_mixins.get("client"), list) or not client_mixins.get("client")
            or "mixins" in client_mixins or "server" in client_mixins
            or client_mixins.get("injectors", {}).get("defaultRequire") != 1):
        raise SystemExit("client mixin configuration lost its client-only contract")
    if not set(oracles["required_client_mixins"]).issubset(client_mixins["client"]):
        raise SystemExit("previously admitted client mixin registrations missing")
    if metadata.get("mixins") != ["mekanism.mixins.json", {"config": "mekanism.client.mixins.json", "environment": "client"}]:
        raise SystemExit("common/client mixin environment contract changed")

    def reject_references(name, forbidden):
        bytecode = artifact.read(name)
        if any(reference in bytecode for reference in forbidden):
            raise SystemExit(f"portable owner dependency contract changed: {name}")

    foundation = oracles["registry_foundation"]
    for name in foundation:
        reject_references(name, (b"net/fabricmc/loader/", b"mekanism/api/chemical/", b"MEKANISM_FABRIC_"))
        lowered = artifact.read(name).lower()
        if any(word in lowered for word in (b"mekanism/api/mekanismapi", b"mekanism:empty", b"radiation", b"datamap", b"data_map", b"kilt", b"connector", b"unfreeze")):
            raise SystemExit(f"registry foundation lost its domain-independent contract: {name}")
    for name in sorted(dev):
        if not name.endswith(".class"):
            continue
        serializer = name.startswith("mekanism/api/SerializerHelper")
        portable_text = (name.startswith("mekanism/api/text/") or name in {
            "mekanism/api/IIncrementalEnum.class", "mekanism/api/RelativeSide.class", "mekanism/api/inventory/IHashedItem.class"})
        if serializer or portable_text:
            reject_references(name, (b"net/fabricmc/loader/", b"MEKANISM_FABRIC_"))
        if (name.startswith(("mekanism/api/recipes/", "mekanism/common/recipe/", "mekanism/api/chemical/"))
                or name in oracles["chemical_singletons"] or name in {
                    "mekanism/common/tile/base/TileEntityUpdateable.class", "mekanism/common/network/to_client/PacketUpdateTile.class",
                    "mekanism/api/fluid/IExtendedFluidTank.class", "mekanism/api/providers/IFluidProvider.class"}):
            reject_references(name, (b"net/fabricmc/",))
        if name.startswith(("mekanism/api/datamaps/", "mekanism/fabric/datamaps/")):
            lowered = artifact.read(name).lower()
            if any(word in lowered for word in (b"mekanism/api/mekanismapi", b"advanceddatamap", b"datamaptyperegister", b"kilt", b"connector")):
                raise SystemExit(f"data-map owner dependency contract changed: {name}")
    if "mekanism/fabric/datamaps/FabricDataMapType.class" in actual:
        raise SystemExit("relocated FabricDataMapType must not remain in the artifact")
    reject_references("mekanism/common/config/ClientWindowConfigAccess.class", (b"mekanism/fabric/", b"net/fabricmc/", b"net/minecraft/client/"))
    common_bootstrap = artifact.read("mekanism/fabric/MekanismFabricBootstrap.class")
    foundation_references = tuple(name.split("$", 1)[0].removesuffix(".class").encode() for name in foundation)
    client_bootstrap = artifact.read("mekanism/fabric/MekanismFabricClientBootstrap.class")
    if any(reference in common_bootstrap + client_bootstrap for reference in foundation_references):
        raise SystemExit("bootstrap must not directly wire the registry foundation")
    if any(reference in common_bootstrap for reference in (b"FabricClientConfigLifecycle", b"FabricWindowConfig", b"ClientWindowConfigAccess", b"SelectedWindowData", b"mekanism/api/SerializerHelper")):
        raise SystemExit("common bootstrap links an isolated client/serialization owner")
    if b"FabricClientConfigLifecycle" not in client_bootstrap or b"FabricMekanismClientNetworking" not in client_bootstrap:
        raise SystemExit("client bootstrap lost its config/network lifecycle")
    dev_metadata = json.loads(development.read("fabric.mod.json"))
    if {key: value for key, value in metadata.items() if key != "jars"} != dev_metadata:
        raise SystemExit("Fabric metadata changed during remapping beyond nested dependency declarations")
    print(f"fabric-packaged-smoke: static contents verified classes={sum(n.endswith('.class') for n in actual)}")
PYEOF
then
  echo "fabric-packaged-smoke: artifact content, metadata or access-widener contract failed" >&2
  exit 1
fi

for required_entry in \
    mekanism/api/IIncrementalEnum.class \
    mekanism/api/RelativeSide.class \
    mekanism/api/inventory/IHashedItem.class \
    mekanism/api/text/APILang.class \
    mekanism/api/text/EnumColor.class \
    mekanism/api/text/IHasTranslationKey.class \
    'mekanism/api/text/IHasTranslationKey$IHasEnumNameTranslationKey.class' \
    mekanism/api/text/ILangEntry.class \
    mekanism/api/text/TextComponentUtil.class \
    'mekanism/api/text/TextComponentUtil$1.class' \
    mekanism/api/container/ContainerInteraction.class \
    mekanism/api/container/InContainerGetter.class \
    mekanism/api/container/InContainerGetterLong.class \
    mekanism/api/container/IntContainerInteraction.class \
    mekanism/api/container/LongContainerInteraction.class \
    mekanism/api/container/LongToLongContainerInteraction.class \
    mekanism/api/math/LongTransferUtils.class \
    mekanism/api/inventory/IInventorySlot.class \
    mekanism/api/inventory/IMekanismInventory.class \
    mekanism/api/inventory/ISidedItemHandler.class \
    mekanism/api/fluid/FluidStack.class \
    mekanism/api/energy/IEnergyContainer.class \
    mekanism/api/energy/IEnergyConversion.class \
    mekanism/api/heat/HeatAPI.class \
    mekanism/api/heat/IHeatCapacitor.class \
    mekanism/api/heat/IHeatHandler.class \
    mekanism/api/heat/IMekanismHeatHandler.class \
    mekanism/api/heat/ISidedHeatHandler.class \
    mekanism/common/capabilities/SimpleDynamicHandler.class \
    mekanism/common/capabilities/DynamicHandler.class \
    mekanism/common/capabilities/energy/BasicEnergyContainer.class \
    mekanism/common/capabilities/energy/DynamicStrictEnergyHandler.class \
    mekanism/common/capabilities/heat/BasicHeatCapacitor.class \
    mekanism/common/capabilities/heat/VariableHeatCapacitor.class \
    mekanism/common/capabilities/holder/BasicHolder.class \
    mekanism/common/capabilities/holder/IHolder.class \
    mekanism/common/capabilities/holder/ProxiedHolder.class \
    mekanism/common/capabilities/proxy/ProxyHandler.class \
    mekanism/common/capabilities/proxy/ProxyHeatHandler.class \
    mekanism/common/capabilities/proxy/ProxyStrictEnergyHandler.class \
    mekanism/common/config/listener/ConfigBasedCachedSupplier.class \
    mekanism/common/config/listener/ConfigBasedCachedFloatSupplier.class \
    mekanism/common/config/listener/ConfigBasedCachedIntSupplier.class \
    mekanism/common/config/listener/ConfigBasedCachedLongSupplier.class \
    mekanism/common/capabilities/holder/energy/EnergyContainerHolder.class \
    mekanism/common/capabilities/holder/energy/IEnergyContainerHolder.class \
    mekanism/common/capabilities/holder/heat/HeatCapacitorHolder.class \
    mekanism/common/capabilities/holder/heat/IHeatCapacitorHolder.class \
    mekanism/common/capabilities/holder/slot/IInventorySlotHolder.class \
    mekanism/common/capabilities/holder/slot/InventorySlotHolder.class \
    mekanism/common/lib/LastEnergyTracker.class \
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
    mekanism/fabric/energy/FabricEnergyConversions.class \
    mekanism/fabric/energy/FabricEnergyLookup.class \
    mekanism/fabric/energy/FabricEnergyStorage.class \
    mekanism/fabric/energy/FabricStrictEnergyHandler.class \
    mekanism/fabric/item/FabricItemLookup.class \
    mekanism/fabric/item/FabricItemStorage.class \
    'mekanism/fabric/item/FabricItemStorage$SlotView.class' \
    mekanism/fabric/fluid/FabricFluidConversions.class \
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

if [[ "$STATIC_ONLY" == 1 ]]; then
  RESULT=0
  printf 'fabric-packaged-smoke: PASSED (static remapped-JAR gates only; no server started)\n'
  exit 0
fi

LAUNCHER="$CACHE_DIR/fabric-server-${MINECRAFT_VERSION}-${LOADER_VERSION}-${INSTALLER_VERSION}.jar"
API_JAR="$CACHE_DIR/fabric-api-${FABRIC_API_VERSION}.jar"
ENERGY_API_JAR="$CACHE_DIR/energy-${TEAM_REBORN_ENERGY_VERSION}.jar"
fetch "https://meta.fabricmc.net/v2/versions/loader/${MINECRAFT_VERSION}/${LOADER_VERSION}/${INSTALLER_VERSION}/server/jar" "$LAUNCHER"
fetch "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/${FABRIC_API_VERSION}/fabric-api-${FABRIC_API_VERSION}.jar" "$API_JAR"
fetch "https://maven.fabricmc.net/teamreborn/energy/${TEAM_REBORN_ENERGY_VERSION}/energy-${TEAM_REBORN_ENERGY_VERSION}.jar" "$ENERGY_API_JAR"
if ! python3 - "$ENERGY_API_JAR" "$TEAM_REBORN_ENERGY_VERSION" <<'PYEOF'
import json, sys, zipfile
with zipfile.ZipFile(sys.argv[1]) as artifact:
    metadata = json.loads(artifact.read("fabric.mod.json"))
    entries = artifact.namelist()
if metadata.get("id") != "team_reborn_energy" or metadata.get("version") != sys.argv[2]:
    raise SystemExit(f"unexpected Team Reborn Energy metadata: {metadata.get('id')!r} {metadata.get('version')!r}")
if entries.count("team/reborn/energy/api/EnergyStorage.class") != 1:
    raise SystemExit("Team Reborn Energy API is missing EnergyStorage.class")
PYEOF
then
  echo "fabric-packaged-smoke: Team Reborn Energy dependency validation failed" >&2
  exit 1
fi

SERVER_DIR=$(mktemp -d "${TMPDIR:-/tmp}/mekanism-fabric-smoke.XXXXXX")
LOG_FILE="$SERVER_DIR/latest.log"
mkdir -p "$SERVER_DIR/mods"
printf 'eula=true\n' > "$SERVER_DIR/eula.txt"
printf 'online-mode=false\nserver-port=0\n' > "$SERVER_DIR/server.properties"
cp "$LAUNCHER" "$SERVER_DIR/fabric-server-launch.jar"
cp "$API_JAR" "$SERVER_DIR/mods/"
cp "$ENERGY_API_JAR" "$SERVER_DIR/mods/"
cp "${MOD_JARS[0]}" "${ADDON_JARS[@]}" "$SERVER_DIR/mods/"
printf '%s\n' "artifact=${MOD_JARS[0]}" "minecraft=$MINECRAFT_VERSION" "loader=$LOADER_VERSION" \
  "fabric_api=$FABRIC_API_VERSION" "team_reborn_energy=$TEAM_REBORN_ENERGY_VERSION" \
  "night_config=$NIGHT_CONFIG_VERSION" "installer=$INSTALLER_VERSION" > "$DIAGNOSTIC_DIR/run-info.txt"

printf 'fabric-packaged-smoke: starting clean server in %s\n' "$SERVER_DIR"
(
  cd "$SERVER_DIR"
  exec setsid java -Xms512M -Xmx2G -jar fabric-server-launch.jar nogui
) >"$LOG_FILE" 2>&1 &
SERVER_PID=$!

deadline=$((SECONDS + TIMEOUT))
mod_loaded=0
energy_mod_loaded=0
config_proof=0
network_proof=0
registry_proof=0
heat_proof=0
data_map_proof=0
chemical_data_map_proof=0
recipe_registration_proof=0
done_loaded=0
addon_configs_loaded=0
crash_pattern='(Exception in thread|A mod crashed on startup|Could not execute entrypoint|Failed to start the minecraft server|---- Minecraft Crash Report ----|NoClassDefFoundError|ClassNotFoundException|[[:space:]]ERROR[[:space:]])'
while (( SECONDS < deadline )); do
  if grep -Eq '(^|[[:space:]])- mekanism([[:space:]]|$)' "$LOG_FILE"; then mod_loaded=1; fi
  if grep -Eq '(^|[[:space:]])- team_reborn_energy([[:space:]]|$)' "$LOG_FILE"; then energy_mod_loaded=1; fi
  if grep -Fq 'MEKANISM_FABRIC_CONFIG_PROOF file=startup.toml values=12 loaded=true created=true corrected=12' "$LOG_FILE"; then config_proof=1; fi
  if grep -Fq 'MEKANISM_FABRIC_NETWORK_PROOF protocol=10.7.19 phase=configuration codecs=2 server_receiver=true client_required=true' "$LOG_FILE"; then network_proof=1; fi
  if grep -Fq 'MEKANISM_FABRIC_NETWORK_CLIENT_PROOF' "$LOG_FILE"; then
    echo 'fabric-packaged-smoke: client networking entrypoint loaded on a dedicated server' >&2
    exit 1
  fi
  if grep -Fq 'MEKANISM_FABRIC_REGISTRY_PROOF key=mekanism:seismic_vibration canonical_identity=true entries=4' "$LOG_FILE"; then registry_proof=1; fi
  if grep -Fq 'MEKANISM_FABRIC_HEAT_PROOF positional=true ambient_finite=true' "$LOG_FILE"; then heat_proof=1; fi
  if grep -Fq 'MEKANISM_FABRIC_DATAMAP_PROOF phase=startup epoch=1 published=21 skipped=0' "$LOG_FILE"; then data_map_proof=1; fi
  if grep -Fq 'MEKANISM_FABRIC_CHEMICAL_DATAMAP_PROOF phase=startup epoch=1 maps=5' "$LOG_FILE"; then chemical_data_map_proof=1; fi
  if grep -Fq 'MEKANISM_FABRIC_RECIPE_REGISTRATION_PROOF recipe_types=26 serializers=30 identity_preserved=true' "$LOG_FILE"; then recipe_registration_proof=1; fi
  if grep -Eq 'Done \(' "$LOG_FILE"; then done_loaded=1; fi
  # Release schema: core 303 (general without the development-only alphaWarning) + tools 63 + additions 41 + generators 51.
  if grep -Fq 'MEKANISM_FABRIC_SERVER_CONFIG_PROOF phase=server_start loaded=true configs=10 values=458 ' "$LOG_FILE"; then addon_configs_loaded=1; fi
  if grep -Eiq "$crash_pattern" "$LOG_FILE"; then
    echo 'fabric-packaged-smoke: crash signature detected' >&2
    grep -Ei "$crash_pattern" "$LOG_FILE" | tail -n 10 >&2 || true
    exit 1
  fi
  if (( mod_loaded && energy_mod_loaded && config_proof && network_proof && registry_proof && heat_proof && data_map_proof && chemical_data_map_proof && recipe_registration_proof && done_loaded && addon_configs_loaded )); then
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
    printf 'fabric-packaged-smoke: PASSED (Mekanism and Team Reborn Energy loaded, config/network/registry/heat/data-map/chemical proofs passed, and server reached Done)\n'
    grep -E '(^|[[:space:]])- (mekanism|team_reborn_energy)([[:space:]]|$)|MEKANISM_FABRIC_(CONFIG|NETWORK|REGISTRY|HEAT|DATAMAP|CHEMICAL_DATAMAP|RECIPE_REGISTRATION)_PROOF|Done \(' "$LOG_FILE" | tail -n 16
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

echo "fabric-packaged-smoke: timed out after ${TIMEOUT}s (mod_loaded=$mod_loaded energy_mod_loaded=$energy_mod_loaded config_proof=$config_proof network_proof=$network_proof registry_proof=$registry_proof heat_proof=$heat_proof data_map_proof=$data_map_proof chemical_data_map_proof=$chemical_data_map_proof recipe_registration_proof=$recipe_registration_proof done=$done_loaded)" >&2
tail -n 80 "$LOG_FILE" >&2
exit 1
