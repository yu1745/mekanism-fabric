# M-parity — 官方 NeoForge 与 Fabric 移植的功能覆盖对账

状态：P0–P6 已执行并提交，P7 补测（气体／复杂配方机器）已执行（2026-11-02）。状态表以 `README.md` 为准，本文只记现状／下一步／未决项／证据。

## 目标与口径

"覆盖率"指**功能**：每个方块／物品类型在官方 NeoForge 与 Fabric 移植上，是否同时满足

1. **存在**：注册 ID 存在、可放置／可给予、方块实体与 GUI 可打开；
2. **配置数值**：容量、最大输入输出、槽位数、处理 tick 数、产率等与官方一致；
3. **运行行为**：给燃料／输入／能量，加速若干 tick，读产出与消耗，与官方一致；
4. **存档往返**：官方世界在 Fabric 加载后状态保留（只做官方→Fabric 单向）。

比较口径：直接读方块实体 NBT 与 `storage` 读数。Mekanism 内部能量单位是 J，读 NBT 能绕开 FE／E 与 mB／droplet 的换算；流体、化学品读 NBT 内的储罐字段。覆盖率 = 各层通过项 ÷ 应测项，逐家族列表，不合并成单一百分数。

## 非目标

- 不新增测试类、不改产品代码；发现差异先记录为"差异"，是否修复另行裁决。
- 不做像素级 GUI 比对（只做存在性、槽位／控件结构）；不做行为类物品（喷火器、护甲、工具实际效果）的使用测试，另列清单。
- 已有官方对照证据的项目（W-116 涡轮、W-121 运输器守恒、W-122 太阳能链、W-123 四台机器 sorting、W-124 太阳能配置）直接引用，不重测。
- 不触生产、不 push、不发布；不动 `upstream/`。

## 复用环境（已验证）

| 项 | 位置 |
|---|---|
| 官方工程（tag `v1.21.1-10.7.19.85`，commit `a00109e4`） | `/tmp/mekanism-neoforge`；`runs/client/mods` 内有 mdb 与 Kotlin for Forge 四个 jar |
| 官方客户端会话 | `mdb session start neo --project /tmp/mekanism-neoforge --task runClient --game-dir /tmp/mekanism-neoforge/runs/client --display :140` |
| Fabric 客户端会话 | `fabric/gradlew`、`fabric/gradle` 临时符号链接（用户要求保留）；`mdb session start fab --project . --game-dir fabric/run --world <世界> --display :140` |
| 显示 | 主代理自起的 Xvfb `:140`（854x480，GUI scale 2.0） |
| 配置 | 两端 `Mekanism/client.toml` 的 `[window.side_config] pinned` 须为 `false`，否则侧边窗遮住 GUI |

两端都用**客户端会话的集成服务端**做功能读数（`--session <名>` 同时路由两个平面），保证架构对称；读数走服务端平面，不依赖渲染。

## 方法

- **探针表驱动**：`scripts/parity/probes/*.yaml`，每行一个方块家族／类型：方块 ID、布局（放置、相邻部件）、初始化（`inv.setSlot`、`be.setNbt`、`storage.insert`）、sprint tick 数、待读字段（`be.getNbt` 路径、`storage.get`）。
- **同表两端各跑一次**：`scripts/parity/run_probe.py --side neo|fab --table X` 输出 `docs/milestones/evidence/parity/<table>.<side>.json`；`scripts/parity/diff.py` 逐字段 diff，产出 `<table>.diff.json` 与表格。
- **等待纪律**：用 `wait.until` + `/tick sprint`，不 sleep；先读一次谓词值再长等；随机性（净化室耗氧等）只比"允许的范围"，并在表中标注。
- **证据体积**：只存 JSON；截图每个阶段至多 3 张，压缩后入库（预算上限 8 MB，当前 6.2 MB）。
- **会话纪律**：只用 `mdb session start/stop`；每阶段收尾确认无残留 java/gradle 进程；Xvfb `:140` 为自有，整个里程碑结束再停。
- **成本记录**：每个子代理任务按其 session 文件里每次请求的精确 usage 折算美元（Haiku 5.5：输入 $0.10、输出 $0.50、缓存写 ×1.25、缓存读 ×0.1／百万 token；单次提示超 10 万 token 时 ×5）。

## 阶段

| 阶段 | 内容 | 完成标志 |
|---|---|---|
| P0 | 环境固化：把"起会话、对齐 client.toml、清场、停会话"写成 `scripts/parity/session.sh`；两端各起一次确认 `world_ready` | 两端 `session.sh start/stop` 可重复，无残留进程 |
| P1 | **存在性对账**：枚举官方 4 个 JAR 与 Fabric 4 个发布 JAR 的方块／物品 ID（blockstates／items 资源 + 运行时 `setBlock`／`give` 探测），逐类 diff | 清单与差异表；缺失／多出 ID 逐条给原因 |
| P2 | **发电机家族**：热能、太阳能（含高级）、风力、生物质、燃气、热电（若存在）等，按配置表对 `energy_containers` 产率、容量、燃料消耗做同条件读数；涡轮／裂变／聚变多方块引用既有证据，只补未覆盖项 | 每个发电机类型的产率／容量／消耗两端同表 |
| P3 | **加工机器家族**：碎石、富集、熔炼、压缩、注入、净化、化学类、离心、熔炉工厂各 tier；输入→输出、处理 tick、耗能 | 每台机器一条配方链读数，两端同表 |
| P4 | **储能／储罐／传输器**：能量立方各 tier 容量与输入输出、流体／化学品储罐容量、管线／线缆 tier 吞吐 | 各 tier 容量与限速同表 |
| P5 | **GUI 存在性与存档加载**：官方侧把全部方块按网格放置并存档，Fabric 加载同一份拷贝，逐个 `open-block` 读 `gui screen` 的槽位／控件结构并抓 `gui problems`；对比与官方的结构差异 | 每个有 GUI 的方块：能打开、无渲染错误、槽位坐标一致 |
| P6 | 汇总：功能覆盖率表（家族 × 四层）、差异清单、未测范围，更新本文与 `README.md` 状态格 | 覆盖率表与差异清单落盘 |

每阶段结束写一小节：做了什么、读数文件、差异、未测范围；做不通带首条错误原文，立刻转下一阶段，不静默扩题。

## 现状

P0–P6 已完成，结果见本节各阶段与"P6 汇总"；未发现需修复的产品缺陷。限制与未覆盖范围见"发现与限制"。

### P2 发电机家族（完成，9/9 一致）

- 方式：Fabric 侧改用 **发布 JAR 的一次性专用服务端**（`scripts/parity/fab_server.sh`，四个 `dist/*.jar` 同进程 + mdb 服务端平面），官方侧用开发客户端的集成服务端；同表 `probes/generators.json`，`run_probe.py` 跑、`probe_diff.py` 比。原因：generators 开发会话间歇性启动失败（见下"发现"），发布 JAR 路径不受影响且更接近交付物。
- 9 个用例（太阳能、高级太阳能、风力、生物质乙醇、热能煤／岩浆、燃气氢／乙烯）200 tick（燃气 20 tick）的能量、流体、化学品、物品、`active_state` 逐字段完全一致（如太阳能 8844、高级太阳能 53064、风力 12060、生物质 70350、岩浆 19687）。读数：`evidence/parity/gen.*.json`。
- 涡轮／裂变／聚变多方块沿用既有证据（W-116 等），本里程碑未重测。

### P3/P4 加工机器、储能、容量（完成）

- **默认状态与 NBT 基线**（`baseline_probe.py`）：501 个方块，放置后与空转 40 tick 后的默认方块状态和方块实体 NBT，496 个一致；5 个差异全是储液罐多出 `fluid_light` 状态——`BlockFluidTank` 为让原版光照引擎感知储液亮度的**有意适配**（`bab48b57`，源码注释说明）。
- **容量扫描**（`capacity_probe.py`）：177 个方块实体过量写入后读回，能量容器钳制值 177/177 两端一致（如基础能量立方 4,000,000 J、终极 256,000,000 J、碎石机 20,000 J）。注意：流体／化学品储罐载入时不钳制，该方法**未能**揭示罐容量，只证明槽位索引一致。
- **机器行为**（`probes/machines.json`，29 例：碎石、富集、熔炼、锯木、结合、压缩、四级熔炼／粉碎／富集工厂、加热器、激光放大器等）200 tick 后 28 例逐字段一致；`sawmill_oak_planks` 差在锯末副产物，属概率产出，补测两端各 6 轮每轮 32 块木板 1000 tick：产出均为木板剩余 30、木棍 12，锯末两端都出现但不是每次（Fabric 6 轮中 1 轮无锯末，官方 3 轮均有）——概率性，未做统计检验。
- 气体／复杂配方机器已在 P7 补测（见下）。仍未覆盖：数字采矿机、QIO 全系、传输器吞吐速率、热能传导、感应矩阵等多方块的数值对账；已有的 W-116／W-121／W-122／W-124 证据不重复。

### P5 存档加载与 GUI（完成）

- **官方存档 → Fabric 发布 JAR 服务端**（`world_roundtrip.py`）：在官方世界网格摆放全部 177 个方块实体，写入种子状态（能量、水、氢、物品），快照后存档，拷贝给 Fabric 服务端加载：151/177 完全一致；其余 26 个全部属于**运行中状态**（正在加工的机器的 `active`、进度、能量消耗，燃烧发电机的储量与 `update_delay`，放射性废料桶的衰变，`heat_capacitors`）加上 5 个储液罐的已知 `fluid_light`；所有物品、流体与化学品**内容**以及静态字段没有丢失或改变。该比对在加载后 20 tick 读取，运行中机器的差异是时间效应，不是数据丢失，但未做"先让机器全部空闲再存档"的严格版本。
- **GUI 存在性**（`gui_sweep.py`）：官方 104 个方块打开 GUI；Fabric 核心会话 98 个 + generators 会话共 104 个。界面类、标题、槽位数、GUI 矩形、控件数、全部槽位坐标 **104/104 完全一致**。额外检查了 `gui.problems` 计数字段（见 JSON）。additions／tools 无方块 GUI 差异需要比。

### 发现与限制（需用户知晓）

1. **generators 开发会话间歇性启动失败**：`Registry value mekanism:security is not bound`（`MekanismGenerators.java:63`→`BlockDeferredRegister.java:47`）。子代理只读调查结论为"无法定论"：间歇性，08:41 与 08:55 成功，08:45–08:48 连续失败；已排除入口顺序颠倒、mod.json 未展开、重新编译；怀疑 `fabric/generators.gradle:15-16` 把 `main` 输出与运行时类路径又加入 generators 源集，造成核心类两条加载路径，**尚未证实**。发布 JAR 路径（专用服务端）四模块同进程加载正常，故不影响交付物，仅影响开发会话。若要查，下次失败时加 `-Xlog:class+load` 过滤 `MekanismDataComponents`。子代理另指出 08:37／08:39 的 `run-generators` 会话其实没有加载 generators（`devModule` 未生效）。
2. **mdb 在 Fabric 侧 `storage list` 对 Mekanism 机器返回空**：与 W-80 记录一致，是 mdb 能力发现限制，不是移植缺陷；因此本里程碑不用 `storage` 句柄，能量用 NBT。
3. **mdb 报错不明显**（官方侧缺 Kotlin for Forge 时静默等待），已记 memory，待改 mdb。
4. Gradle 配置缓存会让 `devModule` 失效，`session.sh` 对 Fabric 关闭配置缓存。
5. 声音事件注册未对账（超过 mdb 摘要上限）；官方持续供汽、受控裂变熔毁、语音等既有未验证项不变。

### P6 汇总（功能覆盖率）

| 层 | 范围 | 结果 |
|---|---|---|
| 存在（注册表、放置、物品、BE） | 4 个命名空间的方块 499／物品 782 个有效 ID；8 个小注册表 | 两端集合一致，0 差异 |
| 默认状态与放置后 NBT | 501 个方块 | 496 一致，5 个有意适配（`fluid_light`） |
| 能量容量 | 177 个方块实体 | 177 一致 |
| 发电机行为 | 9 例（6 种发电机） | 9 一致 |
| 加工机器行为 | 29 例 | 28 一致，1 例概率产出两端均出现 |
| 存档往返（官方→Fabric） | 177 个方块实体 | 151 完全一致，26 仅运行态差异，内容无丢失 |
| GUI 存在与布局 | 104 个有界面的方块 | 104 一致 |

没有发现需要修复的产品缺陷。能量立方、机器和发电机的数值与官方一致到个位数。

### P7 补测：气体／流体／复杂配方机器（完成，无产品差异）

- 表 `probes/machines2.json`（16 例）：电解分离器、化学氧化机、化学灌注器、化学注入室、净化室、化学溶解室、化学洗涤器、化学结晶器、旋转冷凝机、加压反应室、同位素离心机、营养液化机、颜料提取机、颜料混合机、流体释放器、基础储液罐。官方开发客户端 vs 发布 JAR 的 Fabric 专用服务端，200 tick。证据 `mach2.{neo,fabsrv,diff}.json`。
- 结果：**14/16 逐字段一致**，其中加压反应室一例两端都没启动（输入配方不对：官方同样 progress 0），**该例无效，不算覆盖**；其余 13 例有实际产出（含电解产出氢 402／氧 201、离心机 UF6→燃料 201、洗涤器、旋转冷凝机、溶解室等的物品／罐／能量全部同值）。加压反应室仍待用正确配方补测。
- 2 例差异（化学注入室、净化室）：物品产出和能量消耗一致，只差**气体余量**（官方 37 vs Fabric 26）和 `used_so_far`。同表在官方端自跑两次，同样互相不一致（气体余量 49/71，`used_so_far` 163/140，差异最大约 ±20%）。原因是探针落点：`data merge` 与首次 tick 的相位不固定、气体罐过量写入被钳到约 210 mB，用量按 tick 累计，相位差几 tick 即改变读数。Fabric 读数落在官方自身的波动范围内，不判为差异。
- 轨迹表 `probes/gas_trajectory.json`（1／10／50／100／150 tick）：溶解室 5/5 逐字段一致；注入室、净化室因上述相位噪声无法逐值对齐，已用官方自比较界定噪声范围。
- 限制：本轮没有精确到逐 tick 的相位控制，气体用量的**逐 tick 速率**未做严格对账；数字采矿机、QIO、传输器吞吐、热传导、感应矩阵仍未覆盖。

## 未决项

- "使用所有物品"中的行为类用法（喷火器、护甲、工具效果）未纳入，待另行裁决。
- Fabric 世界的地形不是超平坦（出生点 y=99）；探针一律在专用区域 `forceload` 后自建平台，不依赖原地形。

## 执行记录

### P0 环境固化（完成）

- `scripts/parity/session.sh start|stop|status neo|fab`；Fabric 用 `PARITY_MODULE=core|generators|additions|tools` 选源集，游戏目录 `fabric/run`（core）或 `fabric/run-<module>`。两端都进入超平坦、创造、允许作弊的 `MekParity` 世界（世界创建只需一次；其余模块目录直接复制 `MekParity` 存档）。
- 踩到的坑（均已处理或记录）：① 官方侧 mdb 的 NeoForge jar 依赖 Kotlin for Forge ≥5.10，缺失时游戏停在加载错误界面且 `session start` 静默等待（已记 memory，待改 mdb）；② 继承来的 `DISPLAY=127.0.0.1:1` 无效，必须传自有 Xvfb `:140`；③ Fabric mdb jar 依赖 fabric-language-kotlin，非核心模块目录需补；④ **Gradle 配置缓存会让 `devModule` 失效**，模块会话只加载核心——`session.sh` 现对 Fabric 设 `-Dorg.gradle.configuration-cache=false`，并以日志里的 `mekanismgenerators` 等模块 id 核对。
- 通信：`scripts/parity/rpc.py` 直连 `<gameDir>/mcdebug/socket`，一次连接数千次调用（存在性探针全量 <1 s）。

### P1 存在性对账（完成，零差异）

- 方块：官方与 Fabric 运行时可放置的方块 ID 集合一致（核心 200、发电机 28、附加 271、工具 0；各有 1 个资源名既非方块——`advanced_bounding_block`、`superheating_element` 等——两端均不可放置，按"两端皆无"处理）。物品：核心 381、发电机 33、附加 296、工具 72，两端皆可 `give`；其余为模型变体名，两端皆失败。方块实体有无：0 处不一致。
- 小注册表（menu/fluid/entity_type/creative_tab/data_component_type/recipe_type/recipe_serializer/particle_type）按命名空间对账：全部一致（menu 87、fluid 44、entity 10 等）。
- `block`、`item`、`block_entity_type`、`sound_event` 超过 mdb 摘要上限，由 `exist_probe.py` 的运行时放置／给予覆盖；声音事件未对账。
- 读数：`evidence/parity/exist.*.json`、`exist.diff.json`、`registry.*.json`、`registry.diff.json`；脚本：`exist_probe.py`、`exist_diff.py`、`registry_dump.py`、`registry_diff.py`。
