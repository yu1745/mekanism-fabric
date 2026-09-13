# Concrete machine-owner admission checkpoint (after Task050)

Baseline: `c433ace0` (Task050 accepted; 205/224/45). This records the initial source checkpoint, **not** Task051 implementation or a compile-success claim. Subsequent parent decision is in `TASK051_PLAN.md`: verified Fabric per-find provider behavior removes the false need for world/bounding cache-notification coupling; admit the complete real cache/base lifecycle with a permanently bound real-component policy callback, not a cache-only or fake-config batch.

## What was read

Parent read all of `TileEntityMekanism.java` (constructor through final computer methods), `CapabilityTileEntity.java`, `CapabilityCache.java`, `TileComponentConfig.java`, `TransmissionType.java`, `AttachedSideConfig.java`, and `ITileComponent.java`. `ContainerType.java` was read through its registration and handler-creation section (lines 1–230 only); remaining methods were not audited. Registration/bounding dependencies below were also identified by imports and previously documented owner inspection, not a fresh recursive scan.

## Concrete ownership, not a file-count blocker

- `TileEntityMekanism` constructor creates all five actual holder managers, then **unconditionally** constructs `TileComponentFrequency`; upgrade/security components are conditional on block attributes. `addComponent` both owns the lifecycle list and registers real side config with the capability cache.
- Its server tick runs frequency/upgrade/chunkloader components, recipe subclass work, radiation, heat, comparator and redstone bookkeeping. Load/save and implicit item components call every supported `ContainerType`; GUI tracking calls `MekanismContainer`, `SyncMapper`, and typed syncables. Removing the non-Crusher branches would change the public base class behavior.
- `TileComponentConfig` requires real registration, facing, packet dispatch, save marking, loaded state, five resource invalidation calls and security validation from its tile. It also owns component persistence (`AttachedSideConfig`/registered `SIDE_CONFIG`), GUI eject-state tracking via the nested `MekanismContainer.ISpecificContainerTracker`, and computer-access checks. A cache predicate alone does not port this complete component.
- `CapabilityCache` only calls `TileComponentConfig.isCapabilityDisabled`, but `CapabilityTileEntity.addConfigComponent` receives the actual component and `TileEntityMekanism.addComponent` owns its registration. Replacing that field with a callback could preserve cache behavior; it would **not** independently satisfy the real component/tile admission obligation. No such callback/interface has been added.
- `CapabilityTileEntity.invalidateCapabilitiesFull` revokes resolver views before world invalidation and visits every bounding position through `AttributeHasBounding`. Bounding attributes also place/remove real bounding blocks and synchronize their master entity; those operations cannot be deleted merely to admit this class. Fabric cache invalidation semantics are being investigated separately from this dependency graph.
- `TransmissionType` is not only a codec enum: two public `checkTransmissionType` overloads take `Transmitter`/`TileEntityTransmitter`; the actual caller is `TransporterUtils`. Deleting those overloads to admit side-config persistence would truncate public behavior.
- `AttachedSideConfig.getStoredConfigInfo` uses the actual registered `MekanismDataComponents.SIDE_CONFIG`. `ContainerType` static five-domain descriptors bind real tile getters/apply/collect methods, registered attachment components and item capability providers. These are ownership dependencies, not optional imports or harmless constants.

## Boundary not yet authorized or implemented

A permanent, fully behavior-preserving owner-contract extraction could decouple component state/persistence from the monolithic tile while retaining concrete implementations in the original tile and consumers. It would need an explicit design for tile lifecycle/security, GUI tracking, transmission ownership, and data-component registration; merely inventing interfaces with test-only implementations is a synthetic seam and is not acceptable. An isolated predicate-only cache batch or another unrelated leaf batch does not resolve this boundary.

The alternative is to admit the concrete existing ownership closure, including its actual frequency/security/upgrade/persistence/GUI and bounding obligations. No number of files is a reason to reject it, but a compiling module obtained by fake/no-op implementations is not acceptable. A next implementation dispatch must choose and specify this ownership design; do not hand an implementer an open-ended candidate hunt disguised as a machine implementation task.

No production changes, new interfaces, provider registrations, runtime gates or recursive closure scanner were introduced at this checkpoint.

## After Task051 acceptance — remaining concrete owners

The initial boundary discussion above is historical: Task051 accepted `CapabilityCache`/`CapabilityTileEntity` with the permanent actual-component predicate binding and real Fabric BE lifecycle. See `TASK051_PLAN.md` for implementation and correction `2c65edb8` (Fabric already forwards chunk/world unload into BE unload). It does not admit the following owners.

Additional parent source reads: complete `lib/frequency/TileComponentFrequency.java`, `FrequencyType.java`, `FrequencyManager.java`, and `inventory/container/MekanismContainer.java`.

- The actual frequency component owns security plus custom frequency selection, periodic validation and revocation, component/config-card persistence with permissions, real registered `FrequencyAware` components, and container synchronization. Even a machine without custom frequencies still constructs this owner; dropping frequency behavior from the shared tile would not be a complete port.
- `FrequencyType` statically registers real TELEPORTER/INVENTORY/SECURITY/QIO codecs, constructors and manager wrappers. They are executable domain dependencies, not string constants that can be replaced by inert descriptors.
- `FrequencyManager` owns global load/tick/reset, overworld `MekanismSavedData`, and trusted-player cross-manager frequency views. Server lifetime and persistence must be real; a test-only server holder or in-memory substitute would not complete this owner.
- `MekanismContainer` owns the ordered tracked-data list, dynamic tracking offsets, initial/dirty property packets, typed property application (including frequency/list/collection), per-player window selection and shift-click behavior. Most slot prerequisites are already admitted. Removing frequency/list property branches, packets or security semantics to compile this shared container would truncate its public behavior.

These are source-grounded integration obligations, not file-count limits or an authorization for a fake/no-op owner. No Task052 implementation boundary is selected by this checkpoint.
