# 并行实现评估记录

本文档长期记录同一开发任务由多个模型独立实现后的实际表现。任务 001–004 是四模型选拔阶段，任务 005–015 主要使用 SOL/DeepSeek 并穿插临时模型观察。结论只服务于本 Mekanism Fabric 项目的开发流程，不视为跨项目永久排行榜。

## 当前执行策略（任务 016 起）

常规开发默认只使用 **5.6 SOL**（继承/default 模型）作为单一隔离 worktree 实现代理，由主代理审查真实 diff、综合修正并运行最终门禁。不再维持第二个常驻实现模型，也不为常规任务复制完整实现。

只有 NeoForge/Fabric 语义替换、patched Minecraft、registry lifecycle、transaction/rollback、networking、序列化、线程/owner 生命周期等高风险边界，才按风险临时选择额外模型做只读 adversarial review。DeepSeek 额度于 Task028 前耗尽；按用户决定，Task028 起此类审查默认改用 **GLM 5.3（`thinking=high`）**，直到用户再次调整。该架构决定优先减少重复实现、文档和门禁成本，不等价于形成跨项目永久模型排名。任务 001–015 的原始比较证据完整保留在下文。

### MiniMax M3 临时观察（已终止）

原计划在任务 005–007 额外加入 **MiniMax M3**。任务 005 首轮中，该代理违反明确搜索边界、未完成门禁，并生成一个可复现失败的边界测试；用户随即要求停止并排除该模型。实际证据保留在任务 005 记录中，任务 006–007 不再运行 MiniMax；它从未进入 `AGENTS.md` 或正式默认组合。

### GLM 5.3 临时复测（已提前终止）

GLM 在任务 013、014 完成两轮临时复测；任务 015 以受支持的 high effort 启动第三轮。用户一度把观察期延长至任务 018，随后基于多实现架构的边际收益与成本，明确决定立即停止正在运行的 Task015 GLM，并从 Task016 起改用单 SOL 实现架构。Task015 partial 不进入主线，也不据此判断 GLM/DeepSeek 的永久替换；GLM 从未进入默认 `AGENTS.md` 实现组合。

## 历史多模型执行与评价规则

- 参评代理必须从同一 clean commit 开始，使用独立 Git worktree，并收到相同的目标、范围、约束和验收门禁。
- 顶层参评代理启动后默认各开启 300 秒 supervision watch；只在方向、范围、验收漂移、反复无新证据失败、明确求助或高风险越界时干预，普通构建/测试错误留给代理自行修复。
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

从任务 005 起，正式双模型继续遵循同 clean commit、隔离 worktree、相同 prompt/gates、300 秒 watch、主代理检查真实 diff 并独立验证的规则。MiniMax M3 临时观察在任务 005 首轮即因明确失败终止，详见下一节；正式组合没有改变。该选择是基于本仓库样本的工程决策，不推广为所有项目或所有任务的永久模型排名。

---

## 任务 005：纳入 ChunkUtils 与通用官方测试 oracle

### 任务简述

- **基线：** `62322bdb39c90044816a8ac708b294c5e71e35dd`
- **目标：** 原样纳入官方 `ChunkUtils`，保持四方向 inclusive、X-fast chunk range 顺序与负 block 坐标 floor-section 转换；把 15-file distribution test oracle 泛化为含 `ChunkUtilsTest` 的精确 16-file root-test oracle。
- **关键验收：** API/common/platform 为 35/30/10；不复制或修改官方源码/测试；覆盖四方向、单轴倒序、跨零去重、packed 坐标边界及 Y 无关性；`ChunkUtils.class` 恰好打包一次且无测试类；不宣称 `IndexedCuboidMap`、spatial index、QIO/miner 或调用方已移植。

### 5.6 SOL

- **提交：** `11be9e52f3a97db47cd88f372a3f8cfc422a2df9`
- **规模：** 8 files，`+172/-35`；98 行、4 个 boundary tests。
- **资源：** 506.0s，99.3k tokens，56 次工具调用，约 `$1.50`。
- **优点：** 最小实现；直接泛化现有 compileTestJava allowlist；四方向与单轴倒序用显式 table 固定，跨零顺序清晰；独立 ZIP 门禁能精确计数并拒绝 `ChunkUtilsTest*`/`ChunkUtilsBoundaryTest*`（含潜在 nested classes）；106 tests 和全部门禁通过。
- **不足：** packed-Y 只在一个 X/Z 组合上取样；`assertDecodedCoordinates` 在已做 packed-array equality 后有一定重复；README 文档增量比 DeepSeek 更长。
- **本任务评价：** 完整且最精炼，质量非常接近最佳。

### DeepSeek v4.1 Flash

- **提交：** `cc5367aefafea7171bece6c32dda4a343a701d23`
- **规模：** 8 files，`+203/-28`；151 行、5 个 boundary tests。
- **资源：** 429.3s，82.5k tokens，55 次工具调用；通知未提供成本。
- **优点：** 本轮耗时、tokens 和调用数均低于 SOL；在全部 X/Z 边界笛卡尔积上验证 floor-section，并对多组正负 Y 验证不变性；额外固定 BlockPos pack/decode 与 ChunkUtils 结果，直接覆盖每个验收点；文档整体更短；107 tests 和全部门禁通过。
- **不足：** 比 SOL 多 53 行测试，其中部分 BlockPos 自身 round-trip 不是 ChunkUtils 独有风险；packaging gate 的 `ChunkUtils*` allowlist 会在未来增加合法同名前缀生产类时要求同步更新，但当前闭包精确。
- **本任务评价：** 小幅胜出：额外覆盖直接对应 packed-coordinate 风险，同时实现与运行资源仍受控。

### MiniMax M3（临时观察，用户中止）

- **宿主自动保存快照：** `72c7673b468759cb5157d556c10883fc9c3593f0`；代理本身未形成已验证提交。
- **停止时规模：** 8 files，`+257/-32`；204 行、5 个 boundary tests。
- **停止时资源：** 654.1s，95.2k tokens，78 次工具调用，约 `$0.26`。
- **可取处：** common slice、generic oracle 命名和 ZIP exact-count 方向基本正确；源码身份未被破坏。
- **明确问题：**
  - 无视 prompt 与 `AGENTS.md` 的搜索红线，执行 `find /home/wangyu ...` 和 `find ~/.gradle ...`，需主代理主动 steer；
  - 重复写入官方已有的五组 range case，测试比 SOL 长约 108%；
  - 用户停止时尚未运行 Gradle、packaged smoke 或 release，也没有代理提交；
  - 主代理在宿主保存快照上独立运行 focused test，结果 **5 tests / 1 failure**：`packedPositionsRoundTripThroughDecoder` 先把 `{-17,-16,-1,0,15,16}` 映射为重复 chunk 坐标，却错误断言 36 个 packed chunks 全部唯一（实际只有 16 个唯一值）；
  - 注释还错误描述 packed Y 占据 bits 52–63，与 vanilla BlockPos long 布局不符。
- **本任务评价：** 不合格。问题同时覆盖约束遵守、坐标语义、测试正确性和完成度；用户决定立即排除，不再运行原计划的第二、三轮。

### 本任务结论

- **完成实现质量：** DeepSeek > 5.6 SOL；MiniMax 未达到可比较完成状态且存在确定失败。
- **效率：** DeepSeek 最快且 tokens/调用数最低；SOL 接近；MiniMax 在未进入任何完整门禁前已耗时最长、调用最多。
- **最终策略：** 采用 DeepSeek 提交 `cc5367ae`。它比 SOL 多 31 additions，但更完整覆盖 packed 坐标/Y 边界，同时运行资源更低；不吸收 MiniMax 内容。
- **主代理独立验证：** 官方 `ChunkUtils`、`ChunkUtilsTest` 与 15 个 distribution oracle 相对 `4646f509` 均无 diff；slice 35/30/10；107 tests、0 failures/errors/skips，三个 jqwik properties 各 100,000 tries；完整 Gradle gate 18.12s，`fabric-build.sh` 2.27s，packaged smoke 43.55s，clean release 10.20s；一个 JAR、`ChunkUtils` 与六个 distribution entries 各一次、无测试/helper、fixture、NeoForge、jqwik 或 JUnit 类，`build-info.json` source commit 与 `cc5367ae` 一致。

---

## 任务 006：纳入 Java 21 LambdaMetaFactoryUtil

### 任务简述

- **基线：** `4935a7c182c8bb3bda36ab34a01dc0e4583c7520`
- **目标：** 原样纳入官方 `LambdaMetaFactoryUtil`，固定 Java 21 下空名称直接 `Field` 与非空名称 virtual method 两条绑定路径。
- **关键验收：** common slice 增至 31；覆盖八种 primitive 与 reference 的装箱/拆箱、null、每实例独立、named method 确实执行并可变换值，以及 lookup/access/invocation failure；最终 JAR 只含 outer、`CallSiteInfo` 和五个私有 primitive setter interface 共七个 class entries；保持 16-file root oracle 不变；不纳入 `SyncMapper`、annotations、tracked data、containers、networking、反射扫描或调用方。

### 5.6 SOL

- **提交：** `5641af258d6da16322323500ec80d6894a18081b`
- **规模：** 7 files，`+263/-10`；213 行、6 个 tests。
- **资源：** 562.7s，103.7k tokens，55 次工具调用，约 `$1.73`。
- **优点：** 用两个紧凑 table 覆盖 direct 与 named 的九种类型；每个 named setter 都执行可观察的值变换并计数，直接证明走的是方法而非字段；同时覆盖双实例、重复读取、reference null，以及 getter/setter 抛出的同一异常对象原样传播；七类 ZIP allowlist、文档和全部门禁完整。
- **不足：** 单体测试没有固定“方法名存在但签名不匹配”的 `NoSuchMethodException`，也没有断言 primitive named setter 对 null/错误 boxed 类型的自然失败；这两项后来从 DeepSeek 方案提炼补入综合版。
- **本任务评价：** 单体质量最佳：语义证明更直接，尤其是九条 named 分支的变换与实际 invocation exception identity，同时测试显著更短。

### DeepSeek v4.1 Flash

- **提交：** `77d3beb028974aee2d735cd8e255e5088fa894f0`
- **规模：** 7 files，`+414/-8`；354 行、9 个 tests。
- **资源：** 551.3s，100.4k tokens，69 次工具调用；通知未提供成本。
- **优点：** 本轮墙钟和 tokens 略低；除九种 direct/named 类型外，单独证明 named path 只把未开放的 `Field` 当类型见证、named getter/setter 可与 raw field 相差 100、精确签名 mismatch、primitive null/错误 boxed type，以及 direct/named 多实例不捕获状态；打包和文档边界完整。
- **不足：** 测试比 SOL 长 141 行（约 66%），同一核心事实拆成较多独立 fixture/assertion；虽然报告概括为异常传播，但实际没有像 SOL 那样让 named accessor 主体抛出 sentinel 并断言异常对象 identity；文档也相对更长。
- **本任务评价：** 质量第二且提供了有价值的失败边界；额外覆盖适合择要吸收，不值得整体替换更紧凑且 invocation 证据更强的 SOL 版本。

### 本任务结论

- **单体代码质量：** 5.6 SOL > DeepSeek。
- **执行效率：** DeepSeek 墙钟快 11.4s、tokens 少约 3.3k；SOL 少 14 次工具调用且有已知成本数据，整体接近。
- **最终策略：** 采用 SOL 提交 `5641af25` 为基底，并在 `86846220` 中吸收 DeepSeek 的精确签名 mismatch 与 primitive null/错误 boxed-type 两组失败断言；保留 SOL 的 transform/counter 与同一异常对象传播设计，不引入其余冗余 fixture。
- **综合规模：** 相对基线 7 files，`+282/-10`；最终测试 232 行、8 个 tests。
- **主代理独立验证：** 官方 production source 相对 `4646f509` 保持不变；slice 35/31/10；115 tests、0 failures/errors/skips，三个 jqwik properties 各 100,000 tries/checks；完整 Gradle gate 10.25s，`fabric-build.sh` 3.02s，packaged smoke 45.10s；clean release contract 10.72s，恰好一个 JAR，七个 lambda family classes 精确各一次，无 fixture/NeoForge/JUnit/jqwik 泄漏，`build-info.json` source commit 与最终 HEAD 一致，服务端到达 `Done`。

---

## 任务 007：纳入 RegistryUtils 并替换 NeoForge 注入方法

### 任务简述

- **基线：** `ee9240db66049f4d2ec332cf53d4d055dfec6812`
- **目标：** 将官方 `RegistryUtils` 纳入 common slice，保持 holder key、defaulted registry、NBT ID 与 generic built-in registry lookup 语义。
- **约束调整：** 初始任务误判该类可原样编译；实际编译证明 `Holder.getKey()` 和 `Registry.getKeyOrNull()` 都是 NeoForge 注入方法。两个候选收到相同修订：只允许用 vanilla `unwrapKey` / `getResourceKey` 作最小替换，禁止用会让 defaulted registry 对未知对象返回默认 key 的 `Registry.getKey`。用户随后要求把 NeoForge 21.1.200 patched Minecraft 源码解压到 `upstream/neoforge-minecraft/`，主线以独立提交 `1994bba9` 登记；候选保持固定基线，只读取其中三个精确相关文件。
- **关键验收：** slice 35/32/10；built-in block entity canonical holder、NBT null/missing/wrong-type/malformed/unknown/valid ID、block path、reference/direct naming、registered/unknown raw object 与 direct-holder short circuit；不变更 registry、不强造 `getBEHolder` null fallback；RegistryUtils production class 精确一次且测试/helper 不泄漏；不纳入 caller、lifecycle 或 custom registry。

### 5.6 SOL

- **提交：** `17a2f8d5f39e724f858e3affaaba89d56262bd49`
- **规模：** 9 files，`+143/-11`；98 行、6 个 tests；production `+3/-3`。
- **资源：** 恢复后的累计通知为 466.4s、180.0k tokens、90 次工具调用、约 `$3.02`；其中包含最初严格按“原样源码”尝试、以三处缺失符号证据停止并清理 worktree 的诊断轮。
- **优点：** 最先明确报告不可编译边界；修订后实现最小，六个紧凑测试覆盖全部显式验收；文档和 ZIP contract 简洁；全部门禁及 121 tests 通过。
- **不足：** registry generic lookup 以内联 unchecked cast 表达，行较长；直接把 holder 替换为 `unwrapKey().orElse(null)`，没有发现 key 未绑定 intrusive `Holder.Reference` 上 vanilla 会抛异常、NeoForge nullable getter 返回 null 的隐藏差异，并错误概括为完全无返回值影响。
- **本任务评价：** 显式验收内最精炼且执行明显更高效，适合作为整合基底；patched-source 深层边界审计逊于 DeepSeek。

### DeepSeek v4.1 Flash

- **提交：** `17e299d439bc18be778518ff2e3032119ac346bc`
- **规模：** 9 files，`+259/-12`；154 行、7 个 tests；production `+9/-4`。
- **资源：** 891.1s，233.0k tokens，146 次工具调用；通知未提供成本。
- **优点：** 用窄泛型 helper 代替 inline raw-style cast；测试额外覆盖第二种 built-in registry 类型并更细拆分结果；最重要的是直接从新加入的 patched `Holder.Reference` 源码发现并准确记录 unbound intrusive key 的异常/null 差异，避免 SOL 的错误“零差异”结论；全部门禁及 122 tests 通过。
- **不足：** 虽发现隐藏差异，候选仍把它作为 residual 留下，没有在生产 helper 中恢复 NeoForge 行为或增加对应测试；测试比 SOL 长 56 行，API delta 增量 49 行，README/Matrix 重复较多；耗时约 1.9 倍、tokens 约 1.3 倍、工具调用约 1.6 倍。
- **本任务评价：** 语义审计质量最佳但实现不够克制；其关键发现必须吸收，整体代码不宜原样采用。

### 本任务结论

- **单体语义审计：** DeepSeek > 5.6 SOL；**单体实现克制与效率：** 5.6 SOL > DeepSeek。
- **最终策略：** 在用户要求的 patched-source 登记提交 `1994bba9` 之后，以 SOL 候选 cherry-pick `c7fa833a` 为基底；主代理依据 DeepSeek 发现增加只针对 vanilla `Holder.Reference` 的 nullable-key helper：key-only standalone 仍返回 key，value-only intrusive 在 key 未绑定时恢复 NeoForge null；同时采用窄 registry helper。`5498f98a` 增加一个不注册、不变更 registry 的 focused test，固定 standalone/intrusive 两种 unbound 状态及三个公开入口，不采用 DeepSeek 的其余冗余断言。
- **综合规模：** Task007 slice 本身 9 files，`+185/-12`，最终测试 120 行、7 tests；另有用户单独要求的 `AGENTS.md` patched-source 登记 `+11/-7`。
- **主代理独立验证：** source diff 仅含两个 NeoForge-extension 边界的 vanilla helper；16 个 official root oracle 和 `fabric/build.gradle` 均未改变；slice 35/32/10；122 tests、0 failures/errors/skips，三个 jqwik properties 各 100,000 tries/checks；完整 Gradle gate 9.66s，`fabric-build.sh` 2.35s，packaged smoke 46.96s 并到达 `Done`；`RegistryUtils.class` 精确一次，无同名前缀测试/helper；最终 clean release contract 10.32s，恰好一个 JAR，无 fixture/NeoForge/JUnit/jqwik 泄漏，`build-info.json` source commit 与最终 HEAD 一致。

---

## 任务 008：纳入顺序 long transfer API 闭包

### 任务简述

- **基线：** `89d7654867834f8809d4929d90d0054508fb4480`
- **目标：** 原样纳入 `InContainerGetterLong`、`LongToLongContainerInteraction` 与 `LongTransferUtils`，恢复 generic callback 与 energy-container 的六个顺序 insert/extract overload family。
- **关键验收：** API/common/platform 为 38/32/10；non-positive/zero-container short circuit、single context forwarding、non-empty-first stable insertion、source-order shrinking extraction、非正 callback 结果、supplier extract 的 zero/negative lookup 差异、SIMULATE/EXECUTE、真实 `BasicEnergyContainer` 与大于 `Integer.MAX_VALUE` 的 long；三个 production class 精确各一次；不引入 fair-distribution、外部 Energy API、Fabric transaction 或调用方，不为 handler/count 违约增加策略。

### 5.6 SOL

- **提交：** `90d4e99cc2bd68b62f489575e3c0b16bc5e2ad11`
- **规模：** 8 files，`+489/-25`；419 行、8 个 tests。
- **资源：** 684.4s，130.5k tokens，61 次工具调用，约 `$2.23`。
- **优点：** 八个聚合测试覆盖全部六类 overload；callback insert 以 negative remainder、energy-list insert 以 zero remainder 分别固定 `<= 0` stop，extract 同时固定负值忽略与满足即停；自定义 probe 对 amount/Action/Automation identity 和 caller range 作强断言；真实 Basic 容器用一个用例完成双向 SIMULATE/EXECUTE 与 true-long；文档总体更短。
- **不足：** 自定义 probe 为实现 `IEnergyContainer` 带入无关 NBT 空实现；多个断言使用 `assertSame(null, side)`；packaged gate 把 transfer entries 混入 distribution required set 后再做集合减法，结构不如独立 contract 清楚；没有像 DeepSeek 那样固定 listener notification，单个聚合测试失败时定位粒度较粗。
- **本任务评价：** 语义完整且较克制，但测试仍达 419 行；整体质量略逊于覆盖更清晰、运行更快的 DeepSeek 版本。

### DeepSeek v4.1 Flash

- **提交：** `2f430878f83a7dfb07fee197e3e528391ed16c04`
- **规模：** 8 files，`+587/-11`；478 行、13 个 tests。
- **资源：** 517.1s，111.6k tokens，83 次工具调用；通知未提供成本。
- **优点：** 十三个按语义分组的测试完整覆盖显式验收；tracking subclass 直接复用真实 `BasicEnergyContainer`，同时证明调用顺序、current remainder/request、mutation 和 listener notification；zero/negative remainder、非正 drain、两种 side、四种 overload context 及 true-long insert/extract 证据清晰；package contract 独立于 distribution 集合；本轮墙钟快 167.3s、tokens 少 18.9k。
- **不足：** 比 SOL 多 59 行测试和 98 行总 diff；最后一组 caller-range 循环与前面真实容器测试有部分重复，文档也更详细；留下一个未使用的 `assertFalse` import，以及一个从未传入 transfer 调用的 `unused` container/空事件断言，主代理清理了这五行。
- **本任务评价：** 单体质量最佳；增量覆盖直接对应 mutation/notification 与诊断粒度，额外体积尚可接受，且运行资源更低。

### 本任务结论

- **单体代码质量：** DeepSeek > 5.6 SOL。
- **执行效率：** DeepSeek 墙钟和 tokens 更低，SOL 工具调用更少且有成本数据。
- **最终策略：** 采用 DeepSeek 提交并在主线上 cherry-pick 为 `096a8b7d`；`274445b6` 只删除未使用 import 和不参与 transfer 的 tautological fixture，不改变覆盖或行为。未吸收 SOL 的自定义 `IEnergyContainer` probe 和混合 package-set 结构。
- **综合规模：** 8 files，`+582/-11`；最终测试 473 行、13 tests。
- **主代理独立验证：** 三个 production source 与 `4646f509` byte-identical；16 个 root oracle、`fabric/build.gradle`、common/platform slices 和 `docs/API_DELTAS.md` 不变；slice 38/32/10；135 tests、0 failures/errors/skips，三个 jqwik properties 各 100,000 tries/checks；完整 Gradle gate 10.08s，`fabric-build.sh` 2.35s，packaged smoke 49.34s 并到达 `Done`；三个 transfer classes 精确各一次且无 `LongTransferUtils*` test/helper；最终 clean release contract 10.94s，恰好一个 JAR，无 test/fixture/NeoForge/JUnit/jqwik 泄漏，`build-info.json` source commit 与最终 HEAD 一致。

---

## 任务 009：纳入 strict energy-handler API 闭包

### 任务简述

- **基线：** `2b1c56881ab92a99c1013d6a87611daebb5313d0`
- **目标：** 纳入 `IStrictEnergyHandler`、`ISidedStrictEnergyHandler` 与 `IMekanismStrictEnergyHandler`，把已验证的顺序 long-transfer 与 energy-container 语义连接成 unsided、sided 和 container-backed 三层 default-method API。
- **关键验收：** `IMekanismStrictEnergyHandler` 与官方基线字节一致；前两份接口只删除 `FluidStack`/`IFluidHandler` import 并把两个 Javadoc-only fluid-fill 链接改成 accepted amount 与 unaccepted remainder 的平台中立文字。API/common/platform 为 41/32/10；覆盖 unsided aggregation、全部 per-container/aggregate side bridge、nullable default side、invalid index/no-op、negative-set clamp、`AutomationType.handler`、一次 list lookup、`SIMULATE`/`EXECUTE` 和 true-`long`；精确限制发布 JAR 的 energy API class；不引入外部 Energy API、换算、lookup/transaction、configurable holder、concrete handler、owner 或 caller。

### 5.6 SOL

- **提交：** `97f4c69dd59e2047967449e1afb576603cf29d2a`
- **规模：** 13 files，`+541/-32`；三份测试共 467 行、8 个 tests。
- **资源：** 762.7s，151.1k tokens，80 次工具调用，约 `$3.19`。
- **优点：** 三层接口各自一份测试，结构与生产层次对应清楚；以较短实现完整覆盖 indexed non-empty-first insertion、source-order extraction、所有 unsided per-container bridge、显式 null/non-null side、Action/Automation identity、invalid-index defaults、单次 aggregate lookup、simulation/execution 和 true-`long`；文档最克制，全部门禁通过。
- **不足：** sided fixture 始终覆写 `getEnergySideFor()` 为非空值，没有直接证明接口自身默认实现返回 null；发布门禁只拒绝三个 handler 同名前缀的额外 class，不能拒绝其它意外进入的 energy API class。
- **本任务评价：** 单体实现质量最佳：验收覆盖与体积平衡更好，两个缺口都能用很小的综合改动补齐。

### DeepSeek v4.1 Flash

- **提交：** `c9bab09c3b07009f69f8d3ed2b52de271f819e86`
- **规模：** 12 files，`+669/-31`；两份测试共 558 行、14 个 tests。
- **资源：** 586.8s，154.3k tokens，93 次工具调用；通知未提供成本。
- **优点：** 比 SOL 快 175.9s；直接测试 actual default null side 和 unsided null forwarding；精确拒绝既有 `IEnergyContainer` 与三个新接口之外的全部 energy API class；还固定 `canHandleEnergy=false` 是实现者 empty-list 契约、default 本身不会暗中强制，以及 IStrict/IMekanism 两层各自的 true-`long`；测试分组细、诊断粒度高，全部门禁通过。
- **不足：** 总 diff 多 128 行、测试多 91 行和 6 个 test；多处重复已经由 `LongTransferUtils` 与同文件其它用例证明的 sequential insertion/extraction、simulate/execute 和 true-long 事实，README/API delta 也更长；`canHandleEnergy=false` 的一半用例故意构造违反接口文档的实现，适合审计 default 不加策略，但不属于调用者可依赖的有效实现契约。
- **本任务评价：** 边界审计与执行速度更强，单体质量第二；其 default-null 与 exact-package 发现应吸收，但无需采用整体较重的测试/文档版本。

### 本任务结论

- **单体代码质量：** 5.6 SOL > DeepSeek；**执行效率：** DeepSeek 墙钟更快，tokens 基本持平（多约 3.2k），SOL 少 13 次工具调用。
- **最终策略：** 采用 SOL 候选并在主线 cherry-pick 为 `6fe8e6b9`；综合提交 `c763da5d` 将 sided fixture 拆为真正使用接口 default-null 的基类和 fixed-side 子类，增加一个聚焦 null-forwarding test，并把发布门禁收紧为只允许 `IEnergyContainer` 与三个 strict handler interface，文档同步改成 exact-package 边界。没有吸收其余重复测试或违约 `canHandleEnergy=false` fixture。
- **综合规模：** 相对基线 13 files，`+560/-32`；三份测试共 485 行、9 tests。
- **主代理独立验证：** `IMekanismStrictEnergyHandler` 与 `4646f509` 字节一致，另两份 production source 只有各两个 import 删除和两个 Javadoc 替换；16 个 root oracle、`fabric/build.gradle`、common/platform slices 不变；slice 41/32/10 且无重复；28 suites、144 tests、0 failures/errors/skips，三个 jqwik properties 各 100,000 tries/checks；完整 Gradle gate 9.49s，`fabric-build.sh` 2.24s，packaged smoke 51.17s 并到达 `Done`；clean release contract 10.47s，恰好一个 JAR，energy API 集合精确为四类，无 test/fixture/NeoForge/JUnit/jqwik 泄漏，`build-info.json` source commit 与实现 HEAD `c763da5d` 一致，JAR sha256 为 `0de740d57c2de93683e87b6d17e4e4a64e4d270144528264186eb01fd69d44fb`。

---

## 任务 010：纳入 indexed spatial-lookup collection 闭包

### 任务简述

- **基线：** `99ad0abdc69d473815e752eb3c07cf1bfa08eebd`
- **目标：** 原样纳入 `BiLongMultimap`、`FilterTransformIterator` 与 `IndexedCuboidMap`，并把官方 `BiLongMultimapTest`、`IndexedCuboidMapTest` 加入精确 root oracle。
- **关键验收：** 三个 production 与两份 official test 均保持 baseline 字节一致；slice 41/35/10，root oracle 16→18；本地只补 dual-index mutation return、negative/extreme long、filter-transform exhaustion、negative chunk/inclusive box、invalid center、same-box replacement 与 mixed removal；发布 JAR 的 collection package 精确为 outer、两个 anonymous iterator 与 `CenteredBoundingBox` 共六类；不宣称 miner/QIO/spatial caller、world/chunk lifecycle/loading、persistence、concurrency 或多线程 mutation 已移植。
- **运行中约束调整：** 用户明确要求只限制未来测试增长、不消融现有测试；两个候选收到同一 steer，要求依赖官方测试、Fabric-local 总计不超过 200 行/5 tests。两者均满足。长期规则另以独立提交 `917a122a` 写入 `AGENTS.md`。

### 5.6 SOL

- **提交：** `828125087c350de392c017088947cf0e1095ffc3`
- **规模：** 9 files，`+146/-16`；单一 local suite 106 行、3 tests。
- **资源：** 379.3s，89.6k tokens，46 次工具调用，约 `$1.29`。
- **优点：** 两份官方测试承担完整正向 grid/removal coverage，本地三个聚合测试只补缺失边界；BiLongMultimap 同时证明 duplicate put/putAll、pair remove、极值排序与双向清理；Indexed 测试实际让 remove、removeAt、removeIf、clear 各自移除对象并保留 survivor，same-box replacement 也证明 value 数量为一；exact package gate 最短，文档没有新增独立文件且同步修正 `FABRIC_ENERGY.md` 的 slice 数量。全部门禁通过。
- **不足：** iterator 用例最初只调用一次 `hasNext`，没有固定 repeated `hasNext` 不消费 prefetched value；空间用例虽用宽 Y 范围证明边界，却没有显式查询“同 X/Z chunk、Y 超界”；`removeAt` 只证明首次 true，没有固定重复删除 false。
- **本任务评价：** 正确性、有效 mixed-removal 证据、测试预算和文档克制均最佳，是明显更合适的整合基底。

### DeepSeek v4.1 Flash

- **提交：** `3269aaebe48cf51c6d0feda5188bbf90931ef34b`
- **规模：** 9 files，`+266/-18`；单一 local suite 160 行、4 tests。
- **资源：** 493.2s，155.2k tokens，78 次工具调用；通知未提供成本。
- **优点：** 明确覆盖 repeated `hasNext`、trailing/fully-filtered iterator、同 X/Z chunk 的 Y 超界、invalid-center 不污染独立 map、更多 BiLongMultimap mutation return 和 repeated `removeAt=false`；测试仍低于临时预算，exact package 诊断清楚，全部门禁通过。
- **不足：** mixed-mutation 用例在 remove 与 removeAt 已清空 map 后才调用 `removeIf(value -> true)`，因此没有实际证明 removeIf 的 index/value 清理；相比 SOL 多 54 行测试、120 行总 diff、32 次工具调用与 113.9s；新增 41 行 `FABRIC_COLLECTIONS.md` 并在 README 重复较多细节，同时漏改 `FABRIC_ENERGY.md` 中当前 common count，留下 32 而非 35。
- **本任务评价：** 提供三个值得吸收的独立断言，但单体在有效覆盖、文档一致性、体积与效率上均逊于 SOL。

### 本任务结论

- **单体代码质量与执行效率：** 5.6 SOL > DeepSeek。
- **最终策略：** 在用户测试政策提交 `917a122a` 后，以 SOL candidate cherry-pick 为主线提交 `398160f0`；综合提交 `1e17e80b` 只吸收 repeated `hasNext`、同 chunk/Y 超界和第二次 `removeAt=false` 三条独有断言，保留 SOL 实际执行 removeIf 的 mixed-removal fixture，不引入 DeepSeek 的空/尾 iterator 重复、独立文档或冗长说明。
- **综合规模：** Task010 本身相对基线 9 files，`+149/-16`；local suite 109 行、3 tests，低于后续长期目标。
- **主代理独立验证：** 三个 production 和两份新 official tests 与 `4646f509` 字节一致，先前 16 个 oracle 文件无修改，oracle 精确 18 个 existing unique paths；slice 41/35/10 且无重复；31 suites、169 tests、0 failures/errors/skips，三个 jqwik properties 各 100,000 tries/checks；完整 Gradle gate 9.37s，`fabric-build.sh` 2.81s，packaged smoke 56.22s 并到达 `Done`；clean release 10.55s，恰好一个 JAR，collection package 精确六类，无 test/fixture/NeoForge/JUnit/jqwik 泄漏，`build-info.json` source commit 与实现 HEAD `1e17e80b` 一致，JAR sha256 为 `e889fd46b206468e07c096f258c0378ebb59e22575a14a5812149bb69194ca37`。

---

## 任务 011：纳入 platform-neutral codec 闭包

### 任务简述

- **基线：** `e8a6eca2e93b943d464d39fd0f6445d302079762`
- **目标：** 原样纳入 API `DependentMapCodec` 与 common `SequencedCollectionCodec`、`DroppingUnboundedMapCodec`，不增加依赖或 adapter。
- **关键验收：** slice 42/37/10，18-file root oracle 与 `fabric/build.gradle` 不变；覆盖 dependency-selected codec/error、sequenced encode/decode bounds/order/immutable/partial remainder、dropping-map first-wins/malformed partial/duplicate partial-error early return；两个 codec package 精确四类；不宣称 `ModuleContainer`、`AttachedSideConfig`、recipes、data components、config/network owner、persistence/registry/packet lifecycle 或 caller 已移植。遵守新增测试目标不超过 200 行、3–6 tests。

### 5.6 SOL

- **提交：** `11bb591e751bbe1d8c3be8d99d656823660c195b`
- **规模：** 8 files，`+149/-9`；单一 local suite 112 行、5 tests。
- **资源：** 469.2s，90.5k tokens，54 次工具调用，约 `$1.64`。
- **优点：** 一个短测试类完成三个 codec 的全部主要边界；Dependent fixture 用事件序列直接证明 dependency decode 发生在 selector 之前，并让 int/text 两类 round-trip 产生可观察不同形状；dropping-map 仅用标准 codec combinator 构造 partial-value error，first-wins、malformed partial 与 round-trip 都很紧凑；文档和 exact package gate 明显更短，全部门禁通过。
- **不足：** sequenced minimum 用例最初只断言 error present；元素自身 decode error 即使错误地按 total count 判断 minimum 也可能让该断言通过。maximum 用例虽断言 `too long: 3` 和 retained elements，却没有同时固定 failed-input remainder。
- **本任务评价：** 单体实现最佳，测试预算与有效覆盖比例优秀；两个断言缺口可在不新增 test method 的情况下补齐。

### DeepSeek v4.1 Flash

- **提交：** `eeddb0526ca37150cfdd449dcc91f9faaa3d1ab6`
- **规模：** 9 files，`+262/-10`；两份 local suites 共 199 行、6 tests。
- **资源：** 695.1s，130.5k tokens，108 次工具调用；通知未提供成本。
- **优点：** sequenced minimum 精确断言 `too short: 1`，maximum 同时固定 retained elements 与 `[4]` remainder；Dependent 的 missing element message、missing/invalid dependency 无 partial result 更具体；自定义 partial codec 清楚地区分 malformed-without-partial 与 duplicate-with-partial early return。全部门禁通过并恰好守住 200 行/6-test 目标。
- **不足：** 相比 SOL 多 87 行测试、113 行总 diff、54 次工具调用与 225.9s；Dependent 用 `AtomicReference` 只观察 selector 最终参数，没有像 SOL 的 event list 那样直接固定 dependency/selector 顺序；custom partial codec 本身 20 多行且返回非标准 decode remainder，但生产测试只消费 first value；README/PORT_MATRIX 重复了较长的逐行为说明。
- **本任务评价：** 边界诊断更细且提供两个必须吸收的断言，单体质量第二；额外体积没有形成足够独有 mutation coverage 来取代 SOL 基底。

### 本任务结论

- **单体代码质量与执行效率：** 5.6 SOL > DeepSeek。
- **最终策略：** 以 SOL candidate cherry-pick 为主线提交 `9e1a4527`；综合提交 `5ff829a5` 在原 sequenced test 中把 minimum 改成精确 `List is too short: 2` 断言，并固定 maximum 的 failed-input remainder 为 `["bad"]`，不新增 test method，也不引入 DeepSeek 的额外 fixture、第二测试文件或冗长文档。
- **综合规模：** 8 files，`+153/-11`；local suite 114 行、5 tests，满足未来测试增长政策。
- **主代理独立验证：** 三个 production source 与 `4646f509` 字节一致，`fabric/build.gradle`、18 个 oracle files/paths 和全部既有测试不变；slice 42/37/10 且无重复；32 suites、174 tests、0 failures/errors/skips，三个 jqwik properties 各 100,000 tries/checks；完整 Gradle gate 10.18s，`fabric-build.sh` 4.06s，packaged smoke 52.67s 并到达 `Done`；clean release 10.98s，恰好一个 JAR，两个 codec package 精确四类，无 test/fixture/NeoForge/JUnit/jqwik 泄漏，`build-info.json` source commit 与实现 HEAD `5ff829a5` 一致，JAR sha256 为 `c7e1a9211e26c4747270ad2c1073a9ece6ae2309a78d08c91878b757373eb278`。

---

## 任务 012：纳入 dynamic strict-energy policy 闭包

### 任务简述

- **固定比较基线：** `326cdec11399524ed80abd4d6dcc2465835ddb4c`；两个候选启动后，主线另以独立 docs-only 提交 `b369ca79` 删除测试源码行数限制，该政策变更不参与候选比较。
- **目标：** 原样纳入 `SimpleDynamicHandler`、`DynamicHandler` 与 `DynamicStrictEnergyHandler`，把已验证的 strict handler defaults 连接到 nullable-side predicate、container supplier 与可选 listener policy。
- **关键验收：** 三份 production source 与 `4646f509` 字节一致；slice 42/40/10，18-file official root oracle 与 `fabric/build.gradle` 不变；覆盖四种 denied operation、supplier short-circuit、indexed 与 aggregate insert/extract、side/Action/Automation forwarding、predicate independence、nullable direct lookup 和 listener；root capability 与 common energy 包精确限制；修正不存在的 `EnergyHandler` 旧措辞；不引入 helper/config holder、side configuration/ejector、Universal Cable、external API/conversion/lookup/transaction、owner、persistence 或 caller。

### 5.6 SOL

- **提交：** `c22d9449ad9f0ca4bfbd3adad3b61264550a2b7a`
- **规模：** 8 files，`+187/-18`；local suite 123 行、4 tests。
- **资源：** 416.5s，91.9k tokens，48 次工具调用，约 `$1.48`。
- **优点：** 四种 indexed/aggregate insert/extract denied 路径都同时证明 predicate result、原样 remainder/zero 和 supplier 零调用；allowed 路径同时覆盖 indexed 与 aggregate 两个方向及两种 Action，直接记录 `AutomationType.EXTERNAL`；nullable direct supplier identity 与可选 listener 独立清楚；文档比 DeepSeek 明显简洁；候选的 178 tests、package gate、packaged smoke 与 release 均通过。
- **不足：** allowed indexed 路径只有单容器并固定 index 0；即使实现错误地忽略 index，也会通过，未独立证明 index forwarding。predicate independence 由双向 allowed/denied 组合间接证明，不如 DeepSeek 用单 handler 对照直接。
- **本任务评价：** 行为方向覆盖、文档克制和 package contract 最平衡，是更合适的整合基底；唯一实质缺口可用极小 fixture 调整补齐。

### DeepSeek v4.1 Flash

- **提交：** `2b9a6af0287d16dacd7d9441df077c92ae9f8c73`
- **规模：** 8 files，`+240/-19`；local suite 131 行、4 tests。
- **资源：** 390.4s，98.1k tokens，77 次工具调用；通知未提供成本。
- **优点：** indexed insertion 明确选择 index 1，并证明第一个 container 完全未调用；同一 handler 的 insert allowed/extract denied 直接固定两个 predicate 相互独立；四种 denied path、supplier short-circuit、nullable lookup/list identity 和 listener 均清楚；比 SOL 快 26.1s，全部门禁通过。
- **不足：** allowed 路径只证明 insertion：extraction 只出现在 denied 路径，没有把 allowed extract 的 amount/Action/Automation bridge 送入真实 container；aggregate 也只有 insertion。`FABRIC_ENERGY.md` 与 `fabric/README.md` 分别比 SOL 多约 31/20 行，较多重复逐项说明；总 diff 多 53 行、工具调用多 29 次。
- **本任务评价：** exact-index 与 predicate-independence 证据最佳，但 allowed direction coverage 和文档信息密度逊于 SOL；适合作为针对性补充来源。

### 本任务结论

- **单体代码质量：** 5.6 SOL > DeepSeek；**执行效率：** DeepSeek 墙钟略快，SOL 少 6.2k tokens、29 次工具调用且有成本数据。
- **最终策略：** 将 SOL candidate 在包含最新测试政策的主线上 cherry-pick 为 `4d804f64`；综合提交 `b6ee11db` 把其 indexed fixture 改为 `[untouched, target]` 并调用 index 1，立即断言第一个 container 无事件，随后用独立 aggregate handler 保留 SOL 原有双向 aggregate 证据。没有吸收 DeepSeek 的 insertion-only allowed 结构或冗长文档。
- **综合规模：** Task012 本身相对固定基线（排除独立 `AGENTS.md` 政策提交）8 files，`+200/-18`；local suite 136 行、4 tests。
- **主代理独立验证：** 三个 production source 与 `4646f509` 字节一致，18 个 official root oracle、`fabric/build.gradle` 和既有测试均未改变；slice 42/40/10 且无重复；33 suites、178 tests、0 failures/errors/skips，三个 jqwik properties 各 100,000 tries/checks；完整 clean Gradle gate 20.23s，`fabric-build.sh` 2.79s，packaged smoke 51.87s 并到达 `Done`；root capability 与 common energy 包精确匹配预期四个 production class，无 test/fixture/NeoForge/JUnit/jqwik 泄漏；implementation clean release 11.05s，恰好一个 JAR，`build-info.json` source commit 与 `b6ee11db` 一致，JAR sha256 为 `2b0a9f18f9e316ac51831403e3618d3d7216e807036df2d381108334a6bf60e1`。

---

## 任务 013：纳入 per-game-time energy tracker

### 任务简述

- **基线：** `b34f721e8bdf3b291b976cbe89c945468c5d7eec`
- **目标：** 原样纳入 `mekanism.common.lib.LastEnergyTracker`，恢复只以三个 `long` 字段完成的 current/last game-time bucket accounting。
- **关键验收：** production source 与 `4646f509` 字节一致；slice 42/41/10，18-file official oracle、`fabric/build.gradle`、API/platform slices 和既有测试不变；3–4 个黑盒测试覆盖 same-time accumulation、任意不同时间戳（跳号/回退）、初始 tick zero、zero/negative/true-long amount 与 client-sync setter；direct common-lib package 精确为它与 `WildcardMatcher` 各一次；不引入唯一 caller `TileEntityMekanism`、world tick、client sync/network/persistence、machine/network owner、external energy 或 Fabric transaction。
- **GLM 临时复测：** 本任务是用户指定三轮 GLM 5.3 复测的第 1/3 轮；因闭包极小，GLM 使用 `thinking=low`，且未修改 `AGENTS.md`。

### 5.6 SOL

- **提交：** `b21e768a580ac1b6ce19102688af13ef56431067`
- **规模：** 8 files，`+113/-7`；local suite 58 行、4 tests。
- **资源：** 363.4s，79.7k tokens，49 次工具调用，约 `$1.28`。
- **优点：** 三者中测试最短，四个方法仍完整区分同 tick 累加/下一 tick 发布、跳号/回退、初始 tick zero、zero/negative/>int long 与 setter 生命周期；每条断言都通过 public API，未引入 overflow 或反射 fixture；文档和 exact-package gate 完整且总体最克制，182 tests 与全部门禁通过。
- **不足：** common slice 新路径放在 `WildcardMatcher` 后而非词法顺序；packaged Python check 的外层错误标签未加入 common-lib-root，失败诊断稍弱；true-long 使用 `Integer.MAX_VALUE + 9` 而非 long 极值。
- **本任务评价：** 以明显最少测试代码获得完整需求覆盖，production/package/docs 边界也准确，是最佳整合基底。

### DeepSeek v4.1 Flash

- **提交：** `f8cc00d597ad137d7074727f4539ee6e5585e93c`
- **规模：** 8 files，`+159/-9`；local suite 86 行、4 tests。
- **资源：** 311.1s，70.2k tokens，60 次工具调用；通知未提供成本。
- **优点：** 三者最快；slice 顺序和 packaged 外层错误标签正确；测试独有地固定 `Long.MAX_VALUE + 1` 采用 Java raw-long wrap，并把多个负/零 bucket transition 连续串起；source identity、182 tests、package/release 门禁均通过。
- **不足：** overflow 并非本闭包必须新增的策略边界，复杂 setter 用例将 wrap、负 bucket、zero transition 混在一起，诊断和可读性逊于 SOL；测试多 28 行，总 diff 多 46 行，`FABRIC_ENERGY.md`/README 重复较多逐步说明；工具调用最多。
- **本任务评价：** 执行最快并提供两个可吸收的 gate 细节，但测试/文档体积相对一个 25 行类偏重，单体质量第三。

### GLM 5.3（临时复测 1/3，thinking=low）

- **提交：** `625516c7ce33f0f15a079c88aabe31b7aa29df91`
- **规模：** 8 files，`+117/-5`；local suite 87 行、4 tests。
- **资源：** 354.0s，55.6k tokens，38 次工具调用，约 `$0.36`。
- **优点：** tokens、工具调用和已知成本均最低，墙钟略快于 SOL；文档增量是三者最短且覆盖全部延期边界；测试用 `Long.MAX_VALUE` 做不溢出的完整 bucket round-trip，并把 setter 放在独立 tracker 上避免前段状态干扰；slice/source/182 tests/package/release 全部通过，严格未改 `AGENTS.md`。
- **不足：** 87 行测试主要因每个方法同时使用长 `@DisplayName`、断言 message 和解释性注释，信息密度低于 SOL；package check 在 exact set 前又单独重复检查 `WildcardMatcher` 一次，并未复用后续 required-entry 结构或更新外层诊断标签。
- **本任务评价：** 单体质量第二；低 reasoning effort 下正确完成全部验收并表现出本轮最佳 token/调用/成本效率，是值得继续观察的正面信号，但一轮简单任务不足以恢复默认席位。

### 本任务结论

- **单体代码质量：** 5.6 SOL > GLM 5.3 > DeepSeek；**墙钟：** DeepSeek > GLM > SOL；**token/工具/已知成本：** GLM 最佳。
- **最终策略：** 采用 SOL candidate，在主线 cherry-pick 为 `830859ad`；`a1607d4d` 吸收 DeepSeek/GLM 都采用的词法 slice 顺序，并吸收 DeepSeek 的 common-lib-root 外层诊断标签；`e231cbee` 将 SOL 的 true-long 输入提升为 GLM 的 `Long.MAX_VALUE`，但继续使用 SOL 的短测试结构，不加入 DeepSeek overflow 或 GLM 冗长 DisplayName/messages。`docs/MODEL_IMPLEMENTATION_COMPARISONS.md` 的 GLM 三轮临时说明同提交写入，不改变 `AGENTS.md`。
- **综合规模：** 排除评估文档本身，8 files，`+114/-8`；local suite 58 行、4 tests。
- **主代理独立验证：** production source 与 `4646f509` 字节一致；18-file oracle、`fabric/build.gradle`、API/platform slices 和既有测试不变；slice 42/41/10 且无重复；34 suites、182 tests、0 failures/errors/skips，三个 jqwik properties 各 100,000 tries/checks；最终完整 Gradle gate 9.12s，`fabric-build.sh` 0.64s，packaged smoke 49.70s 并到达 `Done`；direct common-lib package 精确为两个 class 且无 test/fixture/NeoForge/JUnit/jqwik 泄漏；implementation clean release 11.42s，恰好一个 JAR，`build-info.json` source commit 与 `e231cbee` 一致，JAR sha256 为 `ee8b7fc16e7d95069627dde04e74366e82308090ae6980bbd2bf6bef148b0498`。

---

## 任务 014：纳入 platform-neutral common collection utilities

### 任务简述

- **基线：** `8489dbcadbf70608d019ccea59d0c2a6657416e6`
- **目标：** 原样纳入 `BiMultimap`、`EmptySequencedMap`、`HashList`、`LRU`，把既有 indexed spatial collection 扩展为不依赖 owner 的十二 class-entry common collection utility 闭包。
- **关键验收：** 四份 production source 与 `4646f509` 字节一致；slice 42/45/10，18-file oracle、build script、API/platform slices、AGENTS 与既有测试不变；最多八个本地测试覆盖双向 index、empty SequencedMap、ordered unique list 与 unique/non-null LRU 的独立状态转换；collection package 精确十二类；所有 security/frequency/filter/sync、QIO、module/gear/data component/codec、GUI/recipe/client、network/persistence/registry caller 继续延期，不声明 thread safety、concurrent mutation 或通用 mutable-view 契约。
- **GLM 临时复测：** 第 2/3 轮。主代理启动时误传通用 `medium`，随后用户说明 GLM 5.3 只支持 low/high/max，且原本想给 medium 时必须传 low；运行中已 steer 为 low scope，但底层启动参数无法追溯修改。因此本轮如实记为“配置修正样本”，不把 effort-sensitive 差异解释成干净的 low 或 medium 结论；Task015 起只传受支持档位。

### 5.6 SOL

- **提交：** `ee2a76cd23c7d680fd19e73dbf40ded80f0d8140`
- **规模：** 8 files，`+213/-16`；单一 local suite 183 行、8 tests。
- **资源：** 372.7s，72.2k tokens，38 次工具调用，约 `$1.11`。
- **优点：** 用三者约 56% 的测试行数覆盖同样八个独立 test methods；BiMultimap 预先持有 live read views 后验证插入可见性，并完整走 pair/key/value/clear；HashList 在两个方法内覆盖全部安全 replace 分支、callback 顺序、invalid swap、clone 与 ordered equality；LRU 和 empty map 边界紧凑；文档最短，精确十二类 package gate 清楚，全部门禁通过。
- **不足：** `assertEquals(new LinkedHashMap<>(), map)` 把普通 map 放在 JUnit expected 一侧，只调用 expected 的 `equals`，没有直接证明 `EmptySequencedMap.equals` 的 `SequencedMap` 类型限制；没有像 GLM 那样固定空 `HashMap` 不相等。全部四类放在一个 suite，文件级诊断不如分开四个 suite。
- **本任务评价：** 覆盖/体积/执行资源比明显最佳；唯一实质语义缺口可用两条不新增 method 的断言修复。

### DeepSeek v4.1 Flash

- **提交：** `a1e9442b5753985f96e9c3ab1981eca5184c5a60`
- **规模：** 11 files，`+361/-14`；四份 local suites 共 329 行、8 tests。
- **资源：** 388.4s，81.9k tokens，64 次工具调用；通知未提供成本。
- **优点：** 四类各自独立 suite，失败定位最直接；empty map 以被测 map 为 receiver 正确固定 empty `LinkedHashMap` equality；LRU 额外覆盖已在 head 的 `moveUp` 与非空 forward/descending iterator exhaustion；HashList 的 set membership/index 和 clone 两侧状态断言较细；全部门禁通过，墙钟只比 SOL 多 15.7s。
- **不足：** 相比 SOL 多 146 行测试、26 次工具调用和 15.7s，但多数增量是断言 message、派生 size/contains/hash 与已有状态输出的重复；没有固定 empty non-SequencedMap 的特殊 inequality，也没有形成需要整体采用的独有 production boundary。
- **本任务评价：** 诊断组织最好但测试信息密度最低；可提供 equality 方向审查，整体不宜替代 SOL 基底。

### GLM 5.3（临时复测 2/3，effort 配置修正样本）

- **提交：** `170613f404c0e2a4f43ede34de9b74f89374d1f6`
- **规模：** 11 files，`+356/-14`；四份 local suites 共 314 行、8 tests。
- **资源：** 650.6s，122.3k tokens，66 次工具调用，约 `$1.12`。
- **优点：** 三者单体语义覆盖最细：明确固定 `EmptySequencedMap` 等于 empty `SequencedMap`、不等于 empty `HashMap` 的官方特殊行为；BiMultimap 固定失败 pair removal 不破坏任一 index 且 clear 后可复用；LRU 移除到空，HashList callback 两参数顺序均清楚；测试比 DeepSeek 少 15 行，全部门禁通过，并在一次错误 reverse-key 预期后正确判断 production 无误、修复自身测试。
- **不足：** 测试仍比 SOL 多 131 行；README 新增说明最长；墙钟约 SOL 的 1.75 倍，tokens 约 1.69 倍，工具调用多 28 次。启动 effort 参数发生上述中途修正，不能把效率差异归因于某个有效 GLM 档位。
- **本任务评价：** 语义审计贡献最佳、综合单体质量第二；提供一个必须吸收的 equality 边界，但资源与体积明显逊于 SOL。本轮只作为正确信号与配置纠错记录。

### 本任务结论

- **综合代码取舍：** 5.6 SOL > GLM 5.3 > DeepSeek；**墙钟/token/工具：** SOL 最佳。
- **最终策略：** 采用 SOL candidate，在主线 cherry-pick 为 `b392a4f4`；综合提交 `99ff667e` 修正 equality receiver，并吸收 GLM 独有的 empty non-SequencedMap inequality，保留 SOL 185 行单 suite，不引入其余重复断言或文档扩张。
- **综合规模：** 8 files，`+215/-16`；local suite 185 行、8 tests。
- **主代理独立验证：** 四份 production source 与 `4646f509` 字节一致；protected surfaces 和既有测试不变；slice 42/45/10 且无重复；35 suites、190 tests、0 failures/errors/skips，三个 jqwik properties 各 100,000 tries/checks；完整 Gradle gate 9.88s，`fabric-build.sh` 2.41s，packaged smoke 38.67s 并到达 `Done`；collection package 精确十二类且无 test/fixture/NeoForge/JUnit/jqwik 泄漏；implementation clean release 11.26s，恰好一个 JAR，`build-info.json` source commit 与 `99ff667e` 一致，JAR sha256 为 `507b231f0c935f466af92148ddef4dcacd444b2a31d30ef9f1731633f4442470`。
- **后续效率调整：** 用户指出单类任务固定成本过高。从 Task015 起按同一 dependency/ownership boundary 批量纳入约 4–12 个紧密相关 production files；候选代理只跑 source/slice、编译、单测和静态 JAR package contract，真实 dedicated-server packaged smoke 与 clean release 由主代理在选定/综合版本上各跑一次。遇到平台 API、registry lifecycle、transaction 或 patched-Minecraft 语义分歧时仍立即拆分，不能为追求数量混合不相关边界。

---

## 任务 015：批量纳入 spatial/color/effect foundations（最后一次常规多实现）

### 任务简述

- **固定候选基线：** `6392702c`；候选启动后，主线另以 `dc714c21`/`20b91cbe` 切换为单 SOL 实现且默认不 watch，该政策不参与候选代码比较。
- **目标：** 一批纳入七个 byte-identical foundation sources：`Color`、`CustomEffect`、`Pos3D`、`Quaternion`、`Range3D`、`MultipartUtils`、`StatUtils`，验证更大批次是否能提高每次固定门禁的有效进度。
- **关键验收：** slice 42/52/10，七份源码与 `4646f509` 字节一致；12 个本地 tests 分别对应 channel/pack、blend/composite、HSV、ColorFunction、deterministic statistics、effect state、dimension/range、immutable vector ops、degree/order rotation、quaternion state、closest ray selection、miss/result identity 十二个独立 mutation family，因此四个超出默认八个的 tests 均有不可由其它输出替代的失效面；精确限制 direct common-lib/effect/math/MultipartUtils/StatUtils class families；不纳入任何 renderer、entity/player、transmitter、packet、machine、multiblock、registry/config/persistence caller。
- **效率门禁试行：** 候选只运行 source/slice、完整本地测试、Fabric build 和静态 JAR 检查；真实 dedicated-server smoke 与 clean release 仅由主代理在综合版本执行。

### 5.6 SOL

- **提交：** `0d2c77dca5cea667d8c9cd1f2decdf5d723a93e0`
- **规模：** 10 files，`+353/-16`；三份 local suites 共 293 行、12 tests。
- **资源：** 445.9s，105.0k tokens，60 次工具调用，约 `$1.83`。
- **优点：** 用 DeepSeek 不到一半的测试行数完成全部十二个 mutation family；Color pack/raw-unclamped、负 hue、HEAT 三段，StatUtils 的 negative-zero wrap，Pos3D matrix 与 quaternion handedness 差异，非零世界坐标 ray hit、null-slot subHit 和 strict-less tie 都是高信息密度断言；三套 suite 按 color/effect、spatial math、multipart 分层恰当；文档和 package gate 最短，完整本地门禁与静态 JAR 检查通过。
- **不足：** `randVec` 直接断言长度为一，没有容忍 vanilla `Vec3.normalize()` 对极小向量返回零的约 `4e-12` 体积概率，理论上存在极低概率 flaky；遗漏 `Pos3D.create(GlobalPos)` 与 `getPerpendicular()` 的 `z == 0` 分支；quaternion 乘法只断言不相等，没有固定两个顺序的精确分量；`FABRIC_ENERGY.md` 仍留一处历史 42/45/10 count。
- **本任务评价：** 明显最佳实现基底；所有缺口均可在现有方法内用少量断言修复，不需要采用更重测试结构。

### DeepSeek v4.1 Flash

- **提交：** `6ccf73e0952aeef00034ddfff4bc2c33a11fc456`
- **规模：** 13 files，`+735/-14`；六份 local suites 共 615 行、12 tests。
- **资源：** 561.5s，130.9k tokens，105 次工具调用；通知未提供成本。
- **优点：** 六个 suite 的文件级诊断最细；独有地覆盖 `GlobalPos` factory、perpendicular 的两个分支、quaternion 两种乘法顺序精确分量，以及 `randVec` unit-or-zero fallback；全部 count 更新正确；完整本地门禁与静态 JAR 检查通过，且严格遵守不跑 server smoke/release 的新策略。
- **不足：** 比 SOL 多 322 行测试、382 行总 diff、45 次工具调用、25.9k tokens 和 115.6s；大量 DisplayName、assert message、所有 hue/包装/equals 派生组合及 32 次随机循环重复同一 production mutation；首次误把 Gradle console 参数传给 wrapper，随后自行修复。额外体积没有形成足以整体取代 SOL 的独有边界。
- **本任务评价：** 审计补充价值高但实现信息密度明显偏低；四个独有边界应吸收，整体代码不采用。

### GLM 5.3（用户取消，非候选）

- **状态：** high effort 运行中由用户明确叫停；无模型提交。host 仅保存未完成 worktree snapshot `2e185027`，禁止整合。
- **取消时资源：** 838.3s，161.6k tokens，54 次工具调用，约 `$1.16`。
- **partial 状态：** 已验证七份 source identity、更新 slice 并写出六份/12-test 草稿；第一次完整测试有五个候选自身预期错误（subHit pre-increment、HSV 480、alpha 40/255、stored reference identity、fresh helper identity），仅部分修正；Multipart 仍失败，文档、build、静态 JAR 检查和 commit 均未完成。
- **评价边界：** 取消源于用户决定终止多实现架构，而不是给模型的失败判决；partial 不进入质量排名，也不用于 GLM/DeepSeek 替换结论。它只说明本轮在被叫停前已成为整体完成路径的延迟项。

### 本任务结论与架构决策

- **已完成候选质量与效率：** 5.6 SOL > DeepSeek；GLM partial 不排名。
- **最终策略：** 将 SOL candidate cherry-pick 为 `bba5824b`。综合提交 `dd170288` 吸收 DeepSeek 的 `GlobalPos` factory、`z == 0` perpendicular、quaternion 精确乘法分量，并把随机向量断言改成 unit-or-vanilla-zero fallback；`39a9cf36` 修正 SOL 遗漏的旧 slice count。保留 SOL 的三套短测试和精炼文档。
- **综合规模：** 排除独立代理架构政策文档，10 files，`+360/-17`；三份 local suites 共 299 行、12 tests。
- **主代理独立验证：** 七份 production source 与 `4646f509` 字节一致；protected surfaces/既有测试不变；slice 42/52/10 且无重复；38 suites、202 tests、0 failures/errors/skips，三个 jqwik properties 各 100,000 tries/checks；完整 Gradle gate 10.12s，`fabric-build.sh` 4.05s；最终版本才运行的 packaged smoke 38.22s 并到达 `Done`，证明候选阶段省去了两次约 38 秒 server startup；clean release 11.40s，恰好一个 JAR，所有五组 task class family 精确且无 test/fixture/NeoForge/JUnit/jqwik 泄漏，`build-info.json` source commit 与 `39a9cf36` 一致，JAR sha256 为 `d1e42185e6bdebcd32e98694a64920adb64f7995e528ab3e49530e0191c83f68`。
- **最终默认架构：** 用户认可停止常规多实现，并在 Task015 运行中立即取消 GLM。任务 016 起只由 SOL 在隔离 worktree 实现，默认不 watch；主代理审查/综合并运行最终门禁。DeepSeek、GLM 或其它模型只在已识别的高风险平台、patched、registry、transaction、network、serialization、thread/owner 边界中按需做只读 adversarial review。此架构选择使“GLM 替换 DeepSeek”的问题失去必要性，二者都不再是常驻实现代理。

---

## 任务 019：signed-long TOML/config 边界（风险触发审查）

- **固定基线与范围：** `1165abd3`；SOL 增加 `MekanismConfigSpec` signed-long schema、仅替换两个 import 的 `CachedLongValue`，并原样纳入 `EnergyCubeTier`、`InductionCellTier`、`InductionProviderTier`。最终 slice 42/74/10；六个新增测试方法。
- **SOL 初始候选：** `300011fa`，460.1s、135.5k tokens、56 次工具调用、约 `$2.08`。source/slice、228 tests、build 与静态 JAR 门禁通过，但其 `Float.toString`/`BigDecimal.valueOf` 规范化没有按 exact IEEE 值转换，且未发现 Night Config 3.8.3 对超出 signed-long 的 TOML integer 做 modulo-2^64 wrap。
- **DeepSeek 第一次只读审查：** 257.9s、87.0k tokens、43 次工具调用。以真实 pinned parser 复现 `9223372036854775808 -> Long.MIN_VALUE`、`-9223372036854775809 -> Long.MAX_VALUE`、`18446744073709551616 -> 0`，并指出 overflow assertions 只落在 `[-10,10]` 会产生 range-only 假阳性；结论 `ACCEPT WITH FIXES`，阻止不安全候选进入主线。
- **SOL 修复：** corrective `e487b939`（修复运行 407.1s、84.7k tokens、34 次工具调用、约 `$1.46`）改用 exact IEEE `BigDecimal` 构造，并在 Night Config 前增加 decimal/hex/octal/binary integer lexical preflight，使 overflow 进入既有 `.invalid` 备份→默认文档持久化→一次性发布路径；测试保持六方法并移到 full-range 验证。
- **DeepSeek 第二次只读审查：** 365.8s、104.6k tokens、56 次工具调用。再次用有效 TOML 证明四引号 multiline string 可令 scanner 失步并重新暴露 wrap，同时发现 whitespace-around-dot numeric key 误报与 nested-array 潜在漏检；仍判 `ACCEPT WITH FIXES`。这三项均由主代理提交 `7da25680` 修复：multiline closing delimiter 吞并最多两个合法内容 quote、single-line string 禁止跨行、`.` 识别为 dotted-key continuation，并区分 line-leading table header 与 nested value array；同一测试方法加入 literal/basic 四引号、dotted numeric key 与 nested-array overflow 回归。
- **最终取舍：** 保留 SOL 的完整实现与测试结构，但没有采信“候选 gates 通过即正确”；两轮 adversarial review 均发现可执行的高严重度反例，审查价值明确。主线序列为 `e0fe72d9`、`26fccb9f`、`7da25680`。
- **主代理独立验证：** 三个 tier source 与 `4646f509` 字节一致，`CachedLongValue` 精确只有两个 import 替换；42/74/10 无重复；46 suites、228 tests、0 failures/errors/skips，三个 jqwik properties 各 100,000 tries/checks；完整 Gradle gate 19.94s，`fabric-build.sh` 2.96s，packaged smoke 37.21s 到达 `Done`；implementation clean release 12.44s，恰好一个 JAR，config-value 五项与 tier 六项精确且无 NeoForge/test/fixture 泄漏，`build-info.json` source commit 为 `7da25680`，JAR sha256 `c44ce83bada8428a26d05ff7c372c74c88b0bb5b08b4e222aa8b5adad83b9ab0`。

---

## 任务 023：Team Reborn Energy transaction boundary（风险触发审查）

- **固定基线与范围：** `ee31a91f`；一个 byte-identical `IEnergyConversion`，以及四个新 Fabric adapter：`FabricEnergyConversions`、`FabricStrictEnergyHandler`、`FabricEnergyStorage`、`FabricEnergyLookup`。引入 external、非 nested 的 Team Reborn Energy 4.1.0；最终 slice 43/88/14、五个 suite 中精确八个新增测试方法。owner/provider registration、cables/networks/machines、FE config activation、persistence/networking/thread ownership 全部延期。
- **SOL 初始候选：** `409cde29`，1844.4s、311.9k tokens、148 次工具调用、约 `$10.19`；候选 gates 通过但不可集成。DeepSeek 首轮只读审查（1513.3s、406.3k tokens、101 次调用）实证 side-bound self-unwrapping 可跨方向、64/256+halving 量化会 false-zero/非单调/严重少传、execute 后 count guard 会阻止 rollback snapshot 读取，另指出 holder support、close lifecycle 与 no-op `setEnergy` backing 风险。
- **第一轮 SOL 修复：** `b7f2109c` 对应修复运行 1670.6s、318.6k tokens、114 次调用、约 `$8.70`，修复 side/live policy/lifecycle/count rollback，并换成 continued-fraction seed + verified power-of-two family。DeepSeek 第二轮审查（656.9s、178.3k tokens、69 次调用）仍以 `372965497689.465` 证明 bare-convergent seed 可永久 zero，以 `10.906989658246147`/`575.403` 证明高 bound 利用率仅 0.62%/0.10%，并发现 capacity shrink 可令 `setEnergy` 静默 under-restore。
- **第二轮 SOL 修复：** `af468743` 对应运行 1521.9s、256.0k tokens、128 次调用、约 `$7.95`；建立固定 10-bit dyadic mesh + 每个 canonical-decimal convergent denominator 的 1..64 multiplier/binary-shift closure，加入 named-rate ≥99% 回归，restore 后逐 index read-back，count-drift query 返回 false/zero。DeepSeek 第三轮审查（2060.1s、418.8k tokens、124 次调用）确认前述正确性缺陷关闭，但量化每次可能重新执行 21k–69k probes，热路径在非 terminating rate 下达到约 0.05–0.5ms/search，构成可扩大的 tick 风险；其“family bound-dependent”判断经主代理按集合定义复核后撤销，不作为缺陷。
- **第三轮 SOL 性能修复：** `0a222e0b` 对应运行 1129.1s、426.2k tokens、85 次调用、约 `$6.40`；四槽 synchronized weak-identity cache 按 conversion `==` + raw rate bits 发布 immutable primitive snapshot，cold build 有界，warm selection 变为 `O(log U)` 并逐次 owner-method 复验，不增加第五个/nested class。DeepSeek 第四轮审查（2933.2s、683.4k tokens、165 次调用）实测 warm forward+reverse 从约 1760ns 降至约 97–122ns，生产 2.5 cold build 约 3.6–3.9ms，确认热路径阻断已消失；同时发现 same-bit behavior drift 在旧 snapshot 无 in-window pair 时会永久 zero。
- **主代理综合与终审：** 主线依次落地 `7805f671`、`5ecb7416`、`f9ba1db4`、`a044351c`。`02706151` 增加 empty-window 首 pair sentinel/invalidation 回归，并明确 exact-1 是 authoritative identity contract；最终短审查（199.2s、68.2k tokens、27 次调用）确认 offset/rate/race/loop/test-count 正确，只要求把 sentinel 从“完整检测”收窄为单 pair mitigation。`0c62c33e` 完成该文档边界：zero-pair snapshot 无 sentinel，cache 要求 advertised rate bits 不变时 owner behavior 稳定；documented stable conversion 下才声明 component-wise monotonicity。
- **资源结论：** 四轮 SOL 实现/修复合计约 6166.0s、1.313M tokens、475 次调用、已知成本约 `$33.24`；五轮 DeepSeek 审查合计约 7362.7s、1.755M tokens、486 次调用（通知未提供成本）。该任务证明风险审查能阻止多个 gates-green 但事务/数值语义错误的候选，也证明过宽的首个 transaction bridge 会造成极高迭代成本；不据此改变常规任务的单 SOL/no-watch 架构。
- **主代理独立验证（实现 HEAD `0c62c33e`）：** focused 8 tests 6.64s；clean full gate 10.84s，54 suites、250 tests、0 failures/errors/skips；`fabric-build.sh` 4.03s；packaged dedicated-server smoke 37.21s，Mekanism 与 `team_reborn_energy 4.1.0` 均加载并到达 `Done`；clean release 14.80s。slice 43/88/14；JAR 中 Fabric energy 精确四类、API energy 精确五类、无 `team/reborn/**` 或 test class；恰好一个 JAR，sha256 `fe96b32c470b544772569d5e27aafcd9bba0d92fdf9e65eeff6d3a0be74944e7`，`build-info.json` source commit 为 `0c62c33e`。最终文档提交后须重跑 clean release，使 source commit 与最终 HEAD 对齐。

---

## 任务 025：primitive cached config values（风险触发审查）

- **范围与 SOL 候选：** 基线 `febfabdd`；`CachedBooleanValue`、`CachedByteValue`、`CachedShortValue`、`CachedDoubleValue` 各自只替换一个 `ConfigValue` import，`MekanismConfigSpec` 增加 strict boolean 与 exact-before-narrow byte/short builder API。候选 `73b1a0df`，17 files、`+480/-29`，907.8s、320.6k tokens、115 次工具调用、约 `$5.59`；候选 focused4/full258/build/static gates 通过。
- **DeepSeek 只读审查：** 1257.7s、291.1k tokens、119 次调用（通知未提供成本）。用真实 Night Config 3.8.3、独立101断言和21个 mutation 验证 import-only、radix/overflow/fraction/nonfinite、先校验后窄化、`-0.0`、correction-persist-before-publish 与 package/count；未发现 production defect。审查指出 byte listener changed/unchanged 与 loaded-path `getOrDefault` 文档声称但测试不足，以及 byte/short overload 的 Java literal 类型要求。
- **最终取舍：** 主线集成为 `afe8a089`；`7d3be211` 在原四方法内补齐 byte invalidation、四类型 loaded-path `getOrDefault`，并记录 default/min/max 必须同为 byte/short primitive，否则 Java 会选择 int overload。generic/resolvable/list/map/enum、真实 config activation、sync/UI/watcher/owner 均延期。
- **主代理独立验证：** 四份 wrapper 均精确 one-import-only；slice47/92/14；focused4 tests 4.10s；clean full gate10.73s，56 suites/258 tests/0 failures/errors/skips；build2.74s；packaged smoke41.36s并到达`Done`；implementation clean release12.10s。JAR config-value family精确9类，无 test/Team API 泄漏；一个 JAR sha256 `e44e057e70418d3baee05ffb369d9e2ee7ee6f5cb6f743664e2af9d75743017b`，build-info source `7d3be211`。最终文档提交后重跑 release 对齐 source commit。

---

## 任务 028：deferred codec/criterion/loot registration（风险触发审查）

- **固定基线与范围：** `68ce7b86`；原样纳入 `DeferredMapCodecHolder`、`DeferredMapCodecRegister`、`DeferredCriterionTrigger`、`LootFunctionDeferredRegister`，并把既有 Fabric `MekanismDeferredHolder.value()` 返回类型从 `R` 修复为 covariant `T`，方法体仍为 `return get();`。最终 slice **47/103/14**，一个本地 suite 恰好四个 tests；production declarations、global registry mutation/freeze、datapack codec execution、serialization、loot/advancement runtime 与 owner/threading 均延期。
- **SOL 候选：** `10e97682`，9 files、`+325/-9`，933.5s、201.4k tokens、135 次工具调用、约 `$4.77`。四个新增 allowlist source 与 `4646f509` 字节一致；候选证明 function/supplier codec laziness、custom holder、canonical binding、duplicate/lifecycle、criterion trigger/instance identity，以及 loot basic/codec identity；269 tests、build 与静态 package gates 通过。
- **GLM 5.3 只读审查（`thinking=high`）：** 582.8s、119.5k tokens、77 次调用、约 `$1.02`；结论 **ACCEPT**。独立 `javap` 证明 `R`/`T` 均擦除为 `Object`、descriptor 不变且无额外 `ACC_BRIDGE`；确认 typed-holder invariant 未增加新 unchecked hole；核对 Fabric registrar canonical return、duplicate/late lifecycle、vanilla criterion default method、Bootstrap 幂等且无 custom/built-in entry mutation、loot codec identity、测试非空洞、精确 package family 和文档边界，未发现 required fix。
- **最终取舍：** 主线 fast-forward 采用 SOL `10e97682`，无需综合修复。该任务也是 DeepSeek 额度耗尽后，按用户要求改用 GLM 5.3 high 做风险审查的首个样本；单任务 ACCEPT 不形成永久模型质量排名。
- **主代理独立验证：** focused4 tests 8.02s；full gate 9.67s，59 suites/269 tests/0 failures/errors/skips；`fabric-build.sh` 4.33s；packaged smoke 42.54s，Mekanism 与 external `team_reborn_energy 4.1.0` 均加载并到达 `Done`；release contract 0.79s。JAR direct registration 精确6项、impl精确3项，无 test/NeoForge 泄漏；一个 JAR sha256 `42aeadbd68712d478b9e5c923c9a6a9ec5132b20a12e9c26d0fb17165abef815`，build-info source `10e97682`。最终文档提交后须重跑 release 对齐 source commit。

---

## 任务 029：transport/tank tier metadata（风险触发审查）

- **固定基线与范围：** `937c9e27`；纳入 `CableTier`、`ChemicalTankTier`、`ConductorTier`、`FluidTankTier`、`PipeTier`、`TransporterTier`、`TubeTier`。五个 lookup enum 只以自身 `values()` 替代 `EnumUtils` cached array；四个 NeoForge fluid-constant users 只把 compile-time `FluidType.BUCKET_VOLUME` 替换为 integer literal `1_000`。该值保持 Mekanism internal/config mB metadata，不使用 Fabric Transfer 的 81,000 droplets；fluid/chemical adapter 与全部 owners/callers 延期。最终 slice **47/110/14**，`TierMetadataTest` 新增恰好七方法、总计十二。
- **SOL 候选：** `13eab7b1`，17 files、`+429/-76`，1062.7s、255.5k tokens、141 次工具调用、约 `$6.69`。七份 production diff 严格落在指定 import/token/loop adaptation；完整 metadata、lookup/fallback/name/color 与每个 mutable config reference 的 loaded/live/restorable behavior 均有测试；276 tests、build、static exact-13 tier package gates 通过。
- **GLM 5.3 只读审查（`thinking=high`）：** 618.9s、111.8k tokens、75 次调用、约 `$1.45`；结论 **ACCEPT**。从 pinned NeoForge 21.1.200 source 确认 `BUCKET_VOLUME` 是 compile-time `int 1000`，并把 oracle/candidate 以相同 stubs 编译后做 normalized `javap` 对照：七 enum 除五个 `get()` 的 array source 外，constructor/constants/getters 指令等价；Pipe `/4`、creative maxima、int/long 表达式均精确。审查确认 tier/config 是 mB domain、future conversion 应留在 adapter boundary，`values()` 内容/顺序/null/fallback 等价且当前无 production caller，测试恢复与 package/docs 边界正确；无 required fix。
- **最终取舍：** 主线 fast-forward 采用 `13eab7b1`，无需综合修复。`values()` 每次创建 4-element clone，相比共享可变 `EnumUtils` snapshot 有微小分配但避免共享数组被改写；当前无 in-slice production caller，审查认为可接受。
- **主代理独立验证：** focused12 tests 6.35s；full gate 9.80s，59 suites/276 tests/0 failures/errors/skips；`fabric-build.sh` 6.11s；packaged smoke 41.44s 并到达 `Done`；release contract 0.79s。JAR direct tier family 精确13项，无 nested/test/helper/NeoForge 泄漏；一个 JAR sha256 `97fbfdcee91d722e8484a6d545ada4b99a95773db3bf006145b14b50b3e6f1c8`，build-info source `13eab7b1`。最终文档提交后须重跑 release 对齐 source commit。

---

## 任务 030：generic/list-backed cached config（风险触发审查）

- **固定基线与范围：** `03bf90cb`；纳入 `CachedResolvableConfigValue`、`CachedConfigValue`、`CachedMapConfigValue`、`CachedOredictionificatorConfigValue`。前三源仅替换一个 NeoForge `ConfigValue` import，Orediction 源仅替换两个 imports 与 Builder type；`MekanismConfigSpec` 新增唯一 strongly typed `defineListAllowEmpty`。最终 slice **47/114/14**，一个新 suite 恰好四个 tests；`GeneralConfig`/`MekanismConfig`、cardboard/Orediction owners、`CachedEnumValue`、UI/watch/sync/lifecycle 均延期。
- **SOL 候选：** `e63b783a`，16 files、`+539/-37`，1371.4s、292.7k tokens、137 次工具调用、约 `$8.38`。list schema 对 default/load/set 进行 immutable top-level copy，整表拒绝 invalid element，复用既有 correction-persist-before-publish 与 synchronized update；测试固定 generic/cache/null sentinel/map ordering/Orediction locale 及官方 resolved-map set alias；280 tests、build、static exact-14 package gates 通过。
- **GLM 5.3 只读审查（`thinking=high`）：** 677.7s、118.9k tokens、80 次调用、约 `$1.27`；结论 **ACCEPT**。独立验证 pinned NeoForge 四参数 API shape 和四份 source exact adaptation，并以额外 probes 覆盖 mixed/nested lists、fresh/already-loaded write failure、two-value correction、immutable instance、TOML integer preflight 与 warm-cache unload。确认 whole-list rejection、default once、new-element supplier uncalled 是任务规定且已文档化的 Fabric 差异；encoded list immutable 与官方 caller-owned resolved Map alias 区分正确，无 required fix。
- **环境发现：** 本机 PATH 中裸 `diff` 指向会对不同文件错误返回“无差异”的 OpenHarmony SDK 程序。审查已用 `/usr/bin/diff`、`cmp` 与 hashes 重做证据；后续源码比较禁止信任裸 `diff`，`git diff` 不受影响。
- **最终取舍：** 主线 fast-forward 采用 `e63b783a`，无需综合修复。可选未落地项只有把 reviewer 已实证通过的 fresh-spec write-failure 场景也加入 shipped tests；现有测试已覆盖 already-loaded failure 且生产 pipeline 是既有行为，不为此增加第五个测试。
- **主代理独立验证：** focused4 tests 4.39s；full gate 9.36s，60 suites/280 tests/0 failures/errors/skips；`fabric-build.sh` 3.95s；packaged smoke 38.50s 并到达 `Done`；release contract 0.76s。JAR config-value family 精确14项，无 test/helper/NeoForge 泄漏；一个 JAR sha256 `bd75a2af5c6491bcf3062276dcd62aa704fd198dbd01e403bd71201a053537a7`，build-info source `e63b783a`。最终文档提交后须重跑 release 对齐 source commit。

---

## 任务 031：sound/damage declaration metadata（风险触发审查）

- **固定基线与范围：** `f146e0ce`；纳入 `SoundEventDeferredRegister`、`SoundEventRegistryObject`、`DamageTypeRegister`、`MekanismDamageType`、`MekanismSounds`、`MekanismDamageTypes`。最终 slice **47/120/14**；完整 sound 表是一个 registrar owner + **46** holders，共47个 public declarations，damage 表四项。真实 owner registration/datapack/playback/combat/Level/entity 全部延期。
- **SOL 首轮阻断：** 648.6s、192.4k tokens、93 次调用、约 `$3.14`；严格 byte-identical 约束下编译发现 `RegistryAccess.holderOrThrow(ResourceKey)` 是 NeoForge 注入 API，Fabric/Mojang 不存在，因此清理全部草稿、无提交。主代理随后读取 pinned NeoForge 21.1.200 `IHolderLookupProviderExtension`，确认其实现恰为 `lookupOrThrow(key.registryKey()).getOrThrow(key)`，据此精确修订范围。
- **SOL 修正版：** `4c4d0666`，16 files、`+482/-37`，1034.8s、277.8k tokens、165 次工具调用、约 `$7.69`。`SoundEventRegistryObject`/`DamageTypeRegister` byte-identical；sound register 改接已审计 deferred base/holder factory，两个 owners 只使用 literal namespace，damage holder 只展开上述 vanilla chain。五个测试覆盖 fresh sound lazy/canonical/range/lifecycle、47-declaration owner table、damage overload/live duplicate/metadata，以及 disposable local frozen damage registry；285 tests/build/static gates 通过。
- **GLM 5.3 只读审查（`thinking=high`）：** 699.4s、184.5k tokens、78 次调用、约 `$1.15`；结论 **ACCEPT**。从 pinned source 与 vanilla bytecode证明扩展/展开在 registry/key missing exception 和 exact Holder identity 上一致，且正确避免语义不同的 `registryOrThrow`；确认 specialized holder 的两个 compiler bridge 无递归、46 holders 全覆盖、owner class-init 不创建/注册 SoundEvent、local MappedRegistry 不污染 global、Bootstrap 幂等、package/docs 边界完整，无 required fix。
- **最终取舍：** 主线 fast-forward 采用 `4c4d0666`，无需综合修复。首轮阻断证明 source byte-identical 不能代替平台注入审计；修订只承接已证实的 vanilla 等价表达式，没有新增通用 shim。
- **主代理独立验证：** focused5 tests 10.66s；full gate 11.71s，61 suites/285 tests/0 failures/errors/skips；`fabric-build.sh` 5.62s；packaged smoke 45.24s 并到达 `Done`；release contract 0.86s。JAR registration/impl 精确7项、registries精确3项，无 test/helper/NeoForge 泄漏；一个 JAR sha256 `6fdbf366e8cda78a5920436a9aeb9374a83417731a7b196ea8d0ece34433d04a`，build-info source `4c4d0666`。最终文档提交后须重跑 release 对齐 source commit。

---

## 任务 032：module config API closure（风险触发审查）

- **固定基线与范围：** `56003d7c`；纳入 `ModuleConfig`、`ModuleBooleanConfig`、`ModuleColorConfig`、`ModuleEnumConfig`、`IHasModeIcon`、`IHasTextComponent`。前三者中的 Boolean/Color/ModeIcon 与 oracle 字节一致；其余只做 literal namespace、enum stream codec 和 `TranslatableEnum` 父型移除三处有界适配。最终 slice **53/120/14**；`ModuleData`、containers/items、codec catalog、GUI/client、packet/network owners、persistence/registration/lifecycle 与全部 callers 延期。
- **SOL 候选：** `2faf7a1a`，11 files、`+511/-26`，1057.4s、507.8k tokens、121 次工具调用、约 `$8.26`。一个 same-package suite 恰好七个 tests，覆盖 legacy name rewrite、Boolean/Color data 与 wire、enum selectable/data/supplied/named codecs、bounded changed-`with`、equality/text/icon；292 tests、build 与静态 package gates 通过。
- **GLM 5.3 只读审查（`thinking=high`）：** 737.8s、136.3k tokens、85 次调用、约 `$1.45`；结论 **ACCEPT**。从 pinned NeoForge 21.1.200 source 和 vanilla bytecode证明 `readEnum/writeEnum` 与 local `ByteBufCodecs.VAR_INT` + direct constants indexing 在 valid bytes、negative/high AIOOBE、malformed varint、null encode、singleton identity 与 declaring class 上等价；确认三个 unchanged source、其它 exact bounded deltas、generic descriptors、无 packaged anonymous class、官方 bounded/unbounded/RGB/ARGB/equality 语义、精确5+2 package family 和完整 deferral，无 required fix。
- **最终取舍：** 主线 fast-forward 采用 `2faf7a1a`，无需综合修复。reviewer 唯一可选项是上游遗留 Javadoc 仍写 nested interface implements Neo `TranslatableEnum`；这不影响签名/运行语义，项目 docs 已明确兼容性丢失，为保持 production delta 严格有界暂不改源注释。captured constants 与每次重新获取 constants 仅在不支持的运行时 enum bytecode mutation 下可观察。
- **主代理独立验证：** focused7 tests 6.49s；full gate 9.75s，62 suites/292 tests/0 failures/errors/skips；`fabric-build.sh` 5.19s；packaged smoke 44.40s 并到达 `Done`；implementation release contract 0.74s。JAR direct gear-config 精确5项、`IHasTextComponent` outer+nested 精确2项，无 test/helper/NeoForge 泄漏；一个 JAR sha256 `00c9d81055c0dea4655a79fea699968416c84b37d8487cee9297b9c05a79eadd`，build-info source `2faf7a1a`。最终文档提交后须重跑 release 对齐 source commit。

---

## 任务 033：item inventory → Fabric Transfer gateway（风险触发审查）

- **范围选择：** 两轮 bounded leaf scout 均 BLOCK 后，不再 padding 单类，转向 Crusher 直接前置。Plan 用 364.6s、180.6k tokens、146 次调用、约 `$2.77` 比较 item/chemical/fluid/gameplay-network/registry-owner，选择 item inventory。最终 production scope 为 API `IInventorySlot`/`ISidedItemHandler`/byte-identical `IMekanismInventory`、byte-identical 两个 slot holder，以及 project-owned `FabricItemStorage`/`FabricItemLookup`；slice **56/122/16**。provider/cache owner、reverse adapter、concrete slots、persistence、recipes/CachedRecipe、tiles/Crusher 均延期。
- **SOL 初始候选：** 原始 `efe9c810`（主线等价 cherry-pick `501db118`），20 files、初始 `+1561/-108`，1823.5s、360.4k tokens、232 次调用、约 `$12.96`。生产实现建立 physical-side stable views、live policy/topology invalidation、true-long ordered distribution、ItemVariant components 和全 slot snapshot/nested rollback；八个新 tests 与静态 gates 通过，但候选错误地把 `fabric-loader-junit` 放入普通 test runtime。
- **GLM 5.3 首轮只读审查（`thinking=high`）：** 1038.3s、193.3k tokens、120 次调用、约 `$2.20`；结论 **BLOCK**。主代理先发现预期64 suites/300 tests 实际只有63/297；GLM 在临时副本只移除 loader-junit 后证明缺失正是 `DistributionPropertyTest` 三个各100,000 tries/checks 的 jqwik properties，同时 ItemVariant tests 因无 Knot mixin 失败，建立直接因果。另要求撤销 common-slice 86行无关排序，并把 SIMULATE 非变异表述改为 backing-slot 前置条件。其反例/probes 接受并关闭生产 API、variant/components、capacity、long/int、nested/outer rollback、mismatch/all-slot restore、policy/topology/invalidation 与 exposure 语义。
- **SOL 修复：** 新隔离实现运行 1212.9s、226.3k tokens、161 次调用、约 `$6.90`，形成 corrective `e5880d53`：只把 item transaction suite 移到独立 `fabricItemTest` source set/task，Knot runtime 仅在该任务；普通 test 恢复 jqwik；`check`/`fabric-build.sh` 各运行两任务一次；common slice 恢复为 baseline +2/-0；纠正文档/Javadoc，无 production 行为改动。最终总 diff 20 files、`+1547/-66`。
- **GLM 聚焦复审：** 502.9s、72.8k tokens、49 次调用、约 `$0.54`；结论 **ACCEPT**。临时 archive 实证普通 test63 suites/296 tests（含三项100k properties），Knot item task1 suite/4 tests，aggregate64/300；classpaths 隔离、check/build wiring、slice +2、SIMULATE 前置条件、production corrective zero-code delta、JAR 无 loader/test 泄漏全部闭合，无 required findings。
- **最终取舍：** 主线 fast-forward 到 `e5880d53`。保留显式 all-slot restore/hard failure 与 nonrollback listener 说明；不为当前无 owner 的边界增加 speculative cache/provider。底层 slot 违反 `Action.SIMULATE` 时的副作用不由 adapter 回滚，已作为明确调用前置条件。
- **主代理独立验证：** focused API4 tests 11.44s；Knot item4 tests 10.80s；ordinary full9.93s 为63 suites/296 tests，三项 properties 各100,000 tries/checks，aggregate64 suites/300 tests，无 failure/error/skip；`fabric-build.sh`4.76s；packaged smoke39.61s 并到达`Done`；implementation release0.80s。JAR inventory API精确3、slot holder2、Fabric item outer+nested3，无 test/NeoForge/loader-junit 泄漏；一个 JAR sha256 `37f6b1c270e72947ce74947d5335c890eb82692b9b343e3297dd4e9d3ad22880`，build-info source `e5880d53`。最终文档提交后须重跑 release 对齐 source commit。

---

## 任务 034：Mekanism-owned fluid value/unit gateway（风险触发双审查）

- **范围选择：** concrete-slot scout（345.9s、85.3k tokens、64 次调用、约 `$0.77`）证明 `BasicInventorySlot` 闭包仍被 `SerializerHelper` 的 NeoForge FluidStack 公开面及 menu/warning graph 阻塞。Plan（352.1s、149.9k tokens、79 次调用、约 `$2.38`）因此选择项目自有 `mekanism.api.fluid.FluidStack`，保留 int mB、components、codec/NBT/wire 语义，并以唯一 pure `FabricFluidConversions` seam 映射 `1 mB = 81` Fabric Transfer units；拒绝 FabricVariant-in-domain 和通用 NeoForge facade。最终 production 只有这两份 source，slice **57/122/17**；tank/storage/transaction/provider、FluidType attributes、SerializerHelper 及 197 个 caller replacements 全部延期。
- **SOL 初始候选：** `59911048`（主线等价 cherry-pick `31835cc0`），19 files、`+1381/-52`，2640.2s、734.6k tokens、276 次调用、约 `$17.51`。实现 exact int/raw-empty/component/copy/matching、JSON/NBT/VAR_INT-first stream surface，转换层拒绝 negative、non-divisible、overflow 与 flowing normalization；把 Task033 Knot suite byte-identical 泛化到 `fabricTransferTest`。候选 ordinary64/304、transfer2/8、aggregate66/312，build/static gates 通过，但错误采用不存在语言键的 `fluid.*` description，并遗漏 NeoForge nominal parent 提供的八个 platform-neutral component overloads。
- **GLM 5.3 双独立审查（`thinking=high`）：** conversion/Knot/build 审查 687.6s、136.6k tokens、56 次调用、约 `$1.10`，结论 **ACCEPT**；从 pinned Fabric Transfer source 与 live Knot probes 验证 factor81、max `173946175407`、patch/no-alias、blank/flowing、校验顺序、classpaths、package/static gates，无 required fix。API/codec 审查 884.0s、162.0k tokens、86 次调用、约 `$1.54`，结论 **ACCEPT WITH FIXES**；用 vanilla language table、pinned NeoForge FluidType registrations 与 compiled surface 证明 `fluid.minecraft.*` 不可翻译，并准确列出 Supplier set/remove、四个 update、两个 copyFrom 缺口，其余 codec/wire/provenance 全部接受。
- **SOL 修复：** corrective `9b790d49` 对应隔离提交 `68ed21de`，680.2s、152.0k tokens、97 次调用、约 `$3.33`。description 改为 default fluid state 的 legacy block key，water/lava/empty 固定 `block.minecraft.water`/`block.minecraft.lava`/`block.minecraft.air`；八个 overload 以 oracle-identical generics/return semantics 恢复，copyFrom 对 source 缺值清 target，并在单一新增测试方法覆盖全部 families。最终总 diff 19 files、`+1486/-52`，ordinary64/305、transfer2/8、aggregate66/313。
- **GLM 聚焦复审：** 889.3s、112.8k tokens、104 次调用、约 `$1.50`；结论 **ACCEPT**。对 pinned `MutableDataComponentHolder` 逐签名、compiled `javap`、runtime previous-value/default/absent-clearing、block IDs、文档、scope 与 package gates 复核，确认两项 required finding 全部关闭。唯一信息项为 oracle 本身两个 varargs overload 的 zero-arg ambiguity/heap-pollution warning，不是移植回归。
- **最终取舍：** 主线采用 `31835cc0` + `9b790d49`。保留 Mekanism-owned mB domain 与 explicit conversion seam；不把本批包装为 fluid storage/transaction/owner 集成。LGPL pinned oracle 仅用于行为与签名核对，独立实现和来源 hashes 已记录在 `FABRIC_FLUID.md`。
- **主代理独立验证（实现 HEAD `9b790d49`）：** focused FluidStack9 tests 9.34s；ordinary full11.44s 为64 suites/305 tests，三项 jqwik properties 各100,000；Knot transfer9.14s 为2 suites/8 tests，aggregate66/313，无 failure/error/skip；`fabric-build.sh`5.32s；packaged smoke43.45s 到达`Done`；implementation release0.78s。JAR 精确一个 `FluidStack.class` 与一个 `FabricFluidConversions.class`，无 nested/test/NeoForge/loader leakage；恰好一个 JAR sha256 `96a3dbfce9e934b3f1c9dc74dd3ba073949b9364999a0b8b8adfc2c7c7777f2a`，build-info source `9b790d49`。最终文档提交后须重跑 release 对齐 source commit。

---

## 任务 035：shared serialization ownership（风险触发审查）

- **范围选择：** concrete-slot scout（462.4s、177.6k tokens、103 次调用、约 `$2.47`）再次证明 FluidStack 只解除一个 import：真实 slot 闭包仍跨 menu/window、warning/translation、ChemicalStack/TriPredicate 与 recipe owner，11-source 表面集合的一层直接下界约20。Plan（1753.7s、285.7k tokens、146 次调用、约 `$7.59`）比较四条架构入口后，选择恢复 `SerializerHelper` shared owner，并让 `IInventorySlot` 删除 Task033 private duplicated codec。该两 touched files/一新 source 是明确的小批例外，优于用无关 leaves 凑足4项；其余 owner 均继续延期。
- **SOL 候选：** `71fa2e33`（主线 cherry-pick `b68cb277`），12 files、`+489/-78`，1151.5s、380.6k tokens、110 次调用、约 `$7.73`。`SerializerHelper` 保留11个 public codec fields、6个 public methods及 oracle long/item/owned-fluid/oversized/conditional/stringify 行为；只替换 FluidStack import，并以 `LoggerFactory.getLogger("mekanism.api.MekanismAPI")` 保持原 category/ERROR/templates而不初始化未完成 registry facade。`IInventorySlot` 只恢复 shared `saveOversized` delegation。一个新 ordinary suite 恰好5 methods；slice58/122/17，候选67 suites/318 tests、build/static package gates 通过。
- **GLM 5.3 只读审查（`thinking=high`）：** 639.3s、112.2k tokens、60 次调用、约 `$1.01`；结论 **ACCEPT**。normalized oracle diff 只含三类授权适配，compiled surface 精确11+6；独立验证 legacy unsigned/decimal oddities、item/fluid partial fallback与 exact diagnostics、oversized NBT/components、conditional truth tables、logger SLF4J→log4j2 route、无 `MekanismAPI` type reference/class init/registry creation，以及 IInventorySlot 的 shared codec 等价。确认 package outer+$1/$2/$3、startup/static gate、test counts与三项 jqwik100k，无 required findings。
- **最终取舍：** 主线采用 `b68cb277`。共享 codec singleton 比每次构造 private lazy wrapper 更接近 oracle 且无状态；同名 logger category 是为了隔离未完成 public owner 的最小内部适配，不建立 logger-only `MekanismAPI` facade。ChemicalStack、ConstantPredicates、concrete slots、menu/window、warning/translation、recipes 与 registry/config/lifecycle 全部仍延期。
- **主代理独立验证（实现 HEAD `b68cb277`）：** focused SerializerHelper5+InventoryApiHolder4 为2 suites/9 tests、10.55s；ordinary full11.00s 为65 suites/310 tests，三项 jqwik properties 各100,000；Knot transfer8.73s 为2 suites/8 tests，aggregate67/318，无 failure/error/skip；`fabric-build.sh`4.91s；packaged smoke39.67s 到达`Done`；implementation release0.71s。JAR SerializerHelper 精确 outer+$1/$2/$3，各一次，无 `MekanismAPI.class`、test/NeoForge/loader/startup leakage；一个 JAR sha256 `48e14b47241fa3e7dde177dfff6fcb6d163e4ef412a63cba7aadd3dd8adec8fa`，build-info source `b68cb277`。最终文档提交后须重跑 release 对齐 source commit。

---

## 任务 036：portable text/color SCC（风险触发审查）

- **范围选择：** Task036 scout（541.8s、209.2k tokens、122 次调用、约 `$3.24`）找到真实连接的8-source closure：新纳入 `IIncrementalEnum`、`IHashedItem`、`APILang`、`EnumColor`、`ILangEntry`、`TextComponentUtil`，并立即恢复已纳入的 `IHasTranslationKey` 与 `RelativeSide`。IHashedItem 是 TextComponentUtil 公开 dispatch 的直接依赖，不是 padding；concrete HashedItem、ConstantPredicates、ChemicalStack、TriPredicate 仍延期。最终 slice **64/122/17**。
- **SOL 候选：** `59b50c1d`（主线 cherry-pick `e7d5b256`），17 files、`+802/-102`，1848.1s、458.6k tokens、242 次调用、约 `$13.85`。三个新 leaf 与恢复后的 RelativeSide oracle-byte-identical；incremental enum 仅把两个不逃逸 alwaysTrue 调用换 stateless lambda，APILang 仅用 literal namespace，TextComponentUtil 换 owned FluidStack、raw Fluid block-derived key，并展开 vanilla 缺失的 NeoForge-injected Level description；IHasTranslationKey 恢复 helper delegation。一个新 suite 恰好5 methods；候选68 suites/323 tests、build/static gates 通过。
- **主代理风险与 GLM 5.3 审查：** 主代理在集成前发现 scout 未列出的两处 `Level#getDescription()` 适配，且 shipped suite 未直接覆盖，因而阻止直接采纳。GLM high 审查 687.6s、139.6k tokens、76 次调用、约 `$1.17`，结论 **ACCEPT**；从 pinned `ILevelExtension` 与 patched Level 证明唯一实现正是 `dimension().location().toLanguageKey("dimension")` + ID fallback，候选两处分派 bytecode 精确，fresh component 后省略 redundant `.copy()` 不可观察。额外三方法 probe 覆盖 vanilla/custom/pathological dimension key/fallback、fresh identity与双向 mutation isolation。审查同时确认57 lang keys、18 colors、increment semantics、formatting/null/style/trailing behavior、raw/owned fluid naming、compiled surface与 package gates，无 required findings。
- **最终取舍：** 主线采用 `e7d5b256`。保留 exact Level extension展开，但不把 reviewer 临时 probe 膨胀进已具5个高信息测试的提交；该证据已记录于本节。custom NeoForge FluidType description override 是明确平台差异，raw fluid 与 Task034 FluidStack 均采用 legacy-block-derived key。warning/menu/concrete slots/chemical/recipe owner 仍未落地。
- **主代理独立验证（实现 HEAD `e7d5b256`）：** focused PortableText5+RelativeSide3 为2 suites/8 tests、7.68s；ordinary full10.48s 为66 suites/315 tests，三项 jqwik properties 各100,000；Knot transfer8.63s 为2 suites/8 tests，aggregate68/323，无 failure/error/skip；`fabric-build.sh`3.10s；packaged smoke41.04s 到达`Done`；implementation release0.69s。JAR 新 direct families 与 TextComponentUtil outer+$1/RelativeSide outer+$1 精确，无 deferred chemical/predicate/HashedItem impl、test/NeoForge/loader/startup leakage；一个 JAR sha256 `5c0be068222cf800bd5071edadca1b49ac5a47202f43139573650149109e7ef0`，build-info source `e7d5b256`。最终文档提交后须重跑 release 对齐 source commit。

---

## 任务 037：generic intrusive/defaulted registry foundation（风险触发审查）

- **范围选择：** chemical scout（707.0s、157.3k tokens、104 次调用、约 `$2.98`）证明完整 Chemical/ChemicalStack/value registry 首个诚实闭包至少24 sources，拒绝三文件 attribute leaf、孤立 TriPredicate、truncated MekanismAPI 与提前伪称 chemical owner。Plan（2350.6s、521.0k tokens、251 次调用、约 `$9.45`）找到独立可执行的4-source prerequisite：新增 `FabricDefaultedRegistry`/`FabricCustomRegistryLifecycle`，强化 `FabricDeferredRegister`/`FabricDeferredHolder`。它不注册 chemical、不进 production bootstrap，只建立可复用 intrusive/defaulted/root/SYNCED 原语；slice **64/122/19**。
- **SOL 候选：** `619bebd5`（主线等价 cherry-pick `14b2eede`），14 files、初始`+901/-45`，2269.3s、452.5k tokens、265 次调用、约 `$14.20`。exact subclass 使用 intrusive `DefaultedMappedRegistry`、`FabricRegistryBuilder.from(this)` 与 owner-thread fail-closed lifecycle；strict duplicate/value/key preflight，canonical default Holder.Reference，无 unfreeze/rollback。普通5个新 tests、独立 server-side Knot root worker1个 test、Gradle isolation/count gate和exact7-class package gate均通过。
- **GLM 5.3 首轮只读审查（`thinking=high`）：** 1034.1s、201.4k tokens、124 次调用、约 `$2.97`；结论 **ACCEPT WITH FIXES**。从 pinned vanilla/Fabric/Loader bytecode与多次 worker重跑确认 super ctor无virtual dispatch、builder exact identity+MODDED/SYNCED、ResourceKey interning使引用比较成立、server/client automatic freeze同 owner thread、missing-default preflight可恢复而 side-effecting freeze/register failure终止、canonical holder publication/partial batch/普通 registrar seam/测试隔离全部正确。唯一 required finding 是 `FABRIC_REGISTRY.md` 两个 Loom-remapped JAR hashes不存在。
- **SOL 文档修复与聚焦复审：** doc-only SOL `5074c6b9` 对应主线 `5f8aa4f5`，103.7s、45.8k tokens、12 次调用、约 `$0.40`；删除 regeneration-sensitive remapped hashes，保留 verified raw/source、mapped Minecraft与loader pins，最终总 diff14 files、`+900/-45`。原 GLM reviewer resume 用68.0s复核，累计350.9k tokens/135次调用/约`$3.60`（相对首轮增量约149.5k/11/`$0.64`），结论 **ACCEPT**；tree-identical candidate+单文档diff与所有稳定 pins 均确认。
- **最终取舍：** 主线采用 `14b2eede` + `5f8aa4f5`。真实 root mutation 只发生在 forkEvery=1 的单方法 Knot worker并随进程退出；production 没有 startup reference或manual freeze。该 foundation 不降低下一完整 chemical vertical 的≥24-source下界，也不声称 registry reads在population期间线程安全、data-map reload或payload sync。
- **主代理独立验证（实现 HEAD `5f8aa4f5`）：** focused existing5+new5 registry tests7.52s；isolated root9.36s；ordinary full11.09s 为67 suites/320 tests，三项 jqwik properties 各100,000；transfer8.55s 为2/8，root rerun8.20s 为1/1，aggregate70/329，无 failure/error/skip；`fabric-build.sh`5.10s；packaged smoke51.22s 到达`Done`；implementation release0.75s。JAR registry direct family精确7类，无 synthetic/test/Chemical/MekanismAPI/NeoForge/loader/unfreeze/startup leakage；一个 JAR sha256 `0e13c92e920eb64b239b943daa33a8869c383606ef400ec136631895173e7e46`，build-info source `5f8aa4f5`。最终文档提交后须重跑 release 对齐 source commit。

---

## 任务 038：ordinary MekaSuit absorption data-map vertical（风险触发多阶段审查）

- **范围选择与规划：** Chemical/ChemicalStack 的严格首个闭包经 scout 证明至少约24个 production sources，并继续受真实 radiation、tooltip/tank owners 牵引，因而拒绝截断 Chemical、孤立 attribute leaf 与无执行入口的数据-map storage。一次失控 SOL Plan 在508 turns、1,403次工具调用、约2.5M tokens/`$50` 后仍无结果且无仓库修改，其输出作废；后续所有规划均以明确 `max_turns` 收束。GLM 5.3 high 的非 Chemical MekaSuit 方案用404.1s、66.9k tokens、22次调用、约`$0.20`找到真实资源 anchor，但未证明 `/reload` 时 live `RegistryAccess`；GPT-6 Astra high 随后从 pinned Fabric lifecycle/mixin/source 证明 `SERVER_STARTING` + `END_DATA_PACK_RELOAD(success=true)` 可在资源、registries 与 tags 安装后取得 `server.getResourceManager()` 和 `server.reloadableRegistries().get()`。最终 Astra Plan 用401.4s、93.0k tokens、37次调用、约`$1.77`形成 bounded GO：只接 ordinary、unconditional、non-synced MekaSuit absorption map，不伪造 Chemical/sync/Advanced owner。
- **最终范围：** 基线 `80f0a8de`；production 只新增/修改 `MekaSuitAbsorption`、`FabricDataMapType`、`FabricDataMapLoader`、`FabricMekanismDataMaps` 与 `MekanismFabricBootstrap` 五源，精确打包 map JSON 及其21项 damage-type tag。resource stacks 按 low→high、每层 top-level replace→ordered writes→ordered removals；malformed layer 独立跳过，reload 从空重建并原子发布 immutable epoch snapshot，owner 绑定 exact server/thread，failed startup 与 stop 清理 fail-closed。slice **65/122/22**；Chemical maps、sync、conditions、Advanced mergers/removers、holder mixin、NeoForge facade 与 client payload 全部延期。
- **SOL 初始候选：** `8d8bb46a`（主线等价 `956172e8`），20 files、`+1431/-43`，1518.6s、238.4k tokens、147次调用、约`$7.44`。ordinary4 methods、fresh Knot1 method、隔离 packaged fixture 和 reload shell gate构成72 suites/334 tests；候选 local/build/static checks通过，但主代理阻止直接集成：foreign namespaces 被错误计入 owner warnings、unknown direct target 用 WARN 而非 oracle ERROR、failed-start stop path可二次抛错、fixture isolation未接入`check`，且负面 smoke 的 ERROR accounting 与新 datapack discovery 不严。
- **GLM 5.3 首轮只读审查（`thinking=high`）：** 744.1s、152.8k tokens、71次调用、约`$1.47`，结论 **ACCEPT WITH FIXES**。它以实际 tests/build/JAR probes接受核心 parser、epoch与owner语义，并把上述问题收束为六项 required fixes：console `reload` discovery、direct-target ERROR、descriptor namespace过滤、failed-start identity cleanup、`check` wiring、phase-bounded ERROR whitelist。
- **SOL 修复与首次聚焦复审：** corrective `d8938cee`（主线 `0468c919`）用590.0s、124.7k tokens、66次调用、约`$2.26`完成七文件`+171/-23`，focused46.71s、full check25.16s、static package12.57s及fixture/slice gates均通过。GLM high 聚焦复审用496.8s、80.6k tokens、43次调用、约`$0.60`确认前五项和大部分日志门禁关闭，但结论仍为 **ACCEPT WITH FIXES**：用 malformed dynamic-registry JSON 制造 `/reload` future failure并不成立，且预期日志等级错误。
- **最终 fixture 修复与复审：** SOL high `7b9c14e7`（主线 `a308b609`）用494.6s、105.5k tokens、54次调用、约`$1.79`，在 test-only fixture JAR 注册 marker-gated Fabric `SERVER_DATA` synchronous reload listener；marker存在时真实 reload future失败，fixture不供应 map也不调用 production loader。shell 要求 exact `WARN Failed to execute reload`、END `success=false`、epoch3/state fresh probe、零新增 ERROR，并在移除 marker 后证明 epoch4恢复。GLM high 最终只读复审用382.0s、68.4k tokens、44次调用、约`$0.60`，从 pinned vanilla/Fabric bytecode核对 future、callback与日志，结论 **ACCEPT**；唯一剩余证据是主代理真实 runtime run。主代理实跑时另以一行 `1e5edcad` guard 消除 `latest.log` 创建前的 grep 噪声，不改变验收语义。
- **最终取舍与主代理独立验证（实现 HEAD `1e5edcad`）：** 采用上述三段候选加 startup guard。focused ordinary4 + data-map Knot1与fixture isolation通过；full gate为ordinary68/324、transfer2/8、root1/1、data-map Knot1/1，aggregate **72/334**，0 failure/error/skip，三项 jqwik仍各100,000；`fabric-build.sh`通过；packaged dedicated-server smoke到达`Done`并发布 startup epoch1/21；真实 `fabric-datamap-reload-smoke.sh` 47.46s通过 epochs1–4、layer failure、END-false state stability、recovery与stop cleanup。clean implementation release34.68s，恰好一个 JAR sha256 `634a8f10ad0bc41e76b983ffb26f49086cd6d350a71b4a1270c53d06150d1008`，build-info source `1e5edcad`。最终文档提交后须重跑 release 对齐 source commit。
