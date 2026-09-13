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

Parent follow-up to candidate `436fb30e`: the existing fresh-JVM tests originally checked only API holders after registration. Adding common `get()` / `value()` /
`getRecipeType()` checks reproduced a `StackOverflowError` in both load orders: the specialized holder called `super.value()`, which virtually dispatched back through
its own `get()`. The fix calls the parent's concrete `get()` instead; the same tests then pass. The official factory-taking `registerMek(String, Function<...>)`
overload is retained alongside the prebuilt overload and exercised without a second registry write. Binding recipe-list keys now carry `RecipeCacheSource<R>`'s type,
and stored lists are defensively immutable. No additional test methods were added: existing tests now also cover cached empty lists/rebind, different explicit access
changing synthetic output without contaminating the bound list, all smelting skip conditions, multiple Ingredient entries, and special-recipe helper filtering.
The real client fixture checks exact connection access and canonical owner/synthetic smelting at initial packet, reload and reconnect, before the required phase markers.

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

## Task 043 — recipe type and serializer registration with the concrete recipe family

Task 043 implements `TASK043_PLAN.md` from clean commit `87fe68e6`, in an isolated worktree. It closes the boundary Task 042A explicitly left open: the 26 recipe
types are now **really registered**, and every Mekanism recipe serializer is registered so datapack recipes decode.

Slices move **163/184/37 → 199/188/38** (API +36, common +4, platform +1; 425 production sources). The executable contract moves to ordinary **77 suites/384 tests**,
transfer **5/27**, root-registry **1/2**, data-map **2/4**, and a new recipe-registry worker **1/5**; ordinary+transfer+root+data-map **85/417**; five-group aggregate
**86/422**.

### Recipe type registration preserves identity by construction

042A's `MekanismRecipeTypes.TYPE_*` were identity-stable but non-registered. Task 043 keeps that exact design and adds the registry write in the same class:
`ensureRegistered()` writes each of the 26 concrete instances into `BuiltInRegistries.RECIPE_TYPE` under its official id and attaches the canonical registry holder
back onto the holder over that same instance. Because there is only ever one creation site, `Recipe#getType()` identity and vanilla `RecipeManager`'s identity-keyed
`byType` grouping survive registration unchanged; the isolated worker asserts canonical identity explicitly. Static initialization itself performs no registry write,
and the 042A suite keeps asserting that, so `isRegistered()` remains a real boundary.

Registration runs in the ordinary mod-init window alongside the existing game event registration. The plan's assumption that the write had to precede
`BuiltInRegistries.bootStrap()` was disproved by a direct probe: the built-in registries stay writable after `BootStrap()` in this environment.

### Serializers and the concrete recipe family

The whole `mekanism.api.recipes.basic` package (26 concrete recipes, 7 abstract bases, 2 output interfaces) is admitted, plus the three common serializers and
`WrappedShapedRecipe`. All were byte-identical to `4646f509` at baseline; the only deltas are the four authorized families (project `FluidStack`, literal
`"mekanism"`, `MekanismKeyedHolder` for the 16 toast-item holders and the 26 API serializer fields, and two exact local replacements for the NeoForge
`NeoForgeStreamCodecs.composite` 7-arity and `NeoForgeExtraCodecs.withAlternative(MapCodec, MapCodec)` helpers).

`mekanism.fabric.recipe.FabricRecipeRegistration` performs the real write with the official ids and factory order. The official `MekanismRecipeSerializersInternal`
is deliberately not admitted: its four remaining entries belong to the recipe-upgrade and bin crafting-special closure, which measures 1,214 files outside the slice.

The 10 `getToastSymbol()` holders stay key-correct and unbound, and that is now provably unreachable rather than merely asserted: `MekanismRecipe.isSpecial()` is
`true`, and both `ServerRecipeBook.addRecipes` and `ClientRecipeBook.categorizeAndGroupRecipes` gate on `!isSpecial()`, so the only `getToastSymbol()` caller
(`RecipeToast`) is never reached for a Mekanism recipe. No `MekanismItems` entry is needed.

### Verification

Codec contracts run in the ordinary JVM: official JSON shapes round-trip for the item, combining, chemical, fluid, electrolysis, reaction, sawmill, energy and
rotary serializers; the rotary alternative accepts all three official shapes with the distinct-union key set and first-preference encoding; sawmill dependent
optionality and the energy scalar round-trip; and all 26 holders are key-stable and legitimately unbound. Two plan assertions were corrected to the real contracts:
an empty chemical output is an omitted optional field, and the no-output reaction and chance-without-output cases are rejected by the recipe constructor's
precondition rather than by a codec error result.

The isolated `fabricRecipeRegistryTest` worker asserts real registration: all 26 recipe-type ids resolve with canonical identity equal to the declared instances, all
26 serializer ids resolve through their holders, a real `RecipeManager` decodes a real `mekanism:crushing` document and serves it under the declared type and
serializer, and duplicate registration fails while repeated `ensureRegistered()` is idempotent.

Gates run: the five test tasks plus `verifyFabricTestContract` and `remapJar`; `check build`; the three `verify*SliceNoNeoForge` tasks; `scripts/scan-platform-deps.py`;
`./fabric-build.sh`; and `FABRIC_SMOKE_STATIC_ONLY=1 scripts/fabric-packaged-smoke.sh`. The packaged smoke admits exactly the new `basic`, `serializer` and
`WrappedShapedRecipe` classes plus `FabricRecipeRegistration` and the new access-widener entry, and still rejects `MekanismRecipeType*`,
`IMekanismRecipeTypeProvider`, `RecipeTypeRegistryObject`, `RotaryInputRecipeCache`, `MekanismRecipeSerializersInternal`, the upgrade/bin/ClearConfiguration
recipe families, every `mekanism/client/**`, `MekanismItems*`, `mekanism/api/MekanismAPI.class`, and all production recipe JSON. `./build.sh --clean` and the
dedicated-server/client smokes are not run in this worktree.

Non-claims: no `MekanismItems` or any item/block/fluid/Chemical registration; no machines or tile entities; no `MekanismRecipeType` cache owner,
`IMekanismRecipeTypeProvider`, `RecipeTypeRegistryObject`, `RecipeTypeDeferredRegister`, `RotaryInputRecipeCache`, or machine-facing `InputRecipeCache` index; no
`MekanismRecipeSerializersInternal` and therefore no `mek_data`/`clear_configuration`/`bin_insert`/`bin_extract` crafting specials and no bin or recipe-upgrade
recipes; no production recipe JSON in the artifact; no JEI/EMI or client recipe viewer; no `getToastSymbol()`-correct items; no item/fluid/Chemical transaction or
storage exposure; no broader Fabric integration.

### Generic holder shape (Task 043 correction from independent review)

Task 042A declared `MekanismKeyedHolder<T> implements Holder<T>`, which is not type compatible with the official surface: the oracle fields are
`DeferredHolder<RecipeType<?>, RecipeType<X>>`, and that two-parameter shape *is* a `Holder<RecipeType<?>>` because the registry element type is split from the
declared value type. Compilation confirmed the incompatibility. Task 043 therefore re-declares the helper as `MekanismKeyedHolder<R, T extends R>`, so all 26 recipe
type fields are `MekanismKeyedHolder<RecipeType<?>, RecipeType<X>>` and all 26 serializer fields are
`MekanismKeyedHolder<RecipeSerializer<?>, RecipeSerializer<X>>`, each assignable to the official widened `Holder<...>` while still returning the precise declared
value type from `get()`/`value()`. The 16 toast-item fields keep the oracle's declared `Holder<Item>`. `attach` additionally rejects a delegate whose bound value is
not the instance the keyed holder was built over, so registration cannot bind an unrelated instance.

Coverage is executable, not documentary: for every registered type the isolated worker asserts the official id resolves, the registry value is identity-equal to the
declared `TYPE_*` value, the canonical holder is that same instance, the holder is bound, `get()`/`value()` agree, and all 26 instances are distinct under an identity
set. A real `RecipeManager` decode-and-serve case proves the served recipe reports the registered type and serializer.

## Task 044 — rotary input-cache ownership closure

Task 044 implements `TASK044_PLAN.md` from clean commit `8a4c6105`, in an isolated worktree. It ports the last remaining input-cache shape onto the per-binding
`AbstractInputRecipeCache` contract without touching the `MekanismRecipeType` cache owner, registration, providers, monitors, factories, viewers, or any other cached
recipe family.

`RotaryInputRecipeCache` now takes a `RecipeCacheSource<RotaryRecipe>` and keeps both directional input caches (`ChemicalInputCache`, `FluidInputCache`) and both
complex-recipe fallback sets in a private `State extends CacheState`, created by `newState()` and populated by `initCache(recipes, state)`. Lookups call the shared
`resolveState(world)`, so context resolution, the exact-Level-manager mismatch failure, `clear(current binding)` and the no-context null result all come from the base
class. The class' own `clear()` override is removed and inherited. The only real delta against `4646f509` is the Fabric recipe-cache re-hosting plus the authorized
NeoForge-to-project fluid swap: `net.neoforged.neoforge.fluids.FluidStack` → `mekanism.api.fluid.FluidStack`, exactly as `FluidInputCache` and the sliced
`RotaryRecipe` already use.

Direction flags, type-only insertion predicates, amount-sensitive recipe selection, empty-input rejection and indexed-first fallback are preserved unchanged: a
fluid-to-chemical only recipe is never indexed in, or offered by, the chemical direction, and vice versa.

### Verification

Five new ordinary methods in one new suite, `RotaryInputCacheContextTest`, drive the real production cache over real `BasicRotaryRecipe` instances and a real
`RecipeCacheSource.forType(MekanismRecipeTypes.TYPE_ROTARY.value())`: one-way direction exclusion plus bidirectional serving and a directional rebuild after rebind;
indexed amount sensitivity versus type-only insertion; a component-bearing fluid ingredient served only through the complex fallback while the other direction stays
clean, including indexed-first precedence and fallback after an indexed amount mismatch; `clear()` releasing both directional indices and the populated fluid fallback while the binding stays live; and a fresh binding isolating both directional indices and the populated fluid fallback. Chemical fallback remains structurally retained but unreachable with the current `ChemicalInputCache.mapInputs()` (always false); no synthetic chemical scenario is claimed.
The generic binding/thread/epoch matrix stays owned by `GenericInputCacheContextTest` and is not repeated.

Slices move **199/188/38 → 199/189/38** (426 production sources). Ordinary executable totals move **77 suites/384 tests → 78/389**; transfer **5/27**,
root-registry **1/2**, data-map **2/4**, recipe-registry **1/5** unchanged; five-group aggregate **86/422 → 87/427**.

Gates run: the five test tasks plus `verifyFabricTestContract`; the three `verify*SliceNoNeoForge` tasks and `verifyOfficialRootTestSources`; the five
`verify*FixtureIsolation` tasks; `scripts/scan-platform-deps.py`; `./fabric-build.sh`; and `FABRIC_SMOKE_STATIC_ONLY=1 scripts/fabric-packaged-smoke.sh`. The static
gate now admits exactly `RotaryInputRecipeCache.class` and `RotaryInputRecipeCache$State.class` once each in the common recipe class family and no longer lists the
class in the deferred-family rejections, while still rejecting `MekanismRecipeType*`, `IMekanismRecipeTypeProvider`, `RecipeTypeRegistryObject`,
`MekanismRecipeSerializersInternal`, the upgrade/bin/ClearConfiguration recipe families, every `mekanism/client/**`, `MekanismItems*`, `mekanism/api/MekanismAPI.class`,
and all production recipe JSON. `./build.sh --clean` and the real dedicated-server/client smokes are not run in this worktree; the parent runs the runtime smoke and
clean release independently.

Non-claims: no `MekanismRecipeType` cache owner, `IMekanismRecipeTypeProvider`, `RecipeTypeRegistryObject`, `RecipeTypeDeferredRegister` or owner-registration
change; no monitors, factories, recipe-viewer integration, synthetic smelting, tiles, or other cached-recipe families; no codec or canonical recipe-identity change; no
machine-facing `InputRecipeCache` index; no new global mutable cache state or second context mechanism.

## Task 045 — canonical common recipe-type owner and provider

Task 045 implements `TASK045_PLAN.md` from clean baseline `c6255a4e`. It closes the ownership boundary Task 044 left open: the 26 recipe types are now owned by the
**common** layer, the API no longer creates anonymous `RecipeType` instances, and the recipe-cache binding carries the registry access that synthetic vanilla
smelting needs.

Slices move **199/189/38 → 200/193/39** (432 production sources). Ordinary executable totals move **78 suites/389 tests → 79/395**; transfer **5/27**,
root-registry **1/2**, data-map **2/4**, recipe-registry **1/5 → 3/7**; five-group aggregate **87/427 → 90/435**.

### One canonical object per id, common ownership

`MekanismRecipeType`, `IMekanismRecipeTypeProvider`, `RecipeTypeRegistryObject` and `RecipeTypeDeferredRegister` are ported into the common slice with all 26
declarations, the complete provider surface, `getRecipeFor`/`byKey` helpers, incomplete-recipe reporting and synthetic vanilla smelting. `RecipeTypeDeferredRegister`
is the creation point: each concrete type is created exactly once **before** any registry write, installed on the specialized `RecipeTypeRegistryObject` as that
holder's prebuilt value, and handed to the existing deferred machinery as a supplier returning that same instance. `RecipeTypeRegistryObject` keeps its
`MekanismDeferredHolder` parent: before binding it reports the prebuilt value, after binding it verifies the canonical registry entry **is** that value and throws
rather than splitting identity. The generic `MekanismDeferredRegister`/`FabricDeferredRegister` lifecycle is unchanged, so occupied keys, repeats and duplicates still
fail loudly.

The 26 declaration ids are literal local strings, so common declarations never read an API static field and neither load order can force the other. The API
`MekanismRecipeTypes` keeps its 26 official `NAME_*` ids and public `MekanismKeyedHolder<RecipeType<?>, RecipeType<X>>` fields, and obtains the canonical instances
through one new layer-neutral service, `mekanism.api.recipes.IRecipeTypeRegistry`, loaded with `MekanismServices` and registered by exactly one new service descriptor
implemented by `mekanism.fabric.recipe.FabricRecipeTypeRegistry`. `ensureRegistered()` invokes the common owner's single registry write and then attaches the canonical
holders back onto the API holders over the identical instances; it performs no duplicate write and stays idempotent. A missing service fails honestly instead of
falling back to a second identity.

`mekanism/api/recipes/MekanismRecipeTypes$1.class` therefore disappears from the artifact and `IRecipeTypeRegistry.class` plus the two exact common owner classes and
`FabricRecipeTypeRegistry.class` take their place in the packaged contracts; the old `MekanismRecipeType*` / `IMekanismRecipeTypeProvider` / `RecipeTypeRegistryObject`
rejections are replaced by exact class-family admissions, not a package whitelist.

### Binding-owned lists and access

`cachedRecipes`, `FMLEnvironment`, `ServerLifecycleHooks`, `MekanismClient` and the monolithic `Mekanism` dependency are gone; incomplete diagnostics use a
class-local SLF4J logger. `RecipeCacheContext.Binding` now additionally owns an optional `RegistryAccess` and each owner's immutable usable-recipe list (empty results
cached too), all inside the existing binding — no second `ThreadLocal`. The existing `bind(manager, epoch)` overload is kept and an access-bearing overload added.

Lookups follow the plan contract: a level/no-arg lookup with no bound context serves nothing; a non-null level whose manager is not the exact bound manager fails; a
null level uses the bound manager and its published access. An explicit manager/access query always answers for the supplied identities, reusing the binding-owned list
only when both match, otherwise computing an uncached immutable result rather than polluting or rejecting. `getRecipes(manager)` borrows access only from a binding
serving that exact manager, else serves without synthetic smelting. `clearCache` drops both the recipe list and all input-cache state; a rebind discards both
atomically; other threads and bindings are unaffected. Input-cache initialization now goes through the owner's own recipe-list method
(`RecipeCacheSource.recipes(manager, access)`), so ordering, incomplete filtering and synthetic smelting are shared rather than bypassed with `forType`.

Both publishers were updated: the server start/successful-reload events bind `server.registryAccess()`, and the client recipe-packet tail publisher passes the
connection's own `registryAccess()` (obtained from the packet listener, valid before a `Level` exists) so access is never borrowed from a foreign connection. Failed
reload, stop/disconnect and late-update contracts are preserved.

Synthetic vanilla smelting keeps own-recipes-first ordering, the official skip conditions, output semantics and the `/mekanism_generated/` prefix on the original id
(retaining the original namespace), using `ItemStackIngredient.of(ingredients, 1)` and `ResourceLocation.withPrefix` directly.

### Verification

Six new ordinary methods in `MekanismRecipeTypeOwnerTest` cover: 26 distinct canonical types with official ids, provider behaviour and nominal `RecipeType`
assignability; binding-owned list caching plus input-cache clearing and rebuild; foreign explicit-manager isolation; a real `Level` mismatch failure and the
no-context contract; synthetic smelting inclusion/order/id/output/union plus the null-access omission and cache feeding; incomplete reporting and filtering; and
canonical-instance resolution with foreign-thread isolation. The exact-manager mismatch case drives a real `Level` (`RecipeLevelStub`) through
`Level#getRecipeManager()` rather than a misleading name. Two further fresh-JVM tests, `CommonFirstInitializationTest` and `ApiFirstInitializationTest`, execute both
initialization orders in isolated `fabricRecipeRegistryTest` workers and prove all 26 ids converge on one distinct canonical instance and stay registry-identical.

Gates run with raw logs and real Gradle exit codes: `check` (five test groups plus `verifyFabricTestContract`), the three `verify*SliceNoNeoForge` tasks,
`verifyOfficialRootTestSources`, the five `verify*FixtureIsolation` tasks, `scripts/scan-platform-deps.py`, `./fabric-build.sh`, and
`FABRIC_SMOKE_STATIC_ONLY=1 scripts/fabric-packaged-smoke.sh`. JUnit XML confirms ordinary 79/395, transfer 5/27, root-registry 1/2, data-map 2/4,
recipe-registry 3/7, aggregate 90/435, all with zero failures and errors. The recipe-runtime fixture additionally asserts canonical owner/API identity, registered
smelting identity, published-access identity and synthetic smelting through the owner source; `./build.sh --clean`, the packaged server smoke and
`scripts/fabric-recipe-runtime-smoke.sh` are left to the parent.

Non-claims: no items/tiles/monitors/factories/recipe-viewer/codec or production recipe JSON; no other cached-recipe family; no second recipe type per id; no global
world fallback, no second context mechanism and no weakening of the generic deferred-registry lifecycle.

## Task 046 — recipe lookup handlers and generic monitor ownership

Task 046 implements `TASK046_PLAN.md` from clean baseline `2db8a184`. It completes the common owner → handler → cached-recipe execution spine by porting the five
lookup-handler interfaces and the two generic monitors, and replaces the official process-global one-tick flush flag with the existing binding's publication identity
plus a narrow binding-local invalidation generation.

Slices move **200/193/39 → 201/201/39**. Ordinary executable totals move **79 suites/395 tests → 80/402**; the other four groups are unchanged, so the five-group
aggregate moves **90/435 → 91/442**.

### Handlers

`IRecipeLookupHandler` and `IEitherSideRecipeLookupHandler` are byte-identical to the admitted official sources. `ISingleRecipeLookupHandler`,
`IDoubleRecipeLookupHandler` and `ITripleRecipeLookupHandler` change only the two planned platform type swaps (project `mekanism.api.fluid.FluidStack` and project
`mekanism.api.functions.TriPredicate`), the same audited replacements the corresponding input caches already use. Every default helper keeps its official name,
direction, input order and return type and forwards through the real canonical provider's input cache, so no index-matching matrix is duplicated.

`mekanism/client/recipe_viewer/type/IRecipeViewerRecipeType.java` is admitted **unchanged** to preserve the `recipeViewerType()` signature. Despite the package it
has no `net.minecraft.client` dependency: only `List`, `ResourceLocation`, `ItemStack`, `ItemLike` and the already-sliced `IHasTextComponent`. It remains the only
`mekanism/client/**` class in the artifact; the packaged static contract carves out exactly that one entry rather than whitelisting the client package.

`ItemStackConstantChemicalToObjectCachedRecipe` is admitted in full for the monitor's real `loadSavedData` branch: both factories, `ChemicalUsageMultiplier` and
true-long saved-usage handling stay intact, and the chemical branch is not removed because Crusher does not use chemicals. Its `TwoInputCachedRecipe` Javadoc link is
not a type dependency and no closure was expanded for it. `NucleosynthesizerRecipeCacheLookupMonitor` remains deferred with its machine/energy closure.

### Monitor ownership instead of a global flush

`RecipeCacheLookupMonitor` observes the thread-bound `RecipeCacheContext` directly. It retains only the binding's opaque publication identity token and the
binding-local invalidation generation it last resolved against — never the binding, manager, level or registry access — so it cannot keep an old graph alive. A
rebind, even to the same manager and epoch, is a fresh identity and invalidates.

`invalidateCache()` is a pure predicate. It compares the acknowledged stamp with the current publication and never acknowledges anything itself, so calling it twice,
or before `getUpdatedCache`, cannot consume an invalidation. The observation is acknowledged only after a successful resolution, so a failed lookup keeps the old stamp
stale. With no published context it always reports invalid, and `updateAndProcess` then detaches the cached recipe and delivers the normal changed-to-null
notification once instead of processing it. A real non-null handler level whose manager is not the exact bound manager throws before any processing, so a
still-input-valid cached recipe can never bypass recipe lookup.

`MekanismRecipeType.clearCache()` now advances the current binding's invalidation generation exactly once before dropping the owner's lists and indices. The
publication identity and epoch are deliberately untouched: an explicit clear discards derived state, it is not a successful datapack reload. Individual index or
list clears do not advance the generation. No second `ThreadLocal`, global flag or global counter was introduced, and neither the server nor the client publisher
needed a change.

`FactoryRecipeCacheLookupMonitor` keeps its `onChange` sorting callback and its explicit `updateCachedRecipe`, which participates in the same ownership contract,
acknowledges the same binding so the selection survives the next tick, and neither reloads saved state nor discards the selection. All other official monitor
semantics are preserved: no-recipe memoization, contents-callback then `onChange`/unpause ordering, change notification before processing, saved operating ticks,
constant chemical saved usage, wrong-cache-index behaviour and energy-delta reporting.

### Verification

The candidate's seven new ordinary methods in `RecipeCacheLookupMonitorTest` drive the real monitors, real canonical providers, real input caches and a real
`OneInputCachedRecipe`, reusing existing fixtures rather than rebuilding lower-level matrices. Parent correction adds an eighth method below. Final parent
443-test/build/static/clean-release/real packaged-server gates all passed on `8d826837`; exact evidence and artifact identity are in `TASK046_PLAN.md`.
No live publication callback changed, so the plan did not require an additional recipe-runtime smoke for this binding-local batch.

Non-claims: no `CommonWorldTickHandler` closure, no machine/tile/item/block/menu/viewer/codec/recipe-registration or production recipe JSON change, no
other cached-recipe family, no Nucleosynthesizer monitor, no change to the live publication callbacks, and no fake or no-op production seam.

### Task046 parent ownership correction

Candidate `7cc8e774` passed 442 tests, but parent adversarial tests exposed three missing enforcement paths: a handler that returns a recipe independently of the
canonical cache could still process without a context; an explicit Factory selection did not validate Level/context before invoking creation; and a synchronous
lookup callback could rebind before the monitor acknowledged the **new** current stamp over the old candidate. The initial GLM read-only review accepted the candidate,
classifying callback rebinding as hypothetical; parent executable evidence supersedes that verdict. Original-parent red log `/tmp/task046-parent-ownership-red.log`
shows 8 tests /3 failures, not a failure inferred from a method name.

The corrected monitor captures binding/generation before resolving and checks it after lookup/creation/saved-data callbacks, changed-cache notification and unpause,
then records that exact captured stamp. Interrupted resolutions cannot process and drop the monitor's cache through the ordinary changed-to-null notification. Direct
`getUpdatedCache` queries enforce no-context/mismatch and discard interrupted candidates but do not acknowledge a cache they have not installed. Factory selection
requires a matching published binding before creation and rejects an intervening context change instead of installing/stamping an unowned candidate. No global state,
new context, or live publication callback was added. Parent focused green log `/tmp/task046-parent-ownership-green.log`: 8 tests PASS.

The existing seven tests remain; one eighth test exercises synchronous lookup rebind, creation-time clear and changed-cache callback clear. Existing no-context and
Factory tests now include independently returning handlers/foreign Level/no-context selection, and measure actual positive energy consumption by a real CachedRecipe
rather than only zero. Final ordinary totals become80 suites/403 tests, aggregate91/443; no tests were deleted.
