# M-platform — 核心平台入口闭包

## 现状

ROADMAP P3 W-15–22 已通过阶段验收，运行基线 `4e6b6577`，最终生产提交 `843af935`。命令、builders、登录成就、条件、reload/scanner、交互与 55 个 payload 的实际 owner 已核对，缺口已在相同边界修复。W-22 的 46 个未纳入文件逐一保留理由，不代表整个核心已可发布。

## 下一步

进入 P4 W-23–28，使用真实能量、流体、物品、化学与热量 owner 做端到端和生命周期验收；活文档见 `../milestones/M-transfer.md`。

## 未决项

[未纳入清单](../milestones/evidence/p3-sources/unadmitted-core.csv) 中客户端模型、按键、粒子和装备渲染留 P5/P7；混合 JEI/EMI 的公共 IMekanismAccess/MekanismAccess 仍未纳入，P8 汇总发布限制。第三方集成不在本规划内，不增加占位实现。第三方权限插件 node override 沿用既有 MekanismPermissions 平台差异。

## 验收证据

2026-10-30 W-15 源码映射核对：

| 官方入口行为 | 当前真实 owner | 核对结论 |
|---|---|---|
| RADIATION entity attachment | FabricRadiationAttachments.RADIATION | 保留 mekanism:radiation、baseline initializer、不 copyOnDeath；set baseline 时移除。原 payload 仍是同步 owner |
| RADIATION_LEVEL_DATA / MELTDOWN_DATA | FabricRadiationAttachments | ID radiation_data/meltdown_data、实际 CODEC、世界 attachment；空 mutable 对象保留以确保清空后落盘，不重复分配全局 owner |
| FLAMETHROWER_MODE attachment | EntityFlame 原有 save/read 与 MODE synched data | 实际 flame owner 保存 `neoforge:attachments` 内 `mekanism:flamethrower_mode`，非 COMBAT 才写，invalid/absent 回 COMBAT，保留其它 attachment；不是需要再注册一个未使用 Fabric attachment 的缺口。ItemFlamethrower 的 item mode 另由实际 data component 承接 |
| MekaSuitAbsorption | FabricMekanismDataMaps.MEKASUIT_ABSORPTION | 同 ID/codec，非同步、server session；reference/direct holder 查询转到实际 owning registry |
| Chemical solid/fuel/radioactivity/cooled/heated 五图 | FabricChemicalDataMaps 的五个 canonical 常量 | allTypes=六图；保留 data/network codec 区分、server reload session、失败保留完整旧 epoch及真实客户端同步 |
| IMekanismDataMapTypes 服务 | src/main/resources/META-INF/services 的 FabricChemicalDataMaps | 服务绑定到同一 provider；不把仍含 NeoForge 的旧注册入口类当作运行依赖，也不创建第二份 data-map register |

结论：W-15 的行为已有等价 owner，当前不需要新增生产代码或扩大 slice；旧 MekanismAttachmentTypes/MekanismDataMapTypes 属已被 Fabric composition root 承接的注册入口，W-22 清单应写明理由，不能简单按未纳入文件数视为缺失行为。该结论不替代 M-gear 的真实 flamethrower 使用验收。

既有 `fabricDataMapTest` PASS（5 秒）；现有 EntityFlamePersistenceTest / EntitySynchedDataWireTest 也 PASS（16 秒）；分别覆盖模式存读档／默认清理以及真实 synched-data wire。无新测试、无 reference tree 写入、无生产状态修改。


2026-10-30 W-16/17 实现：审计七个原始命令／builder 文件，其中 ForceRetrogenCommand 已纳入，实际 common slice 新增六个（1503→1509）；未重复加入原有文件。bootstrap 在内容初始化后注册五个官方 builders 和完整 CommandMek 树；原有权限谓词、radiation/retrogen/tp/testrules/build/remove 业务保留。ChunkCommand 由 Fabric ServerChunkEvents 承接 load/unload，ticket 通知复用已有 TransmitterTicketLevelMixin 的真实有效调度分支，未新建重复 mixin。stop 清理 chunk watchers 与 tpStack，保持每次 server 生命周期独立。BuildCommand 使用 vanilla holder.unwrapKey 及固定官方 namespace，保留原 startsWith 选择行为。

编译 compileJava PASS，实际集成服启动成功并注入现有 ticket mixin。通过 mcdebug 执行 `mek radiation get` 得基线 99.999 nSv/h（原命令低于阈值返回 0，不能把 tool 的 success=false 误判为未执行）；`mek chunk watch 0 0 p3probe` / unwatch 均返回 1。真实 ServerPlayer command source 经 Commands.performPrefixedCommand 执行 `mek build evaporation empty 32 100 0`，实际生成 thermal_evaporation_block。mcdebug 的 execute-as 调用未成功，改用真实玩家 CommandSourceStack 调用相同命令解析器后成功；未绕过原 build 业务。权限谓词对 level0=false、level4=true。原始运行结果在 [p3-command](../milestones/evidence/p3-command/chunk-watch.json)（同目录含 radiation、unwatch、builder 方块与权限结果）。GUI error 无记录；未声称所有 builders 结构成型验收完成，后续多方块里程碑仍待做。

自有客户端正常 stop，完整隔离世界从本轮前备份恢复且逐文件 SHA-256 相等；清理临时 mod、wrapper 链接和 Xvfb。没有新增测试。最终 assemble PASS（12 秒），含三份 slice gates。W-18–22 及 P3 全阶段门禁尚未完成。

提交后风格审查：已读本次真实 diff，并对照原命令树／builders 与固定 ChunkMap 事件位置。保留原权限、消息、返回值、build/remove 算法；原始 builders/StructureBuilder/RadiationCommand 仅纳入而未重写。所有 Fabric callback 在 bootstrap，ticket 通知复用实际 owner，无独立事件框架或占位命令。未发现需要修复的生产偏离；全 builders 成型与 P3 全门禁不在本次通过声明内。


2026-10-30 W-18：原始 MekanismCriteriaTriggers 的 12 种 criterion 已由 bootstrap 注册到 TRIGGER_TYPES，生成 advancement JSON 已打包；缺口是 CommonPlayerTracker 的 logged_in 没有 Fabric consumer。最小修复在现有 MekanismUsernameLifecycle JOIN owner 记录用户名后调用原 LOGGED_IN.trigger(handler.player)，不再注册第二套登录 owner。原始 MekanismAdvancement record 与完整 MekanismAdvancements 常量目录纳入 slice（1509→1511），仅把 root Mekanism.rl 改为等价固定 namespace 的 ResourceLocation 构造；无 ID 或 title/parent 变更。

最终 assemble PASS（21 秒，含三份 slice gates）；真实隔离集成服连接后，实际玩家 advancement JSON 中 `mekanism:root` 的 automatic criterion 完成、done=true。通过真实 give 命令把 basic_tier_installer 放进同一玩家库存后，`mekanism:installer` 的 inventory_changed criterion 也完成、done=true；没有执行 advancement grant。原始 [玩家进度](../milestones/evidence/p3-advancement/progress.json) 及 UUID／give 结果同目录保存。首个自定义 logged_in criterion 与第二个原版 inventory 条件均证明资源加载／注册／实际 consumer 联通；未以单个成就触发扩大为所有触发器均真机覆盖。

自有会话正常 stopped，完整隔离世界恢复至启动前备份且所有文件 SHA-256 相等，临时 debug dependencies、wrapper 链接、Xvfb 清理。无新测试。CommonPlayerTracker 的其余 logout/dimension/respawn/interaction 行为仍在 W-20 核对，未随本次宣布完成。P3 阶段全门禁仍待 W-22。


2026-10-30 W-19：实际源码为 MekanismRecipeConditions 注册两个自定义 condition（不是三种自定义；第三类平台依赖是 tag_empty）。现有 mod_version_loaded / tag_empty 已有 Fabric owner，发现 condition_exists 未注册。本次新增最小 FabricConditionExistsCondition，在 bootstrap 注册同一 ID；Fabric 的 discriminator 占用 `condition`，因此转换将官方嵌套 `condition` 字段映射为 `value`。子条件 decode 出错／缺失时 Optional.empty，test=false，保持官方 orElse fallback。compat 转换支持递归 condition_exists，未知 optional 子条件／后代转 false，不影响顶层未映射条件的失败门禁。新增一个审计过的 platform source（202→203），无新测试。

真实隔离数据包加载四个临时配方：已知且满足的 fabric:all_mods_loaded 子条件使配方加载，recipe give 返回 1；unknown condition、missing child、malformed child 对应配方均在加载时过滤，查询报 Unknown recipe。没有以显式 grant 替代 recipe load。[实际结果](../milestones/evidence/p3-condition/load-results.json)。运行后正常 stopped，完整世界从备份恢复，临时 debug mod／数据包／wrapper／Xvfb 清理；首轮 assemble PASS（20 秒），最终 assemble 在提交前复核。

W-19 尚有版本比较语义待收口：FabricModVersionLoadedCondition 仍使用 Fabric Version，官方为 Maven ComparableVersion；当前 637 compat 资源的简单版本 floor 已有转换，但不能把这个事实扩称所有输入等价。该差异随后已在下节收口，继续 W-20。不另建条件框架或删掉官方行为。

本次提交后风格审查：检查已提交 condition adapter、bootstrap、slice 与转换 diff，对照官方 ConditionExistsCondition.test/makeCodec 与固定 Fabric ResourceCondition.CODEC。没有重复 registry 或 fake 条件；false fallback 只作用于 optional 子条件，已有未知顶层条件仍报错。真实资源加载证据覆盖成功及三类失败输入。最终 assemble PASS（4 秒），版本比较差异仍明确待做。


2026-10-30 W-19 版本比较收尾：FabricModVersionLoadedCondition 改用官方同一 Maven ComparableVersion 算法，FabricLoader 仅负责查找实际 mod metadata；比较 minVersion 与实际版本 friendly string，不再用 Fabric Version.parse 或将 Maven 支持的 qualifier 当作解析错误。固定使用 `org.apache.maven:maven-artifact:3.9.9`，implementation/include 均关闭 transitive，仅嵌套实际算法依赖；原库 LICENSE/NOTICE 随 nested JAR 保留。packaged 静态合同同时列出该第三份 nested dependency，不放宽其它完整性断言。

assemble PASS（23 秒，含三份 slice gate）。真实隔离数据包分别以 minecraft 1.21.1 为目标：release `1.21.1` 与 alias `1.21.1-ga` 配方成功加载，service-pack 下限 `1.21.1-sp` 配方被过滤；[原始读数](../milestones/evidence/p3-condition/version-results.json)。这覆盖原 comparator 差异，不测试／重写 Maven 内部算法。此前 condition_exists 四类实际加载证据继续有效；W-19 的两个自定义 condition 和 tag-empty 均有实际 Fabric owner，后续 W-22 记录旧 NeoForge 注册入口被替代的理由。

运行后自有会话正常 stopped，完整世界回退到备份且临时 debug runtime／wrapper／Xvfb 清理，无新测试。最终 JAR 已核对嵌套 ComparableVersion.class 与 LICENSE/NOTICE；静态 packaged 门禁在提交前复核。本批完成 W-19，下一项 W-20。

提交后风格审查：检查已提交 comparator、依赖、packaged 合同与实际读数 diff；与官方 ModVersionLoadedCondition 的 ComparableVersion 顺序和 absent-mod=false 分支一致。原始库直接复用而非复制／重写，嵌套依赖固定且许可证保留。静态 packaged PASS；未发现本次行为边界内的移植风格偏离。


2026-10-30 W-20 进行中：CommonPlayerTracker 的 tracking、换维度、respawn、disconnect 已由 FabricGearEffectsNetworking 接通，logged_in 已在 W-18 修复；rightClick 的外部 wrench/cardboard 语义仍待核对。ReloadListener 的缓存失效已由 FabricRecipeCacheLifecycle 承接；发现 IncompleteRecipeScanner 未纳入，现纳入 common（1511→1512），原扫描和警告算法保留，事件参数改实际 server/player，启动及成功 reload 在实际 cache bind 后扫描，JOIN 仅发送已有警告，stop 清空状态。

真实空-tag crushing 配方探针先暴露网络断连：ItemStackIngredient.NetworkCodec 调用拒绝 Ingredient.EMPTY 的公共 of 工厂，而网络将空 tag 展开成空数组。修复为 network decoder 直接构造保留不完整状态，count 继续正数校验，mode/component 组合校验不变；公共构造工厂规则保留。原官方 stream 是 SizedIngredient.STREAM_CODEC 映射到私有 wrapper 构造，不通过公共工厂。assemble PASS（修复后），同一探针客户端成功进入世界，scanner 实际状态 true：[读数](../milestones/evidence/p3-scanner/start.json)。不通过删除配方或绕过 scanner 获得连接成功。

随后移除临时配方并执行 reload，真实既有机器 tick 崩溃：`Recipe lookup monitor level is not bound to the active recipe manager (epoch 1)`，首个业务帧 RecipeCacheLookupMonitor.validatedBinding:217 → updateAndProcess:85 → TileEntityElectricMachine.onUpdateServer:98。完整 [原始报告](../milestones/evidence/p3-scanner/reload-crash.txt) 与 lifecycle.log 保留。本次不能声明 reload/扫描收尾通过。下一步检查真实 RecipeManager 替换与 Fabric END_DATA_PACK_RELOAD 回调之间的绑定时序，在现有 recipe owner 修复，不放松 foreign-level/manager guard。

游戏进程已确认 terminal（runClient exit 255），隔离世界完整恢复到启动前备份，临时 mod／wrapper／Xvfb 已清理。无新测试；本批是明确失败点的可恢复实现检查点，W-20 未完成，后续须在同一行为闭包修复并验收。

提交后审查：已检查实际 scanner lifecycle 与 wire decoder diff；count/mode/component 限制及列表不可变性保留，未改变公共 factory 或丢弃不完整配方。真实连接成功支持 empty-wire 修复，但 reload 崩溃仍是明确未通过项，当前检查点不作 W-20 完成声明。原始 crash/log 已显式纳入 Git，后续据此修复。


2026-10-30 W-20 reload 修复：固定 Fabric lifecycle 源码显示 END_DATA_PACK_RELOAD 通过 handleAsync 再排入 server executor；vanilla 已在内部 completion 中替换 resources/RecipeManager，此时后续机器 tick 可先看到新 manager，而缓存仍是 epoch1。没有放松 RecipeCacheLookupMonitor 的真实 manager guard。新增最小 PlayerList.reloadResources HEAD mixin，在 vanilla 完成 resources 赋值、updateRegistryTags 后且同步玩家 recipes 前调用现有 FabricRecipeCacheLifecycle.resourcesApplied，立即为实际 manager 发布新 binding 和 epoch。END 只做成功扫描／结果记录；失败发生在准备阶段时不会触发 publication，保留旧 binding，若 vanilla 已实际 apply 则跟随真实 manager 而非回退伪装。

assemble PASS（21 秒，三 slice gates）；platform slice 203→204（真实 publication mixin）。原空 tag 探针客户端正常进入后，scanner=true；移除探针配方并 reload，scanner=false / epoch2，客户端和既有机器继续运行。重新加入配方并第二次 reload，scanner=true / epoch3，客户端保持连接。每次 apply 与 reload 只有同一 epoch，未重复绑定。[实际读数目录](../milestones/evidence/p3-reload/after.json)，同目录含 start/again、epochs、status 和 lifecycle.log。这修复上一节原始 reload crash 的同一复现；扫描错误日志是探针有意制造的诊断结果，不作为意外 crash。

正常停止自有会话并完整恢复世界，备份与恢复后逐文件 SHA-256 一致；临时 runtime/wrapper/Xvfb 清理。保留现有 foreign-level/manager 检查，另跑既有 RecipeCacheLookupMonitorTest，提交前核对结果。无新增测试。W-20 剩余外部 wrench/cardboard 右键语义核对，之后再进 W-21；不提前声明整个 P3 完成。

提交后审查：检查实际 publication mixin、生命周期和证据 diff，并对照 MinecraftServer.reloadResources 内 resources 赋值 → updateRegistryTags → PlayerList.reloadResources 顺序及 Fabric handleAsync 回调。未放松真实 manager/level 校验，原 binding identity／invalidation 机制继续复用；准备失败不发布，已应用的 manager 不回退旧引用。既有 RecipeCacheLookupMonitorTest PASS（8 秒），未发现本次边界内需再修正的偏离。


2026-10-30 W-20 交互收尾：原 CommonPlayerTracker.rightClickEvent 的两个剩余分支现接入同一 ConfiguratorUseMixin：对 CONFIGURATORS tag 且非自家 Configurator 的物品，遇 BlockMekanism/BlockBounding 时强制走 block-use；对其它物品的潜行纸箱交互强制 block-use，并在原物品 useOn 入口阻止纸箱仍存在时的后续 item-use。保留原 else-if 优先级、自家 Configurator 的双手 bypass 逻辑；纸箱成功恢复后提前消费，不误阻止其它方块。无通用 Forge 事件层，无新 slice 或测试。

assemble PASS，实际集成服验证：临时数据包将 vanilla stick 加入 mekanism:configurators；潜行使用 stick 拆卸真实 Crusher，blockConsumed=true、itemConsumed=false、stick count1 保留、Crusher 变 air。真实纸箱物品先捕获地面石头，实际 box 在 `[6,99,0]`；持石头潜行点击该 box 后恢复内部 stone、blockConsumed=true、itemConsumed=false、手中 stone count1 保留。最初按放置目标 `[6,100,0]` 点击空气的调用只是探针位置错误，不计入验收。[wrench](../milestones/evidence/p3-interaction/wrench.json)、[纸箱](../milestones/evidence/p3-interaction/cardboard.json) 与前后方块读数同目录。没有 GUI error。纸箱失败恢复时的 item-use 抑制由原生调用入口检查覆盖，未扩称本次另做了破坏权限矩阵。

W-20 映射闭环：ReloadListener → FabricRecipeCacheLifecycle + native publication mixin；IncompleteRecipeScanner → 启动／成功 reload 扫描、JOIN 警告、stop 清理（同探针两次 reload 已通过）；CommonPlayerTracker login → MekanismUsernameLifecycle，tracking/dimension/respawn/logout → FabricGearEffectsNetworking，rightClick → 现有 ConfiguratorUseMixin。原 alpha warning 代码本就注释禁用，不额外启用。旧三个 NeoForge 入口不照搬，W-22 记替代理由。阶段全门禁仍在 W-22；下一项 W-21 payload 目录核对。

本轮自有会话正常 stopped，完整隔离世界恢复备份且逐文件 SHA-256 一致，临时 runtime／wrapper／Xvfb 清理；工作范围无额外扩展。

提交后风格审查：已读实际 mixin 与证据 diff，对照 CommonPlayerTracker 的外部 wrench first branch／纸箱 else-if 和原生 useItemOn 的 block→item 顺序。保留真实 tryWrench/unbox owner、原始 tag 身份与双手 Configurator 判定，没有更改机器算法或删除交互分支。未发现本次边界内移植风格偏离；W-21/22 尚待完成。

2026-10-30 W-21 完成：以官方基线 `4646f509` 的 PacketHandler/BasePacketHandler 为目录，而非已迁移并删去部分注册语句的当前旧入口。逐项核对 **35 play C2S + 19 play S2C + 1 configuration S2C = 55 payloads**；其中两个 instanced payload 转成 PacketShowModeChange/PacketQIOItemViewerKill，仍为原 show_mode_change/kill_qio ID 与 unit body。所有 53 个原 typed TYPE 的路径字符串与官方相同；原两个 instanced ID 也相同。逐包阶段、ID 与实际 Fabric owner 路径见 [目录](../milestones/evidence/p3-network/official-payload-owners.csv)。临时核对直接读取明确目录与真实注册调用，不新增依赖扫描器或测试。

C2S 主 owner FabricServerPlayNetworking.register 同时注册 codec 与 context.player() 主线程 handler（28 包）；FabricContainerNetworking 对另外 7 包逐一注册 playC2S codec 与 dispatch，保留原 menu/QIO 校验。S2C 的 portal、transmitter、container、tile、gear/player-state、radiation、security owners 均核对实际 codec 和 client receiver/helper 的实现，并核对 bootstrap 的 initialize/initializeClient 调用。没有只凭 import／TYPE 引用当作已接线。核心分派继续复用实际 packet.handle，不出现占位 handler。

配置阶段 PacketBatchSecurityUpdate 在 FabricSecurityNetworking.configurationS2C 注册；FabricSecurityClientNetworking 在 client configuration receiver 应用到当前 connection；SyncAllSecurityData 在实际 server task 构造完整 snapshot，并由真实 configuration handler completeTask。版本握手由 FabricMekanismNetworking 的 exact Version 字符串契约承接，入口不照搬旧 IEventBus。此前 P0/P2 两次 fresh client 已通过该 configuration→play 链路，W-21 没有借目录核对重新声称每个 packet 的所有业务分支均经过真机测试。

既有 PlayerStateNetworkingTest / PortalNetworkingTest PASS（18 秒），未新增测试，未改生产源码。W-21 未发现漏注册包，下一项 W-22：核对非第三方未纳入文件的实际替代／延期理由并执行 P3 阶段门禁；不能把 55 包目录完整等同于整个移植完成。

2026-10-30 W-22 清单与 public service 补齐：固定 `src/main/java` 共 1802 文件；开始核对时 common slice 1512，缺 290，其中明确第三方目录 243，余 47。逐项记录实际替代／后续阶段理由后，发现 EnergyConversionHelper 不是可直接忽略的第三方代码：公共 IEnergyConversionHelper 与原 service descriptor 均未纳入。现将**原样** API 接口和原样 common provider 纳入（API 262→263、common 1512→1513），生产 processResources 复制原 `META-INF/services/mekanism.api.energy.IEnergyConversionHelper`，实际返回原 EnergyUnit.JOULES/FORGE_ENERGY，未引入第二套转换算法。原 service/API/provider 三者在 remapped JAR 中均核对存在且唯一，descriptor 指向原 provider；assemble PASS（21 秒）。客户端 window fixture 未使用该服务，不扩大其资源合同。

当前非第三方过滤后未纳入 **46 个文件**，每个路径及理由见 [精确清单](../milestones/evidence/p3-sources/unadmitted-core.csv)。其中：NeoForge composition/registrar/event 类由已验收的 Fabric owner 替代；client model/key/particle/render 类留 P5/P7 逐行为核对，不把替代类存在写成完成；四个共享 recipe-viewer helper 与 LazyChemicalProvider 的实际调用属于已排除的第三方集成。IMekanismAccess/MekanismAccess 混合暴露 JEI/EMI，creator 功能已由实际 IIngredientCreatorProvider 承接，但原混合公共 API 仍列明未纳入理由，不能以 stub 假装完整。该清单不是发布完成声明，P8 仍须汇总残留与实际行为验收。

本轮补齐 service 前的完整 `./fabric-build.sh` PASS（4m12s，现有全部测试与合同）；service 补齐后的 assemble/slice 门禁 PASS。尚需在最终当前树执行 P3 阶段 build、verifyApiSliceNoNeoForge、machine smoke，才关闭 W-22。没有新增测试或更改参考源码。本轮具体下一步就是该最终门禁与收尾，不重做已确认 owner 扫描。


2026-10-30 W-22 最终门禁（生产树 `843af935`）：`./fabric-build.sh` PASS（4m33s，578 tests，0 failures/errors/skipped）；`./gradlew -p fabric verifyApiSliceNoNeoForge --console=plain` PASS（10 秒）；`scripts/fabric-machine-smoke.sh` exit0，`fabric-machine-smoke: PASSED`。最终 service 补齐后的当前树已通过，前述“门禁待做”为历史检查点。对应日志位于本次会话 `/tmp/mekanism-p3-current-{build,api,machine}.log`；持久门禁摘要见 [结果](../milestones/evidence/p3-sources/final-gates.md)。

阶段提交：W-15 `6928f068`，W-16/17 `f4c760fd`，W-18 `339e4978`，W-19 `797b2b03` / `8cffaec8`，W-20 `fde38c22` / `d511c844` / `de036b6d`，W-21 `504b3dbb`，W-22 `843af935`。新增未决项均在上述清单明确归属后续阶段；未跳过 W 项，无新增测试。P3 归档后进入 P4，不作 P4–P10 完成声明。WIP 历史 squash 仍按 ROADMAP 留给用户决定。
