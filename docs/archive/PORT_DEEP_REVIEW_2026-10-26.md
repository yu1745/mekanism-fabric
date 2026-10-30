# 深度移植审查与串行修复

用户授权：子代理并行只读审查，当前会话独占实现、验证和提交。该授权覆盖本次只读审查的 AGENTS 单会话限制，不授权第二个实现者。用户最新定界：只修复已提交内容，停止扩题；配置、按键和其它未移植模块不在本轮新增实现。并行审查已结束。

审查范围为 `8d0d4ebd..b626904b` 的平台适配及其立即调用方，对照官方 `4646f509` 与既有 Fabric owner。实现基线包含用户确认已结束的另一会话提交 `ffbcb655`（GUI）；保留其代码，不回退。非目标：重做客户端/世界生成整个模块、外部兼容全覆盖、部署/push。

## 已核实与修复路线

- ChunkLoaderTickets：独立 `TicketType<BlockPos>` + vanilla region distance 2，按 block owner 隔离，不碰 vanilla `/forceload`。启动校验重建所有有效票据，快照遍历防重入，删除记录持久化；真实 component 的 forceTicks 决策通过最小 ServerChunkCache tick gate 接回。旧实现已经写入的全局 forced 标记无法区分与手动 `/forceload` 重叠，禁止自动清除；记录迁移限制。
- 数字采矿机/纸箱：恢复 owner 模拟和 Fabric `PlayerBlockBreakEvents.BEFORE` 真实否决 probe，并检查 Mekanism 自身 block security。只 probe，不虚报 AFTER。
- Robit：在 vanilla actuallyHurt 的未 clamp 健康写入处取得实际减伤后伤害并扣能/维持满血；删除 hurt 返回后才回血的错误覆盖，保留 vanilla 减伤与事件。
- 生命周期：频率与辐射世界 tick 已有 Fabric owner；删除 CommonWorldTickHandler 重复接线，避免每 tick 执行两次。
- 能量网络：与现有化学/流体/热网络复用 FreshNetworkAcceptorCache，避免仅在查询时刷新的 lookup 被上层旧缓存绕过。
- 流体默认值：固定 NeoForge 21.1.200 API source 核实 source conversion=false、explosionResistance=1，流体替换还需 !isSame。不得允许化工流体凭空再生。
- 工具/装备：恢复蜂巢收获、电弓火焰模式；Flight grant 按实体身份与生命周期管理，保留 creative/spectator 权限，撤回时清 flying；伤害适配放在原版 invulnerability/fire-resistance guards 后；工具组件被外部替换/移除时尊重真实组件。
- 客户端按键的缺失与实际 GUI 基线重新对照，能在已有 owner 内恢复的直接修复；未完成的大模块不能通过新空壳声称完成。

## 并行审查记录与实现者核对

三名只读审查者分别检查生命周期/区块、事件/装备、能力/注册/网络；实现者逐项阅读实际 diff、原版/NeoForge 对应入口及直接调用方，再独占修改。追加只读审查覆盖 `ffbcb655` 和本轮 owner/事务修改。所有源码修改和构建均由当前会话完成。

| 严重度 | 来源 | 具体缺陷与最终边界 |
|---|---|---|
| P1 | `4e5a4232` | 全局 `setChunkForced` 互相释放 owner；改独立 region ticket，不修改外部 `/forceload` |
| P2 | 同上 | 清空 owner 后取消 dirty；改为保存空结果，启动重放全部有效票据，而非只加差集 |
| P2 | 同上 | 丢失 `forceTicks`；恢复原版 tickChunks 两个子条件上的最小额外门控，纠正旧注释的 non-ticking 概念 |
| P1 | `5138fd15` | Robit 在 hurt 返回后回血，死亡分支已经发生、耗能被 1HP 截断；改 vanilla 未 clamp 的伤害写入边界 |
| P1 | 同上 | 采矿机恒定允许、纸箱装箱/拆箱删除 veto；改真实 Fabric BEFORE probe + Mekanism 安全检查，假玩家 finally 清理 |
| P2 | `509afc1b` | Frequency/radiation 世界 tick 重复注册；保留既有 Fabric owner，删除重复世界 load 接线 |
| P2 | `5d2c9182` | 能量下层改逐查询刷新，上层网络仍保留旧 endpoint；复用现有 FreshNetworkAcceptorCache |
| P1/P2 | `1af82201` | 化工流体 source conversion=true、抗性100、替换遗漏同族检查；恢复官方 false/1/!isSame |
| P2 | `1d4c4027` | 剪切模块删掉蜂巢分支；直接调用 access-widened 原版 harvest，保留实体能量剪切路径 |
| P2 | `76f790ce` | 电弓删除 virtual Flame 后仍收费而不着火；在实际 projectile owner 传入带 live Flame 的 detached bow copy，不改玩家原栈 |
| P2 | gear/catalog batch | 原子分解机/MekaTool 对已移除或替换 TOOL 仍强加 catch-all；仅默认 component 应用平台适配 |
| P2 | `1af82201` | Flight 撤权破坏创造/旁观、遗留 flying 与 UUID 生命周期；实体身份管理、撤两项权限、保存排除临时授权、服务端线程清理 |
| P2 | `2529dca8` | 伤害吸收在原版拒绝 guards 前收费；移到 guards 后，双注入以 invocation-local Share 持有同一个结果，不依赖 ThreadLocal 缓存与注入顺序 |
| P2 | `509afc1b` | chunk gen version 从未写入、删除版本丢失升级依据；serializer 恢复原 root `mek_world_gen_version`，transient attachment 只承载实际 chunk 的 pending version |
| P1/P2 | recent energy compat | 物品 lookup 用 withConstant，成功交换却丢弃替换栈；充电板/模块回到既有 ItemEnergyContainer，由实际槽位安装结果并守原速率；裸栈仅保留 registered Mekanism strict access，外来物品使用 context overload |
| P1 | `ffbcb655` 暴露调用方 | 侧面配置/升级/红石 GUI 发未注册 PacketGuiInteract；补 C2S codec+receiver，实际计数26 |
| P2 | `ffbcb655` | allowColors 粘贴误用会删 § 的原版 filter；恢复并重命名原专用 filterColoredText |
| P2 | vanilla/NeoForge holder 边界 | vanilla ItemStack.is(Holder) 比引用，NeoForge 比 value；修正已纳入的 Flight、呼吸模块、HDPE 修理与模块 GUI 的直接 item 比较，避免 deferred wrapper 恒不相等 |
| P1/P2 | 官方 Module + Fabric registry 接线 | NeoForge holderByNameCodec 自动 getDelegate，vanilla 只接受 Holder.Reference；Module 在真实 registry value identity 上取得 canonical holder，修复装备保存与模块 packet 的共同前置 |
| P2 | 早期 `dc9c5687a` | 数据映射服务只认 reloadable access，实际 player.registryAccess 使用 base/world access；同一服务器线程内接受两种真实 view，并校验 damage registry exact identity，仍拒绝 foreign owner |

流体默认值的额外证据：精确 [NeoForge 21.1.200 sources](https://maven.neoforged.net/releases/net/neoforged/neoforge/21.1.200/neoforge-21.1.200-sources.jar)，SHA-256 `7b31b7f4b817c90b8aa05ade830d761223dff07967aab49ab1f197d51c4d1420`，`BaseFlowingFluid` 的 source delegation/replacement 与 Properties 默认值、`FluidType.Properties.canConvertToSource=false`。只参考行为，未复制该项目代码。其余 Minecraft 边界以固定本地只读参考和实际打包运行核对。

追加审查纠正了实现中的两个生命周期细节：DISCONNECT 可在 Netty 线程发布，故只排队到 server executor；PlayerList 在 disconnect 前 saveAll，故必须过滤保存的临时飞行权限，不能只在断线后清理。测试 fixture 的 remapper 也显式包含 main 输出，保证继承自 Minecraft 的 mod 类字段/方法正确 remap。

## 验证

- 最终 `compileJava --rerun assemble` 与三个 slice gate：PASS（26s）。slice 保持 api262/common1389，仅精确加入四个必要平台 mixin，platform121→125。
- 现有 focused energy tests：5 suites / 9 cases，0 failures；现有 ComponentItemContext + ConcreteInventorySlot：2 suites / 7 cases，0 failures。未新增 JUnit test matrix，未运行全量测试或 clean release。
- 独立 `portReviewFixture` 位于生产 source set 外，`remapPortReviewFixtureJar` 显式带 main 输出作为 remap classpath；主 JAR 未包含 fixture，四个新 mixin 各恰好一份。
- 真实打包 risk gate：8/8 groups PASS，覆盖 lethal-size Robit damage、原版火抗拒绝不收费/连续吸收各收费、owner veto、充电板与 native item replacement 能量守恒、默认/移除/替换 TOOL 与电弓 Flame、飞行撤权/创造/保存、原 root chunk version、region 多 owner 与 /forceload 隔离/remote forceTicks/空记录持久化，以及 brine 非无限源与原版水对照。
- 原有真实机器/传输 smoke：18/18 PASS，使用最新 remapped 主 JAR；C2S 注册计数26。没有宣称真实客户端点击全部 GUI 已验证。

复现：先 `./gradlew -p fabric remapJar remapPortReviewFixtureJar`，再 `scripts/fabric-port-review-smoke.sh` 与 `scripts/fabric-machine-smoke.sh`。前者有明确 fixture-only GearConfig 前提，详见下节。安装器 metadata 不可达时，可用 `FABRIC_SMOKE_RUNTIME_CACHE_DIR` 指向已有同版本开发 smoke 的运行目录，仅复制 libraries/versions/.fabric/server 二进制，仍新建 world/mod/config；不复用世界或生产配置。修正旧 machine script 在 dist 不存在时因 pipefail 静默退出的 artifact 选择。

限制：未新增真实客户端交互、全 JVM 重启票据恢复或第三方保护模组兼容矩阵；这些不能由启动、静态审查或现有 gate 推导为通过。蜂巢调用、重复 tick 删除和动态 acceptor 刷新另有直接源码/owner 核对，未冒充各自独立运行场景。旧错误实现已写入的全局 forced 标记/飞行权限不自动猜测来源迁移。

## 检查点与新边界

生产全量 `compileJava --rerun` 与三个 slice gate 已通过，`remapJar` 和独立 review fixture 已构建。首次打包探针确认 Robit 不再因十点伤害进入死亡，随后在装备验证发现另一个真实主干阻断：`MekanismItems` 的 MekaSuit/电弓等 provider 要求 `MekanismConfig.gear.isLoaded()`，但当前 bootstrap 只加载 startup/common 与 world general fragment；完整 GearConfig 为 SERVER spec，尚无生产加载/停止/客户端同步 owner。因此 `StorageUtils.getEnergyContainer` 对这些物品返回 null。

这不是应当在测试中绕过的 guard，也不能通过把 SERVER gear.toml 放到全局 config 目录冒充完成。必要调用链为：真实 server-config lifetime + world path → canonical GearConfig → item provider guard → equipment energy + 客户端 tooltip/容量/模块行为；远端客户端还需与同一服务器配置同步、disconnect reset，集成服需隔离 owner。完整配置闭包明显超出本轮独立缺陷修复，待用户裁定是否本轮纳入。review fixture 可明确加载真实 GearConfig 到一次性测试 serverconfig，以验证修复边界，但必须报告此前提，不能声称当前生产装备已可用。继续完成其余已确定修复。

用户裁定：先完成当前缺陷修复，完整配置闭包单列；本轮不扩大生产配置加载/同步范围。

按键保持客户端闭包待办：MekanismKeyHandler → ClientTickHandler/MekanismClient → ASCEND/BOOST/gear state 与 HUD 设置，不能仅补几次 send 就声称完整。`ffbcb655` 只注册三个机器 screen，并未纳入完整客户端 tick 或完整 client.toml owner；本轮保留该边界。尚未编译的 CommonPlayerTracker/MekanismElytraLayer 也仍需在其原闭包纳入时应用相同的 deferred-holder item 比较规则，未为它们扩 slice。

## 任务边界与恢复

本轮已完成用户要求的只读并行审查与单实现者修复，不继续寻找新模块。后续配置闭包按用户裁定单列，按键留在原客户端闭包。审查 prompt 位于 `PORT_COMMIT_REVIEW_PROMPT.md`，按用户要求保持简短。

已评估上下文切换：修复已收口，下一配置任务边界独立；本轮以仓库文档、代码和提交恢复即可，不在最终回报前机械滚窗，也不因此自动启动下一任务。

最终证据：`/tmp/mekanism-port-review-final-build.log`；`/tmp/mekanism-port-review-runtime-final.log`（8/8，实际目录 `/tmp/mekanism-port-review.YxdUYe`）；`/tmp/mekanism-port-review-machine-final.log`（18/18，实际目录 `/tmp/mekanism-machine-smoke.YgGhDy`）。本轮最终整编后的 remapped JAR 已重新跑这两项，测试 JAR SHA-256 `3b39fcce4239441d3e1f3fdee2cc5e7fcee95b6dbc686a3f40ca1b55a8cd655a`。最初试跑中的 fixture remap classpath、spawn immunity 与异步 ticking-future 前提已修正，未把这些测试配置错误记为生产缺陷。
