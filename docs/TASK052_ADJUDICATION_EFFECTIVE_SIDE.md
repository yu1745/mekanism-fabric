# Task052 adjudication — EffectiveSide server/worker thread-group semantics

This resolves the priority blocker recorded in `TASK052_PAUSE_AFTER_ASTRA.md`. The pause is lifted by the user's 2026-10-26 authorization to continue the port. Per the same user instruction, the model-dispatch sections of `AGENTS.md` are moot from now on: the main agent is the sole implementer, no pi/Astra/DeepSeek dispatch applies.

## Upstream mechanism (evidence)

FML `net.neoforged.fml.util.thread` (repo `neoforged/FancyModLoader`, branch `main`, paths recorded 2026-10-26):

- `EffectiveSide.get()`: `Thread.currentThread().getThreadGroup()` must be `instanceof SidedThreadGroup`; its `getSide()` decides. **Any other group defaults to `LogicalSide.CLIENT`.** There is no COMMON answer and no ancestry walk — exact group identity only.
- `SidedThreadGroup extends ThreadGroup implements ThreadFactory`; `SidedThreadGroups.CLIENT`/`SERVER` are two static singletons created once per JVM and reused across server restarts.
- NeoForge 1.21.1 (`neoforged/NeoForge`, branch `1.21.1`, `patches/net/minecraft/server/MinecraftServer.java.patch`): the ONLY sided-group wiring on this branch is in `MinecraftServer.spin` — the server thread is created as `new Thread(SidedThreadGroups.SERVER, runnable, "Server thread")`. Everything else in `spin` (uncaught handler, priority-8, factory-apply order, `start()`) is unchanged.
- The newer `patches/net/minecraft/server/network/EventLoopGroupHolder.java.patch` seen in the repo belongs to a post-1.21.1 vanilla layout; **that class does not exist in 1.21.1** and is not evidence for this port.
- Consequence on NeoForge 1.21.1: threads created by the server thread inherit the SERVER group (plain `ThreadGroup` inheritance); the server's Netty IO factory is built lazily inside `ServerConnectionListener.startTcpServerListener`/`startMemoryChannel` (vanilla `Suppliers.memoize`), whose first `.get()` happens on the server thread in `initServer()` — so those IO threads land in the same group without any extra hook. Everything else (client threads, bootstrap threads) is non-sided and answers CLIENT.

Vanilla 1.21.1 (pinned `upstream/fabric-minecraft`): `MinecraftServer.spin` contains exactly one `new Thread(Runnable, "Server thread")` (`MinecraftServer.java:270`), confirming the preserved implementer's bytecode evidence.

## Required semantics at the six official call sites (baseline `4646f509`)

| Call site | Question asked | Why the group answer is correct |
|---|---|---|
| `attachments/FrequencyAware` codec/stream/getFrequency | `isServer()` → look up global frequency manager during component decode | decode runs on server thread (world load/save), client thread, or the respective Netty IO threads; server IO threads inherit the marked group |
| `item/loot/PersonalStorageContentsLootFunction` | `isClient()` → client-side placeholder inventory | loot runs on server thread; client loot (creative pickup) on client threads |
| `lib/inventory/personalstorage/PersonalStorageManager.forOwner` | `isClient()` → no SavedData on client | same |
| `lib/transmitter/DynamicNetwork.isRemote()` | `world == null` fallback → `isClient()` | client networks answer CLIENT exactly as before |
| `util/text/OwnerDisplay.getOwnerName` | `player == null && isServer()` → server username lookup | name resolution must not hit the client map on server threads |
| `util/MekanismUtils` (last-known-username skin fallback) | `isServer()` → ask Mojang | server-thread-only fetch |

## Approved permanent boundary

1. **One Mekanism-owned server ThreadGroup marker.** A platform mixin at `MinecraftServer.spin` creates/reuses a single Mekanism-owned `ThreadGroup` whose parent is the creating thread's group, and creates the server thread inside it (`new Thread(group, runnable, "Server thread")`). All other `spin` statements keep vanilla order and behavior. The marker instance is created once per JVM and reused across server restarts — parity with `SidedThreadGroups.SERVER`.
2. **One common-side query.** A Mekanism-owned replacement for `EffectiveSide.get()` living in `mekanism.common` without platform imports (the marker is a JDK `ThreadGroup`; the platform mixin only needs a registration seam). Semantics: exact current-group identity against the marker → SERVER; anything else → CLIENT (FML default; no COMMON answer). `isServer()`/`isClient()` callers keep their decision shape.
3. The six call sites above switch to the Mekanism query; no other behavioral change.
4. **No thread injection, no generic worker hook, no `EventLoopGroupHolder` port** (class absent in 1.21.1; the server IO group inherits the marker by construction). If future evidence shows an unmarked thread running server-side Mekanism logic, return to adjudication with that evidence instead of adding a speculative hook.

## Explicitly rejected

- Integrated-server-existence or `getServer() != null` checks (client instance is non-null while an integrated server runs).
- Server-main-thread-only identity checks (misses server-spawned workers and Netty IO threads).
- Storing a client/server "guess" or per-thread affinity maps.
- Any generic side-classification framework beyond the marker + query.

## Status

Adjudicated 2026-10-26. Implementation happens inside the preserved Task052 WIP (branch `task052-cb-deepseek-1`, worktree `mekanism-fabric-wt-t052`, tip `8d0d4ebd`) by the main agent as sole implementer. `DynamicNetwork`/`FrequencyAware`/`OwnerDisplay` admissions now proceed against this decision; the remaining semantic queue from `TASK052_CONVERGENCE_14.md` is still adjudication-gated item by item.
