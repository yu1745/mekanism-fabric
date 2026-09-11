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

## Sequential transfer utilities

The unchanged official long-valued transfer closure is now admitted (API slice count 38, common 32, platform 10):

- `mekanism.api.math.LongTransferUtils`
- `mekanism.api.container.InContainerGetterLong`
- `mekanism.api.container.LongToLongContainerInteraction`

All three are byte-identical to baseline `4646f509`; `docs/API_DELTAS.md` is unchanged because the closure needs no adaptation. This restores Mekanism's sequential generic/energy-container insert/extract helpers. It is **not** the fair-distribution layer (that is `SplitInfo`/`Target`, already admitted) and **not** a Fabric transaction or external Energy API adapter (`Action` is forwarded, no rollback policy is added).

Observable semantics preserved:

- generic callback overlays: `insert(stack, side, action, containerCount, inContainerGetter, insert)` and `extract(amount, side, action, containerCount, extract)`;
- side-to-list supplier overlays: `insert(stack, side, supplier, action, automationType)` and `extract(amount, side, supplier, action, automationType)`;
- explicit list overlays: `insert(stack, action, automationType, containerCount, energyContainers)` and `extract(amount, action, automationType, containerCount, energyContainers)`;
- insertion visits containers that already hold energy first, then the deferred empty containers, each phase in source order, forwarding the current remainder and returning as soon as a remainder is `<= 0` (including the defensive negative-remainder case);
- extraction walks source order, ignores non-positive drains, forwards the shrinking request, accumulates valid drains, and breaks exactly when the request is satisfied;
- the callback single-container path forwards index `0`, the exact `Action`, and the nullable side, and never consults `InContainerGetterLong`; the container paths forward the exact amount/`Action`/`AutomationType`;
- `SIMULATE` forwards the same calculation without mutating storage or notifying listeners; `EXECUTE` mutates;
- true `long` amounts above `Integer.MAX_VALUE` stay lossless.

One observable asymmetry is intentional and tested: the energy-supplier `extract` overload short-circuits only `amount == 0` before the supplier lookup, so a negative amount still reaches the supplier and is then rejected by the explicit overload, which short-circuits `amount <= 0 || containerCount == 0`.

## Caller preconditions

The official source adds no policy, and this slice adds none. Callers must provide:

- nonnegative offers/requests;
- for the explicit overloads, a nonnegative `containerCount` that matches a fresh, consistent `List<IEnergyContainer>` (the loop iterates the whole list once `containerCount > 1`);
- handlers whose insert remainder stays in `[0, offer]` and whose extract result stays in `[0, request]`;
- lookups (`ToIntFunction`/`Function`) that tolerate the nullable side;
- `InContainerGetterLong`/`LongToLongContainerInteraction` results that do not require overflow defense.

Invalid handler returns, mismatched `containerCount`/list size, and overflow remain undefined for callers rather than defended here.

## Deliberately deferred

This is an internal storage slice, not external Fabric energy interoperability. It does not yet include:

- configurable energy holders and machine side configuration;
- block/item/entity lookups;
- Team Reborn Energy API;
- Fabric transaction rollback;
- Joule/FE/Energy conversion policy;
- machines, menus, sync packets, or persistence owners;
- `IStrictEnergyHandler`, `EnergyHandler`, configurable holders/helpers, and every caller of the sequential closure (`ComponentBackedEnergyHandler`, `EnergyRecipeData`).

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

The packaged smoke verifies both energy classes are present in the remapped JAR as before, plus exactly one occurrence of each long-transfer production class, rejecting any other `LongTransferUtils*.class` so test/helper leakage cannot reach the release JAR. Energy behavior is exercised by JUnit rather than by constructing test state during production bootstrap.

`fabric/src/test/java/mekanism/api/math/LongTransferUtilsTest.java` covers the sequential closure with observable event/order/value assertions:

- non-positive and zero-container short circuits, proving which count/supplier/getter/interaction callbacks are and are not invoked (including the supplier-`extract` zero-versus-negative asymmetry);
- single-container forwarding of index, nullable side, `Action`, and `AutomationType`;
- non-empty-first insertion with deferred empty containers in source order, shrinking remainder forwarding, and stopping on `<= 0`/negative remainders;
- source-order extraction that ignores non-positive drains, forwards the shrinking request, accumulates drains, and stops exactly when satisfied;
- both callback-backed and real `IEnergyContainer`/`BasicEnergyContainer` paths, `SIMULATE` versus `EXECUTE` mutation and notification behavior, and true-`long` amounts above `Integer.MAX_VALUE`;
- caller-precondition invariants (nonnegative offers, remainder within `[0, offer]`, extract result within `[0, request]`, matching count and a fresh consistent list) without relying on overflow or mismatched counts.

## Reference boundary

No compatibility-layer code was copied. The later transactional adapter may compare behavior with Kilt commit `9e8f0d67411f10234bf0d864bd151a6dbcef14b4`, especially:

- `upstream/kilt/compat/transfer-api-compat/src/main/kotlin/xyz/bluspring/kilt/compat/transfer/energy/NeoForgeEnergyStorage.kt`

That reference's fixed conversion and integer narrowing are not adopted here; Mekanism needs an explicit, tested conversion policy before external exposure.
