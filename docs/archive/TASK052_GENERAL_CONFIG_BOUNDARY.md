# Task052 — GeneralConfig 完整 owner 边界待裁决

基线 `758ad05f`；2026-10-30。已纳机器 SERVER 配置与 COMMON/STARTUP canonical owner 均已完成实际验证及提交后审查。下一步不启动 Additions，而是核对现有核心机器仍未接通的 GeneralConfig。

## 已确认的缺口与真实调用

- `MekanismConfig.general` 已纳片但未加载。当前 `FabricChemicalConfig.GeneralFragment` 唯一加载世界 `serverconfig/Mekanism/general.toml`，只发布 radiation.enabled、energy_conversion.feConversionRate、blacklistForge 三字段。
- `TileEntityDigitalMiner.setRadius` / NBT 与组件加载 / `MinerEnergyContainer` 读 general.minerMaxRadius；客户端 `GuiDigitalMinerConfig` 的输入上限长度读同一字段。只加载服务端而不同步会留下双方参数不一致。
- `SecurityUtils` 直接读 allowProtection、opsBypassRestrictions；现有文件设置未到 canonical instance。
- `ItemBlockCardboardBox` 读 cardboardModBlacklist；`TileEntityOredictionificator` 与 `OredictionificatorItemFilter` 使用 validOredictionificatorFilters 及其 invalidation listener。这两项不能通过略过非标量字段来假装完整接通。

## 最小真实边界

建议一次接通整份已纳 GeneralConfig，不再新增 partial fragment：

1. 世界 owner 固定集合加入 canonical general，移除原 GeneralFragment 的第二 schema/文件 writer；COMMON owner 不动。
2. 原 ChemicalConfigAccess.ServerPolicy 由已加载的 canonical general 三字段导出；保留 ChemicalDataContext 的 epoch publication 与客户端化学快照，而不是再建立一个辐射/能量转换 owner。`FabricChemicalDataMapSession.load` 在发布 epoch 时读取该 policy，因此启动/停止事件顺序须明确：先加载 general、再发布 policy、再加载 data maps；不要用默认 policy 绕过顺序。
3. 现有 typed snapshot 增加仅此 schema 所需的字符串列表（cardboard namespace 列表、oredictionificator 编码后的 resource-location 列表），沿用现有 config normalizer。GeneralConfig 没有任意 map/object 协议需求；`CachedOredictionificatorConfigValue` 的 map 是列表解析结果，不直接传 map。nutritionalPasteSaturation 的 backing value 已为 Double，不需新增 float wire type。
4. 当前四份共 235 值，上限 256；完整 general 加入后需要按实际 schema 调整边界，不能排除字段来保留旧上限。general 的 alphaWarning 仅 development 环境定义：需按官方 dev-only 语义处理开发/发布两端 schema 差异，不伪造字段或无审计放宽整个 schema 校验。
5. 连接替换/断线缓存与 listener 清理覆盖同一完整配置，集成服仍共享 canonical instance。既有 tests 只在必要 API 变更时调整，不主动加用例；复用构建与一次真实 miner 或 oredictionificator 非默认行为验证。

## 范围与裁决

这比已完成的四配置标量闭包多出：原 general policy owner 合并、字符串列表传输，以及 dev-only schema 差异处理。此前批次明确把 general 排除在外，故先请求用户同意该完整边界，再修改生产代码。

不包含完整 world/client 配置合并、文件 watcher/PLAY 热重载、通用 Forge 配置兼容层、第三方能量模组接入、Additions/Generators/Tools、新测试矩阵。这里仅记录真实 owner 与必要调用链，不把未测试边角当作必须扩张的任务。用户已在本会话的范围选择中明确批准“同意完整接通 GeneralConfig”，该完整边界转为 GO；按上述非目标与不增加测试矩阵约束实施。批准前未改 GeneralConfig 或协议。
