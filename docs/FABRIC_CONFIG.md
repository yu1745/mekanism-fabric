# Fabric config 最小适配

当前 config 垂直切片移植官方 `MekanismStartupConfig`，并通过 Mekanism 自有的窄接口替代
NeoForge `ModConfigSpec` / `ModConfig.Type`。它不是 Forge Config API 兼容层，也不提供事件总线。

## 当前闭包

`fabric/common-slice.txt` 纳入：

- `IMekanismConfig`、`BaseMekanismConfig`、`MekanismConfigType`；
- `IConfigTranslation` 与仅含 startup 元数据的 `StartupConfigTranslations`；
- `MekanismStartupConfig`；
- `CachedValue`、`CachedIntValue`、`CachedFloatValue`、`CachedLongValue`；
- `EnergyCubeTier`、`InductionCellTier`、`InductionProviderTier` 三份 long-valued tier metadata。

三个 tier 源与 `4646f509` byte-identical；`CachedLongValue` 相对该基线只把 NeoForge `Builder`/`ConfigValue` 两个 import 替换为 `MekanismConfigSpec.Builder`/`.ConfigValue`。

Fabric 侧 `MekanismConfigSpec` 提供当前声明实际需要的 category、translation/comment、
`gameRestart`、整数/浮点/有符号 long 范围与 value API；`FabricMekanismConfigManager` 只管理
`config/Mekanism/<name>.toml` 的安全路径、load/unload 和 cache invalidation。TOML 存储使用直接
打包的 Night Config 3.8.3 core/toml，不向 common 公共签名泄漏 Night Config 类型。两个嵌套
JAR 保持独立，LGPL-3.0 文本与 attribution 位于 `META-INF/licenses/`。

当前明确不实现 enum/list/map/codec、world restart、文件监听、配置同步、客户端 UI，也不纳入
`TierConfig`、会一次构造九份配置的 `MekanismConfig` 或其他真实 config 文件。其余 cached value kind、其他 tier family、owners/callers、machines/blocks/items、capability registration、external energy/transactions、networking/serialization/persistence/lifecycle/threading 同样延期。这只是 schema/value 支持与 tier metadata，并未激活 `tiers.toml`。

## Startup 契约

生成文件为 `config/Mekanism/startup.toml`，保留官方 12 个路径、大小写、默认值、范围和
`gameRestart` 元数据，包括历史字段名 `bodyArmorArmor`。`gear` 下三个 category 为
`armored_free_runner`、`armored_jetpack` 和 `mekasuit`。

为避免仅因常量/翻译而拉入巨大的 `GearConfig` 与 `MekanismConfigTranslations` 闭包，当前切片
局部保留三个稳定 category 字符串，并把对应的官方 translation/title/tooltip 元数据提取到
`StartupConfigTranslations`。完整配置表可编译后应消除此临时分片，恢复统一引用。

## 加载与保存语义

- 整份 TOML 先解析、逐项规范化和范围验证，再一次性发布给 config values；
- long 仅接受范围内的 `Byte`/`Short`/`Integer`/`Long`，以及恰为整数、可无损表示为 signed long 且范围内的 `BigInteger`/`BigDecimal`/finite `Float`/`Double`；null、非 number、fraction、NaN/infinity、signed-long overflow、配置越界及 rounded-double `2^63` 全部拒绝，不舍入、截断、饱和，也不以有损 `double == long` 比较；
- Night Config TOML integer 在 load/correction/save 中保持 `2^53` 以上及 `Long.MIN_VALUE`/`Long.MAX_VALUE` 的精确 round trip；
- 缺失、类型错误、非有限或越界数值恢复官方默认，并记录完整 dotted path；
- malformed 文件先复制为同目录 `*.toml.invalid`，再用完整默认文档替换；
- 新建/修正文档及显式 save 使用同目录临时文件和 atomic move（文件系统不支持时降级为 replace）；
- cache 的首次解析、`getOrDefault`、set 与 listener invalidation 行为仍由官方 cached value 类保留；
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
- 三个 source-slice NeoForge 引用门禁；
- `./fabric-build.sh test`；
- `scripts/fabric-packaged-smoke.sh`；
- `./build.sh --clean --out <dir>`。
