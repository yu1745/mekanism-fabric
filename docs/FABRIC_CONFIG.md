# Fabric config 最小适配

当前 config 垂直切片包含 official selected startup、Task 039 physical-client-lifetime
`client.toml` window fragment（Task052 review17 在同一 owner 增加 transmitter accessibility 值），以及 Task 040 `common.toml` + world `general.toml` Chemical policy fragment；
Mekanism 自有窄接口替代 NeoForge `ModConfigSpec` / `ModConfig.Type`。它不是 Forge Config API 兼容层，
也不提供事件总线或完整九配置 facade。

## 当前闭包

`fabric/common-slice.txt` 纳入：

- `IMekanismConfig`、`BaseMekanismConfig`、`MekanismConfigType`；
- `IConfigTranslation` 与仅含 startup 元数据的 `StartupConfigTranslations`；
- `MekanismStartupConfig`，以及 Task 040 的 `CommonConfig`、完整 selected `MekanismConfigTranslations` 与 `ChemicalConfigAccess`；
- `CachedValue`、`CachedBooleanValue`、`CachedByteValue`、`CachedShortValue`、`CachedDoubleValue`、`CachedFloatValue`、`CachedIntValue`、`CachedLongValue`、`CachedEnumValue`，以及 `CachedResolvableConfigValue`、`CachedConfigValue`、`CachedMapConfigValue`、`CachedOredictionificatorConfigValue`；
- `EnergyCubeTier`、`InductionCellTier`、`InductionProviderTier` 三份 long-valued tier metadata；
- Task 029 的 `CableTier`、`ChemicalTankTier`、`ConductorTier`、`FluidTankTier`、`PipeTier`、`TransporterTier`、`TubeTier` 七份 transport/tank tier metadata。

前三个 tier 源与 `4646f509` byte-identical；`CachedLongValue` 相对该基线只把 NeoForge `Builder`/`ConfigValue` 两个 import 替换为 `MekanismConfigSpec.Builder`/`.ConfigValue`。四个 primitive cached-value 官方源各自相对 `4646f509` **精确只有一个** source-byte 差异：将 NeoForge `ConfigValue` import 替换为 `MekanismConfigSpec.ConfigValue`，其余字节不变。Task 030 的 `CachedResolvableConfigValue`、`CachedConfigValue`、`CachedMapConfigValue` 也各自只替换这一 `ConfigValue` import；`CachedOredictionificatorConfigValue` 则只把 NeoForge `ModConfigSpec`、nested `ConfigValue` import/type 换成对应 `MekanismConfigSpec` 类型，所有方法体保持官方行为。不得把这四份源统称为 byte-identical。Task 029 七源的 production adaptation 也严格封闭：五个带 `get(BaseTier)` 的 enum 只移除 `EnumUtils` import 并以自身 `values()` 替代对应数组，保持 order/identity/mapping/`BASIC` fallback；四个引用 NeoForge fluid 常量的 enum 只移除 `FluidType` import，并将全部 compile-time `FluidType.BUCKET_VOLUME` 精确替换为 integer literal `1_000`，保持当前 metadata 值和 `int`/`long` 表达式行为。这个 `1_000` 是 Mekanism 既有 mB/config metadata bucket basis，刻意不是 Fabric Transfer API 的 81,000-droplet transaction unit。Task 030 落地时 allowlist 为 API/common/platform **47/120/14**，各 slice 内唯一。

Fabric 侧 `MekanismConfigSpec` 提供当前声明实际需要的 category、translation/comment、`gameRestart`、strict boolean、typed byte/short、int/long/double 范围、value API，以及唯一一个 strongly typed `defineListAllowEmpty`。该 list surface 只承接当前字符串 schema：default/new-element/validator functions 全部 non-null；default supplier 在定义时只求值、验证一次；empty list 合法，non-list、null element 或任一 predicate miss 会拒绝整表；default、load 和 programmatic set 接受值都作 top-level immutable copy。new-element supplier 仅为官方 source shape 保留，本项目没有 config UI/editor，既不调用它也不生成 UI metadata。所有 path/category collision、pending metadata、builder freeze、set-before-update validation、load correction、persist-before-publish 与 synchronized update 都复用既有私有 `define`/`ConfigValue` pipeline。Night Config TOML list 保持 list；不深复制元素（当前 owner 只使用字符串）。

`FabricMekanismConfigManager` 仍只管理 `config/Mekanism/<name>.toml` 的安全路径、load/unload 和 cache invalidation。TOML 存储使用直接打包的 Night Config 3.8.3 core/toml，不向 common 公共签名泄漏 Night Config 类型。两个嵌套 JAR 保持独立，LGPL-3.0 文本与 attribution 位于 `META-INF/licenses/`。官方 `CachedResolvableConfigValue.set` 未修改：它先 encode/set immutable backing value，再把原始 resolved `TYPE` 放进 cache。因此 encoded `ConfigValue` list 不受 caller 或 Night Config list mutation 影响，但 `CachedMapConfigValue`/oredictionificator 在 set 后、clear/reload 前的 resolved `Map` 仍是 caller-owned alias；clear/reload 后才从排序后的 encoded strings 重建 normalized map。这里不承诺任意 `TYPE` 或 map 的 deep immutability。

当前仍不激活完整 `GeneralConfig`、会一次构造九份配置的 `MekanismConfig`、cardboard blacklist 或 Oredictionificator filter/item owner；Task 040 只为三个 world policy 值实现 world-restart metadata。其它 define schema、文件监听、通用配置同步和客户端 UI 仍延期。这些 cached-value/tier metadata 本身不激活对应 tier owners/callers、cables/pipes/tubes/transporters/conductors、machines/blocks/items 或 `tiers.toml`；窄 owner 生命周期仅以下两节为准。

## Task 039 `client.toml` window owner（Task052 扩展同一 spec）

Task052 review17 继续使用 `FabricWindowConfig` 作为唯一 manager-loaded `client.toml` spec，不创建第二 writer；除原 28 个 window scalar 外，增加官方 `accessibility.opaqueTransmitters=false`，strict boolean / `gameRestart`。`FabricClientConfigLifecycle` 同步 load 后将真实值 pin 在本 physical-client lifetime 的 `TransmitterModelConfig` publication，早于 model plugin 初始化；shutdown 在 window save drain 后移除 publication。未初始化/关闭后访问报错，不再有 unbound false fallback。资源 reload 重建 sprite-bearing models，读取同一 process-pinned 值；没有 live toggle/watcher 承诺。未知 accessibility/window/其它 TOML key 与原异步 saver 保留。下文 Task039 的 11/28 是窗口子集的历史验收，不代表扩展后整个 spec 仍只有 28 值；旧 runtime fixture 本批未重跑。

Task 039 已把 API/common/platform slice 扩为 **65/145/24**，并启用 `<config dir>/Mekanism/client.toml` 的唯一窗口 fragment owner。`FabricWindowConfig` 精确拥有 `window.*` 下 11 份记录/28 个 scalar：22 个 strict signed-int x/y 默认 `Integer.MAX_VALUE`，crafting0/1/2、side_config、transporter_config、upgrade 六项另有 strict boolean `pinned=false`。`MekanismConfigSpec.Builder#define(String,int)` 是刻意新增的 strict generic-int surface：TOML integer primitive/`BigInteger` 可在 signed-32-bit 范围内接受，integral float/double/decimal 仍拒绝；boolean string 同样拒绝。

加载会先修复 owned scalar/table collision、验证并持久化，再发布 current values；unowned section、key 及 owned table 内未知 key 保留。正常支持 `ATOMIC_MOVE` 的文件系统上，同目录临时文件加 atomic replace 在失败时保留原磁盘文件；既有 `AtomicMoveNotSupportedException` 分支会降级为普通 `REPLACE_EXISTING`，因此不能承诺该 fallback 在进程/系统崩溃时仍 crash-atomic 地保留旧文件。这里也不声称回滚已经发生的内存更新，startup config 使用的同一 fallback 行为保持不变。`FabricMekanismConfigManager` 现在以 process-wide normalized path claim 阻止跨 manager 的第二 writer；注册失败释放 claim，unregister/unload 同时清 cache/spec 和 claim。

`MekanismFabricClientBootstrap` 在 client networking 前同步建立 `FabricClientConfigLifecycle`。common `ClientWindowConfigAccess` 不 import Fabric/client、不含 default/lazy fallback；适用窗口在 pre-init、post-close 或 wrong owner thread 明确失败，`UNSPECIFIED` 不访问 owner。production lifecycle 只注册 physical `CLIENT_STOPPING`，没有 JOIN/DISCONNECT 回调，因此 owner 的 connection independence 是结构性结论；同一进程中的真实 disconnect/reconnect 尚未 runtime 证明并明确延期。每次 changed `SelectedWindowData` update 保持官方 x→y→pinned 的逐字段修改，再向 owner 的 single-thread executor 提交一个 logical job。官方与 candidate 都可能让此前已排队的异步 save 在这些逐字段修改之间读取状态，短暂将 mixed x/y/pinned tuple 写到磁盘；本次 update 自己随后排入 FIFO 的 save 会收敛为完整 tuple，但该小窗口内的 JVM crash 或终止性写失败可能让 mixed tuple 留在磁盘，所以不承诺 tuple-atomic persistence。每个 logical job 内最多尝试四次；shutdown 先停止 admission，再在不持有 admission monitor 时 drain/join，最后 unload/release/remove。

`src/clientWindowFixture` 保持生产 artifact 外；`scripts/fabric-client-window-smoke.sh` 以两个顺序的新 client JVM 和同一 disposable config dir 验证 11/28 schema、unknown retention、持久化、unchanged update 不重写、physical-client-lifetime owner 可用及 physical shutdown flush。它不伪造 connection callbacks，也不声称实际证明同一进程 disconnect/reconnect。完整 ClientConfig/QIO、真实 same-process reconnect、外部 watcher、NeoForge config events、`.bak` rotation、comment-only correction 与 unspecified-key pruning 仍延期；未来 schema 必须在 drain 后扩展/替换此 owner 并加载保留文档，不得并行创建第二 writer。

## Task 040 common/general Chemical policy owners

Task 040 candidate 将 slices 扩为 **98/164/33**。common bootstrap在 registry 前通过既有
`FabricMekanismConfigManager` 唯一加载 `<config>/Mekanism/common.toml`；official `CommonConfig` 完整
selected 五值为 `energyType=FORGE_ENERGY`、`temperatureUnit=KELVIN`、`enableDecayTimers=true`、
`copyBlockData=true`、`holidays=true`。`MekanismConfigSpec#defineEnum` 只接受 declared enum name/value；
错误 string/type在 publish 前修正。`CachedEnumValue` 除 NeoForge nested config type替换外保持
官方 cache/set semantics。

每个 `MinecraftServer` 在 server thread claim normalized
`<world>/serverconfig/Mekanism/general.toml`。`FabricChemicalConfig` 只拥有并保留式更新：

- `radiation.enabled=true`；
- `energy_conversion.feConversionRate=2.5`，finite 且范围 `0.0001..10000`；
- `energy_conversion.blacklistForge=false`，带 world-restart metadata。

同路径第二 owner、wrong-thread、duplicate server owner均失败；startup异常释放 claim，stop unload并
恢复 `ChemicalConfigAccess.ServerPolicy.DEFAULT` 与 `RadiationValidation` default supplier。unowned TOML
section/key保持。common unit values不通过网络覆盖；连接只把上述三项 server behavior policy封装进
`ChemicalDataContext`，disconnect 清 context后恢复 local/default view。`ChemicalConfigAccess` 不 import
Fabric且 pre-owner使用明确 official defaults，不构造第二 owner。单元测试覆盖 strict enum、五值真实
TOML、unowned world fragment保留、exclusive claims、owner publish/reset；real packaged/chemical gates
覆盖 server path与 client policy behavior。详见 `FABRIC_CHEMICAL.md`。

## Startup 契约

生成文件为 `config/Mekanism/startup.toml`，保留官方 12 个路径、大小写、默认值、范围和
`gameRestart` 元数据，包括历史字段名 `bodyArmorArmor`。`gear` 下三个 category 为
`armored_free_runner`、`armored_jetpack` 和 `mekasuit`。

为避免仅因常量/翻译而拉入巨大的 `GearConfig` 与 `MekanismConfigTranslations` 闭包，当前切片
局部保留三个稳定 category 字符串，并把对应的官方 translation/title/tooltip 元数据提取到
`StartupConfigTranslations`。完整配置表可编译后应消除此临时分片，恢复统一引用。

## 加载与保存语义

- 整份 TOML 先解析、逐项规范化和范围验证，再一次性发布给 config values；
- boolean 只接受 TOML/programmatic `Boolean`，数字和字符串不会转换为 boolean；
- byte/short 复用 long 的 exact-integral normalizer：先拒绝非 number、fraction、NaN/infinity、signed-long overflow 及各自 primitive/configured range miss，全部验证通过后才精确收窄，绝不截断、wrap、舍入或饱和；调用 typed overload 时 default/minimum/maximum 三个参数都必须显式为对应 primitive 类型，混入 int literal 会按 Java overload resolution 选择 int overload；
- long 仅接受范围内的 `Byte`/`Short`/`Integer`/`Long`，以及恰为整数、可无损表示为 signed long 且范围内的 `BigInteger`/`BigDecimal`/finite `Float`/`Double`；Float/Double 按运行时 IEEE 值的精确十进制展开验证，null、非 number、fraction、NaN/infinity、signed-long overflow、配置越界及 rounded `2^63` 全部拒绝，不舍入、截断、饱和，也不以有损 `double == long` 比较；
- 既有 int/long/double normalizer、strict long TOML lexical preflight、同步边界与 parse→correct/persist→atomic publish 顺序保持不变；TOML integer literal 在 Night Config 解析前对 decimal/hex/octal/binary 作 signed-long 无损范围检查，避免 parser 的 modulo wrap；合法范围内 integer literal 在 load/correction/save 中保持 `2^53` 以上及 `Long.MIN_VALUE`/`Long.MAX_VALUE` 的精确 round trip（该保证只针对 TOML integer literal）；
- 缺失、类型错误、非有限或 schema 越界数值恢复官方默认；lexical signed-long overflow 走 malformed-file 保留/默认替换路径，并记录完整 dotted path；
- malformed 文件先复制为同目录 `*.toml.invalid`，再用完整默认文档替换；
- 新建/修正文档及显式 save 使用同目录临时文件和 atomic move；既有 `AtomicMoveNotSupportedException` 分支降级为普通 `REPLACE_EXISTING`，该 fallback 不承诺 crash-atomic 的旧文件保留；
- boolean/byte/short/double 的 `getOrDefault`、lazy cache/supplier、validated set、clear 与 changed-vs-unchanged listener invalidation 保留官方行为；double load/cache/set 保留 raw `-0.0` bits，而官方 `old != new` 使 signed-zero 切换不计为 changed；
- list default/current backing 值始终是 immutable top-level copies；invalid set 在 cache/current/document mutation 前抛出，invalid loaded list 整体恢复 immutable default，先原子持久化 correction 再发布；`CachedResolvableConfigValue` 保留 unloaded `getOrDefault` 不写 cache、null sentinel 重试、loaded lazy reuse 与 clear 后 changed comparison；map decoding 依输入顺序且 duplicate key 由 owner 决定（当前实现 last-wins），encoding 对全部 emitted strings 全局排序；Oredictionificator 用 `Locale.ROOT` lowercase、忽略 encode 时无效 ID，并保留 duplicate/order；
- `BaseMekanismConfig.save()` 保留单线程异步保存和首次失败后最多三次重试。

Fabric entrypoint 在 registry 注册前加载 startup 配置，并输出：

```text
MEKANISM_FABRIC_CONFIG_PROOF file=startup.toml values=12 loaded=true created=true corrected=12
```

干净 packaged smoke 同时要求该 marker、用 Python `tomllib` 对账全部 12 个路径/默认值、四项 registry proof 和服务端 `Done`。

## 对照资料

仅参考生命周期/格式选择，未复制实现：

- Kilt `9e8f0d67411f10234bf0d864bd151a6dbcef14b4`：
  `upstream/kilt/compat/forge-config-api/src/main/kotlin/xyz/bluspring/kilt/compat/neoconfig/KiltForgeConfigApiPortCompat.kt`；
- Connector `8b27f1ad042aae8037bcc522b321c03fcce1a12a`：
  `upstream/connector/src/main/java/org/sinytra/connector/util/ConnectorConfig.java`。

本项目不采用 Kilt 的 Forge Config API/event 转发，也不把 Connector 的 JSON 配置格式用于
Mekanism TOML。

## 验证

- `fabric/src/test/java/mekanism/fabric/config/MekanismConfigSpecTest.java`；
- `fabric/src/test/java/mekanism/fabric/config/FabricWindowConfigTest.java` 恰好六个方法；
- `fabric/src/test/java/mekanism/fabric/config/WindowDomainTest.java` 恰好六个方法；
- `scripts/fabric-client-window-smoke.sh` 两个顺序 real client JVM；
- `scripts/fabric-chemical-runtime-smoke.sh` 的 server-policy snapshot、disconnect/reconnect与 reload gate；
- `fabric/src/test/java/mekanism/common/config/MekanismStartupConfigTest.java` 与 `ChemicalServiceConfigTest.java`；
- `fabric/src/test/java/mekanism/common/config/value/PrimitiveCachedValueTest.java` 中恰好四个 `@Test`（每个 primitive wrapper 一个）；
- `fabric/src/test/java/mekanism/common/config/value/GenericCachedConfigValueTest.java` 中恰好四个 `@Test`（逐一对应 generic、resolvable、map、oredictionificator source），使用真实 spec/TOML 固定 list correction/immutability、cache/null sentinel、map order/sort 和 official set alias boundary；
- `fabric/src/test/java/mekanism/common/tier/TierMetadataTest.java` 恰好 12 个 `@Test`，其中 Task 029 一 enum 一个新方法，固定完整 metadata 与 loaded config-reference override/live-set/reflective-finally restoration；
- 三个 source-slice NeoForge 引用门禁，以及 config-value JAR family 精确 14 类（含 `CachedMapConfigValue$ValueEncoder`）、direct tier family 精确 13 类且无 test/helper/fixture 泄漏的静态门禁；
- `./fabric-build.sh test`；
- `scripts/fabric-packaged-smoke.sh`；
- `./build.sh --clean --out <dir>`（仅 integration parent 的最终 clean-release gate；Task 040 candidate 不执行）。
