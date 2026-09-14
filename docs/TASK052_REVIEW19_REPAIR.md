# Task052 review19 repair — ingredient-type registry ID equality, smoke class family, test generics, evidence correction

Baseline clean parent `6a11b3a4` (docs `TASK052_REVIEW_19.md`). The WIP branch
`pi-agent-be680cb1-c5bd-437` (`1b273877`, canonical chemical ingredient type registry) was merged FIRST on branch
`task052-review19-fix` (merge `64ffcf0a`), so all prior ancestry and WIP is preserved. Only the three concrete REVIEW19
repairs plus the evidence-document correction were applied; no other ownership, registry, module/robit, or global
investigation was performed. Task052 remains unaccepted.

## 1. `FabricChemicalIngredientTypeRegistryLifecycle.verify` ID comparison

File: `fabric/src/main/java/mekanism/fabric/chemical/FabricChemicalIngredientTypeRegistryLifecycle.java`.

`verify` compared `candidate.getKey(held) != builtIn.id()` and `ChemicalIngredientTypeRegistry.idOf(...) != builtIn.id()` by
object reference. `ResourceLocation` is a value identifier (`ResourceLocation.equals` is namespace/path based, and every
`ResourceLocation.fromNamespaceAndPath`/`parse` call returns a fresh instance), while `ResourceKey` interning
(`ResourceKey.create(...)` → weak-valued `VALUES` map keyed by an `InternKey` record using the registry/location values) may
retain a previous equal-but-distinct `ResourceLocation` inside the returned key. A reference test could therefore fail on a
genuinely correct registry.

Repair: compare the returned IDs by value (`!builtIn.id().equals(...)`), while the registry, codec (`held != builtIn.codec()`)
and holder (`candidate.getHolder(entryKey).orElseThrow().value() != builtIn.codec()`) stay reference comparisons. This is the
same value-vs-identity split the canonical registry itself relies on.

## 2. Packaged-smoke class family

File: `scripts/fabric-packaged-smoke.sh`.

`expected_platform_chemical` is checked for exact set equality of every `mekanism/fabric/chemical/*.class` entry in the
release artifact, so the nested `private record BuiltIn` emitted by the new owner
(`FabricChemicalIngredientTypeRegistryLifecycle$BuiltIn.class`) made the gate reject. The real emitted class name was verified
directly with `javac` on the owner (not guessed): the compile output directory contains
`FabricChemicalIngredientTypeRegistryLifecycle.class` and `FabricChemicalIngredientTypeRegistryLifecycle$BuiltIn.class`. The
expected set now includes the nested class; the gate is unchanged in strength (no filter relaxed, no `BuiltIn` removed).

## 3. Root test codec registry generic type

File: `fabric/src/fabricRegistryTest/java/mekanism/fabric/registry/FabricRegistryRootIntegrationTest.java`.

The new `@Order(3)` method used a raw cast `MappedRegistry<ChemicalIngredient> registry =
(MappedRegistry) FabricChemicalIngredientTypeRegistryLifecycle.initialize();`, which is the wrong type argument
(`ChemicalIngredient` is the registry value of the *other* registry; the ingredient-type registry's value is
`MapCodec<? extends ChemicalIngredient>`). It now uses the actual
`MappedRegistry<MapCodec<? extends ChemicalIngredient>>` type with no raw cast.

One focused equal-but-distinct-ID regression was added in the same method (no new fixture): a freshly constructed
`mekanism:empty` `ResourceLocation` must be `assertNotSame` from the constant yet `assertEquals` to it, must resolve through
`codec(...)` to the exact `EmptyChemicalIngredient.CODEC` identity, and `idOf(EmptyChemicalIngredient.CODEC)` must be equal to
it. This is what actually pins the value-equality behaviour repaired in (1).

## 4. Evidence-document correction

File: `docs/TASK052_CONTINUATION12_INGREDIENT_REGISTRY.md`.

The initialization-path section incorrectly grouped `IngredientServiceCodecClosureTest` with `ChemicalTestBootstrap` as a
"directly installs the native registry" bootstrap. The actual source calls
`FabricChemicalIngredientTypeRegistryLifecycle.initialize()` and lives in the Knot `fabricTransferTest` source set
(`IngredientServiceCodecClosureTest.java:66`, `fabric/build.gradle` `fabricTransferTest` compileClasspath sees `main` output).
Only the ordinary `ChemicalTestBootstrap` installs the genuine native registry directly, because the plain JVM root is already
frozen by `Bootstrap.bootStrap()`. The document now states that distinction.

## Executed verification (honest)

Fresh pinned classpath generated through Loom (`scripts/task052-renderer-classpath.init.gradle`,
`fabric/build/task052-main-compile-classpath.txt`).

- **PASS `javac` of the changed production owners**: `ChemicalIngredientTypeRegistry`,
  `FabricChemicalIngredientTypeRegistryLifecycle`, `MekanismChemicalIngredientTypes` — 0 errors (only pre-existing
  deprecation/unchecked notes).
- **PASS `javac` of the changed test sources**: `FabricRegistryRootIntegrationTest` (including the new ID regression),
  `ChemicalTestBootstrap`, `IngredientServiceCodecClosureTest` — 0 errors.
- **PASS emitted-class check**: the real `javac` output of the owner contains
  `FabricChemicalIngredientTypeRegistryLifecycle$BuiltIn.class`.
- **PASS Python syntax check** of the modified `scripts/fabric-packaged-smoke.sh` heredoc block.

Not executed / not claimed: no test method was run (`fabricRegistryTest` and `fabricTransferTest` still depend on the whole
unported production slice and cannot compile as Gradle tasks — the same pre-existing blocker as REVIEW17), no packaged smoke,
no runtime/server/client fixture, no whole-slice/MekanismAPI/root compile, no Task052 acceptance.
