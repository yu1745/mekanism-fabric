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
- [~] API allowlist：当前编译 42/266 个文件，其中官方基线 41/265，另含 1 个 Mekanism-owned registry-aware NBT 契约；`Action`、energy/heat container contracts 与 platform-neutral enum translation 是首批登记的平台适配，byte-identical 的顺序 long-transfer utility 闭包及 strict energy-handler 三层 default-method 闭包也已纳入；后者仅两份接口清理 NeoForge fluid import/Javadoc 链接，`IMekanismStrictEnergyHandler` 保持 baseline 字节一致（语义与 caller 前置条件见 `FABRIC_ENERGY.md`）；
- [~] lifecycle/registry：官方 deferred wrapper 已组合 Fabric adapter，四个官方 GameEvent 已验证同步注册、canonical holder 和冻结前时序；
- [~] config：Mekanism-owned TOML spec/manager 与官方 12 项 startup 配置已通过缓存、校验、原子保存和 packaged server 门禁；其余八份 Core config 暂未纳入；
- [~] network adapter：configuration `FriendlyByteBuf` 双向 codec、强制 `10.7.19` 协议协商、服务端 task gate、独立 client entrypoint、dedicated-server 打包门禁、两个独立客户端 JVM 的顺序 join，以及错版/同版本显式拒绝/缺失协议客户端的 configuration-phase 拒绝门禁已完成；同进程 reconnect、超时策略与 gameplay dispatcher 尚未完成（见 `FABRIC_NETWORK.md`）；
- [~] holder/storage：官方 energy container、strict/sided/container-backed energy handler defaults、basic/variable heat capacitor、heat handler aggregation、`RelativeSide` 与两种非配置 holder 已通过 storage、indexed/side/automation delegation、动态热系数、weighted transfer 和全方向 side-routing 测试；concrete `EnergyHandler`、config holder、外部 energy/heat API、换算、lookup/transaction、network/machine owners/callers 及其余存储族尚未完成（见 `FABRIC_ENERGY.md`、`FABRIC_HEAT.md`、`FABRIC_HOLDERS.md`）；
- [x] bounded common utilities：官方 `WildcardMatcher`、五个 fair-distribution 源文件、`ChunkUtils` 与 `LambdaMetaFactoryUtil` 原样纳入，并以两个 vanilla 等价替换纳入 `RegistryUtils`，形成 37-file common slice（API 42、platform 10）。Wildcard 覆盖官方行为、`Locale.ROOT`、`TagKey` 与 BMP digit 边界；distribution 通过显式路径 allowlist 直接编译官方 root tests，保留 14 个 JUnit examples 与三个各 100,000 tries 的 jqwik properties，并另有 Fabric 边界测试覆盖 null/empty/zero、插入序 remainder、`int` 最大值和 true-`long` 最大值。`ChunkUtils` 保留四方向 inclusive 行主序与负坐标 floor-section 语义。Java 21 本地测试证明 `LambdaMetaFactoryUtil` 的空名称路径仅绑定调用者已开放的实例 `Field`，非空名称路径按精确签名绑定可能具有变换/副作用的 virtual getter/setter；覆盖八种 primitive 的装箱/拆箱、object/null、两实例重复调用、全部 named setter 分支与原异常传播。调用者负责 field accessibility/module openness、精确 `objType`/方法签名、instance field、boxed value 类型兼容及 primitive setter 非 null；static/final field 不在契约内。`RegistryUtils` 的 Fabric 测试证明 built-in canonical holder/key、NBT ID 拒绝、block path、reference/direct 与 key-only/value-only unbound holder 命名、registered/unknown raw object 与 direct-holder short circuit；正常 vanilla built-ins 下不可达的 `getBEHolder` null fallback 不通过全局 registry 变异强行覆盖。通用 official root-test oracle 为 18 个 baseline-identical 显式路径文件（新增两份 collection 官方测试）（19 JUnit examples + 三个各 100,000 tries 的 jqwik properties）。发布物门禁还精确限定 collection 包只能有 `BiLongMultimap`、`FilterTransformIterator`、`IndexedCuboidMap` 及其两个匿名类和 record 共六项；行为由官方与 Fabric-local Java 21 测试而非启动 marker 证明。三份 collection 源码保持 baseline 字节一致，覆盖双向 mutation、null filter/exhaustion、负 chunk/inclusive box、replacement 与混合清理。Miner/QIO/spatial caller、world/chunk lifecycle/loading、persistence、concurrency 与多线程 mutation，以及 `SyncMapper`、tracked data、机器均未移植或声明；
- [x] platform-neutral codec closure：`DependentMapCodec`、`SequencedCollectionCodec`、`DroppingUnboundedMapCodec` 均与官方 `4646f509` 字节一致；5 个 Java 21 tests 只验证 dependency-first 选择/错误、sequenced 边界/顺序/immutable/partial remainder、dropping-map first-wins/partial-error，JAR 精确限制 1 个 API 与 3 个 common codec class 且无 startup marker。`ModuleContainer`、`AttachedSideConfig`、recipes、data components、config/network owners 与所有 callers 明确延期；不据此声称 persistence、registry、packet、lifecycle-owner 或 serialized-format backward compatibility。
- [ ] distribution 的 energy/item/fluid/chemical transport 调用方、Fabric transaction adapter、lookup、machine/network owner、long-transfer 调用方（`ComponentBackedEnergyHandler`、`EnergyRecipeData`）、concrete `EnergyHandler`、configurable holder、外部 energy API 与 Joule/FE 换算，以及 CachedRecipe、机器基类与 Crusher。

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
