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
| `src/**/*.java` | 2,448 |
| 含 NeoForge import 的文件 | 709 |
| NeoForge import 出现次数 | 1,462 |
| 不含 NeoForge import 的文件 | 1,739 |

分类是按脚本中的有序规则将每条 `net.neoforged` import **唯一归类**，不是工作量估算：

| 类别 | import | 文件 |
|---|---:|---:|
| lifecycle | 93 | 54 |
| registry | 115 | 86 |
| config | 63 | 36 |
| network | 84 | 79 |
| capability + attachments | 105 | 75 |
| item | 54 | 45 |
| fluid | 299 | 203 |
| chemical | 0 | 0 |
| energy | 6 | 5 |
| datagen | 106 | 93 |
| integration | 39 | 15 |
| events | 192 | 91 |
| client | 97 | 49 |
| other | 209 | 156 |

`chemical = 0` 是有意义的结果：NeoForge 没有 Mekanism chemical 类型；Chemical 是 Mekanism 自有领域模型，其平台边界通过 capability/lookup、物品暴露和网络同步出现。扫描器只统计 import；另有 3 个含全限定 `net.neoforged` 引用而无对应 import 的文件，应在实际改类时检查。注释、字符串、静态 import 和间接 Gradle 依赖不计入。因此 1,739 只能视为“优先原样保留候选”，不能仅凭此数字断言完全无平台耦合。

高频依赖显示真正的迁移热点：`FluidStack` 166、`DeferredHolder` 65、`IPayloadContext` 57、`ExistingFileHelper`/`FluidType` 各 52、`Tags` 38、`ModelData` 31、`IFluidHandler`/`IItemHandler` 各 28、`IEventBus` 27、`SubscribeEvent` 26、`BlockCapability`/`BlockCapabilityCache` 各 25、`ModConfigSpec` 21。

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

### 当前 P0 network 落点

首个窄切片已实现 configuration phase 的双向 payload、强制 `10.7.19` 精确协商和服务端 `ConfigurationTask` gate；common/client 注册类严格分离，dedicated packaged smoke 验证不会加载 client adapter。隔离 Loom server + 两个顺序启动的独立 Xvfb client JVM 已验证 matching round-trip、Netty callback 与每连接重新协商；另一个隔离门禁已验证错版、同版本显式 `accepted=false` 与缺失 payload receiver 的客户端在进入 play 前被拒绝且服务端继续运行。它只建立协议与阶段边界，不提前引入官方 packet catalog 或通用 context facade；同一 client 进程 reconnect、withheld-response timeout 策略以及 gameplay dispatcher 仍是 P0 未完成项，详见 `FABRIC_NETWORK.md`。

### 当前 platform-neutral utility 落点

当前 allowlist 数量为 API **41**、common **32**、platform **10**。官方 `WildcardMatcher` 已原样纳入 bounded common slice，保持递归/回溯、`Locale.ROOT` 大小写折叠、`TagKey.location()` 和 UTF-16 `char`/`Character.isDigit(char)` 语义。Fabric 测试与 packaged JAR 门禁覆盖该边界；这不表示 QIO、miner、Finder 或其他未来调用方已经移植。官方 `ChunkUtils` 也原样纳入同一 slice，保留 `rangeClosed` 四方向的 inclusive 行主序遍历（X 最快、其次 Z）、packed `long` 行为与 `packedBlockToChunk` 对负数坐标的 `>> 4` floor-section 语义；返回数组按 inclusive 维度分配，representable/可分配矩形属调用者前置条件。这不表示 `IndexedCuboidMap`、空间索引、QIO/miner 或任何调用方已移植。

官方 `LambdaMetaFactoryUtil` 同样保持 baseline 字节不变：空方法名通过 unreflect 绑定调用者已准备 accessibility 的实例 `Field`，非空方法名按精确类型绑定 named virtual getter/setter，因此 named method 可有计数、变换等不同于 raw field 的语义。Java 21 的 table/helper-driven Fabric 测试覆盖八种 primitive setter interface 分支、object/`BiConsumer`、装箱/拆箱、reference/null、两实例重复调用、lookup/access failure 与原异常传播。调用者必须保证 field accessibility/module openness、准确 `objType` 与方法签名、instance field、兼容 boxed value，并且 primitive setter 不接收 null；不扩展 static/final field。JAR allowlist 只验证 outer class、`CallSiteInfo` 和五个 private primitive setter interfaces 各一次且无同名前缀测试/helper，行为仍由测试证明，不增加 startup marker。此 prerequisite 不表示 `SyncMapper`、同步 annotations、tracked data、containers、networking、reflection scanning 或任何 caller 已移植。

`RegistryUtils` 以 holder-key helper 中的 `unwrapKey()` 与 registry helper 中的 `getResourceKey(...).map(ResourceKey::location)` 两个 vanilla 等价替换纳入；前者只对 key 未绑定的 intrusive `Holder.Reference` 恢复 NeoForge 的 nullable key，后者避免 defaulted registry 把未知 raw object 错报为默认 key，并保留 direct-holder 立即返回 null 的短路。测试固定 built-in block entity/block 的 canonical holder/key、NBT null/missing/wrong-type/malformed/unknown/valid ID、block path、reference/direct 与 key-only/value-only unbound holder，以及 registered/unknown generic lookup；正常 vanilla built-ins 下不可达的 `getBEHolder` null fallback 不通过 registry 变异强制覆盖。JAR 门禁只证明 production class 恰好一次且同名前缀 test/helper 不泄漏，不增加 startup marker；不声明 caller、registry mutation/lifecycle 或 custom registry 已移植。

官方 `EmitUtils`、`SplitInfo`、`IntegerSplitInfo`、`LongSplitInfo` 和 `Target` 也以 byte-identical 源码纳入同一 bounded common slice。它们保留 `int`/`long` 分流、低容量目标触发的重平衡、按 collection 插入序分 remainder，以及 simulation 高报但实际少收时的 fallback；`Target.acceptAmount` 必须把实际接收量传给 `SplitInfo.send`。官方 16-file root-test oracle 由精确路径 allowlist 原地编译，而非复制或广纳 root tests；发布物仅检查 `ChunkUtils` 与六个 distribution production class entries 和测试/helper 隔离，算法与坐标行为由 examples/properties/boundary tests 证明，不以 server startup marker 代替。

该原样算法维持官方调用契约而不增加策略：调用者提供非负 offer、每次分配使用未复用的 `Target`；handler 的 simulation/accept 必须遵守非负、容量及实际接收记账约定。负输入、复用已积累内部状态的 `Target`、或违约 handler 不获得额外防御保证。此落点不代表 energy/item/fluid/chemical transport caller、Fabric transaction/rollback、lookup/cache invalidation 或 machine/network owner 已移植，这些仍属于 P1 边界。

官方 `LongTransferUtils`、`InContainerGetterLong` 与 `LongToLongContainerInteraction` 也以 byte-identical 源码进入 API slice（当前 API 41 / common 32 / platform 10，无重复）。该闭包是顺序 generic/energy-container insert/extract helper，不是 fair-distribution、Fabric transaction 或外部 energy API adapter：insert 先按源顺序访问已有能量的 container，再按源顺序处理延迟的 empty container，每次传入当前 remainder 并在 remainder `<= 0` 时立即返回；extract 按源顺序遍历、忽略非正 drain、传入递减的 request、累加有效 drain，并在恰好满足时 break。`Action`/`AutomationType` 原样透传，大于 `Integer.MAX_VALUE` 的 true-`long` 不丢精度。Fabric 本地 Java 21 测试用可观测的调用/顺序/数值事件覆盖六个 overload family，并证明 energy-supplier extract 重载只对恰好 `0` 在 supplier lookup 之前短路、负值仍会到达 supplier 后被 explicit 重载拒绝。发布物门禁证明三个 production class 各恰好一次，并拒绝任何其它 `LongTransferUtils*.class`；无 startup marker。

调用者前置条件：offer/request 非负、explicit 重载的 `containerCount` 非负且与 fresh 一致 list 匹配、insert remainder 落在 `[0, offer]`、extract 结果落在 `[0, request]`、lookup 可安全接受 nullable side。官方源码不为不匹配的 count、越界 handler 返回或溢出增加防御策略，本切片也不增加。strict energy-handler 三层 API 已在当前闭包纳入；concrete `EnergyHandler`、configurable holder/helper、机器、network owner、Team Reborn Energy API、Joule/FE 换算、Fabric lookup/transaction 代码，以及 `ComponentBackedEnergyHandler`、`EnergyRecipeData` 等全部 caller 仍属后续 P1 边界。

### 当前 energy storage 落点

官方 `IEnergyContainer`、`BasicEnergyContainer` 及 `IStrictEnergyHandler` / `ISidedStrictEnergyHandler` / `IMekanismStrictEnergyHandler` 已作为内部 `long` storage/handler 窄切片纳入。前两份 handler 接口仅删除 NeoForge fluid import，并将 Javadoc-only `fill` 链接改为保留 accepted amount 与 unaccepted remainder 差异的平台中立文字；后者与 baseline 字节一致。测试证明 indexed 非空优先 insertion、源序 extraction、unsided→指定 side 桥接、nullable side 与 `Action` 原样传递、null/internal 和 non-null/external automation、invalid-index defaults、negative-set clamp，以及 aggregate 每次只查一次 side-specific list；`canHandleEnergy=false` 时返回 empty list 仍是实现者契约。

`RelativeSide` 与非配置 `EnergyContainerHolder` 也已保留全方向映射、动态 facing 和 internal/external visibility。发布物门禁只证明三接口与既有 `IEnergyContainer` 恰好存在一次，并拒绝当前闭包之外的其它 energy API class；不增加 startup marker，行为由 Fabric-local Java 21 测试证明。该切片不宣称 Fabric energy interoperability：concrete `EnergyHandler`、config holder、external Energy API、Joule/FE/Energy conversion、lookup/transaction、owners、machines、network 与 callers 均仍属 P1 后续边界，详见 `FABRIC_ENERGY.md`、`FABRIC_HOLDERS.md`。

### 当前 heat storage 落点

官方 `HeatAPI`、`IHeatCapacitor`、`BasicHeatCapacitor`、`VariableHeatCapacitor`、三层 handler interface 与非配置 `HeatCapacitorHolder` 已作为内部 `double` storage/routing 窄切片纳入，保留 ambient initialization、batched update、epsilon、listener、动态系数 supplier、capacity-weighted aggregation/distribution、relative-side routing 与 strict-double NBT 语义。Fabric access widener 精确承接官方 access transformer 对位置修正 biome temperature 的访问，不以 base temperature 降级。Config holder/helper、外部 heat API、transaction、network、machine owner 与 persistence lifecycle 仍属后续 P1 闭包，详见 `FABRIC_HEAT.md`、`FABRIC_HOLDERS.md`。

## `other` 桶的处理

209 个 import 尚落在通用桶，主要包括 `common.Tags`、conditions/crafting、`INBTSerializable`、`Lazy`/函数式 helper、permissions、world/biome modifiers 和测试辅助。处理顺序不是批量建 shim：

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
