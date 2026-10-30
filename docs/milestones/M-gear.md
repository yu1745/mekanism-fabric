# M-gear — 装备与模块功能

## 现状

P7 W-36–37已完成，五类模块factory与直接owner均已接通。修复原MekaSuit armor tick的NeoForge全局槽号假设；其余五类代表功能通过。P5附件预览／客户端注册不重复。

## 下一步

衔接W-38注册表与W-39世界生成；完成后执行P7阶段门禁，再归档装备／注册里程碑。

## 未决项

所列五类factory已核对，未穷举全部装备／配置组合；MekaSuit槽号差异已修复，不再作为未解决缺陷。第三方集成／Generators不扩入本阶段；W-40前packaged smoke对照与协调者只读审查仍为发布前置。

## 验收证据

W-36：六类装备代表功能、两次真实Modification Station安装、C2S模式及实际能量／燃料消耗通过；修复后worn耗能、unworn不tick。assemble34秒与两项既有focused tests18秒PASS。[结果表](evidence/p7-gear/w36-functions.csv)、[边界／测量纠正](evidence/p7-gear/w36-validation.json)。
完整隔离世界81文件恢复一致，自有会话和临时依赖清理；无新增测试。不宣布P7阶段或发布门禁通过。

W-37：五类真实factory／直接owner核对通过，无生产代码缺口；Farming、Shearing、充能优先级与守恒、Swim两倍水中跳跃、Flight原生授予／耗能／存档过滤／撤销通过。[调用链与限制](evidence/p7-gear/w37-owner-map.csv)、[验证范围](evidence/p7-gear/w37-validation.json)。
Elytra仅工厂和原生资格检查；可选附件充能等未重复实测。隔离世界81文件恢复一致，自有运行会话与临时依赖清理。
