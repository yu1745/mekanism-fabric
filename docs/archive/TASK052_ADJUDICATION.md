# Task052 — parent adjudication after first implementation checkpoint

Baseline of accepted production remains `a4feef18`. Main planning commit is `41eec187`. Child branch `pi-agent-ed6068f8-4b48-4cc`, tip `b9c26ac6`, preserves WIP only (preceded by superseded checkpoint `de73bab7`). **Do not merge or label this API-only WIP a completed Task052.** Child reported compileJava/assemble success at223/226/46; parent has not rerun it. No machine owner was admitted. The branch is retained for continuation, not discarded.

## Parent findings on the actual WIP

Parent read the complete WIP diff (13 files), the corrected checkpoint, complete original Mekanism/MekanismHooks, BlockBounding/TileEntityBoundingBlock, and the pinned lifecycle MinecraftServerMixin.

- `IRadiationManager.backCompat$getLevel` cannot use `FabricLoader.getGameInstance() instanceof MinecraftServer`: the client-side game instance is Minecraft even while an integrated server runs. Use a real lifecycle-bound server owner available on both dedicated/integrated servers, with start/stop identity and post-save ordering, shared with SavedData's real server need. Never use a test-only holder or store a client/server guess.
- `IBlockSecurityUtils` unnecessarily discards supplied state/BE parameters. Pinned Fabric BlockApiLookup explicitly has `find(world,pos,state,blockEntity,context)`. Preserve the original hints using that overload; the per-find provider evidence is not a reason to change arguments.
- The new ItemAbility token/provider interface has no actual item implementation or caller migration in this WIP. Keep it provisional until its actual main-path consumers/providers are ported; do not claim API behavior merely because it compiles. Radiation/security `INSTANCE` service loading similarly requires the real service implementations/resources before claiming usable APIs.
- The checkpoint's list of admitted types includes some already-admitted files; actual diff adds18. Use the slice diff as authority.
- A mechanical script that rewrites slice counts is not an admission audit. Do not use it to justify broad inclusion; remove this unnecessary new helper on continuation and keep explicit reviewed additions/counts.

## Q1 — No replacement or truncated Mekanism singleton

Reject both a shadow source and a reduced in-place Mekanism whose omitted static members are promised for later. Original Mekanism has real player state, packets, multiblock managers, server reset and registration ownership: these cannot be deleted to manufacture a core.

The permitted permanent Fabric delta is **call-site dependency narrowing to actual owners**, as in accepted prior batches:
- MODID-only uses take `MekanismAPI.MEKANISM_MODID`; rl-only calls use vanilla `ResourceLocation.fromNamespaceAndPath`; logging-only uses use the established Mekanism SLF4J category, without loading the whole NeoForge entrypoint.
- Computer presence/binding moves to the real Fabric computer-integration owner described under Q3, not a fake Mekanism.hooks facade.
- Packet dispatch must use the actual existing Fabric networking owner with implemented real payloads and thread/receiver semantics, not a fake PacketHandler instance.
- Any other mutable field/manager dependency must retain its actual implementation, initialization, callers and cleanup. Port the concrete required domain or return that exact unresolved path. This authorization does not reduce those domains to booleans/null/no-ops or allow a generic replacement singleton.

The original excluded Mekanism file remains intact unless an actual complete responsibility is moved with all immediate callers updated and preserved forwarding where appropriate. Document Fabric signature/call-site changes. Do not claim the original entrypoint is admitted by these changes.

## Q2 — Canonical bounding registration, not a future dedupe promise

Choose real bounding content and a **permanent registry composition**, not registry-by-name fallback and not deleting catalog entries. The owning module must register original BlockBounding + BlockItem + TileEntityBoundingBlock under canonical IDs with real providers, and must own their actual shared deferred-register objects. Original MekanismBlocks/MekanismTileEntityTypes must forward their BOUNDING_BLOCK fields to the exact same holder, and forward/use the shared deferred registers rather than maintaining another registration. This is an immediate source change to both original catalogs, not a future plan to dedupe. Initialize applicable catalogs before registerAll/freeze; no post-freeze insertion, duplicate suppliers or anonymous temporary type carriers. Fabric bootstrap invokes the real registration path now.

Preserve full BlockBounding gameplay: shape/interaction/main-position redirection, destruction/drops/explosion behavior, neighbor power/comparator, fluid state, master synchronization, provider forwarding, upgrades and naming. TileEntityBoundingBlock's IUpgradeTile return type binds the real TileComponentUpgrade; do not stub it to make this a separate tiny batch. This remains part of the connected machine owner closure.

Clarification confirmed to the implementer at checkpoint `a050e8ab`: the two original catalog files may remain excluded from the slice while their aliases/shared register references are edited now. Only the admitted bounding module and its real content/behavior owners need compile for this composition step; this is not a demand to compile the entire catalogs just to establish the aliases. No standalone API/bounding completion claim substitutes for the Task052 connected owner target.

If a stable shared registry composition cannot retain actual existing holder/registry types, return the precise conflict rather than inventing a new registry facade. Complete original dependent domain classes are allowed irrespective of file count.

## Q3 — Real optional computer integration

Authorize a pinned compatible **CC:Tweaked Fabric1.21.1 compileOnly** dependency and the actual ComputerCraft binding/helper + FactoryRegistry/annotation-generated owner path. Confirm the artifact/version/API before using it; do not assume NeoForge and Fabric helper signatures match. Only load optional CC classes after actual FabricLoader mod-presence detection. Bootstrap must initialize the real integration before registered machines advertise it; initialization failure must be explicit, not reported as support.

Do not port the entire MekanismHooks unrelated optional-mod catalog just for computer presence. A permanent Mekanism-specific Fabric computer integration module can own actual presence, initialization and bindings, used by the real tile and actual provider registration. Preserve common computer annotations/methods and real runtime dispatch.

No hardcoded false because code is missing. OC2's lack of a Fabric port is a platform support boundary, not an implemented binding: document that boundary and do not advertise OC2-backed support. Do not claim universal optional-mod equivalence. Any additional reachable optional integration needing a new policy returns a precise checkpoint rather than being disabled silently.

## Q4 — Lifecycle mapping approved with implementation-level proof

Parent read `/tmp/lifecycle/net/fabricmc/fabric/mixin/event/lifecycle/MinecraftServerMixin.java` completely: world LOAD fires **after** `Map.put` inside createWorlds; END_SERVER_TICK is tick tail; SERVER_STOPPED is shutdown tail. This is stronger than the javadoc-only claim. Use actual world LOAD for frequency loading, END_SERVER_TICK for tick with tickRateManager.runsNormally(), and post-save SERVER_STOPPED for FrequencyType.clear then FrequencyManager.reset. Retain the lazy-load safety net. The real server reference may be captured on SERVER_STARTING, but accessing overworld/SavedData must wait until actual world availability. Clear only the matching stopped server after domain save/reset; integrated-server restart must not retain the prior world/server.

QIOGlobalItemLookup is **not automatically a future unrelated sibling**: full QIOFrequency is in this selected closure. Check its real lookup owner and preserve load/reset ordering whenever used. Likewise any multiblock sibling executed by admitted domains. Do not mark an executable dependency deferred merely because the callback also services it in the original mod.

## Continuation contract

Continue from clean planning baseline in one isolated GLM5.3 worktree and cherry-pick preserved commits `de73bab7` then `b9c26ac6` (checkpoint followed by corrected checkpoint and WIP changes) before correction, so no progress is lost; do not merge its sources into the parent accepted branch yet. Apply the concrete WIP corrections and the owner solutions above as part of Task052, not another API-leaf completion. Do not repeat a global closure scan or reinterpret the scope by raw import counts. First checkpoint about10–15min should contain real implementation progress and any specific unresolved owner/platform conflict. Further connected-domain implementation is authorized when semantics are established; repeated compilations self-heal normally.

Ordinary assemble + few relevant existing focused checks; no full/runtime/packaged/clean or new fixture bureaucracy by default. Preserve WIP with accurate validation if interrupted. Same safety boundaries and immediate270s supervision as TASK052_PLAN. Parent accepts only actual coherent diff and evidence, never a GO claim or a compiling service declaration without its runtime owner.
