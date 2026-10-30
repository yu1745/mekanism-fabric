# M-platform — 核心平台入口闭包

## 现状

ROADMAP P3 W-15–22；运行基线 `4e6b6577`。P0/P1/P2 已完成，当前工作区无生产 WIP。W-15 已核对实际 owner；W-16/17 命令入口尚未接通，下一批按完整命令树接入。

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
