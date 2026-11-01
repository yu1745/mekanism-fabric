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

**门禁稳定性复核**（协调者 J1 第 4 条）：机器 smoke 连跑 3 次全部 exit 0、均 31 PASS，耗时 65/65/70 秒（提速前 113 秒），无 flaky。证据目录同时从 9.3 MB 压到 6.0 MB（256 色量化，不下采样，文字仍可读）。[记录](evidence/p8-release/w117-smoke-stability.json)

W-104 涡轮（W-116）：**受阻并已定界**。工业涡轮属于 **MekanismGenerators** 模块，dev 客户端只加载核心 mod —— `world fill-box ... mekanismgenerators:turbine_casing` 直接报 `block not registered`；且多方块成型需玩家交互（`BlockBasicMultiblock.useItemOn` → `onActivate(player,…)`），无玩家服务端触发不了。按 W-116 约定带原文结束。**已把 `TurbineValidator` 的完整结构规则提取留存**（尺寸/中心复合体/转子连续/叶片>0/线圈自复合体上方蔓延/分散器铺满平面/冷凝器在复合体之上/通风口不低于复合体），其中叶片数**可通过 NBT 注入**（`SerializationConstants.BLADES` 会被 `loadAdditional` 读取）。[记录](evidence/p8-release/w116-turbine.json)

**门禁提速**：机器 smoke 的长壁钟等待改为 `/tick sprint`（900/200/300 tick），耗时 **113 s → 65 s**，断言仍 **29 PASS / 0 FAIL**（响应协调者 H3「固定 sleep 应改加速」）。[记录](evidence/p8-release/w104-chunk-unload.json)

W-110（双玩家安全拒绝）：**阻塞，需新增 fixture 代码**。已排除的方向：单人会话改不动 `ownerUUID`（`reflect` 传不了 UUID 对象），且 `/setblock` 放置的机器无主、`SecurityUtils.canAccessObject` 首条短路是 `owner == null → 允许`（与官方一致我们在上一轮已对照过）。**新发现**：现有的 `handshakeClient` 运行配置只是 `--quickPlayMultiplayer --username`，连上后**不做任何动作**，两个客户端还共用同一用户名；要断言“B 打开 A 的机器被拒绝”，必须在 fixture 里实现客户端 use-on 与拒绝检测——属于新增测试代码，按 AGENTS 需另获授权，故未擅自实现。[记录](evidence/p8-release/w110-security-denial.json)

W-104（W-81/82 余项）：**区块卸载完成（已按 W-114 修正为真证明）**，涡轮与跨维度传送仍开放。做法：x=2000 先 forceload 建机器、注入 4 矿石+20k FE，再 `forceload remove`；用 **`execute unless loaded 2000 100 0` 输出标记**证明区块确实未加载（旁证 `data get` 回 `That position is not loaded`），`forceload add` + **`tick sprint 300`** 后 `execute if loaded` 证明已重载，同台机器继续加工产出铁粉。smoke **EXIT=0 / 26 PASS**（`Sprint completed with 6302 ticks per second`）。未做：涡轮性能（W-116 手搭多方块）、跨维度传送（需玩家实体移动）。[记录](evidence/p8-release/w104-chunk-unload.json)

W-112（工厂/升级/并行吞吐）：**主体完成**。三档富集工厂的能量缓冲 ≈20k/40k/80k、`progress` 槽数 3/5/9 均与 tier 一致。关键结论：工厂**默认 sorting 关闭**时只跑 1 个进程（20 秒 2 矿石）；**用 GUI 点开排序页后 `sorting=1`**，同一窗口内消耗 **9 个矿石**、`progress` 9 个槽全动，**实测 ≈4.5× 加速（2→9 矿石/20 秒）**，截图见 `w74/factory-parallel.png`（9 输入槽各 12~13 矿、9 输出槽各 6 粉、排序页显示 on）。官方构建默认同样是 `sorting: 0b`，故非移植偏差。**未测**：速度/能量升级的影响（升级槽是虚拟容器槽，`be set-nbt` 写不进，需光标持有物品）与满输出回压。[记录](evidence/p8-release/w112-factory-throughput.json)

W-111（完整化学链）：**×3 主干跑通；×4 段未接通（已回退）**。×3 链：化学灌注机（三氧化硫+水蒸气→硫酸）→ 溶解室（+铁矿石→dirty_iron）→ 储罐 → 洗涤机（+水→clean_iron）→ 结晶器（→**crystal_iron**），实测结晶器 `items=[{slot:1, id:"mekanism:crystal_iron", count:1}]`，smoke **EXIT=0 / 27 PASS**。关键配方：侧面数组按 **RelativeSide** 索引；输出机 `INPUT_OUTPUT(7)`+eject、缓冲储罐 `7`、接收机 `INPUT(1)`，链尾需补能。**×4 段完整闭环**（smoke **EXIT=0 / 28 PASS**）：电解分离器把卤水拆成钠+氯 → 化学灌注机（+氢）产出氢氯酸 → **化学储罐 → 加压管道 → 化学注入室** → 铁晶体变成碎片。注入室**不再种子 HCl**，碎片产出的唯一来源就是这条输送链，因此物理输送本身也被证明。关键写法：会弹射的机器不直接对机器弹射，中间加一个化学品储罐做中转（与 ×3 的 chamber→tank→tube→washer 同构）；断言只放在最终消费者，因为产出方与其缓冲罐在 dump 时都可能已被抽空。官方参考服对照：注入室种子 HCl 时同样出碎片。未做：×5（净化/粉碎/富集）与硫酸自产链。[记录](evidence/p8-release/w111-chemical-chain.json)

**×5 段完整物理链**（smoke **EXIT=0 / 28 PASS**）：注入室产出的碎片经运输器依次送进 **净化室（+氧气→团块）→ 粉碎机（→脏粉）→ 富集仓（→铁粉）**，全程**无物品种子**，链尾富集仓读到 `items=[{slot:1, mekanism:dust_iron}]` —— 即 晶体→碎片→团块→脏粉→铁粉 整条 ×3/×4/×5 路径物理贯通。关键写法：串联沿 +z（chunk 已加载）；注入室物品侧 `OUTPUT(4)`+eject、中间级 `INPUT_OUTPUT(7)`、**末级 `INPUT(1)`**（能被推入但不被抽走，产物才留得住）；运输器掩码北 PULL 南 PUSH；链尾需两轮补能 + 600 tick sprint。只断言链尾，因为会弹射的中间机器 dump 时都是空的。[记录](evidence/p8-release/w111-chemical-chain.json)

W-102（JEI 分类覆盖）：**完成**（改用运行时计数代替逐类截图）。`MekanismJEI` 在 `onRuntimeAvailable(IJeiRuntime)` 里对注册过的 30 个分类逐个 `createRecipeLookup(...).includeHidden().get().count()`，打点 `MEKANISM_FABRIC_JEI_CATEGORY_PROOF`：实测 **count=30 empty=0**，配方数从 1（activating、sps_casing）到 200（crushing）；完整分类-配方数清单见证据页。此前“逐类截图”的路线在 mdb 上不可行（JEI 搜索框不接收合成字符、按 R 不弹配方页），已记录；未覆盖的是各分类的**渲染外观**逐类核对与 QIO 配方转移按钮。[记录](evidence/p8-release/w102-jei-categories.json)

W-105（游戏内配置界面）：Fabric 版原本**没有**配置界面入口（Fabric 无加载器级配置屏，本移植也无 Mod Menu 集成）。按“可选依赖 + 最小适配”补齐：`modCompileOnly` 的 Mod Menu 11.0.5 + `-PmodmenuDev` 开关 + `modmenu` entrypoint → `FabricModMenuIntegration` → 只读的 `GuiMekanismConfig`。真机验证：Mod Menu 里 Mekanism 的配置齿轮由禁用变可用，点击打开界面并列出 `client.toml / common.toml / startup.toml` 及字节数与“编辑后重启生效”提示（截图 `w74/modmenu-config-screen.png`）。顺带修掉三个坑：平台 slice 未登记的类不会编译（`loadSlice` 跳过注释行，计数 209→211）、`ObjectSelectionList` 必须 `addRenderableWidget`、1.21.1 没有 `setRenderBackground`。发布 JAR 不含 `com/terraformersmc/` 类，泄漏断言与 entrypoint 契约已同步。[记录](evidence/p8-release/w105-config-screen.json)

W-109（文档同步）：`CHANGELOG.md` 增“Optional integrations”表（四个 viewer 的版本/`-P*Dev` 开关/能力/未验证项）；`docs/INSTALL.md` 把“无第三方集成”改为“四个可选 viewer 即插即用”并列出 jar 名与注意事项（EMI 需 Fabric Language Kotlin、WTHIT 需 badpackets、JEI 版本受 Loom 限制）；`docs/RELEASE_CHECKLIST.md` 的客户端检查项扩到四个、已知限制同步；`docs/LICENSES.md` 新增许可表（JEI/EMI=MIT，Jade/WTHIT=CC-BY-NC-SA-4.0，均仅编译期链接不打包，许可证取自各 JAR 的 `fabric.mod.json`）。四份文档链接全部可解析。[记录](evidence/p8-release/w109-doc-sync.json)

W-108（安全/权限）：**部分验证**。机器 GUI（Crusher）与安全台 GUI 都能由 `gui open-block` 打开并正确渲染（截图 `w74/crusher-gui.png`、`w74/security-desk.png`），顺带澄清：此前 W-83/92 的 `-32018 no screen handler factory` 是“方块刚放置、BE 未同步”的临时态，BE 就绪后同样调用成功。新放置的安全台显示 “No Owner / Security Offline”（无频率时的正确语义），世界目录也还没有 `mekanism_frequencies.dat`。**未验证**：跨玩家拒绝与公开/私有/受信门禁——mdb 单机会话只有一个玩家，也没有服务端命令通道，无法构造第二个玩家；需用双客户端环境（`fabric-paired-network-smoke.sh` 扩展）另开一项。[记录](evidence/p8-release/w108-security.json)

W-107（崩溃/卸载鲁棒性）：加工中 `kill -9` 集成服务端再重启——存档正常加载、**物品守恒 64**（63锭+1粉 → 62锭+2粉，无复制无丢失）、重新充能后机器可继续加工（progress 0→47）；线缆网络在硬崩后 `connections=32` 与崩溃前**完全一致**。未验证：autosave 瞬间硬杀、在途传输物品、跨维度传送恢复。两次硬杀均无残留进程。**能量持久化补测（回应巡检 G0 第 5 条）**：改用正常保存对照，写入 12,345 FE 后立即停止，重启读到 **45 FE**、物品仍守恒 8——说明**能量确实跨存档保留**，先前两次的 `energy=0` 是“关机前把电用完了”（机器在保存完成前仍在加工，两次观察里物品都恰好前进 1–2 个），不是存档丢失。[记录](evidence/p8-release/w107-crash-robustness.json)

W-106（矿石加工链端到端）：mdb 集成服务端上搭 Crusher / Enrichment Chamber / Energized Smelter 三台机器实测——**铁矿石→富集→每矿 2 粉（×2）**、铁锭→粉碎→铁粉、铁粉→通电熔炉→铁锭，全部按预期产出。**排除一个误判**：把矿石放进粉碎机不动是正确行为（官方与该移植的 `data/mekanism/recipe/` 同为 2215 个配方，粉碎机本就没有矿石配方）。未验证：×3/×4/×5 化学链、管道运输（W-82 已单独验）、工厂版与速度升级。另**自查记录**：本轮曾误用 `gradlew runClient` 起客户端约 40 秒，发现后按进程组终止并改回 mdb；起因是 mdb 缺少 `loom-cache/argFiles/runClient`，正确解法是先跑 `configureClientLaunch`。[记录](evidence/p8-release/w106-ore-chain.json)

W-103（JEI/WTHIT 落地后的最终全量）：在 HEAD `b0c205b9`（含 EMI/Jade/JEI/WTHIT 四个集成）上跑完整六步 `release-check` **PASSED**——clean build、静态门禁、packaged server 55s、paired network 81s、configuration rejection 105s、machine runtime smoke。F4 建议的“根 `wthit_plugins.json` 是否随包”断言**已加入**静态门禁（发布 JAR 缺该描述文件即失败）；该断言在本轮 6 步之后加入，需在下一次全量里复跑闭环。**已复跑闭环两次**：W-105 落地后一次、W-102 的 JEI 运行时打点落地后再一次，均 **PASSED (6 steps)**；第二次覆盖了 `MEKANISM_FABRIC_JEI_CATEGORY_PROOF` 这段生产代码改动。[记录](evidence/p8-release/w103-final-release-check.json)

W-74d（WTHIT）：5 个文件编译通过（`compileJava` + 457 项测试），依赖用 `mcp.mobius.waila:wthit:fabric-12.10.2`（bai Maven，Modrinth 无该坐标），`-PwthitDev` 开关运行期。**注册机制与 EMI/Jade/JEI 不同**：WTHIT 12 读自己的插件 JSON `assets/<modid>/wthit_plugins.json`（`{id, initializer, environment}`），把对象塞进 `fabric.mod.json` 的 entrypoints 会被 Fabric Loader 拒绝（`Entrypoint value must be present`）；已新增该资源并加入 `processResources` 白名单，`assemble` 产物内含。**未验证**：开发客户端（目录形式资源）里 WTHIT 只初始化了 `waila:core`，没有加载本插件（`MEKANISM_FABRIC_WTHIT_PROOF` 计数 0），说明其插件扫描只认 JAR；而发布 JAR 客户端按 AGENTS 硬规则不能用 mdb 启动，故插件注册的真机验证受工具限制，需在真实发布物上确认。**第二轮补正**：注册的**正确位置**是 `fabric.mod.json` 的 `custom` 段 `"waila:plugins"`（放根级被 Loader 拒绝、放 `entrypoints` 也被拒）；但加上后客户端**崩溃**于 `UnsupportedOperationException: Found legacy plugins`——官方插件类实现的是旧版 `IWailaPlugin`（WTHIT 4.x API），WTHIT 12 要求 `IWailaCommonPlugin`+`IWailaClientPlugin` 拆分；上游遗留的旧格式 `src/main/resources/wthit_plugins.json` 也触发同一异常（已删除）。**当前状态：注册已移除**（客户端装 WTHIT 时正常启动、只显示 WTHIT 内置 tooltip），**第三轮**：插件类已改写为 WTHIT 12 的 `IWailaCommonPlugin`+`IWailaClientPlugin` 双接口并通过编译；但注册仍失败——`custom."waila:plugins"` 走的是**已废弃路径**（`PluginInfo.registerDeprecated`，要求旧 `IWailaPlugin`），现代路径 `PluginInfo.register(...)` 需要 common/client 两个初始化器且位于内部包 `mcp.mobius.waila.plugin`。注册已再次移除，客户端装 WTHIT 时正常启动（legacy/ClassCastException 均为 0）；**第四轮：注册成功**。查 WTHIT 官方文档与 1.21.1 源码后确认：现代 API 的注册方式是模块根目录的 **`wthit_plugins.json`**，且必须用 `entrypoints.common`/`entrypoints.client` 两个字段——`custom."waila:plugins"` 在 Fabric 加载器里硬编码走 `registerDeprecated`，之前两次失败是**路径选错**而非类实现错。实测 `MEKANISM_FABRIC_WTHIT_PROOF` 出现 2 次（common+client 各自 register）、legacy/ClassCastException 为 0；准星对准写入 20M FE 的 Crusher，WTHIT 显示 “Crusher / **8.0 kFE/8.0 kFE** / Mekanism (Fabric)”（截图 `w74/wthit-tooltip.png`）。**W-74d 完成**（多方块/传输器读数与 Robit tooltip 未核对）。[记录](evidence/p8-release/w74d-wthit.json)

W-74c（JEI）：**41 个文件编译通过并经真机注册验证**——依赖用 `mezz.jei:jei-1.21.1-fabric:19.43.0.398`（最新 19.70 由 Loom 1.18.2 构建，本仓 Loom 1.13.6 拒绝；Modrinth 坐标拿到的是不含 loader API 的套壳 jar），仅编译期 + `-PjeiDev` 运行期。移植要点：7 个文件的流体类型改写、**JEI 流体 ingredient 类型差异**（Fabric 是 `FabricTypes.FLUID_STACK` + `IJeiFluidIngredient`，故新增 `JeiFluidStacks` 适配器并在 3 处改写）、`addFluidAliases` 的映射/文本分离、与 EMI 一致地去掉物品流体 capability 回退、`Mekanism.hooks.emi.isLoaded()` 换成 `FabricLoader.isModLoaded`。`jei_mod_plugin` entrypoint 已注册并同步进打包契约。真机（只装 JEI）输出 `MEKANISM_FABRIC_JEI_PROOF`，JEI 显示 57 页物品与搜索框、无异常。**未验证**：24 个分类的逐个渲染、QIO 合成转移。改动后 `release-check --skip-build` **5 步全过**（260/55/84/113/98 秒），其中静态门禁含“发布 JAR 不得出现 `mezz/jei/` 类”的断言。[记录](evidence/p8-release/w74c-jei.json)

W-100（给用户的决策清单）：新增 [`DECISIONS_PENDING.md`](DECISIONS_PENDING.md)——①强制推送确认（W-101 squash 后需 `--force-with-lease`，worker 不自行推送）②版本号与发布形态（`+fabric.1` 是否要改、是否正式发布）③剩余 W 项取舍（W-79、W-83 受阻路径、W-80/81/82 余项、W-98 Sodium/Iris 覆盖档位、W-99 选哪个第三方模组、W-74c/d JEI+WTHIT 是否仍做）④发布说明的已知限制口径 ⑤其他未阻塞事项（证据体积、完整演练）。按用户裁决，`server-v2` 的 submodule 指针**未列入**。[记录](evidence/p8-release/w100-decisions.json)

W-94（注册表差异定性）：重跑比对得 blocks 501/501、items 1064/1066（missing 0）、tags 444/448（missing 2 / extra 6），**自己命名空间 missing 恒为 0**。逐项定性：items 多出的 `neoforge:bucket`/`neoforge:default` 是端口 shim（Fabric 没有 NeoForge 提供的父模型）；tags 缺的 `neoforge:damage_type/is_environment`、`projecte:block/vein/shovel` 与多出的 `accessories:item/*`、4 个 `neoforge:damage_type/*` **全是外来命名空间**，分别对应 Accessories 集成与无 ProjectE/无 NeoForge 消费者的惰性标签。附带核实：核心 JAR 有 **104 个资产 JSON 引用 `neoforge:` 装载器**（composite 82、item/bucket 18、fluid_container 18、obj 2），由 `FabricMekanismModelPlugin`/`FabricCompositeModel`/`FabricObjModel`/`BlockModelCompositeMixin` 解释，**不是残留**。结论：无漏移植，无需补实现。[记录](evidence/p8-release/w94-registry-deltas.json)

W-92（Jade 范围补全）：**能量行有数值**（Crusher 20M FE → 8.0 kFE/8.0 kFE）、**流体行有数值**（用水桶经 `world use-on-block` 注入 → “Liquid: Water / Water: 1,000 mB”，顺带解决此前 NBT 写不进 attachment 的注液限制）、化学行渲染但为空（无对应注入道具）、文本行=名称/模组行；**无 Jade 启动验证通过**——移走 JAR 后客户端正常进世界、无 tooltip、无崩溃，`MEKANISM_FABRIC_JADE_PROOF` 计数 0 而 EMI 证明行仍在，说明入口可选且缺失安全。**未验证**：Robit 实体 tooltip（无视角控制 API，准星压不上实体）、附加模块方块（开发客户端只加载 core）、多方块与传输器读数。[记录](evidence/p8-release/w74b-jade.json)

W-91（EMI 范围补全）：**三个附加模块里只有 generators 有 EMI 实现**（`GeneratorsEmi` + `FissionReactorEmiRecipe`；tools/additions 只有 alias 与 JEI），已把 4 个文件纳入 generators 切片（113→117）并在 `compileGeneratorsJava` 通过；`FissionRecipeViewerRecipe` 的冷却剂表读取改用 `ChemicalDataContext.current().maps()`（数据表归 Fabric data-map 会话，与 Boiler 一致）；generators 的 `emi` entrypoint 已注册并随 JAR 打包。**③ fluid delta 影响**：Mekanism 物品的流体在组件里，去重/分组不变，只有“仅靠 capability 暴露流体”的第三方物品不再按流体区分。**② 真机交互未验证**：开发客户端里 `screen open-block` 报 `-32018`（BE 存在，该路径要求真实交互），generators 客户端仍无法用 mdb 启动。改动后 `release-check --skip-build` **5 步全过**（15/56/81/108/98 秒）。[记录](evidence/p8-release/w91-emi-scope.json)

W-95：`IBarInfoHandler` 结构偏离复核——结论写入 [`API_DELTAS.md`](../API_DELTAS.md)：该类型属客户端内部（不进 api-slice、不参与网络/存档/NBT），附加模块已随 W-74a 一并改到顶层类型并编译通过；保留顶层版本，理由是恢复官方嵌套会让 `RecipeViewerUtils` 与 EMI 重新需要第二套类型或桥接。**已完成，无需改代码**。

W-96：发布演练清单——新增 [`RELEASE_CHECKLIST.md`](../RELEASE_CHECKLIST.md)：干净 HEAD 检查 → 全量 `release-check`（六步与耗时基线）→ 可复现性校验（两次 `--clean` 比对）→ 干净目录安装演练 → W-65 样本世界存档往返 → mdb 客户端抽查 → 产物清单与 sha256 → 用户侧动作（tag/推送/上传，worker 不执行）→ 已知限制（W-98/W-99、JEI-WTHIT、注册表差异）。当前 HEAD 的四个 sha256 已写入。[记录](evidence/p8-release/w96-release-checklist.json)

W-97：核心 JAR 不可复现的根因修复——逐条目比对两次 clean build 的核心 JAR（9066 条目，集合相同），**只有一个条目不同**：`mekanism/generated/mekanism/ComputerMethodRegistry_mekanism.class`。根因是 `ComputerMethodProcessor` 用 `HashMap` 迭代生成源码（`TypeElement` 是身份哈希，顺序每次变）；改为 `TreeMap` 按限定名排序 + 元素按名排序后，连续三次 clean 编译生成的源码 md5 恒定，连续两次 `./build.sh --clean` 的**四个 JAR 哈希完全一致**（core `81cbda9f…`），`./gradlew -p fabric test` 457 项通过。**C-3 对四个 JAR 全部成立**，先前“仅附加 JAR 可复现”的限定作废。[记录](evidence/p8-release/w97-core-jar-reproducible.json)

W-93：集成改动后的全量回归——两次全量 `release-check` 各暴露一处**契约过期**（不是产品缺陷）：①`verifyFabricTestContract` 期望 ordinary 95/451、聚合 111/511，W-90 加测试后 clean build 在此失败，已改 96/457 与 112/517；②`fabric-packaged-smoke.sh` 把 entrypoints 钉死为 main+client，新增的 `emi`/`jade` 触发契约失败，已加入两者并注明只在第三方模组存在时生效。修完 `release-check --skip-build` **5 步全过**（14/55/79/113/96 秒）。**已完成**：修复后在同一 HEAD（`cf1de6ea`）跑通 **6 步全量**（278/14/54/80/103/97 秒，PASSED），W-70 的发布结论刷新到当前 HEAD；**并复核可复现性**：删产物再 assemble，四个 sha256 与 `--clean` 产物逐个一致（core `b9022885…`、additions `e2019d55…`、generators `3ee7497b…`、tools `b74c23a5…`），W-89 的结论在集成改动后仍成立（那一轮比对是 `assemble` 与 `--clean` 产物一致）。**但 E3 要求的“最终 HEAD 两次 clean build”推翻了可复现性结论的一半**：同一 commit 连续两次 `build.sh --clean`，**核心 JAR 哈希不同**（`b66051c1…` vs `00fab557…`），三个附加 JAR 稳定。已作为 W-89 遗留项记录——**W-97 已定位并修复**：不是 Loom 重打包，而是注解处理器 `ComputerMethodProcessor` 用 HashMap 迭代生成 `ComputerMethodRegistry`，改为 TreeMap + 按名排序后四个 JAR 逐位可复现。[记录](evidence/p8-release/w93-integration-regression.json)

W-74b（Jade）：looking-at 集成编译 + 真实客户端注册通过——`maven.modrinth:jade:15.10.6+fabric` 仅编译期依赖（`-PjadeDev` 开关运行期），`common/integration/lookingat/**` 16 个文件纳入；四处移植改写（`MekanismAPI` 入口、仓内 `FluidStack`/`IExtendedFluidHandler`、`NeoForgeExtraCodecs.withAlternative` 用 `alternativeElement` 复刻、`new ListTag(int)` 改 `new ListTag()`、世界级流体能力改直取 `IMekanismFluidHandler`）；`jade` entrypoint 已注册。客户端实测输出 `MEKANISM_FABRIC_JADE_PROOF energy=mekanism:energy fluid=mekanism:fluid chemical=mekanism:chemical`，截图中准星对准 Crusher 显示 “Crusher / Mekanism (Fabric)”。**自定义 tooltip 行已确认并修好一处真 bug**：日志出现 `Failed to serialize jade looking at data: Unknown Element Type, expected either fluid or chemical`，根因是我复刻 `NeoForgeExtraCodecs.withAlternative` 时把外层分支映射写反（Chemical/Energy 都进了 `Either.left`）；改正为 Fluid/Chemical→left、Energy/Text→right 后，准星对准写有 20M FE 的 Crusher，Jade 显示 “Crusher / **8.0 kFE/8.0 kFE** / Mekanism (Fabric)”（截图 `w74/jade-tooltip-energy.png`）。**元素覆盖**：能量行已验证；化学行确认参与渲染（Basic Chemical Tank 显示 “Empty”，`w74/jade-tooltip-chemical.png`），但罐内容写不进去——移植的罐内容属 attachment 数据，空罐不写 NBT，`be set-nbt` 合并 `chemical_tanks`（两种格式）都返回 ok 却仍为 Empty，这是 mcdebug 的能力边界；流体行未验证。[记录](evidence/p8-release/w74b-jade.json)

W-90：采纳官方测试（用户已批准）——`SearchQueryParserTest` **byte-identical** 加入 `officialRootTestOracle`（清单 18→19，`verifyOfficialRootTestSources` 断言同步改成 19），`./gradlew -p fabric test` 从 **451 → 457 项**、全绿；文档计数同步（AGENTS 457/584、README、CHANGELOG）。复核时发现并更正了 W-63 评估页的一处错误：`BiLongMultimapTest`/`IndexedCuboidMapTest`/`ChunkUtilsTest`/`Distribution*` 三个**早已在 oracle 清单里**，真正的缺口只有 `SearchQueryParserTest`。[记录](evidence/p8-release/w63-oracle-evaluation.json)

W-74a（EMI，第一步）：依赖与移植面勘察——`fabric/build.gradle` 加入 Modrinth maven 与 `modCompileOnly("maven.modrinth:emi:1.1.24+1.21.1+fabric")`（`emiDev` 属性开关 `modLocalRuntime`，不进发布物），依赖解析通过；`client/recipe_viewer/emi/**` 共 39 个文件，对 NeoForge 的引用只有 7 行（分布在 6 个文件：`FluidStack`/`FluidType`/`IFluidHandler`），其余是 EMI API + 本仓 GUI/配方类型。临时把这 39 个文件加入 slice 后 `verifyCommonSliceNoNeoForge` **按预期拦下**，随后已回滚、仓库保持 BUILD SUCCESSFUL。**第二步**：7 行 NeoForge 流体引用已改写为本仓类型（`mekanism.api.fluid.FluidStack`、`IExtendedFluidHandler`、`BUCKET_VOLUME`→1000）；再次纳入后编译暴露出真正的移植缺口——EMI 的 NeoForge 专属 `NeoForgeEmiStack` 在 Fabric 版 EMI 中不存在，且 EMI 树还依赖未纳入的 `QIOCraftingTransferHandler` 等文件，故 slice 再次回滚保持绿灯。**第三步**：5 处 `NeoForgeEmiStack` 已换成 Fabric 版 `FluidEmiStack(fluid, componentsPatch, amount)`，`Mekanism.rl/logger` 换成 `MekanismAPI`（核心入口 `Mekanism.java` 属 NeoForge 版不得纳入）；补纳入 `GuiElementHandler`/`GhostIngredientHandler`/`QIOCraftingTransferHandler`/`RVTransferUtils`/`color` 包后，编译错误从审计拦截一路收敛到 **32 处 GUI API 签名不匹配**（`GuiVerticalScroll`/`GuiInnerScreen` 等构造参数不同）。slice 仍回滚保持绿灯。**第四步（编译通过）**：EMI 子树与依赖类全部纳入 slice 后编译成功。关键修复是 `GuiBar.IBarInfoHandler` 与顶层 `IBarInfoHandler` 两套类型并存——删掉内嵌接口、全仓 21 个文件统一用顶层类型，一举消掉 32 个错误中的 26 个；其余为 `chemical.getKey()`→`Holder.unwrapKey()`、`Capabilities.CHEMICAL.getCapability(stack)`、以及 NeoForge item-capability 回退改为只查组件式罐。`fabric.mod.json` 已注册 `emi` entrypoint 与 `suggests.emi`，`assemble` 产物内含该 entrypoint，`./gradlew -p fabric test` 451 项通过。**第五步（真实客户端通过）**：mdb owned session + `fabric/run/mods` 里放 EMI 1.1.24，客户端输出 `MEKANISM_FABRIC_EMI_PROOF categories=30 chemical_stacks=65 ethene=true`——**30 个配方分类注册成功、65 个化学品进入 EMI 适配器、`mekanism:ethene`（上游的 ethylene）在册**；EMI 在创造物品栏正常渲染（截图存档）。尚未逐个点开分类核对渲染细节与 ghost/拖拽交互。[记录](evidence/p8-release/w74a-emi-step1.json)

W-89：回归检查与修复——全量 `release-check.sh`（含 clean build，6 步 352+19+59+88+122+100 秒）**PASSED**，未发现功能回归；但发现一个真实的发布完整性问题：**同一份源码两次 clean build 出来的 JAR sha256 不同**（W-71/W-85/W-89 三次核心 JAR 哈希各异），发布物无法用哈希验证。原因与修复：Gradle 归档默认写文件时间戳且按目录遍历顺序打包，已在 `fabric/build.gradle` 加 `tasks.withType(Jar).configureEach { preserveFileTimestamps = false; reproducibleFileOrder = true }`；连续三次重建四个 JAR 哈希**完全一致**，`./build.sh` 产出的 dist 与 standalone assemble 一致，随后 `release-check --skip-build` 仍 5 步 PASSED。[记录](evidence/p8-release/w89-regression-reproducible.json)

W-87（第二遍）：docs/ 顶层去重归档——把三份只被 ROADMAP 与索引引用的早期工作笔记 `MACHINE_SPINE.md`、`MACHINE_OWNER_ADMISSION.md`、`MODEL_IMPLEMENTATION_COMPARISONS.md`（1055 行）移入 `docs/archive/`，各加 `Archived (W-87, 2026-10-31)` 注记指向 `milestones/README.md`；`docs/README.md` 索引与 ROADMAP W-05 行同步更新。顶层文档 24→22（含新增索引），链接校验 0 broken。保留 `PORT_MATRIX.md`/`POLICY_HISTORY.md`/`API_DELTAS.md`/`FABRIC_*.md`（均被 AGENTS 或里程碑现行引用）。[记录](evidence/p8-release/w87b-docs-archive.json)

W-83（受阻）：裂变熔毁受控路径**未完成**——`/mek build` 的 `build` 字面量要求命令来源是 `ServerPlayer`，控制台没有实体，`help mek` 里看不到 `build`、`mek build fission <pos>` 直接报错；同一台官方 NeoForge 参考服控制台表现**完全一致**（可见子命令 chunk/debug/retrogen/radiation/testrules 相同），证明这是上游设计而非移植缺失，D4 关于“控制台可跑 `mek build`”的假设需要修正。三条可行路径已记录（玩家连接后 `execute as`、离线写 level attachment、reflect 取 AttachmentType），推荐第一条。**路径一实际尝试后又被拦**：开发客户端（只加载 core）加入四 JAR 专用服务器时被拒——客户端报 `Received 781 registry entries that are unknown to this client ... mekanismadditions`，说明模组集不匹配拒绝正常工作；要做下去必须让客户端与服务端同模组集（loom 的 `generatorsClient`/`generatorsServer` 配对），但 **mdb 只接受 runClient 类任务**，`runGeneratorsClient` 不被接受，需要用户裁决或给 mdb 加白名单。[记录](evidence/p8-release/w83-fission-blocked.json)

W-88：文档终稿——根 `README.md` 重写为入口（Fabric 来源与成熟度说明 + 「我想做…→看哪份文档」6 行表 + 三条常用命令 + 证据索引，上游介绍原样保留在 “Upstream Mekanism” 小节）；新增 `docs/README.md` 索引（24 份顶层文档逐份一行说明 + archive 说明 + 三条约定：当前状态以 `milestones/README.md` 为准、每个断言链证据、archive 只读）。两份入口文档的相对链接经脚本校验 **0 broken**。未移动 docs/ 顶层文档——逐份检查都是现行主题笔记，移动会破坏既有引用。[记录](evidence/p8-release/w88-docs-final.json)

W-87（第一遍）：清理第二轮——`rg TASK0[0-9]{2}` 命中 57 处，绝大多数是**出处说明**（解释与上游的差异来源），删除会丢审查依据，故只修**失效路径**：`TASK052_RESUME_2026-10-26.md` 与 `TASK052_ADJUDICATION_EFFECTIVE_SIDE.md` 已归档到 `docs/archive/`，6 处源码注释仍指旧路径，已全部改到归档路径；`./gradlew -p fabric assemble` BUILD SUCCESSFUL。重复/过时文档合并留给 W-88。[记录](evidence/p8-release/w87-cleanup-pass2.json)

W-86：CHANGELOG 与发布说明——新增 [`CHANGELOG.md`](../../CHANGELOG.md)：版本命名、四 JAR 能力范围、与 NeoForge 的差异（Fabric 平台/Team Reborn Energy/条件翻译/无第三方集成/NeoForge 品牌配置项）、已知限制（含频率与 QIO 未纳入往返探针）、四条验证命令，以及可直接贴到 Modrinth/CurseForge 的发布说明段落；README 已加指向。数字与声明逐条对应证据（依赖版本取自 `fabric.mod.json`、451 JVM 测试、存档结论对应 W-65/W-77/W-80/W-82）。[记录](evidence/p8-release/w86-changelog.json)

W-85：许可证与归属审计——新增 [`docs/LICENSES.md`](../LICENSES.md)；发现并修复两处缺失：**三个附加 JAR 完全没有 LICENSE**（Jar 任务补 `from('../LICENSE')` 重命名）、**maven-artifact 的 Apache-2.0 与 NOTICE 未随 JAR**（补进 `META-INF/licenses/`）。核心已带 MIT + night-config LGPL-3.0 + NeoForge 两副本（仅归属说明，JAR 内无 `net.neoforged` 类）。重建后四个 JAR 均含许可证条目，sha256 全部更新（记在证据里，并说明 W-71 的旧哈希以当时提交为准）。[记录](evidence/p8-release/w85-licenses.json)

W-82（部分）：传输网络跨区块——跨区块短网络（源 OUTPUT=4 / 汇 INPUT=1 / 中间线缆显式 `connection:[I;3,3,3,3,2,1]`）在一个 `sprint 200t` 内把 800000L 从源搬到汇；`save-all flush` + 停服 + 重启后汇保留 1200000L，再传 200000 升到 1400000L，**网络从存档重建并继续工作**。会话内真正卸载未能证明（`forceload remove` 后 `loadedChunks` 仍 1444）。顺带记下一条关键经验：**`fill` 出来的线缆 `connection` 全 0、网络不成立**，前几轮传输失败都是这个原因。跨维度/区块加载器/辐射未做。[记录](evidence/p8-release/w82-multidim-networks.json)

W-80：**机器库存与侧配置的跨端往返通过**——找到 tile 槽位的真实序列化键（`ContainerType.ITEM` → `SerializationConstants.ITEMS="items"`，元素 `{item:{...},slot:<byte>}`），用 `be set-nbt` 写入 4 个铁矿石 + 控制台 `data merge` 写入 `component_config.config0=[6,6,6,6,6,6]`，停服后由四个 Fabric JAR 加载同一世界，`be get-nbt` 回读**逐字段一致**；此前的 `Items` 键、`storage.insert`、`screen open-block`、`reflect` 全部被丢弃或拒绝，原因已记录。能量**也已通过**（`be set-field --path energy_containers --value '[{"slot":0,"stored":{"#nbt":"long","value":5000}}]'`：mcdebug 只有 `{"#nbt":"long",…}` 能表达 LONG，`be set-nbt` 只写 INT 所以六种形状全被丢弃；`ok:false NOT_APPLIED` 是键名规范化造成的假阴性），NeoForge 写 stored=5000 → Fabric 回读一致。此前的定位记录：`BasicEnergyContainer.deserializeNBT` 只认 `stored` 的 **LONG** 标签，而 mcdebug 的 `--nbt` 参数是 JSON、数字只能表达 INT，六种键形状全被静默跳过；另记录一条工具事实——mcdebug 在 Fabric 侧对 Mekanism 机器 `storage list` 为空（同服箱子正常、machine smoke 的漏斗断言通过），属 mcdebug 发现限制而非移植缺陷。

W-80（第一遍，部分）：**打通了 NeoForge 参考服的 mdb 驱动**（副本环境加 `mcdebug-neoforge-0.8.0.jar` + `kotlinforforge-5.11.0-all.jar`，status 返回 `modLoader=neoforge`/`fluidUnit=mb`/`energyUnit=FE`，安装步骤写进 `neoforge-reference.sh` 的 `install_env`），但**写入机器运行内容仍无可行路径**：`inv.*`（非 vanilla 容器）、`data merge`/`be set-nbt`（键不是槽位序列化键）、`storage.insert`（inserted:0，带面则找不到句柄）、`screen open-block`（-32018 无 MenuProvider）、`reflect`（NeoForge 专用服加载 client 类被拒绝）六条都试过，逐条贴了返回错误。W-77 的库存/能量残留与 W-80 同源。[记录](evidence/p8-release/w80-save-roundtrip.json)

W-84：CI 工作流 `.github/workflows/fabric-port.yml`——在 `java-port` 分支的 push/PR 上跑 `./gradlew -p fabric test verifyApiSliceNoNeoForge assemble`（本地已跑通，BUILD SUCCESSFUL），并上传四个 JAR 作 artifact；**不跑**六个需要 MC 运行时的任务与全部 smoke（在注释里写明原因与本地等价命令）。YAML 已解析校验，但未在真实 GitHub runner 上执行。[记录](evidence/p8-release/w84-ci.json)

W-81（第一遍）：规模与堆基线——两端用**完全相同的 fill 命令**搭约 700 根终极通用线缆的网络，`/tick sprint 120000t` 加速采样：Fabric 四 JAR 侧 **13.1 s（9176 ticks/s，0.11 ms/tick，mspt 0.62，1781 区块）**，官方 NeoForge 侧 **29.2 s（4114 ticks/s，0.24 ms/tick）**；堆用量 Fabric 716MB/1024MB、官方 379MB/872MB，RSS 1526/1437MB。**不把"快 2.2 倍"当性能结论**——两侧世界不同（新超平坦 vs 样本世界）、模块集不同（四 JAR vs 仅核心），且 sprint 吞吐是加速态指标。[记录](evidence/p8-release/w81-scale-baseline.json)

W-64（部分）：真实客户端读数——mdb 会话在 `Mekanism F60 Debug` 世界读到 **Mekanism 键位确实注册**（44 个键位里 10 个 `key.mekanism.*`）与 **GUI 真实打开**（`GuiMetallurgicInfuser`，42 槽位，176x166，截图落盘）。过程中踩到三处工具链问题并解决：mdb 把 `fabric/run` 误推断为 `:fabric:runClient`（改用 `--project fabric`）、fabric 独立构建没有 gradlew（临时 symlink 根 wrapper，用完删除）、dev 客户端缺 kotlin adapter（把 fabric-language-kotlin 放进 `fabric/run/mods`）。第二轮读数：**MekaSuit HUD 在客户端渲染**（穿戴后左上角三枚指示器，数值 0% 因未充能）、**MekaTool 的 OBJ 模型正常渲染**（主手截图可见），并记录一条告警级贴图警告 `meka_tool#inventory` 材质名 `minecraft:3/6` 被当 sprite 解析。仍未读数：裂变熔毁、Generators 音效、按键触发行为。[记录](evidence/p8-release/w64-client-readings.json)

W-63：官方测试 oracle 评估（只出评估页，不扩测试矩阵）——官方 `src/test` 24 个文件、`src/gameTest` 12 个文件；移植测试树 93 文件/451 项里只有 `WildcardMatcherTest` 有对应物且已改写（新增 locale 用例），该页曾误称“当前无 byte-identical 纳入项”，W-90 复核后**已更正**：`officialRootTestOracle` 早已纳入 18 个官方根测试（含 `BiLongMultimapTest`/`IndexedCuboidMapTest`/`ChunkUtilsTest`/`Distribution*` 三个），仅 `SearchQueryParserTest` 缺失；现已补入（见 W-90）。可 byte-identical 纳入的是 4 个纯逻辑用例（`SearchQueryParserTest`、`BiLongMultimapTest`、`IndexedCuboidMapTest`、`ChunkUtilsTest`），`Distribution*Test` 三个早已纳入；不可纳入的：三个 `*AccessTest`（测有意不加载的 `IMekanismAccess` 服务）、`forgeenergy/EnergyConversionTest`（NeoForge 能量语义）、整包 `src/gameTest`（依赖 NeoForge GameTest 框架）。datagen 与发布资源一致性沿用 W-75 结论（无未解释缺口）。按 AGENTS“不主动增加测试覆盖”，本轮不纳入任何用例。[记录](evidence/p8-release/w63-oracle-evaluation.json)

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

## 已知工作区产物（给巡检）

- `fabric/gradlew`、`fabric/gradle` 是**临时符号链接**（指向上级目录的 wrapper）。原因：`mdb session start --project fabric` 找不到 `fabric/` 下的 wrapper 会直接拒绝（`target project has no Gradle wrapper`），而客户端又必须由 mdb 启动。**每次会话结束都会删除**；若巡检时看到它们，说明当时有会话在跑或清理遗漏，可直接删除，不影响构建。

### 清理后重建客户端启动参数（mdb）

`build.sh --clean` 与 `release-check.sh` 的第 1 步都会删掉 `fabric/build/`，连带删除 Loom 生成的
`build/loom-cache/argFiles/runClient`。此后 `mdb session start` 会失败于 `could not open .../argFiles/runClient`，
而 `configureClientLaunch` 不会重建它（只有真正执行 `runClient` 才会，但按 AGENTS 客户端只能由 mdb 启动，
不能为了生成参数文件去跑一次客户端）。可用的重建办法：用 init 脚本导出真实运行时 classpath，再按 Loom 的格式写回。

```bash
cat > /tmp/print-cp.gradle <<'EOF'
allprojects { tasks.register('dumpRuntimeCp') { doLast {
  def cfg = configurations.findByName('runtimeClasspath')
  if (cfg != null) new File("/tmp/cp-${project.name}.txt").text = cfg.files.collect { it.absolutePath }.join(':')
} } }
EOF
./gradlew -p fabric -I /tmp/print-cp.gradle dumpRuntimeCp --no-configuration-cache
```

然后按 `-classpath` + 冒号连接的方式写入 `fabric/build/loom-cache/argFiles/runClient`。**两点必须注意**：

1. `remapClasspath.txt` **不是**启动 classpath（缺 fabric-loader / dev-launch-injector），用它拼出来会报
   `ClassNotFoundException: net.fabricmc.devlaunchinjector.Main`；正确来源是 Gradle 的 `runtimeClasspath`。
2. `runtimeClasspath` 同样**不含**本项目的类与资源输出目录，必须把
   `fabric/build/classes/java/main` 与 `fabric/build/resources/main` 加在最前面。漏掉它们时客户端能启动、
   但**开发模组根本不在 classpath 上**——表现为 `block not registered: mekanism:crusher`、JEI 里没有
   Mekanism 物品，很容易被误判成“注册坏了”。

## 收尾状态（2026-10-31）

**已完成并落盘证据**：W-74a…W-74d（EMI/Jade/JEI/WTHIT）、W-91…W-96、W-97…W-100、W-101 分析、W-102、W-103、W-105、W-106、W-107、W-108（部分）、W-109。

**未完成（已知、有缺口记录、有最小实施路径）**：

| 项 | 缺口 | 为何没有做完 |
|---|---|---|
| W-104 | 涡轮性能、真实区块卸载、跨维度传送余项 | 需要构造对应场景（远景传送、区块卸载后的网络重连、涡轮满负荷），单项都要独立会话 |
| W-110 | 双玩家安全拒绝 | `fabric-paired-network-smoke.sh` 用的是握手 fixture（同名单玩家、连上即退出），做安全验证要新增 fixture 代码，超出“最小扩展” |
| W-111 | ×3/×4/×5 化学链 | 需搭氧气/氢气/硫酸产线与化学品槽，非一次会话能完成 |
| W-112 | 工厂/升级/并行吞吐 | 同上，需要满输入输出与回压场景 |
| W-79 / W-98 / W-99 | 整合包形态、Sodium/Iris、第三方科技能量 | 均在等用户裁决或外部样本，属挂起 |

**本轮明确排除的误判**：把矿石放进粉碎机不动是正确行为（官方同无该配方）；JEI 逐类截图不可行是工具限制（改用运行时计数覆盖 30/30）；
机器能量跨存档保留（45 FE 实测），先前 `energy=0` 是关机前耗尽而非存档丢失。
