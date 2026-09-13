# Task050 — Official machine configuration state module

## Boundary (parent-selected)

Admit the complete official configuration state module `mekanism.common.tile.component.config` through `common-slice`: `ConfigInfo`, `DataType`, `IPersistentConfigInfo`, and all eight `slot/*.java` (`ISlotInfo`, `BaseSlotInfo`, `EnergySlotInfo`, `HeatSlotInfo`, `InventorySlotInfo`, `ChemicalSlotInfo` already admitted; this task adds `FluidSlotInfo`, `IProxiedSlotInfo` with its five domain proxy subclasses and `ProxySlotInfoCreator`).

`ConfigInfo` is the real mutable state owner of relative-side data types, supported types, disabled sides, eject flags, and container→`DataType` mappings. This is **configuration state only** — not `TileComponentConfig`, not machine functionality, not provider invalidation (Task049 deferrals unchanged).

## Authorized delta (only production change)

`ConfigInfo` constructor: `EnumUtils.SIDES` → `RelativeSide.values()`. Verified upstream `EnumUtils.java:61` defines `public static final RelativeSide[] SIDES = RelativeSide.values();` — identical elements and order, so initialization semantics are unchanged. `EnumUtils` itself stays unadmitted. All other admitted files are byte-identical upstream sources.

Dependency check (all previously admitted): `RelativeSide`, `IIncrementalEnum`, `EnumColor`, `ILangEntry`, `IHasTranslationKey`, `NothingNullByDefault`, `IChemicalTank`, `IExtendedFluidTank`, `IInventorySlot`, `IEnergyContainer`, `IHeatCapacitor` (api-slice); `MekanismLang` (10 `SIDE_DATA_*` entries), `BasicInventorySlot`, `BasicFluidTank` (common-slice).

Immediate source consumers `TileComponentConfig` and `AttachedSideConfig` remain deferred and are not faked/truncated. No temporary owner interfaces, no fake tiles, no generic compatibility layer.

## Evidence

- `fabric/common-slice.txt`: +5 entries (219 → 224); `fabric/build.gradle` slice-count gate updated to 224 (205/224/45, gate not weakened).
- Focused tests: `fabric/src/test/java/mekanism/common/tile/component/config/ConfigInfoTest.java` — 3 tests: (1) side data-type state, lazy/incremental supported-type set, disabled-side policy, wrapping increment/decrement, eject flags; (2) concrete `IInventorySlot`/`IExtendedFluidTank` container mappings in insertion order, multi-type containers, fresh mutable empty lists for unknown containers; (3) all five `IProxiedSlotInfo` proxies delegate to live suppliers plus `DataType` wire-format round-trips (STREAM_CODEC id, JsonOps serialized-name codec, WRAP `BY_ID`, `canOutput`).
- `./gradlew -p fabric assemble test --tests mekanism.common.tile.component.config.ConfigInfoTest --console=plain` — results recorded below after run.
- Not run per completion-first policy: full suites, packaged smoke, clean release, runtime fixtures.

## Results

Implemented in isolated worktree from clean `97f1e3d6`. Production delta is exactly the authorized `ConfigInfo` constructor change (with an explanatory comment); the other four admitted files are byte-identical upstream sources. Slice gate now 205/224/45 (`fabric/build.gradle` updated, not weakened).

`./gradlew -p fabric assemble test --tests mekanism.common.tile.component.config.ConfigInfoTest --console=plain` → **BUILD SUCCESSFUL** (`assemble` incl. remapJar + slice no-NeoForge gates; ConfigInfoTest: **3 tests, 0 failures, 100%**). Test bootstrap uses the standard `SharedConstants.tryDetectVersion()`/`Bootstrap.bootStrap()` `@BeforeAll` (same as `HandlerManagerTest`) because `BasicInventorySlot`/`BasicFluidTank` class-init touches vanilla registries; no chemical registry fixture needed.

Parent acceptance: reviewed the complete six-file candidate diff `2969e8be`, newly admitted sources, and all three focused tests. Independently reran the same `assemble test --tests mekanism.common.tile.component.config.ConfigInfoTest --console=plain` command in the main checkout: **BUILD SUCCESSFUL in 15s**, XML **3 tests / 0 failures / 0 errors / 0 skipped** (`/tmp/task050-parent-final.log`). Only subsequent production edit clarifies the constructor comment (identical elements/order, not array identity); no executable change. Full/runtime/clean release gates were intentionally not run.

Remaining obligations (unchanged deferrals): `TileComponentConfig` and `AttachedSideConfig` are the immediate source consumers and stay unadmitted; `CapabilityCache`/provider registration/invalidation wiring belongs to the Task049 follow-up chain; no machine tile or GUI functionality is claimed.
