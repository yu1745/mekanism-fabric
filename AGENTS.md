# AGENTS.md — Mekanism Fabric Java 移植约束

当前生效规则的精简版；指令演变与被取代条文见 `docs/POLICY_HISTORY.md`，历史任务文档在 `docs/archive/`。

## 目标与源码边界

将官方 Mekanism `v1.21.1-10.7.19.85` 从 NeoForge 移植到 Fabric（Minecraft 1.21.1、Java 21、Mojang official mappings）。保留官方 `mekanism.api.*`、`mekanism.common.*`、`mekanism.client.*` 包名、公共行为、领域模型、注册 ID、NBT、配方与存档语义。

- **开发中版本：** 移植尚未发布，较早移植提交与较晚提交间不要求存档/网络兼容；旧移植数据与代码可直接丢弃，不写迁移或拒绝旧开发版的代码。不免除对官方版本行为、注册 ID、NBT、配方、存档语义的保留。
- API/common/platform 分别由 `fabric/api-slice.txt`、`fabric/common-slice.txt`、`fabric/platform-slice.txt` 精确纳入。不得复制源码、改包名、加入 Kotlin，或无审计地扩大清单。
- 不实现通用 Forge/Fabric 转换层，不伪造 `net.neoforged.*`，不建立 Crusher 专用替代机器框架。
- 状态与路线：`docs/FABRIC_PORT.md`、`docs/PORT_MATRIX.md`（止于 Task049 的历史叙事与平台依赖清点；050+ 状态以 `docs/milestones/README.md` 为准）；平台差异 `docs/API_DELTAS.md`、`docs/FABRIC_{REGISTRY,CONFIG,SMOKE}.md` 及专题文档，按需读取。**里程碑与状态表见 `docs/milestones/README.md`，执行规划见 `docs/milestones/ROADMAP.md`**（Task052 已关闭为历史名，不再新开 `TaskNNN`）；进行中的活文档位于 `docs/milestones/M-*.md`，已完成的归档到 `docs/archive/`。

## 安全与参考源码

仅在任务相关具体目录内用 `rg`/`fd` 搜索；禁止扫描 `~`、`/`、`~/server` 或宽泛父目录。禁止使用 zvec-grep 及其索引。开发任务不包含生产、镜像服、部署或 push 操作。

以下 `.gitignore` 目录只供参考，不是编译/运行依赖；不得修改、生成构建产物或提交其中内容：

| 路径 | 固定身份 | 用途 |
|---|---|---|
| `upstream/neoforge-minecraft/` | MC `1.21.1` / NeoForge `21.1.200` / NeoForm `1.21.1-20240808.144430`；patched-source SHA-256 `a2003718973f1b772e1b7a1c8527bd2b19a86aa938104a77515e8140ca865ce1` | patched Minecraft（NeoForge 视角）；只含 MC 自身包，不含 `net.neoforged.neoforge.**` 本体 |
| `upstream/fabric-minecraft/` | MC `1.21.1` / Fabric（Loom `1.13.6` + Vineflower）；5363 个 `*.java`；见 `SOURCE.txt` | 同上，Fabric 视角；与上一份互相对照 |
| `upstream/kilt/` | `KiltMC/Kilt` `version/1.21.1`，`9e8f0d67411f10234bf0d864bd151a6dbcef14b4` | NeoForge→Fabric 行为参考；LGPL-2.1 |
| `upstream/connector/` | `Sinytra/Connector` `1.21.x`，`8b27f1ad042aae8037bcc522b321c03fcce1a12a` | Fabric→NeoForge 反向参考；MIT |

patched Minecraft 仅含 `com/mojang/**`、`net/minecraft/**` 共 5,363 个 java；禁止复制或修改。参考版本只能显式更新并同步本表（更新 patched MC 须重建 `SOURCE.txt`、核对文件数）。

对照须知：两棵树目录对齐、同类同路径，但**是参考存档不是 diff 对象**——排版（空格 vs tab、javadoc）、局部变量名（混淆名 vs 重命名）、客户端注解（`@OnlyIn` vs `@Environment`）不同；两份都已烘入本项目的访问放宽（`mekanism.accesswidener` / `accesstransformer.cfg`），可见性与 vanilla 不同（如 `ShapedRecipe.pattern` 为 `public`），勿据此推断 vanilla 语义。

## 平台映射

依次判断 vanilla 类型/能力 → Fabric API → patched Minecraft → Kilt → Connector，基于实际行为实现最小 Mekanism 专用 adapter。参考项目不默认加入依赖；直接采用代码须记录仓库、commit、文件路径并遵守许可证。

重点核对 EMPTY/null、amount 单位、side/context、simulate/execute/transaction、缓存失效、区块卸载、线程/owner 生命周期、注册冻结和 codec/NBT/packet 顺序。

## 工作方式（当前生效）

- **自主工作、不向用户提问**（2026-10-30）。超出已授权范围的事项记录到任务文档，暂停该分支，继续已确定范围。
- **单一实现者**负责选路、实现与验收闭环：确定基线 commit、真实 owner、允许的 delta、非目标、验收。可并行派发只读子代理审查**已提交**的真实 diff；子代理不得修改文件/提交，结论由主代理复核后串行修复。
- **按完整纵向闭包交付，不碎片化**：按模块/ownership 纳入直接前置与立即调用方，不设文件数上限，也不为拆小截断公共行为或引入临时 holder、重复 adapter、synthetic seam。区分真实行为 owner 与类型/常量引用，不见引用就展开整个新模块。必需的新模块会明显扩大边界时，先记录调用链与最小边界并暂停该分支。
- **收敛优先**：优先修通已纳入的机器、组件、注册、容器、能力主干，以整体构建/实际 owner 接通为进度依据。禁止删除公共行为、fake/no-op、重复框架、排除必要源码取得假绿。连续补叶子而主干不前进时，重估批次边界与上下文交接。
- 检查点约 10–15 分钟，记录 GO、具体 blocker 或待裁决方案；发现新语义问题先落文档。
- 复用已验证的依赖事实与 consumer 编译 probe；扫描须区分真实类型/调用与字符串/注释/常量，不每批重造扫描器。

## 审查

- 每次移植提交后，按 [移植风格审查 Prompt](docs/PORT_COMMIT_REVIEW_PROMPT.md) 审查该提交真实 diff；**结论写入对应任务文档小节或提交信息，不单独提交**。发现偏离在该提交行为边界内修复并复查，不借审查扩大范围。wip 提交在合并/收尾前 squash。
- 独立只读审查仅在重大语义分歧、明确数据损坏风险或用户要求时调用；审查聚焦生产缺陷，不以测试缺口扩张任务。

## 测试与验收

完成度优先，不主动增加测试覆盖，避免过度防御编程（2026-09-13 / 10-30）。现有测试保留；普通批次只做编译/构建与直接覆盖高风险改动的少量现有 focused tests。新增测试仅在另获授权时进行，聚焦平台差异与层间连接，不测试 vanilla/JDK 自身行为。有 byte-identical 官方测试时优先纳入。

完整测试、packaged smoke、真实运行和发布物核验集中到功能里程碑或用户明确要求；优先一个端到端检查证明实际功能。记录已验证范围与未验证风险，不冒充通过。

```bash
./build.sh [--clean] [--out <dir>]    # 统一 Fabric 发布契约
./fabric-build.sh                     # allowlist 开发构建
./gradlew -p fabric verifyApiSliceNoNeoForge
scripts/scan-platform-deps.py         # 平台依赖清点
scripts/fabric-packaged-smoke.sh      # 最终 remapped JAR 干净服务端门禁
scripts/fabric-paired-network-smoke.sh
scripts/fabric-configuration-rejection-smoke.sh   # 后两项需 Linux、systemd user、Xvfb
```

## 客户端调试（mcdebug）

优先用 mcdebug 取得真实 GUI、渲染、交互与客户端状态证据，不凭空推演；加载 `mcdebug` skill，参数以子命令 `--help` 为准。

- CLI：PATH 上的 `mcdebug`/`mdb`，否则 `java -jar /home/wangyu/server-v2/develop/mcdebug-wt-121/mcdebug-cli.jar`；CLI 与游戏内 mod 版本须匹配。游戏目录 `fabric/run`，先 `status` / `gui status --brief` 确认目标，不因 socket 文件存在就认定 JVM 存活；同目录不起第二个客户端，不手动删 socket/lock。
- 服务端写入后先 `gui settle` 再看客户端；槽位以当前菜单 index 为准；客户端 BE 不持完整库存，权威库存读服务端；反射读客户端须 `--client`，反射写不触发同步/保存；`inv.*` 仅限 vanilla Container。
- 方法体改动可 `gui hotswap`；字段/构造器/生命周期变化需重启。以真实命令结果判定成功。
- 复用现有隔离调试世界，少量真实功能检查；结束时恢复临时方块/物品/配置，保存并关闭自己启动的会话，清理临时 debug mod。mcdebug 依赖不进入发布依赖或源码切片。

## NeoForge 参考环境（文件常驻）

- 官方 NeoForge 版 Mekanism 的比对文件常驻在 `~/.cache/mekanism-neoforge-reference/`（约 200MB）：NeoForge `21.1.200` 服务端文件 + 官方 `Mekanism-1.21.1-10.7.19.85.jar`（Modrinth，sha256 `004dbc9f3106f4d192aeaa1ee1190dd16ec9ca8059ed3d093b80034f4c574f43`）+ W-65 样本世界。**只落盘，不常驻进程**；不进仓库、不进入发布依赖或源码切片。
- 用 `scripts/neoforge-reference.sh start|stop|status|logs|install`：需要 NBT／存档／行为对照时 `start`（端口 25594），用完 `stop`；默认动作是 `status`，不要留下后台服务端。文件不要删除，可长期复用，不必重复下载安装。
- 参考端只用于服务端侧比对：命令经 `~/.cache/mekanism-neoforge-reference/server.log` 回读；要改世界就把样本复制到别处再改，保持常驻世界干净。样本世界的生成命令见 `docs/milestones/evidence/p8-release/w65-neoforge-save-probe.json`。

## 任务等待纪律

- **会自己退出的任务**（`gradle build`/`test`、`build.sh`、单次编译、smoke 脚本本体）：直接执行（可后台）并阻塞取结果，用退出码和输出判断；不要 `sleep` 轮询，也不要套标记行等待。
- **不会自己退出的进程**（服务端、客户端、游戏内 smoke）：用 `scripts/wait-for-log-line.sh [-t ≤60s] [-n 次数] [-p 进程] [--fail-on 正则] 标记正则 日志...` 等既定标记行（`*_PROOF`、`PASSED/FAILED`、`Done (`）。0=命中即返回；3=超时、4=进程已退出、5=命中失败标记，**3/4/5 一律按失败处理**。
- 单次调用 ≤60 秒；需要更久就由 agent 自己再次调用该脚本续等，不用 `sleep`，也不要求给 gate 脚本加分片循环。
- 有写日志的进程就传 `-p`；已知崩溃/端口占用/拒绝签名用 `--fail-on` 提前退出。
- 失败先抓首条错误原文，不先推演再翻日志。

## 持久记录与上下文

仓库文件与 Git 是长期状态的权威：决定、用户策略、验收和恢复步骤落入文档/脚本并提交；Notes 只存当前会话临时状态。每个大任务闭环后评估是否切换上下文窗口：先把结论与证据提交，再精简 Notes（仅留当前请求、运行中任务、未决问题、下一动作），成功 checkpoint 后切换；新会话只依赖仓库恢复。
