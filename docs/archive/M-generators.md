# M-generators — Generators 附加模块

## 现状

W-50–58 完成。实现 `9b67cf52`、`e26940e0`；精确纳入 113 份原源码与 3 份 native 接入，8 份第三方 viewer/alias 排除，保留原包名、ID、NBT、配方与算法。独立 Generators JAR；核心、Tools、Additions、Generators 四 JAR 构建通过，核心包体不含 Generators 类。[逐文件边界](../milestones/evidence/generators/source-boundary.csv)。

复用 TileEntityMekanism、原容器／传输／热量／辐射与三个 MultiblockManager、cache/data/validator/builder。新增化学／模块类型在各自已有 owner 冻结前注册；先核对核心 EMPTY／顺序，再追加 3 化学／2 模块。51 个 SERVER 字段完整保留（44＋5＋2），四模块合计 459/512；单独核心＋Generators 同步 355 值并 ack。24 机器／多方块方块＋4 液体方块、24 BE、16 菜单；没有替代机器框架。

## 下一步

进入 P13 W-59–62：修版本拒绝顺序，执行已规划的完整发布检查与协调事项 C-1…C-4。当前模块回归不等于四 JAR 发布门禁通过。

## 未决项

没有已确认的模块开发阻塞。原 fuel assembly 发光 wrapper 及生产者在官方停用，本批保留几何算法而不启用该效果；NeoForge 配置屏幕沿核心平台边界排除。当前主机 OpenAL 设备不可用，声音播放未验收；未穷举第三方渲染器或资源包。裂变仅在隔离世界验证原配置关闭熔毁时的停堆／恢复，未执行破坏性爆炸。

## 验收证据

- [六机器与配置](../milestones/evidence/generators/simple-machines.json)：燃料／热／太阳／风均发电，六个原 GUI 打开，热／生物／燃气向实际物品放置的能量立方输出；[风机](../milestones/evidence/generators/wind-gui.png)。
- [三多方块运行](../milestones/evidence/generators/multiblock-operation.json)：涡轮 28 叶片、蒸汽发电及冷凝水输出；裂变 1.0 燃烧率、蒸汽／核废料输出，强制停堆与 15 级红石后恢复；真实激光放大器经聚焦矩阵点火聚变，氘氚消耗及能量输出成立。
- 21 份 GUI 源文件的原实现全部编译，16 个实际菜单绑定均打开；两种原 C2S 包完成统计／热量／燃料页面切换及逻辑模式修改，无新增渲染错误。[涡轮](../milestones/evidence/generators/turbine-gui.png)、[聚变](../milestones/evidence/generators/fusion-gui.png)。
- [生命周期](../milestones/evidence/generators/lifecycle.json)：三结构破坏重建、真实区块卸载重载、冷启动存档保持；裂变燃料 662 保留，非默认 bioGeneration=777 被真实机器读为输出上限 1554。创作栏键入 wind 只返回风机；自有 JVM、复制世界、临时依赖／wrapper／Xvfb 已清理，原调试世界未改。
- [模块回归与四 JAR 摘要](../milestones/evidence/generators/regression.json)：统一构建、578 项既有测试、API 检查和 machine smoke 通过，没有新增测试。真实 diff 复核已在实现／修复提交记录；原 FormationProtocol 每次成型主动更换 UUID，按原缓存恢复内容，不额外要求 UUID 恒等。
