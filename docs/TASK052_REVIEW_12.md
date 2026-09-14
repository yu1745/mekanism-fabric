# Task052 correction checkpoint review — continue, not accepted

Parent checked actual `884eadfe` / branch `pi-agent-fcf4b3f5-0e05-484`, preserving WIP028acb26 through merge a5757dd0. This checkpoint adds TxOps and changes TransporterManager only; no build or functional acceptance claimed.

## Mandatory direction correction

New `TxOps.onAbortAndCommit` is incorrect for three transaction levels: root A, nested B, nested C. Register in C, commit C, abort B, commit A. C's own callback saw commit, root sees commit, so undo never happens and commit notification publishes an aborted operation. A once-guard prevents duplicate callback execution but does not propagate intermediate-parent rollback. This violates REVIEW11 R2/R4/R7 and the explicit stable real-owner SnapshotParticipant design. Do not spread this helper across owners; remove it and implement stable owner participants with Fabric's actual nested snapshot propagation. Carry explicit TransactionContext through prediction/routing; no new generic transaction shim or ambient-current shortcut. Use one small direct focused nested-grandchild check if feasible, not a new fixture program.

The agent's proposed miner fallback (arbitrary external storage + Finder by item type instead of selected physical mainSlots) is NOT parent-approved. Same variant does not prove selected-slot ownership. Implement selected identities through the actual sided FabricItemStorage provider as CONTINUATION09 requires. If the real bounding provider cannot expose these identities, report its concrete source/call chain and ask for a decision before substituting broader extraction.

Creative Bin adapter changes must be operation-specific and conditional on actual creative state; do not globally bypass finite readback just because a slot class implements an interface. Continue R1–R7 in full, including QIO and all immediate callers. No more full-source reread or global replanning is needed: the previous agent has located the owners and declared no blocker. Preserve all WIP, compile/build and few focused checks, report excluded-source RED honestly. This is a checkpoint, not task completion.
