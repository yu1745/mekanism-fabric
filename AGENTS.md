# AGENTS.md — Mekanism Fabric Java 移植约束

## 项目目标

本分支将官方 Mekanism `v1.21.1-10.7.19.85` 从 NeoForge 定向移植到 Fabric（Minecraft 1.21.1、Java 21）。保留官方 `mekanism.api.*`、`mekanism.common.*`、`mekanism.client.*` 包名、领域模型、注册 ID、NBT、配方和存档语义；不再做 Kotlin 全量重写。

迁移方案与门禁见：

- `docs/FABRIC_PORT.md`
- `docs/PORT_MATRIX.md`
- `docs/API_DELTAS.md`
- `docs/FABRIC_REGISTRY.md`
- `docs/FABRIC_CONFIG.md`
- `docs/FABRIC_SMOKE.md`

## 持久记录边界

- 仓库本地文件与 Git 历史是长期任务的唯一持久记忆。架构决定、用户策略、已完成/阻断状态、依赖证据、验收结果、模型评估、后续边界和恢复步骤，必须写入对应 `AGENTS.md`、`docs/`、测试、脚本或提交记录；不得只存在于会话 Notes、聊天历史或代理输出中。
- Notes 只允许保存当前 Session 内跨 context window 所需的短期状态，例如正在运行的代理 ID、尚未落盘的临时发现和紧接着要执行的动作。到达规划结论、任务边界、暂停点或 Session 可能结束前，必须先把有长期价值的内容落入仓库文件并提交；落盘后 Notes 只保留路径/commit 指针。
- 不把 Notes 当 changelog、长期任务数据库或仓库文档副本。新 Session 应只依赖仓库即可恢复完整项目状态；若做不到，视为本次记录工作未完成。

## 本地只读参考源码

以下目录被 `.gitignore` 排除，不进入本仓库提交，也不是运行时依赖：

| 相对路径 | 上游与版本 | 当前参考身份 | 用途 |
|---|---|---|---|
| `upstream/neoforge-minecraft/` | Minecraft `1.21.1`，NeoForge `21.1.200`，NeoForm `1.21.1-20240808.144430` | patched-source archive SHA-256 `a2003718973f1b772e1b7a1c8527bd2b19a86aa938104a77515e8140ca865ce1` | 直接查看 NeoForge patch 后的 `com.mojang.*` / `net.minecraft.*` 源码，识别 vanilla 类中新增的接口、方法和语义 |
| `upstream/kilt/` | `KiltMC/Kilt` `version/1.21.1` | commit `9e8f0d67411f10234bf0d864bd151a6dbcef14b4` | 查看 (Neo)Forge API、对象和生命周期如何在 Fabric 上承接 |
| `upstream/connector/` | `Sinytra/Connector` `1.21.x` | commit `8b27f1ad042aae8037bcc522b321c03fcce1a12a` | 从反方向查看 Fabric API 到 NeoForge 的对象转换与调用语义 |

`upstream/neoforge-minecraft/` 是匹配本项目 NeoForge 基线的 patched-source JAR 直接解压结果，只保留 5,363 个 `com/mojang/**/*.java` 与 `net/minecraft/**/*.java`；本地来源、过滤条件和版本同时记录在该目录的 `SOURCE.txt`。它用于核对 patch 后 Minecraft 表面，禁止作为编译依赖，也不要复制或修改 Minecraft 源码。

仅在任务相关的具体子目录内使用 `rg`/`fd` 搜索；禁止扫描 `~`、`/`、`~/server` 或宽泛父目录。所有参考目录默认只读，不要在其中改代码、生成构建产物或提交。需要更新参考版本时必须显式执行；同时更新本表身份，patched Minecraft 还须重建 `SOURCE.txt` 并核对 Java 文件数量。

## 平台 API 映射规则

遇到 NeoForge/Fabric 无明显一一对应的 API 时：

1. 优先判断是否可直接使用 vanilla Minecraft/Mojang 类型；
2. 再寻找 Fabric API 的直接能力；
3. 在 `upstream/neoforge-minecraft/` 查看目标 vanilla 类被 NeoForge patch 后具体增加或改变了什么；
4. 在 `upstream/kilt/` 查看 NeoForge→Fabric 的实现与边界；
5. 在 `upstream/connector/` 查看 Fabric→NeoForge 的反向映射；
6. 基于上述行为实现最小的 Mekanism 专用 adapter，并补语义测试。

Kilt 与 Connector 是参考实现，不是必须照抄的架构。本项目不实现通用 Forge/Fabric 转换层，不批量伪造 `net.neoforged.*`，也不默认把二者加入依赖。若直接采用具体代码，必须在实现或提交中记录来源仓库、commit、文件路径并遵守许可证；Kilt 为 LGPL-2.1，Connector 为 MIT。默认做法是参考行为和测试后独立实现。

特别核对：对象空值/EMPTY 语义、amount 单位、side/context、simulate/execute 与 Fabric transaction、缓存失效、区块卸载、线程切换、注册冻结、codec/NBT/packet 顺序。

## 当前构建入口

```bash
./build.sh [--clean] [--out <dir>]      # 当前 Fabric 发布物统一契约
./fabric-build.sh                       # Fabric allowlist 开发构建
./gradlew -p fabric verifyApiSliceNoNeoForge
scripts/scan-platform-deps.py
scripts/fabric-packaged-smoke.sh        # 最终 remapped JAR 的干净服务端门禁
scripts/fabric-paired-network-smoke.sh  # 隔离 Loom server + 两个顺序新 client JVM 的成功配置握手门禁（需 Linux/systemd user/Xvfb）
scripts/fabric-configuration-rejection-smoke.sh # 错版/同版本显式拒绝/缺失协议 receiver 客户端的拒绝门禁（同上环境要求）
```

## 测试增长约束

现有测试不得仅因数量或行数增长而消融；只有发现测试本身错误、失效或明确重复且用户另行要求清理时才删除。以下约束只适用于后续新增测试：

- byte-identical 官方闭包已有可执行官方测试时，优先直接纳入精确官方 oracle；Fabric-local 测试只补官方未覆盖的平台差异、关键边界或新层间连接。
- 每个新闭包默认新增 **3–6 个 Fabric-local tests**；超过 **8 个 tests** 前必须逐项说明每个超额测试能独立捕获的 production mutation，并将理由写入模型实现评估。无法证明独有价值的测试不得加入。测试源码不设行数限制，避免为压缩行数投入额外设计时间或牺牲可读性。
- 已完整测试的下层负责排列组合；上层 bridge 通常只保留一个 happy path 和一个平台特有边界，不重复下层全部算术、方向或状态矩阵。
- 精确输出已经蕴含的长度、唯一性、范围等派生断言不重复添加；不为 Mekanism 测试 vanilla/JDK 自身行为。
- platform adapter、transaction/simulation、序列化兼容、registry freeze、config 原子发布、network handshake/rejection、true-long/overflow、patched-Minecraft 语义替换与打包隔离可按独立风险突破默认目标，但仍须保持最小并记录理由。

## 任务批次与候选门禁

- 为提高整体移植速度，默认按完整 dependency/ownership vertical 组成约 **15–40 个紧密相关 production files** 的批次；不再把 4–12 个文件作为常规目标。应主动一并纳入同一主路径上的直接前置、owner 与立即调用方，而不是为了保持小 diff 反复停在外围 foundation。
- 不为凑数量混合无关代码，也不只因闭包较大就回避主干。超过 40 个文件时，只要它们属于不可诚实拆分的 registry、配置、事务、序列化、机器或生命周期闭包，且能定义分阶段实现和统一的可执行验收，也允许作为一个任务；拆分必须沿真实 owner/生命周期边界，而不是沿文件数。
- 遇到 NeoForge/Fabric 语义分歧、registry lifecycle、transaction/rollback、线程或 owner 生命周期边界时，先隔离并证明该风险边界，但默认继续完成其所属纵向闭包。只有无法建立真实 owner、不得不截断 public behavior 或使用 fake/no-op 实现时才阻断整批。
- 大批次测试按独立风险和层间连接设计，不按 production 文件数线性增加；复用已验证下层的排列组合，上层集中覆盖 owner lifecycle、平台差异和端到端连接。候选实现默认验证 source identity/delta、slice/fixture isolation、focused 与 full local unit tests、Fabric build 及静态 JAR class/leakage contract；不再由每个候选重复启动真实 dedicated server 或执行 clean release contract。
- 主代理在审查并选定/综合真实 diff 后，独立重跑完整测试，再对最终实现各执行一次 `scripts/fabric-packaged-smoke.sh` 和 `./build.sh --clean`，核对最终 commit、JAR 数量与 sha256。高风险网络/注册/启动任务可在任务验收中显式恢复候选级真实运行门禁。

## 双 GLM 实现试行与风险审查

出于 SOL 成本考虑，从 Task 041 起试行连续 **三轮完整开发任务**的双候选策略：每轮让两个 **GLM 5.3 high/max** 从同一 clean commit 在隔离 worktree 中独立实现，互不参考；主代理比较真实 diff、source identity、门禁结果与非声明，选择较强候选为基底并只按需移植另一候选中可独立证明更好的实现或测试，禁止机械合并。Task 041 为第一轮。每轮必须在 `docs/MODEL_IMPLEMENTATION_COMPARISONS.md` 记录两候选的成本、完成度、缺陷与最终取舍。

三轮后由主代理按一次通过率、范围/语义遵循、独立门禁质量、必要返工量、合并额外复杂度和总成本判断效果；若双候选没有稳定提高质量或降低主代理修复风险，则停止复制实现，后续仍改为每任务只派一个 GLM 5.3 high/max。默认不再使用 SOL 实现，除非用户再次明确要求。主代理始终必须检查真实 diff、补足遗漏并独立运行最终门禁，不能只采信代理总结；默认不启用 supervision watch，只有用户显式要求监督或出现已证实的高风险不可逆操作时才临时启用。

涉及 NeoForge/Fabric 语义替换、patched Minecraft、registry lifecycle、transaction/rollback、networking、序列化兼容、线程/owner 生命周期等高风险边界时，候选综合完成后仍按风险调用独立模型做**只读 adversarial review**；审查者默认不改代码、不重复完整文档与发布门禁。真实发生的实现比较与独立审查都必须记录证据。

Fabric 主线使用 Mojang official mappings。官方 API、common 与 Fabric adapter 源码分别通过 `fabric/api-slice.txt`、`fabric/common-slice.txt`、`fabric/platform-slice.txt` 逐项纳入；不得复制源码、改包名、加入 Kotlin，或为消除编译错误而无审计地扩大清单。
