# Task 040 plan — complete Chemical registry, data-map, tank and synchronization vertical

Status: **approved for one isolated implementation candidate**

## Decision and baseline

- Baseline: clean `java-port` commit `1a65b0a8e92fdea5cf81fccac1e46e563d461ad5`.
- Official source oracle: `4646f509` (Mekanism `v1.21.1-10.7.19.85`).
- Starting slices: API/common/platform **65/145/24**; aggregate **75 suites / 353 tests**.
- User direction: prefer complete 15–40+ owner/dependency verticals; an indivisible registry/config/reload/network SCC may exceed 40 files rather than being split into fake leaf tasks.

Astra high planning run `b9123fc6-091a-432` (634.3s, 124.5k tokens, 82 tool calls, about `$4.20`) compared four paths to the first Crusher. The Chemical path is selected because recipes, configurable holders/ejection and machines all require the same canonical Chemical/stack/tank owners. Item-only `InputHelper`/`OneInputCachedRecipe`, item-only side configuration, or a placeholder Crusher would truncate real public behavior.

Parent review accepts the large vertical with one architectural correction: the existing `mekanism.fabric.datamaps.FabricDataMapType` cannot become a public API method type because API/common must not depend on a Fabric package. Move that descriptor into the API as `mekanism.api.datamaps.MekanismDataMapType`, add its optional synchronized codec there, update the loader, and remove the old platform class from the compiled slice. This keeps the final compiled-source total from the planning result while preserving layer direction.

Task040 is therefore one **68 touched-production-path / 61 net-new-compiled-source** task. Four internal phases are implementation checkpoints only; acceptance is unified: a real registered radioactive chemical changes real tank admission after authoritative policy/data reload, round-trips a true-long stack exactly, and presents matching isolated client attributes/tooltips.

## Exact production scope

`U` = newly admitted official source with no semantic source delta; `A` = newly admitted official source with the bounded adaptation below; `N` = new Mekanism/Fabric source; `M` = existing compiled source modified; `R` = existing platform descriptor relocated to API and removed from the platform slice.

### API — 33 additions

1. U `src/api/java/mekanism/api/chemical/BasicChemicalTank.java`
2. A `src/api/java/mekanism/api/chemical/Chemical.java`
3. A `src/api/java/mekanism/api/chemical/ChemicalBuilder.java`
4. A `src/api/java/mekanism/api/chemical/ChemicalStack.java`
5. U `src/api/java/mekanism/api/chemical/ChemicalUtils.java`
6. A `src/api/java/mekanism/api/chemical/IChemicalHandler.java`
7. A `src/api/java/mekanism/api/chemical/IChemicalTank.java`
8. U `src/api/java/mekanism/api/chemical/IMekanismChemicalHandler.java`
9. A `src/api/java/mekanism/api/chemical/ISidedChemicalHandler.java`
10. U `src/api/java/mekanism/api/chemical/attribute/ChemicalAttribute.java`
11. U `src/api/java/mekanism/api/chemical/attribute/ChemicalAttributeValidator.java`
12. A `src/api/java/mekanism/api/chemical/attribute/ChemicalAttributes.java`
13. U `src/api/java/mekanism/api/chemical/attribute/IChemicalAttributeContainer.java`
14. A `src/api/java/mekanism/api/datamaps/chemical/ChemicalSolidTag.java`
15. A `src/api/java/mekanism/api/datamaps/chemical/attribute/ChemicalFuel.java`
16. A `src/api/java/mekanism/api/datamaps/chemical/attribute/ChemicalRadioactivity.java`
17. A `src/api/java/mekanism/api/datamaps/chemical/attribute/CooledCoolant.java`
18. A `src/api/java/mekanism/api/datamaps/chemical/attribute/HeatedCoolant.java`
19. U `src/api/java/mekanism/api/datamaps/chemical/attribute/IChemicalAttribute.java`
20. A `src/api/java/mekanism/api/datamaps/chemical/attribute/IChemicalCoolant.java`
21. A `src/api/java/mekanism/api/datamaps/IMekanismDataMapTypes.java`
22. N `src/api/java/mekanism/api/datamaps/MekanismDataMapType.java` (relocated/extended descriptor)
23. U `src/api/java/mekanism/api/providers/IBaseProvider.java`
24. A `src/api/java/mekanism/api/providers/IChemicalProvider.java`
25. A `src/api/java/mekanism/api/text/ITooltipHelper.java`
26. A `src/api/java/mekanism/api/MekanismAPITags.java`
27. U `src/api/java/mekanism/api/IDisableableEnum.java`
28. A `src/api/java/mekanism/api/functions/ConstantPredicates.java`
29. N `src/api/java/mekanism/api/functions/TriPredicate.java`
30. N `src/api/java/mekanism/api/chemical/ChemicalRegistry.java`
31. N `src/api/java/mekanism/api/chemical/ChemicalDataContext.java`
32. N `src/api/java/mekanism/api/radiation/RadiationValidation.java`
33. N `src/api/java/mekanism/api/MekanismServices.java`

### Common — 19 additions

1. U `src/main/java/mekanism/common/capabilities/chemical/DynamicChemicalHandler.java`
2. U `src/main/java/mekanism/common/capabilities/holder/chemical/IChemicalTankHolder.java`
3. U `src/main/java/mekanism/common/capabilities/holder/chemical/ChemicalTankHolder.java`
4. U `src/main/java/mekanism/common/capabilities/proxy/ProxyChemicalHandler.java`
5. U `src/main/java/mekanism/common/tile/component/config/slot/ChemicalSlotInfo.java`
6. U `src/main/java/mekanism/common/ChemicalConstants.java`
7. U `src/main/java/mekanism/common/base/IChemicalConstant.java`
8. A `src/main/java/mekanism/common/registration/impl/ChemicalDeferredRegister.java`
9. U `src/main/java/mekanism/common/registration/impl/DeferredChemical.java`
10. A `src/main/java/mekanism/common/registration/impl/SlurryRegistryObject.java`
11. A `src/main/java/mekanism/common/registration/DoubleWrappedRegistryObject.java`
12. A `src/main/java/mekanism/common/resource/PrimaryResource.java`
13. U `src/main/java/mekanism/common/resource/ResourceType.java`
14. A `src/main/java/mekanism/common/registries/MekanismChemicals.java`
15. A `src/main/java/mekanism/common/util/UnitDisplayUtils.java`
16. A `src/main/java/mekanism/common/util/text/TooltipHelper.java`
17. A `src/main/java/mekanism/common/config/CommonConfig.java`
18. A `src/main/java/mekanism/common/config/value/CachedEnumValue.java`
19. N `src/main/java/mekanism/common/config/ChemicalConfigAccess.java`

### Platform — 10 additions, five modifications, one relocation/removal

New:

1. `fabric/src/main/java/mekanism/fabric/chemical/FabricChemicalRegistryLifecycle.java`
2. `fabric/src/main/java/mekanism/fabric/chemical/FabricChemicalLifecycle.java`
3. `fabric/src/main/java/mekanism/fabric/chemical/FabricChemicalClientLifecycle.java`
4. `fabric/src/main/java/mekanism/fabric/datamaps/FabricChemicalDataMaps.java`
5. `fabric/src/main/java/mekanism/fabric/datamaps/FabricChemicalDataMapSession.java`
6. `fabric/src/main/java/mekanism/fabric/config/FabricChemicalConfig.java`
7. `fabric/src/main/java/mekanism/fabric/network/ChemicalSnapshotPayload.java`
8. `fabric/src/main/java/mekanism/fabric/network/ChemicalSnapshotAckPayload.java`
9. `fabric/src/main/java/mekanism/fabric/network/FabricChemicalNetworking.java`
10. `fabric/src/main/java/mekanism/fabric/network/FabricChemicalClientNetworking.java`

Modify:

11. `fabric/src/main/java/mekanism/fabric/MekanismFabricBootstrap.java`
12. `fabric/src/main/java/mekanism/fabric/MekanismFabricClientBootstrap.java`
13. `fabric/src/main/java/mekanism/fabric/config/MekanismConfigSpec.java`
14. `fabric/src/main/java/mekanism/fabric/config/FabricMekanismConfigManager.java`
15. `fabric/src/main/java/mekanism/fabric/datamaps/FabricMekanismDataMaps.java`

Relocate/remove from compiled platform slice:

16. `fabric/src/main/java/mekanism/fabric/datamaps/FabricDataMapType.java`; its layer-neutral contract becomes API `MekanismDataMapType`, and `FabricDataMapLoader` is updated in place to consume it. This source update is part of the touched path count even though `FabricDataMapLoader` remains one of the existing 24 platform sources.

Expected final slices: API/common/platform **98/164/33** (295 total compiled production sources). Any count change requires a documented dependency reason; do not silently widen source roots.

## Exact resources and services

Admit exactly these five generated data maps:

- `data/mekanism/data_maps/mekanism/chemical/chemical_solid_tag.json`
- `data/mekanism/data_maps/mekanism/chemical/chemical_attribute_fuel.json`
- `data/mekanism/data_maps/mekanism/chemical/chemical_attribute_radioactivity.json`
- `data/mekanism/data_maps/mekanism/chemical/chemical_attribute_cooled_coolant.json`
- `data/mekanism/data_maps/mekanism/chemical/chemical_attribute_heated_coolant.json`

Admit exactly these 13 generated chemical tags:

- `bio.json`, `carbon.json`, `clean.json`, `diamond.json`, `dirty.json`, `fungi.json`, `gaseous.json`, `gold.json`, `redstone.json`, `refined_obsidian.json`, `tin.json`, `waste_barrel_decay_blacklist.json`, `water_vapor.json` under `data/mekanism/tags/mekanism/chemical/`.

Admit only the two service descriptors `META-INF/services/mekanism.api.text.ITooltipHelper` and `META-INF/services/mekanism.api.datamaps.IMekanismDataMapTypes`. The latter must name the actual Fabric-backed complete implementation, not the uncompiled NeoForge owner. Do not add a broad `src/main/resources` or generated-data root.

## Ownership graph and internal phases

```text
common Fabric bootstrap
 ├─ chemical registry lifecycle
 │   └─ ChemicalRegistry → canonical empty + MekanismChemicals (64 non-empty entries)
 ├─ configuration lifecycle
 │   ├─ CommonConfig/common.toml (two units + three booleans)
 │   └─ sole general.toml fragment (radiation.enabled + FE rate/blacklist)
 │       └─ ChemicalConfigAccess → RadiationValidation + UnitDisplayUtils
 └─ server lifecycle
     └─ five-map atomic reload session → ChemicalDataContext(server)
         ├─ modern/legacy attributes
         ├─ complete IMekanismDataMapTypes service, including Task038 delegation
         └─ bounded configuration/play snapshot → ChemicalDataContext(client)

Chemical → ChemicalStack → BasicChemicalTank
                           → dynamic/sided/proxy handlers → ChemicalSlotInfo
attributes → complete ITooltipHelper → TooltipHelper → UnitDisplayUtils/config
```

1. **Registry/domain checkpoint:** construct/register the synchronized intrusive defaulted registry; register `mekanism:empty` and all 64 non-empty declarations; compile full stack/tank/handler domain.
2. **Service/config checkpoint:** complete service loading, tooltip methods, `common.toml`, and the narrowly owned `general.toml` server fragment. No fake `IRadiationManager`.
3. **Reload/context checkpoint:** load all five maps into one candidate epoch and atomically publish server context after full validation.
4. **Network/runtime checkpoint:** configuration ACK, play reload broadcast, disconnect/stop cleanup, same-process reconnect and integrated server/client isolation.

No checkpoint alone completes Task040. The production roots are the existing two bootstraps. An isolated runtime fixture may instantiate a tank; do not claim or add a machine.

## Authorized deltas and required semantics

### Registry, empty ownership and public services

- Do not admit a partial `MekanismAPI`; its complete surface reaches modules, Robit skins and ingredient serializers. Move chemical registry key/default holder ownership to `ChemicalRegistry`, and complete service loading to `MekanismServices`. Adapt admitted callers only. A future full `MekanismAPI` must alias these exact objects.
- Build on `FabricDefaultedRegistry` with `SYNCED`, intrusive holders and vanilla/Fabric freeze behavior. Initialization must occur on the construction thread before registry freeze. Register `mekanism:empty` once, then the official 32 named chemicals, 18 pigments and 14 slurries in official declaration order and IDs.
- Never assume matching server/client raw IDs in application snapshots. Fabric registry synchronization owns remapping; snapshot entries use resource names.
- Replace API uses of NeoForge `DataMapType` with layer-neutral `MekanismDataMapType`. It owns registry key, ID, persistent codec and optional synchronized codec/flag, preserves descriptor identity, and has no Fabric import.
- `MekanismServices` uses one complete, deterministic service-loader policy with explicit zero/multiple-provider failure. No no-op fallback.

### Domain compatibility

- Preserve all admitted public constructors, overloads, deprecated methods/nested types, validations and codec layouts except exact type substitutions documented here.
- `Chemical`/`ChemicalStack` use `ChemicalRegistry`; replace NeoForge holder-data injection with `ChemicalDataContext`/the complete data-map service. Preserve `updateFromDataMap` participation and protected attribute tracking rather than bypassing subclass behavior.
- `IChemicalTank` keeps the project registry-aware NBT contract. Remove only documentation/capability references that have no Fabric equivalent.
- `ConstantPredicates` uses the project `FluidStack` plus a complete Mekanism `TriPredicate` (`test`, short-circuit `and`/`or`, `negate`, null checks). Singleton identities are contractual because `BasicChemicalTank` compares them for deprecated/modern compatibility; do not replace them with fresh lambdas.
- `PrimaryResource` builds exact `c:ores/<resource>` item tags. `MekanismChemicals` uses local `values()` and literal namespace construction. `ChemicalDeferredRegister` may relocate only the private color-representation helper needed from unadmitted `ChemicalUtil`.
- Chemical amount is true-long Mekanism units, with no factor-81 fluid conversion. Preserve canonical `ChemicalStack.EMPTY`, `{}` optional NBT, positive/non-empty codecs, amount-before-holder stream order, direct/reference holder checks and exact listener/action behavior.
- Replace direct `MekanismAPI.logger` use with one exact-category logger owner; do not admit the root facade merely for logging or constants.

### Configuration and tooltip service

- `RadiationValidation` exposes only enabled policy and official baseline `1e-7`. It is deliberately not `IRadiationManager`; environmental radiation APIs remain absent.
- Adapt `CommonConfig` to the existing Fabric spec and own the complete official five values: energy unit, temperature unit, decay timers, copy block data and holidays. Add strict enum definition/encoding to `MekanismConfigSpec`; accept only declared names/values, correct before publication, and preserve defaults/metadata.
- One sole preserving `Mekanism/general.toml` fragment owns `radiation.enabled=true`, `energy_conversion.feConversionRate=2.5` in `0.0001..10000`, and world-restart `energy_conversion.blacklistForge=false`. On a server use its world `serverconfig/Mekanism/general.toml`; preserve all unowned TOML. Path claims remain normalized and exclusive. No external watcher, NeoForge config event or automatic template copy.
- Local common configuration is not overwritten by server sync. Only relevant server policy is published into the client context for connected behavior and reset on disconnect.
- `ChemicalConfigAccess` is the no-Fabric common seam for current units/rates/policy and controlled owner publication. It must fail or use explicitly documented official defaults before ownership; it must not silently construct a second config owner.
- `UnitDisplayUtils` removes `EnumUtils`, `MekanismConfig` and `TranslatableEnum` coupling while preserving complete enums, IDs/codecs, conversion, translation and formatting behavior. `TooltipHelper` implements all five methods and replaces only two `MekanismUtils` formatting calls through the real unit/config owner.

### Data maps and contexts

- Extend the Task038 loader to validate a catalog and load five descriptors without reporting sibling known descriptors as unknown. Reuse its low→high resource-stack order, replace/write/remove, tags, layer-level failure behavior and immutable results.
- Decode all five maps into a single candidate; validate registry references and values; publish one immutable epoch only after all maps succeed. A failed local publication retains the previous complete epoch.
- Preserve legacy-plus-modern merge, modern replacement/removal fallback, ore tag behavior, radioactivity and validation cache resets.
- `ChemicalDataContext` separates server identity/thread ownership from physical-client ownership. Mutable derived attribute caches must be context-local: publishing/clearing client data may not mutate the integrated server view of the same registered Chemical objects. Late queued work after stop/disconnect cannot revive either owner.
- The complete `IMekanismDataMapTypes` implementation handles direct/reference holders and delegates MekaSuit absorption to the existing Task038 server owner while serving all five chemical descriptors/context queries.

### Bounded synchronization

- Add configuration S2C snapshot and C2S ACK plus play S2C reload publication. Initial configuration requires receiver support and successful publication ACK before completing its task.
- Snapshot includes session identity, positive monotonic epoch, the five maps and relevant server policy. Keys and referenced chemicals use registry names. Use each descriptor’s declared network codec, including compact solid-tag and radioactivity codecs.
- Bound individual strings/collections, chunks, total entries and total decoded allocation. Reject malformed, unknown, duplicate, stale-session, nonpositive/regressing epoch and unresolved registry entries. Decode bounded transport values first; registry resolution and publication occur on the owning executor.
- Successful reload broadcasts a new epoch. Failed reload/publication retains prior state. Disconnect removes client state and restores local/default policy; server stop removes server state. Same-process reconnect gets a new session and cannot accept queued messages from the old session.
- Do not introduce a Fabric chemical provider or transaction contract. Internal `Action` simulation/execution and listeners remain official but are not advertised as rollback-safe.

## Tests, runtime proof and gates

Add five suites / **26 tests**, justified by six independent mutation surfaces rather than source count:

- ordinary domain: 6;
- ordinary service/config: 5;
- ordinary snapshot protocol: 5;
- transfer-Knot chemical codec/tank connections: 6;
- data-map-Knot owner integration: 3;
- registry-root integration: extend the existing suite by 1 method rather than adding a suite.

Expected totals: ordinary **73/352**, transfer **4/21**, registry-root **1/2**, data-map **2/4**, aggregate **80/379**. If practical isolation requires a distinct runtime source set, document the changed suite arithmetic instead of weakening isolation.

Required candidate gates:

1. source identity/delta report against `4646f509`; exact slices/resources/services and fixture isolation;
2. focused tests, full `check`, and ordinary Fabric build;
3. static remapped-JAR exact class/resource/service/leakage gate;
4. packaged dedicated-server startup because production root registry and server config/reload startup change;
5. new real chemical runtime gate with dedicated server and real clients: changed registration ordering/remap, initial attributes, reload/removal, unknown-entry rejection, exact true-long tank NBT/stream round trips, same-process reconnect, and integrated server/client context isolation;
6. existing paired/rejection networking and client-window regressions.

Unified acceptance must demonstrate a registered radioactive chemical whose tank admission changes after authoritative policy/data reload, exact long amount survives NBT and stream encoding, client attributes/tooltips match the authoritative snapshot, and client disconnect/reconnect cannot contaminate server context.

Candidate work must not run `./build.sh --clean`, push, deploy, access production, or use the mirror server. After integration, the parent independently reruns full gates, one packaged chemical runtime gate and one final clean release.

Mandatory independent read-only review before integration:

1. intrusive/defaulted startup, freeze/remap and canonical EMPTY cycles;
2. legacy/modern attribute merging and integrated context isolation;
3. config path ownership, enum semantics, publication and shutdown;
4. configuration/play ordering, bounded decoding, ACK/reload/disconnect races;
5. public surface, oracle deltas, resource/service and artifact isolation.

## Explicit deferrals and next boundary

Deferred: full `MekanismAPI`; modules/Robit registries; chemical ingredients; environmental radiation and `IRadiationManager`; external Chemical storage/provider/transactions; configurable holders/ejector; fluid tanks/handlers; machine/menu/block/item registration; Crusher.

After Task040, Task041 should take the complete recipe ingredient/input-handler/CachedRecipe/reload-cache vertical. It must no longer stop at Chemical registry, canonical EMPTY, data-map attributes, tooltip services or internal chemical tank behavior.
