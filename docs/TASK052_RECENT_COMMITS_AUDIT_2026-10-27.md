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

## 审查发现与待复核

### F1 — 能源接收侧失效后的自发现缺口（`5d2c9182`，静态调用链已核实；运行复现及修复待完成）

`BlockEnergyCapabilityCache.getCapability()` 只有在被再次调用时才观察接收能力是否变化；不存在主动推送监听。`AbstractAcceptorCache.getCachedAcceptor()` 对未连接侧直接返回 null；`NetworkAcceptorCache` 仅在 `changedAcceptors` 被标记时刷新，`EnergyNetwork.tickEmit()` 则遍历网络里已保留的接收对象，不对断开侧重新查询。实际 Mekanism 机器配置入口 `TileComponentConfig.sideChangedBasic()` 调用 `tile.invalidateCapability(...)`，而 `CapabilityTileEntity.invalidateCapability()` 只撤销本地缓存，不通知世界/邻居。因而当已加载相邻机器的能量侧配置从不可用改为可用，且没有独立方块邻居更新或其他主动刷新时，现有调用链没有触发重新发现的路径；反向变化还可能保留过期对象。此结论是有条件的静态分析，尚需真实世界回归复现并确认最小跨 mod 语义修复；不能把现有能量烟测当作该情景证据。

### F2 — 客户端键绑定行为被移除后仍未接回（`6b6bcb0f`，静态代码确认；运行复现及修复待完成）

此提交将官方模式切换、模块调节器、HUD 开关和 boost 按下/释放回调从 `MekanismKeyHandler` 移除，仅声明并注册 vanilla KeyMapping，承诺留待 client input vertical。当前 `src/main/java/mekanism/client` 与 `fabric/src/main/java` 中对这些绑定的检索显示，手部绑定仅另由 `MekKeyHandler.isRadialPressed()` 查询，其他绑定仅声明和注册；当前 `FabricClientTickRegistration` 只注册 `ClientTickHandler.onTick()` 及实体载入声音事件，没有模式切换/HUD/模块按键消费。`MekanismClient.updateKey` 有定义但在上述客户端路径未发现调用。故不能继续声称该已完成客户端输入面等同官方；快捷键的上下文、modifier、重复按下及服务端同步须在原行为边界内复现并修复。尚未进行实机 GUI 验证。

### F3 — Robit 无法通过充电板的实体能量路径充电（`5d2c9182`，静态代码确认；运行复现及修复待完成）

`TileEntityChargepad.onUpdateServer()` 对非玩家生物调用 `EnergyCompatUtils.getStrictEnergyHandler(entity)`，并明确注明 Robit 走此分支；`EntityRobit` 实现 `IMekanismStrictEnergyHandler` 并持有真实能量容器。当前 `EnergyCompatUtils` 对实体只遍历外部能量 compat，最终查询 `FabricEnergyLookup.ENTITY_ENERGY`。整个已纳源码中对该 lookup 只有定义和查询，没有注册任何实体 provider；因此 Robit 查到 null，`provideEnergy(null)` 返回 false。此缺口独立于是否启用外部能量转换：官方原先将自身 strict capability 列为第一优先，不能用 Team Reborn 是否启用来替代。修复应保留真实 Robit 能量 owner、外部查询以及官方优先级和自动化语义；尚未完成运行复现。

`5d2c9182` 的 Team Reborn 单位转换、事务和实体注册仍需逐项审查；`6b6bcb0f` 的 holiday 和 tooltip 差异已读取，尚需核对后续实现和调用顺序。两提交均不标记为完整通过。

## 恢复位置

依时间顺序继续：完成 `5d2c9182`，随后从 `6b6bcb0f` 开始。固定范围的其余提交均未审查，不得引用此前一次性切片扫描或旧的自评代替本轮逐提交审查。
