# M-release — 核心发布验证

## 现状

P0–P7已完成。W-40前packaged smoke逐项对照与同边界修复已完成，待协调者只读复核；尚未开始本轮P8发布验收，不声明发布门禁通过。

## 下一步

提交221项旧新检查对照供协调者只读复核；继续准备W-40全套测试／smoke、W-41发布物核验和W-42限制汇总。复核前不宣布发布门禁通过。

## 未决项

dev→remapped内容相等只能证明构建产物完整性，不能替代旧脚本对源码／服务descriptor／客户端作用域的独立oracle。对照须明确记录差异，不能将所有旧检查笼统算作等价。独立只读审查`b7fb09ab`发现P2：仅检查client非空列表会丢失原三项mixin注册下限；类仍在、两份JSON同时漏注册时可假绿。已按原三项set-subset补回独立注册下限，允许已纳入新mixin；static PASS，同一纯内存漏注册反例现被拒绝，待对提交只读复查；未完成前不宣布安全网或发布通过。

## 验收证据

W-40前置：118个旧Python失败点＋76个必需条目＋27项shell／运行检查逐项映射；恢复565类下限、21资源哈希、4个service字节oracle及客户端／portable owner约束，补齐4条W-32 AW预期。现有static gate PASS，未启动服务端／未新增测试。
[逐项对照](evidence/p8-release/packaged-smoke-check-map.csv)、[审查入口／待裁决差异](evidence/p8-release/packaged-smoke-review-input.json)、[静态证据与首错](evidence/p8-release/pre-w40-validation.json)；P7基线`c472e4d0`。

W-42准备：README新增发布限制，历史46项已按当前slice／真实owner逐项更新（5原源码纳入、32实际替代、7第三方、1开发工具、1旧反射辅助含可视差异）；[逐项表](evidence/p8-release/w42-historical46-current.csv)、[限制依据](evidence/p8-release/w42-preparation.json)。仅文档，不关闭W-42或宣布P8通过。

W-40安全网复核：独立审查发现并确认1项P2（原三项client mixin注册下限），已在原gate修复并复验；[审查与处理](evidence/p8-release/packaged-safety-review.json)、[相同反例拒绝](evidence/p8-release/mixin-floor-counterexample.json)。其余映射／oracle来源未发现实际缺陷，待提交后复查，不代表发布通过。
