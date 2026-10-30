# M-platform — 核心平台入口闭包

## 现状

ROADMAP P3 W-15–22；运行基线 `4e6b6577`。P0/P1/P2 已完成，当前工作区无生产 WIP。W-15 已核对实际 owner；W-16/17 命令树与 builders 已接入并有真实运行证据。W-18 已补齐登录成就入口并实机触发；下一项 W-19 conditions。

## 下一步

W-16/17 最小完整边界：原始 `command/{CommandMek,ChunkCommand,RadiationCommand,ForceRetrogenCommand}`、`command/builders/{BuildCommand,Builders,StructureBuilder}` 七个源码，依次审计纳入 common slice；已有 MekanismPermissions 的 vanilla fallback level 谓词复用。bootstrap 使用 Fabric CommandRegistrationCallback 注册原命令树，按官方 commonSetup 顺序注册 boiler/matrix/tank/evaporation/sps 五个真实 builder。BuildCommand 的 root Mekanism.MODID 依赖改为现有固定 namespace，保留原 build/remove 算法。命令 tpStack/chunk watchers 在 server stop 清理，避免跨世界 JVM 生命周期残留。

Chunk watch 的 LOAD/UNLOAD 使用 Fabric ServerChunkEvents；ticket-level 通知须接真实 ChunkMap.updateChunkScheduling 的有效调度分支末尾（Fabric 参考源 367 起；NeoForge 参考源 397 起、fireChunkTicketLevelUpdated 紧邻分支 return）。不能在提前返回的 unloaded→unloaded 分支伪造事件。真实 owner 接收 ServerLevel、chunkPos、old/new level，并保留原玩家广播行为。该入口仅服务 Mekanism command owner，无通用事件总线。完成后编译，并用实际 `/mek radiation`、`/mek chunk` 与权限等级验收；builders 不静默遗漏。

其后 W-18 成就、W-19 conditions、W-20 生命周期、W-21 payload 完整核对、W-22 门禁按 ROADMAP 执行。

## 未决项

当前没有需要用户裁决的扩边界事项；命令七源码和 ticket 通知属于已授权 W-16/17 闭包，下一轮直接实现。第三方权限插件 node override 无 Fabric 默认等价，沿用已经提交的 MekanismPermissions 平台差异，不另建兼容框架。

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

编译 compileJava PASS，实际集成服启动成功并注入现有 ticket mixin。通过 mcdebug 执行 `mek radiation get` 得基线 99.999 nSv/h（原命令低于阈值返回 0，不能把 tool 的 success=false 误判为未执行）；`mek chunk watch 0 0 p3probe` / unwatch 均返回 1。真实 ServerPlayer command source 经 Commands.performPrefixedCommand 执行 `mek build evaporation empty 32 100 0`，实际生成 thermal_evaporation_block。mcdebug 的 execute-as 调用未成功，改用真实玩家 CommandSourceStack 调用相同命令解析器后成功；未绕过原 build 业务。权限谓词对 level0=false、level4=true。原始运行结果在 [p3-command](evidence/p3-command/chunk-watch.json)（同目录含 radiation、unwatch、builder 方块与权限结果）。GUI error 无记录；未声称所有 builders 结构成型验收完成，后续多方块里程碑仍待做。

自有客户端正常 stop，完整隔离世界从本轮前备份恢复且逐文件 SHA-256 相等；清理临时 mod、wrapper 链接和 Xvfb。没有新增测试。最终 assemble PASS（12 秒），含三份 slice gates。W-18–22 及 P3 全阶段门禁尚未完成。

提交后风格审查：已读本次真实 diff，并对照原命令树／builders 与固定 ChunkMap 事件位置。保留原权限、消息、返回值、build/remove 算法；原始 builders/StructureBuilder/RadiationCommand 仅纳入而未重写。所有 Fabric callback 在 bootstrap，ticket 通知复用实际 owner，无独立事件框架或占位命令。未发现需要修复的生产偏离；全 builders 成型与 P3 全门禁不在本次通过声明内。


2026-10-30 W-18：原始 MekanismCriteriaTriggers 的 12 种 criterion 已由 bootstrap 注册到 TRIGGER_TYPES，生成 advancement JSON 已打包；缺口是 CommonPlayerTracker 的 logged_in 没有 Fabric consumer。最小修复在现有 MekanismUsernameLifecycle JOIN owner 记录用户名后调用原 LOGGED_IN.trigger(handler.player)，不再注册第二套登录 owner。原始 MekanismAdvancement record 与完整 MekanismAdvancements 常量目录纳入 slice（1509→1511），仅把 root Mekanism.rl 改为等价固定 namespace 的 ResourceLocation 构造；无 ID 或 title/parent 变更。

最终 assemble PASS（21 秒，含三份 slice gates）；真实隔离集成服连接后，实际玩家 advancement JSON 中 `mekanism:root` 的 automatic criterion 完成、done=true。通过真实 give 命令把 basic_tier_installer 放进同一玩家库存后，`mekanism:installer` 的 inventory_changed criterion 也完成、done=true；没有执行 advancement grant。原始 [玩家进度](evidence/p3-advancement/progress.json) 及 UUID／give 结果同目录保存。首个自定义 logged_in criterion 与第二个原版 inventory 条件均证明资源加载／注册／实际 consumer 联通；未以单个成就触发扩大为所有触发器均真机覆盖。

自有会话正常 stopped，完整隔离世界恢复至启动前备份且所有文件 SHA-256 相等，临时 debug dependencies、wrapper 链接、Xvfb 清理。无新测试。CommonPlayerTracker 的其余 logout/dimension/respawn/interaction 行为仍在 W-20 核对，未随本次宣布完成。P3 阶段全门禁仍待 W-22。


2026-10-30 W-19：实际源码为 MekanismRecipeConditions 注册两个自定义 condition（不是三种自定义；第三类平台依赖是 tag_empty）。现有 mod_version_loaded / tag_empty 已有 Fabric owner，发现 condition_exists 未注册。本次新增最小 FabricConditionExistsCondition，在 bootstrap 注册同一 ID；Fabric 的 discriminator 占用 `condition`，因此转换将官方嵌套 `condition` 字段映射为 `value`。子条件 decode 出错／缺失时 Optional.empty，test=false，保持官方 orElse fallback。compat 转换支持递归 condition_exists，未知 optional 子条件／后代转 false，不影响顶层未映射条件的失败门禁。新增一个审计过的 platform source（202→203），无新测试。

真实隔离数据包加载四个临时配方：已知且满足的 fabric:all_mods_loaded 子条件使配方加载，recipe give 返回 1；unknown condition、missing child、malformed child 对应配方均在加载时过滤，查询报 Unknown recipe。没有以显式 grant 替代 recipe load。[实际结果](evidence/p3-condition/load-results.json)。运行后正常 stopped，完整世界从备份恢复，临时 debug mod／数据包／wrapper／Xvfb 清理；首轮 assemble PASS（20 秒），最终 assemble 在提交前复核。

W-19 尚有版本比较语义待收口：FabricModVersionLoadedCondition 仍使用 Fabric Version，官方为 Maven ComparableVersion；当前 637 compat 资源的简单版本 floor 已有转换，但不能把这个事实扩称所有输入等价。下一步核对现有 Maven 依赖可用性，并在同一 condition owner 保留官方比较算法，随后继续 W-20。不另建条件框架或删掉官方行为。

本次提交后风格审查：检查已提交 condition adapter、bootstrap、slice 与转换 diff，对照官方 ConditionExistsCondition.test/makeCodec 与固定 Fabric ResourceCondition.CODEC。没有重复 registry 或 fake 条件；false fallback 只作用于 optional 子条件，已有未知顶层条件仍报错。真实资源加载证据覆盖成功及三类失败输入。最终 assemble PASS（4 秒），版本比较差异仍明确待做。
