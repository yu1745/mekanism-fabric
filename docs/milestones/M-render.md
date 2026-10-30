# M-render — 客户端模型与交互验收

## 现状

ROADMAP P5 W-29–33。P4 已验证能量／流体／物品／化学／热量及网络生命周期；其中 Contents 无贴图材质已在 `d10def07` 修复，W-24 实机确认 contentModelCache 已成功 bake。W-29 已完成原OBJ六类行为映射、真实连接／断开截图，并修复原版流体RGB→ARGB与默认模型block-light两项差异。W-30 已补齐粒子与直接tick调用方，模型映射与focused tests通过；W-31–33仍待完成。

## 下一步

W-31：按machine/factory/QIO/robit/teleporter等真实screen/menu owner做GUI巡检，记录打不开／渲染异常并在同一闭包修复。随后W-32按键／客户端注册／accessory渲染，W-33阶段门禁。

## 未决项

P3 未纳入 ledger 中的客户端类须逐行为确认替代，不能因 Fabric 类名存在就核销。混合 JEI/EMI 公共 API 继续留 P8 发布限制汇总，第三方集成不在规划内。重大新模块边界按既有协议记录并跳到独立项，不增加占位实现。

## 验收证据

本阶段尚无完成声明。已有前置：P4 W-23 Contents 修复、W-24 实际水内容模型 bake；原始与运行证据见 `../archive/M-transfer.md` 及 `evidence/p4-fluid/contents-bake-cache.json`。

2026-10-30 W-29 真机发现：cable/pipe/tube外壳与连接模型均可渲染，固定相机断开后客户端renderData由NORMAL/NORMAL变NONE/NONE且几何出现间隙。但水内容不可见；客户端pipe network.lastFluid=water/currentScale=1，FabricFluidColors.getTintColor=4159204（0x003F76E4），MekanismRenderer直接按ARGB使用使alpha=0。须核对Fabric handler RGB契约与Mekanism自有FluidType透明度，在现有颜色adapter修正并重拍，当前不宣布W-29完成。

同一W-29 wrapper核对发现第二项必要差异：官方WrapperModelConfiguration.useBlockLight恒false，并用于默认item bake；FabricTransmitterBlockModel却把继承gui_light的true传入baked model。实际basic cable模型usesBlockLight=true已确认。最小修正为在同一bake入口保留false，不改模型几何／纹理／display；随后资源reload取得新模型复核。

2026-10-30 W-29 完成（基线 `21cf422a`）：原OBJ目录六类的精确映射见 [目录](evidence/p5-transmitter/official-obj-owners.csv)。实际FabricModelLoadingPlugin发现base与descendant，FabricTransmitterAssets读取原model/flip_v/glass/mtl_override；BlockModel父链解析alias与display；FabricBakedModel从冻结的TransmitterModelData选六个parts，沿原NONE/NORMAL/PUSH/PULL组、端面NO_ROTATION/ROTATE_270、opaque纹理映射与glass tint分层；BaseModelCache/RenderTransmitterBase按live icon重烘contents。原NeoForge包装类无须照搬；P3 ledger仍保留当时未验证的历史身份，本映射提供后续核销证据。

真机固定相机：spectator `[6.5,103,-1.5]` 朝向三排三节管线，端点z0/z4，中心z2；x=-3 cable，0 pipe(water)，3 tube(oxygen)。客户端相机与framebuffer854×480实际确认；临时white concrete平台、night vision与hideGui只用于读图，hideGui最后恢复false，存档完整恢复。首次截图有chat／教程覆盖，未计最终证据；reload返回后首次截图仍有Mojang淡出overlay，也已重拍，最终overlay=null。已实际查看 [连接](evidence/p5-transmitter/connected.png) 与 [断开](evidence/p5-transmitter/disconnected.png)：cable/pipe/tube外壳贴图和内容可见，无missing-texture棋盘格；中心两面断开后端帽和间隙出现。client-connected/client-disconnected.json分别读回三者NORTH/SOUTH为NORMAL/NORMAL→NONE/NONE，是真实客户端renderData。未扩称本批覆盖每个tier／全部六向旋转／opaque配置矩阵。

修复一：原生水getTintColor返回4159204=0x003F76E4，但原MekanismRenderer需要ARGB，导致fluidScale=1且water存在时内容仍透明。固定Fabric rendering-fluids-v1 `3.1.6+1daea21519` sources：FluidRenderHandler.getFluidColor说明alpha不被原生渲染支持；SimpleFluidRenderHandler.coloredWater明确RGB且忽略alpha。现有FabricFluidColors边界为原生handler RGB补alpha255，为MekanismFlowingFluid保留真实MekanismFluidType的alpha，handler的RGB仍使用实际注册值；不改共用领域Renderer。实际水恢复0xFF3F76E4，重载后仍相同；brine=-69732，等于原FluidType.color，未被替换。见 tint-before/tint-after/tint-after-reload与brine-*；保留修前截图供核对。

修复二：原WrapperModelConfiguration.useBlockLight=false经VisibleModelConfiguration用于默认item bake；Fabric此前继承gui_light而返回true。现在真实FabricTransmitterBlockModel bake传false，几何、sprite、transform和其它owner不变。实际basic cable baked model由true→false，新resource reload取得另一实例后仍false。两项方法体hot swap均ok=true／failed=[]，客户端资源reload成功，内容模型重新bake；运行仅既有启动WARN，无新增GUI ERROR。

最终assemble PASS（17秒，含三slice gates与remapped JAR）；此前颜色修复assemble19秒也通过。无新增测试，验证使用实际客户端API与截图；P5完整门禁留W-33。自有会话正常stopped，完整隔离世界81文件SHA-256恢复一致，临时mod/wrapper/Xvfb清理。W-29完成，下一项W-30。

2026-10-30 W-30 边界：四个particle类型已注册但factory缺失；官方RenderTickHandler.tickEnd及jetpack/scuba/idle-flamethrower直接调用方也尚未接线。完整最小闭包为四个原粒子类、现有RenderTickHandler的原tickEnd与四个辅助方法、现有client bootstrap的ParticleFactoryRegistry与END_CLIENT_TICK；复用PlayerState.CLIENT、ItemFlamethrower和原Pos3D算法。不恢复其它无关NeoForge事件hub或第三方JEI分支。Laser getRenderBoundingBox是NeoForge扩展，Fabric vanilla不执行该frustum扩展；保留公开方法与bounding box算法，只移除@Override。

首轮compile错误原文：FlameParticle/BubbleParticle构造器不是public，无法在原子类调用super。仅补这两个vanilla构造器的access widener以保留原继承与算法；不复制vanilla粒子。随后重新编译验证。

首轮真实ParticleEngine.createParticle报`NullPointerException: this.sprites is null`；原四份particles/*.json存在，但production processResources仅复制radiation.json，实际sprite set未绑定。完整闭包增加四份原JSON的精确资源纳入（textures已有原样打包）；重新构建／资源reload后再复验，不把仅factory注册当作完成。

W-30模型核对发现DataBased loader一处行为差异：官方唯一SUPPORTED_PROPERTIES是emitting并忽略其它JSON字段；Fabric此前任选no_data/loader之外第一个key，资源包加parent等metadata时可能错选。最小修复为直接解析emitting，保留原实际owner和两状态模型，不扩为通用ModelData框架。

2026-10-30 W-30 完成（基线 `be66e52c`）：原baked/data/Robit包装类的真实owner映射见 [目录](evidence/p5-model-particles/official-model-owners.csv)。QIO block的原12个placement与rotation由FabricQIODriveGeometry和RenderQIODriveArray绘制，item状态读取真实frequency与DriveMetadata；FabricQIOModelPlugin先调用原ItemOverrides并包装实际resolved model。DataBased选择真实BE的emitting状态；Robit保留自有atlas render types、skin transform、父显示变换与reload cache。既有四个model client tests各1项共4项PASS（45秒），验证真实shipped assets、drive/item状态、两种DataBased模型与光照、Robit数据包选模/缓存失效。原DataBased仅支持emitting；本批修正任选JSON额外key的差异，最终同一DataBased test重跑PASS。未新建通用ModelData兼容层。

粒子完整闭包：common slice1513→1517，纳入原JetpackFlame/Smoke、ScubaBubble、Laser四类；前三者与官方baseline字节一致，Laser只移除Fabric vanilla不存在的getRenderBoundingBox @Override，方法与边界算法仍在。Flame/Bubble构造器的必要AW与NeoForge patched-source protected构造器对照，未复制vanilla源码。client bootstrap原ParticleFactoryRegistry精确注册四个Factory；原particles/*.json精确打包，原textures已存在。发布JAR四class及5份JSON唯一。

直接调用方：恢复原RenderTickHandler.tickEnd、doScubaRender、doJetpackRender、tryAddIdleFlamethrowerParticles、renderJetpackSmoke并以Fabric END_CLIENT_TICK接线。只将全局Mekanism.playerState改用既有PlayerState.CLIENT、无状态方法static化；原游戏mode／运行状态guard、玩家遍历、每4tick判断、位置／速度／姿态与双手优先级保留，不恢复无关事件hub或JEI分支。Laser原TileEntityBasicLaser的原生particle packet发送路径已存在，未另建网络协议。

真实客户端：新JVM启动通过构造器访问，补齐JSON并资源reload后ParticleEngine.createParticle根据实际注册provider创建四个原类；sprite分别minecraft:flame、minecraft:generic_*、minecraft:bubble、mekanism:laser；Laser lifetime5，bounds来自原实现。见 [原生factory读数](evidence/p5-model-particles/native-factory-particles.json)。未直接new Factory绕开注册。实际给玩家持有含1000hydrogen的Flamethrower，isIdleFlamethrower=true，END tick后实际particleEngine队列含JetpackFlameParticle及多个非零age，证明原持物→tick→factory→engine路径。见 [条件](evidence/p5-model-particles/idle-flame-eligible.json)、[队列](evidence/p5-model-particles/idle-flame-particles.json)。这不冒充W-36整套Jetpack/Scuba装备行为已完成。

首轮compile两构造器访问失败、首轮factory sprite=null以及CLI枚举/Float参数探针均已记录；补验使用同一真实owner，不放松检查。最终assemble+DataBased focused test PASS（22秒，含slice gates），[验收摘要](evidence/p5-model-particles/validation.md)。运行无新增未处理客户端ERROR；早期反射factory的已知NPE已由缺失JSON修复。自有客户端正常stopped，隔离世界81文件SHA-256恢复一致，临时mod/wrapper/Xvfb清理。无新测试；P5全阶段门禁留W-33。下一项W-31。
