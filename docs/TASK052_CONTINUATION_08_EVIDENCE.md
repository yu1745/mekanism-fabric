# Task052 continuation 08 — implementation evidence (implementer)

## Scope (exact diff)

New:
- `src/main/java/mekanism/common/lib/transmitter/acceptor/FluidAcceptorCache.java` — per-pipe fresh fluid cache. `AcceptorInfo` stores only lookup context (ServerLevel, neighbor pos, opposite face); each `acceptor()` performs a fresh `FluidStorage.SIDED.find(level, pos, opposite)` guarded by `WorldUtils.isChunkLoaded` (+ `level.isRemoved()`). No returned `Storage` retained, no `BlockCapabilityCache`, no refresh listener consumed (rediscovery is via the official bitmap refresh callers).
- `src/main/java/mekanism/common/lib/transmitter/acceptor/FluidNetworkAcceptorCache.java` — fluid-only derived `NetworkAcceptorCache` specialization with deferred membership supplier. All public readers (`getAcceptorValues/getAcceptorCount/hasAcceptor/getCachedAcceptor/getAcceptorDirections/getAcceptorFastIterator`) build a detached ephemeral `pos -> face -> Storage` snapshot per read: for each valid, non-remote, loaded member pipe, all six sides — `refreshAcceptorConnections(side)` (official bitmap/packet policy) then `canConnectToAcceptor(side)` (canSendTo) then bitmap-gated `getAcceptor(side)` fresh lookup; keyed at `WorldUtils.relativePos(pipePos, side)` under `side.getOpposite()`. `updateTransmitterOnSide` override = bitmap refresh only (no retained map writes). `adoptAcceptors` override = `adoptPendingDirty` only (no Storage copy; membership re-read post-merge). `getAcceptorDirections` returns detached `EnumSet.copyOf`/empty — never a view. Client/no-world reads see no member pipes → empty, no chunk loading.

Modified:
- `NetworkAcceptorCache.java` — `adoptAcceptors` split into protected `adoptRetainedAcceptors` + `adoptPendingDirty` (base behavior unchanged; fluid override uses only the latter).
- `DynamicNetwork.java` — `acceptorCache` now assigned in constructor via new protected `createAcceptorCache()` hook (default base cache for all other network types); no subclass state evaluated during superclass construction.
- `FluidNetwork.java` — overrides `createAcceptorCache()` → `new FluidNetworkAcceptorCache(this::getTransmitters)`. `tickEmit`/`FluidSendTransaction` fair-send/exact-debit path untouched.
- `MechanicalPipe.java` — `createAcceptorCache` → `FluidAcceptorCache`; covariant getter; `pullFromAcceptors` now calls `refreshAcceptorConnections(side)` on each PULL side before the fresh `getConnectedAcceptor` lookup (no direct storage bypass); transaction/snapshot/destination-identity logic untouched. Stale `Capabilities.FLUID.block()` registration removed.
- `FluidUtils.java` — ownership comment updated (fluid pipes migrated; energy/chemical/item remain old lookups).

Cost (parent-accepted, unbenchmarked): every network snapshot read is O(6 × current member pipes) plus fresh PULL-side lookups; no per-tick memoization.

## Build / checks (honest)

- `./fabric-build.sh` → EXIT=1, `BUILD FAILED`, failure stage `:compileJava`, capped display "100 个错误 / 11 个警告" (log: `/tmp/c8-build.log`, 202 error lines total incl. repeats). Diagnostics are the pre-existing slice-exclusion failures (e.g. `TransmissionType.java` cannot resolve `mekanism.common.content.network.transmitter`); none of the changed/new files appear in the diagnostics.
- All changed/new files are NOT in `fabric/{api,common,platform}-slice.txt` → **this build did not compile them; it is not validation of these sources.** No allowlist expansion was made.
- `./gradlew -p fabric :fabricTransferTest --tests '*FabricFluidSendTransactionTest*'` → EXIT=1, same `:compileJava` failure stage; **no tests executed**.
- Compensating source/import review done against actual signatures: `AbstractAcceptorCache`/`AcceptorInfo` contract, `NetworkAcceptorCache.updateTransmitterOnSide` generics (`Transmitter<ACCEPTOR,?,?>`), `DynamicNetwork.getTransmitters()` → `Collection<MechanicalPipe>`, `WorldUtils.isChunkLoaded/relativePos`, `Level.isRemoved()`, `EnumSet.copyOf` non-empty guard, `FluidStorage.SIDED.find(level, pos, side)` — all verified present with these erasures/signatures in the current tree. Unchecked-cast warning on `MechanicalPipe.getAcceptorCache` remains (warning only).

## Preserved semantics / non-goals

Transactions, fair sender, exact source debit, destination identity checks, `refreshAcceptorConnections` packet-only-on-bit-change, redstone/mutual exclusions, energy/chemical/item cache policy, no reverse facade, no per-tick memoization, no global polling/unload-hook duplication (Task051 untouched).
