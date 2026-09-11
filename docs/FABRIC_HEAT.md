# Fabric heat-container core

## Current slice

The current heat milestone admits the official internal heat contract, implementation, standard handler aggregation, and non-config relative-side holder:

- `mekanism.api.heat.HeatAPI`
- `mekanism.api.heat.IHeatCapacitor`
- `mekanism.api.heat.IHeatHandler`
- `mekanism.api.heat.ISidedHeatHandler`
- `mekanism.api.heat.IMekanismHeatHandler`
- `mekanism.common.capabilities.heat.BasicHeatCapacitor`
- `mekanism.common.capabilities.holder.heat.IHeatCapacitorHolder`
- `mekanism.common.capabilities.holder.heat.HeatCapacitorHolder`

Heat and capacity remain `double` values with the official Kelvin-based temperature calculation. The three handler interfaces and two holder types are unchanged from the official baseline. This is a domain-storage and routing slice, not a Fabric capability or transaction bridge.

## Platform adaptations

`IHeatCapacitor` extends the project-owned registry-aware `mekanism.api.INBTSerializable<CompoundTag>` instead of NeoForge's interface. Its public methods and serialization signatures are unchanged.

`BasicHeatCapacitor` reads its two double tags directly instead of admitting the broad `NBTUtils` dependency closure. Reads remain strict: only `TAG_DOUBLE` values for `stored` and `heat_capacity` are accepted. Deserialization still avoids listener notification, preserves queued heat, and updates capacity without changing stored heat.

`HeatAPI.getAmbientTemp(LevelReader, BlockPos)` calls vanilla's position-adjusted biome-temperature method. Mekanism's NeoForge build makes this private vanilla method public through `META-INF/accesstransformer.cfg`; the Fabric build mirrors that single access change in `fabric/src/main/resources/mekanism.accesswidener`. It does not substitute `Biome.getBaseTemperature()`, which would lose height and frozen-biome adjustment semantics.

## Preserved behavior

- heat capacity and inverse conduction below one are rejected; inverse insulation remains unrestricted;
- stored heat initializes lazily to capacity multiplied by ambient temperature;
- `handleHeat` batches transfers until `update`, notifying once when the aggregate magnitude exceeds `HeatAPI.EPSILON`;
- smaller aggregates remain queued rather than being discarded;
- direct heat assignment is not clamped and suppresses no-op notifications;
- capacity changes can either preserve temperature by adding ambient heat or preserve the current heat value;
- packet capacity updates do not notify;
- NBT always writes `stored` and `heat_capacity` as doubles; pending heat is neither serialized nor cleared by a load.

## Deliberately deferred

This slice does not include `HeatCapacitorHelper`, `ConfigHeatCapacitorHolder`, `ISideConfiguration`, `HeatNetwork`, conductors, machine or multiblock owners, sync packets, persistence owners, or any external API. The helper currently couples non-config and configurable factories, so it must wait for the real side-configuration closure rather than being admitted with stubs.

## Verification

`fabric/src/test/java/mekanism/api/heat/HeatAPITest.java` covers null-world fallback, the plains baseline, conversion, and both clamp bounds. `fabric/src/test/java/mekanism/api/heat/HeatHandlerTest.java` covers empty/invalid defaults, single and capacity-weighted aggregation/distribution, insulation, standalone base/sided defaults, and dynamic side delegation. `fabric/src/test/java/mekanism/common/capabilities/heat/BasicHeatCapacitorTest.java` covers validation (including baseline non-finite behavior), lazy ambient initialization, batching and epsilon accumulation, notifications, capacity updates, strict NBT types, the `-1` initialization sentinel, and pending-heat load behavior. `fabric/src/test/java/mekanism/common/capabilities/holder/heat/HeatCapacitorHolderTest.java` covers internal/all-side exposure, filtering, horizontal and vertical live facing, order, duplicates, and list mutability.

Required gates:

```bash
./gradlew -p fabric test
./gradlew -p fabric verifyApiSliceNoNeoForge verifyCommonSliceNoNeoForge
./fabric-build.sh
scripts/fabric-packaged-smoke.sh
```

The packaged smoke verifies the eight heat classes and the access widener are present in the remapped JAR, then calls the position-adjusted biome-temperature path against the running server world and requires the `MEKANISM_FABRIC_HEAT_PROOF` marker. Storage behavior is exercised by JUnit.
