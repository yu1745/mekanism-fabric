# Fabric config 最小适配

当前 config 垂直切片移植官方 `MekanismStartupConfig`，并通过 Mekanism 自有的窄接口替代
NeoForge `ModConfigSpec` / `ModConfig.Type`。它不是 Forge Config API 兼容层，也不提供事件总线。

## 当前闭包

`fabric/common-slice.txt` 纳入：

- `IMekanismConfig`、`BaseMekanismConfig`、`MekanismConfigType`；
- `IConfigTranslation` 与仅含 startup 元数据的 `StartupConfigTranslations`；
- `MekanismStartupConfig`；
- `CachedValue`、`CachedBooleanValue`、`CachedByteValue`、`CachedShortValue`、`CachedDoubleValue`、`CachedFloatValue`、`CachedIntValue`、`CachedLongValue`，以及 `CachedResolvableConfigValue`、`CachedConfigValue`、`CachedMapConfigValue`、`CachedOredictionificatorConfigValue`；
- `EnergyCubeTier`、`InductionCellTier`、`InductionProviderTier` 三份 long-valued tier metadata；
- Task 029 的 `CableTier`、`ChemicalTankTier`、`ConductorTier`、`FluidTankTier`、`PipeTier`、`TransporterTier`、`TubeTier` 七份 transport/tank tier metadata。

前三个 tier 源与 `4646f509` byte-identical；`CachedLongValue` 相对该基线只把 NeoForge `Builder`/`ConfigValue` 两个 import 替换为 `MekanismConfigSpec.Builder`/`.ConfigValue`。四个 primitive cached-value 官方源各自相对 `4646f509` **精确只有一个** source-byte 差异：将 NeoForge `ConfigValue` import 替换为 `MekanismConfigSpec.ConfigValue`，其余字节不变。Task 030 的 `CachedResolvableConfigValue`、`CachedConfigValue`、`CachedMapConfigValue` 也各自只替换这一 `ConfigValue` import；`CachedOredictionificatorConfigValue` 则只把 NeoForge `ModConfigSpec`、nested `ConfigValue` import/type 换成对应 `MekanismConfigSpec` 类型，所有方法体保持官方行为。不得把这四份源统称为 byte-identical。Task 029 七源的 production adaptation 也严格封闭：五个带 `get(BaseTier)` 的 enum 只移除 `EnumUtils` import 并以自身 `values()` 替代对应数组，保持 order/identity/mapping/`BASIC` fallback；四个引用 NeoForge fluid 常量的 enum 只移除 `FluidType` import，并将全部 compile-time `FluidType.BUCKET_VOLUME` 精确替换为 integer literal `1_000`，保持当前 metadata 值和 `int`/`long` 表达式行为。这个 `1_000` 是 Mekanism 既有 mB/config metadata bucket basis，刻意不是 Fabric Transfer API 的 81,000-droplet transaction unit。当前 allowlist 为 API/common/platform **47/120/14**，各 slice 内唯一。

Fabric 侧 `MekanismConfigSpec` 提供当前声明实际需要的 category、translation/comment、`gameRestart`、strict boolean、typed byte/short、int/long/double 范围、value API，以及唯一一个 strongly typed `defineListAllowEmpty`。该 list surface 只承接当前字符串 schema：default/new-element/validator functions 全部 non-null；default supplier 在定义时只求值、验证一次；empty list 合法，non-list、null element 或任一 predicate miss 会拒绝整表；default、load 和 programmatic set 接受值都作 top-level immutable copy。new-element supplier 仅为官方 source shape 保留，本项目没有 config UI/editor，既不调用它也不生成 UI metadata。所有 path/category collision、pending metadata、builder freeze、set-before-update validation、load correction、persist-before-publish 与 synchronized update 都复用既有私有 `define`/`ConfigValue` pipeline。Night Config TOML list 保持 list；不深复制元素（当前 owner 只使用字符串）。

`FabricMekanismConfigManager` 仍只管理 `config/Mekanism/<name>.toml` 的安全路径、load/unload 和 cache invalidation。TOML 存储使用直接打包的 Night Config 3.8.3 core/toml，不向 common 公共签名泄漏 Night Config 类型。两个嵌套 JAR 保持独立，LGPL-3.0 文本与 attribution 位于 `META-INF/licenses/`。官方 `CachedResolvableConfigValue.set` 未修改：它先 encode/set immutable backing value，再把原始 resolved `TYPE` 放进 cache。因此 encoded `ConfigValue` list 不受 caller 或 Night Config list mutation 影响，但 `CachedMapConfigValue`/oredictionificator 在 set 后、clear/reload 前的 resolved `Map` 仍是 caller-owned alias；clear/reload 后才从排序后的 encoded strings 重建 normalized map。这里不承诺任意 `TYPE` 或 map 的 deep immutability。

当前仍不激活 `GeneralConfig`、会一次构造九份配置的 `MekanismConfig`、cardboard blacklist 或 Oredictionificator filter/item owner；`CachedEnumValue`、其它 define schema、world restart、文件监听、配置同步和客户端 UI 也延期。所有 tier owners/callers、cables/pipes/tubes/transporters/conductors/tanks、machines/blocks/items、capability registration、Fabric fluid/chemical storage/conversion/transactions、external energy、networking/serialization/persistence、config ownership/lifecycle/threading 同样延期。这只是 list schema/cached-value 支持与 tier metadata，并未激活任何新增真实配置或 `tiers.toml`。

## Task 039 client.toml ownership checkpoint

Task 039 前置审计确认 `SelectedWindowData` 需要官方 `<config dir>/Mekanism/client.toml` 中 `window.*` 下的完整 11 个位置记录/28 个 scalar values：全部 x/y 默认为 `Integer.MAX_VALUE`，六个可 pin 记录另有默认 `false`。读取必须返回 fresh position record；更新按 x、y、pinned 顺序比较并修改，只有至少一个适用字段变化时才提交一次异步 save。四个非 pin window 加三组 crafting、side-config、transporter-config、upgrade 的物理路径不得与 `configuration.mekanism.client.last_window_positions.*` 翻译键混淆。

未来实现必须让 `MekanismFabricClientBootstrap` 建立唯一 physical-client-lifetime owner，并保证 common/dedicated-server classloading 不引用客户端类。同一 `client.toml` 不允许两个独立 manager/writer；窗口 schema 必须作为可扩展 fragment 归属一个 owner，保留尚未纳入的 qio/hud/rendering 等未知 sections，使未来完整 ClientConfig 扩展同一 owner 而不丢数据或路径冲突。

本项目不把 NeoForge loader 的全部基础设施细节当作该 fragment 的先决条件。外部文件 watcher、NeoForge 配置事件、五级 `.bak` 轮转、comment-only correction 与 unspecified-key pruning 可以作为明确的 Fabric 平台差异延期；但 owned `window.*` 字段的类型验证、默认值、原子 correction/publication、条件保存、异步写序列化、shutdown/flush 和失败行为必须有可执行证据。该 checkpoint 只是已落盘的规划边界，不表示 client owner、SelectedWindowData 或 concrete slots 已进入当前 slice。

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
- 新建/修正文档及显式 save 使用同目录临时文件和 atomic move（文件系统不支持时降级为 replace）；
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
- `fabric/src/test/java/mekanism/common/config/MekanismStartupConfigTest.java`；
- `fabric/src/test/java/mekanism/common/config/value/PrimitiveCachedValueTest.java` 中恰好四个 `@Test`（每个 primitive wrapper 一个）；
- `fabric/src/test/java/mekanism/common/config/value/GenericCachedConfigValueTest.java` 中恰好四个 `@Test`（逐一对应 generic、resolvable、map、oredictionificator source），使用真实 spec/TOML 固定 list correction/immutability、cache/null sentinel、map order/sort 和 official set alias boundary；
- `fabric/src/test/java/mekanism/common/tier/TierMetadataTest.java` 恰好 12 个 `@Test`，其中 Task 029 一 enum 一个新方法，固定完整 metadata 与 loaded config-reference override/live-set/reflective-finally restoration；
- 三个 source-slice NeoForge 引用门禁，以及 config-value JAR family 精确 14 类（含 `CachedMapConfigValue$ValueEncoder`）、direct tier family 精确 13 类且无 test/helper/fixture 泄漏的静态门禁；
- `./fabric-build.sh test`；
- `scripts/fabric-packaged-smoke.sh`；
- `./build.sh --clean --out <dir>`。
