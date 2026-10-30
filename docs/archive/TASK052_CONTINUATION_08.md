# Task052 continuation 08 — fluid pipe lookup lifetime

## Baseline and scope

Parent baseline 1a936e2d; preserve and FIRST merge WIP 06c13370 (branch pi-agent-f2d87fb9-4dc6-4b9) into the isolated implementation worktree. The WIP is not accepted or buildable. REVIEW_08 and PIPE_LIFECYCLE_EVIDENCE are binding; earlier transaction corrections remain binding. One GLM5.3 implementer. Do not restart Task052, omit WIP, change item routing, or expand unrelated source slices.

## Parent decision

Implement the complete fluid acceptor lookup ownership boundary, covering MechanicalPipe pulls, FluidNetwork sends and inherited public acceptor queries. External Fabric Storage objects must be operation-local, not retained in per-pipe/network caches, copied on adopt, or reused next tick. No reverse tank facade.

Use a Mekanism-specific fresh fluid AbstractAcceptorCache implementation (AcceptorInfo stores only lookup context, not returned Storage), guarded by current loaded server level/position and opposite face. FluidStorage.SIDED lookup must run afresh on each acceptor resolution, including formerly null providers. Do not duplicate Task051 global unload hooks or change energy/chemical/item cache policy.

For the network layer, use a fluid-specific NetworkAcceptorCache specialization with access to the authoritative FluidNetwork.getTransmitters membership. Add a narrow protected cache-construction hook in DynamicNetwork, preserving its default cache for all other network types; FluidNetwork overrides it to construct the specialization with a deferred membership supplier. Never evaluate subclass state during superclass construction. The fluid specialization builds a detached ephemeral position -> face -> Storage snapshot from current valid, loaded member pipes on each public read (iterator/values/count/has/get/directions). It must not populate the base retained ACCEPTOR map. Its commit/update/adopt/deregister handling must explicitly honor the derived-state design: connection refresh and pending dirty cleanup remain real, while no Storage objects are copied on merge. Do not silently inherit base adopt copying. Document that no retained endpoint state exists to transfer; current authoritative membership is re-read after merges/removals.

Snapshot discovery checks all six sides, not merely existing positive connections; use official refreshAcceptorConnections before the canSendTo/getAcceptor decision, preserve redstone/mutual transmission exclusions and side direction. A public query may see no world/client network; return empty without server lookup or chunk loading. Return detached directions sets (empty when absent), not invalidated views. MechanicalPipe pull must refresh relevant PULL-side connection validity as well as fresh lookup; do not bypass connection/redstone/exclusion policy by querying storage directly. Preserve captured destination identity/snapshot/exact insertion and existing FluidSendTransaction fair send/exact source debit.

Cost explicitly accepted by parent: O(6 * current member pipes) per network snapshot query, plus fresh relevant PULL-side lookup. No per-tick result memoization (provider results may change within a tick), no global polling framework. Record this unbenchmarked cost, do not claim performance parity. This is correctness-first implementation of the existing fluid ownership boundary, not a new generic cross-platform cache framework.

## Evidence and ordering

Parent read DynamicBufferedNetwork: onUpdate calls super then updates scale only; adopt first delegates to DynamicNetwork then merges chunks/capacity; deregister clears chunks/range after parent. WIP TransmitterNetworkRegistry.onTick processes changed chunks, invalid transmitters, orphan assignment, commitChanges, then updates networks if runsNormally. Thus event invalidation/commit alone does not rediscover silent dynamic providers between operations. No registry platform conversion approved in this substep.

Exact public consumer search: ItemNetworkReader reads count/has/directions, fluid description/toString reads count. Only logistical transporters call network getCachedAcceptor in current production, but the inherited fluid method must still be coherent. NetworkAcceptorCache getters also used by send/adoption; all must avoid retained Storage. Per-pipe getCachedAcceptor gates a connection bitmap; getConnectedAcceptor does not. The existing refreshAcceptorConnections sends a packet only when that side's bit changes and does not re-enqueue dirty work.

## Delivery and checks

Checkpoint in roughly 10–15 minutes: concrete progress or blocker if the specified owner design cannot be implemented without missing semantics. Do not choose a different global route. Preserve work on model quota failure, stop without fallback.

Read actual AcceptorInfo and lookup implementations before edits. Keep normal full source implementations, not fake stubs; do not expand allowlists just to hide unrelated compilation failures. Compile/build once with full logs and separately identify whether changed sources participated. Existing build has failed; capped error counts are not baseline-equivalence evidence. If sources remain excluded, report explicitly: build is not validation of those sources. Use only a few existing relevant focused tests if executable; no full runtime or new large fixture. Record exit status/failure stage and don't claim tests ran when compilation failed. Source/import review required even for excluded files. Commit code plus concise evidence; return commit, exact diff scope, commands/results, and remaining blockers. No production/mirror/deploy/push/reference writes/zvec/index/broad scans.
