# 最近 24 小时开发审视（2026-09-13）

审视窗口：北京时间 **2026-09-12 18:46:37 → 2026-09-13 18:46:37**。
基线 `079e2f6fa2eeb929be790490a9d295afb305a6fe`；窗口末 `2f2740bad884e9b9764c7ead898913d6f11569c2`。
范围为 Tasks 035–043 的落地，以及当前组织方式；不将正在运行的 Task044 算作已完成。
本文是审计与改进建议，**不是自动批准新的架构、删除测试或降低门禁**。

## 可复核的进展

- 三份 source slice：API/common/platform 从 **57/122/17（196）** 到 **199/188/38（425）**，净增 **229** 个编译源。不能据此计算整项目完成百分比。
- 已记录测试从 Task034 的 **66 suites / 313 tests** 到 Task043 的 **86 / 422**；这是测试执行规模，不是功能完成度。
- 实际落地包括共享序列化/text、custom-registry foundation、data-map reload、具体 inventory slots、完整 internal Chemical vertical、recipe ingredients/cache/reload、output/one-input execution，以及 26 recipe types/serializers 的真实注册。
- 窗口末仍没有完整 Crusher 的 block/entity registration、机器运行/持久化、真实自动化 provider 与 unload/restart 验收；干净服务端到达 Done 不等于机器可玩。
- Git 窗口共 53 个提交（49 个非 merge）。`git diff --numstat` 中 docs/AGENTS 类别为 21 文件、+3209/-107；`fabric/build.gradle` +546/-2，`scripts/fabric-packaged-smoke.sh` +798/-15。行数不是浪费率，但显示人工同步负担已明显增长。

## 路线结论与独立审查复核

总体仍沿官方 Java/Mojang mappings、保留领域模型与 ID/存档语义、局部平台 adapter 的路线前进，**不支持推倒重来的结论**。问题主要是到首台机器的优先级与任务组织，而不是项目身份再次变成自创重写。

独立只读审查使用 GPT-5.6 Terra high（不是 DeepSeek high），run `22919750-0643-440`，150.4s、38次工具调用；未改文件或执行门禁。主代理随后核对实际调用链，结论如下：

1. **已确认的高风险整合约束，不是当前已触发 bug：** `api/recipes/MekanismRecipeTypes` 已创建并注册26个匿名 vanilla recipe-type instance；尚未纳入的 `common/recipe/MekanismRecipeType` 既实现 `RecipeType` 又创建自己的 type/cache owner，`TileEntityCrusher.getRecipeType()` 返回后者的 `CRUSHING`。以后机械纳入官方 owner 会撞 registry key，或使机器查询与 RecipeManager 采用不同 identity。必须保证 decode→cache→machine 只有一个 canonical type。可以让 owner 委托当前 canonical type，也可以在同一批中统一重构唯一创建点并重绑 API holders；不允许同时保留两套注册路径。不能为了赶第一台机器另造 Crusher 专用框架或截断通用 provider 的公共面。
2. **Task042 的阻断判断部分过度：** Task043 已证明注册和解码不要求先注册 toast items 或接入 client recipe viewer。Task042A 有实际价值，但“042B 不能继续”并不是所有列举依赖都成立。两参数 holder 的编译修复是应前置检查的已证实返工。
3. **Task038 是可复用但非最短路径的先行试验：** MekaSuit damage-map consumer 不属于 Crusher；loader 后被 Chemical 复用，不能说是死代码。应把这种工作明确标成平台验证投资，而不是首台机器功能完成。
4. **对 reviewer 的 Task039/040 旁支判断作重要限缩：** “Crusher 直接源码不用 Chemical/窗口”不等于保留官方通用类时没有它们的编译/owner依赖。主代理实查 `TileEntityRecipeMachine` 的 Chemical tank 接口、`TileEntityMekanism` 的 `IMekanismChemicalHandler` 与 manager，以及 `OneInputCachedRecipe` 的完整 Chemical factory，故不能据此断言 Task040 本可整批跳过。`BasicInventorySlot→InventoryContainerSlot→IInsertableSlot.exists(SelectedWindowData)` 同样是真实公共签名链。客户端持久化/网络何时激活可以审视，但不能靠删除这些公共面伪造更短路径。reviewer 没有证明一个保持现有约束的可执行替代闭包，因此不接受“全部是不必要开发”的强结论。
5. **对 reviewer 的 Task043 验证缺失判断作纠正：** 仓库记录确实落后，但主代理在本 session 已于 merge `2f2740ba` 实跑 full tests/build、packaged dedicated-server smoke 与 clean release。smoke 路径 `build/fabric-packaged-smoke/20260913T100328Z-2646506`；日志为26 types/26 serializers/identity_preserved=true，并到达 Done；clean release为一个JAR，sha256 `dbe35c043d481fd866c2b9514dba005f23bf51c6db508644ccf185a85dd40ffb`。产物临时 `dist/` 已清理。这里是**验收事实没有及时落入仓库**，不是没有运行门禁；本次补记。该路线审计也不冒充完整 Task043 codec/lifecycle adversarial review。

下一项最有价值的验收是：官方 recipe lookup/provider/monitor 与现有 registered type、RecipeCacheSource、reload context 的实际连接，先证明 crushing 查询端到端同一 identity，再推进 ElectricMachine runtime owner。范围仍按真实通用模块确定，不预设必须只做一个 recipe family，也不以纳入全部家族为理由扩大到无关 client viewer。

## 已证实的组织成本

### 1. 反复从零搜索可接受闭包

`MODEL_IMPLEMENTATION_COMPARISONS.md` 的 Task035 记录 scout 462.4s、plan 1753.7s；Task037 记录 scout 707.0s、plan 2350.6s。Task038 还记录过一次最终无结果的规划：508 turns、1403 次工具调用、约 2.5M tokens。上述是任务报告数据，不能把全部耗时无条件加作本审视窗口的独占墙钟时间。

历史文件数上限推动了“找小闭包 → 大 owner 被阻断 → 再找 leaf”的循环。`7be356b4` 已取消数量目标/硬上限，这是正确修正；下一步要把它落实为按 owner/caller 路径规划，而不是仅改文案。

当前主代理给 Task044 的指令又把“选择下一个边界”和“实现”同时下放给实现代理；已收到的监督证据显示它连续数轮仍在计算依赖闭包。这是主代理组织责任：应先理解并确定边界，再交付可执行任务，而不是要求实现代理重新规划全局。实现者仍应有权基于新编译/语义证据修订细节或报告真实 blocker，不能以规划检查点迫使它仓促接受不成立的前提。

### 2. 模型思考与重复审查比测试执行更贵

Task041 两个候选分别用 6495.3s 与 5335.0s；它们并发，**不能相加为墙钟耗时**。B 在截止时仍缺少必要证据，最终只吸收了一个设计思路。之后 DeepSeek high 独立审查为 3967.9s（约66分钟），复审再用688.5s。

该审查确实发现两个 HIGH 与多个 codec/wire 问题，不能据此取消独立风险审查。更好的做法是把争议限制到新的语义 delta，在实现前锁定签名、JSON/wire 和生命周期契约，而不是完成后再让 reviewer 重建全项目。

对照 Task040 主代理整组 local/runtime 门禁仅约6m47s，clean release约45s；Task043 clean release约51s。主要加速目标应是减少规划/模型循环，不是先牺牲真实验证。

### 3. 已解决的试验错误再次出现

- Task038 `a308b609` 已用 test-only reload listener 解决“malformed datapack 不一定导致真实 reload future 失败”的错误假设。
- Task041 `7aeff2ad` 又修正了同类不真实 failed-reload trigger。
- Task042A 的单参数 holder 无法表达官方 `Holder<RecipeType<?>>`；Task043 `855c11e0` 修正两参数 shape，波及39个文件。一个提前编译的典型官方 consumer 就能更早发现这个问题。

应复用已经证明的 failure fixture 方法，并为跨平台 public surface 先建最小 consumer compile/probe。这里的“小”指验证争议本身，不是把生产闭包再次拆成大量小任务。

### 4. 文档与门禁维护出现形式负担

同一任务的范围/计数/非声明反复写入 plan、FABRIC_PORT、PORT_MATRIX、API_DELTAS、专题 docs、FABRIC_SMOKE 和比较记录。已经出现现实漂移：Task043 的 isolated test 实际5 methods，`API_DELTAS.md` 仍写4；同一文档先出现旧单参数 holder 表述，后面再补两参数纠正；测试类 Javadoc 的 bootstrap 顺序和方法体注释互相矛盾。

`fabric/build.gradle:775–818` 人工重复维护每 suite 的 `@Test` 数、各 runner 总数和 aggregate；packaged smoke 手工穷举大量 nested classes。`7a973c64` 为符合 `$1.class` 形态门禁改回等价 codec composition，是门禁开始影响实现表达方式的具体例子。安全约束本身有价值，机械计数不应成为领域设计约束。

## 推荐组织方式（待采纳）

1. **维护一份短的 Crusher 关键路径表。** 以可执行能力和实际 owner 为单位：registered recipe → live lookup/monitor → 官方机器必要 storage/side/config/组件 → 官方 block entity/机器 tick/NBT → 自动化、reload、unload/restart。每项列出下一调用方、真实 blocker 和验收；旁支明确排后。表可分支，不把猜测的线性依赖当事实。
2. **主代理负责选路，单 DeepSeek low 负责实现。** 任务开始前交付已理解的文件入口、允许的 delta、owner 边界与验收；规划发现沉淀复用，不每批重算整个 import graph。high 只处理明确难点，不默认承担从选题到交付的长链。独立风险审查保留，但限定新 delta/高风险 owner，不复制实现。
3. **给规划设检查点，不给生产文件设上限。** 建议一次短研究约10–15分钟后必须交付候选边界或具体未决问题；仍无结论则主代理决定继续证明哪个问题，而不是让代理无限换候选。时间只是进度检查点，不是到点强行宣告安全。
4. **形成窄的双通道。** 实现代理推进当前已定任务时，主代理准备下一项任务说明或核对上一项风险证据；同时最多一个实现者，不复制两个完整候选。共享文件修改仍串行集成，审查对象必须是固定 commit。
5. **保留测试，改掉重复手工账。** runner/engine isolation、原有 jqwik执行和全量测试不能缩水；逐步由单一测试清单/发现结果校验真实执行，计数由报告生成。JAR门禁依据批准 source provenance、依赖/fixture/resource禁区，而非把每个编译器生成 `$N` 都当架构决策。不能从待验 JAR 盲目生成并接受自己的白名单。
6. **一次实现、一次最终产物验收。** 候选跑 focused/full local + build/static；主代理审查最终 diff 后，对最终 commit 执行 clean build，再让 smoke 验证该 exact JAR/hash，避免多个入口重复重建。runtime 场景按实际变更风险选择；修改网络/registry/lifecycle时不省略其真实场景。现有脚本/契约需另批明确调整后才改变调用方式。
7. **事实只维护一个权威位置。** 当前能力/依赖放短路径表；语义差异放专题文档；任务计划区分最初设计与最终 outcome；验证数据用机器产出的 commit/hash/results receipt，其他文档链接它。历史评价保留，不每次重抄。

## DeepSeek 耗时专项补充（用户追加审视）

Task044 的一次实测快照：DeepSeek V4.1 Flash **low** 已运行2045.9s（约34分钟）、130次工具调用、工具报告累计550.3k tokens、context20%，仍主要处于探索。累计token不等于生成token，也不能据此计算服务端推理速度；平均每工具调用约15.7s只是整段工作摊销，并非shell执行延迟。

公开轨迹显示：反复重算同一1351/1020或1550/1175规模依赖图；重复读取MekanismRecipeType、lookup和registry相关文件；多次修订临时closure scanner；一条Gradle cache查询超120s后主动终止。最具体的误差是把logger字符串 `mekanism.api.MekanismAPI` 当真实依赖，导致虚假大闭包；代理后来识别并改进scanner，五个cached-recipe候选随之变成仅五个outside-slice源。这是在适应、自修复，不是完全停滞，但付出了大量可避免的探索成本。

因此不能把“慢”简单归因于high：low也会被开放选题、过长串行工具链、图工具误报和缺乏明确完成条件拖慢。当前无证据区分provider排队、每次模型推理和工具执行的精确占比，也没有证据证明上下文已耗尽。应优先收紧任务输入、持久化经过验证的依赖分析结果、要求阶段性GO/BLOCK结论，并由主代理处理owner方案取舍；不是切成更多小生产批次或对仍在适应的单次错误强行干预。

## 执行边界

本次不修改生产源码、测试或验收门禁，不停止或改变 Task044 的实现范围，也不执行生产/镜像/部署操作。加速建议的预期收益是减少已可观察的重复劳动；**没有足够证据承诺固定倍数或完成日期**。
