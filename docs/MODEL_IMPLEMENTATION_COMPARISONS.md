# 并行实现评估记录

本文档长期记录同一开发任务由多个模型独立实现后的实际表现。任务 001–004 是四模型选拔阶段；用户指定任务 004 为最后一次四模型比较，之后缩减为两个模型。结论只服务于本 Mekanism Fabric 项目的默认开发流程，不视为跨项目永久排行榜。

## 当前固定参评模型

从任务 005 起，后续适合并行比较的开发任务默认同时使用：

1. **5.6 SOL**（继承/default 模型）；
2. **DeepSeek v4.1 Flash**（`commandcode/deepseek-v4.1-flash`）。

Gemini 3.8 Flash 与 GLM 5.3 不再进入默认实现面板；需要其特定能力时仍可按任务显式调用。任务 001–004 的四模型原始评价和指标完整保留在下文。

### MiniMax M3 临时观察

任务 005–007 额外加入 **MiniMax M3**，与正式双模型使用相同 clean commit、prompt、隔离和门禁，作为第三份临时实现。三轮结果照常记录在本文档，但 MiniMax 不进入 `AGENTS.md`、不因此改变正式的 SOL + DeepSeek 默认组合；三轮后再由用户决定是否继续或调整。

## 执行与评价规则

- 两个代理必须从同一 clean commit 开始，使用独立 Git worktree，并收到相同的目标、范围、约束和验收门禁。
- 两个顶层代理启动后默认各开启 300 秒 supervision watch；只在方向、范围、验收漂移、反复无新证据失败、明确求助或高风险越界时干预，普通构建/测试错误留给代理自行修复。
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
- 已知 provider/计量故障必须标注；受污染的 token 数据不能归因于模型本身，也不参与跨模型效率结论。

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

---

## 任务 002：纳入动态热系数电容器

### 任务简述

- **基线：** `e89b69d64268eb1e4a4163846d036ffde059ef4d`
- **目标：** 将官方原样的 `VariableHeatCapacitor` 纳入 Fabric common allowlist，验证动态传导/绝缘系数 supplier 语义，并补齐打包证明和状态文档。
- **关键验收：**
  - 官方源码与 `4646f509` 完全一致，不做平台适配，只增加一个 common slice 路径；
  - simple factory 默认系数及继承的 ambient/listener/storage 行为不变；
  - 两个 supplier 构造时各采样一次，getter 后续实时重采样；
  - 传导系数严格保持 `Math.max(1, supplied)` 的 sub-one、NaN、正负无穷行为，绝缘系数原样返回；
  - 保留 variable factory 绕过 `BasicHeatCapacitor.create` 验证的官方行为，null coefficient supplier 必须失败；
  - remapped JAR 必须包含该类且不泄漏 fixture/NeoForge 类；
  - 修复 `fabric/README.md` 的旧 slice 数量并准确记录边界；运行单测、slice/isolation、Fabric build、packaged smoke 和 clean release 门禁。

### 5.6 SOL

- **提交：** `24ca02bffc8c21ac0e9533557ef43d47081115a6`
- **规模：** 8 files，`+131/-24`；新测试 104 行、5 个 test。
- **资源：** 377.7s，61.9k tokens，38 次工具调用，约 `$0.78`。
- **验证：** 代理报告全部门禁通过。
- **优点：**
  - 以最短测试完整覆盖两个 supplier 的构造采样、实时重采样、默认/自定义 ambient、listener、sub-one 和全部非有限值；
  - 明确保留默认模式、factory 验证绕过和 null supplier 行为，没有重复测试已有 `BasicHeatCapacitor` 的整个 NBT 契约；
  - `FABRIC_HEAT.md` 正确把打包验证的 heat 类数量从 8 更新为 9；
  - `fabric/README.md` 是四者中最简洁且边界最明确的版本，准确写明 API 35、common 23、platform 10，以及未声明外部 API/transaction/config holder/network/machine owner 集成。
- **不足：**
  - 没有像 DeepSeek 那样额外统计 ambient supplier 的惰性读取次数，也没有在本测试中重复对照 `BasicHeatCapacitor.create` 的异常；这些语义已由现有 Basic 测试覆盖，不构成本任务缺口。
- **本任务评价：** 正确性、覆盖度和简洁度最平衡，单体实现最佳。

### Gemini 3.8 Flash

- **提交：** `4e5b8c83f31fe17f18a3b70f625317475ccd84f5`
- **规模：** 8 files，`+220/-23`；新测试 187 行、5 个 test。
- **资源：** 485.6s，397.4k tokens，69 次工具调用，约 `$0.48`。
- **验证：** 代理报告全部门禁通过。
- **优点：**
  - 所有显式验收语义均有覆盖，包括两个 supplier 的采样次数、继承存储/NBT、完整非有限值和 nullability；
  - heat 类数量、slice 数量和延后边界总体正确；
  - 相比任务 001，未引入生产语义回退。
- **不足：**
  - 测试比 5.6 SOL 长约 80%，重复了已有 Basic NBT 测试，并留下未使用的 `assertNull` import；
  - `Math.max` 测试中保留了“先声称 NaN 返回 1、再自问并纠正为 NaN”的多行思考过程，不应进入正式源码；
  - README 逐项枚举大量已纳入类型，信息密度低于 5.6 SOL 的闭包级描述；
  - token 消耗约为 5.6 SOL 的 6.4 倍，四者最高。
- **本任务评价：** 功能正确且验收完整，但缺乏最终代码清理，冗余明显。

### DeepSeek v4.1 Flash

- **提交：** `281beba9385ce4144ac086e8eea24609384177ed`
- **规模：** 8 files，`+173/-13`；新测试 144 行、5 个 test。
- **资源：** 435.7s，68.5k tokens，60 次工具调用；通知未提供成本。
- **验证：** 代理报告全部门禁通过。
- **优点：**
  - 四者中测试语义证明最细：额外验证 ambient 惰性读取次数、Basic/Variable factory 的直接对照，以及 non-finite factory 输入；
  - 两个 coefficient supplier 的构造/重采样计数完整，测试只依赖可观察行为；
  - 文档中的 heat 类数量和延后边界正确，README 也明确不声明外部 API、capability、transaction 或 machine integration；
  - 第一次测试发现自身断言顺序错误后能定位并修复。
- **不足：**
  - 对这个 40 行无平台适配类而言，144 行测试和较长文档略显过量；
  - 工具调用约为 5.6 SOL 的 1.6 倍；
  - packaged smoke 曾留下一个临时 orphan server process，虽然代理随后手动终止且没有仓库/运行时影响，但说明长门禁清理过程不够平滑。
- **本任务评价：** 语义验证最强，整体质量第二；适合作为补充审查来源，但没有必要整体替换更精炼的 5.6 SOL 版本。

### GLM 5.3

- **提交：** `c818ea0c0166aa4cbabce1c249291675abcc857d`
- **规模：** 实际为 8 files，`+135/-19`；新测试 106 行、5 个 test。代理报告误写成 7 files、`+29/-19`，遗漏了新测试文件的 106 行。
- **资源：** 349.4s，24.3k tokens，31 次工具调用，约 `$0.25`。
- **验证：** 代理报告全部门禁通过。本任务运行时尚未启用用户后来指定的 `thinking=high`。
- **优点：**
  - 再次获得最低耗时、token、调用数和成本；
  - 测试对默认行为、动态系数、非有限值、验证绕过和 null supplier 均有实质覆盖；
  - 未扩大生产 slice，构建和打包结果正确。
- **不足：**
  - 只统计 conduction supplier 的构造/后续读取次数，没有证明验收明确要求的 insulation supplier 也在构造时采样一次并由 getter 重采样；
  - `FABRIC_HEAT.md` 仍声称 packaged smoke 验证 8 个 heat 类，加入 Variable 后应为 9；同一文档也没有登记新增测试；
  - README 比 5.6 SOL 版本更长，且最终报告的 diff 规模不准确。
- **本任务评价：** 执行效率最佳，但存在一个测试证据缺口和一个文档事实错误，因此本任务的单体质量排在第四。下一项任务将按用户要求使用 `thinking=high`，观察这些问题是否改善。

### 本任务结论

- **单体代码质量：** 5.6 SOL > DeepSeek > Gemini > GLM 5.3。
- **执行效率：** GLM 5.3 > 5.6 SOL > DeepSeek > Gemini。
- **最终策略：** 直接采用 5.6 SOL 实现；其他版本没有必须合入的代码。DeepSeek 的额外断言作为评审证据使用，但现有 Basic 测试已覆盖其主要增量。
- **整合提交：** `c7b3aa0f68ad0d28bfd61ccc96462fb343795daa`
- **主代理独立验证：**
  - 官方 `VariableHeatCapacitor.java` 相对 `4646f509` diff 为空；
  - slice 数量 API 35 / common 23 / platform 10，且无重复；
  - 68 个 JUnit tests，0 failures / 0 errors；三个 source slice gate 和 fixture isolation 通过，14.44s；
  - `fabric-build.sh` 通过，1.92s；
  - packaged dedicated-server smoke 通过，39.87s；
  - clean release contract 通过，7.25s；恰好一个 JAR，包含 `VariableHeatCapacitor.class`、无 fixture/NeoForge 类，`build-info.json` source commit 与 `c7b3aa0f` 一致。

### 两项任务后的暂时纵向信号

样本仍然只有两个，而且都偏向 Java fixture/domain 单元测试、Bash/Gradle 门禁和文档，不能代表大型平台 adapter 或并发事务实现：

- **5.6 SOL：** 两项任务都给出最佳单体实现，优势是首次方案的一致性、精确命名和适度测试；
- **DeepSeek：** 两项任务均为质量第二，稳定优势是证据组织、脚本重构和更细的边界测试，代价是更多调用与实现体积；
- **GLM 5.3：** 两项任务都为效率第一，但分别出现边界表达/超时余量问题和验收/文档遗漏；下一项启用 `thinking=high` 后再观察；
- **Gemini：** 两项任务均明显消耗更多 tokens；任务 001 有未被门禁触发的语义回退，任务 002 功能正确但未清理冗余与思考痕迹。

继续累计不同类型任务；不把当前顺序视为永久模型排名。

---

## 任务 003：纳入平台无关通配符匹配器

### 任务简述

- **基线：** `21b61941c8361e35809ff27c16b0a32fd5a6b6ee`
- **目标：** 原样纳入官方 `WildcardMatcher`，保留官方 `*`/`?`/`#`、递归回溯、`Locale.ROOT` 与 UTF-16 `char` 语义，增加真实 `TagKey`、非 ASCII BMP digit 和打包边界测试。
- **关键验收：** 只增加一个 common slice（最终 API 35 / common 24 / platform 10）；官方 21 个断言全部保留；默认 locale 临时切换必须 `finally` 恢复；不得声称 QIO/miner/Finder 等消费者已移植；最终 JAR 精确包含该类且无 fixture/NeoForge 类。

### 5.6 SOL

- **提交：** `6bdbf2404f37ae92ae9efd60d19d25739c2a982c`
- **规模：** 7 files，`+98/-6`；83 行、7 个 tests。
- **资源：** 325.6s，65.1k tokens，35 次工具调用，约 `$0.79`。
- **优点：** 原样保留四个官方 test methods 和全部断言，以最少增量精确覆盖 Turkish locale 恢复、真实 `TagKey`、BMP digit；README 和独立 matrix 小节简洁、边界明确。
- **不足：** 未额外固定 supplementary digit surrogate 负例和自然 null failure；前者后来作为有价值的 UTF-16 边界补入综合版，后者属于可选范围。
- **本任务评价：** 最佳平衡和最佳整合基底。

### Gemini 3.8 Flash

- **提交：** `409b0e823e938d2de2e6fc52720f4d7378befb6b`
- **规模：** 7 files，`+144/-6`；128 行、8 个 tests。
- **资源：** 597.6s，362.2k tokens，71 次工具调用，约 `$0.47`。
- **优点：** 验收完整，额外覆盖两个 registry 类型、多组 TagKey、supplementary surrogate、三个 null 输入；文档事实正确。
- **不足：** 相比 5.6 SOL 有较多重复正反例，测试长约 54%；packaged smoke 诊断过程多次重跑，整体最慢之一。
- **计量说明：** 用户已确认 Reload 前 Gemini provider 存在缓存击穿 bug；任务 001–003 的 Gemini token 数均受污染，不能作为模型自身效率结论。外部修复已完成但需 Reload，任务 004 将作为修复后首个观测样本。
- **本任务评价：** 正确但冗长；质量第四，token 效率不在本任务据此判定。

### DeepSeek v4.1 Flash

- **提交：** `9696583e52fd96b3b4ee14938f171743605ec3a2`
- **规模：** 7 files，`+119/-7`；104 行、8 个 tests。
- **资源：** 314.6s，62.1k tokens，57 次工具调用；通知未提供成本。
- **优点：** 在接近 5.6 SOL 的体积下增加 supplementary-plane digit/UTF-16 surrogate 负例和完整自然 null failure；验收、文档、打包均正确；本任务墙钟和 tokens 最低。
- **不足：** 将官方测试统一改为 static imports，虽保留全部用例但产生无必要 churn；额外 null 测试不是必需，工具调用明显多于 5.6 SOL。
- **本任务评价：** 质量第二；supplementary surrogate 负例被综合版吸收。

### GLM 5.3（`thinking=high`）

- **提交：** `d685c8d6efbf4a22dab3662dcdd27a015ddad3ba`
- **规模：** 7 files，`+134/-6`；117 行、8 个 tests。
- **资源：** 626.4s，94.3k tokens，47 次工具调用，约 `$0.64`。
- **优点：** 与任务 002 默认 effort 相比，本次没有验收或文档事实遗漏；locale 测试同时证明 Turkish 环境生效及恢复，digit 测试覆盖多个 BMP 数字和 supplementary code-point/char 差异，文档边界完整。
- **不足：** 声称官方测试“unchanged”，但实际改成 static imports；测试比必要实现更长；本任务耗时最高，tokens 相比任务 002 的默认 effort 24.3k 明显增加。任务复杂度和并发负载不同，不能把变化完全归因于 high effort。
- **本任务评价：** high effort 明显改善完整性，质量第三；需要更多任务判断提升是否稳定以及成本是否值得。

### 本任务结论

- **单体代码质量：** 5.6 SOL > DeepSeek > GLM 5.3 high > Gemini。
- **执行效率：** DeepSeek 与 5.6 SOL 接近领先；GLM high 明显更重；Gemini 数据受 provider bug 污染。
- **最终策略：** 以 5.6 SOL 为基底，把 TagKey 正例改为同时覆盖路径大小写，并吸收后三者验证的 supplementary Unicode digit/UTF-16 surrogate 负例。
- **综合提交：** `05827bc28bb576aab9b396eda3b0e54e96cc5545`，7 files，`+104/-6`，89 行、7 个 tests。
- **主代理独立验证：**
  - 官方源 diff 为空，slice 精确为 35 / 24 / 10；
  - 75 个 JUnit tests，0 failures / 0 errors；source slice 与 fixture isolation gates 通过，14.88s；
  - `fabric-build.sh` 通过，1.75s；packaged smoke 通过，45.24s；
  - clean release 通过，7.30s；一个 JAR，`WildcardMatcher.class` 恰好一次，无 fixture/NeoForge 类，build-info commit 与 `05827bc2` 一致。

### 三项任务后的暂时纵向信号

- 5.6 SOL 连续三项给出最适合作为基底的实现，优势仍是完整性与克制的平衡；
- DeepSeek 连续提供有价值的边界测试/重构建议，本任务在时间与 tokens 上也接近或略优于 5.6 SOL；
- GLM high 在本任务补齐了此前遗漏，但成本优势消失；继续以 high effort 观察，不能跨不同任务直接比较倍数；
- Gemini 三项 Reload 前 token 数据统一标记为 provider 缓存 bug 污染；修复效果必须从 Reload 后任务 004 开始衡量。

三个样本仍不足以形成永久排名，且尚未覆盖大型 adapter、并发和事务语义。

---

## 任务 004：纳入官方公平分配算法

### 任务简述

- **基线：** `4e8327c7c0216a03287104c035981411f37f695a`
- **目标：** 原样纳入 `EmitUtils`、`SplitInfo`、`IntegerSplitInfo`、`LongSplitInfo` 与 `Target`，保持 int/long 均分、重平衡、余量顺序、lying-handler fallback 与 handler 契约语义。
- **关键验收：** common slice 从 24 增至 29；15 个官方测试/辅助源码原样接入（14 个 JUnit examples + 3 个各 100,000 tries 的 jqwik properties）；本地边界测试覆盖 null/empty/zero、插入顺序、`Integer.MAX_VALUE` 和不经 int clamp 的 true-long/`Long.MAX_VALUE`；最终 JAR 六个生产 class entries 恰好各一份且无测试/helper；不宣称 transport caller 或 Fabric transaction 已移植。

### 5.6 SOL

- **提交：** `970bda120e050bc40ac89567ef0e6bd39e5fc0bb`
- **规模：** 8 files，`+286/-16`；166 行、5 个 boundary tests；不复制官方测试。
- **资源：** 649.8s，114.7k tokens，65 次工具调用，约 `$2.00`。
- **优点：** 直接把精确 15-file oracle 集合追加到 `compileTestJava`，结构最少；边界测试以 5 个聚合用例完整满足验收；packaged smoke 使用 ZIP allowlist 同时证明六类各一次并拒绝任何额外 distribution class；四份文档最克制。生产和 oracle 源均保持 byte-identical，97 个测试节点及全部门禁通过。
- **不足：** oracle 路径硬编码在 `build.gradle`，没有像 GLM 那样对 root oracle 目录未来新增文件自动报错；边界用例聚合后单个失败的定位粒度不如其他实现。
- **本任务评价：** 正确性、门禁强度、体积和可维护性平衡最佳，直接作为主线实现。

### Gemini 3.8 Flash（Reload 后首个样本）

- **提交：** `9a88c34cdaa2dd3f09100575602485d76bf1ce57`
- **规模：** 24 files，`+1221/-15`；复制 15 个 oracle 文件并加 byte-fidelity gate；591 行、19 个 boundary tests。
- **资源：** 847.3s，787.1k tokens，134 次工具调用，约 `$1.63`。
- **优点：** 自动逐字节比较复制 oracle 与 root；边界测试覆盖很广，111 个测试节点全部通过；JUnit BOM 与 jqwik wiring 清楚。
- **不足：** 相比 SOL 增加约 4.3 倍代码，复制 15 个本可原地编译的测试文件，19 个本地测试大量重复官方 rebalancing/fallback 和相邻数值案例；packaged smoke 只验证类至少存在一次，而最终总结错误声称脚本已证明“恰好一次”。
- **Reload 后计量：** provider 修复后的本轮仍为 787.1k tokens，是 SOL 的 6.9 倍、三位其他模型中位数的约 6 倍；相对任务 002–003 的同组倍率没有实质下降。单个不同复杂度任务不能证明 provider 内部细节，但至少没有观察到可用于本项目决策的 token 正常化。
- **本任务评价：** 功能正确但明显过度实现，并存在总结与真实门禁不一致；质量第四。

### DeepSeek v4.1 Flash

- **提交：** `62525ae75ddda145a4e378e08989d922ef9dfeae`
- **规模：** 10 files，`+424/-10`；292 行、7 个 boundary tests；不复制官方测试。
- **资源：** 558.7s，132.1k tokens，92 次工具调用；通知未提供成本。
- **优点：** 本轮墙钟最快；以独立 `test-oracle-slice.txt` 和 source set 原地编译官方测试，和生产 allowlist 风格一致；true-long 测试详细；packaged smoke 同 SOL 一样精确计数六类；99 个测试节点及全部门禁通过。
- **不足：** 为一个测试族新增 ignored-text 例外和 source set，边界测试比 SOL 长约 76%，README 说明也偏重；oracle verifier 固定数量但不自动核对 root 目录未来新增文件。
- **本任务评价：** 质量第二；证据充分且速度好，但没有足够收益抵消额外结构和代码量。

### GLM 5.3（`thinking=high`）

- **提交：** `75c92c3d`
- **规模：** 9 files，`+412/-10`；272 行、7 个 boundary tests；不复制官方测试。
- **资源：** 1353.5s，156.1k tokens，96 次工具调用，约 `$2.02`。
- **优点：** 独立 oracle source set 设计清楚；四者中唯一主动让 verifier 在 root oracle 目录未来增删文件时都失败；边界与文档完整，99 个测试节点及最终门禁通过。
- **不足：** Gradle wiring 最复杂；packaged smoke 只查存在而未在脚本内精确计数；中途经历 source-set 编译配置失败和 stale-test 诊断，最终墙钟为四者最慢、约 SOL 2.1 倍，`thinking=high` 未形成稳定收益。
- **本任务评价：** 质量第三；有一个值得参考的完整性门禁，但成本和复杂度不适合作为整合基底。

### 本任务结论

- **单体代码质量：** 5.6 SOL > DeepSeek > GLM 5.3 high > Gemini。
- **执行效率：** DeepSeek 与 SOL 领先；GLM high 最慢；Gemini token 使用仍远高于同组。
- **最终策略：** 直接采用 SOL 提交 `970bda12`，不加入其余实现的额外结构；当前精确 allowlist 已防止无关 root tests 被编译，未来官方 baseline 固定，因此 GLM 的目录新增检测不足以抵消复杂度。
- **主代理独立验证：** 五个生产文件与 15 个 root oracle 相对 `4646f509` diff 为空；slice 为 35 / 29 / 10；97 tests、0 failures/errors/skips，三个 property 各 100,000 tries；完整 Gradle gate 18.67s，`fabric-build.sh` 2.47s，packaged smoke 47.83s，clean release 10.44s；一个 JAR、六类各一次、无 distribution test/helper、fixture、NeoForge、jqwik 或 JUnit 类，`build-info.json` source commit 与 `970bda12` 一致。

## 四模型阶段最终结论与双模型选择

四项任务在本项目上的单体质量顺序如下：

| 任务 | 质量顺序 |
|---|---|
| 001 configuration rejection | SOL > DeepSeek > GLM > Gemini |
| 002 variable heat capacitor | SOL > DeepSeek > Gemini > GLM |
| 003 wildcard matcher | SOL > DeepSeek > GLM > Gemini |
| 004 fair distribution | SOL > DeepSeek > GLM > Gemini |

最终默认保留 **5.6 SOL + DeepSeek v4.1 Flash**：

- **5.6 SOL** 四次均提供最适合作为整合基底的实现，稳定优势是语义完整、范围克制、测试够强且代码量最低或接近最低。
- **DeepSeek** 四次均为质量第二，持续提供可吸收的边界测试、shell/Gradle 结构或诊断证据；速度与 tokens 大多接近 SOL，本轮还是墙钟最快。它是最稳定且互补的第二实现来源。
- **GLM** 在默认 effort 的小任务中曾有明显成本优势，但出现验收和文档遗漏；`thinking=high` 后完整性改善，时间/token 优势却消失，任务 004 增至 1353.5s。其强项可按需用于机械、强门禁任务，但不保留在默认面板。
- **Gemini** 能完成门禁，但四次均有冗余、语义/清理或总结准确性问题。任务 001–003 token 数据受已知 provider bug 污染；Reload 后任务 004 仍未显示相对同组的实质改善，因此不以 provider bug 作为唯一淘汰理由，代码质量和范围控制也不足以超过 DeepSeek。

从任务 005 起，正式双模型继续遵循同 clean commit、隔离 worktree、相同 prompt/gates、300 秒 watch、主代理检查真实 diff 并独立验证的规则。任务 005–007 临时增加 MiniMax M3，并把三轮实际结果记入本文档，但不改变 `AGENTS.md` 中的正式组合。该选择是基于本仓库样本的工程决策，不推广为所有项目或所有任务的永久模型排名。
