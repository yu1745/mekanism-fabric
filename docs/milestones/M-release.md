# M-release — 核心开发与后续发布验证

## 现状

按用户“聚焦开发”要求，已补Meka-Tool左右手模型／OBJ继承烘焙和QIO dashboard LED逐层全亮度。平台仅新增两个实际渲染owner（207→209），未新建转换框架。

## 下一步

Tools与Additions主体已完成；Generators的机器、多方块、GUI、生命周期和冷启动均已真机验证，W-58统一构建、578 tests／API／machine回归已通过，独立四JAR已生成。现在进入P13：W-59修拒绝顺序，再执行W-60–62已有发布检查与C-1…C-4，不扩展审查或测试覆盖。

## 未决项

W-40 rejection真实失败：等待mismatch rejection超时，客户端先被Fabric registry remap拒绝（998个未知mekanism条目）；实际选路已确认：Fabric registry-sync-v0 5.3.2 在 BEFORE_CONFIGURE 默认阶段排入注册表同步，而当前版本任务排在 CONFIGURE；应将原版本任务放在 BEFORE_CONFIGURE 的默认阶段之前，保留独立fixture与正常注册表同步。下一步实施此调整并运行现有拒绝与正常连接smoke。其后smoke／W-41 clean发布验证未执行，未宣布发布通过。第三方集成和未穷举风险见README限制节。

## 验收证据

本批开发：assemble与3项既有focused checks通过；冷启动实机右手／GUI107 quad，第一／第三人称左手102 quad，变换来自各自JSON；dashboard底层light0、LED full-bright15728880。[实机](evidence/development-render/native-summary.json)、[范围／恢复](evidence/development-render/validation.json)。隔离世界81文件和options恢复，自有JVM／临时debug依赖已清理，无新增测试。

先前安全网复核：`b7fb09ab`对照221项，`85799912`修复独立审查提出的mixin注册下限P2并复查关闭；[结论](evidence/p8-release/packaged-safety-review.json)。此项已结束，不重复审查。

W-40已跑：当时578 tests／API／machine／paired通过，rejection失败后停止；[结果与未跑项](evidence/p8-release/w40-results.json)。此后用户要求转回开发，这些旧结果不覆盖后续修改；Tools收口的当前构建另见evidence/tools/build.json，仍不等同完整发布门禁通过。

W-42准备：历史46项已逐一更新、README已列真实发布限制；[清单](evidence/p8-release/w42-historical46-current.csv)。原两项模型可视差异现已实际实现，最终发布收口尚未完成。
