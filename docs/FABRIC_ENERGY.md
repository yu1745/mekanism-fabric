# Fabric energy-container core

## Current slice

The first storage milestone admits the official Mekanism energy-container contract and implementation without machines, configurable holders, external lookups, or a third-party energy API:

- `mekanism.api.energy.IEnergyContainer`
- `mekanism.common.capabilities.energy.BasicEnergyContainer`

The domain remains measured in Mekanism's `long` energy units. `insert` returns the unaccepted remainder; `extract` returns the extracted amount. `Action.SIMULATE` performs the same capacity/rate/policy calculation as execution without mutating storage or notifying listeners.

## Platform adaptations

`IEnergyContainer` now extends the project-owned, registry-aware `mekanism.api.INBTSerializable<CompoundTag>` instead of NeoForge's serialization interface. Its method names, arguments, return values, listener contract, and NBT key remain unchanged. A Javadoc-only NeoForge fluid-handler comparison was replaced with the same behavior described in plain text.

`BasicEnergyContainer` keeps its official factories and automation predicates. Two broad helper dependencies were removed at the Fabric boundary:

- `ConstantPredicates.alwaysTrue()` is replaced by one local cached predicate, avoiding the unrelated chemical/fluid predicate closure.
- `NBTUtils.setLegacyEnergyIfPresent` is implemented locally for the `stored` field, preserving long tags, decimal legacy strings, unsigned overflow clamping, invalid-string reset, and absent-key behavior without importing the broad NBT utility closure.

Guava `Preconditions.checkArgument` was replaced by the equivalent explicit `IllegalArgumentException` for negative direct assignments.

## Preserved behavior

- capacity uses `long` and direct values above capacity clamp;
- negative `setEnergy` fails before mutation;
- listeners run exactly once for each real executed change;
- simulation never calls listeners;
- input containers reject external extraction but permit insertion;
- output containers accept only internal insertion but permit extraction;
- unrestricted containers permit all automation types;
- empty serialization omits `stored`; populated serialization writes a long;
- legacy string energy is loaded with the official unsigned/decimal compatibility behavior.

## Deliberately deferred

This is an internal storage slice, not external Fabric energy interoperability. It does not yet include:

- configurable energy holders and machine side configuration;
- block/item/entity lookups;
- Team Reborn Energy API;
- Fabric transaction rollback;
- Joule/FE/Energy conversion policy;
- machines, menus, sync packets, or persistence owners.

The non-config relative-side holder layer is now available and documented in `FABRIC_HOLDERS.md`. External energy boundaries must still wait for explicit conversion configuration and configurable exposure policy. Fabric API itself does not define a standard energy API.

## Verification

`fabric/src/test/java/mekanism/common/capabilities/energy/BasicEnergyContainerTest.java` covers:

- execute/simulate insertion and extraction, capacity remainder, and listener counts;
- every automation type across input/output/unrestricted/custom policies;
- finite and zero insertion/extraction rate hooks under simulation and execution;
- direct set clamping, no-op notification suppression, and negative rejection;
- non-positive transfer behavior;
- long NBT round trip plus decimal, overflow, invalid, and absent legacy values.

Required gates:

```bash
./gradlew -p fabric test
./gradlew -p fabric verifyApiSliceNoNeoForge verifyCommonSliceNoNeoForge
./fabric-build.sh
scripts/fabric-packaged-smoke.sh
```

The packaged smoke verifies both energy classes are present in the remapped JAR; energy behavior is exercised by JUnit rather than by constructing test state during production bootstrap.

## Reference boundary

No compatibility-layer code was copied. The later transactional adapter may compare behavior with Kilt commit `9e8f0d67411f10234bf0d864bd151a6dbcef14b4`, especially:

- `upstream/kilt/compat/transfer-api-compat/src/main/kotlin/xyz/bluspring/kilt/compat/transfer/energy/NeoForgeEnergyStorage.kt`

That reference's fixed conversion and integer narrowing are not adopted here; Mekanism needs an explicit, tested conversion policy before external exposure.
