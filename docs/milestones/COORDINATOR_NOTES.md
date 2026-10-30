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
