# Task 045 — canonical common recipe-type owner and provider

## Parent GO and evidence

Baseline: Task044 accepted at `65b2b5d4`. This is the next complete ownership boundary, not a Crusher-only framework and not a monitor/tile task. The parent read the full owner/provider, both specialized registration classes, common/Fabric deferred registration and holder implementations, API type declarations/keyed holder, RecipeCacheContext/Source and both live lifecycle publishers.

- `MekanismRecipeType` currently implements both vanilla `RecipeType` and the full common provider, declares all 26 input-cache shapes, stores a global recipe list, and handles incomplete checking plus synthetic vanilla smelting. All cache shapes now exist in the Fabric slice. Preserve the complete owner/provider public surface rather than omit smelting or nominal RecipeType assignability.
- API `MekanismRecipeTypes` currently constructs 26 anonymous objects and writes them at `ensureRegistered()`. A common wrapper that also implements RecipeType would split identity. Replacing already-observed objects during registration is equally invalid.
- There is a static-init trap: API names/types and common declarations cannot eagerly resolve each other in both load orders. Common declaration construction must not read API static fields, resolve ingredients/recipes, or write registries.
- Existing `MekanismServices` + layer-neutral interface + exact service resource is the established API-to-common discovery pattern (IngredientCreatorAccess). Preserve API neutrality; do not import common implementation types into API.
- `MekanismDeferredRegister` already delegates actual registry writes/canonical binding to FabricDeferredRegister; the latter rejects duplicate keys and repeat registration. Reuse this lifecycle, not a second registrar.
- The existing binding owns exact manager, epoch, and all input-cache state but lacks RegistryAccess. Both real publishers know the corresponding server/connection registry access. Synthetic smelting requires that access, not a process-global fallback.
- `ItemStackIngredient.of(List<Ingredient>, 1)` already implements audited compound union semantics; `RecipeViewerUtils.synthetic(id, prefix)` is exactly `id.withPrefix("/" + prefix + "/")`. No client recipe-viewer closure or new ingredient adapter is needed.

## Fixed implementation route

### 1. One canonical object, common ownership

Admit and port `src/main/java/mekanism/common/recipe/MekanismRecipeType.java`, `IMekanismRecipeTypeProvider.java`, and `common/registration/impl/{RecipeTypeRegistryObject,RecipeTypeDeferredRegister}.java`. Keep all 26 declarations and precise generics, public owner/provider methods, and vanilla RecipeType assignability.

The common declaration owner creates each concrete MekanismRecipeType once, before registry writes, and its existing specialized holder exposes that same prebuilt object. Rehost the existing RecipeTypeRegistryObject, do not invent a parallel temporary holder. A viable minimal implementation keeps its MekanismDeferredHolder parent and adds a one-time prebuilt value used before actual canonical binding; after binding, identity must agree with that value. RecipeTypeDeferredRegister.registerMek eagerly creates once, registers a supplier returning exactly that value through the existing deferred machinery, and exposes immutable ordered entries needed by owner enumeration. Do not weaken the generic deferred registry lifecycle.

Keep common declarations independent of API clinit: use the literal official paths in the common declaration table and construct the `mekanism` ResourceLocations locally (API public NAME_* constants remain unchanged). Verify the complete name/type bijection rather than depend on a fragile cross-class initializer order. Construction of input-cache shells may capture `this::getRecipes` but must not evaluate it at initialization time.

Add one narrowly scoped layer-neutral API service interface (for example `mekanism.api.recipes.IRecipeTypeRegistry`) providing canonical resolution by ResourceLocation and registration, plus one common implementation using the real common declaration owner. API `MekanismRecipeTypes.newRecipeType` obtains the canonical common value through that service and wraps it with the existing MekanismKeyedHolder; eliminate anonymous RecipeType creation. `ensureRegistered()` invokes the common registry service, then attaches vanilla canonical holders to API holders; it must not perform duplicate writes. Preserve repeat-call idempotence. Use `MekanismServices.load`, an exact service resource under `src/main/resources/META-INF/services/`, and explicit build resource inclusion. Missing service must fail honestly, not fall back to another anonymous identity.

Preserve public API holder generic types and official ids. Both API-first and common-first initialization must produce the same non-null pre-registration identities, and registration must preserve those identities for all 26. No runtime replacing, unsafe static rewriting, or second RecipeType per id.

### 2. Binding-owned lists and full provider behavior

Remove global `cachedRecipes`, FMLEnvironment, ServerLifecycleHooks, MekanismClient and monolithic Mekanism dependencies. Use a class-local SLF4J logger for incomplete diagnostics. Preserve complete provider methods, `checkIncompleteRecipes` overloads, `getRecipeFor`, `byKey`, and strict synthetic cast type check.

Extend the existing RecipeCacheContext.Binding with optional RegistryAccess and type-safe owner recipe-list state; do not add another ThreadLocal/global context. Keep the existing `bind(manager, epoch)` overload (no registry access), adding an explicit access-bearing overload. Recipe lists are immutable, cache empty results too, and live only inside that binding. Use the existing source abstraction as the owner/source key where possible. Input-cache creation uses the owner's recipe-list method so smelting and filtering are shared, not bypassed with `forType`.

Contract:
- Level/no-arg provider lookup follows the existing input-cache binding contract: no context => empty; non-null mismatched Level manager => fail; null Level uses the bound manager/access. No stale global world fallback.
- Explicit `getRecipes(manager, registryAccess)` always queries that supplied manager/access. It may reuse binding-owned lists only when both identities match; otherwise compute an uncached immutable result rather than pollute current state or reject a legitimate explicit query.
- `getRecipes(manager)` may borrow access only from a matching current binding; absent access, serve own recipes without synthetic vanilla smelting, as the existing owner's nullable-access path specifies.
- clearCache clears both recipe-list and all input-cache state for the current binding; rebind discards both atomically; another thread/binding is unaffected.
- Update server start/successful-reload publishers with server registryAccess. Update the client recipe-packet tail publisher with the current matching connection registryAccess (the Level may not yet exist); reject/avoid access borrowed from a foreign connection. Retain failed reload, stop/disconnect and late-update behavior. Adjust isolated fixtures only as required to pass real context, not fake production callbacks.

### 3. Synthetic smelting and exact inclusion

Preserve own-recipes-first ordering, complete-recipe filtering, the official skip conditions (special/incomplete/empty output/no ingredients), output semantics and `/mekanism_generated/` id prefix retaining original namespace. Use existing `ItemStackIngredient.of(ingredients, 1)` instead of NeoForge CompoundIngredient; use ResourceLocation.withPrefix directly instead of importing RecipeViewerUtils. Do not remove synthetic support to shrink scope.

Precisely update API/common/platform slices, resource includes, class-count and leakage contracts. Replace the API anonymous `$1` expected class and deferred-owner exclusions with actual exact admitted classes/service; do not whitelist packages. No items/tiles, production recipe JSON, client viewer, monitors, factories, codec registration expansion, or other cached-recipe families.

## Verification and checkpoint

First checkpoint at about 10–15 minutes: show a real canonical owner/provider diff or a concrete incompatible signature. This is not an invitation to choose another closure or rerun global dependency scanners. If the fixed route needs nominal/public behavior loss, a second identity, or cannot avoid initialization cycles, report the exact blocker and proposed scope decision before proceeding.

Normally 6–8 new focused tests total, reusing existing registry/lifecycle fixtures and exact official oracles; justify anything beyond 8 by independent mutation. Cover independent risks, not 26 copies of one test:

1. API-first and common-first initialization in fresh JVM contexts, then all-26 canonical registry/holder/Recipe.getType identity; preserve public consumer generic assignability.
2. Real provider and input-cache agreement over manager recipes, complete filtering, immutable/empty cached lists, clear/rebind and foreign explicit-manager isolation.
3. Level mismatch is a real executable check, not a misleading test name; no-context/owner clear behavior.
4. Synthetic smelting inclusion/order/id/output/union and exclusions, null versus explicit access, and no cross-access cached output reuse. Reuse lower ingredient tests; do not re-test vanilla itself.
5. Incomplete reporting and generic helper filtering preserve special recipe behavior.

Candidate gates: focused and all five full local test groups, exact slice/source-delta/fixture isolation contracts, Fabric build and static final-JAR/service/leakage gates. Initializer order must execute, not just compile. No clean release or generic packaged server duplicate in the candidate. Because this task changes live registry/cache access lifecycle, parent must additionally run `scripts/fabric-recipe-runtime-smoke.sh` on the final implementation (extend fixture proof to owner/registryAccess where needed), besides independent full tests, one packaged server smoke and one clean release. Independent fixed-candidate read-only adversarial review is required for canonical initialization, registry lifecycle, binding/access identity and smelting semantics.

Record actual commands/results and source deltas in task/topic docs. No test deletion, production/mirror/deploy/push, zvec/indexing, broad-root scan or upstream edits. If a DeepSeek quota error occurs, checkpoint and pause rather than switching model.

## Parent verification (2026-09-13)

Implementation candidate `436fb30e` was integrated and inspected. Fixed implementation **`c1c9aa3fabae9e5436e552d5652478d790460510`** adds the parent corrections recorded in `FABRIC_RECIPES.md`; the fixed-candidate independent review and targeted follow-up are recorded in `MODEL_IMPLEMENTATION_COMPARISONS.md`. No runtime evidence is inferred from the candidate's static-only gates.

- Parent red/green reproduction: post-registration common holder access in both fresh-JVM initialization-order tests failed with StackOverflowError before the concrete parent-getter correction, then passed. Logs `/tmp/task045-parent-bound-holder-{red,green}.log`. The common holder assertions cover all26 through get/value/getRecipeType, not only the API/vanilla views.
- `./gradlew -p fabric check compileRecipeRuntimeFixtureJava verifyChemicalRuntimeFixtureIsolation verifyClientWindowFixtureIsolation verifyDataMapReloadFixtureIsolation verifyHandshakeFixtureIsolation verifyRecipeRuntimeFixtureIsolation --rerun-tasks`: PASS, 63s, 30 tasks executed. Log `/tmp/task045-parent-fixed-check.log`. After clean release, XML independently checked again: ordinary79/395, transfer5/27, registry1/2, data-map2/4, recipe-registry3/7; aggregate **90 suites / 435 tests**, zero failures/errors/skips.
- `scripts/fabric-recipe-runtime-smoke.sh`: PASS with real isolated dedicated server and physical client JVM. Required initial/reload/reconnect phases additionally emitted `MEKANISM_RECIPE_RUNTIME_CLIENT_OWNER published_access=true canonical=true synthetic_smelting=true` at epochs1/2/1. Failed reload retained epoch2; disconnect/reconnect and late-update rejection passed. Server fixture checked real bound access, common/API registered identity and synthetic smelting through owner/input-cache before its required phase proof. Diagnostics `build/fabric-recipe-runtime-smoke/20260913T124842Z-3617385/`; log `/tmp/task045-parent-recipe-runtime.log`.
- `./build.sh --clean --out build/task045-release`: PASS (clean892ms, build67s), exactly one JAR and build-info. `scripts/fabric-packaged-smoke.sh`: PASS for static contracts and real disposable dedicated server, reached `Done (5.553s)` with 26 recipe types /26 serializers /identity_preserved=true plus all other required startup proofs. Diagnostics `build/fabric-packaged-smoke/20260913T125151Z-3637296/`; logs `/tmp/task045-parent-clean.log`, `/tmp/task045-parent-packaged.log`.
- Artifact `build/task045-release/Mekanism-Fabric-Bootstrap-10.7.19.85-fabric-bootstrap.jar`: SHA-256 **`6477749433f1b9ea480615cff2377e52a711f1e1dffa0f28667a5ab623f7e877`**. Parent recalculated and matched release and smoke JAR hashes; build-info source_commit is exactly **`c1c9aa3fabae9e5436e552d5652478d790460510`**, not this later documentation-only evidence commit. Generated artifact/diagnostics stay gitignored. No production/mirror/deploy/push actions.

Targeted independent follow-up `717a8a96-59c6-4bb` accepted fixed `c1c9aa3f`: P1/P2 closed, no remaining concrete blocker. Combined with the independent gates above, **Task045 is accepted**. Next bounded task: `TASK046_PLAN.md` (lookup handlers and generic monitor ownership).
