# Task052 continuation7 — QE frequency typed fluid collection

Accepted production remains Task051. Parent baseline clean 1c594507; WIP 2f1b440a (continuation6) merged first as merge commit 4c728325 in the isolated worktree; all prior WIP and REVIEW_07/item evidence retained. Task052 is still RED and no working-machine claim is made.

## Parent scope (REVIEW_07 route decision)

QE frequency lookup was the selected next fluid connection: the generic `getCachedCapability<T>` returned the old NeoForge fluid handler identity while the WIP frequency already consumes Fabric `Storage<FluidVariant>`. Fix actual identity, no generic/unchecked cast for fluid, no reverse facade.

## Actual diff (3 files)

- `src/main/java/mekanism/common/tile/TileEntityQuantumEntangloporter.java`
  - New typed nullable getter `getFluidStorage(Direction side)`: guards `level != null && WorldUtils.isChunkLoaded(level, neighbor)` then performs a fresh `FluidStorage.SIDED.find(level, worldPosition.relative(side), side.getOpposite())` each operation — same established continuation6 caller pattern (Ejector/Tank/Pump). The returned Storage is never cached or retained across ticks.
  - Generic `getCachedCapability` switch: `FLUID` case removed (comment explains why); only `CHEMICAL` remains in the fallback NeoForge cache path. HEAT/ENERGY/ITEM branches untouched. The generic path can no longer return any old fluid handler.
- `src/main/java/mekanism/common/content/entangloporter/InventoryFrequency.java`
  - `Map<TransmissionType, Consumer<?>>` replaced with `Map<TransmissionType, BiConsumer<TileEntityQuantumEntangloporter, Direction>>`; the static generic `accept` helper removed; the eject loop now calls `entry.getValue().accept(qe, side)` directly.
  - FLUID closure is typed: `qe.getFluidStorage(side)` → `target.accept(storage)` on `SendingFluidHandlerTarget` (already `Consumer<Storage<FluidVariant>>`). No generic or unchecked cast anywhere on the fluid path.
  - ENERGY/CHEMICAL closures keep their existing cached NeoForge lookup semantics by calling the existing generic helper `qe.getCachedCapability(side, type)` inside the closure (unchanged behavior; the unchecked cast there is pre-existing and out of scope).
  - Preserved verbatim: activeQEs owner table, `isValid`/`lastEject != gameTime` once-per-gameTime guard, `qe.canFunction()`, `level.shouldTickBlocksAt(ChunkPos.asLong(...))`, config/ejector `isEjecting` check, relative-side iteration order, sender-owned `FluidSendTransaction` source snapshot/exact debit in `SendingFluidHandlerTarget.run()`.
- `src/main/java/mekanism/common/util/FluidUtils.java`
  - Connection-status comment updated: InventoryFrequency/QE fluid collection now migrated; MechanicalPipe acceptor cache remains explicitly open.

## Included/excluded files

Included (edited): the 3 files above. Excluded (not touched): MechanicalPipe/AcceptorCache family, item transit, energy/chemical semantics, allowlists, upstream references. The QE source block itself is a caller, not part of the excluded set; no excluded changed source became compiled by this edit.

## Build evidence

- `./fabric-build.sh` run twice (first interactive, then captured to `/tmp/c7-changed.log` on this machine): BUILD FAILED, same pre-existing RED state. Two javac passes each capped at 100 displayed errors (`100 个错误` twice), 11 warnings each — i.e. identical displayed totals to the continuation6 388-error baseline under javac's 100-per-pass cap; the cap prevents an exact total recount without a new build harness, which was not built.
- **Zero of the displayed errors are in `TileEntityQuantumEntangloporter.java`, `InventoryFrequency.java`, or `FluidUtils.java`** — classpath-free parse shows no new diagnostics from this change; this is not a claim of type-correct compilation.
- Focused checks attempted: `:fabric:test --tests DistributionLongTest --tests APIAccessTest` → BUILD FAILED at compile of the RED main source set, so staged tests remain not runnable (same as continuation6). No test infrastructure built.

## Remaining blockers

- Main source set still RED for pre-existing reasons (missing NeoForge-era packages such as `mekanism.common.attachments.containers.*`, `mekanism.common.tile.transmitter`, transporter pathfinder, etc.) — out of this scope; no allowlist expansion attempted.
- MechanicalPipe network acceptor-cache migration remains open per REVIEW_07 (needs Transmitter dirty/update and network send lifecycle design; not delegated here).
- Item transit implementation remains unapproved policy.

## Checkpoint

GO — scope delivered as WIP commit; hand back to parent for review of the actual diff and decision on the next connection (pipe acceptor cache lifecycle or item policy).
