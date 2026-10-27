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
| `073e10ff` | 已读完整 diff，仅记录已裁定事项和目录接入顺序，不直接改变运行逻辑；相关语义仍需以后续生产提交及实际 owner 为准。 |
| `22e74220` | 已读完整 diff：156 个目录/工厂源文件被纳入，明确记录当时 gate 未通过，不应将该中间提交视作可运行闭包。CreativeTab 注册 seam 的行为及完整目录注册继续在后续提交审查。 |
| `fd92051e` | 已分段读完整 diff：逐项替换桶常量、FluidStack 类型、工厂物品映射、Lazy 和技术伤害标签。固定 NeoForge 参考确认桶值为 1000、物品映射使用相同 fastutil 策略；发现 F5/F6 并分别修复。FluidStack 跨平台存储/转换语义尚需在相关后续 owner 提交中继续核对，不能仅凭 import 替换断言完整等价。 |
| `5138fd15` | 已分段读完整 diff：事件基类和派发、fake player、纸箱/矿机破坏保护、Robit 伤害、传送与渲染数据。F7 已修复；该提交的无条件破坏许可及 Robit 先死亡后恢复在后续 `5ee61549` 被替换，当前看到的调用链需在审查该提交时进一步验证，不能把历史实现视为当前行为。fake player 与第三方保护事件互操作尚未实机验证。 |
| `a03c1888` | 已分段读完整 diff，检查 GaugeDropper、容器流体、充电板、传送器、量子纠缠和 QIO 导入器。确认 QIO 写入与提取未组成原子事务的风险 F8，以及槽位遍历顺序变化 F9；完整修复和真实 owner 回归验证尚未完成，不标记通过。 |
| `d6af1549` | 已读完整 diff：第二环纳片、罐规格谓词、蒸发太阳能查询、SPS lightning、个人存储侧查询等。该时点仍有大量编译错误，不作为运行闭包；`EVAPORATION_SOLAR` lookup 只定义和查询，未发现 Fabric provider，官方 provider 在尚未移植的 Generators advanced solar 源树，跨模组接入未验证且不能声称有效。SPS 原事件 veto 差异有原提交记录，交互兼容性尚未实测。 |
| `e39d256f` | 已读完整 diff：机器基类/upgrade 纳片、GUI packet 由 context 取 player 改为直接传入、FluidStack 类型迁移。当前 Fabric 服务端注册将 `context.player()` 传给同一 handler；单靠签名替换不能证明所有 packet 的权限和时序安全，需在网络注册提交处继续审查。当时仍未整体编译成功。 |

## 审查发现与待复核

### F1 — 能源接收侧失效（`5d2c9182`，前次判断更正；空网络外观待验证）

**撤回前次关于有能量网络无法重新发现接收方的结论。** 前次误将基类 `NetworkAcceptorCache` 的保留对象行为当成当前能源网络行为：`EnergyNetwork.createAcceptorCache()` 已在后续提交 `5ee61549` 改为 `FreshNetworkAcceptorCache`，其 `getAcceptorValues()` 构造快照时遍历真实成员的六侧、调用 `refreshAcceptorConnections()` 并重新查询 provider。`EnergyNetwork.onUpdate()` 在缓冲区非空时调用 `tickEmit()`，因此无独立邻居更新时下次发射仍会重新发现侧配置变化，也不保留旧接收对象。上一版记录称其遍历已保留对象、不再查询，是错误结论，不能据此提交修复。

仍需核实的较窄问题：缓冲区为空时 `onUpdate()` 不调用 `tickEmit()`，而 `TileComponentConfig.sideChangedBasic()` 的 `invalidateCapability()` 只撤销本地缓存、不通知邻居；此时传输实际不受影响，但连接位和外观是否会一直保持旧状态，需进一步查其他生命周期/网络读取路径并做实机验证。历史提交自身的推送失效语义与现状态应分开评估。

### F2 — 客户端键绑定回调缺失（`6b6bcb0f`，核心接线已修复；兼容与实机验证待完成）

原提交将官方模式切换、模块调节器、HUD 开关和 boost 按下/释放回调从 `MekanismKeyHandler` 移除，仅声明并注册 vanilla KeyMapping；审查时确认这些回调并未重新接入。用户后续明确要求补修此前发现的问题。

修复 `b876af80` 按历史 `MekanismKeyHandler` 恢复装备/手持模式包、模块调节器包、HUD 保存和 boost 同步；增加键盘/鼠标输入 mixin，同一 N 键在 GUI 与游戏上下文分别处理，并在 tick 中处理打开 GUI 后的 boost 释放。description 在 GUI 中恢复默认 SHIFT 条件。已逐段复审完整 diff，三个切片 gate 与两个键盘/扫描码/鼠标绑定匹配测试通过；客户端真实启动 smoke 15/15，但 smoke 未模拟按键，且运行后额外补了 overlay 一行保护并重新通过编译和测试。因此不能称为真实按键端到端验收。

剩余偏离：Curios 位置的模式切换仍未接入；vanilla 设置界面无法表示/自定义组合键 modifier，描述键的显示不一定体现 SHIFT；`MekKeyHandler` 在 GUI 原始轮询不支持扫描码；鼠标 mixin 在 vanilla macOS control-click 归一化之前触发，可能与官方不同。还需在 mcdebug/真实客户端验证 GUI、重绑、重复、按下释放、远程同步与上述边界，不得将 F2 标成完全闭环。

### F3 — Robit 无法通过充电板的实体能量路径充电（`5d2c9182`，代码修复已提交；端到端验证待完成）

`TileEntityChargepad.onUpdateServer()` 对非玩家生物调用 `EnergyCompatUtils.getStrictEnergyHandler(entity)`，并明确注明 Robit 走此分支；`EntityRobit` 实现 `IMekanismStrictEnergyHandler` 并持有真实能量容器。修复前的 `EnergyCompatUtils` 对实体只遍历外部能量 compat，最终查询 `FabricEnergyLookup.ENTITY_ENERGY`。整个已纳源码中对该 lookup 只有定义和查询，没有注册任何实体 provider；因此 Robit 查到 null，`provideEnergy(null)` 返回 false。此缺口独立于是否启用外部能量转换：官方原先将自身 strict capability 列为第一优先，不能用 Team Reborn 是否启用来替代。修复提交 `6ebdda4f` 在实体查询处先返回可处理能量的真实 Robit handler，再保留其他实体的原 Fabric 外部查询。历史基线 `Capabilities.registerCapabilities` 对 ROBIT 注册的实体能源 provider 正是 `(robit, ctx) -> robit`，且官方 strict compat 在外部 compat 前查询。已重审该提交真实 diff：仅改这一入口，不改能量容器、转换或注册 ID；未发现该范围内新增偏离。`compileJava` 及 `FabricEnergyLookupTest`、`FabricStrictEnergyHandlerTest`、`FabricEnergyStorageTest` 通过，但这些现有测试不直接覆盖 Robit/充电板，尚未完成真实世界复现与修复后端到端验证。

F2 的剩余 GUI/联机、组合键重绑和输入平台边界仍需验证。`handModeSwitchKey` 和 `descriptionKey` 默认都绑定 N，而 1.21.1 vanilla 的 `KeyMapping.click` 使用单值 MAP；修复绕过该单值分派独立检查 in-game 绑定，仍须实测两个上下文。

### F4 — 火焰弹点燃目标能力被误拒绝（`dba8824e`，已修复，端到端待验）

`EntityFlame` 对命中方块以 `Items.FIRE_CHARGE` 的真实上下文调用 `ToolAbilityUtils.getToolModifiedState(..., FIRESTARTER_LIGHT, false)`；移植助手只接受 `IItemAbilityProvider`，原版火焰弹不实现该 Mekanism 专用接口，导致该分支一律返回 null，不能按官方路径点燃未点燃的营火或蜡烛。对照本地 `build/moddev/artifacts/neoforge-21.1.200-sources.jar` 中 `FireChargeItem.canPerformAction()` 和 `ItemAbilities.DEFAULT_FIRECHARGE_ACTIONS`，原版火焰弹确实具有此能力；打火石也同理。

修复 `8cfc8d79` 仅对非空原版火焰弹/打火石和 `FIRESTARTER_LIGHT` 恢复此入口，其余动作依旧走原有能力提供方；已按审查 prompt 阅读该提交完整 diff，未发现该限定行为边界内新增偏离。`ToolAbilityUtilsFirestarterTest` 三项通过：火焰弹/未点燃营火，打火石/蜡烛及已点燃情况，空物品/木棍/错误动作拒绝；单元测试显式绑定并恢复 vanilla 标签，不是运行中世界或投射物端到端验证。首次测试失败原因是普通 JVM bootstrap 未加载数据包标签，补充标签后重跑通过。

### F5 — 技术伤害标签未按承诺随数据包提供（`fd92051e`，资源已修复，实际世界载入待验）

该提交让 MekaSuit 通过 `neoforge:is_technical` 过滤技术伤害，并声明标签内容会随数据包提供；但此前源资源及生成资源中均不存在该标签。固定参考 `build/moddev/artifacts/neoforge-21.1.200-sources.jar` 的 `data/neoforge/tags/damage_type/is_technical.json` 包含 generic_kill、outside_border、out_of_world 和可选 `#forge:is_technical`。缺少文件会使该标签的官方默认成员及 forge 扩展成员在本模组数据包中不可用；其他 datapack 可能另行补充，且某些默认伤害也可能被已有绕过盔甲规则挡住，因此不能仅由静态检查推断每种伤害的最终实际效果。

修复 `521a373b` 在 Fabric 自有资源路径补上与固定参考 JSON 语义完全相同的合并标签，保留可选扩展，不改原伤害判断。已阅读提交完整 diff 并复查：资源与固定参考经 JSON 解析比较一致；`TechnicalDamageTagResourceTest` 验证处理后测试运行时资源路径和所有条目，通过。尚未以真实服务器 datapack reload 或发布 jar 单独验证。

### F6 — Lazy 跨线程发布和 null 语义偏离（`fd92051e`，已修复）

原提交称 NeoForge `Lazy` 非线程安全并缓存 null；固定 `neoforge-21.1.200-sources.jar` 的 `Lazy` 实际使用 volatile 与同步保护、拒绝 null、支持失效。端口版原先无同步并缓存 null。实际 `ItemAtomicDisassembler.LAZY_RADIAL_DATA` 由客户端模式 UI 和服务端 `PacketRadialModeChange` 路径共用，而 `IRadialModeItem` 比较对象引用；集成服并发初始化可能得到不同引用，属于有真实调用链的竞态风险，但尚未实际复现游戏中故障。

修复 `932a7f6e` 保留已有 owner，使用同步读取/失效、拒绝 null 并允许失败后重试；无新 registry 或 adapter。按审查 prompt 已读该提交完整 diff，与固定 NeoForge 参考的行为契约核对；三项 `LazyTest` 通过，覆盖并发调用共享实例、null 后重试、失效后重新计算。实现使用更简单的同步方式，不声称性能与上游的 volatile 快路径相同。

### F7 — 传送事件公共访问器及基类订阅缺失（`5138fd15`，已修复局部行为）

固定 NeoForge 21.1.200 的 `EntityTeleportEvent` 公开 `getPrev()`（返回 `getEntity().position()`），且事件基类的订阅者接收其子类。移植版 `MekanismTeleportEvent` 未保留继承的 `getPrev()`；移植事件总线仅按事件精确类派发，监听 `MekanismTeleportEvent` 的插件无法收到 `Robit` 等子事件。此问题针对 Fabric 端口暴露的 Mekanism 事件 API，不声称与 NeoForge 二进制兼容。

修复 `a4cfb58b` 添加同语义访问器，并将现有单一项目事件总线改为按注册类型与子类派发，保持同步调用和注册顺序；已审阅该提交完整 diff。`MekanismEventBusTest` 通过，覆盖父类/子类接收、兄弟类不接收、取消状态和返回同一事件。测试未覆盖实际传送或 `getPrev()` 实体坐标。事件优先级、取消后监听者过滤、线程与第三方互操作未证明等价，该修复不宣称完整复刻 NeoForge 总线。

### F8 — QIO 导入的频率写入和存储提取不是原子操作（`a03c1888`，候选修复待验证）

原版移植的 `TileEntityQIOImporter.tryImport()` 先对外部 `StorageView` 在独立事务中模拟提取，随后 `freq.addItem(stack)` 立即更改 QIO 频率/驱动，再开启另一个事务实际提取。若实际提取数量少于模拟值、提取或开事务抛错，或者提交失败，频率的先前写入没有加入提取事务；短提取只记录错误并提交提取，已写入 QIO 的数量不撤销，存在物品复制/状态不一致。正常稳定 provider 运行时不必然出现差异，此处是静态确认的异常及状态变化路径，不声称已在实机复现。上游也是先写入后执行抽取并依赖处理器的一致性；Fabric 迁移后的事务边界仍必须真实处理失败，不能把“采用了 Transaction”当作原子性证明。

最小真实修复边界不是简单倒换调用或 remove-then-add 补偿：原 `QIOFrequency.addItem`/`massInsert` 和 `QIOItemTypeData.add` 未接受 `TransactionContext`，原 `QIODriveData.add` 会立即写驱动 metadata 并通知 holder；原有事务日志只覆盖 removal。进一步核对：已有 `FrequencyRemoveParticipant` 快照频率索引与计数，但不覆盖插入清理的 failed-wildcard 集合；`TypeRemoveParticipant` 快照类型计数与驱动成员但不含 `itemUUID`；现有 `DriveStateParticipant` 快照实际 stack 身份、metadata、itemMap 并在最终提交通知 holder，可以作为插入侧真实 owner 的基础。新类型 `createTypeDataForAbsent` 会调用全局 `QIOGlobalItemLookup.getOrTrackUUID`，它立即修改全局双向映射及 saved-data dirty 标志；仅复用 removal 快照会留下全局副作用。应先设计频率共享索引、类型状态、各驱动实际 stack/metadata、全局 UUID 分配及提交通知的插入侧事务参与，再让外部提取和频率插入参与同一事务；涵盖嵌套 abort、短提取、异常、已有/新类型、多个驱动和 drive 替换身份。下述候选已开始实现同一事务，但尚未经过真实 owner 的故障路径验证；不要仅根据编译或日志将数据风险宣称闭环。

**阶段性 WIP `b8fe2c31` 的提交复审**：已阅读完整 diff。驱动 owner 新增带事务的 `add` 重载，复用原 `DriveStateParticipant` 在修改前核对真实 stack 身份并采集快照，holder 通知推迟至最终提交；现有三参 `add` 继续原先非事务行为。该阶段的重载当时尚无生产调用，只有 `compileJava` 证据；后续 `f8df4ccc` 已接到导入链，现有测试见下。接续阶段性 WIP `bc9f5aeb` 已扩展同一频率快照以包含 failed-wildcard 集合、类型快照以包含 `itemUUID`；其完整 diff 已复审，`compileJava` 成功。它也改变原有移除事务回滚覆盖面，尚无真实 owner 的嵌套 abort/交错写入测试，不能据此断言无回归。全局 UUID、插入时注册快照、旧状态 dirty、提交发布以及 importer 的同一事务连接仍未完成；两阶段均不能单独交付当作修复。

**候选 WIP `f8df4ccc` 的完整 diff 复审**：导入先模拟 QIO 容量，实际提取与频率插入使用同一外层事务；短提取以实际数插入，超出请求和频率无法容纳全部实际提取时 abort。频率和真实驱动都登记快照；新类型的全局 UUID 推迟至外层最终提交，提交前读取该暂存 UUID 明确抛错而不是提前污染全局索引。`compileJava`、槽顺序测试与三个切片 gate 通过，但槽顺序测试未覆盖事务。试图在普通 JVM 创建真实频率/驱动 fixture 时，`MekanismDataComponents` 初始化触发自定义 chemical 根注册，因 vanilla registry 尚未 bootstrap 抛出 `IllegalArgumentException: Not bootstrapped (called from registry ResourceKey[minecraft:root / minecraft:game_event])`；未通过设置假 holder/跳过注册来掩盖，实验测试已移除。后续测试提交 `eb6109b5` 已完整复审：独立 Knot worker 执行真实 Mekanism 数据组件注册，以真实 `QIOFrequency`、`QIODriveSlot`、`ItemQIODrive` 与 vanilla `InventoryStorage`，配一个只提供驱动槽和通知计数的测试 holder。`qioTransactionTest` 七项通过，覆盖新/已有类型、嵌套 abort、提交分配 UUID 与通知、短提取、异常回滚源库存与 QIO、跨两个驱动回滚、替换盘不被覆盖。短提取和异常测试直接按 importer 同一事务模式调用真实 storage/frequency，未调用实际 tile 的 `tryImport`；驱动替换只证明抛错且替代盘不被写，并不证明频率、原盘和物品都一致。先前替换盘断言按传入 `ItemStack` 身份检查而失败，核对 slot 会复制 stack 后改为捕获 slot 实际存储的对象，重跑通过。Fabric `TransactionManagerImpl` 的关闭逻辑捕获 close callback 异常后继续调用其余参与者，但当前驱动身份失配时仍会留下原驱动缓存/物理盘状态不一致的日志风险。仍须核查真实服务端 holder 替换、开放事务中的非事务性重入以及 `onFinalCommit` 异常；候选不可视为完整验收。

### F9 — QIO 导入槽顺序变化（`a03c1888`，有槽存储已修复）

上游 `IItemHandler` 自最后一个槽递减到第一个；移植版对 `Storage.iterator()` 正向遍历。不同类型超过单次类型数或数量上限时，可能优先导入不同物品。Fabric 的 `SlottedStorage` 提供 `getSlots()`；修复 `61621cd9` 对其倒序遍历，无槽存储保持 provider 自身顺序。已审阅修复提交的完整 diff：仅调整取视图的顺序，不声称改变事务语义。两项 `QIOImporterSlotOrderTest` 用真实 vanilla `SimpleContainer` 的 Fabric `InventoryStorage` 验证索引和空槽的逆序；首次按 item variant 断言时普通 JVM 缺 Fabric mixin 导致 `ClassCastException`，改用真实槽 view 对象身份断言后通过。尚未在运行中游戏验证 sided inventory 的槽映射和导入上限。该修复不解决 F8 事务风险。

## 恢复位置

优先研究 F8 的 QIO 真实 owner 插入事务闭包，并补验 F9 的 sided inventory 场景；`a147116b` 的完整 diff 共 2180 行、52 文件，已阅读前 919 行（切片、fluid tint、BlockData、Capabilities 组合、外部 storage 常量 context 查询、Curios 边界及部分物品调用），余下未审查，继续时从 `/tmp/mek-a147-review.diff` 第 920 行或重新生成相同 git diff 开始，不得将其标记为已完整审查。`dba8824e` 初版 `isCorrectToolForDrops` 对移除/自定义工具组件的兜底曾过宽，当前 `ItemAtomicDisassembler` 已区分组件缺失与替换组件，需追查后续修正提交并核对 `ItemMekaTool`，不把历史问题直接认定为当前缺陷。同时跟进 F1 空网络外观、F2 快捷键恢复和 F3/F4 的端到端验证。固定范围的后续提交尚未逐个审查，不得引用此前一次性切片扫描或旧的自评代替本轮逐提交审查。
