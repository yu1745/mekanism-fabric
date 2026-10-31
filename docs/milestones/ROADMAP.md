# 规划书：2026-10-30 起的后续提交（W-01 … W-62）

协调者编写，worker 按序执行。状态以 `docs/milestones/README.md` 为准；本文只定"做什么、什么顺序、怎样算完成、卡住怎么办"。不得借本文扩大 `AGENTS.md` 的边界。

## 进度与调整记录（协调者，2026-10-30 14:20）

- P0–P4 已完成并过门禁（W-01…28，证据在 `docs/archive/M-{owner,config,platform,transfer}.md`）；P5 已做完 W-29/30/31/32，余 W-33 门禁。节奏明显快于预估，质量检查点（证据 CSV、首条错误原文、无新增测试）符合协议。
- 实际新增里程碑 **M-platform**（P3 的命令/成就/条件/生命周期/payload，46 个未纳入文件逐项记理由）；视同 ROADMAP P3 的活文档，不另立阶段。
- **微调（本次生效）**：
  1. **W-34 缩范围**：五个核心多方块在 W-31 已由官方 builder 搭成并打开 GUI，W-34 只验证**生命周期**——破坏/重建、区块卸载重载、存读档后结构保持、尺寸校验失败路径——不重复搭建与 GUI。
  2. **W-36 去重**：accessory 预览与客户端注册已在 W-32 验证，W-36 只做 MekaSuit/MekaTool/Jetpack/Scuba/Flamethrower/Configurator 的功能读数与模块安装。
  3. **W-42 增"发布限制汇总"**：汇总第三方集成缺口（JEI/EMI 混合公共 API 等）、M-platform 的 46 个延期文件、未验证风险，写入 `README.md` 一节；不单独成项。
  4. **W-40 增"安全网复核"**：P0 重写了 `fabric-packaged-smoke.sh`（1772→485 行，改为 dev→remapped 全量清点）。W-40 前由协调者派一次**只读审查**（AGENTS 已授权）确认没有削弱门禁，再据此宣布发布门禁。
- **文档纪律收紧（防止回到流水账）**：
  - 活文档"验收证据"节每个 W 项 ≤5 行结论 + 指向 `evidence/` 文件；长叙事（命令、哈希、首轮错误）放 evidence 文件，不放 M 文档。M-render.md 已 95 个长段，W-33 归档前压缩。
  - `README.md` 状态格 ≤100 字；细节在活文档。
  - `evidence/` 仅存 CSV/JSON/必要截图，不存日志与大二进制；P5 结束时检查体积（目前 6MB）。
- 给 worker 的具体事项见 `COORDINATOR_NOTES.md`。
- **待用户决定**（不阻塞 worker）：①本地领先 origin 31 个提交，需要时再推送；②wip 提交 squash；③submodule 指针更新。

- **2026-10-30 16:15 巡检 1**：用户指示 worker"聚焦开发"，P8 发布门禁后置。W-43 已完成（`ed8e1217`），worker 工作区正在做 W-44（Tools）。路线图因此：P10 细化为 W-44a–e；新增 P11 Additions（W-45…49）、P12 Generators（W-50…58）、P13 发布收口（W-59…62）；引入"回归监测门"取代对每个模块的整套门禁；rejection smoke 的真失败列为 W-59 首项。
- **2026-10-30 17:01 巡检 2**：W-44a–e 完成（`184299a6`，Tools 已验证并归档），W-45 Additions 边界页完成（`f206c34f`），worker 工作区已开始 W-46。路线图补：P14 评估页（W-63）、W-46 的 evidence 精简前置、W-51 的同步值上限核算；其余 W-46…62 覆盖剩余工作，暂无需再扩。此后不再有自动巡检，worker 如耗尽 W 项，按 P13 之后的"发布后议题"向用户汇报即可。
- **2026-10-30 18:15 巡检 A1**：Tools、Additions、Generators 均已移植并验证（`184299a6`/`3f365066`/`e26940e0`+`47fe5afa`），W-59 rejection 顺序缺陷已修（`aa71976b`），worker 进入 P13。路线图因 W 项即将耗尽，新增 P15 W-64…68（未验证行为收口、NeoForge 存档加载探针、浸泡、性能基线、第三方集成评估页）。P13 的 W-60 须补：协议阶段改动后的**正常连接**验证（paired-network smoke）。
- **2026-10-31 04:47 巡检 B3**：W-60/W-61 已完成（`77118a66`/`376cf9a5`），W-62 准备已落盘并保持待协调者复核。补 W-69（发布 JAR 客户端启动，时间盒）与 W-60b（addon 负向探针）为 W-62 前置。
- **2026-10-31 05:17 巡检 B4（最后一次）**：W-60b、W-69 已完成（`04d54f00`、`470fe86e`），A1/B3 的全部要求落实；**P13 的发布路径门禁（核心 + 三个附加模块，服务端与客户端发布 JAR）判定为协调者复核通过**，范围与未覆盖项见 COORDINATOR_NOTES B4。路线图补 P16 W-70…74（一键发布检查、发布元数据、核心延期文件复盘、用户文档、第三方集成待裁决）。此后无自动巡检；worker 按 P15 → P16 自行推进，遇边界问题写 `M-*.md` 未决项并跳过。\n- **2026-10-31 05:52 巡检 C1**：worker 推进 P15（W-64 声音/语音采集已验证、HUD/熔毁如实记为本环境不可验证；W-65 round 1 用真实 NeoForge 官方样本世界加载 Fabric 成功）。连续 3 个纯验证提交，但均为新事实，不判审计陷阱；为防 P15 后纯验证化，补 P17 W-75…79（官方对照差异与清理）。未开始的 W 项约 8（含被用户批准阻塞的 W-74）。\n- **2026-10-31 09:22 巡检 D4（最后一次）**：09:11–09:21 完成 W-71（版本 `10.7.19.85+fabric.1`，并因此暴露并修复了 `split("-")` 版本解析缺陷）、W-76、W-77、W-78、W-73、W-72、W-63；W-75 修正后确认 637 个兼容配方是转换后随包而非排除。路线图仅余 W-74（待用户）与 W-79（预留），故补 P18（W-80…90，含 P0/P1/P2 优先级与待用户项），供无人值守继续。此后无自动巡检。\n- **2026-10-31 10:17 用户裁决**：批准 W-74（先 EMI 再 Jade）与 W-90（采纳 4 个官方测试）；JEI/WTHIT 不在范围。路线图与 M-integration 状态已更新。
- **2026-10-31 11:17 巡检 E2**：W-74a（EMI 真机）、W-74b（Jade）、W-90（仅补 1 个测试）、W-89 完成；路线图原有项基本耗尽，补 P19（W-91…96：EMI/Jade 范围补全、集成后全量回归、注册表差异实质修复、发布演练）。
- **2026-10-31 12:17 巡检 E4（最后一次）**：W-93 完成但在其"可复现仍成立"的表述被 `be1383fd` 自我推翻（核心 JAR 两次 clean build 哈希不同，附加三个一致）；补 P20（W-97 可复现根因修复 P0、W-98 渲染器兼容、W-99 真实第三方能量互通、W-100 用户决策清单）。此后无自动巡检。
- **2026-10-31（用户裁决）**：批准 JEI 与 WTHIT（W-74c/W-74d）；已 push 本地提交到 origin。
- **2026-10-31 13:17 巡检 F2**：W-74c（JEI）、W-91/92/94/100 及 W-101 准备已完成；未开始项降到约 4（W-74d、W-79、W-98、W-99），补 P21（W-102…105：JEI 范围验证、最终全量、W-81/82 缺陷修复、游戏内配置界面核对）。
- **2026-10-31 14:17 巡检 F4（最后一次）**：W-74a/b/c/d 全部完成（WTHIT 经过三轮定位后以模块根 `wthit_plugins.json` + 双接口拆分注册成功，有 tooltip 截图）；补 P22（W-106…109：矿石加工链端到端、崩溃/卸载鲁棒性、安全/频率、集成文档同步），转入以场景找 bug 为主。此后无自动巡检。
- **2026-10-31 15:16 即时检查 G0**：W-103（最终全量 6 步通过）、W-105（Mod Menu 只读配置界面）、W-106/107/108/109 完成但 W-106/107/108 零缺陷；补 P23（W-110…113：双玩家安全拒绝、完整化学链、工厂/升级/QIO 守恒、mdb 启动前置固化）。
- **2026-10-31（W-102/W-104 期间）**：W-102 改为**运行时计数**完成——`MEKANISM_FABRIC_JEI_CATEGORY_PROOF` 实测 `count=30 empty=0`（`d90783b9`）；新增 `scripts/rebuild-client-argfile.sh` 固化 G0 第 1 条的防再犯措施（不再需要裸跑 `gradlew runClient` 生成参数文件）；Mod Menu（第五个可选依赖）记入 `DECISIONS_PENDING.md` 的 D-05 待用户裁决。
- **2026-10-31（本轮收尾）**：P19（W-91…W-96）、P20（W-97…W-100）、P21/P22 的 W-102/103/105/106/107/108/109 均已完成并落盘证据；
  **仍未完成**：W-79（自带整合包/服务端配置形态）、W-98（Sodium/Iris）、W-99（第三方科技能量互通）——三项都在等用户裁决或外部样本，属挂起而非阻塞；
  W-104（W-81/82 余项修复：涡轮性能、真实区块卸载、跨维度传送）与 P23 的 W-110（双玩家安全拒绝）、W-111（×3/×4/×5 化学链）、
  W-112（工厂/升级/并行）、W-113 是**已知且未做**的验证/修复项，均已在对应证据页写明缺口与最小实施路径，不属于“已通过”。
- **2026-10-31 16:17 巡检 G2**：W-102 完成（JEI 30/30 分类 `empty=0`）、W-113 脚本化、W-107 能量基线澄清（持久化）；但 `a3530a37` 的"收尾状态"把 P0/P1 项标为挂起，不被接受；补 P24 恢复必做并给出可行路径。
- **2026-10-31 18:57 巡检 H3（最后一次）**：H0–H2 的要求大部分被落实（`.gitignore`、遗留字段删除、W-112 根因、W-111 第一阶段、W-104 卸载场景）；但 W-104 的"卸载"断言可能是空证明、W-111 洗涤阶段只回退未定位；补 P25（W-114…117）。此后无自动巡检。
- **0. 已知事实**中的 slice 数字、"约 1700/1802"为 10-30 早间快照，当前 platform slice 已 207→209，不作为依据。

## 0. 已知事实（规划依据，worker 在 P0 复核）

- 官方 `src/main/java` 共 1802 个文件，三份 slice 清单已纳入约 1700 个；**核心模块的主干基本都已纳入并能编译**（`compileJava`/`compileTestJava` 于 `741d580d` 通过）。
- 尚未纳入的核心文件（精确清单见 P5）：`Mekanism.java`/`PacketHandler`/`BasePacketHandler`（平台入口，已由 `MekanismFabricBootstrap` 与 Fabric 网络替代）、`MekanismAttachmentTypes`、`MekanismDataMapTypes`、`MekanismAdvancements`、`CommandMek`/`ChunkCommand`/`RadiationCommand`/`builders/*`、`ReloadListener`、`CommonPlayerTracker`、`IncompleteRecipeScanner`、`MekanismRecipeConditions` 与 3 个 condition、`client/render/obj/*`（Transmitter 模型）、`client/model/{baked,data}`、粒子 4 个、`MekKeyBinding*`、`ClientRegistration*`、`MekanismCurioRenderer`。
- 第三方集成不属于核心：`integration/{crafttweaker,projecte,lookingat,framedblocks,jsonthings,gender,energy,computer/opencomputers2}` 与 `client/recipe_viewer/{jei,emi,alias,color}`（约 240 个文件）。
- 已有真机证据：Crusher/Energy Cube 非默认配置容量与耗能；free runner armor=13；paired-network smoke（235 值同步）；`scripts/fabric-machine-smoke.sh` 曾验证 enrichment chamber 与 energized smelter 产出。这些在 P0 重跑确认仍然成立。
- 未验证：整套 M-owner 验收、传输、渲染、多方块、装备模块的端到端行为，以及 `FabricGearConfigLifecycleTest`。
- `src/additions`（64）、`src/generators`（121）、`src/tools`（52）三个官方 source set 尚未开始。

## 1. 协调者已做的裁决（worker 不必再问；用户可随时推翻）

1. **范围次序**：先把核心模块做到"发布可用"（P0–P9），再启动 Tools → Additions → Generators（P10），每个先出一页边界说明再动代码。
2. **第三方集成**（CraftTweaker、ProjectE、JEI/EMI 等）**不在本规划内**，发布后另议；core 中对它们的引用用既有 platform 边界处理，不新增 stub 框架。
3. `Mekanism.java`、`PacketHandler`、`ReloadListener`、`CommonPlayerTracker` 等 **NeoForge 事件总线入口不照搬**，由 Fabric 事件接口在 `MekanismFabricBootstrap` 一侧承接；逐项记录"官方行为 → Fabric 事件"映射，缺的行为才实现。
4. wip 提交的 squash 不由 worker 执行（改写历史，需用户决定）；P11 之前提醒一次即可。
5. 测试遵循 `AGENTS.md`：不主动加测试；用现有 smoke + mcdebug 单个端到端证据。

## 2. 执行协议（每个 W 项都适用）

- **一个 W 项 = 一个或少数几个提交**，提交信息 `类型: 简述`（`port`/`fix`/`verify`/`docs`），验证型提交的正文写明做了什么、读到什么、未验证什么。
- **取证先于判断**：状态结论必须来自 mcdebug 真机读数、smoke 的 `*_PROOF` 标记行或构建结果；失败先抓首条错误原文。
- **审查**：按 `PORT_COMMIT_REVIEW_PROMPT.md` 的"使用分级"；结论写入对应 `M-*.md` 的"验收证据"，**不单独提交审查记录**。
- **阻塞处理**：遇到必须明显扩边界的模块/语义分歧，在对应 `M-*.md` 的"未决项"记录（调用链、不可截断的行为、最小边界），**跳到下一个互不依赖的 W 项继续**；不停下问用户，不 fake/no-op，不排除源码求绿。
- **门禁**：每个 Phase 结束跑一次 `./fabric-build.sh`、`./gradlew -p fabric verifyApiSliceNoNeoForge`、`scripts/fabric-machine-smoke.sh`；任何一项退化先修，再进下一 Phase。
- **等待纪律**：长命令后台运行，单次轮询 ≤1 分钟，只匹配标记行。
- **状态同步**：W 项完成即更新 `README.md` 状态表对应格；Phase 完成在 `PORT_MATRIX`/`FABRIC_PORT` 各补**一行**结论；活文档完成后移入 `docs/archive/`。
- **上下文**：每个 Phase 结束评估是否换窗口；换窗前把结论与证据提交。

## 3. 阶段与工作项

### P0 事实复核（W-01 … W-05）—— 不改生产代码

| ID | 内容 | 完成标志 |
|---|---|---|
| W-01 | 全套门禁基线：`./fabric-build.sh`、`verifyApiSliceNoNeoForge`、`scripts/scan-platform-deps.py`、完整 `./gradlew -p fabric test` 与 `qioTransactionTest`；记录每项结果与耗时 | 基线表写入 `M-owner.md` 证据节；失败项各开一个 `fix` 条目挂到 P1 |
| W-02 | 重跑 `scripts/fabric-machine-smoke.sh` 与 `fabric-paired-network-smoke.sh`、`fabric-packaged-smoke.sh` | 三条 PASS 标记行 |
| W-03 | 从 `git log` 与 `docs/archive/TASK052_{CONVERGENCE_14,CONTINUATION_14,ASTRA14_PROGRESS,RESUME_2026-10-26}.md` 提炼 M-owner 实际接通清单；逐文件归类校正 `README.md` 的旧文档归类表 | 清单写入 `M-owner.md` |
| W-04 | 补写 `FABRIC_PORT.md`/`PORT_MATRIX.md` 的 Task050–052 缺口：每个里程碑一行结论，不写叙事 | 两文档止于 Task049 的缺口消除 |
| W-05 | 盘点 `docs/` 顶层未审文档（`Development.md`、`Mekanism.xml`、`MODEL_IMPLEMENTATION_COMPARISONS.md`、`MACHINE_SPINE.md`、`MACHINE_OWNER_ADMISSION.md`、`FABRIC_*.md`）：过时者归档或加"已被取代"头注 | 顶层文档均有明确现状（W-87 二次清理：`MACHINE_SPINE`/`MACHINE_OWNER_ADMISSION`/`MODEL_IMPLEMENTATION_COMPARISONS` 已移入 `docs/archive/`） |

### P1 M-owner 验收（W-06 … W-11）

mcdebug 使用 `fabric/run` 的隔离调试世界；结束恢复现场。

| ID | 内容 | 完成标志 |
|---|---|---|
| W-06 | Crusher 端到端：放置 → 注入能量 → 放入矿石 → 产出；用 GUI 打开读 screen/menu | mcdebug 读数 + 截图证据 |
| W-07 | 存读档：同一台 Crusher 带半成品保存世界、重启客户端/服务端后状态一致（能量、输入、进度、侧配置、升级） | 前后读数一致 |
| W-08 | 工厂（Factory）与升级：放置 Basic Factory，装升级，tier 切换保留库存 | 读数一致 |
| W-09 | 频率与保存数据：Teleporter、Quantum Entangloporter、Security Desk 的频率创建→重启后仍在；`MekanismSavedData` 世界落盘 | 重启后读数一致 |
| W-10 | Bounding 与多格方块：Digital Miner / Teleporter / Quantum Entangloporter 的 bounding 放置、破坏、主从同步；有效侧线程语义（`EffectiveSide`）无断言 | 放拆循环无残留 |
| W-11 | M-owner 收尾：把 W-06…10 结论写入 `M-owner.md`，状态改"已验证"或列出具体失败项并各开 `fix` 提交（每个失败一个提交，限在 owner 边界内） | `README.md` 状态已定 |

### P2 M-config 收尾（W-12 … W-14）

| ID | 内容 | 完成标志 |
|---|---|---|
| W-12 | General 非默认真机：`minerMaxRadius` 非默认→Digital Miner 服务端上限与客户端 GUI 输入上限一致；或 `validOredictionificatorFilters` 列表同步到客户端 | 两端读数一致 |
| W-13 | 跑 `FabricGearConfigLifecycleTest`（`qioTransactionTest`）；对 `741d580d` 做提交后风格审查并记入 `M-config.md`；确认 `alphaWarning` 开发/发布 schema 行为 | 测试 PASS、结论入档 |
| W-14 | M-config 标"已验证"，补 `PORT_MATRIX` 一行，`M-config.md` 归档 | 状态表更新 |

### P3 核心平台缺口（W-15 … W-22）

逐项对照官方行为，"缺的行为才实现"；每项先写映射结论，再决定是否改代码。

| ID | 内容 |
|---|---|
| W-15 | `MekanismAttachmentTypes`、`MekanismDataMapTypes`：核对 Fabric 侧是否已有等价 owner（attachment / data map）；缺口补齐 |
| W-16 | `CommandMek`、`ChunkCommand`、`RadiationCommand`：注册 `/mek` 命令树（Fabric `CommandRegistrationCallback`），含权限等级；真机 `/mek radiation`、`/mek chunk` 可执行 |
| W-17 | `builders/*`（`/mek build`）：作为命令的一部分一并接入或记录为后续；不静默删除 |
| W-18 | `MekanismAdvancements`/`MekanismAdvancement`：成就触发器与 criterion 注册；真机触发一个成就 |
| W-19 | `MekanismRecipeConditions` 与 3 个 condition：映射到 Fabric resource conditions 或官方等价机制，使依赖条件的配方正常加载 |
| W-20 | `ReloadListener`、`CommonPlayerTracker`、`IncompleteRecipeScanner`：逐个映射到 Fabric 生命周期/事件，缺的行为补齐 |
| W-21 | 网络入口：`PacketHandler`/`BasePacketHandler` 与已有 Fabric payload 注册逐包核对，缺失的包补齐 |
| W-22 | P3 收尾：门禁 + 报告"核心官方文件中仍未纳入且非第三方"清单应为空（或列出理由） |

### P4 M-transfer 传输（W-23 … W-28）

| ID | 内容 | 完成标志 |
|---|---|---|
| W-23 | 能量：Universal Cable 连接两台机器，能量按 FE/Joules 换算传输；Team Reborn 外部能量 | mcdebug 读两端能量 |
| W-24 | 流体：Mechanical Pipe → Fluid Tank，满载/断开/回滚 | 读数 |
| W-25 | 物品：Logistical Transporter + Sorter 过滤；与原版漏斗互通 | 物品到位 |
| W-26 | 化学：Pressurized Tube；热量：Thermodynamic Conductor | 读数 |
| W-27 | 网络生命周期：区块卸载/重载、方块拆除、世界保存读取后网络重建 | 重启后仍通 |
| W-28 | M-transfer 状态落定 + 失败项 `fix` | 状态表 |

### P5 M-render / 客户端（W-29 … W-33）

| ID | 内容 |
|---|---|
| W-29 | Transmitter 模型链：`client/render/obj/*`（OBJ/MTL）纳入或确认等价实现；cable/pipe/tube 外观与连接状态截图 |
| W-30 | `client/model/{baked,data}`（DriveArray、Extension override、DataBased）与粒子 4 个 |
| W-31 | 机器屏幕巡检：对每类 GUI（machine/factory/QIO/robit/teleporter 等）`gui open-block` 并读 `problems`，列出打不开/渲染异常项并逐项 `fix` |
| W-32 | 按键（`MekKeyBinding*`）、`ClientRegistration*`、Curio/Accessories 渲染（`MekanismCurioRenderer`） |
| W-33 | M-render 状态落定 |

### P6 多方块（W-34 … W-35）

| ID | 内容 |
|---|---|
| W-34 | 核心多方块逐个搭建验证：Dynamic Tank、Thermoelectric Boiler、Induction Matrix、Evaporation Plant、Spaced-Out SPS（尺寸校验、结构形成/破坏、重启后保持） |
| W-35 | 失败项 `fix`，状态落定 |

### P7 M-gear 与 M-registry（W-36 … W-39）

| ID | 内容 |
|---|---|
| W-36 | MekaSuit / MekaTool / Jetpack / Scuba / Flamethrower / Configurator：穿戴、模块安装、耗能、模式切换各一次真机读数 |
| W-37 | 模块工厂缺口（Farming/Shearing/ChargeDistribution/Swim/Flight）：按 `TASK052_CONVERGENCE_14.md` 所列边界核对现状，缺则补，边界过大则记录 |
| W-38 | 注册表收尾：legacy alias、化学 ingredient 注册表、Robit 注册与数据包、biome modifier；注册冻结后 ID/alias 与官方一致 |
| W-39 | 世界生成：ore retrogen smoke、worldgen smoke 重跑；盐/矿脉生成 |

### P8 发布门禁（W-40 … W-42）—— **后置到 P13（W-59…62）；W-40 已部分执行，见 `evidence/p8-release/w40-results.json`**

| ID | 内容 |
|---|---|
| W-40 | 全量测试 + 全部 smoke（machine/paired/rejection/packaged/recipe/chemical/model/worldgen/retrogen/tile）逐条 PASS，记录耗时；**须先满足 `COORDINATOR_NOTES.md` 的 C-1…C-4（负向探针、recipe 计数兜底）** |
| W-41 | `./build.sh --clean` 发布物核验：remapped JAR、`build-info.json` sha256、干净服务端启动；干净构建后**重跑同一静态检查**并记录 jar sha256（C-3） |
| W-42 | 文档收口：`README.md` 状态表全部为"已验证"或带具体残留项；`PORT_MATRIX`/`FABRIC_PORT` 一页结论；相关 `M-*.md` 归档 |

### P9 Tools 模块立项（W-43）—— 已完成（`ed8e1217`）

| ID | 内容 |
|---|---|
| W-43 | 通读 `src/tools`（52 文件）：写一页边界说明（依赖核心哪些 owner、需要的 slice、风险），建 `docs/milestones/M-tools.md`。**仅文档提交**；之后沿用本规划的协议接力实现 |

### P10 Tools 实现（W-44，活文档 `M-tools.md`，52 文件，其中 4 个第三方集成排除）

| ID | 内容 | 完成标志 |
|---|---|---|
| W-44a | 独立 `tools` source set、精确 slice、可单独加载的 Tools JAR；`build.sh` 收集附加 JAR；核心 JAR 不混入 Tools 类 | 编译 + remap 通过，核心包体不变 |
| W-44b | `MekanismTools → 配置 → ToolsArmorMaterials → ToolsItems → serializer/创作栏/资源`；STARTUP 材料在物品构造前加载，SERVER 配置并入 `FabricServerConfig` 同一 owner/快照 | 72 物品、6 护甲材料注册；非默认配置真机生效 |
| W-44c | 生物装备（Zombie/Skeleton/Piglin `finalizeSpawn`）→ `MobEquipmentHelper`、paxel 能力、piglin 中立物品 | 真机：生成生物装备与官方时机/随机序一致 |
| W-44d | 客户端：盾牌 renderer/blocking 属性、护甲渲染、资源 | mcdebug 截图 |
| W-44e | 一个端到端场景（注册 + paxel 操作/耐久 + 配方 + 护甲/盾牌渲染）；**回归监测门**（见 P11 前说明）；M-tools 状态落定 | 状态表更新 |

### P11 Additions 实现（W-45 … W-49，活文档 `M-additions.md`，64 文件）

范围来自目录盘点：`common/block`(13) · `registries`(9) · `entity`(9：EntityBalloon、EntityObsidianTNT、四种 Baby 生物等) · `client/render`(6) · `config`(4) · `client/voice`+`common/voice`(5：对讲机) · `world`(2：BabyEntitySpawn biome/structure modifier) · `item`(2：ItemBalloon、ItemWalkieTalkie)。

| ID | 内容 | 完成标志 / 风险 |
|---|---|---|
| W-45 | 边界页：逐文件归类，标出三块高风险——①baby 生物生成修饰（NeoForge `BiomeModifier`/`StructureModifier` → Fabric biome modification API）②语音/对讲机（`client/voice` 的音频与网络）③实体注册与客户端渲染；写 `M-additions.md` | 仅文档；高风险项各有最小边界或明确延期理由 |
| W-46 | **先精简 `evidence/`（见 COORDINATOR_NOTES 巡检 2）**；source set / slice / JAR（沿用 W-44a 做法）；配置、registries、blocks、items（塑料方块、气球物品） | 编译 + 注册；非默认配置生效 |
| W-47 | 实体：Balloon、Obsidian TNT、四种 Baby 生物及其渲染；spawn 修饰映射 | 真机生成/渲染/爆炸各一次读数 |
| W-48 | 语音/对讲机：先核对官方实际依赖（服务端转发？客户端音频捕获？）；能原样承接则接，依赖平台库则按"超出边界→记录并跳过"处理，**不 stub** | 要么真机可用，要么 `M-additions.md` 未决项写清调用链 |
| W-49 | 端到端场景 + 回归监测门 + 状态落定 | 状态表更新 |

### P12 Generators 实现（W-50 … W-58，活文档 `M-generators.md`，121 文件；最大，开工前再出细化版）

范围：`common/tile`(26：Bio/Gas/Heat/Solar/AdvancedSolar/Wind 六种简单发电机；涡轮 Casing/Rotor/Valve/Vent；裂变 Assembly/ControlRod/FuelAssembly/Casing/Port/LogicAdapter；聚变 Controller/Port/LogicAdapter/Block/LaserFocusMatrix；ElectromagneticCoil、RotationalComplex、SaturatingCondenser、ReactorGlass) · `client/gui`(21) · `content`(14：多方块数据/校验) · `registries`(13) · `config`(5) · `client/render`(7) · `client/model`(3) · `item`(4) · `network`(3)。

| ID | 内容 | 完成标志 / 风险 |
|---|---|---|
| W-50 | 边界页：依赖核心哪些多方块 owner（沿用 M-multiblock 的 `mek build` 搭建法）；确认化学/热量/能量传输与裂变/聚变的核心依赖 | 仅文档 |
| W-51 | source set / slice / JAR；配置（5）、registries（13）、items/blocks；**同步值上限核算**：核心 304 + Tools 63 + Additions 41 = 408，Generators 配置会逼近 512，须在开工时先数清字段数，必要时有依据地提高 `ServerConfigSnapshotPayload.MAX_VALUES`（记录理由），不得靠排除字段保上限 | 编译 + 注册 |
| W-52 | 六种简单发电机（含 GUI、能量输出、燃料/热/太阳/风）；这是最小纵向闭包，先于多方块 | 每种一次真机发电读数 |
| W-53 | 涡轮：多方块成型、转子/叶片、蒸汽→能量、Vent/Valve | 搭建后发电并存读档 |
| W-54 | 裂变反应堆：成型、燃料/控制棒、热量/冷却剂、逻辑适配器、熔毁路径（只读验证不造成世界破坏——在隔离世界执行） | 受控运行读数 |
| W-55 | 聚变反应堆：控制器、激光聚焦矩阵、电磁线圈、氘氚燃料注入 | 点火读数 |
| W-56 | 客户端：21 个 GUI 巡检（沿用 W-31 方法）、涡轮/聚变渲染、模型 | GUI 巡检表 + 截图 |
| W-57 | 生命周期：各多方块破坏重建、区块重载、存读档（沿用 W-34 方法） | 重启后保持 |
| W-58 | 端到端场景 + 回归监测门 + 状态落定 | 状态表更新 |

### P13 发布收口（W-59 … W-62，活文档 `M-release.md`）

用户 2026-10-30 指示"聚焦开发"，故发布门禁后置。已跑结果（578 tests / API / machine / paired 通过）仅覆盖该时刻的核心，**不覆盖其后改动**。

| ID | 内容 | 完成标志 |
|---|---|---|
| W-59 | **先修 rejection 真失败**：W-40 中 handshake mismatch fixture 先被 Fabric registry remap 以"998 个未知 mekanism 条目"拒绝，未走到 Mekanism 自己的拒绝路径。判断是 fixture 搭法问题还是真实回归，修到 `rejection smoke: PASSED` | 标记行 PASS；原因写入 `M-release.md` |
| W-60 | 全量测试 + 全部 smoke（含 Tools/Additions/Generators 的 JAR）逐条 PASS；先满足 `COORDINATOR_NOTES.md` 的 C-1…C-4（负向探针、recipe 计数兜底） | 逐条 PASS，记录耗时 |
| W-61 | `./build.sh --clean` 多 JAR 发布物核验（核心 + 三个附加）；干净构建后重跑同一静态检查并记录各 jar sha256（C-3） | `build-info.json` 含全部 sha256 |
| W-62 | 文档收口：README 状态表全部"已验证"或带具体残留；发布限制汇总终稿；`PORT_MATRIX`/`FABRIC_PORT` 一页结论；相关活文档归档 | 完成声明由协调者复核后才成立 |

### P13 补充（协调者 2026-10-31 04:55 裁决，W-62 前置）

| ID | 内容 | 完成标志 |
|---|---|---|
| W-69 | **发布 JAR 客户端启动与握手（时间盒 60 分钟）**：用四个发布 JAR 起一个干净客户端，经 Fabric Loader 加载（优先复用 Loom 缓存里的 MC client jar / assets / libraries，离线模式绕过 sessionserver，Xvfb 下运行），取客户端 `client_applied values=458` 与 ack。目的：覆盖发布路径特有风险——intermediary 名下的客户端 mixin refmap、四模块客户端类（渲染器、屏幕）加载 | 标记行 PASS + 客户端日志无 ERROR；**或**时间盒耗尽时在 `M-release.md` 与 README 发布限制写明"发布 JAR 客户端路径未验证"并转 P15 |
| W-60b | 附加模块的负向探针：在发布 JAR 拷贝上各做一次——缺少一个 addon JAR、重复 addon id、addon `depends.mekanism` 版本错误——对 `fabric-packaged-smoke.sh` 的 addon 检查各应 FAIL；结果追加到 `negative-probes.json`。半小时内可完成，在拷贝上做，真实产物不动 | 3 条首错原文入档 |
| W-62 | **（W-69 与 W-60b 已完成，协调者于 05:17 复核通过）** 收口措辞：此前在 W-69 与 W-60b 完成前，P13 结论只能写"**发布服务端路径已验证**"，不得写"发布通过" | 协调者复核后才成立 |

### P16 发布准备与收尾（W-70 … W-74；P13 通过后，与 P15 可穿插，按价值排序）

| ID | 内容 | 完成标志 |
|---|---|---|
| W-70 | **一键发布检查**：把 W-60/61/69 的门禁串成 `scripts/release-check.sh`（build --clean → 四 JAR 静态检查 → packaged server → packaged client → paired/rejection → machine），任一步失败即停并打印首条错误原文；只编排现有脚本，不新写检查逻辑 | 一条命令复现 P13 全部结论，退出码可信 |
| W-71 | **发布元数据**：`mod_version` 从 `*-fabric-bootstrap` 过渡到正式号（与官方 `10.7.19.85` 的关系写清，如 `10.7.19.85+fabric.1`）、`fabric.mod.json` 的描述/图标/许可证/联系方式；四个 JAR 一致。版本号选择写入 `M-release.md`，**改号后重跑 W-70** | 四 JAR 元数据一致，sha256 重记 |
| W-72 | **核心 37 个延期文件复盘**：逐项对照 M-platform 台账，凡有玩家可见功能影响的（命令构建器细节、权限、热键等）判断是否值得补；只做有真实功能价值的，其余保持延期理由 | 台账更新，补的项各自 `port:` 提交并真机读数 |
| W-73 | **用户文档**：根 `README` 或 `docs/INSTALL.md`——依赖（Fabric Loader/API、Team Reborn Energy）、四个 JAR 的安装与可选关系、与 NeoForge 存档的差异（W-65 结论）、已知限制（来自 README 发布限制节）| 新用户按文档可装好并启动 |
| W-74 | **第三方集成实现（用户已于 2026-10-31 批准，范围：先 EMI 再 Jade）**：W-74a EMI（39 个上游文件，客户端可选依赖，化学品 ingredient 序列化沿用上游实现）；W-74b Jade（7 个文件 + 共享 lookingat 元素类）。**JEI、WTHIT 不在批准范围，REI/TOP 不做**；依赖一律 `suggests`/运行期探测，不进必需依赖。验收按 `M-integration.md`「验收方式」：mdb owned session 客户端装入查看器，断言机器配方可列出、乙烯 ingredient 出现、`gui problems` 无异常；`fabric-client-window-smoke.sh` 回归 | 两个子项各自 `port:` 提交（每项最多 3 个）+ 真机读数；M-integration.md 状态更新 |

### P17 对照官方的一致性与清理（W-75 … W-79；W-65 的 NeoForge 参考环境 `scripts/neoforge-reference.sh` 可复用）

P15 基本是验证；为避免连续纯验证提交，P17 以"产出差异清单 → 修复"为主，并含清理类的真实代码改动。

| ID | 内容 | 完成标志 |
|---|---|---|
| W-75 | **数据/资源逐文件对照**：官方 NeoForge JAR 与 Fabric 核心 + 三个附加 JAR 的 `data/`、`assets/` 文件清单与 SHA 差异（排除已知的平台文件：`META-INF/neoforge*`、`fabric.mod.json` 等）。差异分类：预期（平台/重命名）、缺失、多余、内容不同；缺失/内容不同的逐条修复或记入限制 | 差异表 + 修复提交；0 个未解释的缺失 |
| W-76 | **注册表 ID 对照**：在 W-65 的官方 NeoForge 参考服上导出方块/物品/方块实体/实体/菜单/配方类型/化学/模块/音效/标签的 ID 集，与 Fabric 四 JAR 服务端导出做 diff | 差异表；0 个未解释的缺失 ID |
| W-77 | **W-65 第二轮**：补 round 1 未覆盖的"运行内容"——用 `data merge block` 在 NeoForge 参考服上写入机器库存/能量/升级/侧配置，再在 Fabric 加载比对；频率数据若无法在无玩家时生成，则记为限制 | 比对表 + 修复 |
| W-78 | **清理过期物**：删除/归档 Task052 时期的临时脚本（`scripts/task052-*.gradle`、`Task052*.java`、`TransmitterModelChainCheck` 等，先 grep 确认无引用）、`registry` 之外的重复文档、遗留 `TASK*` 注释；每删一项在提交信息写明引用检查 | 构建/smoke 仍通过；`scripts/` 无过期项 |
| W-79 | **W-66/W-67 反馈修复（预留）**：浸泡与性能基线发现的问题逐个修复，每个 `fix:` 一提交；无发现则本项记"无"并结束 | 问题清单清零或有明确延期理由 |

### P18 深度验证与发布后加固（W-80 … W-90；P17 已基本完成后的无人值守余量，按优先级排序）

背景：W-71/72/73/75/76/77/78/63 已于 09:11–09:21 完成。剩余残留主要是"需要玩家会话的行为"（库存/能量/频率往返、多维度、HUD）和"规模/长稳"（大型网络、堆）。**mcdebug 同样支持 NeoForge 21.1.x**，所以官方参考服也可以经 mdb 注入库存、能量、频率——这是 W-77 的明确后续。以下为优先级 P0 > P1 > P2。

| ID | 优先级 | 内容 | 完成标志 / 风险 |
|---|---|---|---|
| W-80 | P0 | **含运行内容的存档往返**：用 mdb 的 owned session 连接 NeoForge 参考服（`scripts/neoforge-reference.sh start`），写入机器库存、能量、升级、传输器内容、传送机/QE/安全台频率；停服复制世界到 Fabric 四 JAR 服务端，经 mdb 读回逐项比对 | 差异表；字段不一致的逐条修复或记入限制；频率往返结论明确 |
| W-81 | P0 | **规模与堆基线**（W-66/67 残留）：服务端脚本（允许）+ `/tick sprint` 加速，用 `mek build` 搭大型传输网 + 涡轮 + 裂变，采样 mspt、TPS、`jcmd GC.heap_info`/RSS 起止值；在官方参考服上做同场景粗对比 | 数值表；异常（泄漏、mspt 暴涨）逐条定位或记入未决 |
| W-82 | P0 | **多维度行为**：下界/末地中的传送机跨维度、区块加载器（含 `forceTicks`）、辐射、传输网络跨区块卸载重载；官方对照其中语义差异大的一项 | 每项真机读数；差异修复 |
| W-83 | P1 | **裂变熔毁受控路径**：W-64 因 mdb 无命令 RPC 未能做。现服务端可经控制台 FIFO 执行命令：在隔离世界用 `mek build fission` 搭堆并触发熔毁，读取爆炸/辐射结果；做不到则记录具体命令失败原文并结束 | 读数，或带原文的"不可验证" |
| W-84 | P1 | **CI 工作流**：`.github/workflows/` 加 `./gradlew -p fabric test` + `verifyApiSliceNoNeoForge` + `assemble`（不含需要 MC 运行时的 smoke）；写明本地等价命令 | 工作流文件 + 本地可复现；不新增测试 |
| W-85 | P1 | **许可证与归属审计**：核对四个 JAR 内置/依赖库（night-config 等）的许可证、官方 MIT 归属、`LICENSE`/NOTICE 是否随 JAR 分发；补缺 | `docs/LICENSES.md` + 缺失项修复 |
| W-86 | P1 | **CHANGELOG 与发布说明**：`CHANGELOG.md`（版本 `10.7.19.85+fabric.1`：功能范围、已知限制、与 NeoForge 的差异）；发布说明可直接用于 Modrinth/CurseForge 页面 | 文档；内容均有证据引用 |
| W-87 | P2 | **清理第二轮**：源码中残留的 `TASK0xx` 注释、重复/过时文档（W-78 明确留的第二遍）；每删一项在提交信息写引用检查 | `rg 'TASK0[0-9]{2}' src fabric/src` 无残留（归档文档除外）；构建/smoke 通过 |
| W-88 | P2 | **文档终稿**：根 `README` 重写为入口（项目现状、安装指向 INSTALL、状态指向 milestones、限制）；`docs/` 顶层只留现行文档，过时的归档 | 新用户 5 分钟内能找到安装与限制 |
| W-89 | P2 | **W-81 反馈修复（预留）**：性能/泄漏发现的问题逐个 `fix:` | 清零或有延期理由 |
| W-90 | P1（用户已于 2026-10-31 批准采纳） | **采纳 4 个 byte-identical 官方测试**（W-63 评估：SearchQueryParser、BiLongMultimap、IndexedCuboidMap、ChunkUtils）。仅限这 4 个与官方逐字节一致的测试，不得借机新写其他测试；纳入后跑 `./gradlew -p fabric test` 并更新测试计数文档 | 4 个测试纳入且通过，计数同步 |

无人值守规则：按 P0→P1→P2 顺序；W-74（仅 EMI→Jade）与 W-90 已获用户批准（10-31），在 P0 之后、P1 并列推进；JEI/WTHIT 仍须另行批准；每个 W 项最多 3 个提交；纯文档项（W-86/88）不得连续超过 2 个；遇需要玩家会话的内容只用 mdb owned session，遵守 AGENTS 的进程树规则。

### 回归监测门（取代 P8 对每个模块的整套门禁）

每个模块（M-tools / M-additions / M-generators）收口前跑一次，便宜且足以发现核心退化：`./fabric-build.sh`、`./gradlew -p fabric verifyApiSliceNoNeoForge`、`scripts/fabric-machine-smoke.sh`。失败先修。完整门禁只在 P13 做一次。

### P15 功能缺口与长稳（W-64 … W-68；P13 通过后开始，按价值排序，可被 worker 在 P13 间隙穿插）

P11/P12 已完成后，"文件已移植"不再是进度瓶颈，剩余风险在**未验证的行为**。本阶段只做有真实功能价值的项，不做审计。

| ID | 内容 | 完成标志 / 依赖 |
|---|---|---|
| W-64 | **已知未验证项清单收口**：Generators 音效播放与裂变熔毁（隔离世界、受控）、Additions 语音音频采集/多客户端（需带音频设备的主机，否则明确标"本机不可验证"并写出替代证据）、Tools 之外的 Meka 套装 HUD/按键细节 | 每项：真机读数，或明确的"不可验证 + 原因" |
| W-65 | **NeoForge 存档加载探针**：`AGENTS.md` 要求保留 NBT/存档语义。用官方 NeoForge 版 Mekanism（本仓库官方源码 / 已有 NeoForge 参考环境）生成一份含机器、频率、传输器、多方块的小样本世界，在 Fabric 版加载并比对关键 NBT；**只用独立生成的样本，不读生产/镜像/备份数据** | 样本与比对结果入 `evidence/`；差异逐条记录并修复或记入限制 |
| W-66 | **专用服务器 + 多客户端浸泡**：1 服 2 客户端，传输网络 + 发电机 + 多方块运行 ≥30 分钟，日志无 ERROR/泄漏，tick 时间读数 | 浸泡日志摘要（标记行）+ 结论 |
| W-67 | **性能基线**：大型传输网络与涡轮/裂变的 tick 耗时、区块加载压力；与官方 NeoForge 同场景粗对比（若 W-65 的环境可复用）| 数值表，异常项记入未决 |
| W-68 | **第三方集成立项评估**：JEI/EMI 配方查看是玩家最直接的缺口；评估 Fabric 上 EMI/JEI/REI 适配的最小边界（`client/recipe_viewer` 现有 38+41 文件）、looking-at（WTHIT/Jade）。**仅出边界页**，是否实现等用户裁决 | `M-integration.md` 边界页 |

### P14 收尾评估与发布后议题（W-63，可选）

| ID | 内容 | 完成标志 |
|---|---|---|
| W-63 | 评估官方 `src/gameTest`（12 文件）与 `src/test` 中可直接纳入的 byte-identical 官方测试，作为发布 oracle；`src/datagen` 生成资源与已发布资源的一致性核对。只做评估页，不扩测试矩阵（遵 `AGENTS.md`） | `M-release.md` 一节：纳入/不纳入各自理由 |

发布后议题（本规划不排期，记录以防遗忘）：第三方集成（JEI/EMI 替代、CraftTweaker、ProjectE、looking-at）、wip 提交 squash、submodule 指针更新、`mod_version` 从 `*-fabric-bootstrap` 过渡到正式号。

## 4. 依赖与可并行性

```text
P0 ──► P1 ──► P2 ┐
              ├──► P3 ──► P4 ──► P6 ──► P8 ──► P9 ──► P10
              └────────► P5 ──┘      P7 ──┘
```

- P1 通过前，P4/P6 的"通过"结论不作数（依赖 owner）。
- 同一时刻只有一个实现者；P3–P7 内部互不依赖的项，worker 可按"阻塞则跳"调整顺序，但需在提交信息里写明跳过原因。

## 5. 风险与预案

| 风险 | 预案 |
|---|---|
| P0 发现门禁已退化或 smoke 失败 | 先修（`fix` 提交），把原 W 项顺延；规划书不重写，只在 README 记"顺延" |
| 某多方块/模块需要大块新模块 | 记入未决项，继续后续项；P8 前汇总一次交协调者裁决 |
| mcdebug 与游戏 mod 版本不匹配 | 按 `AGENTS.md`"客户端调试"用工作树内的 CLI；确认 `status` 后再操作 |
| 工作量超出 44 项 | 失败项 `fix` 可追加编号 `W-xx.n`，不改变总体顺序 |
| 提交数量膨胀回到流水账 | 每个 W 项最多 3 个提交（实现 / 修正 / 文档）；审查与证据写进同一提交 |

## 6. 每个 Phase 的汇报格式（worker 在 Phase 结束写入对应 `M-*.md`）

1. 完成的 W 项与提交 id。
2. 门禁结果（三项命令 + 一个 smoke）。
3. 新增未决项（调用链 + 最小边界）。
4. 与规划书的偏差（顺延、拆分、跳过）。

### P19 集成收尾与发布前回归（W-91 … W-96；E2 巡检补充，W-74a/b、W-84…90 大多已完成后的余量）

背景：10:38–11:12 完成 W-89、W-74a（EMI 真机验证）、W-74b（Jade）、W-90（实际只缺 1 个：`SearchQueryParserTest`，其余 6 个早已纳入）。路线图原有项基本用尽，以下以**开发/修复项**为主。JEI、WTHIT **仍未获批**，不在本节。

| ID | 优先级 | 内容 | 完成标志 / 风险 |
|---|---|---|---|
| W-91 | P0 | **EMI 范围补全**：W-74a 当前验证了核心配方与乙烯 ingredient；补 ①三个附加模块自己的 EMI 分类（上游约 14 个 viewer 文件）；②QIO 合成转移（`QIOCraftingTransferHandler`）、ghost ingredient、exclusion 的真机交互；③ item 流体容器在 Fabric 上只显示原版容器的 port delta 是否影响 Mekanism 罐 | 每项 mdb 真机读数或带原文的限制；JAR 不含 `dev/emi` 类的断言保持通过 |
| W-92 | P0 | **Jade 范围补全**：W-74b 已有 tooltip 截图；补 ①化学品/能量/流体元素、多方块（涡轮/裂变/储罐）、传输器的显示读数；②Jade 的 entity provider（机器人 Robit）；③附加模块方块；④无 Jade 时（不装）客户端启动无异常 | 读数表；缺项修复或记入 M-integration 限制 |
| W-93 | P0 | **集成改动后的全量回归**：EMI/Jade 改了 `common-slice`（1564→1580+）、`GuiBar.IBarInfoHandler` 提到顶层（21 个文件）、api-slice；跑 `scripts/release-check.sh` 全量（含 C-3 可复现哈希、四 JAR 无第三方类泄漏），把 W-70 结论刷新到当前 HEAD，并同步测试计数（457/584）到所有文档 | 一次完整通过的 release-check 记录；P13 结论措辞在新 HEAD 上仍成立 |
| W-94 | P1 | **注册表差异的实质修复**：W-76 留下 items 1064 vs 1066、tags 444 vs 448 的差异，逐项判断是"Fabric 合理差异"还是"漏移植"；漏的补上 | 差异清零或每项带理由 |
| W-95 | P1 | **IBarInfoHandler 结构偏离复核**：官方为 `GuiBar.IBarInfoHandler` 嵌套接口，现提到顶层。核对对外二进制/源码兼容影响（附加模块、第三方）并在 `API_DELTAS.md` 记录；若可恢复嵌套而不破坏 EMI 编译则恢复 | 记录或恢复，二选一，有依据 |
| W-96 | P1 | **发布演练**：按 `docs/INSTALL.md` 在干净目录从四 JAR 装好、起服、加载官方样本世界（W-65）、读 W-77/W-80 往返、关服，全程只用脚本与 mdb；产出 `docs/RELEASE_CHECKLIST.md`（打 tag、校验 sha256、上传物清单）。不上传、不 push | 清单文档 + 一次演练记录 |

无人值守追加规则：W-91/92 先于 W-94…96；W-93 必须在 W-91/92 完成后再跑（避免重复 14 分钟全量）；W-74 范围外（JEI/WTHIT/REI）不得启动。

### P20 可复现缺陷与兼容性余量（W-97 … W-100；E4 巡检补充，最后一次自动巡检后的无人值守余量）

| ID | 优先级 | 内容 | 完成标志 / 风险 |
|---|---|---|---|
| W-97 | **P0（先于 W-91…96）** | **核心 JAR 不可复现的根因修复**：同一 commit 连续两次 `./build.sh --clean`，核心 JAR 哈希不同（附加三个一致）。已发现并在工作区修改 `ComputerMethodProcessor`（HashMap→TreeMap 排序）——验证它是否是根因：对两次构建的核心 JAR 逐条目比对，列出不同条目；其余候选（JiJ 内嵌 jar、`MANIFEST`、注解处理器的其他无序集合）逐个排除。修复后 `build.sh --clean` 连续两次四个哈希全一致 | 两次 clean build 四哈希一致的记录；W-89/W-93 证据页中"可复现仍成立"的错误表述更正 |
| W-98 | P1 | **渲染器兼容抽样**：B4 的"未覆盖"项。用 mdb owned session 在开发客户端分别装入 Sodium、Iris（含一个光影包）、至少一个资源包，对比 Mekanism 的方块实体渲染、多方块、激光/粒子、GUI 是否异常；`gui problems` 与日志读数。装不上或无法验证的如实记限制 | 组合×渲染项的读数表；崩溃/花屏逐条修复或记入 INSTALL 的已知限制 |
| W-99 | P1 | **能量互通真实第三方样本**：当前 Team Reborn Energy 互通只在自身验证。选一个真实的 Fabric 科技模组（如 Modern Industrialization 或 Tech Reborn；先查 Modrinth 1.21.1 可用版本），做 Mekanism 发电机→对方机器、对方发电机→Mekanism 机器的真机读数，单位换算与官方 `IEnergyConversion` 语义一致 | 双向读数；不一致修复或记限制；该模组仅作客户端/服务端测试依赖，不进发布依赖 |
| W-100 | P2 | **给用户的决策清单**：写 `docs/milestones/DECISIONS_PENDING.md`，汇总仍待用户的事项（wip 提交 squash 的具体 commit 范围与风险、submodule 指针更新步骤、推送本地领先提交的清单、JEI/WTHIT 是否批准、第三方集成的最终范围），每项给推荐与理由。**不执行**任何一项 | 一页决策清单，条目都能直接勾选 |

无人值守追加规则：W-97 先做且最多 3 个提交；W-99 引入的第三方模组只能放测试运行期；所有新的客户端会话仍只用 mdb owned session。

### W-74 范围追加（用户 2026-10-31 批准：JEI 与 WTHIT）

W-74 的批准范围由"EMI→Jade"扩大为 **EMI → Jade → JEI → WTHIT**（REI、TOP 仍不做）。

| ID | 优先级 | 内容 | 完成标志 |
|---|---|---|---|
| W-74c | P1 | **JEI**（上游 41 个文件，24 个机器分类，QIO 合成转移需单独核对）。同 EMI：`modCompileOnly` + `-PjeiDev` 运行期，不进发布依赖；发布 JAR 泄漏断言已含 `mezz/jei/`；entrypoint 键（`jei_mod_plugin`）按 W-93 的方式写入契约并注明仅可选集成键；slice 新增条目按 owner/调用链记入 `M-integration.md` | mdb 真机：机器配方可列出、乙烯 ingredient 出现、QIO 转移可用；全量 release-check 再过 |
| W-74d | P2 | **WTHIT**（上游 5 个文件，与 Jade 共享 lookingat 元素类）。同样可选依赖 | mdb 真机 tooltip 读数 |

顺序：W-97 → W-91/92（EMI/Jade 补全）→ W-74c → W-74d；JEI 的 slice 改动落地后，**最终 HEAD 上须再做一次完整 `release-check.sh`**。

### W-101 wip 提交 squash（用户 2026-10-31 批准，**由 worker 执行**）

| ID | 优先级 | 内容 | 完成标志 |
|---|---|---|---|
| W-101 | P1（在 W-97 之后、工作区干净且无进行中批次时做；不与 W-91…W-96 的开发穿插） | 把 `java-port` 上约 62 个 `wip:` 提交按**功能主题**合并成少量有意义的提交（建议按里程碑：owner / config / platform / transfer / render / multiblock / registry / gear 等，非 wip 提交与后续 W 项提交保持原样）。步骤：①工作区必须干净；②先建本地备份 `git branch backup/pre-squash-<日期>` 与 tag；③用非交互方式重排（`git rebase -i` 在本环境不可用，用 `git reset --soft`/`commit-tree`/`cherry-pick --no-commit` 或脚本化 rebase 构造新历史）；④**验证新旧最终树逐字节相同**（`git diff backup/pre-squash-<日期> HEAD` 为空，`git rev-parse HEAD^{tree}` 两者相同）；⑤新提交信息写清合并了哪些旧提交（保留旧 hash 列表）；⑥跑 `./gradlew -p fabric test` 确认 | 新历史无 `wip:` 提交；树哈希与备份一致；记录写入 `M-release.md`。**强制推送不属于本项**：改写历史后 origin 与本地分叉，`git push --force-with-lease` 必须**等用户明确同意后**才执行，worker 只准备好并写入 `DECISIONS_PENDING.md`（W-100） |

### P21 JEI 收口与余量（W-102 … W-105；F2 巡检补充）

| ID | 优先级 | 内容 | 完成标志 / 风险 |
|---|---|---|---|
| W-102 | P0 | **JEI 范围验证**（W-74c 后续）：当前只有 `jei-inventory.png`。补 ①24 个机器分类逐类抽样（至少每种 recipe 形态一类：物品→物品、化学品→化学品、流体、能量、多输入）能列出配方；②QIO 合成转移（`JeiQIOCraftingTransferHandler`）真机可用；③ghost ingredient；④JEI 与 EMI 同装时"EMI 在场不启用 JEI 处理器"规则；⑤三个附加模块的 JEI 分类 | 每项 mdb 读数或带原文的限制 |
| W-103 | P0 | **JEI/WTHIT 落地后的最终全量**：JEI 使 common slice 1580→1627；W-74d 再改一次。两者都完成后在最终 HEAD 上跑一次完整 `release-check.sh`（含 build --clean），连续两次 `build.sh --clean` 四哈希一致，刷新 `RELEASE_CHECKLIST.md` 第 6 节的 sha256 | 一次通过的 6 步 + 可复现记录 |
| W-104 | P1 | **W-81/82 余项的修复**：涡轮性能、真实区块卸载、跨维度传送余项中凡暴露出来的缺陷逐个 `fix:`（不是只记录）；确实不是缺陷的写依据 | 缺陷清零或每项有依据 |
| W-105 | P1 | **游戏内配置界面核对**：官方 NeoForge 版在客户端注册了配置界面入口（`IConfigScreenFactory`）。核对 Fabric 版是否有等价入口（Mod Menu 的 `modmenu` entrypoint 或自带界面），没有则按"可选依赖 + 最小适配"补齐；有则真机截图 | 读数/截图，或补齐后的 mdb 验证 |

### P22 找 bug 阶段的场景化验证（W-106 … W-109；F4 巡检补充，最后一次自动巡检后的无人值守余量）

背景：四个第三方集成（EMI/Jade/JEI/WTHIT）已落地，功能移植基本完成，工作重心转向"找 bug"。以下以**玩家真实玩法场景**为主，每项都应先尝试暴露缺陷，找到就修（`fix:`），而不是只做复核。

| ID | 优先级 | 内容 | 完成标志 / 风险 |
|---|---|---|---|
| W-106 | P0 | **矿石加工链端到端**：用 mdb 的 server plane 搭一条真实链（如铁：粉碎/富集→溶解→化学氧化→净化→注入→结晶器 → 锭），用管线连接，读每级产出并与官方配方期望（`1 块矿 → 5 倍产出` 的各档倍率）对照；**在 NeoForge 参考服上跑同一链做数值比对** | 逐级倍率表；任何偏差定位到配方/机器/传输；修复或记限制 |
| W-107 | P0 | **崩溃/卸载鲁棒性**：①传输/加工进行中 `kill -9` 服务端再启动，存档能否加载、机器与管线状态是否合理；②传输网络跨越区块在加工中被卸载、重载；③带内容的多方块（涡轮/储罐）在半建成状态存读档。进程树按 AGENTS 回收 | 每个场景的读数；崩溃/丢物品/重复物品逐个修复 |
| W-108 | P1 | **安全/权限与频率**：安全台（Security Desk）的公开/私有/受信模式对机器 GUI、传输器、QIO 的访问控制，频率的所有权与跨玩家可见性。mdb 服务端可用假玩家则用；不行则记录"需要第二个玩家会话"，**不得裸起第二个客户端** | 读数或带原文的限制 |
| W-109 | P2 | **文档同步**：`CHANGELOG.md`、`INSTALL.md`、`RELEASE_CHECKLIST.md` 补入 EMI/Jade/JEI/WTHIT 四个可选集成（版本、`-P*Dev` 开关、已验证与未验证项）和 `docs/LICENSES.md` 的对应条目 | 文档与证据一致 |

无人值守追加规则：W-106/107 与 W-102 并列 P0；纯文档项（W-109、W-103 的文字部分）不得连续超过 2 个；发现缺陷优先修复而不是继续新增验证。

### P23 找 bug 的纵深（W-110 … W-113；G0 即时检查补充）

背景：W-106/107/108 三次真机场景都**没有发现缺陷**，其中 W-106 只搭了 3 台独立机器的 ×2/单步链，W-108 因单玩家会话被阻塞。零发现不等于没有 bug，往往说明场景太浅。下一步把场景推向**最容易出错的地方**：多系统耦合、化学品、跨玩家、升级与工厂。

| ID | 优先级 | 内容 | 完成标志 / 风险 |
|---|---|---|---|
| W-110 | P0 | **安全拒绝的双玩家验证**（W-108 阻塞项）：在既有 `scripts/fabric-paired-network-smoke.sh` 的双客户端环境里**最小扩展一条断言**——玩家 A 把机器设为私有，玩家 B 尝试打开被拒绝；再加受信者可打开。不新写框架，不新增测试类；不能做就带原文记录限制 | 拒绝/放行两条读数，或带原文的限制 |
| W-111 | P0 | **完整化学链（×3/×4/×5）**（W-106 余项）：溶解→氧化→洗涤→结晶→注入，用真实加压管线与化学品槽相连，读每级产出并与 NeoForge 参考服同链数值对照；关注管线阻塞、槽满回压、气体混合拒绝 | 倍率表 + 官方对照；偏差定位并修复 |
| W-112 | P1 | **工厂 / 升级 / 并行**：Factory 各 tier、速度/能量/过滤升级、并行加工在满输入输出时的吞吐与回压；QIO 驱动阵列在高并发存取下的计数一致性（物品守恒） | 守恒与速度读数；不一致修复 |
| W-113 | P1 | **mdb 启动前置固化**：W-106 调试时因 `fabric/build/loom-cache/argFiles/runClient` 缺失，mdb 启动失败，导致 worker 一度裸启 `./gradlew runClient`（已自述违规）。把"先跑 `configureClientLaunch` 再 `mdb session start`"固化进一个小脚本或 AGENTS 的 mdb 小节一行说明，**防止再违规**（只限 mdb 前置，不改 mdb 本身） | 一条可复用命令；`fabric/gradle`/`gradlew` 临时软链接也在同一脚本里创建并保证退出即删 |

规则：发现缺陷优先 `fix:`，每个 W 项最多 3 个提交；纯证据提交不得连续超过 3 个（G0 观察到 98336009→25908976 间 5 个证据/文档提交，虽有真机运行但零缺陷）。

### P24 把"挂起"项重新定为必做（G2 巡检；worker 的"收尾状态"表不被接受）

`a3530a37` 把 W-79/W-98/W-99 标为"等用户裁决或外部样本"，把 W-104/110/111/112 标为"非一次会话能完成"。**这些理由不成立**，下列各项恢复为必做，按顺序推进：

| ID | 优先级 | 修正后的做法 |
|---|---|---|
| W-111 | P0 | **不是一次会话的事就分多次会话**，每个会话一个提交。先做最小可行链：电解水→氢/氧 → 化学品槽 → 加压管线 → 化学氧化机/溶解室，再逐级增加；每级读数并与 NeoForge 参考服同链对照。AGENTS 的"完整纵向闭包"是交付粒度，不是不做的理由 |
| W-110 | P0 | 双玩家 fixture 不是前提。备选路径（任选其一，写明为何）：①在 mdb 单人会话里用 `be set-nbt` 把机器 `ownerUUID` 改成另一个 UUID，再以真实玩家 `gui open-block`，断言被拒绝；②直接在服务端 plane 读 `SecurityUtils`/`IOwnerAccess` 的判定入口；③确需第二玩家再最小扩展 paired smoke 的 fixture（它是脚本，不是新测试）。三条都走不通时带原文记录，而非以"超出最小扩展"为由搁置 |
| W-98 | P1 | **路线图已明确是自主项，不等用户**。Sodium/Iris 在 Modrinth 都有 1.21.1 Fabric 版，下载方式与 EMI/WTHIT 相同（运行期开关，不进发布依赖）；光影包选一个常见的（如 Complementary）。读数表即可，装不上才记限制 |
| W-99 | P1 | **自主项**。Modern Industrialization 或 Tech Reborn 任选其一，下载同样走 Modrinth；做发电机↔机器双向读数 |
| W-104 | P1 | 涡轮满负荷（用 `/tick sprint`）、区块卸载后网络重连、跨维度传送，三个场景各一个会话，发现问题 `fix:` |
| W-112 | P1 | 工厂/升级/并行与 QIO 物品守恒；至少做 QIO 在 `kill -9` 前后的物品守恒（延续 W-107） |
| W-79 | 说明 | **只是 W-66/W-67 的反馈修复预留槽**（不是"整合包形态"，那是文档误写）；无发现则记"无"，不挂起 |

### P25 H3 巡检补充：把"过了"变成"真的证明了"（W-114 … W-117）

| ID | 优先级 | 内容 | 完成标志 / 风险 |
|---|---|---|---|
| W-114 | P0 | **W-104 区块卸载断言要真的证明"已卸载"**：`data get block` 对未加载区块会报 `That position is not loaded`，所以"卸载期间读到了库存"恰恰说明那一刻区块**仍是加载的**（`forceload remove` 后区块不会立刻卸载，票据要过期）。改法：`forceload remove` 后用 `/tick sprint` 推进，并用 `execute if loaded 2000 100 0` 断言返回**假**（区块确实卸载）；之后 `forceload add` 重新加载，再断言库存完好、继续加工。把固定 `sleep 15` 换成 `/tick sprint` 或轮询 | 脚本断言含"确实已卸载"一条；EXIT=0 且 3 次一致 |
| W-115 | P0 | **洗涤/结晶阶段"化学弹射送不到相邻机器"不能只回退了事**。这可能是 setup 错（侧配置的输出/弹射未开、目标机器输入面不对），也可能是真缺陷。在同一 rig（`data merge`）上对**官方 NeoForge 参考服**做相同布置（直接相邻的化学输出机器→化学输入机器，弹射开启）；官方能送、移植送不到 → 缺陷，定位到化学弹射/`ChemicalUtil.emit`/能力解析并 `fix:`；两边都送不到 → 记 setup 结论 | 官方/移植对照读数；缺陷则有修复提交 |
| W-116 | P1 | **涡轮满负荷**（W-104 余项）：不依赖玩家命令。用 `setblock`/`fill` 在专用服务端 rig 里手搭最小涡轮（外壳 + 阀门 + 转子 + 涡轮叶片 + 通风口），注入蒸汽，`/tick sprint` 跑满负荷，读发电量、蒸汽消耗、与官方参考服同布置对照。搭不出来则带原文记录并结束，不再挂起 | 数值或带原文限制 |
| W-117 | P1 | **P24/P25 全部完成后的收口序列**（不可乱序）：①`release-check.sh` 最终全量（机器 smoke 已增长）；②两次 `build.sh --clean` 四哈希一致；③W-101 squash（22 个显式 wip，备份在先，树哈希一致，不强制推送）；④把结果与待用户项写入 `DECISIONS_PENDING.md` | 一次完整序列记录 |

规则：W-98/W-99（P24）仍是自主项，在 W-114/W-115 之后立即做，不得再以"等用户"为由挂起。

## 2026-10-31 会话收尾（W-104/110/111/112 现状）

| 工作项 | 状态 | 证据 |
|---|---|---|
| W-104 真实区块卸载 | ✅ 完成 | `execute unless loaded` 证明真卸载 + `tick sprint` 重载后续加工；smoke 29 PASS（[w104-chunk-unload.json](evidence/p8-release/w104-chunk-unload.json)）|
| W-104 涡轮（W-116） | 未做 | 多方块需手工搭建，风险高，留给后续会话 |
| W-104 跨维度传送 | 未做 | 需玩家实体移动，mdb 无该能力 |
| W-110 双玩家安全拒绝 | 阻塞 | 单人会话改不动所有者（`reflect` 传不了 UUID）；`/setblock` 机器无主且 `owner == null → 允许`。需 paired fixture 双用户名（[w110-security-denial.json](evidence/p8-release/w110-security-denial.json)）|
| W-111 ×3 链 | ✅ 完成 | 硫酸→dirty_iron→clean_iron→**crystal_iron** 实测（[w111-chemical-chain.json](evidence/p8-release/w111-chemical-chain.json)）|
| W-111 ×4 链 | ✅ 化学反应 | 卤水→氯→**HCl**→**shard_iron** 实测；官方服对照一致；缺口：注入室 HCl 用种子（输送未断言）|
| W-111 ×5 链 | 未做 | 净化/粉碎/富集 |
| W-112 tier/并行 | ✅ 完成 | 三档缓冲 20k/40k/80k、progress 3/5/9；sorting 开启后 **9 进程并行**、20 秒 2→9 矿石（[w112-factory-throughput.json](evidence/p8-release/w112-factory-throughput.json)）|
| W-112 升级 | 阻塞 | 升级槽是虚拟容器槽，`be set-nbt` 写不进、需光标持有物品 |
| 发布门禁 | ✅ 通过 | `release-check.sh` 六步全绿；机器 smoke 提速 113s→65s |

**关键经验（避免重复踩坑）**

1. `component_config` 的 6 元侧面数组按 **`RelativeSide`**（FRONT/LEFT/RIGHT/BACK/TOP/BOTTOM）索引，**不是绝对方向**。
2. 侧面类型：`INPUT(1)`、`OUTPUT(4)`、`OUTPUT_2(6)`、`INPUT_OUTPUT(7)` 语义不同——分离器的氯在右罐必须用 **6**；机器既要进料又要弹射用 **7**；缓冲储罐也用 **7**，否则管道拉不走。
3. 断言要放在**下游**：会弹射的机器自身罐在 dump 时通常是空的（分离器、溶解室都是这样）。
4. 专用服务端 + `data merge` 才能改激活中的方块实体组件；客户端 `be set-nbt` 改不到。
5. 机器只吃**游戏 tick**，无玩家时用 `/tick sprint` 加速，不要固定 `sleep`。
6. `/forceload` 用**方块坐标**；未加载区块拒绝 `setblock`。
