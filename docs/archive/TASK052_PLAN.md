# Task052 — Concrete TileEntityMekanism ownership closure

## Parent decision

Baseline: accepted Task051 `a4feef18` (API/common/platform 205/226/46). This is an implementation direction, **not an admission or machine-completion claim**.

Choose the concrete original owner route described in `MACHINE_OWNER_ADMISSION.md`, rather than inventing an owner interface to evade shared machine behavior or continuing unrelated leaf admissions. Target the full original `src/main/java/mekanism/common/tile/base/TileEntityMekanism.java`, its actual owned components and direct persistence/container/registry obligations. Scope follows executable ownership, not a source-count limit. Preserve all shared branches, not only Crusher's currently exercised branch.

Parent source evidence: previously read full TileEntityMekanism, TileComponentConfig, ITileComponent, TransmissionType, AttachedSideConfig, TileComponentFrequency, FrequencyType, FrequencyManager and MekanismContainer. This checkpoint additionally read the remaining ContainerType methods, complete MekanismDataComponents and MekanismSavedData, and the actual Fabric bootstrap. ContainerType really invokes tile container getters and apply/collect, and its item capability providers enforce config-loaded and stack-count policies. MekanismDataComponents' frequency and mode registrations are executable typed codecs. Neither can be reduced to constants or omitted fields while claiming its admission.

## Concrete work and allowed deltas

1. Admit the original TileEntityMekanism with its actual five manager constructor contracts, actual component list, conditional security/upgrade/chunkloader behavior, frequency lifecycle, server/client ticks, save/load, implicit components, redstone/comparator, radiation/heat, GUI and computer-facing public behavior intact. Reuse accepted CapabilityTileEntity lifecycle and permanently bound config predicate; do not reopen exposure from clearRemoved or rebind revoked aliases.
2. Complete the original TileComponentConfig (Task050's real state module, Task051's lookup identities), plus its actual facing/security/save/update/container/component consumers. Preserve TransmissionType public overloads and actual transmitter consumers if needed; no method deletion to compile an enum. All provider registration uses the real ItemStorage.SIDED, FluidStorage.SIDED, Team Reborn EnergyStorage.SIDED, ChemicalHandlerManager.CHEMICAL_HANDLER and HeatHandlerManager.HEAT_HANDLER identities.
3. Retain the real TileComponentFrequency/FrequencyType/FrequencyManager ownership. TELEPORTER/INVENTORY/SECURITY/QIO require their real domain implementations, serializers and manager wrappers, not inert descriptors. MekanismSavedData must use the real server overworld DimensionDataStorage and preserve mekanism_ naming, factories and HolderLookup. Bind required server load/tick/reset to real Fabric lifecycle events from MekanismFabricBootstrap; do not use test-only/global unowned server state or in-memory persistence substitutes. Inspect original event order before selecting matching callbacks.
4. Preserve ContainerType's complete five-domain descriptors, component-backed storage copy/apply/collect and item-provider guards. Port real DataComponentDeferredRegister/MekanismDataComponents registration with canonical IDs, aliases, codecs and actual registry-freeze ordering. No fake registry holders, null codec stand-ins or pruning the static catalog. Existing official package names remain.
5. Preserve the actual MekanismContainer ordered/dynamic sync tracking, full typed frequency/list/collection property application, packets, security, windows and shift-click semantics. Use real Fabric networking registration and its server/player/level/thread ownership, not local callbacks pretending to be packets. Reuse accepted network/config/registry machinery where it truly fits.
6. Retain actual block/entity registry provider wiring (TileEntityTypeDeferredRegister) and bounding placement/removal/master synchronization when required by this closure. Task051 removed only an inapplicable cache notification walk: it did not authorize removal of bounding gameplay or provider registration. Use original classes and IDs, never a Crusher-specific substitute machine or fabricated content.

For platform substitutions use vanilla then Fabric API, then the pinned read-only patched Minecraft/Kilt/Connector evidence. Small permanent Mekanism-specific adapters for actual lifecycle, transactions and packet ownership are allowed; preserve EMPTY/null, units, side/context, simulate/execute/rollback and codec order. No general Forge translation layer, net.neoforged shims, duplicated sources, Kotlin or unreviewed allowlist flooding.

## Checkpoint and unresolved risks

This is a large connected owner closure. At about 10–15 minutes return either a concrete GO with the next bounded implementation step and established real owners, or the exact blocking API/call path and proposed resolution for parent adjudication. Do not spend the checkpoint performing a global candidate hunt or rebuilding recursive dependency scanners. Compiler errors identify necessary direct dependencies but source counts alone never justify a blocker. If a new semantic conflict requires deleting public behavior, fake/no-op owners, or unrelated catalog implementation, preserve progress and ask the parent; do not silently split off a leaf batch or call an incomplete tile complete.

Important unsettled items: server lifecycle ordering for frequency persistence/reset; full data-component catalog initialization and executable domain dependencies; container packet/security integration; third-party computer integrations and registry/bounding dependencies. These require source-grounded decisions, not assumptions that their dependencies are harmless constants. The selected route is the concrete owner closure; the checkpoint is not permission to compare unrelated alternative projects.

## Verification and safety

Single implementer in the clean isolated worktree: this session selects the route, implements, and verifies; no sub-agent dispatch and no model switching. Preserve and report progress if a hard blocker appears. No production, mirror, deployment, push, indexing, broad directory scans or reference-tree writes. Search precise task directories with rg/fd; never scan ~, /, ~/server or broad parents.

Completion-first ordinary gate: assemble with existing slice/platform guards, plus a small selection of existing focused checks for the changed risky boundary. No default full suite, clean release, client/server fixtures, packaged smoke or new test infrastructure. Tests are not a reason to defer connecting the real owners. Parent reviews actual diff and evidence before acceptance. A genuine usable-machine milestone, if reached, needs an actual end-to-end check before a functionality claim; do not claim one merely because the owner compiles.

## Status

Parent route selected; implementation/checkpoint pending. No Task052 production admission or validation result yet.
