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

## Deferred registry wrapper surface

状态：首个 common source slice 已移植，用于官方 GameEvent 声明。

`MekanismDeferredRegister<T>` 不再继承 NeoForge `DeferredRegister<T>`，而是组合
`FabricDeferredRegister<T>`；保留 Mekanism 自身使用的两个 typed `register` 重载，并新增通过
vanilla `Registry<T>` 提交批次的入口。`MekanismDeferredHolder<R, T>` 不再继承 NeoForge
`DeferredHolder<R, T>`，而是组合 Fabric holder，同时直接实现 vanilla `Holder<R>` 与
`Supplier<T>`。`get/getKey/key/getId/getName/is/keyMatches`、绑定前 key 可用性、绑定后 canonical
对象身份均保留；绑定前读取值继续明确失败。

兼容影响：依赖 NeoForge `DeferredRegister`/`DeferredHolder` 继承关系或其额外方法的 addon
需要迁移到 Fabric/vanilla 注册 API。Mekanism 自身面向 holder/value/key 的公共用法保持源码级
表面。当前只承诺 `fabric/common-slice.txt` 已纳入的闭包；后续每引入一种 registry wrapper，
都必须补足实际调用到的 holder 语义，而不是伪造整个 NeoForge registry API。

语义依据与验证见 `docs/FABRIC_REGISTRY.md`、
`fabric/src/test/java/mekanism/common/registration/MekanismDeferredRegisterTest.java` 以及 packaged
server smoke。Kilt/Connector 仅用于核对生命周期、冻结和 canonical holder 行为；实现未复制。

## Config contract

状态：基础 spec/manager、primitive cached value 与官方 startup config 已移植。

`IMekanismConfig` 的 `ModConfigSpec` / `ModConfig.Type` 签名分别替换为
`MekanismConfigSpec` / `MekanismConfigType`；`IConfigTranslation.applyToBuilder` 改接
Mekanism-owned builder，并直接声明原有 `getTranslationKey()`，不再通过尚含 NeoForge
`TranslatableEnum` 的 `IHasTranslationKey` 继承。`MekanismStartupConfig` 构造器改为 public，供
Fabric bootstrap 在完整 `MekanismConfig` 纳入前构造唯一 startup 实例。Cached int/float 的公开
读写方法与 listener 语义不变，内部 backing value 类型改为 `MekanismConfigSpec.ConfigValue`。

兼容影响：直接依赖 NeoForge config spec/type 或 `IConfigTranslation` 到
`IHasTranslationKey` 赋值关系的 addon 必须改用 Fabric 侧契约。TOML 文件名、12 个 startup
路径、默认值、范围、restart 元数据与缓存行为保持。细节和验证见 `docs/FABRIC_CONFIG.md`。

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

## Energy container contract

状态：内部 energy storage 首个垂直切片已移植，外部 Fabric energy lookup/transaction adapter 尚未实现。

`IEnergyContainer` 的 NeoForge `INBTSerializable<CompoundTag>` 父接口替换为上文的 Mekanism-owned registry-aware 契约；energy getter/setter、execute/simulate、automation、listener、capacity 和 NBT 方法签名保持。仅删除了指向 NeoForge fluid handler 的 Javadoc 类型链接，改用平台中立文字描述同一返回值差异。

`BasicEnergyContainer` 保留官方 public factory/predicate 表面与行为。为避免引入 chemical/fluid 和 broad NBT helper 闭包，`ConstantPredicates.alwaysTrue()` 改为类内缓存 predicate，`NBTUtils.setLegacyEnergyIfPresent` 的 `stored` long/legacy string 读取语义窄化到类内；Guava 参数检查改为同类型的显式 `IllegalArgumentException`。兼容细节、暂缓边界和验证见 `docs/FABRIC_ENERGY.md` 与 `fabric/src/test/java/mekanism/common/capabilities/energy/BasicEnergyContainerTest.java`。

## Heat container contract

状态：内部 heat storage 首个垂直切片已移植，heat handler/holder、网络、机器 owner 与外部边界尚未实现。

`IHeatCapacitor` 的 NeoForge `INBTSerializable<CompoundTag>` 父接口替换为上文的 Mekanism-owned registry-aware 契约；其 getter/setter、batch update、listener、capacity 和 NBT 方法签名保持。

`BasicHeatCapacitor` 为避免引入 broad `NBTUtils` 闭包，局部实现 `stored` 与 `heat_capacity` 的 exact `TAG_DOUBLE` 读取；缺失/错误类型忽略、加载不通知、pending heat 保留等语义不变。`HeatAPI` 本身与官方基线字节一致；Fabric access widener 仅镜像官方 access transformer 对 `Biome.getTemperature(BlockPos)` 的 public access，以保留位置修正温度。兼容细节、暂缓边界和验证见 `docs/FABRIC_HEAT.md` 与 `fabric/src/test/java/mekanism/common/capabilities/heat/BasicHeatCapacitorTest.java`。

## Relative-side translation contract

状态：`RelativeSide` 与非配置 energy holder 闭包已移植。

`RelativeSide` 保持 `mekanism.api.RelativeSide` 类型身份、枚举顺序、serialized names、translation keys、`CODEC`、`BY_ID`、`STREAM_CODEC` 和全部方向映射。为避免仅六个 key 拉入 `APILang` → `MekanismAPI`/text 的广泛闭包，私有构造参数改为稳定 path，并通过 vanilla `Util.makeDescriptionId` 生成相同的 `side.mekanism.*` key。

`IHasTranslationKey.IHasEnumNameTranslationKey` 保持原 FQN、父级 Mekanism interface 和 `getTranslatedName()` 方法，但不再继承 NeoForge `TranslatableEnum`；default 实现直接调用 `Component.translatable`，与原 `TextComponentUtil.translate(String)` 行为相同。依赖该 NeoForge subtype 关系的 addon 需要迁移到 Mekanism-visible interface。这一变化集中在 API 边界，不在 Fabric 上伪造 `TranslatableEnum`。

`IHolder`、`BasicHolder`、`IEnergyContainerHolder` 与 `EnergyContainerHolder` 未改公开/可见性表面；方向过滤、内部访问、动态 facing、identity/order、duplicate 与 live-list 行为保持。详见 `docs/FABRIC_HOLDERS.md` 及对应测试。
