# Task052 implementation checkpoint (continuation, GLM5.3 sole implementer)

Baseline: clean `f2bebe7d` + cherry-picked `de73bab7`,`b9c26ac6` (as instructed), corrections committed as `8b210b50`.
The four Q1–Q4 adjudications in `TASK052_ADJUDICATION.md` are being executed; no deltas from the superseded checkpoint remain.

## Done (compiles green: `gradlew -p fabric compileJava` + all three slice NeoForge gates)

1. **WIP corrections applied** (commit `8b210b50`):
   - `scripts/sync-slice-gate.sh` deleted; slice-count gate bumped by explicit audited entry only (`MekanismServerLifecycle`, api 223→224).
   - `IRadiationManager.backCompat$getLevel` no longer guesses from `FabricLoader.getGameInstance`. New real lifecycle-bound owner `mekanism.api.MekanismServerLifecycle` (api slice): server captured by platform on `SERVER_STARTING` (no overworld access at capture), released on `SERVER_STOPPED` post-save with same-instance identity check. Shared with `MekanismSavedData` (its `ServerLifecycleHooks` call replaced by the same owner).
   - `IBlockSecurityUtils` preserves supplied `state`/`blockEntity` hints via the pinned Fabric `BlockApiLookup.find(world,pos,state,blockEntity,context)` overload (3 sites: owner x2, security, getOwnerUUID).
2. **Frequency core ports staged** (not yet in slice; waiting for their own direct deps):
   - `MekanismSavedData`, `FrequencyType` (lazy stream codec via inline delegating codec, replacing `NeoForgeStreamCodecs.lazy`; per-domain `Mekanism/Frequency` SLF4J logger replacing `Mekanism.logger`), `FrequencyManager` (same logger + vanilla `ListTag` no-capacity delta), `FrequencyManagerWrapper` (logger swap pending), `SecurityFrequency` staged next.
   - Q4 wiring plan locked: `ServerWorldEvents.LOAD` → `FrequencyManager.load()` (+ `QIOGlobalItemLookup.INSTANCE.createOrLoad()` when admitted); `ServerTickEvents.END_SERVER_TICK` → `FrequencyManager.tick(server.tickRateManager().runsNormally())`; `ServerLifecycleEvents.SERVER_STOPPED` (post-save) → domain cleanup in original order, then `MekanismServerLifecycle.releaseStoppedServer(server)` last, same-server only.

## Remaining semantic conflict — Q2 catalog composition needs one interpretation confirmed

The adjudication orders: "Original `MekanismBlocks`/`MekanismTileEntityTypes` must forward their `BOUNDING_BLOCK` fields to the exact same holder … This is an immediate source change to both original catalogs."

Verified source fact: both original catalogs import **every** machine block/tile/item class (`MekanismBlocks.java`: 1131 lines, imports 100+ machine tiles incl. all factories/multiblocks/transmitters; `MekanismTileEntityTypes.java`: 661 lines, imports every tile + references most `MekanismBlocks` holders). Admitting either catalog file into the common slice compiles the whole-mod machine closure (~800 files incl. all GUIs/renders/items) in one step — that is the entire remaining port, not a bounded step.

**Executed interpretation (needs parent confirmation, continuing meanwhile):** the *shared deferred-register objects* and the *canonical bounding holders* live in a small admitted owning module (`mekanism.common.registries.MekanismBoundingBlocks`-style, original package) that registers `BlockBounding` + `BlockItem` + `TileEntityBoundingBlock` under canonical `mekanism:` IDs through the **original** `BlockDeferredRegister`/`TileEntityTypeDeferredRegister` classes (ported, so future machine registrations use the same register types). The **original catalog files are edited now** so `MekanismBlocks.BOUNDING_BLOCK`/`MEKANISM_TILES.BOUNDING_BLOCK`(exact field: `MekanismTileEntityTypes.BOUNDING_BLOCK`) alias the same holder and `MekanismBlocks.BLOCKS`/`MekanismTileEntityTypes.TILE_ENTITY_TYPES` forward to the same shared register instances — but those two catalog files themselves are **not added to the slice yet**, because compiling them requires the machine monolith. No registry-by-name fallback, no dedupe promise, no deleted entries; the alias edit is in the catalogs' source now, and when the machine content lands the catalogs compile against the same holders/registers with zero re-registration. If the parent instead wants the whole-mod monolith admitted in this task, that is a different order of magnitude and needs explicit direction.

## Execution order (in progress)

3. Frequency domain full closure (api `IFrequency`; `Frequency`, `FrequencyManagerWrapper`, `IdentitySerializer`, `IFrequencyHandler/Item`, `IColorableFrequency`, `SecurityFrequency`, `TeleporterFrequency`, `InventoryFrequency`, `QIOFrequency`, `QIOGlobalItemLookup`, `TileComponentFrequency`) + `PacketUtils` port on the real Fabric networking owner + `MekanismUtils.getLastKnownUsername` chain (UsernameCache port + `MekanismServerLifecycle`-based profile lookup).
4. Lifecycle wiring into `MekanismFabricBootstrap` (Q4 order, cleanup-then-release).
5. Security quartet (`SecurityUtils`, `BlockSecurityUtils`, `EntitySecurityUtils`, `ItemSecurityUtils`) so the five existing service descriptors resolve to real admitted implementations; radiation manager domain.
6. Bounding owner module + original `BlockBounding`/`TileEntityBoundingBlock` gameplay + catalog alias edits (see conflict above).
7. Attachments/`ContainerType` five-domain closure + `MekanismDataComponents`/`DataComponentDeferredRegister`; tile components (config/security/upgrade/frequency/chunkloader); CC:Tweaked compileOnly + real computer binding (Q3); `MekanismContainer`+sync; attributes; then `TileEntityMekanism` admission last.
