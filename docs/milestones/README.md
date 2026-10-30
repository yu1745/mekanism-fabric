# 里程碑与状态（取代 TaskNNN 流水编号）

**Task052 已关闭为历史名**：它最初的定义是"TileEntityMekanism 具体 owner 闭包"（`docs/archive/TASK052_PLAN.md`），但 2026-09-14 至 10-30 间约 350 个提交已分散进多个独立系统。此后不再新开 `TaskNNN`，改用下列**按系统命名、以可验证功能为验收**的里程碑。每个里程碑只维护一份状态（本表）和至多一份活文档，不再产生 CONTINUATION_NN / CHECKPOINT_NN / REVIEW_NN 编号文件。

> 注：`FABRIC_PORT.md` 与 `PORT_MATRIX.md` 的任务叙事止于 Task049，**未记录 Task050–052**；在补写之前，本表与 `git log` 才是 050+ 的状态依据。

后续提交的执行规划见 `docs/milestones/ROADMAP.md`（W-01…W-44，按 Phase 推进）。

## 状态表

状态含义：未核实 / 进行中 / 已提交待验证 / 已验证（有真机或构建证据）。状态来自提交主题与文档标题，**逐项核实前不要当作完成声明**。

| 里程碑 | 范围 | 验收（一个端到端用例） | 状态 |
|---|---|---|---|
| M-owner | `TileEntityMekanism`、`TileComponentConfig`、频率/`FrequencyManager`、`ContainerType`、`MekanismContainer` 同步、有效侧线程语义、bounding | 真机：Crusher 放置→通电→加工→存读档 | 未核实（Astra whole-owner 与 convergence 文档为最近记录） |
| M-config | gear / machine+tier / common+startup / general 四类配置 owner 与同步 | 非默认配置真机加载并同步到客户端 | gear、machine+tier、common+startup 已提交且有运行证据；general 已提交 `741d580d`（wip），待真机验证；活文档 `M-config.md` |
| M-transfer | 流体/物品传输 owner、pipe 查询生命周期、`SnapshotParticipant`、QE 频率流体 | 管道把流体/物品送入机器并回滚正确 | 未核实 |
| M-render | transmitter 渲染（OBJ/MTL 几何）、Robit atlas、QIO 渲染 | mcdebug 截图：管线与 QIO 正确渲染 | 未核实 |
| M-registry | 化学 ingredient 类型注册表、Robit 注册/数据包、官方 legacy alias、biome modifier | 注册冻结后 ID/alias 与官方一致 | 未核实 |
| M-gear | accessory、Curios 策略、ItemAbility、suit/tool 规则 | 穿戴/工具行为真机核对 | 未核实 |

## 旧 Task052 文档归类（按标题，未逐份通读）

- **M-owner**：`TASK052_PLAN`、`ADJUDICATION`、`ADJUDICATION_EFFECTIVE_SIDE`、`CONTINUATION_03/14`、`CHECKPOINT`、`CONVERGENCE_14`、`ASTRA14_PROGRESS`、`ASTRA_REPAIR`、`PAUSE_*`、`REVIEW_02/03/04/12/14`、`RESUME_2026-10-26`、`RECENT_COMMITS_AUDIT_2026-10-27`
- **M-transfer**：`CONTINUATION_04/06/07/08/09`（及 `_EVIDENCE`）、`CHECKPOINT_05/06/09/13`、`REVIEW_05/06/07/08/09/10/11/13`、`REVIEW13_FIX_WIP`、`ITEM_*`、`PIPE_LIFECYCLE_EVIDENCE`
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
