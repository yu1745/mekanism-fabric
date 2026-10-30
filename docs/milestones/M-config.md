# M-config — Mekanism 配置 owner 与同步

验收：非默认配置值真机加载并被实际 consumer 采用，且服务端→客户端同步一致。详细历史与证据原文：`docs/archive/TASK052_MACHINE_CONFIG_CONTINUATION.md`、`docs/archive/TASK052_GENERAL_CONFIG_BOUNDARY.md`。

## 现状

| 配置 | owner | 状态 |
|---|---|---|
| gear | `FabricServerConfig`（世界 SERVER 固定集合） | 已提交，真机/握手证据见归档 |
| machine storage / usage / tiers | 同上 | 已提交且**已验证**：Crusher 容量 44444、耗能 37，Basic Energy Cube 容量 1234567（mcdebug 读实际 BE）；paired-network smoke PASS，同步 235 值 |
| common / startup | canonical `MekanismConfig.common/startup`（进程级，bootstrap manager 加载） | 已提交且**已验证**：armor=13 经真实注册 consumer 读到，holidays=false 经 `HolidayManager` 读到 |
| general | 加入世界 SERVER 固定集合；`ServerPolicy`/`RadiationValidation` 由其派生；移除 `GeneralFragment`；payload 支持字符串列表（上限 512 值） | 已提交 `741d580d`（wip），**待验证** |

general 已验证：`compileJava`/`compileTestJava`、`MekanismConfigSpecTest` 通过。

## 下一步

1. 一次真机非默认 general 值检查：优先 miner（`minerMaxRadius`，含客户端 GUI 输入上限）或 oredictionificator（`validOredictionificatorFilters` 列表同步），另需覆盖 `qioTransactionTest` 的 `FabricGearConfigLifecycleTest`。
2. 通过后：本表改"已验证"，在 `PORT_MATRIX`/`FABRIC_PORT` 补一行结论，本文件移入 `docs/archive/`。
3. 对 `741d580d` 做提交后风格审查（`PORT_COMMIT_REVIEW_PROMPT.md`），结论追加到本文件"验收证据"。

## 未决项

- `alphaWarning` 仅 development 环境定义：现以开发/发布两端条件处理，需确认发布构建的 schema 行为。
- 集成服跨世界切换与热重载未验证；PLAY 阶段热重载、文件 watcher、完整 world/client 合并均为非目标。

## 非目标

Additions/Generators/Tools、通用 Forge 配置兼容层、第三方能量模组接入、新测试矩阵。

## 验收证据

见上表与归档文档；`741d580d` 之后的新证据追加在此。

2026-10-30 / W-01.1：P0 全量构建发现 `FabricGearConfigLifecycleTest` 仍构造不含 general 的旧快照，导致 `Config snapshot schema mismatch`。现有测试补齐 general 后，完整构建与 QIO 50 tests 全部通过（其中生命周期 1 test）；未放宽生产 schema 校验，也未增加测试。非默认 general 真机与 alphaWarning 开发／发布核对仍待 W-12/13。

P0 paired-network 重跑通过：5 个 SERVER configs、304 个值，两次 fresh client 均 client_applied 并获得 server ACK；这补充默认值完整 schema 的实际网络证据，不替代 W-12 非默认 consumer 验收。

### P2 检查点（2026-10-30，基线 `3a3a6ba8`）

W-12 consumer 实机证据：隔离世界 general.toml 的 `digital_miner.maxRadius` 从 32 改为 7 后正常启动集成服。完整 BlockItem 路径放置 Digital Miner，实际 `setRadiusFromPacket(999)` 后服务端 radius=7；真实 GUI Config 按钮打开 `GuiDigitalMinerConfig`，客户端 tile.radius=7，`radiusField.textField.maxLength`=1。读数与截图在 [p2-config](evidence/p2-config/miner-radius-seven.png)，同目录包含 server-radius/client-radius/gui-radius-length JSON。无 GUI error。此证据证明非默认值被服务端和 GUI consumer 使用；集成服共享配置实例，独立客户端非默认快照仍待验证，不能用它替代远端安装路径。

W-13 focused test：`./gradlew -p fabric qioTransactionTest --tests mekanism.fabric.config.FabricGearConfigLifecycleTest --console=plain` PASS（8 秒），原生命周期断言全部通过；未新增测试。

`741d580d` 真实 diff 审查：General 加入 canonical 世界 CONFIGS，并移除第二 GeneralFragment writer；Chemical ServerPolicy/RadiationValidation 改由同一 owner 在 start/stop 发布／清理。serverSnapshot 对列表复制，payload 保留 session/path/type 校验与列表长度约束，installRemoteValues 对完整 schema 校验并先规范化所有值再发布，失败清理已安装 config；未发现该接线引入重复 owner 或删除官方配置行为。原提交遗漏测试快照中的 general 已由 P0 `dba1010d` 修复。alphaWarning 源码核对：GeneralConfig 仅 dev 定义，production 使用 ALWAYS_TRUE；接收端仅对该字段适配开发／发布 schema，其余字段保持精确校验。开发／发布跨环境实际运行仍待补，W-13 不标完整完成。

现场已正常停止自有会话，完整隔离世界恢复至本轮前备份，逐文件 SHA-256 一致；临时 debug mod、Kotlin runtime、wrapper 链接及 Xvfb 清理。无生产源码修改。下一步：独立客户端非默认 General 同步、alphaWarning 发布 schema 实证，然后 P2 门禁与 W-14 收尾。
