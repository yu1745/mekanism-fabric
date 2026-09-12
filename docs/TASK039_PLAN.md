# Task 039 plan — client window configuration and concrete inventory slots

Status: **candidate implemented; mandatory review pending**

## Candidate checkpoint

SOL5.6 high produced isolated commit `c591ad4df395c33874f32b0a0203c9e3b4adbfad` from exact parent `2921ee0f` after 3997.1s, 505.9k tokens and 326 tool calls (about `$21.52`; run ended at its 240-turn bound). The cumulative candidate is 32 changed files, `+1583/-113`, and reports all candidate gates passing at the planned **75 suites / 353 tests** with candidate remapped JAR SHA-256 `ffb87660205c6a70612b589ad8c1315e84ed7c3e2f3b575aeb410b90f9aac7fa`. It did not run clean release, the parent dedicated-server rerun, deployment or parent review.

This report is not acceptance. Parent source inspection before review identified three explicit questions for adversarial verification: whether a queued save can snapshot x/y/pin between individual config-value updates because the save worker does not acquire the update admission monitor; whether omitting the plan's owner-selected strict atomic-move policy weakens the promised failed-write contract; and whether manually invoking Fabric JOIN/DISCONNECT invokers with null handlers is adequate evidence for the documented real connection-lifetime claim. The fixture also did not exercise Armor/Offhand with the planned real connected player. Integration remains forbidden until an independent high-risk review resolves these and the full source/artifact boundary.

## Baseline and reason

- Candidate baseline: clean `java-port` commit `970d67c0`.
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
- Armor/Offhand replace NeoForge background extension with vanilla `getNoItemIcon()` returning the same atlas/texture pair. Armor equip validation uses the owning vanilla player inventory’s `getEquipmentSlotForItem`; document that this is vanilla/Fabric equipment behavior and does not claim arbitrary NeoForge item-hook parity.
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
- Initialize synchronously in `MekanismFabricClientBootstrap` before client networking. Owner survives integrated-server connect/disconnect and closes only at physical-client shutdown.
- One owner executor serializes logical saves. Each changed update submits one job; retries are contained in that job. Shutdown stops admission, drains accepted work, joins without holding the owner monitor, unloads the spec, removes the exact owner and releases the file claim. Failed writes preserve the previous disk file; in-memory updates are not falsely rolled back.
- Future full ClientConfig must extend or replace this same owner after a drain and load the preserved document; it must not create a second writer.

## Verification plan

Risk-based tests, not per-file test multiplication:

- ordinary `FabricWindowConfigTest`: six methods for schema/default/metadata, strict correction and unknown retention, malformed/collision and publication failure, save ordering/retries, cross-manager ownership, client lifetime/thread/shutdown.
- ordinary `WindowDomainTest`: six methods for enum/path/codec, fresh position reads and one-save order, unpinnable/unspecified/pre-init behavior, warning registration/short-circuit, warning order/dedup/energy special case, translations/TextUtils.
- isolated `fabricTransferTest` `ConcreteInventorySlotTest`: six methods for Basic, Input/Output policy, NBT restore, real InventoryContainerSlot behavior, player-slot/InventorySlotInfo behavior, and virtual-slot/window/warning behavior.
- add one integration method to existing `FabricItemStorageTest` proving Basic/Input/Output through existing holder/exposure/transaction commit and rollback.

Expected totals: ordinary **70/336**, transfer **3/15**, registry-root **1/1**, data-map Knot **1/1**, aggregate **75/353**.

Add an isolated client-window fixture and real `scripts/fabric-client-window-smoke.sh`: two sequential fresh client JVMs share one disposable config directory; the first loads seeded unknown sections, performs real bootstrap/SelectedWindowData changes and flushes; the second verifies persisted values, unknown retention and unchanged-update behavior. Also prove owner survives disconnect/reconnect and closes on physical-client shutdown. Fixture code/resources must remain outside the production artifact.

Static gates must enforce exact source/class/resource families, full generated language-resource identity, no fixture/test/NeoForge/QIO/Chemical leakage, unchanged common/client entrypoint separation, and no client-owner linkage from the common bootstrap. Candidate runs focused/full local tests, slice/fixture/static gates, build, client-window smoke and existing paired/rejection networking regressions because the client bootstrap changes. Candidate does not run clean release. Parent reruns accepted gates, packaged dedicated-server smoke and final clean release.

## Mandatory review and deferrals

Independent read-only high-risk review is mandatory before integration. It must examine owner publication/replacement, cross-manager path claims, strict typing/correction, unknown retention, atomic writes, save retry/drain races, thread/lifetime handling, exact SelectedWindowData behavior, equipment substitutions, warnings and artifact isolation.

Deferred after Task039: full ClientConfig and QIO SCC, external config watching/events/backups/comment-pruning, actual GUI windows, menu networking, ItemStorage provider/cache owner, side configuration/ejector, machine registration, recipes/CachedRecipe, Chemical and Crusher. Task039 immediately removes the concrete input/output slot and menu-slot barriers from that path.
