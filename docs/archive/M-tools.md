# M-tools — Tools 附加模块

## 现状

W-43边界基线`c41de667`，W-44实现基线`ed8e1217`。独立`tools`源集／精确slice／remapped JAR保留48份原源码、72物品、6护甲材料、2配方serializer及原生成资源；4份JEI/alias/gender集成按ROADMAP排除。两份native mixin分别接实际刷怪finalizeSpawn与护甲light，不建立事件或机器替代框架。

原材料STARTUP在注册前加载，伤害默认值延迟求值；63项SERVER配置加入既有FabricServerConfig，实际核心304＋Tools63＝367。paxel复用ToolAbilityUtils与原耐久配方，剑复用vanilla浮点属性工厂；盾牌接原renderer/reload/blocking属性，原生护甲保留姿态纹理并替换荧石light。核心搜索owner支持可选Tools页，无反向类依赖；build.sh收集两个真实mod JAR。

## 下一步

W-44a–e已完成，按新版ROADMAP W-45推进Additions边界与实现。P8未完成的核心发布检查继续顺延，不重复安全网审查。

## 未决项

第三方viewer/gender与NeoForge配置屏幕不在本批范围；未宣布核心发布门禁通过。材料STARTUP沿官方语义不自动同步。验证覆盖一个完整Tools场景，未穷举每种材料／所有装备概率组合。

## 验收证据

- 真机核心与Tools共同启动，72物品／6材料／2serializer，6份SERVER配置／367值完成send/apply/ack。
- 实际客户端paxel剥皮，耐久＋1；原配方匹配受损输入100/200/300，输出damage400/max3122；原生僵尸村民获得Tools铲。
- 夜间盾牌材质与荧石护甲全亮；修复Holder比较后无新增渲染问题。读数、截图。隔离副本已删除，自有JVM／Xvfb与临时debug依赖已清理。
- `build.sh`产出两个独立JAR，578项现有测试零失败；API检查与机器smoke通过。新版规划在实现期间落地，W-44a/e回归在本批合并执行一次。构建、回归。
