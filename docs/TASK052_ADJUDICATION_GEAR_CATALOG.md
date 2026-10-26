# Task052 adjudication — Curios policy, ItemAbility migration, Tool drop rule

## 1. Curios / accessories policy

Official Mekanism integrates Curios (accessory slots) in exactly two shapes relevant here: guarded lookups (`Mekanism.hooks.curios.isLoaded()` → `CuriosIntegration.findFirstCurio/getCuriosInventory`) for jetpack/free-runner/charge-distribution, and the radiation shielding item (queued separately). The official Curios Fabric port exists — Curios `9.5.1+1.21.1` on Modrinth with documented coordinates `top.theillusivec4.curios:curios-fabric` on `maven.illusivesoulworks.com` — the same-mod cross-loader case, so unlike OC2 this is NOT a platform-absence boundary.

**Decision: integration not bound in this port today — environment-forced, documented, revisitable.** `maven.illusivesoulworks.com` is unreachable from this build environment (DNS-level block, verified with both curl and Gradle resolution: "Name or service not known"), so the artifact cannot be pinned here, and the Fabric API surface is therefore unaudited. Per the CC:Tweaked/Q3 policy the dependency must be confirmed before use; guessing signatures is prohibited. Consequences:

- The `curios` branches compile against a WIP-owned presence seam whose `isLoaded()` stays `false` until the artifact can be pinned (maven allowlist for `maven.illusivesoulworks.com`, or a manually provided `curios-fabric-9.5.1+1.21.1` artifact) and `CuriosIntegration` is ported to the Fabric API with a slot-semantics audit. Flipping it is the recorded follow-up, and the user can authorize the maven allowlist at any time.
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

**Decision (precise native representation):** vanilla routes drop correctness through the virtual `Item.isCorrectToolForDrops(ItemStack, BlockState)` → component rules. The port keeps the component with only the serializable deny rule (`Tool.Rule.deniesDrops(incorrect-tag)`) and overrides the virtual item method to honor any user-supplied component rules first, then apply the official default: every block not explicitly denied drops correctly. That reproduces the upstream net behavior (deny-tag denies, all else drops) natively — no snapshot, no late-registration hole, and replaced components still contribute their rules before the fallback. Delta: a component that is *replaced in order to remove the deny behavior* still loses against the built-in fallback (documented; upstream semantics for replaced components are otherwise unobservable).
