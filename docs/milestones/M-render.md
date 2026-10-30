# M-render — 客户端模型与交互验收

## 现状

ROADMAP P5 W-29–33。P4 已验证能量／流体／物品／化学／热量及网络生命周期；其中 Contents 无贴图材质已在 `d10def07` 修复，W-24 实机确认 contentModelCache 已成功 bake。W-29 已完成原OBJ六类行为映射、真实连接／断开截图，并修复原版流体RGB→ARGB与默认模型block-light两项差异。W-30–33仍待完成。

## 下一步

W-30：核对原 baked/data 模型（DriveArray、Extension override、DataBased、Robit）与四种粒子的真实 Fabric owner，缺的行为在实际调用链修复；不因替代类名存在就核销。随后W-31各类GUI巡检、W-32按键／客户端注册／accessory渲染、W-33阶段门禁。

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
