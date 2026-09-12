# Fabric bootstrap build

This is an independent Gradle/Loom build for the first Java Fabric migration
stage. It compiles explicit, reviewed allowlists from `fabric/src/main`,
`../src/api/java`, and `../src/main/java`; all other upstream Java sources remain
excluded. It uses Minecraft 1.21.1, Java 21, and Mojang official mappings.

## Audited source slices

`api-slice.txt` is the auditable allowlist of API source paths, relative to
`src/api/java`. The Fabric source set reads that file directly, so the listed
sources retain their original `mekanism.api.*` names without being copied or
modified. The allowlist contains **65 current source files**: **62 official baseline
sources** plus the project-owned registry-aware `INBTSerializable`, bounded
`FluidStack`, and Task 038 `MekaSuitAbsorption` contracts.
Intentional API adaptations include that contract, `Action`'s removal of two
NeoForge `FluidAction` conversion methods, the Javadoc-only strict-handler
fluid-import/link cleanup, Task 032's bounded module-config namespace/enum/text
adaptations, Task 033's inventory NBT/nominal-parent boundary, Task 034's
Mekanism-owned int-mB fluid value/API delta, Task 035's shared serialization
owner/logger adaptation, and Task 036's stateless incremental predicate, stable
APILang namespace, project-fluid/raw-fluid naming, vanilla level-description, and
existing nominal-parent adaptations. `IMekanismStrictEnergyHandler`,
`IMekanismInventory`, `IHashedItem`, `EnumColor`, and `ILangEntry` remain
byte-identical.
See `../docs/API_DELTAS.md`, `../docs/FABRIC_ITEM.md`, `../docs/FABRIC_FLUID.md`,
and `../docs/FABRIC_TEXT.md`. JetBrains annotations and JSR-305 remain `compileOnly`
and are not bundled in the mod.

`common-slice.txt` contains **122 official source files**, and
`platform-slice.txt` contains **22 project-owned Fabric sources**. Seven spatial/color/effect foundations (`Color`, `CustomEffect`, `Pos3D`, `Quaternion`, `Range3D`, `MultipartUtils`, and `StatUtils`) remain byte-identical to `4646f509`. Twelve Java 21 tests bound channel/HSV/compositing/statistics, effect state, vector/range/rotation/quaternion state, and direct multipart ray selection. Packaging permits exactly four direct common-lib classes, one effect class, three math classes, the three `MultipartUtils` classes, and one `StatUtils` class. This admits no caller or owner: renderers/particles/GUI/recipe viewers/client lifecycle, transmitters/multipart blocks/gear, machines/block types/entities, packet tracking/network ranges, SPS/multiblock, additions/generators, external energy, world/chunk lifecycle, registries/config/persistence all remain deferred; there is no rendering integration, entity/player lifecycle, block registration, networking, thread-safety, or collision-owner guarantee. Together with
the **65-file API slice**, they form bounded registry/GameEvent, TOML
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

Sixteen registration/domain and transport/tank tier metadata sources are now admitted: the six earlier tier enums plus `CableTier`, `ChemicalTankTier`, `ConductorTier`, `FluidTankTier`, `PipeTier`, `TransporterTier`, and `TubeTier`, together with `IResource`, `MiscResource`, and `BlockResourceInfo`. The earlier nine sources remain byte-identical to `4646f509`; the seven Task 029 adaptations are bounded below. Fifteen Fabric-local tests bind exact enum order and complete capacity/output/pull/speed/process/type/count/conduction/heat/insulation/color/BaseTier/name tables (including creative signed maxima and QIO `16_000_000_000L`), loaded cached-config overrides with live subsequent sets and reflective `finally` restoration, complete resource suffix/accessor tables, and property construction. Exact package-family gates allow only thirteen direct tier and three resource classes, once each. `BlockResourceInfo` proves property construction only. `PrimaryResource`, `ResourceType`, ore types/config, tags/datagen/registration, block/item implementations, factories/bins/QIO and transport/tank owners, and broader lifecycle remain deferred.

`CachedLongValue` differs from `4646f509` only by replacing its NeoForge `Builder` and `ConfigValue` imports with `MekanismConfigSpec` types. Task 025 added `CachedBooleanValue`, `CachedByteValue`, `CachedShortValue`, and `CachedDoubleValue`; each differs from that oracle by exactly one NeoForge-to-Fabric `ConfigValue` import replacement. Task 030 adds `CachedResolvableConfigValue`, `CachedConfigValue`, and `CachedMapConfigValue` with that same sole import replacement, while `CachedOredictionificatorConfigValue` only replaces its NeoForge `ModConfigSpec`/nested value imports and types with `MekanismConfigSpec` equivalents. Their method bodies remain official, but these four sources are not byte-identical.

The Fabric spec now adds one strongly typed `defineListAllowEmpty` beyond its primitive definitions. It null-checks all functions, evaluates and validates the default exactly once before registration, never invokes the UI-only new-element supplier, accepts empty lists, rejects a non-list or the entire list on any null/predicate-invalid element, and immutable-copies each default/load/set top-level list. Existing path/metadata/freeze, set validation, correction, persistence-before-publication, synchronization, and TOML array behavior remain in the common pipeline. Exactly four methods in one same-package class cover real TOML schema, resolvable null/cache behavior, map last-wins/ignore/sort, and Oredictionificator root-locale/correction/order/round-trip semantics. Unchanged official `CachedResolvableConfigValue.set` still caches caller `TYPE`: the encoded `ConfigValue` list is immutable, while a caller map aliases until clear/reload reconstructs normalized state. The direct config-value JAR family is exactly 14 entries including `CachedMapConfigValue$ValueEncoder`, each once, with no startup marker. `GeneralConfig`/`MekanismConfig`, cardboard blacklist, Oredictionificator filters/items, `CachedEnumValue`, other schemas, sync/UI/watchers/owners/world restart, tier owners/callers, machines/blocks/items, capabilities, external transactions, networking/serialization/persistence/lifecycle/threading remain deferred.

Eight state/permission wrapper sources (`ProxiedHolder`, the three proxy classes, and the four generic/primitive cached suppliers) remain byte-identical to `4646f509`. Eight Fabric-local tests bind null-side intrinsic read-only behavior with exact read forwarding, non-null exact-side and independent insert/extract permission forwarding, plus lazy resolution/reuse, dependency-listener registration, real `clearCache` eager refresh, generic null-sentinel retries, negative-zero float bits, and a large long. Exact JAR family gates reject unexpected same-package classes and test/helper/fixture leakage. Except for Task 023's narrow composition of the strict-energy proxy, owners/callers, actual capability provider registration, side configuration/ejection, machines/transmitters/networks, real config load/reload/file I/O/lifecycle/threading, persistence/network serialization, and broader Fabric integration remain deferred.

The six unchanged tile behavior interfaces (`IHasDumpButton`, `IHasGasMode`, `IHasMode`, `IHasVisualization`, `ITileActive`, and `IComparatorSupport`) are byte-identical to `4646f509`. Four deterministic Fabric-local JUnit methods bind exact mode/action dispatch with unchanged zero and negative tank indexes, visualization default/override/toggle transitions, reusable zero `NO_DELAY` plus activatable and raw active state, and comparator support plus independent boundary-valued level observations. The remapped JAR's direct `mekanism/common/tile/interfaces/` package must contain exactly those six classes, once each, with no nested/synthetic/test/helper/fixture leakage and no startup marker. This admits contracts only: every tile/machine implementation, `TileEntityMekanism`, GUI/button packet caller, block-state/comparator update scheduling, chemical behavior despite the legacy gas name, capability registration/lookup/exposure, side configuration/ejection, persistence, networking/wire serialization, registry lifecycle, world ticking, thread ownership, and client/server synchronization remain deferred.

The platform-neutral `ISlotInfo`, `BaseSlotInfo`, `EnergySlotInfo`, and `HeatSlotInfo` descriptors are also byte-identical to `4646f509`. Exactly five Fabric-local JUnit methods bind all four flag combinations and OR enablement, energy varargs identity/order with zero/all-empty behavior, source-order first-non-empty short-circuit, exact live list identity/membership/contained-state changes, and heat flags/list identity with unconditional `isEmpty() == false` and no capacitor-state read. Packaging restricts `mekanism/common/tile/component/config/slot/` to exactly those four production classes, once each, with no direct/nested/synthetic/test/helper/fixture leakage and no startup marker. This does not admit `ConfigInfo`, `DataType`, `TileComponentConfig`, `TileComponentEjector`, configurable holders/helpers, chemical/fluid/inventory/proxy slot descriptors, any tile/machine/block caller, capability exposure, side-configuration execution, ejection, persistence, networking/wire serialization, config/registry lifecycle, transactions, or thread ownership.

The Task 022 holiday value foundations `KnownDate`, `MonthlyDate`, `YearlyDate`, and `HolidayMessage` are likewise byte-identical to `4646f509`; that task brought the audited slices to API/common/platform **42/88/10**. Exactly five deterministic same-package JUnit methods bind yearly exact month/day including a Feb 29 value and mismatch cases, monthly same-month days and another-month rejection, rollover-safe `YearlyDate.now()` snapshots, themed-component plus ordered deep-array equality/hash and inequality cases, and exact component/varargs-array aliasing with order and caller-mutation visibility. `YearlyDate.now()` uses the JVM default clock/zone; tests do not change clock or time-zone globals. `HolidayMessage` retains the supplied component and array references rather than being deeply immutable. Packaging requires direct `mekanism/common/base/holiday/` to contain exactly `KnownDate.class`, `MonthlyDate.class`, `YearlyDate.class`, and `HolidayMessage.class`, once each, with no other direct/nested/synthetic/test/helper/fixture class and no startup marker. `Holiday`, `HolidayManager`, every concrete holiday, player notification, translations/colors/skins, client holiday state, lifecycle/event wiring, persistence, networking, registries, config, and world/tick/thread ownership remain deferred.

Task 023 brought its slices to API/common/platform **43/88/14**. The newly admitted official `IEnergyConversion` is byte-identical to `4646f509`; `FabricEnergyConversions`, `FabricStrictEnergyHandler`, `FabricEnergyStorage`, and `FabricEnergyLookup` independently implement the first bidirectional Team Reborn Energy **4.1.0** boundary. The dependency comes from Fabric Maven via `modImplementation`, is required in `fabric.mod.json`, and is not nested. Exact rate `1.0D` is treated as the adapter's authoritative identity contract, bypasses official double loss, and preserves every positive long; contradictory custom conversion overrides are unsupported. Finite positive non-1 rates select the largest officially verified, in-bound member of a fixed family: a global 10-bit dyadic mesh plus every canonical-decimal continued-fraction denominator times coefficients 1..64 and fitting binary shifts. Bounds only filter that finite family, so selection is deterministic and component-wise monotone for documented stable conversion behavior without a caller-bound-sized scan. A cold identity/rate build has at most 399,103 pre-dedup candidate occurrences; four synchronized weak-identity slots publish immutable primitive snapshots keyed by exact raw rate bits. Enabled state remains live, changed bits rebuild, distinct implementations never share by rate alone, and warm selection is `O(log U)` plus final owner-method verification without BigDecimal/Euclid/enumeration. An empty requested window probes the first cached pair when one exists; a zero-pair snapshot has no sentinel, and cache validity requires stable conversion behavior while advertised rate bits remain unchanged. It remains conservative, has no global maximality or general utilization claim, and can return zero when no family member verifies. Tests cover 2.5/0.4/0.7, a decimal with no verified bare convergent, the reviewer nondecimal pair, named high-bound rates with an explicit 99% projected-utilization floor, and deterministic cache invalidation/identity/call-count behavior; no FE equivalence is hardcoded.

The external-to-strict direction has one true-long indexed container, aborts `SIMULATE`, commits exact execution, and uses compat-oriented current-transaction access only for lifecycle `OPEN`; `NONE` opens an outer transaction and close/outer-close callbacks short-circuit without storage access. Simulation/execution mismatch aborts without reporting transfer. The strict-to-external direction snapshots every captured container before `EXECUTE`; repeated/nested commits remain parent-rollbackable. A count change during execute aborts/restores and reports zero without rechecking changed count during restore. Later mutation and stable-handler access fail fast, while support/amount/capacity queries return false/zero on drift. Amount/capacity otherwise saturate raw Joule sums before one conversion. Deterministic `setEnergy` for every index is mandatory: the no-op strict adapter is rejected, all setters are attempted and read back, and setter/read exceptions or clamped mismatches propagate with first-primary/later-suppressed ordering while explicitly forfeiting atomicity. Immediate execute/restore listener/save/sync effects remain observable and nontransactional.

The sole lookup/factory seam rejects null/internal sides, unusable conversions, disabled handlers, and empty side views. It stores the exact physical exposure side and rejects every mismatched-side wrap regardless of conversion; same-side identical conversion unwraps, while another usable conversion may wrap. Live exact-side nullable-holder suppliers drive both support flags and operations through allow→deny→allow transitions. Owners must cache one adapter per stable handler/physical side and notify neighbors when support changes; owner wiring remains deferred. Exactly eight local JUnit methods prove the conversion, true-long, lifecycle, rollback/precondition, aggregate, exact-side, live-support, and self-unwrapping contracts. Packaging requires exactly these four platform classes and the five-class API energy family, rejects nested `team/reborn/**`, validates external Energy 4.1.0, and has no transfer marker.

Deferred are ConfigHolder/ConfigInfo/DataType/ISideConfiguration/configurable holders/helpers, TileComponentConfig/Ejector, real block/item/entity providers and owner caching/invalidation, cables/networks/emission/machines, hardcoded FE config activation, the item reverse/provider path, every fluid storage/transaction adapter beyond Task 034's value seam, chemical/heat adapters, persistence, packets/client sync, and thread safety.

Task 024 brought the API/common/platform slices to **47/88/14** by admitting unchanged official `ContainerInteraction`, `InContainerGetter`, `IntContainerInteraction`, and `LongContainerInteraction`; all four are byte-identical to `4646f509`. One Fabric-local same-package class has exactly four deterministic JUnit methods covering exact generic stack/result identity, arbitrary/negative index, nullable/physical side, exact `SIMULATE`/`EXECUTE`, raw signed `int` endpoints, and true-`long` endpoints plus an odd value above `2^53`, without normalization. The remapped JAR's direct `mekanism/api/container/` family must be exactly these four plus `InContainerGetterLong` and `LongToLongContainerInteraction`, each once, with no other direct/nested/synthetic/test/helper/fixture class and no startup marker. These are callback contracts only: `ChemicalUtils`, `ExtendedFluidHandlerUtils`, chemical/fluid handlers, component-backed/recipe-upgrade owners, actual transfer/platform transactions, registries, serialization/networking, and lifecycle/thread ownership remain deferred.

Task 025 brought its slices to **47/92/14** with the four primitive cached-value sources and the minimum typed schema support described above. Packaging expanded only the exact config-value family from five to nine entries while preserving the Task 023 Energy and Task 024 container gates. There is no startup marker and no activation of additional real configs.

Task 026 brought its slices to **47/96/14** by admitting unchanged `IStateStorage`, `IFluidLogType`, `FluidLogType`, and `TransmitterType`, all byte-identical to `4646f509`. Exactly three methods in one same-package Fabric-local test class fix live exact-`Fluids.EMPTY` identity; fluid-log enum order, serialized names, canonical fluid identities, EMPTY-only behavior, and enum consistency; and exact transmitter order, SMALL/LARGE assignments, 6/8 center sizes, and enum consistency. Marker-only `IStateStorage` has no vacuous reflection test or runtime claim. Packaging restricts exact `mekanism/common/block/states/` to its five expected entries (including `TransmitterType$Size`) once each, with no other direct/nested/synthetic/test/helper/fixture class and no startup marker. Tests use only the established narrow vanilla bootstrap required for static initialization and perform no custom registry mutation. Fluidlogging implementation/state definition/world updates/persistence, transmitters/cables/networks/rendering, actual block registration, codecs/serialization/networking, lifecycle/thread ownership, and all callers/implementers remain deferred.

Task 027 brought its slices to **47/99/14** as an explicitly justified coherent three-source exception: bounded scouts found no honest four-source closure. `GuiComponents` and `ContainerSlotType` are byte-identical to `4646f509`. `SlotOverlay` has only the minimal Fabric-safe adaptation from `MekanismUtils.getResource(ResourceType.GUI_SLOT, texture)` to exact vanilla `ResourceLocation.fromNamespaceAndPath("mekanism", "gui/slot/" + texture)` with the two helper imports removed; all eleven outputs remain the exact official `mekanism:gui/slot/overlay_*.png` IDs. One Fabric-local class has exactly four tests for seven/eleven-value order and enum contracts, each 18×18 overlay and resource ID, fresh values arrays, and test-local dropdown/toggle exact `Component`/`ResourceLocation` identity with default-null/overridden icons and forwarding. It uses no renderer, texture I/O, bootstrap, registry/global mutation, or startup marker. Packaging permits only the `GuiComponents` outer and its two interfaces under that prefix, and only direct `ContainerSlotType`/`SlotOverlay` in the inventory slot package, each once. Screens/widgets/rendering/runtime texture validation, concrete slots/`IInsertableSlot`/inventory/menu/player/window owners, GUI lifecycle, networking/sync, registries/serialization/thread ownership remain deferred; this closure is metadata only.

Task 028 brought its slices to **47/103/14**. Four newly admitted official sources—`DeferredMapCodecHolder`, `DeferredMapCodecRegister`, `DeferredCriterionTrigger`, and `LootFunctionDeferredRegister`—remain byte-identical to `4646f509`. Distinctly, the one production repair changes only the existing Fabric-adapted `MekanismDeferredHolder.value()` return type from `R` to covariant `T`; its body remains `return get();`. Because `T extends R`, this legally overrides `Holder<R>.value()` and restores the subtype contract. Both unbounded type variables erase to `Object`, leaving the JVM descriptor and runtime behavior unchanged; this compiler emits no extra bridge. One same-package test class has exactly four tests for MapCodec function/supplier laziness, ID/key, subtype/custom holder, canonical binding, pre-bind failure and delegated duplicate/lifecycle behavior; local criterion trigger/instance identity; and loot basic/codec laziness, keys, and exact codec identity. Tests commit only through the package-private registrar seam, with the established standard vanilla `SharedConstants`/`Bootstrap` initialization used solely where criterion static state requires it; they add no custom/built-in registry entry and perform no serialization, datapack codec, or loot-context execution. Task 028's packaging gate permitted exactly the six requested direct registration entries (including `MekanismDeferredRegister$Registrar`) and exactly `GameEventDeferredRegister`, `DeferredCriterionTrigger`, and `LootFunctionDeferredRegister` in impl, rejecting all other nested/test/helper classes. There is no startup marker. Real production declarations, global registration/freeze, datapack codec execution, serialization, loot/advancement runtime, owners, networking, and threading remain deferred.

Task 029 brought the API/common/platform slices to **47/110/14** by admitting the coherent seven-enum transport/tank metadata family. Relative to the unchanged official oracle, `CableTier`, `ConductorTier`, `PipeTier`, `TransporterTier`, and `TubeTier` only drop `EnumUtils` and iterate their own `values()` in `get(BaseTier)`, preserving declaration-order identity and the `BASIC` fallback. `ChemicalTankTier`, `FluidTankTier`, `PipeTier`, and `TubeTier` only drop NeoForge `FluidType` and replace every compile-time `FluidType.BUCKET_VOLUME` token with the exact integer literal `1_000`, preserving all resulting values and `int`/`long` arithmetic. Here `1_000` is Mekanism's existing mB/config metadata bucket basis inherited from NeoForge; it deliberately is not Fabric Transfer API's 81,000-droplet transaction unit. Exactly seven new methods bring `TierMetadataTest` to twelve and bind each enum's complete order/BaseTier/base/current tuple, all five lookup tables and absent-tier fallback, chemical serialized names, packed and per-channel RGBA conductor color, and loaded signed/large/fractional cached-reference overrides with live sets and reflective `finally` restoration. The remapped JAR gate now requires exactly all thirteen direct tier classes once each, with no nested/test/helper/fixture leakage and no startup marker. This remains metadata only: `TierConfig`/`tiers.toml`, Fabric fluid/chemical storage, conversion and transactions, cables, pipes, tubes, transporters, conductors, tanks, every owner/caller, and registry/network/persistence/lifecycle/threading are deferred.

Task 030 brought its slices to **47/114/14** with the bounded generic/list cached-value closure described above. The 18-file root oracle and API/platform slices stayed unchanged; exact import/type-only oracle diffs, four real-TOML tests, and the 14-entry static package family are its evidence rather than a startup marker.

Task 031 brought the slices to **47/120/14** by admitting the sound/damage declaration closure. `SoundEventRegistryObject` and `DamageTypeRegister` remain byte-identical to `4646f509`; `SoundEventDeferredRegister` only switches from NeoForge `DeferredRegister` to the existing `MekanismDeferredRegister` plus `SoundEventRegistryObject::new`, the two declaration owners only use literal `"mekanism"`, and `MekanismDamageType` only expands the injected holder lookup to vanilla `lookupOrThrow(...).getOrThrow(...)`. Exactly five tests cover fresh sound laziness/typed holders/canonical identity/range/lifecycle, the complete 47-public-declaration sound table (one owner and 46 unbound holders), every damage overload/live collection/duplicate and the four declared damage identities, plus one frozen disposable local damage registry. Static packaging permits exactly seven direct registration implementations and three registry declaration classes; there is no startup marker or owner registration. Real sound registration/freeze/playback/client/resources/subtitles, real damage registration/freeze/datapack bootstrap, production `RegistryAccess`/`Level`/entities/combat, and all owners/callers remain deferred.

Task 032 brought its slices to **53/120/14** with six module-config API sources. `ModuleBooleanConfig`, `ModuleColorConfig`, and `IHasModeIcon` remain byte-identical to `4646f509`. `ModuleConfig` only replaces the `MekanismAPI` owner with literal `"mekanism"`, retaining canonical keys, legacy `minecraft`-namespace decode rewrite, other namespaces, and exact encode names. `ModuleEnumConfig` only replaces NeoForge's enum codec with a method-local captured-constants codec built on `ByteBufCodecs.VAR_INT`: ordinal bytes are unchanged, negative/high decode still throws `ArrayIndexOutOfBoundsException`, and null encode still throws `NullPointerException`, with no anonymous/package/platform class. `IHasTextComponent` removes only the NeoForge `TranslatableEnum` subtype/import and obsolete override; its public default `getTranslatedName()` and exact component forwarding body remain, but NeoForge subtype compatibility is intentionally unavailable with no shim.

Exactly seven same-package tests use real Mojang codecs and Netty/`FriendlyByteBuf` to bind boolean and signed RGB/ARGB data/wire forms, alpha locking, enum count/list/data/supplied/named stream behavior, exact VAR_INT failures, official bounded changed-`with` return-to-full-list behavior, equality/hash/class/count, and text/icon identity. Packaging requires exactly five direct gear-config entries and exactly the `IHasTextComponent` outer+nested pair, once each, rejecting nested/synthetic/test/helper/fixture leakage; no startup marker. Deferred are `ModuleData`, module containers/items, codec catalog, GUI/radial/client rendering, packets/network owner integration, persistence/data components/registration/lifecycle, and every caller.

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
machine/network owners. Task 038 adds the one ordinary/unconditional/non-synced server-side
`MekaSuitAbsorption` data-map vertical. The complete low-to-high layer stack,
replace/write/remove order, atomic per-resource failures, immutable per-server epochs,
reference/direct query rules, exact two-resource packaging, dedicated Knot test and
isolated packaged reload fixture are documented in `../docs/FABRIC_DATA_MAPS.md`.
The current exact test contract is ordinary **68/324**, transfer **2/8**, root **1/1**,
data-map Knot **1/1**, aggregate **72/334**, retaining jqwik 3×100,000. Chemical,
synced/conditional/Advanced maps, holder mixins and broad owners remain deferred.

See `../docs/FABRIC_REGISTRY.md`, `../docs/FABRIC_DATA_MAPS.md`,
`../docs/FABRIC_CONFIG.md`, `../docs/FABRIC_NETWORK.md`,
`../docs/FABRIC_ENERGY.md`, `../docs/FABRIC_HEAT.md`, and
`../docs/FABRIC_ITEM.md`. Night Config core/toml
3.8.3 are nested in the remapped mod JAR.

Task 033 adds the two byte-identical inventory holder sources and the bounded
physical-side item transaction seam described in `../docs/FABRIC_ITEM.md`. Its
`InventoryApiHolderTest` stays in ordinary `test` without Knot, while
`FabricItemStorageTest` is byte-identically relocated into the generalized
`fabricTransferTest` source set/task. Its runtime alone includes pinned
`fabric-loader-junit`, and it shares one full Knot startup with Task 034's
`FabricFluidConversionsTest`; `check` runs ordinary and transfer tasks exactly once.
The factory performs no provider registration; future ownership must cache exactly
one view per physical side and invalidate it for removal/reload/facing/side-config
or topology changes. Concrete slots, reverse lookup, provider/cache wiring,
persistence, recipes, tiles, machines, and Crusher remain deferred.

Task 034 adds only the independently implemented `mekanism.api.fluid.FluidStack`
value and `mekanism.fabric.fluid.FabricFluidConversions` value/unit seam. The API
retains signed-int mB, empty/component/copy/matching and exact JSON/NBT/stream
contracts while explicitly omitting FluidType/capability-only methods. Although the
NeoForge mutable nominal parent is absent, all platform-neutral component overload
families remain callable; descriptions follow the default-state legacy block. The
Fabric class pins factor 81, exact divisibility/int-domain checks, component/fluid
identity, blank/zero mapping, and fail-closed flowing normalization. It adds no
Storage, Transaction, handler, lookup, provider, cache, or lifecycle claim. Nine
ordinary and four Knot methods brought Task 034 totals to 64 suites/305 tests and 2
suites/8 tests respectively; the three jqwik properties remain ordinary-only. The
static JAR gate permits exactly the two new outer classes and no fluid startup marker. See
`../docs/FABRIC_FLUID.md`.

Task 035 admits official `mekanism.api.SerializerHelper` as the shared item/fluid
serialization owner and restores `IInventorySlot.serializeNBT` delegation after
removing only its temporary Task 033 private codec/imports. Relative to `4646f509`,
the helper changes only the NeoForge `FluidStack` import to the project-owned type
and the two diagnostic logger calls: a private
`LoggerFactory.getLogger("mekanism.api.MekanismAPI")` preserves the exact category,
ERROR level, and item/fluid message templates without eagerly initializing the
unfinished `MekanismAPI` facade. All 11 public codec fields, six public methods,
legacy-long/item/fluid/oversized/conditional/stringify semantics remain; the helper
is not referenced by a startup entrypoint, and vanilla bootstrap precedes its first
test reference.

One new ordinary class has exactly five methods; unchanged `InventoryApiHolderTest`
keeps four integration methods. Task 035 landing totals were ordinary **65 suites/310 tests**,
transfer **2 suites/8 tests**, aggregate **67 suites/318 tests**; Task 036 landing totals
were **66/315**, **2/8**, and **68/323**. Task 037 landing ordinary/transfer/root/aggregate
totals were **67/320**, **2/8**, **1/1**, and **70/329**; Task 038 current totals are ordinary/transfer/root/data-map/aggregate
**68/324**, **2/8**, **1/1**, **1/1**, and **72/334**, with three jqwik properties at 100,000 each. The remapped JAR must contain exactly the compiled
SerializerHelper outer plus `$1`/`$2`/`$3`, once each, no `MekanismAPI.class`,
platform leakage, test/helper extras, startup reference, or marker. API/common/platform
Task 035 counts were **58/122/17**; no dependency, common, or platform source changed.

Task 036 admits the exact portable text/color SCC: new allowlist entries are
`IIncrementalEnum`, `IHashedItem`, `APILang`, `EnumColor`, `ILangEntry`, and
`TextComponentUtil`, while existing `IHasTranslationKey` and `RelativeSide` are
immediately restored to helper/APILang delegation. Only two internal always-true
arguments avoid the still-excluded `ConstantPredicates`; APILang uses literal
`"mekanism"` without initializing `MekanismAPI`; TextComponentUtil uses the owned
FluidStack and block-derived raw-fluid translation keys. Its two NeoForge-injected
Level description calls are expanded to the same vanilla dimension language-key
and ID-fallback component. Public methods, dispatch/style/null order, smart-translate
argument semantics, and RelativeSide behavior remain.

One bootstrapped ordinary class has exactly five methods. Task 036 landing slice and test
totals were **64/122/17**, ordinary **66/315**, transfer **2/8**, aggregate **68/323**;
the three jqwik properties remain 100,000 each. Mechanical JAR inspection fixes
TextComponentUtil to outer + `$1` and the complete direct text/RelativeSide/
incremental/inventory families exactly once. Static gates reject concrete
`HashedItem`, `MekanismAPI`, `ConstantPredicates`, chemical/TriPredicate classes,
unexpected nested/test/helper/fixture leakage, NeoForge/Loader bytecode, and startup
markers/references. This task makes no startup claim. Raw-fluid naming deliberately
cannot preserve custom NeoForge FluidType description overrides; it remains
block-derived. See `../docs/FABRIC_TEXT.md`.

Task 037 adds the reusable intrusive/defaulted custom-registry prerequisite. The final
`FabricDefaultedRegistry<T>` directly subclasses vanilla `DefaultedMappedRegistry<T>` with
stable lifecycle and intrusive holders; `FabricCustomRegistryLifecycle` records exact
CREATED/root-registration/freezing/frozen/failed phases without enum-switch synthetic output.
Factory key/default validation occurs before super-backed use, mutation/root/freeze is
construction-owner-only and synchronized, pure preflights remain recoverable, and any failure
after vanilla/Fabric side effects is terminal. Exact canonical default references, default
fallback versus optional lookup, and vanilla dangling-holder failure are preserved.

The existing deferred register now checks custom context before batch state/suppliers, checks
occupancy before each supplier and identity after construction, uses `Registry.registerForHolder`,
and publishes its exact reference before holder value. The registrar seam and terminal partial
commit remain. Ordinary tests add exactly five methods without rooting any registry. A dedicated
`fabricRegistryTest` adds one class/one method in a fresh server-side Knot fork with its own
working directory; it performs the only global mutation, genuinely root-registers one fixed
unique synced registry via the Fabric builder, and invokes the real built-in automatic freeze
path before the worker exits.

Task 037 landed at API/common/platform **64/122/19** with ordinary **67/320**, transfer **2/8**, isolated
root **1/1**, aggregate **70/329**. Task 038 current counts are **65/122/22** with ordinary **68/324**,
transfer **2/8**, root **1/1**, data-map Knot **1/1**, aggregate **72/334**; jqwik remains 3×100,000 in ordinary only. Static packaging
requires exactly seven direct registry classes and rejects synthetic/test/helper, NeoForge/
Loader, Chemical/MekanismAPI, Kilt/Connector, bypass strings, startup markers, and references
from either bootstrap. There is no chemical owner, `mekanism:empty`, data map/radiation,
reload/client class, production startup wiring, manual production freeze, or concurrent-read
promise. Existing GameEvent startup behavior is unchanged. Verified pinned behavior and hashes
are recorded in `../docs/FABRIC_REGISTRY.md`.

Nearby boundaries still excluded are `ChemicalStack`, full `ConstantPredicates`,
all remaining 195 FluidStack import replacements, concrete slots, menu/warning/
recipes, `ModuleData`, module containers/items, codec catalogs, GUI/radial/client
rendering, and every owner integration.

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
