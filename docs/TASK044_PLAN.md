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

## Parent acceptance (2026-09-13)

- Integrated candidate `45a5c96a`; checked the real nine-file diff. Independent read-only review found no production regression; findings and their disposition are recorded in `MODEL_IMPLEMENTATION_COMPARISONS.md`.
- Parent follow-up `afd984b6abcb5bb7ef467f114f6b904e73a6eac2` strengthens the existing five tests (no added test methods): populated fluid fallback survives neither clear nor rebind; indexed candidates take precedence, but an indexed amount failure permits fallback. Chemical fallback is unreachable in the current index implementation, not synthetically exercised. No production code changes after the candidate.
- Independently reran the complete command from candidate acceptance, combining all five test tasks, `verifyFabricTestContract`, three slice checks, official-source check and all five fixture-isolation tasks, with `--rerun-tasks`: PASS, 49s. XML checked again after clean release: ordinary 78/389, transfer 5/27, registry 1/2, data-map 2/4, recipe-registry 1/5; aggregate **87 suites / 427 tests**, zero failures/errors/skips. Final log: `/tmp/task044-parent-final-tests.log`.
- `./build.sh --clean`: PASS (clean 855ms, build 52s), exactly one release JAR. `build-info.json.source_commit` is **`afd984b6abcb5bb7ef467f114f6b904e73a6eac2`**, the tested implementation/review commit; this later documentation-only acceptance commit is not misrepresented as the artifact's source. Log: `/tmp/task044-parent-clean.log`.
- `scripts/fabric-packaged-smoke.sh`: PASS, both static contracts and a real disposable dedicated server. The server reached `Done (5.416s)` and emitted `MEKANISM_FABRIC_RECIPE_REGISTRATION_PROOF recipe_types=26 serializers=26 identity_preserved=true`; other required startup proof contracts passed. Diagnostics: `build/fabric-packaged-smoke/20260913T113837Z-3200153/`; log: `/tmp/task044-parent-smoke.log`. This does not claim a paired-client runtime gate.
- Artifact: `Mekanism-Fabric-Bootstrap-10.7.19.85-fabric-bootstrap.jar`, SHA-256 **`d5e3b17b3ecfdb2c25ed6a755e3eddabd07b6a2b77be59ba1632db69e284d795`**. Parent recalculated the release and smoke JAR hashes and confirmed equality; rotary outer and `$State` classes each occur exactly once. Generated `dist/` was moved unchanged to gitignored `build/task044-release/` (including build-info), not committed.
- Task044 accepted. No production, mirror, deployment or push action. Common recipe-type owner/provider integration remains the next separately bounded task; no such integration is claimed here.
