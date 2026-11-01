# M-additions — Additions 附加模块

## 现状

W-45边界`f206c34f`，W-46–49主体实现及回归完成。保留61份原源码与原包名、注册ID、NBT及WalkieData codec；3份JEI/alias集成按既有边界排除，逐文件清单见evidence/additions/source-boundary.csv。6份native接入直接承接生成查询、TNT/火焰点火及信标扫描。独立Additions源集/JAR不混入核心包体，当时build.sh收集核心、Tools、Additions三个JAR；当前已含Generators，共四个JAR。

实际注册271方块、271方块物品＋25其他物品、8实体（6种baby）、1组件、1声音。SERVER配置41项：本次核心＋Additions同步345值；同时安装Tools时合计408，低于512上限。原配置、继承/物理/爆炸/生成比例算法与NBT保留；气球color/latch接vanilla实体追踪。原重染difference ingredient在构建资源中使用Fabric原生等价形式；魔法伤害标签数据依据NeoForge21.1.200的NeoForgeDamageTypeTagsProvider，仅补所需magic/poison/wither分类，Fabric原生毒伤仍由magic表达。

生成修饰复用FabricBiomeModifiers的真实活动数据包作用域：原codec/none覆盖/blacklist在worldgen结束时选择，SERVER策略在原生biome/structure生成查询读取，避免注册冻结时误用默认值；结构bounding box与生物生成成本沿用原义。核心仅扩展既有模型缓存、re-equip、创作栏与worldgen owner。火焰会先移除TNT方块，专用native接入使用原方法捕获的状态；没有替代机器/事件框架。

## 下一步

主体实现与既有回归已完成；当前随 `M-release.md` 收口，不重开已完成的 Generators 规划。W-48 原始 16kHz/big-endian 音频与双向会话仍未验收；W-64 的 48kHz/little-endian Java Sound 采样只证明另一格式的线路可用，不能替代原协议读数。未新增音频测试。

## 未决项

W-48实现已接真实server/connection owner：JDK AudioSystem＋TCP，原16kHz/16bit/mono/big-endian协议及short帧、频道转发和默认关闭保留；世界/玩家读取回到server线程，socket/音频line随owner关闭。实际SERVER_STARTED启动、客户端TCP连接、玩家识别与退出已验证。`AdditionsClient.launch → VoiceClient.run → VoiceInput.run → TargetDataLine.open`在当前环境报`LineUnavailableException`（原PCM输入格式不可用）；双向音频采集与多客户端实际音频仍未验收，不声明语音完整可用。NeoForge配置屏幕沿核心既定排除范围；完整发布门禁未通过。

## 验收证据

- 真机：271方块／296物品／8实体，6份SERVER配置／345值send/apply/ack；文件中的TNT引信20、爆炸半径2、baby bogged权重0.2和语音端口36124在重启后生效。[读数](evidence/additions/native-summary.json)。
- 实际气球物品交互、存读档后客户端red/latch一致；magic免疫有效；swamp生成列表14项，bogged权重2→6；fortress保留PIECE并从5项扩为7项；六种baby完成生成，关键渲染见[实体](evidence/additions/native-babies.png)、[气球/信标](evidence/additions/native-balloon-beacon.png)。
- 火石与实际FireBlock点火都生成20tick引信的原TNT实体，21tick后破坏地面；信标red=-51140。恢复256项重染配方，总3982项，红色重染接受blue/拒绝red；对讲机1→8换频道，客户端属性值8.0。
- 自有JVM正常停止，端口释放，隔离世界副本、临时debug依赖及自有Xvfb已清理，原调试世界未动。统一构建、既有578项测试、API检查与机器smoke通过；[回归及三JAR摘要](evidence/additions/regression.json)。没有新增测试。提交后真实diff复核已移除语音public方法不必要的checked exception，保持server线程读取和可中断退出。
