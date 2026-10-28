# Task052 adjudication — Curios policy, ItemAbility migration, Tool drop rule

## 1. Curios / accessories policy

Official Mekanism integrates Curios (accessory slots) in exactly two shapes relevant here: guarded lookups (`Mekanism.hooks.curios.isLoaded()` → `CuriosIntegration.findFirstCurio/getCuriosInventory`) for jetpack/free-runner/charge-distribution, and the radiation shielding item (queued separately). **Correction:** the earlier claim that Curios `9.5.1+1.21.1` on Modrinth is an official Fabric build is unsupported: its [actual release page](https://modrinth.com/mod/curios/version/9.5.1+1.21.1) identifies `curios-neoforge-9.5.1+1.21.1.jar` and lists NeoForge only. This does not prove that no compatible Fabric accessory project or other Curios build exists; the exact artifact, identity and slot semantics must be independently established before choosing an integration.

**Decision: integration not bound in this port today — documented, revisitable.** Earlier attempts reported `maven.illusivesoulworks.com` unreachable (DNS-level block with curl and Gradle); that historical result is not evidence that the purported Fabric artifact exists. The artifact and its Fabric API surface remain unverified. Per the CC:Tweaked/Q3 policy the dependency must be confirmed before use; guessing signatures is prohibited. Consequences:

- The `curios` branches compile against a WIP-owned presence seam whose `isLoaded()` stays `false` until a real compatible artifact and its provenance are verified, pinned, and `CuriosIntegration` is ported to its Fabric API with a slot-semantics audit. A Maven allowlist or a manually supplied jar alone does not establish that the previously named artifact exists or is compatible.
- Armor-path behavior is unaffected (officially the armor branches run before/independently of the curios guard).
- This is an explicit functional delta (accessory-slot support absent), recorded here; it is not a silent armor-only reduction.

## 2. ItemAbility migration (NeoForge `ItemAbility`/`ItemAbilities` → project tokens)

The project seam from the early Task052 checkpoint (`mekanism.api.ItemAbility` token + `IItemAbilityProvider` + `MekanismItemAbilities.canPerformAction`) is now load-bearing: group-D consumers (`ItemAtomicDisassembler`, `ModuleFarmingUnit`, `ModuleShearingUnit`, `EntityFlame`) previously imported the NeoForge types.

**Decision:**

- `mekanism.common.item.ItemAbilities` mirrors the NeoForge catalog subset Mekanism uses, with token names byte-identical to upstream (including the `HOE_TILL = "till"` irregularity) so interned tokens match official identifiers; wrench tokens are already project API (`MekanismItemAbilities`) and are not duplicated. Default action sets mirror upstream (`DEFAULT_AXE_ACTIONS` etc.).
- The patched `BlockState#getToolModifiedState(UseOnContext, ItemAbility, boolean)` + the `IBlockExtension.getToolModifiedState` default are replicated in `mekanism.common.util.ToolAbilityUtils` over vanilla data: `AxeItem.STRIPPABLES` and `ShovelItem.FLATTENABLES` (access-widened), `WeatheringCopper.getPrevious`, `HoneycombItem.WAX_OFF_BY_BLOCK`, and the verbatim upstream branches for `HOE_TILL` (rooted-dirt drop + above-air check), `SHEARS_TRIM` (growing-plant max-age trim — plant trimming, not armor trims), `SHOVEL_DOUSE` (campfire dowse), `FIRESTARTER_LIGHT` (campfire/candle/candle-cake light). The tool gate is `MekanismItemAbilities.canPerformAction`; the `BlockToolModificationEvent` override hook has no Fabric equivalent and is not fired; NeoForge's data-map extensions of the strip/unwax maps are out of vanilla parity (vanilla-map behavior only).
- Module-factory `canPerformAction(..., ItemAbility)` overrides keep their official decision logic against the project token type; the `ItemMekaTool`/`ItemAtomicDisassembler` provider implementations consult them (caller migration happens with the gear files' admission).

## 3. AtomicDisassembler/MekaTool Tool drop rule (upstream `AnyHolderSet` catch-all)

Upstream default component: `deniesDrops(INCORRECT_FOR_DISASSEMBLER)` + `Tool.Rule(new AnyHolderSet<>(BLOCKS.asLookup()), empty, true)` — a NeoForge holder-set *type* meaning "every block, including later-registered ones"; it has no serializable vanilla equivalent, and both naive replacements are rejected (a registry snapshot misses later-registered blocks; a pure item-level override discards user-replaced Tool components).

**Superseded decision:** the previous deny-only prototype plus item-level fallback did not preserve an explicitly supplied deny-only component: vanilla normalizes a patch equal to the prototype away, so that value was indistinguishable from the default. F66 in `TASK052_RECENT_COMMITS_AUDIT_2026-10-27.md` records the actual defect, implementation, review and verification.

**Current representation:** both original tool prototypes contain the deny rule followed by a Mekanism-specific dynamic `BlockToolAnySet` backed by the live block registry lookup. The restricted `Tool.Rule` codec extension supports the fixed official `{"type":"neoforge:any"}` representation and a matching Fabric-side stream marker; ordinary rules delegate to the original codecs. Replaced or removed components use their actual rules, without the former item-level fallback. This is not a general NeoForge holder-set compatibility layer or a claim of NeoForge wire compatibility; other mods' codec mixins, actual cross-process play and real saved worlds remain unverified as documented in F66.
