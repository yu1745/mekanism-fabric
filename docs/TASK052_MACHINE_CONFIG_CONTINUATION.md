# Task052 — 已纳机器配置接线

## 2026-10-30 实现边界

基线 `bfba348f`（GearConfig 闭包 `6a653f77` 已按移植风格 prompt 审查）。用户要求修复后继续移植，并明确不主动增加测试覆盖、避免过度防御性编程。

下一批优先接通已经纳片的核心机器配置，而非启动 Additions 等新模块：

- `MekanismConfig.storage` → `MekanismBlocks` energy attributes → `MachineEnergyContainer` 容量。
- `MekanismConfig.usage` → 同一机器属性/teleporter/chargepad 等实际耗能调用。
- `MekanismConfig.tiers` → `TierConfig` 构造时设置官方 tier cached references → 能量方块、传输器、储罐等真实容量/速率及客户端 tooltip。

三份均是官方 SERVER 标量配置，类与调用方已经在当前 slice 中；当前 `MekanismConfig.registerConfigs` 从未由 bootstrap 调用，不能直接调用它将不同生命周期/已有 writer 的配置一起加载到全局 config。真实边界为上述三份配置与已接通的 gear 共用一个世界 owner、配置阶段快照和连接卸载，不建立第二套配置或网络框架。保留官方字段路径、默认值、范围和 tier reference，替换仅限平台生命周期和同步。

实施路线：把现有 gear 专用 owner/transport 扩为明确列举这四份 config 的 Mekanism SERVER 标量 owner；每份 TOML 仍使用官方文件名，网络按文件名与原字段路径区分，客户端加载/断线清理覆盖同一固定配置集合。开发版无跨提交兼容负担，不保留旧 gear-only payload 或 adapter。集成服仍共享 canonical 实例而不是复制配置。

非目标：完整 general/world 同步、client window owner 合并、startup/common writer 整理、PLAY 热重载/文件 watcher、Additions/Generators/Tools、通用 Forge/Fabric 配置转换、异常矩阵和新测试基础设施。若这三份的实际 schema 超出已有 scalar 类型，先记录具体调用和最小前置，不悄悄扩为通用类型协议。

验收：整体编译/重映射、三个切片 gate及少量现有 config focused tests；一个真实非默认机器配置加载/同步路径优先于补充覆盖矩阵。继承的测试仅随 owner/API 改名和行为集合调整，不新增测试用例。每次移植提交后读取真实 diff，并记录审查结论。

## 实现检查点

已将 gear-only owner/transport 原地改为 `FabricServerConfig` / `FabricServerConfigNetworking` / `FabricServerConfigClientNetworking` / `ServerConfigSnapshotPayload`，固定拥有 gear、machine-storage、machine-usage、tiers 四份官方配置。磁盘 filename/schema 不变；网络用 `<filename>.<field-path>`，复用原有 typed scalar codec 与 CONFIGURATION ACK。未新增 adapter 或纳片数量，原 `FabricMekanismConfigManager` 负责批量 load/unload；中途文件加载或远程安装失败清理已经加载的 spec，避免留下半批 owner。集成服仍验证共享实例而不写第二份。

编译、remapJar、remapPortReviewFixtureJar、三个切片 gate 与继承的单一生命周期用例 PASS（17s，`/tmp/mekanism-machine-config-build.log`）。已有测试只调整 owner 名和四配置快照输入，断言/测试数量不增加。既有 paired-network smoke PASS（`/tmp/mekanism-machine-config-paired.log`），运行约 82 秒（启动至第二客户端入世界），独立服务器与两次 fresh client 正常进入 PLAY。保留诊断 `build/fabric-paired-network-smoke/20261030T081852Z-3129744/`：server start 明确 `configs=4`；两客户端完整 `values=235` 小于既有 codec 上限 256，无需扩大限制；两个 session 的 send/client_applied/ack 一致（`beb28c70-4f67-4f36-8940-9a5299d57b4d`、`4d95928e-86aa-4271-b1f4-56dc040d4648`）。脚本通过终止 scope 收尾，不将它算作 graceful disconnect/stop 验证。

当前未宣称真实非默认机器参数、四配置集成服跨世界切换或热重载验证已完成；不为这些限制新增测试矩阵。下一步完成提交后审查，再优先用实际机器验证非默认容量/耗能。
