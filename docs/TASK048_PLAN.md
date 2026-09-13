# Task048 — machine fluid storage and transaction boundary

## Parent GO / scope

Baseline: bb95d8b0. Implement with **GLM5.3** (fulfills the user's next-implementer override, not a review). Completion-first policy applies: build and a few focused checks, no full-suite/runtime/release cycle.

The next complete prerequisite is the missing fluid storage pipeline, not another value-only leaf: official tank → sided domain handler/holder → Fabric transactional fluid view. `TileEntityMekanism` directly implements `IMekanismFluidHandler` and constructs its `FluidHandlerManager` from `getInitialFluidTanks`. The real tile/config/cache ownership remains a later integration obligation; this task must not claim a registered working machine or register synthetic block providers.

Concrete entries: API `IExtendedFluidHandler`, `ISidedFluidHandler`, `IMekanismFluidHandler`, `ExtendedFluidHandlerUtils`; common `BasicFluidTank`, `DynamicFluidHandler`, `holder/fluid/IFluidTankHolder`, `FluidTankHolder`. Existing `IExtendedFluidTank` and Mekanism `FluidStack` are already admitted. Include direct functional-interface dependencies only as needed. Add Fabric fluid storage/lookup alongside the existing item/energy boundaries, reusing `FabricFluidConversions`. VariableCapacityFluidTank and helpers tied to MultiblockData/config owners remain deferred; do not truncate them to compile.

## Required semantics / allowed changes

- Retain complete official domain methods, automation rules, same-fluid-before-empty distribution, component identity, capacities, simulation, listener and NBT behavior. Replace NeoForge FluidStack with the existing Mekanism value. Remove NeoForge-only inheritance/fill-drain glue, explicitly declare inherited domain queries, document deltas; do not create a fake Forge interface or change packages.
- Fluid amounts stay int millibuckets internally, long Fabric units externally (81 units/mB). Transfer requests are maxima: operate only whole mB, returning a multiple of81 no greater than requested. No fractional accumulation, rounding up, overflow or component loss. Conversion helpers keep their existing exact conversion contract.
- Fabric view uses real tanks owned by the supplied IMekanismFluidHandler; physical sides only. Respect holder visibility/insertion/extraction restrictions. Snapshot actual backing tanks for nested transactions, restore on abort even after view invalidation, share transaction ownership across side views of the same tank (follow existing item implementation rather than independent conflicting snapshots).
- Stable retained views/iterators must stop accessing the owner after explicit invalidation or topology replacement. Do not merely clear a lookup map. No provider registration until real CapabilityTileEntity/config owner is integrated.
- No broad capability-cache/config rewrite in this task, no fake tile/config owner, no reverse fluid adapter, no new generic platform registry. No production operations/push/reference writes/indexing/test deletion.

## Acceptance / reporting

Inspect actual diff and exact slice additions; compile/remap build plus about3–6 focused tests for the new transaction/unit boundary using existing test source sets. Reuse existing test infrastructure, do not build a new runtime fixture. Do not rerun full suite, packaged smoke or clean release. Record actual verified range and remaining machine-owner integration plainly. Report a checkpoint if a new semantic blocker requires changing this scope; do not silently choose another task. Update this plan and MACHINE_SPINE/FABRIC_PORT succinctly on completion.
