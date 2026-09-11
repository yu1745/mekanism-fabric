# Fabric registry 最小适配

当前 P0 切片在 `mekanism.fabric.registry` 提供 Mekanism 专用的同步适配器。它只收集
`ResourceKey` 与 `Supplier`，在 Fabric `onInitialize` 中调用 vanilla
`Registry.register`，并将其返回的 canonical 实例及 `Holder.Reference` 绑定到 holder。holder
在绑定前读取值会失败，但 key/id 可用；批次拒绝重复键、重复提交，以及 supplier 注册期间
递归读取 holder。这里不实例化官方 `Mekanism.java`，不模拟 EventBus/NeoForge lifecycle，
也不提供通用转换层。

官方 `MekanismDeferredRegister` 与 `MekanismDeferredHolder` 已改为组合该 adapter，
`GameEventDeferredRegister` 和 `MekanismGameEvents` 通过 `fabric/common-slice.txt` 审计后直接参与
Fabric 构建；本地 adapter/entrypoint 同样由 `fabric/platform-slice.txt` 精确列举。临时
`MekanismFabricGameEvents` 已删除。当前会同步注册并验证四个官方声明：

- `mekanism:seismic_vibration`（radius 64）；
- `mekanism:jetpack_burn`（radius 16）；
- `mekanism:gravity_modulate`（radius 16）；
- `mekanism:gravity_modulate_boosted`（radius 32）。

启动后 entrypoint 会从 vanilla `GAME_EVENT` registry 回查每个 key、半径、canonical 对象与 holder
身份，并输出含 `entries=4` 的 `MEKANISM_FABRIC_REGISTRY_PROOF` 日志。为避免把尚未移植的完整
`Mekanism.java` 拉入这个最小闭包，`MekanismGameEvents` 暂时直接使用稳定 namespace
`"mekanism"`；完整 bootstrap 迁入后恢复对统一 MODID 常量的引用。

## 对照资料

仅对照生命周期、canonical holder 与冻结 registry 边界，未复制实现：

- Kilt commit `9e8f0d67411f10234bf0d864bd151a6dbcef14b4`：
  `compat/fabric-compats/src/main/kotlin/xyz/bluspring/kilt/compat/fabric/architectury/KiltArchitecturyApiCompat.kt`；
- Kilt commit `9e8f0d67411f10234bf0d864bd151a6dbcef14b4`：
  `src/main/java/xyz/bluspring/kilt/injects/core/MappedRegistryInject.java`；
- Connector commit `8b27f1ad042aae8037bcc522b321c03fcce1a12a`：
  `src/mod/java/org/sinytra/connector/mod/mixin/registries/BuiltInRegistriesMixin.java`。

Kilt/Connector 为通用兼容所做的 registry mixin/冻结绕过在这里是反例：本切片只使用正常
Fabric 初始化时序和公开的 `Registry.register`，不绕过 vanilla registry freeze。
