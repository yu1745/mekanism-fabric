# 移植方式历史审查（2026-10-26）

审查 HEAD `b626904b`；最近批次以 `8d0d4ebd..b626904b` 为比较范围，包含此前主线的策略合并。Git 最近两天的实际变更提交均为 10 月 26 日，未见 10 月 25 日提交；不能据此推定工作实际开始时间。提交 author 均为 yu1745，Git 身份本身不能证明由哪个人或模型实现。

## 结论

架构方向延续，行为保持标准执行不一致。最近批次保留官方包名、机器继承与领域算法，继续使用精确 slice、原生 Fabric lookup/mixin 和既有 registrar；恢复官方 serializer catalog 比早期手写重复 catalog 更符合单 owner 原则。但若干改动把缺少 NeoForge 接口直接处理为删除行为或恒定返回，违反“不以 fake/no-op 或删除公共行为换编译通过”的原则。

批量纳入本身不是缺陷；最近批次 common slice 增加 416 条，293 个文件变化（5,666 additions / 2,924 deletions），必须审查真实行为，而不能以数量或 javac 错误下降判定质量。早期同样有 WIP 和未完成领域，不能将“以前全部正确、现在全部错误”作为结论。

## 历史参照

- 官方语义基线：`4646f509`，移植原则见 `docs/FABRIC_PORT.md`。
- `fdd8bb07`：真实 component item owner + Fabric nested transaction/exchange，保留官方 container/handler，adapter 承担平台事务。
- `951dd62b`：接回原始 filter 生命周期与 transmitter geometry，而不是替换官方机器。
- `83ce2d8e`：纳入已经移植的直接 config 声明，明确不得制造第二个文件 writer；`docs/TASK052_CONVERGENCE_14.md` 对 chunk-loader 等实际 owner 要求语义裁决，禁止 placeholder。
- 新批次正例：`59e389a0` 的服务器线程组 seam，`a1f1c46f` 恢复官方 serializer catalog，`55c59a0c`/`b0f7e13d` 修复 remap 接口及真实 capability exposure。它们表明仍有按真实调用链修通的工作，不应整体回退。

## 具体发现（静态代码审查）

### P1：区块强加载丢失 owner 隔离

来源 `4e5a4232`，`ChunkLoaderTickets.setTicket` 第 55–71 行以及 `release` 第 148–152 行。

每次释放直接 `level.setChunkForced(..., false)`，没有检查其他 owner 的集合。A、B 同时申请同一区块，A 拆除/关闭就会清掉 B 仍需的强加载；同样会与 vanilla `/forceload` 共用并清除全局标记。维护 owners Map 并未让底层票据变成按 owner 独立。应建立真实独立票据生命周期，保留重叠 owner 和外部强加载；不能只把全局布尔开关包装为 owner API。

### P1：数字采矿机与纸箱删除保护否决行为

来源 `5138fd15`。`TileEntityDigitalMiner.canMine` 第 591–599 行把 owner UUID 模拟 + cancellable break probe 改为 `boolean canMine = true`；`ItemBlockCardboardBox.canReplace` 第 51–56 行只保留 vanilla interaction 检查。

当保护依赖 break veto 时，自动采矿或纸箱搬走方块不再经过原来的否决路径。代码注释所称“真实玩家破坏上游已经处理”不覆盖自动采矿/右键纸箱，也不能替代这些路径的权限。`ServerPlayerGameModeMixin` 只覆盖真实 `destroyBlock`，未恢复它们。需按真实 owner 接入 Fabric 适用的取消/权限路径；不支持的兼容范围应明确裁决，不能恒定允许并称保持行为。

### P2：按键注册保留，原始行为被删除

来源 `6b6bcb0f`。`MekanismKeyHandler` 第 8–41 行只剩 KeyMapping 声明与注册；模式切换、模块 GUI、BOOST press/release 同步和 HUD toggle 的回调被删，SHIFT+N 变为 N。

当前客户端主干尚未完成，因此不将此描述为已验证可玩客户端的回归。但这不是完整按键移植，也不能以“回调没有 vanilla 等价物”证明行为必须删除。后续应在 Fabric 输入生命周期恢复上述调用链和组合键判断。注释称调用链 intact 与实际删除的代码不符；Task031 声明先行的历史不能自动授权删除本模块行为。

### P2：剪切模块漏掉原版蜂巢收获

来源 `1d4c4027`，`ModuleShearingUnit.onDispense` 第 113–122 行。删除 `tryDispenseShearsHarvestBlock` 后只剪实体，满蜜蜂巢前发射 MekaTool 不再收获蜂蜜脾。

本地 Fabric MC 参考 `upstream/fabric-minecraft/net/minecraft/core/dispenser/ShearsDispenseItemBehavior.java` 的 `tryShearBeehive` 已包含 vanilla 行为，因此它不是“无平台对等能力”的必然差异。应恢复原始 block-harvest 分支的原版语义，再分别界定扩展行为。

### P2：区块票据清理可能不写回磁盘

同为 `4e5a4232`。`ChunkLoaderTickets.validate` 第 91–105 行移除失效 owner 时没有设置自身 dirty；第 142–144 行在 owners 为空时反而 `setDirty(false)`。

已有磁盘记录在启动校验中被全部剔除时，空结果不会覆盖旧保存数据；下次启动继续加载失效 owner。应将删记录持久化，不能以“不写空 entry”代替清理历史磁盘文件。此项与上面的 owner 冲突是独立问题。

## 验证与边界

本轮为 Git/源码只读审查，仅新增本报告；未修改生产源码，未重新运行构建或游戏。以上触发链为静态证据，未声称实际复现。已有提交记录的 full compile / packaged machine smoke 是正面证据，但不覆盖重叠 chunk owner、保护否决、客户端按键或蜂巢分支，不能据其推导整体语义保持。

优先修复两个 P1，再修复票据持久化；客户端未完成项纳入原有客户端闭包，剪切分支恢复真实行为。保留已经接通的机器与传输成果，不建议整体回退或重写 adapter 架构。本审查是明确任务边界，后续实现应从此报告及 Git 恢复；无需为只读审查主动滚动上下文。
