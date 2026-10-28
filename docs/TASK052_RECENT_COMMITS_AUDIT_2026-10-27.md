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
| `6b6bcb0f` | 已读整个 diff：节日初始化、key 注册与按键轮询、tooltip 流体类型、切片和记录；F2 核心回调已接回，modifier/Curios 和真实按键语义仍未闭环。holiday 和 tooltip 尚未独立做实机语义验证。 |
| `dba8824e` | 已读整个 diff：能力 catalog、工具状态助手、三处调用迁移、drop-rule override、AW 与裁定文档。确认 F4（下述，修复 `8cfc8d79`）；自定义 TOOL 组件兜底问题在后续修复后需复审其测试与两个工具。NeoForge 工具修改事件和数据映射差异原提交已有明确记录，未证明等价。 |
| `073e10ff` | 已读完整 diff，仅记录已裁定事项和目录接入顺序，不直接改变运行逻辑；相关语义仍需以后续生产提交及实际 owner 为准。 |
| `22e74220` | 已读完整 diff：156 个目录/工厂源文件被纳入，明确记录当时 gate 未通过，不应将该中间提交视作可运行闭包。CreativeTab 注册 seam 的行为及完整目录注册继续在后续提交审查。 |
| `fd92051e` | 已分段读完整 diff：逐项替换桶常量、FluidStack 类型、工厂物品映射、Lazy 和技术伤害标签。固定 NeoForge 参考确认桶值为 1000、物品映射使用相同 fastutil 策略；发现 F5/F6 并分别修复。FluidStack 跨平台存储/转换语义尚需在相关后续 owner 提交中继续核对，不能仅凭 import 替换断言完整等价。 |
| `5138fd15` | 已分段读完整 diff：事件基类和派发、fake player、纸箱/矿机破坏保护、Robit 伤害、传送与渲染数据。F7 已修复；该提交的无条件破坏许可及 Robit 先死亡后恢复在后续 `5ee61549` 被替换，当前看到的调用链需在审查该提交时进一步验证，不能把历史实现视为当前行为。fake player 与第三方保护事件互操作尚未实机验证。 |
| `a03c1888` | 已分段读完整 diff，检查 GaugeDropper、容器流体、充电板、传送器、量子纠缠和 QIO 导入器。确认 QIO 写入与提取未组成原子事务的风险 F8，以及槽位遍历顺序变化 F9；完整修复和真实 owner 回归验证尚未完成，不标记通过。 |
| `d6af1549` | 已读完整 diff：第二环纳片、罐规格谓词、蒸发太阳能查询、SPS lightning、个人存储侧查询等。该时点仍有大量编译错误，不作为运行闭包；`EVAPORATION_SOLAR` lookup 只定义和查询，未发现 Fabric provider，官方 provider 在尚未移植的 Generators advanced solar 源树，跨模组接入未验证且不能声称有效。SPS 原事件 veto 差异有原提交记录，交互兼容性尚未实测。 |
| `e39d256f` | 已读完整 diff：机器基类/upgrade 纳片、GUI packet 由 context 取 player 改为直接传入、FluidStack 类型迁移。当前 Fabric 服务端注册将 `context.player()` 传给同一 handler；单靠签名替换不能证明所有 packet 的权限和时序安全，需在网络注册提交处继续审查。当时仍未整体编译成功。 |
| `a147116b` | 已分段读完整 2180 行 diff：能力 catalog、物品流体、armor/BE 注册、multiblock manager 和个人存储，检查真实上下文及后续状态。确认整叠流体能力检查遗漏 F10 并修复；Curios 一直返回 false/empty 的假兼容边界为 F11。BlockData 的非原版流体蒸发 hook、客户端 tint 和具体 registry 生命周期仍未证明与官方等价；当时记录仍有编译错误，不作为可运行闭包。 |
| `12ebbcf6` | 已分段读完整 1478 行 diff：component holder 迁移、喷火实体同步、激光破坏/盾牌处理和 10 项纳片。确认并修复喷火存档 F12、激光与喷火熔炼破坏保护 F13；同步 spawn、盾牌事件扩展和 INFERNO 放置事件等价尚未实机证明。当时仍是编译未闭合的中间状态。 |
| `76f790ce` | 已分段读完整 594 行 diff：物品扩展删除、附魔、重装备/挖掘、pick-block 和 TNT。当前实际缺失模块附魔在游戏查询的合并，已按 F14 修复；附魔适用/书、装备动画和 pick-block 等剩余官方 hook 单独列 F15，不把此前注明 deferred 当已实现。当前电弓射弹模式已有后续实现在 `createProjectile`，但未证明官方全部虚拟附魔查询一致。 |
| `1d4c4027` | 已分段读完整 316 行 diff：blockview v2、工厂纳片、充电与水下模块、三元谓词及重复注解。Curios 充电属于已记录的 F11；水下加速将两种不同属性错误合一、流体能耗只检查脚部位置，且追查发现真实装备属性桥未注册，见 F17；已修普通水中推进和桥接，实机及非原版流体仍待验证。render-data v2 及 factory 行为未凭导入改名判为全面等价。 |
| `7785a9c1` | 已分段读完整 454 行 diff：实体注册、数据序列化、网络形参、工厂/容器纳片与日志。当前传送粒子包缺失 S2C codec/receiver，已按 F18 接回；追查实体数据 wire id 发现本地递增注册在客户端/服务端额外 serializer 的顺序不同会错解，F19 已改为稳定原版类型，用户后续明确无需旧开发版兼容，握手恢复官方版本；异模组双端仍待验。 |
| `4e5a4232` | 已分段读完整 625 行 diff：配方查看器、模拟玩家与量子纠缠的导入调整、chunk loader 持久票据替代。后续 `5ee61549` 已修共享所有权及空数据持久化；旧 Fabric 开发版存档迁移不在用户要求范围，见 F21。 |
| `b6e5c54d` | 子代理固定 `b6acf106` 已读完整 diff，主代理已复核并修复未注册的传送速度/激光命中特效包，见 F22；其余链路仍按报告范围记录。 |
| `509afc1b` | 子代理固定 `b6acf106` 已读完整 diff，主代理已复核并修复服务器关闭时 retrogen 特征缓存未清理，见 F23；历史 chunk NBT 问题已在后续提交修正。 |
| `1af82201` | 子代理固定 `b6acf106` 已读完整 diff；主代理已修复 SPS 闪电、精炼黑曜石框、流体渲染/声音及液体罐动态光照，见 F24–F26、F28。官方历史注册别名等尚待核验。 |
| `2529dca8`、`38b8b1af` | 子代理固定 `3b5301c3` 已读完整 diff；旧伤害路径问题有后续修复，创造标签局部搜索缺失待主代理复核；前者纳入的 11 个既有 mixin 未逐个完成调用链核验。 |
| `a1f1c46f` | 子代理固定 `3b5301c3` 已读完整 diff，主代理复核并修复后续提交引入的差集/交集输入缓存错误，见 F27；第三方兼容配方及升级物品端到端未验。 |
| `53fda9e5` | 子代理固定 `ddbbdb99` 完整审查注册与 26 包调用链；原官方 handler 的 GUI 操作存在未鉴权风险，主代理复核并分组修复，见 F29；未声明所有网络授权问题均已关闭。 |
| `55c59a0c` | 子代理固定 `ddbbdb99` 完整读取 diff 并追踪当前机器 tick、能力与能量入口，未发现已证实当前缺陷；历史 smoke 仅匹配产物 ID，不能单独证明文档记载的产量或能耗。 |
| `b0f7e13d` | 子代理固定 `07b7e6ba` 完整读取父提交真实 diff；主代理复核并修复内部能源错误依赖外部 FE 开关和转换精度，见 F30。历史 smoke 不能证明精确能量数值。 |
| `9cabfea9` | 子代理固定 `07b7e6ba` 完整读取真实 diff：只改运输 smoke/文档，不改生产源码；后续物品、流体、化学调用链已追踪，未证实该提交引入的当前生产缺陷，历史 smoke 数量断言不足。 |
| `985137ff`、`9ed83dea`、`b626904b`、`d9c291e9` | 子代理固定 `8e8b0a9b` 完整读取 diff，四者均是文档，没有生产代码变更；历史文档提及的已修问题不等于当前仍有同样缺陷。 |
| `f787cad7` | 子代理固定 `8e8b0a9b` 完整读取 diff、复核分离机与 ejector 调用链：只改文档/机器 smoke；原失败探针用错输出槽和自动输出开关。目标氧标签断言不证明精确数量、源扣量、GUI 或全部方向。 |
| `ffbcb655` | 子代理固定 `8e8b0a9b` 完整读取 diff 并追踪后续注册和渲染，主代理复核修复字典流体、非 Mekanism 发光及喷气背包上升同步，见 F31–F33；真实 GUI/亮度及按键尚待实测。 |
| `5ee61549` | 子代理固定 `0c15b485` 完整读取该提交相对父提交的 diff，重点追踪区块票据、版本附件及假玩家保护，未证实新的具体缺陷；Fabric attachment 的 ProtoChunk 转移和脏标记已核查，不能误判丢失。 |
| `5c0fcb1f` | 子代理固定 `0c15b485` 完整读取 diff 和当前 worldgen 路径，主代理初核固定 jar 路径问题，见待修 F34；旧 smoke 不能证明 datapack 覆盖或配置开关。 |
| `519e1132`、`257b9d05` | 子代理固定 `b452ba25` 完整读取真实 diff：均为审查规范/记录文档，无生产行为变更。 |
| `b3b87ec3`、`7da81542` | 子代理固定 `b452ba25` 完整读取各自 diff；主代理复核并修复多输出机器漏斗抽取饥饿，见 F35。后者补矿车入口但原 smoke 未实测矿车。 |
| `9bb884a3`、`1e08c1e5` | 子代理固定 `b452ba25` 完整读取 diff，主代理复核并修复方向面分桶及普通模型继承 composite 几何，见 F37；启动日志不能证明画面。 |
| `d77c3252` | 子代理固定 `b452ba25` 已读行为 diff；主代理补读完整 common-slice diff 及文档，按前后集合核对新增 30 个真实渲染/直接前置文件、无移除，余下为重排。该提交现已读完整 diff。已修流体缓存 F36 并恢复高亮 F38（真实画面仍待验）；大型 renderer 因 `shouldRenderOffScreen` 全局注册而必然被 section 剔除的初判已撤回。 |
| `ae208105`、`4bdbcdac`、`6928d67c` | 子代理完整读取各自 diff，均为审查记录文档，不改生产行为。 |
| `afd1e77b`、`46f62053` | 子代理完整读取各自 diff 并追踪当前资源重载链：历史模型自引用问题已被 `e0dee5bd` 修复；实际物品及装备 renderer 缺重载回调，见 F40。关于 transporter 层提前缺失的初判已撤回，该时点尚未注册 transporter renderer。 |
| `347e5803` | 主代理完整读 873 行真实 diff（含删除的 320 条排除清单）、核对当前类型化配料与缓存：F27 已修；新确认相等性缺少差集/交集字段并按 F41 修复。嵌套类型化配料及第三方数据包语义未全面验证。 |
| `fb65b9b7`、`7b70f56d` | 主代理完整读取真实 diff，均为原审查记录，缓存等价的历史说法已由 F27 更正，不引入生产代码。 |
| `fa1396a0`、`0702ce4c` | 已读完整真实 diff，均为工作纪律与兼容条件记录文档；其 tag 差异判断不正确，见 F42。 |
| `011356e0` | 主代理完整读取真实 diff 与官方、Fabric 条件实现：修复 64 个兼容配方标签条件取反错误，见 F42；实际第三方模组兼容正路径及版本比较器差异仍未实测。 |
| `d04c73d0`、`673d8768` | 主代理已读两项完整 diff 并对照固定 NeoForge GUI 列高度实现；确认坐骑血条被错误计入左列，见 F43。Curios 和模式文字的既有布局 delta 未由启动 proof 证明。 |
| `a4516503`、`a0506c12` | 主代理已读完整 diff：Flame 渲染器按 Fabric 实体类型注册，文档提交仅作记录。标题屏 proof 不证明世界内火舌实际渲染。 |
| `1d44ca1f`、`2cc11b1e` | 主代理已读完整 diff，并对照固定两份 Minecraft 的滚轮与雾调用点；发现滚轮触发时机及近雾设置错误，见 F44。其他视觉、输入和声音实际交互尚未全面验证。 |
| `601da1af`、`5d074d11` | 主代理已读完整 diff；原静态模型/BER 分割已有后续真实 FRAPI 模型修复，见 F39。Quad 的顶点重编码问题已修，见 F45；能量方块、装备及节日模型画面仍待实机验证。 |
| `254afa6b`、`bc13ab39` | 主代理已读完整 diff，对照官方附加层和 vanilla 鞘翅绘制；修正普通鞘翅被附加层重复绘制，见 F47。装备模型资源重载已在 F40 修复，实际穿戴画面仍未验证。 |
| `3cedf196`、`3833f46f` | 主代理已读完整 diff 并追查皮肤注册与物品、实体模型选择；物品忽略皮肤自定义模型，见待修 F48；实体与物品在真实世界、资源重载及自定义皮肤的渲染尚未验。 |
| `a634adff`、`a7369797` | 主代理已读完整 diff，对照模型 JSON、六方向 blockstate 与 tile 同步；原 BER 叠绘并未实现官方状态切换，见待修 F49；实际视觉未验。 |
| `a64dca8b`、`9941f867` | 主代理已读完整 diff，对照原始 tick 声音守卫；暂停时声音更新时机不一致，见 F50，真实声音未验。 |
| `f45d2bd7` | 主代理已读完整 diff，仅为 MekaSuit OBJ 下一轮边界记录，不直接改变生产逻辑。 |
| `7765313e` | 主代理已读完整真实 diff、追踪模块注册、分类与重载；确认模块规格未注册、模型数据类型转换崩溃和重载分类失效，见 F51。模型启动 proof 只证 handler 安装，不证明穿戴或模块渲染；方块图集清单缺失另见 F52。 |
| `91fbebef` | 已读完整 diff，仅为原审查记录；其通过结论被 F51/F52 的证据更正，不改变生产代码。 |
| `ab5f7ad0` | 已读完整 diff、对照固定 NeoForge 动态桶模型和客户端实际缺纹理日志；缺失图集资源、缺默认 display transform 等问题已按 F52 修复核心部分。启动计数不证明模型完成。 |
| `b1b97518`、`c34a60af` | 已读完整 diff，均为历史审查文档；原称 z 平移等价、从本地 jar 运行时读纹理及已闭环均不成立，见 F52。 |
| `9876fe84` | 已读完整 diff，仅删除误放仓库根目录的两项资源；原构建不包含该根目录，删除并未造成运行时新增变化，但没有在真实资源树补 mask，相关缺失已按 F52 修复。 |
| `1f3283dd` | 已读完整 diff、查 Fabric 固定事件 mixin 与实际 owner；首次登录也触发 dimension reset，与官方仅跨世界 clone 不符，见 F53。addon 模块生命周期列表目前未见注册者，跨模组行为仍未验。 |
| `205a67fd` | 已读完整 diff，仅为历史审查文档；其事件等价的结论需按 F53 更正。 |
| `1b770d06`、`7bb720c7` | 已读完整实现及文档 diff，对照固定官方 ClientTickHandler；原审查误称官方每 1200 tick 通知，实际上每 tick 调用，见 F54。图集回调和节日消息尚未实际画面/日期验证。 |
| `64fedbc7` | 已读完整 diff，对照固定官方实体加入时声音守卫：客户端实体加载 Player 与 enablePlayerSounds 守卫一致；真实追踪、静音及音频尚未实机验证。 |
| `fe375103`、`ae350c7c` | 已读完整实现和原审查 diff，对照官方 DriveArrayBakedModel、资源及物品组件；物品驱动器显示完全缺失、方块驱动器 LED 的 NeoForge 顶点亮度未生效，见 F55。miner 变换实机及第三方模型尚未验。 |
| `c4c4dc0f`、`20aaec89` | 已读完整实现及原审查 diff；机械管流体缓存原错误已在 F36 修复。传输器 contents 每帧重新烘焙而非按模型/图标缓存，已按 F56 恢复；实际管线画面和第三方模型未验。 |
| `e0dee5bd`、`1726b774` | 已读完整 diff、相关 renderer 及同步路径；transit 更新标签和 S2C batch 的 Fabric 注册均存在，重载时 block entity renderer 重建，原模型抑制自引用已有本提交修复。未证实新的具体生产缺陷；在途物品、分流覆盖和第三方渲染器画面尚未实测。 |
| `83cd82f1`、`6647ec72` | 已读完整 diff，对照两份固定 Minecraft 的 GuiGraphics 调用点及 NeoForge 装饰器 handler；调用在 vanilla 绘制和 pose pop 之后、空栈被过滤，MekaSuit 色值与官方一致。但 NeoForge 对每个装饰器显式备份/复位 GL 状态并支持多个装饰器，Fabric 单个注册表没有这些平台保证；普通 vanilla 物品绘制会 flush 并启用深度，不能仅凭差异断言实际损坏，特殊 GUI/第三方交互及画面仍待验证。 |
| `95b025ae`、`c67d838f` | 已读完整 diff，确认五屏与原实体容器匹配。皮肤预览与实体/物品共享模型接线还存在普通模型未注册及物品 builtin 不触发的问题，扩充 F48；旧审查“全闭环”和标题屏 proof 均不证明皮肤 GUI 画面。 |
| `e5f41424`、`d4d34fcd` | 已读完整 diff，与官方屏幕注册逐项对照；历史声明故意延后的两个物品 GUI 实际已有注册物品、容器和使用入口；原提交还遗漏四个官方 helper 注册的机器屏幕（包括 Crusher），均见 F58。其余注册对照不代替真实交互验证。 |
| `f3c7947b`、`07eb2698` | 已读完整脚本和文档 diff，仅改验证、不改生产代码。脚本以再生日志证明触发，不证明源矿石/盐数量、目标版本持久化或全部区块；第一阶段等待错误日志名且两个固定 sleep 不符合当前长任务纪律，不能将旧 smoke 当作这些性质的完整证明。 |
| `4858ee35`、`a02d7298` | 已读完整脚本和文档 diff；扫描器仅检查显式 import，不涵盖同包、通配、完全限定名及反射，也不检查 entrypoint 是否纳片，故“0 边/系统审查通过”只能解释为该正则范围内的结果，不能替代本轮真实 diff/调用链审查。原文档未改变生产代码。 |

## 审查发现与待复核

### F58 — 四种机器和两种物品缺失客户端屏幕（`e5f41424`，代码已修；实机待验）

逐项核对 `ClientRegistration.registerScreens` 发现，`CRUSHER`、`CHEMICAL_INJECTION_CHAMBER`、`OSMIUM_COMPRESSOR`、`PURIFICATION_CHAMBER` 四个通过官方泛型 helper 注册的机器被该批次遗漏，尽管真实方块和菜单已经注册，直接影响 Crusher 等已纳机器的 GUI。另两个物品、对应容器和服务器使用入口均已纳入并注册：`ItemPortableTeleporter.use` 触发 `PORTABLE_TELEPORTER` 菜单，`ItemSeismicReader.use` 在振动和能源满足时触发 `SEISMIC_READER` 菜单；但两份官方 GUI 不在 common slice，`FabricScreenRegistration` 也没有对应 MenuScreens 映射。历史将其整体延后会使现有公开物品无法正常显示对应菜单，而非仅是尚未纳入的孤立新功能。修复 `0bed7f36` 纳入两份现有 GUI 并注册六个原菜单，机器使用原 electric/advanced electric 屏幕；地震 GUI 使用 vanilla block clone、按 flowing/source 归一化流体、现有 Fabric fluid render handler 与 FluidStack 名称。已按审查 prompt 重读该提交完整 205 行 diff，对照六项官方注册和两物品使用路径，未发现额外限定范围缺陷。初次编译因四个泛型构造引用无法推断而失败，补全真实 tile/container 类型后编译与三个 slice gate 通过；Knot 聚焦测试覆盖水/岩浆流动与源归一化。真实客户端启动 smoke 19/19，日志含六个准确菜单 ID，客户端 timeout 124；未打开实际菜单，因此不证明交互与显示。Fabric 不提供 NeoForge 的通用 FluidType：多个不相关源 fluid 共享同一 NeoForge type、自定义 type 显示名、带玩家/命中信息的第三方 pick-block 扩展仍可能不同；必须用 mcdebug/真实客户端验证 Crusher、便携传送器、地震列显示以及第三方流体，不能将启动 proof 当交互验收。

### F57 — Robit 模型元素自发光丢失（`3cedf196`，代码已修；实机画面待验）

官方 Robit 两种模型 JSON 为眼睛和部分 allay 几何设置 `neoforge_data` 的 block/sky 最低亮度；Fabric `FabricRobitUnbakedModel` 使用 vanilla `BlockElement` 解析、烘焙时忽略此字段，实体和物品绘制又用 vanilla `putBulkData` 只传环境亮度。即使模型加载修复，暗处这些指定的面仍不会按原设定发光；GUI 预览本身传 FULL_BRIGHT，不足以证明实体光照。修复 `e854c16a` 将元素最低亮度写入对应烘焙顶点，实体与物品绘制时按 block/sky 分量与环境光合并；不会将整个模型设为全亮。Knot 测试使用官方 allay JSON 和合成 sprite 验证暗、3 级、全亮及较亮环境四类输出；GUI 本身全亮，未测真实世界眼睛外观。该 helper 在一个 quad 的四顶点间取最大值，不能声称保留第三方逐顶点不同亮度；面级或继承的 NeoForge 数据亦未全面验证。

### F56 — 传输器内容几何缓存丢失（`c4c4dc0f`，已修复）

官方 `RenderTransmitterBase` 用 `ContentsModelData(visible, icon)` 缓存最终平面法线 quad，纹理重缝时清空；移植版每次渲染均调用 OBJ 逐组重新烘焙、解包和重编码。`BaseModelCache.OBJModelData.getQuads(..., iconOverride)` 对非空 icon 每次新建临时 map，不能像原审查称的那样按 `(visible, icon)` 缓存。四个真实传输器 BER 每帧走此路径，会随可见管线数量重复分配和烘焙；静态调用链能确认工作量差异，但没有量化实机帧率。修复 `e72ac0f2` 恢复原 renderer owner 的最终 quad 缓存，复制 visible 列表作不可变键并在 `onStitch` 同时清空缓存与 OBJ 几何。已按审查 prompt 读取该提交完整真实 diff、对照原缓存键和当前纹理重载调用链，未发现该限定行为新增偏离；`compileJava` 和三项 slice gate 通过。未做游戏内帧率或资源包热重载实测。

### F55 — QIO 驱动阵列的物品外观和发光（`fe375103`，代码修复已提交；实机画面待验）

原 `DriveArrayBakedModel.DriveArrayOverrideList` 从阵列物品的 12 个附件槽、drive metadata 和频率推导 OFFLINE/READY/NEAR_FULL/FULL，并将相应驱动器几何添加到物品模型；Fabric 仅注册方块实体渲染器，物品模型无 override，因此带驱动器的阵列在手中/物品栏仍显示空架。原审查已明确延后，但这属于已接入模型的官方公共视觉行为，不能以启动 proof 算闭环。

三个在线 drive 模型的 LED 使用 `neoforge_data` 的 block/sky 15（offline 模型无 LED）；原缓存走 vanilla JSON 烘焙忽略此字段，且 BER 使用 vanilla `putBulkData` 不读取 baked light，故暗处发光语义未实现。阵列在线 shell 的频率/状态 LED 也有同类声明。

修复 `ed4ec533` 按官方状态算法读取物品附件，并让物品和方块实体共用原 drive 模型、摆位和方向；模型加载阶段按原始元素及实际烘焙旋转恢复逐面亮度，shell 和物品用 FRAPI 发射、BER 按分量合并环境和最低亮度。模型缓存改在 wrapper 后捕获，每次资源重载重新解析；物品仍委托原模型及 override，非 vanilla 自定义模型的发射也保留委托。已依审查 prompt 重读该提交完整 618 行真实 diff，对照官方 override、Fabric 顶层物品烘焙及模型事件 phase 顺序，未发现该限定路径新增偏离。`compileJava` 和两项隔离 Knot focused tests 通过：真实附件槽和 drive 元数据覆盖空、离线及三个在线容量状态；合成 sprite 的两个旋转验证原始模型中发光与普通面分别匹配。首次状态测试缺少真实槽 creator 而失败，按实际 owner 注册后重跑通过。真实客户端 smoke 16/16；日志证明物品顶层 wrapper 装载且 shell 四面、三个在线 drive 各五面亮度被解析，离线为零；客户端在标题 proof 后主动终止（exit 1），未证明任何像素。

仍未用 mcdebug 验证手持、物品栏、暗处方块、朝向、透明渲染或资源包重载画面。非 vanilla 自定义 FRAPI 模型的基础发射直接委托原模型，其自定义的 NeoForge 灯光元数据不由此 wrapper 强加；其他 QIO 壳体不属于本次修复，资源包 override 链与第三方模型兼容也未实测。

### F54 — 节日通知频率偏离官方（`1b770d06`，代码修复已提交）

固定官方基线 `4646f509` 的 `ClientTickHandler.onTick` 每个有玩家的客户端 tick 调用 `HolidayManager.notify`，内部 `holidaysNotified` 已保证只在当日首次执行实际通知；原提交凭空引入 init 标志及 1200 tick 轮询，日期切换后可能延迟约一分钟提示，且误称为官方节奏。该提交新增的 `MekanismClient.ticksPassed` 在官方基线无此字段，当前仅由这一轮询消费。

修复 `cfd7219d` 恢复原始调用频率、位置并删除两项仅服务该轮询的状态；已读该提交完整 diff 并按审查 prompt 对照基线及通知 owner，未发现本边界新增偏离；`compileJava` 通过。真实跨午夜的聊天与资源画面未实测。

### F53 — 首次登录被当作跨维度克隆（`1f3283dd`，修复已提交；实际转换待验）

固定 Fabric lifecycle API 的 `MinecraftClientMixin` 在 `Minecraft.setLevel` 尾部对所有非空新世界触发 `AFTER_CLIENT_WORLD_CHANGE`，包含首次登录；该提交无条件调用 `resetDimensionChange`。官方 `onCloneRespawn` 仅当旧、新玩家 level 不同才调用，登录本身由 `launchClient` 处理。额外清理会清空 `PlayerState`、振动器及客户端辐射等；集成服中共享的 state/map 还可能影响其他玩家状态。

修复 `21b0d740` 跟踪非空旧世界，仅在不同世界间切换时调用 dimension reset，断线清除引用，保留原网络清理。已重读该提交完整 diff，对照 Fabric 注入点和 `ClientPacketListener.handleRespawn`：后者只在维度不同创建新 ClientLevel 并 setLevel，同维度重生不触发 world change；未发现该范围新增偏离。`compileJava` 通过。尚未实机验证登录、断线重连及跨维度时序：Fabric 回调早于创建新玩家，而官方 clone 回调在新玩家建立后，不能声明时序完全等价；集成服实际影响与 addon 生命周期仍待验证。

### F52 — 化学桶及装备图集资源与桶变换缺失（`ab5f7ad0`，核心修复已提交；实际画面待验）

真实客户端 `fabric/run/logs/latest.log` 在 18 个桶报告缺少 `neoforge:item/mask/bucket_fluid`，并在大多数桶和流体方块报告缺少 `mekanism:liquid/liquid` / `mekanism:liquid/steam`。`processResources` 未打包官方生成的 `assets/minecraft/atlases/blocks.json`，而根目录中误提交的 NeoForge mask 随 `9876fe84` 删除且未在 Fabric 资源中补回；已检查当前构建 jar 中两者均不存在。缺失图集清单也影响其中列出的 MekaSuit 纹理和其他 Mekanism 精灵。修复需在可分发许可范围内提供真实 mask 与官方 atlas 源，不能依赖未纳入构建的本地参考 jar；随后验证烘焙真实 sprite 而非 missingno。

桶模型另硬编码 `ItemTransforms.NO_TRANSFORMS` 和 identity bake，而固定 NeoForge `assets/neoforge/models/item/default.json` 给出 ground/head/firstperson/thirdperson/fixed 变换，`DynamicFluidContainerModel` 使用 context transforms、incoming model state 和以中心缩放的 fluid transform；原实现将 fluid 两面统一平移也不等价。

修复 `67e27f60` 打包官方生成的方块图集清单及固定 NeoForge 21.1.200 的 mask/父模型（LGPL-2.1 许可证和来源、hash 随产物提供）；桶模型通过 vanilla 父模型解析纹理/变换，恢复 incoming state、fluid 中心缩放、mask 边缘与官方 seam 处理，粒子默认取 fluid。已依 prompt 重读该提交真实 diff、对照固定 NeoForge `DynamicFluidContainerModel`、`UnbakedGeometryHelper` 和 `ClientHooks`；二进制资源核对 hash、许可证文件逐字匹配系统 LGPL-2.1 原文，未在修复范围内发现新偏离。`build` 和 focused 测试通过；后者验证父模型显示变换、烘焙方向和流体 tint quad，使用合成 sprite 而非真实画面。实际客户端 smoke 16/16，重载后先前缺纹理日志消失，包内检查确认 atlas、三个 NeoForge 资源及许可证存在；client exit 124 为脚本预设超时。真实桶持握/GUI、MekaSuit 外观、资源包重载及第三方纹理覆盖仍未视觉验收；官方动态 contained-fluid override 和非本模组通用流体语义未声明完成。

### F51 — MekaSuit 模块模型接线与重载失效（`7765313e`，修复已提交；实机画面待验）

原官方 `ClientRegistration.init` 注册 jetpack、modulator、elytra 三个规格；Fabric 仅注册 OBJ 模型而未注册规格，故这些模块无法参与外观选择。`registerMekaSuitModuleModel` 创建普通 `OBJModelData`，而 `createQuads` 在规格非空时将所有条目强转为 `ModuleOBJModelData`，第三方注册规格或恢复官方规格后会抛 ClassCastException。即便修正类型，原 `clearCached()` 只清几何和烘焙缓存，`ModuleOBJModelData` 的 `classified` 和 `specParts` 不重置，资源包重载更换组名后仍使用旧分类。

修复 `a17f3df7` 恢复三个官方规格及飞行谓词，使用有类型的 `ModuleOBJModelData`，资源重载和迟到规格注册清分类与 armor quad 缓存；保留注册去重。已按审查 prompt 重读该提交完整真实 diff，对照官方规格与当前重载/注册调用链，未发现此范围新增偏离。最终 `compileJava` 和两个 focused 测试通过，分别验证模型类型/去重及模拟组名变化后的活动/不活动分类；既有客户端模型测试亦通过。真实客户端启动 15/15，达到标题屏后主动终止客户端，脚本零失败（client exit 1 为主动终止），只证明初始化而非穿戴渲染。第三方纹理图集/OBJ 支持、真实模块切换和资源包重载画面尚未验证。

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

**候选 WIP `f8df4ccc` 的完整 diff 复审**：导入先模拟 QIO 容量，实际提取与频率插入使用同一外层事务；短提取以实际数插入，超出请求和频率无法容纳全部实际提取时 abort。频率和真实驱动都登记快照；新类型的全局 UUID 推迟至外层最终提交，提交前读取该暂存 UUID 明确抛错而不是提前污染全局索引。`compileJava`、槽顺序测试与三个切片 gate 通过，但槽顺序测试未覆盖事务。试图在普通 JVM 创建真实频率/驱动 fixture 时，`MekanismDataComponents` 初始化触发自定义 chemical 根注册，因 vanilla registry 尚未 bootstrap 抛出 `IllegalArgumentException: Not bootstrapped (called from registry ResourceKey[minecraft:root / minecraft:game_event])`；未通过设置假 holder/跳过注册来掩盖，实验测试已移除。后续测试提交 `eb6109b5` 已完整复审：独立 Knot worker 执行真实 Mekanism 数据组件注册，以真实 `QIOFrequency`、`QIODriveSlot`、`ItemQIODrive` 与 vanilla `InventoryStorage`，配一个只提供驱动槽和通知计数的测试 holder。`qioTransactionTest` 七项通过，覆盖新/已有类型、嵌套 abort、提交分配 UUID 与通知、短提取、异常回滚源库存与 QIO、跨两个驱动回滚、替换盘不被覆盖。短提取和异常测试直接按 importer 同一事务模式调用真实 storage/frequency，未调用实际 tile 的 `tryImport`；驱动替换只证明抛错且替代盘不被写，并不证明频率、原盘和物品都一致。先前替换盘断言按传入 `ItemStack` 身份检查而失败，核对 slot 会复制 stack 后改为捕获 slot 实际存储的对象，重跑通过。Fabric `TransactionManagerImpl` 的关闭逻辑捕获 close callback 异常后继续调用其余参与者，但当前驱动身份失配时仍会留下原驱动缓存/物理盘状态不一致的日志风险。进一步复查发现非事务性重入会在 QIO 快照之后执行、然后被 abort 静默抹掉。修复 `0592e828` 已审阅完整 diff：插入事务激活期间拒绝非事务性的频率及驱动修改、驱动装卸和频率保存/tick，guard 与事务快照一起回滚，最终提交时解除；测试第八项验证插入、直接驱动修改、卸盘被拒并在 abort 后恢复普通操作，八项 Knot 测试通过。此 guard 是为了防止独立写入丢失的明确失败，不宣称具备对任意外部 owner 的自动事务化。仍须核查真实服务端 holder 替换、绕开 holder 的直接 stack 变更及 `onFinalCommit` 异常；候选不可视为完整验收。

**超出八项测试的理由**：第九项针对最终提交 holder 通知抛异常时的真实事务结果；与前述事务体中抛错触发 abort 不同，Fabric 此时已完成外层提交，必须确认源库存与 QIO 同时保留提交、UUID 已发布且防重入 guard 已释放，避免将提交后异常误当成可回滚并造成重复转移。`d461c115` 已审阅完整 diff；第九项及其余八项在隔离 Knot worker 全部通过，测试让 holder 的 `onDataUpdate` 在实际外层提交时抛错，确认源库存已扣、频率已增、UUID 已发布和 guard 已解除。该结果不覆盖 UUID 分配自身抛错，也不覆盖真实 tile 的网络或保存回调。

**真实槽 owner 只读复核**：子代理固定 `eec8bd9a` 审查并由主代理对照当前代码，发现插入事务活跃时若同步重入服务端驱动阵列的空槽装盘，`QIODriveSlot.setStack/Unchecked` 先让 `BasicInventorySlot` 写入并通知，之后 `frequency.addDrive` 才触发 guard 抛错，留下槽有盘但频率未登记；真实 GUI 和 `insertItem` 都调用该 setter。非空替换/卸盘原本先触发 remove guard，不能混为同一缺口。此条件下状态不一致已由调用顺序确认，但尚无普通合规 provider/玩家可同步重入的证明，也不应声称已实机复制。应将预检前移到真实槽写入前，并覆盖其他直接改变槽数的执行入口；仅捕获异常不能撤销写入。修复 `a4d481e9` 在服务端 `QIODriveSlot` 的 setter、执行型插入/提取及数量变更前查询同一 frequency guard；模拟操作不变，null/client world 仍遵守原装卸约定。十项隔离 Knot QIO 测试通过：新增第十项使用真实 `QIODriveSlot`、真实频率和有服务端语义的 `TileLevelStub`，验证重入时 set/unchecked/execute 写入在槽变化和 listener 前被拒、simulate 可用，abort 后正常装盘且频率登记；这项独立捕获空槽先写后抛的 mutation，故有据超过八项。已按 prompt 读该提交完整 diff，未发现限定边界新增偏离。测试并未证明真实 tile 的同步重入、直接修改已返回的 live ItemStack 或所有回调一致性。最终 UUID 发布异常发生在外层已提交之后，但尚未证明普通环境下可触发，仍列未验证边界。

### F9 — QIO 导入槽顺序变化（`a03c1888`，有槽存储已修复）

上游 `IItemHandler` 自最后一个槽递减到第一个；移植版对 `Storage.iterator()` 正向遍历。不同类型超过单次类型数或数量上限时，可能优先导入不同物品。Fabric 的 `SlottedStorage` 提供 `getSlots()`；修复 `61621cd9` 对其倒序遍历，无槽存储保持 provider 自身顺序。已审阅修复提交的完整 diff：仅调整取视图的顺序，不声称改变事务语义。两项 `QIOImporterSlotOrderTest` 用真实 vanilla `SimpleContainer` 的 Fabric `InventoryStorage` 验证索引和空槽的逆序；首次按 item variant 断言时普通 JVM 缺 Fabric mixin 导致 `ClassCastException`，改用真实槽 view 对象身份断言后通过。尚未在运行中游戏验证 sided inventory 的槽映射和导入上限。该修复不解决 F8 事务风险。

### F10 — 流体交互漏掉原物品堆能力检查（`a147116b`，已修复）

官方 `FluidUtils.handleTankInteraction` 先对原始整叠查询流体能力，只有存在 provider 才复制单件；该提交删除原始检查，直接对单件副本查询，致使刻意只对 count=1 暴露 handler 的物品堆也被处理。当前 Mekanism `ContainerType` 创建组件 handler 明确以 `stack.getCount() == 1` 为条件，`ComponentItemContext.read` 把 Fabric context 的实际 count 传给 provider，故这是实际调用链上的差异，不仅是表面 import。修复 `086ad4ed` 在复制前用相同 `ItemFluidContainer` lookup 检查原堆，不更改后续流体移动或创造成分。已复审完整 diff；Knot 下 `FluidTankInteractionEligibilityTest` 使用按真实 context count 决定是否提供 storage 的测试 provider，验证两件不被操作、单件可识别，另确认两个 vanilla 空桶仍可查询。此检查未验证真实世界 GUI、带组件的数据包或所有第三方 provider。

### F11 — Curios 可选集成被固定禁用（`a147116b`，未修复）

当前 `CuriosIntegration.isLoaded()` 无条件 false，查找返回空；即使安装兼容的 Fabric Curios 也不会使用原有喷气背包/自由奔跑靴、辐射屏蔽或对应模式键。该 no-op 是明确遗留，不等同于“无 Curios 环境正确”即可关闭，也不符合以假实现充当闭包的规则。历史裁定称 `curios-fabric 9.5.1+1.21.1` 的 Maven 当时无法访问；尚未重新验证依赖可取得性与其 Fabric slot/render API。真实修复涉及新增第三方可选模块及客户端渲染，超出当前已纳机器 owner，依 AGENTS 的范围规则先记录具体边界，不用永久 false 假称兼容；在获得范围裁决并核实依赖后实现，不以此阻塞同范围内其他审查和修复。

### F12 — 喷火实体模式未保存（`12ebbcf6`，已修复，实际世界加载待验）

原版 `MekanismAttachmentTypes.FLAMETHROWER_MODE` 以 `FlamethrowerMode.CODEC` 持久化非 `COMBAT` 模式；固定 NeoForge 21.1.200 的 `AttachmentHolder` 将注册名序列化在实体 NBT 的 `neoforge:attachments` 下，对应键 `mekanism:flamethrower_mode`。`12ebbcf6` 改用同步数据但没有读写保存，短生命周期实体在存盘/区块加载后会恢复默认 `COMBAT`，不能仅凭 spawn packet 同步认定保留持久语义。

修复 `205ecb43` 在真实 `EntityFlame` 的读写方法中沿用这两个官方 NBT 键和原枚举 codec/default，保留 superclass 的 owner 数据，写入已存在的附件 compound 时不覆盖其其他键；已复审完整 diff。Knot 下 `EntityFlamePersistenceTest` 两项通过，使用真实实体和模拟 Level 验证 heat 回环、缺失/无效默认以及已有 compound 中其他键不被覆盖。初次单独运行通过，但整个 `fabricTransferTest` 执行时此前测试已安装不同 chemical registry，导致初始化失败 `IllegalStateException: Chemical registry already installed`；不是生产功能通过的证据。修复测试隔离 `a7f0e1a4`（已审阅完整 diff）将该测试移至每类独立 Knot worker 的现有 source set，保留真实 registry 初始化；随后 `qioTransactionTest` 和整个 `fabricTransferTest` 均通过。该测试并未证明实际世界区块卸载/重新加载，也不承诺 Fabric 自动保存其他 mod 的未知 NeoForge 附件。

### F13 — 激光与喷火熔炼绕过破坏权限（`12ebbcf6`，已接入 Fabric veto，实机待验）

该提交去掉激光假玩家破坏/TNT 激活和喷火 HEAT 熔炼的 NeoForge `BlockEvent.BreakEvent`，直接修改世界；之后虽然矿机和纸箱已调用 `WorldUtils.canBreakBlock`，这两个入口仍未接入，因而不会触发 Mekanism 方块安全与 Fabric `PlayerBlockBreakEvents.BEFORE` 拒绝。修复 `8aa8f7dd` 在激光 TNT/普通破坏之前以仿真 owner 的真实假玩家检查，在 HEAT 有熔炼配方且服务器侧以射手检查，拒绝即不破坏；激光假玩家清理改为 `finally`。已复审完整 diff；隔离 Knot 的 `AutomaticBreakProtectionTest` 验证真实 Fabric BEFORE listener 的拒绝确实进入共享检查，`qioTransactionTest` 和整个 `fabricTransferTest` 通过。该测试不调用激光或真实喷火实体的世界修改，不能证明实机保护插件兼容；INFERNO 的放置事件/点火及第三方 NeoForge 事件不在此 break-event 修复覆盖范围。

### F14 — 模块附魔未进入原版游戏效果（`76f790ce`，游戏查询已修复）

该提交删除 MekaTool/MekaSuit 的 `getEnchantmentLevel` 和 `getAllEnchantments`；固定 NeoForge patched `EnchantmentHelper` 的等级查询委托 stack，两个 `runIterationOnItem` 也使用 gameplay 附魔，Fabric 原版只读 `DataComponents.ENCHANTMENTS`。`ModuleContainer.moduleBasedEnchantments()` 保存的模块附魔因此不参与上述原版游戏效果。修复 `3b945464` 只针对真实 MekaTool/MekaSuit，在原版等级查询及两个游戏效果遍历入口按官方 `max(stored,module)` 合并，不修改物品真实附魔组件，也不把虚拟附魔写进合成/存档。已审阅提交完整 diff：隔离 Knot 的 `GameplayModuleEnchantmentsTest` 对真实 MekaTool 和 MekaSuit 构造真实 module component，验证重叠取最大值、仅模块等级、原组件不变、两类原版迭代和无物理附魔组件及其他物品不受影响；完整 `qioTransactionTest`、`fabricTransferTest` 和三项切片 gate 均通过。后续 `8de19d7d`（已审阅完整 diff）修正过宽的直接 component 查询：原版实际通过 `IModuleHelper.INSTANCE.getModuleContainer`，因此未登记为模块容器的 Meka 物品即使携带组件也不应生效；测试现在使用真实支持注册，并验证未登记物品无虚拟等级。两个装备聚焦测试通过。未做实际玩家受伤/采掘和 remapped JAR 验证，其他模组对同一原版方法的 mixin 兼容性未证明。

### F15 — 附魔适用、物品重装备和 pick-block 行为仍待恢复（`76f790ce`）

同一提交还删去官方 `isBookEnchantable`、`isPrimaryItemFor`、`supportsEnchantment`、`shouldCauseReequipAnimation`、`shouldCauseBlockBreakReset` 和带模块装备的 `isNotReplaceableByPickAction`。pick-block 部分修复 `b7d2830d` 已审阅完整 diff：在原版 `Inventory.getSuitableHotbarSlot` 的原 `isEnchanted` 判断追加已登记 MekaTool/MekaSuit 的真实 `hasInstalledModules`，同一原版 Inventory 同时供客户端挑选与服务端 `handlePickItem` 调用；仍保留空槽优先与全部受保护时返回当前槽的官方回退，不承诺绝对不能替换。隔离 Knot 的 `PickBlockModuleProtectionTest` 使用真实注册模块、装备和库存，验证工具/护甲、空槽、全部受保护及无模块回退。首次测试因未通过实际 ModuleHelper 注册测试装备而失败，按真实支持注册后重跑通过；不声称真实客户端/服务端网络已验证。手持重装备与挖掘重置修复 `72ce9a66`（已审阅完整 diff）按官方不同物品类别，在客户端 `ItemInHandRenderer.tick` 和 `MultiPlayerGameMode.sameDestroyTarget` 恢复忽略同物品组件变化，保留主手槽位变化、空栈、不同物品以及普通物品的原比较；能源方块使用 mixin invoker 调用原有可覆写的 `exposesEnergyCapOrTooltips`。隔离 Knot 分类测试、全部 `qioTransactionTest`/`fabricTransferTest` 与三项 slice gate 通过；客户端 smoke 到达标题并无 mixin 启动错误，但未用 mcdebug 实际验证动画或挖掘进度，能源方块 invoker 在实机变更组件时也未验证。附魔规则修复 `5e84aef4`（已审阅完整 383 行 diff）分别接入 `EnchantmentHelper.getAvailableEnchantmentResults`、`AnvilMenu.createResult`、`EnchantCommand.enchant` 和 `EnchantRandomlyFunction.run`：恢复潜水面罩 Aqua Affinity 例外、电弓不从附魔台获得 Flame 但仍可用 Flame 书、不可附魔装备的 supports/primary/book 检查，保留铁砧创造模式覆盖 supports 但仍按官方拒绝书。隔离 Knot 测试实际调用附魔列表、铁砧和命令 owner，验证上述情形及普通电弓附魔；随机战利品 owner 只验证类加载，未执行实际 loot；完整事务/转移测试与三项切片 gate 通过。电弓虚拟 Flame 补丁 `5df76505`（已审阅完整 352 行 diff）恢复单附魔等级和两个效果遍历：无实体上下文按 NeoForge 顺序尝试当前服务端后客户端世界注册表，装备遍历使用实体自身注册表，找不到 Flame 时不伪造 holder；存档组件不写入虚拟附魔。为防虚拟等级查询使原射弹分支误以为真实附魔已存在，`createProjectile` 改检查真实组件，再向射弹传临时拷贝。隔离 Knot 测试验证等级、装备效果遍历、已存更高等级及真实 `createProjectile` 向箭物品传入 Flame 拷贝；完整事务/转移测试与三项切片 gate 通过。未实测有生命服务器上下文的无实体遍历、实际箭实体点火或远程客户端注册表。

### F16 — 客户端护甲层注册修改遍历中的列表（`254afa6b`，已修复，渲染效果待验）

本轮客户端启动日志出现 `Caught error loading resourcepacks` → `Failed to create model for minecraft:piglin` → `ConcurrentModificationException`，实际栈指向 `FabricArmorLayerRegistration.addCustomLayers`；原实现在遍历 `renderer.layers` 时调用会同步添加该列表的 `registrationHelper.register`。`git blame` 将该循环追至尚未按时间顺序审查的 `254afa6b`，不是将该提交其他内容当作已审。修复 `deb6c79e` 对原 layers 做 `ArrayList` 快照后遍历，保留 null 过滤、精确类匹配和各类最多添加一层；已审阅完整 diff。再次运行客户端 smoke 到达标题，日志中未再出现上述资源重载异常；旧有能源方块模型 bake 警告仍存在，视觉装备、资源重载及第三方渲染器兼容未做实机验收。

### F17 — 装备属性桥未启用，水下推进与耗能条件失真（`1d4c4027`）

追查水下模块调用链时发现 `ItemStackAttributesMixin` 源码由较早的 `1efaff7f` 建立，但当前切片与 mixin 配置均未纳入，故自由跑鞋、MekaTool 及模块的条件属性并未进入 `ItemStack.forEachModifier`。同时 `1d4c4027` 将 NeoForge `SWIM_SPEED` +1 错加到上限为 1 的 `WATER_MOVEMENT_EFFICIENCY`：四模块已有 1 点水中移动效率，额外值被截断，不会达到官方对水中 `moveRelative` 和水中上下移动乘 2 的效果；以脚部方块是否含流体替代实体相交流体缓存也会漏掉碰到身体上部的流体。

修复 `9e063837`（已审阅完整 301 行 diff）纳入/注册已有装备属性桥，保留官方原有水中移动效率修饰，在玩家穿戴启用且有能量的四级模块时于 vanilla 水中推进与上下运动 owner 实施对应 2 倍，不再添加错误属性。能耗检测改为遍历实际实体包围盒相交的非空流体面，并先检查周边区块已加载。真实自由跑鞋 ItemStack 的两种属性查询和模式切换测试、水面部分相交及不加载区块测试通过；首次两次测试失败分别来自 fixture 的光照及 `hasChunk` 未建模，改用真实 chunk section 和 fixture 的已安装区块映射后通过。完整事务/转移测试、三项 slice gate 及客户端标题 smoke 通过。

**仍有限制：** 未用实际穿戴满级且有电的模块实测水中行进、耗电、服务端同步；该补丁只接 vanilla 水的移动 owner，NeoForge 的非原版 `FluidType` 可参与水式移动、`isAir` 和自定义运动，Fabric 没有相同通用语义，不能声称此部分等价；包围盒重查与 NeoForge 已缓存 fluid type 的时点亦可能不同。已记录，不用测试通过关闭这些边界。

### F18 — 传送粒子包没有 Fabric S2C 注册（`7785a9c1`，已接线）

该提交保留 `PacketPortalFX` 的 payload 和 handler；当前传送器、便携传送器及 MekaTool 原有路径通过 `PacketUtils.sendToAllTracking` 发送，但 Fabric S2C codec 与客户端 receiver 原来均未注册。`PacketHandler` 的旧声明不在当前 common slice，不能当作现有注册；真实有追踪玩家时发送未注册 payload 存在失败风险。修复 `791a227c`（已审阅完整 138 行 diff）在 common bootstrap 注册原 TYPE/STREAM_CODEC、在 client bootstrap 将原 handler 接到本地玩家，保留同样的发送位置及粒子逻辑。Knot 测试验证注册、重复初始化与原包 codec 往返，客户端标题 smoke 通过；尚未进行两进程传送/粒子显示端到端测试。

### F19 — 实体同步数据 serializer 数字 ID 依赖本地注册顺序（`7785a9c1`，已改用稳定原版类型）

原 `MekanismDataSerializers` 将 Robit UUID、安全模式、皮肤和 Flame 模式注册到 vanilla `EntityDataSerializers` 本地递增表；`SynchedEntityData.DataValue.write/read` 在线路只写数字 ID 并按接收端该数字查 serializer。若其他 mod 仅在一端或以不同顺序注册，同一数字可对应不同类型或不存在，触发实体数据解码/赋值错误。修复 `88edd27a`（已审阅完整 531 行 diff）让两个真实实体的数据使用 vanilla 固定编号的 OPTIONAL_UUID、INT 和 STRING，枚举仍使用原 ordinal/BY_ID、skin 仍还原 canonical ResourceKey，NBT 字段未改；保留原 serializer 注册以免静默移除已有公开 holder。曾为拒绝旧开发版而增加 Fabric 握手后缀；用户明确本移植尚未发布，不要求跨开发提交兼容或拒绝旧开发版，故撤去后缀并恢复官方 `10.7.19`。隔离 Knot 测试在先注册额外 serializer 后用真实 Robit/Flame 数据写入、解码、赋值和 NBT 回环；完整普通/事务/转移测试及切片 gate 通过。此前两客户端 smoke 使用的是现已移除的后缀，不能作为恢复官方版本后的运行证据。不同模组集合的双进程实体同步尚未证明。

### F20 — Robit 实体安全服务声明未打包（调用 F19 测试时发现）

首次执行真实 Robit `setSecurityMode` 的测试抛出 `No provider found for Mekanism service mekanism.api.security.IEntitySecurityUtils`：`EntitySecurityUtils` 已纳入 common slice，官方 service 文件也存在，但 Fabric `processResources` 白名单漏了该文件，因此生产包同样无法通过 ServiceLoader 初始化安全工具。修复 `c8b07b97` 已审阅完整 diff，将原始服务声明纳入 Fabric 资源，不替换为假实现。重跑真实实体安全模式、同步和存档回环通过；实际游戏中 Robit 安全 GUI 与权限状态联动仍待验证。

### F21 — 早期 Fabric 区块加载数据迁移（不在要求范围）

此前将 `4e5a4232` 早期 Fabric 强加载存档与 `5ee61549` 后 region ticket 之间的迁移列为待决。用户明确本移植未向玩家发布，不要求旧 Fabric 开发版间存档兼容，旧移植数据可丢弃、不编写迁移代码，因此该问题撤出修复范围，不再等待选择。此裁定不改变固定官方 Mekanism 的存档语义要求；当前 owner ticket 的多维重启、崩溃行为仍需与官方实现分别核对。

### F22 — 传送速度与激光命中特效客户端包未注册（`b6e5c54d`，已接线）

只读子代理固定 `b6acf106` 审阅真实 diff 并报告；主代理复核实际发送方 `TileEntityTeleporter`、`TileEntityBasicLaser` 及 Fabric common/client 注册 owner，确认两个包没有 S2C codec/receiver。`8c478972`（已审阅完整 118 行 diff）接回两个包的原 TYPE/codec 与客户端 handler，并将激光命中特效从整块破碎粒子 `destroy` 改为 vanilla 朝命中面 `crack`。Knot focused 测试通过：注册、幂等及实际 codec 往返；客户端真实传送速度、激光粒子视觉及多人 E2E 未验证。打包 smoke 先因过期的测试总数门禁失败，`724b01c2`（完整 diff 已审阅）按实际 XML 更新总数，重试时 Gradle `build` 全部通过；随后 smoke 因脚本中预期 access widener 清单过时而在启动服务器前失败，故不能以该次运行证明 dedicated 启动。

### F23 — retrogen 特征缓存未随服务器停止清理（`509afc1b`，已修）

只读子代理报告并经主代理复核：`GenHandler.cachedFeatures` 保存上一世界 registry 的 placed-feature holder，原入口服务停止时 `reset`，Fabric 停止 owner 漏调；同一 JVM 重进不同世界会复用错误 holder。`cda8b1fe`（完整 diff 1 行已审阅）在 `MekanismServerState.clearServerState` 调用既有 `GenHandler.reset()`，与其他 post-save 清理同生命周期；编译通过，真实双世界 retrogen 待验证。

### F24 — SPS 合成闪电额外造成普通闪电伤害（`1af82201`，已修）

主代理对照 NeoForge `Entity.thunderHit` 使用 `lightning.getDamage()`、Fabric vanilla 固定 5 点，确认官方 SPS 将 bolt damage 设为零但移植漏掉。`c01c1db2`（已审阅完整 152 行 diff）以 SPS 专用合成闪电标识，限定 vanilla `Entity.thunderHit` 的 hurt 伤害参数为零，保留实际雷击回调、实体变种转换和火焰状态恢复，不影响普通闪电。Knot 测试执行真实 `thunderHit`，验证合成闪电 0、普通闪电 5；真实 SPS 实验实体和外部雷击 veto 仍未端到端验证。

### F25 — 精炼黑曜石不被识别为下界传送门框（`1af82201`，已修）

主代理核对官方 `BlockResource.isPortalFrame` 和 vanilla `PortalShape` 的底边、侧边及顶部检查；`3c48166e`（已审阅完整 140 行 diff）只对官方资源标志为 true 的 `BlockResource` 扩展原判定。Knot 测试调用真实 `PortalShape` 验证精炼黑曜石、混合原版黑曜石、原版框架与非框架钢块；实际点火和传送仍未实机验证。

### F26 — 流体渲染 handler 与取桶音缺失（`1af82201`，已接线）

`MekanismFluids.FLUIDS` 持有流体原纹理与颜色，但原 Fabric 客户端没有为 still/flowing 注册 `FluidRenderHandler` 或 translucent layer；真实渲染 owner 会得到 missing sprite 或无 tint。`8cd0876f`（已审阅完整 131 行 diff）通过 Fabric 注册每组原 still/flow/overlay 及 tint，并接原透明层；同时让两种流体变体的 `getPickupSound` 返回官方 bucket-fill 声音。真实注册对象的源/流动声音测试通过，真实 server + 两个 client 的握手和进入游戏通过；**未实际观察液体、储罐、GUI 贴图或听觉效果**，不能由标题/连接推断视觉正确。液体罐随储液发光已在 F28 单独修复，真实光传播仍待验证。

### F27 — 配方差集/交集被当作简单并集缓存（后续 `347e5803`）

只读子代理追踪官方染色配方与 `containsPairing`，主代理核对 `ItemStackIngredient` 和缓存后确认：差集基底与交集子项被当作各自可匹配，排除或非交集物品被错误允许进入配方槽。`b6a6e365`（已审阅完整 123 行 diff）令差集/交集走真实 `testType` 的 complex 路径，且过滤代表物并正确识别空差集/交集。既有 cache 测试扩展覆盖双输入匹配、单输入、显示代表物及空结果，focused 测试通过；本轮完整普通测试、隔离 Knot 测试及三项 slice gate 均通过。未声明实际产物错误或游戏 UI 已验。

### F28 — 储液罐没有根据储液发光（`1af82201`，已接入状态型亮度）

主代理核对固定官方 `BlockFluidTank.getLightEmission` 取环境亮度与储液 `FluidType` 亮度最大值；Fabric 原有 `checkLight` 却只读取静态方块亮度。`bab48b57`（已审阅完整 176 行 diff）为储液罐新增 `fluid_light` 状态，让原版光照索引在服务端液体变化后收到保存和同步的亮度；Mekanism 液体使用自身 FluidType 元数据，其他 Fabric 液体以其默认流体方块静态光度回退。已有官方旧存档没有新属性时使用默认 0，服务器后续 tick 按实际内容纠正；不要求兼容旧 Fabric 开发版。Knot 测试验证空、水、岩浆和自定义亮度以及实际方块状态发光，双客户端连接和资源加载通过；未实测真实游戏世界中插入/抽出、光引擎传播、重启及第三方动态流体亮度，不能将这些作为已验结论。

### F29 — GUI 客户端包缺少服务端目标与权限绑定（`53fda9e5`，部分已收紧）

只读子代理检查全部 26 个 C2S 注册及调用方，主代理复核确认原官方 handler 允许通过客户端提供坐标，绕过 GUI 入口访问已加载的他人安全桌、改装站或储罐；安全桌模式/override/可信名单操作也缺少服务端 frequency-owner 校验。这是上游共享的安全问题，不伪称由本移植独自引入。`88ef07e9`（已审阅完整 420 行 diff）对机器配置、过滤、频率、能量、滴管、模块移除和切页等 GUI 包要求当前菜单对象、仍有效及实时 canAccess，兼容普通 tile 与 QIO dashboard；Robit GUI 包绑定当前实体菜单；安全桌可信名单、模式及 override 限定真实 frequency owner，客户端删除按钮同步显示 owner 权限。全套普通、Knot、转移测试和三个 slice gate 通过；focused 测试验证无菜单、实体菜单目标不符、权限撤销及失效菜单，但**未验证真实 tile 菜单正向交互、QIO、多方块、实际网络攻击或 GUI**。PacketKey 任意 int 导致无界 key 集的问题已在 F31 复核修复；装备设置的菜单约束及便携传送延迟仍是独立候选，不声称全网络已加固；无菜单热键与便携传送未强行套 GUI 限制。

### F30 — Mekanism 内部能源错误依赖外部 FE 开关及换算（`b0f7e13d`，已修）

子代理固定 `07b7e6ba` 追踪实际 cube→cable→machine 调用链，主代理复核确认 `EnergyAcceptorCache` 只在外部 Forge/Team Reborn compat 启用时查询，且内部 strict handler 经 `2.5 J/FE` 转换，设置 `blacklistForge=true` 会切断内部能源，1 J 可能卡住而非被转移。`d0d6ed9c`（已审阅完整 511 行 diff）接回已有 `mekanism:strict_energy_handler` Fabric lookup：真实机器、电缆及 bounding 端口优先提供精确 Joules，保持侧配置检查、实体生命周期与代理撤销；外部 Team Reborn 保留原开关/换算后备。普通 manager 测试在 `blacklistForge=true` 与 2.5 配置下验证 1 J、错误侧及旧代理失效；独立 Knot 测试验证真实 Fabric lookup 命中 strict 身份和侧向解析；完整普通/Knot/转移测试及三项 slice gate 通过。使用实际 remapped 包的 dedicated machine smoke 在 `<world>/serverconfig/Mekanism/general.toml` 明确写入 `blacklistForge=true`、`feConversionRate=2.5` 后达到 cable-powered 机器产物与电池放电 PASS；脚本断言不证明精确产量或世界中的 1 J 端到端迁移，动态相邻变化、其他 mod 互操作仍待验证。首次普通测试因直接初始化完整 `Capabilities` 触及非 Knot 环境 FabricLoader 而失败，后将身份断言移到真正 Knot 测试后通过。

### F31 — 上升键同步与按键包生命周期（`ffbcb655`、`53fda9e5`，已修；真实按键待验）

`ffbcb655` 将官方 `ClientTickHandler` 中喷气背包持有时的 `MekanismClient.updateKey(..., KeySync.ASCEND)` 留为 deferred 注释；但服务器 `CommonPlayerTickHandler` 仍凭 ASCEND 状态处理飞行，导致客户端上升输入未同步。`1141ee82` 恢复官方原位置的调用；同时收紧已注册 `PacketKey` 的任意整数输入，只接受现有 ASCEND/BOOST，避免单个玩家无限增长 key 集合，并在服务器断连、停止及客户端 reset 释放对应状态。已审完整 143 行 diff；真实 Knot handler 测试验证非法 key 被拒、两个合法 key 的加减通过，原 GUI 授权测试通过。该安全防护属于原版继承的无界输入问题，不要求 GUI。复审发现集成服务器与客户端跨线程共享该静态 map，后续 `1d5edb40` 改用并发 map 与同步集合、消除 contains/get 的删除竞争；完整 diff 已复审、focused handler 测试通过。没有真实客户端跳跃/喷气背包/断线重连演练，特别是集成服务端断线时序仍需验证；F2 其他输入边界未关闭。

### F32 — 字典不读取普通物品内流体标签（`ffbcb655`，已修；真实 GUI 待验）

原移植只查 Mekanism attachment，不能识别 vanilla 水桶或其他 `FluidStorage.ITEM`；`2d630147` 优先使用 Fabric `ContainerItemContext.withConstant(stack).find(FluidStorage.ITEM)` 的非空视图，后备仍保留自身 attachment。已核对固定 Fabric API 源码：桶注册到该 lookup，constant context 不修改物品；`compileJava` 通过，完整 diff 已复审。没有通过 mcdebug 实测字典界面或第三方容器。

### F33 — 外部发光流体渲染忽略自身亮度（`ffbcb655`，已修；画面待验）

原 `MekanismRenderer.calculateGlowLight` 只处理本 mod 流体，其余直接用环境光，熔岩在罐、机械管和罐物品内因此可能偏暗；这不同于 F28 的方块照明。`2d630147` 保留本 mod 精确元数据，其他流体使用默认状态对应方块的发光值，与 F28 的平台 fallback 一致；编译通过、完整 diff 已复审。非 vanilla 动态流体亮度和真实暗处画面均未验证。

### F34 — 矿石 biome modifier 未读取活动 datapack（`5c0fcb1f`，修复进行中）

子代理核验、主代理初核：`FabricBiomeModifiers.initialize()` 在 mod 初始化时用 `FabricLoader.getModContainer(...).findPath(...)` 固定读取 jar 中 6 个 JSON，再固定注册 Fabric biome modification；不会读取世界启用的数据包对同名 `neoforge/biome_modifier` 的覆盖、禁用或额外条目。`#mekanism:spawn_ores` tag 本身在选择器执行时动态查询，不能混同为 tag 覆盖失效。活动资源必须在 biome registry 装载/修改前按世界取到；当前 Fabric `BiomeSelectionContext` 未直接提供 ResourceManager，必须继续追踪 `RegistryDataLoader.load(ResourceManager, ...)` 与 Fabric biome 修改 hook 的同一世界装载时序、pack priority、codec/type 和客户端网络加载边界，再选择可靠实现；不能只把启动期读取改为另一处全局缓存。固定 Fabric biome API 源码显示仅在 `MinecraftServer` 构造器 RETURN 注入 `BiomeModificationImpl.finalizeWorldGen`；在此之前构造器已由 `WorldStem` 设置资源管理器。该方法依次遍历 biome 和已注册修改器，标记 registry 只修改一次，因此每个世界使用资源作用域而不是在启动时或每次建档累计注册；涉及注入顺序、异常清理及第三方 modifier 类型，仍须证明。`7d592b71` 在世界构造时捕获活动 ResourceManager，以 Fabric biome finalize 的同步作用域读取 pack 优先级胜出的 Mekanism 命名空间资源；单个固定 Fabric modifier 不随世界累积，并支持 `neoforge:none`。实际 remapped JAR 隔离服务端放置 datapack 覆盖 `fluorite` 为 none，日志出现 `modifiers=5 features=9 source=active_datapacks`（默认资源为 6/11），进程由 55 秒 timeout 主动结束，退出码 124 非自然退出。该证据只验证覆盖和该版本实际 mixin 链，不证明不同世界接续、datapack 新增条目、真实生成量或所有 codec 语义。资源优先级与异常后作用域清理的 focused 测试通过；完整 `verifyFabricTestContract` 和平台切片检查亦通过（普通 94/446、总计 110/506）。该提交完整真实 diff 已依 prompt 复审：未发现已支持的覆盖路径另有具体偏离，但对 Fabric 内部 finalize 的依赖随 API 版本变化存在风险。当前适配限 Mekanism 命名空间的 add_features/none，placed feature tag、其他 modifier 类型/其他命名空间及完整官方 datapack registry 仍未实现；不能将全部 F34 标为闭环。

### F35 — 多输出机器被漏斗首槽阻塞（`b3b87ec3`，已修）

原 `FabricHopperTransfer.suckOneItem` 第一可抽物品不能放入漏斗时立即失败，不尝试后续输出；例如锯木机首输出和漏斗不兼容，第二输出可合并时会饥饿。`224db75d` 事务回滚后继续扫描后续 view，维持每次一物品与 vanilla 插入；真实 FabricItemStorage lookup + SimpleContainer focused 测试覆盖首槽回滚、第二槽成功和全满失败。完整 `verifyFabricTestContract` 通过；首次失败为增加测试后固定方法计数未更新，调整后通过。该测试不是实机矿车验证。完整 diff 已复审。

### F36 — 流体罐和管渲染缓存按可变对象身份积累（`d77c3252`，已修）

端口 `FluidStack` 不重写 equals/hashCode；渲染缓存用普通 map 会按每次同步生成的对象身份永久累积相同流体模型。`d8c86ce4` 将流体罐中心、阀门和机械管缓存键改为不可变 `FluidVariant`（流体+组件，不含数量），保留材质重载清理，并更正原文档误述；编译通过，完整 diff 已复审。未进行客户端长期同步内存/视觉实测。

### F37 — composite 方向面与继承几何（`9bb884a3`，代码已修；实机画面待验）

原合并模型所有 side 返回同一列表，且合并 child 时只取 null 面，造成重复绘制与 cullface 几何缺失。`88abcebf` 按方向收集 child 面、仅 null 返回无剔除面；普通 JVM 分桶测试和完整测试契约通过，完整 diff 已复审。首次把测试放在 Knot server 失败：环境禁止装载 BakedQuad，转普通 JVM 后通过；不构成实际客户端画面验收。独立缺陷：普通子 BlockModel 继承 `FabricCompositeModel` 时 vanilla bake 不调用父类自定义 bake，工厂物品和营养液化器内部 composite 子模型丢几何。`8babd36b` 用客户端 mixin 追踪已解析父链（遇到自身 elements 即停止），解析 composite 子模型并以真实后代为属性与 item override owner 执行其几何烘焙；子模型仍按官方自身 parent/texture 解析。完整 383 行 diff 已复审；隔离的 client Knot 测试实际执行 mixin、检查普通物品和嵌套子模型继承/侧面，以及所有者 AO，服务端 QIO 测试也通过。原临时放在 server Knot 的客户端测试发现 discovery 阶段即拒绝 client 类，已移入独立 client 测试任务；真实客户端 Xvfb 可达标题屏，但没有进入游戏检查实际工厂物品及液化器画面。

### F38 — 方块高亮监听移除（`d77c3252`，代码已恢复，画面待验）

主代理对照 `d77c3252^` 的官方 `onBlockHover` 和当前调用链，确认自定义 Java/JSON 选框、配置器侧面叠色、MekaTool 范围挖掘附加选框均无当前事件入口。修复将原 handler 接至 Fabric `BLOCK_OUTLINE`，保持默认选框替换与 bounding block 主方块重定向；附加目标逐一分发 Fabric outline 事件，再用访问放宽的 vanilla voxel 轮廓方法绘制，递归保护只抑制重新计算挖掘范围。Fabric 事件与 NeoForge 事件机制不同，其他模组无法在 Fabric 事件中取得模拟的 BlockHitResult（仅有附加目标位置/状态）；这一互操作差异以及真实世界的叠色、线框和挖掘范围画面尚未验证。`2fba1d15` 的完整真实 diff 已按移植风格 prompt 复审，未发现该边界内其他已证实偏离；`compileJava`、`verifyPlatformSliceNoNeoForge` 和现有 `fabricClientModelTest` 通过，模型测试不覆盖轮廓事件。大型 BER 一律被 section 裁剪的初判已撤回，因为相关渲染器通过 shouldRenderOffScreen 列入 globalBlockEntities。

### F39 — 能量立方几何烘焙与动态端口（相关客户端模型闭包，代码已修；实机画面待验）

真实 Xvfb 客户端启动发现 90 条 `Unable to bake model ... energy_cube` / `Missing frame`：tier JSON 仅覆盖 texture，原适配器却把 tier 自身而非 parent carrier 送进 geometry parser。子代理另指出物品计算 sideStates 后丢弃，主代理核实；既有静态模型还对每个方向重复整套 quads、忽视 blockstate 旋转，并将默认 inactive 与动态 LED 重叠。`59cd9d3f` 从活动资源的 base 模型继承几何和 texture、应用真实 ModelState，按面分桶；区块侧将现有 `RenderDataBlockEntity` 的 side 状态交给 Fabric 渲染网格，正常与 fullbright 材质分层，避免依赖 BER 距离或空能量时的缺失；物品 renderer 用自己的 sideStates 画动态分层，BER 只保留透明核心。真实 client Knot 用官方 base/basic JSON 实际烘焙，验证北/东旋转、frame 分桶和 active/inactive split；客户端 Xvfb 在修改前重现 90 条错误、修改后相关错误消失且到达标题屏（两次 85–100 秒超时终止属主动限定，非自然退出）。完整 394 行 diff 已复审。**未进入存档或物品界面验证 FRAPI 网格、亮度、方向、材质叠层、侧配置同步及物品画面**；不得把启动或单元测试当作最终视觉验收。

### F40 — 物品与装备渲染模型缺资源重载（`afd1e77b`、`46f62053`，代码已修，画面待验）

当前已纳 `RenderJetpack`、`RenderFreeRunners`、`RenderScubaMask`、`RenderScubaTank`、`RenderAtomicDisassembler`、`RenderFlameThrower` 和 `RenderEnergyCubeItem` 的模型字段只在 `onResourceManagerReload` 初始化，而 Fabric 物品 renderer 直接调用它们，原端口没有客户端重载监听；两个 armored 物品是独立实例。主代理也核实装备侧的 `JetpackArmor`、`FreeRunnerArmor`、`ScubaMaskArmor`、`ScubaTankArmor` 同样只在重载时初始化，却已接入 `FabricGearModels`。因此首次实际渲染时有确定的 null 解引用调用链（能源立方核心仅在渲染核心时触发），不是标题屏通过所能覆盖。`db3c69db` 通过 Fabric 客户端资源重载注册这 15 个真实 owner，Fabric 资源加载器将自定义监听排在 vanilla `EntityModelSet` 之后；该提交完整真实 diff 已按移植风格 prompt 复审，未发现该行为边界内新增偏离。真实 Knot 客户端启动时日志出现 `MEKANISM_FABRIC_GEAR_MODEL_RELOAD_PROOF listeners=15`，标记后主动停止进程；此证据证明所有回调成功执行，不证明物品/装备画面或后续资源包重载。`RenderFluidTankItem` 的重载是官方空方法，无模型需初始化。旧 proof 的部分计数为字面量，不能证明模型初始化成功。

### F41 — 差集和交集配料的相等性忽略谓词（`347e5803`，已修）

`ItemStackIngredient` 新增 `subtracted` 和 `intersectionChildren` 后，`equals`/`hashCode` 仍只看主 ingredient、count、components 和 compound children；共享同一 base 对象的普通配料、不同差集或不同交集会误判相等，违反公开值对象语义，集合/映射消费者可能合并不同谓词。未发现本次审计范围内直接以该类型作 Map 键的内部调用，不能据此声称机器配方已复现错误。`ad25b1b4` 将两个字段加入相等性和哈希，复用真实 Ingredient 子对象的既有相等性；扩展既有 focused 测试覆盖不同差集/交集、普通与差集的区分以及相等对象哈希一致。`NeoForgeTypedIngredientTest` 和 API slice gate 通过；完整真实 diff 已按 prompt 复审，未发现该局部边界的新增偏离。

### F42 — compat 配方标签非空条件被反转（`011356e0`，已修，第三方实测待验）

64 个 BSWG 配方的官方条件为 `neoforge:not(neoforge:tag_empty)`，但变换把内部 `tag_empty` 直接映射为 `fabric:tags_populated` 并保留外层 not，导致非空/存在标签时被跳过，缺失标签时反而通过；原文档“仅存在空标签差异”错误。固定 NeoForge `TagEmptyCondition` 真实检查 `context.getTag(tag).isEmpty()`；固定 Fabric `tags_populated` 仅检查重载标签 ID 是否存在。修复在 Fabric 同一 tag 加载边界捕获 item tag 实际非空 ID，用 `mekanism:tag_empty` 保留官方条件本身及任意嵌套 not，重载结束按 Fabric 同一边界清理。focused 测试覆盖缺失、存在但空、非空和作用域清理；完整测试契约 111/507 及 API/platform slice gate 通过；实际 remapped JAR 隔离服务端用四个真实 datapack 配方测试空、缺失、非空及非空取反，加载数量由无测试包的 2868 变为 2871，启动 proof 和正常停服成功，未安装真实 BSWG、未验证这些配方的全部实际产物。`d76188c9` 的完整真实 diff 已按 prompt 复审：未发现已支持配方条件路径的其他具体偏离；监听依赖固定 Fabric API 内部标签快照时序，失败重载时 thread-local 与 Fabric 自身类似可保留到下次重载，后续版本升级须复核。

### F43 — 骑乘时 MekaSuit 能量条使用错误左列高度（`d04c73d0`，代码已修，画面待验）

`FabricHudHeights.leftColumnHeight` 将坐骑血条加到左列；固定 NeoForge GUI 的生命值和护甲修改 `leftHeight`，而坐骑血条修改 `rightHeight`，MekaSuit 能量条使用的是前者。因此骑乘具有血量的实体时原移植计算值偏高 10 像素。`c714b385` 移除此项，保留左列生命和护甲计算；已读完整真实 diff，按 prompt 对照固定 GUI 复审，未发现该限定计算范围新增偏离。编译和平台 slice gate 通过；尚未用 mcdebug 进入真实世界检查坐骑、MekaSuit 条和模式文字叠层，原模式文字 8px 共享高度差异仍未闭合。

### F44 — 滚轮 hook 上下文与雾近裁剪距离（`1d44ca1f`，代码已修，实机交互待验）

固定 NeoForge `MouseHandler.onScroll` 在有效窗口、无 overlay、无 screen、有玩家且原版滚动累计达到整数后才派发游戏内 `MouseScrollingEvent`，传入离散滚轮设置及灵敏度处理后的 Y 值。原 Fabric mixin 在方法 HEAD 使用原始值：打开界面并按住潜行时可能错误切换手持工具模式并吞掉界面滚动，且灵敏度/分数累计行为不同。修复将注入点移至同一原版游戏内分支的 spectator 判断前，并按相同选项计算事件 Y 值。另一个独立错误是雾 mixin 仅在远裁剪距离变化时才设置近裁剪距离；上游启用模块时无条件设置 -8，即使远距离变换结果恰巧相等。修复独立更新两者。`be0b7242` 完整真实 diff 已按移植风格 prompt 复审，未发现该边界内新增偏离。真实客户端 Xvfb 标题屏模型 smoke 15/15，说明 mixin 可应用并到达启动，不证明世界内滚轮、屏幕、灵敏度或雾画面；尚需 mcdebug 实测。

### F45 — Quad 重编码丢失或错放顶点属性（`601da1af`，代码已修，实机画面待验）

`Quad(BakedQuad)` 使用 vanilla `putBulkData` 默认重载，它丢弃原始颜色、光值和法线并以调用参数及 quad 方向重建；`Quad.bake()` 又将 overlay 写入 BLOCK 格式实际的光位，将 light 写入法线位，颜色的整数高低位也与 native 内存中的 RGBA 字节顺序不符。此代码被能量方块 `FaceData.transform`、Robit、MekaSuit 和 transmitter 的模型变换路径调用。Fabric 当前部分能量灯使用显式 emissive 材质，不能仅据此断言每种画面都错误；但可确认通用 quad 的颜色、光和法线往返不保真。修复按 native byte order 的 BLOCK 顶点格式读取和写回 RGBA、光与法线；未设置法线的 vanilla 面回退至 face 方向，BLOCK 没有 overlay 字段。`30fd10f3` 的完整真实 diff 已按移植风格 prompt 复审，未发现该局部编码边界新增偏离。两个隔离客户端 Knot 测试以非白色/非零光及法线验证往返和 fullbright 只改变光位，platform slice gate 通过；尚未实测实际模型画面。消费者是否采纳逐顶点光值是独立问题，见 F46。

### F46 — MekaSuit 逐面全亮光值在 vanilla 绘制中被忽略（`601da1af` 后续装备模型路径，代码已修，画面待验）

`MekaSuitArmor.getQuads` 对 LED 四边形施加 `QuadTransformation.fullbright`，但 `putQuads` 使用 vanilla `VertexConsumer.putBulkData` 简化重载：固定 Fabric Minecraft 实现将同一个调用方 `light` 传给全部顶点，不读取 `BakedQuad` 的光位。因此单独修正 F45 的打包仍不能使这条实际装备渲染路径尊重 LED 自发光。对照固定 NeoForge `IVertexConsumerExtension.applyBakedLighting`：逐顶点取 baked 光和环境光各通道最大值，非零 baked normal 经 pose 法线变换。修复在既有装备 renderer 执行此合并，仍按官方传入着色、overlay，未将整个装备强制全亮。复审 `132da13a` 发现其对非均匀变换下的 baked normal 调用 `Pose.transformNormal` 会多做归一化，不完全符合固定 NeoForge extension 的直接法线矩阵乘法；`2f352450` 修正按 baked normal 是否非零区分两个路径，并复用 scratch buffer 而非每面重新分配 Quad/顶点；其完整 diff 已按移植风格 prompt 复审，未发现此限定边界新的偏离。隔离客户端 Knot focused 测试调用真实 `MekaSuitArmor.putQuads`，验证不同顶点环境/baked 光合并和非均匀缩放后的 baked normal；首次 fixture 未 bootstrap 原文报 `Not bootstrapped ... game_event`，补 vanilla bootstrap 后通过。实际世界中装备画面、性能及资源重载未验证。

### F47 — 普通鞘翅被额外绘制一次（`254afa6b`，代码已修，画面待验）

`FabricArmorLayerRegistration` 是在已有 vanilla `ElytraLayer` 的渲染器上**追加** `MekanismElytraLayer`，而端口对非 HDPE 物品调用 `super.render`，因此普通 vanilla 鞘翅先后由原层和附加层各绘一次。官方附加层的 `shouldRender` 只接受 HDPE；原提交审查中声称普通物品走 super 是正确行为，忽略了原层仍存在。修复移除非 HDPE 分支，让 vanilla 层独自处理普通鞘翅，HDPE 绘制保持原路径。`cb083cba` 的完整真实 diff 已按移植风格 prompt 复审，未发现该限定行为边界新增偏离；`compileJava` 和 platform slice gate 通过。未在真实客户端观察附魔 glint、皮肤/披风及 HDPE 画面。

### F48 — Robit 实体、物品和皮肤预览的模型加载及选择（`3cedf196`、`95b025ae`，核心代码已修；运行验证待完成）

官方 `RobitItemOverrideList` 从当前 registry 取得 `RobitSkin.customModel()`，指定时查询该模型并解析其 item overrides；Fabric `FabricRobitItemRenderer.modelFor` 不查询皮肤，仅看 resource key 的路径是否恰为 `allay`，其他一律用默认模型。数据包自定义皮肤的公共 `custom_model` 因此对物品无效，名字为 `allay` 但命名空间不同或皮肤已回退时也会误选 allay 模型。当前实体渲染与皮肤选择 GUI 已经走 `MekanismModelCache.getRobitSkin`，但该 owner 也有按路径特判 fallback 的偏差；custom model 是否被模型加载器作为 top-level 注册、物品 builtin 抑制及其覆盖模型语义尚需验证，不能只换一行 getter 就宣称闭环。标题屏 proof 不证明 Robit 物品或实体实际绘制。

进一步对照固定 Fabric API `BakedModelManagerMixin` 与 vanilla 顶层物品加载：`getModel(ResourceLocation)` 只查 `Context.addModels` 注册的 resource-model key，不查普通 inventory variant；当前 `item/robit` 与 `item/robit_allay` 都未被 `addModels`，因此实体和 GUI 的 `getRobitSkin` 实际退回 missing model，物品 renderer 亦查不到模型。另有更早的入口缺口：`FabricMekanismModelPlugin` 将这两个模型解析为非 `BlockModel` 的 `FabricRobitUnbakedModel`，而 `FabricItemRendererRegistration` 的抑制包装仅接受 `BlockModel`；其 `StaticQuads.isCustomRenderer()` 返回 false，所以普通 Robit 物品的 builtin renderer 也不会因该注册而被调用。需同时恢复默认/自定义模型实际烘焙 owner、资源重载和 inventory builtin 接线；官方 `BaseModelCache.registerJSONAndBake` 可按 registry 中的任意 custom_model 动态烘焙，不能只硬编码 allay 或扫描自身命名空间代替。修复 `e854c16a` 将基础模型作为额外 resource model 烘焙、在真实模型缓存持有当次 Fabric baker，并在读取 registry 的任意 custom_model 时按需烘焙和缓存，重载时丢弃旧 baker；inventory 顶层变体单独包装为 builtin，资源模型不再被空 quad 包装。资源准备阶段也识别其他命名空间的 `mekanism:robit` loader，恢复模型显示变换继承及未映射以外纹理引用；物品 skin 从 registry 选择而不按路径猜测。已依审查 prompt 重读该提交完整 541 行 diff，对照固定官方动态烘焙和 Fabric 顶层/资源模型调用链，发现如下尚未闭合的行为边界而非宣称全面等价：当时 builtin 绘制重新以 seed 0、无 entity 解析模型，可能与外层 override 不同。后续 `32a1b56e` 在 vanilla `ItemRenderer` 调用 builtin 的唯一位置对 Robit 包装模型取出已选几何，保留原 override 的 world/entity/seed 决定，不影响其他物品；独立直接调用 builtin 的 fallback 仍会重新解析。已按 prompt 阅读该提交完整 127 行 diff，对照固定 Fabric `ItemRenderer.render` 的模型变换与 builtin 调用顺序，未发现该限定路径新增偏离。`compileJava`/切片 gate 及真实客户端启动 smoke 19/19 通过（客户端 timeout 124）；启动未执行 Robit 绘制，故 hook 实际帧、选中纹理的 world 上下文、附魔 glint 及其他模组 mixin 互操作仍未证明。动态 custom_model 的真实 Fabric baker 和附加资源包、重载时序未实测，非默认纹理跨 robit atlas 与官方同样存在局限。

编译和三项 slice gate 通过；隔离 Knot 测试以真实两份 Robit JSON、合成 sprite 和测试 baker 验证基础几何非空、allay 继承显示变换、不同命名空间 custom id 的模型选择/缓存及清空后的重烘焙。真实客户端 smoke 18/18，日志证明额外基础模型烘出 66 面且 inventory wrapper 已安装；脚本在标题后主动终止客户端（exit 1），未实际调用动态皮肤选择或绘制。实体、手持、GUI 皮肤预览、重载和第三方资源包仍需 mcdebug/实际客户端验证，不以启动 proof 宣告 F48 完全闭环。

### F49 — QIO 红石适配器未按状态替换完整模型（`a634adff`，候选修复，画面与 FRAPI 连接待验）

当前 `FabricDataBasedUnbakedModel` 固定返回 unlit chunk 模型，emitting 时 `RenderQIORedstoneAdapter` 再绘制 lit 全模型；两套资源的非 torch 几何在同一位置重合，不能视为官方按 model data 选择其中一个模型。全局 lit 模型 map 还只按资源 ID 保存：同一模型按六种 blockstate 方向多次烘焙，后一次覆盖之前结果，BER 对所有朝向都取同一结果；只绘制 null 方向 quads，遗漏有 cullface 的底面，并忽略 tinted 面的 block color。JSON 的 `neoforge_data` 指定 LED 和 lit torch 全亮，vanilla 解析未证明保留。候选改为每个方块态 bake 同时保存两个模型，tile 显式提供 Fabric render data，FRAPI 网格按 Boolean 选择完整模型、逐面分桶并以官方频率颜色注册 adapter block tint；移除多余的全局 lit map 与叠绘 BER。对原 JSON 的元素 `neoforge_data` 重新按同一 state 烘焙对应面，完整亮度映射 emissive、部分亮度写 lightmap。隔离 client Knot 测试以原始 unlit/lit 资源验证两种状态、上下旋转、剔除面和灯 metadata 确实参与烘焙；编译及平台 gate 通过，真实客户端标题屏模型 smoke 15/15。首次编译因误用 block registry 的 `getBlock()` 报找不到符号，改成 `.get()` 后通过。`45bb7c0d` 的完整 554 行真实 diff 已按移植风格 prompt 复审；确认需明确保留以下偏离，未将其记为完全闭环。`2b9d0dca` 的完整 diff 已复审：追加测试核实所有官方 emitting 光照元数据生成的四边形确实匹配对应烘焙 quads 且为全亮，tile 可空标注也与实际返回值一致；未发现该局部改动的新增偏离。测试未执行实际 FRAPI 网格、色调或运行中状态切换，故不能证明画面；逐 face/inherited `neoforge_data`、几何完全相同但 metadata 不同的第三方资源包仍未覆盖，需继续复核与 mcdebug 实测，不能记全面等价。

### F50 — 声音 tick 守卫与官方相反（`a64dca8b`，代码已修，声音待验）

`1d44ca1f^` 的官方 tick 在有效世界/玩家时每次调用 `restartSounds()`，而 Geiger 新建有 `tickingNormally` 守卫；后续移植把 restart 放进该守卫，同时去掉 Geiger 的守卫，故暂停/非正常 tick 时的配置重启及 Geiger 建立时机发生变化。修复在现有 tick handler 恢复原守卫与位置；`132da13a` 的完整 diff 已按移植风格 prompt 复审，该声音边界未发现新增偏离。`compileJava` 和平台 slice gate 通过，未进行真实音频验证。

### 并行初审待主代理核验

尚待主代理核验：`1af82201` 官方历史 registry alias 是否影响固定官方存档；`38b8b1af` 创造标签内搜索；`2529dca8` 纳入的 11 个既有 mixin 调用链；以及 F29 尚未复核的非 GUI 网络包边界。`a1f1c46f` 发现 shift 合成 bin 可能丢额外物品，但只读子代理对照 NeoForge `ResultSlot` 发现官方在同一点触发回调，属于上游共享问题，暂不认定为移植偏差；也未实机验证。不能将只读初审自动计为全部关闭。

## 恢复位置

固定范围的 101 项均已逐项读取并登记至末项 `a02d7298`。已读不等于所有疑点已关闭：继续复核 F8/F11/F34/F48/F49 及“并行初审待主代理核验”中尚未关闭的真实调用链；F48/F49/F57/F58 的实际画面与平台交互按各节限定验证，不把启动 proof 当端到端证据。完成必要缺陷修复和审查收尾后，才可恢复原持续移植目标。

此前检查点的开放事项（状态以各发现小节及上方记录为准）：F8 的 QIO 真实 owner 插入事务闭包及 F9 的 sided inventory 场景；`a147116b` 已读完整 2180 行 diff；`12ebbcf6` 和 `76f790ce` 已读完整 diff，F14 已修，F15 附魔适用/手持/选槽及电弓虚拟 Flame 已接线但部分实机待验；`1d4c4027` 已读完整 diff 并修 F17 普通水下路径；`7785a9c1` 已读完整 diff，F19/F20 已修，不要求旧开发版握手兼容，双进程实体同步仍待实测；`4e5a4232` 已读完整 diff，F21 旧票据迁移已按用户决策退出范围。子代理已并行读完 `b6e5c54d`、`509afc1b`、`1af82201`、`2529dca8`、`38b8b1af`、`a1f1c46f` 的真实 diff；部分问题已由主代理复核修复，剩余候选见上，不把初审直接计为全部关闭。`53fda9e5` 至 `ffbcb655` 的相邻提交已由子代理读完真实 diff，F29 已部分修复，F30–F33 已修且保留各自验证边界；`5ee61549`、`519e1132`、`5c0fcb1f`、`257b9d05`、`b3b87ec3`、`7da81542`、`9bb884a3`、`1e08c1e5` 已读完整真实 diff；`d77c3252` 已补读完整切片重排及文档，五个后续 ISBER 提交亦已读完整 diff。F34 的 datapack 生物群系修复仍待完成；F37 composite 父链代码已修、真实画面待验，F39 能量立方相关代码已修但 FRAPI 及物品画面待实机验证。`dba8824e` 初版 `isCorrectToolForDrops` 对移除/自定义工具组件的兜底曾过宽，当前 `ItemAtomicDisassembler` 已区分组件缺失与替换组件，需追查后续修正提交并核对 `ItemMekaTool`，不把历史问题直接认定为当前缺陷。同时跟进 F1 空网络外观、F2 快捷键恢复和 F3/F4 的端到端验证。固定范围的后续提交尚未逐个审查，不得引用此前一次性切片扫描或旧的自评代替本轮逐提交审查。
