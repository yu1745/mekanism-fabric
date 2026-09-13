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
"$ROOT/fabric-build.sh" remapJar
mapfile -t MOD_JARS < <(find "$ROOT/fabric/build/libs" -maxdepth 1 -type f -name '*.jar' \
  ! -name '*-sources.jar' ! -name '*-dev.jar' | sort)
if [[ ${#MOD_JARS[@]} -ne 1 ]]; then
  printf 'fabric-packaged-smoke: expected exactly one remapped jar, found %d\n' "${#MOD_JARS[@]}" >&2
  printf '  %s\n' "${MOD_JARS[@]:-<none>}" >&2
  exit 1
fi
if ! python3 - "${MOD_JARS[0]}" "$TEAM_REBORN_ENERGY_VERSION" <<'PYEOF'
import json, sys, zipfile
with zipfile.ZipFile(sys.argv[1]) as artifact:
    metadata = json.loads(artifact.read("fabric.mod.json"))
    access_widener = artifact.read("mekanism.accesswidener").decode().splitlines()
expected = {
    "main": ["mekanism.fabric.MekanismFabricBootstrap"],
    "client": ["mekanism.fabric.MekanismFabricClientBootstrap"],
}
if (metadata.get("id") != "mekanism" or metadata.get("entrypoints") != expected
        or metadata.get("accessWidener") != "mekanism.accesswidener"
        or metadata.get("depends", {}).get("team_reborn_energy") != f">={sys.argv[2]}"):
    raise SystemExit(
        f"unexpected Fabric metadata: id={metadata.get('id')!r} "
        f"entrypoints={metadata.get('entrypoints')!r} accessWidener={metadata.get('accessWidener')!r} "
        f"team_reborn_energy={metadata.get('depends', {}).get('team_reborn_energy')!r}"
    )
expected_access_widener = [
    "accessWidener\tv2\tintermediary",
    "accessible\tmethod\tnet/minecraft/class_1959\tmethod_21740\t(Lnet/minecraft/class_2338;)F",
    "accessible\tfield\tnet/minecraft/class_1869\tfield_47320\tLnet/minecraft/class_8957;",
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
if jar tf "${MOD_JARS[0]}" | grep -Eq '^team/reborn/'; then
  echo "fabric-packaged-smoke: Team Reborn Energy must remain an external dependency" >&2
  exit 1
fi
if jar tf "${MOD_JARS[0]}" | grep -Eq '^net/fabricmc/loader/'; then
  echo "fabric-packaged-smoke: Fabric Loader classes must remain external" >&2
  exit 1
fi
if jar tf "${MOD_JARS[0]}" | grep -Eq '^mekanism/fixture/|^mekanism/fabric/(datamaps|chemical)/fixture/'; then
  echo "fabric-packaged-smoke: artifact unexpectedly contains fixture classes" >&2
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
forbidden_test_entries = sorted(
    entry for entry in entries
    if entry.endswith("Test.class") or "Test$" in entry.rsplit("/", 1)[-1]
)
if forbidden_test_entries:
    raise SystemExit(f"test classes leaked into the production artifact: {forbidden_test_entries!r}")
forbidden_compat_entries = sorted(
    entry for entry in entries
    if entry.startswith("xyz/bluspring/kilt/") or entry.startswith("org/sinytra/connector/")
    or "unfreeze" in entry.lower()
)
if forbidden_compat_entries:
    raise SystemExit(f"compatibility or registry-bypass classes leaked into the artifact: {forbidden_compat_entries!r}")
expected_holiday_values = {
    "mekanism/common/base/holiday/KnownDate.class",
    "mekanism/common/base/holiday/MonthlyDate.class",
    "mekanism/common/base/holiday/YearlyDate.class",
    "mekanism/common/base/holiday/HolidayMessage.class",
}
actual_holiday_values = {
    entry for entry in entries
    if entry.startswith("mekanism/common/base/holiday/") and entry.endswith(".class")
}
if (actual_holiday_values != expected_holiday_values
        or any(entries.count(entry) != 1 for entry in expected_holiday_values)):
    raise SystemExit(f"unexpected holiday value class family: {sorted(actual_holiday_values)!r}")
expected_block_state_descriptors = {
    "mekanism/common/block/states/IStateStorage.class",
    "mekanism/common/block/states/IFluidLogType.class",
    "mekanism/common/block/states/FluidLogType.class",
    "mekanism/common/block/states/TransmitterType.class",
    "mekanism/common/block/states/TransmitterType$Size.class",
}
actual_block_state_descriptors = {
    entry for entry in entries
    if entry.startswith("mekanism/common/block/states/") and entry.endswith(".class")
}
if (actual_block_state_descriptors != expected_block_state_descriptors
        or any(entries.count(entry) != 1 for entry in expected_block_state_descriptors)):
    raise SystemExit(f"unexpected block-state descriptor class family: {sorted(actual_block_state_descriptors)!r}")
expected_gui_components = {
    "mekanism/common/inventory/GuiComponents.class",
    "mekanism/common/inventory/GuiComponents$IDropdownEnum.class",
    "mekanism/common/inventory/GuiComponents$IToggleEnum.class",
}
actual_gui_components = {
    entry for entry in entries
    if entry.startswith("mekanism/common/inventory/GuiComponents") and entry.endswith(".class")
}
if (actual_gui_components != expected_gui_components
        or any(entries.count(entry) != 1 for entry in expected_gui_components)):
    raise SystemExit(f"unexpected GuiComponents class family: {sorted(actual_gui_components)!r}")
expected_inventory_slot_metadata = {
    "mekanism/common/inventory/container/slot/ArmorSlot.class",
    "mekanism/common/inventory/container/slot/ContainerSlotType.class",
    "mekanism/common/inventory/container/slot/HotBarSlot.class",
    "mekanism/common/inventory/container/slot/IInsertableSlot.class",
    "mekanism/common/inventory/container/slot/InsertableSlot.class",
    "mekanism/common/inventory/container/slot/InventoryContainerSlot.class",
    "mekanism/common/inventory/container/slot/IVirtualSlot.class",
    "mekanism/common/inventory/container/slot/MainInventorySlot.class",
    "mekanism/common/inventory/container/slot/OffhandSlot.class",
    "mekanism/common/inventory/container/slot/SlotOverlay.class",
    "mekanism/common/inventory/container/slot/VirtualInventoryContainerSlot.class",
}
slot_package_prefix = "mekanism/common/inventory/container/slot/"
actual_inventory_slot_metadata = {
    entry for entry in entries
    if entry.startswith(slot_package_prefix)
    and "/" not in entry[len(slot_package_prefix):]
    and entry.endswith(".class")
}
if (actual_inventory_slot_metadata != expected_inventory_slot_metadata
        or any(entries.count(entry) != 1 for entry in expected_inventory_slot_metadata)):
    raise SystemExit(f"unexpected direct inventory-slot class family: {sorted(actual_inventory_slot_metadata)!r}")
expected_concrete_inventory = {
    "mekanism/common/inventory/slot/BasicInventorySlot.class",
    "mekanism/common/inventory/slot/InputInventorySlot.class",
    "mekanism/common/inventory/slot/OutputInventorySlot.class",
}
actual_concrete_inventory = {
    entry for entry in entries if entry.startswith("mekanism/common/inventory/slot/") and entry.endswith(".class")
}
if actual_concrete_inventory != expected_concrete_inventory or any(entries.count(entry) != 1 for entry in expected_concrete_inventory):
    raise SystemExit(f"unexpected concrete inventory-slot family: {sorted(actual_concrete_inventory)!r}")
expected_warning_family = {
    "mekanism/common/inventory/warning/ISupportsWarning.class",
    "mekanism/common/inventory/warning/IWarningTracker.class",
    "mekanism/common/inventory/warning/WarningTracker.class",
    "mekanism/common/inventory/warning/WarningTracker$WarningType.class",
}
actual_warning_family = {
    entry for entry in entries if entry.startswith("mekanism/common/inventory/warning/") and entry.endswith(".class")
}
if actual_warning_family != expected_warning_family or any(entries.count(entry) != 1 for entry in expected_warning_family):
    raise SystemExit(f"unexpected warning family: {sorted(actual_warning_family)!r}")
expected_window_domain = {
    "mekanism/common/inventory/container/IGUIWindow.class",
    "mekanism/common/inventory/container/SelectedWindowData.class",
    "mekanism/common/inventory/container/SelectedWindowData$CachedWindowPosition.class",
    "mekanism/common/inventory/container/SelectedWindowData$WindowPosition.class",
    "mekanism/common/inventory/container/SelectedWindowData$WindowType.class",
    "mekanism/common/inventory/container/SelectedWindowData$WindowType$ConfigSaveData.class",
}
container_prefix = "mekanism/common/inventory/container/"
actual_window_domain = {
    entry for entry in entries if entry.startswith(container_prefix)
    and "/" not in entry[len(container_prefix):] and entry.endswith(".class")
}
if actual_window_domain != expected_window_domain or any(entries.count(entry) != 1 for entry in expected_window_domain):
    raise SystemExit(f"unexpected direct window-domain family: {sorted(actual_window_domain)!r}")
expected_fabric_registry = {
    "mekanism/fabric/registry/FabricCustomRegistryLifecycle.class",
    "mekanism/fabric/registry/FabricDefaultedRegistry.class",
    "mekanism/fabric/registry/FabricDeferredHolder.class",
    "mekanism/fabric/registry/FabricDeferredRegister.class",
    "mekanism/fabric/registry/FabricDeferredRegister$Entry.class",
    "mekanism/fabric/registry/FabricDeferredRegister$Registrar.class",
    "mekanism/fabric/registry/FabricRegistryLifecycle.class",
}
fabric_registry_prefix = "mekanism/fabric/registry/"
actual_fabric_registry = {
    entry for entry in entries
    if entry.startswith(fabric_registry_prefix) and entry.endswith(".class")
}
if (actual_fabric_registry != expected_fabric_registry
        or any(entries.count(entry) != 1 for entry in expected_fabric_registry)):
    raise SystemExit(f"unexpected Fabric custom-registry class family: {sorted(actual_fabric_registry)!r}")
with zipfile.ZipFile(sys.argv[1]) as registry_artifact:
    for entry in expected_fabric_registry:
        bytecode = registry_artifact.read(entry)
        lowered = bytecode.lower()
        if b"net/neoforged/" in bytecode or b"net/fabricmc/loader/" in bytecode:
            raise SystemExit(f"platform leakage in Fabric custom-registry bytecode: {entry}")
        if (b"mekanism/api/chemical/" in bytecode or b"mekanism/api/mekanismapi" in lowered
                or b"mekanism:empty" in lowered or b"radiation" in lowered or b"datamap" in lowered
                or b"data_map" in lowered or b"kilt" in lowered or b"connector" in lowered
                or b"unfreeze" in lowered):
            raise SystemExit(f"deferred registry architecture leaked into Fabric custom-registry bytecode: {entry}")
        if b"MEKANISM_FABRIC_" in bytecode:
            raise SystemExit(f"Fabric custom-registry foundation must not contain a startup marker: {entry}")
    registry_startup_bytecode = (
        registry_artifact.read("mekanism/fabric/MekanismFabricBootstrap.class")
        + registry_artifact.read("mekanism/fabric/MekanismFabricClientBootstrap.class")
    )
    registry_startup_references = {
        entry.split("$", 1)[0].removesuffix(".class").encode()
        for entry in expected_fabric_registry
    }
    leaked_registry_startup_references = sorted(
        reference.decode() for reference in registry_startup_references if reference in registry_startup_bytecode
    )
    if leaked_registry_startup_references:
        raise SystemExit(
            "Fabric custom-registry foundation must not be wired from either bootstrap: "
            f"{leaked_registry_startup_references!r}"
        )
expected_registration = {
    "mekanism/common/registration/INamedEntry.class",
    "mekanism/common/registration/MekanismDeferredHolder.class",
    "mekanism/common/registration/MekanismDeferredRegister.class",
    "mekanism/common/registration/MekanismDeferredRegister$Registrar.class",
    "mekanism/common/registration/DeferredMapCodecHolder.class",
    "mekanism/common/registration/DeferredMapCodecRegister.class",
    "mekanism/common/registration/DoubleWrappedRegistryObject.class",
}
registration_prefix = "mekanism/common/registration/"
actual_registration = {
    entry for entry in entries
    if entry.startswith(registration_prefix)
    and "/" not in entry[len(registration_prefix):]
    and entry.endswith(".class")
}
if (actual_registration != expected_registration
        or any(entries.count(entry) != 1 for entry in expected_registration)):
    raise SystemExit(f"unexpected direct deferred-registration class family: {sorted(actual_registration)!r}")
expected_registration_impl = {
    "mekanism/common/registration/impl/GameEventDeferredRegister.class",
    "mekanism/common/registration/impl/DeferredCriterionTrigger.class",
    "mekanism/common/registration/impl/LootFunctionDeferredRegister.class",
    "mekanism/common/registration/impl/SoundEventDeferredRegister.class",
    "mekanism/common/registration/impl/SoundEventRegistryObject.class",
    "mekanism/common/registration/impl/DamageTypeRegister.class",
    "mekanism/common/registration/impl/MekanismDamageType.class",
    "mekanism/common/registration/impl/ChemicalDeferredRegister.class",
    "mekanism/common/registration/impl/DeferredChemical.class",
    "mekanism/common/registration/impl/SlurryRegistryObject.class",
    "mekanism/common/registration/impl/RecipeTypeDeferredRegister.class",
    "mekanism/common/registration/impl/RecipeTypeRegistryObject.class",
}
registration_impl_prefix = "mekanism/common/registration/impl/"
actual_registration_impl = {
    entry for entry in entries
    if entry.startswith(registration_impl_prefix)
    and "/" not in entry[len(registration_impl_prefix):]
    and entry.endswith(".class")
}
if (actual_registration_impl != expected_registration_impl
        or any(entries.count(entry) != 1 for entry in expected_registration_impl)):
    raise SystemExit(f"unexpected deferred-registration impl class family: {sorted(actual_registration_impl)!r}")
expected_registries = {
    "mekanism/common/registries/MekanismGameEvents.class",
    "mekanism/common/registries/MekanismSounds.class",
    "mekanism/common/registries/MekanismDamageTypes.class",
    "mekanism/common/registries/MekanismChemicals.class",
    "mekanism/common/registries/MekanismChemicalIngredientTypes.class",
}
registries_prefix = "mekanism/common/registries/"
actual_registries = {
    entry for entry in entries
    if entry.startswith(registries_prefix)
    and "/" not in entry[len(registries_prefix):]
    and entry.endswith(".class")
}
if (actual_registries != expected_registries
        or any(entries.count(entry) != 1 for entry in expected_registries)):
    raise SystemExit(f"unexpected registry declaration class family: {sorted(actual_registries)!r}")
expected_tile_interfaces = {
    "mekanism/common/tile/interfaces/ITileWrapper.class",
    "mekanism/common/tile/interfaces/IHasDumpButton.class",
    "mekanism/common/tile/interfaces/IHasGasMode.class",
    "mekanism/common/tile/interfaces/IHasMode.class",
    "mekanism/common/tile/interfaces/IHasVisualization.class",
    "mekanism/common/tile/interfaces/ITileActive.class",
    "mekanism/common/tile/interfaces/IComparatorSupport.class",
}
actual_tile_interfaces = {
    entry for entry in entries
    if entry.startswith("mekanism/common/tile/interfaces/")
    and "/" not in entry[len("mekanism/common/tile/interfaces/"):]
    and entry.endswith(".class")
}
if (actual_tile_interfaces != expected_tile_interfaces
        or any(entries.count(entry) != 1 for entry in expected_tile_interfaces)):
    raise SystemExit(f"unexpected direct tile-interface class family: {sorted(actual_tile_interfaces)!r}")

# Exact reduced-tile-update family: the production base, the payload, the Fabric adapter, and the two client dispatch hooks only.
expected_tile_update = {
    "mekanism/common/tile/base/TileEntityUpdateable.class",
    "mekanism/common/network/to_client/PacketUpdateTile.class",
    "mekanism/fabric/network/FabricTileUpdateNetworking.class",
    "mekanism/fabric/network/FabricTileUpdateHandler.class",
    "mekanism/fabric/mixin/tile/LevelChunkMixin.class",
    "mekanism/fabric/mixin/tile/ClientPacketListenerTileMixin.class",
}
actual_tile_base = {entry for entry in entries if entry.startswith("mekanism/common/tile/base/") and entry.endswith(".class")}
actual_tile_update_platform = {entry for entry in entries if entry.endswith(".class") and (
      entry.startswith("mekanism/fabric/mixin/tile/")
      or entry in {"mekanism/fabric/network/FabricTileUpdateNetworking.class", "mekanism/fabric/network/FabricTileUpdateHandler.class"})}
if actual_tile_base != {"mekanism/common/tile/base/TileEntityUpdateable.class"}:
    raise SystemExit(f"unexpected tile base class family: {sorted(actual_tile_base)!r}")
if actual_tile_update_platform != (expected_tile_update - actual_tile_base - {"mekanism/common/network/to_client/PacketUpdateTile.class"}):
    raise SystemExit(f"unexpected reduced-tile-update platform class family: {sorted(actual_tile_update_platform)!r}")
if entries.count("mekanism/common/network/to_client/PacketUpdateTile.class") != 1:
    raise SystemExit("the reduced-tile-update payload must occur exactly once")
tile_update_fixtures = sorted(entry for entry in entries if entry.endswith(".class") and (
      entry.startswith("mekanism/fabric/tile/fixture/") or "TileUpdateRuntimeFixture" in entry
      or "TileUpdateRuntimeServerFixture" in entry or "TileUpdateRuntimeClientFixture" in entry))
if tile_update_fixtures:
    raise SystemExit(f"tile update runtime fixture leaked into the release artifact: {tile_update_fixtures!r}")
with zipfile.ZipFile(sys.argv[1]) as tile_update_artifact:
    for entry in actual_tile_base | actual_tile_update_platform | {"mekanism/common/network/to_client/PacketUpdateTile.class"}:
        bytecode = tile_update_artifact.read(entry)
        if b"net/neoforged/" in bytecode or b"xyz/bluspring" in bytecode or b"org/sinytra" in bytecode:
            raise SystemExit(f"unsupported architecture leaked into tile-update bytecode: {entry}")
        if entry.startswith("mekanism/common/") and b"net/fabricmc/" in bytecode:
            raise SystemExit(f"Fabric type leaked into portable tile-update bytecode: {entry}")
expected_slot_info = {
    "mekanism/common/tile/component/config/slot/ISlotInfo.class",
    "mekanism/common/tile/component/config/slot/BaseSlotInfo.class",
    "mekanism/common/tile/component/config/slot/EnergySlotInfo.class",
    "mekanism/common/tile/component/config/slot/HeatSlotInfo.class",
    "mekanism/common/tile/component/config/slot/InventorySlotInfo.class",
    "mekanism/common/tile/component/config/slot/ChemicalSlotInfo.class",
}
actual_slot_info = {
    entry for entry in entries
    if entry.startswith("mekanism/common/tile/component/config/slot/") and entry.endswith(".class")
}
if actual_slot_info != expected_slot_info or any(entries.count(entry) != 1 for entry in expected_slot_info):
    raise SystemExit(f"unexpected slot-info class family: {sorted(actual_slot_info)!r}")
expected_tiers = {
    "mekanism/common/tier/BinTier.class",
    "mekanism/common/tier/CableTier.class",
    "mekanism/common/tier/ChemicalTankTier.class",
    "mekanism/common/tier/ConductorTier.class",
    "mekanism/common/tier/EnergyCubeTier.class",
    "mekanism/common/tier/FactoryTier.class",
    "mekanism/common/tier/FluidTankTier.class",
    "mekanism/common/tier/InductionCellTier.class",
    "mekanism/common/tier/InductionProviderTier.class",
    "mekanism/common/tier/PipeTier.class",
    "mekanism/common/tier/QIODriveTier.class",
    "mekanism/common/tier/TransporterTier.class",
    "mekanism/common/tier/TubeTier.class",
}
actual_tiers = {
    entry for entry in entries
    if entry.startswith("mekanism/common/tier/") and entry.endswith(".class")
}
if actual_tiers != expected_tiers or any(entries.count(entry) != 1 for entry in expected_tiers):
    raise SystemExit(f"unexpected tier class family: {sorted(actual_tiers)!r}")
expected_config_values = {
    "mekanism/common/config/value/CachedValue.class",
    "mekanism/common/config/value/CachedValue$IConfigValueInvalidationListener.class",
    "mekanism/common/config/value/CachedBooleanValue.class",
    "mekanism/common/config/value/CachedByteValue.class",
    "mekanism/common/config/value/CachedShortValue.class",
    "mekanism/common/config/value/CachedDoubleValue.class",
    "mekanism/common/config/value/CachedFloatValue.class",
    "mekanism/common/config/value/CachedIntValue.class",
    "mekanism/common/config/value/CachedLongValue.class",
    "mekanism/common/config/value/CachedResolvableConfigValue.class",
    "mekanism/common/config/value/CachedConfigValue.class",
    "mekanism/common/config/value/CachedMapConfigValue.class",
    "mekanism/common/config/value/CachedMapConfigValue$ValueEncoder.class",
    "mekanism/common/config/value/CachedOredictionificatorConfigValue.class",
    "mekanism/common/config/value/CachedEnumValue.class",
}
actual_config_values = {
    entry for entry in entries
    if entry.startswith("mekanism/common/config/value/") and entry.endswith(".class")
}
if (actual_config_values != expected_config_values
        or any(entries.count(entry) != 1 for entry in expected_config_values)):
    raise SystemExit(f"unexpected config-value class family: {sorted(actual_config_values)!r}")
expected_direct_config = {
    "mekanism/common/config/BaseMekanismConfig.class",
    "mekanism/common/config/BaseMekanismConfig$ConfigSaver.class",
    "mekanism/common/config/ClientWindowConfigAccess.class",
    "mekanism/common/config/ClientWindowConfigAccess$Owner.class",
    "mekanism/common/config/ClientWindowConfigAccess$Position.class",
    "mekanism/common/config/ClientWindowConfigAccess$Registration.class",
    "mekanism/common/config/IConfigTranslation.class",
    "mekanism/common/config/IConfigTranslation$ConfigTranslation.class",
    "mekanism/common/config/IMekanismConfig.class",
    "mekanism/common/config/MekanismConfigTranslations.class",
    "mekanism/common/config/MekanismConfigTranslations$OreConfigTranslations.class",
    "mekanism/common/config/MekanismConfigTranslations$OreVeinConfigTranslations.class",
    "mekanism/common/config/MekanismConfigTranslations$TierTranslations.class",
    "mekanism/common/config/MekanismConfigType.class",
    "mekanism/common/config/MekanismStartupConfig.class",
    "mekanism/common/config/StartupConfigTranslations.class",
    "mekanism/common/config/TranslationPreset.class",
    "mekanism/common/config/CommonConfig.class",
    "mekanism/common/config/ChemicalConfigAccess.class",
    "mekanism/common/config/ChemicalConfigAccess$ServerPolicy.class",
}
common_config_prefix = "mekanism/common/config/"
actual_direct_config = {
    entry for entry in entries if entry.startswith(common_config_prefix)
    and "/" not in entry[len(common_config_prefix):] and entry.endswith(".class")
}
if actual_direct_config != expected_direct_config or any(entries.count(entry) != 1 for entry in expected_direct_config):
    raise SystemExit(f"unexpected direct common-config family: {sorted(actual_direct_config)!r}")
expected_fabric_config = {
    "mekanism/fabric/config/FabricClientConfigLifecycle.class",
    "mekanism/fabric/config/FabricMekanismConfigManager.class",
    "mekanism/fabric/config/FabricMekanismConfigManager$Registration.class",
    "mekanism/fabric/config/FabricWindowConfig.class",
    "mekanism/fabric/config/FabricWindowConfig$SaveOperation.class",
    "mekanism/fabric/config/FabricWindowConfig$WindowValues.class",
    "mekanism/fabric/config/MekanismConfigSpec.class",
    "mekanism/fabric/config/MekanismConfigSpec$Builder.class",
    "mekanism/fabric/config/MekanismConfigSpec$ConfigValue.class",
    "mekanism/fabric/config/MekanismConfigSpec$LoadResult.class",
    "mekanism/fabric/config/MekanismConfigSpec$Metadata.class",
    "mekanism/fabric/config/MekanismConfigSpec$RestartScope.class",
    "mekanism/fabric/config/MekanismConfigSpec$EnumValue.class",
    "mekanism/fabric/config/FabricChemicalConfig.class",
    "mekanism/fabric/config/FabricChemicalConfig$GeneralFragment.class",
}
actual_fabric_config = {
    entry for entry in entries if entry.startswith("mekanism/fabric/config/") and entry.endswith(".class")
}
if actual_fabric_config != expected_fabric_config or any(entries.count(entry) != 1 for entry in expected_fabric_config):
    raise SystemExit(f"unexpected Fabric config family: {sorted(actual_fabric_config)!r}")
expected_language_classes = {"mekanism/common/MekanismLang.class", "mekanism/common/MekanismLang$1.class"}
actual_language_classes = {entry for entry in entries if entry.startswith("mekanism/common/MekanismLang") and entry.endswith(".class")}
if actual_language_classes != expected_language_classes or any(entries.count(entry) != 1 for entry in expected_language_classes):
    raise SystemExit(f"unexpected MekanismLang class family: {sorted(actual_language_classes)!r}")
expected_text_utils = {
    "mekanism/common/util/text/TextUtils.class",
    "mekanism/common/util/text/TooltipHelper.class",
}
actual_text_utils = {entry for entry in entries if entry.startswith("mekanism/common/util/text/") and entry.endswith(".class")}
if actual_text_utils != expected_text_utils or entries.count("mekanism/common/util/text/TextUtils.class") != 1:
    raise SystemExit(f"unexpected common text utility family: {sorted(actual_text_utils)!r}")
import hashlib
with zipfile.ZipFile(sys.argv[1]) as task39_artifact:
    language = task39_artifact.read("assets/mekanism/lang/en_us.json")
    if task39_artifact.namelist().count("assets/mekanism/lang/en_us.json") != 1:
        raise SystemExit("generated en_us.json must occur exactly once")
    if hashlib.sha256(language).hexdigest() != "20c06e55147089e6d4b2fc3688da3970e8a6a96b61f8efd9e6f9841d40ec34a5":
        raise SystemExit("generated en_us.json differs from the exact oracle")
    task39_classes = (expected_inventory_slot_metadata | expected_concrete_inventory | expected_warning_family
                      | expected_window_domain
                      | (expected_direct_config - {
                          "mekanism/common/config/CommonConfig.class",
                          "mekanism/common/config/ChemicalConfigAccess.class",
                          "mekanism/common/config/ChemicalConfigAccess$ServerPolicy.class",
                      })
                      | (expected_fabric_config - {
                          "mekanism/fabric/config/FabricChemicalConfig.class",
                          "mekanism/fabric/config/FabricChemicalConfig$GeneralFragment.class",
                      })
                      | expected_language_classes | {"mekanism/common/util/text/TextUtils.class",
                          "mekanism/common/tile/component/config/slot/InventorySlotInfo.class"})
    for entry in task39_classes:
        bytecode = task39_artifact.read(entry)
        lowered = bytecode.lower()
        if (b"net/neoforged/" in bytecode or b"mekanism/api/chemical/" in bytecode
              or b"mekanism/common/content/qio/" in bytecode or b"constantpredicates" in lowered):
            raise SystemExit(f"deferred platform/QIO/Chemical/predicate dependency leaked into Task039 bytecode: {entry}")
    seam = task39_artifact.read("mekanism/common/config/ClientWindowConfigAccess.class")
    if b"mekanism/fabric/" in seam or b"net/fabricmc/" in seam or b"net/minecraft/client/" in seam:
        raise SystemExit("common client-window seam contains a Fabric or client linkage")
    common_bootstrap = task39_artifact.read("mekanism/fabric/MekanismFabricBootstrap.class")
    for forbidden in (b"FabricClientConfigLifecycle", b"FabricWindowConfig", b"ClientWindowConfigAccess", b"SelectedWindowData"):
        if forbidden in common_bootstrap:
            raise SystemExit(f"dedicated-server common bootstrap links client window owner: {forbidden!r}")
    client_bootstrap = task39_artifact.read("mekanism/fabric/MekanismFabricClientBootstrap.class")
    if b"FabricClientConfigLifecycle" not in client_bootstrap or b"FabricMekanismClientNetworking" not in client_bootstrap:
        raise SystemExit("client bootstrap must link synchronous config lifecycle and networking")
forbidden_task39_entries = sorted(entry for entry in entries if entry.startswith((
    "mekanism/fixture/", "mekanism/common/content/qio/"
)) or entry in {"mekanism/common/config/ClientConfig.class", "mekanism/common/config/MekanismConfig.class"})
if forbidden_task39_entries:
    raise SystemExit(f"fixture/deferred Task039 class leakage: {forbidden_task39_entries!r}")
expected_resources = {
    "mekanism/common/resource/IResource.class",
    "mekanism/common/resource/MiscResource.class",
    "mekanism/common/resource/BlockResourceInfo.class",
    "mekanism/common/resource/PrimaryResource.class",
    "mekanism/common/resource/ResourceType.class",
}
actual_resources = {
    entry for entry in entries
    if entry.startswith("mekanism/common/resource/") and entry.endswith(".class")
}
if actual_resources != expected_resources or any(entries.count(entry) != 1 for entry in expected_resources):
    raise SystemExit(f"unexpected resource class family: {sorted(actual_resources)!r}")
expected_direct_common_lib = {
    "mekanism/common/lib/Color.class",
    "mekanism/common/lib/Color$ColorFunction.class",
    "mekanism/common/lib/LastEnergyTracker.class",
    "mekanism/common/lib/WildcardMatcher.class",
}
actual_direct_common_lib = {
    entry for entry in entries
    if entry.startswith("mekanism/common/lib/")
    and "/" not in entry[len("mekanism/common/lib/"):]
    and entry.endswith(".class")
}
if (actual_direct_common_lib != expected_direct_common_lib
        or any(entries.count(entry) != 1 for entry in expected_direct_common_lib)):
    raise SystemExit(f"unexpected direct common lib class family: {sorted(actual_direct_common_lib)!r}")
expected_effect = {"mekanism/common/lib/effect/CustomEffect.class"}
actual_effect = {
    entry for entry in entries
    if entry.startswith("mekanism/common/lib/effect/") and entry.endswith(".class")
}
if actual_effect != expected_effect or any(entries.count(entry) != 1 for entry in expected_effect):
    raise SystemExit(f"unexpected effect class family: {sorted(actual_effect)!r}")
expected_math = {
    "mekanism/common/lib/math/Pos3D.class",
    "mekanism/common/lib/math/Quaternion.class",
    "mekanism/common/lib/math/Range3D.class",
}
actual_math = {
    entry for entry in entries
    if entry.startswith("mekanism/common/lib/math/") and entry.endswith(".class")
}
if actual_math != expected_math or any(entries.count(entry) != 1 for entry in expected_math):
    raise SystemExit(f"unexpected math class family: {sorted(actual_math)!r}")
expected_multipart = {
    "mekanism/common/util/MultipartUtils.class",
    "mekanism/common/util/MultipartUtils$RayTraceVectors.class",
    "mekanism/common/util/MultipartUtils$AdvancedRayTraceResult.class",
}
actual_multipart = {
    entry for entry in entries
    if entry.startswith("mekanism/common/util/MultipartUtils") and entry.endswith(".class")
}
if actual_multipart != expected_multipart or any(entries.count(entry) != 1 for entry in expected_multipart):
    raise SystemExit(f"unexpected MultipartUtils class family: {sorted(actual_multipart)!r}")
expected_stat = {"mekanism/common/util/StatUtils.class"}
actual_stat = {
    entry for entry in entries
    if entry.startswith("mekanism/common/util/StatUtils") and entry.endswith(".class")
}
if actual_stat != expected_stat or entries.count("mekanism/common/util/StatUtils.class") != 1:
    raise SystemExit(f"unexpected StatUtils class family: {sorted(actual_stat)!r}")
expected_api_codec = {"mekanism/api/codec/DependentMapCodec.class"}
actual_api_codec = {
    entry for entry in entries
    if entry.startswith("mekanism/api/codec/") and entry.endswith(".class")
}
if actual_api_codec != expected_api_codec or any(entries.count(entry) != 1 for entry in expected_api_codec):
    raise SystemExit(f"unexpected API codec class family: {sorted(actual_api_codec)!r}")
expected_serializer_helper = {
    "mekanism/api/SerializerHelper.class",
    "mekanism/api/SerializerHelper$1.class",
    "mekanism/api/SerializerHelper$2.class",
    "mekanism/api/SerializerHelper$3.class",
}
actual_serializer_helper = {
    entry for entry in entries
    if entry.startswith("mekanism/api/SerializerHelper") and entry.endswith(".class")
}
if (actual_serializer_helper != expected_serializer_helper
        or any(entries.count(entry) != 1 for entry in expected_serializer_helper)):
    raise SystemExit(f"unexpected SerializerHelper class family: {sorted(actual_serializer_helper)!r}")
if "mekanism/api/MekanismAPI.class" in entries:
    raise SystemExit("SerializerHelper must not admit MekanismAPI.class")
with zipfile.ZipFile(sys.argv[1]) as serializer_artifact:
    for entry in expected_serializer_helper:
        bytecode = serializer_artifact.read(entry)
        if b"net/neoforged/" in bytecode or b"net/fabricmc/loader/" in bytecode:
            raise SystemExit(f"platform leakage in SerializerHelper bytecode: {entry}")
        if b"MEKANISM_FABRIC_" in bytecode:
            raise SystemExit(f"SerializerHelper must not contain a startup marker: {entry}")
    if b"mekanism/api/SerializerHelper" in serializer_artifact.read("mekanism/fabric/MekanismFabricBootstrap.class"):
        raise SystemExit("SerializerHelper must not be initialized by the Fabric startup entrypoint")
expected_module_configs = {
    "mekanism/api/gear/config/ModuleConfig.class",
    "mekanism/api/gear/config/ModuleBooleanConfig.class",
    "mekanism/api/gear/config/ModuleColorConfig.class",
    "mekanism/api/gear/config/ModuleEnumConfig.class",
    "mekanism/api/gear/config/IHasModeIcon.class",
}
module_config_prefix = "mekanism/api/gear/config/"
actual_module_configs = {
    entry for entry in entries
    if entry.startswith(module_config_prefix)
    and "/" not in entry[len(module_config_prefix):]
    and entry.endswith(".class")
}
if (actual_module_configs != expected_module_configs
        or any(entries.count(entry) != 1 for entry in expected_module_configs)):
    raise SystemExit(f"unexpected direct module-config class family: {sorted(actual_module_configs)!r}")
expected_incremental_enum = {"mekanism/api/IIncrementalEnum.class"}
actual_incremental_enum = {
    entry for entry in entries
    if entry.startswith("mekanism/api/IIncrementalEnum") and entry.endswith(".class")
}
if (actual_incremental_enum != expected_incremental_enum
        or any(entries.count(entry) != 1 for entry in expected_incremental_enum)):
    raise SystemExit(f"unexpected IIncrementalEnum class family: {sorted(actual_incremental_enum)!r}")
expected_relative_side = {
    "mekanism/api/RelativeSide.class",
    "mekanism/api/RelativeSide$1.class",
}
actual_relative_side = {
    entry for entry in entries
    if entry.startswith("mekanism/api/RelativeSide") and entry.endswith(".class")
}
if (actual_relative_side != expected_relative_side
        or any(entries.count(entry) != 1 for entry in expected_relative_side)):
    raise SystemExit(f"unexpected RelativeSide class family: {sorted(actual_relative_side)!r}")
expected_text_api = {
    "mekanism/api/text/APILang.class",
    "mekanism/api/text/EnumColor.class",
    "mekanism/api/text/IHasTextComponent.class",
    "mekanism/api/text/IHasTextComponent$IHasEnumNameTextComponent.class",
    "mekanism/api/text/IHasTranslationKey.class",
    "mekanism/api/text/IHasTranslationKey$IHasEnumNameTranslationKey.class",
    "mekanism/api/text/ILangEntry.class",
    "mekanism/api/text/TextComponentUtil.class",
    "mekanism/api/text/TextComponentUtil$1.class",
    "mekanism/api/text/ITooltipHelper.class",
}
text_api_prefix = "mekanism/api/text/"
actual_text_api = {
    entry for entry in entries
    if entry.startswith(text_api_prefix)
    and "/" not in entry[len(text_api_prefix):]
    and entry.endswith(".class")
}
if actual_text_api != expected_text_api or any(entries.count(entry) != 1 for entry in expected_text_api):
    raise SystemExit(f"unexpected direct text API class family: {sorted(actual_text_api)!r}")
forbidden_portable_text_entries = sorted(
    entry for entry in entries
    if entry.startswith("mekanism/common/lib/inventory/HashedItem")
)
if forbidden_portable_text_entries:
    raise SystemExit(f"portable text closure admitted a deferred class: {forbidden_portable_text_entries!r}")
portable_text_classes = expected_incremental_enum | expected_relative_side | expected_text_api | {
    "mekanism/api/inventory/IHashedItem.class",
}
with zipfile.ZipFile(sys.argv[1]) as text_artifact:
    for entry in portable_text_classes:
        bytecode = text_artifact.read(entry)
        if b"net/neoforged/" in bytecode or b"net/fabricmc/loader/" in bytecode:
            raise SystemExit(f"platform leakage in portable text bytecode: {entry}")
        if b"MEKANISM_FABRIC_" in bytecode:
            raise SystemExit(f"portable text class must not contain a startup marker: {entry}")
    startup_bytecode = (
        text_artifact.read("mekanism/fabric/MekanismFabricBootstrap.class")
        + text_artifact.read("mekanism/fabric/MekanismFabricClientBootstrap.class")
    )
    startup_references = {
        entry.split("$", 1)[0].removesuffix(".class").encode()
        for entry in portable_text_classes
        if entry.endswith(".class")
    }
    leaked_startup_references = sorted(reference.decode() for reference in startup_references if reference in startup_bytecode)
    if leaked_startup_references:
        raise SystemExit(f"portable text closure must not be initialized by a Fabric entrypoint: {leaked_startup_references!r}")
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
    "mekanism/common/lib/collection/BiMultimap.class",
    "mekanism/common/lib/collection/EmptySequencedMap.class",
    "mekanism/common/lib/collection/FilterTransformIterator.class",
    "mekanism/common/lib/collection/HashList.class",
    "mekanism/common/lib/collection/IndexedCuboidMap.class",
    "mekanism/common/lib/collection/IndexedCuboidMap$1.class",
    "mekanism/common/lib/collection/IndexedCuboidMap$2.class",
    "mekanism/common/lib/collection/IndexedCuboidMap$CenteredBoundingBox.class",
    "mekanism/common/lib/collection/LRU.class",
    "mekanism/common/lib/collection/LRU$LRUEntry.class",
    "mekanism/common/lib/collection/LRU$LRUIterator.class",
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
expected_container_callbacks = {
    "mekanism/api/container/ContainerInteraction.class",
    "mekanism/api/container/InContainerGetter.class",
    "mekanism/api/container/InContainerGetterLong.class",
    "mekanism/api/container/IntContainerInteraction.class",
    "mekanism/api/container/LongContainerInteraction.class",
    "mekanism/api/container/LongToLongContainerInteraction.class",
}
actual_container_callbacks = {
    entry for entry in entries
    if entry.startswith("mekanism/api/container/")
    and "/" not in entry[len("mekanism/api/container/"):]
    and entry.endswith(".class")
}
if (actual_container_callbacks != expected_container_callbacks
        or any(entries.count(entry) != 1 for entry in expected_container_callbacks)):
    raise SystemExit(f"unexpected direct container callback class family: {sorted(actual_container_callbacks)!r}")
expected_long_transfer = {"mekanism/api/math/LongTransferUtils.class"}
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
expected_proxy_handlers = {
    "mekanism/common/capabilities/proxy/ProxyHandler.class",
    "mekanism/common/capabilities/proxy/ProxyHeatHandler.class",
    "mekanism/common/capabilities/proxy/ProxyStrictEnergyHandler.class",
    "mekanism/common/capabilities/proxy/ProxyChemicalHandler.class",
}
actual_proxy_handlers = {
    entry for entry in entries
    if entry.startswith("mekanism/common/capabilities/proxy/") and entry.endswith(".class")
}
if actual_proxy_handlers != expected_proxy_handlers or any(entries.count(entry) != 1 for entry in expected_proxy_handlers):
    raise SystemExit(f"unexpected proxy-handler class family: {sorted(actual_proxy_handlers)!r}")
expected_direct_holders = {
    "mekanism/common/capabilities/holder/BasicHolder.class",
    "mekanism/common/capabilities/holder/IHolder.class",
    "mekanism/common/capabilities/holder/ProxiedHolder.class",
}
actual_direct_holders = {
    entry for entry in entries
    if entry.startswith("mekanism/common/capabilities/holder/")
    and "/" not in entry[len("mekanism/common/capabilities/holder/"):]
    and entry.endswith(".class")
}
if actual_direct_holders != expected_direct_holders or any(entries.count(entry) != 1 for entry in expected_direct_holders):
    raise SystemExit(f"unexpected direct holder class family: {sorted(actual_direct_holders)!r}")
expected_inventory_slot_holders = {
    "mekanism/common/capabilities/holder/slot/IInventorySlotHolder.class",
    "mekanism/common/capabilities/holder/slot/InventorySlotHolder.class",
}
actual_inventory_slot_holders = {
    entry for entry in entries
    if entry.startswith("mekanism/common/capabilities/holder/slot/")
    and "/" not in entry[len("mekanism/common/capabilities/holder/slot/"):]
    and entry.endswith(".class")
}
if (actual_inventory_slot_holders != expected_inventory_slot_holders
        or any(entries.count(entry) != 1 for entry in expected_inventory_slot_holders)):
    raise SystemExit(f"unexpected direct inventory-slot holder class family: {sorted(actual_inventory_slot_holders)!r}")
expected_energy_holders = {
    "mekanism/common/capabilities/holder/energy/EnergyContainerHolder.class",
    "mekanism/common/capabilities/holder/energy/IEnergyContainerHolder.class",
    "mekanism/common/capabilities/holder/energy/ProxiedEnergyContainerHolder.class",
}
actual_energy_holders = {
    entry for entry in entries
    if entry.startswith("mekanism/common/capabilities/holder/energy/") and entry.endswith(".class")
}
if actual_energy_holders != expected_energy_holders or any(entries.count(entry) != 1 for entry in expected_energy_holders):
    raise SystemExit(f"unexpected energy-holder class family: {sorted(actual_energy_holders)!r}")
expected_energy_distribution = {
    "mekanism/common/content/network/distribution/EnergyAcceptorTarget.class",
    "mekanism/common/content/network/distribution/EnergySaveTarget.class",
    "mekanism/common/content/network/distribution/EnergySaveTarget$SaveHandler.class",
    "mekanism/common/content/network/distribution/EnergySaveTarget$DelegateSaveHandler.class",
}
actual_energy_distribution = {
    entry for entry in entries
    if entry.startswith("mekanism/common/content/network/distribution/") and entry.endswith(".class")
}
if (actual_energy_distribution != expected_energy_distribution
        or any(entries.count(entry) != 1 for entry in expected_energy_distribution)):
    raise SystemExit(f"unexpected energy-distribution class family: {sorted(actual_energy_distribution)!r}")
expected_laser_item = {"mekanism/common/capabilities/laser/item/LaserDissipationHandler.class"}
actual_laser_item = {
    entry for entry in entries
    if entry.startswith("mekanism/common/capabilities/laser/item/") and entry.endswith(".class")
}
if actual_laser_item != expected_laser_item or entries.count(next(iter(expected_laser_item))) != 1:
    raise SystemExit(f"unexpected laser-item class family: {sorted(actual_laser_item)!r}")
expected_cached_suppliers = {
    "mekanism/common/config/listener/ConfigBasedCachedSupplier.class",
    "mekanism/common/config/listener/ConfigBasedCachedFloatSupplier.class",
    "mekanism/common/config/listener/ConfigBasedCachedIntSupplier.class",
    "mekanism/common/config/listener/ConfigBasedCachedLongSupplier.class",
}
actual_cached_suppliers = {
    entry for entry in entries
    if entry.startswith("mekanism/common/config/listener/") and entry.endswith(".class")
}
if actual_cached_suppliers != expected_cached_suppliers or any(entries.count(entry) != 1 for entry in expected_cached_suppliers):
    raise SystemExit(f"unexpected cached-supplier class family: {sorted(actual_cached_suppliers)!r}")
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
expected_inventory_api = {
    "mekanism/api/inventory/IHashedItem.class",
    "mekanism/api/inventory/IInventorySlot.class",
    "mekanism/api/inventory/IMekanismInventory.class",
    "mekanism/api/inventory/ISidedItemHandler.class",
}
inventory_api_prefix = "mekanism/api/inventory/"
actual_inventory_api = {
    entry for entry in entries
    if entry.startswith(inventory_api_prefix)
    and "/" not in entry[len(inventory_api_prefix):]
    and entry.endswith(".class")
}
if actual_inventory_api != expected_inventory_api or any(entries.count(entry) != 1 for entry in expected_inventory_api):
    raise SystemExit(f"unexpected direct inventory API class family: {sorted(actual_inventory_api)!r}")
expected_energy_handlers = {
    "mekanism/api/energy/IStrictEnergyHandler.class",
    "mekanism/api/energy/ISidedStrictEnergyHandler.class",
    "mekanism/api/energy/IMekanismStrictEnergyHandler.class",
}
for entry in expected_energy_handlers:
    if entries.count(entry) != 1:
        raise SystemExit(f"strict energy-handler interface must be packaged exactly once: {entry}")
expected_energy_api = expected_energy_handlers | {
    "mekanism/api/energy/IEnergyContainer.class",
    "mekanism/api/energy/IEnergyConversion.class",
}
actual_energy_api = {
    entry for entry in entries
    if entry.startswith("mekanism/api/energy/") and entry.endswith(".class")
}
if actual_energy_api != expected_energy_api or any(entries.count(entry) != 1 for entry in expected_energy_api):
    raise SystemExit(f"unexpected packaged energy API class family: {sorted(actual_energy_api)!r}")
expected_fabric_energy = {
    "mekanism/fabric/energy/FabricEnergyConversions.class",
    "mekanism/fabric/energy/FabricEnergyLookup.class",
    "mekanism/fabric/energy/FabricEnergyStorage.class",
    "mekanism/fabric/energy/FabricStrictEnergyHandler.class",
}
actual_fabric_energy = {
    entry for entry in entries
    if entry.startswith("mekanism/fabric/energy/") and entry.endswith(".class")
}
if actual_fabric_energy != expected_fabric_energy or any(entries.count(entry) != 1 for entry in expected_fabric_energy):
    raise SystemExit(f"unexpected Fabric energy adapter class family: {sorted(actual_fabric_energy)!r}")
expected_fabric_item = {
    "mekanism/fabric/item/FabricItemLookup.class",
    "mekanism/fabric/item/FabricItemStorage.class",
    "mekanism/fabric/item/FabricItemStorage$SlotView.class",
}
actual_fabric_item = {
    entry for entry in entries
    if entry.startswith("mekanism/fabric/item/") and entry.endswith(".class")
}
if actual_fabric_item != expected_fabric_item or any(entries.count(entry) != 1 for entry in expected_fabric_item):
    raise SystemExit(f"unexpected Fabric item adapter class family: {sorted(actual_fabric_item)!r}")
expected_fluid_stack = {"mekanism/api/fluid/FluidStack.class"}
actual_fluid_stack = {
    entry for entry in entries
    if entry.startswith("mekanism/api/fluid/FluidStack") and entry.endswith(".class")
}
if actual_fluid_stack != expected_fluid_stack or any(entries.count(entry) != 1 for entry in expected_fluid_stack):
    raise SystemExit(f"unexpected FluidStack class family: {sorted(actual_fluid_stack)!r}")
expected_fabric_fluid = {"mekanism/fabric/fluid/FabricFluidConversions.class"}
actual_fabric_fluid = {
    entry for entry in entries
    if entry.startswith("mekanism/fabric/fluid/FabricFluidConversions") and entry.endswith(".class")
}
if actual_fabric_fluid != expected_fabric_fluid or any(entries.count(entry) != 1 for entry in expected_fabric_fluid):
    raise SystemExit(f"unexpected Fabric fluid conversion class family: {sorted(actual_fabric_fluid)!r}")
PYEOF
then
  echo "fabric-packaged-smoke: custom-registry/portable-text/SerializerHelper/item-boundary/block-state-descriptor/inventory-metadata/deferred-registration/tile-interface/slot-info/tier/resource/codec/collection/distribution/ChunkUtils/RegistryUtils/LambdaMetaFactoryUtil/long-transfer/dynamic-strict-energy/proxy/config-listener/common-lib-root production/test packaging contract failed" >&2
  exit 1
fi
if ! python3 - "${MOD_JARS[0]}" <<'PYEOF'
import json, sys, zipfile

expected_api_recipes = {
    "mekanism/api/recipes/MekanismRecipe.class",
    "mekanism/api/recipes/cache/CachedRecipe.class",
    "mekanism/api/recipes/cache/CachedRecipe$OperationTracker.class",
    "mekanism/api/recipes/cache/CachedRecipe$OperationTracker$RecipeError.class",
    "mekanism/api/recipes/cache/CachedRecipeHelper.class",
    "mekanism/api/recipes/cache/ICachedRecipeHolder.class",
    "mekanism/api/recipes/cache/ItemStackConstantChemicalToObjectCachedRecipe.class",
    "mekanism/api/recipes/cache/ItemStackConstantChemicalToObjectCachedRecipe$ChemicalUsageMultiplier.class",
    "mekanism/api/recipes/inputs/IInputHandler.class",
    "mekanism/api/recipes/inputs/ILongInputHandler.class",
    "mekanism/api/recipes/inputs/InputHelper.class",
    "mekanism/api/recipes/inputs/InputHelper$1.class",
    "mekanism/api/recipes/inputs/InputHelper$2.class",
    "mekanism/api/recipes/inputs/InputHelper$3.class",
    "mekanism/api/recipes/inputs/InputHelper$ChemicalInputHandler.class",
    "mekanism/api/recipes/outputs/IOutputHandler.class",
    "mekanism/api/recipes/outputs/OutputHelper.class",
    "mekanism/api/recipes/outputs/OutputHelper$1.class",
    "mekanism/api/recipes/outputs/OutputHelper$2.class",
    "mekanism/api/recipes/outputs/OutputHelper$3.class",
    "mekanism/api/recipes/outputs/OutputHelper$4.class",
    "mekanism/api/recipes/outputs/OutputHelper$5.class",
    "mekanism/api/recipes/outputs/OutputHelper$6.class",
    "mekanism/api/recipes/outputs/OutputHelper$7.class",
    "mekanism/api/recipes/cache/OneInputCachedRecipe.class",
    "mekanism/api/recipes/MekanismKeyedHolder.class",
    "mekanism/api/recipes/basic/BasicActivatingRecipe.class",
    "mekanism/api/recipes/basic/BasicCentrifugingRecipe.class",
    "mekanism/api/recipes/basic/BasicChemicalChemicalToChemicalRecipe.class",
    "mekanism/api/recipes/basic/BasicChemicalConversionRecipe.class",
    "mekanism/api/recipes/basic/BasicChemicalCrystallizerRecipe.class",
    "mekanism/api/recipes/basic/BasicChemicalDissolutionRecipe.class",
    "mekanism/api/recipes/basic/BasicChemicalInfuserRecipe.class",
    "mekanism/api/recipes/basic/BasicChemicalOxidizerRecipe.class",
    "mekanism/api/recipes/basic/BasicChemicalToChemicalRecipe.class",
    "mekanism/api/recipes/basic/BasicCombinerRecipe.class",
    "mekanism/api/recipes/basic/BasicCompressingRecipe.class",
    "mekanism/api/recipes/basic/BasicCrushingRecipe.class",
    "mekanism/api/recipes/basic/BasicElectrolysisRecipe.class",
    "mekanism/api/recipes/basic/BasicEnrichingRecipe.class",
    "mekanism/api/recipes/basic/BasicFluidToFluidRecipe.class",
    "mekanism/api/recipes/basic/BasicInjectingRecipe.class",
    "mekanism/api/recipes/basic/BasicItemStackChemicalToItemStackRecipe.class",
    "mekanism/api/recipes/basic/BasicItemStackToChemicalRecipe.class",
    "mekanism/api/recipes/basic/BasicItemStackToEnergyRecipe.class",
    "mekanism/api/recipes/basic/BasicItemStackToFluidOptionalItemRecipe.class",
    "mekanism/api/recipes/basic/BasicItemStackToFluidRecipe.class",
    "mekanism/api/recipes/basic/BasicItemStackToItemStackRecipe.class",
    "mekanism/api/recipes/basic/BasicMetallurgicInfuserRecipe.class",
    "mekanism/api/recipes/basic/BasicNucleosynthesizingRecipe.class",
    "mekanism/api/recipes/basic/BasicPaintingRecipe.class",
    "mekanism/api/recipes/basic/BasicPigmentExtractingRecipe.class",
    "mekanism/api/recipes/basic/BasicPigmentMixingRecipe.class",
    "mekanism/api/recipes/basic/BasicPressurizedReactionRecipe.class",
    "mekanism/api/recipes/basic/BasicPurifyingRecipe.class",
    "mekanism/api/recipes/basic/BasicRotaryRecipe.class",
    "mekanism/api/recipes/basic/BasicSawmillRecipe$BasicChanceOutput.class",
    "mekanism/api/recipes/basic/BasicSawmillRecipe.class",
    "mekanism/api/recipes/basic/BasicSmeltingRecipe.class",
    "mekanism/api/recipes/basic/BasicWashingRecipe.class",
    "mekanism/api/recipes/basic/IBasicChemicalOutput.class",
    "mekanism/api/recipes/basic/IBasicItemStackOutput.class",
    "mekanism/api/recipes/MekanismRecipeSerializers.class",
    "mekanism/api/recipes/MekanismRecipeTypes.class",
    "mekanism/api/recipes/IRecipeTypeRegistry.class",
    "mekanism/api/recipes/ChemicalChemicalToChemicalRecipe.class",
    "mekanism/api/recipes/ChemicalCrystallizerRecipe.class",
    "mekanism/api/recipes/ChemicalDissolutionRecipe.class",
    "mekanism/api/recipes/ChemicalToChemicalRecipe.class",
    "mekanism/api/recipes/CombinerRecipe.class",
    "mekanism/api/recipes/ElectrolysisRecipe.class",
    "mekanism/api/recipes/ElectrolysisRecipe$ElectrolysisRecipeOutput.class",
    "mekanism/api/recipes/FluidChemicalToChemicalRecipe.class",
    "mekanism/api/recipes/FluidToFluidRecipe.class",
    "mekanism/api/recipes/ItemStackChemicalToItemStackRecipe.class",
    "mekanism/api/recipes/ItemStackChemicalToObjectRecipe.class",
    "mekanism/api/recipes/ItemStackToChemicalRecipe.class",
    "mekanism/api/recipes/ItemStackToEnergyRecipe.class",
    "mekanism/api/recipes/ItemStackToFluidOptionalItemRecipe.class",
    "mekanism/api/recipes/ItemStackToFluidOptionalItemRecipe$FluidOptionalItemOutput.class",
    "mekanism/api/recipes/ItemStackToFluidRecipe.class",
    "mekanism/api/recipes/ItemStackToItemStackRecipe.class",
    "mekanism/api/recipes/NucleosynthesizingRecipe.class",
    "mekanism/api/recipes/PressurizedReactionRecipe.class",
    "mekanism/api/recipes/PressurizedReactionRecipe$PressurizedReactionRecipeOutput.class",
    "mekanism/api/recipes/RotaryRecipe.class",
    "mekanism/api/recipes/SawmillRecipe.class",
    "mekanism/api/recipes/SawmillRecipe$ChanceOutput.class",
    "mekanism/api/recipes/vanilla_input/BiChemicalRecipeInput.class",
    "mekanism/api/recipes/vanilla_input/ChemicalRecipeInput.class",
    "mekanism/api/recipes/vanilla_input/FluidChemicalRecipeInput.class",
    "mekanism/api/recipes/vanilla_input/FluidRecipeInput.class",
    "mekanism/api/recipes/vanilla_input/ItemChemicalRecipeInput.class",
    "mekanism/api/recipes/vanilla_input/ReactionRecipeInput.class",
    "mekanism/api/recipes/vanilla_input/RotaryRecipeInput.class",
    "mekanism/api/recipes/vanilla_input/SingleChemicalRecipeInput.class",
    "mekanism/api/recipes/vanilla_input/SingleFluidChemicalRecipeInput.class",
    "mekanism/api/recipes/vanilla_input/SingleFluidRecipeInput.class",
    "mekanism/api/recipes/vanilla_input/SingleItemChemicalRecipeInput.class",
    "mekanism/api/recipes/ingredients/InputIngredient.class",
    "mekanism/api/recipes/ingredients/IngredientType.class",
    "mekanism/api/recipes/ingredients/ItemStackIngredient.class",
    "mekanism/api/recipes/ingredients/ItemStackIngredient$FlatCodec.class",
    "mekanism/api/recipes/ingredients/ItemStackIngredient$NetworkCodec.class",
    "mekanism/api/recipes/ingredients/FluidStackIngredient.class",
    "mekanism/api/recipes/ingredients/FluidStackIngredient$FlatCodec.class",
    "mekanism/api/recipes/ingredients/ChemicalStackIngredient.class",
    "mekanism/api/recipes/ingredients/fluid/FluidIngredient.class",
    "mekanism/api/recipes/ingredients/fluid/FluidIngredient$SingleFluidIngredient.class",
    "mekanism/api/recipes/ingredients/fluid/FluidIngredient$TagFluidIngredient.class",
    "mekanism/api/recipes/ingredients/fluid/FluidIngredient$CompoundFluidIngredient.class",
    "mekanism/api/recipes/ingredients/fluid/FluidIngredient$ComponentFluidIngredient.class",
    "mekanism/api/recipes/ingredients/fluid/FluidIngredient$EmptyFluidIngredient.class",
    "mekanism/api/recipes/ingredients/fluid/FluidIngredient$FluidMapCodec.class",
    "mekanism/api/recipes/ingredients/fluid/FluidIngredient$FluidNetworkCodec.class",
    "mekanism/api/recipes/ingredients/chemical/ChemicalIngredient.class",
    "mekanism/api/recipes/ingredients/chemical/ChemicalIngredientTypeRegistry.class",
    "mekanism/api/recipes/ingredients/chemical/CompoundChemicalIngredient.class",
    "mekanism/api/recipes/ingredients/chemical/CompoundChemicalIngredient$AliasedChildrenCodec.class",
    "mekanism/api/recipes/ingredients/chemical/DifferenceChemicalIngredient.class",
    "mekanism/api/recipes/ingredients/chemical/EmptyChemicalIngredient.class",
    "mekanism/api/recipes/ingredients/chemical/IntersectionChemicalIngredient.class",
    "mekanism/api/recipes/ingredients/chemical/SingleChemicalIngredient.class",
    "mekanism/api/recipes/ingredients/chemical/TagChemicalIngredient.class",
    "mekanism/api/recipes/ingredients/creator/IIngredientCreator.class",
    "mekanism/api/recipes/ingredients/creator/IItemStackIngredientCreator.class",
    "mekanism/api/recipes/ingredients/creator/IFluidStackIngredientCreator.class",
    "mekanism/api/recipes/ingredients/creator/IChemicalIngredientCreator.class",
    "mekanism/api/recipes/ingredients/creator/IChemicalStackIngredientCreator.class",
    "mekanism/api/recipes/ingredients/creator/IIngredientCreatorProvider.class",
    "mekanism/api/recipes/ingredients/creator/IngredientCreatorAccess.class",
    "mekanism/api/fluid/IExtendedFluidTank.class",
    "mekanism/api/providers/IFluidProvider.class",
}
expected_common_recipes = {
    "mekanism/common/recipe/ingredients/ChemicalIngredientCreator.class",
    "mekanism/common/recipe/ingredients/ChemicalIngredientCreator$1.class",
    "mekanism/common/recipe/ingredients/ChemicalIngredientCreator$DispatchOrElseMapCodec.class",
    "mekanism/common/recipe/ingredients/creator/ChemicalStackIngredientCreator.class",
    "mekanism/common/recipe/ingredients/creator/ItemStackIngredientCreator.class",
    "mekanism/common/recipe/ingredients/creator/FluidStackIngredientCreator.class",
    "mekanism/common/registries/MekanismChemicalIngredientTypes.class",
    "mekanism/common/recipe/lookup/IRecipeLookupHandler.class",
    "mekanism/common/recipe/lookup/IRecipeLookupHandler$IRecipeTypedLookupHandler.class",
    "mekanism/common/recipe/lookup/IRecipeLookupHandler$ConstantUsageRecipeLookupHandler.class",
    "mekanism/common/recipe/lookup/ISingleRecipeLookupHandler.class",
    "mekanism/common/recipe/lookup/ISingleRecipeLookupHandler$ItemRecipeLookupHandler.class",
    "mekanism/common/recipe/lookup/ISingleRecipeLookupHandler$FluidRecipeLookupHandler.class",
    "mekanism/common/recipe/lookup/ISingleRecipeLookupHandler$ChemicalRecipeLookupHandler.class",
    "mekanism/common/recipe/lookup/IDoubleRecipeLookupHandler.class",
    "mekanism/common/recipe/lookup/IDoubleRecipeLookupHandler$DoubleItemRecipeLookupHandler.class",
    "mekanism/common/recipe/lookup/IDoubleRecipeLookupHandler$ItemChemicalRecipeLookupHandler.class",
    "mekanism/common/recipe/lookup/IDoubleRecipeLookupHandler$FluidChemicalRecipeLookupHandler.class",
    "mekanism/common/recipe/lookup/ITripleRecipeLookupHandler.class",
    "mekanism/common/recipe/lookup/ITripleRecipeLookupHandler$ItemFluidChemicalRecipeLookupHandler.class",
    "mekanism/common/recipe/lookup/IEitherSideRecipeLookupHandler.class",
    "mekanism/common/recipe/lookup/IEitherSideRecipeLookupHandler$EitherSideChemicalRecipeLookupHandler.class",
    "mekanism/common/recipe/lookup/monitor/RecipeCacheLookupMonitor.class",
    "mekanism/common/recipe/lookup/monitor/FactoryRecipeCacheLookupMonitor.class",
    "mekanism/client/recipe_viewer/type/IRecipeViewerRecipeType.class",
    "mekanism/common/recipe/lookup/cache/IInputRecipeCache.class",
    "mekanism/common/recipe/lookup/cache/RecipeCacheContext.class",
    "mekanism/common/recipe/lookup/cache/RecipeCacheContext$Binding.class",
    "mekanism/common/recipe/lookup/cache/RecipeCacheSource.class",
    "mekanism/common/recipe/MekanismRecipeType.class",
    "mekanism/common/recipe/IMekanismRecipeTypeProvider.class",
    "mekanism/common/recipe/lookup/cache/AbstractInputRecipeCache.class",
    "mekanism/common/recipe/lookup/cache/AbstractInputRecipeCache$CacheState.class",
    "mekanism/common/recipe/lookup/cache/SingleInputRecipeCache.class",
    "mekanism/common/recipe/lookup/cache/SingleInputRecipeCache$State.class",
    "mekanism/common/recipe/lookup/cache/SingleInputRecipeCache$CheckRecipeType.class",
    "mekanism/common/recipe/lookup/cache/RotaryInputRecipeCache.class",
    "mekanism/common/recipe/lookup/cache/RotaryInputRecipeCache$State.class",
    "mekanism/common/recipe/lookup/cache/DoubleInputRecipeCache.class",
    "mekanism/common/recipe/lookup/cache/DoubleInputRecipeCache$State.class",
    "mekanism/common/recipe/lookup/cache/DoubleInputRecipeCache$DoubleSameInputRecipeCache.class",
    "mekanism/common/recipe/lookup/cache/DoubleInputRecipeCache$CheckRecipeType.class",
    "mekanism/common/recipe/lookup/cache/EitherSideInputRecipeCache.class",
    "mekanism/common/recipe/lookup/cache/EitherSideInputRecipeCache$State.class",
    "mekanism/common/recipe/lookup/cache/TripleInputRecipeCache.class",
    "mekanism/common/recipe/lookup/cache/TripleInputRecipeCache$State.class",
    "mekanism/common/recipe/lookup/cache/InputRecipeCache.class",
    "mekanism/common/recipe/lookup/cache/InputRecipeCache$SingleItem.class",
    "mekanism/common/recipe/lookup/cache/InputRecipeCache$SingleFluid.class",
    "mekanism/common/recipe/lookup/cache/InputRecipeCache$SingleChemical.class",
    "mekanism/common/recipe/lookup/cache/InputRecipeCache$DoubleItem.class",
    "mekanism/common/recipe/lookup/cache/InputRecipeCache$ItemChemical.class",
    "mekanism/common/recipe/lookup/cache/InputRecipeCache$FluidChemical.class",
    "mekanism/common/recipe/lookup/cache/InputRecipeCache$EitherSideChemical.class",
    "mekanism/common/recipe/lookup/cache/InputRecipeCache$ItemFluidChemical.class",
    "mekanism/common/recipe/lookup/cache/type/IInputCache.class",
    "mekanism/common/recipe/lookup/cache/type/BaseInputCache.class",
    "mekanism/common/recipe/lookup/cache/type/ComponentSensitiveInputCache.class",
    "mekanism/common/recipe/lookup/cache/type/ItemInputCache.class",
    "mekanism/common/recipe/lookup/cache/type/ItemInputCache$1.class",
    "mekanism/common/recipe/lookup/cache/type/FluidInputCache.class",
    "mekanism/common/recipe/lookup/cache/type/FluidInputCache$1.class",
    "mekanism/common/recipe/lookup/cache/type/ChemicalInputCache.class",
    "mekanism/common/recipe/serializer/MekanismRecipeSerializer$1.class",
    "mekanism/common/recipe/serializer/MekanismRecipeSerializer.class",
    "mekanism/common/recipe/serializer/RotaryRecipeSerializer$AlternativeMapCodec.class",
    "mekanism/common/recipe/serializer/RotaryRecipeSerializer$ChemicalToFluid.class",
    "mekanism/common/recipe/serializer/RotaryRecipeSerializer$FluidToChemical.class",
    "mekanism/common/recipe/serializer/RotaryRecipeSerializer.class",
    "mekanism/common/recipe/serializer/SawmillRecipeSerializer.class",
    "mekanism/common/recipe/WrappedShapedRecipe.class",
}
expected_platform_recipes = {
    "mekanism/fabric/recipe/FabricIngredientCreatorProvider.class",
    "mekanism/fabric/recipe/FabricRecipeTypeRegistry.class",
    "mekanism/fabric/recipe/FabricRecipeCacheLifecycle.class",
    "mekanism/fabric/recipe/FabricRecipeCacheClientLifecycle.class",
    "mekanism/fabric/recipe/FabricRecipeRegistration.class",
    "mekanism/fabric/mixin/recipe/ClientPacketListenerMixin.class",
}
recipe_service = b"mekanism.fabric.recipe.FabricIngredientCreatorProvider\n"
recipe_type_service = b"mekanism.fabric.recipe.FabricRecipeTypeRegistry\n"
with zipfile.ZipFile(sys.argv[1]) as artifact:
    entries = artifact.namelist()
    actual_api_recipes = {e for e in entries if e.endswith(".class") and (
          e.startswith("mekanism/api/recipes/") or e == "mekanism/api/fluid/IExtendedFluidTank.class" or e == "mekanism/api/providers/IFluidProvider.class")}
    actual_common_recipes = {e for e in entries if e.endswith(".class") and (
          e.startswith("mekanism/common/recipe/ingredients/")
          or e.startswith("mekanism/common/recipe/lookup/")
          or e.startswith("mekanism/common/recipe/serializer/")
          or e.startswith("mekanism/common/recipe/MekanismRecipeType")
          or e == "mekanism/common/recipe/IMekanismRecipeTypeProvider.class"
          or e == "mekanism/common/recipe/WrappedShapedRecipe.class"
          or e == "mekanism/common/registries/MekanismChemicalIngredientTypes.class"
          or e == "mekanism/client/recipe_viewer/type/IRecipeViewerRecipeType.class")}
    actual_platform_recipes = {e for e in entries if e.endswith(".class") and (
          e.startswith("mekanism/fabric/recipe/") or e.startswith("mekanism/fabric/mixin/recipe/"))}
    if actual_api_recipes != expected_api_recipes:
        missing = sorted(expected_api_recipes - actual_api_recipes)
        extra = sorted(actual_api_recipes - expected_api_recipes)
        raise SystemExit(f"unexpected API recipe class family: missing={missing!r} extra={extra!r}")
    if actual_common_recipes != expected_common_recipes:
        missing = sorted(expected_common_recipes - actual_common_recipes)
        extra = sorted(actual_common_recipes - expected_common_recipes)
        raise SystemExit(f"unexpected common recipe class family: missing={missing!r} extra={extra!r}")
    if actual_platform_recipes != expected_platform_recipes:
        missing = sorted(expected_platform_recipes - actual_platform_recipes)
        extra = sorted(actual_platform_recipes - expected_platform_recipes)
        raise SystemExit(f"unexpected platform recipe class family: missing={missing!r} extra={extra!r}")
    for entry in expected_api_recipes | expected_common_recipes | expected_platform_recipes:
        if entries.count(entry) != 1:
            raise SystemExit(f"recipe class must occur exactly once: {entry}")
        bytecode = artifact.read(entry)
        if (b"net/neoforged/" in bytecode or b"kilt" in bytecode.lower() or b"connector" in bytecode.lower()
              or b"xyz/bluspring" in bytecode or b"org/sinytra" in bytecode):
            raise SystemExit(f"unsupported architecture leaked into recipe bytecode: {entry}")
        if entry.startswith("mekanism/api/") or entry.startswith("mekanism/common/"):
            if b"net/fabricmc/" in bytecode:
                raise SystemExit(f"Fabric type leaked into API/common recipe bytecode: {entry}")
    if entries.count("META-INF/services/mekanism.api.recipes.ingredients.creator.IIngredientCreatorProvider") != 1 \
          or artifact.read("META-INF/services/mekanism.api.recipes.ingredients.creator.IIngredientCreatorProvider") != recipe_service:
        raise SystemExit("wrong or duplicate ingredient creator service descriptor")
    if entries.count("META-INF/services/mekanism.api.recipes.IRecipeTypeRegistry") != 1 \
          or artifact.read("META-INF/services/mekanism.api.recipes.IRecipeTypeRegistry") != recipe_type_service:
        raise SystemExit("wrong or duplicate recipe type registry service descriptor")
    mixin_config = json.loads(artifact.read("mekanism.client.mixins.json"))
    if mixin_config.get("package") != "mekanism.fabric.mixin" or mixin_config.get("client") != [
          "recipe.ClientPacketListenerMixin", "tile.LevelChunkMixin", "tile.ClientPacketListenerTileMixin"] \
          or "mixins" in mixin_config or "server" in mixin_config or mixin_config.get("injectors", {}).get("defaultRequire") != 1:
        raise SystemExit(f"client-only mixin contract violated: {mixin_config!r}")
    metadata = json.loads(artifact.read("fabric.mod.json"))
    if metadata.get("mixins") != [{"config": "mekanism.client.mixins.json", "environment": "client"}]:
        raise SystemExit(f"mixin config must be environment-scoped client-only: {metadata.get('mixins')!r}")
    recipe_data = sorted(e for e in entries if e.startswith("data/mekanism/recipe/"))
    if recipe_data:
        raise SystemExit(f"production recipe data leaked into the artifact: {recipe_data!r}")
    forbidden_042b = sorted(e for e in entries if e.endswith(".class") and (
          e.startswith("mekanism/common/registries/MekanismRecipeSerializersInternal")
          or e.startswith("mekanism/common/recipe/upgrade/")
          or e.startswith("mekanism/common/recipe/bin/")
          or e == "mekanism/common/recipe/ClearConfigurationRecipe.class"
          or (e.startswith("mekanism/client/") and e != "mekanism/client/recipe_viewer/type/IRecipeViewerRecipeType.class")
          or e.startswith("mekanism/common/registries/MekanismItems")
          or e == "mekanism/api/MekanismAPI.class"))
    if forbidden_042b:
        raise SystemExit(f"deferred recipe families leaked into the artifact: {forbidden_042b!r}")
    recipe_fixture_entries = sorted(e for e in entries if e.endswith(".class") and (
          e.startswith("mekanism/api/recipes/Test")
          or e.startswith("mekanism/api/recipes/RecipeOutputHandlingTest")
          or "Fixture" in e.split("/")[-1]))
    if recipe_fixture_entries:
        raise SystemExit(f"recipe fixture/test classes leaked into the artifact: {recipe_fixture_entries!r}")
PYEOF
then
  echo "fabric-packaged-smoke: recipe ingredient/cache/platform class family contract failed" >&2
  exit 1
fi
if ! python3 - "${MOD_JARS[0]}" <<'PYEOF'
import hashlib, sys, zipfile

expected_api = {
    "mekanism/api/datamaps/IMekanismDataMapTypes.class",
    "mekanism/api/datamaps/MekaSuitAbsorption.class",
    "mekanism/api/datamaps/MekanismDataMapType.class",
    "mekanism/api/datamaps/chemical/ChemicalSolidTag.class",
    "mekanism/api/datamaps/chemical/attribute/ChemicalFuel.class",
    "mekanism/api/datamaps/chemical/attribute/ChemicalRadioactivity.class",
    "mekanism/api/datamaps/chemical/attribute/CooledCoolant.class",
    "mekanism/api/datamaps/chemical/attribute/HeatedCoolant.class",
    "mekanism/api/datamaps/chemical/attribute/IChemicalAttribute.class",
    "mekanism/api/datamaps/chemical/attribute/IChemicalCoolant.class",
}
expected_platform = {
    "mekanism/fabric/datamaps/FabricChemicalDataMapSession.class",
    "mekanism/fabric/datamaps/FabricChemicalDataMaps.class",
    "mekanism/fabric/datamaps/FabricChemicalDataMaps$1.class",
    "mekanism/fabric/datamaps/FabricChemicalDataMaps$Decoder.class",
    "mekanism/fabric/datamaps/FabricChemicalDataMaps$Encoder.class",
    "mekanism/fabric/datamaps/FabricDataMapLoader.class",
    "mekanism/fabric/datamaps/FabricDataMapLoader$Entry.class",
    "mekanism/fabric/datamaps/FabricDataMapLoader$Layer.class",
    "mekanism/fabric/datamaps/FabricDataMapLoader$Result.class",
    "mekanism/fabric/datamaps/FabricMekanismDataMaps.class",
    "mekanism/fabric/datamaps/FabricMekanismDataMaps$Session.class",
    "mekanism/fabric/datamaps/FabricMekanismDataMaps$Snapshot.class",
}
resources = {
    "data/mekanism/data_maps/damage_type/mekasuit_absorption.json": "437587714884bfef9045ac987b97e8b2d63ccb304142181d934e8ef21b471d41",
    "data/mekanism/tags/damage_type/mekasuit_always_supported.json": "7237a571f951f8deef3cacb86518b3b8560f145dad16777701142004814b07da",
    "data/mekanism/data_maps/mekanism/chemical/chemical_attribute_cooled_coolant.json": "14a5f5aec6647b753aadb2afb340fe6cc0bdbbf8f70a19665386038422cfb6b7",
    "data/mekanism/data_maps/mekanism/chemical/chemical_attribute_fuel.json": "4ddf704615c656b520aae009910df3430b372584f60073c229e6ba3144ca573e",
    "data/mekanism/data_maps/mekanism/chemical/chemical_attribute_heated_coolant.json": "550e66bcd2ee76d12554e54f40833966d90a7850ee2e4499f763c93963725391",
    "data/mekanism/data_maps/mekanism/chemical/chemical_attribute_radioactivity.json": "c3e432ad8af5b89256d527be0c718ecd9cad7b3ea734a6e620d43000f4f05b4a",
    "data/mekanism/data_maps/mekanism/chemical/chemical_solid_tag.json": "8899452dcc86851401e43dddc75b805b18466cd23462a986bd132a12475b1dca",
    "data/mekanism/tags/mekanism/chemical/bio.json": "cdd00ab957e9a853f213d618e891ca7d6a98f75967a93a5b23dcf201531e1880",
    "data/mekanism/tags/mekanism/chemical/carbon.json": "82d649000e6aecec5b154c75ae19f6b484a86326e72bfd3e437a8bbd44b8642b",
    "data/mekanism/tags/mekanism/chemical/clean.json": "886efcb61350c3997c64e5a301cb7bb083158f8ccb0449ea0722a520f1c89763",
    "data/mekanism/tags/mekanism/chemical/diamond.json": "9fecc5a3aa0de60d6e2ba6daf2b0558f56b0f6237748f056143620ea3e8c8812",
    "data/mekanism/tags/mekanism/chemical/dirty.json": "cccead98fd5747b5c9174263f2d04960323872021b2dc40eacd43769e775aa47",
    "data/mekanism/tags/mekanism/chemical/fungi.json": "cb07aff1a17f37cfe8b95b59577ed3503be6ad51cec8b267f80780eaf7cee943",
    "data/mekanism/tags/mekanism/chemical/gaseous.json": "e2a66162f320d3015f378ab20f6f87fbf4c3265abf3b781be15632ad81274f6f",
    "data/mekanism/tags/mekanism/chemical/gold.json": "92ce68c2d11a2918d6600f193de250e46e5d1fbf11eaa0d2062dd703deb2ad20",
    "data/mekanism/tags/mekanism/chemical/redstone.json": "092242cbc81247ff4c21f6f1402470578413568a9b9e3722609fa0f47d0c7fa0",
    "data/mekanism/tags/mekanism/chemical/refined_obsidian.json": "71feeecaa60653021681f435f432fa3f63311adaa34829037b86d5c08fb23798",
    "data/mekanism/tags/mekanism/chemical/tin.json": "bcb708d7ed1d247de6d55c17b72f879f5a8868b740d231f82d6ea78e87478646",
    "data/mekanism/tags/mekanism/chemical/waste_barrel_decay_blacklist.json": "21c3c44a56d76779e7a8c779f69e50c9494f2da7a3f43d22b8ebe336b39863fe",
    "data/mekanism/tags/mekanism/chemical/water_vapor.json": "7177474643986262ece934f42d35a24ca4bd9ed3bf4f6307e82557ebf4c7147b",
}
services = {
    "META-INF/services/mekanism.api.text.ITooltipHelper": b"mekanism.common.util.text.TooltipHelper",
    "META-INF/services/mekanism.api.datamaps.IMekanismDataMapTypes": b"mekanism.fabric.datamaps.FabricChemicalDataMaps\n",
}
with zipfile.ZipFile(sys.argv[1]) as artifact:
    entries = artifact.namelist()
    actual_api = {e for e in entries if e.startswith("mekanism/api/datamaps/") and e.endswith(".class")}
    actual_platform = {e for e in entries if e.startswith("mekanism/fabric/datamaps/") and e.endswith(".class")}
    if actual_api != expected_api or actual_platform != expected_platform:
        raise SystemExit(f"unexpected data-map class families: api={sorted(actual_api)!r} platform={sorted(actual_platform)!r}")
    if "mekanism/fabric/datamaps/FabricDataMapType.class" in entries:
        raise SystemExit("relocated FabricDataMapType must not remain in the artifact")
    for entry in expected_api | expected_platform:
        if entries.count(entry) != 1:
            raise SystemExit(f"data-map class must occur exactly once: {entry}")
        bytecode = artifact.read(entry)
        lowered = bytecode.lower()
        if (b"net/neoforged/" in bytecode or b"mekanism/api/mekanismapi" in lowered
                or b"advanceddatamap" in lowered or b"datamaptyperegister" in lowered
                or b"kilt" in lowered or b"connector" in lowered):
            raise SystemExit(f"unsupported architecture leaked into data-map bytecode: {entry}")
    for entry, expected_hash in resources.items():
        if entries.count(entry) != 1:
            raise SystemExit(f"connected data-map resource must occur exactly once: {entry}")
        actual_hash = hashlib.sha256(artifact.read(entry)).hexdigest()
        if actual_hash != expected_hash:
            raise SystemExit(f"wrong data-map resource hash for {entry}: {actual_hash}")
    for entry, expected_content in services.items():
        if entries.count(entry) != 1 or artifact.read(entry) != expected_content:
            raise SystemExit(f"wrong or duplicate service descriptor: {entry}")
    admitted = set(resources)
    unexpected_generated = sorted(e for e in entries if e.startswith("data/mekanism/") and not e.endswith("/")
                                  and ("/data_maps/" in e or "/tags/damage_type/" in e
                                       or "/tags/mekanism/chemical/" in e) and e not in admitted)
    if unexpected_generated:
        raise SystemExit(f"unexpected generated data-map closure resources: {unexpected_generated!r}")
PYEOF
then
  echo "fabric-packaged-smoke: data-map/resource/service/leakage contract failed" >&2
  exit 1
fi
if ! python3 - "${MOD_JARS[0]}" <<'PYEOF'
import sys, zipfile

expected_api_chemical = {
    "mekanism/api/chemical/BasicChemicalTank.class",
    "mekanism/api/chemical/Chemical.class",
    "mekanism/api/chemical/Chemical$DerivationFrame.class",
    "mekanism/api/chemical/Chemical$Derived.class",
    "mekanism/api/chemical/Chemical$DerivedBuilder.class",
    "mekanism/api/chemical/Chemical$DerivedCacheEntry.class",
    "mekanism/api/chemical/Chemical$LegacyState.class",
    "mekanism/api/chemical/ChemicalDataContext$Binding.class",
    "mekanism/api/chemical/ChemicalBuilder.class",
    "mekanism/api/chemical/ChemicalDataContext.class",
    "mekanism/api/chemical/ChemicalRegistry.class",
    "mekanism/api/chemical/ChemicalStack.class",
    "mekanism/api/chemical/ChemicalStack$1.class",
    "mekanism/api/chemical/ChemicalStack$2.class",
    "mekanism/api/chemical/ChemicalUtils.class",
    "mekanism/api/chemical/IChemicalHandler.class",
    "mekanism/api/chemical/IChemicalTank.class",
    "mekanism/api/chemical/IMekanismChemicalHandler.class",
    "mekanism/api/chemical/ISidedChemicalHandler.class",
    "mekanism/api/chemical/attribute/ChemicalAttribute.class",
    "mekanism/api/chemical/attribute/ChemicalAttributeValidator.class",
    "mekanism/api/chemical/attribute/ChemicalAttributeValidator$1.class",
    "mekanism/api/chemical/attribute/ChemicalAttributeValidator$2.class",
    "mekanism/api/chemical/attribute/ChemicalAttributeValidator$ChemicalAttributeValidatorLegacyAdapter.class",
    "mekanism/api/chemical/attribute/ChemicalAttributeValidator$SimpleAttributeValidator.class",
    "mekanism/api/chemical/attribute/ChemicalAttributes.class",
    "mekanism/api/chemical/attribute/ChemicalAttributes$Coolant.class",
    "mekanism/api/chemical/attribute/ChemicalAttributes$CooledCoolant.class",
    "mekanism/api/chemical/attribute/ChemicalAttributes$Fuel.class",
    "mekanism/api/chemical/attribute/ChemicalAttributes$HeatedCoolant.class",
    "mekanism/api/chemical/attribute/ChemicalAttributes$Radiation.class",
    "mekanism/api/chemical/attribute/IChemicalAttributeContainer.class",
}
expected_platform_chemical = {
    "mekanism/fabric/chemical/FabricChemicalClientLifecycle.class",
    "mekanism/fabric/chemical/FabricChemicalLifecycle.class",
    "mekanism/fabric/chemical/FabricChemicalRegistryLifecycle.class",
}
expected_network_chemical = {
    "mekanism/fabric/network/ChemicalSnapshotAckPayload.class",
    "mekanism/fabric/network/ChemicalSnapshotPayload.class",
    "mekanism/fabric/network/ChemicalSnapshotPayload$1.class",
    "mekanism/fabric/network/ChemicalSnapshotPayload$Allocation.class",
    "mekanism/fabric/network/ChemicalSnapshotPayload$Cooled.class",
    "mekanism/fabric/network/ChemicalSnapshotPayload$EntryFactory.class",
    "mekanism/fabric/network/ChemicalSnapshotPayload$Fuel.class",
    "mekanism/fabric/network/ChemicalSnapshotPayload$Heated.class",
    "mekanism/fabric/network/ChemicalSnapshotPayload$Radioactivity.class",
    "mekanism/fabric/network/ChemicalSnapshotPayload$Solid.class",
    "mekanism/fabric/network/FabricChemicalClientNetworking.class",
    "mekanism/fabric/network/FabricChemicalNetworking.class",
    "mekanism/fabric/network/FabricChemicalNetworking$AckDecision.class",
    "mekanism/fabric/network/FabricChemicalNetworking$Pending.class",
    "mekanism/fabric/network/FabricChemicalNetworking$SnapshotTask.class",
}
expected_common_chemical = {
    "mekanism/common/capabilities/chemical/DynamicChemicalHandler.class",
    "mekanism/common/capabilities/holder/chemical/ChemicalTankHolder.class",
    "mekanism/common/capabilities/holder/chemical/IChemicalTankHolder.class",
}
required_singletons = {
    "mekanism/api/MekanismServices.class",
    "mekanism/api/IDisableableEnum.class",
    "mekanism/api/functions/ConstantPredicates.class",
    "mekanism/api/functions/TriPredicate.class",
    "mekanism/api/providers/IBaseProvider.class",
    "mekanism/api/providers/IChemicalProvider.class",
    "mekanism/api/radiation/RadiationValidation.class",
    "mekanism/common/ChemicalConstants.class",
    "mekanism/common/base/IChemicalConstant.class",
}
with zipfile.ZipFile(sys.argv[1]) as artifact:
    entries = artifact.namelist()
    actual_api_chemical = {e for e in entries if e.startswith("mekanism/api/chemical/") and e.endswith(".class")}
    actual_platform_chemical = {e for e in entries if e.startswith("mekanism/fabric/chemical/") and e.endswith(".class")}
    actual_network_chemical = {e for e in entries if e.startswith("mekanism/fabric/network/") and e.endswith(".class")
                               and (e.rsplit("/", 1)[-1].startswith("ChemicalSnapshot")
                                    or e.rsplit("/", 1)[-1].startswith("FabricChemical"))}
    actual_common_chemical = {e for e in entries if e.endswith(".class") and (
          e.startswith("mekanism/common/capabilities/chemical/")
          or e.startswith("mekanism/common/capabilities/holder/chemical/"))}
    families = ((actual_api_chemical, expected_api_chemical, "API chemical"),
                (actual_platform_chemical, expected_platform_chemical, "Fabric chemical"),
                (actual_network_chemical, expected_network_chemical, "chemical network"),
                (actual_common_chemical, expected_common_chemical, "common chemical"))
    for actual, expected, label in families:
        if actual != expected or any(entries.count(entry) != 1 for entry in expected):
            raise SystemExit(f"unexpected {label} class family: {sorted(actual)!r}")
    for entry in required_singletons:
        if entries.count(entry) != 1:
            raise SystemExit(f"chemical closure singleton must occur exactly once: {entry}")
    for entry in expected_api_chemical | required_singletons:
        bytecode = artifact.read(entry)
        if b"net/neoforged/" in bytecode or b"net/fabricmc/" in bytecode:
            raise SystemExit(f"platform dependency leaked into portable chemical bytecode: {entry}")
    leaked_fixtures = sorted(e for e in entries if e.startswith("mekanism/fabric/chemical/fixture/"))
    if leaked_fixtures:
        raise SystemExit(f"chemical runtime fixture leaked into release artifact: {leaked_fixtures!r}")
PYEOF
then
  echo "fabric-packaged-smoke: exact chemical class/leakage contract failed" >&2
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
  printf 'fabric-packaged-smoke: PASS (static remapped-JAR gates only; no server started)\n'
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
cp "${MOD_JARS[0]}" "$SERVER_DIR/mods/"
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
  if grep -Fq 'MEKANISM_FABRIC_RECIPE_REGISTRATION_PROOF recipe_types=26 serializers=26 identity_preserved=true' "$LOG_FILE"; then recipe_registration_proof=1; fi
  if grep -Eq 'Done \(' "$LOG_FILE"; then done_loaded=1; fi
  if grep -Eiq "$crash_pattern" "$LOG_FILE"; then
    echo 'fabric-packaged-smoke: crash signature detected' >&2
    grep -Ei "$crash_pattern" "$LOG_FILE" | tail -n 10 >&2 || true
    exit 1
  fi
  if (( mod_loaded && energy_mod_loaded && config_proof && network_proof && registry_proof && heat_proof && data_map_proof && chemical_data_map_proof && recipe_registration_proof && done_loaded )); then
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
    printf 'fabric-packaged-smoke: PASS (Mekanism and Team Reborn Energy loaded, config/network/registry/heat/data-map/chemical proofs passed, and server reached Done)\n'
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
