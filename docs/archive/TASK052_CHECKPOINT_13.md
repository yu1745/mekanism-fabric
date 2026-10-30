# Task052 checkpoint 13 — SnapshotParticipant owner correction (GLM5.3 single implementer continuation)

Merged prior implementer checkpoint `884eadfe` onto parent baseline `e086f256` (merge `e5a6f66e`, full ancestry
preserved). This checkpoint implements the REVIEW12 mandated direction: **removed the generic `TxOps` callback
compensation entirely** (file deleted) and replaced every rollback/notification seam with **stable real-owner
`SnapshotParticipant`s** using the real pinned Fabric transfer API nesting-depth snapshot promotion. Commits (this
continuation): `24b1e53a`, `439e6af7`, `6a8eac31`, `605092a4`, `e3696f67`, `0c9e8fd4`.

## Owner participants (genuine nested propagation, no ambient-current fallback)

- **TransporterStack**: stable `StackStateParticipant` field covering path type, deep path list copy, idle dir,
  initiation flag, current `itemStack` (deep copy) and flowing reservation membership. `updateSnapshots(tx, level)`
  called before mutation in `recalculatePath`/`recalculateRRPath` and final delivery remainder staging.
- **RR target**: belongs to the ejector owner. New `IAdvancedTransportEjector.updateRoundRobinSnapshot(tx)`;
  `TileEntityLogisticalSorter` and `TileEntityQIOExporter` each hold a stable `SnapshotParticipant<SidedBlockPos>`
  over `rrTarget`. Restores exactly through nested aborts including the one-destination case.
- **LogisticalTransporterBase transit state**: stable `TransitReservationParticipant` (transit map copy, nextId,
  needsSync copy, pendingCommitSync) — commit-only initial packet/save via `onFinalCommit`, only for reservations
  that survived every nesting level. Per-call `undoReservation`/double-root-callback compensation removed.
- **HandlerTransitRequest plan**: stable `PlanParticipant` — deep snapshot of ordered itemMap membership,
  per-type totalCount and every ordered candidate (real slot index / retained real view with count) taken before the
  first debit mutation at each nesting depth; source storage and request plan unwind together (REVIEW11 R2 shared-
  request-consumption defect).
- **QIO trio (REVIEW11 R4)**: `QIODriveData` stable participant (itemMap copy, itemCount, captured actual drive
  stack identity + DRIVE_METADATA; `onFinalCommit = key.dataUpdate()`, identity violation at restore surfaces loudly
  instead of silent skip); `QIOItemTypeData` stable participant (count, shared frequency totalCount, containingDrives;
  LIFO earliest-wins for two types in one tx; needsUpdate/updatedItems never erased); `QIOFrequency` stable
  `FrequencyRemoveParticipant` (ordered itemDataMap + tag/mod/fuzzy memberships restored exactly; wildcard caches left
  cleared, documented as conservative-derivable). No remove-then-add compensation. Fixed the long→ItemStack defect in
  `removeByType` tx branch.

## Other corrections

- **R1 leftovers**: `Transaction.openCurrent()` in HandlerTransitRequest/TransporterUtils replaced with
  `openOuter()` abort-only locals; `tx.openNested()` → `Transaction.openNested(tx)`; `TxOps.simulation()` removed
  everywhere. No `getCurrentUnsafe` ambient fallback remains in the transfer seam.
- **R2 discovery**: slotted discovery now records the **real slot index** (previous code recorded -1 for all slotted
  positions and `addAmount` overwrote that entry). Candidates are one ordered list of real positions (slot index or
  retained real view); debit extracts through the exact same positions, consumes in place.
- **R3 identity**: new `FabricItemStorage.viewOf(IInventorySlot)` identity-checked selection facility;
  `HandlerTransitRequest.addItemFromSelectedSlot` connects ejector/miner/bin selected physical slots to their real
  exposed position in the actual sided provider (loud warn+skip if the provider doesn't expose the identity; the
  miner keeps its own bounding-position provider — no arbitrary Finder fallback added).
- **R5 creative**: new api `ICreativeInventorySlot.isCreative()` (added to `fabric/api-slice.txt`, guard 232→233);
  `BinInventorySlot` and `ComponentBackedBinInventorySlot` implement it; `FabricItemStorage` applies the creative
  policy per exact operation and only while actually creative (expected post-op state of the creative slot is
  unchanged → non-depleting extraction / voiding external insertion), finite read-back stays strict otherwise.
- **R6 endpoint**: `TileEntityLogisticalTransporterBase.TransporterCapabilityResolver` rewritten over the real
  `ItemStorage.SIDED` `BlockApiLookup` (was NeoForge BlockCapability); `sideChanged` invalidates `ItemStorage.SIDED`;
  `TransporterItemStorage` rewritten: checked insert path (current side/canReceiveFrom/physical source re-validated
  per operation), **denial cache and tier pull cap removed**.
- **R7 delivery**: final delivery stages the exact stack remainder (including empty) and the flowing removal inside
  the delivery transaction via the stack participant **before** commit; commit-only notification through the
  participant. Legacy wrapper still exists for non-tx callers.
- Fixed REVIEW11 concrete compile defects: missing `eject(outputter, storage, min, colorFn, tx)` overload added;
  `InventoryUtils.getEjectItemMap` no longer takes IItemHandler / `slots.indexOf`; `Capabilities.ITEM` usage in
  `isItemHandler` replaced with `ItemStorage.SIDED.find`.

## Verification status (explicit: source review vs compile/test)

- `./gradlew -p fabric compileJava --console=plain` → **BUILD FAILED (pre-existing RED partial-slice surface)**,
  log `/tmp/t052c_compile.log`: 202 errors, all "symbol not found" for not-yet-admitted excluded packages
  (`inventory.container.slot`, `TransitRequest` itself, `content.entangloporter`, ...) — identical in shape to
  checkpoint09's capped surface. **The changed main-source owners did NOT compile (they remain excluded from the
  fabric slice) and no tests executed.** Slice-count guard updated for the one new api file and passes; the api-slice
  portable check passes (new file has no NeoForge reference).
- **Focused three-level nested abort check** (parent-requested, real pinned jar
  `fabric-transfer-api-v1-5.4.4+7b3d111d19`, plain javac/java, `/tmp/task052-grandchild-abort-probe/`): root A /
  child B / grandchild C — updateSnapshots in C, mutate, commit C, abort B, commit A → state restored at B (exactly
  one restore) and **no** final commit notification; all-commit path → mutation survives with exactly one
  `onFinalCommit`. **PASS** (restores=1, commits=1). This verifies the participant pattern the owners now use, not
  the production classes themselves.

## Known open risks / non-goals preserved

- `TransporterStack.STREAM_CODEC` still uses `NeoForgeStreamCodecs` (pre-existing networking WIP, unchanged).
- The item-transfer owner closure is still excluded from the fabric slice; compiling it requires the slice-expansion
  decision that stays with the parent.
- Bin creative ejection skips the source debit deliberately (infinite-source policy); target insertion commits.
- No runtime/packaged/full-suite execution, no production/mirror/deploy/push. CHECKPOINT09 overclaims (idempotent
  double-restore, `c-missing` framing, "compile" wording) are corrected by this document's explicit status section.
