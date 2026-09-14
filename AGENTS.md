# AGENTS.md — Mekanism Fabric Java 移植约束

## 目标与源码边界

将官方 Mekanism `v1.21.1-10.7.19.85` 从 NeoForge 移植到 Fabric（Minecraft 1.21.1、Java 21、Mojang official mappings）。保留官方 `mekanism.api.*`、`mekanism.common.*`、`mekanism.client.*` 包名、公共行为、领域模型、注册 ID、NBT、配方与存档语义。

- API/common/platform 分别由 `fabric/api-slice.txt`、`fabric/common-slice.txt`、`fabric/platform-slice.txt` 精确纳入。不得复制源码、改包名、加入 Kotlin，或为消除编译错误无审计地扩大清单。
- 不实现通用 Forge/Fabric 转换层，不伪造 `net.neoforged.*`，不建立 Crusher 专用替代机器框架。
- 路线/状态见 `docs/FABRIC_PORT.md`、`docs/PORT_MATRIX.md`；平台差异与验收见 `docs/API_DELTAS.md`、`docs/FABRIC_{REGISTRY,CONFIG,SMOKE}.md` 及任务对应专题文档，按需读取。

## 安全与参考源码

仅在任务相关具体目录内用 `rg`/`fd` 搜索；禁止扫描 `~`、`/`、`~/server` 或宽泛父目录。禁止使用 zvec-grep、其搜索工具或建立/更新索引。开发任务不包含生产、镜像服、部署或 push 操作。

以下 `.gitignore` 目录只供参考，不是编译/运行依赖；不得修改、生成构建产物或提交其中内容：

| 路径 | 固定身份 | 用途 |
|---|---|---|
| `upstream/neoforge-minecraft/` | MC `1.21.1` / NeoForge `21.1.200` / NeoForm `1.21.1-20240808.144430`；patched-source SHA-256 `a2003718973f1b772e1b7a1c8527bd2b19a86aa938104a77515e8140ca865ce1` | 核对 patched Minecraft 新增接口/方法/语义 |
| `upstream/kilt/` | `KiltMC/Kilt` `version/1.21.1`，commit `9e8f0d67411f10234bf0d864bd151a6dbcef14b4` | NeoForge→Fabric 行为参考；LGPL-2.1 |
| `upstream/connector/` | `Sinytra/Connector` `1.21.x`，commit `8b27f1ad042aae8037bcc522b321c03fcce1a12a` | Fabric→NeoForge 反向参考；MIT |

patched Minecraft 仅保留 5,363 个 `com/mojang/**/*.java`、`net/minecraft/**/*.java`，来源见该目录 `SOURCE.txt`；禁止复制或修改 Minecraft 源码。参考版本只能显式更新，并同步本表；patched Minecraft 更新还须重建 `SOURCE.txt`、核对文件数。

## 平台映射

依次判断 vanilla 类型/能力 → Fabric API → patched Minecraft → Kilt → Connector，基于实际行为实现最小 Mekanism 专用 adapter，补平台差异测试。参考项目不是必须照抄的架构，也不默认加入依赖；直接采用代码须记录仓库、commit、文件路径并遵守许可证。

重点核对 EMPTY/null、amount 单位、side/context、simulate/execute/transaction、缓存失效、区块卸载、线程/owner 生命周期、注册冻结和 codec/NBT/packet 顺序。

## 工作组织与任务定界

- **主代理选路并理解边界，单代理实现。** 派发前给出基线 commit、具体源码入口/调用方、真实 owner、允许的 delta、非目标、可执行验收、已知依赖和未决问题。不得将开放选题、全局路线比较与完整实现一起下放。
- **不设 production 文件数目标或硬上限。** 按完整模块或 ownership/语义边界纳入直接前置与立即调用方；不凑无关代码，不因数量回避主干，不为拆小而截断公共行为或引入临时 holder、重复 adapter、synthetic seam。只有可独立验证/发布且 ownership 清楚的边界才拆分。
- 遇到平台/事务/线程等风险先证明该边界，再完成所属纵向闭包；确实无法建立 owner、必须截断行为或依靠 fake/no-op 才能继续时，报告具体 blocker。
- 规划约 **10–15分钟**设检查点，交出 GO、具体 blocker 或待裁决方案；无结论先交接，不无限换候选，也不为到点而强行接受。实现中发现新语义问题同样回报主代理裁决。
- 复用已验证的依赖事实、官方 consumer 编译 probe 和失败 fixture。扫描须区分真实类型/调用、字符串/注释、常量与生命周期 owner；递归闭包数字不独立构成阻断依据，不每批重造扫描器。
- 同时只运行一个实现者，不复制完整候选。主代理可并行准备下一任务或审查固定 commit；不得只采信代理总结。提速靠减少重复规划、返工和手工同步，不未经验证削减门禁。

## 模型与监督

- **实现模型按用户最新规则（2026-09-14）选择，持续跨 Session 生效。** 常规默认 GLM5.3；所有计费时间按 **UTC+8** 计算，每日 **14:00（含）–18:00（不含）GLM5.3 为3倍计费，此时常规实现切到 CC 的 DeepSeek V4.1 Flash**。CC 的 DeepSeek V4.1 Flash **不存在峰谷计费，全天可用**；不得给它套用峰谷限制。每次派发前核对当前 UTC+8 时间。
- **多轮修复仍不佳时，临时切换 Astra 实现修复。** 当前实现者须先安全保存提交并交接、终止后再启动 Astra，保持单实现者。Astra 修好后，**下一个实现子代理恢复常规模型选择**（非高价时段 GLM5.3，高价时段 CC DeepSeek V4.1 Flash），不能把 Astra 变成永久默认，也不能无视反复修复失败而一直使用 GLM5.3。
- 实现代理从 clean commit 在隔离 worktree 工作，派发时明确范围、交付物和检查点；已有未完成实现须先保存并交接，不丢弃进度或并行启动第二个实现者。
- 每个实现子代理启动后立即开启 **270秒 supervision watch**，GLM5.3、Astra 与 DeepSeek 同样适用。只纠正方向、范围、验收漂移或风险操作；不因沉默或一次可恢复错误干预其自修复。
- 按上述规则选定的模型不可用或明确报告额度耗尽时，保存进度、暂停实现与新代理派发，告知用户并等待裁决；除用户已授权的上述切换外，不静默换模型，不用 sleep 或后台等待。
- 独立只读审查不再作为每个高风险批次的强制前置；仅在具体重大语义分歧阻碍实现、存在明确数据损坏风险或用户要求时调用。审查聚焦生产缺陷，不以测试缺口扩张任务。实际发生的审查保留简要证据。

## 测试与验收

**用户最新优先级（2026-09-13）：完成度优先，显著降低测试权重。** 优先接通官方机器主干与实际可用功能，不为测试完整性、fixture、模型评审或验收文档反复延长任务。此规则优先于旧任务计划中的强制全套门禁。

现有测试保留，不因本次降权删除。普通批次默认只做编译/构建与直接覆盖本次高风险改动的少量现有 focused tests；不以补齐测试矩阵为交付前提。已发现的具体生产缺陷仍需修复，但不得借机扩展为新一轮全面验错。

- 有可执行的 byte-identical 官方测试时优先直接纳入精确 oracle；新增 Fabric-local tests 聚焦平台差异、独立风险和层间连接，不随 production 文件数线性增加。
- 每闭包通常新增 **3–6个 tests**；超过8个前逐项说明超额测试独立捕获的 production mutation，记录于实现评估。高风险边界可有据突破目标；测试源码不设行数限制。
- 下层负责排列组合，上层 bridge 通常只补 happy path 与平台特有边界。不重复精确输出已蕴含的派生断言，不测试 vanilla/JDK 自身行为。
- **普通批次门禁：** 主代理检查真实 diff，确认构建与必要 focused checks；不再要求实现者与主代理各跑全套测试，不默认启动专门 server/client fixture、packaged smoke 或 clean release。
- **功能里程碑门禁：** 完整测试、真实运行和发布物核验集中到机器真正可用、发布等明确里程碑，或用户明确要求时执行。优先一个能证明实际功能的端到端检查，不为了边界完备性持续增建测试基础设施。记录已验证范围与未验证风险即可，不冒充通过。

```bash
./build.sh [--clean] [--out <dir>]    # 统一 Fabric 发布契约
./fabric-build.sh                   # allowlist 开发构建
./gradlew -p fabric verifyApiSliceNoNeoForge
scripts/scan-platform-deps.py        # 平台依赖清点
scripts/fabric-packaged-smoke.sh     # 最终 remapped JAR 干净服务端门禁
scripts/fabric-paired-network-smoke.sh
scripts/fabric-configuration-rejection-smoke.sh
```

后两项分别验证两个顺序新 client JVM 的成功握手，以及错版/同版本显式拒绝/缺失 receiver 的拒绝；需 Linux、systemd user、Xvfb。其他风险场景按对应专题文档选择。

## 持久记录

- **每完成一个大任务（实现、审查与最终验收闭环）后，主动评估是否切换上下文窗口，即使上下文尚未满。** 根据已完成任务的历史负担、下一任务边界及当前状态是否便于恢复决定，不以剩余 token 充足为理由跳过评估，也不要求每批机械滚窗。
- 决定滚窗时，先将长期结论与验收证据落入仓库并提交，再精简 Notes，仅保留当前请求、必要历史定位、运行中代理/任务、未决问题与下一动作；成功 checkpoint 后再切换。滚窗不改变用户的持续工作指令，也不取消运行中的代理；恢复后从仓库与短期 Notes 接续。

仓库文件与 Git 是长期任务的权威：决定、用户策略、状态、依赖证据、验收和恢复步骤必须落入对应文档/测试/脚本并提交，不能只留聊天或代理输出。Notes 只存当前 Session 临时状态（代理ID、未落盘发现、下一动作）；到达任务边界/暂停点前持久化，随后 Notes 仅留必要指针。新 Session 应只依赖仓库恢复项目状态，不把 Notes 当 changelog 或文档副本。
