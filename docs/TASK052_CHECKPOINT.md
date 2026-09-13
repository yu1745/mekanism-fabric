# Task052 implementation checkpoint (continuation session 2, GLM5.3 sole implementer)

Baseline: `f2bebe7d` + cherry-picks; session commits `8b210b50` (adjudicated WIP corrections), `a050e8ab` (checkpoint), `96ea6bd9` (green slice batch).
Gate at every commit: `gradlew -p fabric assemble` + all three slice NeoForge gates green; final `test` task green (existing suites only, no new fixtures).

## Landed production (in slice, compiling, in artifact)

1. **Adjudicated corrections** (all four WIP findings resolved):
   - `mekanism.api.MekanismServerLifecycle` (api): real lifecycle-bound current-server owner. Captured on `SERVER_STARTING` (dedicated+integrated), released on post-save `SERVER_STOPPED` with same-instance check. `IRadiationManager.backCompat$getLevel` and `MekanismSavedData` (staged) both use it — no `FabricLoader.getGameInstance()` guess remains.
   - `IBlockSecurityUtils` passes caller `state`/`blockEntity` hints through `BlockApiLookup.find(world,pos,state,blockEntity,context)` (3 sites).
   - `scripts/sync-slice-gate.sh` deleted; slice counts move only with explicit audited entries (api 205→225, common 226→228 in this session).
2. **Q4 lifecycle wiring live in bootstrap**: `SERVER_STARTING` capture; `SERVER_STOPPED` → domain cleanup then release, same-server only. World-load/END_SERVER_TICK callbacks land with `FrequencyManager` admission (its lazy-load safety net preserved in the staged port).
3. **`MekanismServerState`** (common): complete-responsibility move of the excluded `Mekanism` entrypoint's server-scoped statics (`activeVibrators` set + post-save clear). `WorldUtils.isChunkVibrated` (staged) reads it; future vibrator-domain mutators call the same owner.
4. api `IFrequency`; `IColorableFrequency`; staged ports with deltas: `MekanismSavedData`, `FrequencyType` (inline lazy codec, domain logger), `FrequencyManager` (logger, vanilla ListTag), `FrequencyManagerWrapper`, `NBTUtils` (ported api `FluidStack`), `WorldUtils` (BlockApiLookup dispatch incl. hints, vanilla fluid placement/vaporize/sound semantics, documented no-Fabric-equivalent for NeoForge `BlockDropsEvent` modification hook, `MekanismServerState` owner), full config set (`MekanismConfig` + 8 files, fabric spec/manager adaptation, `FluidType.BUCKET_VOLUME`→1000, `FMLEnvironment.production`→fabric dev check, `EvaporationMultiblockData.MAX_HEIGHT`→local 18, `ModuleGravitationalModulatingUnit.BOOST_ENERGY_MULTIPLIER`→local 4 — all value-identical documented narrowings).

## Verified closure structure (compile-empirical, source-grounded)

The TileEntityMekanism closure converges on six hub clusters; each is real in-closure content, each currently blocked by its own dependency chain:

1. **`MekanismConfig`** ← `ClientConfig` ← `QIOItemViewerContainer` (930 lines: QIO crafting window/transfer helper, search parser, QIO C2S packets, `GuiComponents`, `ISlotClickHandler`, `HashedItem`, `InventoryUtils`) and `SelectedWindowData`. This also blocks `SecurityUtils`/`RadiationManager`/`TileComponentFrequency`/`PacketUtils.log`/`GearConfig` — **highest-priority hub**.
2. **`FrequencyType`** ← all four frequency domains: `TeleporterFrequency` (green-adjacent: `Frequency`→`PacketUtils.LAST_USERNAME_LENGTH`+`MekanismUtils.getLastKnownUsername` — needs a real username-cache owner; `InventoryFrequency` (entangloporter; pulls network-distribution/transmitter classes); `SecurityFrequency` (same Frequency blockers); `QIOFrequency` (pulls `QIODriveData`, `QIOItemViewerContainer`, `CommonWorldTickHandler.flushTagAndRecipeCaches` flag, `TagCache`, `WildcardMatcher`, `BiMultimap`).
3. **`WorldUtils`** full admission ← multiblock domain (`Structure`→`FormationProtocol`, `MultiblockData`→tile prefabs/capability caches/computer annotations) via dormant `isInsideFormedMultiblock`.
4. **`MekanismUtils`** ← `MekanismClient` statics, `MekanismLang`, attachments (`FrequencyAware`), `MekanismDataComponents`, tags, `UsernameCache`/`CommonHooks`/`EffectCures`/`Tags`/`BlockEvent`/`IItemHandler` platform cluster.
5. **`PacketUtils`** ← `MekanismConfig.logPackets`, filter/component interfaces, `DynamicBufferedNetwork` (transmitter domain).
6. **Bounding module (Q2, parent-confirmed interpretation)** ← `BlockDeferredRegister`/`TileEntityTypeDeferredRegister`/`BlockRegistryObject` + `BlockBounding`/`TileEntityBoundingBlock` + `IUpgradeTile`/`TileComponentUpgrade` chain — bounded, not yet started this session.

Proposed attack order for next continuation: (a) container domain core (`MekanismContainer`, sync types, slots, `SelectedWindowData`) + `PacketUtils` real-Fabric send paths; (b) QIO trio (`QIODriveData`, `QIOFrequency`, `QIOItemViewerContainer`) → unblocks `ClientConfig`→`MekanismConfig`; (c) username-cache owner + `Frequency` + `SecurityFrequency` + security quartet; (d) radiation manager; (e) bounding module; (f) components/attachments/data-components; (g) computer integration (Q3); (h) tile admission.

## Open semantic items for parent (non-blocking, decisions recorded here)

- `WorldUtils.getDrops`: NeoForge `BlockDropsEvent` (third-party drop modification) has no Fabric equivalent — computed drops returned directly; documented platform boundary, not silently dropped behavior.
- `activeVibrators`/future `flushTagAndRecipeCaches`: hosted on `MekanismServerState` as complete-responsibility moves with original lifetime (init empty, post-save clear). Alternative (port whole vibrator/multiblock domains now) rejected as disproportionate to dormant paths; mutators converge on this owner when their domains land.
- `UsernameCache`: FML cache needs a real Fabric-owned JSON-persisted equivalent (`usernamecache.json` semantics) before `Frequency`/`MekanismUtils` can compile — planned as the same responsibility-move pattern, not done yet.
