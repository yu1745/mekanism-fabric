# Task052 continuation4 implementation checkpoint — transactional Fabric fluid send boundary

Session start: parent `0d1cb08e` merged preserved WIP `4d56423b` (merge `9d7f0c3f`) before any implementation; nothing was discarded or reimplemented. Slice counts now **api=232 / common=395 / platform=49** (build.gradle gate updated accordingly). Compile state remains **RED by design** on the known machine-owner tail (javac 100-error cap; no error in any file touched by this session beyond the four pre-existing `MekanismUtils` missing-tail symbols at its import lines).

## Approved fluid Storage boundary — implemented

All five REVIEW_04 adjudication points from `TASK052_CONTINUATION_04.md` are implemented as real code; no reverse NeoForge handler facade was created anywhere.

1. **`FluidHandlerTarget` (rewritten, common slice)** — now `Target<Storage<FluidVariant>, FluidStack>` over real Fabric storages. The enclosing `TransactionContext` is supplied by the sending owner through the constructor. `simulate` probes in always-aborted nested transactions (shared destination capacity observed through the enclosing transaction); `acceptAmount` inserts in a nested transaction and commits it into the still-open enclosing send transaction only on an exact whole-millibucket response. 81 units = 1mB via `FabricFluidConversions`; responses validated in `[0, request]`; fractional acceptance aborts the probe and retries at its whole-mB floor in a fresh nested transaction (bounded retry, stops when the floor stops shrinking; otherwise 0). Integer-mB fairness (`EmitUtils.sendToAcceptors` + `SplitInfo`) unchanged.
2. **`FluidSendTransaction` (new permanent send owner, common slice)** — one outer transaction spanning distribution AND source debit. Source tank snapshotted with a Mekanism-specific single-tank `SnapshotParticipant` (`setStackUnchecked` rollback) BEFORE any target execution; after distribution the source is verified unchanged (alias/self-target reentrancy), the exact INTERNAL extraction runs inside the transaction, its fluid/components/amount and resulting contents are verified, and only then does `outer.commit()` run. Any mismatch/exception aborts destinations and source together. Javadoc explicitly documents the non-rollbackable immediate listener/dirty effects limitation (accepted adapter boundary). The no-source overload is an explicit caller-owned-stack distribution with no snapshot/debit and a documented caller-debit contract.
3. **`InventoryFrequency.SendingFluidHandlerTarget` (migrated)** — no longer extends the old NeoForge target; collects `Storage<FluidVariant>` handlers and `run()` delegates to `FluidSendTransaction.send(handlers, storedFluid, toSend.getAmount())`. The old post-distribution debit is gone. File remains unadmitted (QE/tile tail); also fixed: neoforge `FluidStack` import → `mekanism.api.fluid.FluidStack` (the file was mid-migration and inconsistent).
4. **`FluidNetwork` (migrated, remains unadmitted)** — acceptor type changed to `Storage<FluidVariant>`; `tickEmit` collects acceptors and delegates to the sender-owned transaction; `onUpdate` no longer performs the old second `shrinkStack` debit (the debit now lives inside `FluidSendTransaction.send`).
5. **`MechanicalPipe` (migrated, remains unadmitted)** — acceptor cache/generic types changed to `Storage<FluidVariant>`; `pullFromAcceptors` rewritten transactionally: probe/execute extraction with the same bounded whole-mB floor retry, buffer simulation before commit, single abort path (no dupe window). *This is the external-extract side of the same real-Storage boundary; flagged for parent diff review since pull was not separately pre-adjudicated.*
6. **`FluidUtils.emit` (migrated, remains unadmitted)** — tank overloads delegate to `FluidSendTransaction.send`; the public stack overload keeps its caller-owned-stack contract with an explicit caller-debit javadoc; old `canFill` removed. The rest of `FluidUtils` (item interactions via `IFluidHandlerItem`, `FMLEnvironment`, `IClientFluidTypeExtensions`) still awaits its own item-external boundary batch.

## REVIEW_04's five findings — fixed

1. `MekanismUsernameLifecycle.recordLogin` now has the pinned real JOIN signature `(handler, sender, server)`; the player comes from `handler.player`.
2. `MekanismUtils.getLastKnownUsername` captures the lifecycle server once and gates the profile-cache fallback on `server.isSameThread()` (logical-server + server-thread affinity); username-cache reads stay available on the client; no physical-environment guessing.
3. `Object2ObjectOpenHashMap` import restored in `MekanismClient`.
4. `veinMineArea` preserves vanilla destruction semantics (`playerWillDestroy` → `onDestroyedByPlayer` → `block.destroy` → `playerDestroy`) and now dispatches the successful `PlayerBlockBreakEvents.AFTER` with the original pre-break state and the same block-entity instance used for BEFORE/CANCELED.
5. `RecipeMatcher` provenance comments no longer assert a verification date or an all-branches historical negative; verifiable commit/path/license attribution and distribution obligations retained.

## Verification status (accurate, no working-machine claim)

- `./gradlew -p fabric compileJava`: FAILS on the pre-existing machine-owner tail (100-error cap), unchanged error-file set versus the pre-session baseline — no new failing file introduced, and neither new distribution class appears in the error list. Both named containers (`MekanismContainer`, `QIOItemViewerContainer`) remain compile targets and remain red through their tail dependencies.
- Focused tests added (staged, NOT run — the fabricTransferTest source set needs main output which is red): `fabric/src/fabricTransferTest/java/mekanism/fabric/fluid/FabricFluidSendTransactionTest.java` — fractional-only acceptor abort + floor retry, shared-destination capacity with exact source debit, and reentrant source-mutation rollback (3 tests, no new infrastructure).
- No runtime/full/packaged/clean verification performed. No production/mirror/deploy/push.

## Item transit boundary — untouched, pending precise blocker

`TileComponentEjector.outputItems` / `TransitRequest.eject` / `TransitResponse.useAll` were not modified this session. The precise source-consumption/transporter-ownership conflict for the parent, when that path is next: the ejector routes items through transporter pathfinding (`TransitRequest`/`TransporterPathfinder`) and consumes the response itself, so an external `Storage<ItemVariant>` boundary there must decide who owns the sided item handler snapshot vs. the response consumption — that is an item-transit adjudication, not derivable from the fluid decision.

## Remaining tail (unchanged ownership route)

`TileEntityMekanism` components, `FrequencyType`→`InventoryFrequency`/QE tile, transporter tail, attachments catalog, container-type registry bridge, `MekanismAPI`/`Capabilities` token hub, computer integration tail — all still the concrete red compile surface; no target withdrawal, no green-leaf detour.
