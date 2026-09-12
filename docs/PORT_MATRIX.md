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
| energy | `IEnergyStorage` 和 energy capability；Mekanism `IEnergyContainer` | 保留 Joule 与 container；首个边界选用外置 Team Reborn Energy 4.1.0，仅在 Fabric adapter 做 caller-supplied 换算、true-long 与 nested transaction；核心/common 不直接依赖第三方 energy API | P1 | 高：Fabric API 本身没有统一能源语义；单位比例、fixed-point 量化、rollback side effect 与 owner 生命周期必须逐项验证 |
| events | NeoForge event bus、`SubscribeEvent`、priority；block/entity/player/tick/level events、`EventHooks` | 按行为映射到 Fabric lifecycle/player/entity/world/tick callback；缺失事件用窄 mixin 发出 Mekanism 自有 hook；每个订阅记录触发侧、时机、取消和优先级 | P1 | 很高：192 imports；callback 覆盖面与取消/结果语义并非等价，mixin 冲突需最小化 |
| client | client events/extensions、key mappings、screens、render layers、`ModelData`/geometry/OBJ、`BakedModelWrapper` | `ClientModInitializer` 注册 screen/key/render callbacks；Fabric renderer/model loading API 或 vanilla model hooks；动态 model data 设计专用 model state adapter，OBJ 单独选择 loader/转换资源 | P2 | 很高：渲染 API 差异大且 client-only 类加载可使 dedicated server 崩溃 |
| datagen | `GatherDataEvent`、`ExistingFileHelper`、NeoForge providers/model generators/data maps | 使用 Fabric datagen entrypoint/providers；抽取“存在性检查”接口；能改为 vanilla provider/codec 的先改，NeoForge model builder 由窄生成 adapter 或资源预生成承接 | P2 | 中高：运行时不阻塞首个 server slice，但生成结果必须 byte/semantic diff，不能静默漏资源 |
| integration | `ModList`、InterModComms、optional mod API/test framework、CraftTweaker/JEI/EMI 等入口 | Loader mod presence + 每集成独立入口/adapter；用反射或独立 source set 保持 optional linkage；优先 EMI/Fabric 生态，NeoForge-only 集成明确禁用而非 stub | P3 | 中高：可选类验证、版本矩阵及服务端误加载 client 类；测试框架也需另建 Fabric 测试入口 |

### 当前 P0 network 落点

首个窄切片已实现 configuration phase 的双向 payload、强制 `10.7.19` 精确协商和服务端 `ConfigurationTask` gate；common/client 注册类严格分离，dedicated packaged smoke 验证不会加载 client adapter。隔离 Loom server + 两个顺序启动的独立 Xvfb client JVM 已验证 matching round-trip、Netty callback 与每连接重新协商；另一个隔离门禁已验证错版、同版本显式 `accepted=false` 与缺失 payload receiver 的客户端在进入 play 前被拒绝且服务端继续运行。它只建立协议与阶段边界，不提前引入官方 packet catalog 或通用 context facade；同一 client 进程 reconnect、withheld-response timeout 策略以及 gameplay dispatcher 仍是 P0 未完成项，详见 `FABRIC_NETWORK.md`。

### 当前 platform-neutral utility 落点

当前还原样纳入 `DependentMapCodec`、`SequencedCollectionCodec`、`DroppingUnboundedMapCodec`（均与 `4646f509` 字节一致）。紧凑 Java 21 oracle 仅覆盖 dependency-first 选择及字段错误、sequenced encode/decode 边界/顺序/immutable/partial remainder，以及 dropping map 的 first-wins、保留 partial success/failed input 和重复 partial-value 的早返回；打包精确允许 1 个 API 与 3 个 common codec class，无 startup marker。`ModuleContainer`、`AttachedSideConfig`、recipes、data components、config/network owners 和所有 callers 延期；不声明 persistence、registry、packet、lifecycle-owner 或 serialized-format backward compatibility。

当前 allowlist 数量为 API **43**、common **88**、platform **14**。`Color`、`CustomEffect`、`Pos3D`、`Quaternion`、`Range3D`、`MultipartUtils`、`StatUtils` 七个 foundation 源文件均与 `4646f509` 字节一致；12 个本地 Java 21 测试仅界定 color/stat/effect、spatial/range/rotation/quaternion 与 direct raycast，JAR 精确限定 direct common-lib 四项、effect 一项、math 三项、Multipart 三项和 StatUtils 一项。renderers/particles/GUI/recipe viewers/client lifecycle、transmitters/multipart blocks/gear、machines/block types/entities、packet tracking/network ranges、SPS/multiblock、additions/generators、external energy、world/chunk lifecycle、registries/config/persistence 及所有 callers/owners 延期；无 rendering integration、entity/player lifecycle、block registration、networking、thread safety 或 collision-owner guarantee。

官方 `WildcardMatcher` 已原样纳入 bounded common slice，保持递归/回溯、`Locale.ROOT` 大小写折叠、`TagKey.location()` 和 UTF-16 `char`/`Character.isDigit(char)` 语义。Fabric 测试与 packaged JAR 门禁覆盖该边界；这不表示 QIO、miner、Finder 或其他未来调用方已经移植。官方 `ChunkUtils` 也原样纳入同一 slice，保留 `rangeClosed` 四方向的 inclusive 行主序遍历（X 最快、其次 Z）、packed `long` 行为与 `packedBlockToChunk` 对负数坐标的 `>> 4` floor-section 语义；返回数组按 inclusive 维度分配，representable/可分配矩形属调用者前置条件。同样保持 baseline 字节不变的 `BiLongMultimap`、`FilterTransformIterator`、`IndexedCuboidMap` 已构成仅内存的索引查找闭包；两份官方测试与 Fabric-local Java 21 边界测试覆盖双向 mutation、null filter/exhaustion、负 chunk 与 inclusive box、同 box replacement 和混合清理。JAR 精确限定六个 collection class entries。Miner/QIO/spatial caller、world/chunk lifecycle/loading、persistence、concurrency 与多线程 mutation 均未移植或声明。

新增 collection utility closure 的 `BiMultimap`、`EmptySequencedMap`、`HashList`、`LRU` 均与官方 `4646f509` 字节一致；测试边界仅为双向索引、ordered uniqueness、unique non-null LRU 与 immutable empty sequenced map，打包精确限制该 package 为 12 entries。security/frequency/filter/sync、QIO、`ModuleContainer`/gear/data components/codecs、GUI/recipe viewers/client lifecycle、networking/persistence/registries 及全部 callers/owners 延期；不声明 thread safety、concurrent mutation 或 general mutable-view guarantee。

官方 `LambdaMetaFactoryUtil` 同样保持 baseline 字节不变：空方法名通过 unreflect 绑定调用者已准备 accessibility 的实例 `Field`，非空方法名按精确类型绑定 named virtual getter/setter，因此 named method 可有计数、变换等不同于 raw field 的语义。Java 21 的 table/helper-driven Fabric 测试覆盖八种 primitive setter interface 分支、object/`BiConsumer`、装箱/拆箱、reference/null、两实例重复调用、lookup/access failure 与原异常传播。调用者必须保证 field accessibility/module openness、准确 `objType` 与方法签名、instance field、兼容 boxed value，并且 primitive setter 不接收 null；不扩展 static/final field。JAR allowlist 只验证 outer class、`CallSiteInfo` 和五个 private primitive setter interfaces 各一次且无同名前缀测试/helper，行为仍由测试证明，不增加 startup marker。此 prerequisite 不表示 `SyncMapper`、同步 annotations、tracked data、containers、networking、reflection scanning 或任何 caller 已移植。

`RegistryUtils` 以 holder-key helper 中的 `unwrapKey()` 与 registry helper 中的 `getResourceKey(...).map(ResourceKey::location)` 两个 vanilla 等价替换纳入；前者只对 key 未绑定的 intrusive `Holder.Reference` 恢复 NeoForge 的 nullable key，后者避免 defaulted registry 把未知 raw object 错报为默认 key，并保留 direct-holder 立即返回 null 的短路。测试固定 built-in block entity/block 的 canonical holder/key、NBT null/missing/wrong-type/malformed/unknown/valid ID、block path、reference/direct 与 key-only/value-only unbound holder，以及 registered/unknown generic lookup；正常 vanilla built-ins 下不可达的 `getBEHolder` null fallback 不通过 registry 变异强制覆盖。JAR 门禁只证明 production class 恰好一次且同名前缀 test/helper 不泄漏，不增加 startup marker；不声明 caller、registry mutation/lifecycle 或 custom registry 已移植。

官方 `EmitUtils`、`SplitInfo`、`IntegerSplitInfo`、`LongSplitInfo` 和 `Target` 也以 byte-identical 源码纳入同一 bounded common slice。它们保留 `int`/`long` 分流、低容量目标触发的重平衡、按 collection 插入序分 remainder，以及 simulation 高报但实际少收时的 fallback；`Target.acceptAmount` 必须把实际接收量传给 `SplitInfo.send`。官方 18-file root-test oracle 由精确路径 allowlist 原地编译，而非复制或广纳 root tests；发布物仅检查 `ChunkUtils` 与六个 distribution production class entries 和测试/helper 隔离，算法与坐标行为由 examples/properties/boundary tests 证明，不以 server startup marker 代替。

该原样算法维持官方调用契约而不增加策略：调用者提供非负 offer、每次分配使用未复用的 `Target`；handler 的 simulation/accept 必须遵守非负、容量及实际接收记账约定。负输入、复用已积累内部状态的 `Target`、或违约 handler 不获得额外防御保证。此落点不代表 energy/item/fluid/chemical transport caller、Fabric transaction/rollback、lookup/cache invalidation 或 machine/network owner 已移植，这些仍属于 P1 边界。

官方 `LongTransferUtils`、`InContainerGetterLong` 与 `LongToLongContainerInteraction` 也以 byte-identical 源码进入 API slice（该闭包纳入时 API 42 / common 70 / platform 10，无重复）。该闭包是顺序 generic/energy-container insert/extract helper，不是 fair-distribution、Fabric transaction 或外部 energy API adapter：insert 先按源顺序访问已有能量的 container，再按源顺序处理延迟的 empty container，每次传入当前 remainder 并在 remainder `<= 0` 时立即返回；extract 按源顺序遍历、忽略非正 drain、传入递减的 request、累加有效 drain，并在恰好满足时 break。`Action`/`AutomationType` 原样透传，大于 `Integer.MAX_VALUE` 的 true-`long` 不丢精度。Fabric 本地 Java 21 测试用可观测的调用/顺序/数值事件覆盖六个 overload family，并证明 energy-supplier extract 重载只对恰好 `0` 在 supplier lookup 之前短路、负值仍会到达 supplier 后被 explicit 重载拒绝。发布物门禁证明三个 production class 各恰好一次，并拒绝任何其它 `LongTransferUtils*.class`；无 startup marker。

调用者前置条件：offer/request 非负、explicit 重载的 `containerCount` 非负且与 fresh 一致 list 匹配、insert remainder 落在 `[0, offer]`、extract 结果落在 `[0, request]`、lookup 可安全接受 nullable side。官方源码不为不匹配的 count、越界 handler 返回或溢出增加防御策略，本切片也不增加。strict energy-handler 三层 API 与 byte-identical dynamic policy closure 已纳入；predicate、supplier 与 listener 保持 exact nullable-side/Action bridge。baseline 不存在旧文档误称的 concrete `EnergyHandler`；Task 023 已在此基础上增加窄 external Energy conversion/lookup/transaction seam，而 `EnergyContainerHelper`、`ConfigEnergyContainerHolder`、side configuration/ejector、Universal Cable、实际 provider/cache、机器/network/persistence owners 及全部 caller 仍属后续 P1 边界。

### 当前 registration/domain metadata foundations 落点

`BinTier`、`FactoryTier`、`QIODriveTier`、`IResource`、`MiscResource`、`BlockResourceInfo` 六源均与 `4646f509` 字节一致。6 个 Fabric-local 测试固定完整 tier 容量/process/type/count/BaseTier 元数据（含 creative `Integer.MAX_VALUE` 与 QIO `16_000_000_000L`）、真实 `CachedIntValue` current/default 切换并在 `finally` 恢复 enum 引用、完整资源 suffix/accessor 表，以及 ordinary/raw/luminous/exceptional property construction（包括 refined glowstone light 15 与 refined obsidian resistance 2400、portal/non-burning/blocked push）。`BlockResourceInfo` 仅证明 property construction，不证明 registered-block behavior。`PrimaryResource`、`ResourceType`、ore types/config、tags/datagen/registration、block/item registries and implementations、factories/bins/QIO items/recipes/attachments、`EnumUtils`、`TierConfig` 与 real config ownership/load/reload/lifecycle、other tier families、serialization/network/persistence/registry lifecycle/world behavior 均延期；不声明 broader Fabric integration。

### 当前 state/permission wrapper 落点

新增 `ProxiedHolder`、`ProxyHandler`、`ProxyHeatHandler`、`ProxyStrictEnergyHandler` 与 generic/float/int/long 四个 `ConfigBasedCached*Supplier`，八份源码均与官方 `4646f509` 字节一致。四个 proxy 测试固定 null side 固有只读（strict-energy set/indexed+aggregate insert/extract 与 heat indexed+aggregate mutation 均被抑制）而 getter 仍精确转发 null；非 null exact side、独立 insert/extract predicate、denied remainder/zero、allowed index/amount/`Action`/result，以及 energy/heat indexed+aggregate getter/mutation forwarding 均有可观测断言。四个 cache 测试固定 lazy first resolution、复用、dependency listener 注册、真实 `clearCache` invalidation 的 eager refresh、generic null sentinel 次次重求值、negative-zero raw float bits 与大 `long`。

该闭包不纳入任何 owners/callers、capability registration/lookup/exposure、external energy/transactions/conversion、side configuration/ejection、machines/transmitters/networks，也不证明 real config load/reload/file I/O/lifecycle/threading 或 persistence/network serialization；不据此声明 broader Fabric integration。

### 当前 signed-long config/tier metadata 落点

Task 019 当时的 allowlist 为 API **42** / common **74** / platform **10**。三个 energy/induction tier 源与 `4646f509` byte-identical；`CachedLongValue` 仅替换 NeoForge `Builder` 与 `ConfigValue` 两个 import。Fabric long schema 对合法 signed-long TOML integer literal 保持 `>2^53` 和端点精度，并在 Night Config 前以 decimal/hex/octal/binary lexical preflight 拒绝 modulo-wrap overflow；只规范化指定 primitive wrapper、精确 integral/representable 的 `BigInteger`/`BigDecimal` 与按 exact runtime IEEE 值检查的 finite Float/Double。fraction、NaN/infinity、overflow、range miss 和 `2^63` 均拒绝，绝不 rounding/truncation/saturation 或 lossy `double == long`。

边界仅是 schema/value support 与 tier metadata，不激活 `tiers.toml`。`TierConfig`、`MekanismConfig`/其他 real config files、其他 cached value kinds/tier families、config sync/UI/watchers/world restart、owners/callers、machines/blocks/items、capability registration、external energy/transactions、networking/serialization/persistence/lifecycle/threading 均延期。

### 当前 energy storage 落点

官方 `LastEnergyTracker` 与 `4646f509` 字节一致，作为平台中立的 per-game-time accounting closure：同时间戳累加 current bucket 且 visible last 不变，任何不同（含跳跃/递减）时间戳发布旧 bucket；tick zero、raw `long` 输入及 setter 仅控制 visible client-sync value 的语义由黑盒测试固定。JAR 的 direct `mekanism/common/lib` 由它、`WildcardMatcher` 与 `Color` 两项组成精确四项且各一次，无 startup marker。唯一生产 caller `TileEntityMekanism`、world tick/client synchronization、network owner/persistence、machines/networks，以及该 utility 与 Task 023 energy seam 的连接均延期。

官方 `IEnergyContainer`、`BasicEnergyContainer`、`IStrictEnergyHandler` / `ISidedStrictEnergyHandler` / `IMekanismStrictEnergyHandler` 及 `SimpleDynamicHandler` / `DynamicHandler` / `DynamicStrictEnergyHandler` 已作为内部 `long` storage/handler 窄切片纳入；后三份与 `4646f509` 字节一致。前两份 handler 接口仅删除 NeoForge fluid import，并将 Javadoc-only `fill` 链接改为保留 accepted amount 与 unaccepted remainder 差异的平台中立文字；后者与 baseline 字节一致。测试证明 indexed 非空优先 insertion、源序 extraction、unsided→指定 side 桥接、nullable side 与 `Action` 原样传递、null/internal 和 non-null/external automation、invalid-index defaults、negative-set clamp，以及 aggregate 每次只查一次 side-specific list；`canHandleEnergy=false` 时返回 empty list 仍是实现者契约。

`RelativeSide` 与非配置 `EnergyContainerHolder` 也已保留全方向映射、动态 facing 和 internal/external visibility。发布物门禁还精确限制 root capability 包为两个 dynamic base class、energy implementation 包为 `BasicEnergyContainer` 与 `DynamicStrictEnergyHandler`，各一次；不增加 startup marker，行为由 Fabric-local Java 21 测试证明。Task 023 在该内部切片之上增加 bounded Team Energy interoperability；`EnergyContainerHelper`/`ConfigEnergyContainerHolder`、side configuration/ejector、Universal Cable、actual provider/cache owners、machines、network/persistence 与所有 callers 均仍属 P1；baseline 没有旧称的 `EnergyHandler`，详见 `FABRIC_ENERGY.md`、`FABRIC_HOLDERS.md`。

### 当前 heat storage 落点

官方 `HeatAPI`、`IHeatCapacitor`、`BasicHeatCapacitor`、`VariableHeatCapacitor`、三层 handler interface 与非配置 `HeatCapacitorHolder` 已作为内部 `double` storage/routing 窄切片纳入，保留 ambient initialization、batched update、epsilon、listener、动态系数 supplier、capacity-weighted aggregation/distribution、relative-side routing 与 strict-double NBT 语义。Fabric access widener 精确承接官方 access transformer 对位置修正 biome temperature 的访问，不以 base temperature 降级。Config holder/helper、外部 heat API、transaction、network、machine owner 与 persistence lifecycle 仍属后续 P1 闭包，详见 `FABRIC_HEAT.md`、`FABRIC_HOLDERS.md`。

### Task 018 bounded internal energy support

四个新增 common sources（proxied energy holder、两个 energy distribution/save bridge、laser dissipation value handler）均与 `4646f509` byte-identical；该任务当时落点为 **42/70/10**。6 个 tests 只固定 nullable-side 独立权限/list identity、exact action/remainder/true-long/fallback、fair delayed/repeated save 与 clamped/delegate storage、以及 laser bounds/infinity/NaN raw comparisons；exact JAR families 拒绝 helper 泄漏。它不新增 wire protocol 或 adapter。owners/callers、transmitter/dynamic network lifecycle、emission ticks、capability exposure、Fabric Energy conversion/transactions/rollback、side config/ejection、machine/tile/item、laser attachment/damage、networking/serialization/persistence/threading 全部延期。

### Task 020 tile behavior contracts

Task 020 落地时 allowlist 为 API **42** / common **80** / platform **10**。`IHasDumpButton`、`IHasGasMode`、`IHasMode`、`IHasVisualization`、`ITileActive`、`IComparatorSupport` 均与官方 `4646f509` byte-identical。四个 Fabric-local JUnit contracts 只证明 exact mode/action callback dispatch（含 `0`/负 tank index 原样 forwarding）、visualization default/override/toggle、active `NO_DELAY`/default/override/raw round-trip，以及 comparator support default/override 与两个 level getter 对 boundary integers 的独立观察。JAR 的 direct `mekanism/common/tile/interfaces/` package 精确限制为这六个接口 class entry，各一次，不允许任何 nested/synthetic/test/helper/fixture entry；无 startup marker。

这是 contract-only allowlist，不接入 owner。所有 tile/machine implementation、`TileEntityMekanism`、GUI/button packet callers、block-state/comparator update scheduling、legacy gas 名称所涉及的 chemical behavior、capability registration/lookup/exposure、side configuration/ejection、persistence、networking/wire serialization、registry lifecycle、world ticking、thread ownership、client/server synchronization 均延期，不声明 broader integration。

### Task 021 energy/heat slot descriptors

Task 021 落地时 allowlist 为 API **42** / common **84** / platform **10**。`ISlotInfo`、`BaseSlotInfo`、`EnergySlotInfo`、`HeatSlotInfo` 均与官方 `4646f509` byte-identical。五个 Fabric-local contracts 精确证明四种 flag 组合的 accessor 与 OR-enabled policy、energy varargs identity/order 和零/全空语义、source-order scan 与首个 non-empty short-circuit、list identity 及后续 membership/contained-state live changes，以及 heat flags/list identity、恒 `isEmpty() == false` 且不读取 heat state。JAR direct slot package 精确只含四个同名 production class，各一次，无 nested/synthetic/test/helper/fixture entry 或 startup marker。

这是平台中立 descriptor 闭包，不是 side-configuration 执行。`ConfigInfo`、`DataType`、`TileComponentConfig`、`TileComponentEjector`、configurable holders/helpers、chemical/fluid/inventory/proxy descriptors、所有 tile/machine/block caller、capability exposure、side-configuration execution、ejection、persistence、networking/wire serialization、config/registry lifecycle、transactions 与 thread ownership 仍延期，不声明 broader integration。

### Task 022 holiday value foundations

Task 022 落地时 allowlist 为 API **42** / common **88** / platform **10**。`KnownDate`、`MonthlyDate`、`YearlyDate`、`HolidayMessage` 四个 package-private value sources 与 `4646f509` byte-identical。一个同包测试类中的五个 deterministic JUnit contracts 分别证明 yearly exact month/day（含 Feb 29 value、相邻日和异月反例）、monthly 同月多日/异月同日、`now()` 前后 `LocalDate.now()` snapshot rollover 容忍、独立 equivalent component/ordered line content 的 deep-array equality/hash 与不等反例，以及 component/varargs backing array exact alias、顺序和 caller mutation visibility。`YearlyDate.now()` 使用 JVM default clock/zone，测试不修改 clock/time-zone globals；`HolidayMessage` 保留 component/array references，不是 deep immutable。

静态 JAR gate 要求 `mekanism/common/base/holiday/` 精确只有 `KnownDate.class`、`MonthlyDate.class`、`YearlyDate.class`、`HolidayMessage.class` 各一次，拒绝所有其它 direct/nested/synthetic/test/helper/fixture class，不增加 startup marker。`Holiday`、`HolidayManager`、每个 concrete holiday、player notification、translations/colors/skins、client holiday state、lifecycle/event wiring、persistence、networking、registries、config 与 world/tick/thread ownership 全部延期；不声明 broader holiday integration。

### Task 023 Team Reborn Energy transaction boundary

当前 allowlist 为 API **43** / common **88** / platform **14**。官方 `IEnergyConversion` 与 `4646f509` byte-identical；四个独立实现的 `mekanism.fabric.energy` source 建立 Team Reborn Energy **4.1.0** 的首个双向、true-long 边界。依赖通过 Fabric Maven 解析并保持 external（不 `include`）。shared helper 仅接受 enabled、finite、strict-positive conversion；exact `1.0D` 是 adapter 的 authoritative identity contract，在内部绕过 unchanged official double round-trip并完整保留所有 positive long；conversion override 若与其 advertised 1:1 rate 冲突则不受支持。非 1 rate 使用固定、bound-independent 候选族：全局 10-bit dyadic mesh（1..1,023 全整数，随后每个 fitting shift 的 512..1,023 mantissa），加 canonical-decimal continued fraction 每个 denominator 的 1..64 bounded multiplier closure 与 fitting binary shifts。bounds 只过滤候选；对 documented stable conversion behavior，返回两域不越界且通过官方 `convertFrom`/`convertTo` 的最大 eligible member，因此 deterministic 并对两域 bound 分别 monotone，无 caller-bound-sized scan。它仍是 conservative finite selection，不声称 globally maximal 或通用 utilization 下限；无 verified family member 时可返回 zero。完整 cold build 是 28,159 dyadic 加至多 92×64×63 decimal occurrences，总计 sort/dedup 前不超过 399,103。四个 synchronized weak-identity slots 以 raw rate bits 发布 primitive-only immutable snapshot；每次实时咨询 enabled，rate bit 变化重建，distinct conversion identity 不共享 verified pair。warm selection 是 `O(log U)`，不再运行 BigDecimal/Euclid/full-family enumeration，并在返回前用 owner identity 重新双向验证；若请求窗口内无旧 pair 且 snapshot 非空，则用首个 cached pair 作单一 behavior sentinel。zero-pair snapshot 无 sentinel，所有 cache 都要求 owner 在 advertised rate bits 不变时保持 conversion behavior 稳定。回归覆盖无 bare convergent 可验证的 `372965497689.465`（243↔90,630,615,938,540 与 600↔223,779,298,613,679），以及 `10.906989658246147`/`575.403` 在 `Long.MAX_VALUE` 两向至少 99% official projection；后者只是 named-rate test evidence。2.5、0.4、0.7 和 reviewer 682↔9,634,898,391,631 pair 亦保留；mutable-rate counting regression 固定 invalidation、identity isolation 与 warm call envelope；不硬编码 conversion 或 FE 等价。

外部→Mekanism adapter 在 lifecycle `NONE` 开 outer transaction；`OPEN` 时才使用 Transfer API 为 compat 明确保留的 `Transaction.getCurrentUnsafe()` 并开 nested transaction。`CLOSING`/`OUTER_CLOSING` callback 中 insertion 原样返回 remainder、extraction 返回 zero，不访问 storage 或非法读取 current transaction。`SIMULATE` 必定 abort，`EXECUTE` 只在 probe 与实际 acceptance 一致且 fixed-point 可报告时 commit；child commit 仍由 parent abort 回滚。Mekanism→外部 adapter 是 `SnapshotParticipant<long[]>`，mutation 前检查固定 count 并 snapshot 全部 captured containers。execute 中 count 改变会 abort/restore 并报告 zero，restore 不重新检查已变 count；之后 insert/extract 与 `stableHandler()` fail-fast，而 supports 查询返回 false、amount/capacity 返回 zero（聚合途中发现 drift 亦同）。正常 amount/capacity 先饱和汇总 raw Joules 再换算一次，exact-1 无损，non-1 保留 official double rounding。

lookup/factory 只接受 physical non-null side，拒绝 unusable conversion、`canHandleEnergy()==false` 与 empty side；storage 记录 exact exposure side，任何 side mismatch 无论 conversion 都返回 null；same-side/identical-conversion unwrap stable handler，same-side/other-usable-conversion 可 wrap。`ProxyStrictEnergyHandler` 与 storage retained suppliers 都实时咨询 exact side + nullable `IHolder`，`supports*` 也随 allow→deny→allow 改变；owner 必须在 support 改变时通知 neighbors，该 wiring 延期。rollback backing 的每个 captured container 必须 deterministic 支持 `setEnergy`；known no-op `FabricStrictEnergyHandler` 被构造器拒绝。restore best-effort 尝试每个 captured setter，并在每次 attempt 后 read back 同一 index；setter/read throw 或 capacity-shrink clamp 等 mismatch 都按首个 primary、后续 suppressed 聚合传播。restore failure 可能留下 mutation，故明确不保证 atomicity。即使完整恢复，execute/restore immediate listener/save/sync side effect 仍可见且不具事务性。

延期：`ConfigHolder`/`ConfigInfo`/`DataType`/`ISideConfiguration`/configurable holders/helpers、`TileComponentConfig`/`TileComponentEjector`、实际 block/item/entity provider registration 与 owner caching/invalidation、cables/networks/emission/machines、hardcoded FE config activation、item/fluid/chemical/heat adapters、persistence、packets/client sync 和 thread safety。八个 focused JUnit methods 是 transaction 语义证据；packaged smoke 只验证 exact API/platform class families、Energy dependency 不嵌套、4.1.0 metadata/class、两 mod IDs、remapping/classloading 与 dedicated-server safety，不伪造 startup transfer marker。

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
