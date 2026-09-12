# Fabric item inventory boundary

## Task 033 scope

Task 033 is the first deliberate P1 item owner/platform gateway. It landed at API **56** / common **122** / platform **16**; Task 034 leaves all seven item production sources unchanged and brings the current global slices to **57/122/17**. Task 033 production scope is exactly seven sources:

- adapted API contracts:
  - `mekanism.api.inventory.IInventorySlot`
  - `mekanism.api.inventory.ISidedItemHandler`
  - `mekanism.api.inventory.IMekanismInventory`
- unchanged official holder contracts:
  - `mekanism.common.capabilities.holder.slot.IInventorySlotHolder`
  - `mekanism.common.capabilities.holder.slot.InventorySlotHolder`
- project-owned Fabric boundary:
  - `mekanism.fabric.item.FabricItemStorage`
  - `mekanism.fabric.item.FabricItemLookup`

`IMekanismInventory` and both holder sources remain byte-identical to official oracle `4646f509`. This boundary does not add a speculative cache class, an external Fabric-to-Mekanism reverse adapter, or an `ItemStorage.SIDED` provider.

## API adaptations

`IInventorySlot` now extends the existing registry-aware `mekanism.api.INBTSerializable<CompoundTag>` instead of NeoForge's nominal serialization interface. Its public methods and behavior are otherwise retained. NeoForge item/fluid imports used only by Javadoc are removed and the two stale comparisons are expressed in platform-neutral prose.

The broad `SerializerHelper` remains deliberately unadmitted. Task 034 adds the bounded Mekanism-owned `FluidStack` value, but does not migrate `SerializerHelper` or alter this Task 033 implementation. The existing private serialization call remains replaced by a private interface method with a method-local codec. That codec exactly mirrors official `SerializerHelper.OVERSIZED_ITEM_CODEC`:

- `ItemStack.ITEM_NON_AIR_CODEC` under `id`;
- `ExtraCodecs.POSITIVE_INT` under `count`, defaulting to one;
- optional `DataComponentPatch.CODEC` under `components`, defaulting to the empty patch;
- registry-aware `provider.createSerializationContext(NbtOps.INSTANCE)`;
- exact `IllegalStateException("Cannot encode empty ItemStack")` before encoding.

No public or nested helper type is introduced. Counts above an item's vanilla maximum and exact data-component patches remain serializable.

`ISidedItemHandler` no longer extends NeoForge `IItemHandlerModifiable`. The import, nominal parent and now-invalid `@Override` annotations are removed, but every Mekanism-owned unsided compatibility method and every sided method remain with the same signatures and bodies. The unsided boolean bridge still maps exactly through `Action.get(!simulate)`, and `getInventorySideFor()` remains nullable for internal access. Addons that assigned this interface to the NeoForge parent type lose that nominal subtype on Fabric; no shim is provided.

`IMekanismInventory` remains oracle-identical. Invalid indexes retain their no-op/empty/zero/original-remainder defaults; nullable side routing still chooses `AutomationType.handler(side)`; list results remain live; and an implementation returning `hasInventory() == false` remains responsible for exposing an empty list.

## Official holder behavior

`InventorySlotHolder` retains a distinct internal and physical-side contract:

- null/internal access returns the insertion-ordered master list;
- physical access uses the current facing to map a `RelativeSide` and returns the corresponding live list;
- slots without side declarations remain internal-only once any directional mapping exists;
- insert and extract predicates are independent and are consulted against the current facing;
- null is always denied by this holder's physical insert/extract policy, even when a predicate is absent;
- object identity, order, duplicate side mappings, mutable populated lists and immutable missing-side lists remain official behavior.

This differs intentionally from the external Fabric seam, which never exposes a null side.

## Physical-side exposure and lifecycle

`FabricItemLookup.expose` is the sole construction seam. It returns null for a null handler, null/internal side, `hasInventory() == false`, or a physical side with zero exposed slots. It performs no global lookup registration.

A successful `FabricItemStorage` captures the exact `IInventorySlot` object identities and order from one physical side and creates one stable `SingleSlotStorage<ItemVariant>` per captured slot. Every operation checks the live handler list against that captured topology by size, identity and order. Drift, or a later false `hasInventory`, permanently invalidates the storage. The concrete storage also exposes `invalidate()` for its future owner. Once invalid, aggregate and retained single-slot views fail closed: support is false, transfers return zero, resources are blank, and amount/capacity are zero. Iteration and `getSlots()` continue returning the same stable view objects without exposing a mutable `ItemStack`.

A future owner must cache **exactly one** storage per physical side and invalidate it on removal, reload, facing change, side-configuration change, or known topology change. It must also notify neighboring consumers when live support policy changes. The cache/provider owner and all invalidation wiring are deliberately deferred.

The optional `IHolder` is retained and its exact physical-side insertion/extraction policy is consulted live for support and before/during transfers. A blank `ItemVariant` or zero amount maps to no item transfer (`ItemStack.EMPTY` semantics). Nonblank variants are converted with `ItemVariant.toStack(int)`, preserving item and exact data components; backing and caller stacks are never handed out or mutated. Empty-view capacity calls the official `IInventorySlot.getLimit(ItemStack.EMPTY)` path. Negative amounts fail through Fabric `StoragePreconditions.notNegative`.

## Transaction protocol

Fabric requests use `long`, while Mekanism slot methods use `int`. Each per-slot call is capped at `Integer.MAX_VALUE`; the deterministic captured-order distribution aggregates per-slot results with checked, saturating arithmetic and never narrows the original request. A single call can therefore transfer totals above `Integer.MAX_VALUE` across multiple captured slots.

For insertion or extraction:

1. check topology and live holder policy;
2. run a deterministic captured-order plan with `Action.SIMULATE`; every backing `IInventorySlot` implementation is required by its API contract to avoid mutation and notification in this mode. The adapter relies on that precondition, takes no snapshot around simulation, and therefore cannot prevent or roll back simulation side effects from a violating implementation;
3. open a nested Fabric transaction under the caller;
4. recheck topology/policy and enlist one `SnapshotParticipant` snapshot containing defensive copies of **all** captured slot stacks;
5. execute exactly the planned amount for each participating slot with `Action.EXECUTE`, rechecking topology and policy during the operation;
6. require each result, the aggregate total, and complete post-operation read-back to match the simulated item, components and count exactly;
7. commit the nested transaction only after every check succeeds.

A mismatch leaves the child uncommitted and restores every captured slot. A later outer abort likewise restores the pre-operation snapshot, including nested commits. Restore always attempts `setStack` on every captured slot, passing a fresh defensive copy, and then reads every slot back for exact item/component/count equality. Setter failures, getter failures and silent read-back mismatches are accumulated; after all attempts, a hard failure is propagated. If a backing slot cannot restore exactly, atomicity is unavailable and some mutation may survive.

Mekanism listeners, dirty marks, saves or synchronization triggered immediately by `EXECUTE` or by restoration are inherently nontransactional. Tests deliberately observe those notifications across rollback; this adapter does not falsely hide or claim to undo them. Thread ownership and concurrency are not added by this slice.

## Verification boundary

Exactly two Fabric-local classes contain exactly eight meaningful JUnit methods:

- `InventoryApiHolderTest` (four): oversized component-bearing NBT and empty failure; slot defaults/caller immutability/limits/signed grow-shrink; unsided/sided routing, boolean simulation mapping, invalid indexes and automation; holder facing, null-vs-physical policy and live list identity.
- `FabricItemStorageTest` (four): exact `ItemVariant` components, blank/zero/capacity and invalid exposure; stable ordered views plus multi-slot true-long transfer; nested commit/outer abort, mismatch/full-copy restore, observable notifications and all-attempt restore failure; live policy, explicit invalidation and topology drift before/during execution.

At Task 033 landing, ordinary `test` was 63 suites / 296 tests and the isolated item task was one suite / four tests. Task 034 keeps `fabric-loader-junit` off the ordinary runtime and retains all three `DistributionPropertyTest` properties at 100,000 tries/checks each. It generalizes the old `fabricItemTest` source set/task to `fabricTransferTest`: the unchanged `FabricItemStorageTest` and new fluid conversion suite now share one pinned loader-JUnit runtime and one full Knot startup. Current totals are ordinary 64 suites / 304 tests, transfer 2 suites / 8 tests, aggregate 66 suites / 312 tests. `check` runs each task exactly once. Item tests still use only standard vanilla bootstrap, do not mutate registries, and add no startup marker.

The remapped-JAR static gate requires exactly the three direct inventory API classes, exactly the two direct holder/slot classes, and exactly these platform entries, each once:

- `FabricItemLookup.class`
- `FabricItemStorage.class`
- `FabricItemStorage$SlotView.class`

The direct inventory API check does not consume or forbid already-admitted subpackages. Every unexpected item-boundary nested/synthetic/test/helper/fixture class is rejected. There is no item startup/provider proof.

## Deliberately deferred

The following remain outside Task 033:

- every concrete Mekanism inventory slot and `IInsertableSlot` implementation;
- external Fabric-to-Mekanism reverse lookup/adapter;
- actual `ItemStorage.SIDED` provider registration, owner cache and invalidation wiring;
- side configuration, ejector, configurable holder/helper and proxy slot descriptors;
- persistence attachment/component owner and load/save lifecycle;
- `InputHelper`, `OutputHelper`, `CachedRecipe`, recipes and upgrades;
- `TileEntityMekanism`, every machine including Crusher, menus and player inventory;
- fluid storage/transaction interoperability beyond Task 034's separate pure value/unit seam, plus chemical and item-container interoperability beyond this block-side seam;
- networking/client synchronization and thread-safety guarantees.
