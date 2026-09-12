# Fabric registry 最小适配

当前 P0 切片在 `mekanism.fabric.registry` 提供 Mekanism 专用的同步适配器。它只收集
`ResourceKey` 与 `Supplier`，在 Fabric `onInitialize` 中调用 vanilla
`Registry.register`，并将其返回的 canonical 实例及 `Holder.Reference` 绑定到 holder。holder
在绑定前读取值会失败，但 key/id 可用；批次拒绝重复键、重复提交，以及 supplier 注册期间
递归读取 holder。这里不实例化官方 `Mekanism.java`，不模拟 EventBus/NeoForge lifecycle，
也不提供通用转换层。

官方 `MekanismDeferredRegister` 与 `MekanismDeferredHolder` 已改为组合该 adapter，
`GameEventDeferredRegister` 和 `MekanismGameEvents` 通过 `fabric/common-slice.txt` 审计后直接参与
Fabric 构建；Task 031 又纳入 sound/damage declaration closure，但尚未把这两个 owner 接入
entrypoint。本地 adapter/entrypoint 同样由 `fabric/platform-slice.txt` 精确列举。临时
`MekanismFabricGameEvents` 已删除。当前 entrypoint 仍只同步注册并验证四个官方 GameEvent 声明：

- `mekanism:seismic_vibration`（radius 64）；
- `mekanism:jetpack_burn`（radius 16）；
- `mekanism:gravity_modulate`（radius 16）；
- `mekanism:gravity_modulate_boosted`（radius 32）。

启动后 entrypoint 会从 vanilla `GAME_EVENT` registry 回查每个 key、半径、canonical 对象与 holder
身份，并输出含 `entries=4` 的 `MEKANISM_FABRIC_REGISTRY_PROOF` 日志。为避免把尚未移植的完整
`Mekanism.java` 拉入这个最小闭包，`MekanismGameEvents` 暂时直接使用稳定 namespace
`"mekanism"`；完整 bootstrap 迁入后恢复对统一 MODID 常量的引用。

## Task 028 deferred codec/criterion/loot 边界

Task 028 将 common slice 扩为 103 项。`DeferredMapCodecHolder`、
`DeferredMapCodecRegister`、`DeferredCriterionTrigger` 与 `LootFunctionDeferredRegister` 四份源码
保持与 oracle `4646f509` byte-identical；与它们分开记录的唯一既有适配修复，是把
`MekanismDeferredHolder.value()` 的返回类型从 `R` 收窄为 `T`，方法体仍精确为 `return get();`。
这是合法的 `Holder<R>` covariant override：类约束 `T extends R` 使 `T` 对 `R` return-type-substitutable；
两个无界类型变量在 JVM 上都擦除为 `Object`，所以 descriptor 与执行路径不变，本基线编译器也不需
生成额外 bridge class/method。测试中的 `MapCodec<Subtype> typed = holder.value()` 同时提供源码级
subtype 契约证据，静态 JAR family gate 则约束实际 class 输出。

唯一新增测试类恰好四个 `@Test`，只经 package-private `MekanismDeferredRegister.Registrar` seam
提交本地批次，不向 custom 或 built-in registry 增加条目。测试覆盖 function/supplier codec 的 lazy
factory、key/id、custom holder、duplicate/lifecycle、canonical/subtype identity，最小本地 criterion 的
exact trigger/instance identity，以及最小本地 loot function 的 basic/explicit-codec lazy/key/codec identity。
criterion 测试只使用仓库既有的一次性 `SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();`
vanilla 初始化模式；loot/codec 测试不执行序列化或 datapack codec。这里没有 startup marker。

Task 028 当时的发布物门禁把 direct registration family 精确限制为 `INamedEntry`、`MekanismDeferredHolder`、
`MekanismDeferredRegister` 及其 `Registrar`、`DeferredMapCodecHolder`、`DeferredMapCodecRegister`，
并把 impl family 精确限制为 `GameEventDeferredRegister`、`DeferredCriterionTrigger`、
`LootFunctionDeferredRegister`，拒绝其它 nested/test/helper。real production declarations、global
registration/freeze、datapack codec execution、serialization、loot/advancement runtime、owners、
networking 与 threading 均延期。

## Task 031 sound/damage declaration 与 holder lookup 边界

Task 031 将 API/common/platform allowlist 扩为精确 **47/120/14**，API、platform 与 18-file
official root-test oracle 不变。六个新增 common source 中，`SoundEventRegistryObject` 与
`DamageTypeRegister` 保持和 oracle `4646f509` byte-identical；其余四源是明确限定的适配：

- `SoundEventDeferredRegister` 只把 NeoForge `DeferredRegister<SoundEvent>` 换成既有
  `MekanismDeferredRegister<SoundEvent>`，构造器通过 `SoundEventRegistryObject::new` 注入专用 holder
  factory；默认 variable-range、`ResourceLocation` function 与 supplier 三个注册 surface 及 typed
  return 保持不变，旧 `createHolder` 和失效 imports 删除，没有 shim 或新 production source；
- `MekanismSounds` 与 `MekanismDamageTypes` 各只删除完整 `Mekanism` owner import，并把
  `Mekanism.MODID` 换成稳定 literal `"mekanism"`；所有声明、顺序与值保持 oracle；
- `MekanismDamageType` 只把 private holder lookup 的 NeoForge-injected convenience call 展开为
  vanilla 表达式 `registryAccess.lookupOrThrow(key().registryKey()).getOrThrow(key())`，没有 import、公开
  surface 或其它方法变化。它与 NeoForge 21.1.200
  `IHolderLookupProviderExtension.holderOrThrow` 的 registry-missing/key-missing exception 语义一致。

`MekanismSounds` 的完整 oracle 表是 **47 个 public declarations**：一个 `SOUND_EVENTS` owner 和
**46 个** `SoundEventRegistryObject` holders；测试逐名固定全部 46 个 id/key/translation/unbound 状态，
不依赖 reflection 返回顺序，也不提交 owner。`MekanismDamageTypes` 精确含四个 damage entries，测试
固定 live collection 中的 exact identities、exhaustion/effects/key/translation 与 `toVanilla` metadata。
同一个五方法测试类还覆盖 fresh sound default/function/supplier 的 lazy、typed holder、canonical identity、
fixed/variable range 与 duplicate/late/double-commit 规则，以及 `DamageTypeRegister` 三个 overload、live
unmodifiable view 和 duplicate last-wins（不依赖 `HashMap` 顺序）。

DamageSource lookup 测试不向 global/built-in registry 添加条目。它只在测试内构造一个 disposable
`MappedRegistry<DamageType>`，注册并 freeze 一个匹配值，再用仅包含该 registry 的 `RegistryAccess`
验证 plain、position 与 nullable-entity 三个 overload 的 exact holder/value/position、`is`，并验证 missing
key/registry 均失败。测试只执行 `DamageEffects` 静态初始化所需的标准 vanilla bootstrap；唯一由测试
创建的 registry entry 位于上述隔离 registry。没有 `Level` fixture、non-null `Entity`、Mockito、Unsafe
或 entrypoint。

静态发布物门禁现在要求 direct registration family 仍精确六项，direct `registration/impl` 精确七项
（原三项加 `SoundEventDeferredRegister`、`SoundEventRegistryObject`、`DamageTypeRegister`、
`MekanismDamageType`），direct `registries` 精确只有 `MekanismGameEvents`、`MekanismSounds` 与
`MekanismDamageTypes`，每项恰好一次，并拒绝 nested/test/helper。此闭包没有 startup marker。
SoundEvent 的真实 owner registration/playback/client/resources/subtitles，以及 damage type 的真实
registration/freeze/datapack bootstrap、production `RegistryAccess`/`Level`/entities/combat 和全部
owners/callers 均延期。

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
