# Task052 continuation09 parent review — NOT accepted

Reviewed fixed WIP **028acb26**, branch `pi-agent-91c16d22-94f8-46e`, against merged baseline `6a9f8339`. Both parent `cc5b0937` and prior WIP `2cb72d71` are ancestors (verified). `git diff --check` passed. Preserve all work; do not merge/admit as completed functionality.

## Review bounds and verification

Parent read full new HandlerTransitRequest, TransitRequest, TransporterItemStorage, relevant transporter initial insertion/final delivery/path snapshot code, actual QIO removal/metadata snapshots, Bin and ejector/InventoryUtils/provider diffs, and checkpoint09. This is a **bounded rejection review**, not a complete review of all 23 changed files. Remaining cache and caller code still needs review after blockers are repaired. No independent full build or tests run. Read saved build log start/end: compileJava failed with 100 capped errors; capped output cannot establish baseline equivalence. Nothing here claims changed sources compiled.

Pinned dependency proof: fabric/gradle.properties uses Fabric API `0.116.17+1.21.1`; its cached POM declares transfer-api `5.4.4+7b3d111d19`. Parent read the exact source jar Transaction and TransactionContext interfaces. A minimal javac probe against the real pinned binary jar fails with four missing symbols: `Transaction.openCurrent`, `TransactionContext.onAbort`, `onRootAbort`, `onRootCommit`. Temporary evidence `/tmp/Task052TransactionApiProbe.java`, `/tmp/task052-c09-api-probe.log`; extracted interfaces `/tmp/task052-{Transaction,TransactionContext}.java`. This probe checks API existence only, not the production classes.

## Blocking defects and required correction

### R1 — Nonexistent API and broken symbol wiring

Core/request/prediction/QIO/transit code uses the four nonexistent methods above. Real API has `openOuter`, `openNested(context)`, `addCloseCallback`, `addOuterCloseCallback`, and `SnapshotParticipant`; `commit()` closes immediately (callbacks do not wait for try-with-resources exit). Pass TransactionContext through nested prediction/routing paths instead of inventing `openCurrent`. Prefer stable owner SnapshotParticipants for rollback propagation; do not build a generic fake transaction compatibility shim.

Additional directly observed compile defects: Bin/ejector call `eject(outputter, storage, min, colorFn, tx)` but no such overload exists; `InventoryUtils.getEjectItemMap` still takes unresolved IItemHandler after removing its import; transaction-aware QIOFrequency assigns the long-returning `data.remove(amount, Action.EXECUTE, tx)` to ItemStack. Check actual changed method signatures/callers, not brace balance. Broader excluded sources may remain red, but these errors cannot be called implementation-complete.

### R2 — Slotted discovery and debit correctness

`HandlerTransitRequest.discover` calls discoverView with retainedView=null for all slotted positions; discoverView then records slot **-1**. Every discovered slotted request attempts getSlot(-1). `addAmount` also overwrites the same -1 entry while accumulating totalCount across slots. Preserve real slot index/identity or actual view per candidate. A partial valid extraction (`0 < extracted < requested`) is currently discarded altogether, leaving provisional extraction in discovery while recording no candidate; record actual positive accepted amount and enforce min on accumulated results.

`HandlerItemData.use(amount, tx)` snapshots **none** of itemMap/slotMap/views/totalCount. If one candidate debit succeeds and the next fails, source storage rolls back but the shared request remains consumed; ejector reuses this request on the next side. Moreover null handler, empty candidate set or leftover amount can return normally without exact debit, allowing destination commit without source removal. Verify full requested amount, reject invalid/unavailable source, and snapshot original request identities/order/counts before mutation. Changes must unwind correctly under nested abort and parent abort.

### R3 — Claimed physical-slot validation is absent

InventoryUtils still records `slots.indexOf(slot)` and debit treats it as the same index in any current SlottedStorage. No code connects the selected InventorySlotInfo/miner physical IInventorySlot identities to that new provider's slot ordering. Ejector comments claim validation which is not implemented. Use a real identity-checked selection facility in FabricItemStorage/real provider; do not infer physical identity from generic SlottedStorage indices. Keep the source provider's sidedness and invalidation contract.

### R4 — QIO rollback is neither idempotent nor complete

`QIOItemTypeData` restore does `totalCount += removedAmount`, registered for both own and root abort. Contrary to checkpoint09, this is not idempotent; a nested abort followed by parent abort double-restores, and callbacks from already-aborted children can overwrite later surviving changes. Dirty/needsUpdate/updatedItems were intentionally not restored, contrary to selected owner contract. Snapshot and register rollback **before** mutation (current drive/type/index registration occurs after mutation). Preserve ordered identities/indexes and existing update state with real nested owner participants, not two global callbacks. Restoring type count while skipping drive restore is invalid.

`QIODriveData` returns silently from rollback if holder slot differs, leaving mutated drive data while frequency rollback proceeds. It captures current stack but does not establish that it is still this drive's backing stack before debit. Validate actual driveMap/key/holder-slot identity before mutation; restore captured drive data and metadata on its original captured owner without overwriting a replacement, and surface an unrecoverable identity violation rather than claiming rollback. Restore missing metadata by actual component semantics. Commit-only save notifications must be associated with operations that survived all nested aborts, not just outer result. Clearing derivable wildcard caches conservatively is acceptable only if documented as such; do not claim exact cache restoration.

### R5 — Creative Bin public storage still broken

No changes to FabricItemStorage/BinInventorySlot exposure were delivered. Only Bin tick skips debit for CREATIVE. Existing FabricItemStorage expects real count decrement/increment and therefore rejects creative infinite extraction/automated void insertion. This was an explicit parent requirement, not an unanticipated test gap. Implement the actual creative policy in the exposed adapter/slot contract (including relevant component-backed Bin behavior), preserving strict finite read-back. Do not merely restate that BinInventorySlot owns it.

### R6 — Endpoint exposure/connection and new denial cache

TileEntityLogisticalTransporterBase resolver still uses NeoForge BlockCapability/Capabilities.ITEM.block signatures even though the real ICapabilityResolver uses BlockApiLookup and item provider identity is ItemStorage.SIDED. Returning a Storage from the old generic cast does not wire the actual Fabric endpoint. Finish this actual resolver and its required provider hookup, preserving null-side nonexposure and invalidation.

TransporterItemStorage.insert calls **insertUnchecked**, bypassing current side/canReceiveFrom/physical source/color permission checks. Retained endpoint aliases never revoke and supportsInsertion always returns true. RR-specific private insert also lost the checked source/connection gate present in the original shared implementation. Restore current owner/level/loaded/side validity on actual operations, including cached aliases.

Endpoint introduced a nontransactional per-tick variant/amount rejection cache and tier pull cap not selected in the parent contract. Failed/partial speculative insertion changes later insertion behavior even after abort, and valid later same-tick space can remain rejected. Remove this synthetic rejection heuristic; transaction-visible real reservations are the chosen model. Do not impose pull-tier throttling on arbitrary external insertion without original behavior evidence.

### R7 — Simulation and final-delivery ownership

The new checked/unchecked insertion helpers hardcode `updateFlowing=true` even when doEmit=false, and the legacy checked wrapper commits its own transaction unconditionally. A dry-run can leave a flowing reservation without a transit stack. Simulation must abort all path/flowing/RR mutations and never publish.

Final delivery removes flowing for a full send but retains the original nonempty stack until deletion after `delivery.commit()`; partial delivery changes stack.itemStack only **after** commit. Because real commit immediately invokes callbacks, destination can be committed while the source still owns all items; exceptions/reentrant callbacks can duplicate ownership. Snapshot and stage actual stack remainder (including empty) inside the same transaction before commit, then schedule map deletion/reroute as documented. Never structurally mutate the transit map in its fast iterator. Stable snapshots are also required for initial nextId/transit/flowing/RR, not `Math.min` counter compensations with double root callbacks.

## Decision

Reject implementation-complete/atomicity claims. Correct checkpoint09 (including `c-missing`, false idempotence, creative exposure and actual compilation status). Preserve028acb26; one GLM5.3 correction implementer from clean isolated parent must merge this latest WIP first. This is not authorization to drop QIO/Bin/remaining callers or restart from a smaller core. Ordinary focused evidence only, no new comprehensive fixture program. The exact API probe and source defects are sufficient to proceed without another independent model review.
