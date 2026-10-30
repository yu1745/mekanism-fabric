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

## Slice compile membership (corrected 2026-09-14)

Membership here means actual presence in `fabric/api-slice.txt` / `fabric/common-slice.txt` / `fabric/platform-slice.txt` — the compile inputs of `./fabric-build.sh` (`gradlew -p fabric build`) — not "edited vs untouched". Verified by exact path search (`rg`) of all three slice lists: **none of `mekanism/common/tile/TileEntityQuantumEntangloporter.java`, `mekanism/common/content/entangloporter/InventoryFrequency.java`, or `mekanism/common/util/FluidUtils.java` appears in any slice list — all three are EXCLUDED from the fabric slice build's compile input.** No allowlist was expanded to include them (per scope).

Out-of-scope (not touched): MechanicalPipe/AcceptorCache family, item transit, energy/chemical semantics, allowlists, upstream references.

## Build evidence (corrected 2026-09-14)

- `./fabric-build.sh` run twice in continuation7 (first interactive, then captured to `/tmp/c7-changed.log`): BUILD FAILED, same pre-existing RED state. Two javac passes each capped at 100 displayed errors, 11 warnings each. The 100-per-pass cap means this is NOT evidence of equality with the continuation6 388-error baseline — only that displayed totals coincide under the cap; an exact total recount would require a new build harness, which was not built.
- The prior claim "zero displayed errors in the 3 changed files" is trivially true and carries no probative weight: the 3 files are not compile inputs of the slice build (see membership above), so this build could never diagnose them. The earlier "classpath-free parse shows no new diagnostics" claim is withdrawn — no such parse was actually performed or recorded, and even if performed it would not be type proof.
- Consequently the compile status of all three edited files (including this continuation's changes) is UNVERIFIED by any build in this task: no slice-included compilation path exercises them.
- Focused checks attempted: `./gradlew -p . :fabric:test --tests "mekanism.common.lib.distribution.DistributionLongTest" --tests "mekanism.api.APIAccessTest" 2>&1 | tail -8`. The captured output (last 8 lines only) shows `BUILD FAILED in 3s`, `2 actionable tasks: 2 executed` — the failure MODE (Gradle task-selection/test-filter failure vs main-source-set compile failure) cannot be distinguished from this truncated output, and the earlier assertion that it failed "at compile of the RED main source set" is unsupported by the captured evidence. Either way the staged tests did not run. No test infrastructure built.

## Correction of this continuation (post-review fix)

Parent review of the full diff found a definite defect: `InventoryFrequency.java` had dropped `import java.util.function.Consumer` while the inner classes `SendingEnergyAcceptorTarget` (L319), `SendingFluidHandlerTarget` (L343) and `SendingChemicalHandlerTarget` (L369) still implement `Consumer<T>`. The import is restored alongside `BiConsumer` in this correction commit. Since the file is excluded from the slice compile input (see membership above), no duplicate build was performed solely for the restored import — it would not compile this file anyway; allowlist expansion to force verification is out of scope. This is a textual fix verified by inspection, not by compilation.

## Remaining blockers

- Main source set still RED for pre-existing reasons (missing NeoForge-era packages such as `mekanism.common.attachments.containers.*`, `mekanism.common.tile.transmitter`, transporter pathfinder, etc.) — out of this scope; no allowlist expansion attempted.
- MechanicalPipe network acceptor-cache migration remains open per REVIEW_07 (needs Transmitter dirty/update and network send lifecycle design; not delegated here).
- Item transit implementation remains unapproved policy.

## Checkpoint

GO — scope delivered as WIP commit; hand back to parent for review of the actual diff and decision on the next connection (pipe acceptor cache lifecycle or item policy).
