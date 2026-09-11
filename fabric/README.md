# Fabric bootstrap build

This is an independent Gradle/Loom build for the first Java Fabric migration
stage. It compiles explicit, reviewed allowlists from `fabric/src/main`,
`../src/api/java`, and `../src/main/java`; all other upstream Java sources remain
excluded. It uses Minecraft 1.21.1, Java 21, and Mojang official mappings.

## Audited source slices

`api-slice.txt` is the auditable allowlist of API source paths, relative to
`src/api/java`. The Fabric source set reads that file directly, so the listed
sources retain their original `mekanism.api.*` names without being copied or
modified. The allowlist contains **43 current source files**: **42 official baseline
sources** plus the project-owned registry-aware `INBTSerializable` NBT contract.
Intentional API adaptations include that contract, `Action`'s removal of two
NeoForge `FluidAction` conversion methods, and the Javadoc-only strict-handler
fluid-import/link cleanup; `IMekanismStrictEnergyHandler` remains byte-identical.
See `../docs/API_DELTAS.md`. JetBrains annotations and JSR-305 remain `compileOnly`
and are not bundled in the mod.

`common-slice.txt` contains **88 official source files**, and
`platform-slice.txt` contains **14 project-owned Fabric sources**. Seven spatial/color/effect foundations (`Color`, `CustomEffect`, `Pos3D`, `Quaternion`, `Range3D`, `MultipartUtils`, and `StatUtils`) remain byte-identical to `4646f509`. Twelve Java 21 tests bound channel/HSV/compositing/statistics, effect state, vector/range/rotation/quaternion state, and direct multipart ray selection. Packaging permits exactly four direct common-lib classes, one effect class, three math classes, the three `MultipartUtils` classes, and one `StatUtils` class. This admits no caller or owner: renderers/particles/GUI/recipe viewers/client lifecycle, transmitters/multipart blocks/gear, machines/block types/entities, packet tracking/network ranges, SPS/multiblock, additions/generators, external energy, world/chunk lifecycle, registries/config/persistence all remain deferred; there is no rendering integration, entity/player lifecycle, block registration, networking, thread-safety, or collision-owner guarantee. Together with
the **43-file API slice**, they form bounded registry/GameEvent, TOML
startup-config, configuration-handshake, internal energy storage/holder, internal
heat storage/holder, wildcard-matching, fair-distribution, chunk-coordinate,
lambda-metafactory, and vanilla-registry utility closures. The unchanged
`WildcardMatcher` retains official recursive, UTF-16 `char` matching semantics;
QIO, miner, Finder, and other consumers remain future work. The byte-identical
`LastEnergyTracker` is a platform-neutral game-time bucket: same-time receives accumulate
without changing the published last bucket, every differing (including skipped or decreasing)
timestamp publishes the current bucket, and its setter changes only the visible client-sync
value until the next transition. The direct common-lib JAR package contains it and `WildcardMatcher` alongside the exact new `Color` pair, once each, with no startup marker. `TileEntityMekanism`, world-tick/client
synchronization and network/persistence owners, machines/networks, and any connection from
this utility to the Task 023 energy boundary remain deferred. The unchanged
distribution closure preserves the separate `int`/`long` split arithmetic,
rebalancing, remainder order, lying-handler fallback, collection order, and
`Target` handler contract. The exact 18-file official root-test oracle is
compiled through an explicit path allowlist (including the two unchanged collection tests and three jqwik
properties at 100,000 tries), with separate Fabric boundary coverage.
Callers must provide nonnegative offers, a fresh/non-reused `Target`, and handlers
whose simulation/acceptance and `SplitInfo.send` accounting obey the documented
contract; the official implementation intentionally adds no defensive behavior
for contract violations.

Nine registration/domain metadata foundations (`BinTier`, `EnergyCubeTier`, `FactoryTier`, `InductionCellTier`, `InductionProviderTier`, `QIODriveTier`, `IResource`, `MiscResource`, and `BlockResourceInfo`) remain byte-identical to `4646f509`. Eight Fabric-local tests bind exact capacity/output/process/type/count/BaseTier/name tables (including creative signed maxima and QIO `16_000_000_000L`), real cached config overrides with enum references restored in `finally`, complete resource suffix/accessor tables, and property construction. Exact package-family gates allow only six tier and three resource classes, once each. `BlockResourceInfo` proves property construction only. `PrimaryResource`, `ResourceType`, ore types/config, tags/datagen/registration, block/item implementations, factories/bins/QIO owners, and broader lifecycle remain deferred.

`CachedLongValue` differs from `4646f509` only by replacing its NeoForge `Builder` and `ConfigValue` imports with `MekanismConfigSpec` types. Two focused tests bind exact values above `2^53`, lazy/default/current/set/supplier behavior, changed/unchanged invalidation and unload behavior, plus helper path/range/translation/comment semantics. The Fabric long schema preserves in-range TOML integer literals over the full signed range and lexically rejects decimal/hex/octal/binary overflow before Night Config can wrap it. Programmatic Float/Double inputs are checked against their exact runtime IEEE values; only explicitly supported exact integral, representable, in-range numbers are accepted, while fractions, NaN/infinity, overflow (including `2^63`) and range misses are rejected without rounding, truncation, saturation, or lossy comparison. This is schema/value support and tier metadata, not `tiers.toml` activation: `TierConfig`, `MekanismConfig`/other real configs, other cached value kinds/tier families, config sync/UI/watchers/world restart, owners/callers, machines/blocks/items, capability registration, external energy/transactions, networking/serialization/persistence/lifecycle/threading remain deferred.

Eight state/permission wrapper sources (`ProxiedHolder`, the three proxy classes, and the four generic/primitive cached suppliers) remain byte-identical to `4646f509`. Eight Fabric-local tests bind null-side intrinsic read-only behavior with exact read forwarding, non-null exact-side and independent insert/extract permission forwarding, plus lazy resolution/reuse, dependency-listener registration, real `clearCache` eager refresh, generic null-sentinel retries, negative-zero float bits, and a large long. Exact JAR family gates reject unexpected same-package classes and test/helper/fixture leakage. Except for Task 023's narrow composition of the strict-energy proxy, owners/callers, actual capability provider registration, side configuration/ejection, machines/transmitters/networks, real config load/reload/file I/O/lifecycle/threading, persistence/network serialization, and broader Fabric integration remain deferred.

The six unchanged tile behavior interfaces (`IHasDumpButton`, `IHasGasMode`, `IHasMode`, `IHasVisualization`, `ITileActive`, and `IComparatorSupport`) are byte-identical to `4646f509`. Four deterministic Fabric-local JUnit methods bind exact mode/action dispatch with unchanged zero and negative tank indexes, visualization default/override/toggle transitions, reusable zero `NO_DELAY` plus activatable and raw active state, and comparator support plus independent boundary-valued level observations. The remapped JAR's direct `mekanism/common/tile/interfaces/` package must contain exactly those six classes, once each, with no nested/synthetic/test/helper/fixture leakage and no startup marker. This admits contracts only: every tile/machine implementation, `TileEntityMekanism`, GUI/button packet caller, block-state/comparator update scheduling, chemical behavior despite the legacy gas name, capability registration/lookup/exposure, side configuration/ejection, persistence, networking/wire serialization, registry lifecycle, world ticking, thread ownership, and client/server synchronization remain deferred.

The platform-neutral `ISlotInfo`, `BaseSlotInfo`, `EnergySlotInfo`, and `HeatSlotInfo` descriptors are also byte-identical to `4646f509`. Exactly five Fabric-local JUnit methods bind all four flag combinations and OR enablement, energy varargs identity/order with zero/all-empty behavior, source-order first-non-empty short-circuit, exact live list identity/membership/contained-state changes, and heat flags/list identity with unconditional `isEmpty() == false` and no capacitor-state read. Packaging restricts `mekanism/common/tile/component/config/slot/` to exactly those four production classes, once each, with no direct/nested/synthetic/test/helper/fixture leakage and no startup marker. This does not admit `ConfigInfo`, `DataType`, `TileComponentConfig`, `TileComponentEjector`, configurable holders/helpers, chemical/fluid/inventory/proxy slot descriptors, any tile/machine/block caller, capability exposure, side-configuration execution, ejection, persistence, networking/wire serialization, config/registry lifecycle, transactions, or thread ownership.

The Task 022 holiday value foundations `KnownDate`, `MonthlyDate`, `YearlyDate`, and `HolidayMessage` are likewise byte-identical to `4646f509`; that task brought the audited slices to API/common/platform **42/88/10**. Exactly five deterministic same-package JUnit methods bind yearly exact month/day including a Feb 29 value and mismatch cases, monthly same-month days and another-month rejection, rollover-safe `YearlyDate.now()` snapshots, themed-component plus ordered deep-array equality/hash and inequality cases, and exact component/varargs-array aliasing with order and caller-mutation visibility. `YearlyDate.now()` uses the JVM default clock/zone; tests do not change clock or time-zone globals. `HolidayMessage` retains the supplied component and array references rather than being deeply immutable. Packaging requires direct `mekanism/common/base/holiday/` to contain exactly `KnownDate.class`, `MonthlyDate.class`, `YearlyDate.class`, and `HolidayMessage.class`, once each, with no other direct/nested/synthetic/test/helper/fixture class and no startup marker. `Holiday`, `HolidayManager`, every concrete holiday, player notification, translations/colors/skins, client holiday state, lifecycle/event wiring, persistence, networking, registries, config, and world/tick/thread ownership remain deferred.

Task 023 brings the current slices to API/common/platform **43/88/14**. The newly admitted official `IEnergyConversion` is byte-identical to `4646f509`; `FabricEnergyConversions`, `FabricStrictEnergyHandler`, `FabricEnergyStorage`, and `FabricEnergyLookup` independently implement the first bidirectional Team Reborn Energy **4.1.0** boundary. The dependency comes from Fabric Maven via `modImplementation`, is required in `fabric.mod.json`, and is not nested. Exact rate `1.0D` bypasses official double loss only inside the adapter and preserves every positive long. Finite positive non-1 rates select the largest officially verified, in-bound member of a fixed family: a global 10-bit dyadic mesh plus every canonical-decimal continued-fraction denominator times coefficients 1..64 and fitting binary shifts. Bounds only filter that finite family, so selection is deterministic and component-wise monotone without a caller-bound-sized scan. It remains conservative, has no global maximality or general utilization claim, and can return zero when no family member verifies. Tests cover 2.5/0.4/0.7, a decimal with no verified bare convergent, the reviewer nondecimal pair, and named high-bound rates with an explicit 99% projected-utilization floor; no FE equivalence is hardcoded.

The external-to-strict direction has one true-long indexed container, aborts `SIMULATE`, commits exact execution, and uses compat-oriented current-transaction access only for lifecycle `OPEN`; `NONE` opens an outer transaction and close/outer-close callbacks short-circuit without storage access. Simulation/execution mismatch aborts without reporting transfer. The strict-to-external direction snapshots every captured container before `EXECUTE`; repeated/nested commits remain parent-rollbackable. A count change during execute aborts/restores and reports zero without rechecking changed count during restore. Later mutation and stable-handler access fail fast, while support/amount/capacity queries return false/zero on drift. Amount/capacity otherwise saturate raw Joule sums before one conversion. Deterministic `setEnergy` for every index is mandatory: the no-op strict adapter is rejected, all setters are attempted and read back, and setter/read exceptions or clamped mismatches propagate with first-primary/later-suppressed ordering while explicitly forfeiting atomicity. Immediate execute/restore listener/save/sync effects remain observable and nontransactional.

The sole lookup/factory seam rejects null/internal sides, unusable conversions, disabled handlers, and empty side views. It stores the exact physical exposure side and rejects every mismatched-side wrap regardless of conversion; same-side identical conversion unwraps, while another usable conversion may wrap. Live exact-side nullable-holder suppliers drive both support flags and operations through allow→deny→allow transitions. Owners must cache one adapter per stable handler/physical side and notify neighbors when support changes; owner wiring remains deferred. Exactly eight local JUnit methods prove the conversion, true-long, lifecycle, rollback/precondition, aggregate, exact-side, live-support, and self-unwrapping contracts. Packaging requires exactly these four platform classes and the five-class API energy family, rejects nested `team/reborn/**`, validates external Energy 4.1.0, and has no transfer marker.

Deferred are ConfigHolder/ConfigInfo/DataType/ISideConfiguration/configurable holders/helpers, TileComponentConfig/Ejector, real block/item/entity providers and owner caching/invalidation, cables/networks/emission/machines, hardcoded FE config activation, item/fluid/chemical/heat adapters, persistence, packets/client sync, and thread safety.

The official `DependentMapCodec`, `SequencedCollectionCodec`, and `DroppingUnboundedMapCodec` are admitted byte-identically as a platform-neutral codec closure. A five-method, table-driven Java 21 oracle covers dependency-first codec selection and field errors, sequenced min/max/order/immutable/partial-remainder semantics, and first-wins map collisions including malformed and deliberately ignored duplicate partial values. Packaging allows exactly the one API and three common codec class entries (including `SequencedCollectionCodec$DecoderState`) and uses no startup marker. This proves only those codec behaviors: `ModuleContainer`, `AttachedSideConfig`, recipes, data components, config/network owners, and every caller remain deferred; no persistence, registry, packet, lifecycle-owner, or backward-compatible serialized-format claim is made.

The unchanged `ChunkUtils` keeps `rangeClosed`'s inclusive, row-major ordering
(X changes fastest, then Z) for all four traversal directions together with its
packed `long` behavior, and `packedBlockToChunk`'s `>> 4` floor-section semantics
for negative coordinates. `rangeClosed` returns a `long[]` sized from the
inclusive dimensions, so the caller owns the precondition that the requested
rectangle is representable and small enough to allocate; no new validation or API
guarantee is added. The unchanged collection utilities now also admit byte-identical `BiMultimap`,
`EmptySequencedMap`, `HashList`, and `LRU`. Focused Java 21 tests bound bidirectional
index symmetry/live reads, ordered unique replace/swap/clone transitions, unique non-null
LRU ordering, and immutable empty sequenced-map behavior. Packaging requires exactly 12
collection class entries and rejects every other class in that exact package. All security,
frequency, filter, sync, QIO, `ModuleContainer`, gear, data-component, codec, GUI, recipe-viewer,
client-lifecycle, networking, persistence, registry, miner, and spatial callers/owners remain
deferred; no thread-safety, concurrent-mutation, or general mutable-view guarantee is made.

The unchanged Java 21 `LambdaMetaFactoryUtil` preserves its two distinct access
modes: an empty method name binds the caller-prepared `Field` with an unreflected
getter/setter, while a non-empty name binds the exact named virtual method (which
may intentionally transform values or otherwise differ from raw-field access).
Fabric-local tests cover boxed/unboxed round trips for all eight primitives,
reference/null values, per-instance reuse, every named setter branch, unchanged
exception propagation, missing methods, and inaccessible fields. Callers must
supply an accessible/module-open instance field for direct access, the exact
`objType` and named-method signature, type-compatible boxed values, and non-null
values for primitive setters. Static/final fields are unsupported boundaries.
Packaging checks only prove the exact outer/nested production class family is
present once and tests are absent; behavior is proved by Java 21 tests, not a
startup marker. `SyncMapper`, annotations, tracked data, containers, networking,
reflection scanning, and all callers remain deferred.

The unchanged official long-transfer utility closure (`LongTransferUtils` with the
`InContainerGetterLong` and `LongToLongContainerInteraction` callback interfaces) is
admitted as three byte-identical baseline sources in the API slice. It restores
Mekanism's sequential generic/energy-container insert/extract helpers; it is not the
fair-distribution layer, a Fabric transaction, or an external energy API adapter.
Insertion visits containers that already hold energy first and only then the deferred
empty containers (each phase in source order), forwards the current remainder to each
next container, and returns as soon as a remainder is `<= 0`. Extraction walks source
order, ignores non-positive drains, forwards the shrinking request, accumulates valid
drains, and breaks exactly when the request is satisfied. `Action` and
`AutomationType` are forwarded verbatim, and true `long` amounts above
`Integer.MAX_VALUE` stay lossless. Callers must provide nonnegative offers, an
explicit-overload `containerCount` that matches a fresh, consistent list, handlers
whose insert remainder stays in `[0, offer]` and extract result in `[0, request]`, and
a lookup that tolerates the nullable side. The official source defends against
neither mismatched counts nor invalid handler returns, and this slice adds no policy.
In particular, the energy-supplier extract overload short-circuits exactly `0` before
the supplier lookup, while a negative amount still reaches the supplier and is then
rejected by the explicit overload. Fabric-local Java 21 tests prove all six public
overload families through observable event/order/value assertions, and the packaged
smoke proves the three production classes occur exactly once with no
`LongTransferUtils*` test/helper leakage and no startup marker. The strict energy-handler closure (`IStrictEnergyHandler`,
`ISidedStrictEnergyHandler`, `IMekanismStrictEnergyHandler`) now connects those
helpers to indexed, sided, and container-backed defaults. Fabric-local Java 21
tests prove non-empty-first/source-order aggregation, exact side and `Action`
propagation, invalid-index/no-op defaults, negative-set clamping, null/internal
versus non-null/external automation, one list lookup per aggregate, and
`SIMULATE`/`EXECUTE` behavior with a true-`long` amount. Packaging requires all
three interfaces and the existing `IEnergyContainer` exactly once, rejects every
other energy API class outside the current closure, and adds no startup marker. The
byte-identical `SimpleDynamicHandler`, `DynamicHandler`, and
`DynamicStrictEnergyHandler` closure adds exact nullable-side predicate and supplier
forwarding plus optional listener forwarding; denied operations never consult the
supplier. The two root capability classes and the energy package's
`BasicEnergyContainer`/`DynamicStrictEnergyHandler` are exact package families.
There is no official `EnergyHandler` class in this baseline. `EnergyContainerHelper`,
`ConfigEnergyContainerHolder`, side configuration/ejector, Universal Cable and other
machine/network/persistence owners, actual Energy provider registration/cache invalidation,
and all callers (`ComponentBackedEnergyHandler`, `EnergyRecipeData`) remain deferred.

`RegistryUtils` has only the two minimal vanilla equivalents documented in
`../docs/API_DELTAS.md`. Its holder-key helper uses `unwrapKey()` and preserves
NeoForge's nullable result for a value-only intrusive reference whose key is not
yet bound; registry scans use `getResourceKey(...).map(ResourceKey::location)`
so an unknown value in a defaulted registry remains null. Fabric-local tests
cover canonical built-in holders, all NBT ID rejection paths, block paths,
keyed/direct and key-only/value-only holder states, registered/unknown raw
objects, and the direct-holder short circuit. The null fallback in `getBEHolder`
is not forced because normal vanilla built-ins make it unreachable and tests do
not mutate registries. Packaging proves only that `RegistryUtils.class` occurs
once and no same-prefix test/helper leaks; behavior is not a startup marker. No
caller, registry lifecycle/mutation, or custom registry is admitted.

Heat includes the unchanged dynamic `VariableHeatCapacitor`; Task 023 provides only the bounded
external Energy transaction seam, while neither domain claims configurable-holder, network, or
machine-owner integration and heat has no external adapter. Likewise, admitting distribution utilities does not
port energy/item/fluid/chemical transport callers, Fabric transactions, or their
machine/network owners. See `../docs/FABRIC_REGISTRY.md`,
`../docs/FABRIC_CONFIG.md`, `../docs/FABRIC_NETWORK.md`,
`../docs/FABRIC_ENERGY.md`, and `../docs/FABRIC_HEAT.md`. Night Config core/toml
3.8.3 are nested in the remapped mod JAR.

The next nearby API candidates are blocked by deliberately excluded boundaries:
incremental-enum helpers close over `ConstantPredicates` (which imports
NeoForge fluid/tri-predicate and chemical types), and the text/provider enums
close over NeoForge `TranslatableEnum` or larger API text graphs. Those families
remain outside the current slice.

To repeat the portability audit and fail on a missing source, duplicate path,
or any `net.neoforged` reference, run from the repository root:

```bash
./gradlew -p fabric verifyApiSliceNoNeoForge verifyCommonSliceNoNeoForge verifyPlatformSliceNoNeoForge
```

The check is also a dependency of Java compilation. Blank lines and lines
beginning with `#` are ignored; keep actual source entries one per line.

Build from the repository root:

```bash
./fabric-build.sh
```

The remapped mod JAR is written to `fabric/build/libs/`. It contains the
Fabric bootstrap and every class compiled from the allowlisted API sources.
The repository-level `./build.sh [--clean] [--out <dir>]` contract invokes this
Fabric build and collects only its remapped release JAR. The exact buildable
NeoForge oracle remains available at baseline commit `4646f509`; current ported
sources are not expected to compile with the root NeoForge Gradle build.

Task 018 adds four byte-identical `4646f509` internal support sources: the proxied energy holder, acceptor/save distribution bridges, and immutable laser dissipation handler. Six local tests bind exact nullable predicate/list identity, action/true-`long` remainder/order and execution shortfall, fair delayed/repeated saturated/zero saves with clamped/delegate storage, and independent laser bounds/infinity plus raw-comparison NaN behavior. Exact JAR gates reject same-package leakage. This adds no wire protocol or adapter; all owners/callers, transmitter/dynamic-network lifecycle, emission ticks, capability registration/lookup/exposure, Fabric Energy conversion/transactions/rollback, side config/ejection, machine/tile/item integration, laser attachment/damage, networking/serialization/persistence/threading remain deferred.

Dependency versions were checked against Fabric's official Maven metadata:

- `https://maven.fabricmc.net/net/fabricmc/fabric-loader/maven-metadata.xml`
- `https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml`
- `https://maven.fabricmc.net/net/fabricmc/fabric-loom/maven-metadata.xml`
