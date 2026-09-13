# Task052 continuation 3 — connect the actual container/QIO/config hub

## Reviewed correction, not whole-task acceptance

Parent read the complete `c9211626` correction diff (14 files) and independently ran `./gradlew -p fabric assemble` in a detached worktree at that exact commit: **BUILD SUCCESSFUL in1m19s**, all3 slice guards and remapJar included. Log `/tmp/task052-parent-c921-assemble.log`; temporary verification worktree removed. This verifies225/228/46 in-slice sources only. WorldUtils/config/Mekanism/frequency staged sources remain excluded. No runtime/full tests/packaged/clean verification was performed by parent. The correction is suitable for **continuing the preserved WIP**, not acceptance of Task052 or a usable machine.

Preserve tip `c9211626` (branch `pi-agent-1cbd0de1-1d20-406`); it includes all earlier WIP and parent review/adjudication docs through3f38be8a. Main parent still contains planning/review docs only atop accepted Task051 production. Do not discard the WIP or independently reimplement it.

Minor checkpoint wording to correct during continuation: IRadiationManager IS in the API slice; its service implementation remains missing. Saying its API source is staged/excluded is inaccurate. Overall staged/in-slice separation remains essential.

## Concrete next implementation target

Start with the **original complete `MekanismContainer` + `QIOItemViewerContainer` and their real QIO/config ownership closure**, not a search for another green leaf. Parent previously read full MekanismContainer and has now read full ClientConfig, QIOItemViewerContainer, QIODriveData, QIOFrequency and QIOGlobalItemLookup. The connection is executable:

- `ClientConfig` directly types its settings as QIOItemViewerContainer.ListSortType/SortDirection; the latter own actual comparators, translation/texture keys and list behavior, not dead enum names. Keep those public nested types and behavior; do not copy enums to fake config metadata.
- MekanismContainer owns ordered/dynamic sync tracking, initial/dirty property packets, typed frequency/list/collection application, selected windows and shift-click. Preserve the real sync and packet receiver owner/thread/container checks and cleanup.
- QIOItemViewerContainer owns real virtual crafting slots/window selection, transfer target ordering and double-click transfer, server frequency viewer open/close, per-UUID cache counts, paused sorting/removal queues, search/sort persistence and real C2S take/place/shift-take payloads. Client-only Minecraft/window calls must not be initialized/executed on a dedicated server. It cannot be admitted by stripping its nested enums, crafting or packet branches.
- QIODriveData loads actual DRIVE_CONTENTS, writes DRIVE_METADATA through the actual drive holder key, and maintains SIMULATE/EXECUTE counts/capacity/dirty state. No in-memory replacement drives or dummy data components.
- QIOFrequency owns total/type capacity, drives and physical saves, UUID-backed type tracking, mass simulate/execute insert/extract, fuzzy/tag/mod/wildcard lookups, security trust rechecks, viewing-player incremental packets and kill-viewer dispatch. Its lazy packet must remain once-per-needed-update (not reconstruct per recipient); preserve dirty-save and deactivation semantics.
- QIOGlobalItemLookup's real singleton binds `mekanism_qio_type_cache` SavedData in overworld, persists serializable item→UUID mappings and aliases, recovers legacy tags and merges collisions. Admit its actual load/save/reset owner with QIOFrequency; do not postpone it as an unrelated sibling. **Concrete cross-server risk in original source:** `reset()` replaces itemCache and nulls dataHandler but does not clear mergedIds. When this owner is integrated, clear mergedIds with the same server-lifetime reset so world A's UUID aliases cannot affect world B. Preserve the current world's persisted aliases. This precise lifecycle correction is authorized, not an invitation to audit all QIO logic.

## Implementation strategy / acceptance boundary

The direction remains the concrete original closure with permanent owner substitutions already adjudicated, no file-count cap, no fake/no-op source, no pruning catalogs/public branches. Use actual existing Fabric packet/registry/config/lifecycle facilities. Complete required immediate owners rather than replacing them with placeholders.

**Keep the real named target in the compilation work-list while resolving its dependencies.** A temporary failing compile on an isolated WIP branch is acceptable; removing the target and calling unrelated admitted APIs a green container batch is not progress against this target. At a checkpoint report the actual compiler surface, implemented owners and next concrete unresolved path. Do not rerun a raw recursive import scanner or repeat global candidate comparisons. Do not spend a new session merely staging another set of uncompiled utility replacements while leaving the named owners untouched.

Bootstrap must retain one actual common/startup config instance/file claim when MekanismConfig is admitted; reuse or move the accepted FabricChemicalConfig/bootstrap objects and update their actual callers. Passing the same manager with two different config objects is not enough. Preserve common/server/client config load/reload/cache invalidation behavior, client-side application ownership and config packet synchronization rather than calling every SERVER config globally loaded at startup. Resolve the selected connected config domain rather than weakening it.

Tag/recipe reload invalidation must bind the real accepted recipe lifecycle and actual QIO tag caches. If moving CommonWorldTickHandler's flush flag, migrate every real writer/reader and pulse/reset ordering; a copied boolean with future-mutator promises repeats the rejected vibrator bug. Username resolution likewise needs a real cache/profile owner with persistence and lifecycle, not a constant username or guessed current server. New semantic conflicts requiring policy decisions return precise call paths for parent adjudication.

The permanent shared bounding registration aliases and real optional CC:Tweaked Fabric binding remain the later connected obligations in TASK052_ADJUDICATION. Do not fabricate them to unblock container compilation; pull them in only when actually required by this target.

## Working contract

One GLM5.3 implementer, isolated clean worktree, merge c9211626 first to preserve WIP. Immediate270s supervision. First10–15min checkpoint contains actual implementation/compile progress or a new exact semantic blocker, not another full dependency map; continue implementing when the route is established. No silent model fallback; preserve and stop if model unavailable/quota.

Ordinary compile/assemble plus only a few directly relevant existing focused checks once coherent. No full suite/runtime/client/server fixture/packaged/clean/new test harness by default, and no acceptance claim before actual target compilation. Preserve useful failing WIP and exact compiler diagnostics if interrupted. No production/mirror/deploy/push/index, broad scans, reference writes, duplicated sources or test deletion. Parent reviews real diffs and necessary checks, not summaries alone.
