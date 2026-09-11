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

## Registry-aware NBT contract

状态：基础契约与 `DataHandlerUtils` 已移植；容器接口将在各自依赖闭包可编译时逐项切换。

新增 Mekanism-owned 公共契约：

```java
mekanism.api.INBTSerializable<T extends Tag>
```

它保留 NeoForge 1.21.1 接口的两个注册表感知方法：

```java
T serializeNBT(HolderLookup.Provider provider)
void deserializeNBT(HolderLookup.Provider provider, T tag)
```

`DataHandlerUtils` 仅把泛型约束从 NeoForge 接口切换为上述 Mekanism 接口；列表顺序、byte 索引、空标签跳过和 NBT 键格式保持官方行为。后续 `IChemicalTank`、`IEnergyContainer`、`IExtendedFluidTank`、`IHeatCapacitor`、`IInventorySlot` 的父接口变化必须随各自完整依赖闭包迁移，不能为了扩大 allowlist 添加 stub。

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
