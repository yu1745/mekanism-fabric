# M-owner — TileEntityMekanism 中枢 owner

原始计划：`docs/archive/TASK052_PLAN.md`。相关历史文档按 `docs/milestones/README.md` 的归类表查找。

**验收（唯一）**：真机上一台 Crusher 放置 → 通电 → 加工 → 存档读档后状态一致。通过前状态保持"未核实"，不得声明机器可用。

## 现状

未核实。已知：配置、传输、渲染等相邻系统已拆为独立里程碑；本里程碑仅含 `TileEntityMekanism`、`TileComponentConfig`、频率/`FrequencyManager`、`ContainerType`、`MekanismContainer` 同步、有效侧线程语义、bounding。

## 下一步（交给 worker）

1. 核实：读 `git log` 与 `docs/archive/TASK052_{CONVERGENCE_14,CONTINUATION_14,ASTRA14_PROGRESS,RESUME_2026-10-26}.md`，列出 `TileEntityMekanism` 及上述各 owner 的实际接通状态（已接通 / 已纳入未接通 / 未纳入）。
2. 按验收用例用 mcdebug 在 `fabric/run` 的隔离调试世界实测，记录通过/失败点；失败时抓首条错误原文。
3. 结果写入本文件"验收证据"，并改 `docs/milestones/README.md` 状态表；通过后在 `PORT_MATRIX`/`FABRIC_PORT` 补一行结论。

## 未决项（协调者待裁决）

- 若核实发现某必需模块会明显扩大边界：worker 记录调用链与最小边界后暂停该分支，不自行扩题。
- 历史文档归类仅按标题，worker 发现归错的在 README 归类表更正。

## 非目标

配置（M-config）、传输（M-transfer）、渲染（M-render）、注册收尾（M-registry）、装备（M-gear）；新测试矩阵。

## 验收证据

### P0 / W-01–W-03 复核（2026-10-30，基线 `8adfac35`）

工作区开始时干净；本阶段不修改生产代码、不扩大 slice、不新增测试。完整构建与既有 smoke 正在重跑，结果待补；下表是源码接线证据，不代替 W-06–W-10 的真机验收。

| 实际 owner | 当前接通情况 | 证据与剩余验收 |
|---|---|---|
| `TileEntityMekanism` / `TileComponentConfig` | 已纳入并接到真实机器继承、组件与五域 provider | `common-slice.txt`、`FabricCapabilityTileLifecycle`；历史 `e2985141` / `ed040501`，后续 `55c59a0c` 修通机器 tick；Crusher 保存读取仍待 W-06/07 |
| `FrequencyManager` / `TileComponentFrequency` | 已纳入；bootstrap 的 world LOAD 调用 load，END_SERVER_TICK 调用 tick，server STOPPED 走统一清理 | `MekanismFabricBootstrap` / `MekanismServerState`；频率重启持久化待 W-09 |
| `ContainerType` | 五域实际 item component / provider 与 tile copy/apply/persistence 已纳入 | `common-slice.txt` 与 `TASK052_ASTRA14_PROGRESS` 的 item-component owner 记录；工厂 tier 升级保留待 W-08 |
| `MekanismContainer` / `SyncMapper` | 已纳入，真实 property/QIO payload 通过 `FabricContainerNetworking` 注册，客户端独立 receiver 接收 | `4d56423b` / `ed040501`；GUI 与动态同步需 W-06 实测 |
| `EffectiveSide` | 已纳入；`MinecraftServerMixin` 在真实 spin 创建入口安装 ThreadGroup 标记 | `59e389a0`；不能沿用 CONVERGENCE_14 中“尚待裁决”的旧状态 |
| Bounding | `BlockBounding` / `TileEntityBoundingBlock` / destroy、explosion mixin 已纳入 | `ed040501`、两份 slice；放拆与主从行为待 W-10 |

历史材料已交叉核对：`CONVERGENCE_14` / `CONTINUATION_14` / `ASTRA14_PROGRESS` 描述的是主干尚未编译通过时期；`RESUME_2026-10-26` 与后续 Git 记录已覆盖其编译 blocker。它们的语义约束保留，旧 FAIL 和待纳入清单不能当作当前状态。该 resume 同时记录传输、渲染、注册、装备和配置进展，应作为跨里程碑索引，而非只归 M-owner。

依赖扫描：`scripts/scan-platform-deps.py` 成功（0.86 秒），扫描 `src/**/*.java` 共 2508 文件，231 个文件含 565 条 NeoForge import。口径包含未纳入的 source sets、datagen 和第三方集成，不是当前 Fabric slice 的依赖缺口数。

W-01 基线发现待修 `W-01.1`：`FabricGearConfigLifecycleTest.remoteOwnersRefreshCachesAndIgnoreStaleDisconnects` 第 55 行抛 `IllegalArgumentException: Config snapshot schema mismatch`，调用链 `MekanismConfigSpec.installRemoteValues:178 → FabricServerConfig.attachClient:126`。生产 CONFIGS 已含 general，现有测试快照仍只装 gear/storage/usage/tiers；最小修复是补齐现有测试输入，不放宽生产校验、不新增测试。该回归挂 P1 前置修复，验收顺延至修复后的串行门禁。

首次 `./fabric-build.sh` 278.10 秒失败；另有 XML 报告写入错误，因为 packaged smoke 内部 `fabric-build.sh remapJar` 与 Gradle 测试重叠。后续显式 gates 与 packaged 调用已停止（143），不作为通过证据；统一改串行运行。机器 smoke 独立使用已生成 JAR，113.94 秒返回 `fabric-machine-smoke: PASSED`。

W-03 已完成上述 owner 实际接通核实，README 将跨系统 resume / recent audit 从单一 owner 归类移出；逐文件标题与正文核对另将事务修复 `ASTRA_REPAIR` / `REVIEW_12/14` 归回 M-transfer。W-04 已在两份总览各补 Task050–052 / 六个里程碑的一行结论。W-05 已核对顶层指定文档：Development 为上游 NeoForge 说明，模型评估为已被替代的执行策略，machine 两文档为 Task050–051 历史；FABRIC 专题保留语义依据并明确历史范围，Mekanism.xml 为仍可使用的 IDE 格式预设。未删除历史证据。

### W-01.1 修复与串行基线

仅更新既有 `FabricGearConfigLifecycleTest` 的 companion snapshot，补入真实 general owner，并将变量改名为 `otherConfigSnapshot`；原有断言与全部生产代码不变。修复后 `./fabric-build.sh` **PASS，173.00 秒**，`verifyApiSliceNoNeoForge test qioTransactionTest` **PASS，0.73 秒**（测试复用刚完成结果）。XML 核对 ordinary 451、transfer 45、registry 3、data-map 4、recipe-registry 8、QIO 50、client-model 17，总计 **578 tests，0 failures/errors/skips**。完整 build 同时通过三份 slice gate 与 remapJar。临时日志 `/tmp/mekanism-roadmap-p0/{build-fixed,gates-fixed}.log`，持久结论以本节为准。
