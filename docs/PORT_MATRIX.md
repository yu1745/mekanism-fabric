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
| registry | `DeferredRegister`、`DeferredHolder`、`RegisterEvent`、`NeoForgeRegistries`、data maps | built-in 走 vanilla register/holder；Task 037 foundation + Task 040 建立首个 canonical 65-entry MODDED/SYNCED Chemical root；Task 038 ordinary damage map 与 Task 040 five-map atomic Chemical owner 共用层中 descriptor/loader。其它 registry families 仍逐 owner 映射 | P0 | 高：Chemical canonical EMPTY、官方 raw-order verification/name remap、construction freeze/idempotent snapshot refreeze、resource-stack/tag/reload/cleanup 已门禁；其它 owner 仍需单独对账 |
| config | `ModConfigSpec`、`ConfigValue`、`ModConfigEvent`、`IConfigSpec`、config screen factory | Task 039 physical-client window owner之外，Task 040 启用 official selected five-value `common.toml` 与唯一 preserving world `general.toml` Chemical policy fragment；其它 DTO/UI 逐 owner 接入 | P0 | 中高：strict enum/range、normalized claim、publish/reset 与 connection policy separation 已门禁；完整 config facade、watch/event/backup/UI 仍延期 |
| network | payload registrar/handler、`IPayloadContext`、`PacketDistributor`、`IContainerFactory`、NeoForge stream codecs | Fabric configuration 先做 exact 10.7.19 negotiation；Task 040 再以 ordered task发送 bounded descriptor-codec Chemical snapshot并等待 ACK，play S2C只发布 successful reload epoch | P0 | 高：owner-executor resolution、name references、bounds、unknown/duplicate/stale/regressing rejection、same-process reconnect 已门禁；通用 dispatcher 与 withheld-ACK timeout 仍延期 |
| capability + attachments | `Block/Item/EntityCapability`、provider、register event、`BlockCapabilityCache`、attachments | 使用 Fabric `BlockApiLookup`/`ItemApiLookup`（适用处）和 entity/block hooks；以小型 lookup facade 隔离查询；缓存必须监听失效/区块卸载，数据附件用 Fabric attachment API 或明确的 component/持久化 owner | P1 | 很高：缓存失效、side/context、生命周期和持久化最容易造成复制、泄漏或 stale reference |
| item | `IItemHandler`、slot view、item capability；菜单/机器/物流的 insert/extract | Task 033 gateway 上已由 Task 039 接入 Basic/Input/Output、真实 container/player/virtual slot、warning 与 InventorySlotInfo；反向 adapter、provider/cache 与 machine owner 延期 | P1 | 很高：real slot commit/rollback 与 menu-slot behavior 已门禁；provider invalidation、side config/ejector 和机器生命周期尚待实现 |
| fluid | `FluidStack`、`FluidType`、`IFluidHandler`/item、`BaseFlowingFluid`、fluid ingredients/crafting | Task 034 已建立 Mekanism-owned int-mB `FluidStack` 与 exact factor-81 `FluidVariant` value/unit seam；后续 lookup 采用 `Storage<FluidVariant>` 并单独实现 transaction/tank/owner，Task 036 后其余 import replacements 仍逐闭包迁移（Task 042A 迁移其中 14 个 recipe 源） | P1 | 极高：值层只关闭 empty/component/codec/unit/flowing-normalization；温度/属性、事务、桶装、配方、owner lifecycle 仍可能失真 |
| chemical | Mekanism `Chemical`/`ChemicalStack`、tank/handler、side exposure；平台 import 为 0 | Task 040 已接入 internal registry/domain、true-long stack/tank、dynamic/sided/proxy handler、non-config holder、slot info、five-map attributes/tooltips；未来另建 Mekanism-owned lookup/transaction adapter，不假装 Fabric fluid | P1 | 高：canonical EMPTY、amount/NBT/wire、tank admission、name-based sync与owner isolation已门禁；第三方 provider/invalidation/transaction rollback 未承诺 |
| energy | `IEnergyStorage` 和 energy capability；Mekanism `IEnergyContainer` | 保留 Joule 与 container；首个边界选用外置 Team Reborn Energy 4.1.0，仅在 Fabric adapter 做 caller-supplied 换算、true-long 与 nested transaction；核心/common 不直接依赖第三方 energy API | P1 | 高：Fabric API 本身没有统一能源语义；单位比例、fixed-point 量化、rollback side effect 与 owner 生命周期必须逐项验证 |
| events | NeoForge event bus、`SubscribeEvent`、priority；block/entity/player/tick/level events、`EventHooks` | 按行为映射到 Fabric lifecycle/player/entity/world/tick callback；缺失事件用窄 mixin 发出 Mekanism 自有 hook；每个订阅记录触发侧、时机、取消和优先级 | P1 | 很高：192 imports；callback 覆盖面与取消/结果语义并非等价，mixin 冲突需最小化 |
| client | client events/extensions、key mappings、screens、render layers、`ModelData`/geometry/OBJ、`BakedModelWrapper` | `ClientModInitializer` 注册 screen/key/render callbacks；Fabric renderer/model loading API 或 vanilla model hooks；动态 model data 设计专用 model state adapter，OBJ 单独选择 loader/转换资源 | P2 | 很高：渲染 API 差异大且 client-only 类加载可使 dedicated server 崩溃 |
| datagen | `GatherDataEvent`、`ExistingFileHelper`、NeoForge providers/model generators/data maps | 使用 Fabric datagen entrypoint/providers；抽取“存在性检查”接口；能改为 vanilla provider/codec 的先改，NeoForge model builder 由窄生成 adapter 或资源预生成承接 | P2 | 中高：运行时不阻塞首个 server slice，但生成结果必须 byte/semantic diff，不能静默漏资源 |
| integration | `ModList`、InterModComms、optional mod API/test framework、CraftTweaker/JEI/EMI 等入口 | Loader mod presence + 每集成独立入口/adapter；用反射或独立 source set 保持 optional linkage；优先 EMI/Fabric 生态，NeoForge-only 集成明确禁用而非 stub | P3 | 中高：可选类验证、版本矩阵及服务端误加载 client 类；测试框架也需另建 Fabric 测试入口 |

### 当前 P0 network 落点

configuration phase 的双向 payload 和服务端 `ConfigurationTask` gate 使用官方版本 `10.7.19` 精确协商。此前双客户端 smoke 在临时 Fabric 版本后缀上通过，并不直接证明恢复官方版本后的运行结果；当前旧隔离 rejection fixture 缺少新增内容注册，会在 Fabric 注册表同步时先断开，尚不能证明配置握手的真实拒绝路径。common/client 注册类继续分离；同一 client 进程 reconnect 和 withheld-response timeout 策略仍未验证，详见 `FABRIC_NETWORK.md`。

### 当前 platform-neutral utility 落点

当前还原样纳入 `DependentMapCodec`、`SequencedCollectionCodec`、`DroppingUnboundedMapCodec`（均与 `4646f509` 字节一致）。紧凑 Java 21 oracle 仅覆盖 dependency-first 选择及字段错误、sequenced encode/decode 边界/顺序/immutable/partial remainder，以及 dropping map 的 first-wins、保留 partial success/failed input 和重复 partial-value 的早返回；打包精确允许 1 个 API 与 3 个 common codec class，无 startup marker。`ModuleContainer`、`AttachedSideConfig`、recipes、data components、config/network owners 和所有 callers 延期；不声明 persistence、registry、packet、lifecycle-owner 或 serialized-format backward compatibility。

Task 041 候选态当前 allowlist 数量为 API **129**、common **184**、platform **37**。Task 040 集成态曾为 API **98**、common **164**、platform **33**。`Color`、`CustomEffect`、`Pos3D`、`Quaternion`、`Range3D`、`MultipartUtils`、`StatUtils` 七个 foundation 源文件均与 `4646f509` 字节一致；12 个本地 Java 21 测试仅界定 color/stat/effect、spatial/range/rotation/quaternion 与 direct raycast，JAR 精确限定 direct common-lib 四项、effect 一项、math 三项、Multipart 三项和 StatUtils 一项。这些 foundation 本身不纳入 renderers/particles/GUI/recipe viewers、transmitters/multipart blocks/gear、machines/block types/entities、packet tracking/network ranges、SPS/multiblock、additions/generators、external energy 或 world/chunk lifecycle。Task 039 的窄 client-window lifecycle/config owner 以本页对应小节为准；完整 ClientConfig/QIO 与其它 config/persistence callers/owners 仍延期。

官方 `WildcardMatcher` 已原样纳入 bounded common slice，保持递归/回溯、`Locale.ROOT` 大小写折叠、`TagKey.location()` 和 UTF-16 `char`/`Character.isDigit(char)` 语义。Fabric 测试与 packaged JAR 门禁覆盖该边界；这不表示 QIO、miner、Finder 或其他未来调用方已经移植。官方 `ChunkUtils` 也原样纳入同一 slice，保留 `rangeClosed` 四方向的 inclusive 行主序遍历（X 最快、其次 Z）、packed `long` 行为与 `packedBlockToChunk` 对负数坐标的 `>> 4` floor-section 语义；返回数组按 inclusive 维度分配，representable/可分配矩形属调用者前置条件。同样保持 baseline 字节不变的 `BiLongMultimap`、`FilterTransformIterator`、`IndexedCuboidMap` 已构成仅内存的索引查找闭包；两份官方测试与 Fabric-local Java 21 边界测试覆盖双向 mutation、null filter/exhaustion、负 chunk 与 inclusive box、同 box replacement 和混合清理。JAR 精确限定六个 collection class entries。Miner/QIO/spatial caller、world/chunk lifecycle/loading、persistence、concurrency 与多线程 mutation 均未移植或声明。

新增 collection utility closure 的 `BiMultimap`、`EmptySequencedMap`、`HashList`、`LRU` 均与官方 `4646f509` 字节一致；测试边界仅为双向索引、ordered uniqueness、unique non-null LRU 与 immutable empty sequenced map，打包精确限制该 package 为 12 entries。security/frequency/filter/sync、QIO、`ModuleContainer`/gear/data components/codecs、GUI/recipe viewers/client lifecycle、networking/persistence/registries 及全部 callers/owners 延期；不声明 thread safety、concurrent mutation 或 general mutable-view guarantee。

官方 `LambdaMetaFactoryUtil` 同样保持 baseline 字节不变：空方法名通过 unreflect 绑定调用者已准备 accessibility 的实例 `Field`，非空方法名按精确类型绑定 named virtual getter/setter，因此 named method 可有计数、变换等不同于 raw field 的语义。Java 21 的 table/helper-driven Fabric 测试覆盖八种 primitive setter interface 分支、object/`BiConsumer`、装箱/拆箱、reference/null、两实例重复调用、lookup/access failure 与原异常传播。调用者必须保证 field accessibility/module openness、准确 `objType` 与方法签名、instance field、兼容 boxed value，并且 primitive setter 不接收 null；不扩展 static/final field。JAR allowlist 只验证 outer class、`CallSiteInfo` 和五个 private primitive setter interfaces 各一次且无同名前缀测试/helper，行为仍由测试证明，不增加 startup marker。此 prerequisite 不表示 `SyncMapper`、同步 annotations、tracked data、containers、networking、reflection scanning 或任何 caller 已移植。

`RegistryUtils` 以 holder-key helper 中的 `unwrapKey()` 与 registry helper 中的 `getResourceKey(...).map(ResourceKey::location)` 两个 vanilla 等价替换纳入；前者只对 key 未绑定的 intrusive `Holder.Reference` 恢复 NeoForge 的 nullable key，后者避免 defaulted registry 把未知 raw object 错报为默认 key，并保留 direct-holder 立即返回 null 的短路。测试固定 built-in block entity/block 的 canonical holder/key、NBT null/missing/wrong-type/malformed/unknown/valid ID、block path、reference/direct 与 key-only/value-only unbound holder，以及 registered/unknown generic lookup；正常 vanilla built-ins 下不可达的 `getBEHolder` null fallback 不通过 registry 变异强制覆盖。JAR 门禁只证明 production class 恰好一次且同名前缀 test/helper 不泄漏，不增加 startup marker；不声明 caller、registry mutation/lifecycle 或 custom registry 已移植。

官方 `EmitUtils`、`SplitInfo`、`IntegerSplitInfo`、`LongSplitInfo` 和 `Target` 也以 byte-identical 源码纳入同一 bounded common slice。它们保留 `int`/`long` 分流、低容量目标触发的重平衡、按 collection 插入序分 remainder，以及 simulation 高报但实际少收时的 fallback；`Target.acceptAmount` 必须把实际接收量传给 `SplitInfo.send`。官方 18-file root-test oracle 由精确路径 allowlist 原地编译，而非复制或广纳 root tests；发布物仅检查 `ChunkUtils` 与六个 distribution production class entries 和测试/helper 隔离，算法与坐标行为由 examples/properties/boundary tests 证明，不以 server startup marker 代替。

该原样算法维持官方调用契约而不增加策略：调用者提供非负 offer、每次分配使用未复用的 `Target`；handler 的 simulation/accept 必须遵守非负、容量及实际接收记账约定。负输入、复用已积累内部状态的 `Target`、或违约 handler 不获得额外防御保证。此落点不代表 energy/item/fluid/chemical transport caller、Fabric transaction/rollback、lookup/cache invalidation 或 machine/network owner 已移植，这些仍属于 P1 边界。

官方 `LongTransferUtils`、`InContainerGetterLong` 与 `LongToLongContainerInteraction` 也以 byte-identical 源码进入 API slice（该闭包纳入时 API 42 / common 70 / platform 10，无重复）。该闭包是顺序 generic/energy-container insert/extract helper，不是 fair-distribution、Fabric transaction 或外部 energy API adapter：insert 先按源顺序访问已有能量的 container，再按源顺序处理延迟的 empty container，每次传入当前 remainder 并在 remainder `<= 0` 时立即返回；extract 按源顺序遍历、忽略非正 drain、传入递减的 request、累加有效 drain，并在恰好满足时 break。`Action`/`AutomationType` 原样透传，大于 `Integer.MAX_VALUE` 的 true-`long` 不丢精度。Fabric 本地 Java 21 测试用可观测的调用/顺序/数值事件覆盖六个 overload family，并证明 energy-supplier extract 重载只对恰好 `0` 在 supplier lookup 之前短路、负值仍会到达 supplier 后被 explicit 重载拒绝。发布物门禁证明三个 production class 各恰好一次，并拒绝任何其它 `LongTransferUtils*.class`；无 startup marker。

调用者前置条件：offer/request 非负、explicit 重载的 `containerCount` 非负且与 fresh 一致 list 匹配、insert remainder 落在 `[0, offer]`、extract 结果落在 `[0, request]`、lookup 可安全接受 nullable side。官方源码不为不匹配的 count、越界 handler 返回或溢出增加防御策略，本切片也不增加。strict energy-handler 三层 API 与 byte-identical dynamic policy closure 已纳入；predicate、supplier 与 listener 保持 exact nullable-side/Action bridge。baseline 不存在旧文档误称的 concrete `EnergyHandler`；Task 023 已在此基础上增加窄 external Energy conversion/lookup/transaction seam，而 `EnergyContainerHelper`、`ConfigEnergyContainerHolder`、side configuration/ejector、Universal Cable、实际 provider/cache、机器/network/persistence owners 及全部 caller 仍属后续 P1 边界。

### 当前 registration/domain metadata foundations 落点

此前的 `BinTier`、`FactoryTier`、`QIODriveTier`、`IResource`、`MiscResource`、`BlockResourceInfo` 六源均与 `4646f509` 字节一致；Task 029 另纳入七个 transport/tank tier enum，精确 adaptation 见下节。当前 15 个 Fabric-local 测试固定完整 enum order 与 capacity/output/pull/speed/process/type/count/conduction/heat/insulation/color/BaseTier/name metadata（含 creative signed maxima 与 QIO `16_000_000_000L`）、loaded cached-reference current/default/live-set 切换及 reflective `finally` identity 恢复、完整资源 suffix/accessor 表，以及 ordinary/raw/luminous/exceptional property construction（包括 refined glowstone light 15 与 refined obsidian resistance 2400、portal/non-burning/blocked push）。JAR gate 精确限制 direct tier 13 项与 resource 三项，各一次。`BlockResourceInfo` 仅证明 property construction，不证明 registered-block behavior。`PrimaryResource`、`ResourceType`、ore types/config、tags/datagen/registration、block/item registries and implementations、factories/bins/QIO/transport/tank owners、`EnumUtils`、`TierConfig` 与 real config ownership/load/reload/lifecycle、serialization/network/persistence/registry lifecycle/world behavior 均延期；不声明 broader Fabric integration。

### 当前 state/permission wrapper 落点

新增 `ProxiedHolder`、`ProxyHandler`、`ProxyHeatHandler`、`ProxyStrictEnergyHandler` 与 generic/float/int/long 四个 `ConfigBasedCached*Supplier`，八份源码均与官方 `4646f509` 字节一致。四个 proxy 测试固定 null side 固有只读（strict-energy set/indexed+aggregate insert/extract 与 heat indexed+aggregate mutation 均被抑制）而 getter 仍精确转发 null；非 null exact side、独立 insert/extract predicate、denied remainder/zero、allowed index/amount/`Action`/result，以及 energy/heat indexed+aggregate getter/mutation forwarding 均有可观测断言。四个 cache 测试固定 lazy first resolution、复用、dependency listener 注册、真实 `clearCache` invalidation 的 eager refresh、generic null sentinel 次次重求值、negative-zero raw float bits 与大 `long`。

该闭包不纳入任何 owners/callers、capability registration/lookup/exposure、external energy/transactions/conversion、side configuration/ejection、machines/transmitters/networks，也不证明 real config load/reload/file I/O/lifecycle/threading 或 persistence/network serialization；不据此声明 broader Fabric integration。

### 当前 primitive config/tier metadata 落点

Task 019 当时的 allowlist 为 API **42** / common **74** / platform **10**。三个 energy/induction tier 源与 `4646f509` byte-identical；`CachedLongValue` 仅替换 NeoForge `Builder` 与 `ConfigValue` 两个 import。Fabric long schema 对合法 signed-long TOML integer literal 保持 `>2^53` 和端点精度，并在 Night Config 前以 decimal/hex/octal/binary lexical preflight 拒绝 modulo-wrap overflow；只规范化指定 primitive wrapper、精确 integral/representable 的 `BigInteger`/`BigDecimal` 与按 exact runtime IEEE 值检查的 finite Float/Double。fraction、NaN/infinity、overflow、range miss 和 `2^63` 均拒绝，绝不 rounding/truncation/saturation 或 lossy `double == long`。

Task 025 在不改变上述 long 行为的前提下加入四个官方 primitive cached wrappers；每份源与 oracle 的唯一差异都是 `ConfigValue` import。spec 仅新增 strict boolean 与 typed byte/short overloads：byte/short 走同一 exact-integral long path，先验证 representability、primitive/configured bounds 后收窄，不允许 truncation/wrap/round/saturation；double 继续使用既有 finite runtime-number 语义。一个同包类恰好四个真实 TOML tests 固定 boolean/byte/short/double 的 default/lazy/supplier/set/cache/invalidation、错误修正与原子 publication，以及 double raw `-0.0` 和 signed-zero unchanged 比较。direct config-value JAR family 精确九项，无 startup marker。

边界仅是 schema/value support 与 tier metadata，不新增 real config activation 或 `tiers.toml`。Task 030 已加入 generic/resolvable/map/Oredictionificator cached source 与唯一 list schema；`TierConfig`、`GeneralConfig`/`MekanismConfig`/其他 real config files、cardboard blacklist、Oredictionificator filters/items、`CachedEnumValue` 与其它 schema、全部 tier owners/callers、config sync/UI/watchers/world restart、machines/blocks/items、capability registration、external energy/fluid/chemical transactions、networking/serialization/persistence/lifecycle/threading 仍延期。

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

Task 023 落地时 allowlist 为 API **43** / common **88** / platform **14**。官方 `IEnergyConversion` 与 `4646f509` byte-identical；四个独立实现的 `mekanism.fabric.energy` source 建立 Team Reborn Energy **4.1.0** 的首个双向、true-long 边界。依赖通过 Fabric Maven 解析并保持 external（不 `include`）。shared helper 仅接受 enabled、finite、strict-positive conversion；exact `1.0D` 是 adapter 的 authoritative identity contract，在内部绕过 unchanged official double round-trip并完整保留所有 positive long；conversion override 若与其 advertised 1:1 rate 冲突则不受支持。非 1 rate 使用固定、bound-independent 候选族：全局 10-bit dyadic mesh（1..1,023 全整数，随后每个 fitting shift 的 512..1,023 mantissa），加 canonical-decimal continued fraction 每个 denominator 的 1..64 bounded multiplier closure 与 fitting binary shifts。bounds 只过滤候选；对 documented stable conversion behavior，返回两域不越界且通过官方 `convertFrom`/`convertTo` 的最大 eligible member，因此 deterministic 并对两域 bound 分别 monotone，无 caller-bound-sized scan。它仍是 conservative finite selection，不声称 globally maximal 或通用 utilization 下限；无 verified family member 时可返回 zero。完整 cold build 是 28,159 dyadic 加至多 92×64×63 decimal occurrences，总计 sort/dedup 前不超过 399,103。四个 synchronized weak-identity slots 以 raw rate bits 发布 primitive-only immutable snapshot；每次实时咨询 enabled，rate bit 变化重建，distinct conversion identity 不共享 verified pair。warm selection 是 `O(log U)`，不再运行 BigDecimal/Euclid/full-family enumeration，并在返回前用 owner identity 重新双向验证；若请求窗口内无旧 pair 且 snapshot 非空，则用首个 cached pair 作单一 behavior sentinel。zero-pair snapshot 无 sentinel，所有 cache 都要求 owner 在 advertised rate bits 不变时保持 conversion behavior 稳定。回归覆盖无 bare convergent 可验证的 `372965497689.465`（243↔90,630,615,938,540 与 600↔223,779,298,613,679），以及 `10.906989658246147`/`575.403` 在 `Long.MAX_VALUE` 两向至少 99% official projection；后者只是 named-rate test evidence。2.5、0.4、0.7 和 reviewer 682↔9,634,898,391,631 pair 亦保留；mutable-rate counting regression 固定 invalidation、identity isolation 与 warm call envelope；不硬编码 conversion 或 FE 等价。

外部→Mekanism adapter 在 lifecycle `NONE` 开 outer transaction；`OPEN` 时才使用 Transfer API 为 compat 明确保留的 `Transaction.getCurrentUnsafe()` 并开 nested transaction。`CLOSING`/`OUTER_CLOSING` callback 中 insertion 原样返回 remainder、extraction 返回 zero，不访问 storage 或非法读取 current transaction。`SIMULATE` 必定 abort，`EXECUTE` 只在 probe 与实际 acceptance 一致且 fixed-point 可报告时 commit；child commit 仍由 parent abort 回滚。Mekanism→外部 adapter 是 `SnapshotParticipant<long[]>`，mutation 前检查固定 count 并 snapshot 全部 captured containers。execute 中 count 改变会 abort/restore 并报告 zero，restore 不重新检查已变 count；之后 insert/extract 与 `stableHandler()` fail-fast，而 supports 查询返回 false、amount/capacity 返回 zero（聚合途中发现 drift 亦同）。正常 amount/capacity 先饱和汇总 raw Joules 再换算一次，exact-1 无损，non-1 保留 official double rounding。

lookup/factory 只接受 physical non-null side，拒绝 unusable conversion、`canHandleEnergy()==false` 与 empty side；storage 记录 exact exposure side，任何 side mismatch 无论 conversion 都返回 null；same-side/identical-conversion unwrap stable handler，same-side/other-usable-conversion 可 wrap。`ProxyStrictEnergyHandler` 与 storage retained suppliers 都实时咨询 exact side + nullable `IHolder`，`supports*` 也随 allow→deny→allow 改变；owner 必须在 support 改变时通知 neighbors，该 wiring 延期。rollback backing 的每个 captured container 必须 deterministic 支持 `setEnergy`；known no-op `FabricStrictEnergyHandler` 被构造器拒绝。restore best-effort 尝试每个 captured setter，并在每次 attempt 后 read back 同一 index；setter/read throw 或 capacity-shrink clamp 等 mismatch 都按首个 primary、后续 suppressed 聚合传播。restore failure 可能留下 mutation，故明确不保证 atomicity。即使完整恢复，execute/restore immediate listener/save/sync side effect 仍可见且不具事务性。

延期：`ConfigHolder`/`ConfigInfo`/`DataType`/`ISideConfiguration`/configurable holders/helpers、`TileComponentConfig`/`TileComponentEjector`、实际 block/item/entity provider registration 与 owner caching/invalidation、cables/networks/emission/machines、hardcoded FE config activation、item reverse adapter/provider owner 以及 fluid/chemical/heat adapters、persistence、packets/client sync 和 thread safety。后续 Task 033 的 item physical-side gateway 见 `FABRIC_ITEM.md`。八个 focused JUnit methods 是 transaction 语义证据；packaged smoke 只验证 exact API/platform class families、Energy dependency 不嵌套、4.1.0 metadata/class、两 mod IDs、remapping/classloading 与 dedicated-server safety，不伪造 startup transfer marker。

### Task 024 container callback contracts

Task 024 落地时 allowlist 为 API **47** / common **88** / platform **14**。四个 byte-identical official callbacks `ContainerInteraction`、`InContainerGetter`、`IntContainerInteraction`、`LongContainerInteraction` 加入既有 long callbacks；一个同包测试类的恰好四个 deterministic methods 证明 object identity、任意/负 index、nullable/physical side、exact `Action`，以及 raw signed `int`/true-`long` boundaries（含 `>2^53`）原样转发。JAR direct `mekanism/api/container/` family 精确只有六个 callback interfaces 各一次，无 nested/synthetic/test/helper/fixture，也无 startup marker。

这只是 callback contracts。`ChemicalUtils`、`ExtendedFluidHandlerUtils`、chemical/fluid handlers、component-backed/recipe-upgrade owners、actual transfer/platform transactions、registries、serialization/networking 与 lifecycle/thread ownership 仍延期；不声明 transfer 或 owner integration。

### Task 025 primitive cached-value sources

Task 025 落地时 allowlist 为 API **47** / common **92** / platform **14**。`CachedBooleanValue`、`CachedByteValue`、`CachedShortValue`、`CachedDoubleValue` 四份官方 common source 各自相对 `4646f509` 精确只有 NeoForge→Fabric `ConfigValue` 的一个 import replacement。`MekanismConfigSpec` 只扩展 strict boolean 与 typed byte/short range API；byte/short 复用 exact integral long normalizer 并在 narrowing 前完成 primitive/configured range 检查，既有 int/long/double、long lexical overflow preflight、同步和 publication ordering 保持不变。

唯一新增的同包测试类恰好四个 methods，使用真实 `BaseMekanismConfig`、Builder/spec 和 temporary TOML，证明四个 primitive wrapper 的 typed normalization、signed/endpoints、lazy cache/supplier、validated set、changed/unchanged invalidation、correction persistence/failure isolation，以及 double raw negative-zero semantics。Task 025 当时的 JAR family 精确九项且无 startup marker；Task 030 已进一步纳入 generic/resolvable/map/Oredictionificator 并扩为 14 项。real config activation、`CachedEnumValue`、未声明 schema、sync/UI/watchers/owners 继续延期。

### Task 026 block-state descriptors

Task 026 落地时 allowlist 为 API **47** / common **96** / platform **14**。四个 unchanged official sources `IStateStorage`、`IFluidLogType`、`FluidLogType`、`TransmitterType` 与 `4646f509` byte-identical，直接编译闭包仅为 java.base、vanilla `Fluid`/`Fluids`/`StringRepresentable` 与既有 annotations。一个同包测试类恰好三个 methods，验证 live exact-EMPTY identity、fluid-log enum order/name/canonical-fluid/emptiness/enum consistency，以及 transmitter seven-value order/size/6-and-8-center/enum consistency；不为 marker-only `IStateStorage` 添加 vacuous reflection test，也无 startup marker；只用既有 narrow `SharedConstants`/`Bootstrap` pattern 满足 vanilla static initialization，不作 custom registry mutation。

JAR exact package gate 只允许 `IStateStorage.class`、`IFluidLogType.class`、`FluidLogType.class`、`TransmitterType.class`、`TransmitterType$Size.class` 各一次，拒绝其它 direct/nested/synthetic/test/helper/fixture。fluidlogging block implementation/state-definition/world update/persistence、transmitters/cables/networks/rendering、actual block registration、codecs/serialization/networking、lifecycle/thread ownership 及全部 callers/implementers 仍延期；marker 无 runtime claim。

### Task 027 inventory metadata contracts

Task 027 落地时 allowlist 为 API/common/platform **47/99/14**。bounded scouts 未找到诚实四源闭包，因此本任务是显式批准的 coherent **3-source exception**。`GuiComponents`、`ContainerSlotType` 与 `4646f509` byte-identical；`SlotOverlay` 仅把 `MekanismUtils.getResource(ResourceType.GUI_SLOT, texture)` 及其 imports 替换为 vanilla `ResourceLocation.fromNamespaceAndPath("mekanism", "gui/slot/" + texture)`，十一项结果仍逐项精确为官方 `mekanism:gui/slot/overlay_*.png`。唯一 Fabric-local 测试类恰好四个 methods，覆盖 exact enum order/ordinal/valueOf、全部 ID 与 18×18 metadata、fresh enum arrays，以及 test-local dropdown/toggle enums 的 exact component/resource identity 和 icon defaults/overrides；无 renderer、texture I/O、bootstrap/global mutation 或 startup marker。

JAR gate 对 `GuiComponents` 同名前缀只允许 outer、`IDropdownEnum`、`IToggleEnum` 三项各一次；direct inventory slot package 只允许 `ContainerSlotType`、`SlotOverlay` 各一次，并拒绝所有 nested/synthetic/test/helper/fixture。screens/widgets/rendering/runtime texture validation、concrete slots/`IInsertableSlot`/inventory/menu/player/window owners、GUI lifecycle、networking/sync、registries/serialization/thread ownership 全部延期；只声明 metadata contracts。

### Task 028 deferred codec/criterion/loot registration

Task 028 落地时 allowlist 为 API/common/platform **47/103/14**。`DeferredMapCodecHolder`、`DeferredMapCodecRegister`、`DeferredCriterionTrigger`、`LootFunctionDeferredRegister` 四源原样纳入并与 `4646f509` byte-identical；唯一且另行审计的 production adaptation 是 `MekanismDeferredHolder.value()` 返回类型从 `R` 收窄为 `T`，方法体继续是 `return get();`。`T extends R` 令它成为合法 `Holder<R>` covariant override 并恢复 subtype contract；两者 JVM erasure 均为 `Object`，descriptor/运行路径不变，当前编译产物没有额外 bridge。

唯一新增测试类恰好四个 methods，使用 package-private registrar seam 验证 function/supplier MapCodec 的 laziness、ID/key、custom holder、duplicate/lifecycle、canonical/subtype identity，最小 criterion 的 exact trigger/instance identity，以及最小 loot function 的 basic/codec laziness、key 和 codec identity。criterion 仅按仓库既有模式执行标准 `SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();`，测试自身不向 custom/built-in registry 注册条目；不作 datapack codec execution、serialization 或 loot context execution，也无 startup marker。JAR direct registration family 精确六项（含 `MekanismDeferredRegister$Registrar`），impl 精确为既有 `GameEventDeferredRegister` 加两个新 class，拒绝其它 nested/test/helper。real production declarations、global registration/freeze、datapack codec execution、serialization、loot/advancement runtime、owners/network/threading 仍延期。

### Task 029 transport/tank tier metadata

Task 029 落地时 allowlist 精确为 API/common/platform **47/110/14**，无重复；official root-test oracle 与 API/platform slices 均不变。新增 `CableTier`、`ChemicalTankTier`、`ConductorTier`、`FluidTankTier`、`PipeTier`、`TransporterTier`、`TubeTier`。相对 official oracle 的 production diff 是封闭的：`CableTier`、`ConductorTier`、`PipeTier`、`TransporterTier`、`TubeTier` 仅移除 `EnumUtils` import，并将各自 `get(BaseTier)` 的 exact `EnumUtils.*_TIERS` iterable 改为本 enum `values()`，保持 order/identity/present mappings 与 `BASIC` fallback；`ChemicalTankTier`、`FluidTankTier`、`PipeTier`、`TubeTier` 仅移除 NeoForge `FluidType` import，并将每个 compile-time `FluidType.BUCKET_VOLUME` 改为 exact integer literal `1_000`，保持现有数值与 `int`/`long` 算术。该 `1_000` 是 Mekanism 原有 mB/config metadata bucket basis，不是 Fabric Transfer API 的 81,000-droplet transaction unit；没有 Fabric constant、adapter 或 storage 被引入。

`TierMetadataTest` 恰有 12 个 methods（新增七个、一 enum 一个），逐项固定完整 order/BaseTier/base/current tuple、五个 lookup 的全部 present mapping 与 absent fallback、chemical serialized names、conductor exact conduction/heat-capacity/insulation/RGBA tuple，以及每个 mutable cached-reference field 的 loaded override 和 subsequent live set。测试用 signed/large/fractional distinct values；private prior reference 即使为 null 也经 reflection 捕获、`finally` 恢复并随后 assert identity。JAR direct tier family 精确为六个既有 entry 加七个新增 entry，共 13 个且各一次，无 nested/test/helper/fixture leakage 或 startup marker。

这不激活 `TierConfig`/`tiers.toml`，也不实现 Fabric fluid/chemical storage/conversion/transactions、cables/pipes/tubes/transporters/conductors/tanks 或任何 owners/callers；registry/network/persistence/lifecycle/threading 全部延期。

### Task 030 generic/list cached values

Task 030 落地时 allowlist 精确为 API/common/platform **47/114/14**，无重复；18-file official root-test oracle、API slice 和 platform slice 不变。`CachedResolvableConfigValue`、`CachedConfigValue`、`CachedMapConfigValue` 各只把 NeoForge nested `ConfigValue` import 换成 `MekanismConfigSpec.ConfigValue`；`CachedOredictionificatorConfigValue` 只换 `ModConfigSpec`/nested value imports 与 builder/value types，所有方法体保持 oracle 行为。因此四源均为有界 type adaptation，不宣称 byte-identical。

Fabric spec 只新增一个 generic `defineListAllowEmpty` surface。default/new-element/validator functions 必须 non-null；default supplier 在 definition 恰好求值一次并在注册前验证，new-element supplier 只保留 source/API shape 且因无 UI/editor 不调用。empty list 有效；non-list、null 或 predicate-invalid element 令整表无效，不静默 drop。default 和 accepted load/set list 均 immutable top-level copy，element 不 deep-copy。既有 define pipeline 继续负责 path/category collision、pending translation/comment/restart、freeze、validated set、load correction、atomic persist-before-publish 和 synchronized document/current update；TOML list 不降成字符串。

一个新 same-package suite 恰好四个 tests，逐一覆盖 generic immutable schema/atomic correction、resolvable unloaded/null/load/set/invalidation、map duplicate last-wins/owner-ignore/global sort，以及 real Oredictionificator 的 `Locale.ROOT` grouping、invalid-ID omission、whole-list correction、empty/duplicate/order 和 save/clear/reload。官方 `set` 继续在 encode 之后缓存 caller `TYPE`，所以 encoded `ConfigValue` list immutable 不等于 resolved map deep immutable：map set 后 alias caller，clear/reload 后才从 globally sorted encoded strings 重建 normalized state。JAR config-value family 精确 14 entries（包括 `CachedMapConfigValue$ValueEncoder`），各一次且拒绝其余 nested/test/helper/fixture，无 startup marker。

`GeneralConfig`/`MekanismConfig` activation、cardboard blacklist、Oredictionificator filter/item owners、`CachedEnumValue`、其余 schema、config UI/watch/sync、ownership/lifecycle/threading 均延期。

### Task 031 sound/damage registration declarations

Task 031 落地时 allowlist 精确为 API/common/platform **47/120/14**；API/platform 与 root oracle 不变。新增六个 common sources：`SoundEventRegistryObject`、`DamageTypeRegister` 两个 leaf 与 `4646f509` byte-identical；`SoundEventDeferredRegister` 精确改接既有 `MekanismDeferredRegister<SoundEvent>` 和 `SoundEventRegistryObject::new` holder factory，保留三个 overload behavior/typed return 并删除旧 `createHolder`；两个 registry declaration class 只把未纳入的 `Mekanism.MODID` owner 依赖替换为 literal `"mekanism"`；`MekanismDamageType` 只把 NeoForge 注入 convenience call 换成 vanilla `registryAccess.lookupOrThrow(key().registryKey()).getOrThrow(key())`。后者保持 NeoForge 21.1.200 extension 对 missing registry/key 的失败语义，不建立 shim。

五个 focused tests 覆盖 fresh sound lazy/canonical/translation/fixed-variable/lifecycle，完整 47 项 public `MekanismSounds` declaration table（一个 register owner、46 holders），全部 damage-register overload/live-unmodifiable/duplicate-last-wins、四项 `MekanismDamageTypes` exact metadata/identity，以及唯一允许的 disposable local `MappedRegistry<DamageType>` mutation。local registry 注册、freeze 一个 matching value后，通过 isolated `RegistryAccess` 验证三个非-Level source overload、holder/value/position/nullable entities/`is` 与 missing key/registry。无 global custom entry、reflection order assumption、Mockito、Unsafe、entrypoint 或 startup marker。

JAR direct `registration/impl` family 从三项扩为精确七项；direct `registries` 精确三项 `MekanismGameEvents`、`MekanismSounds`、`MekanismDamageTypes`，各一次并拒绝 nested/test/helper。real sound registration/freeze/playback/client/resources/subtitles，real damage registration/freeze/datapack bootstrap、production access/level/entity/combat 与全部 owner/caller 延期。

### Task 032 bounded module-config value contracts

Task 032 落地时 allowlist 精确为 API/common/platform **53/120/14** unique sources；common、platform 与 18-file root oracle 不变。新增 API source 中，`ModuleBooleanConfig`、`ModuleColorConfig`、`IHasModeIcon` 与 `4646f509` byte-identical。`ModuleConfig` 只以 literal `"mekanism"` 替代完整 `MekanismAPI` owner；legacy `minecraft` namespace 仅在 decode 时改写，其它 namespace 与 encode name 保持。`IHasTextComponent` 只移除 NeoForge `TranslatableEnum` import/父型及 obsolete `@Override`，nested interface 的 public default method 与 exact forwarding body 保留；NeoForge subtype compatibility 丢失且无 shim。

`ModuleEnumConfig` 只以捕获 declaring constants 的 local vanilla `StreamCodec` 替换 NeoForge codec：`ByteBufCodecs.VAR_INT` 编码 ordinal、解码后直接数组索引，保留 exact varint bytes、negative/high ordinal 的 `ArrayIndexOutOfBoundsException` 与 null encode 的 `NullPointerException`，且不新增 anonymous/package/platform class。官方 selectable list、data/supplied-stream 与 `with` 行为完全保留，包括 equal count unlimited、immutable subset、bounded data validation、supplied bounded stream 不额外验证 incoming value，以及 changed bounded `with(TYPE)`/`with(int)` 回到 full option list。Boolean contracts 和 color signed RGB/ARGB、alpha locking、VAR_INT/fixed INT wire forms 同样未改。

恰好七个 same-package tests 使用 real Mojang codecs 和 Netty/`FriendlyByteBuf` 固定上述语义、equality/hash/class/name/value/count 与 text/icon identity。static JAR gate 精确限定 direct gear-config 五项和 `IHasTextComponent` outer+nested 两项，各一次，无 nested/synthetic/test/helper/fixture 或 startup marker。`ModuleData`、module containers/items、codec catalog、GUI/radial/client rendering、packets/network owner integration、persistence/data components/registration/lifecycle 与全部 callers 均延期。

### Task 033 physical-side item inventory gateway

Task 033 将当时 allowlist 扩为 API/common/platform **56/122/16**。`IMekanismInventory`、`IInventorySlotHolder`、`InventorySlotHolder` 与 `4646f509` byte-identical。`IInventorySlot` 仅把 NeoForge NBT 父型切换到既有 Mekanism registry-aware contract、清理 Javadoc imports，并用 private method-local exact oversized item codec 替换未纳入的 broad `SerializerHelper` call；`ISidedItemHandler` 仅移除 NeoForge nominal parent/imports 与失效 overrides，全部 unsided/sided methods、nullable side 和 `Action.get(!simulate)` 保留。

两个 project-owned Fabric source 提供单向 Mekanism→Fabric、physical-side-only `SlottedStorage<ItemVariant>`。factory 拒绝 null/internal、disabled/empty inventory；storage capture slot identity/order，live 查询 holder policy，提供 owner invalidation，并在 topology drift 后永久 fail closed。每次 execute 先作 exact `SIMULATE` plan，再在 nested transaction 中 snapshot 全部 stacks、执行 same plan，并用 item/components/count/total 与 full state readback 决定 commit。mismatch/outer abort 用 defensive copies restore every slot，setter/readback failure 聚合后 hard fail；execute/restore listener/dirty/sync 立即副作用明确不可回滚。long request 每个 int call 有界并跨 slots 饱和汇总。

恰好两个测试类/八个 methods 固定 oversized NBT/defaults/routing/holder 与 ItemVariant/blank/true-long/stable views/nested rollback/mismatch/all-copy restoration/listener/live policy/invalidation/topology/restore failure/null-zero exposure。JAR gate 精确限制 direct API 3、holder/slot 2、platform item 3 entries，无 startup/provider claim。Task 034 仅将其 Knot source set/task 泛化为 `fabricTransferTest`，item test source bytes 和 production behavior 不变。具体边界见 `FABRIC_ITEM.md`。concrete slots、reverse adapter、actual `ItemStorage.SIDED` registration/cache/invalidation owner、side config/ejector、persistence owner、Input/Output helpers、CachedRecipe、tiles/machines/Crusher 仍延期。

### Task 034 Mekanism-owned fluid value/unit gateway

Task 034 将 allowlist 扩为 API/common/platform **57/122/17**，common slice byte-for-byte 不变，新增 production 精确为 `mekanism.api.fluid.FluidStack` 与 `mekanism.fabric.fluid.FabricFluidConversions`。API value 以 int mB 保留 NeoForge 21.1.200 platform-neutral behavior：identity/Fluid/amount empty、empty-observing getters、mutable independent components、raw overflow/split/copy、holder/tag/predicate、identity Object equality、matching/hash、required/fixed/optional/lenient codec、registry-aware parse/save、VAR_INT-first stream 和不需要 capability 的 deprecated methods。虽失去 NeoForge `MutableDataComponentHolder` nominal parent，direct/Supplier set/remove、两组 direct/Supplier update 与两组 varargs copyFrom callable overload parity 已恢复；update 经 getOrDefault/set，copyFrom 对 source 缺值会清 target。实现仅依行为契约独立编写；LGPL-2.1-only provenance 见 `FABRIC_FLUID.md`。

FluidType/capability-only methods 被显式删除；description 使用 default fluid state 的 legacy block description ID，WATER/LAVA/EMPTY 精确为 `block.minecraft.water`/`block.minecraft.lava`/`block.minecraft.air`，内建 water/lava 与 pinned NeoForge explicit ID parity；custom FluidType override 仍不可用，Fabric convention 为 block-derived。Fabric seam 唯一量纲为 `1,000 mB == FluidConstants.BUCKET == 81,000`、factor 81；反向必须 nonnegative、整除81、quotient `<= Integer.MAX_VALUE`，max exact domain `173,946,175,407`，无 floor/hidden remainder。source water/lava + component patch exact round trip；flowing variant normalization hard reject；blank/zero 映射 canonical empty。

九个 ordinary FluidStack tests 与四个 Knot conversion tests 固定 value、component overload parity/absent clearing、golden JSON/NBT/wire、exact errors/adaptations、unit edge 与 deterministic 100,000-case loop。旧 item Knot suite unchanged relocation 后两套共用单一 `fabricTransferTest` startup；预期 ordinary 64/305、transfer 2/8、aggregate 66/313，三项 jqwik property 各100,000。JAR 只允许 FluidStack/FabricFluidConversions outer class 各一次，无 tests/helpers/NeoForge/loader 或 startup marker。

Task 034 落地时严格延期 `SerializerHelper` 与其余 listed boundaries；Task 035 迁移该 shared owner，Task 036 仅迁移 portable `TextComponentUtil` closure，并以两个不逃逸 stateless predicates 避免完整 `ConstantPredicates`；Task 042A 迁移 14 个 recipe/`vanilla_input` source 的 FluidStack import 并纳入 project `TriPredicate`。其余 import replacements、fluid types/attributes、tanks/handlers/storage/transactions、item concrete slots、warning/menu、recipes、provider/cache owner 和 machines/Crusher 仍延期；Task 034 本身不构成 transaction 或 owner integration。

### Task 035 shared serialization ownership

Task 035 将 allowlist 扩为 API/common/platform **58/122/17**。这是经 concrete-slot scouting 后批准的两个紧耦合 production files 架构例外：新纳入 `SerializerHelper`，并只修改既有 `IInventorySlot` 以删除 Task 033 private oversized codec、恢复 `SerializerHelper.saveOversized(provider, getStack())`。后者的 registry-aware NBT nominal parent、Javadoc adaptations 与全部 public/default inventory methods 不变；common/platform slices 与 dependencies 均不变。

`SerializerHelper` 相对 `4646f509` 只把 NeoForge `FluidStack` import 换成 project-owned `mekanism.api.fluid.FluidStack`，并把两处 `MekanismAPI.logger.error` 改由 private `LoggerFactory.getLogger("mekanism.api.MekanismAPI")` 记录，以避开 unfinished facade 的 eager class initialization。logger category、ERROR level、`Tried to load invalid item: '{}'` / `Tried to load invalid fluid: '{}'` 保持。11 个 public codec fields、6 个 public methods、legacy floating-long、item/fluid optional/lenient/oversized、conditional map 与 stringify semantics/error/defaults 均保留；无 startup entrypoint reference。

一个新 ordinary class 恰好五个 methods，与 unchanged `InventoryApiHolderTest` 四个 methods构成 focused 2/9。Task 035 落地 totals 为 ordinary **65/310**、Knot transfer **2/8**、aggregate **67/318**；Task 036 落地为 **66/315**、**2/8**、**68/323**；Task 037 落地 ordinary/transfer/root/aggregate 为 **67/320**、**2/8**、**1/1**、**70/329**；Task 038 落地 ordinary/transfer/root/data-map/aggregate 为 **68/324**、**2/8**、**1/1**、**1/1**、**72/334**；Task 039 落地为 **70/336**、**3/15**、**1/1**、**1/1**、**75/353**；Task 040 集成态当前为 **73/355**、**4/21**、**1/2**、**2/4**、**80/382**，三项 jqwik properties 各100,000。实编译 JAR family 为 `SerializerHelper` outer + 三个 anonymous classes `$1`/`$2`/`$3`；exact gate 各允许一次并拒绝 same-prefix extras、`MekanismAPI.class`、NeoForge/Loader leakage 与 startup marker/reference。在 Task 035 落地点，ChemicalStack、完整 ConstantPredicates、concrete slots、menu/warning/recipes、registry/config/lifecycle/transaction/transfer/thread owner 和 broad facade 全部延期；Task 039 已纳入的 bounded concrete-slot/window subset 以本页对应小节为准，其余仍延期。

### Task 036 portable text/color SCC

Task 036 将 allowlist 扩为 API/common/platform **64/122/17**，无 common/platform production 或 dependency change。新增六份 API source：`IIncrementalEnum`、`IHashedItem`、`APILang`、`EnumColor`、`ILangEntry`、`TextComponentUtil`；立即恢复既有 `IHasTranslationKey`、`RelativeSide`。后三个 leaf 中 `IHashedItem`/`EnumColor`/`ILangEntry` 以及恢复后的 `RelativeSide` 与 `4646f509` byte-identical。

有界适配只有：incremental enum 两个 `ConstantPredicates.alwaysTrue()` internal arguments 换成 stateless non-escaping lambdas；APILang private constructor 的完整 API owner 换 literal `"mekanism"`；TextComponentUtil 换 project FluidStack，两处 raw Fluid 走 Task 034 block-derived translatable key；vanilla 缺失的两个 NeoForge-injected Level descriptions 展开为 exact dimension language-key + ID fallback component。`IHasTranslationKey` 保持已批准的 `TranslatableEnum` nominal-parent/Javadoc removal，并恢复 TextComponentUtil delegation。所有 public/default signatures、dispatch order、holder/null/style/smartTranslate semantics、57 keys、18 colors、six-side direction/codecs/serialized names 不变。

唯一 ordinary `PortableTextColorTest` 恰有五个 methods，加 unchanged `RelativeSideTest`。Task 036 落地 ordinary **66/315**、transfer **2/8**、aggregate **68/323**；jqwik3×100,000。实编译 gate 固定 `TextComponentUtil` outer + `$1`、RelativeSide outer + `$1`、完整 direct text 与 inventory families，各一次；拒绝 concrete HashedItem、MekanismAPI、full ConstantPredicates、chemical/TriPredicate、unexpected nested/test/helper/fixture、NeoForge/Loader 和 startup marker/reference。custom NeoForge FluidType description override 因 Fabric 无该 facade 而不可保留，raw fluid naming 明确为 legacy-block-derived。无 startup claim。

该 closure 仅是 warning/menu/concrete-slot/recipe 等未来调用方的 portable prerequisite；上述调用方、ChemicalStack 及所有 owners 均延期。详见 `FABRIC_TEXT.md`。

### Task 037 generic intrusive/defaulted custom registry

Task 037 将 API/common/platform 扩为 **64/122/19**，只新增 final
`FabricDefaultedRegistry` 与六态 `FabricCustomRegistryLifecycle`，并修改既有 deferred holder/register
完成 canonical-reference 连接。custom registry 直接继承 vanilla defaulted mapped registry，固定 stable
与 intrusive；factory 在 super use 前验证 key relation并捕获 owner。entry/root/freeze mutation 均
owner-only/synchronized；pure preflight 可恢复，super/Fabric builder/vanilla freeze 开始后的异常 terminal
`FAILED`，没有 retry 或 bypass。default holder 必须是 exact returned reference，optional lookup 刻意不走
default fallback。

Fabric `0.116.17+1.21.1` 的 registry module、Minecraft 1.21.1 mapped vanilla classes及
loader-junit 0.19.5 均按实际 artifacts/hash 独立检查，详情见 `FABRIC_REGISTRY.md`。普通新增 suite 恰好
五 methods且所有 registry 均 never-rooted；另一个专用 `fabricRegistryTest` 仅一类一方法，在 fresh
server Knot fork 中真实调用 `FabricRegistryBuilder.from(this)` 注册 fixed unique synced root，随后经
Fabric server 所用的 `BuiltInRegistries.bootStrap()` automatic root freeze 验证 FROZEN。该全局 mutation
只存在于立即退出的 worker。测试当前 ordinary **67/320**、transfer **2/8**、root **1/1**、aggregate
**70/329**；ordinary classpath 没有 loader-junit，jqwik 仍是3×100,000。

发布物 direct registry family 精确七 entries，不允许 enum-switch synthetic、test/helper、NeoForge/
Loader、Kilt/Connector、bypass string、startup marker 或两 bootstrap reference。现有 GameEvent proof
不改。本任务没有 Chemical、`mekanism:empty`、data maps/radiation、MekanismAPI/facade、reload/client、
具体 registry owner、production startup wiring、manual production freeze 或 concurrent-read promise。

### Task 038 ordinary MekaSuitAbsorption data map

Task 038 将当前 API/common/platform 扩为 **65/122/22**。唯一 API admission 是
`MekaSuitAbsorption`（仅 `MekanismAPI` namespace 改 literal）；新增 identity-only descriptor、ordinary
loader 与实际 server identity/session owner，并在既有 bootstrap 注册 STARTING、END reload、STOPPED
三回调。folder mapping、完整 low→high resource stacks、replace→ordered values/tag expansion→ordered
remove、object-first codec alternatives 与 `[0,1]` failure 均按固定 NeoForge 21.1.200 oracle 独立复现。
逐 resource layer 失败原子跳过并发布其余候选；false vanilla END 不变更；global invariant 的 startup
fail 与 later stale-local 行为分离，不声称 vanilla joint rollback。

资源只纳入固定 map/tag 两项且各一次；clean packaged baseline 查询为 21 项（20×1.0、sonic 0.75）。
owner snapshots immutable、只含 copied keys/values；reference/direct queries 分别走 key 与当前 registry
canonicalization，stop 清除 exact server session。四个 ordinary methods + 一个隔离 Knot method 和专用
packaged reload fixture/gate 提供边界证据。NeoForge source/binary hash、layer truth table 与完整延期项见
`FABRIC_DATA_MAPS.md`；Chemical/sync/conditions/Advanced/holder mixin/facade/network/service owner 未纳入。

### Task 039 client-window → concrete-slot vertical

Task 039 从 physical `ClientModInitializer` 在 networking 前同步建立 sole `Mekanism/client.toml` writer，经 platform-neutral `ClientWindowConfigAccess` 连接完整 `SelectedWindowData`，再连接 Basic/Input/Output、InventoryContainerSlot、player/virtual slots、warning 与 InventorySlotInfo。11 records/28 fields、strict int/boolean、unknown retention、scalar/table correction、persist-before-publish、process-wide normalized claim、changed-only serialized save、contained retry 和 physical shutdown drain 均有 unit/real-client/static artifact 门禁。generated `en_us.json` 以原 source/resource wiring 精确打包。

明确 platform delta：Armor 使用 vanilla player equipment classification，并由 `getNoItemIcon()` 返回官方 atlas/texture；不承诺 arbitrary NeoForge item hook。Task 039 落地时 Chemical 尚延期，现由 Task 040 关闭 internal domain；完整 ClientConfig/QIO、actual GUI/menu networking、ItemStorage provider/cache、side config/ejector 与 machine/Crusher 仍延期。

### Task 040 Chemical registry/data-map/tank/synchronization vertical

Task 040 已集成为一个不可拆的 owner SCC：65-entry synced intrusive/defaulted registry、Chemical
value/stack/tank/handlers、五 data maps与 context-local attributes、selected services、common/world config、
configuration snapshot/ACK、play reload和physical-client cleanup/reconnect。最终 slices **98/164/33**；
新增 compiled sources 61。计划 headline 漏计 loader path，实际分类为 U/A/N/M/R
**18/27/17/6/1**，unique touched production paths **69**；18 个 U blobs 与 `4646f509` byte-identical。

平台决策是：应用 snapshot 永不使用 raw ID；map keys及 coolant references 均编码 resource name。
每个 descriptor 的 declared network codec是 value wire format 的唯一 owner，先输出 max-256-byte
length-delimited opaque chunk，network thread 只作总量有界 decode，client executor再以 registry-aware
buffer 解析/验证/发布。每 map≤65、总 entries≤325、value bytes≤65,536；session/epoch/duplicate/unknown/
trailing/stale均 fail closed。generation guard使旧 play task既不能 revive context，也不能断开新连接。

这关闭 internal Chemical prerequisite，不关闭 external capability/lookup：没有 Chemical provider、Fabric
transaction/storage、configurable holder/ejector、ingredient/recipe、environmental radiation、machine 或
Crusher。完整 class/resource/service hashes、config ownership与真实 runtime evidence见
`FABRIC_CHEMICAL.md`。

### Task 041 recipe ingredient/generic-cache/reload vertical

Task 041 从 clean approval commit `e4d85c3e` 在隔离 worktree 实现：完整 item/fluid/Chemical ingredient
值与 creator service、全部 InputHelper 分支、generic CachedRecipe/input-cache 族与真实 server/client
recipe reload ownership，并按 Task 040 的 gate 要求落地 Chemical exact-context/thread-local 热缓存。
候选 slices **129/184/37**（350 compiled production sources），测试 ordinary/transfer/root/data-map/aggregate
**75/372**、**5/27**、**1/2**、**2/4**、**83/405**。计划 U/A/N/M/R **16/30/9/4/0**、unique touched
production sources **59**、net-new compiled sources **55**；候选实现的最终分类核对（含两处 A 实际
byte-identical 的更正）见 `FABRIC_RECIPES.md`。

平台决策是：`ItemStackIngredient` 直接拥有 vanilla `Ingredient` + count，兼容面 round-trip vanilla
item/tag 与官方 `neoforge:compound`（含 legacy `children` alias），未知 custom type 显式失败；fluid 采用
层中性 `FluidIngredient` 代数（single/tag/compound/components，int mB；official typed compound/components、动态空 tag 与显式 empty 分离、positive wire invariant）；Chemical 六 ingredient 以
`ChemicalRegistry`/本地 logger/vanilla 组合 helper 适配，`ChemicalIngredientTypeRegistry` 拥有六个内建
ID 与 reverse codec identity，由 `MekanismChemicalIngredientTypes` 在 bootstrap 一次注册并 freeze。generic
cache 以 `RecipeCacheSource` 取代 deferred `MekanismRecipeType` 构造依赖，`RecipeCacheContext` 以
ThreadLocal 绑定 exact manager identity、positive epoch、fresh identity token 与 thread-confined
identity-keyed mutable state（initialized flags、indices、complex sets）。client 侧使用 client-only tail
mixin（`require=1`，`mekanism.client.mixins.json` 仅 `client` 列表且 environment-scoped）。真实
server/client runtime gate 见 `scripts/fabric-recipe-runtime-smoke.sh` 与 `FABRIC_RECIPES.md`。

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

### Task 042A output handling and one-input cached recipe execution

Task 042A 从 clean commit `cd263092` 在隔离 worktree 实现 `archive/TASK042_PLAN.md` 中获批的 042A 半部。计划已证明
原请求的完整垂直被阻断：vanilla `Recipe#getType()` 为 abstract 且 `RecipeManager` 在真实 reload 路径上调用它，
而本仓库既无 by-key holder 机制，也无 recipe type registration ownership、serializer、production recipe JSON，
且十个 `getToastSymbol()` holder 所需的 `MekanismItems` 家族未纳入。042A 因此在**不写任何 production registry**
的前提下交付完整源码级主题。

slices **129/184/37 → 163/184/37**（384 compiled production sources，common/platform 不变），测试
ordinary/transfer/root/data-map/aggregate **75/372 → 76/378**、**5/27**、**1/2**、**2/4**、**83/405 →
84/411**。相对 `4646f509` 的分类为 U/A/N **12/21/1**（无 M/R）：12 份 byte-identical；21 份有界适配；
唯一新 source 是 layer-neutral `mekanism/api/recipes/MekanismKeyedHolder`。授权的四个 delta family 恰好为
NeoForge `FluidStack` → project type（14 files）、NeoForge `TriPredicate` → project type（1 file）、
`MekanismAPI.MEKANISM_MODID` → literal `"mekanism"`（同时剔除仅因该常量可达的 15 份 module/gear/radial API）、
以及 `DeferredHolder.create(Registries.RECIPE_TYPE|ITEM, key)` → `MekanismKeyedHolder`。

关键设计决策：`MekanismRecipeTypes.TYPE_*` 持有 26 个 **non-registered、identity-stable** 的 concrete
`RecipeType` 实例。vanilla 按 `getType()` identity 分组，因此 in-memory recipes、`OneInputCachedRecipe`
factories 与 `byType` 查询完整可用，而 `BuiltInRegistries.RECIPE_TYPE` 零写入；十个 toast item key 按 key 对
vanilla `BuiltInRegistries.ITEM` 解析，在 `MekanismItems` 未纳入期间保持 non-null、key-correct 但 unbound，
`value()` fail closed。该限制是显式 non-claim 并被测试固定，而不是被隐藏。静态 JAR gate 精确允许新的
`mekanism/api/recipes/**` outer/nested class 并拒绝 042B families。recipe type/serializer registration、concrete
recipe family、`MekanismItems` 与 client recipe viewer 仍属单独审批的 042B，且必须复用这些同一 `RecipeType`
实例以保持 `byType` identity grouping。

### Task 043 recipe type/serializer registration and concrete recipe family

Task 043 从 clean commit `87fe68e6` 实现 `archive/TASK043_PLAN.md`，关闭 042A 明确留下的边界。原 042B blocker 的四条理由中三条经测量
不在本垂直路径上：`MekanismKeyedHolder.attach` 已存在且 `keyed(...)` 允许 attach，因此 registration 可把 canonical holder 绑回
**同一个** `RecipeType` instance，`byType` identity 分组按构造保持；`MekanismRecipe.isSpecial()` 为 `true`，`ServerRecipeBook.addRecipes`
与 `ClientRecipeBook.categorizeAndGroupRecipes` 都以 `!isSpecial()` 为门，唯一 `getToastSymbol()` caller（`RecipeToast`）永不触达，
故 136-item `MekanismItems` 家族不需要；registration/decoding 只需 vanilla `RecipeType` 与 `RecipeSerializer`，不需要
`MekanismRecipeType` cache owner，因此 `mekanism.client.*` 不进闭包。

slices **163/184/37 → 199/188/38**（425 production sources），测试 ordinary/transfer/root/data-map/recipe-registry/aggregate
**76/378 → 77/384**、**5/27**、**1/2**、**2/4**、**1/5**、**84/411 → 86/422**。相对 `4646f509`，16 份 byte-identical
（`BasicChemicalChemicalToChemicalRecipe`, `BasicChemicalCrystallizerRecipe`, `BasicChemicalDissolutionRecipe`, `BasicChemicalToChemicalRecipe`, `BasicCombinerRecipe`, `BasicItemStackChemicalToItemStackRecipe`, `BasicItemStackToChemicalRecipe`, `BasicItemStackToEnergyRecipe`, `BasicItemStackToFluidOptionalItemRecipe`, `BasicItemStackToItemStackRecipe`, `BasicNucleosynthesizingRecipe`, `BasicSawmillRecipe`, `IBasicChemicalOutput`, `IBasicItemStackOutput`, `SawmillRecipeSerializer`, `WrappedShapedRecipe`），其余 24 份只带授权的 delta family。

计划中关于 registration 顺序的假设被实测推翻：写 built-in registry **不**需要早于 `BuiltInRegistries.bootStrap()`，实测
`BootStrap()` 之后 built-in registries 仍可写，与既有 game event registration 处于同一窗口。另外两处 plan codec 断言也与真实
contract 不符并已修正：empty chemical output 是省略的可选字段；no-output reaction 与 chance-without-output 由 recipe constructor 的
precondition 拒绝而非 codec error result。

Gates：五个 test task 加 `verifyFabricTestContract` 与 `remapJar`；`check build`；三个 `verify*SliceNoNeoForge`；
`scripts/scan-platform-deps.py`；`./fabric-build.sh`；`FABRIC_SMOKE_STATIC_ONLY=1 scripts/fabric-packaged-smoke.sh`。
`./build.sh --clean` 与真实 dedicated-server/client smoke 不在本 worktree 运行。

Non-claims：无 `MekanismItems`/item/block/fluid/Chemical registration；无 machines/tile entities；无 `MekanismRecipeType` cache owner、
`IMekanismRecipeTypeProvider`、`RecipeTypeRegistryObject`、`RecipeTypeDeferredRegister`、`RotaryInputRecipeCache` 或 machine-facing
`InputRecipeCache` index；无 `MekanismRecipeSerializersInternal`，因而无 `mek_data`/`clear_configuration`/`bin_insert`/`bin_extract`
crafting special、无 bin 与 recipe-upgrade recipe；artifact 内无 production recipe JSON；无 JEI/EMI 或 client recipe viewer；无
`getToastSymbol()`-correct items；无 item/fluid/Chemical transaction 或 storage exposure；无 broader Fabric integration。

### Task 044 rotary input-cache ownership closure

Task 044 从 clean commit `8a4c6105` 实现 `archive/TASK044_PLAN.md`，把最后一个未接入的 input-cache 形状移植到既有
`AbstractInputRecipeCache` per-binding 契约，不改 `MekanismRecipeType` cache owner、registration、provider、monitor、factory、viewer 或其它
cached recipe family。

`RotaryInputRecipeCache` 改为接收 `RecipeCacheSource<RotaryRecipe>`，并把两个方向的 index（`ChemicalInputCache`/`FluidInputCache`）与两个
complex fallback set 收进 private `State extends CacheState`，由 `newState()` 创建、`initCache(recipes, state)` 填充；查询统一走基类的
`resolveState(world)`，因此 context resolution、exact Level-manager mismatch 失败、`clear(current binding)` 与 no-context null 全部由基类提供，
类自身的 `clear()` override 删除并改为继承。相对 `4646f509` 的真实 delta 只有 Fabric cache re-hosting 与授权的流体类型替换：
`net.neoforged.neoforge.fluids.FluidStack` → `mekanism.api.fluid.FluidStack`，与 `FluidInputCache` 和已 slice 的 `RotaryRecipe` 一致。
direction flags、type-only insertion、amount-sensitive processing、empty rejection、indexed-first fallback 保持不变：仅支持 fluid→chemical 的
recipe 不会进入、也不会由 chemical 方向返回，反之亦然。

slices **199/188/38 → 199/189/38**（426 production sources）。测试 ordinary/transfer/root/data-map/recipe-registry/aggregate
**77/384 → 78/389**、**5/27**、**1/2**、**2/4**、**1/5**、**86/422 → 87/427**。新增一个 ordinary suite `RotaryInputCacheContextTest`
恰好五个方法，用真实 `BasicRotaryRecipe` 与真实 `RecipeCacheSource.forType(MekanismRecipeTypes.TYPE_ROTARY.value())` 驱动生产 cache：
单向方向排除与双向服务及 rebind 后的方向重建；indexed 查询的 amount 敏感 versus type-only insertion；component 流体 ingredient 只经
complex fallback 命中且不污染另一方向，并验证 indexed-first 与 indexed amount 不足后的 fallback；`clear()` 释放两个方向的 index 与已填充 fluid fallback 而 binding 仍存活；fresh binding 隔离两个方向 index 与 fluid fallback。Chemical fallback 在当前 `ChemicalInputCache.mapInputs()` 恒返回 false 的实现下不可达，不伪造测试。
通用 binding/thread/epoch 矩阵仍由 `GenericInputCacheContextTest` 拥有，不重复。

Gates：五个 test task 加 `verifyFabricTestContract`；三个 `verify*SliceNoNeoForge` 与 `verifyOfficialRootTestSources`；五个
`verify*FixtureIsolation`；`scripts/scan-platform-deps.py`；`./fabric-build.sh`；`FABRIC_SMOKE_STATIC_ONLY=1 scripts/fabric-packaged-smoke.sh`。
static gate 精确允许 `RotaryInputRecipeCache.class` 与 `RotaryInputRecipeCache$State.class` 各一次，并已从 deferred-family 拒绝清单移除该类；
`common/recipe/MekanismRecipeType*`、`IMekanismRecipeTypeProvider`、`RecipeTypeRegistryObject`、`MekanismRecipeSerializersInternal`、
upgrade/bin/ClearConfiguration recipe family、`mekanism/client/**`、`MekanismItems*`、`mekanism/api/MekanismAPI.class` 与全部 production
recipe JSON 仍被拒绝。`./build.sh --clean` 与真实 dedicated-server/client smoke 不在本 worktree 运行，由 parent 独立执行。

Non-claims：无 `MekanismRecipeType` cache owner / `IMekanismRecipeTypeProvider` / `RecipeTypeRegistryObject` / `RecipeTypeDeferredRegister`
或 owner-registration 变更；无 monitor、factory、recipe-viewer、synthetic smelting、tile 或其它 cached recipe family；无 codec 或 canonical
recipe identity 变更；无 machine-facing `InputRecipeCache` index；无新的全局可变 cache state 或第二套 context 机制。
