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
