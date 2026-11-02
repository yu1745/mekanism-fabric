# 里程碑与状态（取代 TaskNNN 流水编号）

**Task052 已关闭为历史名**：它最初的定义是"TileEntityMekanism 具体 owner 闭包"（`docs/archive/TASK052_PLAN.md`），但 2026-09-14 至 10-30 间约 350 个提交已分散进多个独立系统。此后不再新开 `TaskNNN`，改用下列**按系统命名、以可验证功能为验收**的里程碑。每个里程碑只维护一份状态（本表）和至多一份活文档，不再产生 CONTINUATION_NN / CHECKPOINT_NN / REVIEW_NN 编号文件。

> `FABRIC_PORT.md` 与 `PORT_MATRIX.md` 已补 Task050–052 的简明索引；早期叙事保留作历史证据，当前状态仍以本表及对应活文档为准。

当前执行与后续规划见 `docs/milestones/ROADMAP.md` 的“当前执行入口”；下方历史范围按对应证据理解。

## 状态表

状态含义：未核实 / 进行中 / 已提交待验证 / 已验证（有真机或构建证据）。状态来自提交主题与文档标题，**逐项核实前不要当作完成声明**。

| 里程碑 | 范围 | 验收（一个端到端用例） | 状态 |
|---|---|---|---|
| M-owner | `TileEntityMekanism`、`TileComponentConfig`、频率/`FrequencyManager`、`ContainerType`、`MekanismContainer` 同步、有效侧线程语义、bounding | 真机：Crusher 放置→通电→加工→存读档 | 已验证：P1 W-06–11与门禁通过；[证据](../archive/M-owner.md)。 |
| M-config | gear / machine+tier / common+startup / general 四类配置 owner 与同步 | 非默认配置真机加载并同步到客户端 | 已验证：P2 W-12–14与门禁通过；[证据](../archive/M-config.md)。 |
| M-platform | P3 核心平台入口、命令、成就、条件、生命周期与 payload 核对 | 原始行为由真实 Fabric owner 承接，命令真机可执行 | 已验证：P3 与回归通过，46 项边界已说明。[证据](../archive/M-platform.md)。 |
| M-transfer | 能量、流体、物品、化学、热量传输与网络生命周期 | 管线连接真实机器，传输／回滚与存读档后重建正确 | 已验证：P4 W-23–28与门禁通过；[证据](../archive/M-transfer.md)。 |
| M-render | transmitter/Robit/QIO 模型、粒子、GUI、按键与客户端注册 | mcdebug 实际外观／连接截图与 GUI 巡检 | 已验证：P5 W-29–33及578 tests/API/machine门禁通过；[证据](../archive/M-render.md)。 |
| M-multiblock | 五种核心多方块生命周期 | 破坏重建、区块重载、存读档、尺寸失败路径 | 已验证：P6 W-34–35与build/API/machine门禁通过；[证据](../archive/M-multiblock.md)。 |
| M-registry | 化学 ingredient 类型注册表、Robit 注册/数据包、官方 legacy alias、biome modifier | 注册冻结后 ID/alias 与官方一致 | 已验证：W-38–39注册／生成与P7门禁通过；[证据](../archive/M-registry.md)。 |
| M-gear | accessory、Curios 策略、ItemAbility、suit/tool 规则 | 穿戴/工具行为真机核对 | 已验证：W-36–37功能／factory与P7门禁通过；[证据](../archive/M-gear.md)。 |
| LICENSES | 许可证与归属（W-85） | 四个 JAR 均带 LICENSE/NOTICE；内嵌库许可齐全 | [LICENSES](../LICENSES.md) |
| M-deferred | W-72 核心延期文件复盘（35 个非集成类） | 每项有替代或不适用，无玩家可见缺口 | 已复盘：全部有替代，无需补实现。[M-deferred](M-deferred.md) |
| M-integration | W-68 第三方配方查看器与 looking-at 的 Fabric 边界（EMI/JEI/REI、Jade/WTHIT/TOP） | 边界页与建议顺序，实现须用户裁决（W-74） | 已批准 EMI→Jade：[M-integration](M-integration.md)。 |
| M-release | 核心全量测试、smoke、发布物与限制汇总 | 全部门禁／发布契约通过并完成协调者复核 | 授权收口验收通过：584项、11门禁组件分段通过；两次clean四JAR一致。范围与未跑项见[M-release](M-release.md)。 |
| M-parity | 官方 NeoForge 与 Fabric 移植的功能对账（存在、默认状态、容量、发电机／机器行为、存档加载、GUI） | 同探针表两端读数逐字段对比 | 已执行：覆盖结果与限制见 [M-parity](M-parity.md)；P7 已补测气体／复杂配方机器。 |
| M-tools | Tools材料／工具／护甲／配方／刷怪装备与客户端渲染 | 独立附加JAR与一个真实工具使用闭包 | 已验证：Tools 功能与回归通过。[证据](../archive/M-tools.md)。 |
| M-additions | 塑料方块／气球／TNT／baby生物／对讲机 | 独立附加JAR与实际实体及物品闭包 | 进行中：主体真机与回归通过；语音采集格式受环境限制。[证据](M-additions.md)。 |
| M-generators | 六种发电机／涡轮／裂变／聚变 | 原机器发电与多方块运行及生命周期 | 已验证：机器、多方块、GUI／生命周期与回归通过。[证据](../archive/M-generators.md)。 |

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

## 发布限制（W-42 准备）

服务端路径已验证（W-60／W-61，随后续 JAR 自动复验）；发布 JAR 客户端路径仅有 W-69 一次性证据、不自动复验，改版本号后须用户批准单独复验；完整发布声明待协调者复核。

- **第三方集成**：EMI／JEI／Jade／WTHIT 与 Mod Menu 已作为可选接入保留，发布物不内嵌第三方实现。REI／TOP、CraftTweaker、ProjectE、FramedBlocks、JsonThings、gender、OpenComputers2 不在当前范围。原 NeoForge 混合 `IMekanismAccess`／`MekanismAccess` 公共服务未提供，不能从原生 viewer 接入推断该第三方 API 已兼容。
- **历史 46 项**：P3 清单中已有 5 份原源码纳入、32 项由实际 Fabric owner 承接；另有 7 项第三方边界、1 项离线开发工具、1 项旧反射辅助。逐项现状见[对照](evidence/p8-release/w42-historical46-current.csv)，旧反射辅助已由原生透视模型分派取代。
- **平台交互**：不提供任意 modifier 重绑编辑器；Accessories 默认 H 与 Mekanism HUD 绑定冲突可在原版设置中重绑。命令使用原版权限等级／op 回退，未提供 NeoForge 权限插件的逐 node、动态 context 或离线覆盖。多个飞行提供者之间缺少原版权限归属 token；工具动作采用原版映射，不触发 NeoForge `BlockToolModificationEvent` 或其第三方 data-map 扩展。
- **本批渲染修复**：Meka-Tool 已按原生显示上下文选择独立左右手模型，QIO dashboard 已按官方 JSON 给 LED 层应用 full-bright；[直接实机结果](evidence/development-render/native-summary.json)。未穷举第三方渲染器与资源包组合。
- **验证边界**：现有证据覆盖代表装备／流体／网络路径，未穷举全部模块组合、流体／船／滑翔、第三方容器与能量提供者。worldgen 的保存 palette 与 retrogen 触发读数不证明全部矿脉分布；[范围与证据](evidence/p8-release/w42-preparation.json)。
- **证据目录体积上限（2026-11-01 复核，W-118）**：上限由 5 MB 调整为 **8 MB**。理由：PNG 截图已压到 **1.63 MB**（256 色量化、不下采样，文字仍可读，本轮 4.95 MB→1.63 MB），剩余约 4.3 MB 是 **676 份 JSON 证据**（每份对应一个工作项/场景的机器可读读数，平均 6 KB）——它们是本项目的验证记录，删减等于丢证据。新增证据时先用 `python3 -c` 量化压缩截图，超过 8 MB 再评估；引用文件名变化必须同步更新 JSON 内的链接。
- **后续模块**：Tools、Generators、Additions 已完成移植与模块回归；四 JAR 净构建与哈希见 W-61 证据。
- **NeoForge 存档语义**：机器／传输器／多方块 NBT 已在官方 NeoForge 样本世界上比对通过（W-65 第一轮）；频率与机器运行库存尚未覆盖。
- **发布 JAR 客户端路径**：已验证并成档（W-69）：生产客户端加载四个发布 JAR 并应用 458 值后加入游戏；该启动脚本已按“客户端只能用 mdb 启动”规则退役，结论不再重复执行。
- **尚未真机验证（P15 处理，`docs/milestones/ROADMAP.md` W-64）**：Generators 音效播放、裂变熔毁受控路径、Additions 原始16kHz/big-endian PCM与双向语音、Meka 套装 HUD 细节。
