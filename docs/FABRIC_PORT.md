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
- [~] 官方 API allowlist：已编译 25/265 个文件，其中 `Action` 是首个登记的平台适配；
- [ ] lifecycle、registry、config、network adapter；
- [ ] holder/storage、CachedRecipe、机器基类与 Crusher。

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
