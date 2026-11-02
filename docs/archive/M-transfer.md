# M-transfer — 传输与网络生命周期

## 现状

ROADMAP P4 W-23–28 已完成并通过阶段门禁。阶段基线 `843af935`；唯一生产修复 `d10def07` 保留官方无贴图Contents材质。能量、流体、物品、化学、热量的真实端到端传输，以及五种网络的区块卸载／重载、拆装、保存重启均有运行证据。

## 下一步

进入P5 W-29–33：transmitter模型／纹理／连接截图、baked/data模型与粒子、GUI巡检、按键与客户端注册。活文档见 `../milestones/M-render.md`。

## 未决项

W-23 覆盖原生 Team Reborn API 与 SimpleEnergyStorage，不等同于认证每个第三方机器 mod；第三方集成继续遵循 ROADMAP 排除边界。P5 仍需完整模型与材质巡检，本批只修复 W-23 直接暴露的 contents 材质故障。

## 验收证据

P3 最终 `fabric-machine-smoke: PASSED` 可作本阶段已存在链路的基线，不写作 W-23 完成。下述为本阶段专属验收。

2026-10-30 W-23 真机前置故障：Team Reborn storage simulate/execute 均返回10000单位，模拟后cube为0J，执行后25000J；随后放置通电 Cable 引发客户端 `TransmitterQuadBaker: unknown material 'Contents'`（RenderUniversalCable→BaseModelCache.OBJModelData→bakeGroup）。原 `transmitter_contents.mtl` 声明 Contents/Kd/Ka/d，无 map_Kd；官方内容 bake 用 `material -> icon` 覆盖材质贴图。现 parser 在文件末尾丢弃无贴图材质，导致原材质身份和颜色丢失。最小前置边界是既有 OBJ parser 保留材质与默认 missing texture，让原 icon override 生效；不扩展整个 P5。先修复并重跑同一实际复现，再判 W-23。

2026-10-30 W-23 完成：`ForgeEnergyCompat` 使用真实 `EnergyStorage.SIDED` / ITEM / ENTITY lookup，经既有 FabricStrictEnergyHandler 进入 Joules；真实 capability resolver 的 owner 继续由 CapabilityTileEntity 生命周期保护。公共 IEnergyConversionHelper.INSTANCE 在游戏内加载成功，getConversion=2.5。通过 mcdebug 外部 `teamreborn:energy` handle 模拟注入10000单位，cube库存保持0；执行后25000J。cube `[0,100,4]` 输出→basic cable `[1,100,4]`→无原料crusher `[2,100,4]`：稳定读数源0J、crusher20000J、cable5000J，合计25000J。外部API读取crusher为8000单位／capacity8000，正确对应20000J。见 源、最终源、机器、线缆、外部读数。

断开cable后，cube置25000J用于独立反向提取：模拟1000外部单位不改库存；实际提取1000后22500J。crusher本来不提供能量输出，最初对它请求output返回STORAGE_NOT_FOUND，未修改原机器输出能力。见 extract/after-extract 系列读数。原生 Team Reborn `SimpleEnergyStorage(100000,100000,100000)` 初始化4000单位，经真实 `FabricEnergyLookup.wrapAsStrictEnergyHandler`：模拟提取2500J仍4000单位，执行后3000单位，再注入5000J返回remainder0且存量5000单位。见 external-handler/external-after 系列读数；这是调用真实第三方API对象的运行探针，没有注册虚拟机器或修改生产框架。

同一实机复现修复：无 map_Kd 的材质保留为原 Kd/d/Ka/tint 和默认missing texture，原内容bake的icon override仍是唯一贴图来源，不丢弃Contents／none0等材质。assemble PASS（20秒，含slice gates）；重启后同样通电链路保持连接，`gui problems` 只有启动WARN，无新增ERROR，实际状态见 status。首个异常与业务栈 cable-crash 保留；未把这次局部修复写作整个P5验收。既有 FabricEnergyStorageTest / FabricStrictEnergyHandlerTest / FabricEnergyLookupTest 共6项 PASS（15秒）。无新测试。

本批自有会话正常 stopped，世界从启动前备份恢复，全部81个文件SHA-256一致；临时debug mod、wrapper与Xvfb清理。W-23完成，下一项W-24；P4阶段全门禁留W-28统一执行。

2026-10-30 W-24 完成（基线 `d10def07`，未改生产代码）：FluidHandlerManager→FabricFluidLookup→FabricFluidStorage 保留真实物理side与快照事务；pipe sideChanged 在启停NONE时撤销对应视图，holder/tank identity漂移失效规则保留。MechanicalPipe.pullFromAcceptors 对捕获的真实network tank做快照并与外部extraction共同commit；FluidSendTransaction 负责分发期间的原子扣款。既有 FabricFluidStorageTest 与 FabricFluidSendTransactionTest 共12项PASS（23秒），直接覆盖共享目标容量、分数单位、outer abort及视图失效后恢复。

真实世界：source tank `[0,100,4]` 8000mB water，sink `[2,100,4]` 31800mB，basic pipe `[1,100,4]` WEST=PULL / EAST=PUSH。稳定后source5800、pipe2000、sink32000，总39800mB与初始一致；sink外部API为2592000droplets/capacity2592000，满载再插入81000返回0。见 初始源、初始目标、满载源、管道、目标、拒绝插入。储罐并不保存machine component_config，读回以其实际fluid_tanks为准。

真实断开：先取得pipe EAST的实际FabricFluidStorage，slot amount=162000droplets；调用真实transmitter.setConnectionTypeRaw(EAST,NONE)、refreshConnections、notifyTileChange，与原配置入口使用相同owner，未反射写字段。旧view立刻amount0。模拟从sink提取81000返回81000，但sink仍32000mB；实际抽走81000后sink31000，source5800与pipe2000保持。模拟插回81000后sink仍31000；再以mcdebug storage transfer在同一outer transaction内向sink插入并从source扣除81000，abort后source5800/sink31000，证明两端回滚。已经核对mcdebug StorageOps.transferBetween/transferInsert/transferExtract：simulate执行真实Storage调用但不commit，close outer触发abort，不是仅调用领域SIMULATE。见 abort-* / after-* 读数。首次wait探针使用DSL不支持的数组方括号而报解析错误，未计入验收；断开后的连续实际NBT读数才是证据。

重新调用同一transmitter owner设EAST=PUSH并刷新：sink32000/source4800/pipe2000，总38800mB，恰为初始39800减实际抽走1000；新查询返回pipe162000droplets，保留旧view仍0。见 重连源、重连目标、新视图、旧视图。再次程序核对本批所有守恒与abort读数成功。

客户端contentModelCache.size=1，说明真实水内容模型已成功bake，也补强W-23同一Contents材质修复的运行证据；没有新增GUI ERROR。自有客户端正常停止，世界81个文件SHA-256完整恢复，临时debug mod/wrapper/Xvfb清理。W-24完成，下一项W-25；整个P4及W-27保存／卸载验收仍待做。

2026-10-30 W-25 完成（基线 `61ec00c5`，未改生产代码）：Sorter.getHomeInventory/getTargetInventory 每操作以真实物理side查 ItemStorage.SIDED；filter.mapInventory 生成官方 TransitRequest，目标是 TransporterItemStorage 时调用真实 insertMaybeRR。路由reservation与response.useAll源扣款处于同一caller transaction，仅commit后设active。原 allowDefault/strictFinder/autoEject 分支保留，本例关闭autoEject，仅按item-stack filter发送。HopperTransferMixin仅在实际Mekanism Storage端点经FabricHopperTransfer桥接vanilla push/pull，现有FabricItemStorageTest共6项PASS（23秒），包括阻塞variant后继续尝试后续机器槽。

真机过滤路径：chest `[-3,100,4]`→east-facing sorter `[-2,100,4]`→basic transporter `[-1,100,4]`→vanilla hopper `[0,100,4]`向下→chest `[0,99,4]`。通过实际NBT codec载入 `sorter_itemstack_filter/target_stack=minecraft:iron_ore`，eject=false；原codec省略默认enabled=true。混合8 iron_ore与8 cobblestone，最终源箱只余8圆石，目标箱有8铁矿，途经hopper为空、transporter无在途stack，数量没有重复或丢失。见 过滤配置、源、目标、经过的漏斗、管道。

真实漏斗进出机器：vanilla hopper `[5,100,0]` facing west，enrichment chamber `[4,100,0]` 的LEFT=item INPUT、BOTTOM=item OUTPUT，底部hopper `[4,99,0]`。源hopper放1 iron_ore，机器给40000J；初次读取时hopper已经推入，machine-initial真实读数input1、progress14（不是声称tick0快照）。自然tick完成后机器input/output皆空、能量10000J，底部hopper收到2 mekanism:dust_iron，源hopper空；wait predicate `inv[4,99,0].0.count == 2` matched，ranTicks19。见 机器初次读数、最终机器、产物、等待结果。没有使用inv.*读写Mekanism库存，权威读数均来自服务端NBT。

GUI：实际打开GuiLogisticalSorter（38 slots）与GuiElectricMachine（41 slots），后者菜单index2显示1 iron_ore。见 sorter-screen/machine-screen.json。第一次teleport落在无平台位置，玩家掉落至y62，open-block明确OUT_OF_REACH；未计入成功证据。回到目标附近并补临时支撑后实际打开成功；一条fill对原有stone返回“No blocks were filled”，无游戏故障。gui problems仅既有启动WARN，无新增ERROR。自有会话正常stopped，世界81个文件SHA-256恢复一致，临时debug mod/wrapper/Xvfb清理。无新测试，未改发布源码／依赖。W-25完成，下一项W-26；P4最终门禁仍留W-28。

2026-10-30 W-26 完成（基线 `e6c21283`，未改生产代码）：PressurizedTube 继续使用原 IChemicalHandler 的simulate/execute领域语义与ChemicalNetwork；HeatHandlerManager 使用唯一 mekanism:heat_handler lookup 和原ProxyHeatHandler，HeatNetwork更新真实导体heat capacitor，ThermodynamicConductor.getAdjacent刷新实际side acceptor。没有将化学域伪装成Fabric fluid，也未引入外部热量接口或替换官方热交换公式。既有 HeatHandlerTest、BasicHeatCapacitorTest、ChemicalCodecTankConnectionTest 各6项，共18项PASS（26秒）。

化学真机：source basic chemical tank `[0,100,4]` 8000 mekanism:oxygen；basic pressurized tube `[1,100,4]` WEST=PULL/EAST=PUSH；sink `[2,100,4]` 空罐输入。稳定读数sink8000、source空、tube无buffer，保持8000单位守恒且化学ID不变。见 初始源、最终源、tube、目标。

热量真机：resistive heater `[0,100,0]`→basic thermodynamic conductor `[1,100,0]`→无能量heater `[2,100,0]`。两heater初始heat capacity100，温度分别299.375000894K／299.532437982K；给source的真实energy container配置usage100000J/t、stored10000000J。实际network字符串为“1 transmitters, 2 acceptors”；接收端energy container始终无stored能量、active=false，但heat从29953.2438上升至1780144.009，后续独立temperature读数17413.771867K；source／conductor后续温度分别17568.423930K／1719116.262784K。读数来自连续自然tick，不声称这些独立RPC是同tick守恒快照。网络heatTransferred=346867.317494（原字段是温度传递统计，不写作J）。源的10000000J在采样前已经耗完，仍由已储热容自然交换。见 heat-initial-*/heat-powered-*/heat-temperature-* 与 实际network。

控制验证：通过真实transmitter.setConnectionTypeRaw(EAST,NONE)、refreshConnections、notifyTileChange断开；network变1 acceptor。将无能量接收端heat capacitor经NBT重置30000（300K），连续读数299.997947→299.991262K，仅环境变化。恢复EAST=NORMAL并刷新后，同一接收端升至8142.187896K，证明热量来自导体连接；未反射写热量字段，也未人为给接收端供能。见 heat-disconnected-temperature-1/2、heat-disconnected-network、heat-reconnected-temperature.json。官方热算法具有环境散失，本项不伪称绝热／同tick总热量守恒。

运行无新增GUI ERROR；自有客户端正常stopped，完整世界81文件SHA-256恢复一致，临时debug mod/wrapper/Xvfb清理。无新增测试或发布依赖变更。W-26完成；下一项W-27网络实际生命周期，P4全阶段门禁尚待W-28。

2026-10-30 W-27 运行检查点：远端chunk(64,64)五种网络已实际卸载（ServerChunkCache.hasChunk=false，五个保留transmitter canExposeCapabilities均false）并重载，旧对象仍关闭，energy/fluid/chemical缓冲与库存和item数量保持。第一次拆装已触发真实setRemoved并撤销五个旧对象；拆前RPC快照仍含energy4000/chemical3000，不能假定耗尽，补做自然tick等待且断言空缓冲后的第二次拆装。当前仍须完成保存重启和实际传输复验，W-27尚未关闭。

2026-10-30 W-27 完成（基线 `3d8d760b`，未改生产代码）：FabricCapabilityTileLifecycle通过真实BLOCK_ENTITY_UNLOAD关闭能力世代，LevelChunkUnloadMixin在clearAllBlockEntities调用原onChunkUnloaded释放transmitter份额；setRemoved仍走原onWorldSeparate/remove。既有CapabilityTileLifecycleTest 3项与FabricEnergyLifetimeTest 2项共5项PASS（19秒）。未新增生命周期框架、测试或生产日志。

同一远端chunk(64,64)，玩家保持出生区：各行源x1028／transmitter x1029／目标x1030、y100；z1028 energy cube+cable+cube，1030 fluid tank+pipe+tank，1032 chemical tank+tube+tank，1034 chest+transporter+chest，1036 resistive heater+conductor+passive heater。tube两端显式使用实际chemical side config；最初默认侧未开放只作场景准备，不计入通过。所有网络实际字符串为1 transmitter；energy/heat有2 acceptors，fluid/chemical/item有1。取消唯一forceload后ServerChunkCache.hasChunk(64,64)=false，保留五个BE的canExposeCapabilities全false，证明真实卸载，未以直接调用生命周期方法代替。重新forceload后五网重建，旧BE仍false。见 before-unload/after-reload、chunk-after-unforce与各retained.json。

卸载前后逐项核对：energy source82000J、buffer8000J、sink4000000J完全相同；fluid30000/2000/32000mB；chemical60000/4000/64000氧气；item目标8 iron_ore且源空。各transmitter connection与化学/流体身份保留。热量持续按原环境散失，不写作同tick完全相等。

拆装：通过真实setblock air触发五种transmitter setRemoved，保留旧BE全false，再重新放置。第一次拆前实际快照还含4000J／3000化学，不能假称清空或用该次数量差判保存问题。后续明确关闭WEST源侧并排空：实际Basic Cube extract请求16000仅返回4000J、ChemicalTank请求10000仅返回1000，符合原tier rate；第一次空缓冲断言因此失败，原读数保留为rate-limited-drain.json。按真实返回量补充排空并自然等待40tick，before-clean-removal确认energy/fluid/boxed_chemical均空，再次拆装五种传输方块。网络均恢复；源箱再放4 ore，目标由8→12，证明新transporter实际工作。见 before-clean-removal/after-clean-replacement与clean-drains.json。热量导体拆除不作热量守恒声明。

存档：集成服没有注册save-all flush命令，实际通过正常owned session stop关闭并保存所有维度；保存日志。新JVM启动同一存档，未再手工forceload之前hasChunk(64,64)=true，说明强制加载状态随原存档恢复；五网均重建。after-clean-replacement与after-restart两份读数的source/sink容器、Items及energy/fluid/chemical transmitter buffer逐项完全相同：energy54000/8000/4000000J，fluid10000/2000/32000mB，chemical51000/4000/64000；目标12 ore。

重启后的实际传输：从目标取4000J／1000mB／1000氧气后自动补满，源分别变50000J／9000mB／50000氧气，网络buffer仍8000/2000/4000；源箱再加入4 ore，目标变16且源空。source heater以同样1000000J脉冲再次加热，passive sink heat升到181393.119720（capacity100、无stored energy），热网仍1 transmitter/2 acceptors。见 restart-drains/restart-transfer.json。按真实返回量核对，未将独立热量采样写作绝热守恒。

最终客户端无新增GUI ERROR，两次自有会话正常stopped；完整世界从首轮前备份恢复，81文件SHA-256一致，临时debug mod/wrapper/Xvfb清理。所有失败探针与补验边界已注明。W-27完成，W-28当前完整build仍在执行；API门禁17秒PASS、machine smoke PASS，不提前关闭整个P4。

2026-10-30 W-28 阶段收尾：当前生产树的完整 `./fabric-build.sh` PASS（4m56s，578 tests，0 failures/errors/skipped）；`./gradlew -p fabric verifyApiSliceNoNeoForge --console=plain` PASS（17秒）；`scripts/fabric-machine-smoke.sh` exit0、`fabric-machine-smoke: PASSED`。前述“门禁仍在执行”为历史检查点，本轮最终已全部通过。持久摘要见 门禁，会话日志 `/tmp/mek-p4-final-{build,api,machine}.log`。

阶段提交：W-23 `d10def07`，W-24 `61ec00c5`，W-25 `e6c21283`，W-26 `3d8d760b`，W-27 `b486f402`，W-28为本次归档提交。无新增测试。与规划偏差仅为W-23提前修复直接阻塞的P5 Contents材质，W-24实际内容bake进一步验证；完整P5仍未验收。W-27探针受tier rate限制的失败与补验均保留，不扩称所有第三方机器或完整多人生命周期矩阵。阶段未新增需用户裁决的模块；P3混合公共API与客户端未纳入ledger仍由后续阶段处理。整体ROADMAP仍继续，非发布完成声明。
