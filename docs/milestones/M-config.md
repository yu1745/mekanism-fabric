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
