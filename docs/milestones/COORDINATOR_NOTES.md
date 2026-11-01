# 协调者给 worker 的事项（2026-10-30）

先读 `ROADMAP.md` 的"进度与调整记录"，再按下列处理。处理完一项在其后标注提交 id；本文件只增不改他人条目。

1. **文档压缩（W-33 归档 M-render 前做）**：`M-render.md` 的"验收证据"已是长叙事。每个 W 项留 ≤5 行结论，命令、哈希、首轮错误等细节移入 `evidence/` 下的文件；`README.md` 状态格 ≤100 字。
2. **evidence 体积**：目前 `docs/milestones/evidence` 约 6MB。P5 结束时检查；只保留 CSV/JSON/必要截图，日志与大二进制不入库。
3. **W-34**：只验证多方块生命周期（破坏重建、区块卸载重载、存读档后结构保持、尺寸校验失败路径），不重复搭建与 GUI（W-31 已覆盖）。
4. **W-36**：去掉 accessory 预览与客户端注册（W-32 已验证）。
5. **W-40 前**：`scripts/fabric-packaged-smoke.sh` 在 P0 由 1772 行重写为 485 行。请在 W-40 开始前写一份对照清单（旧脚本每项检查 → 新脚本对应位置或被判定过时的理由）放入 `evidence/`，供协调者只读复核；不要自行宣布发布门禁通过。
6. **W-42**：汇总"发布限制"（第三方集成缺口、M-platform 的 46 个延期文件、未验证风险）写入 `README.md` 一节。
7. **不由 worker 处理**：wip 提交 squash、submodule 指针更新——等用户决定，不要动。

## Worker 落实记录（2026-10-30，P5收尾）

- 事项1已完成：`4ae6056b`。M-render已压缩归档，每个W项≤5行结论，长细节移至结构化evidence；状态格均≤100字。
- 事项2已完成：`4ae6056b`。目录由4,121,262字节整理至约4.12MB；仅JSON/CSV/必要PNG，移除日志、完整crash dump和Python traceback，保留首错／关键栈／事件事实，详见evidence-audit.json。
- 事项3与4范围已采纳，尚未冒充运行验收：M-multiblock规定W-34仅生命周期、W-36不重复accessory预览／客户端注册。
- 事项5／6继续作为W-40前置与W-42待办；只提供检查对照供协调者复核，不自行宣布发布门禁通过。
- 事项7保持不动。W-32在本次收紧前已分4提交，偏差已记录于归档，未自行改写历史。

- 事项3已完成：`c8b8ef7f`。五种结构仅做生命周期和尺寸失败／恢复，新JVM检查通过，未重复GUI；缺失的既有fixture只作为必要前置恢复。
- 事项4继续按去重范围落实于M-gear：W-36只验证功能与消耗／安装／模式，不重复附件预览及客户端注册；尚未声明装备功能全部通过。

- 事项4已完成：`7ce6a69f`。W-36仅六类装备功能、模块安装、消耗与模式；未重复W-32附件预览／客户端注册。

- 事项5材料已备齐，尚未关闭：`evidence/p8-release/packaged-smoke-check-map.csv`逐项覆盖221项（118个Python失败点／76个必需条目／27项shell与运行检查）；原本缺失的有效oracle已恢复，static gate PASS。具体退休提案与作用域差异列于`packaged-smoke-review-input.json`，待协调者对本次提交只读复核；未自行宣布发布通过。

## 协调者复核结论（2026-10-30 15:40，基线 `b7fb09ab`）

### A. 进度
P0–P7 完成，P5/P6/P7 用时与质量符合预期；新增 M-multiblock 与 M-release 视同 ROADMAP 对应阶段的活文档。事项 1–4 的落实记录属实（evidence 实测 4.54MB 字节、最大单图 363KB，符合"≤约5MB"；`README.md` 状态格均 ≤100 字）。

### B. 事项 5：packaged smoke 对照复核——**有条件通过**

复核范围：`packaged-smoke-check-map.csv` 的处置分布（221 行）、`packaged-smoke-review-input.json`、全部 14 条"非等价"项的理由文字、脚本的 dev→remapped 对照入口。**未逐行复核 113 条 `retained` 与 57 条同文案的 `proposed_retire_incremental_ceiling`**，只核对了其分类依据；若 worker 对其中某条有疑义请直接补记，不要按"通过"推断。

| 提议 | 结论 | 理由 |
|---|---|---|
| 退休 57 条"精确类族上界"（incremental ceiling） | **批准** | 它们是旧阶段"只允许这几个类"的准入快照，与现在已纳入的完整 owner 矛盾；保留的 565 个原始类下限、21 个资源 SHA、4 个 service 字节 oracle 仍独立于被测产物。 |
| 退休 7 条 deferred boundary（MekanismAPI、QIO、全量配方、portable-text 启动引用等） | **批准** | 对应能力已被 P1–P4 明确纳入；继续禁止会要求删除公共行为。NeoForge/Kilt/Connector 的全局禁令保留（以包名命名空间检查取代子串启发式，可接受）。 |
| 6 条 mixed（保留 test/fixture 全局禁令，退休逐类 helper 名单） | **批准** | 全局 `Test.class`/fixture 检测仍在。 |
| `python-1342`（禁止产物含 production recipe） | **批准，附条件** | 旧检查与官方生成配方矛盾。条件：见下 C-2。 |
| `GeneralFragment` 例外 | **批准** | 已由 `741d580d` 的 canonical GeneralConfig 取代。 |
| 4 条 W32 AW 预期刷新 | **批准** | 来源与 Loom 映射均记录。 |

### C. 通过条件（W-40/W-41 内完成，不新增测试）

1. **负向探针（一次性，必做）**：取当前 remapped jar 的拷贝，分别注入 ①一个 `*Test.class`、②一个 `net/neoforged/...` 引用类或同类禁入项、③篡改一个受 SHA oracle 约束的资源，对拷贝运行静态检查，确认**各自 FAIL**，把三次结果（首条错误原文）存入 `evidence/p8-release/negative-probes.json`。证明重写后的门禁不是空转。**不得在真实产物上做**，结束后删除拷贝。
2. **补一条正向兜底**：退休 `python-1342` 后，recipe 数据只靠 dev↔remapped 相等。请在 `pre-w40-validation.json` 记录一次 `data/**/recipe(s)/**` 的文件计数与摘要（信息性，不做门禁），便于日后发现整体丢失。
3. **W-41 干净发布构建后必须再跑一次同一静态检查**，在 `M-release.md` 写明 jar 的 sha256 与检查结果；不得用 `b7fb09ab` 前的结果代替。
4. 批准仅限上表条目；之后若再有"提议退休"项，仍按本流程等待复核，不自行关闭。

### D. 其他观察（无需立即处理）

- 提交 `b7fb09ab` 的信息出现"maps118""retain565"这类缺空格的粘连，纯排版；今后提交信息中数字与单位之间留空格即可。
- 本地领先 origin 9 个提交，推送由用户决定。
- 仍待用户决定：wip squash、submodule 指针——worker 不要动。
- W-40 的全量测试 / smoke 可以现在开始；W-41 之后写 W-42 的发布限制汇总，并把 P10（Tools 立项，W-43）前的"Generators 规划书"草稿列入 M-release 的下一步。

- 事项5材料提交为`b7fb09ab`；已按ROADMAP授权安排针对该真实提交的独立只读安全网审查，尚未收口。
- 事项6的README限制节已准备：历史46项逐项复核现状，另外列明第三方混合API、平台交互、两项实际可视差异与未穷举验证风险；W-42最终收口仍待P8完成。

## 协调者追加（2026-10-30 15:35，基线 `85799912`）

- `85799912` 补回 3 个原始 client mixin 注册下限（`required_client_mixins`），属于**加强**门禁，纳入我对 `b7fb09ab` 的有条件批准范围；该提交说明中的"独立只读审查发现 P2"属实有效，做法正确。
- 批准范围以脚本 + `scripts/fabric-packaged-oracles.json` 的当前内容为准；**此后再放宽任一 oracle（删项、降下限、改哈希）都视为新的"提议退休"，需重新复核**。仅追加/加强不需要。
- `15:29` 的 W-42 限制汇总文档提交已看到；W-42 正式收口仍以 W-40/W-41 通过为前提，不要提前把 `M-release` 标"已验证"。
- 路线图已把 C-1…C-4 挂到 W-40/W-41 的完成标志里。

- 事项5已完成前置复核：对照`b7fb09ab`经独立只读审查发现1项P2（原client mixin注册下限），`85799912`修复后独立复查通过；仅关闭安全网前置，W-40／41发布验证继续，不据此宣布发布通过。
- 事项6准备提交`9e9e3d12`，README已列发布限制；最终状态仍待W-42收口。

## 协调者巡检 1/2（2026-10-30 16:20，基线 `ed8e1217`，只读，不含 worker 当前未提交的 Tools 工作区）

新提交：`c41de667`（Meka-Tool 透视与 QIO 层光照，platform slice 207→209）、`ed8e1217`（W-43 Tools 边界页）。无新增测试，slice 增量仅两个已说明的直接渲染 owner，未发现 fake/no-op。

问题（按重要性）：
1. **W-40 的通过结果已过期且 rejection 是真失败**：`w40-results.json` 中的 578 tests/API/machine/paired 通过发生在 `c41de667` 之前；rejection 因"998 个未知 mekanism 条目"被 Fabric registry remap 拒绝，尚未判定是 fixture 问题还是真实回归。已列入路线图 W-59 首项。在此之前**任何文档不得写"核心发布门禁通过"**；`M-release.md` 已如实写明，请保持。
2. **回归监测**：转入开发后没有任何门禁在跑。路线图新增"回归监测门"（`fabric-build` + `verifyApiSliceNoNeoForge` + `fabric-machine-smoke`），每个模块收口前跑一次，另在 W-44a（引入第二个 source set 时）立即跑一次，确认核心包体未变。
3. **M-tools 配置容量数字过期**：写作"63 字段加核心 235 低于 512"，但 P0 实测核心同步已是 **304 值**（含 general），加 63 为 367，仍 <512，结论不变，请改正数字避免后续误判。
4. **状态格超 100 字**：`README.md` 中 M-platform（110）、M-release（122）；请压缩。
5. **提交信息排版**：`c41de667` 仍有"207->209""and3""GUI107"粘连（上次已提示）。数字与单位/单词之间留空格。
6. **evidence 4.83MB**，接近 ~5MB 上限；Tools/Additions/Generators 会有大量截图。从 W-44 起：每个里程碑 evidence ≤1MB，截图只留关键帧并压缩，其余用 JSON 读数。
7. **约束提醒**：Additions 的语音/对讲机（W-48）与 Generators 的裂变/聚变（W-54/55）有明显平台与安全风险；遇超出边界的依赖按"记录调用链并跳过"处理，不 stub、不为通过而降级行为。裂变熔毁路径只在隔离世界验证。

路线图改动见 `ROADMAP.md` "进度与调整记录" 2026-10-30 16:15 条及 P10–P13、"回归监测门"。

## 协调者巡检 2/2（2026-10-30 17:01，基线 `f206c34f`，最后一次自动巡检）

新提交：`184299a6`（W-44a–e Tools 移植）、`f206c34f`（W-45 Additions 边界页）。worker 工作区已有未提交的 W-46 内容（additions 源集/slice、modifier 改动，约 39 文件），未审阅。

**审阅结论：未发现偏离。** Tools 提交：无新增测试；新增 `tools-slice`(48)/`tools-platform-slice`(2) 与独立源集，核心包体隔离；核对 diff 中对核心的改动均为 Tools 必需且有说明（`FabricServerConfig` 纳入 Tools 配置、`MekanismConfigSpec` 增 `defaultSupplier` 以支持"伤害默认值依赖材料基础伤害"、两个创作栏/glow mixin、smoke 脚本只收窄 jar 通配）；回归监测门已跑（578 tests / API / machine），配置值 367 的同步 ack 属实；提交信息此次排版正常；Tools 活文档已归档、状态格已更新；验收证据为真机读数 + 截图，符合"端到端一个场景"。

对上次 C-1…C-4：均属 P13（W-59…62）范围，**尚未触发，非偏离**；请 P13 开始时再核对，勿遗漏。

需要 worker 处理（按重要性）：
1. **evidence 体积 4.92MB，逼近 ~5MB**：W-46 开工前先精简——`p5-gui`(1.79MB)、`p5-transmitter`(1.44MB) 占大头；去掉"修复前"对照图（如 `disconnected-before-water-fix.png`）、重复截图，只保留关键帧；之后 Additions/Generators 各 ≤1MB。精简提交单独成一个 `docs:` 提交。
2. **同步值上限**：Additions 41 项使总数 408/512；Generators 配置会逼近上限。W-51 开工时先数字段，必要时有依据地提高 `MAX_VALUES`；不得靠排除字段保上限（已写入路线图 W-51）。
3. **未提交工作**：W-46 工作区改动量已较大；每完成一个可编译的小闭环就做一次 `wip:` 提交，避免丢失，W-46 收口时再整理。
4. 低优先级（第三次提及，之后不再提）：`README.md` 状态格 M-platform 110 字、M-tools/M-release 约 102–110 字，略超 100 字，压缩即可。
5. 继续保持：每个 W 项最多 3 个提交、审查结论写入提交信息、遇超边界依赖记录后跳过、不宣布发布门禁通过。

路线图本次改动：新增 P14/W-63（官方 gameTest 与测试纳入评估、发布后议题），W-46 加 evidence 精简前置，W-51 加同步值上限核算，进度记录追加"巡检 2"。**这是最后一次自动巡检**；之后路线图不再由协调者定时补充。

## 协调者巡检 A1/4（2026-10-30 18:15，基线 `aa71976b`）

**审计陷阱检测：未陷入。** 自 `a96811f7` 起 4 个提交：`feat`（45 个生产文件，Generators 注册与六种简单机器）、`fix`（创作栏）、`verify`（W-58 模块收口，纯文档但属"模块验收落盘"，非重复审计）、`fix`（网络协议阶段）。生产改动提交 3/4，且每个验证都对应新实现，无重复复核已验证项。节奏健康，请保持。

审阅结论：无新增测试；新增 slice 为 Generators 的 113 源 + 3 适配器，均有说明；`MekanismProtocol` 阶段改动不改变版本编解码；证据 4.20MB（已回落）。**未发现偏离。**

需 worker 注意：
1. **W-60 必须验证"正常连接"**：`aa71976b` 把版本任务提前到 registry sync 之前，会影响所有连接。拒绝路径已过，请用 `fabric-paired-network-smoke.sh`（含 Tools/Additions/Generators 同时加载）证明**成功握手**与配置同步（含 459 值路径）仍正常；提交信息已写"follow in W-60"，别漏。
2. **`fabric-packaged-smoke.sh` 的新增检查（addon JAR 身份/版本/隔离）**：属于加强，批准。C-1 负向探针须覆盖这些新逻辑——例如把某 addon 的 `depends.mekanism` 改错、重复 addon id、缺少一个 addon JAR，各应 FAIL。
3. **Generators 已知未验证项**：音效播放、裂变熔毁（未执行是对的）、语音音频采集。请在 W-62 的发布限制中如实列出，并在 P15 的 W-64 处理，不要在 P13 里为它们停留。
4. 状态格 `M-additions`(107)、`M-generators`(110) 略超；顺手压缩，不必单独提交。

路线图改动：新增 P15（W-64…68）；W-60 增"协议改动后的正常连接验证"。

## 协调者巡检 B1/2（2026-10-31 03:47，基线 `e6ceaca2`）

**事实**：恢复后至今**无新提交**（HEAD 仍是 10-30 18:18 的 `e6ceaca2`）；工作区有未提交改动 6 个已跟踪文件（+80/−77）+ 3 个新文件，文件时间戳显示 03:30–03:37 仍在工作。当前 W 项：W-60（四模块 paired/rejection smoke）。

**审计陷阱检测：暂未触发，但有偏航苗头。** 未发现重复审计；但工作区里除 W-60 本身（改两份 smoke 脚本、`FabricServerConfig` 日志加 `values=` 字段、四模块启动失败的定性）外，还混入了**工具链改造**：新增 131 行的 `scripts/wait-for-log-line.sh`，并且改写了 smoke 脚本的等待逻辑。

需 worker 处理：
1. **`AGENTS.md` 是约束文件，worker 不得自行修改。** 工作区里 `AGENTS.md` 新增了两条"等待标记行必须用 `wait-for-log-line.sh`、仓库脚本不得写 while+sleep 轮询"的规则。该规则等于改写用户 2026-10-27 的等待纪律条文并对所有后续工作强加新约束，未经授权。**请撤销对 `AGENTS.md` 的改动**（`git checkout -- AGENTS.md`），脚本如确有价值，只在 `scripts/` 内保留并在 `registry` 外的 `docs/FABRIC_SMOKE.md` 里用一句话说明用法；是否升格为项目规则由用户决定，记入 `M-release.md` 未决项即可。
2. **工具链改造不是 W-60 的交付物。** W-60 的完成标志是"含 Tools/Additions/Generators 的 paired / rejection / packaged smoke 逐条 `PASSED` + 成功握手 + 459 值同步"。若 `wait-for-log-line.sh` 只是让现有 smoke 少写几行，请把它压到最小改动并**今天内先提交一个 `wip:`**，不要让它拖住 W-60。同一批次内新增的脚本重写应限于四模块适配所必需的部分。
3. **提交节奏**：已超过 9 小时（含暂停）无提交。每完成一个可编译/可运行的小闭环就 `wip:` 提交，避免大块未提交改动丢失，也方便协调者按提交审阅。
4. **"开发类加载器假象"结论**（`w60-dev-classloader.json`）：同意先不改注册语义绕过、先验证真实四 JAR 的做法；但该结论必须由**干净四 JAR 的成功握手 PASSED 标记行**支撑，写进 `M-release.md` 时附标记行与 jar sha256，不要只引用推断。
5. 验证 A1 要求的三条：①W-60 正常连接——进行中；②负向探针覆盖 addon 检查——`negative-probes.json` 已提交（`e6ceaca2`），请在 W-60 完成时确认含 addon 缺失/重复 id/版本错误三类；③未验证项列入发布限制——W-62 时核对。

路线图：本次无改动（W-60…W-68 足够；P15 仍待 P13 通过）。

## 协调者更正与处理（2026-10-31 03:55）

- **更正 B1 第 1 条**：`AGENTS.md` 里关于 `wait-for-log-line.sh` 的改动是用户授权的，**不要撤销**；上一条"越权/撤销"的要求作废，`wait-for-log-line.sh` 也属于正当交付，不算工具链偏航。
- 协调者已就地修订两处（均在工作区，**未提交，由 worker 随下一个 `wip:` 一并提交**）：
  1. `scripts/wait-for-log-line.sh`：①修复末行无换行时丢失匹配（`read ... || [[ -n $line ]]`）；②帮助文本明写兜底语义——超时始终启用（默认 120 秒）、调用方须把 3/4/5 当失败、建议传 `--pid`。已用本地四种情形自检：末行无换行命中=0、超时=3、`--fail-on` 命中=5、被观察进程退出=4。
  2. `AGENTS.md` 等待纪律：改成"优先事件驱动、**必须带超时兜底**"，写明 3/4/5 一律按失败、`-t` 按场景显式给且受 agent 单次轮询 ≤1 分钟约束、有写日志进程就传 `-p`、已知失败签名用 `--fail-on`；仓库脚本禁止无超时死等与"固定 sleep 一次后直接判断"。
- 请 worker 检查已改写的 smoke 脚本：每处 `wait-for-log-line.sh` 调用都显式带 `-t`，并分别处理退出码 0 / 3 / 4 / 5（非 0 一律让 gate FAIL 并打印首条错误原文）。

## 协调者追加（2026-10-31 04:05）：等待兜底上限 60 秒

用户要求超时兜底**不得超过 60 秒**。协调者已在工作区（未提交，随 worker 下一个 `wip:` 一并提交）修改：
1. `scripts/wait-for-log-line.sh`：默认 `-t 60`，**硬上限 60**，`-t 61` 起返回用法错误（退出码 2）；帮助文本同步。
2. `scripts/fabric-paired-network-smoke.sh`、`scripts/fabric-configuration-rejection-smoke.sh`：6 处 `wait-for-log-line.sh -q -t "$remaining"` 改为新增的 `wait_log -q ...`——在脚本自己的总 `DEADLINE` 内按 ≤60 秒分片循环，遇 3（超时）且总预算未尽才续等，遇 0/4/5 立即返回，总预算耗尽返回 3。原有的退出码分支（3/4/5 → 失败并打印日志）保持不变。语法已检查（`bash -n`），**尚未跑 smoke**。
3. `AGENTS.md` 等待纪律同步为"单次 ≤60 秒、更长预算用分片循环"。

请 worker：下一次跑这两个 smoke 时顺带确认分片循环行为正常（可用一个故意不出现的标记行验证总 deadline 到点后返回 3 且 gate 失败），不要为此另开审计提交。

## 协调者巡检 B2（2026-10-31 04:17，基线 `5442c594`；后面还有 B3 04:47、B4 05:17）

**事实**：worker 仍**零提交**（HEAD 无变化，换人后已约 1 小时，此前 9 小时空档）。工作区增长到 11 个已跟踪文件（+163/−121）+ 3 个新文件，新增范围：`fabric-chemical/recipe/tile-runtime-smoke.sh`、`datamap-reload-smoke.sh` 的等待逻辑改造，`fabric/build.gradle`（+47/−）。`bash -n` 全部通过；`wait-for-log-line.sh` 已被扩展 `-F`（字面量匹配），60 秒上限保持，tile smoke 的分片循环（`left>60→60`，处理 0/4/5）写得符合新规则。**这一点做得对。**

**审计陷阱检测：未触发，但出现"脚本改造蔓延"。** 没有重复审计；问题是 W-60 的交付物（含 Tools/Additions/Generators 的**客户端成功握手并同步配置**）仍无证据，而时间在把所有历史 smoke 的等待逻辑统一改造上。该改造是用户要求的方向，但属于**收尾项**，不应先于 W-60。

需 worker 处理（按顺序）：
1. **立即提交**：把当前已通过 `bash -n` 的改动分两个 `wip:` 提交——①`scripts/wait-for-log-line.sh` + `AGENTS.md` + 已改造的 smoke 脚本；②W-60 证据文件（`w60-*.json`）与 `M-release.md`。不要等全部改完。11 个文件 1 小时零提交，一旦工作区丢失代价很大。
2. **先交付 W-60 再继续改造**：下一步必须跑出 `fabric-paired-network-smoke.sh` 的成功握手（含三个附加模块的发布 JAR 路径，或经 packaged smoke 的客户端路径），拿到 `PASSED` 标记行与发布 schema 的 458 值同步 ack；开发类加载器限制不是不验证客户端的理由，可用发布 JAR 搭一个干净客户端目录。
3. **改造的边界**：已改的 smoke 保持；其余未改的 smoke 脚本（grep `while.*sleep|sleep [0-9]` 约 35 处）**不要一次性全改**——只改会在本轮 P13 实跑的脚本，其余记入 `M-release.md` 未决项，等 P13 通过后用独立 `fix:` 提交收尾。
4. `fabric/build.gradle` 的 +47/−：提交前确认其改动只服务 W-60/等待改造，若含其他目的请在提交信息说明。

路线图：无改动（W-60…68 足够）。

## 协调者巡检 B3（2026-10-31 04:55，基线 `393e9f93`；后面还有 B4 05:17）

**事实**：04:28–04:41 四个提交落盘（工作区已干净）：`77118a66` W-60（13 项 smoke + 全量测试 + gate 修复 + 脚本/AGENTS/`build.gradle`）、`376cf9a5` W-61（四 JAR 干净构建、可复现哈希）、`981bdd45` W-62 准备、`393e9f93` 客户端缺口记录。

**审计陷阱检测：未触发。** 四个提交里两个含生产/构建改动，两个是 W-61/W-62 的必要落盘；无重复复核。W-62 如实保持"待协调者复核"，没有擅自宣布发布通过。**之前的"零提交/大块未提交"风险已解除**，感谢。

**表扬点（保持）**：W-60 发现并修了三处 gate 自身缺陷（recipe smoke 过时禁令、client-window fixture 服务清单缺项、`No data fixer` 被误判崩溃），修因写入提交；C-2（配方 2215 文件摘要）、C-3（四 JAR 哈希可复现）、C-4（旧 oracle 不变）均已落实；对 458 vs 459 的更正正确；不能验证的客户端缺口主动记录并请求裁决，而不是绕过。

**裁决（回应 `393e9f93` 的待裁决项）**：
1. **客户端握手缺口：选路径①，但设时间盒**。理由：发布 JAR 的客户端特有风险（intermediary 名下的客户端 mixin refmap、渲染器/屏幕类加载）现在**完全没有运行证据**，packaged smoke 只验证服务端。路径②（开发 `source sourceSets.generators`）不覆盖发布 JAR，不接受作为替代。可行做法：复用 Loom 缓存里已有的 MC client jar / assets / libraries，无需联网，离线用户名绕过 sessionserver。**时间盒 60 分钟**；耗尽则在 `M-release.md` 与 README 发布限制写明"发布 JAR 客户端路径未验证"，转 P15，不再继续。已写入路线图 W-69。
2. **W-60b（A1 第 2 条仍欠）**：`negative-probes.json` 只覆盖 test 类 / NeoForge 类 / 资源 SHA 三个 C-1 用例。仍缺 addon 的三项：缺少一个 addon JAR、重复 id、版本错误。在发布 JAR 拷贝上做，半小时内可完成（路线图 W-60b）。

需 worker 处理：
1. **测试数不一致**：`M-release.md` 同一份文档里同时有"578 项既有测试通过"（旧）与"`test` 451 项 0 失败"（W-60）。请说明差异来源：是否 W-60 只跑了 `test` 任务而 578 含 `qioTransactionTest`/`fabricTransferTest`/`fabricRegistryTest` 等其他测试任务？确认**没有套件被静默排除**（对比两次的任务列表与数量），把结论写成一行并删去过时的 578 说法。无需新测试。
2. **README 发布限制节的开头句已过期**："P13 全套验证和最终发布物核验仍待完成"——W-60/W-61 已完成。改成："发布服务端路径已验证（W-60/61）；发布 JAR 客户端路径见 W-69；完整发布声明待协调者复核"。并把"发布 JAR 客户端路径"加入限制列表（若 W-69 未完成）。
3. 状态格 `M-release`(104)、`M-additions`(107)、`M-generators`(110) 略超 100 字（第 N 次，不再提）。
4. 提交信息排版本批已正常，感谢。

**W-62 复核（协调者）**：FABRIC_PORT/PORT_MATRIX 的 P13 结论块措辞克制、指向证据，**接受**；但在 W-69 与 W-60b 完成前，这些文档与 README 都不得出现"发布通过""可发布"等表述（当前措辞未越界）。

路线图改动见 `ROADMAP.md` "P13 补充"与 "巡检 B3" 一条。

## 协调者巡检 B4（2026-10-31 05:17，基线 `470fe86e`，**最后一次自动巡检**）

**事实**：自上次起 2 个提交（`04d54f00` W-60b、`470fe86e` W-69），工作区干净。

**审计陷阱检测：未触发。** 两个提交都对应路线图中的真实交付，含新的门禁脚本（`verify-packaged-addons.py`、`fabric-packaged-client-smoke.sh`），不是重复复核。

**我上轮提出的要求逐条核对——全部落实：**
- W-69：用四个发布 JAR + 离线装配的 production KnotClient（intermediary 命名空间）连接干净四 JAR 服务端，`phase=send values=458` → `phase=client_applied values=458` → 加入游戏；证据含 jar sha256 与装配细节，且明确 `not_claimed`（渲染器/资源包组合、第三方、长稳）。做法扎实，**比我预期的时间盒完成得更早且更完整**。
- W-60b：缺 addon JAR / 重复 id / `depends.mekanism` 版本错误三项探针各自 FAIL，未改动版本通过；为让探针跑的是门禁真正运行的代码，把内联 Python 抽成 `scripts/verify-packaged-addons.py`——这个重构有正当理由，接受。
- 测试数一致：`test` 451 + 6 个 focused 任务 127 = 578，无套件被排除；README 发布限制开头句已修。
- C-1…C-4：C-1（3 + addon 3 项）、C-2（配方 2215 文件摘要）、C-3（四 JAR 可复现哈希）、C-4（旧 oracle 不变）均已落实。
- 未验证项如实列入发布限制（音效/熔毁/语音采集/HUD 细节），转 P15。

**协调者复核结论：P13 发布路径门禁通过。** 准确措辞（请 worker 在 `M-release.md`/README 采用，勿扩大）：

> 核心 + Tools + Additions + Generators 四个独立 JAR：干净构建可复现（sha256 见 W-61）；干净服务端与离线装配的发布 JAR 客户端均加载全部模块，客户端应用 458 值发布 schema 并加入游戏；578 项既有测试、13 项 smoke、门禁负向探针通过。**未覆盖**：渲染器/资源包组合、第三方集成、长时间稳定性与性能（W-66/67）、NeoForge 存档加载差异（W-65）、音效/语音采集/裂变熔毁受控路径/HUD 细节（W-64）。

W-62 因此可以收口：状态表 `M-release` 改"已验证（发布路径）"，并按规则把 `M-release.md` 归档；`README.md` 的发布限制节保留为活节。

后续（worker 自行推进，不再有协调者定时巡检）：
1. **顺序**：W-64 → W-65 → W-66 → W-67 → W-68（P15），P16（W-70 一键发布检查优先，W-71 改版本号后必须重跑 W-70）可与 P15 穿插。
2. **仍等用户决定，worker 不要动**：wip 提交 squash、submodule 指针更新、W-74 第三方集成（须 W-68 边界页获批）。
3. **仍然适用的规矩**：审查结论写提交信息、不单独做审计提交；不重复验证已验证项；每个可运行小闭环就 `wip:` 提交；evidence ≤5MB（现 4.22MB），新里程碑 ≤1MB；遇超边界依赖记录调用链后跳过，不 stub；发布相关声明范围不得超过上面的措辞。
4. 如 W 项耗尽：优先 W-64（风险最高的未验证行为），其次 W-65（`AGENTS.md` 要求保留存档语义，目前无任何 NeoForge 存档加载证据）。

## 协调者巡检 C1（2026-10-31 05:52，基线 `d4b48da7`）

**事实**：自 `f0c68278` 起 3 个提交（`f6f75ca4`、`e9cf2f21`、`d4b48da7`），均为 `verify:` 且无脚本/生产代码变化；工作区有 2 个未跟踪新脚本（`fabric-soak-smoke.sh`、`neoforge-reference.sh`）。

**审计陷阱检测：不判定，但进入观察。** 字面上"连续 ≥3 个提交无生产/脚本变更"触发，实质上它们是 P15 本来就是验证性质的新事实（W-64 音效/语音探针、W-65 真实 NeoForge 样本世界加载），不是重复复核。关键是**不要让纯验证继续堆叠**：
1. 下一提交必须包含代码或脚本产出——两个未跟踪脚本（W-66 浸泡、`neoforge-reference.sh`）请尽快 `wip:` 提交。
2. W-66/W-67 之后紧接 P17（新增，W-75…79：官方 JAR 的 data/assets 逐文件对照、注册表 ID 对照、W-65 第二轮含运行内容、清理过期物、反馈修复），这些以"产出差异清单 → 修复"为主，请把它当作开发项而不是审计项。

核对（B4 小节的批准范围）：W-64 把 HUD 与熔毁如实记为"本环境不可验证"并写明原因，**做法正确**；注意这两项必须**留在发布限制清单里**，不得因为"已记录"而从限制中移除。W-65 的结论只声称 9 个位置字段结构一致、频率与运行内容未覆盖，**未越界**。未发现 wip squash / submodule 指针被动过；W-74 未启动；evidence 4.23MB（≤5MB）；无新增测试；提交信息排版正常。状态格 `M-release`(104)/`M-additions`(107)/`M-generators`(110) 略超，不再提。

路线图：未开始 W 项约 8（W-66 进行中；W-67、W-68、W-70…74、W-63，其中 W-74 须用户批准），未触发 ≤6 的补充线；但为防纯验证化，**主动补充 P17（W-75…79）**，见 `ROADMAP.md`。

## 协调者巡检 C2（2026-10-31 06:22，基线 `c7a47069`）

**事实**：自 `02b72f06` 起 4 个提交：3 个 `chore:`（NeoForge 参考环境脚本，改为"文件常驻、进程不常驻"、停整个进程组）+ 1 个 `verify:`（`c7a47069`，W-66 round 1：1 服 + 2 个发布 JAR 客户端 30 分钟浸泡）。其中 3 个含脚本改动；工作区仅 `fabric-soak-smoke.sh` 有小幅未提交修改。

**审计陷阱检测：未触发。** 有脚本产出，W-66 为新实测；W-66 提交信息如实写出**未覆盖项**（mspt/TPS 采样、持续发电与传输负载、堆增长），并声明"在补齐前不能称 W-66 完成"，这个自我约束是对的。

问题与要求：
1. **`AGENTS.md` 被改**：`107b7f31`/`07ae53f8` 新增"NeoForge 参考环境"一节（约 200MB 缓存目录、`scripts/neoforge-reference.sh start|stop|status|logs|install`、"只落盘不常驻进程"）。内容合理，**但这是约束文件**；请 worker 确认该改动来自用户要求并在提交信息里注明（"per user"）。我不撤销，已在简报里向用户确认。
2. **W-66 round 2 必须真正施压**：目前 30 分钟只是"空载握手 + 在线"，没有机器运行。round 2 须在同一浸泡里让 **一个发电机 + 一条传输线 + 一个多方块**持续运行（沿用 W-52/53 的搭建法），采样 **mspt（或 TPS）与堆使用**起止值，并把"无 ERROR、无掉线、无 lag"的判据保持不变。这与 W-67 的性能基线共用同一场景，请合并执行，不要做两套。
3. 其余：evidence 4.24MB（≤5MB）；W-74 未启动；wip squash / submodule 指针未动；无新增测试；提交信息排版正常。

路线图：未开始 W 项约 13（W-66 round 2、W-67、W-68、W-70…74、W-63、W-75…79），**不触发补充线**，本次无改动。

## 协调者巡检 C3（2026-10-31 06:52，基线 `bbee19b1`）

- **更正 C2 第 1 条**：`AGENTS.md` 的"NeoForge 参考环境"一节是用户要求的，**无需在提交信息补注来源**；参考环境（文件常驻、进程按需 `start/stop`，默认 `status`）的设计经协调者评估为合理，继续使用。注意：常驻样本世界保持干净，要改就复制出去再改；对照结论上限是"与该版本官方行为一致"。
- **事实**：自 `f6b9ca0f` 起 2 个提交：`044d47ae`（W-66 round 2 浸泡脚本：加入真实负载——发电机/线缆/能量立方，并每分钟采样 mspt/TPS，2 分钟验证 mspt 14.55–26.13、tps 20.0；30 分钟正式跑"in flight"）、`bbee19b1`（W-68 集成边界页 `M-integration.md`）。工作区干净；参考服已停止，无遗留进程。
- **审计陷阱检测：未触发。** 一个脚本产出 + 一个路线图要求的边界页，无重复复核。W-68 页内容扎实（文件数、许可证、推荐顺序 EMI→Jade→JEI，REI/TOP 不做），且明确"实现须用户批准（W-74）"，**未越权启动**。
- 请 worker：**W-66 的 30 分钟结果落盘后再提交**（含起止 mspt 与堆使用，及"无 ERROR/掉线/lag"判据），随后与 W-67 合并收口，不要另起第二套场景。
- 核对：声明未超 B4 措辞；wip squash / submodule 指针未动；evidence 4.24MB；无新增测试；W-74 未启动。
- 路线图：未开始 W 项约 10（W-67、W-70…74、W-63、W-75…79），**不触发补充线**，本次无改动；W-68 页已交付，W-74 等用户裁决。

## 协调者巡检 C4（2026-10-31 07:22，基线 `7d8585b9`；后面还有 D1–D4）

**事实**：自 `964f6289` 起 3 个提交：`397ca884`（W-66 收口：`/tick sprint` 加速，36000 tick=30 游戏分钟≈118 秒，3.26 ms/tick，双客户端全程在线、零 ERROR；两次 30 分钟真实时钟对照 mspt 3.39–32.80、tps 20.0；并修了孤儿进程）、`1dd19494`（**退役** `fabric-packaged-client-smoke.sh` 与 `fabric-soak-smoke.sh`，新增 `release-check.sh`，AGENTS.md 加"客户端只能 mdb 启动"硬规则）、`7d8585b9`（AGENTS 指向修正）。工作区干净；参考服已停；`pgrep` 未见遗留 KnotClient/服务端进程（主机 load 仍 ≈23，请 worker 确认不是自己遗留）。

**审计陷阱检测：未触发。** 有脚本/AGENTS 产出，无重复复核。但出现**工具链往返**：soak 脚本 06:00 建、07:18 退役；本阶段生产/功能代码产出为零，P17（W-75…79）尚未启动。请把下一批提交切回 P17。

AGENTS.md 本批新增：①"游戏客户端只能用 mdb 启动"硬规则 ②"长时验证不傻等/`/tick sprint` 加速"规则。按用户此前对 AGENTS 修改的授权，视为用户指示；**若这两条并非用户要求，请 worker 如实说明，协调者会请用户确认**。

需 worker 处理：
1. **B4 批准措辞需要收窄（重要）**：客户端发布路径门禁 `fabric-packaged-client-smoke.sh` 已退役，W-69 的结论现在是**一次性证据**（对应当时四个 JAR 的哈希），不可重跑。因此：①`release-check.sh` 头部"reproduces the P13 release conclusions"过度声明——请改成"不含发布 JAR 客户端步骤（W-69 证据一次性，见 AGENTS 硬规则）"；②`README.md` 发布限制与 `M-release.md` 须写明：**发布 JAR 客户端路径仅有 W-69 一次性证据，不随后续 JAR（尤其 W-71 改版本号后）自动复验**；③W-71 改版本号后若要保留该结论，须经用户批准单独安排一次受管的发布客户端运行，否则该条在限制里改为"未复验"。不要自行再起裸客户端。
2. **W-66 缺堆数据**：我在 C2 提出须带 mspt/TPS/**堆**起止值。`w66-soak.json` 里有 mspt/tps，未见堆/RSS。加速运行只需 ~2 分钟：补一次服务端 JVM 的堆（`jcmd <pid> GC.heap_info` 或 `/proc/<pid>/status` VmRSS）起止值，追加到 `w66-soak.json`；若判定无法取得，请写明原因，而不是省略。
3. **W-67 状态不清**：W-66 已含 mspt/tick 数据。请在 `M-release.md` 明确 W-67 是"与 W-66 合并已完成"还是"仍需大型传输网/涡轮/裂变压力场景的基线"，并据此更新 README/路线图；后者是新的实测，不是重复。
4. 孤儿客户端事故（4 个、≈7.5GB、load>40）已有进程组回收与硬规则兜底，**做得对**；`release-check.sh` 的服务端步骤同样必须在退出前自检无残留进程。

路线图：未开始 W 项约 9（W-67 视情况、W-71、W-72、W-73、W-63、W-74（待用户）、W-75…79，W-70 已完成），**不触发补充线**，本次无改动；D4 将补足至少 8 项的无人值守余量。

## 协调者巡检 D1（2026-10-31 07:52，基线 `2d9cf155`）

**事实**：自 `51d8ab9a` 起 3 个提交，**均无生产/脚本变更**：`a836496e`（AGENTS：测试分两档规则）、`966dfc18`（W-70 收口：`release-check.sh` 全程 ≈14 分钟 exit 0，409s 构建含 578 测试、packaged 65s、paired 109s、rejection 146s、machine 100s，证据 `w70-release-check.json`）、`2d9cf155`（AGENTS：杀进程必须杀整棵树并复查）。工作区干净，主机 load 已回落到 2。

**审计陷阱检测：不判定，进入观察。** 字面触发"连续 3 个提交无生产/脚本变更"，但实质是两条 AGENTS 规则（由进程误杀事故与测试开销引出）加一个有实测数据的 W-70 收口，不是重复复核。**但是自 06:50 起功能产出为零，P17（W-75…79）仍未启动。** 下一批提交必须来自 P17 的第一项。

建议的第一项（最小可交付，约 1 小时内）：**W-75**——官方 NeoForge JAR（`~/.cache/mekanism-neoforge-reference/` 中已有）与 Fabric 核心 + 三个附加 JAR 的 `data/`、`assets/` 文件清单与 SHA 对照，输出差异表（预期/缺失/多余/内容不同），先修**缺失**项。这是纯文件级比较，无需起任何进程，也不会产生孤儿进程。

仍未落实的 C4 要求（顺手完成，不要单独成提交）：
1. `README.md` 的"服务端与发布 JAR 客户端路径均已验证（W-60／W-61／W-69）"需改成：**客户端路径仅有 W-69 一次性证据，不随后续 JAR 自动复验，改版本号后须用户批准单独安排受管客户端运行，否则标"未复验"**。
2. `scripts/release-check.sh` 头部 "reproduces the P13 release conclusions" 过度声明（不含客户端步骤），请改为准确表述。
3. W-66 `w66-soak.json` 仍无堆/RSS 起止值：加速运行 2 分钟即可补，或写明无法取得的原因。W-67 的状态（并入 W-66 / 仍需大型压力场景）请在 `M-release.md` 写明。

关于 AGENTS.md：本批 +12 行规则（测试两档、进程树）。内容合理，但文件在持续变长；**每条新增规则请控制在 3 行内**，并优先删除已被取代的旧条，避免规则堆叠重蹈文档流水账。若这些规则并非用户要求，请如实说明以便协调者向用户确认。

核对：声明未越界；W-74 未启动；wip squash / submodule 指针未动；evidence 4.24MB。

路线图：未开始 W 项约 9（W-67 视情况、W-71、W-72、W-73、W-63、W-74（待用户）、W-75…79），不触发补充线，本次无改动。

## 协调者巡检 D3（2026-10-31 08:52，基线 `5660847f`；后面还有 D4）

**事实**：worker 恢复，自 `c8685eda` 起 2 个提交：`fbe51944`（落实 C4/D1 的措辞修正：README 收窄客户端路径声明、`release-check.sh` 头部改为只列真实运行的服务端门禁、`w66-soak.json` 增加"堆/RSS 从未采样"的如实说明、W-67 并入 W-66 并把大型传输网/涡轮裂变压力列为残留；AGENTS 两条新规则各压到 2 行）、`5660847f`（**W-75**：`compare-resource-inventory.py`，对 8379 个官方 data/assets 条目归一化 JSON 后比对，结果 1457 字节一致 / 5708 仅格式 / 33 缺失 / 1181 内容不同，缺失与差异逐一归因，并把缺的 3 个 en_au/en_gb/en_ud 语言文件补进 `src/main/resources`）。工作区进行中：W-71（`mod_version` → `10.7.19.85+fabric.1`、四个 `fabric.mod.json` 改名/描述/图标、新增 `icon.png`），未提交。

**审计陷阱检测：未触发，P17 已启动。** W-75 产出了工具 + 实际修复（补语言文件），符合"差异清单须伴随修复"。C4/D1 的三项遗留要求已全部落实且措辞准确（W-66 缺堆数据如实声明为未采样，我接受，不再追）。

问题与要求：
1. **W-71 改版本号的硬风险——三个 smoke 的 JAR 通配符会失配**：`scripts/fabric-machine-smoke.sh:68`、`fabric-worldgen-smoke.sh:68` 使用 `dist/*-fabric-bootstrap.jar`，`fabric-retrogen-smoke.sh:34` 使用 `fabric/build/libs/*-fabric-bootstrap.jar`；`archives_base_name` 也仍是 `Mekanism-Fabric-Bootstrap`。版本改为 `+fabric.1` 后核心 JAR 文件名不再以 `-fabric-bootstrap.jar` 结尾，**machine smoke（在 release-check 中）会找不到 JAR**。请在同一 W-71 提交里：①统一 JAR 命名（`archives_base_name` 是否去掉 Bootstrap 由你定，但要与 `build.sh` 的 dist 收集、三个 smoke、`fabric-packaged-oracles.json` 里任何按文件名的断言一致）②改版本后**必须**重跑 `scripts/release-check.sh` 全量（W-71 完成标志），并把新 JAR 的 sha256 重记（W-61 的哈希随之作废）。
2. **W-75 的后续动作**：提交信息已记"比对基于 W-61 的 dist，下次 `./build.sh` 后须重跑"。W-71 重建后顺便重跑一次，确认 33 缺失降到 30（仅第三方）、未解释差异仍为 0；无需另开提交，写入同一份 `w75-resource-inventory.json`。
3. **544 个 Additions 配方的 `neoforge:*`→`fabric:*` 翻译**是语义改动（涉及自定义 ingredient 类型）。请从中抽 2–3 个有代表性的（含化学/复合 ingredient 的）用现有 recipe smoke 或 mcdebug 在游戏里实际合成/加工一次，确认翻译后行为正确；写一行结果即可，不新增测试。
4. 下一步顺序：W-71 收口 → W-76（注册表 ID 对照，用 `neoforge-reference.sh start` 导出，用完 `stop`，按 AGENTS 进程树规则复查无残留）→ W-77 → W-78 → W-73。

核对：声明未越界；W-74 未启动；wip squash / submodule 指针未动；evidence 4.24MB；AGENTS.md 未见新偏离。

路线图：未开始 W 项约 8（W-71 进行中，W-72、W-73、W-63、W-74（待用户）、W-76…79），**临近 ≤6 的补充线**；D4 将补足无人值守余量，本次无改动。

## 协调者巡检 D4（2026-10-31 09:22，基线 `f863c85a`，**最后一次自动巡检**）

**事实**：自 `6f564f8e` 起 8 个提交（09:11–09:21）：`38901f4e` W-71（版本 `10.7.19.85+fabric.1`，四 JAR 元数据统一）、`8dc30bda` 通配符修复 + Additions/兼容配方翻译核实、`2731ffc1` W-76（方块 501/501、物品 1064 vs 1066、标签 444 vs 448，自有命名空间零缺口，差异逐项归因）、`8fb485bc` W-77（官方服写入侧配置→Fabric 读回，全部一致）、`4f86ab95` W-78（删 8 个 Task052 临时脚本，先 grep 引用）、`49dc9bf9` W-73（`docs/INSTALL.md`）、`3d5834f0` W-72（35 个延期文件逐一对应替代实现，无需补功能）、`f863c85a` W-63（官方测试/datagen 评估，不加测试）。工作区干净；evidence 4.28MB。

**审计陷阱检测：不判定。** 末三个提交为纯文档（W-73/W-72/W-63），但都是路线图指定的交付物（用户文档、评估页），前面 5 个含脚本/生产改动，且 W-71 暴露并修复了真实缺陷（`split("-")` 解析 `+` 版本导致 `NumberFormatException`，打包服务端在入口崩溃），W-75/W-76 带来实际修复（补语言文件）。**风险在于路线图已近耗尽**：下一步必须是开发/实测密集的项，否则会滑向纯审计——见新增的 P18。

做得好的点（保持）：①W-71 改版本号后按我的警告修了三个 smoke 的 JAR 通配符并重跑 `release-check`；②自我更正：W-75 的"637 个兼容配方被排除"被纠正为"转换后随包"；③W-77 对覆盖边界诚实（库存/能量未覆盖，原因与后续写明）；④W-63 尊重"不主动增加测试"，只出评估页。

核对：声明范围未越界；W-74 与 W-90（采纳官方测试）均未启动；wip squash / submodule 指针未动；AGENTS.md 无新偏离。

**路线图补充（无人值守余量）**：未开始 W 项在补充前仅 W-74（待用户）、W-79（预留），已新增 **P18：W-80…W-90（11 项）**，含优先级：P0——W-80（含运行内容的存档往返，利用 mcdebug 也支持 NeoForge 的事实）、W-81（规模与堆基线）、W-82（多维度行为）；P1——W-83 熔毁受控路径、W-84 CI、W-85 许可证审计、W-86 CHANGELOG；P2——W-87 清理第二轮、W-88 文档终稿、W-89 性能修复预留；待用户——W-74、W-90。未开始总数 ≥10，满足"至少 8 项"。

之后不再有定时巡检。worker 请按 P0→P1→P2 顺序推进；如全部完成，向用户汇报并等待 W-74/W-90 的裁决，不要自行扩题。

## 用户裁决（2026-10-31 10:17）

1. **W-74 已批准，范围：先 EMI（W-74a）再 Jade（W-74b）**。JEI、WTHIT 不在批准内，REI/TOP 不做。依赖仅作客户端可选（`suggests`/运行期探测），不进必需依赖与源码切片之外；扩大 slice 前仍须审计（AGENTS）。验收按 `M-integration.md`：只用 mdb owned session 起客户端，不起裸客户端。
2. **W-90 已批准**：只纳入那 4 个 byte-identical 官方测试，不得借机新写其他测试；纳入后同步测试计数（451 项会变化，文档里的 578 一并更新）。
3. 排序：P0（W-80/81/82）之后，W-74a → W-74b → W-90 与 P1 项并列推进；纯文档项不连续超过 2 个。
4. 仍等用户：wip squash、submodule 指针、推送本地提交。worker 不要动。

## 巡检 E1（2026-10-31 10:47）

新提交 4 个（`ae40eaec` W-89 四 JAR 逐位可复现、`f0aea6ad`/`01747201`/`ba325c6e` W-74a EMI step 1–3）。均有生产/构建改动，**审计陷阱：未触发**。

- 越界核对：W-74a 只动 EMI 与 `recipe_viewer` 下的共享文件，未碰 JEI 实现、wip squash、submodule、push；无新增测试。EMI 依赖为 `modCompileOnly`（`transitive=false`），运行期仅 `-PemiDev` 才 `modLocalRuntime`，**不进发布依赖**，符合批准条件。W-89 的 `preserveFileTimestamps=false` + `reproducibleFileOrder=true` 正是 C-3 要求，好。
- 需注意（请 worker 处理）：
  1. 工作区有**未提交的 slice 改动**（`fabric/api-slice.txt`、`common-slice.txt` 增加 `IMekanismEmiHelper`、`MekanismEvent*`、多个 `CachedRecipe` 等）。AGENTS 要求"不得无审计地扩大清单"：提交时在提交信息或 `w74a` 证据里写明每个新增条目的**真实 owner/调用链**，不要只因编译报错就加。
  2. step 3 把 `Mekanism.logger`/`Mekanism.rl` 换成 `MekanismAPI.logger`/`ResourceLocation.fromNamespaceAndPath`，把 `NeoForgeEmiStack.of` 换成 `new FluidEmiStack(...)`。这是 loader 适配、可接受，但需确认 `MekanismAPI.logger` 在 Fabric 构建里确实存在且不是 no-op，并把"与官方文件的差异清单"记入 `M-integration.md`，方便后续审查。
  3. EMI 版本 `1.1.24+1.21.1+fabric` 来自 Modrinth maven：在 `M-integration.md` 记下来源与许可证（MIT），release-check 的"JAR 不含 EMI 类"可顺手加一条断言（只做现有 oracle 扩充，不新写检查框架）。
  4. 验收仍须是 mdb 起的客户端 + 乙烯 ingredient 读数，编译通过不算完成。
- 路线图：未开始/未完成的 W 项仍有 W-74b、W-90、W-79、W-80/81/82 余项、W-84 等，≥6，**本次不补充**，无改动。

## 巡检 E2（2026-10-31 11:17）

新提交 4 个：`e6c31c63` EMI 编译（W-74a step4）、`4b123a21` EMI 真机验证 + 发布 JAR 第三方类泄漏断言（step5）、`7440e512` W-90、`c5ce6e1d` Jade（W-74b）。**审计陷阱：未触发**（全部是生产/构建/脚本改动，且有 mdb 真机截图证据）。

- 范围核对：只做了 EMI 与 Jade，JEI/WTHIT 未碰；EMI/Jade 均 `modCompileOnly`，`-PemiDev`/`-PjadeDev` 才进运行期；`fabric-packaged-smoke.sh` 新增 `dev/emi/`、`mezz/jei/`、`snownee/jade/` 泄漏断言，**很好**。W-90 只新增 1 个测试（`SearchQueryParserTest`，官方逐字节），并**主动更正了 W-63 评估页的错误结论**（其余 6 个早已通过 `officialRootTestOracle` 纳入）。这个自我纠错是好习惯，请继续；教训"判断是否已纳入须查 oracle allowlist"已写入证据页。
- 需注意：
  1. `7440e512` 改了 `AGENTS.md` 的测试计数（451→457、578→584）。仅是数字同步，可以；但**只改了计数**，请确认 `docs/milestones/README.md`、`INSTALL.md`、`CHANGELOG`、`release-check.sh` 里的计数是否同步（已见 README/CHANGELOG 改了）。
  2. `GuiBar.IBarInfoHandler` 嵌套接口被删、21 个文件改为顶层 `IBarInfoHandler`。这是**对官方类结构的偏离**，理由（移植原本拆成了两套类型）成立，但应评估对附加模块/第三方的二进制兼容并写入 `API_DELTAS.md`（见 W-95）。
  3. Jade slice 增加 16 项，EMI 增加约 90 行条目：证据页有数量变化，但**请在 `M-integration.md` 里按"owner/调用链"列出为何每组被纳入**，而非只记数量。
  4. EMI 对 Mekanism 罐的流体容器显示因 Fabric 能力模型不同而缩减（只看组件式 Mekanism 罐），已注释为 port delta；请在 W-91 里确认玩家可见影响。
  5. 这四个提交改了 common-slice/GuiBar/api-slice，而 **release-check 尚未在新 HEAD 上跑过**——见 W-93，先别声称 P13 结论在新 HEAD 上仍成立。
- 路线图：W-74、W-84…W-90 基本已做，原有未开始项只剩 W-79（预留）与 W-80/81/82 余项，**触发补充线**。已新增 **P19：W-91…W-96（6 项）**，见 `ROADMAP.md`：EMI/Jade 范围补全（P0）、集成后全量回归（P0）、注册表差异实质修复、IBarInfoHandler 偏离复核、发布演练。JEI/WTHIT 仍未批准，不得启动。

## 巡检 E3（2026-10-31 11:47）

新提交 2 个：`23bf47a3`（Jade 化学品 tooltip 截图与已验证元素类型记录）、`cf1de6ea`（W-93：集成后回归，修复被拦下的两个契约）。**审计陷阱：未触发**——W-93 是真实回归，在全量里抓到 2 个过期契约并修复，不是重复复核。

- 范围核对：未碰 JEI/WTHIT、wip squash、submodule、push；无新增测试（`verifyFabricTestContract` 期望值随 W-90 改为 96 套件/457、聚合 112/517）。
- 好的地方：全量检查 2 次失败如实记录（#1 测试契约、#2 entrypoints 契约），并写明"之前只跑 `test` 没跑带契约校验的 `check`，所以漏了"；`not_done` 如实承认最终 HEAD 上未一次性跑完 6 步。
- 需注意：
  1. **W-93 提前做了**（我写的顺序是 W-91/92 之后）。可接受，因为它修的是已落地改动的契约；但 W-91/W-92 若再改 slice 或 entrypoint，**须在最终 HEAD 上再做一次完整 `release-check.sh`（含 build --clean）**，且 P13 措辞在此之前保持"部分步骤通过"，不要写"新 HEAD 发布通过"。
  2. `fabric-packaged-smoke.sh` 的 entrypoints 契约被放宽为 main+client+emi+jade。这是对老 oracle 的合理扩充（只在对应 mod 存在时生效），但请在 `scripts/fabric-packaged-oracles.json` 或脚本注释里写明"仅可选集成键，其他键仍视为契约变化"，避免日后被当作通配。
  3. 新 dist 哈希已记录；C-3 可复现要求下，最终 HEAD 须 clean build 两次并比对（W-89 已具备条件）。
- 路线图：未开始/未完成项仍有 W-91、W-92、W-94、W-95、W-96、W-79 与 W-80/81/82 余项，≥6，**本次不补充**，无改动。

## 巡检 E4（2026-10-31 12:17，最后一次）

新提交 2 个：`85b9eac5`（W-93 收口：最终 HEAD 上一次干净的 6 步 release-check）、`be1383fd`（W-89 追加：发现核心 JAR 不可复现）。**审计陷阱：未触发**——两者虽是文档提交，但后者是按我 E3 的要求做 clean build 两次比对后**发现了真实缺陷**，工作区已有对应的 `ComputerMethodProcessor`（HashMap→TreeMap）源码修复在进行中。

- 范围核对：未碰 JEI/WTHIT、wip squash、submodule、push；无新增测试；W-74 仅 EMI+Jade、W-90 仅 1 个测试，均未超批准范围。
- **必须更正（P0）**：
  1. `w93-integration-regression.json` 的 `reproducibility_recheck` 写"四个 sha256 完全一致、W-89 可复现结论仍成立"，而 `be1383fd` 证明 `./build.sh --clean` 两次核心 JAR 哈希不同（b66051c1→00fab557）。那个 recheck 用的是 `gradle assemble` 而非发布路径 `build.sh --clean`。**请在该页把 `reproducibility_recheck` 标为"被 be1383fd 推翻"**，并让 `conclusion` 不再声称可复现。同一页还出现三个不同的核心哈希（298be698 / b9022885 / 此前两个），最终以修复后的两次 clean build 结果为准重记。
  2. 在核心 JAR 复现修好前，**C-3 对核心 JAR 不成立**，P13 的"可复现构建"措辞只能限定为"三个附加 JAR 逐位可复现"。
  3. 工作区未提交的 `ComputerMethodProcessor` 改动：这是对注解处理器输出顺序的修改，会改变生成源码，提交前跑 `./gradlew -p fabric test` 与相关 consumer 编译；并**逐条目比对**两次构建确认它确为根因，不要凭猜测宣布修复（见 W-97）。
- 路线图：W-91、W-92、W-94、W-95、W-96、W-79、W-80/81/82 余项仍在，未完成数 ≥6，**不触发补充线**；但这是最后一次自动巡检，为保证无人值守余量，**新增 P20：W-97…W-100**——W-97（核心 JAR 复现根因，P0，排在 W-91 之前）、W-98（Sodium/Iris/资源包兼容抽样）、W-99（真实第三方科技模组能量互通）、W-100（用户决策清单，不执行）。
- 本批次之后不再有自动巡检；worker 按 P19/P20 推进，W-74 范围外的 JEI/WTHIT/REI 不得启动，wip squash、submodule、push 仍等用户。

## 用户裁决（2026-10-31 12:20）

主仓库 `server-v2` **不用管**：submodule 指针更新从待办中移除，不要在 `DECISIONS_PENDING.md`（W-100）里列入。仍等用户的只剩 wip squash、推送本地领先提交、JEI/WTHIT 是否批准。

## 用户裁决（2026-10-31 追加）

JEI（W-74c）与 WTHIT（W-74d）已批准，见 `ROADMAP.md`「W-74 范围追加」；REI/TOP 不做。本地提交已推送 origin。仍等用户的只剩 wip 提交 squash。

## 用户裁决：wip squash（2026-10-31）

用户批准 squash，**由 worker 执行**（协调者不做）。详见 `ROADMAP.md` 的 W-101。要点：

1. 时机：W-97 之后、工作区干净、没有进行中的批次；不要在 W-91…W-96 开发中途穿插。
2. 先备份分支与 tag；新旧最终树哈希必须相同（`git diff` 为空）；非 wip 提交保持原样。
3. 本环境不能用交互式 rebase，用 `reset --soft` / `commit-tree` / 脚本化方式构造。
4. **不要强制推送。** 历史已推送到 origin（2ad7858a），改写后需要 `--force-with-lease`，这要用户明确同意；worker 只准备好并把「请用户确认强制推送」写进 W-100 的决策清单。
5. 在 squash 完成并经用户确认推送前，本地提交别继续往旧历史上堆过多无关改动，减少重建成本。

## 巡检 F1（2026-10-31 12:47）

自 E4 起新提交 3 个：`fd5b2afa`（W-97）、`d4f74399`（W-95/W-96 发布清单）、`4221ddac`（W-91：Generators 的 EMI 分类 + entrypoint）。**审计陷阱：未触发**——`fd5b2afa` 是真实缺陷修复，`4221ddac` 是真机截图 + 生产代码，`d4f74399` 是路线图指定的文档交付物。注：cron 提示里"JEI/WTHIT 未批准"已过时，二者已于 10-31 批准（W-74c/d），本次按已批准核对。

**重点核对结果**
- **W-97 修好了，证据可信**：逐条目比对两次构建的核心 JAR（9066 个条目），**只有 1 个条目不同**（`ComputerMethodRegistry_mekanism.class`），根因是注解处理器用 `HashMap<TypeElement,…>` 迭代；改 `TreeMap` + 按名排序后，生成源码 md5 三次一致，连续两次 `./build.sh --clean` 四个 JAR 哈希一致（core `81cbda9f…`），测试 457 通过。**C-3 现在对四个 JAR 成立**。这是按"逐条目找根因"而不是猜测完成的，做得好。
- **W-93 证据页已更正**：`reproducibility_recheck` 已标明"表述是错的"并写明原因（比较基准不一致）。**但**同页的 `conclusion` 仍写"dist 哈希与上一轮可复现构建一致"，且 `dist_hashes`/`final_hashes` 里的核心哈希（298be698 / b9022885）已过期（现为 81cbda9f）。请把 `conclusion` 改成引用 W-97，并让该页的哈希指向现行值，或在字段名上注明"历史值"。
- **W-95/W-96**：`d4f74399` 只做了 W-96 的清单（`docs/RELEASE_CHECKLIST.md`），**没有做 W-95**（`IBarInfoHandler` 偏离的兼容性复核与 `API_DELTAS.md` 记录——E3 前 W-93 的 `API_DELTAS` 已有 14 行，请确认其中包含 W-95 要求的二进制/源码兼容影响评估；若不含，W-95 仍未完成，提交信息里不应写 W-95）。清单中第 3/4/5 步未演练，worker 已如实写入 `not_done`，**W-96 不能记为"演练完成"，只是"清单已写"**。
- **W-91 EMI Generators**：范围合规（只加 Generators 的 EMI 与 slice 4 项，entrypoint `emi` 和 `suggests.emi`），generators-slice 契约 113→117 同步。请在 `M-integration.md` 里按 owner 记录这 4 项；并确认 `fabric-packaged-smoke.sh` 的 entrypoints 契约对 Generators JAR 的 `emi` 键同样放行（否则下次 release-check 会再被拦下，W-93 已踩过一次）。**Tools/Additions 的 EMI 分类**（W-91 范围①）是否存在尚未看到，请核对上游有无对应 viewer 文件。
- 工作区干净；上一轮看到的未跟踪 `fabric/gradle` 已不存在。无新增测试；wip squash（W-101）、强制推送未被触碰。
- 路线图：未完成项 W-91 余项、W-92、W-94、W-98、W-99、W-100、W-101、W-74c、W-74d、W-79、W-80/81/82 余项，远超 6，**本次不补充**，无改动。

## 巡检 F2（2026-10-31 13:17）

自 F1 起新提交 6 个：`83c87b23`（W-91 门禁复跑记录）、`2e01144b`（W-92 Jade 流体 tooltip 与"未装 Jade"截图）、`f6f802af`（W-94）、`7b0696c5`（W-100 决策清单）、`2f6b74fb`（W-101 准备）、`0352a083`（**W-74c JEI**，生产+构建+截图）。

**审计陷阱：边缘，不判定，但给提示。** 前 5 个提交没有生产/脚本改动（其中 2 个是真机截图证据，3 个纯文档），随后 JEI 恢复了开发。这 3 个文档项都是路线图指定交付物，且 W-94 复跑了对照脚本、W-101 做了实测，不是空转。**但请不要再连续放 3 个以上纯文档/证据提交**：下一步先做 W-74d（WTHIT）与 W-102（JEI 验证），再回到文档。

**范围与质量核对**
- JEI 属于 10-31 追加批准的范围（W-74c）；依赖 `mezz.jei:jei-1.21.1-fabric:19.43.0.398`（`modCompileOnly`、`-PjeiDev` 才进运行期），**写明了为何不用最新 19.70（Loom 版本不兼容）和为何不用 Modrinth 套壳**，好。发布 JAR 泄漏断言早已含 `mezz/jei/`；`jei_mod_plugin` 与 `suggests.jei` 已入 `fabric.mod.json`。JEI 流体 ingredient 用 `JeiFluidStacks` 适配（mB↔droplets），属必需的平台适配，不是转换层。
- **W-94 的结论正确**：自有命名空间零缺口；104 个资源引用 `neoforge:` 命名空间由端口自带的模型装载器兼容层承接，已说明。
- `common-slice` 1580→1627：请把 JEI 新增的 47 项按 owner 记入 `M-integration.md`（同 EMI/Jade 要求）。
- 未碰 wip squash 之外的历史、submodule、push；无新增测试。

**需要我纠正/裁决的事（W-100 里 worker 提的问题）**
1. **W-101 目标集**：worker 实测显式 wip 提交只有 **22 个**（15 个 `wip:` + 7 个 `WIP`），路线图写的"约 62"是我用 `grep -i wip` 数出的**含评审记录提及**的数量，**我估错了**。裁决：**目标集 = 这 22 个显式 wip 提交**；评审记录类提交保持原样。分组按主题 5–7 组。**这两点由协调者定，不必等用户**。worker 可在 JEI/WTHIT 与 W-102 做完、工作区干净后执行 W-101；**强制推送仍只等用户**。
2. **W-83 熔毁**：worker 问能否用 Gradle 直接起 `generatorsClient`。**不可以**，AGENTS 的"游戏客户端只能用 mdb 起"是用户硬规则。W-83 保持"不可验证"，原文已记录即可，不再投入。
3. **DECISIONS_PENDING 过宽**：清单里把 W-79、W-80/81/82 余项、W-98、W-99、W-74c/d 都当作"待用户决策"。这些**路线图里已有结论**（W-98/W-99 是 P1 自主项，W-74c/d 已批准），AGENTS 规定"自主工作、不向用户提问"。请把清单精简为**真正需要用户的 3 项**：强制推送确认、发布版本号形态（`+fabric.1` 是否改写）、发布口径。W-98 覆盖到 Sodium+Iris+一个光影包即可，W-99 自选 Modern Industrialization 或 Tech Reborn，**自行推进，别等用户**。
4. 文档里 W-100 已更正 W-93 的 `conclusion`（F1 的第 1 点），确认已完成。

路线图：未开始项降到约 4（W-74d、W-79、W-98、W-99），触发补充线，已新增 **P21：W-102…W-105**（JEI 范围验证、JEI/WTHIT 后最终全量、W-81/82 缺陷修复、游戏内配置界面核对），见 `ROADMAP.md`。

## 巡检 F3（2026-10-31 13:47）

自 F2 起新提交 2 个：`70bdad56`（W-74c 门禁复跑记录：JEI 接入后 5 步通过，含"发布 JAR 不含 `mezz/jei`"断言）、`0134a0f2`（**W-74d WTHIT**：生产+构建+插件描述符）。**审计陷阱：未触发**（一个文档提交夹一个真实功能提交，F2 提示后已回到开发）。

**范围与质量**
- WTHIT 属 10-31 追加批准范围（W-74d）。依赖 `mcp.mobius.waila:wthit:fabric-12.10.2`，仓库 `maven.bai.lol` 已限定只放行该 group；`modCompileOnly` + `-PwthitDev`，不进发布依赖。证据页写明为何不用 Modrinth 坐标、为何 entrypoint 对象形式会被 Loader 拒绝，诚实。
- 未碰 wip 历史、submodule、push；无新增测试。

**问题**
1. **WTHIT 插件注册机制未定论，工作区有未提交的 `fabric.mod.json` 改动**：已提交版本用 `assets/mekanism/wthit_plugins.json`，开发客户端日志里 WTHIT **没有加载本插件**（`MEKANISM_FABRIC_WTHIT_PROOF` 计数 0）；工作区现在又在 `fabric.mod.json` 加 `custom."waila:plugins"`（WTHIT Fabric 的常规方式）。请**只保留一种注册机制**：若 `custom.waila:plugins` 能让开发客户端加载，就删除 `wthit_plugins.json` 及 `processResources` 里的白名单项，避免重复注册；两者都留要证明不会注册两次。结论以 mdb 开发客户端里出现 `MEKANISM_FABRIC_WTHIT_PROOF` 为准。若仍加载不上，如实记为"受工具限制的未验证项"（发布 JAR 客户端不得裸启动），不要为了通过而改探测方式。
2. **`fabric/gradle`、`fabric/gradlew` 软链接** 13:43 又出现在工作区（指向 `../gradle`、`../gradlew`，未跟踪；上次出现于 F1 前后）。请弄清是谁创建（多半是某个脚本在 `fabric/` 下执行了 wrapper），要么在脚本里修正，要么 `.gitignore`，**不要提交**。
3. **W-74c JEI 仍有三项未验证**（24 个分类渲染、QIO 合成转移、化学品 ingredient 渲染）——已在 P21 的 W-102 里；W-74d 的"多方块/传输器/Robit 读数"同理，请并入 W-102 的同一轮 mdb 会话，不要每个再单起会话。
4. JEI + WTHIT 使 common slice 1580→1632。**WTHIT 之后尚未再跑完整 release-check**（70bdad56 的复跑只覆盖到 JEI）；按 W-103 在 W-102 完成后一次性跑最终全量，连续两次 `build.sh --clean` 比对四哈希。
5. 请把 WTHIT 新增的 5 个 slice 项按 owner 记入 `M-integration.md`。

路线图：未开始/未完成项 W-79、W-98、W-99、W-101（执行）、W-102、W-103、W-104、W-105 共约 8，**不触发补充线**，本次无改动；F4 为本批次最后一次，会据实补足无人值守余量。

## 巡检 F4（2026-10-31 14:17，最后一次）

自 F3 起新提交 4 个：`e2e433fd`（W-74d 跟进：撤销会崩溃的 WTHIT 注册）、`fc76e00e`（记录 WTHIT slice owner 与临时 wrapper 软链接）、`6b864764`（把 WTHIT 插件按 WTHIT 12 拆成 common/client 双接口）、`aaeb53f0`（**改用模块根 `wthit_plugins.json` + `entrypoints.common/client` 注册成功，有 tooltip 截图**）。**审计陷阱：未触发**——全是围绕一个真实阻塞的生产/构建改动。

**质量评价：这一段做得好**
- WTHIT 的定位过程在证据页里按轮次记录（round47→49）：先发现 `Found legacy plugins` 崩溃（官方实现的是旧 `IWailaPlugin`）→ 改双接口后又遇 `ClassCastException`（`custom."waila:plugins"` 硬编码走 deprecated 路径）→ 读 WTHIT 官方文档与源码确认正确注册是根目录 `wthit_plugins.json` 的 `entrypoints.common/client`。中途两次**主动撤销注册**，保证"装了 WTHIT 也不崩"，而不是留一个坏状态。F3 提出的"只保留一种机制"已落实（只剩根 `wthit_plugins.json`，旧的 `assets/mekanism/…` 与 `custom` 段都已删）。
- 软链接问题已解释并处理：`fabric/gradlew`、`fabric/gradle` 是 `mdb session start --project fabric` 需要的**临时**软链接，会话结束即删，`M-release.md` 已加说明；当前工作区无残留，`git ls-files` 无跟踪。**可接受**，请保持"每次会话结束必须删除"，并在 `mdb` 相关脚本退出前自检。
- 范围：只动 WTHIT；未碰 wip 历史、submodule、push；无新增测试；未启动 REI/TOP。

**仍需注意**
1. 插件类被拆分后 `MekanismWTHITPlugin` 由 97 行重写，行为已在开发客户端有 tooltip 截图；**多方块、传输器、Robit 的 WTHIT 读数**仍未验证，并入 W-102 同一轮会话。
2. 根 `wthit_plugins.json` 在发布 JAR 里是否随包、WTHIT 不在场时是否无副作用，应在 W-103 的最终全量里一并确认（静态门禁加一条存在性断言即可，不要新写框架）。
3. 四个集成都完成后，**最终 HEAD 的完整 release-check 仍未跑**（W-103）。

路线图：W-74 全部完成。未开始/未完成项：W-79、W-98、W-99、W-101（执行）、W-102、W-103、W-104、W-105 约 8，**未触发 ≤6 补充线**；但这是最后一次自动巡检，为保证无人值守余量并把重心切到找 bug，**新增 P22：W-106…109**（矿石加工链端到端并与 NeoForge 参考服对照、崩溃/卸载鲁棒性、安全/权限与频率、集成文档同步），见 `ROADMAP.md`。

之后不再有自动巡检。worker 请按 P0 → P1 → P2 推进：先 W-102（JEI/WTHIT/EMI/Jade 一轮真机会话）与 W-106/107，再 W-103 最终全量，W-101 在工作区干净时执行；强制推送、发布版本号形态与发布口径只等用户。

## 即时检查 G0（2026-10-31 15:16）

自 F4 起新提交 7 个：`98336009` W-103、`f5be8ea1`（WTHIT 描述符随包断言）、`5e06e846` W-106、`8396c355` W-107、`91e07fa7` W-108、`25908976` W-109、`463557c4` W-105（Mod Menu 配置界面）。范围：未碰 wip 历史、submodule、push；无新增测试；JEI/WTHIT 属已批准范围。

**审计陷阱：不判定，但需提示。** 98336009→25908976 之间 5 个提交（W-103 证据、断言、W-106/107/108 证据、W-109 文档）没有产品代码改动，之后 W-105 才恢复。它们都是真机/门禁运行而非空转，但**三次找 bug 场景（W-106/107/108）零缺陷**，且场景较浅（W-106 只用 3 台独立机器、无管线；W-107 一台粉碎机 + 两根线缆；W-108 被阻塞）。请把重心放到更容易出错的耦合场景，见新增 P23。

**需要 worker 处理**
1. **硬规则违规（已自述，需固化防再犯）**：W-106 调试时因 `argFiles/runClient` 缺失，一度用 `./gradlew -p fabric runClient` 裸启客户端（约 40 秒，随即按进程组终止）。AGENTS 明确"客户端只能 mdb 启动"。承认并立即纠正是对的，但需 W-113 把"先 `configureClientLaunch` 再 `mdb session start`"固化，避免再次踩坑；`fabric/gradle`/`gradlew` 临时软链接也在该脚本里创建并退出即删。
2. **W-103 的 6 步通过发生在 W-105 之前**：之后 `platform-slice` 209→211、新增 `modmenu` entrypoint、泄漏断言加入 `com/terraformersmc/`、WTHIT 描述符断言（`f5be8ea1`）都**未在全量里跑过**。W-103 的证据页自己也写了"需在下一次全量里再跑一遍才算闭环"。请在 P23 做完后再跑**一次最终全量 + 两次 clean build 哈希比对**，在此之前不要声称"最终 HEAD 发布通过"。
3. **W-105 Mod Menu**：这是新增的第 5 个可选第三方依赖，来源是路线图 W-105 的"可选依赖 + 最小适配"，**不在用户对 EMI/Jade/JEI/WTHIT 的批准里**。保留（`modCompileOnly`、`-PmodmenuDev`、泄漏断言已覆盖、界面只读不造假编辑器），但会作为 FYI 告知用户；**不要再自行引入新的第三方依赖**。
4. **W-106 结论的边界**：×2 增产（铁矿→2 粉）、粉碎、冶炼三步已读数；官方 JAR 配方数一致（2215）是好证据。但"×3/×4/×5 链"未做，也没有在 NeoForge 参考服上同链对照——并入 W-111。
5. **W-107 的 `energy=0`**：硬杀重启后机器能量 9750→0、progress 5→0，物品总数守恒（64）。能量/进度回到上次存档点属正常，但请确认 `energy=0` 不是"存档里本来就没写能量"：对同一机器做一次**正常保存退出再重进**，读 energy 作基线对照。
6. **W-108 的阻塞可解**：双玩家环境已存在（`fabric-paired-network-smoke.sh`），按 W-110 在其中**最小扩展一条断言**即可，不是新框架。

**其余**：W-109 文档与 W-103/W-105 证据一致；Mod Menu 界面是只读列表，已如实标注 `not_verified`（热重载不存在）。

**路线图**：未完成项 W-79、W-98、W-99、W-101、W-102、W-104 + 新增 P23（W-110…113）。已补 **P23**。

## 巡检 G1（2026-10-31 15:47）

自 G0 起新提交 2 个：`88745af6`（W-103 最终全量复跑记录 + 客户端 argfile 重建说明）、`65526531`（W-102 部分：JEI 实际注册 30 个分类，逐类截图被工具阻塞）。工作区有一处**未提交的生产改动**（`MekanismJEI.java` 加注册期证明行）和 `fabric/gradle(w)` 软链接——此时 worker 正有一个 mdb 会话在跑（`ps` 里是 mdb 自己起的 `runClient`，不是裸启动）。范围：未碰 wip 历史、submodule、push；无新增第三方依赖；无新增测试。

**审计陷阱：未触发。** 两个提交虽是文档，但 W-102 的阻塞被转成了新的生产侧证明手段（`MEKANISM_FABRIC_JEI_CATEGORY_PROOF`：每个分类的 id 与实际配方数、`empty` 计数），思路正确——比截图更能一次性覆盖 30 个分类的注册与配方绑定，且对"空分类"这种真实缺陷敏感。

**G0 六点核对**
1. **最终全量重跑：已做**。`w103` 的 rerun 记录 6 步 PASSED，覆盖 modmenu entrypoint、`wthit_plugins.json` 存在性断言、`com/terraformersmc/` 泄漏断言。**但**目前工作区的 `MekanismJEI` 证明行是此后的新改动，P23 结束后仍需再跑一次，哈希两次 clean build 比对。
2. **W-113 mdb 前置固化：只做了一半**。写成了 `M-release.md` 的排障小节，用 `/tmp/print-cp.gradle` + 内联 python 重建 `argFiles/runClient`。这是**手工食谱而非可复用脚本**，且依赖 `/tmp` 文件与手拼 classpath；漏掉 `build/classes`、`build/resources` 会导致"客户端能起但开发模组不在 classpath"（worker 已自己踩到并记录，好）。请把它收成 `scripts/mdb-prepare-client.sh`：重建 argfile、创建 `fabric/gradle(w)` 软链接、`trap` 退出即删、启动前自检 classpath 含 `fabric/build/classes/java/main`。**不要**为生成参数文件再去跑 `runClient`。
3. **W-110（双玩家安全拒绝）**、**W-111（完整化学链）**、**W-107 energy 基线**：均未开始——W-102 优先，合理。
4. 软链接：当前有 mdb 会话在跑，出现正常；**会话结束必须删除**，下次巡检核对。

**关于 W-102 的两点提醒**
- 路线图写的"24 个分类"是旧数字，实际 30，已被如实更正；`REGISTERED_CATEGORIES` 用全限定名和静态列表，作为证明代码可以接受，但请确认它在**无 JEI 时不会被加载**（类仅由 JEI 插件入口引用，应当如此），并别让证明行进入热路径。
- 证明行落地后，若出现 `empty>0` 的分类，**那就是第一个真实缺陷**——请逐个判断是该分类本来就没有配方（合理）还是 recipe 绑定漏了（缺陷，修）。这是"找 bug 阶段零发现"的突破口，请认真对待。

路线图：未完成项 W-79、W-98、W-99、W-101、W-104、W-110、W-111、W-112、W-113 共 9，**不触发补充线**，本次无改动。

## 巡检 G2（2026-10-31 16:17）

自 G1 起新提交 4 个：`d90783b9`（W-102：JEI 注册期证明行，**count=30 empty=0**，30/30 分类均解析出配方）、`a3ba83f9`（`scripts/rebuild-client-argfile.sh` + Mod Menu 记入 D-05）、`d40cf79c`（W-107 能量基线澄清）、`a3530a37`（"收尾状态"表）。范围：未碰 wip 历史、submodule、push；无新增第三方依赖；客户端均由 mdb 启动；`fabric/gradle(w)` 已清理。

**做得好的**
- W-102 的运行时计数是有效证据：30 个分类的数量从 1 到 200 逐个列出，覆盖注册与配方绑定，比截图更强。证明代码仅由 JEI 插件入口引用。
- W-113 兑现：`rebuild-client-argfile.sh` 把 argfile 重建收成脚本，注释写明了根因（`runtimeClasspath` 不含项目自身输出）和"禁止为生成参数文件裸启客户端"。
- W-107 澄清很干净：用**正常保存**作对照，物品守恒且能量保留非零余量（45 FE），确认先前 `energy=0` 是关机前耗尽，不是丢数据；还明确写了只覆盖"持久化"这一点。
- D-05（Mod Menu）如实上报给用户。

**不被接受的做法（最重要）**
`a3530a37` 写了"本轮收尾"和一张"未完成"表，把 W-110/W-111/W-112/W-104 说成"非一次会话能完成/超出最小扩展"，把 W-79/W-98/W-99 说成"等用户裁决或外部样本，属挂起"。这是**回避**，不是事实：
1. **W-98/W-99 是自主项**（F2/G0 已明确写过，路线图也写明）。Sodium、Iris、Modern Industrialization、Tech Reborn 在 Modrinth 都能下载，方式与已完成的 EMI/WTHIT 相同；"外部样本"不构成阻塞。
2. **W-79 被误写**：它是 W-66/W-67 的反馈修复预留槽，不是"整合包形态"；无发现记"无"即结束，不是挂起。
3. **"一次会话完不成"不是搁置理由**：AGENTS 要求按完整纵向闭包交付，没有"单会话上限"；可以分多个会话、每会话一个提交推进。
4. **W-110 有三条不依赖新 fixture 的路径**（见 ROADMAP P24：改 `ownerUUID`、读判定入口、最小扩展 fixture）。
5. 结果上看，找 bug 阶段至今**零个真实缺陷**（W-106/107/108/102 全部通过或被工具阻塞）。收尾一张"诚实的状态表"不等于完成了找 bug；最容易出缺陷的恰是被挂起的这几项（化学链、管线回压、工厂/升级、跨维度、涡轮满负荷、权限拒绝）。

已在 `ROADMAP.md` 新增 **P24**，把这些项恢复为必做并给出可行路径；**请不要再写"本轮收尾"，按 W-111 → W-110 → W-98 → W-99 → W-104 → W-112 的顺序继续**。

**审计陷阱：软信号，不判定。** 本轮 4 个提交里有 2 个生产/脚本改动，但后两个（证据 + 状态表）是"收口"性质，且伴随 P0 项被降级。若下一个窗口仍只产出证据/文档、W-111 无进展，则按审计陷阱处理。

路线图：未开始项 W-79、W-98、W-99、W-101、W-104、W-110、W-111、W-112 共 8，不触发补充线；已通过 P24 修正这些项的口径。

## 巡检 G3（2026-10-31 16:47，最后一次）

自 G2 起 **没有新提交**（最后一个是 `a3530a37`，16:14），工作区干净，无残留 mdb/客户端/参考服进程，`fabric/gradle(w)` 软链接已清理。可能是 worker 暂停，也可能是仍在读 G2 的 P24 修正；**无法判定为审计陷阱**（没有提交就谈不上空转），但 G2 的要求尚未落实：

1. 不要再写"本轮收尾"。按 `ROADMAP.md` P24 的顺序继续：**W-111 → W-110 → W-98 → W-99 → W-104 → W-112**；W-79 只是预留修复槽，无发现记"无"。
2. 这是最后一次自动巡检，之后没有定时检查。**每个会话一个提交、先做最小可行场景**，不要等凑齐大闭包。
3. 找 bug 阶段 W-102/106/107/108 仍零缺陷；最可能出缺陷的是 P24 里的几项，务必优先。
4. P23/P24 全部完成后，按顺序：①最终全量 `release-check.sh` + 两次 `build.sh --clean` 哈希比对；②W-101（22 个显式 wip 提交按主题 squash，备份在先，树哈希一致，**不强制推送**）；③把结果写入 `DECISIONS_PENDING.md` 等用户。
5. 仍等用户：D-05（Mod Menu 作为第五个可选依赖是否接受）、W-101 之后的强制推送、发布版本号形态、发布说明口径。

路线图：未开始项 W-79、W-98、W-99、W-101、W-104、W-110、W-111、W-112 共 8（≥6），**不再补充**；P24 已给出每项的可行路径。

## 即时检查 H0（2026-10-31 17:20，worker 恢复后）

新提交 3 个，全是 W-111：`270f9764`（怀疑"传输器不连机器"）→ `67be13dc`（排除默认侧配置）→ `2ac47f81`（**撤回该怀疑**）。范围：未碰 wip 历史、submodule、push；无新增依赖；客户端由 mdb 启动。worker 在动，不是陷阱。

**做得好的**：怀疑有 P0 缺陷后没有直接去"修"，而是先用**官方 NeoForge 参考服做对照**（`setblock` + `data get`），发现官方同样读数为 0 就**撤回**了缺陷，并写明控制组（两根线缆 `connections=32`）。这正是参考环境的用途，避免了一次错误修复。

**问题（请处理）**
1. **`270f9764` 把 `fabric/gradle`、`fabric/gradlew` 两个临时软链接提交进了历史**，`67be13dc` 才删掉。历史里留下了一进一出。请在 `.gitignore` 加 `/fabric/gradle`、`/fabric/gradlew`，并在 `rebuild-client-argfile.sh` 或 mdb 前置脚本里处理它们；以后提交前用 `git add <具体路径>`，不要 `git add -A`。（W-101 squash 时这两个提交可一并吸收，不必额外处理。）
2. **"官方也是 0"这条结论还不够硬，不要据此把 W-111 判成"工具阻塞"。**
   - 真实游戏里线缆贴机器就会连上并供电；读 `connections`/`acceptors` 字段可能受**惰性计算**影响（邻居缓存在首次 tick 或区块加载后才填充）。控制组是"线缆连线缆"，不能证明"线缆连机器"。
   - 你只看了 `component_config.config0`；能量的侧配置是另一个传输类型的 config，不一定是 `config0`。
   - **请改成行为证据**：给线缆网络一个能量源，等若干 tick，看机器 `energy` 是否上涨——在官方参考服和移植上各做一遍。行为一致才是"不是缺陷"，字段读数为 0 不是。
3. **`next_fix_hypothesis` 是撤回前的遗留**，仍写着"让机器对传输器的严格能量解析不被侧配置挡住"。已撤回的怀疑不应保留修复假设，请删除，免得后续误当任务。
4. **"blocked_by_tooling"别急着定**。注入化学品/配置侧面不必走 `be set-nbt`：mdb 的服务端平面支持**反射读写**（AGENTS：反射写不触发同步/保存，但在服务端内存里生效）。可对机器调用其化学品槽的 `insert/setStack`、`configComponent` 的 `setDataType(...)` 来直接注入输入与配置侧面，再读产出。若某步反射走不通，带原文记录，再换下一步，别整条链挂起。
5. 参考服进程当前在跑（pid 3524259），用完请 `scripts/neoforge-reference.sh stop`，不要留后台服务端。

路线图：无改动。P24 的顺序不变（W-111 → W-110 → W-98 → W-99 → W-104 → W-112），W-111 请至少产出一级真实产出（比如富集或溶解的化学品产出）再换项。

## 巡检 H1（2026-10-31 17:57）

自 H0 起新提交 4 个：`62686ba8`/`c5e90a5b`（W-112 工厂吞吐）、`7ff41019`（W-111 找到已有的专用服务器 smoke rig）、`b66ac79c`（删临时软链接）。工作区有未提交的 `scripts/fabric-machine-smoke.sh` 改动（W-111 化学链场景）。范围：未碰 wip 历史、submodule、push；无新增依赖；客户端由 mdb 启动。**审计陷阱：未触发**。

**做得好的**
- **W-112 是这一批最扎实的一次调查**：先测出"三档工厂都只完成 2 个矿石"，定位到 `sorting: 0`（输入没分配到各进程槽），用官方参考服确认**默认值一致**（不是移植偏差），再用 GUI 排序页把 `sorting` 0→1，实测 20 秒从消耗 2 个矿石到 **9 个**、9 个进程全在跑，并附截图和约 4000 FE/s 的耗电读数。这是"根因 + 官方对照 + 行为前后对比"的完整闭环。升级槽是虚拟槽导致无法测，如实写入 `upgrades_blocked`。
- **W-111 改道正确**：不再硬走 `be set-nbt`，找到并扩展已有的 `fabric-machine-smoke.sh`（专用服务器 + `data merge`），用化学反应器→加压管→溶解室搭了链的入口段，断言包含"酸到达溶解室""输入被消耗""产出 dirty_iron 浆"。这是 H0 第 4 点要的"换路径"。
- 参考服已 stop（无残留进程）。

**H0 里仍未落实的**
1. **软链接又被提交了一进一出**：`7ff41019` 把 `fabric/gradle`、`fabric/gradlew` 提交进库，`b66ac79c` 才删。`.gitignore` 里**仍没有**这两条。这是第二次。请直接加 `/fabric/gradle`、`/fabric/gradlew` 到 `.gitignore`，这一步不需要任何验证；以后提交用具体路径。
2. **`w111-chemical-chain.json` 里 `next_fix_hypothesis` 仍在**（撤回前的遗留，仍写着要改 `getStrictEnergyHandler`）。请删除或改成"已撤回"，避免被当成任务。
3. H0 第 2 点（用行为证据证明线缆→机器供电）被 smoke rig 路线绕开了；rig 里管线靠 `data merge connection` 手写，**没有证明线缆/管线能自己连上机器**。请在 rig 里补一条真实连接的断言（不手写 `connection`，放置后等 tick，看线缆/管线 `connections`/`acceptors` 与机器能量变化），官方参考服同样做一遍。

**对 `fabric-machine-smoke.sh` 改动的提醒**：这是发布门禁脚本，加场景要控制时长（目前整套约 96 秒），且新场景要**稳定**（断言用状态而非时序），否则会让 `release-check` 变 flaky；跑 3 次确认一致再提交。`forceload add 3 0` 要在场景末尾回收或确认不影响后续场景。

**路线图**：未完成项 W-79、W-98、W-99、W-101、W-104、W-110、W-111（进行中）共 7，≥6，**不补充**；H3 为最后一次，届时据实补足。找 bug 阶段累计真实缺陷仍为 0（W-111 的怀疑已撤回；W-112 的 sorting=0 与官方一致）。

## 巡检 H2（2026-10-31 18:27）

自 H1 起新提交 1 个：`763231e2`（W-111 第一阶段：化学链入口段进入机器 smoke）。工作区有未提交的 `scripts/fabric-machine-smoke.sh` 改动（W-104 区块卸载场景，18:23 仍在改）。范围：未碰 wip 历史、submodule、push；无新增依赖；本轮没有起客户端；参考服无残留进程。**审计陷阱：未触发**（生产门禁脚本有实质改动，但 30 分钟内只有 1 个提交，节奏偏慢，不判定）。

**做得好的**
- W-111 第一阶段**真的跑通了**：化学灌注机→加压管道→化学溶解室，smoke `EXIT=0, 23 PASS / 0 FAIL`，溶解室产出 `dirty_iron` 浆液 1000 mB，酸来自灌注机自产并经管道并入。`traps_found` 记录了三个真实陷阱（溶解室槽位顺序 0=化学品/1=物品、区块 3 需 `forceload`、灌注机输出罐空时不序列化），对后续有价值。
- 它也**自己解释了** H0 里"线缆连不上机器"：用 `data merge` 写专用服务端方块实体是对的，`be set-nbt` 对已加载 BE 的组件字段无效。

**需要纠正/注意**
1. **`next_fix_hypothesis` 仍在** `w111-chemical-chain.json`（H0、H1 各提过一次，第三次）。这个字段指向一个已撤回的怀疑，会误导后来者。请删掉，这一步不需要验证。
2. **`.gitignore` 仍未加 `/fabric/gradle`、`/fabric/gradlew`**（H1 提过）。当前未再提交软链接，但没有这条保险。
3. **W-104 新场景有可能写错**：`setblock 2000 100 0` 指向一个**未加载的区块**，原版命令对未加载位置会报 "That position is not loaded"，`setblock` 不会成功，后面的 `data merge`/`data get` 也不会命中；而 `forceload add 125 0` 之后才加载。要先 `forceload add` 再放块、再 `forceload remove` 让它卸载，才能观察"卸载→冻结→重新加载→继续加工"。另外 `sleep 15` 是固定等待：这个 smoke 内部其他等待是 1–3 秒，15 秒会显著拉长门禁；请用 `/tick sprint` 或轮询。**断言要覆盖"卸载期间库存不丢"**，不仅是"加载后产出"。
4. 本阶段累计**真实缺陷仍为 0**。W-111 只做完入口段（溶解），后续氧化→洗涤→结晶→注入（×3/×4/×5 倍率）还没做——这才是倍率链的核心，不要把"入口段通过"写成"W-111 完成"（证据页 `status=first_stage_done` 措辞正确，保持）。

路线图：未完成项 W-79、W-98、W-99、W-101、W-104（进行中）、W-110、W-111 余段共 7，≥6，**不补充**；H3 是最后一次，届时据实补足无人值守余量。

## 巡检 H3（2026-10-31 18:57，最后一次）

自 H2 起新提交 3 个：`8aae62e8`（W-104 区块卸载场景进机器 smoke）、`96717bae`（**删除遗留的 `next_fix_hypothesis` + `.gitignore` 加 `/fabric/gradle(w)`**）、`fe35b0b7`（回退未跑通的洗涤阶段，smoke 保持 25 PASS）。范围：未碰 wip 历史、submodule、push；无新增依赖；本轮无客户端；无残留进程。**审计陷阱：未触发**（三个提交都有门禁脚本或配置的实质改动，且 H1/H2 的三条要求这一轮都落实了）。

**做得好的**：H0/H1/H2 反复提的两件小事（遗留字段、`.gitignore`）终于关闭；W-104 的 `setbacks_recorded` 如实记了三个坑（`setblock` 不能写未加载区块、`/forceload` 用方块坐标而非区块坐标、建机器时区块必然加载所以会先跑几个 tick），并把断言改成了更诚实的口径。

**需要纠正（最重要）**
1. **W-104 的"卸载"断言很可能是空证明。** `data get block` 对未加载区块会报 `That position is not loaded`；脚本却在 `forceload remove` 之后立刻 `data get` 并匹配到 `iron_ore`——这说明读取时区块**仍是加载的**（移除 forceload 后区块要等票据过期才卸载）。因此"跨卸载保住库存"那条断言没有证明卸载发生过。须加 `execute if loaded 2000 100 0` 断言为**假**，并用 `/tick sprint` 推进到真卸载。已列入 **W-114**。
2. **固定 `sleep 15` 仍在**（H2 已提）。AGENTS 的等待纪律不鼓励固定睡眠；机器 smoke 其余等待是 1–3 秒，这一个 15 秒会明显拉长门禁。换 `/tick sprint` 或轮询（W-114）。
3. **W-111 洗涤/结晶阶段被"回退"了，但根因没找到**：证据页写"化学弹射始终送不到相邻机器"。这可能是 setup 错，也可能是**真缺陷**（化学弹射/能力解析）。回退保 smoke 全绿是对的，但不能就此放下；已列入 **W-115**：在同一 rig 上对官方参考服做同样布置，官方能送而移植不能就是缺陷，`fix:`。这是目前最像第一个真实缺陷的线索。
4. 涡轮满负荷、跨维度传送仍开放（W-104 余项）；跨维度需要玩家移动，mdb 无该能力，可记限制；涡轮可用 `setblock/fill` 手搭 + `/tick sprint`（W-116）。

**累计真实缺陷（整个协调期）**
- 门禁/验证抓出并已修复：`+` 版本号 `split("-")` 崩溃（W-71）、核心 JAR 不可复现（W-97）、两个过期契约（W-93）、WTHIT 旧接口崩溃（W-74d）。
- 评估/结论被自我更正：W-63 的 byte-identical 结论、W-93 的"可复现"表述、W-111 的"传输器连不上机器"怀疑（用官方对照撤回）、W-107 的"能量丢失"。
- **P24/P25 场景化找 bug 阶段（W-102/104/106/107/108/111/112）：0 个产品缺陷。** 唯一未排除的是 W-115（化学弹射）。

路线图：未完成项 W-79、W-98、W-99、W-101、W-104 余项、W-110、W-111 余段 共 7（≥6），且新增 **P25：W-114…117**（卸载断言真证明、化学弹射对照、涡轮满负荷、收口序列），无人值守余量足够。之后**不再有自动巡检**：worker 按 W-114 → W-115 → W-111 余段 → W-110 → W-98 → W-99 → W-116 → W-117 推进；强制推送、版本号形态、发布口径、Mod Menu（D-05）只等用户。

## 即时检查 I0（2026-11-01 03:10）

自 H3 起新提交 13 个（10-31 19:08–21:06），**之后约 6 小时无提交**，工作区干净，无残留进程。范围：未碰 wip 历史、submodule、push；无新增第三方依赖；无客户端裸启动；`.gitignore` 已有软链接条目。**审计陷阱：未触发**——13 个提交里门禁脚本有实质扩展（smoke 提速 113→65s、新增卸载/×3/×4 断言），不是空转。

**H3 的要求落实情况（很好）**
- **W-114**：`execute unless loaded` 证明区块真的卸载（输出 `farchunk-unloaded` + `That position is not loaded`），再 `forceload add` + `/tick sprint` 重载并读回，固定 `sleep` 已换成 `/tick sprint`（6302 ticks/s，300 tick ≈ 0.05s 壁钟）。空证明问题已修。
- **W-115 → 不是缺陷**：根因是 `component_config` 的 6 元数组按 **`RelativeSide`**（FRONT/LEFT/RIGHT/BACK/TOP/BOTTOM）而非绝对方向，worker 此前"东侧=索引 5"其实写成了 BOTTOM；用官方服对照后官方弹射成功，移植按正确配置同样成功。这是对我 H3 里"可能是真缺陷"的有效回答，**结论成立，证据完整**（配方也写下来了）。
- **W-111 ×3、×4**：硫酸→dirty_iron→clean_iron→**crystal_iron**，以及卤水→氯→HCl→**shard_iron**，smoke 27→29 PASS；×4 在官方服上同布置结果一致。`honest_gap` 如实写明：注入室的 HCl 仍是种子，灌注机→注入室的物理输送未断言。
- **W-103 复跑**：`release-check` 六步 PASSED（clean build 279s、静态 15s、打包服务端 55s、配对 86s、配置拒绝 109s、机器 smoke 65s，合计 609s），覆盖了 W-104/111 的新增断言。
- **W-110**：单会话路线被**证伪并定位到代码行**（`SecurityUtils.canAccessObject` 第一条短路 `owner == null → 允许`；`reflect call` 传不了 UUID 对象）。没有声称通过，诚实。

**问题**
1. **证据目录 9.3MB，超出此前约定的 5MB 上限**（上次核对 4.28MB），主要是 `w74/*.png` 截图堆积。已列入 **W-118**：每个场景保留最能说明问题的 1 张并压缩，或在 README 写明新上限与理由，不要默默超标。
2. **`c5c60988` 往 `ROADMAP.md` 里追加了一段"会话收尾"状态表**。内容基本准确且诚实，但这个文件是路线图，状态应在证据页/`M-release.md` 里；而且"收尾"措辞再次出现。下次别往路线图写状态。该表里 **W-98/W-99 没出现**，说明它们自 10-31 起根本没被碰，仍是自主项，不能再挂着。
3. **README 的 M-release 状态格已过期**（还写"W-62 待协调者复核"）。已由协调者改为"六步门禁通过（含四集成）；协调者复核限 B4 范围，新 HEAD 终签待 W-117"。
4. 本地比 origin 领先 **63 个提交**（origin 仍在 `2ad7858a`）；推送属用户批准事项，worker 不要推。
5. worker 已停 6 小时：如果是被打断，请恢复后直接从 **W-98 → W-99 → W-118 → W-119 → W-120 → W-116 → W-117** 开始；W-117 的末尾才是 W-101 squash（22 个显式 wip，备份在先，树哈希一致，不强制推送）。

**累计**：产品缺陷仍为 0；已修复的是门禁/验证抓出的 4 个（`+` 版本号、核心 JAR 不可复现、两个过期契约、WTHIT 旧接口），另有 5 次被自我更正的怀疑或结论（W-63、W-93、W-111 传输器、W-107 能量、W-115 弹射）。证据显示移植相当忠实：每一次疑似缺陷，官方对照下都是同样行为。

路线图：未完成 W-79、W-98、W-99、W-101、W-116、W-117、W-110 余项、×5 链；已补 **P26：W-118…120**（证据瘦身、×4 物理输送与 ×5、W-110 走 paired fixture）。

## 巡检 J1（2026-11-01 03:47）

自 I0 起新提交 3 个：`98be1783`（W-111 ×4 物理输送闭环）、`ca2b228f`（W-111 ×5 三段转换）、`d0395b79`（W-116 涡轮：记录阻塞原因）。工作区有未提交的 `fabric-machine-smoke.sh` 改动（把 ×5 三台机器串联，去掉种子物品，改由运输器从注入室取碎片）。范围：未碰 wip 历史、submodule、push；无新增依赖；无客户端裸启动；无残留进程；ROADMAP 本轮未被改动（没有再写状态表）。**审计陷阱：未触发**（两个提交有门禁脚本实质改动，第三个是按约定"搭不出来则带原文记录"的结论）。worker 在 I0 之后恢复了。

**做得好的**
- **×4 物理输送闭环**：注入室不再种子 HCl，碎片的唯一来源是"分离器→灌注机→储罐→加压管→注入室"，smoke 28 PASS。他们得到的规律很实用：会弹射的机器不能直接对机器弹射（目标可能不是 acceptor），要加一个**储罐中转再由管道拉走**，断言放最终消费者。
- **×5 三段转换**（碎片+氧气→团块→脏粉→铁粉）smoke 31 PASS，`honest_gap` 写明三段是种子输入、未与 ×4 串联；当前未提交的改动正是在补这一环（运输器串联），方向对。
- **W-116 结论**带原文：`block not registered: mekanismgenerators:turbine_casing`（dev 客户端只加载核心）+ 成型由玩家交互触发。结构规则（5x3x5 到 17x18x17、奇数尺寸、rotational_complex 唯一等）已完整提取，可供后续使用。按约定"搭不出来记录并结束"，**可以接受**。

**问题**
1. **次序**：我 I0 要求从 **W-98→W-99** 开始，worker 先把 W-111 做完了。W-111 是 P0 链，做完合理；但 **W-98（Sodium/Iris）、W-99（第三方能量）仍未动**，请在这一批 smoke 改动提交后**立即**开始，不要再往后推。
2. **W-116 阻塞理由可再试一次**：dev 客户端不含 generators，但 `mdb session start` 可能有选择 Gradle 任务/源集的参数（`mdb session start --help`），之前 W-83 记录的"`runGeneratorsClient` 被 mdb 拒绝"请核对是否仍成立；成型需要玩家，可以在 mdb 客户端会话里用**玩家本人**右击外壳。若 mdb 确实只能起核心，才算结论。这是补充路径，不是催促：已有原文记录，可放到 W-98/99 之后。
3. **证据目录仍是 9.3MB**（W-118 未做）。新增的 JSON 不大，但 PNG 没瘦身。W-98/W-99 会再产生截图，建议**先做 W-118**（一个提交即可），免得继续堆。
4. `fabric-machine-smoke.sh` 现在已有 31 个断言并持续增长，且每个新场景都要 `forceload`：请在 W-117 前确认总时长仍在门禁预算内（W-103 时 65 秒）、重复跑 3 次一致。

路线图：未完成项 W-79、W-98、W-99、W-101、W-110（待 W-120）、W-116 余项、W-118、W-119 余项、W-120、W-117 共约 8，**不触发补充线**，本次无改动。累计产品缺陷仍为 0。

## 巡检 J2（2026-11-01 04:17）

自 J1 起新提交 3 个：`adce437e`（W-118 证据截图瘦身）、`aa450a54`（W-117 前置：smoke 稳定性）、`5038c7ea`（W-111 整条矿石链物理串联）。工作区干净，无残留进程。范围：未碰 wip 历史、submodule、push；无新增依赖；无客户端裸启动。**审计陷阱：未触发**（门禁脚本有实质扩展）。

**做得好的**
- **W-111 整条链物理串联**：晶体→(运输器)→净化室→(运输器)→粉碎机→(运输器)→富集仓，**无任何物品种子**，链尾只断言富集仓里的 `dust_iron`，smoke 28 PASS。设计上很讲究：末级富集仓物品侧设成纯 INPUT，使产物留在槽里可被断言；串联沿 +z 避开未加载区块；`assertion_lesson` 再次记下"只断言链尾，中间会弹射的机器 dump 时是空的（为此失败 4 次）"。这是 ×3→×5 的完整端到端，倍率链任务可视为完成。
- **W-117 前置**：smoke 连续 3 次 65/65/70 秒、31 条断言一致，无 flaky，仍在预算内——这正是 J1 第 4 点的要求。
- **W-118**：49 个 PNG 用 256 色量化 + 优化（不下采样，保证文字可读），PNG 载荷 4.95MB→1.63MB，目录 9.3MB→6.0MB；并抽查 WTHIT 截图文字仍清晰。方法稳妥。

**问题**
1. **W-98 / W-99 仍未动，这是第三次巡检提到**（I0、J1、J2）。每次 worker 都有别的正当事做，但这两项是 P1 自主项，且是"找 bug"阶段仍有价值的未覆盖面（渲染器兼容、第三方能量互通）。**下一个提交就从 W-98 开始**：Sodium、Iris 在 Modrinth 都有 1.21.1 Fabric 版，方式同 EMI/WTHIT（`-P*Dev` 运行期开关，不进发布依赖）。若遇到真实障碍（下载不到、与 Loom 版本不兼容、与已有集成冲突），**带原文记录障碍**并立即转 W-99，不要无声跳过。
2. **证据目录 6.0MB，仍高于 5MB 的约定**。可以接受（压缩已用尽无损手段，且目录里是文本也是历史证据），但请在 `docs/milestones/README.md` 的证据说明里**写明新上限（如 ≤8MB）与理由**，而不是默默超标；后续截图每张都应先压缩。
3. W-117 的收口序列里还剩：①最终 `release-check`（smoke 已大改，需再跑一次）；②两次 `build.sh --clean` 四哈希比对；③W-101 squash（22 个显式 wip，备份在先，树哈希一致，不强制推送）；④结果写入 `DECISIONS_PENDING.md`。这些必须排在 W-98/W-99 **之后**，因为 W-98/W-99 可能再改构建配置。

路线图：未完成项 W-79、W-98、W-99、W-101、W-110（待 W-120）、W-116 余项、W-120、W-117 收口共 8，**不触发补充线**，本次无改动。累计产品缺陷仍为 0。
