# 规划书：2026-10-30 起的后续提交（W-01 … W-44）

协调者编写，worker 按序执行。状态以 `docs/milestones/README.md` 为准；本文只定"做什么、什么顺序、怎样算完成、卡住怎么办"。不得借本文扩大 `AGENTS.md` 的边界。

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

### P8 发布门禁（W-40 … W-42）

| ID | 内容 |
|---|---|
| W-40 | 全量测试 + 全部 smoke（machine/paired/rejection/packaged/recipe/chemical/model/worldgen/retrogen/tile）逐条 PASS，记录耗时 |
| W-41 | `./build.sh --clean` 发布物核验：remapped JAR、`build-info.json` sha256、干净服务端启动 |
| W-42 | 文档收口：`README.md` 状态表全部为"已验证"或带具体残留项；`PORT_MATRIX`/`FABRIC_PORT` 一页结论；相关 `M-*.md` 归档 |

### P9 Tools 模块立项（W-43）

| ID | 内容 |
|---|---|
| W-43 | 通读 `src/tools`（52 文件）：写一页边界说明（依赖核心哪些 owner、需要的 slice、风险），建 `docs/milestones/M-tools.md`。**仅文档提交**；之后沿用本规划的协议接力实现 |

### P10 后续模块（W-44，滚动）

| ID | 内容 |
|---|---|
| W-44 | Tools → Additions → Generators，每个模块重复 W-43 的"边界说明 → 分 Phase 实现 → 门禁 → 验收"。Generators（121 文件，含涡轮/裂变/聚变多方块）最后做；开始前再出一版本规划书 |

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
