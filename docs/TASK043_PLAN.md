# Task 043 plan — recipe type and serializer registration with the concrete recipe family

Status: **GO. Subject-matter complete, no machines, no item registration, no client recipe viewer.** This document was written from clean integrated baseline
`87fe68e6` (Task 042A landed). Scope is defined by the natural module boundary — the `mekanism.api.recipes.basic` package plus the recipe serializer module plus the
two vanilla registries they must populate — not by a file count.

## Why the 042B blocker dissolves

`TASK042_PLAN.md` recorded four reasons why the requested vertical was blocked. Three of them are now measurably **not** on this vertical's path, and the fourth is
the subject matter itself.

1. **Recipe-type registration (real blocker, now solvable).** `Recipe#getType()` is abstract (`upstream/neoforge-minecraft/net/minecraft/world/item/crafting/Recipe.java:68`)
   and `RecipeManager` calls it twice on the live reload path (`RecipeManager.java:64`, `:171`). 042A answered this with 26 identity-stable, **non-registered**
   concrete `RecipeType` instances. `MekanismKeyedHolder.attach(Holder)` already exists, is public, and `keyed(...)` permits attach (`registry == null && value != null`
   passes its guard). Registration can therefore bind the canonical `Holder.Reference` back onto the **exact same instances**, so `getType()` identity and
   `RecipeManager`'s identity-keyed `byType` grouping survive registration unchanged. That is the 042B checkpoint, and it is satisfied by construction rather than by
   re-creating types.
2. **`MekanismItems` for the ten toast holders (not required).** `MekanismRecipe.isSpecial()` returns `true`, and `ServerRecipeBook.addRecipes`
   (`upstream/.../stats/ServerRecipeBook.java:32`) only records, highlights, and sends a recipe when `!recipeholder.value().isSpecial()`.
   `ClientRecipeBook.categorizeAndGroupRecipes` applies the same gate. The only `getToastSymbol()` caller in the tree is
   `client/gui/components/toasts/RecipeToast.java:50`, reachable only from `ClientPacketListener.handleAddOrRemoveRecipes` case `ADD` — i.e. only for recipes the
   server actually sent. Mekanism recipes are never sent, so the unbound toast-item holders are never dereferenced. The 136-declaration `MekanismItems` family is a
   genuine non-claim of this task, not a hidden dependency, and the plan tests that claim directly.
3. **`MekanismRecipeType` / `RecipeTypeRegistryObject` family (not required).** Those types exist to own a per-type `InputRecipeCache` for machines. Registration and
   datapack decoding need only a vanilla `RecipeType` instance and a `RecipeSerializer`. The generic `InputRecipeCache` family already landed in Task 041 with a
   `RecipeCacheSource<TYPE>` seam, so no cache owner is needed here. `MekanismRecipeType`'s `mekanism.client.*` imports therefore never enter scope, and the client
   recipe viewer stays deferred.
4. **Recipe serializers and decodable recipes — this is the subject matter.** `Recipe.CODEC` dispatches on `getSerializer()` through
   `BuiltInRegistries.RECIPE_SERIALIZER.byNameCodec()`, so a registered serializer is what makes a datapack recipe decodable at all. This task registers all 26.

Registration mechanism precedent: `MekanismFabricBootstrap.onInitialize()` already calls
`MekanismGameEvents.GAME_EVENTS.registerAll(BuiltInRegistries.GAME_EVENT)` and a real dedicated-server run asserts `MEKANISM_FABRIC_REGISTRY_PROOF ...
canonical_identity=true entries=4`. Writing to a frozen-in-vanilla built-in registry from `onInitialize` is therefore an established, gate-verified capability of this
port, and `RECIPE_TYPE` / `RECIPE_SERIALIZER` are ordinary built-ins of the same kind.

## Module boundary

Baseline measurements at `87fe68e6`: slices **API 163, common 184, platform 37** (384 production sources). Ordinary **76 suites/378 tests**, transfer **5/27**,
root-registry **1/2**, data-map **2/4**, aggregate **84/411**.

The vertical is exactly:

- **the whole `mekanism.api.recipes.basic` package** (35 files): 26 concrete recipe classes, 7 abstract bases, 2 output interfaces;
- **`mekanism.api.recipes.MekanismRecipeSerializers`** (26 declared serializer holders) — currently byte-identical to the oracle and still typed on
  `net.neoforged.neoforge.registries.DeferredHolder`;
- **the three common serializers** `MekanismRecipeSerializer`, `RotaryRecipeSerializer`, `SawmillRecipeSerializer`;
- **`mekanism.common.recipe.WrappedShapedRecipe`**, required by `MekanismRecipeSerializer.wrapped`;
- **one new platform registration owner**;
- **two existing files extended** (`MekanismKeyedHolder`, `MekanismRecipeTypes`).

Authoritative file set (additions only; `→` shows the delta family):

| Group | Files |
|---|---|
| Basic package | all 35 under `src/api/java/mekanism/api/recipes/basic/` |
| API serializers | `mekanism/api/recipes/MekanismRecipeSerializers.java` |
| Common serializers | `mekanism/common/recipe/serializer/{MekanismRecipeSerializer,RotaryRecipeSerializer,SawmillRecipeSerializer}.java` |
| Common recipe base | `mekanism/common/recipe/WrappedShapedRecipe.java` |
| New platform owner | `fabric/src/main/java/mekanism/fabric/recipe/FabricRecipeRegistration.java` |

Additions: **API +36, common +4, platform +1** → slices **199 / 188 / 38** (425 production sources).

Identity against `4646f509`: every one of the 40 candidate sources is currently **byte-identical** to the oracle (verified per file). After implementation the
classification is `U`/`A`/`N`:

- `U` byte-identical (16): `BasicChemicalChemicalToChemicalRecipe`, `BasicChemicalCrystallizerRecipe`, `BasicChemicalDissolutionRecipe`, `BasicChemicalToChemicalRecipe`, `BasicCombinerRecipe`, `BasicItemStackChemicalToItemStackRecipe`, `BasicItemStackToChemicalRecipe`, `BasicItemStackToEnergyRecipe`, `BasicItemStackToFluidOptionalItemRecipe`, `BasicItemStackToItemStackRecipe`, `BasicNucleosynthesizingRecipe`, `BasicSawmillRecipe`, `IBasicChemicalOutput`, `IBasicItemStackOutput`, `SawmillRecipeSerializer`, `WrappedShapedRecipe`. (`MekanismKeyedHolder` and `MekanismRecipeTypes` remain the 042A `A` files they already are and are not re-classified here.)
- `A` bounded adaptation (24): the 2 `FluidStack`-only abstract bases, the 6 other `FluidStack`-bearing basics, the 16 `DeferredHolder`-bearing basics,
  `MekanismRecipeSerializers`, and 2 of the 3 common serializers (`MekanismRecipeSerializer`, `RotaryRecipeSerializer`; `SawmillRecipeSerializer` is `U`).
- `N` new source (1): `FabricRecipeRegistration`.

Authorized delta families, and no others:

1. `net.neoforged.neoforge.fluids.FluidStack` → project `mekanism.api.fluid.FluidStack` (applies to 6 basic files and 2 common serializers);
2. `net.neoforged.neoforge.registries.DeferredHolder<RecipeSerializer<?>, X>.create(Registries.ITEM, key)` → `MekanismKeyedHolder.registryKeyed(...)` for the
   16 toast-item holders and the 26 `MekanismRecipeSerializers` fields (the same substitution 042A already made for `MekanismRecipeTypes` and the 10 abstract
   toast holders);
3. `MekanismAPI.MEKANISM_MODID` → literal `"mekanism"` (the Task 032 precedent);
4. `net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs.composite` (7-arity) → a local private 7-arity `StreamCodec.composite` equivalent in
   `MekanismRecipeSerializer`;
5. `net.neoforged.neoforge.common.util.NeoForgeExtraCodecs.withAlternative(MapCodec, MapCodec)` → a local private `AlternativeMapCodec` equivalent.

Delta families 4 and 5 are exact semantic replacements, not approximations. The pinned NeoForge 21.1.200 sources (Gradle cache coordinate documented in
`docs/API_DELTAS.md` convention; read from `neoforge-21.1.200-sources.jar`) show:

- `NeoForgeStreamCodecs.composite(...7...)` is plain sequential decode/encode with no Neo-specific behaviour; the local replacement is byte-for-byte the same algorithm.
- `NeoForgeExtraCodecs.withAlternative(MapCodec, MapCodec)` returns `AlternativeMapCodec`, whose `keys` is `Stream.concat(codec.keys, alternative.keys).distinct()`,
  whose `decode` falls through to the alternative only when the first errors, and whose `encode` builds the first codec against `ops.empty()` and falls through to the
  alternative only on error. The local replacement reproduces all four behaviours exactly. This is required because `RotaryRecipeSerializer`'s codec must accept the
  three official JSON shapes (both-ways, fluid-to-chemical, chemical-to-fluid), and encode order must stay first-preference.

## Registration design

### Recipe types — inside `MekanismRecipeTypes` (identity-preserving by construction)

`newRecipeType(name)` keeps creating the anonymous concrete instance, then registers it and binds the canonical holder back onto the same instance:

```java
RecipeType<RECIPE> type = new RecipeType<RECIPE>() { public String toString() { return name.toString(); } };
Registry.registerForHolder(BuiltInRegistries.RECIPE_TYPE, key, type);   // real registry write, official id
MekanismKeyedHolder<RecipeType<RECIPE>> holder = MekanismKeyedHolder.keyed(Registries.RECIPE_TYPE, name, type);
holder.attach(BuiltInRegistries.RECIPE_TYPE.getHolderOrThrow(key));
```

Registration lives in the class that owns the instances, which is the strongest available proof that `RecipeType` identity is reused: there is no second creation
site to diverge. A new `public static void ensureRegistered()` forces class initialization at bootstrap, because nothing on the startup path otherwise touches
`MekanismRecipeTypes` (the serializer factories are lambdas and do not run at registration time).

`ensureRegistered()` runs in the normal mod-init window alongside the existing game event registration; see the empirical ordering note below. The registry-lifecycle
assertions live in a fresh isolated Knot worker because the shared ordinary JVM is bootstrapped once by a test harness and cannot register afterwards, not because the
write must precede bootstrap.

### Recipe serializers — inside the new platform owner

`MekanismRecipeSerializers` (API) cannot construct serializers, because the factories live in common. Its 26 fields therefore become
`MekanismKeyedHolder.registryKeyed(Registries.RECIPE_SERIALIZER, path, () -> BuiltInRegistries.RECIPE_SERIALIZER)`, resolving lazily by key out of the real registry.
`MekanismKeyedHolder` gains one new overload,
`registryKeyed(ResourceKey<? extends Registry<R>>, ResourceLocation, Supplier<? extends Registry<R>>)`, mirroring the existing typed-key `keyed(...)` overload so
`MekanismRecipeSerializers.CRUSHING` can be typed `MekanismKeyedHolder<RecipeSerializer<BasicCrushingRecipe>>` rather than `MekanismKeyedHolder<RecipeSerializer<?>>`.
This keeps the official field surface exact; no public field changes type parameters.

`mekanism.fabric.recipe.FabricRecipeRegistration` then performs the real write:
`FabricDeferredRegister<RecipeSerializer<?>>` populated with the 26 official factories (`MekanismRecipeSerializer.itemToItem`, `.combining`, `.separating`,
`.fluidToFluid`, `.chemicalToChemical`, `.crystallizing`, `.dissolution`, `.itemChemicalToItem`, `.nucleosynthesizing`, `.itemToEnergy`, `.itemToChemical`,
`.chemicalChemicalToChemical`, `new RotaryRecipeSerializer(...)`, `new SawmillRecipeSerializer(...)`) using the official ids and the same factory order as
`MekanismRecipeSerializersInternal`, followed by `registerAll(BuiltInRegistries.RECIPE_SERIALIZER)`. The official `MekanismRecipeSerializersInternal` is **not**
admitted: its remaining four entries (`mek_data`, `clear_configuration`, `bin_insert`, `bin_extract`) belong to the recipe-upgrade and bin crafting-special closure,
which drags `MekanismShapedRecipe`, `ClearConfigurationRecipe`, `BinInsertRecipe`, `BinExtractRecipe` and a measured 1,214-file closure. Those four are a separate
owner and are recorded as a non-claim; omitting them is a module boundary, and no public surface is truncated because the unadmitted file is never copied.

`MekanismFabricBootstrap.onInitialize()` gains two lines, after the existing ingredient-service verification:
`MekanismRecipeTypes.ensureRegistered();` then `FabricRecipeRegistration.register();`.

### Decoding without a cache owner

Once serializers are registered, `RecipeManager` decodes datapack JSON through `Recipe.CODEC`, groups by `getType()` identity, and serves the 26
`getAllRecipesFor(MekanismRecipeTypes.TYPE_X.value())` lookups. No `MekanismRecipeType`, `IMekanismRecipeTypeProvider`, `RecipeTypeRegistryObject`,
`RecipeTypeDeferredRegister`, `RotaryInputRecipeCache`, machine or tile entity is on this path.

## Tests

Two new suites at two different isolation levels, plus the existing 042A suite corrected.

1. **Ordinary `mekanism.api.recipes.RecipeSerializerContractTest` — 6 methods.** Codec-level contracts that need no registry write: official JSON shapes
   round-trip for `itemToItem`, `combining`, `itemToChemical`, `chemicalChemicalToChemical`, `itemChemicalToItem`, `nucleosynthesizing`, `separating`,
   `fluidChemicalToChemical`, `fluidToFluid`, `crystallizing`, `dissolution`, `reaction`, and `itemToEnergy`; the Rotary three-shape alternative required exactly
   once per shape and the encoder staying on the first preferred shape; Sawmill dependent optionality (`secondary_chance` absent, present, and rejected at zero or
   above one); the `reaction` "no output" validation rejection; stream-codec presence and stability for all 26; and the 26 `MekanismRecipeSerializers`
   identity-stable holder keys resolving to the same objects on repeat access.
2. **New isolated `fabricRecipeRegistryTest` Knot runner — 4 methods.** The registry-lifecycle surface, which cannot run in the shared ordinary JVM because that JVM
   is bootstrapped once by the harness and has no registration window: bootstrap, run the real owner sequence, then assert (a) all 26 official recipe-type ids resolve in `BuiltInRegistries.RECIPE_TYPE` and each canonical holder `.value()` is identity-equal
   to the corresponding `MekanismRecipeTypes.TYPE_*` value — the direct proof that registration reused the 042A instances; (b) all 26 official serializer ids
   resolve and each `MekanismRecipeSerializers.*.value()` returns the registered instance; (c) a real `RecipeManager` decodes a real `mekanism:crushing` JSON through
   the registered serializer, returns that recipe from `getAllRecipesFor(TYPE_CRUSHING.value())`, and reports the expected `getType()`/`getSerializer()` identity;
   (d) a duplicate registration attempt for an occupied key fails, and re-running `ensureRegistered()` is idempotent.
3. **Existing `RecipeOutputHandlingTest` corrected.** Its current assertion that `getType()` is *not* in `BuiltInRegistries.RECIPE_TYPE` was the correct 042A
   non-claim and is now false by design. It becomes an assertion that the type **is** registered under its official id with canonical identity, and the "no registry
   mutation" comment is replaced by the 043 claim. Its other 5 methods are unchanged.

Ordinary **76/378 → 77/384**; the new isolated source set adds **1 suite/4 tests**; ordinary+transfer+root+data-map totals move to **85/417**, and the aggregate
across all five groups is **86/421** (transfer 5/27, root-registry 1/2, data-map 2/4
unchanged). `fabric/build.gradle` must be updated in the same change: the slice-count guard `163/184/37 → 199/188/38`, the new source set and its `Test` task and
`fabric.side=server` worker, the new fixed-source and `@Test`-count guards for `RecipeSerializerContractTest` (6) and the new Knot test (4), the
`readTotals` expectations, and the aggregate sum.

## Gates

```
./gradlew -p fabric --no-daemon --console=plain \
  test fabricTransferTest fabricRegistryTest fabricDataMapTest fabricRecipeRegistryTest \
  verifyFabricTestContract remapJar
./gradlew -p fabric --no-daemon --console=plain check build
./gradlew -p fabric --no-daemon --console=plain \
  verifyApiSliceNoNeoForge verifyCommonSliceNoNeoForge verifyPlatformSliceNoNeoForge
scripts/scan-platform-deps.py
./fabric-build.sh
FABRIC_SMOKE_STATIC_ONLY=1 scripts/fabric-packaged-smoke.sh
```

`scripts/fabric-packaged-smoke.sh` must gain the exact new class families: the 26 `mekanism/api/recipes/basic/**` outer and nested classes, the 26
`mekanism/common/recipe/serializer/**` classes, `mekanism/common/recipe/WrappedShapedRecipe`, the new private nested codec helpers, and
`mekanism/fabric/recipe/FabricRecipeRegistration`. It must keep rejecting the still-deferred families: `common/recipe/MekanismRecipeType*`,
`IMekanismRecipeTypeProvider`, `RecipeTypeRegistryObject`, `RecipeTypeDeferredRegister`, `RotaryInputRecipeCache`, `common/registries/MekanismRecipeSerializersInternal`,
`common/recipe/{upgrade,bin,ClearConfigurationRecipe}`, every `mekanism/client/**`, `MekanismItems*`, and `mekanism/api/MekanismAPI.class`.

The dedicated-server portion of the packaged smoke is **kept** for this task (not static-only) because the task introduces real registry lifecycle, and it must
assert a new `MEKANISM_FABRIC_RECIPE_REGISTRATION_PROOF` line reporting the registered type/serializer counts with canonical identity. It also asserts the 26
`mekanism:crushing`-style recipe-type ids appear in the server's registry dump.

`./build.sh --clean` is not run, and no push, deploy, production, mirror, or secret operation is performed.

## Non-claims

No `MekanismItems`; no item, block, fluid or Chemical registration; no machine or tile entity; no `MekanismRecipeType` cache owner, `IMekanismRecipeTypeProvider`,
`RecipeTypeRegistryObject`, `RecipeTypeDeferredRegister`, `RotaryInputRecipeCache`, or machine-facing `InputRecipeCache` index; no `MekanismRecipeSerializersInternal`
and therefore no `mek_data`/`clear_configuration`/`bin_insert`/`bin_extract` crafting specials, no recipe-upgrade wrapped shaped recipe, no bin recipes; no
production recipe JSON in the artifact; no JEI/EMI or client recipe viewer; no `getToastSymbol()` correctness (unreachable while `isSpecial()` is true, asserted as
such); no item/fluid/Chemical transaction or storage exposure; no broader Fabric integration.

## 043 implementation outcome (recorded after landing)

Implemented from clean commit `87fe68e6` in an isolated worktree by the single DeepSeek implementation agent, with no scope expansion into the non-claims.

Measured result:

- slices **163/184/37 → 199/188/38** (API +36, common +4, platform +1; 425 compiled production sources);
- ordinary **76/378 → 77/384**; transfer **5/27**, root-registry **1/2**, data-map **2/4** unchanged; new recipe-registry worker **1/4**;
  ordinary+transfer+root+data-map **85/417**; five-group aggregate **86/421**;
- identity against `4646f509`: the 35 `basic` package files, the three common serializers, `WrappedShapedRecipe`, and `MekanismRecipeSerializers` were all
  byte-identical at baseline. After the change, 16 remain byte-identical (`BasicChemicalChemicalToChemicalRecipe`, `BasicChemicalCrystallizerRecipe`, `BasicChemicalDissolutionRecipe`, `BasicChemicalToChemicalRecipe`, `BasicCombinerRecipe`, `BasicItemStackChemicalToItemStackRecipe`, `BasicItemStackToChemicalRecipe`, `BasicItemStackToEnergyRecipe`, `BasicItemStackToFluidOptionalItemRecipe`, `BasicItemStackToItemStackRecipe`, `BasicNucleosynthesizingRecipe`, `BasicSawmillRecipe`, `IBasicChemicalOutput`, `IBasicItemStackOutput`, `SawmillRecipeSerializer`, `WrappedShapedRecipe`) and the other 24 carry only the authorized delta families.

Design decisions actually taken:

- **Recipe type registration preserves identity by construction.** `MekanismRecipeTypes` creates its 26 concrete instances once in the static initializer; a new
  memoized `ensureRegistered()` writes each into `BuiltInRegistries.RECIPE_TYPE` under its official id and attaches the canonical registry holder back onto the
  holder over that exact same instance. There is no second creation site, so `getType()` identity and `RecipeManager`'s identity-keyed `byType` grouping cannot
  diverge. `recipeTypeHolders()` and `isRegistered()` expose the lifecycle for verification. The companion `is/recipeTypeHolders` surface is additive only.
- **Touching the class alone must not write to a registry.** Static initialization stays pure; the registry write is an explicit step. 042A's suite keeps asserting
  that class initialization alone registers nothing and that `isRegistered()` is false, which is now a real boundary rather than a stale non-claim.
- **Two NeoForge helpers became exact local equivalents.** `NeoForgeStreamCodecs.composite` (7-arity) became a private `composite` in `MekanismRecipeSerializer`;
  `NeoForgeExtraCodecs.withAlternative(MapCodec, MapCodec)` became a private `AlternativeMapCodec` in `RotaryRecipeSerializer` reproducing all four behaviours
  (distinct-union `keys`, decode fallthrough on error, probe-then-encode fallthrough, and `toString`). Semantics were read from the pinned NeoForge 21.1.200 sources
  jar at its documented Gradle cache coordinate, not guessed.
- **`MekanismKeyedHolder` gained one typed-key overload** so `MekanismRecipeSerializers`' 26 public fields can keep their exact official type parameters while
  resolving lazily by key. No public field changed type parameters.
- **The 26 serializers are registered by a new platform owner**, `mekanism.fabric.recipe.FabricRecipeRegistration`, mirroring the official ids and factory order of
  `MekanismRecipeSerializersInternal`. The official `MekanismRecipeSerializersInternal` is not admitted: its remaining four entries (`mek_data`,
  `clear_configuration`, `bin_insert`, `bin_extract`) belong to the recipe-upgrade and bin crafting-special closure, which measures 1,214 files outside the slice.
  Those four are a module boundary, not a truncation, because nothing is copied and no public surface is cut.
- **One access-widener entry was added** for `ShapedRecipe.pattern`, which `WrappedShapedRecipe` passes through to its own super constructor. This mirrors the
  upstream access-transformer expectation and is now asserted verbatim by the packaged smoke.

Findings that corrected the plan:

- The plan asserted registration had to precede `BuiltInRegistries.bootStrap()` because `BuiltInRegistries.internalRegister` calls `Bootstrap.checkBootstrapCalled`.
  A direct probe proved the built-in registries are still writable **after** `BootStrap()` in this environment — the same window the existing game event
  registration already uses. The comments in `MekanismRecipeTypes`, `FabricRecipeRegistration`, and `MekanismFabricBootstrap` state the empirical ordering. The
  isolated worker exists because the shared ordinary JVM has no registration window, not because of a freeze deadline.
- Two plan assertions about codec-level rejection were wrong and were corrected to the real contracts: an empty chemical output is an omitted optional field (it
  cannot be JSON-encoded as `mekanism:empty`), and both the "no reaction output" and "secondary chance without a secondary output" cases are rejected by the recipe
  **constructor's** precondition while the codec is assembling the value, so they surface as `IllegalArgumentException` rather than as a codec error result.

Verified gates: `test fabricTransferTest fabricRegistryTest fabricDataMapTest fabricRecipeRegistryTest verifyFabricTestContract remapJar`; `check build`; the three
`verify*SliceNoNeoForge` tasks; `scripts/scan-platform-deps.py` (inventory, not a gate); `./fabric-build.sh`; and
`FABRIC_SMOKE_STATIC_ONLY=1 scripts/fabric-packaged-smoke.sh`. The dedicated-server portion of the packaged smoke was **not** run in this worktree; the plan requires
it as the final release gate for this task because the task introduces real registry lifecycle, so `./build.sh --clean` remains for the parent. No push, deploy,
production, mirror, or secret operation was performed.
