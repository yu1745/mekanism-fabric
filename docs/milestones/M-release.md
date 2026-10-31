# M-release — 核心开发与后续发布验证

## 现状

按用户“聚焦开发”要求，已补Meka-Tool左右手模型／OBJ继承烘焙和QIO dashboard LED逐层全亮度。平台仅新增两个实际渲染owner（207→209），未新建转换框架。

## 下一步

W-60现有完整smoke将使用四模块发布物；C-1三项拷贝负向探针均按预期FAIL，C-2四JAR配方计数／摘要已记录，C-4旧oracles保持不变，仅追加Additions已纳入的11条必需AW预期。静态检查通过；W-60 复核的测试口径为 `test` 451 项 + `fabricClientModelTest` 17 + `fabricDataMapTest` 4 + `fabricRecipeRegistryTest` 8 + `fabricRegistryTest` 3 + `fabricTransferTest` 45 + `qioTransactionTest` 50 = 578，无套件被静默排除。

Tools与Additions主体已完成；Generators的机器、多方块、GUI、生命周期和冷启动均已真机验证，W-58统一构建、578 tests／API／machine回归已通过，独立四JAR已生成。现在进入P13：W-59拒绝顺序已修，继续执行W-60–62已有发布检查与C-1…C-4，不扩展审查或测试覆盖。

## 未决项

NeoForge 参考环境文件常驻：官方 NeoForge 21.1.200 + 官方 Mekanism 10.7.19.85 + W-65 样本世界落在 `~/.cache/mekanism-neoforge-reference/`（约 200MB，不进仓库），由 `scripts/neoforge-reference.sh start|stop|status|logs|install` 按需启停（端口 25594，用完 stop，不常驻进程），后续 NBT／存档／行为对比直接复用。

W-69 已完成（原 B2 缺口）：发布 JAR 客户端路径已验证——用 Loom/Gradle 缓存离线装配 intermediary 生产客户端 + 干净四 JAR 服务端，客户端 `phase=client_applied values=458` 并加入游戏；该结论已成档；对应裸客户端启动脚本按新规则退役，不再重复执行。

W-60四模块开发联机启动失败已定性为开发环境类加载产物，非移植缺陷：Loom 开发运行把每个 mod 源集放进独立 class-path group，addon 代码因此看到第二份核心注册静态表（`mekanism:security` 未绑定；remapped JAR 另有 intermediary 名）。干净 remapped 四 JAR 服务端（packaged smoke）加载四模块并到达 `Done`，故不改注册语义；开发侧 paired／rejection smoke 保持仅加载核心 mod，四模块装配由 packaged smoke 断言。[证据](evidence/p8-release/w60-dev-classloader.json)

W-60 同步值实测更正：发布 schema 下四模块合计 **458**（core 303 + tools 63 + additions 41 + generators 51），而非先前估计的 459——core 的 `general:alphaWarning` 只在开发 schema 存在（开发 304／发布 303）。packaged smoke 现断言 `configs=10 values=458`，逐 schema 数字记于 `detail=`。[证据](evidence/p8-release/w60-dev-classloader.json)

W-40 rejection 的真实顺序缺陷已修：Fabric 注册表同步先于原 CONFIGURE 版本任务，独立客户端先被 registry remap 拒绝。版本任务现排入 BEFORE_CONFIGURE 默认阶段之前，三种拒绝 smoke 已通过；正常连接验证与 W-60–62 仍待完成。Additions 语音 PCM 采集受当前主机限制；第三方集成与未穷举风险保持 README 已列边界。

## 验收证据

W-83（受阻）：裂变熔毁受控路径**未完成**——`/mek build` 的 `build` 字面量要求命令来源是 `ServerPlayer`，控制台没有实体，`help mek` 里看不到 `build`、`mek build fission <pos>` 直接报错；同一台官方 NeoForge 参考服控制台表现**完全一致**（可见子命令 chunk/debug/retrogen/radiation/testrules 相同），证明这是上游设计而非移植缺失，D4 关于“控制台可跑 `mek build`”的假设需要修正。三条可行路径已记录（玩家连接后 `execute as`、离线写 level attachment、reflect 取 AttachmentType），推荐第一条。[记录](evidence/p8-release/w83-fission-blocked.json)

W-88：文档终稿——根 `README.md` 重写为入口（Fabric 来源与成熟度说明 + 「我想做…→看哪份文档」6 行表 + 三条常用命令 + 证据索引，上游介绍原样保留在 “Upstream Mekanism” 小节）；新增 `docs/README.md` 索引（24 份顶层文档逐份一行说明 + archive 说明 + 三条约定：当前状态以 `milestones/README.md` 为准、每个断言链证据、archive 只读）。两份入口文档的相对链接经脚本校验 **0 broken**。未移动 docs/ 顶层文档——逐份检查都是现行主题笔记，移动会破坏既有引用。[记录](evidence/p8-release/w88-docs-final.json)

W-87（第一遍）：清理第二轮——`rg TASK0[0-9]{2}` 命中 57 处，绝大多数是**出处说明**（解释与上游的差异来源），删除会丢审查依据，故只修**失效路径**：`TASK052_RESUME_2026-10-26.md` 与 `TASK052_ADJUDICATION_EFFECTIVE_SIDE.md` 已归档到 `docs/archive/`，6 处源码注释仍指旧路径，已全部改到归档路径；`./gradlew -p fabric assemble` BUILD SUCCESSFUL。重复/过时文档合并留给 W-88。[记录](evidence/p8-release/w87-cleanup-pass2.json)

W-86：CHANGELOG 与发布说明——新增 [`CHANGELOG.md`](../CHANGELOG.md)：版本命名、四 JAR 能力范围、与 NeoForge 的差异（Fabric 平台/Team Reborn Energy/条件翻译/无第三方集成/NeoForge 品牌配置项）、已知限制（含频率与 QIO 未纳入往返探针）、四条验证命令，以及可直接贴到 Modrinth/CurseForge 的发布说明段落；README 已加指向。数字与声明逐条对应证据（依赖版本取自 `fabric.mod.json`、451 JVM 测试、存档结论对应 W-65/W-77/W-80/W-82）。[记录](evidence/p8-release/w86-changelog.json)

W-85：许可证与归属审计——新增 [`docs/LICENSES.md`](../LICENSES.md)；发现并修复两处缺失：**三个附加 JAR 完全没有 LICENSE**（Jar 任务补 `from('../LICENSE')` 重命名）、**maven-artifact 的 Apache-2.0 与 NOTICE 未随 JAR**（补进 `META-INF/licenses/`）。核心已带 MIT + night-config LGPL-3.0 + NeoForge 两副本（仅归属说明，JAR 内无 `net.neoforged` 类）。重建后四个 JAR 均含许可证条目，sha256 全部更新（记在证据里，并说明 W-71 的旧哈希以当时提交为准）。[记录](evidence/p8-release/w85-licenses.json)

W-82（部分）：传输网络跨区块——跨区块短网络（源 OUTPUT=4 / 汇 INPUT=1 / 中间线缆显式 `connection:[I;3,3,3,3,2,1]`）在一个 `sprint 200t` 内把 800000L 从源搬到汇；`save-all flush` + 停服 + 重启后汇保留 1200000L，再传 200000 升到 1400000L，**网络从存档重建并继续工作**。会话内真正卸载未能证明（`forceload remove` 后 `loadedChunks` 仍 1444）。顺带记下一条关键经验：**`fill` 出来的线缆 `connection` 全 0、网络不成立**，前几轮传输失败都是这个原因。跨维度/区块加载器/辐射未做。[记录](evidence/p8-release/w82-multidim-networks.json)

W-80：**机器库存与侧配置的跨端往返通过**——找到 tile 槽位的真实序列化键（`ContainerType.ITEM` → `SerializationConstants.ITEMS="items"`，元素 `{item:{...},slot:<byte>}`），用 `be set-nbt` 写入 4 个铁矿石 + 控制台 `data merge` 写入 `component_config.config0=[6,6,6,6,6,6]`，停服后由四个 Fabric JAR 加载同一世界，`be get-nbt` 回读**逐字段一致**；此前的 `Items` 键、`storage.insert`、`screen open-block`、`reflect` 全部被丢弃或拒绝，原因已记录。能量**也已通过**（`be set-field --path energy_containers --value '[{"slot":0,"stored":{"#nbt":"long","value":5000}}]'`：mcdebug 只有 `{"#nbt":"long",…}` 能表达 LONG，`be set-nbt` 只写 INT 所以六种形状全被丢弃；`ok:false NOT_APPLIED` 是键名规范化造成的假阴性），NeoForge 写 stored=5000 → Fabric 回读一致。此前的定位记录：`BasicEnergyContainer.deserializeNBT` 只认 `stored` 的 **LONG** 标签，而 mcdebug 的 `--nbt` 参数是 JSON、数字只能表达 INT，六种键形状全被静默跳过；另记录一条工具事实——mcdebug 在 Fabric 侧对 Mekanism 机器 `storage list` 为空（同服箱子正常、machine smoke 的漏斗断言通过），属 mcdebug 发现限制而非移植缺陷。

W-80（第一遍，部分）：**打通了 NeoForge 参考服的 mdb 驱动**（副本环境加 `mcdebug-neoforge-0.8.0.jar` + `kotlinforforge-5.11.0-all.jar`，status 返回 `modLoader=neoforge`/`fluidUnit=mb`/`energyUnit=FE`，安装步骤写进 `neoforge-reference.sh` 的 `install_env`），但**写入机器运行内容仍无可行路径**：`inv.*`（非 vanilla 容器）、`data merge`/`be set-nbt`（键不是槽位序列化键）、`storage.insert`（inserted:0，带面则找不到句柄）、`screen open-block`（-32018 无 MenuProvider）、`reflect`（NeoForge 专用服加载 client 类被拒绝）六条都试过，逐条贴了返回错误。W-77 的库存/能量残留与 W-80 同源。[记录](evidence/p8-release/w80-save-roundtrip.json)

W-84：CI 工作流 `.github/workflows/fabric-port.yml`——在 `java-port` 分支的 push/PR 上跑 `./gradlew -p fabric test verifyApiSliceNoNeoForge assemble`（本地已跑通，BUILD SUCCESSFUL），并上传四个 JAR 作 artifact；**不跑**六个需要 MC 运行时的任务与全部 smoke（在注释里写明原因与本地等价命令）。YAML 已解析校验，但未在真实 GitHub runner 上执行。[记录](evidence/p8-release/w84-ci.json)

W-81（第一遍）：规模与堆基线——两端用**完全相同的 fill 命令**搭约 700 根终极通用线缆的网络，`/tick sprint 120000t` 加速采样：Fabric 四 JAR 侧 **13.1 s（9176 ticks/s，0.11 ms/tick，mspt 0.62，1781 区块）**，官方 NeoForge 侧 **29.2 s（4114 ticks/s，0.24 ms/tick）**；堆用量 Fabric 716MB/1024MB、官方 379MB/872MB，RSS 1526/1437MB。**不把"快 2.2 倍"当性能结论**——两侧世界不同（新超平坦 vs 样本世界）、模块集不同（四 JAR vs 仅核心），且 sprint 吞吐是加速态指标。[记录](evidence/p8-release/w81-scale-baseline.json)

W-64（部分）：真实客户端读数——mdb 会话在 `Mekanism F60 Debug` 世界读到 **Mekanism 键位确实注册**（44 个键位里 10 个 `key.mekanism.*`）与 **GUI 真实打开**（`GuiMetallurgicInfuser`，42 槽位，176x166，截图落盘）。过程中踩到三处工具链问题并解决：mdb 把 `fabric/run` 误推断为 `:fabric:runClient`（改用 `--project fabric`）、fabric 独立构建没有 gradlew（临时 symlink 根 wrapper，用完删除）、dev 客户端缺 kotlin adapter（把 fabric-language-kotlin 放进 `fabric/run/mods`）。第二轮读数：**MekaSuit HUD 在客户端渲染**（穿戴后左上角三枚指示器，数值 0% 因未充能）、**MekaTool 的 OBJ 模型正常渲染**（主手截图可见），并记录一条告警级贴图警告 `meka_tool#inventory` 材质名 `minecraft:3/6` 被当 sprite 解析。仍未读数：裂变熔毁、Generators 音效、按键触发行为。[记录](evidence/p8-release/w64-client-readings.json)

W-63：官方测试 oracle 评估（只出评估页，不扩测试矩阵）——官方 `src/test` 24 个文件、`src/gameTest` 12 个文件；移植测试树 93 文件/451 项里只有 `WildcardMatcherTest` 有对应物且已改写（新增 locale 用例），**当前无 byte-identical 纳入项**。可 byte-identical 纳入的是 4 个纯逻辑用例（`SearchQueryParserTest`、`BiLongMultimapTest`、`IndexedCuboidMapTest`、`ChunkUtilsTest`，目标类都在 common-slice），`Distribution*Test` 三个需小适配；不可纳入的：三个 `*AccessTest`（测有意不加载的 `IMekanismAccess` 服务）、`forgeenergy/EnergyConversionTest`（NeoForge 能量语义）、整包 `src/gameTest`（依赖 NeoForge GameTest 框架）。datagen 与发布资源一致性沿用 W-75 结论（无未解释缺口）。按 AGENTS“不主动增加测试覆盖”，本轮不纳入任何用例。[记录](evidence/p8-release/w63-oracle-evaluation.json)

W-72：核心延期文件复盘——slice 排除的 35 个非集成类文件逐项对照 Fabric 侧实现，**全部有替代或确认不适用，无玩家可见缺口**，本轮不改生产代码。替代关系（示例）：`Mekanism`→`MekanismFabricBootstrap`、`PacketHandler/BasePacketHandler`→`fabric/network`、`ClientRegistration*`→`MekanismFabricClientBootstrap`+`Fabric{ClientModel,EntityRenderer,Screen}Registration`、`MekKeyBinding*`→`MekanismKeyHandler.registerKeybindings()`、NeoForge ModelData 一批→`Fabric{DataBased,DeferredLoader,Composite}*`、`MekanismCurioRenderer`→`FabricAccessoryRenderRegistration`、`CommonPlayerTracker`→`MekanismUsernameLifecycle`、`MekanismAttachmentTypes`→`FabricRadiationAttachments`+`EntityFlame` 持久数据、三个条件类→`mekanism:{condition_exists,mod_version_loaded,tag_empty}`。[明细表](M-deferred.md)、[证据](evidence/p8-release/w72-deferred-review.json)
未覆盖：键位缺真机读数（W-64）、库存/能量注入（W-77 残留）、249 个集成类文件归 W-68。

W-73：用户文档 `docs/INSTALL.md`——依赖（按 `fabric.mod.json` 实测声明）、四个 JAR 的必需/可选关系与 `+fabric.N` 版本含义、安装步骤、与 NeoForge 存档的兼容边界（已比对项与未比对项）、已知限制、构建与门禁命令；README 顶部 Fabric 分支说明已加指向。页内相对链接全部校验存在。[记录](evidence/p8-release/w73-install-doc.json)

W-78：清理 Task052 时期临时物——删除 8 个脚本（3 个 init.gradle、2 个 transmitter 检查、Robit 检查脚本与其 Java、TransmitterObjGeometryCheck），每项都先 grep 确认只被 archive 历史叙述或彼此引用；`task052-robit-check.sh` 依赖已删的 renderer-classpath init，且其命令被 W-38 证据引用，故在证据里加 `retired_by` 注记后删除。`./gradlew -p fabric assemble` 仍 BUILD SUCCESSFUL，`scripts/` 已无 Task052 条目。重复文档与源码 `TASK*` 注释留待单独一轮。[记录](evidence/p8-release/w78-cleanup.json)

W-77：运行内容比对（部分）——官方参考服用新增的 `neoforge-reference.sh cmd`（console.fifo）写入侧配置 `component_config.config0=[6,6,6,6,6,6]`，同一世界由 Fabric 四 JAR 加载后 mcdebug 回读**逐字段一致**；机器库存与能量未能从控制台注入（无玩家、`Items` 键与 Mekanism 槽位序列化不符），记为残留并给出 mdb 玩家注入的后续方案。[记录](evidence/p8-release/w77-runtime-content.json)

W-76：注册 ID 对照（`scripts/compare-registry-ids.py`）——方块 **501/501 完全一致**，物品 1064/1066、标签 444/448，**自有命名空间零缺口**；差异全在外部命名空间：`neoforge:item/{bucket,default}` 是移植自带的兼容父模型、`neoforge:damage_type/*` 是移植的伤害类型分组、`accessories:item/{back,shoes}` 是装备槽标签、`projecte` 属第三方。运行期转储不可得（mcdebug 无注册表 RPC），故用双方发布物里游戏解析 ID 的数据文件；运行期数量仍由 content proof 覆盖。[明细](evidence/p8-release/w76-registry-ids.json)

W-71：版本号过渡到 `10.7.19.85+fabric.1`（上游号不变，`+fabric.N` 为本移植构建序号），四个 JAR 的 name／description／license(MIT)／contact／authors／icon 已统一且图标实际打进 JAR；改号引发一处真实缺陷——附加模块引导用 `-` 截断版本后缀，`85+fabric` 进 `Integer.parseInt` 崩溃，已在 `Version.fromFabricVersion` 统一修复。改号后重跑 `release-check --skip-build` 通过（5 步：静态 15s／packaged 服务端 55s／paired 85s／rejection 112s／machine 99s），W-75 复跑缺失 33→30（语言文件缺口消除）。[元数据与哈希](evidence/p8-release/w71-release-metadata.json)

W-75：官方四个 NeoForge JAR 与 Fabric 四个发布 JAR 的 `data/`+`assets/` 逐文件对照完成（`scripts/compare-resource-inventory.py`，JSON 规范化后比较）：8379 个上游文件中 7165 个一致（5708 个仅格式化），33 个缺失（29 个 CraftTweaker `.zs`、1 个 ProjectE、3 个英文变体语言文件）与 1181 个内容差异（637 个 compat 配方的条件包装翻译（`neoforge:conditions`→`fabric:load_conditions`）、544 个 Additions 配方的 loader ingredient 翻译）全部有成因。已补入 `en_au/en_gb/en_ud` 三个语言文件，待下次构建复验。[明细](evidence/p8-release/w75-resource-inventory.json)

W-70：一键发布检查 `scripts/release-check.sh` 通过——6 步全绿：clean 四 JAR 构建（409s，含 578 项测试）→ 四 JAR 静态门禁（17s）→ packaged 服务端 50 mods/458 值（65s）→ paired 双客户端握手（109s）→ 三种配置拒绝（146s）→ machine 运行时 smoke（100s），总墙钟约 14 分钟。[逐步耗时与标记行](evidence/p8-release/w70-release-check.json)

W-66：1 服 2 客户端浸泡通过（脚本随后按“客户端只能用 mdb 启动”规则退役，结论与读数已成档）。加速模式（原版 `/tick sprint`）跑 36000 tick = **30 游戏分钟仅 118 秒壁钟**（306 ticks/s、3.26 ms/tick），两客户端全程在线、无掉线、真实 ERROR 0；另有两次 30 分钟实时运行（含熔岩热力发电机→线缆→能量立方的实时负载）作对照，mspt 3.39–32.80、tps 恒 20。未覆盖：小时级真实时钟行为、RSS/堆起止采样（脚本退役后无采样路径，已注明原因）、大型传输网络。[记录](evidence/p8-release/w66-soak.json)

W-65（第一轮）：官方 NeoForge 21.1.200 + Mekanism 10.7.19.85 生成的独立样本世界（机器／传输器／成型动态储罐）在 Fabric 四模块服务端直接加载通过，9 个位置的方块实体 NBT 字段与值一致，含非零连接位掩码（32／16）与多方块成型（两阀门共享 inventory_id；该 id 两侧每次加载都重生成，非持久数据）。残留：频率数据与机器运行库存未覆盖（需 GUI 授权路径）。[比对表与生成命令](evidence/p8-release/w65-neoforge-save-probe.json)

W-64：音效与语音采集已收口——7 个声音事件 / `sounds.json` / 7 个 ogg 在发布 JAR 中一致，生产客户端加载四模块时不报 `Missing sound for event: mekanism*`；本机 Java Sound 采集线路可用（48000Hz/16bit/mono，500ms 采到 48000 字节），"本机限制"结论已过时。Meka 套装 HUD/按键与受控裂变熔毁**本环境不可验证**：mcdebug 无命令执行 RPC、`inv.*` 只覆盖方块实体容器，无法给出套装或执行 `/mek build fission`；原因与替代证据已记录。[证据](evidence/p8-release/w64-verification.json)

W-69：发布 JAR 客户端路径已验证。生产（intermediary）客户端从 `<gameDir>/mods` 发现四个发布 JAR（62 mods），对干净四 JAR 服务端完成 `phase=send`／`phase=client_applied values=458` 并 `joined the game`；全程离线，用 Loom 缓存的合并 intermediary 客户端 JAR、Gradle 缓存的库/loader/ASM 与 `-Dfabric.runtimeMappingNamespace=intermediary`。[标记行与装配方式](evidence/p8-release/w69-packaged-client.json)，该脚本已退役（客户端一律 mdb 管理）。

W-60b：addon 元数据契约（缺 JAR／重复 id／版本不符）负向探针三项全部按预期 FAIL，基线拷贝通过；门禁内联检查提取为 `scripts/verify-packaged-addons.py`，探针跑同一文件。[结果](evidence/p8-release/w60b-addon-negative-probes.json)

W-61：`./build.sh --clean` 在 `77118a66` 产出四个 JAR（dist/，含 build-info.json 的 sha256）；随后 `FABRIC_SMOKE_STATIC_ONLY=1 scripts/fabric-packaged-smoke.sh` 在同一提交 PASSED，重建的四个 JAR 与 dist 逐字节同摘要（核心 `1c7bca94…`、Tools `afb0734d…`、Additions `9873a802…`、Generators `01681014…`），C-3 满足。[明细](evidence/p8-release/w61-release.json)

W-60：13 项既有 smoke 与完整测试全部复跑通过（packaged 干净四 JAR：50 mods、`configs=10 values=458`、`Done`；paired 两客户端 304 值；rejection 三种拒绝；machine/recipe/chemical/model/worldgen/retrogen/tile/datamap/port-review/client-window 各自 PASSED；`test` 451 项 0 失败）。期间修正三处 gate 自身缺陷：recipe smoke 仍禁用已批准退休的"产物含 recipe 数据"、client-window fixture 的 services 清单停在 4 条（Task040 后启动即缺 IRadiationManager provider）、四个 smoke 把 vanilla `No data fixer` 当崩溃。[逐项标记行与修因](evidence/p8-release/w60-results.json)

W-59：[顺序修复与三种独立客户端拒绝通过](evidence/p8-release/w59-rejection.json)。保留 fixture 隔离及正常 registry sync，无新增测试；实际 diff 仅原任务阶段／顺序及 PASSED 标记，不改协议或拒绝标准。

本批开发：assemble与3项既有focused checks通过；冷启动实机右手／GUI107 quad，第一／第三人称左手102 quad，变换来自各自JSON；dashboard底层light0、LED full-bright15728880。[实机](evidence/development-render/native-summary.json)、[范围／恢复](evidence/development-render/validation.json)。隔离世界81文件和options恢复，自有JVM／临时debug依赖已清理，无新增测试。

先前安全网复核：`b7fb09ab`对照221项，`85799912`修复独立审查提出的mixin注册下限P2并复查关闭；[结论](evidence/p8-release/packaged-safety-review.json)。此项已结束，不重复审查。

W-40已跑：当时578 tests／API／machine／paired通过，rejection失败后停止；[结果与未跑项](evidence/p8-release/w40-results.json)。此后用户要求转回开发，这些旧结果不覆盖后续修改；Tools收口的当前构建另见evidence/tools/build.json，仍不等同完整发布门禁通过。

W-42准备：历史46项已逐一更新、README已列真实发布限制；[清单](evidence/p8-release/w42-historical46-current.csv)。原两项模型可视差异现已实际实现，最终发布收口尚未完成。
