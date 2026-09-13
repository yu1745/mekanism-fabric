# Fabric recipe ingredient/cache/reload vertical (Task 041)

## Status and boundary

Task 041 implements the recipe ingredient, generic execution/cache, and reload-ownership vertical approved by `TASK041_PLAN.md`, starting from approval commit
`e4d85c3e`. The compiled slices move **98/164/33 → 129/184/37** (350 production sources) and the executable test contract moves to ordinary **75 suites/372 tests**,
transfer-Knot **5/27**, root-registry **1/2**, data-map-Knot **2/4**, aggregate **83/405**.

This vertical owns:

- the complete item/fluid/Chemical ingredient value layer and creator surface;
- all four `InputHelper` branches (item, fluid, ordinary Chemical, constant Chemical) with reset-versus-pause behavior;
- generic `CachedRecipe`/`CachedRecipeHelper`/`ICachedRecipeHolder` execution contracts;
- the generic input-cache family (`Single`/`Double`/`EitherSide`/`Triple`) with per-binding mutable state;
- the Chemical exact-context/thread-local hot cache required by the Task 040 gate;
- real server recipe lifecycle (`SERVER_STARTED` epoch 1, `END_DATA_PACK_RELOAD` advance/retain, `SERVER_STOPPED` clear) and a client-only tail mixin after
  `handleUpdateRecipes`.

It does **not** add `OneInputCachedRecipe`/`TwoInputCachedRecipe`, `OutputHelper`, `MekanismRecipeType(s)`/serializers, concrete recipe abstractions or production
recipe JSON, machines, JEI/EMI, or a Fabric fluid storage/transaction contract.

## Ingredient values and creators

`IngredientCreatorAccess` now delegates to a narrow `IIngredientCreatorProvider` service (`mekanism.api.recipes.ingredients.creator.IIngredientCreatorProvider`)
loaded exactly once through `MekanismServices` with the same zero/multiple-provider failure discipline as every other Mekanism service. The implementation is
`mekanism.fabric.recipe.FabricIngredientCreatorProvider`, registered by exactly one new packaged service descriptor. The broad `IMekanismAccess` facade remains
unadmitted.

- **Item**: `ItemStackIngredient` owns a vanilla `Ingredient` plus a positive count, replacing `SizedIngredient`. The flat codec composes the vanilla
  item/tag map form inline with `count` (default one on decode, always written like official `optionalFieldAlwaysWrite`), round-trips the official `neoforge:compound` typed union form including the legacy
  `children` field alias, and fails unknown custom types explicitly. Non-strict data component predicates created from stacks with non-default components are carried
  by the ingredient and mark quick per-item cache mapping invalid. The stream codec syncs concrete-item forms exactly; tag-valued children flatten to their current
  stacks through the vanilla contents codec, the platform wire behavior.
- **Fluid**: a new layer-neutral immutable `FluidIngredient` algebra (single holder, tag, compound union, component predicate) operates on project
  `mekanism.api.fluid.FluidStack` in integer mB; `FluidStackIngredient` owns the predicate plus a positive amount and preserves the flat JSON
  (`fluid`/`tag` inline with `amount`, default 1,000 mB on decode and always written), official `neoforge:compound` (including the legacy `ingredients` alias) and
  `neoforge:components` typed forms, explicit-empty versus dynamically empty-tag behavior, positive wire amount validation, matching, representations, equality,
  and stream behavior. Malformed or unknown fluid ingredient types fail
  explicitly. `IExtendedFluidTank` declares the platform-neutral methods formerly inherited from the NeoForge tank parent and drops only the
  `IFluidTank`/`FluidAction` capability bridges; `IFluidProvider` uses the project stack and fluid-state-derived naming (no `FluidType` override promise).
- **Chemical**: the six official ingredient implementations are admitted with the planned adaptations (`ChemicalRegistry` for registry/empty-key access, an exact
  category logger, and vanilla replacements for the three platform codec helpers: exclusive-or, type dispatch with fallback, and the aliased children field).
  `ChemicalIngredientTypeRegistry` narrowly owns the six built-in `mekanism:compound|difference|empty|intersection|single|tag` IDs and reverse codec identity;
  `MekanismChemicalIngredientTypes` registers them once in official order and freezes before recipe decoding. Duplicate IDs, duplicate codec identity, late
  registration, and missing built-ins fail. Type registration is deliberately triggered from the mod bootstrap (and isolated test bootstraps), not from the creator
  service class, because the ingredient codecs consult the creator service during their own initialization.

## Generic execution and input caches

`CachedRecipe`, `CachedRecipeHelper`, `ICachedRecipeHolder`, `IInputHandler`, `ILongInputHandler`, and `IOutputHandler` are byte-identical to the oracle.
`InputHelper` changes only the project `FluidStack` and an exact-category logger; item, fluid, ordinary Chemical, and constant-Chemical branches are complete,
including reset-versus-pause and exact `Action.EXECUTE` consumption.

The generic caches replace the deferred `MekanismRecipeType` constructor dependency with `RecipeCacheSource<RECIPE>` (one vanilla `RecipeType` wrapper that filters
incomplete recipes like the official owner, no synthetic smelting). `RecipeCacheContext` binds through a `ThreadLocal` the exact `RecipeManager` identity, a positive
epoch, a fresh identity token per successful publication, and thread-confined identity-keyed mutable state holding the initialized flags, input-cache indices, and
complex-recipe sets. A new binding discards all cached candidates atomically and `clear()` releases manager and recipe references. Lookups: no bound context returns
no recipes; null-level calls use the bound manager; a non-null level whose manager differs from the exact bound manager fails instead of consulting stale global
state. `InputRecipeCache.EitherSideChemical` is generic over `MekanismRecipe<?> & BiPredicate<ChemicalStack, ChemicalStack>` with explicit extractors; triple caches
use the project `TriPredicate`.

## Chemical hot cache

The Task 040 per-query derivation is replaced by a thread/context-local cache with no shared mutable Chemical state and no global derived-value map:

- `ChemicalDataContext` binds a `ThreadLocal<Binding>` pairing the exact immutable context with an `IdentityHashMap<Chemical, DerivedCacheEntry>`. Re-binding the
  same context instance by identity retains the cache; binding a different instance installs a fresh cache even when the numeric epochs match; `clear()` removes the
  entire binding.
- `Chemical` keeps its legacy derivation inputs in one volatile immutable copy-on-write `LegacyState`; `addAttribute` publishes a new state and invalidates only that
  chemical's entry on its next query. A completed entry is valid only for the exact current state; a query racing the publication sees one complete old/new state and
  subsequent happens-before queries see the new one.
- `derived()` preserves every Task 040 boundary: cache hits return the completed immutable value without allocation or descriptor lookup; misses establish the
  derivation frame and invoke the virtual `updateFromDataMap` exactly once; same-chemical reentrancy observes the immutable partial snapshot; a different chemical
  derives independently and restores the outer frame; exceptions are not cached; direct `updateFromDataMap` outside a frame remains invalid.
- The amortization is one builder/list allocation and five descriptor lookups per `(thread, exact context identity, chemical identity, legacy-state identity)`.

## Server and client reload ownership

`FabricRecipeCacheLifecycle` binds the actual server `RecipeManager` at `SERVER_STARTED` epoch 1. A successful `END_DATA_PACK_RELOAD` binds a fresh context with an
incremented epoch only after vanilla replaced the recipes; a failed reload preserves the context identity, epoch, and cache state; `SERVER_STOPPED` clears the
server-thread binding. Chemical data-map publication alone never advances the recipe epoch.

Fabric has no sufficient high-level callback for every recipe packet, so a client-only mixin (`mekanism.fabric.mixin.recipe.ClientPacketListenerMixin`) injects with
`require=1` at the tail of `ClientPacketListener.handleUpdateRecipes`, after `recipeManager.replaceRecipes`, recipe-book rebuilding, and search-tree refresh. The
mixin config (`mekanism.client.mixins.json`) is client-only (`client` list only, no common/server list) and environment-scoped `client` in `fabric.mod.json`, so a
dedicated server never resolves it or any client lifecycle class. `FabricRecipeCacheClientLifecycle` binds on the Minecraft client thread, treats the first manager
packet as epoch 1, increments for each later successful update of the same manager, resets to epoch 1 for a fresh manager (new connection), clears on play disconnect
and client stop, and rejects late updates after stop.

## Verification

Verification surfaces: focused and full `check build` with the fixed test totals; the isolated Knot ingredient closure suite; slice/no-NeoForge checks;
`scan-platform-deps.py`; `fabric-build.sh`; static and packaged dedicated-server smoke; and the new real `scripts/fabric-recipe-runtime-smoke.sh`. The runtime gate
loads a fixture recipe using an item compound, fluid tag, and chemical ingredient through the real server `RecipeManager`, finds and rejects through the generic
triple input cache, proves mismatched-manager failure with the real server level, advances the server recipe context exactly once on a successful `/reload`
(removing old candidates and exposing the replacement), retains the usable context through a deliberately failing reload, publishes the matching client epochs
through the tail mixin, proves disconnect cleanup and fresh-manager freshness on a same-JVM reconnect, proves same-JVM distinct manager/chemical-context thread
ownership, explicitly stops the client and requires the late-update rejection marker, refreshes the remapped artifact before inspection, and verifies the fixture
recipe type/serializer/classes/JSON are absent from the release JAR.

The derive-once amortization itself is proven by the tracking-override unit contracts in `ChemicalDomainTest` (invocation counting through the virtual
`updateFromDataMap`); the runtime gate proves value stability and exact context-instance retention on the real recipe path.
