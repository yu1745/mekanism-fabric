# M-additions — Additions 附加模块

## 现状

W-45基线`184299a6`。原64份源码逐文件归类见evidence/additions/source-boundary.csv；61份实现源码保留原包名，3份JEI/alias按既有路线排除。Tools已提供独立源集/JAR与统一build.sh收集方式，本模块沿用同一模式，不混入核心包体。范围包括塑料/Glow Panel/TNT、气球/对讲机、8实体（其中6种baby）、配置与原资源。

## 下一步

W-46按完整调用链接入：DataComponents/EntityTypes→Items/Blocks→tab/resources，复用核心注册器、BlockBase、fluid logging、IModeItem、ModModuleState与FabricServerConfig。41项SERVER配置在核心304＋Tools63上增至408，仍低于既有512上限；保留文件名、默认值、NBT和WalkieData codec。直接依赖的原实体和客户端一并纳入，W-47/48逐项完成实际行为，再做W-49一个端到端场景与一次回归监测。

## 未决项

- 生成修饰：保留两种原modifier及`baby_mob_spawn` codec、原活动数据包路径/none覆盖和blacklist。Fabric worldgen注册冻结早于SERVER配置owner；不能在biome callback偷用默认值。最小边界为现有FabricBiomeModifiers资源作用域接入模块loader，并在实际biome/structure spawn查询保留原SpawnConfig比例、成本和bounding box语义；实现时核对world owner，禁止静态缓存跨世界泄漏。
- 实体与渲染：气球原complex spawn携带color/latch；接vanilla实体追踪，保留原NBT与latched移动算法。TNT原点火/爆炸、透明塑料beacon tint、fluid logging、pick/reequip都需真实入口；仅去掉Override不算完成。客户端复用BaseModelCache真实bake/reload与原renderer。
- 语音：实际依赖为JDK AudioSystem＋TCP，16kHz/16bit/mono，short长度帧、服务端按玩家IP识别并按WalkieData频道转发；没有外部平台音频库。接真实server/connection owner并保留默认关闭，核对线程退出、socket和音频line释放。真机音频设备若不可用，明确记录验证限制，不伪造音频成功，不以stub代替。明显超出该链的依赖记录后继续已确定范围。

## 验收证据

W-45仅文档边界，尚未宣布Additions可用。后续以编译/独立JAR、非默认配置、气球追踪/存读档、baby生成与渲染、隔离TNT爆炸和语音实际连接作为证据；每模块evidence≤1MB，不新增测试，完整发布门禁留到P13。
