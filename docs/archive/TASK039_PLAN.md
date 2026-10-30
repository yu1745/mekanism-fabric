# Task 039 plan — client window configuration and concrete inventory slots

Status: **complete and final-release verified**

## Candidate checkpoint

SOL5.6 high produced isolated commit `c591ad4df395c33874f32b0a0203c9e3b4adbfad` from exact parent `2921ee0f` after 3997.1s, 505.9k tokens and 326 tool calls (about `$21.52`; run ended at its 240-turn bound). The cumulative candidate is 32 changed files, `+1583/-113`, and reports all candidate gates passing at the planned **75 suites / 353 tests** with candidate remapped JAR SHA-256 `ffb87660205c6a70612b589ad8c1315e84ed7c3e2f3b575aeb410b90f9aac7fa`. It did not run clean release, the parent dedicated-server rerun, deployment or parent review.

This report is not acceptance. Parent source inspection before review identified three explicit questions for adversarial verification: whether a queued save can snapshot x/y/pin between individual config-value updates because the save worker does not acquire the update admission monitor; whether the existing atomic-move fallback weakens the promised failed-write contract; and whether manually invoking Fabric JOIN/DISCONNECT invokers with null handlers is adequate evidence for the documented real connection-lifetime claim. The fixture also did not exercise Armor/Offhand with the planned real connected player. Integration remains forbidden until an independent high-risk review resolves these and the full source/artifact boundary.

GLM 5.3 high independently reviewed the exact candidate in run `099d5295-f34c-41b` (1286.0s, 211.3k tokens, 111 tool calls, about `$2.34`) and returned **ACCEPT WITH FIXES**. It reproduced full `check` at **75/353**, slices **65/145/24**, static packaged families/resources/leakage, and verified the 28-source oracle deltas. Its executable stress probe observed 72 transient torn x/y disk snapshots in 85,615 saves, but proved this race already exists in the official asynchronous per-field update and converges through the FIFO update-owned save; production code need not diverge from the oracle. Required repair is to document that bounded race and re-qualify the pre-existing ATOMIC_MOVE→REPLACE_EXISTING fallback. Parent additionally promotes the reviewer’s optional fixture cleanup: do not invoke Fabric JOIN/DISCONNECT callbacks with null network handlers or label them as real reconnect evidence; the current owner’s connection independence is structural, while an actual same-process reconnect remains unproved. Armor/Offhand hook equivalence was source/bytecode reviewed and its absent real-player test remains an explicit evidence boundary, not a runtime claim.

The corrective candidate preserves all production code while adding the two config-contract qualifications and removing the synthetic null connection-event calls and associated runtime overclaim from the isolated fixture, smoke markers, and docs. Its completed focused and reconstruction gates are recorded below; focused rereview remains required.

SOL high reconstructed the candidate as `3b143709` over durable main and committed the focused correction as `58a62d7463d33d0640fe5c3553343cccdb3982b8` (505.6s, 115.7k tokens, 58 tool calls, about `$1.85`). Production source is tree-identical to `c591ad4d`; only three docs, the isolated client fixture and its smoke script differ. Focused12/12, full75/353, slices65/145/24, static packaged gate and corrected two-fresh-client smoke passed; the remapped JAR remains `ffb87660205c6a70612b589ad8c1315e84ed7c3e2f3b575aeb410b90f9aac7fa`.

Fresh GLM 5.3 high focused rereview `2a39f6d6-304a-459` (196.4s, 53.7k tokens, 31 tool calls, about `$0.28`) returned **ACCEPT**. It verified production zero-diff, both persistence-contract corrections, removal of all synthetic null connection events/old markers, honest two-physical-client-lifetime evidence, explicit reconnect/real-player boundaries, plan integrity, arithmetic and fixture isolation. Task039 is approved for integration and parent final gates.

## Baseline and reason

- Candidate baseline: clean `java-port` commit `2921ee0ff184126e6154fce94f460831b1e03668` (the committed approved-plan revision).
- Official source oracle: `4646f509`.
- Starting slices: API/common/platform **65/122/22**; tests **72 suites / 334 tests**.
- User direction in `AGENTS.md`: use complete 15–40+ dependency/owner verticals rather than repeatedly stopping at small prerequisite batches.

Astra high planning (`c30cf836-80cd-4c1`, 402.1s, 118.6k tokens, 80 tool calls, about `$2.43`) returned GO for a 28-production-Java-file vertical. Earlier Task039 dependency investigations established that the concrete-slot predicate and warning/menu graph is bounded; the remaining owner is the physical-client `client.toml` window fragment. Full official `ClientConfig` is not selected because its public QIO enum fields lead into the real QIO container/frequency/security/network/chemical SCC, not merely a larger declaration list. That SCC must not be replaced by copied enums or no-op owners.

The selected batch uses an explicit Fabric platform delta: one sole forward-compatible `client.toml` fragment owner owns `window.*` and preserves unowned sections. It does not claim complete NeoForge config-loader parity. External file watching, NeoForge config events, rotating backups, comment-only correction and unspecified-key pruning remain explicit deferrals.

## Production scope

### New/admitted common sources — 23

1. `src/main/java/mekanism/common/MekanismLang.java`
2. `src/main/java/mekanism/common/util/text/TextUtils.java`
3. `src/main/java/mekanism/common/config/MekanismConfigTranslations.java`
4. `src/main/java/mekanism/common/config/TranslationPreset.java`
5. new `src/main/java/mekanism/common/config/ClientWindowConfigAccess.java`
6. `src/main/java/mekanism/common/inventory/container/SelectedWindowData.java`
7. `src/main/java/mekanism/common/inventory/container/IGUIWindow.java`
8. `src/main/java/mekanism/common/inventory/slot/BasicInventorySlot.java`
9. `src/main/java/mekanism/common/inventory/slot/InputInventorySlot.java`
10. `src/main/java/mekanism/common/inventory/slot/OutputInventorySlot.java`
11. `src/main/java/mekanism/common/inventory/container/slot/IInsertableSlot.java`
12. `src/main/java/mekanism/common/inventory/container/slot/InventoryContainerSlot.java`
13. `src/main/java/mekanism/common/inventory/container/slot/InsertableSlot.java`
14. `src/main/java/mekanism/common/inventory/container/slot/MainInventorySlot.java`
15. `src/main/java/mekanism/common/inventory/container/slot/HotBarSlot.java`
16. `src/main/java/mekanism/common/inventory/container/slot/ArmorSlot.java`
17. `src/main/java/mekanism/common/inventory/container/slot/OffhandSlot.java`
18. `src/main/java/mekanism/common/inventory/container/slot/IVirtualSlot.java`
19. `src/main/java/mekanism/common/inventory/container/slot/VirtualInventoryContainerSlot.java`
20. `src/main/java/mekanism/common/inventory/warning/ISupportsWarning.java`
21. `src/main/java/mekanism/common/inventory/warning/IWarningTracker.java`
22. `src/main/java/mekanism/common/inventory/warning/WarningTracker.java`
23. `src/main/java/mekanism/common/tile/component/config/slot/InventorySlotInfo.java`

### New/adapted platform sources — 5

24. new `fabric/src/main/java/mekanism/fabric/config/FabricWindowConfig.java`
25. new `fabric/src/main/java/mekanism/fabric/config/FabricClientConfigLifecycle.java`
26. adapt `fabric/src/main/java/mekanism/fabric/config/MekanismConfigSpec.java`
27. adapt `fabric/src/main/java/mekanism/fabric/config/FabricMekanismConfigManager.java`
28. adapt `fabric/src/main/java/mekanism/fabric/MekanismFabricClientBootstrap.java`

Admit the exact generated language resource without copying or widening the resource root:

- `src/datagen/generated/mekanism/assets/mekanism/lang/en_us.json`

Expected slices after implementation: API/common/platform **65/145/24**.

## Connected ownership graph

```text
Fabric client entrypoint
  -> FabricClientConfigLifecycle
  -> sole FabricWindowConfig / Mekanism client.toml writer
  -> common ClientWindowConfigAccess
  -> SelectedWindowData
  -> IInsertableSlot / virtual-window linkage
  -> InventoryContainerSlot <-> Basic/Input/OutputInventorySlot

BasicInventorySlot -> ISupportsWarning -> complete WarningTracker/IWarningTracker -> MekanismLang
FabricWindowConfig -> complete MekanismConfigTranslations -> TextUtils -> MekanismLang
InsertableSlot -> MainInventorySlot / HotBarSlot / ArmorSlot / OffhandSlot
InventorySlotInfo -> real IInventorySlot instances
```

This closes concrete slot and container behavior but does not claim a machine, menu network owner, ItemStorage provider registration, QIO or Crusher.

## Authorized official-source adaptations

- `SelectedWindowData`: use the narrow common owner seam instead of `MekanismConfig.client`; resolve the owner per applicable operation; preserve x→y→pin comparison/update order and exactly one changed-only asynchronous save. Replace `IQIOCraftingWindowHolder.MAX_CRAFTING_WINDOWS` with audited compile-time `(byte) 3`; replace `Mekanism.rl` with vanilla `ResourceLocation.fromNamespaceAndPath("mekanism", path)`.
- Basic/Input/Output slot private `ConstantPredicates` uses become non-escaping stateless lambdas with exact null behavior: always true, always-true bi-predicate, `automation != EXTERNAL`, and `automation == INTERNAL`. Do not admit a partial public `ConstantPredicates`, ChemicalStack or TriPredicate.
- `MekanismLang` and config translations use vanilla resource-location construction and literal stable Mekanism owner/name where the full owner is not admitted.
- Armor/Offhand replace NeoForge background extension with vanilla `getNoItemIcon()` returning the same atlas/texture pair. Armor equip validation uses the owning vanilla player inventory’s `getEquipmentSlotForItem`; this is vanilla/Fabric equipment behavior and does not claim arbitrary NeoForge item-hook parity. Independent source/bytecode review supports the substitution, but Task 039 has no real-player runtime test for Armor/Offhand.
- All other admitted official sources remain byte-identical to `4646f509`. Preserve every public constructor, field, nested type, enum value and method.

## Client configuration contract

- Sole physical path: `<Fabric config dir>/Mekanism/client.toml`.
- Own all 11 saved positions/28 scalar values under `window.*`: 22 strict signed-32-bit x/y values defaulting to `Integer.MAX_VALUE`, and six pinned booleans defaulting to `false`.
- Preserve exact paths, translations/comments, no-restart metadata, fresh reads, extraData normalization and conditional one-save behavior.
- Add a strict generic-int schema surface; integral float/decimal values must not be accepted as integer fields.
- Deliberate bounded delta: boolean strings are rejected/corrected; only real TOML/programmatic booleans are accepted.
- Preserve all valid unknown/unowned TOML sections and keys for future expansion. Correct owned scalar/table collisions without deleting unrelated sections. Parse/validate/correct/persist before publication.
- There is one process-wide normalized-path claim across manager instances. Failed registration rolls it back; unload releases it. No second `client.toml` writer may coexist.
- Common seam contains no Fabric/client imports, implicit default owner, reflection or lazy initialization. Applicable access before initialization, after close or from the wrong client thread fails explicitly; `UNSPECIFIED` remains config-independent.
- Initialize synchronously in `MekanismFabricClientBootstrap` before client networking. Production registers only physical `CLIENT_STOPPING`, not JOIN/DISCONNECT, so connection independence is established structurally and the owner closes only at physical-client shutdown. An actual same-process disconnect/reconnect is not runtime-proved and remains deferred.
- One owner executor serializes logical saves. Each changed update submits one job; retries are contained in that job. Official and candidate x→y→pin per-field updates can race a previously queued asynchronous save, transiently persisting a mixed tuple; the update-owned FIFO save converges the file, but JVM crash or terminal write failure in that small window can leave the mixed tuple. Tuple-atomic persistence is not claimed. Shutdown stops admission, drains accepted work, joins without holding the owner monitor, unloads the spec, removes the exact owner and releases the file claim.
- Same-directory temp plus `ATOMIC_MOVE` preserves the previous file on normally supporting filesystems. The pre-existing `AtomicMoveNotSupportedException` fallback uses ordinary `REPLACE_EXISTING`, so crash-atomic prior-file preservation is not claimed for that fallback; startup-config fallback behavior remains unchanged. In-memory updates are not falsely rolled back.
- Future full ClientConfig must extend or replace this same owner after a drain and load the preserved document; it must not create a second writer.

## Verification plan

Risk-based tests, not per-file test multiplication:

- ordinary `FabricWindowConfigTest`: six methods for schema/default/metadata, strict correction and unknown retention, malformed/collision and publication failure, save ordering/retries, cross-manager ownership, client lifetime/thread/shutdown.
- ordinary `WindowDomainTest`: six methods for enum/path/codec, fresh position reads and one-save order, unpinnable/unspecified/pre-init behavior, warning registration/short-circuit, warning order/dedup/energy special case, translations/TextUtils.
- isolated `fabricTransferTest` `ConcreteInventorySlotTest`: six methods for Basic, Input/Output policy, NBT restore, real InventoryContainerSlot behavior, player-slot/InventorySlotInfo behavior, and virtual-slot/window/warning behavior.
- add one integration method to existing `FabricItemStorageTest` proving Basic/Input/Output through existing holder/exposure/transaction commit and rollback.

Expected totals: ordinary **70/336**, transfer **3/15**, registry-root **1/1**, data-map Knot **1/1**, aggregate **75/353**.

Add an isolated client-window fixture and real `scripts/fabric-client-window-smoke.sh`: two sequential fresh client JVMs share one disposable config directory; the first loads seeded unknown sections, performs real bootstrap/SelectedWindowData changes and flushes; the second verifies persisted values, unknown retention and unchanged-update behavior. Prove owner availability for each physical client lifetime and closure on physical-client shutdown. Do not synthesize JOIN/DISCONNECT callbacks; actual same-process disconnect/reconnect remains deferred. Fixture code/resources must remain outside the production artifact.

Static gates must enforce exact source/class/resource families, full generated language-resource identity, no fixture/test/NeoForge/QIO/Chemical leakage, unchanged common/client entrypoint separation, and no client-owner linkage from the common bootstrap. The original implementation candidate ran focused/full local tests, slice/fixture/static gates, build, client-window smoke and paired/rejection networking regressions because it changed the client bootstrap. This corrective candidate reruns the focused/full local, fixture, build, static packaged and client-window gates, but not paired/rejection networking, packaged dedicated server or clean release because its production/bootstrap tree is byte-unchanged from `c591ad4d`. Push and deploy remain forbidden.

## Candidate verification note

The sole SOL5.6 candidate implements the full 28-production-source vertical without widening into QIO/Chemical/full ClientConfig. Local candidate gates passed: focused 6+6 ordinary and 6+5 transfer tests; slice counts **65/145/24**; full `check` totals ordinary/transfer/root/data-map/aggregate **70/336**, **3/15**, **1/1**, **1/1**, **75/353**; fixture isolation; normal Fabric build; static remapped-JAR exact family/resource/leakage gates; two-fresh-JVM client-window smoke; paired matching-network smoke; and mismatch/same-version-reject/missing-receiver regression smoke. This note records candidate evidence only and does not claim parent acceptance, dedicated packaged-server rerun, clean release, deployment, or completion of deferred owners.

## Corrective candidate verification

After reconstructing `c591ad4d` over durable main checkpoint `5685dd33`, the correction changed only this plan, `docs/FABRIC_CONFIG.md`, `docs/FABRIC_SMOKE.md`, the isolated client-window fixture, and its smoke script. `git diff --quiet c591ad4d -- src/main src/api src/datagen fabric/src/main` returned zero, so the candidate production tree and remapped JAR SHA-256 `ffb87660205c6a70612b589ad8c1315e84ed7c3e2f3b575aeb410b90f9aac7fa` remain exact.

Bounded reruns passed:

- `git diff --check` (0.00s), `bash -n scripts/fabric-client-window-smoke.sh` (0.00s), and compilation of its one embedded Python block (0.02s).
- `./gradlew -p fabric --no-daemon --console=plain test --tests 'mekanism.fabric.config.FabricWindowConfigTest' --tests 'mekanism.fabric.config.WindowDomainTest' compileClientWindowFixtureJava verifyClientWindowFixtureIsolation` (60.22s): **12/12** focused tests and fixture compile/isolation.
- `./gradlew -p fabric --no-daemon --console=plain check build` (44.69s): ordinary **70/336**, transfer **3/15**, registry-root **1/1**, data-map **1/1**, aggregate **75/353**, slices **65/145/24**.
- `FABRIC_SMOKE_STATIC_ONLY=1 scripts/fabric-packaged-smoke.sh` (8.67s): exact remapped-JAR family/resource/leakage gate passed without starting a server.
- `scripts/fabric-client-window-smoke.sh` (38.00s): two fresh client JVMs passed 11/28 schema, persistence, unknown retention, unchanged byte identity, physical-client owner availability, shutdown drain and owner close with the honest marker.

Paired/rejection networking, packaged dedicated-server smoke, clean release, push and deployment were intentionally not run.

## Parent final verification

At integrated implementation HEAD `cea268e0`, the parent independently passed focused window/transfer/fixture gates in 24.05s; full `check` in 32.62s with ordinary **70/336**, transfer **3/15**, registry-root **1/1**, data-map **1/1**, aggregate **75/353** and zero failure/error/skip; `fabric-build.sh` in 3.76s; real two-client window smoke in 38.89s; paired matching-network smoke in 69.97s; three-case configuration rejection smoke in 98.31s; and packaged dedicated-server smoke in 39.41s through `Done` without client-owner initialization. Clean implementation release passed in 40.72s with exactly one JAR SHA-256 `ffb87660205c6a70612b589ad8c1315e84ed7c3e2f3b575aeb410b90f9aac7fa` and build-info source `cea268e0`. The clean release was rerun after committing the final evaluation; its build-info source equals the current evaluation HEAD and the one-JAR SHA-256 remains identical. No push or deployment occurred.

## Review outcome and deferrals

The independent read-only high-risk review examined owner publication/replacement, cross-manager path claims, strict typing/correction, unknown retention, atomic writes, save retry/drain races, thread/lifetime handling, exact SelectedWindowData behavior, equipment substitutions, warnings and artifact isolation, and returned **ACCEPT WITH FIXES** as recorded above. The fresh focused rereview then returned **ACCEPT**, closing both required documentation findings and accepting the fixture honesty cleanup with an unchanged production tree. The corrected candidate is integrated, all parent semantic/runtime gates are complete, and the final release is aligned to the documentation HEAD.

Deferred after Task039: full ClientConfig and QIO SCC, external config watching/events/backups/comment-pruning, actual GUI windows, menu networking, ItemStorage provider/cache owner, side configuration/ejector, machine registration, recipes/CachedRecipe, Chemical and Crusher. Task039 immediately removes the concrete input/output slot and menu-slot barriers from that path.
