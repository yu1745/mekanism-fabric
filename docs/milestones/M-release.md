# M-release — 核心开发与后续发布验证

## 现状

按用户“聚焦开发”要求，已补Meka-Tool左右手模型／OBJ继承烘焙和QIO dashboard LED逐层全亮度。平台仅新增两个实际渲染owner（207→209），未新建转换框架。

## 下一步

W-60现有完整smoke将使用四模块发布物；C-1三项拷贝负向探针均按预期FAIL，C-2四JAR配方计数／摘要已记录，C-4旧oracles保持不变，仅追加Additions已纳入的11条必需AW预期。静态检查通过；W-60 复核的测试口径为 `test` 451 项 + `fabricClientModelTest` 17 + `fabricDataMapTest` 4 + `fabricRecipeRegistryTest` 8 + `fabricRegistryTest` 3 + `fabricTransferTest` 45 + `qioTransactionTest` 50 = 578，无套件被静默排除。

Tools与Additions主体已完成；Generators的机器、多方块、GUI、生命周期和冷启动均已真机验证，W-58统一构建、578 tests／API／machine回归已通过，独立四JAR已生成。现在进入P13：W-59拒绝顺序已修，继续执行W-60–62已有发布检查与C-1…C-4，不扩展审查或测试覆盖。

## 未决项

协调者 B2 要求"用发布 JAR 搭干净客户端目录"取 458 值客户端 ack：当前环境没有 packaged client 启动链（仅 server launcher；客户端需 MC client jar+assets+libraries 的完整安装），而 Loom 开发客户端的 class-path group 会把核心注册静态表复制一份（`w60-dev-classloader.json`），加载发布 addon JAR 必然 `mekanism:security` 未绑定。可选路径与代价：①落一个 packaged client 目录（一次性下载 client jar/assets/libraries，本机网络对 sessionserver 曾超时）后跑真客户端；②改 handshake 运行配置为 `source sourceSets.generators`（核心+单个 addon，dev schema 355 值）作为多模块客户端证明。两条都未执行，待协调者裁决后再投入；在此之前只声明 packaged 服务端 458 值装配与 dev 304 值 send/apply。

W-60四模块开发联机启动失败已定性为开发环境类加载产物，非移植缺陷：Loom 开发运行把每个 mod 源集放进独立 class-path group，addon 代码因此看到第二份核心注册静态表（`mekanism:security` 未绑定；remapped JAR 另有 intermediary 名）。干净 remapped 四 JAR 服务端（packaged smoke）加载四模块并到达 `Done`，故不改注册语义；开发侧 paired／rejection smoke 保持仅加载核心 mod，四模块装配由 packaged smoke 断言。[证据](evidence/p8-release/w60-dev-classloader.json)

W-60 同步值实测更正：发布 schema 下四模块合计 **458**（core 303 + tools 63 + additions 41 + generators 51），而非先前估计的 459——core 的 `general:alphaWarning` 只在开发 schema 存在（开发 304／发布 303）。packaged smoke 现断言 `configs=10 values=458`，逐 schema 数字记于 `detail=`。[证据](evidence/p8-release/w60-dev-classloader.json)

W-40 rejection 的真实顺序缺陷已修：Fabric 注册表同步先于原 CONFIGURE 版本任务，独立客户端先被 registry remap 拒绝。版本任务现排入 BEFORE_CONFIGURE 默认阶段之前，三种拒绝 smoke 已通过；正常连接验证与 W-60–62 仍待完成。Additions 语音 PCM 采集受当前主机限制；第三方集成与未穷举风险保持 README 已列边界。

## 验收证据

W-60b：addon 元数据契约（缺 JAR／重复 id／版本不符）负向探针三项全部按预期 FAIL，基线拷贝通过；门禁内联检查提取为 `scripts/verify-packaged-addons.py`，探针跑同一文件。[结果](evidence/p8-release/w60b-addon-negative-probes.json)

W-61：`./build.sh --clean` 在 `77118a66` 产出四个 JAR（dist/，含 build-info.json 的 sha256）；随后 `FABRIC_SMOKE_STATIC_ONLY=1 scripts/fabric-packaged-smoke.sh` 在同一提交 PASSED，重建的四个 JAR 与 dist 逐字节同摘要（核心 `1c7bca94…`、Tools `afb0734d…`、Additions `9873a802…`、Generators `01681014…`），C-3 满足。[明细](evidence/p8-release/w61-release.json)

W-60：13 项既有 smoke 与完整测试全部复跑通过（packaged 干净四 JAR：50 mods、`configs=10 values=458`、`Done`；paired 两客户端 304 值；rejection 三种拒绝；machine/recipe/chemical/model/worldgen/retrogen/tile/datamap/port-review/client-window 各自 PASSED；`test` 451 项 0 失败）。期间修正三处 gate 自身缺陷：recipe smoke 仍禁用已批准退休的"产物含 recipe 数据"、client-window fixture 的 services 清单停在 4 条（Task040 后启动即缺 IRadiationManager provider）、四个 smoke 把 vanilla `No data fixer` 当崩溃。[逐项标记行与修因](evidence/p8-release/w60-results.json)

W-59：[顺序修复与三种独立客户端拒绝通过](evidence/p8-release/w59-rejection.json)。保留 fixture 隔离及正常 registry sync，无新增测试；实际 diff 仅原任务阶段／顺序及 PASSED 标记，不改协议或拒绝标准。

本批开发：assemble与3项既有focused checks通过；冷启动实机右手／GUI107 quad，第一／第三人称左手102 quad，变换来自各自JSON；dashboard底层light0、LED full-bright15728880。[实机](evidence/development-render/native-summary.json)、[范围／恢复](evidence/development-render/validation.json)。隔离世界81文件和options恢复，自有JVM／临时debug依赖已清理，无新增测试。

先前安全网复核：`b7fb09ab`对照221项，`85799912`修复独立审查提出的mixin注册下限P2并复查关闭；[结论](evidence/p8-release/packaged-safety-review.json)。此项已结束，不重复审查。

W-40已跑：当时578 tests／API／machine／paired通过，rejection失败后停止；[结果与未跑项](evidence/p8-release/w40-results.json)。此后用户要求转回开发，这些旧结果不覆盖后续修改；Tools收口的当前构建另见evidence/tools/build.json，仍不等同完整发布门禁通过。

W-42准备：历史46项已逐一更新、README已列真实发布限制；[清单](evidence/p8-release/w42-historical46-current.csv)。原两项模型可视差异现已实际实现，最终发布收口尚未完成。
