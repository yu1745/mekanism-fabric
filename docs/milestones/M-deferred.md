# W-72 核心延期文件复盘

被 slice 排除的核心（非第三方集成、非 recipe_viewer）文件共 **35** 个。逐项对照 Fabric 侧实现后，**全部有替代或确认不适用，无玩家可见缺口**，因此本轮不改生产代码。

| 文件 | 职责 | Fabric 侧替代 | 玩家可见缺口 |
|---|---|---|---|
| `Mekanism.java` | 入口主类 | MekanismFabricBootstrap（Fabric entrypoint） | 无 |
| `PacketHandler.java` | NeoForge payload 注册器 | fabric/network/*（PayloadTypeRegistry + ServerPlayNetworking） | 无 |
| `BasePacketHandler.java` | NeoForge payload handler 基类 | 同上；协议版本在 handshake payload 中 | 无 |
| `ClientRegistration.java` | 客户端注册（渲染器/模型/属性） | MekanismFabricClientBootstrap + FabricClientModelRegistration / FabricEntityRendererRegistration / FabricScreenRegistration | 无 |
| `ClientRegistrationUtil.java` | NeoForge 客户端注册工具 | 并入上述 Fabric 注册类 | 无 |
| `MekKeyBinding.java` | NeoForge 键位（conflict context） | MekanismKeyHandler.registerKeybindings()（Fabric KeyBindingHelper），已由 client bootstrap 调用 | 无（W-64 的按键读数仍缺） |
| `MekKeyBindingBuilder.java` | 键位构建器 | 同上 | 无 |
| `ModelToVoxelShapeUtil.java` | NeoForge 模型→VoxelShape | Fabric 侧不需要（用 vanilla VoxelShape 直接构造） | 无 |
| `DriveArrayBakedModel.java` | NeoForge ModelData 烘焙模型 | FabricDeferredLoaderModel / FabricDataBasedUnbakedModel | 无 |
| `ExtensionOverrideBakedModel.java` | 同上 | FabricCompositeModel / FabricMekanismModelPlugin | 无 |
| `ModelDataBakedModel.java` | 同上 | 同上 | 无 |
| `DataBasedBakedModel.java` | ModelData 驱动模型 | FabricDataBasedUnbakedModel | 无 |
| `DataBasedGeometry.java` | ModelData 几何 | 同上 | 无 |
| `DataBasedModelLoader.java` | NeoForge geometry loader | FabricMekanismModelPlugin 注册的 unbaked model | 无 |
| `RobitModelDataBakedModel.java` | Robit 模型数据 | FabricDataBasedUnbakedModel 路径 | 无 |
| `MekanismCurioRenderer.java` | Curios 渲染 | client/render/armor/FabricAccessoryRenderRegistration | 无 |
| `ISpecialGear.java` | NeoForge IClientItemExtensions 接口 | 按物品的 registry（文件内注释说明） | 无 |
| `TransmitterBakedModel.java` | OBJ 发射器烘焙模型 | Fabric 侧 OBJ 由 vanilla 模型系统 + FabricDeferredLoaderModel 承担 | 无 |
| `TransmitterLoader.java` | OBJ loader | 同上 | 无 |
| `TransmitterModel.java` | OBJ 模型 | 同上 | 无 |
| `TransmitterModelConfiguration.java` | OBJ 烘焙上下文 | 同上 | 无 |
| `VisibleModelConfiguration.java` | 同上 | 同上 | 无 |
| `WrapperModelConfiguration.java` | 同上 | 同上 | 无 |
| `CommonPlayerTracker.java` | 玩家登录追踪 | MekanismUsernameLifecycle（ServerPlayConnectionEvents） | 无 |
| `ReloadListener.java` | NeoForge 资源重载 | ResourceManagerHelper + fabric/datamaps 装载器 | 无 |
| `FieldReflectionHelper.java` | NeoForge 反射工具 | Fabric 侧用 vanilla/reflect 直接访问 | 无 |
| `MekAnnotationScanner.java` | NeoForge 注解扫描 | Fabric 无对应需求（编译期切片已静态化） | 无 |
| `ConditionExistsCondition.java` | NeoForge 条件类型 | mekanism:condition_exists（在 transformCompatRecipes 与 Fabric 条件实现中） | 无 |
| `MekanismRecipeConditions.java` | 条件注册 | 同上 | 无 |
| `ModVersionLoadedCondition.java` | 版本条件 | mekanism:mod_version_loaded（FabricModVersionLoadedCondition） | 无 |
| `DatapackDeferredRegister.java` | 数据包注册器 | fabric/registry + datapack 资源直接装载 | 无 |
| `DataMapTypeRegister.java` | data map 类型注册 | fabric/datamaps/* | 无 |
| `MekanismAttachmentTypes.java` | NeoForge 附件类型 | fabric/radiation/FabricRadiationAttachments + EntityFlame 持久数据 | 无 |
| `MekanismDataMapTypes.java` | data map 键 | fabric/datamaps/FabricMekanismDataMaps | 无 |
| `MekanismAccess.java` | 服务接口实现（NeoForge） | Fabric 服务描述符（IMekanismAccess 有意不加载） | 无 |

## 未覆盖

- 键位有实现但缺真机读数（W-64 未验证项）；
- 机器库存/能量注入仍留 W-77 残留；
- 集成类 249 个文件（157+92）不在本轮，归 W-68/W-74。
