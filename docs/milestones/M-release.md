# M-release — 核心开发与后续发布验证

## 现状

按用户“聚焦开发”要求，已补Meka-Tool左右手模型／OBJ继承烘焙和QIO dashboard LED逐层全亮度。平台仅新增两个实际渲染owner（207→209），未新建转换框架。

## 下一步

W-60现有完整smoke将使用四模块发布物；C-1三项拷贝负向探针均按预期FAIL，C-2四JAR配方计数／摘要已记录，C-4旧oracles保持不变，仅追加Additions已纳入的11条必需AW预期。当前静态检查与578项既有测试通过；实际四模块联机／packaged及其他既有smoke继续。

Tools与Additions主体已完成；Generators的机器、多方块、GUI、生命周期和冷启动均已真机验证，W-58统一构建、578 tests／API／machine回归已通过，独立四JAR已生成。现在进入P13：W-59拒绝顺序已修，继续执行W-60–62已有发布检查与C-1…C-4，不扩展审查或测试覆盖。

## 未决项

W-40 rejection 的真实顺序缺陷已修：Fabric 注册表同步先于原 CONFIGURE 版本任务，独立客户端先被 registry remap 拒绝。版本任务现排入 BEFORE_CONFIGURE 默认阶段之前，三种拒绝 smoke 已通过；正常连接验证与 W-60–62 仍待完成。Additions 语音 PCM 采集受当前主机限制；第三方集成与未穷举风险保持 README 已列边界。

## 验收证据

W-59：[顺序修复与三种独立客户端拒绝通过](evidence/p8-release/w59-rejection.json)。保留 fixture 隔离及正常 registry sync，无新增测试；实际 diff 仅原任务阶段／顺序及 PASSED 标记，不改协议或拒绝标准。

本批开发：assemble与3项既有focused checks通过；冷启动实机右手／GUI107 quad，第一／第三人称左手102 quad，变换来自各自JSON；dashboard底层light0、LED full-bright15728880。[实机](evidence/development-render/native-summary.json)、[范围／恢复](evidence/development-render/validation.json)。隔离世界81文件和options恢复，自有JVM／临时debug依赖已清理，无新增测试。

先前安全网复核：`b7fb09ab`对照221项，`85799912`修复独立审查提出的mixin注册下限P2并复查关闭；[结论](evidence/p8-release/packaged-safety-review.json)。此项已结束，不重复审查。

W-40已跑：当时578 tests／API／machine／paired通过，rejection失败后停止；[结果与未跑项](evidence/p8-release/w40-results.json)。此后用户要求转回开发，这些旧结果不覆盖后续修改；Tools收口的当前构建另见evidence/tools/build.json，仍不等同完整发布门禁通过。

W-42准备：历史46项已逐一更新、README已列真实发布限制；[清单](evidence/p8-release/w42-historical46-current.csv)。原两项模型可视差异现已实际实现，最终发布收口尚未完成。
