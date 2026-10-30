# Task052 REVIEW13 correction — partial WIP handoff (stopped by user for model switch to astra)

Base: merge commit `1299c40b` (= clean parent `059d83a7` + preserved `21d722a4`, full ancestry kept).
Branch: `pi-agent-24a4371e-review13-fix` (this commit). All changes below are **edited, NOT compiled/verified**.

## Completed edits (this session, unverified)

1. **FabricItemStorage (defect 1)** — added real `isCreativeNow(IInventorySlot)`:
   `instanceof ICreativeInventorySlot c && c.isCreative()` (per-operation actual creative state), plus import.
   Used unchanged in the existing creative branch of `transfer()` (extraction non-depleting / external insert voids
   with unchanged expected read-back). REVIEW13's "verify Bin/component-backed owner behavior + finite strict
   readback" — NOT yet done.

2. **HandlerTransitRequest (defect 2)** three fixes:
   - `discoverView`: partial positive extraction no longer discarded; records actual accepted positive count
     (`extracted <= 0` → skip; else candidate with accepted amount).
   - `use(int, Transaction)`: null source storage with `amount > 0` now throws `IllegalStateException` (aborts
     caller tx); candidates exhausted with `amount > 0` also throws loudly. No silent success.
   - `addCandidate`: double-add fixed — only newly contributed amount joins `totalCount` (5+3 → 8, not 13).

3. **HandlerTransitRequest (defect 3)** — `PlanSnapshot` now identity-preserving: snapshots original ordered
   `HandlerItemData` object references (`identities` map) + candidate copies + totalCount; `restore` reinstates the
   same objects into `itemMap`, so a captured `TransitResponse`/`HandlerItemData` observes restoration after full
   debit + abort. Invalid unqualified `new HandlerItemData(hashed)` in static context removed entirely.

## NOT started / remaining (defects 4 and 5 untouched)

4. **Transporter simulation R7** (LogisticalTransporterBase + callers, lines verified this session):
   - line ~587 `insertUnchecked(long,...,tx)`: hardcoded `true` updateFlowing under tx → pass `doEmit`.
   - line ~566 private `insert`: same hardcoded `true`.
   - legacy `insert(...)`/`insertMaybeRR(...)` no-tx wrappers commit `own` unconditionally → only commit when
     `doEmit`, abort simulation otherwise.
   - RR private `<BE> insert(..., TxPathCalculator, tx)` (used by insertMaybeRR line ~531) lacks the checked
     `sideDifference`/`canReceiveFrom`/`canInsertToTransporterNN` gate the base private insert has — add it.
   - line ~598 `insertUnchecked(BE,...,PathCalculator,tx)` passes non-tx calculator under tx (mutations not
     snapshot-joined) — needs tx-aware forwarding or rejection.
   - QIOExporter (TileEntityQIOExporter ~379–384) already passes doEmit correctly in its calculators — no change needed there.

5. **QIOFrequency snapshot contract**: frequency-level `FrequencyRemoveParticipant` must additionally snapshot
   shared `totalCount` and prior `isDirty`/`needsUpdate`/`updatedItems` and restore them; per-type
   `TypeRemoveParticipant` must drop `totalCount` from its snapshot (own only count + containingDrives);
   join `frequencyRemoveTx` inside `QIOItemTypeData.remove(amount, action, tx)` so shared totals are covered
   regardless of entry path. Preserve drive identity metadata / commit-only save semantics.

6. Then: CONTINUATION09 full connected immediate caller boundary (QIO/Bin/miner, no Finder selected-source
   fallback), development compile (`./fabric-build.sh` or gradle compile), few focused checks, baseline-prove any
   claimed pre-existing diagnostics. No full fixtures.

## Running commands
None — nothing in background, no build was run.
