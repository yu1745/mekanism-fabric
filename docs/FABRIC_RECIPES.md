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

## Task 042A — output handling and one-input cached recipe execution

Task 042A implements the approved 042A half of `TASK042_PLAN.md` from clean commit `cd263092`, in an isolated worktree. `TASK042_PLAN.md` recorded that the
originally requested full vertical (output handling plus **registered** one-input recipe types and their decoders) is blocked: `Recipe#getType()` is abstract and the
vanilla `RecipeManager` calls it on the live reload path, while this repository has no by-key holder mechanism, no recipe-type registration ownership, no
serializers, and no admitted `MekanismItems` family for the ten `getToastSymbol()` holders.

042A delivers the requested subject matter at the source level without any production registry mutation. Slices move **129/184/37 → 163/184/37** (34 new API sources,
384 compiled production sources); common and platform are unchanged. Totals move to ordinary **76 suites/378 tests**, transfer **5/27**, root **1/2**, data-map
**2/4**, aggregate **84/411**.

### Identity classification against `4646f509`

`U` byte-identical (12): `ChemicalChemicalToChemicalRecipe`, `ChemicalToChemicalRecipe`, `ItemStackChemicalToItemStackRecipe`, `ItemStackChemicalToObjectRecipe`,
`ItemStackToChemicalRecipe`, `ItemStackToItemStackRecipe`, `BiChemicalRecipeInput`, `ChemicalRecipeInput`, `FluidChemicalRecipeInput`, `ItemChemicalRecipeInput`,
`SingleChemicalRecipeInput`, `SingleItemChemicalRecipeInput`.

`A` bounded adaptation (21): the other 14 recipe abstractions (including `MekanismRecipeTypes`, whose 26 `DeferredHolder.create` sites become concrete
recipe-type instances in the new holder) plus the 5 `FluidStack`-bearing `vanilla_input` records and the two task targets `cache/OneInputCachedRecipe` and
`outputs/OutputHelper`.

`N` new source (1): `mekanism/api/recipes/MekanismKeyedHolder`, the layer-neutral keyed holder.

Exactly four authorized delta families are present and no others:

1. `net.neoforged.neoforge.fluids.FluidStack` → project `mekanism.api.fluid.FluidStack` (14 files: the 2 targets, 7 abstractions, 5 `vanilla_input` records);
2. `net.neoforged.neoforge.common.util.TriPredicate` → project `mekanism.api.functions.TriPredicate` (1 file, `PressurizedReactionRecipe`);
3. `MekanismAPI.MEKANISM_MODID` → literal `"mekanism"` (the Task 032 precedent), which also removes the whole 15-file module/gear/radial API group from the closure;
4. `DeferredHolder.create(Registries.RECIPE_TYPE|ITEM, key)` → `MekanismKeyedHolder`.

`OutputHelper` semantics are unchanged apart from the `FluidStack` import: both `operations == 0` early returns, `clampToInt(amountUsed / toAmount)`,
`INPUT_DOESNT_PRODUCE_OUTPUT` versus not-enough-space chosen from `tank.getNeeded() > 0` and `slot.getLimit(slot.getStack()) - slot.getCount() > 0`, item output count
multiplication, the secondary-output loop, and `shouldContinueChecking()` ordering. `OneInputCachedRecipe` keeps `SEPARATOR_OUTPUT_EMPTY` and
`FLUID_OPTIONAL_ITEM_OUTPUT_EMPTY` as distinct predicates, keeps `sawing`'s `ConstantPredicates.alwaysFalse()` output check, and keeps `finishProcessing`'s
null/empty guard.

### Keyed holders and recipe type identity (the one new mechanism)

`MekanismKeyedHolder<T>` implements `Holder<T>` and `Supplier<T>` over a `ResourceKey<T>` and is dependency free, because the API layer may not reference the common
layer. Two forms exist: `keyed(...)` over an identity stable, non-registered value, and `registryKeyed(...)` resolving lazily by key out of an existing registry.
Whenever a vanilla delegate is present all `Holder` queries delegate to it; otherwise the key answers `is`/`unwrapKey`/`unwrap`/`kind` and tag lookups are empty.
Nothing is written into any registry.

`MekanismRecipeTypes.TYPE_*` therefore hold 26 concrete, **non-registered**, identity stable `RecipeType` instances. Vanilla `RecipeManager` groups by
`recipeholder.value().getType()` identity, so in-memory recipes, the `OneInputCachedRecipe` factories, and `byType` lookups are fully functional. The ten toast-item
keys are resolved by key against the vanilla `BuiltInRegistries.ITEM`; while the `MekanismItems` family is not admitted those holders are non-null and key-correct
but legitimately **unbound**, and `value()` fails closed. That limitation is an explicit non-claim and is asserted as such.

### Verification

Six new ordinary methods, in exactly one new suite, `RecipeOutputHandlingTest` (no Knot, no registry mutation, no network): the chemical tank matrix (execute
true-`long` growth, `operations == 0`, `INPUT_DOESNT_PRODUCE_OUTPUT` versus not-enough-space); the fluid tank matrix (the `Integer.MAX_VALUE` simulate cap, integer
truncation, foreign/full/zero-capacity tanks); the item slot matrix (empty-output no-op, count multiplication, simulate/execute split, slot-limit gating);
`ChanceOutput` ordering and per-iteration refresh through by-hand `OperationTracker` counts; the three record handlers' member ordering and tracker short-circuit;
and the `OneInputCachedRecipe` empty guards plus `getType()` identity stability, non-registration, and key-correct-but-unbound toast holders.

Gates run for 042A: the four test tasks plus `verifyFabricTestContract` and `remapJar`; `check build`; the three slice no-NeoForge checks; `scan-platform-deps.py`;
`fabric-build.sh`; and `FABRIC_SMOKE_STATIC_ONLY=1 scripts/fabric-packaged-smoke.sh`. The static packaged gate now admits exactly the new
`mekanism/api/recipes/**` outer and nested classes (including `vanilla_input` records, `MekanismKeyedHolder`, `OutputHelper$1..$7`, and
`MekanismRecipeTypes$1`) and rejects the 042B families (`recipes/basic/**`, `MekanismRecipeSerializers`, `common/recipe/MekanismRecipeType*`,
`IMekanismRecipeTypeProvider`, `RecipeTypeRegistryObject`, `RotaryInputRecipeCache`, `common/recipe/serializer/**`, every `mekanism/client/**`,
`MekanismItems*`, and `mekanism/api/MekanismAPI.class`), plus serializers, production recipe JSON, and every fixture/test class.

`./build.sh --clean` and the real dedicated-server/client smokes are deliberately not run: 042A introduces no lifecycle, registry-frozen, networking, or runtime
owner.

Non-claims: no recipe-type registration; no recipe serializers; no production recipe JSON; no `MekanismRecipeType`/`IMekanismRecipeTypeProvider`/
`RecipeTypeRegistryObject`; no concrete/basic recipe implementations; no `MekanismItems` and therefore no `getToastSymbol()`-correct items; no datapack-loaded
Mekanism recipes; no machines or tile entities; no JEI/EMI or client recipe viewer; no item/fluid/Chemical transaction or storage exposure; no machine-facing
`InputRecipeCache` index; no broader Fabric integration. Recipe-type and serializer registration, the concrete recipe family, and the item-registration boundary
remain the separately approved 042B, which must reuse these exact `RecipeType` instances so `byType` identity grouping survives.
