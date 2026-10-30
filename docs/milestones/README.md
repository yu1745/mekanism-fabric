# 里程碑与状态（取代 TaskNNN 流水编号）

**Task052 已关闭为历史名**：它最初的定义是"TileEntityMekanism 具体 owner 闭包"（`docs/archive/TASK052_PLAN.md`），但 2026-09-14 至 10-30 间约 350 个提交已分散进多个独立系统。此后不再新开 `TaskNNN`，改用下列**按系统命名、以可验证功能为验收**的里程碑。每个里程碑只维护一份状态（本表）和至多一份活文档，不再产生 CONTINUATION_NN / CHECKPOINT_NN / REVIEW_NN 编号文件。

> `FABRIC_PORT.md` 与 `PORT_MATRIX.md` 已补 Task050–052 的简明索引；早期叙事保留作历史证据，当前状态仍以本表及对应活文档为准。

后续提交的执行规划见 `docs/milestones/ROADMAP.md`（W-01…W-44，按 Phase 推进）。

## 状态表

状态含义：未核实 / 进行中 / 已提交待验证 / 已验证（有真机或构建证据）。状态来自提交主题与文档标题，**逐项核实前不要当作完成声明**。

| 里程碑 | 范围 | 验收（一个端到端用例） | 状态 |
|---|---|---|---|
| M-owner | `TileEntityMekanism`、`TileComponentConfig`、频率/`FrequencyManager`、`ContainerType`、`MekanismContainer` 同步、有效侧线程语义、bounding | 真机：Crusher 放置→通电→加工→存读档 | 已验证：P1 W-06–11与门禁通过；[证据](../archive/M-owner.md)。 |
| M-config | gear / machine+tier / common+startup / general 四类配置 owner 与同步 | 非默认配置真机加载并同步到客户端 | 已验证：P2 W-12–14与门禁通过；[证据](../archive/M-config.md)。 |
| M-platform | P3 核心平台入口、命令、成就、条件、生命周期与 payload 核对 | 原始行为由真实 Fabric owner 承接，命令真机可执行 | 已验证：P3 W-15–22与门禁通过；46项边界有明确理由；[证据](../archive/M-platform.md)。 |
| M-transfer | 能量、流体、物品、化学、热量传输与网络生命周期 | 管线连接真实机器，传输／回滚与存读档后重建正确 | 已验证：P4 W-23–28与门禁通过；[证据](../archive/M-transfer.md)。 |
| M-render | transmitter/Robit/QIO 模型、粒子、GUI、按键与客户端注册 | mcdebug 实际外观／连接截图与 GUI 巡检 | 已验证：P5 W-29–33及578 tests/API/machine门禁通过；[证据](../archive/M-render.md)。 |
| M-registry | 化学 ingredient 类型注册表、Robit 注册/数据包、官方 legacy alias、biome modifier | 注册冻结后 ID/alias 与官方一致 | 未核实 |
| M-gear | accessory、Curios 策略、ItemAbility、suit/tool 规则 | 穿戴/工具行为真机核对 | 未核实 |

## 旧 Task052 文档归类（按主题；P0 复核范围见归档 M-owner）

- **M-owner**：`TASK052_PLAN`、`ADJUDICATION`、`ADJUDICATION_EFFECTIVE_SIDE`、`CONTINUATION_03/14`、`CHECKPOINT`、`CONVERGENCE_14`、`ASTRA14_PROGRESS`、`PAUSE_*`、`REVIEW_02/03/04`
- **跨里程碑索引**：`RESUME_2026-10-26`、`RECENT_COMMITS_AUDIT_2026-10-27`（owner / transfer / render / registry / gear / config；历史 blocker 需与后续 Git 核对）
- **M-transfer**：`CONTINUATION_04/06/07/08/09`（及 `_EVIDENCE`）、`CHECKPOINT_05/06/09/13`、`REVIEW_05/06/07/08/09/10/11/12/13/14`、`ASTRA_REPAIR`、`REVIEW13_FIX_WIP`、`ITEM_*`、`PIPE_LIFECYCLE_EVIDENCE`
- **M-render**：`CONTINUATION_10`（及 `_EVIDENCE`/`_EVIDENCE_2`）、`CONTINUATION_11`、`REVIEW_15/16/17/18`、`REVIEW17_REPAIR`
- **M-registry**：`CONTINUATION_12`、`CONTINUATION12_INGREDIENT_REGISTRY`、`CONTINUATION_13`、`REVIEW_19/20`、`REVIEW19_REPAIR`
- **M-gear**：`ADJUDICATION_GEAR_CATALOG`
- **M-config**：`docs/archive/TASK052_MACHINE_CONFIG_CONTINUATION.md`、`docs/archive/TASK052_GENERAL_CONFIG_BOUNDARY.md`（已并入 `docs/milestones/M-config.md`，原文移入 archive）

除 M-config 两份外，其余文件保持原路径（链接不变）。

## 活文档规则

- 每个进行中的里程碑一份 `docs/milestones/M-<name>.md`，仅含：**现状 / 下一步 / 未决项 / 验收证据** 四节，追加式更新。
- 状态只改本表；叙事不写进 `PORT_MATRIX`/`FABRIC_PORT`，两者只在里程碑完成时补一行结论。
- 新发现的中枢级风险记入 M-owner，不新开里程碑；超出某里程碑范围的事项记入"未决项"，不静默扩题。
- 里程碑验收通过后：状态改"已验证"并附证据，活文档移入 `docs/archive/`。
