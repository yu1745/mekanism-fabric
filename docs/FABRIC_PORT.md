# Mekanism 1.21.1 Fabric 移植基线

## Task050–052 后续索引（2026-10-30 / W-04）

下方早期任务叙事保留作历史证据；当前状态统一见 [里程碑表](milestones/README.md)，执行顺序见 [ROADMAP](milestones/ROADMAP.md)。已提交不等于已完成真机验收。

| 范围 | 后续结论 |
|---|---|
| P12（2026-10-30） | Generators W-50–58 原机器与三多方块、GUI／生命周期真机及 578 tests/build/API/machine 回归通过；独立四 JAR 构建，证据 `archive/M-generators.md`，完整发布验证留 P13。 |
| P7（2026-10-30） | W-36–39装备／模块factory、冻结注册身份与生成通过；修复MekaSuit实际armor tick与retrogen轮询，578 tests/API/machine门禁通过；证据 `archive/M-gear.md`、`archive/M-registry.md`，未宣布P8发布通过。 |
| P6（2026-10-30） | W-34–35五种核心多方块生命周期、尺寸失败与重启保持通过，build/API/machine阶段门禁通过；未改生产源，证据 `archive/M-multiblock.md`。 |
| P5（2026-10-30） | M-render W-29–33通过：模型／粒子／71个GUI／客户端注册和附件渲染已验证，578 tests、API与machine阶段门禁通过；证据 `archive/M-render.md`。 |
| P4（2026-10-30） | M-transfer W-23–28 通过：五种传输及网络卸载／重载、拆装、存档重启有实机证据，阶段门禁578 tests/API/machine全部通过；证据 `archive/M-transfer.md`。 |
| P3（2026-10-30） | M-platform W-15–22 与阶段门禁通过：命令、成就、条件、生命周期和 55 payload owners 已核对，46 个未纳入文件保留明确理由；证据 `archive/M-platform.md`。 |
| P2（2026-10-30） | M-config W-12–14 通过：非默认 miner consumer、两次独立客户端同步、开发／发布 schema、生命周期测试与阶段门禁；证据 `archive/M-config.md`。 |
| P1（2026-10-30） | M-owner W-06–11 真机与阶段门禁通过：Crusher 加工/存读档、工厂升级、频率落盘、bounding 放拆；证据归档 `archive/M-owner.md`，下一阶段 P2。 |
| P0（2026-10-30） | W-01–05 完成；578 tests、slice/build 与 machine/paired/packaged 全通过，修复既有测试快照与旧 packaged 合同；P1 真机验收待做，证据见 `archive/M-owner.md`。 |
| Task050–051 | `c433ace0` / `a4feef18` 完成真实配置状态、capability cache 与 base-tile 生命周期；见 `archive/TASK050_PLAN.md`、`archive/TASK051_PLAN.md`。 |
| M-owner（Task052） | 原始 tile、组件、频率、容器、有效侧与 bounding 已接通；P1 W-06–W-10 真机验收通过，详见 `archive/M-owner.md`。 |
| M-config（Task052） | gear、machine/tier、common/startup、general owner 均有运行证据；P2 非默认 consumer、独立客户端同步与 schema 验收通过。 |
| M-transfer（Task052） | `b0f7e13d` / `9cabfea9` 等接通真实机器能量和物品／流体／化学传输；旧 smoke 不代替 W-23–W-28 的生命周期验收。 |
| M-render（Task052） | GUI、模型加载与 BER 后续接线见 `archive/TASK052_RESUME_2026-10-26.md`；完整模型与 GUI 巡检待 W-29–W-33。 |
| M-registry（Task052） | 内容 catalog、配方、Robit、chemical ingredient 等已有后续接线与审查；注册冻结／alias／数据包一致性待 W-38。 |
| M-gear（Task052） | 模块、装备及 accessory 后续实现与运行证据见 Git 历史；整套工具／装备行为待 W-36–W-37，不沿用早期未纳入清单。 |

## 目标

以官方 Mekanism `v1.21.1-10.7.19.85` 为行为、API、数据和资源基线，将 NeoForge 平台接入替换为 Fabric 接入，同时尽量保持：

- `mekanism.api.*`、`mekanism.common.*`、`mekanism.client.*` 包名与公共签名；
- 注册 ID、NBT、配方、配置默认值和存档语义；
- 官方 holder/component、CachedRecipe、机器继承与组合关系；
- Core、Tools、Additions、Generators、Defense 的官方 source-set 边界。

目标环境固定为 Minecraft 1.21.1、Java 21。主线不再进行 Java→Kotlin 全量重写；核心和公共 API 使用 Java，确有价值的边缘工具也必须在不改变兼容契约的前提下引入。

## 基线与归档

- 官方 tag：`v1.21.1-10.7.19.85`
- 官方 commit：`a00109e4856fd38b9c5b3dd7f22ce4a59cd65a80`
- 本仓库 1.21.1 基线 commit：`4646f509ef21`（官方源码树的精确快照）
- 前置 1.20.1 基线 commit：`c684293f`（保留 1.20.1→1.21.1 官方变更，便于审计）
- 旧 Kotlin 重写归档分支：`archive/kotlin-spike`
- 旧 Kotlin 重写归档 tag：`kotlin-spike-final`
- 官方远端：`upstream` → `https://github.com/mekanism/Mekanism.git`

## 移植原则

1. 不翻译纯 Java 领域代码；只修改确实依赖 Forge 的边界。
2. Minecraft 映射优先使用 Mojang official mappings，避免把官方源码整体翻译成 Yarn 命名。
3. 平台差异集中到 Fabric adapter，不通过改写整个 namespace 隔离。
4. 每个官方类记录为：原样保留、Fabric adapter、有意修改、暂缓之一。
5. 先恢复可编译的 API 与基础设施，再恢复机器；禁止先复制具体机器。
6. 开发 classpath 启动不算发布成功，最终 JAR 必须在干净 Fabric Loader 环境验证。
7. 生产服务器不是测试环境；运行时验证使用本地开发服或镜像服。

## NeoForge / Fabric 对照参考

遇到没有明确一一对应关系的平台 API 或对象语义时，可查阅两个本地只读参考仓库：

- [`upstream/kilt`](../upstream/kilt)：[KiltMC/Kilt](https://github.com/KiltMC/Kilt)，主要展示如何在 Fabric 上承接 (Neo)Forge API 与对象语义；
- [`upstream/connector`](../upstream/connector)：[Sinytra/Connector](https://github.com/Sinytra/Connector)，从反方向展示 Fabric API 与 NeoForge 对象、生命周期和调用约定的转换；仓库内另有生产补丁分支，但本目录固定使用官方上游作移植参考。

使用顺序：先找 vanilla/Fabric 的直接能力，再对照 Kilt 和 Connector 如何保持双方语义，最后实现最小的 Mekanism 专用 adapter。它们是语义与边界案例库，**不是必须采用的依赖或设计模板**；本项目不实现通用 Forge/Fabric 转换层，也不批量复制它们的兼容架构。若采用具体实现代码，必须记录仓库、commit、源文件并遵守相应许可证；默认只参考行为和测试思路后独立实现。

具体相对路径、分支和搜索规则记录在仓库根部 `AGENTS.md`。

## 当前状态

> 下列早期 Task 条目中的“延期/未纳入”均记录其各自落地时边界；Task 040 已完成的 Chemical、service、config、data-map 与 networking 能力以本节 Task 040 条目和 `FABRIC_CHEMICAL.md` 为准。

- [x] 独立 Fabric Loom 构建：MC 1.21.1、Java 21、Mojang official mappings；
- [x] 最终 remapped JAR 的干净 Fabric 服务端冒烟门禁；
- [x] NeoForge 平台依赖矩阵与可重复扫描脚本；
- [~] API allowlist：Task 040 集成态当前编译 **98/267** 个 API 文件；在既有 registry-aware NBT、FluidStack、MekaSuit map、item/config/text contracts 上，新增完整 selected Chemical/stack/tank/handler/attribute/service closure。层中性 `MekanismDataMapType` 取代已删除的 platform descriptor；Chemical registry/context ownership 位于 `ChemicalRegistry`/`ChemicalDataContext`，不引入 partial `MekanismAPI` 或 Fabric API 依赖（见 `FABRIC_CHEMICAL.md`、`API_DELTAS.md`）；
- [~] lifecycle/registry：Task 037 foundation 现承载首个具体 owner：`mekanism:chemical` 是 MODDED+SYNCED intrusive/defaulted root，按官方顺序注册 canonical empty + 64 个非空条目并在构造线程预冻结；后续 reload-worker freeze 仅允许已经 FROZEN 的幂等返回。隔离 root worker、packaged startup 和 real server/client gate 对账 exact identity/order/name remap；其它自定义 registries 仍逐 owner 移植；
- [~] config：既有 startup 与 Task 039 client-window owner 之外，Task 040 启用完整五值 `common.toml` selected owner及每世界 preserving `serverconfig/Mekanism/general.toml` 三值 fragment（radiation enabled、strict FE rate、blacklistForge world-restart）；strict enum、exclusive normalized claim、publish/reset 与 local-common/server-policy separation 已门禁。完整 `GeneralConfig`/九配置 facade、其它 fields/UI/watcher/events 仍延期；
- [~] network adapter：强制 `10.7.19` negotiation 后，Task 040 新增 bounded name-based five-map/policy configuration snapshot + exact ACK task，以及 successful reload 的 play S2C epoch。descriptor codec、owner-executor resolution、unknown/duplicate/stale/regressing rejection、disconnect cleanup 和同一 physical client JVM reconnect 已有真实门禁；withheld-ACK timeout 与通用 gameplay dispatcher 仍延期（见 `FABRIC_NETWORK.md`）；
- [~] holder/storage：既有 energy/heat/item holder 与 item/fluid gateway 上，Task 040 纳入 official `BasicChemicalTank`、dynamic/sided/proxy handler、non-config chemical holder 和 `ChemicalSlotInfo`，保持 true-long、validation/listener/Action 与 registry-aware NBT/stream contracts。它是 internal domain，不注册外部 Chemical provider/lookup，也不承诺 Fabric transaction rollback；configurable holders/ejector、fluid tanks/handlers 与 machine owners 仍延期（见 `FABRIC_HOLDERS.md`、`FABRIC_CHEMICAL.md`）；
- [x] bounded common utilities：官方 `WildcardMatcher`、五个 fair-distribution 源文件、`ChunkUtils` 与 `LambdaMetaFactoryUtil` 原样纳入，并以两个 vanilla 等价替换纳入 `RegistryUtils`；Task 040 集成态当前总 common slice 为 **164** files（API **98**、platform **33**）。Wildcard 覆盖官方行为、`Locale.ROOT`、`TagKey` 与 BMP digit 边界；distribution 通过显式路径 allowlist 直接编译官方 root tests，保留 14 个 JUnit examples 与三个各 100,000 tries 的 jqwik properties，并另有 Fabric 边界测试覆盖 null/empty/zero、插入序 remainder、`int` 最大值和 true-`long` 最大值。`ChunkUtils` 保留四方向 inclusive 行主序与负坐标 floor-section 语义。Java 21 本地测试证明 `LambdaMetaFactoryUtil` 的空名称路径仅绑定调用者已开放的实例 `Field`，非空名称路径按精确签名绑定可能具有变换/副作用的 virtual getter/setter；覆盖八种 primitive 的装箱/拆箱、object/null、两实例重复调用、全部 named setter 分支与原异常传播。调用者负责 field accessibility/module openness、精确 `objType`/方法签名、instance field、boxed value 类型兼容及 primitive setter 非 null；static/final field 不在契约内。`RegistryUtils` 的 Fabric 测试证明 built-in canonical holder/key、NBT ID 拒绝、block path、reference/direct 与 key-only/value-only unbound holder 命名、registered/unknown raw object 与 direct-holder short circuit；正常 vanilla built-ins 下不可达的 `getBEHolder` null fallback 不通过全局 registry 变异强行覆盖。通用 official root-test oracle 为 18 个 baseline-identical 显式路径文件（新增两份 collection 官方测试）（19 JUnit examples + 三个各 100,000 tries 的 jqwik properties）。发布物门禁还精确限定 collection 包只能有 `BiLongMultimap`、`FilterTransformIterator`、`IndexedCuboidMap` 及其两个匿名类和 record 共六项；行为由官方与 Fabric-local Java 21 测试而非启动 marker 证明。三份 collection 源码保持 baseline 字节一致，覆盖双向 mutation、null filter/exhaustion、负 chunk/inclusive box、replacement 与混合清理。Miner/QIO/spatial caller、world/chunk lifecycle/loading、persistence、concurrency 与多线程 mutation，以及 `SyncMapper`、tracked data、机器均未移植或声明；
- [x] collection utility closure：`BiMultimap`、`EmptySequencedMap`、`HashList`、`LRU` 与官方 `4646f509` 字节一致；Java 21 黑盒测试仅固定双向索引、ordered-unique 转换、unique non-null LRU 顺序及 immutable empty sequenced-map 语义，JAR 精确为 12 个 collection class entries。security/frequency/filter/sync、QIO、`ModuleContainer`/gear/data components/codecs、GUI/recipe viewers/client lifecycle、networking/persistence/registries 及所有 callers/owners 均延期；不承诺线程安全、并发修改或通用 mutable-view。
- [x] platform-neutral codec closure：`DependentMapCodec`、`SequencedCollectionCodec`、`DroppingUnboundedMapCodec` 均与官方 `4646f509` 字节一致；5 个 Java 21 tests 只验证 dependency-first 选择/错误、sequenced 边界/顺序/immutable/partial remainder、dropping-map first-wins/partial-error，JAR 精确限制 1 个 API 与 3 个 common codec class 且无 startup marker。`ModuleContainer`、`AttachedSideConfig`、recipes、data components、config/network owners 与所有 callers 明确延期；不据此声称 persistence、registry、packet、lifecycle-owner 或 serialized-format backward compatibility。
- [x] holiday value foundations：`KnownDate`、`MonthlyDate`、`YearlyDate`、`HolidayMessage` 四源与 `4646f509` 字节一致，Task 022 落地时总 slice 为 42/88/10；五个确定性测试只固定 exact yearly month/day（含 Feb 29 value）、monthly month predicate、可跨午夜的 `now()` snapshot、message 深数组内容 equality/hash 与 accessor/array alias。`YearlyDate.now()` 使用 JVM 默认 clock/zone；`HolidayMessage` 保留 component 与 caller array 引用，不是 deep immutable。JAR 的 holiday package 精确只有四个同名 class 各一次且无 nested/synthetic/test/helper/fixture，无 startup marker。`Holiday`、`HolidayManager`、全部 concrete holidays、player notification、translations/colors/skins、client holiday state、lifecycle/event wiring、persistence、networking、registries、config 及 world/tick/thread ownership 全部延期。
- [x] spatial/color/effect foundations：`Color`、`CustomEffect`、`Pos3D`、`Quaternion`、`Range3D`、`MultipartUtils`、`StatUtils` 七个源文件与 `4646f509` 字节一致，该闭包落地时总 slice 为 42/88/10；12 个 Java 21 黑盒测试限定 color/stat/effect、position/range/rotation/quaternion 与 direct multipart raycast 语义，JAR 精确限制 direct common-lib 四项、effect 一项、math 三项、Multipart 三项、StatUtils 一项且无测试 helper。所有 renderers/particles/GUI/recipe viewers/client lifecycle、transmitters/multipart blocks/gear、machines/block types/entities、packet tracking/network ranges、SPS/multiblock、additions/generators、external energy、world/chunk lifecycle、registries/config/persistence callers/owners 延期；不声明 rendering integration、entity/player lifecycle、block registration、networking、thread safety 或 collision-owner guarantee；
- [x] registration/domain metadata foundations：`BinTier`、`FactoryTier`、`QIODriveTier`、`IResource`、`MiscResource`、`BlockResourceInfo` 六源均与 `4646f509` 字节一致；6 个 Fabric-local 测试固定 bin 容量/BaseTier/config reference 恢复、factory process progression、QIO type/count/base（含 `16_000_000_000L`）、完整资源 suffix/accessor 与 ordinary/raw/luminous/exceptional property construction。`BlockResourceInfo` 仅证明 property construction，不证明 registered-block behavior。`PrimaryResource`、`ResourceType`、ore types/config、tags/datagen/registration、block/item registries and implementations、factories/bins/QIO items/recipes/attachments、`EnumUtils`、`TierConfig` 及 real config ownership/load/reload/lifecycle、transport/tank owners/callers、serialization/network/persistence/registry lifecycle/world behavior 均延期；不声明 broader Fabric integration；
- [x] transport/tank tier metadata：Task 029 纳入 `CableTier`、`ChemicalTankTier`、`ConductorTier`、`FluidTankTier`、`PipeTier`、`TransporterTier`、`TubeTier`，该任务落地时 slice 为 47/110/14；五个 lookup enum 仅以自身 `values()` 替代 `EnumUtils` 数组，四个 tank/pipe/tube enum 仅以 exact `1_000` 替代 NeoForge bucket 常量。该值保持 Mekanism mB/config metadata，不是 Fabric 81,000-droplet transaction unit；12-method tier oracle 与 exact 13-class JAR gate 只证明 metadata，所有 storage/owner/lifecycle 仍延期；
- [x] generic/list cached values：Task 030 将当时 slice 扩为 47/114/14；三个 generic/resolvable/map source 各只替换 `ConfigValue` import，Oredictionificator 只替换 `ModConfigSpec`/nested value imports/types，方法体保持不变。Fabric list schema 整表验证并 immutable-copy encoded/default/current list；官方 `set` 的 resolved caller `TYPE` alias 保留。恰好四个 real-TOML tests 与 exact 14-entry config-value JAR gate 提供行为/包装证据，无 startup marker；real owners/UI/watch/sync/lifecycle 仍延期；
- [x] sound/damage declaration closure：Task 031 将当时 slice 扩为 47/120/14；两个 leaf 与 oracle byte-identical，四个 source 仅作 deferred-wrapper、literal namespace 与 NeoForge-injected holder lookup 的精确 vanilla 适配。五个 tests 固定完整声明/metadata、lazy/canonical/lifecycle 与 test-local frozen damage registry lookup；无 owner registration 或 startup marker；
- [x] module-config value contracts：Task 032 将当时 slice 扩为 53/120/14；`ModuleBooleanConfig`、`ModuleColorConfig`、`IHasModeIcon` byte-identical，`ModuleConfig`、`ModuleEnumConfig`、`IHasTextComponent` 仅作 literal namespace、vanilla exact enum VAR_INT 与 NeoForge subtype removal 的封闭适配。七个 tests 与 exact 5+2 class JAR families 固定 legacy/RGB/ARGB/selectable/with/text-icon behavior，无 owner 或 startup marker；
- [x] item inventory gateway：Task 033 将当时 slice 扩为 **56/122/16**；三份 inventory API、两份 byte-identical holder 与两份 project-owned Fabric source 建立 physical-side-only transactional item view。恰好两个 classes/八个 tests 与 exact three/two/three class families 固定 oversized component NBT、defaults/routing/holder、ItemVariant/true-long、stable views、nested rollback、mismatch/full restore、live policy/invalidation/topology/restore failure；无 provider、cache owner、reverse adapter、concrete slot 或 startup claim（见 `FABRIC_ITEM.md`）；
- [x] fluid value/unit gateway：Task 034 将当时 slice 扩为 **57/122/17**；`mekanism.api.fluid.FluidStack` 保留 int mB、empty/components/copy/matching/codec/NBT/stream/deprecated surface，明确删除 FluidType/capability-only methods；`FabricFluidConversions` 只作 source/still identity + patch 与 exact factor-81 单位转换，拒绝 flowing normalization、负值、remainder 与 int-domain overflow。恰好 9 ordinary + 4 Knot tests，Task 033 item suite 与 fluid suite 合并为单一 `fabricTransferTest` Knot startup；无 handler/storage/transaction/provider/caller/startup marker（见 `FABRIC_FLUID.md`）；
- [x] shared serialization owner：Task 035 以经批准的两份紧耦合 production-source exception 将当时 slice 扩为 **58/122/17**。`SerializerHelper` 保留 11 codec fields/6 methods 与 oracle semantics，只把 NeoForge `FluidStack` 换成项目类型，并用 private exact-category SLF4J logger 避免 eager `MekanismAPI` 初始化；`IInventorySlot` 只删除 private codec 并恢复 shared delegation。恰好 5 个新 ordinary tests；无 owner integration、concrete slot、chemical behavior 或 startup marker；
- [x] portable text/color prerequisites：Task 036 将当时 slice 扩为 **64/122/17**。六份新 API admission 与两个立即恢复的 caller 构成 exact 8-source SCC；三个 leaf 与恢复后的 `RelativeSide` byte-identical，其余只作 stateless predicate、literal namespace、project FluidStack/raw-fluid block key、vanilla level-description expansion 和既有 nominal-parent adaptation。一个新 ordinary class 恰好 5 methods，Task 036 落地 totals 为 ordinary 66/315、transfer 2/8、aggregate 68/323；无 concrete HashedItem、ConstantPredicates、chemical/TriPredicate、owner 或 startup claim（见 `FABRIC_TEXT.md`）；
- [x] generic intrusive/defaulted custom-registry foundation：Task 037 将当前 slice 扩为 **64/122/19**，只新增 `FabricDefaultedRegistry` 与 `FabricCustomRegistryLifecycle` 并安全接入既有 deferred pair。ordinary **67/320**、transfer **2/8**、隔离 root **1/1**，aggregate **70/329**；三项 jqwik 仍各 100,000。exact 7-entry registry family/static leakage gate 和真实 Fabric root worker 证明 lifecycle/canonical/freeze 边界；不加入 Chemical、`mekanism:empty`、data map/radiation、facade、reload/client、production startup 或 manual production freeze（见 `FABRIC_REGISTRY.md`）；
- [x] ordinary MekaSuit absorption data map：Task 038 将当时 slice 扩为 **65/122/22**，只连接 `MekaSuitAbsorption`、identity descriptor、ordinary loader、server-scoped owner 与既有 bootstrap。完整低→高 resource stacks、replace/write/remove、tag expansion、逐 layer 原子失败、不可变 epoch snapshot、server identity/thread 与 stop cleanup 已实现；ordinary **68/324**、transfer **2/8**、root **1/1**、data-map Knot **1/1**，aggregate **72/334**。两个生成资源被精确打包并验 hash；专用 fixture/reload gate 与 release JAR 隔离。Chemical、sync、conditions、Advanced、holder mixin、NeoForge facade 与 broad owners 全部延期（见 `FABRIC_DATA_MAPS.md`）；
- [x] client window and concrete-slot vertical：Task 039 将 slice 扩为 **65/145/24**，由 physical client bootstrap 在 networking 前同步建立 sole `Mekanism/client.toml` owner，经无 Fabric/client import 的 common seam 连接完整 SelectedWindowData，再接入 Basic/Input/Output、container/player/virtual slot、warning 与 InventorySlotInfo。11/28 strict schema、unknown retention、normalized process claim、atomic persist-before-publish、changed-only serialized retry/drain、thread/lifetime failure、generated language identity及 real slot transaction 均有 unit/Knot/two-client/static gates；landing ordinary/transfer/root/data-map/aggregate 为 **70/336**、**3/15**、**1/1**、**1/1**、**75/353**。完整 ClientConfig/QIO、actual GUI/menu networking、provider/cache、side config/ejector 与 machine/Crusher 延期；
- [x] complete Chemical internal vertical：Task 040 将 slice 扩为 **98/164/33**、tests 为 ordinary/transfer/root/data-map/aggregate **73/355**、**4/21**、**1/2**、**2/4**、**80/382**。canonical 65-entry synced registry、五 map 原子 epoch、context-local attributes、complete selected services、common/world policy、bounded snapshot/ACK/reload、true-long tank codecs、真实 configuration-abort/disconnect/reconnect、三次 distinct-session ACK 及 integrated owner isolation 均已门禁；external provider/transactions、ingredients、environmental radiation、configurable ejection、recipes/machines/Crusher 延期（见 `FABRIC_CHEMICAL.md`）；
- [x] recipe ingredient/cache/reload vertical：Task 041 将 slice 扩为 **129/184/37**（350 production sources）、tests 为 ordinary/transfer/root/data-map/aggregate **75/372**、**5/27**、**1/2**、**2/4**、**83/405**。完整 item/fluid/Chemical ingredient 值与 creator service、全部 `InputHelper` 分支、generic `CachedRecipe`/input-cache 与 `RecipeCacheContext`/`RecipeCacheSource`、Chemical thread/context-local 热缓存（copy-on-write `LegacyState`）、server `SERVER_STARTED`/`END_DATA_PACK_RELOAD` 与 client tail-mixin recipe epoch 已门禁；`OneInputCachedRecipe`/`OutputHelper`/recipe type/serializer/machines/Crusher 延期（见 `FABRIC_RECIPES.md`）；
- [x] output handling and one-input cached recipe execution（Task 042A）：将 slice 扩为 **163/184/37**（384 production sources）、tests 为 ordinary/transfer/root/data-map/aggregate **76/378**、**5/27**、**1/2**、**2/4**、**84/411**。完整 `OutputHelper` chemical/fluid/item/`ChanceOutput`/record-output 语义与全部 `OneInputCachedRecipe` factory 保持；新增 layer-neutral `MekanismKeyedHolder`，`MekanismRecipeTypes.TYPE_*` 改为 26 个 **non-registered identity-stable** `RecipeType` 实例，toast holder 按 key 解析 vanilla item 且在有界范围内保持 unbound。四个授权 delta family 之外无改动；recipe type/serializer registration、`MekanismItems`、client recipe viewer 与 machines 仍属被阻断的 042B（见 `FABRIC_RECIPES.md`）；
- [x] recipe type/serializer registration 与 concrete recipe family（Task 043）：将 slice 扩为 **199/188/38**（425 production sources）、tests 为 ordinary/transfer/root/data-map/新 recipe-registry worker/aggregate **77/384**、**5/27**、**1/2**、**2/4**、**1/5**、**86/422**。26 个 `MekanismRecipeTypes.TYPE_*` 现在按 official id 真正写入 `BuiltInRegistries.RECIPE_TYPE`，并把 canonical holder attach 回**同一个** instance，因此 `getType()` identity 与 `RecipeManager` 的 identity-keyed `byType` 分组不变量按构造保持；static init 本身不写 registry。整个 `mekanism.api.recipes.basic` 包（26 concrete/7 abstract/2 interface）与三个 common serializer、`WrappedShapedRecipe`、`FabricRecipeRegistration` 一并纳入，serializer 按 official id/factory order 注册，datapack recipe 可经 `Recipe.CODEC` 解码。两个 NeoForge helper（7-arity composite、`withAlternative(MapCodec,MapCodec)`）替换为语义等价的本地实现；`ShapedRecipe.pattern` 新增一条 access widener。十个 `getToastSymbol()` holder 保持 unbound 且已证明不可达（`MekanismRecipe.isSpecial()` 为 true，recipe book/toast 路径因此不会被触发），故 `MekanismItems` 不需要。`MekanismRecipeSerializersInternal`、machines、client recipe viewer 与 `MekanismRecipeType` cache owner 仍延期（见 `FABRIC_RECIPES.md`）；
- [x] recipe lookup owner/monitor spine（Tasks044–046）：rotary cache、canonical `MekanismRecipeType` owner及五 lookup handler、generic/Factory monitor已纳入；当前精确 slice **201/201/39**，完整测试 **91 suites/443 tests**。Task046父修复约束无context、foreign Level、同步callback rebind/clear的captured-stamp ownership；完整测试、build、static及真实packaged server/clean release均通过。证据与边界见 `archive/TASK044_PLAN.md`、`archive/TASK045_PLAN.md`、`archive/TASK046_PLAN.md`，不声明任何机器/tile或Crusher已完成。
- [x] tiny energy accounting utility：官方 `LastEnergyTracker` 与 `4646f509` 字节一致；同 game time 累加 current bucket 而不改 visible last，任何不同（含跳跃/递减）时间戳发布旧 bucket，setter 仅直接改 client-sync visible value 至下一次 transition。JAR 的 direct `mekanism/common/lib` 中它与 `WildcardMatcher` 及新增 `Color` 两项共同构成精确四项且各一次，无 startup marker。唯一生产 caller `TileEntityMekanism`、world tick/client synchronization、network owner/persistence、machines/networks，以及该 utility 与 Task 023 energy seam 的连接均延期；
- [x] tile behavior contract interfaces：`IHasDumpButton`、`IHasGasMode`、`IHasMode`、`IHasVisualization`、`ITileActive`、`IComparatorSupport` 六源均与 `4646f509` 字节一致；四个 Fabric-local tests 只固定 mode/action 精确分派与原样 tank index、visualization default/override/toggle、active default/override/`NO_DELAY`/raw state，以及 comparator default/override/两个 boundary-level observation。JAR direct package 精确为这六个接口且各一次，无 startup marker；所有 tile/machine 实现及 integration 均延期；
- [ ] distribution/long-transfer 的所有 callers（含 `ComponentBackedEnergyHandler`、`EnergyRecipeData`）、`EnergyContainerHelper`/`ConfigEnergyContainerHolder`、side configuration/ejector、Universal Cable、actual Energy provider registration 与 owner caching/invalidation、machine/network/persistence owners、CachedRecipe、机器基类与 Crusher；不再延期一个 baseline 中不存在的 `EnergyHandler`。

### Task047 最新进度（完成度优先）

已纳入完整 `TileEntityUpdateable`、`ITileWrapper` 与 reduced-update payload/Fabric接入，slice **201/204/43**。父修复空vanilla包漏调子类 `onDataPacket`；最终构建与6项focused tests通过。按用户最新要求，不再每批追加完整测试/真实运行/clean release，最终运行确认留到实际功能里程碑。详情与未验证范围见 `archive/TASK047_PLAN.md`；机器能力/config owner和Crusher尚未完成。

### Task048 最新进度（完成度优先）

GLM5.3 兑现用户一次性实现模型覆盖（非只读审查）。纳入机器流体存储与事务边界：API `IExtendedFluidHandler`/`ISidedFluidHandler`/`IMekanismFluidHandler`/`ExtendedFluidHandlerUtils`（官方同 fluid-before-empty 分配、per-tank 语义与 NBT 完整保留；仅去 NeoForge `IFluidHandler` 继承与 `fill`/`drain` 桥接，改为直接声明平台无关 tank 查询），common `BasicFluidTank`/`DynamicFluidHandler` 与 `holder/fluid/{IFluidTankHolder,FluidTankHolder}`（FluidStack 换为既有 Mekanism 值，其余官方行为不变），以及 `mekanism/fabric/fluid/{FabricFluidStorage,FabricFluidLookup}` 事务视图：物理侧面、真实 tank 快照、abort 后（含 invalidate 后）恢复、同 tank 跨 side view 共享事务参与者、整 mB maxima 向下取整（81 units/mB，返回≤请求的整倍数，无分数累积/取整上升/溢出/组件丢失），复用未改动的 `FabricFluidConversions`。无 provider 注册、无 fake tile/config owner、无反向 adapter。slice **205/208/45**；`fabricTransferTest` 全绿（新增 `FabricFluidStorageTest` 6 tests，总 33），`build`（含 remapJar 与 slice/no-NeoForge/静态合同门禁）通过。未跑全量测试、packaged smoke 与 clean release。`VariableCapacityFluidTank`、`FluidTankHelper`/config/QuantumEntangloporter holder、`TileEntityMekanism`/`FluidHandlerManager` 集成与 provider 注册仍延期；详情见 `archive/TASK048_PLAN.md`。

### Task049 最新进度（完成度优先）

GLM5.3 初版实现（自 clean `faf97926` 隔离 worktree，commit `826756fb` 仅合并待审、**未验收**），父审查三项修正已在其上追加 commit：纳入官方五资源 handler-manager ownership：common `resolver/{ICapabilityResolver,BasicSidedCapabilityResolver}` 与 `resolver/manager/{ICapabilityHandlerManager,CapabilityHandlerManager,Item/Fluid/Energy/Chemical/HeatHandlerManager}`，加上所需域代理 `proxy/{ProxyItemHandler,ProxyFluidHandler}`。`ICapabilityResolver` 改为以真实 Fabric `BlockApiLookup` 身份为键（ItemStorage.SIDED/FluidStorage.SIDED/Team Reborn `EnergyStorage.SIDED`；chemical/heat 为 Mek 自有 `mekanism:{chemical_handler,heat_handler}` 查找，定义在对应 manager）。放弃 NeoForge `SIDED_HANDLER extends HANDLER` 假设：item/fluid 外露类型为事务 `Storage` 视图，分别经 `FabricItemLookup::expose`/`FabricFluidLookup::expose` 构造（baseHandler 相应窄化为 `IMekanismInventory`/`IMekanismFluidHandler`，二者本身仍是 `ISidedItemHandler`/`IExtendedFluidHandler`，与 `TileEntityMekanism` 及已编译 transmitter 调用形状（`TileEntityMechanicalPipe`/`TileEntityUniversalCable` 等，属审计过的延期真实调用方，不因此新纳入 transmitter 源码）核对，构造调用形状不变）；energy 保留官方独立类形，仅 `EnergyStorage.SIDED` 单一外露身份，经 `FabricEnergyLookup.expose(..., UnitDisplayUtils.EnergyUnit.FORGE_ENERGY)` 用既有 FE 换算，Joules 域仍由 `getContainers`/baseHandler 主宰；chemical/heat 保留官方 `ProxyChemicalHandler::new`/`ProxyHeatHandler::new` 与 null-side 只读域代理。外部 item/fluid/energy 对 null side 一律 null（与已接受 adapter 一致），unsupported lookup identity 与 null capability 一律拒绝；`ProxyItemHandler` 实现 `ISidedItemHandler` 并覆写 `getInventorySideFor()` 使 unsided 默认方法路由到绑定 side；`ProxyFluidHandler` 仅换 `mekanism.api.fluid.FluidStack`。**审查修正后的生命周期语义**：`ProxyHandler` 新增永久 `revoke()`（所有读/写/传输 fail closed，不再触碰旧 owner，`getInternalHandler`/`getTanksIfMekanism` 也不再泄露），`BasicSidedCapabilityResolver` 先 revoke 再删缓存、不支持的 lookup 身份的 invalidate 不再触碰无关缓存，五个 manager 全部覆写 `revokeHandler`（item/fluid 撤销 `Fabric*Storage`，chemical/heat 撤销缓存域代理含 null-side 只读代理，energy 撤销 `FabricEnergyStorage` 与内部只读域视图）；`FabricEnergyStorage.stableHandler()` 改为返回预先创建的可撤销 Joules 域 facade（`StableJoulesHandler`），失效前 retain 的反解 handler 在 `invalidate()` 后全部拒绝（精确 Joules 无换算往返），而 `readSnapshot` 回滚仍走未加锁的 raw handler；item/fluid/energy manager 新增 lazy `getReadOnlyHandler()` 显式内部只读域访问（null-side 语义，随相关 invalidate/invalidateAll 撤销，raw baseHandler 保持存活供新视图与回滚）。slice **205/219/45**；`HandlerManagerTest` 4 tests 与 `FabricEnergyLifetimeTest` 2 tests（均扩展，无新增方法）+ `gradlew -p fabric assemble`（含 remapJar 与 slice/no-NeoForge 门禁）通过。未跑全量测试、packaged smoke、clean release 与真实运行；`CapabilityCache`/`TileComponentConfig`/`CapabilityTileEntity`、tile provider 注册、strict-energy 外露身份、`TileEntityMekanism` 实际纳入与 `ProxyItemHandler`/`ProxyFluidHandler` 的真实调用方（如 `TileEntityFluidTank`）仍延期。详情见 `archive/TASK049_PLAN.md`。

## 阶段顺序

1. Java 21 构建系统、Fabric metadata、Mojang official mappings、source sets 与发布物结构；
2. 官方 API 中与 Forge 无关的类型；
3. lifecycle、registry、config、network adapter；
4. Energy、Item、Fluid、Chemical、Heat holder 与 Fabric lookup/transfer adapter；
5. CachedRecipe、输入输出 handler、NBT 与同步；
6. 最小 `TileEntityMekanism` 和通用机器组件；
7. ElectricMachine 与 Crusher 完整垂直切片；
8. Enrichment Chamber、Energized Smelter，之后才扩展其他内容。

## Crusher 阶段门禁

- `clean build` 和单元测试通过；
- 构建后的最终 JAR 可在干净 Fabric 服务端和客户端启动；
- 注册数量与 ID 对账；
- 外部能源、物品侧面自动化和配方重载可用；
- 区块卸载、服务端重启、客户端重连后状态正确；
- 处理中断不吞物、不复制，NBT 与进度正确恢复。

## 从 Kotlin spike 选择性保留

已搬入主线：

- `build.sh`：仓库统一构建产物契约；
- `wait-rcon.sh`：通用开发服日志就绪检查。

仅作为归档参考、暂不直接搬入：

- Fabric entrypoint 与旧 Yarn 1.20.1 调用经验（仅作概念参考，新主线使用 Mojang official mappings）；
- Fabric Transfer transaction、BlockApiLookup、Datagen 接入思路；
- GUI 视觉实现和基础容器测试思路。

明确不继承为主线架构：

- `mekanism.fabric.api.*`；
- 运行时注解扫描注册；
- 自定义 Chemical registry/tag；
- Kotlin SyncSchema 与 Crusher 专用机器框架；
- 硬编码 `:core:runServer` 的旧启动脚本。

### Task 018 internal energy support 落点

`ProxiedEnergyContainerHolder`、`EnergyAcceptorTarget`、`EnergySaveTarget`、`LaserDissipationHandler` 四源均保持与 `4646f509` byte-identical，该任务当时落点为 API/common/platform **42/70/10**。6 个 Fabric-local tests 精确覆盖独立 nullable-side holder predicates/list identity、`EmitUtils` 下 exact `SIMULATE`/`EXECUTE` 与 true-`long` remainder/order/执行短收、SaveHandler fair capacity 与 delayed/repeated saturated/zero save、clamped stored sum、delegate capacity snapshot/`setEnergy`/current storage，以及 laser 0/1/ordinary/独立越界与 infinity rejection 和官方 raw-comparison NaN 行为。JAR gates 精确限制三组 class family；这不是 wire/network protocol 或 adapter。

所有 owners/callers、transmitter/dynamic network lifecycle、actual emission ticks、capability registration/lookup/exposure、Fabric Energy API/conversion/transactions/rollback、side configuration/ejection、machine/tile/item integration、laser armor/item attachment registration、damage handling、networking/serialization/persistence/threading 均延期，不声明 broader integration。

### Task 019 signed-long config/tier metadata 落点

该任务当时落点为 API/common/platform **42/74/10**。`EnergyCubeTier`、`InductionCellTier`、`InductionProviderTier` 与 `4646f509` byte-identical；`CachedLongValue` 唯一源码差异是 NeoForge `Builder`/`ConfigValue` 两个 import 替换为 Fabric spec 类型。`MekanismConfigSpec` 的 long schema 无损接受完整 signed-long TOML integer literal（含 `>2^53` 与两端点），并在 Night Config 前以 decimal/hex/octal/binary lexical preflight 拒绝会被 modulo wrap 的 overflow；程序化 Float/Double 按 exact runtime IEEE 值检查，只接受精确 integral 且 representable/range-valid 的指定 number 类型，拒绝 fraction、非有限值、overflow 与 `2^63` trap，不作 rounding/truncation/saturation/lossy comparison。

这只是 schema/value support 与 tier metadata，不激活 `tiers.toml`。Task 030 后 `TierConfig`、`GeneralConfig`/`MekanismConfig`/其他 real config files、cardboard blacklist、Oredictionificator filters/items、`CachedEnumValue` 与其它未声明 schema、config sync/UI/watchers/world restart、全部 tier owners/callers、machines/blocks/items、capability registration、external energy/fluid/chemical transactions、networking/serialization/persistence/lifecycle/threading 仍全部延期。

### Task 020 tile interface contract 落点

该任务落地时 allowlist 为 API/common/platform **42/80/10**。`IHasDumpButton`、`IHasGasMode`、`IHasMode`、`IHasVisualization`、`ITileActive` 与 `IComparatorSupport` 六源均与官方 `4646f509` byte-identical。四个确定性 Fabric-local tests 分别限定：(1) dump/next/previous 的 exact one-call dispatch、mode 方向独立，以及 `IHasGasMode.nextMode(int)` 对 `0` 和负 tank index 原样转发；(2) visualization 的 inherited true、override false 与每次 toggle 的本地 observable transition；(3) 可复用 `NO_DELAY` 精确为零、activatable true/false 与 active raw round-trip；(4) comparator support true/false 和 current/non-current level 对 boundary integer 的独立观察。发布物 direct `mekanism/common/tile/interfaces/` family 必须精确只有这六个 `.class` 且各一次，拒绝 nested/synthetic/test/helper/fixture 泄漏；无需 startup marker。

这是 contract-only batch，不是 tile behavior integration。所有 tile/machine implementation、`TileEntityMekanism`、GUI/button packet caller、block-state/comparator update scheduling、legacy gas 名称背后的 chemical behavior、capability registration/lookup/exposure、side configuration/ejection、persistence、networking/wire serialization、registry lifecycle、world ticking、thread ownership 与 client/server synchronization 全部延期；不声明 broader integration。

### Task 021 energy/heat slot descriptors 落点

Task 021 落地时 allowlist 为 API/common/platform **42/84/10**。`ISlotInfo`、`BaseSlotInfo`、`EnergySlotInfo`、`HeatSlotInfo` 四源均与官方 `4646f509` byte-identical。五个 Fabric-local JUnit contracts 分别固定：(1) 四种 input/output flag 组合、accessor 原样保留及 `isEnabled()` 精确 OR；(2) energy varargs 的元素 identity/order、零容器及 all-empty 判定；(3) energy emptiness 按 source order 扫描并在首个 non-empty 精确短路；(4) list 构造器保留同一 mutable list，后续 membership 与 contained state 变化实时可见；(5) heat flags/list identity，且空/非空 capacitor list 均返回 false、绝不读取 capacitor heat state。发布物的 `mekanism/common/tile/component/config/slot/` family 精确只有 `ISlotInfo.class`、`BaseSlotInfo.class`、`EnergySlotInfo.class`、`HeatSlotInfo.class` 各一次，不允许其它 direct/nested/synthetic/test/helper/fixture class；无 startup marker。

这是 descriptor-only batch。`ConfigInfo`、`DataType`、`TileComponentConfig`、`TileComponentEjector`、所有 configurable holders/helpers、chemical/fluid/inventory/proxy slot descriptors、每个 tile/machine/block caller、capability exposure、side-configuration execution、ejection、persistence、networking/wire serialization、config lifecycle、registry lifecycle、transactions 与 thread ownership 全部延期；不声明 broader integration。

### Task 022 holiday value foundations 落点

Task 022 落地时 allowlist 为 API/common/platform **42/88/10**。`KnownDate`、`MonthlyDate`、`YearlyDate`、`HolidayMessage` 四源均与官方 `4646f509` byte-identical。一个同包测试类的五个确定性 JUnit methods 分别固定：(1) yearly exact month/day、相邻日/异月拒绝及 Feb 29 纯 record value；(2) monthly 同月多日匹配与异月同日拒绝；(3) `YearlyDate.now()` 必须匹配调用前后任一 `LocalDate.now()` snapshot，以容忍午夜 rollover，且不改 clock/time-zone globals；(4) independently constructed themed components 与 ordered line-array deep content 的 equality/hash，以及 reorder/theme/line/null inequality；(5) themed component 和 varargs backing array 的 exact reference、line order 与 caller 后续 array mutation 可见。`YearlyDate.now()` 明确使用 JVM default clock/zone；`HolidayMessage` 保留 component/array references，而非 deep immutability。

发布物门禁要求 direct `mekanism/common/base/holiday/` family 精确只有 `KnownDate.class`、`MonthlyDate.class`、`YearlyDate.class`、`HolidayMessage.class` 各一次，拒绝其它 direct/nested/synthetic/test/helper/fixture class，且无 startup marker。`Holiday`、`HolidayManager`、每个 concrete holiday、player notification、translations/colors/skins、client holiday state、lifecycle/event wiring、persistence、networking、registries、config、world/tick/thread ownership 全部延期；不声明任何 holiday lifecycle integration。

### Task 023 Team Reborn Energy boundary 落点

Task 023 落地时 allowlist 为 API/common/platform **43/88/14**。`IEnergyConversion` 原样加入 API slice，与 oracle `4646f509` byte-identical；四个 `mekanism.fabric.energy` 类为独立 Fabric adapter。构建固定 `teamreborn:energy:4.1.0` 与 runtime metadata `>=4.1.0`，只声明 `modImplementation` 而不 `include`。shared helper 拒绝 disabled/NaN/infinite/non-positive rate；exact `1.0D` 作为 adapter authoritative identity contract 无损保留所有 positive long，而不修改 official double contract；与 advertised 1:1 rate 冲突的 custom conversion override 不受支持。non-1 使用固定、bound-independent family：全局 10-bit dyadic mesh 加 canonical-decimal continued-fraction 各 denominator 的 1..64 bounded multiplier closure 和 fitting binary shifts。bounds 只过滤 family；对 documented stable conversion behavior，取经官方双向验证且两域不越界的最大 eligible member，因此 deterministic 且对两域 bound 分别 monotone；family 有限、无 caller-bound-sized scan，但 conservative，不声称 globally maximal 或任意 rate 的 utilization 下限，无 verified member 时返回 zero。cold build 总上界为 28,159 dyadic 加 92×64×63 decimal occurrences，即 sort/dedup 前最多 399,103；随后每个 unique candidate 至多一次双向验证。四槽 synchronized weak-identity cache 以 conversion `==` 与 raw rate bits 隔离 immutable primitive snapshot，实时重查 `isEnabled`，bit 变化重建，same-rate different identity 不共享。warm selection 为 `O(log U)` predecessor/range-minimum query，无 BigDecimal/Euclid/family enumeration，并在返回前用 owner identity 再做官方双向验证；empty requested window 在 snapshot 非空时复验首个 cached sentinel。zero-pair snapshot 无 sentinel，owner 必须在 advertised rate bits 不变时保持 conversion behavior 稳定。除 2.5/0.4/0.7、大 long 与 reviewer 682↔9,634,898,391,631 外，回归还固定无 bare-convergent pair 的 `372965497689.465`、`10.906989658246147`/`575.403` named high-bound 99% projection 下限，以及 mutable-rate invalidation/identity isolation/每对 warm search 的确定 call envelope。

Team storage→strict handler 是单 container、invalid-index default、set no-op，并把 `SIMULATE` 映射到 abort；`EXECUTE` 在 simulation/actual acceptance 一致时 commit。transaction lifecycle 仅 `NONE` 开 outer、`OPEN` 读取 current 并开 child；`CLOSING`/`OUTER_CLOSING` callback 安全短路，避免非法 `getCurrentUnsafe()`。strict handler→Team storage 继承 `SnapshotParticipant<long[]>`，mutation 前检查固定 count 并 snapshot 全部 captured containers；execute 改 count 时 child abort、按 snapshot topology 恢复、报告 zero。之后 insert/extract 与 `stableHandler()` fail-fast，四个 query 则在 count drift 时返回 false/zero，聚合途中发现 drift 亦返回 zero。amount/capacity 正常路径先饱和汇总 raw Joules 再换算一次，exact-1 无损。rollback backing 每个 index 必须 deterministic 支持 `setEnergy`；known no-op strict adapter 被构造器拒绝；restore 尝试全部 captured setters 且每次 read back，capacity clamp mismatch、setter/read throw 都按 primary/suppressed 传播。listener/save/sync immediate side effect 不可回滚。

lookup/factory 只允许 non-null physical side；conversion unusable、`canHandleEnergy()==false`、empty side 均返回 null。storage 保存 exact exposure side，side mismatch 对所有 conversion 都返回 null；same-side + identical conversion 才 unwrap，different usable conversion 可 wrap。side-bound proxy 与 retained suppliers 每次访问 live exact-side/nullable-holder permission，使 `supports*` 与操作同步 allow→deny→allow；owner 在 support change 时必须通知 neighbors，具体 wiring 延期。exact 八个 Fabric-local JUnit methods 证明上述换算/事务/恢复/side 语义；packaged gate 验证 Energy 4.1.0 下载/metadata/class、dependency 非嵌套、API/platform exact class family、两 mod IDs 与 dedicated-server classloading，不增加 startup transfer marker。

明确延期：`ConfigHolder`/`ConfigInfo`/`DataType`/`ISideConfiguration`/configurable holders/helpers、`TileComponentConfig`/`TileComponentEjector`、actual block/item/entity provider registration 与 owner cache/invalidation、cables/networks/emission/machines、hardcoded FE config activation、item reverse adapter/provider owner 以及 fluid/chemical/heat adapters、persistence、packets/client sync 和 thread safety。Task 033 后首个 item physical-side gateway 见 `FABRIC_ITEM.md`。

### Task 024 container callback contracts 落点

Task 024 落地时 allowlist 为 API/common/platform **47/88/14**。`ContainerInteraction`、`InContainerGetter`、`IntContainerInteraction` 与 `LongContainerInteraction` 四份官方 API 源原样加入，并与 oracle `4646f509` byte-identical；加上既有 `InContainerGetterLong` 与 `LongToLongContainerInteraction`，direct `mekanism/api/container/` 发布物精确只有这六个接口且各一次。一个 Fabric-local 同包测试类中的恰好四个 deterministic JUnit methods 固定 generic stack/result identity、任意/负 index、nullable/physical side、exact `SIMULATE`/`EXECUTE`，以及 raw `int`/true-`long` signed boundaries（含 `>2^53`）不规范化。测试类由 exact package gate 排除，不增加 startup marker。

这只是 callback contracts，不实现 transfer policy。`ChemicalUtils`、`ExtendedFluidHandlerUtils`、chemical/fluid handlers、component-backed/recipe-upgrade owners、actual transfer/platform transactions、registries、serialization/networking 与 lifecycle/thread ownership 全部延期；不声明 broader container integration。

### Task 025 primitive cached-value sources 落点

Task 025 落地时 allowlist 为 API/common/platform **47/92/14**。官方 `CachedBooleanValue`、`CachedByteValue`、`CachedShortValue` 与 `CachedDoubleValue` 各自相对 oracle `4646f509` 精确只有一个 import 替换：NeoForge `ModConfigSpec.ConfigValue` 改为 `MekanismConfigSpec.ConfigValue`；其余 source bytes 不变。Fabric spec 只新增 strict `define(String, boolean)` 与 typed byte/short `defineInRange`：boolean 仅接受 `Boolean`；byte/short 复用既有 exact-integral long normalizer，在验证 signed-long representability、primitive bounds 和 configured range 后才收窄，拒绝 fraction、non-finite、overflow/range miss，不 truncation/wrap/round/saturate。既有 int/long/double 行为、strict long TOML lexical preflight、同步及 parse→correct/persist→atomic publish 顺序不变。

一个 Fabric-local 同包测试类恰好四个 `@Test`，每个 primitive wrapper 一个，使用真实 `BaseMekanismConfig`、Builder/spec 和临时 TOML，固定 boolean true/false/错误类型 correction 与 lazy/set/invalidation；byte/short signed primitive/configured endpoints、programmatic/TOML overflow/fraction/radix 拒绝、set failure isolation 和 cache refresh；double finite endpoints、runtime Float/Double normalization、raw `-0.0` load/cache/set 与官方 signed-zero unchanged invalidation，以及 correction/write-failure publication isolation。Task 025 当时把 JAR direct config-value family 从五项扩展为精确九项；Task 030 已进一步纳入 generic/resolvable/map/Oredictionificator 并扩为 14 项。real config activation、`CachedEnumValue`、未声明 schema、sync/UI/watchers/owners 仍延期。

### Task 026 block-state descriptors 落点

Task 026 落地时 allowlist 为 API/common/platform **47/96/14**。`IStateStorage`、`IFluidLogType`、`FluidLogType`、`TransmitterType` 四源均与 oracle `4646f509` byte-identical。一个同包 Fabric-local 测试类恰好三个 `@Test`：固定 `IFluidLogType.isEmpty()` 对 live getter 的 exact `Fluids.EMPTY` identity；`FluidLogType` 的 exact order、serialized names、canonical fluid identity、EMPTY-only emptiness 与 `values()`/`valueOf()`；以及 `TransmitterType` 的 exact seven-value order、SMALL/LARGE assignment、6/8 center size 与 `values()`/`valueOf()`。marker-only `IStateStorage` 不作 vacuous reflection test，其编译与 package identity 是唯一证据；无 startup marker。测试仅以既有 narrow `SharedConstants`/`Bootstrap` pattern 满足 vanilla static initialization，不作 custom registry mutation。

发布物门禁要求 exact `mekanism/common/block/states/` package 仅含 `IStateStorage.class`、`IFluidLogType.class`、`FluidLogType.class`、`TransmitterType.class`、`TransmitterType$Size.class` 各一次，并拒绝其它 direct/nested/synthetic/test/helper/fixture class。fluidlogging block implementation/state-definition/world update/persistence、transmitters/cables/networks/rendering、actual block registration、codecs/serialization/networking、lifecycle/thread ownership 及所有 callers/implementers 均延期；不声明 marker 的 runtime behavior。

### Task 027 inventory metadata contracts 落点

Task 027 落地时 allowlist 为 API/common/platform **47/99/14**。这是经 bounded scouts 确认没有诚实四源闭包后显式批准的 coherent **3-source exception**：`GuiComponents` 与 `ContainerSlotType` 和 oracle `4646f509` byte-identical；`SlotOverlay` 只移除 `MekanismUtils`/`ResourceType` imports，并把构造改为精确 `ResourceLocation.fromNamespaceAndPath("mekanism", "gui/slot/" + texture)`。其 11 个结果 ID 与官方行为逐项一致，均为 `mekanism:gui/slot/overlay_*.png`。一个 Fabric-local 测试类恰好四个 `@Test`，固定七项 slot type、十一项 overlay/order/ID、全部 18×18 与 enum contracts，以及 test-local dropdown/toggle enums 的 exact `Component`/`ResourceLocation` identity、default-null/overridden icon 和 forwarding；无 bootstrap、registry/global mutation、renderer 或 texture I/O，也无 startup marker。

静态发布物门禁要求 `GuiComponents.class`、`GuiComponents$IDropdownEnum.class`、`GuiComponents$IToggleEnum.class` 各一次且同名前缀无其它 nested/test/helper；direct `mekanism/common/inventory/container/slot/` 只允许 `ContainerSlotType.class` 与 `SlotOverlay.class` 各一次，拒绝其它 direct/nested/synthetic/test/helper/fixture。所有 screens/widgets/rendering/runtime texture validation、concrete slots/`IInsertableSlot`/inventory/menu/player/window owners、GUI lifecycle、networking/sync、registries/serialization/thread ownership 全部延期；这里只声明 metadata contracts。

### Task 028 deferred codec/criterion/loot registration 落点

Task 028 落地时 allowlist 为 API/common/platform **47/103/14**。四个新纳入的官方 common source `DeferredMapCodecHolder`、`DeferredMapCodecRegister`、`DeferredCriterionTrigger`、`LootFunctionDeferredRegister` 均与 oracle `4646f509` byte-identical。与这四源明确分开的唯一 production 修复是既有 Fabric adaptation `MekanismDeferredHolder.value()` 从 `R` 改为 covariant `T`，方法体仍为 `return get();`。因类约束 `T extends R`，它合法 override `Holder<R>.value(): R` 且保持 subtype contract；`R`/`T` 均擦除为 `Object`，JVM descriptor 与运行行为不变，本构建不生成额外 bridge。

一个同包 Fabric-local 测试类恰好四个 `@Test`，覆盖 MapCodec function overload 的 lazy ID/key/subtype/canonical binding 与 pre-bind failure，supplier/custom-holder subtype、duplicate/lifecycle delegation 和 covariant exact identity，最小本地 criterion 的 exact instance/trigger identity，以及最小本地 loot function 的 basic/codec lazy/key/codec identity。所有提交都经 package-private registrar seam，不改变 custom/built-in registry；criterion 只用既有标准 `SharedConstants`/`Bootstrap` vanilla 初始化，loot/codec 不作 serialization 或 datapack execution，也无 startup marker。

Task 028 当时的 JAR gate 要求 direct registration family 精确为 `INamedEntry`、`MekanismDeferredHolder`、`MekanismDeferredRegister`、`MekanismDeferredRegister$Registrar`、`DeferredMapCodecHolder`、`DeferredMapCodecRegister`，impl family 精确为 `GameEventDeferredRegister`、`DeferredCriterionTrigger`、`LootFunctionDeferredRegister`，并拒绝其它 nested/test/helper。real production declarations、global registration/freeze、datapack codec execution、serialization、loot/advancement runtime、owners/network/threading 全部延期。

### Task 029 transport/tank tier metadata 落点

Task 029 落地时 API/common/platform allowlist 精确为 **47/110/14** 且各 slice 内唯一。新增的七份 official common metadata source 是 `CableTier`、`ChemicalTankTier`、`ConductorTier`、`FluidTankTier`、`PipeTier`、`TransporterTier` 与 `TubeTier`。相对不变的 oracle，production adaptation 边界只有两组：`CableTier`、`ConductorTier`、`PipeTier`、`TransporterTier`、`TubeTier` 移除 `EnumUtils` import，并在各自 `get(BaseTier)` 中以自身 `values()` 替代对应 `EnumUtils.*_TIERS`，保持声明顺序、enum identity、所有 present mapping 与 absent-tier `BASIC` fallback；`ChemicalTankTier`、`FluidTankTier`、`PipeTier`、`TubeTier` 移除 NeoForge `FluidType` import，并把每个 compile-time `FluidType.BUCKET_VOLUME` 精确替换为 integer literal `1_000`，保持全部现值与既有 `int`/`long` 表达式行为。`1_000` 表示从 NeoForge 常量继承的 Mekanism mB/config metadata bucket basis；它刻意不是 Fabric Transfer API 的 81,000-droplet transaction unit，也未引入 Fabric fluid constant 或 conversion adapter。

`TierMetadataTest` 精确从 5 增至 12 个 `@Test`，七个新增方法逐 enum 固定完整 order/BaseTier/base tuple/current getter tuple；五个 lookup enum 固定全部 present mapping 与 `CREATIVE`/null absent fallback，chemical tank 固定 serialized names，conductor 以 RGBA channels 和 packed value 固定 conduction/heat-capacity/insulation/color tuple。每份测试都用 loaded `CachedIntValue`/`CachedLongValue`/`CachedDoubleValue` 覆盖该 enum 的全部 mutable config-reference field，以 distinct signed/large/fractional 值防 narrowing/wrong-field wiring，并验证后续 `set` 实时可见；private prior references 通过 reflection 捕获，在 `finally` 中全部恢复，随后用 identity assertion 核对（含 prior null）。静态 remapped-JAR gate 要求 direct `mekanism/common/tier/` 精确为原六项加新增七项共 13 个 class entry，各一次，无 nested/test/helper/fixture 泄漏；无 startup marker。

该闭包只承认 tier metadata。`TierConfig` 与 `tiers.toml` activation、Fabric fluid/chemical storage/conversion/transactions、cables/pipes/tubes/transporters/conductors/tanks 及其所有 owners/callers、registry/network/persistence/lifecycle/threading 全部延期。

### Task 030 generic/list cached-value 落点

Task 030 落地时 API/common/platform allowlist 精确为 **47/114/14** 且各 slice 内唯一，18-file official root-test oracle 以及 API/platform slices 不变。新纳入 `CachedResolvableConfigValue`、`CachedConfigValue`、`CachedMapConfigValue`、`CachedOredictionificatorConfigValue`。前三份相对 `4646f509` 各自仅把 NeoForge nested `ConfigValue` import 换成 `MekanismConfigSpec.ConfigValue`；最后一份仅把 NeoForge `ModConfigSpec` 和 nested `ConfigValue` imports/types 换成 `MekanismConfigSpec` 等价项。所有方法体、resolve/encode/cache 行为保持官方版本，四份源码不宣称 byte-identical。

`MekanismConfigSpec.Builder` 只增加 strongly typed `defineListAllowEmpty(String, Supplier<List<? extends T>>, Supplier<T>, Predicate<Object>)`。所有 functions non-null；default supplier 定义时恰好求值/验证一次，new-element supplier 因没有 UI/editor 而不调用。empty list 合法；non-list、null element 或任一 predicate miss 拒绝整表而不是过滤。default 及每份 accepted raw/set list 都成为 immutable top-level copy；element 不 deep-copy（当前 owner 为字符串）。它复用既有 path/category collision、pending translation/comment/restart、builder freeze、private define/value normalizer、set-before-update、load correction、atomic persistence-before-publication 和 synchronized update，保持 TOML array。

唯一新同包测试类恰有四个 `@Test`，分别对应 generic、resolvable、map 与 Oredictionificator：真实 TOML 路径固定 immutable/correction/failed-set/publication；unloaded default、不污染 cache、null sentinel retry、loaded reuse 与 changed/unchanged invalidation；input-order duplicate last-wins、owner-defined malformed ignore 与全局 sorted encoding；以及 `Locale.ROOT` namespace/path、invalid ID encode omission、whole-list correction、empty/duplicate/order、save/clear/reload。官方 `CachedResolvableConfigValue.set` 刻意未改：immutable encoded `ConfigValue` list 与 caller-owned resolved `Map` cache 是两层，set 后 map 保留 caller alias，clear/reload 后才由 deterministic encoded strings 重建 normalized state。

remapped JAR 的 `mekanism/common/config/value/` family 从九项扩为精确 **14** 项：新增三个 outer、`CachedMapConfigValue` outer + `ValueEncoder`、Oredictionificator outer，各一次；拒绝任何其它 direct/nested/synthetic/test/helper/fixture，无 startup marker。`GeneralConfig`/`MekanismConfig` real activation、cardboard blacklist、Oredictionificator filters/items、`CachedEnumValue`、其它 config schema、UI/watch/sync、ownership/lifecycle/threading 仍延期。

### Task 031 sound/damage declaration 与 vanilla holder lookup 落点

Task 031 落地时 API/common/platform allowlist 精确为 **47/120/14** 且各 slice 内唯一；API/platform slices 与 18-file root-test oracle 不变。六个新纳入 common source 中，`SoundEventRegistryObject`、`DamageTypeRegister` 与 `4646f509` byte-identical。`SoundEventDeferredRegister` 只改用既有 `MekanismDeferredRegister<SoundEvent>`，构造时传入 `SoundEventRegistryObject::new`，保留 default/function/supplier behavior 和 typed returns，删除 `createHolder` 与 obsolete imports，不新增 shim。`MekanismSounds`/`MekanismDamageTypes` 只删除完整 `Mekanism` import 并把 `Mekanism.MODID` 换成 `"mekanism"`；声明、顺序、值不变。

`MekanismDamageType` 的唯一变化是把 private NeoForge-injected `registryAccess.holderOrThrow(key())` 展开为 vanilla `registryAccess.lookupOrThrow(key().registryKey()).getOrThrow(key())`。这是 NeoForge 21.1.200 `IHolderLookupProviderExtension.holderOrThrow` 的相同 lookup/exception 语义，无 import、public surface 或其它 method drift。一个新同包类恰好五个 `@Test`：fresh default/function/supplier sound paths 固定专用 holder、translation、lazy/canonical identity、fixed/variable range 与 duplicate/late/double commit；完整 47-public-declaration `MekanismSounds` table（一个 owner + 46 unbound holders）逐名固定 id/key/translation；`DamageTypeRegister` 固定全部 overload、live unmodifiable collection、duplicate last-wins 与 metadata；完整四项 `MekanismDamageTypes` 固定 exact identities 和 `toVanilla`。

private holder 等价性只通过 disposable test-local `MappedRegistry<DamageType>` 验证：注册并 freeze 一个 matching value，构造仅包含它的 `RegistryAccess`，检查 plain/position/nullable-entity overload 的 exact holder/value/position、`is` 和 missing key/registry failure。测试不调用 global/built-in `Registry.register`；标准 vanilla bootstrap 仅满足 `DamageEffects` 静态初始化。没有 `Level` fixture、non-null entity、Mockito、Unsafe 或 entrypoint。static JAR gate 要求 registration/impl 精确七项、registries 精确 `MekanismGameEvents`/`MekanismSounds`/`MekanismDamageTypes` 三项，各一次且拒绝 nested/test/helper；无 startup marker。

明确延期：sound owner 的真实 registration/freeze、playback/client/resource/subtitles，damage 的真实 registration/freeze/datapack bootstrap、production `RegistryAccess`/`Level`/entities/combat，以及全部 owners/callers。

### Task 032 bounded module-config value contracts 落点

Task 032 落地时 API/common/platform allowlist 精确为 **53/120/14** unique sources，common、platform 与 18-file official root-test oracle 均不变。六个新增 API source 中，`ModuleBooleanConfig`、`ModuleColorConfig`、`IHasModeIcon` 与 `4646f509` byte-identical。`ModuleConfig` 只删除 `MekanismAPI` import 并以 literal `"mekanism"` 构造 private resource ID；官方 data codec 仍只在 decode 时把 legacy `minecraft` namespace 重写为 `mekanism`，保留其它 namespace 与 encode 的 exact current name。`IHasTextComponent` 只删除 NeoForge `TranslatableEnum` import/父型及失效的 `@Override`；nested interface 的 public default `getTranslatedName()` 与 `return getTextComponent();` exact body 保留，所以直接 source/binary method behavior 不变，但 NeoForge subtype compatibility 明确丢失且无 shim。

`ModuleEnumConfig` 只把 NeoForge stream codec call 换成捕获 declaring enum constants 的 method-local vanilla codec：`ByteBufCodecs.VAR_INT` encode `ordinal()`，decode 后直接以 ordinal 索引数组。由此所有 VAR_INT bytes 保持，negative/high ordinal 仍是 `ArrayIndexOutOfBoundsException`，null encode 仍是 `NullPointerException`；不生成 anonymous production class 或新增 platform source。官方 selectable behavior 未改：count `<= 0`、大于 length 与 bounded initial value 越界继续失败，equal count 退化为 unlimited，subset list immutable，bounded data codec 校验 value，而 supplied bounded stream codec 仍只携带 selectable list；changed `with(TYPE)`/`with(int)` 仍返回 full enum list。Boolean name/value/codec/stream/identity update 以及 color signed-int、ARGB fixed INT、RGB VAR_INT/alpha `0xFF` locking 都保持。

唯一新 same-package Fabric test class 恰有七个 `@Test`，只用真实 Mojang codecs、Netty 与 `FriendlyByteBuf`，覆盖 canonical/legacy names、boolean/color round trip 与 exact bytes、RGB/ARGB、enum limits/data+supplied streams/named VAR_INT/AIOOBE/NPE、官方 bounded `with`、equality/hash/class/name/value/count 和 text/icon exact identity。remapped JAR 的 direct `mekanism/api/gear/config/` family 必须精确为 `ModuleConfig`、`ModuleBooleanConfig`、`ModuleColorConfig`、`ModuleEnumConfig`、`IHasModeIcon` 五项各一次；`IHasTextComponent` prefix 必须精确为 outer 与 `$IHasEnumNameTextComponent` 两项各一次，并拒绝 nested/synthetic/test/helper/fixture leakage；无 startup marker。

`ModuleData`、module containers/items、codec catalog、GUI/radial/client rendering、packets/network owner integration、persistence/data components/registration/lifecycle 与全部 callers 均延期。

### Task 034 fluid value and exact-unit gateway 落点

Task 034 将 API/common/platform allowlist 扩为 **57/122/17**，common slice 保持 byte-for-byte 不变。生产范围精确只有 project-owned `mekanism.api.fluid.FluidStack` 与 `mekanism.fabric.fluid.FabricFluidConversions`。前者依据 NeoForge 21.1.200 LGPL-2.1-only behavior contract 独立实现，不复制源码文字；保留 final value、int mB、canonical/observed empty、mutable components、copy/split/raw overflow、holder/tag/predicate、identity Object equality、matching/hash、required/fixed/optional/lenient JSON codec、registry-aware NBT、optional/strict stream 与 platform-neutral deprecated surface。vanilla `DataComponentHolder` 取代未提供的 NeoForge mutable nominal parent，但 direct/Supplier set/remove、BiFunction/UnaryOperator direct/Supplier update、DataComponentType/Supplier varargs copyFrom 与 apply callable surface 均恢复；update 经 getOrDefault/set，copyFrom 会清除 source 缺失的 target 值。

FluidType-only `getFluidType`/`is(FluidType)` 与 capability-only deprecated `isFluidEqual(ItemStack)` 明确删除。description 采用 default fluid state 的 legacy block description ID，WATER/LAVA/EMPTY 固定为 `block.minecraft.water`/`block.minecraft.lava`/`block.minecraft.air`；water/lava 与 pinned NeoForge explicit IDs 保持 parity，先前 `fluid.*` delta 已移除。custom FluidType override 仍不可用，Fabric convention 明确为 block-derived。parse/save 只去掉 NeoForge diagnostic logger wrapper，codec result/data/exception 仍直接由 vanilla pipeline 决定。

Fabric seam pin `1_000 mB == 81_000 Transfer units` 与 factor 81，checked mB multiplication，exact reverse divisibility/int-domain validation，max `173_946_175_407L`。blank/empty 与 zero 映射 canonical blank/EMPTY；source water/lava 连同 patch exact round trip；若 `FluidVariant.of` 会把 flowing fluid normalize 为 source，则 hard reject。无 floor/remainder helper、Storage、Transaction、lookup、provider、cache、listener 或 lifecycle claim。

验证恰好新增 `FluidStackTest` 九个 ordinary methods 与 `FabricFluidConversionsTest` 四个 Knot methods；新增 component-focused method 覆盖全部 overload families 及 absent-source clearing。旧 `fabricItemTest` 被泛化为 `fabricTransferTest`，原 item suite byte-identical relocation，新 fluid suite 共用一次 Knot startup；普通 runtime 不含 loader-junit且三项 jqwik properties 保持各100,000 tries/checks。预期 ordinary 64 suites/305 tests、transfer 2/8、aggregate 66/313。static JAR gate 精确只允许 `FluidStack.class` 与 `FabricFluidConversions.class` 各一次，无 nested/test/helper/NeoForge/loader leakage 或 fluid startup marker。

在 Task 034 落地时，`SerializerHelper` 与其余列表均延期；Task 035 迁移 shared owner，Task 036 又仅迁移 portable `TextComponentUtil` closure，并以不逃逸 always-true lambda 避免纳入完整 `ConstantPredicates`。其余 181 个 NeoForge FluidStack import replacements（Task 042A 迁移其中 14 个 recipe/vanilla_input source）、fluid types/attributes、tanks/handlers/storage/transactions、item concrete slots、warning/menu、recipes、provider/cache owner、machines/Crusher 与所有其余 callers 仍全部延期。完整 provenance、format 与风险边界见 `FABRIC_FLUID.md`。

### Task 035 shared serialization ownership 落点

Task 035 是经 concrete-slot scouting 后批准的两份紧耦合 production-source 架构例外；没有用无关 leaves 填充默认 4–12 source batch。其落地 API/common/platform 为 **58/122/17**，production changes 精确局限于新 allowlist owner `mekanism.api.SerializerHelper` 与已纳入的 `mekanism.api.inventory.IInventorySlot`，无 common/platform source 或 dependency change。

相对 `4646f509`，`SerializerHelper` 保留全部 11 public codec fields、6 public methods、signatures、legacy floating-long 的 decimal/unsigned/clamp/encode behavior、item single/optional/lenient/oversized defaults/components/errors、project `FluidStack` lenient codec、conditional map codecs 与 stringify。唯一 import adaptation 是 NeoForge `FluidStack`→`mekanism.api.fluid.FluidStack`；唯一非 import adaptation 是把两处 `MekanismAPI.logger.error` 改由 private `LoggerFactory.getLogger("mekanism.api.MekanismAPI")` 发出。category、error level 与两个 exact message templates 保持，同时避免首次 `SerializerHelper` reference eager 初始化尚未纳入的 `MekanismAPI`。Fabric entrypoint 不引用该类；vanilla-dependent tests 在首次引用前 bootstrap。

`IInventorySlot` 仅删除 Task 033 的 private oversized codec 与 now-unused imports，并恢复 `SerializerHelper.saveOversized(provider, getStack())`；registry-aware NBT nominal parent、Javadoc 适配与全部 public/default inventory methods 原样保留。一个 ordinary `SerializerHelperTest` 恰好 5 methods，加 unchanged `InventoryApiHolderTest` 4 methods组成 focused 2 suites/9 methods；Task 035 落地 totals 为 ordinary **65/310**、transfer **2/8**、aggregate **67/318**；Task 036 落地为 **66/315**、**2/8**、**68/323**；Task 037 落地 ordinary/transfer/root/aggregate 为 **67/320**、**2/8**、**1/1**、**70/329**；Task 038 落地为 ordinary/transfer/root/data-map/aggregate **68/324**、**2/8**、**1/1**、**1/1**、**72/334**；Task 039 当前为 **70/336**、**3/15**、**1/1**、**1/1**、**75/353**，jqwik 三项各 100,000。JAR 经实编译确认 family 精确为 outer + `$1`/`$2`/`$3`，各一次；gate 还拒绝 unexpected same-prefix class、`MekanismAPI.class`、NeoForge/Loader bytecode reference、startup entrypoint reference/marker。

此任务不加入 registry/config/lifecycle/transaction/transfer/thread owner、broad facade 或 chemical behavior。Task 036 只以 local stateless predicates 关闭 portable text prerequisite，并未纳入完整 `ConstantPredicates`；concrete slots、menu/warning/recipes 及上述其余边界仍延期。

### Task 036 portable text/color SCC 落点

Task 036 将 API/common/platform allowlist 扩为 **64/122/17**，没有 common/platform production source 或 dependency change。exact 8-source closure 为新 admission `IIncrementalEnum`、`IHashedItem`、`APILang`、`EnumColor`、`ILangEntry`、`TextComponentUtil`，加立即恢复的既有 `IHasTranslationKey` 与 `RelativeSide`。`IHashedItem`、`EnumColor`、`ILangEntry` 与恢复后的 `RelativeSide` 均与 `4646f509` byte-identical。

`IIncrementalEnum` 仅移除 `ConstantPredicates` import，并把两个内部 `alwaysTrue()` arguments 换为不逃逸的 stateless lambdas；所有 traversal/adjust body 与 public/default signatures 保持。`APILang` 仅以 literal `"mekanism"` 替代 private constructor 中完整 API owner，57 项 table 与 generated keys 不变。`TextComponentUtil` 仅换用 project `FluidStack`，并令两处 raw `Fluid` 与 Task 034 相同地从 default-state legacy block description ID 构造 translatable component。vanilla `Level` 缺失 NeoForge 注入的 `getDescription()`，故两个既有位置精确展开为 dimension language key + exact ID fallback；dispatch order、bound-holder/null/style/copy 与 smartTranslate primitive/string/trailing-format semantics 不变。custom FluidType description override 仍不可用，且不建立 FluidType/custom-name facade。

`IHasTranslationKey` 保留已批准的 NeoForge `TranslatableEnum` parent/Javadoc adaptation，但 default body 恢复 `TextComponentUtil.translate`。`RelativeSide` 删除临时 local key construction，恢复六个 APILang/ILangEntry fields/constructor/delegation；serialized names、key values、codecs 和 direction mappings 不变。

唯一新增 ordinary class `PortableTextColorTest` 恰有 5 methods并在使用前 bootstrap vanilla；加 unchanged `RelativeSideTest` 提供 focused evidence。Task 036 落地 ordinary **66/315**、transfer **2/8**、aggregate **68/323**，三项 jqwik 各 100,000。机械检查 actual remapped JAR 后固定 `TextComponentUtil` outer + `$1`、RelativeSide outer + `$1` 及其它 exact text families，各一次；gate 拒绝 `MekanismAPI`、完整 `ConstantPredicates`、chemical/TriPredicate、concrete `HashedItem`、unexpected nested/test/helper/fixture、NeoForge/Loader 和 startup marker/reference。候选只跑 static packaged smoke，不作 server startup claim。

本任务仅关闭 warning/menu/concrete-slot/recipe 等未来 closure 的 portable text prerequisite；这些调用方、`ChemicalStack` 及所有 registry/config/lifecycle/transfer/persistence/network owners 均未落地。完整说明见 `FABRIC_TEXT.md`。

### Task 037 generic intrusive/defaulted custom-registry foundation

Task 037 只新增两个 platform source：final `FabricDefaultedRegistry<T>` 直接继承 vanilla
`DefaultedMappedRegistry<T>`，`FabricCustomRegistryLifecycle` 固定六态且所有 production 比较均不用
enum switch。前者固定 stable lifecycle 与 intrusive holders，factory 在 super-backed use 前验证
registry/default relation 并捕获 owner thread；mutation/root/freeze 串行且 owner-only，成功 freeze
幂等，任何已进入 super/builder/freeze side effect 的异常 terminal `FAILED`。pure key/location/value/
default/root preflight 不污染状态，未先创建 intrusive holder 时保留 vanilla assertion，dangling holder
由 vanilla freeze 检出。default 的 exact canonical `Holder.Reference`、registry/key/value identity、
`get`/invalid-`byId` fallback 与 optional-no-fallback 都有 executable evidence。

既有 deferred adapter 只作连接性增强：custom context 在 batch state/supplier 前检查，每个 occupied
key/location 在 supplier 前检查，constructed duplicate identity 在 real registration 前检查，并以
`Registry.registerForHolder` 的 exact reference 绑定 holder；registrar seam 仍可 value-only，partial
prefix commit 与 terminal batch failure 不变。普通测试新增一个恰好五方法 suite，既有五方法 suite
不改。

唯一 global mutation 在 `fabricRegistryTest` 的一类一方法 fresh server-side Knot worker：先经 Fabric
Bootstrap mixin 建立尚未最终 freeze 的 vanilla built-ins，使用 `FabricRegistryBuilder.from(this)` 真实
注册一个固定 unique synced root registry，验证 MODDED/SYNCED、root/conflict/default/deferred identity，
随后调用 server automatic path 同一个 `BuiltInRegistries.bootStrap()`，观察 custom lifecycle 自动到
FROZEN。测试不注入 root seam，也不直接首次 freeze custom registry；worker 完成即退出。当前
Task 037 落地时 API/common/platform **64/122/19**；ordinary **67/320**、transfer **2/8**、root **1/1**、aggregate
**70/329**。Task 040 集成态当前为 **98/164/33** 与 ordinary/transfer/root/data-map/aggregate **73/355**、**4/21**、**1/2**、**2/4**、**80/382**；三项 jqwik 各 100,000，ordinary runtime 明确排除 loader-junit。exact remapped-JAR family
为七项且无 synthetic/test/helper。

Task 037 落地时该 foundation 没有 chemical owner、`mekanism:empty`、data map、radiation、`MekanismAPI`、NeoForge
facade、unfreeze/mixin bypass、reload/client class、production startup wiring 或 concurrent-read promise。
Task 040 现以正常 construction-thread freeze 在其上接入 Chemical owner，并只放宽 already-FROZEN 的跨线程幂等调用；不绕过 freeze。固定
Fabric Registry API、vanilla 1.21.1 与 loader-junit 的实查行为及 SHA-256 见 `FABRIC_REGISTRY.md`。

### Task 038 ordinary MekaSuitAbsorption data map

Task 038 只改五份 production Java（API record、三份 platform class、既有 bootstrap），将
`MekaSuitAbsorption` 接到 `Registries.DAMAGE_TYPE`。descriptor 保持对象 identity，catalog collision
按 `(registryKey,id)`；loader 使用 ResourceManager 完整低→高 stack，逐有效 layer 执行 top replace、
顺序 direct/tag writes、顺序 whole-value removals。文件/codec/I/O/conditions 失败整层跳过但本轮其余
有效层仍发布并递增；global owner/registry invariant 在 startup fail-fast，后续则只保留旧 local
snapshot 并明确记录 stale，绝不声称回滚 vanilla 已提交 resources/tags。

owner 由实际 `MinecraftServer` identity 隔离，所有 create/load/query/publish/close 绑定 server thread；
false END 不解析、不发布、不递增，stop 精确移除并关闭。snapshot 只含 copied keys/values 且 immutable；
reference query 走 key，direct query 走当前 registry value→key canonicalization，无 default fallback。
生成 map/tag 两资源以固定 hash 精确打包一次，startup baseline 为 21 项（20×1.0、sonic_boom=0.75）。
完整 oracle、truth table、failure/lifecycle delta、测试与专用 packaged reload gate 见
`FABRIC_DATA_MAPS.md`。Chemical/sync 当时未进入，现由 Task 040 扩展；conditions、Advanced merger/remover、holder mixin/facade 与 broad generic owner 仍未进入。

### Task 040 complete Chemical internal vertical

Task 040 从 clean approval commit `5aadf581` 在隔离 worktree 实现，最终 slice **98/164/33**，净增
61 个 compiled production sources。18 份 `U` source 经 blob identity 与 `4646f509` 精确一致；27 份
`A` source 只作 registry/context、层中 descriptor、service、Fabric config/deferred wrapper、literal
namespace/tag 与 project FluidStack/TriPredicate 的边界适配。新增 17 个 owner/seam source，修改 6 个
platform source并删除旧 `FabricDataMapType`。实际 unique touched production paths 为 69；计划的 68
漏计了正文另述的 loader path。完整逐类分类见 `FABRIC_CHEMICAL.md`。

运行时 root 在 Fabric 同步 registry 中保持 canonical empty + 64 official declarations；五 map 与三项
server policy 组成 immutable monotonic context epoch，server/client 通过 ThreadLocal owner view 隔离。
每个 descriptor 的唯一 network codec 生成有界 opaque chunk，decode 后只在 client executor 解析
name-based registry references；configuration ACK、play reload、generation rejection 与 disconnect/reconnect
cleanup 保持 fail-closed。`common.toml` 五值 owner和 world `general.toml` 三值 preserving fragment 不创建
完整 config facade。真实 gate 证明 nuclear waste 初始 radioactive tank rejection/tooltip、`(1L<<60)+123`
NBT/stream exact round trip、datapack removal 后 epoch2 tank acceptance/tooltip removal、两次不同 session ACK、
same-process reconnect 与 integrated owner isolation。

该 vertical 已以 merge commit `f24ce94e` 集成；mandatory GLM review 的六项 required findings、focused
rereview 唯一要求的 nested different-Chemical frame-restoration assertion，以及主代理完整 semantic/runtime
门禁均已关闭。clean release 在集成文档提交后以 44.56s 通过，恰好一个 JAR SHA-256
`1a49b984bdb766d68337e7ff1055edb7bb21245ae8a3b6aa6de3eb126a52590f`，并在最终评价提交后重跑以对齐
build-info source。不声明 deployment、production、mirror、external Chemical provider/transaction、ingredients、
IRadiationManager/environmental effects、configurable holder/ejector、
recipe/machine/Crusher 或 gameplay 完整性。

## P13 交付结论（2026-10-31，`77118a66`）

- 范围：核心 + Tools / Additions / Generators 四个独立 JAR；各里程碑当前状态以 `docs/milestones/README.md` 为准。
- 发布物：`./build.sh --clean` 的四个 JAR 与 sha256 记于 [W-61 证据](milestones/evidence/p8-release/w61-release.json)；干净四 JAR 服务端装配发布 schema 的 458 值 SERVER 快照（[W-60 证据](milestones/evidence/p8-release/w60-results.json)）。
- 未含：第三方集成（JEI/EMI、CraftTweaker、ProjectE 等）、NeoForge 存档差异（W-65）、长稳与性能（W-66/67）；发布限制见 `docs/milestones/README.md`。
