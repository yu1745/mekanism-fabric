# Fabric ordinary and Chemical data maps

> 状态核实（2026-10-30 / W-05）：保留对应早期切片的语义设计与验收记录；文中 “current”、slice 数量及延期调用方仅指当时范围，不代表 Task052 后的纳入状态。当前状态与后续验收以 `milestones/README.md` 和 `milestones/ROADMAP.md` 为准。

## Task 038 boundary and Task 040 extension

Task 038 implements one connected server-side data-map vertical: `MekaSuitAbsorption` on
`Registries.DAMAGE_TYPE`. It is ordinary, unconditional, and non-synced. The API record keeps its
codec, constructor, public ID and serialized forms; its only source adaptation is replacing the
executable `MekanismAPI.MEKANISM_MODID` reference with the stable literal namespace `"mekanism"`.
Task 040 removes the platform-only `FabricDataMapType` and relocates its identity contract to public,
layer-neutral `MekanismDataMapType<R,T>`, adding optional synchronized codec/flag. Descriptor equality
remains object identity; catalog collisions use `(registryKey, id)`, so the same ID remains legal on
another registry. API/common code does not depend on Fabric.

This work was audited against NeoForge 21.1.200 without copying its LGPL implementation:

- sources: `/home/user/.gradle/caches/modules-2/files-2.1/net.neoforged/neoforge/21.1.200/f0f15283b397e14e812ca49eef21b074f2c484e5/neoforge-21.1.200-sources.jar`
- source SHA-256: `7b31b7f4b817c90b8aa05ade830d761223dff07967aab49ab1f197d51c4d1420`
- binary: `/home/user/.gradle/caches/modules-2/files-2.1/net.neoforged/neoforge/21.1.200/13d648aad7dcdb2a68551a407027620fa32c0afa/neoforge-21.1.200-universal.jar`
- binary SHA-256: `bad2e2d951a275f1063600b432223d3fb5c18345f6bfde204517b51cb25720da`
- audited concepts: `DataMapType`, `DataMapEntry`, `DataMapFile`, `DataMapLoader`, value merger/remover,
  `AdvancedDataMapType`, and conditional operations.

## Packaged inputs

`processResources` admits only these two generated files, unchanged and exactly once:

| Path | SHA-256 |
|---|---|
| `data/mekanism/data_maps/damage_type/mekasuit_absorption.json` | `437587714884bfef9045ac987b97e8b2d63ccb304142181d934e8ef21b471d41` |
| `data/mekanism/tags/damage_type/mekasuit_always_supported.json` | `7237a571f951f8deef3cacb86518b3b8560f145dad16777701142004814b07da` |

The tag names 21 vanilla damage types. With only packaged resources, startup publishes 21 mappings:
20 values at `1.0`, with `minecraft:sonic_boom` overwritten to `0.75`. The generated root is not
admitted broadly and duplicate resource paths fail resource processing.

## Ordinary layer semantics

The resource path is `<map namespace>/data_maps/<registry folder>/<map path>.json`. A vanilla
registry omits its namespace (`damage_type`); a non-vanilla registry uses `<namespace>/<path>`.
The loader consumes the complete same-location resource stack in ResourceManager low-to-high pack
order. It never sorts layers and never reads only the winning resource.

For each valid layer, operations occur in this exact order:

1. top-level `replace` (default `false`) clears all lower accumulated entries when true;
2. required `values` entries run in decoded JSON order; direct and tag targets expand immediately;
3. optional `remove` string/tag list runs in list order and removes whole values;
4. the next higher pack layer starts.

Thus writes followed by removals in one layer leave removals effective. A missing/empty tag expands
to nothing. Unknown direct write targets emit `ERROR` on `Mekanism/FabricDataMaps` and are skipped,
matching the pinned NeoForge `DataMapLoader` diagnostic severity; unknown removals are no-ops. A valid
higher `replace` clears lower values, while an invalid higher layer has no effect at all.

Accepted value forms are bare `0.75`, bare object `{"absorption":0.75}`, wrapped
`{"value":0.75}`, and wrapped object plus `"replace":true`. The object-first MekaSuit codec is
preserved: `{"absorption":0.75,"replace":true}` is the bare value alternative and therefore has
entry replace false. Ordinary maps replace an existing value in every write, so the parsed entry
replace control flag does not change the result. Values outside `[0,1]` fail decoding.

`values` is mandatory but may be empty. `remove` defaults empty and accepts only string/tag lists;
Advanced remover objects are unsupported. Unknown ordinary JSON fields are not globally rejected,
matching vanilla record-codec tolerance. Any `neoforge:conditions` key is explicitly rejected because
conditions are outside this boundary.

Each reader is closed. Invalid key syntax, malformed structure, I/O failure, codec failure, or
unsupported conditions fails the whole resource layer. No writes from that layer publish; loading
continues with the remaining valid low/high layers. Every successful owner load publishes the new
candidate even when some resource layers failed, so the old snapshot is not retained. A failed high
`replace` consequently cannot clear valid low results. Unknown descriptor files in this descriptor catalog's exact registry folder warn and are skipped.
Files below another registry folder or namespace's `data_maps` tree are neither counted nor diagnosed;
this prevents Task 038 damage and Task 040 Chemical catalogs from warning about each other's siblings.

## Lifecycle and ownership

`MekanismFabricBootstrap` registers callbacks exactly once for
`SERVER_STARTING`, `END_DATA_PACK_RELOAD`, and `SERVER_STOPPED`. Initial and successful END loads run
synchronously on the server thread with the current `ResourceManager` and
`server.reloadableRegistries().get()`. A false END result does not parse, publish, or increment.
Callbacks never enqueue to the same server executor and join.

Sessions are stored in an identity map keyed by the actual `MinecraftServer`. Creation, load, query,
publish, and close enforce the owner/server thread. Duplicate creation, reentrant loading,
off-thread access and use after close fail. A fresh server starts at epoch 1. Stop removes the exact
session, closes it, and clears its snapshot.

The failed-start cleanup is source-traced through vanilla 1.21.1
`net/minecraft/server/MinecraftServer.java`: `runServer` reaches `stopServer` from its `finally` even
when startup throws. Fabric API's
`fabric-lifecycle-events-v1/src/main/java/net/fabricmc/fabric/mixin/event/lifecycle/MinecraftServerMixin.java`
invokes `SERVER_STARTING` immediately before `initServer()` in `runServer` and invokes
`SERVER_STOPPED` at the tail of `stopServer`. The owner therefore records the exact failed-start
server identity before rethrowing. Only the subsequent no-session stop for that same identity is
tolerated and consumes the marker; never-started and double-stop identities still fail. A successful
start carries no failed-start identity, and the normal `MEKANISM_FABRIC_DATAMAP_STOP_PROOF` marker is
emitted only after a real session was removed and closed.

Every load rebuilds this owner's descriptors from empty. Therefore a disappeared file publishes an
empty map rather than retaining the previous one; this is an explicit Fabric owner-local lifecycle
delta. Published snapshots are immutable copies containing only registry keys and values—never
resource managers, readers, providers, holders, or holder sets. Reference-holder queries use their
key after checking the damage-type registry. Direct-holder queries canonicalize the exact value with
`Registry.getResourceKey`; defaulted-registry fallback is forbidden. A valid missing attachment
returns null.

Per-resource errors still publish and increment. A global registry/catalog/owner invariant failure at
startup removes the session and fails startup. The same invariant failure after an already-successful
vanilla reload retains only the prior local snapshot and logs an explicit stale diagnostic; the END
callback catches the failure so it cannot turn the original vanilla future into a failure. This is
not joint rollback: already committed vanilla resources and tags are never claimed to roll back.

## Task 040 five-map Chemical catalog and atomic epoch

`FabricChemicalDataMaps` is the selected `IMekanismDataMapTypes` service provider. It retains the
Task 038 descriptor and adds five synchronized descriptors on `ChemicalRegistry.REGISTRY_KEY`:
`chemical_solid_tag`, fuel, radioactivity, cooled coolant, and heated coolant. Their persistent codecs
remain the admitted official codecs. Their declared `RegistryFriendlyByteBuf` codecs are the sole value
wire-format owners; coolant references encode resource names rather than raw holder IDs.

`FabricChemicalDataMapSession` is a distinct server-thread owner. Each successful load starts every map
from empty, uses the ordinary loader semantics above, validates every resulting key against the
Chemical registry, copies all five maps, reads the sole current world policy, and publishes one
immutable `ChemicalDataContext` with exactly one increased epoch. All five descriptors are present in
the candidate even when a result is empty. Fatal catalog/registry/owner failure at startup fails startup;
a fatal local failure after a prior epoch is caught by the Chemical callback and retains that prior
complete epoch. As in Task 038, malformed individual resource layers are isolated and do not claim
vanilla resource rollback.

Chemical and MekaSuit sessions share lifecycle callbacks but not state. `FabricChemicalDataMaps`
delegates damage-type queries to Task 038's server identity owner, while Chemical queries read the
current logical owner context and accept only exact registry reference/direct canonicalization.
Server stop removes/closes both owners through their respective callbacks. `ChemicalDataContext` is
ThreadLocal and immutable, so integrated server and physical client views of the same registered
Chemical objects do not mutate each other.

`processResources` admits exactly five generated Chemical data maps and 13 generated Chemical tags,
in addition to Task 038's two resources; every path occurs once and has a fixed SHA-256 in
`FABRIC_CHEMICAL.md`/the static JAR gate. Clean packaged startup requires
`MEKANISM_FABRIC_CHEMICAL_DATAMAP_PROOF phase=startup epoch=1 maps=5`. The real Chemical runtime pack
removes nuclear-waste radioactivity, producing epoch 2, then proves that real tank admission and
client tooltip behavior change with the authoritative context. Play publication uses the bounded
name-based protocol in `FABRIC_NETWORK.md`.

## Verification surfaces

`FabricDataMapSemanticsTest` contains exactly four ordinary methods for descriptor/catalog identity,
value alternatives, low-to-high/tag/remove behavior, and atomic invalid-layer/unknown-target/
condition behavior. `FabricDataMapResourceStackIntegrationTest` plus the three-method
`ChemicalDataMapOwnerIntegrationTest` are the two suites/four methods in the fresh server-side Knot
source set; they use real `PathPackResources`/`MultiPackResourceManager` where applicable, a
fresh non-root damage registry, immutable publication, reference/direct queries, disappearance and
close behavior. Task 038 landing totals were ordinary **68 suites / 324 tests**, transfer **2/8**, root
**1/1**, data-map Knot **1/1**, aggregate **72/334**. Task 040 candidate current totals are
**73/352**, **4/21**, **1/2**, **2/4**, aggregate **80/379**; jqwik remains three properties at 100,000 tries.

`scripts/fabric-packaged-smoke.sh` statically enforces the exact API/platform data-map class families,
absence of relocated `FabricDataMapType`, resource/service hashes/counts, fixture isolation and forbidden
architecture leakage. Normal server mode requires both Task 038 epoch-1/21-entry and Task 040
epoch-1/five-map proofs. `scripts/fabric-datamap-reload-smoke.sh` builds an
isolated remapped fixture JAR outside `fabric/build/libs`; on a disposable packaged server it queries
epochs 1–4. After readiness it creates the high-priority world pack and uses console `reload`, whose
vanilla `ReloadCommand.discoverNewPacks` rescan discovers and auto-enables the new pack; it does not
issue a stale-repository `datapack enable`. The gate then exercises an invalid high data-map layer
with vanilla reload success, a real reload-future failure with no increment, repair, and stop cleanup.
The failure is induced only by the isolated fixture JAR: its Fabric `SERVER_DATA` reload listener is
inert until the high pack contains the dedicated
`mekanism_datamap_reload_fixture:reload_failure.marker` resource, then throws from its synchronous
reload stage so `MinecraftServer.reloadResources` fails and `END_DATA_PACK_RELOAD` receives
`success=false`. Removing the marker permits the epoch-4 recovery reload. The listener neither
supplies data maps nor calls the production loader.

Phase-bounded log checkpoints reject every `ERROR` during startup, valid high-pack reload, repair, and
normal stop. The malformed-layer window requires exactly the single `Mekanism/FabricDataMaps` `ERROR`
for `mekanism:data_maps/damage_type/mekasuit_absorption.json`. The fixture-failure window requires the
fixture trigger, the explicit END-false/epoch-3-stability callback, a fresh epoch-3 published-state
probe, and the exact `WARN` text `Failed to execute reload`; it allows zero `ERROR` lines. Final total
accounting proves that the malformed-layer diagnostic is the only `ERROR` emitted by the complete gate.

Task 040 closes the selected Chemical maps, synchronized snapshot, context-local radiation/tooltip/tank
consumers, and complete selected service owner described below. Still deferred without partial claims:
conditions, Advanced mergers/removers, holder mixins, NeoForge facade/classes, generic data-map sync,
environmental radiation, external Chemical providers/transactions, and broader service owners.
