# Task 041 plan — recipe ingredients, generic execution/cache, and reload ownership

Status: **implemented as one isolated SOL/GLM candidate on branch `task041-recipe-cache-vertical`; parent review, integration, and release pending**

## Decision and baseline

- Approved code baseline: `71c5188ccfcaf23ec49845e88a2151d54bcf58d5`; the implementation candidate starts from the docs-only approval commit containing this plan.
- Official source oracle: `4646f509` (Mekanism `v1.21.1-10.7.19.85`).
- Starting slices: API/common/platform **98/164/33** (295 production sources); aggregate **80 suites / 382 tests**.
- Task 040 committed the next boundary: complete the item/fluid/Chemical ingredient values and creators, all three `InputHelper` branches, generic `CachedRecipe` scheduling and input caches, and real server/client recipe-reload ownership. Before recipe traffic grows, amortize the current Chemical per-query allocation and five-map lookup without shared mutable Chemical state or a global derived-value cache.
- The project now prefers complete 15–40+ dependency/ownership verticals. This task may exceed 40 because the three ingredient families, creator ownership, generic input caches, and reload lifecycle are one honest closure; no unrelated file may be added merely to pad it.

The planning pass returned **GO** for one **59 touched-production-source / 55 net-new-compiled-source** vertical. It materially advances the first Crusher path while deliberately stopping before the concrete recipe-type/serializer/machine SCC. A leaf-only `OneInputCachedRecipe` port would either truncate its public factories or pull many unrelated concrete recipe outputs and is not accepted.

## Exact production scope

`U` = newly admitted official source with no semantic source delta; `A` = newly admitted official source with a bounded adaptation below; `N` = new layer-neutral or Fabric source; `M` = existing compiled source modified; `R` = removed/relocated compiled source.

### API — 31 additions, two modifications

Existing modifications:

1. M `src/api/java/mekanism/api/chemical/Chemical.java`
2. M `src/api/java/mekanism/api/chemical/ChemicalDataContext.java`

Fluid prerequisites:

3. A `src/api/java/mekanism/api/fluid/IExtendedFluidTank.java`
4. A `src/api/java/mekanism/api/providers/IFluidProvider.java`

Ingredient value layer:

5. U `src/api/java/mekanism/api/recipes/ingredients/InputIngredient.java`
6. A `src/api/java/mekanism/api/recipes/ingredients/ItemStackIngredient.java`
7. A `src/api/java/mekanism/api/recipes/ingredients/FluidStackIngredient.java`
8. A `src/api/java/mekanism/api/recipes/ingredients/ChemicalStackIngredient.java`
9. U `src/api/java/mekanism/api/recipes/ingredients/IngredientType.java`
10. N `src/api/java/mekanism/api/recipes/ingredients/fluid/FluidIngredient.java`
11. N `src/api/java/mekanism/api/recipes/ingredients/chemical/ChemicalIngredientTypeRegistry.java`

Complete Chemical ingredient algebra:

12. A `src/api/java/mekanism/api/recipes/ingredients/chemical/ChemicalIngredient.java`
13. A `src/api/java/mekanism/api/recipes/ingredients/chemical/CompoundChemicalIngredient.java`
14. A `src/api/java/mekanism/api/recipes/ingredients/chemical/DifferenceChemicalIngredient.java`
15. A `src/api/java/mekanism/api/recipes/ingredients/chemical/EmptyChemicalIngredient.java`
16. A `src/api/java/mekanism/api/recipes/ingredients/chemical/IntersectionChemicalIngredient.java`
17. A `src/api/java/mekanism/api/recipes/ingredients/chemical/SingleChemicalIngredient.java`
18. A `src/api/java/mekanism/api/recipes/ingredients/chemical/TagChemicalIngredient.java`

Creator surface:

19. U `src/api/java/mekanism/api/recipes/ingredients/creator/IIngredientCreator.java`
20. A `src/api/java/mekanism/api/recipes/ingredients/creator/IItemStackIngredientCreator.java`
21. A `src/api/java/mekanism/api/recipes/ingredients/creator/IFluidStackIngredientCreator.java`
22. U `src/api/java/mekanism/api/recipes/ingredients/creator/IChemicalIngredientCreator.java`
23. U `src/api/java/mekanism/api/recipes/ingredients/creator/IChemicalStackIngredientCreator.java`
24. A `src/api/java/mekanism/api/recipes/ingredients/creator/IngredientCreatorAccess.java`
25. N `src/api/java/mekanism/api/recipes/ingredients/creator/IIngredientCreatorProvider.java`

Input/output and generic execution contracts:

26. U `src/api/java/mekanism/api/recipes/inputs/IInputHandler.java`
27. U `src/api/java/mekanism/api/recipes/inputs/ILongInputHandler.java`
28. A `src/api/java/mekanism/api/recipes/inputs/InputHelper.java`
29. U `src/api/java/mekanism/api/recipes/outputs/IOutputHandler.java`
30. A `src/api/java/mekanism/api/recipes/MekanismRecipe.java`
31. U `src/api/java/mekanism/api/recipes/cache/CachedRecipe.java`
32. U `src/api/java/mekanism/api/recipes/cache/CachedRecipeHelper.java`
33. U `src/api/java/mekanism/api/recipes/cache/ICachedRecipeHolder.java`

API classification is U/A/N/M/R **11/17/3/2/0**. Net API growth is 31 and the slice moves **98 → 129**.

### Common — 20 additions

Ingredient implementations and built-in type owner:

1. A `src/main/java/mekanism/common/recipe/ingredients/ChemicalIngredientCreator.java`
2. U `src/main/java/mekanism/common/recipe/ingredients/creator/ChemicalStackIngredientCreator.java`
3. U `src/main/java/mekanism/common/recipe/ingredients/creator/ItemStackIngredientCreator.java`
4. U `src/main/java/mekanism/common/recipe/ingredients/creator/FluidStackIngredientCreator.java`
5. A `src/main/java/mekanism/common/registries/MekanismChemicalIngredientTypes.java`

Recipe context and source seams:

6. N `src/main/java/mekanism/common/recipe/lookup/cache/RecipeCacheContext.java`
7. N `src/main/java/mekanism/common/recipe/lookup/cache/RecipeCacheSource.java`

Generic cache orchestration:

8. U `src/main/java/mekanism/common/recipe/lookup/cache/IInputRecipeCache.java`
9. A `src/main/java/mekanism/common/recipe/lookup/cache/AbstractInputRecipeCache.java`
10. A `src/main/java/mekanism/common/recipe/lookup/cache/SingleInputRecipeCache.java`
11. A `src/main/java/mekanism/common/recipe/lookup/cache/DoubleInputRecipeCache.java`
12. A `src/main/java/mekanism/common/recipe/lookup/cache/EitherSideInputRecipeCache.java`
13. A `src/main/java/mekanism/common/recipe/lookup/cache/TripleInputRecipeCache.java`
14. A `src/main/java/mekanism/common/recipe/lookup/cache/InputRecipeCache.java`

Input-type indices:

15. U `src/main/java/mekanism/common/recipe/lookup/cache/type/IInputCache.java`
16. A `src/main/java/mekanism/common/recipe/lookup/cache/type/BaseInputCache.java`
17. A `src/main/java/mekanism/common/recipe/lookup/cache/type/ComponentSensitiveInputCache.java`
18. A `src/main/java/mekanism/common/recipe/lookup/cache/type/ItemInputCache.java`
19. A `src/main/java/mekanism/common/recipe/lookup/cache/type/FluidInputCache.java`
20. A `src/main/java/mekanism/common/recipe/lookup/cache/type/ChemicalInputCache.java`

Common classification is U/A/N/M/R **5/13/2/0/0**. Net common growth is 20 and the slice moves **164 → 184**.

### Platform — four additions, two modifications

1. N `fabric/src/main/java/mekanism/fabric/recipe/FabricIngredientCreatorProvider.java`
2. N `fabric/src/main/java/mekanism/fabric/recipe/FabricRecipeCacheLifecycle.java`
3. N `fabric/src/main/java/mekanism/fabric/recipe/FabricRecipeCacheClientLifecycle.java`
4. N `fabric/src/main/java/mekanism/fabric/mixin/recipe/ClientPacketListenerMixin.java`
5. M `fabric/src/main/java/mekanism/fabric/MekanismFabricBootstrap.java`
6. M `fabric/src/main/java/mekanism/fabric/MekanismFabricClientBootstrap.java`

Platform classification is U/A/N/M/R **0/0/4/2/0**. Net platform growth is four and the slice moves **33 → 37**.

Overall U/A/N/M/R is **16/30/9/4/0**, exactly **59 unique touched production sources** and **55 net-new compiled sources**. Target slices are **129/184/37**, total **350 production sources**. Any implementation-time dependency that changes this arithmetic must be reported and justified before integration; silently widening the slice is forbidden.

## Dependency closure and bounded adaptations

### Narrow creator service instead of the broad facade

Do not admit official `IMekanismAccess`: its public JEI/EMI methods pull optional client integrations and the unfinished `MekanismAPI` facade. Add layer-neutral `IIngredientCreatorProvider`, loaded exactly once through existing `MekanismServices`, to supply the four creator singletons and the item-ingredient codec/component/cacheability bridge. The implementation is `FabricIngredientCreatorProvider`, registered by exactly one new service descriptor:

`src/main/resources/META-INF/services/mekanism.api.recipes.ingredients.creator.IIngredientCreatorProvider`

No Fabric type may appear in API/common signatures or bytecode. Zero/multiple service providers fail with the same exact ownership discipline as existing Task 040 services.

### Item ingredient compatibility

Replace NeoForge `SizedIngredient` with an `ItemStackIngredient` that owns vanilla `Ingredient` plus a positive count while preserving matching, required amount, representations, JSON and stream semantics. The bounded compatibility surface must round-trip vanilla item/tag forms and official `neoforge:compound` union forms, accepting the documented legacy child-field alias where the oracle does. Unknown custom NeoForge ingredient types fail decoding; they must not become permissive predicates.

Fabric custom-ingredient details stay inside `FabricIngredientCreatorProvider`. Exact-component/non-strict component construction and cacheability classification must remain faithful to the official creator behavior.

### Fluid ingredient compatibility

Add a layer-neutral immutable `FluidIngredient` algebra for single fluid holder, tag, compound union, and component predicate. It operates on project `mekanism.api.fluid.FluidStack` in integer mB and never uses Fabric's 81,000-droplet transfer unit. `FluidStackIngredient` owns that predicate plus a positive amount and preserves flat JSON, matching, representations, equality and registry-aware stream behavior. Unknown custom fluid types fail explicitly.

Adapt `IExtendedFluidTank` to project `INBTSerializable<CompoundTag>` and `IContentsListener`; declare the platform-neutral methods formerly inherited from NeoForge directly, retain internal `Action`/`AutomationType`, sizing, listener and registry-aware NBT semantics, and remove only `IFluidTank`/capability/`FluidAction` bridges. This is an internal tank contract, not a Fabric `Storage<FluidVariant>` provider or rollback promise. `IFluidProvider` uses project `FluidStack` and vanilla fluid naming; arbitrary NeoForge `FluidType` description overrides are not promised.

### Complete Chemical ingredient algebra

`ChemicalIngredientTypeRegistry` narrowly owns the six built-in IDs and reverse codec identity:

- `mekanism:compound`
- `mekanism:difference`
- `mekanism:empty`
- `mekanism:intersection`
- `mekanism:single`
- `mekanism:tag`

`MekanismChemicalIngredientTypes` registers those codecs once in official order and freezes before recipe decoding. Duplicate IDs, duplicate codec identity, late registration or missing built-ins fail. Replace `MekanismAPI.CHEMICAL_REGISTRY`/empty access with `ChemicalRegistry`, and replace only NeoForge codec helpers with equivalent Mojang composition. Preserve type-less single/tag maps, array compound forms, explicit six-type maps, exhaustive holder representations, expanded-holder stream semantics, positive true-long `ChemicalStackIngredient.amount`, and amount-after-ingredient stream order.

### Input handlers and generic execution

`CachedRecipe`, `CachedRecipeHelper`, `ICachedRecipeHolder`, `IInputHandler`, `ILongInputHandler`, `IOutputHandler`, `IInputRecipeCache`, and `IInputCache` remain byte-identical. `InputHelper` changes only NeoForge FluidStack to the project value and `MekanismAPI.logger` to an exact-category local logger. Item, fluid, ordinary Chemical, and constant-Chemical branches remain complete, including reset-versus-pause, true-long calculation and exact `Action.EXECUTE` calls. `MekanismRecipe` changes only logger ownership.

## Chemical hot-path cache contract

The cache is thread/context-local, never stored on a canonical `Chemical`, and never a static global map of derived values.

`ChemicalDataContext` binds a `ThreadLocal<Binding>`. A binding contains the exact immutable context object and an `IdentityHashMap<Chemical, DerivedCacheEntry>`. Binding the same context by identity retains the cache; binding a different context installs a fresh cache even when numeric epochs match; `clear()` removes the entire binding.

Refactor legacy derivation inputs into one volatile immutable copy-on-write `LegacyState` identity. A completed cache entry is valid only for the exact current `LegacyState`. `addAttribute` publishes a new state and invalidates only that Chemical on its next query. Caller-owned mutable attribute objects and illegal mutation of immutable data-map values are outside the contract.

`Chemical.derived()` must preserve all Task 040 boundaries:

1. cache hit returns the completed immutable `Derived` without allocation or descriptor lookup;
2. miss establishes the existing derivation frame and invokes virtual `updateFromDataMap` exactly once;
3. same-Chemical reentrancy observes the current immutable partial builder snapshot without recursion;
4. a different Chemical derives/caches independently and restores the outer frame;
5. only successful final snapshots are cached; exceptions are not cached;
6. direct `updateFromDataMap` outside a frame remains invalid;
7. successful context replacement, disconnect, stop and thread change cannot reuse old state; failed reload retaining the exact context may retain its cache.

The required amortization is one builder/list allocation and five descriptor lookups per `(thread, exact context identity, Chemical identity, legacy-state identity)`, not per query. Concurrency is thread-confined except for volatile immutable legacy-state publication; a query racing `addAttribute` may see one complete old/new state, while subsequent happens-before queries must see the new state.

## Recipe cache and reload ownership

### Layer-neutral context and source

`RecipeCacheContext` binds through a `ThreadLocal`:

- exact `RecipeManager` identity;
- positive epoch;
- a fresh identity token for every successful publication;
- thread-confined identity-keyed mutable state for cache instances.

Mutable initialized flags, indices and complex-recipe sets from the official cache classes must live under this binding, not on static/shared cache objects. New binding discards all cached candidates atomically; `clear()` releases manager and recipe references.

`RecipeCacheSource<RECIPE>` supplies immutable `List<RecipeHolder<RECIPE>>` from the bound manager, normally by wrapping one vanilla `RecipeType<RECIPE>` and `RecipeManager#getAllRecipesFor`, while filtering incomplete recipes as the official owner does. It does not add synthetic-smelting recipes.

Replace planned generic-cache `MekanismRecipeType` constructor dependencies with `RecipeCacheSource`. For lookups, a non-null `Level` must have the exact bound manager; null-level calls may use the bound manager; no bound context returns no recipes; mismatched non-null manager fails rather than consulting stale global state. `InputRecipeCache.EitherSideChemical` becomes generic over `MekanismRecipe<?> & BiPredicate<ChemicalStack,ChemicalStack>` instead of importing a concrete Chemical recipe abstraction. Use project `TriPredicate`.

### Server lifecycle

`FabricRecipeCacheLifecycle` binds the actual server `RecipeManager` at `SERVER_STARTED` epoch 1. Successful `END_DATA_PACK_RELOAD` binds a fresh context with incremented epoch only after vanilla replacement; failed reload preserves context identity, epoch and cache state. `SERVER_STOPPED` clears the server-thread binding. Chemical-data-map publication alone never advances the recipe epoch.

### Client lifecycle

Fabric has no sufficient high-level callback for every recipe packet. A client-only mixin injects with `require=1` at the tail of `ClientPacketListener.handleUpdateRecipes`, after `recipeManager.replaceRecipes`, recipe-book rebuilding and search-tree refresh. `FabricRecipeCacheClientLifecycle` binds on the Minecraft client thread, treats the first manager packet as epoch 1, increments each later successful update for that manager, clears on play disconnect/client stop, and rejects late updates after stop.

The mixin config and metadata must be client-only. Dedicated server startup must not resolve the mixin or any client lifecycle class. Kilt's pinned callback placement may be used only as semantic evidence, not copied as a compatibility layer.

## Explicit deferrals

The following remain out of Task 041:

- `OneInputCachedRecipe`, `TwoInputCachedRecipe`, and specialized cached recipes;
- `OutputHelper` and concrete multi-output records;
- `MekanismRecipeType`, `MekanismRecipeTypes`, and `MekanismRecipeSerializers`;
- concrete/basic recipe abstractions, serializers, and production recipe JSON;
- generated production recipes;
- machine/block/item/menu registration and Crusher;
- external item/fluid/Chemical provider, lookup, cache owner and transaction exposure;
- synthetic-smelting augmentation and optional JEI/EMI integration.

`OneInputCachedRecipe` cannot be admitted unchanged because its public factories mention crystallizing, electrolysis, fluid-to-fluid, item-to-fluid, item-to-Chemical, Chemical-to-Chemical and sawmill recipe families. `OutputHelper` similarly exposes deferred output records. Trimming those public overloads is not allowed. The next task may take the complete item-to-item recipe/type/serializer plus generic one-input execution SCC.

## Tests and executable acceptance

Add exactly **23 Fabric-local tests**, grouped by independent mutation risk rather than source count:

1. Chemical hot cache: five methods, extending `ChemicalDomainTest` for repeat-hit amortization, equal-epoch/different-context invalidation, same/different-Chemical reentrancy, copy-on-write legacy invalidation, and thread/clear/rebind isolation.
2. Ingredient/service/codec closure: one six-method Knot suite for item/tag/count and compound/components, fluid single/tag/compound/components in mB, all six Chemical ingredient types/aliases/expanded-holder stream, service singleton and type-registry ownership/failure.
3. Input handlers/generic execution: one six-method ordinary suite for item/fluid/true-long Chemical consumption, constant-Chemical pause behavior, operation/error/energy/tick scheduling, and helper short-circuit/output ordering/holder invalidation.
4. Generic input-cache context: one six-method ordinary suite for single/double/either/triple lookup, complex/component-sensitive fallback, recipe order/dedup, manager identity, equal-epoch fresh context, failed/successful reload, thread isolation and cleanup.

Predicted totals:

- ordinary **73/355 → 75/372**;
- transfer-Knot **4/21 → 5/27**;
- root-registry **1/2**;
- data-map-Knot **2/4**;
- aggregate **80/382 → 83/405**.

If implementation discovers that a test belongs in a different existing isolated source set, suite arithmetic may change only with explicit fixture/classpath-isolation justification; production behavior coverage and aggregate 23-test growth remain fixed unless a real additional mutation risk is documented.

Add isolated `recipeRuntimeFixture` sources and `scripts/fabric-recipe-runtime-smoke.sh`. Fixture classes/resources are process evidence, not JUnit counts, and must remain absent from production slices and the release JAR. A real disposable recipe type/serializer may exist only in the fixture.

Unified runtime acceptance must prove:

1. packaged dedicated server starts with all service descriptors and no client-class linkage;
2. a fixture recipe using item compound, fluid tag and Chemical ingredient loads through a real `RecipeManager`;
3. a generic input cache finds it and rejects component/type mismatches;
4. repeated Chemical queries on the recipe path derive once in epoch 1;
5. successful `/reload` advances server recipe context exactly once, removes old candidates and exposes the replacement;
6. failed reload preserves the usable context/cache;
7. a physical client receives recipe updates and the tail mixin publishes the matching new client epoch;
8. disconnect/reconnect clears client state and a fresh manager cannot reuse prior candidates;
9. integrated server/client threads retain distinct manager and Chemical-context identities;
10. fixture recipe type/serializer/classes/JSON are absent from the release JAR.

Static gates additionally enforce exact ingredient/cache/platform class families, exact service descriptor contents, client-only mixin metadata, no NeoForge/Kilt/Connector/test/fixture leakage, no Fabric references from API/common bytecode, no production `data/mekanism/recipe/**`, and one release JAR.

## Candidate verification and review

The implementation candidate must produce a source identity/delta report against `4646f509`: all 16 `U` paths by blob identity, all 30 `A` and four `M` paths by explicit authorized delta family, and all nine `N` sources separately. It must verify exact slice membership and run at least:

```bash
./gradlew -p fabric --no-daemon --console=plain \
  test fabricTransferTest fabricRegistryTest fabricDataMapTest \
  verifyFabricTestContract remapJar
./gradlew -p fabric --no-daemon --console=plain check build
./gradlew -p fabric --no-daemon --console=plain \
  verifyApiSliceNoNeoForge verifyCommonSliceNoNeoForge verifyPlatformSliceNoNeoForge
scripts/scan-platform-deps.py
./fabric-build.sh
FABRIC_SMOKE_STATIC_ONLY=1 scripts/fabric-packaged-smoke.sh
scripts/fabric-packaged-smoke.sh
scripts/fabric-recipe-runtime-smoke.sh
scripts/fabric-chemical-runtime-smoke.sh
scripts/fabric-datamap-reload-smoke.sh
scripts/fabric-paired-network-smoke.sh
scripts/fabric-configuration-rejection-smoke.sh
scripts/fabric-client-window-smoke.sh
```

Because this task changes codecs, recipe reload ownership, ThreadLocal state and a vanilla client hook, one independent read-only adversarial review is mandatory before integration. It must examine Chemical reentrancy/invalidation, JSON/wire compatibility, manager identity/thread isolation, failed reload/disconnect cleanup, client-only mixin/dedicated safety, public API deltas, source identity and excluded SCCs.

Candidate work must not run `./build.sh --clean`, push, deploy, access production or use the mirror. The parent checks the real diff and independently reruns final semantic/runtime gates after integration; only the parent performs the final clean release and records the final commit, one-JAR count and SHA-256.

## Required repository records

The candidate updates exact slices and build/static/runtime gates, adds `docs/FABRIC_RECIPES.md`, and updates `FABRIC_CHEMICAL.md`, `FABRIC_PORT.md`, `PORT_MATRIX.md`, `API_DELTAS.md`, `FABRIC_SMOKE.md`, and this plan with actual arithmetic/evidence/non-claims. `MODEL_IMPLEMENTATION_COMPARISONS.md` is updated only when the mandatory independent review actually occurs.

No implementation, integration, release, push or deployment is claimed by this approval plan.


## Candidate implementation outcome (GLM, branch `task041-recipe-cache-vertical`)

Implemented from clean approval commit `e4d85c3e` in an isolated worktree by the sole GLM implementation
candidate. Final slice arithmetic matches the plan exactly: API/common/platform **129/184/37** (350
compiled production sources), **59 unique touched production sources**, **55 net-new compiled sources**,
tests ordinary/transfer/root/data-map/aggregate **75/372**, **5/27**, **1/2**, **2/4**, **83/405** (23 new
Fabric-local tests: 5 hot-cache methods added to `ChemicalDomainTest`, 6-method Knot ingredient closure
suite, 6-method input-handler/generic-execution suite, 6-method generic input-cache context suite).

Classification corrections against the plan's letter (counts unchanged): `BaseInputCache` and
`ComponentSensitiveInputCache` were classified `A` but needed no source delta because the per-binding
cache instances already isolate their mutable maps; they compile byte-identical to `4646f509` and are
reported as `U`-effective. `ItemStackIngredient.logMissingTags` cannot report the exact empty tag value
(vanilla ingredients are not introspectable into values), so it uses the official non-simple branch
message. Item wire round-trips flatten tag-valued compound children to their current stacks through the
vanilla contents codec, the platform wire behavior; compound union JSON round-trips the typed form.
`from(int, List<TagKey<Item>>)` uses a union of per-tag vanilla ingredients because vanilla has no public
multi-tag value construction. The item/fluid non-strict component predicates match the official semantics
(stacks missing an expected component do not match).

Candidate gates personally passed (see the candidate report for timings): focused suites, full
`check build`, all slice/no-NeoForge checks, `scan-platform-deps.py`, `fabric-build.sh`,
`FABRIC_SMOKE_STATIC_ONLY=1 scripts/fabric-packaged-smoke.sh`, full packaged dedicated-server smoke, and
the new `scripts/fabric-recipe-runtime-smoke.sh`. The candidate did not run `./build.sh --clean`, did not
push or deploy, and did not touch production or the mirror. The mandatory independent adversarial review
has not occurred; integration and the parent clean release remain pending.
