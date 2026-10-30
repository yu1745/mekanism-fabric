# W-30 验收 — 2026-10-30

- `assemble`（补四份JSON后）PASS 17秒；最终 `assemble fabricClientModelTest --tests mekanism.fabric.client.model.QIODataBasedModelClientTest` PASS 22秒。
- 既有 QIODriveModelClientTest / QIODriveItemStatusClientTest / QIODataBasedModelClientTest / RobitModelClientTest 各1项，共4项PASS（45秒）；DataBased修正后单项再PASS。
- 发布JAR：四个原particle class及五份particles/*.json均存在且唯一；原textures原样打包。
- native-factory-particles.json：真实ParticleEngine.createParticle按实际注册provider创建四个原类并绑定sprite；不是直接new factory代替注册。
- idle-flame-eligible=true；真实带hydrogen的flamethrower经END tick回调发出JetpackFlameParticle，队列含多个非零age。
- 本项不声明整个装备玩法已验收；Jetpack/Scuba等实际gear操作留W-36。无新增测试。

首次资源遗漏的真实错误：`createParticle(...) threw java.lang.NullPointerException: Cannot invoke "java.util.List.size()" because "this.sprites" is null`。补纳原JSON并reload后同入口通过。Laser运行参数需显式Direction枚举与Float包装；前两次CLI参数类型错误不计入通过。尝试直接设置local PlayerState会被原客户端gear状态计算复位，未据此宣称装备已工作；最终callback证据来自真实持有燃料的Flamethrower。
