# Task052 continuation12 — canonical chemical ingredient type registry (prerequisite)

Baseline: clean parent carrying `TASK052_CONTINUATION_12.md` (parent `5e0a9c34`). WIP branch `pi-agent-53a5fc3d-5926-438`
(`5059aac7`, merge `ae14b242`) was merged FIRST and all prior ancestry is preserved (merge commit on branch `task052-merge`).
This is the independently owned chemical ingredient type registry prerequisite, not Task052 acceptance, not whole MekanismAPI,
and not module/robit work. No usable-machine, runtime, release, visual or block-placement claim.

## Real owner changes

- `src/api/java/mekanism/api/recipes/ingredients/chemical/ChemicalIngredientTypeRegistry.java`: the two authoritative maps
  (`TYPES` LinkedHashMap, `CODEC_IDS` IdentityHashMap) are replaced by a single canonical vanilla
  `MappedRegistry<MapCodec<? extends ChemicalIngredient>>`. `install` accepts only the plain, non-defaulted registry with the
  canonical key `mekanism:chemical_ingredient_type`; `registry()` fails loudly when nothing is installed (no substitute store,
  no private fallback). `register` keeps the prior failure surface: duplicate ID and duplicate codec identity are rejected
  before the vanilla write, and late registration after freeze throws. `freeze` re-validates all six built-ins every call and
  freezes once. `codec(id)` translates the native missing-id `null` into `IllegalArgumentException` ("Unknown chemical
  ingredient type ..."), so unknown IDs never silently default; `idOf(codec)` uses the native identity value map via
  `getKey`. All prior public signatures and the six `ResourceLocation` constants are unchanged.
- `fabric/src/main/java/mekanism/fabric/chemical/FabricChemicalIngredientTypeRegistryLifecycle.java` (new, platform slice):
  construction-thread owner that allocates the single native non-intrusive/non-defaulted registry, installs that exact instance,
  publishes it into the vanilla root with Fabric `MODDED`+`SYNCED` through the existing `FabricRegistryBuilder`, verifies exact
  root identity/key and attributes, drives `MekanismChemicalIngredientTypes` (six codecs, official order), verifies per-entry
  codec identity, `getKey`, canonical holder and `idOf` round trip, then freezes. Already-frozen calls are idempotent and return
  the one canonical registry; allocation/root/population/freeze are owner-thread-only; any failure marks the lifecycle `FAILED`.
- `src/main/java/mekanism/common/registries/MekanismChemicalIngredientTypes.java`: now supplies only the exact six codec
  declarations in official order and the freeze; it no longer owns the store and does not allocate a registry. `ensureRegistered`
  is idempotent (early return when already frozen).
- `fabric/src/main/java/mekanism/fabric/MekanismFabricBootstrap.java`: the bootstrap now calls
  `FabricChemicalIngredientTypeRegistryLifecycle.initialize()` in the same window where the chemical registry is constructed
  (line 63), replacing the direct `MekanismChemicalIngredientTypes.ensureRegistered()` call. The existing
  `verifyIngredientCreatorService()` still reads `ChemicalIngredientTypeRegistry.isFrozen()`.

No `MekanismAPI` alias was ported, no public root field was dropped or faked, and no generic Forge/Fabric facade was introduced.

## Initialization path and the plain-JVM boundary

Proven empirically against the pinned 1.21.1 official-mapped compile JAR: in a plain (non-Knot) JVM the vanilla root is already
frozen after `Bootstrap.bootStrap()` — a probe `WritableRegistry.register` into `BuiltInRegistries.REGISTRY` throws
`IllegalStateException: Registry is already frozen`. The Fabric loader window (used by production and by the isolated
`fabricRegistryTest` Knot worker) keeps the root writable at mod-init, which is exactly how the pre-existing chemical registry
owner already root-registers successfully. Therefore:

- production keeps the real Fabric lifecycle contract: the construction owner performs the genuine root + `SYNCED` publication;
- the loader-window integration worker (`fabricRegistryTest`) exercises the real construction owner;
- `IngredientServiceCodecClosureTest` runs in the Knot `fabricTransferTest` source set, so it also calls the real construction
  owner (`FabricChemicalIngredientTypeRegistryLifecycle.initialize()`), exactly as production does — it does **not** install a
  native registry by hand;
- only the ordinary/local `ChemicalTestBootstrap` bootstrap, which cannot publish into the root that
  `Bootstrap.bootStrap()` already froze, installs the same genuine native registry directly, mirroring how it already installs
  the non-root chemical registry. No second authoritative store is created and no production path skips root publication.

Consumers updated to the real owner: `IngredientServiceCodecClosureTest`, `FabricRecipeRegistrationIntegrationTest`,
`RecipeRuntimeServerFixture`, `MekanismFabricBootstrap`. Ordinary tests that only need the frozen registry keep calling
`MekanismChemicalIngredientTypes.ensureRegistered()` after the shared bootstrap installed it.

## Executed verification

Classpath generated fresh through Loom 1.13.6 for Minecraft 1.21.1 official mappings and resolved/remapped Fabric APIs
(`scripts/task052-renderer-classpath.init.gradle`).

- **PASS actual changed-owner compilation** (`javac -proc:none` against the pinned classpath, real in-repo source path):
  `ChemicalIngredientTypeRegistry`, `MekanismChemicalIngredientTypes`,
  `FabricChemicalIngredientTypeRegistryLifecycle` — 0 errors.
- **PASS changed test/fixture source compilation**: `FabricRegistryRootIntegrationTest`, `ChemicalTestBootstrap`,
  `IngredientServiceCodecClosureTest`, `FabricRecipeRegistrationIntegrationTest` — 0 errors.
- The existing three ordinary test imports that still call `ensureRegistered` continue to compile because the shared bootstrap
  installs the registry first.

Focused check (one new method, `@Order(3)`, in the existing isolated root-registry worker; total root-registry methods 2 -> 3):
`productionChemicalIngredientTypeRegistryIsCanonicalRootedSyncedAndFrozen` asserts the construction owner's registry is the API
seam's registry, size 6, frozen, root-published exact identity and key, `MODDED`+`SYNCED`, per-id exact codec identity and
`idOf`/`get` round trip in official order, unknown-id `IllegalArgumentException`, duplicate/late registration
`IllegalStateException`, second-`install` rejection, and idempotent already-frozen construction. **This method was not executed**:
it lives in `fabricRegistryTest`, whose compilation depends on the whole unported production slice, so the Gradle task cannot
run. It is source-compiled and source-reviewed, not run.

## Honest remaining blockers

`./gradlew -p fabric fabricRegistryTest` (and `compileJava`) **fail before the test runs** on the pre-existing unported
production slice: 100 errors from `mekanism.common.content.oredictionificator`, `common.tile.machine`, `common.tile.prefab`,
`common.content.transporter`, plus NeoForge-typed owners (`net.neoforged.neoforge.fluids`/`capabilities`,
`DynamicNetwork` EffectiveSide, capability caches). This is the same broader WIP blocker recorded in REVIEW17; the three
changed owners compile, but the full slice does not compile yet, so no end-to-end task was executed and no whole-MekanismAPI,
root, or Task052 compile is claimed.

Gate updates that stay consistent with the new real owner: `fabric/platform-slice.txt` +1 (59 -> 60, inserted in slug order),
`fabric/build.gradle` platform-slice assertion 59 -> 60, root-registry `@Test` count 2 -> 3, root-registry JUnit total 2 -> 3
and aggregate 467 -> 468, and `scripts/fabric-packaged-smoke.sh` `expected_platform_chemical` gains
`FabricChemicalIngredientTypeRegistryLifecycle.class`.

No runtime/client fixture, no production/mirror/deploy/push, no reference write/copy, no index/zvec, no slice widening beyond
the one audited real owner, and no module/robit or MekanismAPI work was performed.
