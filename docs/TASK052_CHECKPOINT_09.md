# Task052 checkpoint 09 — item transfer owner closure implementation (GLM5.3 single implementer)

## Commits (this continuation, on merged baseline `6a9f8339` = parent `cc5b0937` + preserved WIP `2cb72d71`)

- `36aa9941` core request layer: TransitRequest/HandlerTransitRequest over real sided `Storage<ItemVariant>`
  (slotted real positions or operation-local retained StorageViews; cumulative abort-only discovery; exact debit
  inside the caller transaction, mismatch aborts), TransporterManager abort-only cumulative prediction
  (`Transaction.openCurrent()`, composes with caller transactions, destination-face rejection rules preserved),
  TransporterUtils fresh `ItemStorage.SIDED` lookups + abort-only insert simulation.
- `08f36bdf` transporter layer: `TransporterItemStorage` insert-only endpoint (replaces CursedTransporterItemHandler,
  deleted), tx-aware LogisticalTransporterBase insertion (transit entry/nextId/path/flowing staged with idempotent
  own/root-abort undo, commit-only initial packet/markForSave), pull with sender-owned tx + backoff on abort,
  final in-flight delivery (target insertion + flowing removal in one tx, commit before deferred deletes),
  TransporterStack deep PathSnapshot + RR rrTarget snapshot/restore, ItemAcceptorCache/ItemNetworkAcceptorCache
  (fresh ephemeral design reuse of the reviewed fluid caches), InventoryNetwork over `Storage<ItemVariant>`.
- `c-missing` sorter/ejector: tx-aware TileEntityLogisticalSorter (fresh home/target providers, per-filter caller tx,
  activity only on committed sends, tx-aware sendHome via real back inventory force=false), TileComponentEjector
  per-side fresh lookups + EjectTransitRequest rebind of the actual sided provider, IAdvancedTransportEjector tx
  overload, SorterFilter/InventoryUtils over Storage.
- `41f25117` QIO + remaining callers: transaction-aware QIODriveData.remove (captured-stack metadata rollback,
  deferred holder dataUpdate to outer commit, stale-slot replacement guard), QIOItemTypeData.remove journal,
  QIOFrequency.removeByType tx journal (exact ordered itemDataMap rebuild, index membership restore, wildcard caches
  left cleared, no remove-then-add), TileEntityQIOExporter per-candidate atomic commits with snapshotted iteration
  list and preserved randomization/quotas/RR, TileEntityDigitalMiner fresh providers + getReplace exact-debit-only
  return, TileEntityBin actual DOWN sided source with transactional debit; creative infinite/void policy stays owned
  by BinInventorySlot and flows through the same exposure.

## Verification status (explicit: source review vs compile/test)

- `./gradlew -p fabric compileJava --console=plain` → **BUILD FAILED (pre-existing RED partial-slice surface)**,
  log preserved at `/tmp/task052_c09_fabric_compile.log` (100 displayed errors, all missing not-yet-admitted
  packages/classes such as `tile.machine`, `tile.prefab`, and the excluded `TransitRequest` itself). No diagnostic
  references any class edited this session beyond the pre-existing exclusion-driven "does not exist" for excluded
  owners. **The changed sources did NOT compile (they are excluded from the fabric slice) and NO tests executed.**
- Slice (`fabric/common-slice.txt`) intentionally NOT expanded: adding the item-transfer owners would require their
  full not-yet-ported dependency closure; that decision stays with the parent.
- Brace-balance sanity check passed on all 15 changed/new files.

## Known open risks / non-goals preserved

- `TransporterStack.STREAM_CODEC` still uses `NeoForgeStreamCodecs` (pre-existing networking WIP, unchanged scope).
- Fabric `Transaction` callbacks run LIFO; all registered undos are idempotent (Set-backed flowing re-adds, value
  restores), so double own-abort+root-abort firing is safe. Nested-commit-then-root-abort is covered by onRootAbort.
- Legacy non-tx APIs preserved for unrelated callers (QIO removeByType/remove, sendHome, insertMaybeRR, didEmit/
  getToUse for the QIO Importer).
- No runtime/packaged/full-suite execution, no production/mirror/deploy/push.
