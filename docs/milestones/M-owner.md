# M-owner — TileEntityMekanism 中枢 owner

原始计划：`docs/archive/TASK052_PLAN.md`。相关历史文档按 `docs/milestones/README.md` 的归类表查找。

**验收（唯一）**：真机上一台 Crusher 放置 → 通电 → 加工 → 存档读档后状态一致。通过前状态保持"未核实"，不得声明机器可用。

## 现状

P0 已完成源码接线复核与基线门禁；M-owner 真机存读档验收仍未核实。配置、传输、渲染等相邻系统已拆为独立里程碑；本里程碑仅含 `TileEntityMekanism`、`TileComponentConfig`、频率/`FrequencyManager`、`ContainerType`、`MekanismContainer` 同步、有效侧线程语义、bounding。

## 下一步（交给 worker）

1. P0 W-01–05 已复核，见下方证据；下一项为 P1 W-06/07：Crusher GUI、加工和带半成品存读档，再做 W-08–10。
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

W-02 / W-01.2：packaged 静态门禁失败于 `unexpected remapped access widener`（1.22 秒，未启动服务器）。脚本仍要求 Task043 时点两条 AW，随后还禁止实际已纳入的 MekanismAPI、client、配方数据与 root catalogs。最小边界为现有 packaged 脚本的产物合同：以已通过 slice gate 的同次 dev JAR 验证完整类／资源集合，保留独立 AW remap 期望、平台依赖／fixture 隔离、服务描述符与真实干净服务端 proof。无需更改任何生产 owner 或切片。

### P0 收尾 / W-01.2 packaged 合同修复

现有 packaged 脚本的过期精确子集检查替换为**同次、已通过 slice gate 的 dev JAR 与 remapped JAR 完整集合比较**：2939 个类（包含生成类），无缺失、额外或重复条目；除 manifest、嵌套依赖 metadata、AW 这三项 Loom 转换资源外，所有资源逐字节一致（含服务声明、mixin、refmap、官方数据与 assets）。继续拒绝测试／fixture、NeoForge、Kilt、Connector、外部 Loader/Team Energy 实现泄漏，保留 Night Config 嵌套依赖和许可要求。原脚本禁止机器、客户端和生产 recipe 数据的 Task043 假设已被实际纳入范围取代，不再作为当前门禁。

AW 保留独立的精确 intermediary 期望列表：111 条成员／类项经 named AW 与 Loom 固定 MC 1.21.1 映射逐项核对，包括继承方法；不是测试时从待测 JAR 自动放行。serializer 启动 proof 更新为 30：26 个机器 serializer + 官方 `mek_data` / `clear_configuration` / `bin_insert` / `bin_extract`，与 `MekanismRecipeSerializersInternal` 核对。没有修改生产代码或切片。

| W 项 / 命令 | 结果 | 耗时／证据 |
|---|---|---|
| W-01 `./fabric-build.sh` | PASS | 173.00 s；578 tests、0 failures/errors/skips；三 slice gate、remapJar |
| W-01 `verifyApiSliceNoNeoForge test qioTransactionTest` | PASS | 0.73 s，复用上述已完成测试结果 |
| W-01 `scripts/scan-platform-deps.py` | PASS | 0.86 s；2508 files / 231 boundary files / 565 imports |
| W-02 machine smoke | PASS | 113.94 s；`fabric-machine-smoke: PASSED`；生产 JAR 此后未改 |
| W-02 paired-network smoke | PASS | 78.03 s；两个 fresh clients 完成 configuration 并加入 play；每次 304 值 client_applied + server ack，configs=5 |
| W-02 packaged smoke | PASS | 50.39 s；完整静态合同 + 干净服务端 Done；config/network/registry/heat/data-map/chemical/recipe proof |
| W-03–05 文档核实 | 完成 | `e7dc881a`；源码接通清单、历史归类、总览索引及过期文档提示 |

阶段偏差：P0 基线先发现两项既有门禁退化，按预案先修复再进入 P1；W-01.1 测试快照修复为 `dba1010d`，W-01.2 为本次 packaged 合同提交。没有新增功能、测试用例或边界外模块。非默认 general 的真实 consumer、Crusher 重启一致性等不因这些 PASS 视为完成。

本地诊断：`/tmp/mekanism-roadmap-p0/`；paired 持久工作区日志 `build/fabric-paired-network-smoke/20261030T104728Z-1425317/`；packaged 诊断 `build/fabric-packaged-smoke/20261030T105430Z-1522603/`。smoke 自建临时世界和进程均由脚本清理，未启动 mcdebug 会话。后续客户端使用已有 `fabric/run/saves/Mekanism F1 isolated debug`，先 status/gui status；本轮已确认两平面无活连接。CLI 为 `/home/wangyu/server-v2/develop/mcdebug-wt-121/mcdebug-cli.jar`。当前 shell 缺 systemd 环境，paired 命令显式传入 `XDG_RUNTIME_DIR=/run/user/1000 DBUS_SESSION_BUS_ADDRESS=unix:path=/run/user/1000/bus` 即可。

提交后风格审查：检查本次已提交 packaged 实际 diff，确认没有生产／slice 改动、没有跳过真实服务器门禁；旧 package 子集由完整构建产物比较取代，独立 AW、metadata、依赖及 fixture 隔离保留。复查时补回全 entry 的 `unfreeze` 拒绝，并将兼容层 bytecode 检查统一为精确 namespace（避免把一般 connector 文本误认兼容层）。只需重跑静态门禁；此前相同 JAR 的干净服务端 PASS 仍有效。
