# 四模型并行实现评估记录

本文档长期记录同一开发任务由四个模型独立实现后的实际表现。它不是一次实验后的永久排行榜；只有积累多种任务类型后，才据此判断各模型的稳定优势、弱点和适用范围。

## 固定参评模型

除非用户以后调整，后续适合并行比较的开发任务默认同时使用：

1. **5.6 SOL**（继承/default 模型）；
2. **Gemini 3.8 Flash**（`antigravity/gemini-3.8-flash`）；
3. **DeepSeek v4.1 Flash**（`commandcode/deepseek-v4.1-flash`）；
4. **GLM 5.3**（`zai-coding-cn/glm-5.3`）。

## 执行与评价规则

- 四个代理必须从同一 clean commit 开始，使用独立 Git worktree，并收到相同的目标、范围、约束和验收门禁。
- 每个代理都必须实际开发、验证并提交；禁止读取、复制或 cherry-pick 其他参评代理的实现。
- 比较以真实 commit/diff 和测试证据为准，不以代理总结为准。
- 评价至少覆盖：
  - 正确性与原语义保留；
  - 范围控制及架构质量；
  - 测试/门禁强度；
  - 可维护性、命名与文档；
  - 耗时、token、工具调用和已知成本；
  - 自我修复能力及残余风险。
- 可以选择单一最佳实现，也可以由主代理取各实现优点形成综合版本；综合版本必须重新审查并独立运行门禁。
- 每次实验只形成该任务上的评价，不直接推导永久模型排名。
- 参评分支在完成检查和整合后删除；禁止 push 或部署比较分支。

---

## 任务 001：同版本客户端显式拒绝配置握手

### 任务简述

- **基线：** `4023018d7379933b6603bef0bbee6552c552d3bc`
- **目标：** 扩展 Fabric 配置阶段 rejection gate，新增第三种真实 JVM/网络场景：客户端声明 Mekanism payload receiver、协议版本为 `10.7.19`，但明确回复 `accepted=false`。
- **关键验收：**
  - 复用现有隔离 `handshakeMismatchFixture` 和独立 wire codec，不新增 source set，不加载生产 Mekanism 类；
  - 保留原有 version mismatch 默认行为，并用显式模式选择新场景；
  - 服务端必须记录精确的 `kind=client_rejected`、玩家、session、local/remote 和拒绝原因；
  - 客户端必须满足 READY → RESPONSE → DISCONNECT；服务端满足 START → REJECT；
  - 三个拒绝客户端均不得完成握手或进入 play，服务器保持运行；
  - 更新文档，运行单测、slice/isolation、三场景 rejection、paired success、packaged server 和发布构建门禁。

### 5.6 SOL

- **提交：** `9f853cd0315bb02db26227b8eb7c4c1f496c925f`
- **规模：** 7 files，`+88/-25`；最终 rejection 脚本 250 行。
- **资源：** 887.6s，74.9k tokens，36 次工具调用，约 `$1.04`。
- **验证：** 代理报告全部门禁通过。
- **优点：**
  - 用不可变 `FixtureMode` 枚举集中表示 mode、local protocol 和 accept policy；
  - `version_mismatch` / `same_version_reject`、`client_rejected` 场景目录及环境变量命名精确；
  - reject 模式固定回复 `10.7.19`，同时保留默认模式原有的动态 compatibility 计算；
  - 模式只解析一次，READY 与 RESPONSE 状态一致；
  - 三客户端后把 overall deadline 从 180s 提高到较合理的 240s。
- **不足：**
  - 漏改 `AGENTS.md` 中 rejection gate 的场景说明；
  - server start 和 username disconnect 的 Python 检查仍有重复。
- **本任务评价：** 单体实现中整体代码质量最佳；主要缺点属于容易补齐的文档与去重问题。

### Gemini 3.8 Flash

- **提交：** `af00ce3c1381361e29c5c765ba8338dc89a2ef57`
- **规模：** 8 files，`+110/-28`；最终 rejection 脚本 261 行。
- **资源：** 1107.1s，约 1.004M tokens，163 次工具调用，约 `$1.73`。
- **验证：** 代理报告全部门禁通过。
- **优点：**
  - 使用枚举并采用与服务端一致的 `client_rejected` 模式名；
  - 使用 Loom `property(...)` 转发模式；
  - 240s deadline、文档和 `AGENTS.md` 均覆盖新场景。
- **不足：**
  - 默认 mismatch 模式把 `accepted` 永久写死为 `false`，没有保留原实现“请求协议若恰好等于 fixture protocol 则接受”的计算语义；
  - 空白模式被静默当作默认值，不符合严格拒绝不支持模式的目标；
  - 使用可变静态 `configuredMode`，并暴露不必要的 public fixture 常量；
  - 实现规模和资源消耗均为四者最高。
- **本任务评价：** 门禁场景可通过，但存在未被当前 production-server 路径触发的语义回退，不宜作为整合基底。

### DeepSeek v4.1 Flash

- **提交：** `d3f54ca09ed34bdd7f51ba0903e37552104e48c3`
- **规模：** 8 files，`+92/-49`；最终 rejection 脚本 241 行。
- **资源：** 991.5s，117.8k tokens，92 次工具调用；通知未提供成本。
- **验证：** 代理报告全部门禁通过。
- **优点：**
  - 固定使用 `10.7.19` 模拟同版本拒绝，并保留默认 compatibility 计算；
  - 抽出 `require_username_disconnect` 和 `server_session_start_line`，shell 去重最好；
  - Loom `property(...)`、文档和 `AGENTS.md` 完整；
  - 300s deadline 在并发负载下最保守。
- **不足：**
  - 模式仍由字符串和布尔判断组合表达，不如枚举约束清晰；
  - 默认名 `mismatch` 不如 `version_mismatch` 精确，非法模式错误未列出允许值；
  - 脚本头部注释仍未描述新增显式拒绝场景；
  - 工具调用和耗时明显高于 5.6 SOL 与 GLM。
- **本任务评价：** shell、Gradle wiring 与文档部分最佳；Java 状态模型略逊于 5.6 SOL。

### GLM 5.3

- **提交：** `7b31f7a4ff5700b89454af9d0a9f4599131485e4`
- **规模：** 8 files，`+74/-21`；最终 rejection 脚本 249 行。
- **资源：** 713.0s，70.7k tokens，32 次工具调用，约 `$0.38`。
- **验证：** 代理报告全部门禁通过。
- **优点：**
  - 四者中耗时、token、调用数、成本和 diff 均最低；
  - 文档及 `AGENTS.md` 完整；
  - 实现直接且没有修改生产网络代码。
- **不足：**
  - reject 模式回显收到的服务端协议，而不是固定表达 `10.7.19` 客户端身份；
  - 模式在初始化与请求回调中重复解析，理论上可能产生 marker/response 状态不一致；
  - 保留 180s deadline，而该代理的完整 rejection gate 墙钟已约 183s（主体可能刚好在 deadline 内，清理使总墙钟越界），余量不足；
  - 模式/场景变量命名不如 5.6 SOL 精确，脚本头部注释也未覆盖新场景。
- **本任务评价：** 效率最佳，适合快速、小型且强门禁任务；需要主代理补强边界语义和长期可维护性。

### 本任务结论

- **单体代码质量：** 5.6 SOL > DeepSeek > GLM 5.3 > Gemini。
- **执行效率：** GLM 5.3 > 5.6 SOL > DeepSeek > Gemini。
- **最终策略：** 以 5.6 SOL 为基底，吸收 DeepSeek 的 shell helper 和 Loom `property(...)`，并补上 `AGENTS.md`。
- **综合提交：** `be50a3eb01219d03a8e6b919abb867f4ce1d87a3`
- **综合规模：** 8 files，`+106/-46`；rejection 脚本 247 行。
- **主代理独立验证：**
  - 单测、三个 source slice gate、fixture isolation 与两个 fixture compile：通过；
  - 三场景 rejection smoke：通过，94.89s；
  - paired-success smoke：通过，68.99s；
  - packaged dedicated-server smoke：通过，41.38s；
  - clean release contract：通过，7.18s；恰好一个 JAR，`build-info.json` 的 source commit 与 `be50a3eb` 一致。

### 本任务提供的长期信号

这只是一个以 Bash 集成门禁和小型 Java fixture 为主的样本：

- 5.6 SOL 展现了较好的首次架构一致性；
- DeepSeek 展现了较好的脚本重构和证据组织能力；
- GLM 5.3 展现了明显的速度/成本优势；
- Gemini 在高消耗下仍遗漏了当前门禁未覆盖的既有语义。

后续需要继续用 API 闭包、领域逻辑、平台 adapter、并发/事务语义等不同类型任务验证这些信号，不能据此形成永久结论。
