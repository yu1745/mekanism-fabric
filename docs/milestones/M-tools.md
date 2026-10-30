# M-tools — Tools 附加模块

## 现状

W-43边界基线`c41de667`。原`src/tools/java`共52份：48份核心模块源码，4份JEI/alias/gender集成按ROADMAP排除。保留`mekanism.tools.*`包名、`mekanismtools`模组与注册ID、原72物品、6护甲材料、2配方serializer及生成资源；不复制源码到另一棵树。

## 下一步

W-44 Tools按完整纵向闭包接通：

1. 独立`tools` source set／精确slice和可单独加载的Tools JAR，依赖核心；专用Tools开发运行包含两个真实mod root，核心JAR不混入Tools类。随后沿用`build.sh`输出契约收集附加JAR。
2. `MekanismTools → 原Tools配置 → ToolsArmorMaterials → ToolsItems → serializer/tab/资源`。材料STARTUP在物品构造前加载；SERVER配置加入现有FabricServerConfig同一owner／snapshot／断连恢复，63字段加核心235仍低于既有512上限。复用ModModuleState、Version、注册器、cached values与原配方算法。
3. `原生Zombie/AbstractSkeleton/Piglin.finalizeSpawn HEAD → MobEquipmentHelper`，保留装备覆盖前时机、随机数顺序和过滤；paxel复用ToolAbilityUtils，refined glowstone实现既有IPiglinNeutralItem。客户端接原盾牌renderer／reload／blocking属性，原生护甲渲染仅替换目标物品light，保留姿态、纹理和slot语义。

## 未决项

不引入通用Forge事件、配置或item-extension兼容层。NeoForge配置屏幕、第三方viewer/gender不冒充已实现；原生创作栏的既有平台UI差异沿用核心。注意材料静态初始化、配置owner注册冻结、可选模块不被核心反向引用、盾牌banner组件与glow armor亮度。明显超出这条调用链的内容另记边界，继续已确定实现。

## 验收证据

当前为W-43立项页，未宣布Tools可用。实现批次先编译／打包，再以一个实际Tools场景验证注册、paxel操作／耐久、配方与护甲／盾牌渲染；复用既有检查，不新增测试覆盖，完整发布门禁集中到模块功能里程碑。用户已要求聚焦开发，P8余下发布检查顺延。
