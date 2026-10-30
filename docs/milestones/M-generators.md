# M-generators — Generators 附加模块

## 现状

W-50 基线 `3f365066`。原源码 121 份，计划精确纳入 113 份，8 份 JEI/EMI/viewer/alias 按既定第三方边界排除；逐文件清单见 [source-boundary.csv](evidence/generators/source-boundary.csv)。保留原包名、注册 ID、NBT、配方与计算算法，独立 source set 和 JAR，不混入核心。直接前置为现有 TileEntityMekanism、容器同步、化学/流体/能量/热量传输、辐射和 MultiblockManager；三个真实 manager 为 industrialTurbine、fissionReactor、fusionReactor，沿用原 cache/data/validator 和官方 build builder。

## 下一步

1. W-51–52：接配置、13 份 registries、物品/方块/容器/实体类型及两份 C2S 包；配置并入 FabricServerConfig。GeneratorsConfig 44、Storage 5、Gear 2，共 51 个 SERVER 字段，四模块合计 459/512，无需提高上限。保留三个文件与所有字段，编译后以实际 schema 核对。新增化学与模块类型的模块注册阶段放在现有 registry owner 冻结前；模块支持关系仍在类型注册完成后建立。
2. 同批纳入直接调用的原多方块类型，避免注册 holder 或替代机器。六种简单发电机先取得实际燃料/热/太阳/风发电、GUI与输出读数，再进入 W-53 涡轮蒸汽发电及存读档。
3. W-54–55：原裂变燃料、冷却、热量、逻辑和熔毁；原聚变氘氚/hohlraum、激光点火与能量。裂变风险路径仅在隔离世界执行，保留原辐射/爆炸 owner。
4. W-56–58：原 GUI 与风机/转子/反应堆渲染接入现有模型 owner；多方块破坏重建、区块重载及存读档；模块收口跑一次 build/API/machine 回归，完整发布门禁留 P13。仅使用既有测试及 mcdebug，不新增测试。

## 未决项

核心 FabricChemicalRegistryLifecycle 与 FabricModuleRegistry 目前在核心入口冻结，晚于核心执行的 Generators 入口无法注册原 3 种化学与 2 种模块。最小边界是在各自现有 owner 中增加 Fabric 命名入口，在冻结前调用附加模块的原注册表；先验证核心 65 种化学与 EMPTY/order，再追加 Generators，禁止解冻或伪造类型。流体槽与输出使用既有事务及 capability owner；fuel assembly 模型属性接原生模型链。没有已确认的范围外前置；若发现则记录真实调用链后跳过对应分支。

## 验收证据

- W-50 仅边界与执行规划，尚未声明 Generators 编译或运行通过。源文件逐项归类共 121 项，113 纳入／8 第三方排除。
- SERVER 字段逐项声明计数 44＋5＋2＝51，408＋51＝459；实际编译后 schema 与真机同步待验证。
