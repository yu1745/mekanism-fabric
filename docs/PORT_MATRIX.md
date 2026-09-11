# NeoForge 平台依赖清点

基线：Mekanism `v1.21.1-10.7.19.85`，Minecraft 1.21.1，Java 21。本文件是移植边界清单，不是逐类改写计划。目标仍是保留 `mekanism.api`、`mekanism.common`、`mekanism.client` 以及官方 holder、配方和机器领域架构；平台接入集中在少量 Fabric adapter 中。

## 扫描口径

在仓库根目录运行：

```bash
scripts/scan-platform-deps.py
```

当前快照（脚本按路径和 import 名排序，输出可重复）：

| 指标 | 数量 |
|---|---:|
| `src/**/*.java` | 2,445 |
| 含 NeoForge import 的文件 | 723 |
| NeoForge import 出现次数 | 1,480 |
| 不含 NeoForge import 的文件 | 1,722 |

分类是按脚本中的有序规则将每条 `net.neoforged` import **唯一归类**，不是工作量估算：

| 类别 | import | 文件 |
|---|---:|---:|
| lifecycle | 93 | 54 |
| registry | 117 | 88 |
| config | 72 | 43 |
| network | 84 | 79 |
| capability + attachments | 105 | 75 |
| item | 54 | 45 |
| fluid | 302 | 205 |
| chemical | 0 | 0 |
| energy | 6 | 5 |
| datagen | 106 | 93 |
| integration | 39 | 15 |
| events | 192 | 91 |
| client | 97 | 49 |
| other | 213 | 160 |

`chemical = 0` 是有意义的结果：NeoForge 没有 Mekanism chemical 类型；Chemical 是 Mekanism 自有领域模型，其平台边界通过 capability/lookup、物品暴露和网络同步出现。扫描器只统计 import；另有 3 个含全限定 `net.neoforged` 引用而无对应 import 的文件，应在实际改类时检查。注释、字符串、静态 import 和间接 Gradle 依赖不计入。因此 1,722 只能视为“优先原样保留候选”，不能仅凭此数字断言完全无平台耦合。

高频依赖显示真正的迁移热点：`FluidStack` 167、`DeferredHolder` 66、`IPayloadContext` 57、`ExistingFileHelper` 52、`FluidType` 52、`Tags` 38、`ModelData` 31、`IFluidHandler` 29、`IItemHandler` 28、`IEventBus` 27、`SubscribeEvent` 26、`BlockCapability`/`BlockCapabilityCache`/`ModConfigSpec` 各 25。

## 保留区与边界原则

### 纯 Minecraft / Java：原则上原样保留

- 数学、颜色、文本、序列化辅助、配方计算、CachedRecipe 调度、机器业务规则及 Chemical/Heat 数据模型中只使用 JDK、`net.minecraft`、Mojang codec/stream codec 的部分。
- 资源 ID、registry key、component/NBT 格式、配方 JSON、存档语义和包名。
- `mekanism.api` 中平台无关接口；调用方不应因为 Fabric 移植而改用新的 `mekanism.fabric.api`。
- 具体机器逻辑在 holder/transport adapter 可用前暂缓，而不是先重写。

### NeoForge 边界：窄 adapter 替换

不要在 Fabric 上大规模伪造 `net.neoforged.*` 类树。短期同名 facade 会把 NeoForge 生命周期、事件优先级、capability invalidation 和交易语义泄漏进核心，形成第二套不完整 NeoForge。应以 Mekanism 自有小接口或现有 holder 接口为缝，把 Fabric callback、lookup、transfer transaction 和 payload context 转成领域调用。只有 `FluidStack` 这类渗透极深的值对象可评估一个**项目自有、语义明确**的过渡值层；不得放在 `net.neoforged` namespace。

当某项对应关系不清楚时，先在本地 [`upstream/kilt`](../upstream/kilt) 查 NeoForge→Fabric 的承接方式，再在 [`upstream/connector`](../upstream/connector) 查 Fabric→NeoForge 的反向映射，重点核对对象转换、生命周期、side/context、失效和事务语义。参考结果用于缩小 Mekanism 专用 adapter，而不是引入完整兼容层。实际采用的映射应在对应实现或本表中记录参考仓库的 commit/路径；若直接采用代码，还必须核对并遵守许可证。

## 平台矩阵

优先级：P0 为建立可编译/可启动骨架，P1 为 API 与 Crusher 垂直切片必需，P2 为扩展机器与客户端，P3 为可选集成或后续兼容。

| 类别 | 关键 NeoForge 依赖与调用面 | Fabric 替代 / adapter 策略 | 优先级 | 风险 |
|---|---|---|---:|---|
| lifecycle | `@Mod`、`ModContainer`、FML common/client setup、`InterModEnqueueEvent`、`ServerLifecycleHooks`、dist marker | Fabric `ModInitializer`/`ClientModInitializer` 只做组合根；server lifecycle callbacks；把 setup 拆成显式、幂等的 common/client bootstrap，环境判断集中一处 | P0 | 高：FML 阶段顺序和 enqueue 时机没有一一对应关系；静态初始化可能早于 registry ready |
| registry | `DeferredRegister`、`DeferredHolder`、`RegisterEvent`、`NeoForgeRegistries`、data maps | 用 vanilla `Registry.register`、`RegistryKey`/holder；保留现有 Mekanism registry wrapper 的外形，在 Fabric 实现显式注册；NeoForge 私有 registry/data map 逐项映射为 vanilla registry、tag 或项目自有 reloadable table | P0 | 高：66 处 `DeferredHolder` 已进入类型签名；冻结时机、optional registry 与同步 ID 必须对账 |
| config | `ModConfigSpec`、`ConfigValue`、`ModConfigEvent`、`IConfigSpec`、config screen factory | 保留 config DTO、默认值和校验逻辑；用小型 config service 负责加载/保存/重载，服务端同步沿既有 packet；配置屏幕作为 client 可选 adapter | P0 | 中高：格式兼容、world/server config 路径和热重载语义；不要让 UI 库进入 common API |
| network | payload registrar/handler、`IPayloadContext`、`PacketDistributor`、`IContainerFactory`、NeoForge stream codecs | 使用 Fabric networking 的 payload type registry、play/configuration networking 与 vanilla `CustomPacketPayload`/`StreamCodec`；自有 context 仅暴露 player、enqueue/execute、reply；分发策略封装在 packet dispatcher | P0 | 高：线程切换、登录阶段、方向、断线及 packet ID/codec 必须逐项验证 |
| capability + attachments | `Block/Item/EntityCapability`、provider、register event、`BlockCapabilityCache`、attachments | 使用 Fabric `BlockApiLookup`/`ItemApiLookup`（适用处）和 entity/block hooks；以小型 lookup facade 隔离查询；缓存必须监听失效/区块卸载，数据附件用 Fabric attachment API 或明确的 component/持久化 owner | P1 | 很高：缓存失效、side/context、生命周期和持久化最容易造成复制、泄漏或 stale reference |
| item | `IItemHandler`、slot view、item capability；菜单/机器/物流的 insert/extract | 边界采用 Fabric Transfer `Storage<ItemVariant>` + `TransactionContext`，在 Mekanism inventory slot/handler 与 Storage 之间双向 adapter；领域库存仍保留 | P1 | 很高：NeoForge simulate/execute 与 Fabric transaction 不等价；嵌套事务、余量、slot 限制需测试 |
| fluid | `FluidStack`、`FluidType`、`IFluidHandler`/item、`BaseFlowingFluid`、fluid ingredients/crafting | lookup 边界采用 `Storage<FluidVariant>`；建立明确的 amount 单位换算和 Fabric transaction adapter；域内 tank/recipe 尽量保留。审计 `FluidStack` 数据 component、empty/equality/serialization 用法后再决定渐进值层 | P1 | 极高：302 imports/205 文件，是最大面；单位、component、温度/属性、桶装与配方 codec 均可能失真 |
| chemical | Mekanism `Chemical`/`ChemicalStack`、tank/handler、side exposure；平台 import 为 0 | 类型与 registry/API 原样保留；创建 Mekanism-owned `ChemicalApiLookup` 和 transaction-aware storage adapter，不假装它是 Fabric fluid，也不放入伪 NeoForge capability | P1 | 高：第三方互操作契约、空 stack、量纲、事务回滚和网络 registry ID |
| energy | `IEnergyStorage` 和 energy capability；Mekanism `IEnergyContainer` | 保留 Joule 与 container；提供独立 energy bridge，外部 Energy API（若选用）只在集成层做单位、上限、simulate/transaction 转换；核心不直接依赖第三方 energy API | P1 | 高：Fabric API 本身没有统一能源语义；单位比例、每 tick 限额和双向转换可能复制能量 |
| events | NeoForge event bus、`SubscribeEvent`、priority；block/entity/player/tick/level events、`EventHooks` | 按行为映射到 Fabric lifecycle/player/entity/world/tick callback；缺失事件用窄 mixin 发出 Mekanism 自有 hook；每个订阅记录触发侧、时机、取消和优先级 | P1 | 很高：192 imports；callback 覆盖面与取消/结果语义并非等价，mixin 冲突需最小化 |
| client | client events/extensions、key mappings、screens、render layers、`ModelData`/geometry/OBJ、`BakedModelWrapper` | `ClientModInitializer` 注册 screen/key/render callbacks；Fabric renderer/model loading API 或 vanilla model hooks；动态 model data 设计专用 model state adapter，OBJ 单独选择 loader/转换资源 | P2 | 很高：渲染 API 差异大且 client-only 类加载可使 dedicated server 崩溃 |
| datagen | `GatherDataEvent`、`ExistingFileHelper`、NeoForge providers/model generators/data maps | 使用 Fabric datagen entrypoint/providers；抽取“存在性检查”接口；能改为 vanilla provider/codec 的先改，NeoForge model builder 由窄生成 adapter 或资源预生成承接 | P2 | 中高：运行时不阻塞首个 server slice，但生成结果必须 byte/semantic diff，不能静默漏资源 |
| integration | `ModList`、InterModComms、optional mod API/test framework、CraftTweaker/JEI/EMI 等入口 | Loader mod presence + 每集成独立入口/adapter；用反射或独立 source set 保持 optional linkage；优先 EMI/Fabric 生态，NeoForge-only 集成明确禁用而非 stub | P3 | 中高：可选类验证、版本矩阵及服务端误加载 client 类；测试框架也需另建 Fabric 测试入口 |

## `other` 桶的处理

213 个 import 尚落在通用桶，主要包括 `common.Tags`、conditions/crafting、`INBTSerializable`、`Lazy`/函数式 helper、permissions、world/biome modifiers 和测试辅助。处理顺序不是批量建 shim：

1. JDK/Mojang 已有等价物（`Supplier`、codec、tag key 等）直接替换并保持调用语义；
2. recipe condition、permission、world modifier 分别建立小边界并补数据/行为测试；
3. 只被 gametest 使用的依赖不阻塞运行时 P0/P1，但不能混入发布物；
4. 每轮扫描收窄 `other` 规则，规则变化与报告数字一同评审。

## 分阶段门禁

1. **P0 骨架**：entrypoint 可在 dedicated server/client 启动；注册 ID/数量对账；配置默认值可读；最小 payload 往返且线程正确。
2. **P1 存储垂直切片**：Item/Fluid/Chemical/Energy 的 lookup、side、simulate/transaction、区块卸载和持久化测试通过；再接入 Crusher，验证重启/重连不吞不复制。
3. **P2 表现与生成**：动态模型、GUI、key binding 可用；datagen 产物与官方基线做清单和语义 diff。
4. **P3 集成**：每个 optional mod 独立验证“存在/不存在”两种环境；未移植集成必须显式关闭并记录。

更新本表时保留脚本原始输出或同步数字，并用 `rg 'net\.neoforged' src -g '*.java'` 检查 import 扫描未覆盖的全限定引用。计数下降只是趋势指标；真正门禁是编译、干净 Loader 启动、注册对账和交易/存档行为测试。
