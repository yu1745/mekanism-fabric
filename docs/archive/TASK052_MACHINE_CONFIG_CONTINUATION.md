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

此检查点尚未验证非默认机器参数；其后已完成下节真机验证。四配置集成服跨世界切换与热重载仍不在已验证声明内，不为这些限制新增测试矩阵。

## 提交后风格审查 — `207e08ae`

按 `PORT_COMMIT_REVIEW_PROMPT.md` 完整阅读提交后的真实 diff（含四文件重命名的实际方法变化），对照官方 `4646f509` 的 StorageConfig/UsageConfig/TierConfig：三份 schema 现有差异仅 builder/spec/type 平台替换，官方 filename、字段、默认值、范围、tier reference 绑定均保留，本提交未改其领域源码。当前 `MachineEnergyContainer.input/internal` 仍通过原 `AttributeEnergy` 取容量/耗能，无第二套机器或配置模型。新固定配置集合复用原 manager、spec 校验和握手；无新增纳片、兼容分支或测试用例。结论：此提交边界内未发现新的移植风格偏离。

补充既有 `MekanismConfigSpecTest` 整类运行 PASS（8s，`/tmp/mekanism-machine-config-spec-test.log`），其变化仅 payload 类型更名；不将它说成四配置非默认真机证明。

上下文评估：实现与审查已落盘，下一步仍是同一机器配置边界的实际运行，保留当前会话继续，不再重复历史审计或拆新模块。所有本轮 build/smoke jobs 已收集且结束。

## 非默认机器参数真机验证 — 2026-10-30

复用 `fabric/run/saves/Mekanism F1 isolated debug` 与 mcdebug 0.7.0 / Accessories dev 客户端；仅在启动前临时新增三份配置：`machine-storage.toml` 的 `crusher=44444`、`machine-usage.toml` 的 `crusher=37`、`tiers.toml` 的 `energy_cubes.basicCapacity=1234567`。未通过反射修改配置或机器字段。启动日志 `/tmp/mekanism-machine-config-client.log` 中实际 server_start 为四配置，session `0e70771e-9443-477a-8b84-97e18896bcd7` 的 send/client_applied/ack 均为 235 值。

在确认原方块均为空气后放置真实 Crusher `(10,100,0)` 与 Basic Energy Cube `(12,100,0)`。mcdebug 读取实际 BE 的公共 `getEnergyContainer().getMaxEnergy()` 分别为 **44444** 和 **1234567**；Crusher 的 `getEnergyPerTick()` 为 **37**。客户端线程读取 canonical usage=37 与 tier capacity=1234567，证明集成服共享配置握手未拒绝且实际机器采用非默认数值；它不是独立远程客户端的非默认快照证明，独立客户端默认完整快照的证据见前节。没有额外新增配方 tick/GUI 测试矩阵。

一次 `inv get-size` 返回 `block entity ... is not an Container`：这是 mcdebug vanilla Container API 的适用范围，Mekanism 使用真实 slot/storage owner，并非本次配置缺陷。后续直接读取实际能量 owner。

清理：两机器恢复为空气；未改玩家位置或物品。`gui disconnect` 返回 `serverStopped=true`，其后客户端反射 storage/usage/tiers 的 `isLoaded()` 均为 false。点击 Quit Game 时 RPC 因 JVM 退出而 connection closed；原 runClient job 最终 exit 0。确认绝对路径及 debug JAR 哈希后删除本轮临时两 JAR，并移除原本不存在的三份调试 TOML（生成后的内容存于 `/tmp/mekanism-machine-config-*.toml` 供诊断），没有运行中的本轮客户端或 jobs。

本次仅追加实际运行证据，无生产代码或测试覆盖变更。当前配置接线闭包的实现、构建、关键运行及提交后审查已齐；下一边界按既有 Task052 主干范围选取，不因本轮完成自动启动 Additions 等未授权模块。

## 下一已纳 owner 修复：canonical COMMON

基线 `53103874`。确认 `FabricChemicalConfig` 使用 `new CommonConfig()` 加载完整 common.toml 并发布 ChemicalConfigAccess，但 `MekanismConfig.common` 是另一实例且未加载；HolidayManager、BlockMekanism.copyBlockData、dosimeter/geiger 与 MekanismUtils 温度显示均直接读取后者，用户文件中的非默认值因此不生效。这不是新增配置模块，而是已经存在的完整 COMMON schema 的真实 owner 分裂。

最小闭包：移除独立实例，由同一 bootstrap manager 加载 canonical `MekanismConfig.common` 并发布给原 ChemicalConfigAccess；不修改 schema、文件路径、COMMON 的本地进程生命周期或三字段 general policy，不增加同步、兼容或 defensive 分支。复用现有 ChemicalServiceConfigTest 与构建；必要时用一次非默认 common 值的实际 consumer 读取验证启动顺序。此修复不重开 general/world/client 等前批非目标。

同一 bootstrap owner 核对进一步确认 STARTUP 同类问题：entrypoint 的 `STARTUP_CONFIG = new MekanismStartupConfig()` 被加载，而 MekanismArmorMaterials 注册 armor/toughness/knockback 时读取 canonical `MekanismConfig.startup`。将这两份已存在的进程级配置一次完成收敛，不拆成另一个叶子任务：去掉 startup 独立实例，在既有注册之前的同一位置加载 canonical startup，原 proof 改为读取 canonical；不更改任何 armor schema/算法或注册时机。

实现/验证：两处独立实例已删除，既有 manager 只加载 canonical startup/common，原有 startup proof 同步指向 canonical。`remapJar`、三个 slice gate、现有 ChemicalServiceConfigTest 与 MekanismStartupConfigTest PASS（33s，`/tmp/mekanism-process-config-build.log`），没有新增/修改测试。mcdebug 在实际客户端标题屏读取：修复前 canonical startup.isLoaded=false；修复后 true。启动前临时将 free runner armor 设置为 13、holidays 设置 false，实际注册的 `MekanismItems.ARMORED_FREE_RUNNERS.asItem().getDefense()` 返回 **13**，`HolidayManager.areHolidaysEnabled()` 与 ChemicalConfigAccess 的 common.holidays 均返回 **false**。这验证了真实注册 consumer 和同一 common owner，而非只读 config schema。最终客户端日志 `/tmp/mekanism-process-config-client.log`；退出 JVM 导致 Quit Game RPC connection closed，runClient 正常 exit 0。两原始 TOML 已按备份逐字恢复（cmp），两临时 debug JAR 已按绝对路径/hash 核对删除。未进入世界或改动存档。

提交后风格审查 `11a97f1c`：完整阅读真实 diff 并对照官方 `4646f509` 的 MekanismConfig singleton 注册，确认本次只是让原 Fabric 加载调用使用官方 consumers 所持同一实例；未复制配置、修改领域算法、文件/schema、注册 ID 或同步范围。startup 加载仍早于 material/item 注册；真实 armor=13 已证明这一顺序。common 的旧 ChemicalConfigAccess 与直接调用现在共用同一实例。现有 proof 只是更换读取目标，没有新增防御检查。结论：未发现该提交的新风格偏离。

上下文检查点：canonical 进程配置修复与审查已闭环，当前无运行中的本轮 jobs，继续使用同一会话。下一步核对已纳 GeneralConfig 与现有 general fragment 的完整 owner 边界；若所需非标量同步会明显扩大当前标量协议，先记录具体调用链与方案并交由用户裁决，而非又建 fragment 或静默扩展新模块。
