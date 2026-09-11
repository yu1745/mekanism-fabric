# Fabric energy-container core

## Current slice

The first storage milestone admits the official Mekanism energy-container contract and implementation without machines, configurable holders, external lookups, or a third-party energy API:

- `mekanism.api.energy.IEnergyContainer`
- `mekanism.api.energy.IStrictEnergyHandler`
- `mekanism.api.energy.ISidedStrictEnergyHandler`
- `mekanism.api.energy.IMekanismStrictEnergyHandler`
- `mekanism.common.capabilities.energy.BasicEnergyContainer`
- `mekanism.common.capabilities.SimpleDynamicHandler`
- `mekanism.common.capabilities.DynamicHandler`
- `mekanism.common.capabilities.energy.DynamicStrictEnergyHandler`

The slice count is API 42 / common 70 / platform 10. The common count includes the seven byte-identical `4646f509` color/effect/spatial foundations documented in `FABRIC_PORT.md`; their exact JAR families and bounded tests do not admit any energy caller, owner, external API, lifecycle, networking, persistence, registry, or thread-safety guarantee. It also admits official
`mekanism.common.lib.LastEnergyTracker` byte-identically to `4646f509` as a
platform-neutral per-game-time accounting closure. Same-time calls accumulate a current
bucket while the visible last bucket remains unchanged; every different timestamp—including
skipped or decreasing values—publishes that current bucket and starts another. Tick zero is
the initial current bucket, amounts use raw `long` arithmetic without validation/clamping,
and `setLastEnergyReceived` changes only the visible client-sync value until the next
timestamp transition publishes the accumulated bucket. The packaged direct `mekanism/common/lib` family now contains exactly `Color`, `Color$ColorFunction`, `WildcardMatcher`, and `LastEnergyTracker`, once each, with no startup marker. Its sole production caller, `TileEntityMekanism`, plus world
tick/client synchronization, network ownership and persistence, machines/networks, external
energy APIs, conversion/lookup, and Fabric transaction integration remain explicitly deferred.

The domain remains measured in Mekanism's `long` energy units. `insert` returns the unaccepted remainder; `extract` returns the extracted amount. `Action.SIMULATE` performs the same capacity/rate/policy calculation as execution without mutating storage or notifying listeners.

## Platform adaptations

`IEnergyContainer` now extends the project-owned, registry-aware `mekanism.api.INBTSerializable<CompoundTag>` instead of NeoForge's serialization interface. Its method names, arguments, return values, listener contract, and NBT key remain unchanged. A Javadoc-only NeoForge fluid-handler comparison was replaced with the same behavior described in plain text.

`IStrictEnergyHandler` and `ISidedStrictEnergyHandler` remove only their two NeoForge fluid imports and replace the Javadoc-only `fill` links with platform-neutral prose preserving the exact contrast: fluid fill reports accepted amount, while Mekanism insertion reports the unaccepted remainder. This has no compatibility impact; executable code, signatures, annotations, control flow, and all other Javadoc meaning are unchanged. `IMekanismStrictEnergyHandler` is byte-identical to baseline `4646f509`.

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

## Strict handler defaults

The three-layer handler closure connects the admitted sequential utility to Mekanism's indexed, sided, and container-backed contracts. `IStrictEnergyHandler` aggregates with the nullable internal context: insertion visits non-empty indexed containers before deferred empties, extraction uses source order, and the exact `Action`, shrinking remainder/request, and result are preserved. `ISidedStrictEnergyHandler` routes every unsided bridge through `getEnergySideFor()` and forwards the exact nullable side and `Action` through sided per-container and aggregate calls.

`IMekanismStrictEnergyHandler` defaults expose the side-specific list supplied by the implementer. Valid indexes delegate to their `IEnergyContainer`; invalid indexes return zero/null/remainder defaults or perform no mutation; direct setting clamps negative values to zero. A null side maps to `AutomationType.INTERNAL`, and a non-null side maps to `EXTERNAL`. Aggregate insert/extract obtains the side-specific list once per operation, then applies the unchanged sequential semantics under both `SIMULATE` and `EXECUTE`, including true-`long` values. `canHandleEnergy()` defaults to true; an implementation returning false remains responsible for returning an empty list from `getEnergyContainers`, exactly as the official contract states.

The three dynamic handler sources are byte-identical to `4646f509`. Insert and extract predicates are independent and receive the exact nullable side. Denial returns the original insertion amount or zero extraction without consulting the supplier; allowance delegates through the defaults with exact index, side, amount, and `Action`, and the supplier receives that side. Direct container lookup forwards null/non-null unchanged, while `onContentsChanged` forwards once to a present listener and is harmless when absent.

## Permission proxy closure

`ProxiedHolder`, `ProxyHandler`, `ProxyStrictEnergyHandler`, and `ProxyHeatHandler` are admitted byte-identically to `4646f509`. A null side is intrinsically read-only: strict-energy direct set and indexed/aggregate insert/extract, plus indexed/aggregate heat mutation, are suppressed while all getters still receive exact null. For a non-null side, holder insert and extract predicates gate their operation families independently; denial returns the exact offered insertion amount or zero extraction, while allowance forwards exact side, container, amount, `Action`, and handler result. Heat reads and indexed/aggregate `handleHeat` likewise forward exactly. This is only a permission/forwarding wrapper: all owners/callers, capability registration/lookup/exposure, external Energy API/transactions/conversion, side configuration/ejection, machines/transmitters/networks, config lifecycle, persistence/network serialization, and broader Fabric integration remain deferred.

## Sequential transfer utilities

The unchanged official long-valued transfer closure is admitted as part of the current API/common/platform slice count 42 / 74 / 10:

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

## Internal energy support subclosures

`ProxiedEnergyContainerHolder`, `EnergyAcceptorTarget`, `EnergySaveTarget`, and `LaserDissipationHandler` are admitted unchanged and remain byte-identical to official baseline `4646f509`. Six Fabric-local tests bind three narrow contracts: the proxied holder keeps independent insert/extract predicates and forwards exact nullable sides while returning the function's list identity; the acceptor bridge uses `EmitUtils`, forwards exact `SIMULATE`/`EXECUTE`, preserves true-`long` order/results, computes acceptance as offer minus remainder, and accounts execution shortfall from the actual remainder; save handlers simulate remaining capacity, accept fairly, defer publication until `save()`, publish saturated/zero values repeatedly, clamp aggregate stored totals, and have delegates snapshot max capacity, call `setEnergy`, and read current storage. The immutable laser handler retains 0, 1, ordinary values, validates each percentage independently (including infinities), and intentionally preserves raw-comparison NaN acceptance.

These classes add no wire protocol or adapter. All owners/callers, transmitter/dynamic-network lifecycle, actual emission ticks, capability registration/lookup/exposure, Fabric Energy API/conversion/transactions/rollback, side configuration/ejection, machine/tile/item integration, laser armor/item attachment registration, damage handling, networking/serialization/persistence/threading remain deferred.

## Long-valued tier metadata closure

`EnergyCubeTier`, `InductionCellTier`, and `InductionProviderTier` are admitted byte-identically to `4646f509`; `CachedLongValue` differs only in replacing the two NeoForge builder/value imports with `MekanismConfigSpec` types. The Fabric spec now preserves exact in-range signed-long TOML integer literals through load/correction/save, including values above `2^53`, `Long.MIN_VALUE`, and `Long.MAX_VALUE`, while a decimal/hex/octal/binary lexical preflight rejects overflow before Night Config can wrap it. Its explicit normalizer checks Float/Double against the exact runtime IEEE value and rejects fractions, non-finite values, overflow (including floating `2^63`), and configured range misses without rounding, truncation, saturation, or lossy comparisons.

This closure is only config schema/value support and energy-tier metadata. It does not activate `tiers.toml`; `TierConfig`, `MekanismConfig` and other real config files, other cached value kinds/tier families, config sync/UI/watchers/world restart, owners/callers, machines/blocks/items, capability registration, external energy/transactions, networking/serialization/persistence/lifecycle/threading remain deferred.

## Deliberately deferred

This is an internal storage slice, not external Fabric energy interoperability. It does not yet include:

- configurable energy holders and machine side configuration;
- block/item/entity lookups;
- Team Reborn Energy API;
- Fabric transaction rollback;
- Joule/FE/Energy conversion policy;
- machines, menus, sync packets, or persistence owners;
- `EnergyContainerHelper` and its static dependency `ConfigEnergyContainerHolder`;
- side configuration/ejector, Universal Cable, machines, network/persistence owners, and all callers (including `ComponentBackedEnergyHandler` and `EnergyRecipeData`).

There is no official `mekanism.common.capabilities.energy.EnergyHandler` source in baseline `4646f509`; older generic/concrete `EnergyHandler` deferral wording was stale naming. The helper remains unsplit because its official class statically reaches the config holder and broad tile side-config/ejector/chemical/fluid/inventory owner closure.

The non-config relative-side holder layer is now available and documented in `FABRIC_HOLDERS.md`. External energy boundaries must still wait for explicit conversion configuration and configurable exposure policy. Fabric API itself does not define a standard energy API.

## Verification

`fabric/src/test/java/mekanism/api/energy/{IStrictEnergyHandlerTest,ISidedStrictEnergyHandlerTest,IMekanismStrictEnergyHandlerTest}.java` covers indexed delegation/order, every unsided-to-sided bridge, exact nullable side/`Action`, invalid indexes, negative-set clamping, null/internal versus non-null/external automation, one-list-lookup aggregates, simulation/execution, and a value above `Integer.MAX_VALUE`. The tracking fixtures are limited to delegation identity/order; real `BasicEnergyContainer` provides storage and mutation behavior.

`fabric/src/test/java/mekanism/common/capabilities/energy/DynamicStrictEnergyHandlerTest.java` compactly covers denied policy short-circuiting, independent predicates, exact default-method/supplier side and action bridging, direct nullable lookup, and optional listener forwarding without repeating transfer matrices.

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

The packaged smoke verifies the root capability package is exactly `SimpleDynamicHandler.class` and `DynamicHandler.class`, and the exact energy implementation package is `BasicEnergyContainer.class` plus `DynamicStrictEnergyHandler.class`, each once. It retains the exact energy API and long-transfer contracts. Handler behavior is exercised by JUnit rather than production bootstrap; there is no dynamic-handler startup marker.

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
