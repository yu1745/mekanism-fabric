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

## 本地只读参考源码

以下目录被 `.gitignore` 排除，不进入本仓库提交，也不是运行时依赖：

| 相对路径 | 上游与分支 | 当前参考 commit | 用途 |
|---|---|---|---|
| `upstream/kilt/` | `KiltMC/Kilt` `version/1.21.1` | `9e8f0d67411f10234bf0d864bd151a6dbcef14b4` | 查看 (Neo)Forge API、对象和生命周期如何在 Fabric 上承接 |
| `upstream/connector/` | `Sinytra/Connector` `1.21.x` | `8b27f1ad042aae8037bcc522b321c03fcce1a12a` | 从反方向查看 Fabric API 到 NeoForge 的对象转换与调用语义 |

仅在任务相关的具体子目录内使用 `rg`/`fd` 搜索；禁止扫描 `~`、`/`、`~/server` 或宽泛父目录。参考仓库默认只读，不要在其中改代码、生成构建产物或提交。需要更新参考版本时必须显式执行，并同步更新本表的 commit。

## 平台 API 映射规则

遇到 NeoForge/Fabric 无明显一一对应的 API 时：

1. 优先判断是否可直接使用 vanilla Minecraft/Mojang 类型；
2. 再寻找 Fabric API 的直接能力；
3. 在 `upstream/kilt/` 查看 NeoForge→Fabric 的实现与边界；
4. 在 `upstream/connector/` 查看 Fabric→NeoForge 的反向映射；
5. 基于双方行为实现最小的 Mekanism 专用 adapter，并补语义测试。

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

## 四模型并行实现评估

后续有明确边界和可执行验收门禁的开发任务，默认让 5.6 SOL、Gemini 3.8 Flash、DeepSeek v4.1 Flash 与 GLM 5.3（固定 `thinking=high`）从同一 clean commit 在隔离 worktree 中并行实现。主代理必须检查真实 diff、综合取舍并独立验证，不能只采信代理总结；每次任务的简述、四份评价、指标和最终选择持续追加到 `docs/MODEL_IMPLEMENTATION_COMPARISONS.md`。一次结果只评价该任务，不直接形成永久模型排名。

Fabric 主线使用 Mojang official mappings。官方 API、common 与 Fabric adapter 源码分别通过 `fabric/api-slice.txt`、`fabric/common-slice.txt`、`fabric/platform-slice.txt` 逐项纳入；不得复制源码、改包名、加入 Kotlin，或为消除编译错误而无审计地扩大清单。
