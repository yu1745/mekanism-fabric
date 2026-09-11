# Fabric energy-container core

## Current slice

The current storage milestone admits the official Mekanism energy-container contract and implementation plus the first bounded Team Reborn Energy boundary, without machines, configurable holders, or registered providers:

- `mekanism.api.energy.IEnergyContainer`
- `mekanism.api.energy.IEnergyConversion`
- `mekanism.api.energy.IStrictEnergyHandler`
- `mekanism.api.energy.ISidedStrictEnergyHandler`
- `mekanism.api.energy.IMekanismStrictEnergyHandler`
- `mekanism.common.capabilities.energy.BasicEnergyContainer`
- `mekanism.common.capabilities.SimpleDynamicHandler`
- `mekanism.common.capabilities.DynamicHandler`
- `mekanism.common.capabilities.energy.DynamicStrictEnergyHandler`
- `mekanism.fabric.energy.FabricEnergyConversions`
- `mekanism.fabric.energy.FabricStrictEnergyHandler`
- `mekanism.fabric.energy.FabricEnergyStorage`
- `mekanism.fabric.energy.FabricEnergyLookup`

The current global slice count is API 43 / common 88 / platform 14. The common count includes the seven byte-identical `4646f509` color/effect/spatial foundations documented in `FABRIC_PORT.md`; their exact JAR families and bounded tests do not admit any energy caller, owner, external API, lifecycle, networking, persistence, registry, or thread-safety guarantee. It also admits official
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

`ProxiedHolder`, `ProxyHandler`, `ProxyStrictEnergyHandler`, and `ProxyHeatHandler` are admitted byte-identically to `4646f509`. A null side is intrinsically read-only: strict-energy direct set and indexed/aggregate insert/extract, plus indexed/aggregate heat mutation, are suppressed while all getters still receive exact null. For a non-null side, holder insert and extract predicates gate their operation families independently; denial returns the exact offered insertion amount or zero extraction, while allowance forwards exact side, container, amount, `Action`, and handler result. Heat reads and indexed/aggregate `handleHeat` likewise forward exactly. This is a permission/forwarding wrapper. Task 023 now composes `ProxyStrictEnergyHandler` in its narrow physical-side exposure factory, but actual owner/provider registration, side configuration/ejection, machines/transmitters/networks, config lifecycle, persistence/network serialization, and broader Fabric integration remain deferred.

## Sequential transfer utilities

The unchanged official long-valued transfer closure was admitted when the API/common/platform slice count was 42 / 70 / 10:

- `mekanism.api.math.LongTransferUtils`
- `mekanism.api.container.InContainerGetterLong`
- `mekanism.api.container.LongToLongContainerInteraction`

All three are byte-identical to baseline `4646f509`; that closure required no API-delta entry when admitted. This restores Mekanism's sequential generic/energy-container insert/extract helpers. It is **not** the fair-distribution layer (that is `SplitInfo`/`Target`, already admitted) and **not** a Fabric transaction or external Energy API adapter (`Action` is forwarded, no rollback policy is added).

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

## Energy/heat slot descriptor closure

`ISlotInfo`, `BaseSlotInfo`, `EnergySlotInfo`, and `HeatSlotInfo` are admitted byte-identically to official baseline `4646f509`. The energy descriptor preserves exact varargs order or list identity, observes later list membership and contained-state changes, scans emptiness in source order, and stops exactly at the first non-empty container. Shared base policy preserves both flags and defines enabled as their exact OR. The heat descriptor preserves flags/list identity but always returns false from `isEmpty()` without consulting capacitor state. Five focused tests bind those contracts; static packaging restricts the slot package to exactly the four corresponding production classes, once each, with no startup marker or helper leakage.

This is descriptor metadata only. `ConfigInfo`, `DataType`, `TileComponentConfig`, `TileComponentEjector`, configurable holders/helpers, chemical/fluid/inventory/proxy slot descriptors, every tile/machine/block caller, capability exposure, side-configuration execution, ejection, persistence, networking/wire serialization, config/registry lifecycle, transactions, and thread ownership remain deferred; no broader integration is claimed.

## Long-valued tier metadata closure

`EnergyCubeTier`, `InductionCellTier`, and `InductionProviderTier` are admitted byte-identically to `4646f509`; `CachedLongValue` differs only in replacing the two NeoForge builder/value imports with `MekanismConfigSpec` types. The Fabric spec now preserves exact in-range signed-long TOML integer literals through load/correction/save, including values above `2^53`, `Long.MIN_VALUE`, and `Long.MAX_VALUE`, while a decimal/hex/octal/binary lexical preflight rejects overflow before Night Config can wrap it. Its explicit normalizer checks Float/Double against the exact runtime IEEE value and rejects fractions, non-finite values, overflow (including floating `2^63`), and configured range misses without rounding, truncation, saturation, or lossy comparisons.

This closure is only config schema/value support and energy-tier metadata. It does not activate `tiers.toml`; `TierConfig`, `MekanismConfig` and other real config files, other cached value kinds/tier families, config sync/UI/watchers/world restart, owners/callers, machines/blocks/items, capability registration, external energy/transactions, networking/serialization/persistence/lifecycle/threading remain deferred.

## Task 023 Team Reborn Energy boundary

The Fabric build pins external mod `teamreborn:energy:4.1.0` and resolves Fabric Transfer API `5.4.4`; Energy is a runtime metadata dependency and is not nested in the Mekanism JAR. The official `IEnergyConversion` is admitted byte-identically to oracle `4646f509`. Every adapter receives the conversion object from its caller: no `2.5` constant, Forge Energy equivalence, or hardcoded FE config activation enters production.

`FabricEnergyConversions` rejects disabled, NaN, infinite, zero, and negative rates. It uses official `convertFrom`/`convertTo` rounding and saturation, derives whole-unit candidates from the conversion's canonical decimal ratio, and accepts a pair only when both conversions round-trip exactly. Thus 2.5 uses 2 external units ↔ 5 J, 0.4 uses 5 external units ↔ 2 J, and smaller partial values transfer zero instead of creating or voiding energy. Large candidates that exceed the official double's exact distinguishability are reduced until a verified pair is found; no precision beyond `IEnergyConversion`'s double contract is claimed.

`FabricStrictEnergyHandler` exposes one indexed container around an `EnergyStorage`, keeps official invalid-index and unsupported-set defaults, respects support flags, and remains true-`long`. Each simulation runs in an aborted Fabric transaction. Execution first probes when quantization/partial acceptance is relevant, then commits only an exact fixed-point acceptance. Because this simulation-style API has no transaction parameter, the bridge uses Transfer API's explicitly compatibility-oriented `Transaction.getCurrentUnsafe()` and opens a nested transaction when a parent exists; a later parent abort therefore restores the external storage. Simulation/execution mismatch aborts the mutation and reports no transfer.

`FabricEnergyStorage` is a `SnapshotParticipant<long[]>` over one stable `IStrictEnergyHandler`. Every attempted mutation opens a child transaction, snapshots every fixed container before Mekanism `EXECUTE`, and commits only an exactly reportable result. Repeated/nested commits merge into the parent snapshot; nested abort and later outer abort restore every container. Amount and capacity aggregate with saturation at `Long.MAX_VALUE`; negative Team API requests throw through `StoragePreconditions`. The container count captured by construction is checked before queries, mutations, snapshot creation, and restore. Owners must retain one adapter per stable handler/physical side.

Rollback necessarily calls `IStrictEnergyHandler.setEnergy` for every captured container. Mekanism listeners, dirty/save work, or synchronization performed immediately during the original execute or restore are therefore observable before transaction outcome and cannot be undone by this adapter. Tests explicitly observe these calls; this boundary does not pretend simulation is side-effect-free at those owner layers. Future owners must either tolerate/idempotently coalesce them or defer irreversible effects themselves.

`FabricEnergyLookup` is the sole lookup/factory seam. Public lookup and exposure reject null/internal sides and unusable conversions. Exposure also rejects `canHandleEnergy() == false` and empty physical sides, and builds a `ProxyStrictEnergyHandler` so the exact side and live `IHolder` insertion/extraction policy are consulted on each operation. A same-project `FabricEnergyStorage` is unwrapped only when the identical conversion object is supplied, avoiding recursive adapters; another conversion remains wrapped. No global lookup registration is performed here.

Exactly eight Fabric-local JUnit methods cover the official conversion oracle, shared fixed points, strict queries/transfers/nested transaction rollback and unstable acceptance, reverse adapter preconditions/saturation/fractional returns/full snapshots/listener effects/count stability, and direct lookup/exposure/self-unwrapping.

## Deliberately deferred

This first external boundary does not yet include:

- `ConfigHolder`, `ConfigInfo`, `DataType`, `ISideConfiguration`, configurable holders/helpers, or hardcoded FE configuration activation;
- `TileComponentConfig`, `TileComponentEjector`, Universal Cable, cables/networks/emission, or machines;
- actual block/item/entity provider registration, owner caching/invalidation, or world/chunk lifecycle;
- item, fluid, chemical, or heat adapters;
- persistence, packets/client sync, menus, recipes, or thread-safety guarantees;
- `EnergyContainerHelper` and its static dependency `ConfigEnergyContainerHolder`;
- all callers including `ComponentBackedEnergyHandler` and `EnergyRecipeData`.

There is no official `mekanism.common.capabilities.energy.EnergyHandler` source in baseline `4646f509`; older generic/concrete `EnergyHandler` deferral wording was stale naming. The helper remains unsplit because its official class statically reaches the config holder and broad tile side-config/ejector/chemical/fluid/inventory owner closure.

The non-config relative-side holder layer is documented in `FABRIC_HOLDERS.md`. Actual owners still require explicit conversion configuration and configurable exposure policy. Fabric API itself does not define energy, so this bounded integration depends on Team Reborn Energy 4.1.0.

## Verification

`fabric/src/test/java/mekanism/api/energy/{IStrictEnergyHandlerTest,ISidedStrictEnergyHandlerTest,IMekanismStrictEnergyHandlerTest}.java` covers indexed delegation/order, every unsided-to-sided bridge, exact nullable side/`Action`, invalid indexes, negative-set clamping, null/internal versus non-null/external automation, one-list-lookup aggregates, simulation/execution, and a value above `Integer.MAX_VALUE`. The tracking fixtures are limited to delegation identity/order; real `BasicEnergyContainer` provides storage and mutation behavior.

`fabric/src/test/java/mekanism/common/capabilities/energy/DynamicStrictEnergyHandlerTest.java` compactly covers denied policy short-circuiting, independent predicates, exact default-method/supplier side and action bridging, direct nullable lookup, and optional listener forwarding without repeating transfer matrices.

`fabric/src/test/java/mekanism/common/tile/component/config/slot/SlotInfoTest.java` has exactly five methods covering all flag combinations/OR enablement, energy varargs order and zero/all-empty behavior, source-order first-non-empty short-circuit, live list identity/membership/state, and heat's unconditional non-empty result without reading capacitor state.

`fabric/src/test/java/mekanism/common/capabilities/energy/BasicEnergyContainerTest.java` covers:

- execute/simulate insertion and extraction, capacity remainder, and listener counts;
- every automation type across input/output/unrestricted/custom policies;
- finite and zero insertion/extraction rate hooks under simulation and execution;
- direct set clamping, no-op notification suppression, and negative rejection;
- non-positive transfer behavior;
- long NBT round trip plus decimal, overflow, invalid, and absent legacy values.

Task 023's exact eight methods are in `IEnergyConversionTest`, `FabricEnergyConversionsTest`, `FabricStrictEnergyHandlerTest`, `FabricEnergyStorageTest`, and `FabricEnergyLookupTest`; they assert transaction return values and underlying raw states.

Required gates:

```bash
./gradlew -p fabric test
./gradlew -p fabric verifyApiSliceNoNeoForge verifyCommonSliceNoNeoForge verifyPlatformSliceNoNeoForge
./fabric-build.sh
scripts/fabric-packaged-smoke.sh
```

The packaged smoke verifies the root capability package is exactly `SimpleDynamicHandler.class` and `DynamicHandler.class`, the exact energy implementation package is `BasicEnergyContainer.class` plus `DynamicStrictEnergyHandler.class`, and the exact slot descriptor package is `ISlotInfo.class`, `BaseSlotInfo.class`, `EnergySlotInfo.class`, and `HeatSlotInfo.class`, each once. The energy API family is exactly the current four classes plus `IEnergyConversion.class`; `mekanism/fabric/energy/` is exactly the four Task 023 classes, with no nested/helper/test leakage. The script rejects `team/reborn/**` inside Mekanism, validates and installs external Energy 4.1.0, and requires both mod IDs in the server log before `Done`. Transaction behavior remains JUnit evidence; there is no fabricated startup transfer marker.

`fabric/src/test/java/mekanism/api/math/LongTransferUtilsTest.java` covers the sequential closure with observable event/order/value assertions:

- non-positive and zero-container short circuits, proving which count/supplier/getter/interaction callbacks are and are not invoked (including the supplier-`extract` zero-versus-negative asymmetry);
- single-container forwarding of index, nullable side, `Action`, and `AutomationType`;
- non-empty-first insertion with deferred empty containers in source order, shrinking remainder forwarding, and stopping on `<= 0`/negative remainders;
- source-order extraction that ignores non-positive drains, forwards the shrinking request, accumulates drains, and stops exactly when satisfied;
- both callback-backed and real `IEnergyContainer`/`BasicEnergyContainer` paths, `SIMULATE` versus `EXECUTE` mutation and notification behavior, and true-`long` amounts above `Integer.MAX_VALUE`;
- caller-precondition invariants (nonnegative offers, remainder within `[0, offer]`, extract result within `[0, request]`, matching count and a fresh consistent list) without relying on overflow or mismatched counts.

## Reference boundary

No Kilt, Connector, or Team API source was copied. Team Reborn Energy 4.1.0 and resolved Transfer API 5.4.4 were inspected for the `EnergyStorage`, `TransactionContext`, `Transaction.getCurrentUnsafe`/nested transaction, `SnapshotParticipant`, and `StoragePreconditions` contracts. Kilt's fixed conversion and integer narrowing were not adopted; the implementation is an independent Mekanism-specific, caller-conversion, true-`long` adapter.
