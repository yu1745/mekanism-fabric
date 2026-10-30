# M-gear — 装备与模块功能

## 现状

P7 W-36已完成，W-37模块factory闭包待核对。修复原MekaSuit armor tick的NeoForge全局槽号假设；其余五类代表功能通过。P5附件预览／客户端注册不重复。

## 下一步

W-37对照既有Task052模块边界，核对Farming、Shearing、ChargeDistribution、Swim、Flight的真实factory与直接调用方；只补实际缺口，明显扩边界则记调用链并继续独立项。随后衔接W-38–39注册与世界生成。

## 未决项

本阶段尚未验证全部factory与装备组合；MekaSuit槽号差异已修复，不再作为未解决缺陷。第三方集成／Generators不扩入本阶段；W-40前packaged smoke对照与协调者只读审查仍为发布前置。

## 验收证据

W-36：六类装备代表功能、两次真实Modification Station安装、C2S模式及实际能量／燃料消耗通过；修复后worn耗能、unworn不tick。assemble34秒与两项既有focused tests18秒PASS。[结果表](evidence/p7-gear/w36-functions.csv)、[边界／测量纠正](evidence/p7-gear/w36-validation.json)。
完整隔离世界81文件恢复一致，自有会话和临时依赖清理；无新增测试。W-37尚未完成，不宣布P7阶段或发布门禁通过。
