# 最近三天提交审查（进行中）

## 范围与方法

用户要求按 AGENTS.md 的移植风格审查规则，从头审查最近三天的提交。本审查固定为 2026-10-24 13:56:16 至 2026-10-27 13:56:16（Asia/Shanghai）的滚动 72 小时，以 Git 提交者时间过滤、当前 `java-port` 的可达历史为范围；`git log --since='2026-10-24T13:56:16+08:00' --until='2026-10-27T13:56:16+08:00' --reverse` 给出 101 个提交，起于 `bbbcc2ef`，止于 `a02d7298`。包含 merge 和文档/测试提交；合并按第一父提交检查实际引入差异，同时避免把已检查的分支提交当作独立实现重复计数。审查提交本身不纳入固定范围。

审查逐提交查看真实 diff，并在需要时查当前调用链和历史；审查记录区分已确认缺陷、待核实疑点与未发现偏离。不能以编译或 smoke 通过代替语义验证，也不能把未审查的提交写成通过。遵守 `docs/PORT_COMMIT_REVIEW_PROMPT.md`：审查阶段只读，若确认生产缺陷，在对应行为边界内另行修复并复查。

## 已审查

| 提交 | 范围与结果 |
|---|---|
| `bbbcc2ef` | 文档裁定 EffectiveSide 线程组语义，明确服务端组身份、默认客户端及目标调用方；本文档本身不引入生产代码。语义成立性需继续结合实现及真实运行验证。 |
| `5a6ee373` | 第一父提交 diff 仅引入上述裁定文档；未发现额外 merge 修改。 |
| `e171c5c6` | 更新工作约束及裁定文档措辞，不涉及生产源码；按当前 AGENTS.md 而非历史策略执行审查。 |
| `27ddfddf` | 第一父提交 diff 对应上述约束和文档修改；未发现额外 merge 修改。 |
| `59e389a0` | 已读整个 diff：线程组查询、server spin mixin、四个调用方、清单和文档。调用点保留 server/client 判断形状；mixin 和切片同步登记。未在本次静态检查确认生产缺陷；服务端 IO 线程继承组这一环境假设尚未独立验证，不能等同于完整运行时审查。 |
| `5d2c9182` | 已读整个 diff，检查后续能源查询与事务适配：F3 已提交修复但仍需端到端验证；F1 前次判断已更正，剩空网络外观疑点。现有能源测试覆盖 simulate 回滚、嵌套事务回滚和执行提交，但不证明所有能力生命周期语义。 |
| `6b6bcb0f` | 已读整个 diff：节日初始化、key 注册与按键轮询、tooltip 流体类型、切片和记录；F2 为当前仍缺失的原快捷键回调及 modifier/context 语义。holiday 和 tooltip 尚未独立做实机语义验证。 |
| `dba8824e` | 已读整个 diff：能力 catalog、工具状态助手、三处调用迁移、drop-rule override、AW 与裁定文档。确认 F4（下述，修复 `8cfc8d79`）；自定义 TOOL 组件兜底问题在后续修复后需复审其测试与两个工具。NeoForge 工具修改事件和数据映射差异原提交已有明确记录，未证明等价。 |

## 审查发现与待复核

### F1 — 能源接收侧失效（`5d2c9182`，前次判断更正；空网络外观待验证）

**撤回前次关于有能量网络无法重新发现接收方的结论。** 前次误将基类 `NetworkAcceptorCache` 的保留对象行为当成当前能源网络行为：`EnergyNetwork.createAcceptorCache()` 已在后续提交 `5ee61549` 改为 `FreshNetworkAcceptorCache`，其 `getAcceptorValues()` 构造快照时遍历真实成员的六侧、调用 `refreshAcceptorConnections()` 并重新查询 provider。`EnergyNetwork.onUpdate()` 在缓冲区非空时调用 `tickEmit()`，因此无独立邻居更新时下次发射仍会重新发现侧配置变化，也不保留旧接收对象。上一版记录称其遍历已保留对象、不再查询，是错误结论，不能据此提交修复。

仍需核实的较窄问题：缓冲区为空时 `onUpdate()` 不调用 `tickEmit()`，而 `TileComponentConfig.sideChangedBasic()` 的 `invalidateCapability()` 只撤销本地缓存、不通知邻居；此时传输实际不受影响，但连接位和外观是否会一直保持旧状态，需进一步查其他生命周期/网络读取路径并做实机验证。历史提交自身的推送失效语义与现状态应分开评估。

### F2 — 客户端键绑定行为被移除后仍未接回（`6b6bcb0f`，静态代码确认；运行复现及修复待完成）

此提交将官方模式切换、模块调节器、HUD 开关和 boost 按下/释放回调从 `MekanismKeyHandler` 移除，仅声明并注册 vanilla KeyMapping，承诺留待 client input vertical。当前 `src/main/java/mekanism/client` 与 `fabric/src/main/java` 中对这些绑定的检索显示，手部绑定仅另由 `MekKeyHandler.isRadialPressed()` 查询，其他绑定仅声明和注册；当前 `FabricClientTickRegistration` 只注册 `ClientTickHandler.onTick()` 及实体载入声音事件，没有模式切换/HUD/模块按键消费。`MekanismClient.updateKey` 有定义但在上述客户端路径未发现调用。故不能继续声称该已完成客户端输入面等同官方；快捷键的上下文、modifier、重复按下及服务端同步须在原行为边界内复现并修复。尚未进行实机 GUI 验证。

### F3 — Robit 无法通过充电板的实体能量路径充电（`5d2c9182`，代码修复已提交；端到端验证待完成）

`TileEntityChargepad.onUpdateServer()` 对非玩家生物调用 `EnergyCompatUtils.getStrictEnergyHandler(entity)`，并明确注明 Robit 走此分支；`EntityRobit` 实现 `IMekanismStrictEnergyHandler` 并持有真实能量容器。修复前的 `EnergyCompatUtils` 对实体只遍历外部能量 compat，最终查询 `FabricEnergyLookup.ENTITY_ENERGY`。整个已纳源码中对该 lookup 只有定义和查询，没有注册任何实体 provider；因此 Robit 查到 null，`provideEnergy(null)` 返回 false。此缺口独立于是否启用外部能量转换：官方原先将自身 strict capability 列为第一优先，不能用 Team Reborn 是否启用来替代。修复提交 `6ebdda4f` 在实体查询处先返回可处理能量的真实 Robit handler，再保留其他实体的原 Fabric 外部查询。历史基线 `Capabilities.registerCapabilities` 对 ROBIT 注册的实体能源 provider 正是 `(robit, ctx) -> robit`，且官方 strict compat 在外部 compat 前查询。已重审该提交真实 diff：仅改这一入口，不改能量容器、转换或注册 ID；未发现该范围内新增偏离。`compileJava` 及 `FabricEnergyLookupTest`、`FabricStrictEnergyHandlerTest`、`FabricEnergyStorageTest` 通过，但这些现有测试不直接覆盖 Robit/充电板，尚未完成真实世界复现与修复后端到端验证。

F2 尚需按原有按键上下文、组合键、repeat 与 GUI/联机语义实现和验证，不能通过仅注册静态键绑定宣布完成。额外风险：`handModeSwitchKey` 和 `descriptionKey` 默认都绑定 N，而 1.21.1 vanilla 的 `KeyMapping.click` 通过单值 MAP 分派；必须结合 Fabric 的实际处理和按键重绑定验证，不能假定普通 `consumeClick` 自然保留两个上下文。

### F4 — 火焰弹点燃目标能力被误拒绝（`dba8824e`，已修复，端到端待验）

`EntityFlame` 对命中方块以 `Items.FIRE_CHARGE` 的真实上下文调用 `ToolAbilityUtils.getToolModifiedState(..., FIRESTARTER_LIGHT, false)`；移植助手只接受 `IItemAbilityProvider`，原版火焰弹不实现该 Mekanism 专用接口，导致该分支一律返回 null，不能按官方路径点燃未点燃的营火或蜡烛。对照本地 `build/moddev/artifacts/neoforge-21.1.200-sources.jar` 中 `FireChargeItem.canPerformAction()` 和 `ItemAbilities.DEFAULT_FIRECHARGE_ACTIONS`，原版火焰弹确实具有此能力；打火石也同理。

修复 `8cfc8d79` 仅对非空原版火焰弹/打火石和 `FIRESTARTER_LIGHT` 恢复此入口，其余动作依旧走原有能力提供方；已按审查 prompt 阅读该提交完整 diff，未发现该限定行为边界内新增偏离。`ToolAbilityUtilsFirestarterTest` 三项通过：火焰弹/未点燃营火，打火石/蜡烛及已点燃情况，空物品/木棍/错误动作拒绝；单元测试显式绑定并恢复 vanilla 标签，不是运行中世界或投射物端到端验证。首次测试失败原因是普通 JVM bootstrap 未加载数据包标签，补充标签后重跑通过。

## 恢复位置

依次审查 `073e10ff` 等后续提交。`dba8824e` 初版 `isCorrectToolForDrops` 对移除/自定义工具组件的兜底曾过宽，当前 `ItemAtomicDisassembler` 已区分组件缺失与替换组件，需追查后续修正提交并核对 `ItemMekaTool`，不把历史问题直接认定为当前缺陷。同时跟进 F1 空网络外观、F2 快捷键恢复和 F3/F4 的端到端验证。固定范围的后续提交尚未逐个审查，不得引用此前一次性切片扫描或旧的自评代替本轮逐提交审查。
