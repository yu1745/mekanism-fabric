# M-transfer — 传输与网络生命周期

## 现状

ROADMAP P4 W-23–28；基线 `843af935`（P3 最终生产提交）。P0–P3 已通过阶段门禁。W-23 已通过真实 cable 传输与 Team Reborn API 双向转换验证，并修复通电 contents 材质崩溃。既有机器 smoke 验证 cube→cable→chamber 放电与产出，以及部分物品／流体／化学连接；这些证据不替代本阶段的完整边界验收。

## 下一步

W-24：核对 Mechanical Pipe→Fluid Tank 的实际 Fabric fluid owner，在隔离世界读取满载、断开与事务回滚后的数量；复用现有 focused tests，不新增测试。

随后依次执行 W-24 pipe→tank 满载／断开／事务回滚，W-25 transporter+sorter+vanilla hopper，W-26 tube 与 conductor，W-27 unload/reload、拆除及存读档网络重建；W-28 统一门禁收尾。

## 未决项

W-23 覆盖原生 Team Reborn API 与 SimpleEnergyStorage，不等同于认证每个第三方机器 mod；第三方集成继续遵循 ROADMAP 排除边界。P5 仍需完整模型与材质巡检，本批只修复 W-23 直接暴露的 contents 材质故障。

## 验收证据

P3 最终 `fabric-machine-smoke: PASSED` 可作本阶段已存在链路的基线，不写作 W-23 完成。下述为本阶段专属验收。

2026-10-30 W-23 真机前置故障：Team Reborn storage simulate/execute 均返回10000单位，模拟后cube为0J，执行后25000J；随后放置通电 Cable 引发客户端 `TransmitterQuadBaker: unknown material 'Contents'`（RenderUniversalCable→BaseModelCache.OBJModelData→bakeGroup）。原 `transmitter_contents.mtl` 声明 Contents/Kd/Ka/d，无 map_Kd；官方内容 bake 用 `material -> icon` 覆盖材质贴图。现 parser 在文件末尾丢弃无贴图材质，导致原材质身份和颜色丢失。最小前置边界是既有 OBJ parser 保留材质与默认 missing texture，让原 icon override 生效；不扩展整个 P5。先修复并重跑同一实际复现，再判 W-23。

2026-10-30 W-23 完成：`ForgeEnergyCompat` 使用真实 `EnergyStorage.SIDED` / ITEM / ENTITY lookup，经既有 FabricStrictEnergyHandler 进入 Joules；真实 capability resolver 的 owner 继续由 CapabilityTileEntity 生命周期保护。公共 IEnergyConversionHelper.INSTANCE 在游戏内加载成功，getConversion=2.5。通过 mcdebug 外部 `teamreborn:energy` handle 模拟注入10000单位，cube库存保持0；执行后25000J。cube `[0,100,4]` 输出→basic cable `[1,100,4]`→无原料crusher `[2,100,4]`：稳定读数源0J、crusher20000J、cable5000J，合计25000J。外部API读取crusher为8000单位／capacity8000，正确对应20000J。见 [源](evidence/p4-energy/before-source.json)、[最终源](evidence/p4-energy/after-source.json)、[机器](evidence/p4-energy/after-sink.json)、[线缆](evidence/p4-energy/after-cable.json)、[外部读数](evidence/p4-energy/external-sink.json)。

断开cable后，cube置25000J用于独立反向提取：模拟1000外部单位不改库存；实际提取1000后22500J。crusher本来不提供能量输出，最初对它请求output返回STORAGE_NOT_FOUND，未修改原机器输出能力。见 extract/after-extract 系列读数。原生 Team Reborn `SimpleEnergyStorage(100000,100000,100000)` 初始化4000单位，经真实 `FabricEnergyLookup.wrapAsStrictEnergyHandler`：模拟提取2500J仍4000单位，执行后3000单位，再注入5000J返回remainder0且存量5000单位。见 external-handler/external-after 系列读数；这是调用真实第三方API对象的运行探针，没有注册虚拟机器或修改生产框架。

同一实机复现修复：无 map_Kd 的材质保留为原 Kd/d/Ka/tint 和默认missing texture，原内容bake的icon override仍是唯一贴图来源，不丢弃Contents／none0等材质。assemble PASS（20秒，含slice gates）；重启后同样通电链路保持连接，`gui problems` 只有启动WARN，无新增ERROR，实际状态见 [status](evidence/p4-energy/status.json)。原崩溃报告 [cable-crash](evidence/p4-energy/cable-crash.txt) 保留；未把这次局部修复写作整个P5验收。既有 FabricEnergyStorageTest / FabricStrictEnergyHandlerTest / FabricEnergyLookupTest 共6项 PASS（15秒）。无新测试。

本批自有会话正常 stopped，世界从启动前备份恢复，全部81个文件SHA-256一致；临时debug mod、wrapper与Xvfb清理。W-23完成，下一项W-24；P4阶段全门禁留W-28统一执行。
