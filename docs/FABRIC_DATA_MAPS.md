# Fabric ordinary data maps

## Task 038 boundary

Task 038 implements one connected server-side data-map vertical: `MekaSuitAbsorption` on
`Registries.DAMAGE_TYPE`. It is ordinary, unconditional, and non-synced. The API record keeps its
codec, constructor, public ID and serialized forms; its only source adaptation is replacing the
executable `MekanismAPI.MEKANISM_MODID` reference with the stable literal namespace `"mekanism"`.
No public descriptor API was added. `FabricDataMapType` is a platform-internal identity descriptor:
descriptor equality is object identity, while catalog collisions use `(registryKey, id)`, so the same
ID remains legal on another registry.

This work was audited against NeoForge 21.1.200 without copying its LGPL implementation:

- sources: `/home/wangyu/.gradle/caches/modules-2/files-2.1/net.neoforged/neoforge/21.1.200/f0f15283b397e14e812ca49eef21b074f2c484e5/neoforge-21.1.200-sources.jar`
- source SHA-256: `7b31b7f4b817c90b8aa05ade830d761223dff07967aab49ab1f197d51c4d1420`
- binary: `/home/wangyu/.gradle/caches/modules-2/files-2.1/net.neoforged/neoforge/21.1.200/13d648aad7dcdb2a68551a407027620fa32c0afa/neoforge-21.1.200-universal.jar`
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
to nothing. Unknown direct write targets warn and are skipped; unknown removals are no-ops. A valid
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
`replace` consequently cannot clear valid low results. Unknown descriptor files warn and are skipped.

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

## Verification surfaces

`FabricDataMapSemanticsTest` contains exactly four ordinary methods for descriptor/catalog identity,
value alternatives, low-to-high/tag/remove behavior, and atomic invalid-layer/unknown-target/
condition behavior. `FabricDataMapResourceStackIntegrationTest` is the only source and method in its
fresh server-side Knot source set; it uses real `PathPackResources`/`MultiPackResourceManager`, a
fresh non-root damage registry, immutable publication, reference/direct queries, disappearance and
close behavior. Current exact totals are ordinary **68 suites / 324 tests**, transfer **2/8**, root
**1/1**, data-map Knot **1/1**, aggregate **72/334**; jqwik remains three properties at 100,000 tries.

`scripts/fabric-packaged-smoke.sh` statically enforces the one API class, exact eight platform class
entries, resource hashes/counts, and forbidden architecture leakage, and its normal server mode now
requires the real startup epoch-1/21-entry proof. `scripts/fabric-datamap-reload-smoke.sh` builds an
isolated remapped fixture JAR outside `fabric/build/libs`; on a disposable packaged server it queries
epochs 1–4, exercises a valid high replace/remove layer, an invalid high data-map layer with vanilla
reload success, a real vanilla reload-future failure with no increment, repair, and stop cleanup.
The fixture supplies no data map and never calls the loader directly.

Deferred without partial compatibility claims: Chemical data maps, synced maps, conditions,
Advanced mergers/removers, holder mixins, NeoForge facade/classes, radiation, tooltip/tank consumers,
networking, and broader service owners.
