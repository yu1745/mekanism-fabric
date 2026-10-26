# Fabric API 差异登记

本文件登记相对官方 Mekanism `v1.21.1-10.7.19.85` 公共 API 的有意变更。每项差异必须说明兼容影响、平台原因、参考实现和验证；未登记的公共签名变化视为回归。

## `mekanism.api.Action`

状态：已移植，Fabric API allowlist 中启用。

### 保留

```text
EXECUTE / SIMULATE
execute()
simulate()
combine(boolean)
get(boolean)
```

枚举名称、顺序和以上行为保持官方一致。

### Fabric 变更

移除两个直接泄漏 NeoForge 类型的方法：

```java
FluidAction toFluidAction()
static Action fromFluidAction(FluidAction action)
```

以及内部 `IFluidHandler.FluidAction` 字段。Fabric 侧不为这两个方法伪造 `net.neoforged.neoforge.fluids.capability.IFluidHandler`。调用它们的官方 fluid handler 将在移植时改为由边界 adapter 处理事务。

兼容影响：依赖上述两个 NeoForge 专用方法的 addon 源码需要改用 Fabric transfer adapter；只依赖 Mekanism `Action` 的执行/模拟语义无需改变。NeoForge addon 二进制兼容不在 Fabric 目标范围内。

### 语义依据

- Kilt `9e8f0d67411f10234bf0d864bd151a6dbcef14b4`
  - `upstream/kilt/compat/transfer-api-compat/src/main/kotlin/xyz/bluspring/kilt/compat/transfer/fluid/FabricFluidStorageCapability.kt`
  - `upstream/kilt/compat/transfer-api-compat/src/main/kotlin/xyz/bluspring/kilt/compat/transfer/fluid/NeoForgeFluidStorage.kt`
- Connector `8b27f1ad042aae8037bcc522b321c03fcce1a12a`
  - `upstream/connector/src/mod/java/org/sinytra/connector/mod/compat/FluidHandlerCompat.java`

Kilt 的转换表明 execute/simulate 应在 transfer 边界映射为 transaction commit/abort，反向包装则先模拟、在最终提交时执行。Connector 的 fluid 映射也把 Fabric `FluidVariant` 与 NeoForge `FluidStack`/`FluidType` 转换限制在兼容边界。由此本项目选择让 `Action` 保持纯领域枚举，把 transaction 和 fluid value 转换留给后续 Mekanism 专用 adapter，而不是实现通用兼容层。

### 验证

- `fabric/src/test/java/mekanism/api/ActionTest.java`
- `./gradlew -p fabric verifyApiSliceNoNeoForge`
- `./fabric-build.sh`
- `scripts/fabric-packaged-smoke.sh`

## Mekanism-owned `FluidStack` value (Task 034)

状态：Task 034 落地时 API/common/platform allowlist 精确为 **57/122/17**；
Task 035 后为 **58/122/17**，Task 036 后当前为 **64/122/17**。`mekanism.api.fluid.FluidStack` 是 final、
项目自有、平台中立的 mutable
`int` mB value；`FluidVariant` 与 Fabric Transfer units 只出现在新增的
`mekanism.fabric.fluid.FabricFluidConversions`。不创建 `net.neoforged.*`
shim，也不把 API domain 改成 droplet `long`。

### 保留 surface 与行为

保留 holder/fluid 的 `int` constructors、optional `DataComponentPatch`、public
`EMPTY`、`DataComponentHolder`/`PatchedDataComponentMap`、raw
amount/copy/split/clear family、tag/fluid/holder/holder-set/predicate queries、
matching/hash helpers、identity `Object.equals/hashCode`、
`lenientOtionalFieldOf` 拼写、registry-aware parse/save、JSON/NBT/stream
formats、toString，以及不需 capability 的 deprecated display/equality/containment
methods。NeoForge `MutableDataComponentHolder` nominal parent 不可用，但 callable
platform-neutral parity 已恢复：direct/Supplier `set`、direct/Supplier `remove`、
BiFunction 与 UnaryOperator 两组 direct/Supplier `update`，以及
DataComponentType/Supplier 两组 varargs `copyFrom` 和两种 `applyComponents`。
`update` 精确经 `getOrDefault`/`set`，`copyFrom` 在 source 缺值时删除 target
值。empty 精确为 identity EMPTY、`Fluids.EMPTY` 或 raw amount `<= 0`；observer
返回 empty fluid/zero/empty patch。

`FLUID_NON_EMPTY_CODEC` 排除 `minecraft:empty`；lazy `CODEC` 精确使用 required
`id`、无 default 的 positive `amount`、optional empty `components`。
optional stream 先写 VAR_INT amount，decode `<= 0` 直接 canonical EMPTY，再写
fluid holder 与 patch；strict empty exceptions 精确为
`Empty FluidStack not allowed`。save empty message 精确为
`Cannot encode empty FluidStack`。NeoForge diagnostic logging wrapper 删除，vanilla
codec 的 data/result/exception behavior 不改。

### Fabric adaptation

`getDescriptionId()` 固定为
`getFluid().defaultFluidState().createLegacyBlock().getBlock().getDescriptionId()`，
`getHoverName()` 仍是该 ID 的 translatable component。测试固定 WATER/LAVA/EMPTY
为 `block.minecraft.water`/`block.minecraft.lava`/`block.minecraft.air`，并以
flowing water 固定一个无需自造 registry 的代表性 block-fluid 行为。精确
NeoForge 21.1.200 built-in registrations 对 water/lava 使用相同 explicit block
IDs，因此内建行为保持 parity，不存在先前误记的 `fluid.*` delta。剩余差异是
Fabric 没有 nominal `FluidType`，所以 custom `FluidType` description override
不可用；Fabric convention 明确为 block-derived。

刻意删除 `getFluidType()`、`is(FluidType)` 与 deprecated
`isFluidEqual(ItemStack)` capability bridge。Task 034 落地时所有既有 NeoForge FluidStack
imports 及 callers 均未替换；Task 035 只替换 `SerializerHelper` 的一处 import；完整 provenance、单位 seam、测试与延期边界见
`docs/FABRIC_FLUID.md`。

## Shared serialization owner (Task 035)

状态：`mekanism.api.SerializerHelper` 已加入 API allowlist；这是具体 slot 调用点与
item/fluid codec 的共享 owner，不是 registry、config、lifecycle 或 persistence owner。
Task 035 是经 concrete-slot scouting 后批准的两份紧耦合 production-source 架构例外：
新增 admission 为 `SerializerHelper`，另一份只修改已纳入的 `IInventorySlot`。

相对 oracle `4646f509`，`SerializerHelper` 保留全部 11 个 public codec fields、6 个
public methods、signatures、codec field/default/error semantics、legacy floating-long
unsigned/clamp/decimal behavior、`promotePartial(...).orElse(...)`、oversized item
components/count、两个 conditional map codec 与 `stringify`。production delta 精确为：

1. NeoForge `FluidStack` import 换成既有 `mekanism.api.fluid.FluidStack`；
2. 为避免未完成 `MekanismAPI` facade 的 eager class initialization，两个
   `MekanismAPI.logger.error(...)` call 由一个 private
   `LoggerFactory.getLogger("mekanism.api.MekanismAPI")` SLF4J logger 承接。

第二项是唯一非 import adaptation。logger category 精确保持
`mekanism.api.MekanismAPI`，level 保持 error，消息模板精确保持
`Tried to load invalid item: '{}'` 与 `Tried to load invalid fluid: '{}'`；不改用
caller-derived category，也不新增 public logger。`SerializerHelper` 的 static codec family
仍在首次类引用时 eager 初始化，因此普通测试在首次引用前执行 vanilla
`SharedConstants`/`Bootstrap`；Fabric startup entrypoint 不引用该类，发布物不含
`MekanismAPI.class` 或 serialization startup marker。

`IInventorySlot` 删除 Task 033 的 private oversized codec 与仅由它使用的 imports，并
恢复 exact `SerializerHelper.saveOversized(provider, getStack())` delegation。Task 033 的
Mekanism-owned NBT nominal parent、两处 Javadoc prose 以及全部 public/default methods
保持。兼容效果是共享 codec owner 恢复；无新增依赖、common/platform source、chemical
behavior、concrete slot、menu、recipe、warning 或 broad facade。

验证由恰好五个 `SerializerHelperTest` methods 与 unchanged 四个
`InventoryApiHolderTest` methods 提供；Task 035 落地 totals 为 ordinary **65/310**、Knot transfer
**2/8**、aggregate **67/318**；Task 036 后当前为 **66/315**、**2/8**、**68/323**，
三项 jqwik property 各 100,000。JAR family 精确为 `SerializerHelper.class` 加
`$1`/`$2`/`$3`，各一次；unexpected nested/test/helper、`MekanismAPI.class`、
NeoForge/Loader leakage 与 startup marker 均拒绝。

## Portable text/color prerequisite closure (Task 036)

状态：API/common/platform allowlist 当前为 **64/122/17**。新增 API admissions 精确为
`IIncrementalEnum`、`IHashedItem`、`APILang`、`EnumColor`、`ILangEntry` 与
`TextComponentUtil`；同时恢复已纳入的 `IHasTranslationKey`、`RelativeSide` 两个 caller。
`IHashedItem`、`EnumColor`、`ILangEntry` 与 oracle `4646f509` byte-identical，
`RelativeSide` 恢复 oracle-byte-identical 的 APILang/ILangEntry field、constructor 与 delegation。
无 common/platform production source 或 dependency change。

有意 delta 精确为：`IIncrementalEnum` 只移除 `ConstantPredicates` import，并在两个
无参 default 中传入不逃逸的 stateless `ignored -> true`；`APILang` 只以 literal
`"mekanism"` 替代 private constructor 的 `MekanismAPI.MEKANISM_MODID`；
`TextComponentUtil` 只把 NeoForge FluidStack 换成 project-owned value，并把两处 raw
`Fluid` name 改为 default-state legacy-block description ID 的 translatable component。
此外 vanilla `Level` 不含 NeoForge 注入的 `getDescription()`，故原两个 dispatch position
精确展开为 `dimension().location().toLanguageKey("dimension")` 加 dimension-ID fallback 的
`Component.translatableWithFallback`。所有 public methods、dispatch order、bound-holder/null/
style/copy 以及 `smartTranslate` primitive/string/trailing-format behavior 保持。

`IHasTranslationKey.IHasEnumNameTranslationKey` 保持已批准的 NeoForge
`TranslatableEnum` nominal-parent/Javadoc removal，但 default body 恢复
`TextComponentUtil.translate(getTranslationKey())`。`RelativeSide` 六项再次直接持有相应
APILang entry；serialized names、keys、codecs 和 direction behavior 不变。Fabric 没有
`FluidType` description facade，因此 raw-fluid custom `FluidType#getDescription()` override
不可保留；与 Task 034 一致只承诺 block-derived key，不发明 custom-name facade。

一个新 ordinary class 恰好五个 methods；加 unchanged `RelativeSideTest` 固定 key ownership、
方向/codec 与 text/color behavior。当前预期 ordinary **66/315**、transfer **2/8**、aggregate
**68/323**，jqwik 三项各 100,000。实际 remapped family 为 `IIncrementalEnum`、`IHashedItem`、
`APILang`、`EnumColor`、`ILangEntry`、`TextComponentUtil` outer + `$1`，以及既有 exact
RelativeSide/IHasTextComponent/IHasTranslationKey families，各一次。static gate 拒绝
`MekanismAPI`、`ConstantPredicates`、chemical API、`TriPredicate`、concrete `HashedItem`、
unexpected nested/test/helper/fixture、NeoForge/Loader 与 startup marker/reference；无 startup claim。
完整边界见 `docs/FABRIC_TEXT.md`。

本任务只关闭 portable text prerequisites。`ChemicalStack`、完整 `ConstantPredicates`、warning、
menu、concrete slots、recipes、translation consumers 与所有 owners/callers 仍延期。

## Module config value contracts (Task 032)

状态：Task 032 落地时 API/common/platform allowlist 精确为 **53/120/14** unique sources；common、platform 与 18-file official root-test oracle 不变。新增六份官方 API source 中，`ModuleBooleanConfig`、`ModuleColorConfig` 与 `IHasModeIcon` 和 `4646f509` byte-identical；其余三份仅作以下封闭适配：

- `ModuleConfig` 只删除 `MekanismAPI` import，并以 literal `"mekanism"` 构造三个 canonical key 及 legacy name。`baseCodec` 保留官方行为：decode 时仅把 `minecraft:<path>` 改写为 `mekanism:<path>`，其它 namespace 原样保留；encode 始终写出 config 当前的 exact name。
- `ModuleEnumConfig` 只以 method-local vanilla `StreamCodec` 替换 NeoForge `enumCodec`。它捕获 declaring enum constants，并通过 `ByteBufCodecs.VAR_INT` 写 `ordinal()`、读 ordinal 后直接数组索引，因此 wire bytes 不变；负数或过高 ordinal 继续抛 `ArrayIndexOutOfBoundsException`，null encode 继续抛 `NullPointerException`。没有 anonymous/new packaged class、platform source 或通用 shim。
- `IHasTextComponent.IHasEnumNameTextComponent` 删除 NeoForge `TranslatableEnum` 父型、import 与失效的 `@Override`；public default `Component getTranslatedName()` 及其 exact `return getTextComponent();` body 保留。直接 Mekanism source/binary method behavior 不变，但依赖 NeoForge subtype assignment 的 addon 必须迁移；Fabric 不提供 shim。

官方 module value behavior也保持：boolean codec/stream/name/value 与 immutable `with`；color 的 RGB 构造/codec/stream 强制 alpha 为 `0xFF`、ARGB 保留全部 signed 32-bit bits，两者继续分别使用 VAR_INT 与 fixed INT；enum selectable count 对 `<= 0`、大于 enum length 和 out-of-range bounded initial value的失败、equal-count 的 unlimited 形态、subset immutable option list、data-codec validation 及 supplied-stream behavior 均未修改。特别地，bounded config 的 changed `with(TYPE)`/`with(int)` 仍经官方两参数 constructor 返回 full enum option list；没有“修复”这一既有行为。

一个 Fabric-local same-package class恰有七个 `@Test`，使用真实 Mojang codecs 与 Netty/`FriendlyByteBuf`，固定 canonical/legacy names、boolean/color data 与 exact wire forms、RGB/ARGB、enum bounds/validation、named VAR_INT bytes及异常、官方 bounded `with`、equality/hash/class/count，以及三个 text/icon interface 的 exact component/icon identity。静态 JAR gate 要求 direct `mekanism/api/gear/config/` 精确只有五个 production classes 各一次，并要求 `IHasTextComponent` prefix 精确只有 outer 与 `IHasEnumNameTextComponent` 两项；拒绝 nested/synthetic/test/helper/fixture 泄漏且无 startup marker。

`ModuleData`、module containers/items、codec catalog、GUI/radial/client rendering、packets/network owner integration、persistence/data components/registration/lifecycle 与全部 callers 均延期。

## `mekanism.common.util.RegistryUtils`

状态：作为内部 common utility 纳入；仅将 NeoForge 注入到 vanilla 类型上的两个 convenience contract 改写为 vanilla 1.21.1 API，公共签名和返回值语义不变。

两个 `Holder.getKey()` 调用经私有 `getHolderKey` 改用 `unwrapKey().orElse(null)`。vanilla 的 key 未绑定 intrusive `Holder.Reference#unwrapKey` 会抛异常，而 NeoForge override 直接返回 nullable 字段；helper 只对 vanilla `Holder.Reference` 捕获这一状态并恢复 null。由此 keyed/reference、direct、key-only standalone 与 value-only intrusive 四种 holder 状态均保留原行为。built-in registry 扫描中的 `Registry.getKeyOrNull(element)` 则经窄泛型 helper 改为 `getResourceKey(element).map(ResourceKey::location).orElse(null)`；刻意不使用 `Registry.getKey`，因为 defaulted registry 会把未知对象误报为默认 key。direct-holder 分支仍立即返回而不扫描其 value。

语义依据：NeoForge 21.1.200 的 `IHolderExtension#getKey` 与 `IRegistryExtension#getKeyOrNull` contract，以及同版 patched Minecraft 源码 `upstream/neoforge-minecraft/net/minecraft/core/Holder.java`、`Registry.java`、`DefaultedMappedRegistry.java`；Kilt commit `9e8f0d67411f10234bf0d864bd151a6dbcef14b4` 的 `upstream/kilt/src/main/java/xyz/bluspring/kilt/injects/core/HolderInject.java` 与 `DefaultedMappedRegistryInject.java`。Fabric-local 测试验证 built-in canonical holder/key、NBT ID 拒绝路径、四种 holder 状态、direct-holder fallback/short-circuit 与 raw registry lookup；不声明 registry lifecycle、mutation、custom registry 或任何 caller 已移植。

## Deferred registry wrapper surface

状态：common wrapper 已移植，用于官方 GameEvent，并已扩到 Task 031 的 sound/damage declaration closure。

`MekanismDeferredRegister<T>` 不再继承 NeoForge `DeferredRegister<T>`，而是组合
`FabricDeferredRegister<T>`；保留 Mekanism 自身使用的两个 typed `register` 重载，并新增通过
vanilla `Registry<T>` 提交批次的入口。`MekanismDeferredHolder<R, T>` 不再继承 NeoForge
`DeferredHolder<R, T>`，而是组合 Fabric holder，同时直接实现 vanilla `Holder<R>` 与
`Supplier<T>`。`get/getKey/key/getId/getName/is/keyMatches`、绑定前 key 可用性、绑定后 canonical
对象身份均保留；绑定前读取值继续明确失败。

Task 031 的 `SoundEventDeferredRegister` 用既有 `MekanismDeferredRegister<SoundEvent>` 取代 NeoForge
父类，并在构造器传入 `SoundEventRegistryObject::new`；三种 registration behavior/typed return 保留，
`createHolder` 与旧 imports 删除，无 shim。`SoundEventRegistryObject` 与 `DamageTypeRegister` 仍和
`4646f509` byte-identical。`MekanismSounds`/`MekanismDamageTypes` 仅删除尚未纳入的完整
`Mekanism` owner import，并以 literal `"mekanism"` 替代 `Mekanism.MODID`，声明顺序和值不变。

`MekanismDamageType` 唯一变化位于 private holder lookup：NeoForge 注入的
`registryAccess.holderOrThrow(key())` 被 vanilla 语义展开
`registryAccess.lookupOrThrow(key().registryKey()).getOrThrow(key())` 取代。该表达式就是 NeoForge
21.1.200 `IHolderLookupProviderExtension.holderOrThrow` 的 lookup chain，保持 registry 或 key 缺失时的
`IllegalStateException`；没有 import、公开 API 或其它 method drift。

兼容影响：依赖 NeoForge `DeferredRegister`/`DeferredHolder` 继承关系或其额外方法的 addon
需要迁移到 Fabric/vanilla 注册 API。Mekanism 自身面向 holder/value/key 的公共用法保持源码级
表面。当前只承诺 `fabric/common-slice.txt` 已纳入的闭包；后续每引入一种 registry wrapper，
都必须补足实际调用到的 holder 语义，而不是伪造整个 NeoForge registry API。

语义依据与验证见 `docs/FABRIC_REGISTRY.md`、
`fabric/src/test/java/mekanism/common/registration/MekanismDeferredRegisterTest.java`、
`SoundAndDamageRegistrationTest.java` 以及 static packaged-JAR gates。Kilt/Connector 与 NeoForge
21.1.200 extension source 仅用于核对生命周期、冻结、canonical holder 和 lookup 语义；实现未复制。
当前 **64/122/17** slice 不把 sound/damage owners 接入 entrypoint。

## Config contract

状态：基础 spec/manager、primitive 与 generic/resolvable/map cached value、Oredictionificator 编码值及官方 startup config 已移植；real config owner 仍未激活。

`IMekanismConfig` 的 `ModConfigSpec` / `ModConfig.Type` 签名分别替换为
`MekanismConfigSpec` / `MekanismConfigType`；`IConfigTranslation.applyToBuilder` 改接
Mekanism-owned builder，并直接声明原有 `getTranslationKey()`，不再通过尚含 NeoForge
`TranslatableEnum` 的 `IHasTranslationKey` 继承。`MekanismStartupConfig` 构造器改为 public，供
Fabric bootstrap 在完整 `MekanismConfig` 纳入前构造唯一 startup 实例。Cached int/float 的公开
读写方法与 listener 语义不变，内部 backing value 类型改为 `MekanismConfigSpec.ConfigValue`。

Task 030 的四个 common source 只替换 config backing types：`CachedResolvableConfigValue`、`CachedConfigValue`、`CachedMapConfigValue` 各把 NeoForge nested `ConfigValue` import 改为 `MekanismConfigSpec.ConfigValue`；`CachedOredictionificatorConfigValue` 只把 `ModConfigSpec`/nested `ConfigValue` imports 和 builder/value types 换成 `MekanismConfigSpec` 等价项，方法体不变。它们因此不是 byte-identical。Fabric builder 新增唯一 strongly typed `defineListAllowEmpty`，保留 empty list，整表拒绝 non-list/null/invalid element，并对一次求值且立即验证的 default 与每份 accepted raw/set list 作 immutable top-level copy。new-element supplier non-null 但因没有 UI/editor 而从不调用；既有 path/metadata/freeze/validation/correction/persist-before-publish/synchronization pipeline 继续负责生命周期。

兼容影响：直接依赖 NeoForge config spec/type 或 `IConfigTranslation` 到
`IHasTranslationKey` 赋值关系的 addon 必须改用 Fabric 侧契约。TOML 文件名、12 个 startup
路径、默认值、范围、restart 元数据与缓存行为保持。官方 resolvable `set` 仍缓存 caller 的原始 `TYPE`：encoded list backing 是 immutable copy，但 map 在 clear/reload 前仍可与 caller alias；clear/reload 后才按 deterministic sorted strings 重建。当前 **64/122/17** slice 不激活 `GeneralConfig`/`MekanismConfig`、cardboard blacklist、Oredictionificator filters/items、`CachedEnumValue`、UI/watch/sync 或 config ownership/lifecycle/threading。细节和验证见 `docs/FABRIC_CONFIG.md`。

## Registry-aware NBT contract

状态：基础契约、`DataHandlerUtils` 与首个 `IEnergyContainer`/`BasicEnergyContainer` 闭包已移植；其余容器接口将在各自依赖闭包可编译时逐项切换。

新增 Mekanism-owned 公共契约：

```java
mekanism.api.INBTSerializable<T extends Tag>
```

它保留 NeoForge 1.21.1 接口的两个注册表感知方法：

```java
T serializeNBT(HolderLookup.Provider provider)
void deserializeNBT(HolderLookup.Provider provider, T tag)
```

`DataHandlerUtils` 仅把泛型约束从 NeoForge 接口切换为上述 Mekanism 接口；列表顺序、byte 索引、空标签跳过和 NBT 键格式保持官方行为。`IEnergyContainer` 与 `IHeatCapacitor` 已随各自首个 storage 闭包切换；后续 `IChemicalTank`、`IExtendedFluidTank`、`IInventorySlot` 的父接口变化必须随各自完整依赖闭包迁移，不能为了扩大 allowlist 添加 stub。

兼容影响：实现容器接口的 Fabric addon 将实现 `mekanism.api.INBTSerializable`，而不是 NeoForge 父接口；序列化方法本身保持同名同参。Attachment、Data Component、Codec 和 Fabric 生命周期不进入这个值级契约，持久化 owner 负责传入 `HolderLookup.Provider`。

参考：

- Kilt `9e8f0d67411f10234bf0d864bd151a6dbcef14b4`
  - `upstream/kilt/src/main/java/xyz/bluspring/kilt/injects/world/level/block/entity/BlockEntityInject.java`
  - `upstream/kilt/src/main/java/xyz/bluspring/kilt/injects/world/entity/EntityInject.java`
  - `upstream/kilt/src/main/java/xyz/bluspring/kilt/injects/world/level/chunk/ChunkAccessInject.java`
- Connector `8b27f1ad042aae8037bcc522b321c03fcce1a12a` 没有提供可直接采用的同类 NBT/attachment 契约，因此不据此构造兼容层。

验证：

- `fabric/src/test/java/mekanism/api/DataHandlerUtilsTest.java`
- `./gradlew -p fabric verifyApiSliceNoNeoForge`
- `./fabric-build.sh`
- `scripts/fabric-packaged-smoke.sh`

## Item inventory contracts (Task 033)

状态：Task 033 落地时 API/common/platform allowlist 精确为 **56/122/16**；Task 034 后为 **57/122/17**，Task 035 后为 **58/122/17**，Task 036 后当前为 **64/122/17**。三份 inventory API source 中，`IMekanismInventory` 与 `4646f509` byte-identical；另两份只有以下有界平台适配。

### `IInventorySlot`

NeoForge `INBTSerializable<CompoundTag>` nominal parent 换成既有 `mekanism.api.INBTSerializable<CompoundTag>`。两个仅供 Javadoc 的 item/fluid links/imports 换成平台中立文字；所有 public methods、default bodies、listener、amount/limit、oversized stack 与 nullable container-slot behavior 均保留。

Task 033 因当时 `FluidStack` 尚未纳入而把唯一 shared-helper call 暂时换成 private method-local codec。Task 035 在 shared owner 可闭合后删除该 private codec 和仅由它使用的 imports，并恢复 `SerializerHelper.saveOversized(provider, getStack())`。共享 `OVERSIZED_ITEM_CODEC` 仍精确使用 non-air item holder `id`、positive int `count` default 1、optional empty `DataComponentPatch` `components` 和 registry-aware `NbtOps`；empty stack 仍抛 exact `IllegalStateException("Cannot encode empty ItemStack")`。oversized count/components 与全部既有 inventory behavior 不变。

兼容影响：Fabric implementation 改为实现 Mekanism-owned registry-aware NBT nominal type，而不是 NeoForge nominal type；两个 serialize/deserialize method signatures 不变。依赖 NeoForge parent assignment 的 addon 需迁移。

### `ISidedItemHandler`

删除 NeoForge `IItemHandlerModifiable` import/parent 和因此失效的六个 `@Override`，并只重写 stale Forge/fluid Javadocs。每个既有 unsided compatibility method、每个 sided method、public signature、nullable internal side 和 exact `boolean simulate -> Action.get(!simulate)` body 全部保留，无方法增删。

兼容影响：Mekanism-visible source method surface 保留，但 Fabric 上不再可把对象 nominally 赋给 NeoForge `IItemHandlerModifiable`；不提供伪 NeoForge shim。

`IMekanismInventory` 的 invalid-index defaults、`AutomationType.handler(side)`、side routing、live lists 和 `hasInventory` implementation contract 未改变。行为与 package gates 见 `FABRIC_ITEM.md`、`InventoryApiHolderTest` 和 `FabricItemStorageTest`。

## Client window and concrete slot contracts (Task 039)

状态：Task 039 后 API/common/platform slice 为 **65/145/24**。`SelectedWindowData` 的 public constructors、fields、records、enum order、codec、equals/hash 与 methods 保持官方 surface；内部 owner 从完整 `MekanismConfig.client` 改为不含 Fabric/client import 的 `ClientWindowConfigAccess`。crafting window 上限以经审计的 `(byte) 3` 编译常量替代未纳入 QIO holder，resource IDs 改用 vanilla `ResourceLocation.fromNamespaceAndPath("mekanism", ...)`。适用窗口 pre-init/post-close/wrong-thread 现在明确失败，而 `UNSPECIFIED` 继续返回 default 且不访问配置；这是 Fabric physical-client owner 的显式生命周期 contract，不提供隐式 fallback。

Basic/Input/Output slot 的 public surface 与 domain behavior 不变；仅把 private non-escaping `ConstantPredicates` 调用换成 exact lambda。Armor/Offhand 不依赖 NeoForge-injected slot background extension，改由 vanilla `getNoItemIcon()` 返回相同 atlas/texture pair。Armor placement 使用 owning player 的 vanilla `getEquipmentSlotForItem(stack) == slotType`；因此 vanilla/Fabric equipment slot 保持，但 arbitrary NeoForge item `canEquip` hook override 不具 parity，也不提供 facade。完整 owner、路径、事务/测试与延期见 `FABRIC_CONFIG.md`、`FABRIC_ITEM.md`。

## Container callback contract admission

状态：当前 API/common/platform allowlist 为 **47/99/14**。`ContainerInteraction`、`InContainerGetter`、`IntContainerInteraction` 与 `LongContainerInteraction` 原样加入，均与官方 oracle `4646f509` byte-identical；连同既有 `InContainerGetterLong`、`LongToLongContainerInteraction`，这六个 functional interfaces 的 FQN、泛型、nullable side、signed amount、`Action` 与 return contracts 都没有 API delta。一个 Fabric-local 同包测试类中的恰好四个 methods 只固定 identity/raw-argument forwarding；发布物 exact-package gate 拒绝其它 direct/nested/synthetic/test/helper/fixture class，不使用 startup marker。

这是 callback contracts admission，不是实现迁移。`ChemicalUtils`、`ExtendedFluidHandlerUtils`、chemical/fluid handlers、component-backed/recipe-upgrade owners、actual transfer/platform transactions、registries、serialization/networking 与 lifecycle/thread ownership 均延期。

## Energy conversion admission

状态：`mekanism.api.energy.IEnergyConversion` 已加入 Fabric API allowlist，源码与官方 oracle commit `4646f509` byte-identical；这不是 API 变更。

`isEnabled`、`getConversion`、`convertFrom`、`convertTo`/`convertToDouble`、deprecated `convertToAsLong`、`convertToAsInt` 与 `isOneToOne` 的签名、double rounding 和 saturation 均未修改，包括 exact-1 long 经 double 后对 odd `>2^53` 的官方精度损失。Task 023 只在 `mekanism.fabric.energy` adapter 内把 exact `1.0D` 作为 authoritative identity contract 并旁路该损失；与 advertised 1:1 rate 冲突的 custom conversion override 不受支持。finite positive non-1 rate 仅报告经官方双向验证、两域都不越 bound 的 conservative fixed pair。固定候选族由全局 10-bit dyadic mesh，以及 canonical-decimal continued-fraction 每个 denominator 的 1..64 bounded multiplier closure 和 fitting binary shifts 组成；bounds 只做过滤；对 documented stable conversion behavior，取最大 verified eligible member，因此 deterministic 且对两域 bound 分别 monotone。cold family 在 sort/dedup 前最多 399,103 occurrences，verified primitive snapshot 按 conversion exact identity + raw rate bits 放入 bounded four-slot weak cache；bit 变化重建、enabled 每次实时读取、distinct implementation 不按相同 rate 合并。warm bound selection 为 `O(log U)` 且每个返回 pair 仍经 owner conversion 双向复验；empty requested window 在 snapshot 非空时复验首个 cached sentinel。zero-pair snapshot 无 sentinel，cache 以 owner 在 advertised rate bits 不变时保持 conversion behavior 稳定为前提。它不声明 arbitrary rate 的全局最大值或通用 utilization 下限；换算对象由 caller 提供，不把第三方类型、硬编码 2.5 或 FE 等价写进 Mekanism API。

## Energy container contract

状态：内部 energy storage 首个垂直切片及首个外部 Team Reborn Energy lookup/transaction adapter 已移植；实际 provider/owner lifecycle 尚未实现。

`IEnergyContainer` 的 NeoForge `INBTSerializable<CompoundTag>` 父接口替换为上文的 Mekanism-owned registry-aware 契约；energy getter/setter、execute/simulate、automation、listener、capacity 和 NBT 方法签名保持。仅删除了指向 NeoForge fluid handler 的 Javadoc 类型链接，改用平台中立文字描述同一返回值差异。

`IStrictEnergyHandler` 与 `ISidedStrictEnergyHandler` 同样只删除两个 NeoForge fluid import，并把仅用于 Javadoc 的 `IFluidHandler#fill(...)` 比较链接改为平台中立文字：fluid fill 返回 accepted amount，而 Mekanism energy insertion 返回 unaccepted remainder。可执行代码、公开签名、default-method 控制流、annotation 与其余 Javadoc 含义均不变；`IMekanismStrictEnergyHandler` 与官方基线字节一致。兼容影响：无（仅 Javadoc/source import 清理，删除的类型未出现在公开签名或字节码行为中）。

`BasicEnergyContainer` 保留官方 public factory/predicate 表面与行为。为避免引入 chemical/fluid 和 broad NBT helper 闭包，`ConstantPredicates.alwaysTrue()` 改为类内缓存 predicate，`NBTUtils.setLegacyEnergyIfPresent` 的 `stored` long/legacy string 读取语义窄化到类内；Guava 参数检查改为同类型的显式 `IllegalArgumentException`。兼容细节、暂缓边界和验证见 `docs/FABRIC_ENERGY.md` 与 `fabric/src/test/java/mekanism/common/capabilities/energy/BasicEnergyContainerTest.java`。

## Heat container contract

状态：内部 heat storage、官方 handler aggregation 与非配置 holder 已移植；config holder/helper、网络、机器 owner 与外部边界尚未实现。

`IHeatCapacitor` 的 NeoForge `INBTSerializable<CompoundTag>` 父接口替换为上文的 Mekanism-owned registry-aware 契约；其 getter/setter、batch update、listener、capacity 和 NBT 方法签名保持。

`BasicHeatCapacitor` 为避免引入 broad `NBTUtils` 闭包，局部实现 `stored` 与 `heat_capacity` 的 exact `TAG_DOUBLE` 读取；缺失/错误类型忽略、加载不通知、pending heat 保留等语义不变。`HeatAPI` 本身与官方基线字节一致；Fabric access widener 仅镜像官方 access transformer 对 `Biome.getTemperature(BlockPos)` 的 public access，以保留位置修正温度。`IHeatHandler`、`ISidedHeatHandler`、`IMekanismHeatHandler` 与两种 heat holder 类型未作源码修改。兼容细节、暂缓边界和验证见 `docs/FABRIC_HEAT.md` 与对应测试。

## Relative-side translation contract

状态：`RelativeSide` 与非配置 energy holder 闭包已移植。

`RelativeSide` 保持 `mekanism.api.RelativeSide` 类型身份、枚举顺序、serialized names、translation keys、`CODEC`、`BY_ID`、`STREAM_CODEC` 和全部方向映射。Task 036 已恢复 oracle 的 APILang/ILangEntry representation：六项持有对应 entry，constructor 保留 serialized name，并由 `getTranslationKey()` 精确 delegation 得到同一 `side.mekanism.*` key。

`IHasTranslationKey.IHasEnumNameTranslationKey` 保持原 FQN、父级 Mekanism interface 和 `getTranslatedName()` 方法，但不再继承 NeoForge `TranslatableEnum`；这是既有、已批准的 Fabric nominal-parent 变化。Task 036 将 default body 从临时 direct `Component.translatable` 恢复为 oracle 的 `TextComponentUtil.translate(getTranslationKey())`。依赖 NeoForge subtype 关系的 addon 仍需迁移到 Mekanism-visible interface；Fabric 不提供 shim。

`IHolder`、`BasicHolder`、`IEnergyContainerHolder` 与 `EnergyContainerHolder` 未改公开/可见性表面；方向过滤、内部访问、动态 facing、identity/order、duplicate 与 live-list 行为保持。详见 `docs/FABRIC_HOLDERS.md` 及对应测试。

## `mekanism.api.datamaps.MekaSuitAbsorption` (Task 038)

状态：Task 038 落地时作为唯一直接 `mekanism.api.datamaps` admission 启用。相对官方
`v1.21.1-10.7.19.85`，唯一 executable delta 是 `ID` 的 namespace 来源由
`MekanismAPI.MEKANISM_MODID` 改成稳定 literal `"mekanism"`，从而不触发尚未纳入的 broad facade。
record identity、constructor、`absorption()`、ID path、object-first `CODEC`、bare-float alternative、
`[0,1]` codec 与 constructor rejection、Javadoc/public surface 均保持。Task 038 当时未新增 public
descriptor；Task 040 后该历史边界由下一节取代。

Fabric loader/session 不伪造 `net.neoforged.*`。ordinary、unconditional、server-side、non-synced
MekaSuit absorption 语义保持；Task 040 在同一 loader 上增加 Chemical descriptor/context/sync，而
conditions、Advanced merger/remover、holder mixin与 NeoForge facade 仍延期。NeoForge 21.1.200 oracle
source/binary hash、资源 hash、layer/lifecycle/failure truth table、query 规则和验证见
`docs/FABRIC_DATA_MAPS.md`。

## Chemical registry, data maps, stack and tank contracts (Task 040)

状态：candidate slices **98/164/33**。Fabric API 以 `ChemicalRegistry` 精确拥有
`REGISTRY_ID`/`REGISTRY_KEY`/`EMPTY_KEY` 以及安装后 registry/canonical empty value+holder，取代
`MekanismAPI.CHEMICAL_*` 引用；不提供 partial `MekanismAPI`。`Chemical` 的 public codecs、parse/save、
constructors、legacy/modern attributes、translation/texture/tint、empty/type methods 保持，private logger
继续使用官方 category。NeoForge holder-injected data通过 `ChemicalDataContext` 与 selected service
按 owner thread实时派生；public/virtual `@Internal updateFromDataMap` 和 protected `trackAttribute` 仍参与
subclass behavior，overrider 必须调用 `super`。同一 Chemical 的重入查询读取已累积的 immutable partial
view，另一 Chemical 使用独立 nested frame。与 NeoForge cache-refresh lifecycle 不同，direct external
invocation 明确无效并由 base method 抛出，这是授权 Fabric delta；每个 top-level query 当前重新分配并做
五次 map lookup，Task041 在 recipe hot path 前必须解决且不得引入 shared mutable Chemical/global cache。
`trackAttribute` 参数型从 NeoForge `DataMapType` 精确替换为层中 `MekanismDataMapType`。

新 public `MekanismDataMapType<R,T>` 是 immutable identity descriptor，暴露 `registryKey()`、`id()`、
`codec()`、optional `networkCodec()` 和 `synchronizedToClient()`；不依赖 Fabric。`IMekanismDataMapTypes`
的六个 descriptor return types从 NeoForge 类型替换为此类型，并新增 current-context
`getChemicalData(Holder<Chemical>, MekanismDataMapType<Chemical,DATA>)`。`INSTANCE` 与
`ITooltipHelper.INSTANCE` 改由 `MekanismServices.load` 解析；恰好零个或多个 provider 都显式失败，
没有 no-op fallback。该 nominal type substitution是 Fabric addon 需要重新编译的明确 delta。

`ChemicalStack`/`BasicChemicalTank`/handler family 保留 true-long amount、canonical EMPTY、positive/
non-empty codecs、registry-aware optional NBT `{}`、amount-before-holder stream、copy/split/grow/shrink、
identity/matching、indexed/distributed insertion/extraction、validation、listener 与 deprecated singleton
predicate identity。`IChemicalTank` 仅删除 NeoForge capability Javadoc/supertype引用，不加入 Fabric
transaction接口；`IChemicalHandler`/`ISidedChemicalHandler` 同样只移除 fluid-capability documentation。
Wrapped reference holders按 key canonicalize 到 exact Chemical registry holder，以保证 codec 与 identity。

`ChemicalRadioactivity` 的 public baseline validation从未纳入的 `IRadiationManager.INSTANCE` 改由
`RadiationValidation.BASELINE == 1e-7` 与 current enabled policy；其 record/codec/tooltip surface保持。
`RadiationValidation` 不是 environmental radiation API。五个 Chemical data records只以 literal
namespace和 descriptor type替代 facade/NeoForge references，codec layouts保持。
`ChemicalBuilder`、`MekanismAPITags`、`IChemicalProvider`、`ConstantPredicates`、`ITooltipHelper` 的其余
public surface保持；`TriPredicate` 完整保留 test/and/or/negate与 null-check contract。

完整逐源 U/A/N/M/R report、资源/service、wire bounds、runtime evidence与 external provider/
transaction/ingredient/machine non-claims见 `docs/FABRIC_CHEMICAL.md`。

## Recipe ingredient, creator, input-handler and cache contracts (Task 041)

Task 041 admits the recipe ingredient/input/generic-cache layer with these bounded API deltas against `4646f509`:

- `IngredientCreatorAccess` 改从 `MekanismServices.load(IIngredientCreatorProvider.class)` 获取四个 creator
  singleton，不再引用 broad `IMekanismAccess`；新 interface `IIngredientCreatorProvider` 是层中性 service
  seam，由 `mekanism.fabric.recipe.FabricIngredientCreatorProvider` 实现并以唯一 service descriptor 打包。
- `ItemStackIngredient` 不再包装 NeoForge `SizedIngredient`：它直接拥有 vanilla `Ingredient` + positive
  count，可选 non-strict `DataComponentPredicate` 与 compound children list。`of(SizedIngredient)` 等
  NeoForge 形态 factory 由 `of(Ingredient,int)`、`of(List<Ingredient>,int)`、
  `of(Ingredient,int,DataComponentPredicate)` 取代；`@Internal ingredient()` 返回 vanilla `Ingredient`，
  新增 `@Internal ingredients()/hasComponentPredicate()/count()` 供 input cache 使用。flat codec 与
  stream codec 保持官方 JSON/wire 形态（tag 形态在 wire 上按 vanilla contents codec 展开为当前 stacks，
  与平台行为一致）。
- 新层中性 `mekanism.api.recipes.ingredients.fluid.FluidIngredient`（single holder/tag/compound/component
  predicate）与 `FluidStackIngredient`（predicate + positive int mB amount）取代 NeoForge
  `FluidIngredient`/`SizedFluidIngredient`；显式 empty 与当前解析为空的动态 tag 分离，flat codec 支持 official `neoforge:compound`/legacy alias 与
  `neoforge:components`，wire decode 保持 positive amount/非空 compound invariant；`IExtendedFluidTank` 声明原继承的平台方法并仅删除
  `IFluidTank`/`FluidAction` capability bridges，`IFluidProvider` 使用项目 `FluidStack` 与 fluid-state 命名。
- Chemical 六 ingredient 的适配限定为：`ChemicalRegistry` registry/empty-key 替换 `MekanismAPI` 访问、
  exact-category logger、以及三个 vanilla codec 组合 helper（xor/dispatch-or-else/aliased children
  field）。`ChemicalIngredientTypeRegistry` 新拥有六个内建 type ID 与 reverse codec identity，权威存储改为单一
  vanilla registry `mekanism:chemical_ingredient_type`；Fabric construction owner
  `FabricChemicalIngredientTypeRegistryLifecycle` 分配 plain（非 intrusive/defaulted）registry、安装、以 `SYNCED`
  发布到 vanilla root，`MekanismChemicalIngredientTypes` 在 bootstrap 一次按官方顺序注册并 freeze（duplicate/late/missing
  均失败，unknown ID 不静默 default）。
- `InputHelper` 仅替换项目 `FluidStack` 与 logger；`MekanismRecipe` 仅替换 logger。
  `CachedRecipe`/`CachedRecipeHelper`/`ICachedRecipeHolder`/`IInputHandler`/`ILongInputHandler`/
  `IOutputHandler`/`IInputCache`/`BaseInputCache`/`ComponentSensitiveInputCache`/`IInputRecipeCache`
  与 oracle byte-identical。
- Generic cache 族的构造依赖从 deferred `MekanismRecipeType` 改为 `RecipeCacheSource`；mutable
  initialized flags/indices/complex sets 移入 `RecipeCacheContext` 的 per-binding state；
  `InputRecipeCache.EitherSideChemical` 改为 generic `MekanismRecipe<?> & BiPredicate<ChemicalStack,
  ChemicalStack>` + 显式 extractors；`ItemInputCache`/`FluidInputCache` introspect 项目 ingredient 类型
  （compound children 尽力映射，component predicate 标记 complex）。
- `Chemical`/`ChemicalDataContext` 落地 hot cache：binding 持有 per-thread
  `IdentityHashMap<Chemical,DerivedCacheEntry>`，legacy inputs 折叠为 volatile copy-on-write
  `LegacyState` identity；`addAttribute` 发布新 state 并仅使该 Chemical 的 entry 失效。repeat top-level
  query 在 exact (thread, context, chemical, legacy state) 下零分配零 descriptor lookup。

## Recipe output handling, one-input cached recipes, and keyed holders (Task 042A)

Task 042A admits the 33-source output-handling/one-input closure plus one new layer-neutral source, moving the compiled slices to **163/184/37**. Against
`4646f509` the closure is exactly 12 `U` (byte-identical) and 21 `A`, plus the new `N` source; there is no `M` or `R`.

The only adaptations are the four families approved by `TASK042_PLAN.md`:

- the NeoForge `FluidStack` import becomes the project-owned `mekanism.api.fluid.FluidStack` in 14 files. `OutputHelper`'s fluid paths keep `Integer.MAX_VALUE` as
  the simulate cap, `int amountUsed / toOutput.getAmount()` truncation, and the exact `getNeeded() > 0` error split; the chemical path keeps
  `MathUtils.clampToInt(amountUsed / toOutput.getAmount())` with a true-`long` `copyWithAmount(amount * operations)`.
- NeoForge `TriPredicate` becomes `mekanism.api.functions.TriPredicate` in `PressurizedReactionRecipe`.
- `MekanismAPI.MEKANISM_MODID` becomes the literal `"mekanism"`, matching the Task 032 precedent and dropping the entire 15-file module/gear/radial API group,
  which was reachable only through the single mod-id constant.
- `DeferredHolder.create(Registries.RECIPE_TYPE|ITEM, key)` becomes `MekanismKeyedHolder`.

`MekanismKeyedHolder<T>` is the one genuinely new mechanism. It is a dependency-free `Holder<T>`/`Supplier<T>` over a `ResourceKey<T>`; the API layer cannot
reference the common layer's by-key register, so the helper lives in `mekanism.api.recipes`. `keyed(...)` wraps an identity stable, non-registered value;
`registryKeyed(...)` resolves a key lazily out of an existing registry. With a vanilla delegate present, `unwrap`, `unwrapKey`, `kind`, `canSerializeIn`, `tags`,
`is(...)`, `get`, `value`, and `isBound` delegate verbatim; with no delegate, key-derived `is`/`unwrapKey`/`unwrap`/`kind` answers and key-based `getKey`/`getName`/
`getId` remain available while tag and serialize queries fail closed. The helper never writes to a registry.

Consequently `MekanismRecipeTypes.TYPE_*` hold 26 concrete non-registered `RecipeType` instances. Vanilla groups recipes by `getType()` **identity**, so in-memory
recipes and `OneInputCachedRecipe` factories are usable and `RecipeManager.byType` is correct, while nothing is registered. The ten
`getToastSymbol()` item holders resolve `mekanism:<path>` against `BuiltInRegistries.ITEM`; because the `MekanismItems` family is not admitted they are non-null and
key-correct but unbound, and `value()` throws. `mekanism/api/MekanismAPI.class` is still absent from the artifact.

Verification: six ordinary methods in the single new `RecipeOutputHandlingTest` (no Knot), the three slice no-NeoForge checks, `scan-platform-deps.py`,
`fabric-build.sh`, and the exact static packaged class-family/leakage gate. `./build.sh --clean` and real server/client smokes are not run.

## Recipe type/serializer registration and the concrete recipe family (Task 043)

Task 043 admits the whole `mekanism.api.recipes.basic` package (26 concrete recipes, 7 abstract bases, 2 output interfaces), the API serializer holder class, the
three common recipe serializers, `mekanism.common.recipe.WrappedShapedRecipe`, and one new platform owner. The compiled slices move to **199/188/38**. Against
`4646f509` the admitted set is 16 `U` (byte-identical: `BasicChemicalChemicalToChemicalRecipe`, `BasicChemicalCrystallizerRecipe`, `BasicChemicalDissolutionRecipe`, `BasicChemicalToChemicalRecipe`, `BasicCombinerRecipe`, `BasicItemStackChemicalToItemStackRecipe`, `BasicItemStackToChemicalRecipe`, `BasicItemStackToEnergyRecipe`, `BasicItemStackToFluidOptionalItemRecipe`, `BasicItemStackToItemStackRecipe`, `BasicNucleosynthesizingRecipe`, `BasicSawmillRecipe`, `IBasicChemicalOutput`, `IBasicItemStackOutput`, `SawmillRecipeSerializer`, `WrappedShapedRecipe`) with the other 24 `A` under exactly the authorized families below, plus one `N`.

Adaptations, all previously authorized or newly justified:

- `net.neoforged.neoforge.fluids.FluidStack` → `mekanism.api.fluid.FluidStack` in the six `FluidStack`-bearing recipe classes and two common serializers;
- `DeferredHolder.create(Registries.ITEM|RECIPE_SERIALIZER, key)` → `MekanismKeyedHolder` in the 16 toast-item holders and the 26 API serializer fields, with no
  public field changing its type parameters (a new typed-key `registryKeyed` overload keeps `MekanismKeyedHolder<RecipeSerializer<X>>`);
- `MekanismAPI.MEKANISM_MODID` → literal `"mekanism"` (Task 032 precedent);
- `net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs.composite` (7-arity) → a private `composite` in `MekanismRecipeSerializer` implementing the identical
  sequential decode/encode algorithm;
- `net.neoforged.neoforge.common.util.NeoForgeExtraCodecs.withAlternative(MapCodec, MapCodec)` → a private `AlternativeMapCodec` in `RotaryRecipeSerializer`
  reproducing the pinned NeoForge 21.1.200 behaviour exactly: `keys` is the distinct union of both alternatives, `decode` falls through only on error, and `encode`
  probes the first codec against `ops.empty()` and falls through only on error.

`MekanismRecipeTypes` now registers its 26 concrete instances into `BuiltInRegistries.RECIPE_TYPE` under their official ids from a memoized explicit
`ensureRegistered()` step, and attaches the canonical registry holder back onto the same instance, so `getType()` identity is preserved by construction. Static
initialization performs no registry write. `MekanismKeyedHolder` gains `registryKeyed(ResourceKey<? extends Registry<R>>, ResourceLocation, Supplier<...>)` and
`MekanismRecipeTypes` gains `ensureRegistered()`, `isRegistered()`, and `recipeTypeHolders()`; no existing public signature changes.

One access-widener entry is added: `accessible field net/minecraft/world/item/crafting/ShapedRecipe pattern Lnet/minecraft/world/item/crafting/ShapedRecipePattern;`,
which `WrappedShapedRecipe` needs to pass the inner pattern to its own super constructor and which the packaged smoke now asserts in its remapped form.

Verification: six ordinary methods in `RecipeSerializerContractTest` (official JSON shape round-trips, the rotary three-shape alternative plus distinct-union keys and
first-preference encoding, sawmill dependent optionality, energy scalar, and the 26 key-stable holders) and four methods in the isolated `fabricRecipeRegistryTest`
worker (real registration with canonical identity, holder resolution, a real `RecipeManager` decode-and-serve, and duplicate/idempotence behavior). Two plan
assertions were corrected to the real contract: an empty chemical output is an omitted optional field, and the no-output reaction and chance-without-output cases are
rejected by the recipe constructor's precondition rather than as a codec error result.

### Generic holder shape (Task 043 correction from independent review)

Task 042A declared `MekanismKeyedHolder<T> implements Holder<T>`, which is not type compatible with the official surface: the oracle fields are
`DeferredHolder<RecipeType<?>, RecipeType<X>>`, and that two-parameter shape *is* a `Holder<RecipeType<?>>` because the registry element type is split from the
declared value type. Compilation confirmed the incompatibility. Task 043 therefore re-declares the helper as `MekanismKeyedHolder<R, T extends R>`, so all 26 recipe
type fields are `MekanismKeyedHolder<RecipeType<?>, RecipeType<X>>` and all 26 serializer fields are
`MekanismKeyedHolder<RecipeSerializer<?>, RecipeSerializer<X>>`, each assignable to the official widened `Holder<...>` while still returning the precise declared
value type from `get()`/`value()`. The 16 toast-item fields keep the oracle's declared `Holder<Item>`. `attach` additionally rejects a delegate whose bound value is
not the instance the keyed holder was built over, so registration cannot bind an unrelated instance.

Coverage is executable, not documentary: for every registered type the isolated worker asserts the official id resolves, the registry value is identity-equal to the
declared `TYPE_*` value, the canonical holder is that same instance, the holder is bound, `get()`/`value()` agree, and all 26 instances are distinct under an identity
set. A real `RecipeManager` decode-and-serve case proves the served recipe reports the registered type and serializer.

## Capability cache 与 capability 基类 tile（Task 051）

状态：已移植，Fabric common/platform allowlist 中启用（`mekanism/common/capabilities/CapabilityCache.java`、`mekanism/common/tile/base/CapabilityTileEntity.java`、`mekanism/fabric/tile/FabricCapabilityTileLifecycle.java`）。

### 永久签名差异（Fabric delta）

- `CapabilityCache.addConfigComponent` 的参数由具体 `TileComponentConfig` 收窄为
  `BiPredicate<BlockApiLookup<?,@Nullable Direction>, @Nullable Direction>`（禁用谓词绑定）。真实组件仍由真实 tile 持有，
  `TileEntityMekanism.addComponent` 绑定 `config::isCapabilityDisabled`。这是依赖收窄，不是配置状态重实现；完整 `TileComponentConfig` 仍未入 slice。
- `CapabilityTileEntity` 构造函数接受 vanilla `Holder<? extends BlockEntityType<?>>`（沿用已接受的 `TileEntityUpdateable` 形状），替代 NeoForge
  `TileEntityTypeRegistryObject`。
- Provider 常量/工厂改为 Fabric `BlockApiLookup.registerForBlockEntity(BiFunction, BlockEntityType)` 接受的类型化 `BiFunction` 形状：
  `ITEM_HANDLER_PROVIDER`/`FLUID_HANDLER_PROVIDER` 暴露事务 `Storage` 视图（`ItemStorage.SIDED`/`FluidStorage.SIDED`），
  `CHEMICAL_HANDLER_PROVIDER`/`HEAT_HANDLER_PROVIDER` 走 Mekanism 自有 lookup 常量暴露域 API，energy 用泛型懒工厂
  `capabilityProvider(EnergyStorage.SIDED, ...)`。所有 provider（eager 与 lazy）先经 `canExposeCapabilities()`（非 removed 且 exposure 打开）再查策略。
- 失效方法（`invalidateCapability*`/`invalidateCapabilities*`/`invalidateCapabilitiesFull`）只撤销内部缓存视图：Fabric pinned lookup 只缓存 BE/provider
  而不缓存返回的 API、每次 find 重新调用 provider，因此无 NeoForge `invalidateCapabilities()` 世界通知等价物；`AttributeHasBounding` 通知遍历仅从该
  路径移除（bounding 玩法路径不变）。
- 生命周期：`openCapabilityExposure()`（服务端 load 打开新代）/`closeCapabilityExposure()`（unload/移除关闭并撤销全部视图）；
  `setRemoved()` 先关闭再走 super；`clearRemoved()` 只撤销缓存、不重开 exposure；`FabricCapabilityTileLifecycle` 只注册真实
  `ServerBlockEntityEvents.BLOCK_ENTITY_LOAD/UNLOAD`（pinned Fabric `LifecycleEventsImpl` 本身在区块卸载时向该区块全部 BE 转发 BLOCK_ENTITY_UNLOAD——含
  传送门边界情形——并在世界卸载时经 `LoadedChunksCache` 对每个已加载区块的全部 BE 同样转发，故区块/世界卸载覆盖由转发保全，无需直接 chunk 回调）。
  回调只消费真实事件，不合成世界事件、不强制加载区块。

### 验证

`CapabilityTileLifecycleTest`（3：identity/lazy/unique-resolver 分发与绑定策略；eager provider 策略门禁 + tile 各失效入口对真实 manager 视图的撤销；
eager+lazy 的 removed/clearRemoved/load 代际守卫与保留别名 fail-closed）、`CapabilityTileRegistrationTest`（3，Knot 真加载器：真实
`registerForBlockEntity` 注册后经真实 per-find 分发验证策略/生命周期/逐-find 新鲜解析与 Team Reborn 懒工厂）。`assemble`（remapJar + 三个 slice
gate）BUILD SUCCESSFUL。未运行（按完成度优先策略）：全量测试、packaged smoke、clean release、真实 server/client fixture。

### Play 通道 (C2S) 平台差异（Task052，2026-10-26）

- 官方 `IPayloadContext` 处理器统一改为 `IMekanismPacket#handle(Player)`：Fabric 的 play receiver 已解析发送玩家，`PacketUtils.config/ejector/filterManager/blockEntity` 直接接收该 `Player`。
- 无效包拒绝：`IPayloadContext#disconnect` → `ServerPlayer#connection#disconnect`（play payload 在 Fabric 上没有 context 级断开入口）。
- `PacketDropperUse` 通过 `ContainerType.FLUID.createHandler(stack)` 访问滴管自身的 attachment 流体罐；NeoForge 的外来 item fluid capability 在此无 attachment 等价物。
- `PacketPortableTeleporterTeleport` 经 `MekanismEventBus` 发布真实 `MekanismTeleportEvent.PortableTeleporter`，保留可取消语义。
- `AbstractContainerMenu#synchronizeCarriedToRemote` 在 vanilla 为 private，已加入 `mekanism.accesswidener`；用于滴管手持栈同步。
- `PacketModeChangeCurios` 不移植：Curios 无 Fabric 对等物，官方 `PacketModeChange`（装备槽）保留。
- 注册：25 个 C2S payload 由 `FabricServerPlayNetworking` 在 `PayloadTypeRegistry.playC2S()` 注册，并按类型各注册一个 `ServerPlayNetworking` receiver，重复注册直接抛错。

### 接口重声明 vanilla 方法名（Task052 机器首跑修复，2026-10-26）

- 现象：`TileEntityEnrichmentChamber` 首次 tick 抛 `AbstractMethodError`（`IRecipeLookupHandler.getLevel`）。dev 名字下接口抽象方法与 `BlockEntity#getLevel` 同名即视为实现；remap 后 vanilla 方法变为 `method_10997`，而 mod 接口不在 Minecraft 继承链上、名字不变，导致抽象方法无实现。
- 规则：mod 接口不得以 abstract 形式重声明会被 remap 的 vanilla 方法名（至少 `getLevel`/`getBlockPos` 这类 `BlockEntity` 方法）。
- 修复：新增 `mekanism.common.base.ILevelProvider`，以单一 default `getLevel()` 委托 `BlockEntity#getLevel`；`IRecipeLookupHandler`、`ITileWrapper`、`IQIOCraftingWindowHolder` 均 extend 它并删除各自的 `getLevel` 声明（两个互不相关的 default 也无法解析）。`ITileWrapper#getBlockPos` 同理保留 default；`Transmitter` 与 `PortableQIODashboardInventory` 保留显式实现。
- 验证：`scripts/fabric-machine-smoke.sh` 在真实打包服务端驱动富集仓与冶炼机，断言 2 铁矿石→4 铁粉、2 铁矿石→2 铁锭，且无异常/崩溃；接口层级改动后必须 `compileJava --rerun`。

### 能量边界（Task052 能量网络打通，2026-10-26）

- `ForgeEnergyCompat#capabilityExists` 必须使用 Team Reborn Energy 的真实 mod id `team_reborn_energy`；写错会让所有能量 acceptor 缓存为空且 `isUsable` 路径失效。
- `CapabilityTileEntity#setLevel` 在进入 `ServerLevel` 时开启 exposure。Fabric 的 `BLOCK_ENTITY_LOAD` 只覆盖区块加载的 BE，玩家放置（`LevelChunk#setBlockEntity`）不会触发；只在 LOAD 开启会让新放置机器的 provider 全部返回 null。
- `BlockEnergyCapabilityCache#getCapability` 在本移植返回已包装的 `IStrictEnergyHandler`，`EnergyAcceptorCache` 不得再调用 `wrapAsStrictEnergyHandler`（否则把 handler 强转 `EnergyStorage` 抛 CCE）。
- 通用线缆的能量能力：官方 `EnergyCompatUtils.addBlockCapabilities(builder)` 由 Fabric exposure seam 取代后，`registerCable` 必须显式 `.with(EnergyStorage.SIDED, CapabilityTileEntity::basicCapabilityProvider)`；否则线缆不暴露能量，任何存储/发电机都无法输入。
- 机器默认侧面配置来自物品组件的 `AttachedSideConfig`（放置时由原版应用）；`/setblock` 不应用物品组件，测试需用 `data merge block` 写入 `component_config`（ENERGY ordinal 0 的 `config0` 数组、`eject0`）。
- 验证：`scripts/fabric-machine-smoke.sh` 9/9（含 cube→cable→chamber）。

### 物品/流体/化学传输边界（Task052，2026-10-26）

- 三类传输器的 acceptor 都是“每次解析重新查询”的语义：物品/流体走 Fabric `ItemStorage.SIDED`/`FluidStorage.SIDED`，化学走 Mekanism 自有 lookup；不缓存返回的 `Storage`，因此端点重新暴露时无需失效回调。
- 运行时放置的端点只有在 `CapabilityTileEntity#setLevel` 开启 exposure 后才可被发现（与能量网络同一条修复）；否则传输器看到的邻居能力为 null。
- 传输器侧面连接类型由 `connection` int 数组持久化（Direction 序：down/up/north/south/west/east；ConnectionType：normal=0, push=1, pull=2, none=3）。真实玩家通过 GUI/配置器设置，测试用 `data merge block` 写入。
- `scripts/fabric-machine-smoke.sh` 覆盖 storage→storage 与 transmitter→machine 两条链，且必须显式 forceload 场景跨的三个区块（仅靠出生点区块覆盖会造成偶发失败）。

### 深审修正的平台边界（2026-10-26）

完整来源与验收见 `PORT_DEEP_REVIEW_2026-10-26.md`。本批保持既有官方 owner，不增加通用兼容层。

- Chunk loader 用独立 `TicketType<BlockPos>` 的 region ticket（distance 2 / level 31），不再修改全局 `/forceload`。普通票据也允许 entity/BE tick；`forceTicks` 的额外语义是绕过附近玩家对 random tick/spawn 的门控。保存仍使用 `owners`/`pos`/`chunks`，增加 `force_ticks`（缺失默认 false，启动按真实 component 重新裁定）；恢复所有有效票据，清空也必须保存。旧错误实现写进全局 forced 标记的来源不可区分，不自动删除。
- 数字矿机和纸箱的 speculative break probe 走真实 `PlayerBlockBreakEvents.BEFORE`，加 Mekanism 安全检查；不发送 AFTER，不把 probe 当实际破坏。矿机回调使用 owner UUID 的假玩家并 finally 清理。
- Robit 的 damage→energy 发生在 vanilla actuallyHurt 的未 clamp health 写入处；外层 hurt 尚未判断死亡。伤害吸收在原版拒绝 guards 后，双注入共享本次 invocation 的结果（Loader 自带 MixinExtras Share），不使用跨调用 ThreadLocal 缓存。
- Flight 授权按玩家实例管理，在服务端 executor 清理；保存时仅从输出 NBT 排除自身临时 mayfly/flying，避免 stopServer 先 saveAll 后 disconnect 留下权限。创造/旁观保留原生权限。没有宣称可仲裁其他模组同时写同一 vanilla boolean 的授权，也不能自动辨认旧版本已经保存的错误权限。
- `ItemStack.is(Holder)` 在 vanilla 比较引用，NeoForge 曾改为 value；Mekanism deferred item 的直接调用改为 `.is(holder.get())`。Module 在构造时从模块 registry 的真实 value identity 捕获 canonical holder，供原有 NBT/stream codecs 使用，不改变 wire/schema 顺序。
- 数据映射查询同时接受同一服务器的 base/world registry access 和当前 reloadable view，校验底层 DamageType registry identity 与线程；未引入任意 equivalent-registry fallback。
- 裸 ItemStack 不能承接外部 provider 改 item/count 的结果；strict getter 仅提供 registered Mekanism 内部 handler，外部执行须给真实 `ContainerItemContext` 或使用既有 `ItemEnergyContainer` 并由实际 inventory/slot 安装结果。充电板和模块保留原速率、automation policy 与事务回滚。
- NeoForge 21.1.200 mod-fluid 默认不生成无限源，爆炸抗性为1，向下替换还需异族流体。Mekanism 注册没有覆盖这些值。
- Chunk generation version 恢复原 root `mek_world_gen_version`：旧文件缺失为0；尚待 retrogen 的版本由 transient chunk attachment 承载，完成后显式保存当前版本。这不代表矿物生物群系注入已完成。
- C2S catalog 现为26个，补上三个已注册 GUI 实际使用的 `PacketGuiInteract`；日志计数来自成功注册次数。彩色文本恢复原专用 filter，蜂巢剪切调用实际 vanilla harvest，电弓用 detached enchanted bow 调用原 projectile 创建流程。

### 世界生成边界（Task052 官方矿物生成闭环，2026-10-26）

- 官方 `data/mekanism/neoforge/biome_modifier/*.json`（6 个 `neoforge:add_features`，共引用 11 个 placed feature）按原样打包，仍是唯一事实来源；`FabricBiomeModifiers` 在启动时解析并逐 placed feature 调 `BiomeModifications.addFeature`，不发明注册表、不修改官方 JSON。`biomes` 字段保持官方 `HolderSet<Biome>` 语义：`#tag` 引用或 biome ID（单个/数组），在 biome 选择上下文上惰性匹配（tag 只有世界加载时才可解析）。`#` 前缀必须在 `ResourceLocation.parse` 之前剥离。
- 非 `add_features` 官方类型（当前仅 Additions 的 `mekanismadditions:baby_mob_spawn`）直接拒绝而不是静默忽略，防止未来新增类型被无声丢弃；Additions 的 mob 修饰符不属于本轮边界。
- 打包面新增 `data/mekanism/worldgen/configured_feature/`、`placed_feature/` 与 `neoforge/biome_modifier/` 三个目录（`tags/worldgen/biome/spawn_ores.json` 原已在 `data/mekanism/tags/**` 内）。configured/placed feature 作为普通 datapack 内容加载；`mekanism:ore`、`mekanism:disk`、`mekanism:ore_retrogen` feature、`configurable_constant`/`configurable_uniform` int provider、`configurable` height provider 与 `mekanism:disableable` placement modifier 共 7 个 codec 条目随 bootstrap 注册进对应 BuiltInRegistries。
- 官方世界配置 `MekanismConfig.world`（`serverconfig/Mekanism/world.toml`，SERVER 型）由 `FabricWorldGenConfig` 按 `FabricChemicalConfig` 的世界片段模式接管：SERVER_STARTING 在 server 线程以 `<world>/serverconfig` 为根注册加载，SERVER_STOPPED 卸载，每世界路径一个 claim。feature codec 的解码只绑定 cached value supplier（惰性），真正的 config 读取发生在世界生成/区块事件时，此时 owner 已加载。未来完整配置闭包不得再把 world config 注册进全局 `config/` manager（会与真实 owner 冲突）。
- `ConfigurableHeightProvider` 的官方 `Mekanism.logger` 改为同类别私有 SLF4J logger（与 `SerializerHelper` 先例一致），警告文本不变；四个 registry 类的 `DeferredHolder/DeferredRegister` 换成本项目注册件、`Mekanism.MODID` 换字面 mod id（与 `MekanismItems` 同型）。
- 验收：`scripts/fabric-worldgen-smoke.sh`（固定 seed 世界 + 512 chunk forceload + 停服后扫描真实 region 文件调色板）5/5：注册 proof、`modifiers=6 features=11` proof、datapack 零错误、`world.toml` 落盘、六类目标（五种矿石 stone/deepslate 形态 + 盐盘）均在真实生成区块中命中。retrogen（`enableRegeneration` 默认 false）由既有 `CommonWorldTickHandler`/`ChunkGenVersionMixin` owner 承接，本轮未变。

### 漏斗自动化边界（Task052，2026-10-26）

- 官方 NeoForge 让漏斗经物品 capability 看见机器；vanilla 漏斗只认识 `Container`/`WorldlyContainerHolder`，而 Container 的原地可变 stack 协议（`getItem` 后被 `grow`/`shrink` 直接改写）无法在 Mekanism 槽位 API 上忠实实现——返回副本会让合并路径静默丢物，返回内部 live stack 又绕过槽位校验与变更通知。因此桥接走传输器同一条真实事务面：`HopperTransferMixin` 在 `ejectItems`/`suckInItems` HEAD 处解析目标位置的 `ItemStorage.SIDED`，且仅当返回 `FabricItemStorage` 时才接管，一次移动一件、外层事务 simulate-decide-commit。
- 侧面语义：eject 的机器侧面 = 漏斗朝向的反面（vanilla `getAttachedContainer` 的目标块）；suck 的机器侧面 = DOWN（漏斗吸取上方）。漏斗朝向读 blockstate `HopperBlock.FACING`（原 `facing` 字段为私有实例字段，静态注入不能 shadow）。
- 守卫精确性：vanilla 容器（箱子、漏斗互传、实体容器、地面物品拾取）与第三方 storage 一律回退 vanilla 路径；目标不可收/已满时 abort 回滚，无物品损失。每次尝试重新查询（与传输器 acceptor"每次解析重新查询"语义一致），不引入结果缓存。
- 验收：`scripts/fabric-machine-smoke.sh` 现为 20 项：朝向漏斗把 4 个铁矿推入腔体 LEFT 物品侧（`config3` FRONT..TOP=INPUT、BOTTOM=OUTPUT），下方漏斗从 BOTTOM OUTPUT 拉出 `mekanism:dust_iron`，两项各以漏斗 `Items` 断言，一次连续通过。

### 官方资产与模型加载闭环（Task052 客户端渲染弧第一段，2026-10-26）

- 打包面：release JAR 从只含 lang/particles/shaders 扩到官方完整资产——163 个手写 blockstate、590 个手写模型（含 transmitter/mekasuit 的 OBJ+MTL 源）、857 个贴图、GUI 贴图、代码加载的 render 贴图、音效与全部非英文语言（生成的 en_us.json 仍是唯一英文来源，无重复冲突），另含生成侧 38 blockstate、156 模型、60 贴图、实体图集与 sounds.json。官方文件零修改，`convert_uv.py` 是开发脚本不入包。
- 模型加载闭环（`FabricMekanismModelPlugin`，PreparableModelLoadingPlugin，离线线程解析、严格未知 loader 拒绝）：`neoforge:composite`（82 个工厂模型）按官方 children 定义以 vanilla 管线逐个烘焙合并；`neoforge:obj`（Meka-Tool 实体 OBJ，`mtllib`/`map_Kd` 直引贴图）复用 transmitter 的 OBJ 几何解析/四边形烘焙器（`map_Kd` 扩到官方直引贴图 id 形式、无 map_Kd 的占位材质按 Blockbench 惯例跳过，面真正引用时烘焙器仍大声失败）；`neoforge:separate_transforms` 只烘焙其 base OBJ（左手镜像模型是记录在案的可视 delta：vanilla 1.21.1 的 Fabric 无逐透视模型钩子）；`neoforge:item_layers`（QIO dashboard）rebase 到 `minecraft:item/generated`（LED 层全亮度是记录在案的可视 delta）。四个模型类继承 BlockModel（vanilla 父链解析硬性要求，工厂物品模型直接 parent 到复合方块模型；与 transmitter 先例一致）。
- 大声延迟（proof line 逐族计数，绝不伪造）：`mekanism:energy_cube`/`mekanism:data_based`/`mekanism:robit` 官方烘焙读取 NeoForge model data，回退 vanilla 纯解析（tier 模型 parent 在其上，不能经 resolver 干预否则触发 BlockModel 父链 cast）；`neoforge:fluid_container`（18 个流体桶）经 `FabricDeferredLoaderModel` 显式解析为 vanilla missing 模型并切断只存在于 NeoForge jar 的 `neoforge:item/bucket` 父链（否则资源重载崩溃）；`mekanism:transmitter` 归属既有 transmitter 插件。
- 验收：`scripts/fabric-model-smoke.sh`（Xvfb 下真实客户端 JVM + 共享 Loom 输出互斥锁）5/5：标题屏、客户端入口点、`MEKANISM_FABRIC_MODELS_PROOF composite=82 separate_transforms=1 item_layers=1 obj=1 energy_cube_deferred=1 data_based_deferred=1 robit_deferred=2 fluid_container_deferred=18 transmitter_owned=3`、零缺失模型错误、零加载器异常。回归：machine smoke 20/20（保留目录复跑；一次 15-fail 为 240s 启动超时下的并发 flake，非 jar 缺陷）。

### 瓦片 BER 脊柱闭环（Task052 客户端渲染弧 5(c) 前半，2026-10-26）

- 纳入并接线 15 个瓦片实体渲染器（boiler/dynamic tank/digital miner/dimensional stabilizer/personal chest/nutritional liquifier/pigment mixer/seismic vibrator/teleporter/thermal evaporation/industrial alarm/SPS/bin×5/energy cube×5/fluid tank×5，共 30 个 tile 类型）+ 三个基类（MekanismTileEntityRenderer/ModelTileEntityRenderer/MultiblockTileEntityRenderer）+ IWireFrameRenderer，经 `FabricTileEntityRendererRegistration` 用 vanilla `BlockEntityRenderers.register` 注册（官方 ClientRegistration.registerRenderers 的瓦片半；transmitter 渲染器显式延后——本移植的 transmitter 外壳可视归 baked-model 缝所有，contents 覆盖层依赖官方 obj 几何 loader）。两个 BER Java 模型层（energy core/industrial alarm）经 Fabric `EntityModelLayerRegistry` 注册。
- 事件枢纽拆分（sanctioned delta family）：`RenderTickHandler` 保留 level-render 半（透明 LazyRender 队列、wire-frame 助手、配置器覆盖模型缓存），HUD/护甲/高亮/EMI/tick 监听随各自延后车道移出；排空点由 Fabric `WorldRenderEvents.AFTER_TRANSLUCENT` 注册（对应官方 RenderLevelStageEvent AFTER_TRANSLUCENT_BLOCKS），render-tick 用 access widener 打开的 `LevelRenderer.ticks`（两个上游 AT 树对照为 NeoForge 事件内部注入）。`MekanismRenderer.onStitch` 的瓦片 reset 扇出恢复接线（transmitter 三条仍延后）。
- 模型缓存：`BaseModelCache`/`MekanismModelCache` 裁至瓦片 JSON 半（liquifier blade/vibrator shaft/pigment mixer shaft/QIO drives），OBJ 半（Meka-Suit/Meka-Tool/transmitter contents/robit 皮肤）随 armor/transmitter/robit 车道延后；官方 BakingCompleted/RegisterAdditional 对由 `FabricModelCachePlugin` 的 after-bake 捕获 + `addModels` 顶层注册替代。`Outlines` 去 NeoForge IQuadTransformer/ModelData，顶点 stride/offset 由 `DefaultVertexFormat.BLOCK` 权威推导。
- 文件级适配：瓦片渲染器的 NeoForge FluidStack → `mekanism.api.fluid.FluidStack`；`FluidStackLinkedSet` 缓存策略由端口 FluidStack 的组件感知 equals/hashCode 等价保持；NeoForge 专属 `getRenderBoundingBox` 覆盖移除（Fabric BER 无该钩子，`shouldRenderOffScreen` 已放宽视锥裁剪——记录的裁剪粒度 delta）；`ModelData.EMPTY` 调用点走 vanilla 无参路径；`Mekanism.rl` → `MekanismAPI.MEKANISM_MODID` 私有助手（既定先例）。access widener 增补：LevelRenderer.ticks、Minecraft.levelRenderer、ModelPart.children/cubes、ModelPart$Cube.polygons（上游两树可见性对照核对）。
- 验收：`scripts/fabric-model-smoke.sh` 扩至 7 检查（新增 `MEKANISM_FABRIC_MODEL_CACHE_PROOF json_models=7`、`MEKANISM_FABRIC_BER_PROOF tile_types=30 renderers=15 layers=2`），两次全绿；machine smoke 20/20。未验证风险：BER 的世界内实际渲染画面（需要进入存档的可视验证）归功能里程碑门禁。

### 方块物品 ISBER 闭环（Task052 客户端渲染弧 5(d) 第一段，2026-10-26）

- `MekanismISTER` + `RenderEnergyCubeItem` + `RenderFluidTankItem` 经 Fabric `BuiltinItemRendererRegistry` 以 `DynamicItemRenderer` lambda 接线（官方 registerItemExtensions 语义）；十个物品 id（5 能量立方 + 5 流体罐）的官方 JSON 保持不动，由 `FabricItemRendererRegistration` 的 resolveModel 覆盖用 `QuadsEmptyModel` 包装官方烘焙模型——仅抑制 quads、完整保留父链解析的 display 变换/粒子/overrides，避免 Fabric builtin 渲染器要求改 JSON（`builtin/entity`）导致的双重渲染。
- `MekanismISTER.renderBlockItem` 从 NeoForge 通道（getRenderPasses/renderTypes/ModelData/renderQuadList）改写为 vanilla 1.21.1 等价路径（单块模型 + `ItemBlockRenderTypes.getRenderType(state, fabulous)` + 内联染色四边形循环，即 vanilla renderQuadList 私有体）；NeoForge 树公开化的 `Minecraft.levelRenderer`/`LevelRenderer.ticks`（官方 getTicks getter）/`Minecraft.itemColors`（官方 getItemColors getter）经 access widener 对齐，官方 getter 调用点为宽化字段读（微 delta，注明）。
- 能量立方物品外壳仍属延后的 energy_cube loader 族（其 side-state 切换读 NeoForge model data），核心（ModelEnergyCore）正常渲染；三个官方物品条装饰器（ChemicalFluidBar/MekaSuitBar/TransmitterType）在本 fabric-api 版本无对应 API，延后。
- 验收：client smoke 扩至 8 检查（新增 `MEKANISM_FABRIC_ITEM_RENDERER_PROOF suppressed_models=10 builtin_renderers=10`）；未验证风险：物品在 GUI/手中的实际画面归功能里程碑门禁。

### 装备物品 ISBER 闭环（Task052 客户端渲染弧 5(d) 第二段，2026-10-26）

- 六个装备 ISBER（jetpack/armored jetpack/free runners/armored free runners/scuba mask/scuba tank/atomic disassembler/flamethrower）经同一 `BuiltinItemRendererRegistry` 通道接线；官方物品 JSON 本就 parent 到 `minecraft:builtin/entity`（vanilla 特殊模型、烘焙零 quads），Fabric 通道零 JSON 改动、零抑制器。八个 `Model*` Java 模型（jetpack 等）+ 八个层定义经 `EntityModelLayerRegistry` 注册（transporter box 层随 transmitter 车道）；`Mekanism.rl` → `MekanismAPI.MEKANISM_MODID` 私有助手（既定先例）。
- 护甲形态半（`MekCustomArmorRenderProperties` 的 HumanoidModel gear 渲染，`JetpackArmor` 等）随 armor 车道延后；物品形态先接通。
- 验收：client smoke 8 检查（BER proof layers=2→10、ITEM_RENDERER proof builtin_renderers=10→18）两次全绿；machine smoke 20/20。

### NeoForge 类型化配料解码闭环（Task052 datagen 车道第一段，2026-10-26）

- 官方 320 个使用 `neoforge:compound`/`neoforge:difference`/`neoforge:intersection` 类型化配料的配方（染色/绘画/颜料提取/生物燃料/碳转化等核心目录）此前整批排除；`ItemStackIngredient` 编解码在既有 compound 支持上补齐 difference（base−subtracted）与 intersection（children AND）两类官方形状：JSON 编解码对称、网络流编解码以 mode byte 扩展（0 simple/1 compound/2 difference/3 intersection）、匹配语义（并/差/交）、配方输入缓存映射（intersection 缓存面=全部 children）与空匹配判定（intersection 任一成员为空即空）齐全。
- 打包面解除 320 个排除项（`recipe-neoforge-only.txt` 删除），release JAR 配方 1476→1816；真实服务端 "Loaded 2868 recipes"、零配方解析错误、machine smoke 20/20。637 个 `compat/**` 配方仍延后：其 `neoforge:conditions`（mod_loaded 门）包装键在 Fabric 条件系统（固定 `fabric:load_conditions` 键）无对应物，需打包期 JSON 变换或裁决——作为 datagen 车道剩余切片记录。
- 验收：新增 `NeoForgeTypedIngredientTest` 4 例（三种官方形状的精确 JSON 解码、并/差/交匹配语义、重编码对称、缓存面）。

### compat 配方条件包装变换（Task052 datagen 车道第二段，2026-10-27）

- 637 个官方 `compat/**` 配方以 NeoForge `neoforge:conditions` 包装门控其他模组,该包装键在 Fabric 条件系统（固定 `fabric:load_conditions`）无对应物。采用**打包期变换**（`transformCompatRecipes`，groovy JsonSlurper）:官方源文件 byte 不变,仅打包形态转换——wrapper→`fabric:load_conditions`、`mod_loaded`→`fabric:all_mods_loaded`（注意复数 s,首版误写单数被真实服务端 455 条解析错误暴露）、`not`→`fabric:not`、`tag_empty`→`fabric:tags_populated` 包 `fabric:not`。
- `mekanism:mod_version_loaded`（118 个）为 Mekanism 自定义条件,Fabric 无内建版本区间条件→移植 `FabricModVersionLoadedCondition` 注册同 id 的 fabric ResourceConditionType,语义对齐官方 `ModVersionLoadedCondition`（mod 存在且版本≥下限）。**比较器 delta（记录）**:官方用 Maven `ComparableVersion`,Fabric loader `Version` 为 semver 形;现有 compat 下限均为简单三元版本,两种序一致。
- **tag 存在性 delta（记录）**:NeoForge `tag_empty` 检查空,Fabric `tags_populated` 只检查存在——仅对"存在但为空"的标签可见,此时配方会加载但匹配不到任何物品（惰性配方）。
- 验收:jar 内 637 个 compat 配方全部为变换形态;真实服务端条件注册 proof + 零解析错误 + "Loaded 2868 recipes"(与无 compat 时相同——ae2/BSWG 缺席时 637 全部被条件正确跳过,即门控生效的直接证据)。

### HUD 车道（Task052 client 渲染弧 (d) 段,2026-10-27）

- 四个官方 HUD 层（`LayeredDraw.Layer` 原生接口）全部接通:MekaSuitEnergyLevel（ARMOR_LEVEL 之上）、MekanismHUD（SUBTITLE_OVERLAY 之上）、MekanismStatusOverlay（原生 selected-item-name 位,ModeStatusOverlayMixin 早前已建）、RadiationOverlay（全层之下,RadiationOverlayMixin 早前已建）。1.21.1 fabric API 无分层 HUD 插入（仅尾部 HudRenderCallback）,故前两者经 `FabricHudLayersMixin` 包裹 vanilla `Gui` 构造器的两个 `LayeredDraw.add` 位点（ordinal 2/13）,与 NeoForge 插槽逐位对齐。
- 平台适配:`FabricHudHeights.leftColumnHeight` 复刻 NeoForge `Gui.leftHeight`（vanilla 1.21.1 无此字段,renderPlayerHealth 局部变量算行）——沿 RadiationOverlay→FabricRadiationAttachments 先例;`+=8` 下游消费者缺失致最坏情形模式文本与本条 8px 重叠（记录）。MekanismHUD 的 curios 块随 curios 车道延后（装备槽主路径完整）。访问放宽镜像上游 AT 三项:Gui.subtitleOverlay、SubtitleOverlay.audibleSubtitles、SubtitleOverlay$Subtitle（HUDRenderer 字幕宽度测量用）。
- 验收:模型 smoke 第 9 检查 `MEKANISM_FABRIC_HUD_PROOF layers=2`（新 mixin）+ 既有状态/辐射 mixin 真机运行;9/9 零缺失模型。切片 api 262 / common 1452 / platform 143。
