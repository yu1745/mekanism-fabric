# M-config — Mekanism 配置 owner 与同步

验收：非默认配置值真机加载并被实际 consumer 采用，且服务端→客户端同步一致。详细历史与证据原文：`docs/archive/TASK052_MACHINE_CONFIG_CONTINUATION.md`、`docs/archive/TASK052_GENERAL_CONFIG_BOUNDARY.md`。

## 现状

| 配置 | owner | 状态 |
|---|---|---|
| gear | `FabricServerConfig`（世界 SERVER 固定集合） | 已提交，真机/握手证据见归档 |
| machine storage / usage / tiers | 同上 | 已提交且**已验证**：Crusher 容量 44444、耗能 37，Basic Energy Cube 容量 1234567（mcdebug 读实际 BE）；paired-network smoke PASS，同步 235 值 |
| common / startup | canonical `MekanismConfig.common/startup`（进程级，bootstrap manager 加载） | 已提交且**已验证**：armor=13 经真实注册 consumer 读到，holidays=false 经 `HolidayManager` 读到 |
| general | 加入世界 SERVER 固定集合；`ServerPolicy`/`RadiationValidation` 由其派生；移除 `GeneralFragment`；payload 支持字符串列表（上限 512 值） | 已验证：非默认 miner 服务端／GUI consumer、两次独立客户端半径 7、开发／发布 schema 实证通过 |

general 已验证：`compileJava`/`compileTestJava`、`MekanismConfigSpecTest` 通过。

## 下一步

P2 W-12–14 已完成；继续 ROADMAP P3 W-15 核心平台 owner 缺口核对。

## 未决项

- alphaWarning 开发／发布 schema 已实证；没有额外执行跨开发／发布混合客户端矩阵，该矩阵不作为本里程碑完成要求。
- 集成服跨世界切换与热重载未验证；PLAY 阶段热重载、文件 watcher、完整 world/client 合并均为非目标。

## 非目标

Additions/Generators/Tools、通用 Forge 配置兼容层、第三方能量模组接入、新测试矩阵。

## 验收证据

见上表与归档文档；`741d580d` 之后的新证据追加在此。

2026-10-30 / W-01.1：P0 全量构建发现 `FabricGearConfigLifecycleTest` 仍构造不含 general 的旧快照，导致 `Config snapshot schema mismatch`。现有测试补齐 general 后，完整构建与 QIO 50 tests 全部通过（其中生命周期 1 test）；未放宽生产 schema 校验，也未增加测试。非默认 general 真机与 alphaWarning 开发／发布核对仍待 W-12/13。

P0 paired-network 重跑通过：5 个 SERVER configs、304 个值，两次 fresh client 均 client_applied 并获得 server ACK；这补充默认值完整 schema 的实际网络证据，不替代 W-12 非默认 consumer 验收。

### P2 检查点（2026-10-30，基线 `3a3a6ba8`）

W-12 consumer 实机证据：隔离世界 general.toml 的 `digital_miner.maxRadius` 从 32 改为 7 后正常启动集成服。完整 BlockItem 路径放置 Digital Miner，实际 `setRadiusFromPacket(999)` 后服务端 radius=7；真实 GUI Config 按钮打开 `GuiDigitalMinerConfig`，客户端 tile.radius=7，`radiusField.textField.maxLength`=1。读数与截图在 p2-config，同目录包含 server-radius/client-radius/gui-radius-length JSON。无 GUI error。此证据证明非默认值被服务端和 GUI consumer 使用；集成服共享配置实例，独立客户端非默认快照仍待验证，不能用它替代远端安装路径。

W-13 focused test：`./gradlew -p fabric qioTransactionTest --tests mekanism.fabric.config.FabricGearConfigLifecycleTest --console=plain` PASS（8 秒），原生命周期断言全部通过；未新增测试。

`741d580d` 真实 diff 审查：General 加入 canonical 世界 CONFIGS，并移除第二 GeneralFragment writer；Chemical ServerPolicy/RadiationValidation 改由同一 owner 在 start/stop 发布／清理。serverSnapshot 对列表复制，payload 保留 session/path/type 校验与列表长度约束，installRemoteValues 对完整 schema 校验并先规范化所有值再发布，失败清理已安装 config；未发现该接线引入重复 owner 或删除官方配置行为。原提交遗漏测试快照中的 general 已由 P0 `dba1010d` 修复。alphaWarning 源码核对：GeneralConfig 仅 dev 定义，production 使用 ALWAYS_TRUE；接收端仅对该字段适配开发／发布 schema，其余字段保持精确校验。开发／发布跨环境实际运行仍待补，W-13 不标完整完成。

现场已正常停止自有会话，完整隔离世界恢复至本轮前备份，逐文件 SHA-256 一致；临时 debug mod、Kotlin runtime、wrapper 链接及 Xvfb 清理。无生产源码修改。下一步：独立客户端非默认 General 同步、alphaWarning 发布 schema 实证，然后 P2 门禁与 W-14 收尾。

### P2 W-12/13 运行证据补齐（2026-10-30）

W-12 独立远端补验通过：复用现有 paired-network smoke 的临时本地副本，在 disposable 服务端启动前写 `world/serverconfig/Mekanism/general.toml` 的 `digital_miner.maxRadius=7`；两个 fresh 客户端各自临时加载 mcdebug 后读取实际 `MekanismConfig.general.minerMaxRadius.get()`，两次均为 **7**。原 paired 协商／加入 play 门禁也 PASS。原始结果：client 1、client 2。诊断目录 `build/fabric-paired-network-smoke/20261030T111730Z-1840548/`。第一次只因 Unix socket 路径过长读取失败；修正为使用客户端端点发现文件（自动 TCP fallback）后重跑成功，失败运行不计通过。与上节实际 miner 服务端钳制、客户端 GUI 证据合并，完成非默认配置从文件到独立客户端及 consumer 的链路。

W-13 alphaWarning 实证：复用 P1 未改动 remapped JAR 执行现有 packaged smoke 的临时副本（只省略重复 build，保留全部静态及真实启动断言），PASS 并到达 Done。保留 disposable server 后读取 `world/serverconfig/Mekanism/general.toml`，发布环境确实没有 alphaWarning；此前 dev 实机生成文档含 alphaWarning=true。两份实际 TOML 的读取结果见 schema 证据。这确认各环境 schema 行为；未宣称额外执行开发／发布混合客户端矩阵。focused lifecycle PASS 与 `741d580d` 审查见上一节。

临时脚本副本已移除，正式 smoke 脚本未改；paired 的客户端／服务器均由原脚本清理，临时调试依赖不进入发行物。完整 P2 构建与阶段 machine 门禁正在收尾；W-14 在门禁通过后归档。

W-14 收尾：P2 完整 `./fabric-build.sh` PASS（3m 3s），`verifyApiSliceNoNeoForge` PASS，`fabric-machine-smoke: PASSED`。focused lifecycle、两客户端非默认同步和 packaged 真机启动均已通过；本轮仅验收文档与证据变更，无生产／slice／测试新增。P2 前序 consumer 提交 `17bf4ab0`，本次收尾后 M-config 归档，下一阶段 P3。
