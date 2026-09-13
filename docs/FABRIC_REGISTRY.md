# Fabric registry 最小适配

当前 registry closure 有三个 ownership 层次。既有 `FabricDeferredRegister`/`FabricDeferredHolder`
为 built-in/custom registry 提供同步 deferred 提交；Task 037 的 `FabricDefaultedRegistry`/
`FabricCustomRegistryLifecycle` 建立 intrusive + defaulted foundation；Task 040 的
`FabricChemicalRegistryLifecycle` 是首个具体 custom owner。其它 registry 不因这一 owner 自动纳入。

## Task 037 generic intrusive/defaulted foundation

`FabricDefaultedRegistry<T>` 是 vanilla `DefaultedMappedRegistry<T>` 的 final 精确子类，固定
`Lifecycle.stable()` 与 intrusive holders。唯一 factory 在构造 super 之前验证 registry/default
key 非空且 `defaultEntryKey.isFor(registryKey)`，并捕获构造线程。直接 entry mutation、
`createIntrusiveHolder`、root registration 与首次 freeze 都只允许该 owner thread；同步只串行化
mutation，不承诺 population 期间或之后的并发读取安全。

custom lifecycle 精确为 `CREATED -> REGISTERING_ROOT -> ROOT_REGISTERED` 与
`CREATED|ROOT_REGISTERED -> FREEZING -> FROZEN`，任一已经进入 vanilla/Fabric side effect 后的
异常进入 terminal `FAILED`。key/location occupancy、wrong-registry key、registered value identity、
null `RegistrationInfo`、root key/value identity与 default canonical identity 都在对应 side effect 前
检查；这些纯 precondition failure 不污染 registry。实际注册不补造 intrusive holder，因此遗漏
`createIntrusiveHolder` 时保留 vanilla `AssertionError` 并进入 `FAILED`。freeze 先要求 exact canonical
default holder，再交给 vanilla 检出 dangling intrusive holder；vanilla 可能先置 frozen 再抛错，故该
失败也不可重试。成功 first freeze 后，任何线程的 already-FROZEN `freeze()` 幂等返回同一实例；
这只承接 `RegistryAccess` reload snapshot，不放宽 mutation/首次冻结。`get` 与非法 `byId` 保留 default fallback，而
`getOptional` 不回退。

root path 只使用 `FabricRegistryBuilder.from(this)`：builder 的默认 `MODDED` 必须存在，boolean
选项为 true 时还必须有 `SYNCED`；返回对象、root 中的 value/key 与 attributes 全部按 identity/值
复核。Task 037 落地时 production 未使用该 foundation；其 generic automatic-freeze proof 位于专用
`fabricRegistryTest` 的 Task037 单方法、server-side Knot worker；Task040 在同一隔离类中增加第二方法。该 worker固定
`maxParallelForks=1`/`forkEvery=1`、使用自己的工作目录，并在全局 registry frozen 后立即退出。
ordinary tests 全部使用 never-rooted fresh registries，runtime 不含 `fabric-loader-junit`。

`FabricDeferredRegister.registerAll(Registry)` 保留既有 registry-key check，并在改变 batch state 或
调用 supplier 前询问 custom-registry owner/lifecycle；每个 key/location occupancy 在对应 supplier
前检查，value identity 在构造后、注册前检查。真实 registry path 只调用
`Registry.registerForHolder`，`FabricDeferredHolder` 发布其 exact `Holder.Reference` 后才写最终
volatile value；public `canonicalHolder()` 在 registrar seam 或注册前没有 reference 时失败。
registrar seam 仍可只绑定 value，prefix partial commit 与 batch terminal `FAILED` 语义不变。

Task 037 落地测试分区为 ordinary **67 suites/320 tests**、transfer **2/8**、isolated root **1/1**，
总计 **70/329**。Task 040 candidate 当前为 ordinary/transfer/root/data-map **73/352**、**4/21**、
**1/2**、**2/4**，aggregate **80/379**；API/common/platform **98/164/33**。remapped
JAR 的 direct `mekanism/fabric/registry/` family 必须精确七项：holder、register outer + `Entry` +
`Registrar`、既有 lifecycle、新 defaulted registry 与新 custom lifecycle；enum comparison 不使用
switch，实际产物无额外 synthetic class。

独立核对固定依赖而非只采信设计假设：Fabric API `0.116.17+1.21.1` 解析出的
`fabric-registry-sync-v0:5.3.2+e3eddc2119` 原始 binary/source SHA-256 分别为
`20b69b97f1f56c4e201e842ef2c0a2ab93f0728fbd3ca78034d4de7e4a12c5a7` 与
`9bfb198cd68fbc28a2275c40b51cddb812d015a1f4ea54f9f30d66e2112c8198`；Loom 生成的
official-mapped binary/source 副本会随重新生成而变化，因此不作为稳定 provenance pin。检查确认 builder 先写
attributes、默认总含 `MODDED`，随后把 exact instance 注册进 root；server `MainMixin` 在 mod init
后调用 `BuiltInRegistries.bootStrap()`。

vanilla 行为直接从 Minecraft `1.21.1` official-mapped compile JAR
`3a08db47199e250250d442bba772583748c4846d9df70b67428b7a502b7706a2` 核对：
`MappedRegistry` 的 value map 使用 identity、intrusive register 从 pending map remove 且缺失时
assert、freeze 先置 frozen 再检查 dangling holder；`DefaultedMappedRegistry.getOptional` 直接查
actual entry，不走 default fallback；built-in freeze 先 freeze root 再逐 registry freeze。隔离 worker
使用的 `fabric-loader-junit:0.19.5` binary SHA-256 为
`d8a34f60d0653d8be60673411f5e29125d709be298f9883bbdaf12412b03ec3c`，并显式设置 server side。

Task 037 本身是 prerequisite；其落地时没有 Chemical owner或 production root wiring。Task 040
现按下一节接入具体 owner，仍不引入 `MekanismAPI`/NeoForge facade、unfreeze/mixin bypass或通用
concurrent-read promise。既有 GameEvent startup proof 原样保留。

## Task 040 Chemical custom-registry owner

common bootstrap在其它依赖 Chemical 的 service/data-map初始化前调用
`FabricChemicalRegistryLifecycle.initialize()`。它创建 key `mekanism:chemical`、default
`mekanism:empty` 的 exact `FabricDefaultedRegistry<Chemical>`，注册 root并获得 MODDED+SYNCED，随后
发布 canonical empty value/holder并通过 official deferred declarations注册 32 named chemicals、
18 pigments、14 dirty/clean slurries。production 在 construction thread 主动完成 first freeze，避免
异步 reload worker成为首次 freeze owner；worker之后只能命中 already-FROZEN 幂等 path。

验证不依赖 `keySet()` iteration order：empty raw ID必须为0，完整 official declaration list逐项 raw ID
必须为1..64，每个 resource name/value/deferred holder/canonical reference exact identity一致。raw ID只作
startup/test assertion；Fabric registry synchronization执行 client remap，Chemical data snapshot使用
resource name。隔离 root test现第二个方法在 real Fabric root中重复完整 order/canonical/SYNCED/frozen
assert；packaged server要求 Chemical data-map startup proof，real Chemical gate同时证明server/client
65-entry names/remap。完整 source/resource/network evidence见 `FABRIC_CHEMICAL.md`。

## 既有 built-in GameEvent owner

官方 `MekanismDeferredRegister` 与 `MekanismDeferredHolder` 已组合既有 adapter，
`GameEventDeferredRegister` 和 `MekanismGameEvents` 通过 `fabric/common-slice.txt` 审计后直接参与
Fabric 构建；Task 031 又纳入 sound/damage declaration closure，但尚未把这两个 owner 接入
entrypoint。临时 `MekanismFabricGameEvents` 已删除。当前 entrypoint 仍只同步注册并验证四个官方
GameEvent 声明：

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
