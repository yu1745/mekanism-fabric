# Task 042 plan — output handling plus one-input cached recipe execution

Status: **planning complete, docs-only. Verdict: the requested full vertical is BLOCKED. The task is split into 042A (GO, subject-matter-complete, no registry
mutation) and 042B (BLOCK, requires a separate approval).** No production or test source is written by this task. All numbers below were measured in this
worktree at clean baseline `f82a2246`.

## Approved target and baseline

Requested vertical: the closure immediately downstream of Task 041 around

- `src/api/java/mekanism/api/recipes/outputs/OutputHelper.java`
- `src/api/java/mekanism/api/recipes/cache/OneInputCachedRecipe.java`

Baseline measurements:

- Slices API **129**, common **184**, platform **37** = 350 compiled production sources.
- Test contract in `fabric/build.gradle`: ordinary **75/372**, transfer **5/27**, root-registry **1/2**, data-map **2/4**, aggregate **83/405**.
- Oracle `4646f509` (`v1.21.1-10.7.19.85`) is available; both target files are **byte-identical to the oracle** and are deliberately **not** in any slice.
- Slice no-NeoForge checks pass (0 files each), and no API-slice file imports `mekanism.common.` — i.e. the API layer is currently forbidden from referencing the
  layer-neutral by-key register that lives in common.

## Exact transitive boundary (measured, not estimated)

Closure was computed by resolving every `mekanism.*` import plus same-package simple-name reference in the working tree, transitively, then subtracting slice
membership.

### `OutputHelper`

Its own logic needs exactly one real delta: `net.neoforged.neoforge.fluids.FluidStack` → project `mekanism.api.fluid.FluidStack`. Every tank/slot/value contract
it uses is already admitted: `IOutputHandler`, `CachedRecipe.OperationTracker`/`RecipeError`, `IChemicalTank`, `IExtendedFluidTank`, `IInventorySlot`,
`ChemicalStack`, `Action`, `AutomationType`, `MathUtils`, `ConstantPredicates`.

However, its six public factories name the nested output records `ElectrolysisRecipe.ElectrolysisRecipeOutput`,
`ItemStackToFluidOptionalItemRecipe.FluidOptionalItemOutput`, `PressurizedReactionRecipe.PressurizedReactionRecipeOutput`, and `SawmillRecipe.ChanceOutput`.
Those nesting classes force all 20 recipe abstractions. `OutputHelper`'s own closure is 122 files with **46 outside the slice**; of those, 45 are shared with
`OneInputCachedRecipe` and one is the target itself.

### `OneInputCachedRecipe`

Its execution logic is already fully satisfied by the admitted `CachedRecipe`, `CachedRecipeHelper`, `IInputHandler`, `IOutputHandler`, and
`InputIngredient`. Its nine public static factories must keep their complete signatures, which name `ChemicalCrystallizerRecipe`, `ElectrolysisRecipe`,
`FluidToFluidRecipe`, `ItemStackToItemStackRecipe`, `ItemStackToFluidRecipe`, `ItemStackToFluidOptionalItemRecipe`, `ItemStackToChemicalRecipe`,
`ChemicalToChemicalRecipe`, `SawmillRecipe` and the electrolysis/sawmill/fluid-optional records. Its own closure is 121 files with **47 outside the slice**. Of its
four non-shared files, `CachedRecipeHelper` and `IInputHandler` are already admitted and only `ItemStackToFluidRecipe` (the `itemToFluid` factory) and the target
itself are new.

### Union closure — 48 new sources at baseline

Union of the two closures is 126 files of which 48 are outside the slice:

| Group | Count | Files |
|---|---|---|
| Recipe abstractions | 20 | `ChemicalChemicalToChemicalRecipe`, `ChemicalCrystallizerRecipe`, `ChemicalDissolutionRecipe`, `ChemicalToChemicalRecipe`, `CombinerRecipe`, `ElectrolysisRecipe`, `FluidChemicalToChemicalRecipe`, `FluidToFluidRecipe`, `ItemStackChemicalToItemStackRecipe`, `ItemStackChemicalToObjectRecipe`, `ItemStackToChemicalRecipe`, `ItemStackToEnergyRecipe`, `ItemStackToFluidOptionalItemRecipe`, `ItemStackToFluidRecipe`, `ItemStackToItemStackRecipe`, `MekanismRecipeTypes`, `NucleosynthesizingRecipe`, `PressurizedReactionRecipe`, `RotaryRecipe`, `SawmillRecipe` |
| `vanilla_input` records | 11 | `BiChemicalRecipeInput`, `ChemicalRecipeInput`, `FluidChemicalRecipeInput`, `FluidRecipeInput`, `ItemChemicalRecipeInput`, `ReactionRecipeInput`, `RotaryRecipeInput`, `SingleChemicalRecipeInput`, `SingleFluidChemicalRecipeInput`, `SingleFluidRecipeInput`, `SingleItemChemicalRecipeInput` |
| Module/gear/radial API | 15 | `MekanismAPI`, `MekanismIMC`, `robit/RobitSkin`, `gear/ICustomModule`, `gear/IHUDElement`, `gear/IModule`, `gear/IModuleContainer`, `gear/ModuleConfigListCodec`, `gear/ModuleData`, `providers/IItemProvider`, `providers/IModuleDataProvider`, `radial/RadialData`, `radial/mode/INestedRadialMode`, `radial/mode/IRadialMode`, `radial/mode/NestedRadialMode` |
| This task's targets | 2 | `cache/OneInputCachedRecipe`, `outputs/OutputHelper` |

The module/gear/radial group is **not a real dependency of recipe execution**. It is reachable only because the recipe abstractions name the class
`MekanismAPI` to read the single constant `MEKANISM_MODID = "mekanism"`, and the official `MekanismAPI` transitively pulls its registry, IMC, module and robit
surface. Replacing that reference with the literal namespace is an established precedent in this repository: `FABRIC_SMOKE.md` records that Task 032's
`ModuleConfig` "only replaces the full API owner with literal `\"mekanism\"`". Doing so removes all 15 files without touching a single public recipe surface.
This is a bounded adaptation, not a scope reduction.

**Realistic closure: 33 recipe/`vanilla_input` sources plus one new layer-neutral holder mechanism.**

## The blocker

`net.minecraft.world.item.crafting.Recipe` declares `RecipeType<?> getType();` as **abstract** (verified at
`upstream/neoforge-minecraft/net/minecraft/world/item/crafting/Recipe.java:68`), and the vanilla `RecipeManager` calls it on the live reload path twice — while
indexing at `RecipeManager.java:64` and for every recipe on `replaceRecipes` at `RecipeManager.java:171`. A recipe abstraction that cannot produce a
`RecipeType` instance cannot participate in the real `RecipeManager` at all.

The official source satisfies this with **36 key-resolved, bound-on-registration holder sites**: 26 in `MekanismRecipeTypes` alone
(`DeferredHolder.create(Registries.RECIPE_TYPE, NAME_X)`) and one each in `ChemicalCrystallizerRecipe`, `ChemicalDissolutionRecipe`, `CombinerRecipe`,
`ElectrolysisRecipe`, `FluidToFluidRecipe`, `ItemStackToEnergyRecipe`, `NucleosynthesizingRecipe`, `PressurizedReactionRecipe`, `RotaryRecipe`, `SawmillRecipe`.
Ten of those abstractions additionally materialize an item holder for `getToastSymbol()`.

This project has **no by-key holder mechanism**. `MekanismDeferredHolder` exposes only `attach(...)` plus a public key constructor and no `create`-equivalent;
the only two key-resolved sites anywhere in the repository belong to `MekanismAPI` and `FluidDeferredRegister`, neither of which is in a slice. Every admitted
deferred holder is produced by a `MekanismDeferredRegister` registration with a known name.

The full requested vertical therefore additionally requires:

1. all 26 `MekanismRecipeTypes.TYPE_*` registered into `BuiltInRegistries.RECIPE_TYPE` and bound — i.e. **recipe-type registration ownership**;
2. the concrete `MekanismItems` family (136 declarations) for the ten `getToastSymbol()` holders, since an unbound `Holder` throws on `get()` — i.e.
   **item registration ownership**;
3. `MekanismRecipeType`, `IMekanismRecipeTypeProvider`, `RecipeTypeDeferredRegister`, `RecipeTypeRegistryObject`, `RotaryInputRecipeCache` — and
   `MekanismRecipeType` imports `mekanism.client.MekanismClient` and `mekanism.client.recipe_viewer.RecipeViewerUtils`, i.e. the **client recipe viewer**;
4. recipe serializers and production recipe JSON, so a registered type is decodable at all.

Items 1 and 2 are named hard boundaries for this task (recipe serializers/types/registration; machines/items), and item 3 pulls a second deferred boundary. The
requested full vertical is therefore not a 12–40-file cohesive closure; it is the recipe-type + item-registration + client-recipe-viewer capability set that Task
041 already recorded as the following boundary.

## Split: 042A (GO) and 042B (BLOCK)

### 042A — GO: complete output handling + one-input cached recipe execution, with no production registry mutation

042A delivers the requested subject matter in full at the source level and is verifiable end to end today. Its key design decision removes the blocker without
entering any forbidden ownership:

> `MekanismRecipeTypes.TYPE_*` hold **non-registered, identity-stable concrete `RecipeType` instances** rather than registry-registered ones.

Vanilla `RecipeType` is a plain interface. `RecipeManager.replaceRecipes` and `byType` group by `recipeholder.value().getType()` — **identity**, not registry
membership (`RecipeManager.java:64`, `:171`). Concrete non-registered instances therefore make `getType()` fully functional for in-memory recipes,
`OneInputCachedRecipe`'s factories operational, and the `byType` lookup correct, while writing **nothing** to `BuiltInRegistries.RECIPE_TYPE`. Datapack-decoded
production recipes still require serializers and real registration, which stays in 042B. Registration must reuse the exact same instances so that identity grouping
survives; that is a 042B review checkpoint, not a 042A one.

The ten toast-item keys (`chemical_crystallizer`, `chemical_dissolution_chamber`, `combiner`, `electrolytic_separator`, `thermal_evaporation_controller`,
`energy_tablet`, `antiprotonic_nucleosynthesizer`, `pressurized_reaction_chamber`, `rotary_condensentrator`, `precision_sawmill`) are resolved by key against the
vanilla `BuiltInRegistries.ITEM`, so the `MekanismItems` family is **not** admitted. Because those items are not registered, `getToastSymbol()` returns a
non-null, key-correct, currently-unbound holder. That limitation is an explicit non-claim and must be tested as such rather than hidden.

Required new layer-neutral holder mechanism (this is the only genuinely new production mechanism in 042A): the API layer today cannot reference `common`, so the
keyed-holder helper must be a **new API-layer source** — call it `mekanism/api/recipes/MekanismKeyedHolder` — implementing `Holder<T>`, `Supplier<T>` and
`INamedEntry` over a `ResourceKey<T>` plus a lazily attached vanilla `Holder<T>`, delegating `unwrap`, `unwrapKey`, `kind`, `canSerializeIn`, `tags`, `is(...)`,
`get`, `value` and `isBound` straight to the delegate whenever present. It must be cast-compatible with the existing `MekanismDeferredHolder` comparison sites,
or `MekanismDeferredHolder` must be generalized in the same change. The predecessor proposal in this document to "salvage" a `BaseKeyedHolder` from the pinned
NeoForge artifacts is **withdrawn**: no such source exists in this repository, and admitting one would be an unpriced extra delta. This helper's exact shape is
the single design decision 042A must settle before implementation.

Scope: **34 API additions, 0 common additions, 0 platform additions.** The measured closure is entirely API-layer: every one of the 33 files lives under
`src/api/java`, none imports `mekanism.common.`, and the new keyed-holder helper must stay dependency-free for exactly that reason. No common-layer register or
adapter is needed by 042A.

- API 129 → **163**; common stays **184**; platform stays **37**. Total 350 → **384**.
- Classification **U/A/N/R/M = 12/21/1/0/0** (12 + 21 + 1 = 34).
  - `U` byte-identical to `4646f509` (12): `ChemicalChemicalToChemicalRecipe`, `ChemicalToChemicalRecipe`, `ItemStackChemicalToItemStackRecipe`,
    `ItemStackChemicalToObjectRecipe`, `ItemStackToChemicalRecipe`, `ItemStackToItemStackRecipe`, `BiChemicalRecipeInput`, `ChemicalRecipeInput`,
    `FluidChemicalRecipeInput`, `ItemChemicalRecipeInput`, `SingleChemicalRecipeInput`, `SingleItemChemicalRecipeInput`.
  - `A` bounded adaptation (21): the other 14 abstractions (including `MekanismRecipeTypes`, whose 26 `DeferredHolder.create` sites become the new helper over
    concrete instances) + 5 FluidStack-bearing `vanilla_input` records + the 2 task targets.
  - `N` new source (1): `mekanism/api/recipes/MekanismKeyedHolder`.
- Exactly four authorized delta families:
  1. `net.neoforged.neoforge.fluids.FluidStack` → project `mekanism.api.fluid.FluidStack` (14 files: the 2 targets, 7 abstractions, 5 `vanilla_input`
     records);
  2. `net.neoforged.neoforge.common.util.TriPredicate` → project `mekanism.api.functions.TriPredicate` (1 file, `PressurizedReactionRecipe`);
  3. `MekanismAPI.MEKANISM_MODID` → literal `"mekanism"` (Task 032 precedent);
  4. `DeferredHolder.create(Registries.RECIPE_TYPE|ITEM, key)` → `MekanismKeyedHolder` over concrete recipe-type instances and by-key item resolution.

Review checkpoints that a 042A reviewer must hold the implementation to:

- `OutputHelper` semantics unchanged apart from the FluidStack import: the `operations == 0` early returns; `clampToInt(amountUsed / toAmount)`; the
  `INPUT_DOESNT_PRODUCE_OUTPUT` versus not-enough-space choice gated on `tank.getNeeded() > 0` and on `slot.getLimit(slot.getStack()) - slot.getCount() > 0`;
  item output count multiplication; the secondary-output loop in the `ChanceOutput` handler; and `shouldContinueChecking()` short-circuit ordering.
- `OneInputCachedRecipe` keeps `SEPARATOR_OUTPUT_EMPTY` ("either side empty") and `FLUID_OPTIONAL_ITEM_OUTPUT_EMPTY` ("fluid only") distinct, keeps `sawing`'s
  `ConstantPredicates.alwaysFalse()` output-empty check, and keeps `finishProcessing`'s null/empty guard.
- `getType()` returns a non-null instance that is `==` across calls for the same recipe family, and `RecipeManager.byType` grouping is stable.
- No write to `BuiltInRegistries.RECIPE_TYPE` or `BuiltInRegistries.ITEM` anywhere in production code.

Tests: **6 new methods, all ordinary** (nothing in 042A touches a registry or the network, so no Knot/loader isolation is required). Ordinary **75/372 → 76/378**,
aggregate **83/405 → 84/411**. Exactly one new suite; existing suites are not extended. The `verifyFabricTestContract` expectations in `fabric/build.gradle`
(hard-coded `readTotals(ordinaryXmlResults, 'ordinary', 75, 372)`, the aggregate 83/405 check, and the per-source `@Test` counts list) must be updated to the new
totals in the same change or `check` fails.

1. `OutputHelper` chemical tank: execute path, amount×operations true-long growth, `operations == 0` no-op, and `INPUT_DOESNT_PRODUCE_OUTPUT` versus
   not-enough-space selection.
2. `OutputHelper` fluid tank: same matrix over project `FluidStack`, including the `Integer.MAX_VALUE` simulate cap and integer-division truncation.
3. `OutputHelper` item slot: empty-output no-op, count multiplication, simulate/execute split, `slot.getLimit(...) - slot.getCount()` gating.
4. `OutputHelper` `ChanceOutput`: main-then-secondary ordering, secondary iteration count, `nextSecondaryOutput()` refresh, `shouldContinueChecking()`
   short-circuit.
5. `OutputHelper` record handlers (`PressurizedReactionRecipeOutput`, `FluidOptionalItemOutput`, `ElectrolysisRecipeOutput`): both-member ordering,
   empty-member early-out, tracker continuation.
6. `OneInputCachedRecipe` + holder wiring: the two empty-output predicates distinguish their cases, `sawing`'s always-false predicate, `finishProcessing`'s
   null/empty guard, `getType()` non-null identity stability, and toast holders non-null and key-correct while legitimately unbound.

Gates for 042A:

```
./gradlew -p fabric --no-daemon --console=plain \
  test fabricTransferTest fabricRegistryTest fabricDataMapTest \
  verifyFabricTestContract remapJar
./gradlew -p fabric --no-daemon --console=plain check build
./gradlew -p fabric --no-daemon --console=plain \
  verifyApiSliceNoNeoForge verifyCommonSliceNoNeoForge verifyPlatformSliceNoNeoForge
scripts/scan-platform-deps.py
./fabric-build.sh
FABRIC_SMOKE_STATIC_ONLY=1 scripts/fabric-packaged-smoke.sh
```

`./build.sh --clean` is not run. No real dedicated-server or client smoke is required, because 042A introduces no lifecycle, registry-frozen, networking or
runtime owner. `scripts/fabric-packaged-smoke.sh` must be extended with the exact new `mekanism/api/recipes/**` and `mekanism/common/recipe/**` class families
(including nested `vanilla_input` record classes and the new holder class) and must still reject serializers, production recipe JSON, fixture classes, and every
042B family.

042A non-claims: no recipe-type registration; no recipe serializers; no production recipe JSON; no `MekanismRecipeType`/`IMekanismRecipeTypeProvider`/
`RecipeTypeRegistryObject`; no concrete/basic recipe implementations; no `MekanismItems` and therefore no `getToastSymbol()`-correct items; no datapack-loaded
Mekanism recipes; no machines or tile entities; no JEI/EMI or client recipe viewer; no item/fluid/Chemical transaction or storage exposure; no machine-facing
`InputRecipeCache` index; no broader Fabric integration.

### 042B — BLOCK: recipe-type/serializer/index registration and concrete recipe family

Out of scope now. Before any code is written, a follow-up approval must explicitly open recipe-type and serializer registration ownership, decide whether
`MekanismRecipeType`'s client-recipe-viewer imports are severed or the client viewer is admitted, decide the `MekanismItems`/toast-item boundary, and state the
resulting slice and test arithmetic. 042B must also prove that registration reuses the exact 042A `RecipeType` instances so `byType` identity grouping does not
break. Until that approval exists, 042B must not be started.

## Repository records

This planning task adds only this document. It changes no slice, no gate, no build file, and no production or test source. A later 042A implementation must record:
exact slice arithmetic **163/184/37**; per-file `U`/`A`/`N` identity against `4646f509`; the four authorized delta families; the six new ordinary tests; and
updates to `docs/FABRIC_RECIPES.md`, `FABRIC_PORT.md`, `PORT_MATRIX.md`, `API_DELTAS.md`, and `FABRIC_SMOKE.md`.

## 042A implementation outcome (recorded after landing)

042A was implemented from clean commit `cd263092` in an isolated worktree by the single DeepSeek implementation agent, exactly as planned and with no scope
expansion into the blocked 042B.

Measured result:

- slices **129/184/37 → 163/184/37** (34 new API sources, 384 compiled production sources; common 184 and platform 37 unchanged);
- classification `U`/`A`/`N` = **12/21/1**, verified per file against `4646f509` (the twelve `U` sources are byte-identical, the twenty-one `A` sources carry
  only the four authorized delta families, and `MekanismKeyedHolder` is the single new source);
- exactly four delta families are present and no others: project `FluidStack` import (14 files), project `TriPredicate` import (1 file), literal `"mekanism"` for
  `MekanismAPI.MEKANISM_MODID` (which also drops the 15-file module/gear/radial group), and `MekanismKeyedHolder` replacing `DeferredHolder.create(...)` at the 26
  recipe-type sites and the 10 toast-item sites;
- ordinary **75/372 → 76/378**, transfer **5/27**, root **1/2**, data-map **2/4**, aggregate **83/405 → 84/411**; one new ordinary suite with exactly six methods;
- `fabric/build.gradle` slice/count guards, the per-source `@Test` list, `readTotals(ordinary...)`, and the aggregate check were updated in the same change;
- `scripts/fabric-packaged-smoke.sh` admits exactly the new `mekanism/api/recipes/**` outer and nested classes and rejects the 042B families, serializers,
  production recipe JSON, and fixture/test classes.

Design decisions actually taken:

- `MekanismKeyedHolder` is a dependency-free `Holder<T>`/`Supplier<T>` in the API layer with two factories: `keyed(...)` for identity-stable non-registered values and
  `registryKeyed(...)` for lazy by-key registry resolution. It mirrors the typed-key shape of `DeferredHolder.create(Registry, ResourceLocation)` via an overload
  taking `ResourceKey<? extends Registry<R>>` plus the concrete value, which is required so `MekanismRecipeTypes`'s public field types can be `RecipeType<X>` rather
  than `RecipeType<?>`. `getKey()`, `key()`, `getName()`, `getId()`, and `asOptional()` keep the common-layer named-entry surface callable without a common import.
- `MekanismRecipeTypes.TYPE_*` are concrete non-registered `RecipeType` instances created by one private factory; `BuiltInRegistries.RECIPE_TYPE` receives no write.
- The toast holders stay non-null and key-correct but unbound, which is asserted as an explicit non-claim in the new suite.
- The plan's "cast-compatible with the existing `MekanismDeferredHolder` comparison sites" requirement needed no change to `MekanismDeferredHolder`: no admitted slice
  consumes the extra `is(R)`/`keyMatches(Holder)` helpers, and `equals`/`hashCode`/`is(...)`/`isBound`/`unwrap*`/`kind`/`tags` are shared `Holder` surface on both
  types. `MekanismKeyedHolder` therefore adds `getKey`/`key`/`getName`/`getId`/`asOptional` to keep the named-entry surface callable without a common-layer import.

Verified gates: `test fabricTransferTest fabricRegistryTest fabricDataMapTest verifyFabricTestContract remapJar`; `check build`; the three
`verify*SliceNoNeoForge` tasks; `scripts/scan-platform-deps.py`; `fabric-build.sh`; and `FABRIC_SMOKE_STATIC_ONLY=1 scripts/fabric-packaged-smoke.sh`.
As planned, `./build.sh --clean` and the real dedicated-server/client smokes were not run, and no push, deploy, production, mirror, or secret operation was performed.
