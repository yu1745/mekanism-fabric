# Fabric relative-side energy, heat, item, and Chemical holders

> 状态核实（2026-10-30 / W-05）：保留对应早期切片的语义设计与验收记录；文中 “current”、slice 数量及延期调用方仅指当时范围，不代表 Task052 后的纳入状态。当前状态与后续验收以 `milestones/README.md` 和 `milestones/ROADMAP.md` 为准。

## Current slice

This milestone adds the platform-neutral side-routing layer used by Mekanism's internal holders:

- `mekanism.api.RelativeSide`
- `mekanism.api.text.IHasTranslationKey`
- `mekanism.common.capabilities.holder.IHolder`
- `mekanism.common.capabilities.holder.BasicHolder`
- `mekanism.common.capabilities.holder.energy.IEnergyContainerHolder`
- `mekanism.common.capabilities.holder.energy.EnergyContainerHolder`
- `mekanism.common.capabilities.holder.heat.IHeatCapacitorHolder`
- `mekanism.common.capabilities.holder.heat.HeatCapacitorHolder`
- `mekanism.common.capabilities.holder.slot.IInventorySlotHolder`
- `mekanism.common.capabilities.holder.slot.InventorySlotHolder`

It builds on the internal energy and heat containers. Task 023 adds a side-bound Team Reborn Energy adapter/factory seam, Task 033 adds a physical-side Fabric item view, and Task 040 adds the official internal Chemical tank/handler/holder closure. No block/item/entity Chemical provider is registered. Task 040 candidate current counts are API **98** / common **164** / platform **33**.

## Relative-side contract

`RelativeSide` retains its exact FQN and nested consumers continue to use the same enum identity. The following official contracts are preserved:

- constant order: `FRONT`, `LEFT`, `RIGHT`, `BACK`, `TOP`, `BOTTOM`;
- lowercase serialized names;
- `CODEC`, ordinal `STREAM_CODEC`, and wrapping `BY_ID` behavior;
- all horizontal and vertical `getDirection`/`fromDirections` mappings;
- translation keys `side.mekanism.<name>`;
- `IHasEnumNameTranslationKey` nominal identity and `getTranslatedName()` method.

Task 036 removes the temporary local `Util`/`ResourceLocation`/string-key construction and restores the oracle representation: every enum constant stores its corresponding `APILang` as an `ILangEntry`, and `getTranslationKey()` delegates to that entry. `APILang` itself uses the bounded stable literal namespace `"mekanism"`, so this restoration does not initialize the unfinished `MekanismAPI` facade. The resulting six keys are unchanged, and the returned key object is the entry's own key.

`IHasEnumNameTranslationKey` still does not extend NeoForge `TranslatableEnum`. That removed subtype relationship and adjusted Javadoc remain the approved Fabric API boundary. Task 036 restores only its default body to `TextComponentUtil.translate(getTranslationKey())`; the public method and returned fresh translatable component behavior are unchanged. See `API_DELTAS.md` and `FABRIC_TEXT.md`.

## Holder behavior

`BasicHolder` preserves the official two-view model:

- one insertion-ordered master list for internal (`side == null`) access;
- an `EnumMap<RelativeSide, List<T>>` for external side visibility;
- if no side mapping exists anywhere, all physical sides expose the master list without consulting the facing supplier;
- once any mapping exists, external access is filtered at lookup time using the current facing;
- an entry added without sides then remains internal-only;
- shared entries retain object identity and insertion order;
- duplicate side declarations remain duplicate entries;
- populated lists remain live mutable lists, while absent mappings return the immutable empty list.

`IHolder`/`IEnergyContainerHolder` retain unrestricted default `canInsert` and `canExtract` behavior. `HeatCapacitorHolder` applies the same official routing model without adding energy-style permission methods. `InventorySlotHolder` remains byte-identical and adds independent nullable predicates: physical sides are mapped against the live facing, while null/internal insert and extract are always denied. Its `getInventorySlots` returns the same official internal/directional live lists and exact slot identities. Configurable holders and helpers remain separate.

## Slot descriptor closure

The adjacent platform-neutral descriptors `ISlotInfo`, `BaseSlotInfo`, `EnergySlotInfo`, and `HeatSlotInfo` are admitted byte-identically to `4646f509`. They preserve constructor flags and exact list references; enablement is exactly input OR output. Energy emptiness scans in source order and stops at the first non-empty container, while later list membership and contained state remain live. Heat descriptors always report non-empty and do not inspect capacitor heat state. Five focused JUnit methods bind those contracts, and the remapped JAR package is exactly the four corresponding classes, once each, without nested/synthetic/test/helper/fixture leakage or a startup marker.

## Task 023 physical-side exposure seam

`FabricEnergyLookup.expose` accepts only a non-null physical `Direction`, rejects disabled/invalid conversions, handlers with `canHandleEnergy() == false`, and sides with no containers, then binds and records the exact side through `ProxyStrictEnergyHandler`. A later wrap request from any other side returns null regardless of conversion; same-side identical-conversion lookup unwraps the stable handler, while another usable conversion may remain wrapped. The optional `IHolder` is retained both by the proxy and by live support suppliers, so `supportsInsertion`/`supportsExtraction` and operations observe current exact-side policy rather than a stale snapshot. The resulting `FabricEnergyStorage` is stable: its owner must cache one adapter per handler/physical side, invalidate it with the future owner lifecycle, and notify neighboring capability consumers whenever live support changes. A count change caused during execute first aborts and restores the captured topology; later mutation and `stableHandler()` access fail fast, while support queries return false and amount/capacity return zero on drift.

This is a factory seam, not provider registration; owner invalidation and neighbor-notification wiring remain deferred. Null/internal-side access is never exposed. Every backing container must support deterministic `setEnergy` rollback (`FabricStrictEnergyHandler` is rejected because its setter is intentionally a no-op); every set attempt is read back, and setter/read exceptions or mismatches such as capacity clamping propagate after all captured indices are attempted. Any such restore failure makes atomicity unavailable. `ConfigHolder`, `ConfigInfo`, `DataType`, `ISideConfiguration`, configurable holders/helpers, `TileComponentConfig`, and `TileComponentEjector` remain deferred.

## Task 033 item-holder and exposure seam

The two item-holder sources are unchanged from `4646f509`; tests fix live facing rotation, internal versus physical list identity, independent policies and null denial. `FabricItemLookup.expose` optionally composes that live `IHolder` policy with the captured physical-side inventory. It never exposes null/internal access. Unlike the energy adapter, the concrete `FabricItemStorage` has explicit permanent `invalidate()` and also self-invalidates on slot identity/order drift or `hasInventory() == false`. Future ownership must cache one view per side and wire removal/reload/facing/side-config/topology invalidation. Full item transaction and restore semantics are in `FABRIC_ITEM.md`.

## Task 040 internal Chemical tank/handler closure

Task 040 admits byte-identical `BasicChemicalTank`, `ChemicalUtils`, `IMekanismChemicalHandler`,
`DynamicChemicalHandler`, `IChemicalTankHolder`, `ChemicalTankHolder`, `ProxyChemicalHandler`, and
`ChemicalSlotInfo`; adapted API interfaces retain the official domain methods while removing only
NeoForge fluid/capability documentation/supertypes that have no Fabric equivalent. The registry,
`Chemical`, `ChemicalStack`, attributes and predicates complete their dependency boundary.

`BasicChemicalTank` keeps capacity, validator, insert/extract rate predicates, listener, exact
`AutomationType`/`Action`, deprecated-vs-modern predicate singleton detection, type lock, empty
canonicalization and true-long saturation behavior. Tank NBT is the selected registry-aware project
contract; `ChemicalStack` stream order is amount then holder and does not use fluid's factor 81.
Dynamic/sided/proxy handlers preserve indexed order, side forwarding and distribution behavior.
`ChemicalTankHolder` is the non-config live-list holder matching the existing holder pattern;
`ChemicalSlotInfo` retains canInput/canOutput and live tank list identity.

Chemical validation is context-sensitive rather than copied into holders: a default tank rejects
radioactive nuclear waste at epoch 1, then accepts it after the authoritative radioactivity map is
removed at epoch 2. This behavior, exact `(1L << 60) + 123` NBT/stream round trips, listener/action
boundaries and server/client context isolation are covered by six ordinary domain tests, six
transfer-Knot codec/tank tests, three data-map-Knot tests and the real Chemical runtime gate.

This remains an **internal** Mekanism domain. There is no `ChemicalApiLookup`, Fabric
`Storage<ChemicalVariant>`, provider registration, snapshot rollback adapter, owner cache/invalidation,
configurable side holder/ejector, block/item capability surface, or machine caller. See
`FABRIC_CHEMICAL.md`.

## Deliberately deferred

This slice excludes:

- `EnergyContainerHelper`, `HeatCapacitorHelper`, and all other configurable holder/helper paths;
- `ConfigHolder`, `ConfigEnergyContainerHolder`, and `ConfigHeatCapacitorHolder`;
- `ConfigInfo`, `DataType`, `TileComponentConfig`, `TileComponentEjector`, and side-configuration execution/ejection;
- configurable chemical/energy/heat holders, fluid holders, inventory proxy descriptors, and helper builders;
- actual block/item/entity provider registration, owner caching/invalidation wiring, every tile/machine/block caller, and specialized holders;
- item reverse adapters plus external fluid/Chemical/heat transaction adapters, side ejection and persistence owners. Task 040's internal Chemical registry/NBT/snapshot lifecycle is included, but it is not an external storage contract.

The energy and heat helpers currently couple their non-config and configurable factories. They must be split or admitted with their real side-configuration closure rather than stubbed.

## Verification

- `fabric/src/test/java/mekanism/api/RelativeSideTest.java`
  - exact order, names, translations, `BY_ID`, data/stream codecs;
  - one fixed 6×6 projection table covering every horizontal and vertical facing;
  - all 36 relative/physical round trips against that table.
- `fabric/src/test/java/mekanism/api/text/PortableTextColorTest.java`
  - its RelativeSide method checks all six exact APILang key-object delegations, serialized identities, and fresh translation ownership;
  - its other four methods bind the connected incremental/text/color closure without changing this holder suite.
- `fabric/src/test/java/mekanism/common/capabilities/holder/energy/EnergyContainerHolderTest.java`
  - unconfigured all-side behavior without facing evaluation;
  - internal ordering and external filtering;
  - live facing rotation;
  - internal-only entries after side configuration;
  - identity, duplicate mapping, mutable live-list and immutable empty-list behavior;
  - unrestricted default permissions.
- `fabric/src/test/java/mekanism/common/capabilities/holder/heat/HeatCapacitorHolderTest.java`
  - exercises representative horizontal and vertical routing for the official heat-holder specialization;
  - verifies internal/all-side identity, filtering, live facing, order, duplicates, and list mutability.
- `fabric/src/test/java/mekanism/common/capabilities/holder/slot/InventoryApiHolderTest.java`
  - its holder method covers live facing, internal/physical lists, exact identity and independent null-denying policies;
  - its other three methods bind the admitted item API without adding a second holder-only suite.
- `fabric/src/test/java/mekanism/common/tile/component/config/slot/SlotInfoTest.java`
  - five methods cover all four flag combinations/OR enablement, energy varargs order and zero/all-empty behavior, exact source-order short-circuit, live list identity/membership/state, and heat's unconditional non-empty result without capacitor reads.

Required gates:

```bash
./gradlew -p fabric test
./gradlew -p fabric verifyApiSliceNoNeoForge verifyCommonSliceNoNeoForge
./fabric-build.sh
FABRIC_SMOKE_STATIC_ONLY=1 scripts/fabric-packaged-smoke.sh
```

The packaged smoke verifies RelativeSide outer + `$1`, complete direct text, energy/heat holders, and the exact Chemical API/common holder/handler/slot families once each. The slot descriptor family now includes exact `ChemicalSlotInfo.class` beside `ISlotInfo`, `BaseSlotInfo`, `EnergySlotInfo`, and `HeatSlotInfo`; tests/helpers/fixtures remain forbidden. Task 040 Chemical behavior additionally has focused Knot and real server/client runtime evidence rather than a class-presence claim alone.
