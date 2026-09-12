# Fabric heat-container core

## Current slice

The current heat milestone admits the official internal heat contract, implementation, standard handler aggregation, and non-config relative-side holder:

- `mekanism.api.heat.HeatAPI`
- `mekanism.api.heat.IHeatCapacitor`
- `mekanism.api.heat.IHeatHandler`
- `mekanism.api.heat.ISidedHeatHandler`
- `mekanism.api.heat.IMekanismHeatHandler`
- `mekanism.common.capabilities.heat.BasicHeatCapacitor`
- `mekanism.common.capabilities.heat.VariableHeatCapacitor`
- `mekanism.common.capabilities.holder.heat.IHeatCapacitorHolder`
- `mekanism.common.capabilities.holder.heat.HeatCapacitorHolder`
- `mekanism.common.tile.component.config.slot.ISlotInfo`
- `mekanism.common.tile.component.config.slot.BaseSlotInfo`
- `mekanism.common.tile.component.config.slot.HeatSlotInfo`

Task 033 brings the current audited source counts to API **56** / common **122** / platform **16**. Heat and capacity remain `double` values with the official Kelvin-based temperature calculation. The three handler interfaces, both capacitor implementations, and two holder types are unchanged from the official baseline. This is a domain-storage and routing slice, not a Fabric capability or transaction bridge.

## Platform adaptations

`IHeatCapacitor` extends the project-owned registry-aware `mekanism.api.INBTSerializable<CompoundTag>` instead of NeoForge's interface. Its public methods and serialization signatures are unchanged.

`BasicHeatCapacitor` reads its two double tags directly instead of admitting the broad `NBTUtils` dependency closure. Reads remain strict: only `TAG_DOUBLE` values for `stored` and `heat_capacity` are accepted. Deserialization still avoids listener notification, preserves queued heat, and updates capacity without changing stored heat.

`HeatAPI.getAmbientTemp(LevelReader, BlockPos)` calls vanilla's position-adjusted biome-temperature method. Mekanism's NeoForge build makes this private vanilla method public through `META-INF/accesstransformer.cfg`; the Fabric build mirrors that single access change in `fabric/src/main/resources/mekanism.accesswidener`. It does not substitute `Biome.getBaseTemperature()`, which would lose height and frozen-biome adjustment semantics.

## Preserved behavior

- `BasicHeatCapacitor` rejects heat capacity and inverse conduction below one; inverse insulation remains unrestricted;
- `VariableHeatCapacitor` intentionally bypasses those factory checks, samples both coefficient suppliers during construction, then reads them live; dynamic inverse conduction is `Math.max(1, supplied)` while inverse insulation is unchanged;
- stored heat initializes lazily to capacity multiplied by ambient temperature;
- `handleHeat` batches transfers until `update`, notifying once when the aggregate magnitude exceeds `HeatAPI.EPSILON`;
- smaller aggregates remain queued rather than being discarded;
- direct heat assignment is not clamped and suppresses no-op notifications;
- capacity changes can either preserve temperature by adding ambient heat or preserve the current heat value;
- packet capacity updates do not notify;
- NBT always writes `stored` and `heat_capacity` as doubles; pending heat is neither serialized nor cleared by a load.

## Slot descriptor boundary

The shared `ISlotInfo`/`BaseSlotInfo` policy and concrete `HeatSlotInfo` remain byte-identical to `4646f509` (alongside the same-batch `EnergySlotInfo`). Constructor flags and exact input-list identity are preserved. Unlike the energy descriptor, heat `isEmpty()` is unconditionally false for both empty and populated capacitor lists and never consults capacitor heat state. One of the five focused slot tests binds this heat-specific contract; the four-class exact JAR family has no startup marker.

## Task 029 conductor and transport/tank tier metadata

Task 029 adds metadata-only `ConductorTier` alongside `CableTier`, `ChemicalTankTier`, `FluidTankTier`, `PipeTier`, `TransporterTier`, and `TubeTier`. `ConductorTier` retains exact BASIC→ULTIMATE tuples: inverse conduction `5`, heat capacity `1`, insulation `10`/`400`/`8_000`/`100_000`, and RGBA `(0.2, 0.2, 0.2, 1)` (integer channels `51/51/51/255`). Its only oracle adaptation is dropping `EnumUtils` and iterating its own `values()` in `get(BaseTier)`, preserving every mapping and the `BASIC` fallback; the other four lookup enums make that same exact change. Separately, the four NeoForge fluid-constant users only replace each compile-time `FluidType.BUCKET_VOLUME` with exact integer literal `1_000` and remove that import, preserving every current value and `int`/`long` expression. This `1_000` is Mekanism's existing mB/config metadata bucket basis, not Fabric Transfer API's 81,000-droplet transaction unit; no transfer/storage conversion is activated.

Seven new methods make `TierMetadataTest` exactly twelve methods and prove complete enum/BaseTier/base/current tuples, lookup/name/color contracts, and loaded signed/large/fractional cached references with live sets and reflective `finally` restoration. Packaging admits exactly 13 direct tier classes, each once, without nested/test/helper/fixture leakage or a startup marker.

## Deliberately deferred

`ConductorTier` metadata does not admit conductor blocks or heat transport. This slice still excludes `HeatCapacitorHelper`, `ConfigHeatCapacitorHolder`, every configurable holder/helper, `ConfigInfo`, `DataType`, `TileComponentConfig`, `TileComponentEjector`, chemical/fluid/inventory/proxy slot descriptors, side-configuration execution/ejection, capability exposure, `HeatNetwork`, cables/pipes/tubes/transporters/conductors/tanks and every tile/machine/block/multiblock owner or caller, `TierConfig`/`tiers.toml`, Fabric fluid/chemical storage/conversion/transactions, sync packets or wire serialization, persistence, config/registry lifecycle, transactions, thread ownership, or any external API. The helper currently couples non-config and configurable factories, so it must wait for the real side-configuration closure rather than being admitted with stubs.

## Verification

`fabric/src/test/java/mekanism/api/heat/HeatAPITest.java` covers null-world fallback, the plains baseline, conversion, and both clamp bounds. `fabric/src/test/java/mekanism/api/heat/HeatHandlerTest.java` covers empty/invalid defaults, single and capacity-weighted aggregation/distribution, insulation, standalone base/sided defaults, and dynamic side delegation. `fabric/src/test/java/mekanism/common/capabilities/heat/BasicHeatCapacitorTest.java` covers validation (including baseline non-finite behavior), lazy ambient initialization, batching and epsilon accumulation, notifications, capacity updates, strict NBT types, the `-1` initialization sentinel, and pending-heat load behavior. `VariableHeatCapacitorTest.java` covers defaults, inherited storage/listener behavior, live supplier sampling, exact clamp/non-finite behavior, intentional factory validation bypass, and nullability. `fabric/src/test/java/mekanism/common/capabilities/holder/heat/HeatCapacitorHolderTest.java` covers internal/all-side exposure, filtering, horizontal and vertical live facing, order, duplicates, and list mutability. `fabric/src/test/java/mekanism/common/tile/component/config/slot/SlotInfoTest.java` contributes exactly five descriptor methods, including heat flags/list identity and proof that neither empty nor populated capacitor lists trigger state inspection. `fabric/src/test/java/mekanism/common/tier/TierMetadataTest.java` has exactly twelve methods, including the Task 029 conductor tuple/config-restoration contract.

Required gates:

```bash
./gradlew -p fabric test
./gradlew -p fabric verifyApiSliceNoNeoForge verifyCommonSliceNoNeoForge
./fabric-build.sh
scripts/fabric-packaged-smoke.sh
```

The packaged smoke verifies the nine heat classes and the access widener are present in the remapped JAR, statically requires the slot descriptor package to contain exactly `ISlotInfo.class`, `BaseSlotInfo.class`, `EnergySlotInfo.class`, and `HeatSlotInfo.class`, and requires the direct tier package to contain exactly 13 classes, once each. It then calls the position-adjusted biome-temperature path against the running server world and requires the `MEKANISM_FABRIC_HEAT_PROOF` marker. Slot descriptors have no startup marker; their behavior is exercised by JUnit.
