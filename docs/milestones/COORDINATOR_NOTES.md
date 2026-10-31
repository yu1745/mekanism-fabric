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
