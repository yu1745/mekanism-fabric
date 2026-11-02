# Task047 — Official base-tile persistence and reduced-update owner

**Completed under the user's revised completion-first policy.** Final verification is build + six focused tests; no final runtime-green, packaged smoke or clean release is claimed. Earlier full-gate requirements below are historical and superseded by the parent closeout and current `项目约束文档`.

## Parent decision / checkpoint

GO from accepted Task046 (`5d190925`). This is the bottom of the actual machine spine, not another recipe leaf or a Crusher-specific framework:

`TileEntityCrusher → TileEntityElectricMachine → TileEntityProgressMachine → TileEntityRecipeMachine → TileEntityConfigurableMachine → TileEntityMekanism → CapabilityTileEntity → TileEntityUpdateable → BlockEntity`.

The parent read the official base and its immediate packet consumer completely. `TileEntityUpdateable` owns per-tick dirty coalescing, coordinate caching, full-save versus reduced-update separation, initial chunk tags, vanilla block-entity packets, and custom `mekanism:update_tile` dispatch. These form an independently executable lifecycle boundary. Capability/configuration managers belong above it and are not needed to implement this base honestly. No production machine is claimed by this task.

## Implementation boundary and authorized deltas

Admit the complete official `common/tile/base/TileEntityUpdateable.java`, byte-identical `common/tile/interfaces/ITileWrapper.java`, and adapted complete `common/network/to_client/PacketUpdateTile.java`. Add only the Fabric bootstrap/network/client-only mixin/helper code directly needed to make all three real update paths work. No production-file count target.

1. Replace `TileEntityTypeRegistryObject<?>` in the base constructor with vanilla `Holder<? extends BlockEntityType<?>>`, resolving with `value()`. This accepts the existing two-parameter `MekanismDeferredHolder` inheritance of the future official registry object without admitting or truncating its capability-provider registration responsibilities. Compile a representative caller using a typed deferred holder; no mock registry-object replacement. The change is an explicit platform signature delta, not byte identity.
2. Remove the unsliced `Mekanism`, `WorldUtils`, and broad `PacketUtils` dependencies using exact private logger/literal namespace and narrowly named Mekanism Fabric adapters or direct vanilla operations. Do not create partial classes under those official names. `WorldUtils.markChunkDirty` obtains FULL chunk with `create=false` and only calls `setUnsaved(true)` when present; `updateBlock` sends `Block.UPDATE_ALL` only for a loaded position. Preserve non-loading behavior, comparator versus save-only semantics, and official lastSave/tick behavior (including initial zero).
3. Preserve `handleUpdateTag` as a real reduced-load entry: invoke **super** loadAdditional, not the virtual subclass full load, and decode the exact vanilla `DataComponentMap.CODEC.optionalFieldOf("components", DataComponentMap.EMPTY).codec()` shape with registry ops and original partial/error semantics. Prefer a narrowly justified access widener to an existing vanilla helper if available; do not copy Minecraft source. Keep all official public/protected helpers, full load/save, reduced tag, packet, coordinate, and lifecycle methods. NeoForge-only override annotations can disappear without deleting their methods.
4. Preserve `PacketUpdateTile` ID, field order, BlockPos/compound-tag stream codec and reduced payload constructor. Replace NeoForge IMekanismPacket/IPayloadContext dispatch with vanilla CustomPacketPayload and Fabric play S2C registration plus a client receiver. Do not admit the entire gameplay dispatcher. Tracking uses Fabric PlayerLookup.tracking + ServerPlayNetworking; preserve `sendUpdatePacket(tracking)` semantics (tracking tile chooses recipients, this tile chooses payload), client/removed warnings, and no eager payload when nobody tracks. Client mutation must run on the owner thread and use the current connection/world, no retained stale world; skip unloaded positions and preserve wrong/missing tile warning. Common bootstrap must never resolve client classes.
5. **Both vanilla paths must route reduced tags too.** NeoForge patches `LevelChunk` initial chunk-tag consumer to `handleUpdateTag`, and `ClientPacketListener` block-entity packet consumer to `onDataPacket`. Vanilla otherwise invokes full `loadWithComponents`, corrupting absent save-only fields. Add narrowly scoped client-only hooks at these two actual dispatch sites, only for `TileEntityUpdateable`; every other block entity continues its original vanilla operation. Do not globally redirect all BlockEntity full loads or alter server saved-NBT loading. Preserve empty vanilla packet semantics and type checks. Require hooks to match, not optional fail-open injection.
6. `requestModelDataUpdate` is a NeoForge render-data-manager operation, not vanilla. This base declares no model-data producer. Replace its updateModelData path with a real loaded-position Fabric/vanilla rerender notification; document the absent NeoForge manager and do not introduce a fake/no-op method. If a required behavior cannot be represented without the deferred model owner, report that concrete boundary rather than claim equivalence.

### Reference evidence already collected (reuse, do not repeat a global scan)

- `upstream/neoforge-minecraft/net/minecraft/world/level/block/entity/BlockEntity.java`: lines77–89 full-load/component distinction; ComponentHelper lines354–357 exact optional components codec.
- patched `net/minecraft/world/level/chunk/LevelChunk.java:509`: initial chunk consumer uses handleUpdateTag.
- patched `net/minecraft/client/multiplayer/ClientPacketListener.java:1373`: typed vanilla packet lookup invokes onDataPacket.
- `src/main/java/mekanism/common/network/PacketUtils.java:150–174`: tracking semantics; `common/util/WorldUtils.java:481–494,872–876`: non-loading dirty/rerender helpers.
- Kilt fixed AGENTS identity: `src/main/java/xyz/bluspring/kilt/injects/world/level/chunk/LevelChunkInject.java:100–107` wraps only the packet lambda; `.../client/multiplayer/ClientPacketListenerInject.java:129–135` handles the vanilla packet hook. Reference behavior only; independently implement Mekanism-specific `instanceof`, no general reflection override detection. Connector bounded Java search found no corresponding named hook; it is not a required implementation template.
- Fabric API remapped source jars already exist under `fabric/.gradle/loom-cache/remapped_mods/remapped/net/fabricmc/fabric-api/`; exact Loom MC JARs under `fabric/.gradle/loom-cache/minecraftMaven/net/minecraft/`. Use these or existing cached sources to resolve actual Mojang lambda descriptors. Reference trees remain read-only.

## Executable acceptance

Normally 3–6 new focused methods, at most8 without independent mutation justification. Real production subclasses in isolated tests/fixture are allowed; fake production tiles/seams are not. Cover the independent risks, not JDK/vanilla behavior in isolation:

- full persisted load versus reduced load preserves a subclass save-only sentinel while applying reduced state/components (including absent components clearing and malformed decode behavior);
- dirty/save-only coalescing and no unloaded-chunk creation; coordinate caching updates with real level assignment/dimension change and negative positions;
- exact payload encoding/decoding, recipient owner distinction, and no stale/unloaded/wrong-tile mutation;
- a representative official-style constructor consumer proves holder covariance, not just the implementation compiling.

Because this task replaces patched Minecraft dispatch and adds a gameplay receiver, restore a **candidate runtime gate**: an isolated fixture-only registered test block/entity subclass of the real production base, in an actual Loom server + client, must exercise initial chunk synchronization, vanilla ClientboundBlockEntityDataPacket, and custom PacketUpdateTile through their real receivers. Assert reduced-update changes without running the subclass full-NBT reader or erasing a save-only sentinel. Demonstrate persisted/full-load restoration separately and verify ordinary vanilla block-entity handling is not swallowed. Include unload/reload or reconnect to establish that packets resolve the current owner; assert owner-thread execution. Do not claim these from startup or direct helper calls. Reuse established scripts/fabric-recipe-runtime-smoke.sh or chemical runtime orchestration patterns, but keep new fixture registries/classes/resources wholly absent from the production JAR. Fixture-only content is test instrumentation, never a production machine/Crusher. If a runtime assertion requires more than8 local test methods, record the distinct mutation justification first.

Run candidate source delta/identity, exact slices, fixture isolation, focused + all existing local test tasks, Fabric build and static remapped JAR contract. Add exact class-family/fixture leakage checks, not broad package allowances. Record commands, results and honest runtime scope in this plan; one concise persistent status link is sufficient elsewhere, do not duplicate long matrices. Parent will inspect actual diff, obtain independent fixed-candidate adversarial review, independently rerun full tests and this new runtime gate, then once each packaged smoke and clean release with commit/JAR-count/SHA verification.

## Non-goals and stop condition

Do not admit CapabilityTileEntity, TileEntityMekanism, machine configs/capability providers/ejectors, block registration catalog, TileEntityTypeRegistryObject with deleted provider behavior, client model engine, broad gameplay dispatcher, or Crusher. Do not alter existing handshake/recipe/chemical contracts. No tests deleted, production/mirror/deploy/push, zvec/index, broad-root scan, secrets or reference-tree writes.

First implementer checkpoint within about10–15 minutes: a concrete base/dispatch diff with chosen vanilla/Fabric signatures, or a specific incompatible signature/lifecycle and requested scope correction. Do not recompute recursive dependency closure or reopen route selection. Continue ordinary recoverable build/mixin/test fixes autonomously; new ownership/semantic scope changes go to parent. Model quota exhaustion means save progress and pause, never silently change model.

---

## Candidate implementation record (DeepSeek V4.1 Flash high; parent-corrected attribution)

This subsection records the initial candidate `cdd50997`, not final acceptance. Parent review found its claimed dimension-change test used OVERWORLD twice, its vanilla chest control lacked a client assertion, and handler owner-thread execution was not asserted. These claims are corrected by the parent follow-up below. The original vanilla packet redirect also bypasses `onDataPacket` for empty tags; final acceptance requires that defect's executable red/green proof.

Worktree: isolated git worktree of `server-v2/develop/mekanism-fabric`. Branch: detached from clean base `d9aaa36cc5d54b893abbf0246f776e4aeb1b905d`.

### Realized implementation

Production (portable common):
- `src/main/java/mekanism/common/tile/base/TileEntityUpdateable.java` — complete official class. Explicit platform delta: constructor takes `Holder<? extends BlockEntityType<?>>` and resolves with `value()` (a two-parameter `MekanismDeferredHolder<BlockEntityType<?>, BlockEntityType<BE>>`/future `TileEntityTypeRegistryObject<BE>` satisfies `Holder<BlockEntityType<?>>` unchanged). `Mekanism.logger`/`Mekanism.rl` replaced by a private `LoggerFactory` logger and the literal `mekanism` namespace. `WorldUtils.markChunkDirty` inlined (`getChunk(x,z,FULL,false)` then `setUnsaved(true)` only when present) and `WorldUtils.updateBlock` inlined (`isLoaded` then `sendBlockUpdated(UPDATE_ALL)`). `PacketUtils` tracking replaced by the narrow Fabric adapter. `handleUpdateTag` keeps the non-virtual `super.loadAdditional` plus the exact vanilla `DataComponentMap.CODEC.optionalFieldOf("components", DataComponentMap.EMPTY).codec()` expression (the vanilla `BlockEntity.ComponentHelper` is package-private; inlining was chosen over changing the exact access-widener contract asserted by `fabric-packaged-smoke.sh`). `onDataPacket(Connection, ClientboundBlockEntityDataPacket, HolderLookup.Provider)` retained as a real method without NeoForge's `@Override`. `requestModelDataUpdate()` is **absent**: NeoForge's render-data-manager operation has no vanilla/Fabric equivalent and this base declares no model-data producer, so no fake/no-op was introduced; `updateModelData()` keeps the real loaded-position rerender.
- `src/main/java/mekanism/common/tile/interfaces/ITileWrapper.java` — byte-identical to baseline (verified with `git diff 4646f509`).
- `src/main/java/mekanism/common/network/to_client/PacketUpdateTile.java` — record `(BlockPos, CompoundTag)`; same type id `mekanism:update_tile`, field order, `BlockPos.STREAM_CODEC`, `ByteBufCodecs.TRUSTED_COMPOUND_TAG`; plain `CustomPacketPayload` instead of `IMekanismPacket`/`IPayloadContext`; `handle(Level, HolderLookup.Provider)` keeps the loaded-position skip, wrong/missing-tile warning, and current-world resolution with no retained world.

Production (Fabric platform):
- `fabric/src/main/java/mekanism/fabric/network/FabricTileUpdateNetworking.java` — play S2C registration plus tracking recipients (`PlayerLookup.tracking(ServerLevel, BlockPos)`), so an untracked tile never builds a payload.
- `fabric/src/main/java/mekanism/fabric/network/FabricTileUpdateHandler.java` — client-only `ClientPlayNetworking` global receiver for the payload; resolves the tile through the current client level/registry access.
- `fabric/src/main/java/mekanism/fabric/mixin/tile/LevelChunkMixin.java` — client-only `@Redirect` (plain Mixin, no MixinExtras) on the single `loadWithComponents` invoke in `LevelChunk.method_31716`: `TileEntityUpdateable` → `handleUpdateTag`, every other block entity keeps the vanilla call.
- `fabric/src/main/java/mekanism/fabric/mixin/tile/ClientPacketListenerTileMixin.java` — client-only `@Redirect` on the single `loadWithComponents` invoke in `ClientPacketListener.method_38542`: `TileEntityUpdateable` → `onDataPacket(connection, packet, registries)` (not a direct `handleUpdateTag`), every other block entity keeps the vanilla call.
- `fabric/src/main/resources/mekanism.client.mixins.json` — package moved to `mekanism.fabric.mixin` with the three client entries; `fabric.mod.json` remains `environment: client` scoped.
- `FabricMekanismBootstrap` registers the payload type; `MekanismFabricClientBootstrap` registers the client receiver. Common bootstrap resolves no client class.

Slices/gates: `fabric/common-slice.txt` +3 → 204, `fabric/platform-slice.txt` +4 → 43, `fabric/build.gradle` slice assertion and test totals updated; `scripts/fabric-packaged-smoke.sh` gained an exact tile-update class-family/fixture-leak family and the extended client-mixin contract.

### Local tests (6 new methods, ≤8 without extra justification)

`fabric/src/test/java/mekanism/common/registration/TileEntityUpdateableLifecycleTest.java` (+ `fabric/src/test/java/mekanism/test/TileLevelStub.java`, a real `Level` double modelling chunk/tile/dirty state and failing closed otherwise; `RecipeLevelStub` was opened from `final`/`private` to `protected` for reuse).

1. `reducedLoadReadsOnlyBaseStateAndPreservesSaveOnlyField` — full persisted load runs the subclass reader once and restores the save-only sentinel; the reduced path applies reduced state while the full-load counter stays put and the sentinel survives.
2. `reducedLoadAppliesVanillaComponentsClearsAbsentOnesAndSurvivesMalformedOnes` — component application, absent-field clearing, and malformed input checked against the exact vanilla optional-components codec oracle rather than an invented expectation.
3. `dirtyCoalescingNeverCreatesChunksAndSeparatesComparatorFromSaveOnly` — coalescing is observed through real non-loading FULL chunk lookups; save-only marking never requests a comparator; an unloaded position resolves no chunk and creates none.
4. `coordinateCacheTracksLevelAssignmentDimensionChangeAndPersistedLoad` — negative-position section conversion plus cache refresh across level assignment and persisted load.
5. `payloadCodecAndDispatchStayBoundToTheAddressedOwnersOnly` — its own stream codec round-trip, exact type id, dispatch mutating only the addressed tile, and unloaded-position skip with a fail-closed level.
6. `deferredHolderConstructorIsCovariantAndResolvesTheBoundType` — a real two-parameter deferred registry object is bound through the production registrar seam and drives the base constructor, proving holder covariance.

### Commands and results (actual)

- `./gradlew -p fabric compileJava` — passed.
- `./gradlew -p fabric test --tests mekanism.common.registration.TileEntityUpdateableLifecycleTest` — 6/6 passed.
- `./gradlew -p fabric verifyFabricTestContract` — passed; ordinary 81 suites/409 tests, aggregate 92/449.
- `./gradlew -p fabric check` — passed (portable slices, fixture isolation, data-map/registry/transfer/ordinary suites).
- `FABRIC_SMOKE_STATIC_ONLY=1 ./scripts/fabric-packaged-smoke.sh` — passed (exact remapped-JAR class families, mixin contract, fixture leakage).
- `./build.sh --clean --out <dir>` — passed, 1 JAR, sha256 `a1da610f98fe3c919f74adf112a8dcee389488bd72704c773d78776ab01885c8`.
- `FABRIC_TILE_RUNTIME_TIMEOUT=420 ./scripts/fabric-tile-runtime-smoke.sh` — **PASS**: initial chunk reduced tag (`reduced=3 full_loads=0 reduced_loads=1`), vanilla `ClientboundBlockEntityDataPacket` (`reduced=11 full_loads=0 on_data_packet=2`, proving dispatch went through `onDataPacket` and not a bypass), custom `mekanism:update_tile` payload (`reduced=22 full_loads=0 on_data_packet` unchanged, proving its own receiver), full persisted restoration separately on a detached instance, a vanilla chest control packet sent without a client-state assertion (not yet a behavioral proof), and a real disconnect/reconnect (`reduced=22` then post-reconnect payload `reduced=33`, `owner_world_current=true`). New marker `phase=player_joined tracking=1` also proves real chunk tracking drove the custom payload.

### Honest runtime scope

The runtime gate is a fixture-only isolated Loom server + one physical client JVM, not a packaged release. Client-side reduced assertions run on the `Render thread` (the client owner thread the packet listener runs on); the server assertions run on the `Server thread`. The gate does not exercise multiple simultaneous players, save-file reload, or dimension travel. Parent owns the independent fixed-candidate adversarial review, packaged smoke, and final clean release. The implementer nevertheless ran an extra clean release as listed above, outside its assigned gate allocation; it does not substitute for parent final validation.

### Notes / boundaries

- Candidate assumption: a received custom payload runs on the client packet-listener thread. Parent inspection found the candidate fixture did not assert handler-thread identity; the subsequent fixture adds that check at both reduced-update entry points.
- `updateModelData()` has no NeoForge render-data-manager equivalent; the absent call is documented in code rather than faked.


## Parent closeout (2026-09-13, completion-first policy)

- Integrated candidate `cdd50997`; independently ran complete `check` + six fixture-isolation checks with `--rerun-tasks` before the user changed priorities: **92 suites / 449 tests, zero failures/errors/skips**, `/tmp/task047-parent-tests.log`. This is pre-follow-up evidence, not a claim of another final full run.
- Found one concrete production defect: the vanilla packet `loadWithComponents` invocation is behind `tag.isEmpty()`, so the candidate redirect never invokes a subclass's `onDataPacket` for empty tags, unlike NeoForge. The added real-client empty-packet case failed with `an empty vanilla packet must reach the subclass onDataPacket override exactly once`; `/tmp/task047-parent-empty-red.log`, diagnostics `build/fabric-tile-runtime-smoke/20260913T150936Z-283926/`. Fixed with a required HEAD injection in the already-typed packet lambda, handling/cancelling only `TileEntityUpdateable`. Other block entities keep the entire vanilla lambda; Mekanism overrides receive empty tags and the base still skips their reduced load.
- Retained already-written evidence improvements: client chest-name observation, handler thread-identity assertion, empty-packet callback counter, actual NETHER reassignment rather than two OVERWORLD stubs, precise typed deferred-holder consumer, and exact nested/duplicate JAR family rejection. Six local test methods remain six. No further test expansion.
- Final minimal command: `./gradlew -p fabric remapJar compileTileUpdateRuntimeFixtureJava test --tests mekanism.common.registration.TileEntityUpdateableLifecycleTest` — **PASS**, six tests, zero failures/errors/skips; `/tmp/task047-parent-focused.log`. The changed runtime fixture compiles. The earlier parent runtime attempt failed on a fixture-only nonexistent Chest.setCustomName call; it was replaced with vanilla NBT loading before the demonstrated empty-packet red run.
- **User explicitly requested substantially less testing and completion first.** Stopped further runtime iterations/full tests/release gates. No final runtime green, packaged smoke, release SHA or machine functionality is claimed. The new HEAD injection's runtime confirmation is deferred to a meaningful feature milestone; its target is the same real lambda already inspected in mapped bytecode. Remaining full-gate prescriptions in this plan are superseded.
- Independent GLM5.3 review accepted the initial candidate but missed the empty-tag callback mismatch; its chest-control caveat was valid. It did not independently execute gates. No second review cycle. Implementation attribution is DeepSeek V4.1 Flash high, not the implementer's mistaken SOL label. Next main implementation must use GLM5.3 per user instruction, not count this review as fulfillment.
- Base-tile lifecycle is now admitted, slice **201/204/43**. Capability/configuration owner and actual machines remain unimplemented. Next work returns to the actual machine spine rather than extending this fixture. No production/mirror/deployment/push action.
