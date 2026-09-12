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

状态：作为唯一直接 `mekanism.api.datamaps` admission 启用。相对官方
`v1.21.1-10.7.19.85`，唯一 executable delta 是 `ID` 的 namespace 来源由
`MekanismAPI.MEKANISM_MODID` 改成稳定 literal `"mekanism"`，从而不触发尚未纳入的 broad facade。
record identity、constructor、`absorption()`、ID path、object-first `CODEC`、bare-float alternative、
`[0,1]` codec 与 constructor rejection、Javadoc/public surface 均保持。未新增 public
`mekanism.api` descriptor，也未纳入 `IMekanismDataMapTypes`/`MekanismDataMapTypes`/
`DataMapTypeRegister`。

Fabric 的 descriptor/loader/session 是 `mekanism.fabric.datamaps` 平台实现，不伪造
`net.neoforged.*`。它只承诺 ordinary、unconditional、server-side、non-synced MekaSuit absorption；
Chemical、conditions、Advanced merger/remover、sync、holder mixin、radiation/tooltip/tank owner 与
networking 明确延期。NeoForge 21.1.200 oracle source/binary hash、资源 hash、layer/lifecycle/failure
truth table、query 规则和验证见 `docs/FABRIC_DATA_MAPS.md`。
