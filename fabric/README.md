# Fabric bootstrap build

This is an independent Gradle/Loom build for the first Java Fabric migration
stage. It compiles explicit, reviewed allowlists from `fabric/src/main`,
`../src/api/java`, and `../src/main/java`; all other upstream Java sources remain
excluded. It uses Minecraft 1.21.1, Java 21, and Mojang official mappings.

## Audited source slices

`api-slice.txt` is the auditable allowlist of API source paths, relative to
`src/api/java`. The Fabric source set reads that file directly, so the listed
sources retain their original `mekanism.api.*` names without being copied or
modified. The allowlist contains **42 current source files**: **41 official baseline
sources** plus the project-owned registry-aware `INBTSerializable` NBT contract.
Intentional API adaptations include that contract, `Action`'s removal of two
NeoForge `FluidAction` conversion methods, and the Javadoc-only strict-handler
fluid-import/link cleanup; `IMekanismStrictEnergyHandler` remains byte-identical.
See `../docs/API_DELTAS.md`. JetBrains annotations and JSR-305 remain `compileOnly`
and are not bundled in the mod.

`common-slice.txt` contains **45 official source files**, and
`platform-slice.txt` contains **10 project-owned Fabric sources**. Together with
the **42-file API slice**, they form bounded registry/GameEvent, TOML
startup-config, configuration-handshake, internal energy storage/holder, internal
heat storage/holder, wildcard-matching, fair-distribution, chunk-coordinate,
lambda-metafactory, and vanilla-registry utility closures. The unchanged
`WildcardMatcher` retains official recursive, UTF-16 `char` matching semantics;
QIO, miner, Finder, and other consumers remain future work. The byte-identical
`LastEnergyTracker` is a platform-neutral game-time bucket: same-time receives accumulate
without changing the published last bucket, every differing (including skipped or decreasing)
timestamp publishes the current bucket, and its setter changes only the visible client-sync
value until the next transition. The direct common-lib JAR package is exactly it plus
`WildcardMatcher`, once each, with no startup marker. `TileEntityMekanism`, world-tick/client
synchronization and network/persistence owners, machines/networks, external energy APIs and
Fabric conversion/lookup/transaction integration remain deferred. The unchanged
distribution closure preserves the separate `int`/`long` split arithmetic,
rebalancing, remainder order, lying-handler fallback, collection order, and
`Target` handler contract. The exact 18-file official root-test oracle is
compiled through an explicit path allowlist (including the two unchanged collection tests and three jqwik
properties at 100,000 tries), with separate Fabric boundary coverage.
Callers must provide nonnegative offers, a fresh/non-reused `Target`, and handlers
whose simulation/acceptance and `SplitInfo.send` accounting obey the documented
contract; the official implementation intentionally adds no defensive behavior
for contract violations.

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
machine/network/persistence owners, external Energy API conversion/lookup/transaction,
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

Heat includes the unchanged dynamic `VariableHeatCapacitor`; neither energy nor
heat claims external API, transaction, configurable-holder, network, or
machine-owner integration. Likewise, admitting distribution utilities does not
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

Dependency versions were checked against Fabric's official Maven metadata:

- `https://maven.fabricmc.net/net/fabricmc/fabric-loader/maven-metadata.xml`
- `https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml`
- `https://maven.fabricmc.net/net/fabricmc/fabric-loom/maven-metadata.xml`
