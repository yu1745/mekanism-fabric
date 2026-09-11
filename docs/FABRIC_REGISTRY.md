# Fabric registry 最小适配

当前 P0 切片在 `mekanism.fabric.registry` 提供 Mekanism 专用的同步适配器。它只收集
`ResourceKey` 与 `Supplier`，在 Fabric `onInitialize` 中调用 vanilla
`Registry.register`，并将其返回的 canonical 实例绑定到 holder。holder 在绑定前访问会失败；
批次拒绝重复键、重复提交，以及 supplier 注册期间递归读取 holder。这里不实例化官方
`Mekanism.java`，不模拟 EventBus/NeoForge lifecycle，也不提供通用转换层。

唯一证明声明是 `mekanism:seismic_vibration -> new GameEvent(64)`。它没有加入官方
`MekanismGameEvents`，以免未来启用官方批量声明时重复注册。启动后 entrypoint 会从 vanilla
`GAME_EVENT` registry 回查 key 与对象身份，并输出 `MEKANISM_FABRIC_REGISTRY_PROOF` 日志。

后续接入官方 registry 源码时，将 `MekanismDeferredRegister` / holder 改为**组合**此 adapter，
而不是复制或继承一个 NeoForge 兼容层；届时删除临时的 `MekanismFabricGameEvents` 声明。

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
