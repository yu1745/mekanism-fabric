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
- **2026-10-31 05:17 巡检 B4（最后一次）**：W-60b、W-69 已完成（`04d54f00`、`470fe86e`），A1/B3 的全部要求落实；**P13 的发布路径门禁（核心 + 三个附加模块，服务端与客户端发布 JAR）判定为协调者复核通过**，范围与未覆盖项见 COORDINATOR_NOTES B4。路线图补 P16 W-70…74（一键发布检查、发布元数据、核心延期文件复盘、用户文档、第三方集成待裁决）。此后无自动巡检；worker 按 P15 → P16 自行推进，遇边界问题写 `M-*.md` 未决项并跳过。\n- **0. 已知事实**中的 slice 数字、"约 1700/1802"为 10-30 早间快照，当前 platform slice 已 207→209，不作为依据。

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
| W-05 | 盘点 `docs/` 顶层未审文档（`Development.md`、`Mekanism.xml`、`MODEL_IMPLEMENTATION_COMPARISONS.md`、`MACHINE_SPINE.md`、`MACHINE_OWNER_ADMISSION.md`、`FABRIC_*.md`）：过时者归档或加"已被取代"头注 | 顶层文档均有明确现状 |

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
| W-74 | **第三方集成实现（须用户裁决）**：仅当 W-68 边界页被用户批准后才做；未批准则本项不启动，不得自行扩题 | 用户批准记录 |

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
