# Mekanism 1.21.1 Fabric 移植基线

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

- [x] 独立 Fabric Loom 构建：MC 1.21.1、Java 21、Mojang official mappings；
- [x] 最终 remapped JAR 的干净 Fabric 服务端冒烟门禁；
- [x] NeoForge 平台依赖矩阵与可重复扫描脚本；
- [~] API allowlist：当前编译 47/266 个文件，其中官方基线 46/265，另含 1 个 Mekanism-owned registry-aware NBT 契约；`Action`、energy/heat container contracts 与 platform-neutral enum translation 是首批登记的平台适配，byte-identical 的顺序 long-transfer utility、六接口 container callback family 及 strict energy-handler 三层 default-method 闭包也已纳入；后者仅两份接口清理 NeoForge fluid import/Javadoc 链接，`IMekanismStrictEnergyHandler` 保持 baseline 字节一致（语义与 caller 前置条件见 `FABRIC_ENERGY.md`）；
- [~] lifecycle/registry：官方 deferred wrapper 已组合 Fabric adapter，四个官方 GameEvent 已验证同步注册、canonical holder 和冻结前时序；
- [~] config：Mekanism-owned TOML spec/manager 与官方 12 项 startup 配置已通过缓存、校验、原子保存和 packaged server 门禁；四个与 `4646f509` 字节一致的 cached supplier 已纳入；Task 025 又以各自精确一个 `ConfigValue` import 替换纳入官方 boolean/byte/short/double cached wrappers，并用 strict boolean、pre-narrowing exact integral byte/short 与 finite/raw-negative-zero double schema 支持承接。测试限定 lazy cache/supplier、validated set、changed/unchanged invalidation 和失败 publication 隔离；除 startup 外的 real config activation、generic/resolvable/list/map/enum cached values、sync/UI/watchers/owners 及 lifecycle/threading 仍未纳入；
- [~] network adapter：configuration `FriendlyByteBuf` 双向 codec、强制 `10.7.19` 协议协商、服务端 task gate、独立 client entrypoint、dedicated-server 打包门禁、两个独立客户端 JVM 的顺序 join，以及错版/同版本显式拒绝/缺失协议客户端的 configuration-phase 拒绝门禁已完成；同进程 reconnect、超时策略与 gameplay dispatcher 尚未完成（见 `FABRIC_NETWORK.md`）；
- [~] holder/storage：官方 energy container、strict/sided/container-backed defaults 及 byte-identical dynamic strict-handler 闭包、basic/variable heat capacitor、heat aggregation、`RelativeSide`、两种非配置 holder 与 `ProxiedHolder`/energy+heat proxy 闭包已通过 predicate/supplier/listener、indexed/side/automation、动态热系数、weighted transfer 和 side-routing 测试；proxy 四源与 `4646f509` 字节一致，null side 固有只读但 reads 原样转发 null，非 null side 的独立 insert/extract permission 与 indexed/aggregate forwarding 已固定；四份 byte-identical energy/heat slot descriptor 源也已作为纯描述闭包纳入，仅固定 flags、energy source-order emptiness 与 live list identity、heat 恒非空；Team Reborn Energy 4.1.0 的首个 caller-conversion/lookup/transaction seam 已完成，但 actual provider registration、owner cache/invalidation、可配置 holders/helpers/exposure、side config execution/ejector、Universal Cable、machine/network/persistence owners 与所有 callers 尚未完成；baseline 不存在此前误称的 concrete `EnergyHandler`（见 `FABRIC_ENERGY.md`、`FABRIC_HEAT.md`、`FABRIC_HOLDERS.md`）；
- [x] bounded common utilities：官方 `WildcardMatcher`、五个 fair-distribution 源文件、`ChunkUtils` 与 `LambdaMetaFactoryUtil` 原样纳入，并以两个 vanilla 等价替换纳入 `RegistryUtils`；当前总 common slice 为 110 files（API 47、platform 14）。Wildcard 覆盖官方行为、`Locale.ROOT`、`TagKey` 与 BMP digit 边界；distribution 通过显式路径 allowlist 直接编译官方 root tests，保留 14 个 JUnit examples 与三个各 100,000 tries 的 jqwik properties，并另有 Fabric 边界测试覆盖 null/empty/zero、插入序 remainder、`int` 最大值和 true-`long` 最大值。`ChunkUtils` 保留四方向 inclusive 行主序与负坐标 floor-section 语义。Java 21 本地测试证明 `LambdaMetaFactoryUtil` 的空名称路径仅绑定调用者已开放的实例 `Field`，非空名称路径按精确签名绑定可能具有变换/副作用的 virtual getter/setter；覆盖八种 primitive 的装箱/拆箱、object/null、两实例重复调用、全部 named setter 分支与原异常传播。调用者负责 field accessibility/module openness、精确 `objType`/方法签名、instance field、boxed value 类型兼容及 primitive setter 非 null；static/final field 不在契约内。`RegistryUtils` 的 Fabric 测试证明 built-in canonical holder/key、NBT ID 拒绝、block path、reference/direct 与 key-only/value-only unbound holder 命名、registered/unknown raw object 与 direct-holder short circuit；正常 vanilla built-ins 下不可达的 `getBEHolder` null fallback 不通过全局 registry 变异强行覆盖。通用 official root-test oracle 为 18 个 baseline-identical 显式路径文件（新增两份 collection 官方测试）（19 JUnit examples + 三个各 100,000 tries 的 jqwik properties）。发布物门禁还精确限定 collection 包只能有 `BiLongMultimap`、`FilterTransformIterator`、`IndexedCuboidMap` 及其两个匿名类和 record 共六项；行为由官方与 Fabric-local Java 21 测试而非启动 marker 证明。三份 collection 源码保持 baseline 字节一致，覆盖双向 mutation、null filter/exhaustion、负 chunk/inclusive box、replacement 与混合清理。Miner/QIO/spatial caller、world/chunk lifecycle/loading、persistence、concurrency 与多线程 mutation，以及 `SyncMapper`、tracked data、机器均未移植或声明；
- [x] collection utility closure：`BiMultimap`、`EmptySequencedMap`、`HashList`、`LRU` 与官方 `4646f509` 字节一致；Java 21 黑盒测试仅固定双向索引、ordered-unique 转换、unique non-null LRU 顺序及 immutable empty sequenced-map 语义，JAR 精确为 12 个 collection class entries。security/frequency/filter/sync、QIO、`ModuleContainer`/gear/data components/codecs、GUI/recipe viewers/client lifecycle、networking/persistence/registries 及所有 callers/owners 均延期；不承诺线程安全、并发修改或通用 mutable-view。
- [x] platform-neutral codec closure：`DependentMapCodec`、`SequencedCollectionCodec`、`DroppingUnboundedMapCodec` 均与官方 `4646f509` 字节一致；5 个 Java 21 tests 只验证 dependency-first 选择/错误、sequenced 边界/顺序/immutable/partial remainder、dropping-map first-wins/partial-error，JAR 精确限制 1 个 API 与 3 个 common codec class 且无 startup marker。`ModuleContainer`、`AttachedSideConfig`、recipes、data components、config/network owners 与所有 callers 明确延期；不据此声称 persistence、registry、packet、lifecycle-owner 或 serialized-format backward compatibility。
- [x] holiday value foundations：`KnownDate`、`MonthlyDate`、`YearlyDate`、`HolidayMessage` 四源与 `4646f509` 字节一致，Task 022 落地时总 slice 为 42/88/10；五个确定性测试只固定 exact yearly month/day（含 Feb 29 value）、monthly month predicate、可跨午夜的 `now()` snapshot、message 深数组内容 equality/hash 与 accessor/array alias。`YearlyDate.now()` 使用 JVM 默认 clock/zone；`HolidayMessage` 保留 component 与 caller array 引用，不是 deep immutable。JAR 的 holiday package 精确只有四个同名 class 各一次且无 nested/synthetic/test/helper/fixture，无 startup marker。`Holiday`、`HolidayManager`、全部 concrete holidays、player notification、translations/colors/skins、client holiday state、lifecycle/event wiring、persistence、networking、registries、config 及 world/tick/thread ownership 全部延期。
- [x] spatial/color/effect foundations：`Color`、`CustomEffect`、`Pos3D`、`Quaternion`、`Range3D`、`MultipartUtils`、`StatUtils` 七个源文件与 `4646f509` 字节一致，该闭包落地时总 slice 为 42/88/10；12 个 Java 21 黑盒测试限定 color/stat/effect、position/range/rotation/quaternion 与 direct multipart raycast 语义，JAR 精确限制 direct common-lib 四项、effect 一项、math 三项、Multipart 三项、StatUtils 一项且无测试 helper。所有 renderers/particles/GUI/recipe viewers/client lifecycle、transmitters/multipart blocks/gear、machines/block types/entities、packet tracking/network ranges、SPS/multiblock、additions/generators、external energy、world/chunk lifecycle、registries/config/persistence callers/owners 延期；不声明 rendering integration、entity/player lifecycle、block registration、networking、thread safety 或 collision-owner guarantee；
- [x] registration/domain metadata foundations：`BinTier`、`FactoryTier`、`QIODriveTier`、`IResource`、`MiscResource`、`BlockResourceInfo` 六源均与 `4646f509` 字节一致；6 个 Fabric-local 测试固定 bin 容量/BaseTier/config reference 恢复、factory process progression、QIO type/count/base（含 `16_000_000_000L`）、完整资源 suffix/accessor 与 ordinary/raw/luminous/exceptional property construction。`BlockResourceInfo` 仅证明 property construction，不证明 registered-block behavior。`PrimaryResource`、`ResourceType`、ore types/config、tags/datagen/registration、block/item registries and implementations、factories/bins/QIO items/recipes/attachments、`EnumUtils`、`TierConfig` 及 real config ownership/load/reload/lifecycle、transport/tank owners/callers、serialization/network/persistence/registry lifecycle/world behavior 均延期；不声明 broader Fabric integration；
- [x] transport/tank tier metadata：Task 029 纳入 `CableTier`、`ChemicalTankTier`、`ConductorTier`、`FluidTankTier`、`PipeTier`、`TransporterTier`、`TubeTier`，当前 slice 为 47/110/14；五个 lookup enum 仅以自身 `values()` 替代 `EnumUtils` 数组，四个 tank/pipe/tube enum 仅以 exact `1_000` 替代 NeoForge bucket 常量。该值保持 Mekanism mB/config metadata，不是 Fabric 81,000-droplet transaction unit；12-method tier oracle 与 exact 13-class JAR gate 只证明 metadata，所有 storage/owner/lifecycle 仍延期；
- [x] tiny energy accounting utility：官方 `LastEnergyTracker` 与 `4646f509` 字节一致；同 game time 累加 current bucket 而不改 visible last，任何不同（含跳跃/递减）时间戳发布旧 bucket，setter 仅直接改 client-sync visible value 至下一次 transition。JAR 的 direct `mekanism/common/lib` 中它与 `WildcardMatcher` 及新增 `Color` 两项共同构成精确四项且各一次，无 startup marker。唯一生产 caller `TileEntityMekanism`、world tick/client synchronization、network owner/persistence、machines/networks，以及该 utility 与 Task 023 energy seam 的连接均延期；
- [x] tile behavior contract interfaces：`IHasDumpButton`、`IHasGasMode`、`IHasMode`、`IHasVisualization`、`ITileActive`、`IComparatorSupport` 六源均与 `4646f509` 字节一致；四个 Fabric-local tests 只固定 mode/action 精确分派与原样 tank index、visualization default/override/toggle、active default/override/`NO_DELAY`/raw state，以及 comparator default/override/两个 boundary-level observation。JAR direct package 精确为这六个接口且各一次，无 startup marker；所有 tile/machine 实现及 integration 均延期；
- [ ] distribution/long-transfer 的所有 callers（含 `ComponentBackedEnergyHandler`、`EnergyRecipeData`）、`EnergyContainerHelper`/`ConfigEnergyContainerHolder`、side configuration/ejector、Universal Cable、actual Energy provider registration 与 owner caching/invalidation、machine/network/persistence owners、CachedRecipe、机器基类与 Crusher；不再延期一个 baseline 中不存在的 `EnergyHandler`。

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

这只是 schema/value support 与 tier metadata，不激活 `tiers.toml`。Task 029 后 `TierConfig`、`MekanismConfig`/其他 real config files、其余 cached value kinds、config sync/UI/watchers/world restart、全部 tier owners/callers、machines/blocks/items、capability registration、external energy/fluid/chemical transactions、networking/serialization/persistence/lifecycle/threading 仍全部延期。

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

明确延期：`ConfigHolder`/`ConfigInfo`/`DataType`/`ISideConfiguration`/configurable holders/helpers、`TileComponentConfig`/`TileComponentEjector`、actual block/item/entity provider registration 与 owner cache/invalidation、cables/networks/emission/machines、hardcoded FE config activation、item/fluid/chemical/heat adapters、persistence、packets/client sync 和 thread safety。

### Task 024 container callback contracts 落点

Task 024 落地时 allowlist 为 API/common/platform **47/88/14**。`ContainerInteraction`、`InContainerGetter`、`IntContainerInteraction` 与 `LongContainerInteraction` 四份官方 API 源原样加入，并与 oracle `4646f509` byte-identical；加上既有 `InContainerGetterLong` 与 `LongToLongContainerInteraction`，direct `mekanism/api/container/` 发布物精确只有这六个接口且各一次。一个 Fabric-local 同包测试类中的恰好四个 deterministic JUnit methods 固定 generic stack/result identity、任意/负 index、nullable/physical side、exact `SIMULATE`/`EXECUTE`，以及 raw `int`/true-`long` signed boundaries（含 `>2^53`）不规范化。测试类由 exact package gate 排除，不增加 startup marker。

这只是 callback contracts，不实现 transfer policy。`ChemicalUtils`、`ExtendedFluidHandlerUtils`、chemical/fluid handlers、component-backed/recipe-upgrade owners、actual transfer/platform transactions、registries、serialization/networking 与 lifecycle/thread ownership 全部延期；不声明 broader container integration。

### Task 025 primitive cached-value sources 落点

Task 025 落地时 allowlist 为 API/common/platform **47/92/14**。官方 `CachedBooleanValue`、`CachedByteValue`、`CachedShortValue` 与 `CachedDoubleValue` 各自相对 oracle `4646f509` 精确只有一个 import 替换：NeoForge `ModConfigSpec.ConfigValue` 改为 `MekanismConfigSpec.ConfigValue`；其余 source bytes 不变。Fabric spec 只新增 strict `define(String, boolean)` 与 typed byte/short `defineInRange`：boolean 仅接受 `Boolean`；byte/short 复用既有 exact-integral long normalizer，在验证 signed-long representability、primitive bounds 和 configured range 后才收窄，拒绝 fraction、non-finite、overflow/range miss，不 truncation/wrap/round/saturate。既有 int/long/double 行为、strict long TOML lexical preflight、同步及 parse→correct/persist→atomic publish 顺序不变。

一个 Fabric-local 同包测试类恰好四个 `@Test`，每个 wrapper 一个，使用真实 `BaseMekanismConfig`、Builder/spec 和临时 TOML，固定 boolean true/false/错误类型 correction 与 lazy/set/invalidation；byte/short signed primitive/configured endpoints、programmatic/TOML overflow/fraction/radix 拒绝、set failure isolation 和 cache refresh；double finite endpoints、runtime Float/Double normalization、raw `-0.0` load/cache/set 与官方 signed-zero unchanged invalidation，以及 correction/write-failure publication isolation。JAR direct config-value family 从五项扩展为精确九项且各一次，拒绝 `CachedConfigValue`、resolvable/enum/map/oredictionificator 与 test/helper/fixture；无 startup marker。新增 real config activation、generic/resolvable/list/map/enum define/cached values、sync/UI/watchers/owners 仍延期。

### Task 026 block-state descriptors 落点

Task 026 落地时 allowlist 为 API/common/platform **47/96/14**。`IStateStorage`、`IFluidLogType`、`FluidLogType`、`TransmitterType` 四源均与 oracle `4646f509` byte-identical。一个同包 Fabric-local 测试类恰好三个 `@Test`：固定 `IFluidLogType.isEmpty()` 对 live getter 的 exact `Fluids.EMPTY` identity；`FluidLogType` 的 exact order、serialized names、canonical fluid identity、EMPTY-only emptiness 与 `values()`/`valueOf()`；以及 `TransmitterType` 的 exact seven-value order、SMALL/LARGE assignment、6/8 center size 与 `values()`/`valueOf()`。marker-only `IStateStorage` 不作 vacuous reflection test，其编译与 package identity 是唯一证据；无 startup marker。测试仅以既有 narrow `SharedConstants`/`Bootstrap` pattern 满足 vanilla static initialization，不作 custom registry mutation。

发布物门禁要求 exact `mekanism/common/block/states/` package 仅含 `IStateStorage.class`、`IFluidLogType.class`、`FluidLogType.class`、`TransmitterType.class`、`TransmitterType$Size.class` 各一次，并拒绝其它 direct/nested/synthetic/test/helper/fixture class。fluidlogging block implementation/state-definition/world update/persistence、transmitters/cables/networks/rendering、actual block registration、codecs/serialization/networking、lifecycle/thread ownership 及所有 callers/implementers 均延期；不声明 marker 的 runtime behavior。

### Task 027 inventory metadata contracts 落点

Task 027 落地时 allowlist 为 API/common/platform **47/99/14**。这是经 bounded scouts 确认没有诚实四源闭包后显式批准的 coherent **3-source exception**：`GuiComponents` 与 `ContainerSlotType` 和 oracle `4646f509` byte-identical；`SlotOverlay` 只移除 `MekanismUtils`/`ResourceType` imports，并把构造改为精确 `ResourceLocation.fromNamespaceAndPath("mekanism", "gui/slot/" + texture)`。其 11 个结果 ID 与官方行为逐项一致，均为 `mekanism:gui/slot/overlay_*.png`。一个 Fabric-local 测试类恰好四个 `@Test`，固定七项 slot type、十一项 overlay/order/ID、全部 18×18 与 enum contracts，以及 test-local dropdown/toggle enums 的 exact `Component`/`ResourceLocation` identity、default-null/overridden icon 和 forwarding；无 bootstrap、registry/global mutation、renderer 或 texture I/O，也无 startup marker。

静态发布物门禁要求 `GuiComponents.class`、`GuiComponents$IDropdownEnum.class`、`GuiComponents$IToggleEnum.class` 各一次且同名前缀无其它 nested/test/helper；direct `mekanism/common/inventory/container/slot/` 只允许 `ContainerSlotType.class` 与 `SlotOverlay.class` 各一次，拒绝其它 direct/nested/synthetic/test/helper/fixture。所有 screens/widgets/rendering/runtime texture validation、concrete slots/`IInsertableSlot`/inventory/menu/player/window owners、GUI lifecycle、networking/sync、registries/serialization/thread ownership 全部延期；这里只声明 metadata contracts。

### Task 028 deferred codec/criterion/loot registration 落点

Task 028 落地时 allowlist 为 API/common/platform **47/103/14**。四个新纳入的官方 common source `DeferredMapCodecHolder`、`DeferredMapCodecRegister`、`DeferredCriterionTrigger`、`LootFunctionDeferredRegister` 均与 oracle `4646f509` byte-identical。与这四源明确分开的唯一 production 修复是既有 Fabric adaptation `MekanismDeferredHolder.value()` 从 `R` 改为 covariant `T`，方法体仍为 `return get();`。因类约束 `T extends R`，它合法 override `Holder<R>.value(): R` 且保持 subtype contract；`R`/`T` 均擦除为 `Object`，JVM descriptor 与运行行为不变，本构建不生成额外 bridge。

一个同包 Fabric-local 测试类恰好四个 `@Test`，覆盖 MapCodec function overload 的 lazy ID/key/subtype/canonical binding 与 pre-bind failure，supplier/custom-holder subtype、duplicate/lifecycle delegation 和 covariant exact identity，最小本地 criterion 的 exact instance/trigger identity，以及最小本地 loot function 的 basic/codec lazy/key/codec identity。所有提交都经 package-private registrar seam，不改变 custom/built-in registry；criterion 只用既有标准 `SharedConstants`/`Bootstrap` vanilla 初始化，loot/codec 不作 serialization 或 datapack execution，也无 startup marker。

JAR gate 要求 direct registration family 精确为 `INamedEntry`、`MekanismDeferredHolder`、`MekanismDeferredRegister`、`MekanismDeferredRegister$Registrar`、`DeferredMapCodecHolder`、`DeferredMapCodecRegister`，impl family 精确为 `GameEventDeferredRegister`、`DeferredCriterionTrigger`、`LootFunctionDeferredRegister`，并拒绝其它 nested/test/helper。real production declarations、global registration/freeze、datapack codec execution、serialization、loot/advancement runtime、owners/network/threading 全部延期。

### Task 029 transport/tank tier metadata 落点

当前 API/common/platform allowlist 精确为 **47/110/14** 且各 slice 内唯一。新增的七份 official common metadata source 是 `CableTier`、`ChemicalTankTier`、`ConductorTier`、`FluidTankTier`、`PipeTier`、`TransporterTier` 与 `TubeTier`。相对不变的 oracle，production adaptation 边界只有两组：`CableTier`、`ConductorTier`、`PipeTier`、`TransporterTier`、`TubeTier` 移除 `EnumUtils` import，并在各自 `get(BaseTier)` 中以自身 `values()` 替代对应 `EnumUtils.*_TIERS`，保持声明顺序、enum identity、所有 present mapping 与 absent-tier `BASIC` fallback；`ChemicalTankTier`、`FluidTankTier`、`PipeTier`、`TubeTier` 移除 NeoForge `FluidType` import，并把每个 compile-time `FluidType.BUCKET_VOLUME` 精确替换为 integer literal `1_000`，保持全部现值与既有 `int`/`long` 表达式行为。`1_000` 表示从 NeoForge 常量继承的 Mekanism mB/config metadata bucket basis；它刻意不是 Fabric Transfer API 的 81,000-droplet transaction unit，也未引入 Fabric fluid constant 或 conversion adapter。

`TierMetadataTest` 精确从 5 增至 12 个 `@Test`，七个新增方法逐 enum 固定完整 order/BaseTier/base tuple/current getter tuple；五个 lookup enum 固定全部 present mapping 与 `CREATIVE`/null absent fallback，chemical tank 固定 serialized names，conductor 以 RGBA channels 和 packed value 固定 conduction/heat-capacity/insulation/color tuple。每份测试都用 loaded `CachedIntValue`/`CachedLongValue`/`CachedDoubleValue` 覆盖该 enum 的全部 mutable config-reference field，以 distinct signed/large/fractional 值防 narrowing/wrong-field wiring，并验证后续 `set` 实时可见；private prior references 通过 reflection 捕获，在 `finally` 中全部恢复，随后用 identity assertion 核对（含 prior null）。静态 remapped-JAR gate 要求 direct `mekanism/common/tier/` 精确为原六项加新增七项共 13 个 class entry，各一次，无 nested/test/helper/fixture 泄漏；无 startup marker。

该闭包只承认 tier metadata。`TierConfig` 与 `tiers.toml` activation、Fabric fluid/chemical storage/conversion/transactions、cables/pipes/tubes/transporters/conductors/tanks 及其所有 owners/callers、registry/network/persistence/lifecycle/threading 全部延期。
