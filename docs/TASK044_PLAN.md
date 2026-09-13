# Task 044 — Rotary input-cache ownership closure

## Parent-defined scope

The stopped reconnaissance attempt produced no implementation. This replacement task completes the remaining bidirectional input-cache shape before integrating the common recipe-type owner. It is not a provider/monitor implementation task and has no file-count target.

The current `MekanismRecipeType` combines canonical type registration, all input-cache constructors, global cached recipe lists, client/server fallback, and synthetic vanilla smelting. Porting provider + owner + monitor together would conflate independent registry identity and lifecycle changes. Existing API recipe types already own registered canonical instances; no second recipe-type instance may be introduced.

## Implementation

Port `src/main/java/mekanism/common/recipe/lookup/cache/RotaryInputRecipeCache.java` onto the already implemented `AbstractInputRecipeCache` contract:

- Accept `RecipeCacheSource<RotaryRecipe>` rather than the unsliced common type owner, matching sibling caches.
- Replace NeoForge FluidStack with the existing audited Fabric fluid value type used by `FluidInputCache` and the sliced RotaryRecipe.
- Move chemical/fluid indices and complex fallback sets into a per-binding CacheState. Use `resolveState(world)`; do not add global mutable state or a second context mechanism.
- Preserve direction flags, type-only insertion predicates, amount-sensitive recipe selection, indexed-first fallback, and empty-input rejection.
- Inherit clear/rebind/thread ownership behavior from the shared cache base. No-context lookups must not serve stale recipes; mismatched non-null Level managers must retain the existing failure contract.
- Add the class to the exact common slice and update static packaged inclusion/exclusion contracts that currently exclude RotaryInputRecipeCache. Do not broadly whitelist recipe lookup packages.

## Acceptance

Add a minimal focused test suite (normally 3–6 tests) covering independent rotary risks: one-way direction exclusion and bidirectional operation; insertion-vs-processing amount semantics; complex fallback; binding replacement/clear isolation of both directional indices. Reuse lower-level ownership fixtures rather than retesting their full matrices. Any additional tests require independent mutation justification under AGENTS.md.

Run focused tests, full local tests including isolated integration tasks, slice/isolation checks, Fabric build, and static final-JAR contract. Record actual commands, counts, and results; do not claim a runtime gate from static inspection. Parent independently inspects the diff, reruns final gates, packaged smoke and clean release.

## Exclusions and stop condition

Do not port MekanismRecipeType, IMekanismRecipeTypeProvider, monitors, factories, recipe-viewer integration, synthetic smelting, tiles, or remaining cached-recipe families. Do not change recipe type registration or codecs. Do not alter production, deploy, push, delete tests, scan broad filesystem roots, use zvec, or edit read-only references.

Read the target, sibling cache implementation, its fluid/chemical index types, existing focused fixtures and exact gate entries; then implement. No new global dependency scan or candidate comparison. If the specified cache cannot be implemented through the existing source/state interfaces, report the concrete incompatible signature and required scope change rather than expanding the task. First checkpoint must contain either a real cache implementation diff or a concrete blocker, not another broad plan.

## Implementation result (recorded by the implementer)

Implemented in an isolated worktree from clean baseline `8a4c61050d01c29ade0cd05f6dd1ee2cafc99993`.

Change set:

- `src/main/java/mekanism/common/recipe/lookup/cache/RotaryInputRecipeCache.java`: constructor now takes `RecipeCacheSource<RotaryRecipe>`; both
  `ChemicalInputCache`/`FluidInputCache` indices and both complex fallback sets moved into a private `State extends CacheState`; `newState()` and
  `initCache(recipes, state)` implemented; every lookup resolves through `resolveState(world)`; the class' own `clear()` override removed; the NeoForge fluid type
  replaced by `mekanism.api.fluid.FluidStack`. Direction flags, type-only insertion, amount-sensitive processing, empty rejection and indexed-first fallback are
  unchanged. Delta against official `4646f509` is the cache re-hosting plus that one fluid-type swap.
- `fabric/common-slice.txt` + `fabric/build.gradle`: the class is now an exact common-slice entry (188 -> 189) and the pinned slice count follows.
- `scripts/fabric-packaged-smoke.sh`: `RotaryInputRecipeCache.class` and `RotaryInputRecipeCache$State.class` added once each to the expected common recipe class
  family; the class removed from the deferred-family rejection list.
- `fabric/src/test/java/mekanism/common/recipe/lookup/cache/RotaryInputCacheContextTest.java`: five focused methods driving the real production cache over real
  `BasicRotaryRecipe` values via `RecipeCacheSource.forType(MekanismRecipeTypes.TYPE_ROTARY.value())`; `fabric/build.gradle` pins the new source and its five `@Test`
  methods and moves the ordinary totals 77/384 -> 78/389 and aggregate 86/422 -> 87/427.

Executed commands and results:

- `./gradlew -p fabric test --tests mekanism.common.recipe.lookup.cache.RotaryInputCacheContextTest`: PASS, 5 tests.
- `./gradlew -p fabric test fabricTransferTest fabricRegistryTest fabricDataMapTest fabricRecipeRegistryTest verifyFabricTestContract verifyApiSliceNoNeoForge verifyCommonSliceNoNeoForge verifyPlatformSliceNoNeoForge verifyOfficialRootTestSources`: BUILD SUCCESSFUL. JUnit XML totals read back
  independently: ordinary 78 suites/389 tests, transfer 5/27, root-registry 1/2, data-map 2/4, recipe-registry 1/5; aggregate 87/427.
- `./gradlew -p fabric verifyChemicalRuntimeFixtureIsolation verifyClientWindowFixtureIsolation verifyDataMapReloadFixtureIsolation verifyHandshakeFixtureIsolation verifyRecipeRuntimeFixtureIsolation`: BUILD SUCCESSFUL.
- `scripts/scan-platform-deps.py`: exit 0 (inventory; no NeoForge import entered the cache).
- `./fabric-build.sh`: BUILD SUCCESSFUL (`remapJar` included).
- `FABRIC_SMOKE_STATIC_ONLY=1 scripts/fabric-packaged-smoke.sh`: PASS (static remapped-JAR gates only); the remapped JAR contains
  `RotaryInputRecipeCache.class` and `RotaryInputRecipeCache$State.class` exactly once.
- Source identity: `git show 4646f509:.../RotaryInputRecipeCache.java` vs the new file is 46 insertions / 36 deletions, all within the authorized cache re-hosting
  and the one fluid-type swap; no other file changed outside the listed set.

`./build.sh --clean` and the real dedicated-server/client smokes are deliberately not run in this worktree; the parent performs the runtime smoke and clean release
independently. No production, mirror, deploy, push, secret, zvec or broad-root scan operation was performed.
