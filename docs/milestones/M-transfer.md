# M-transfer — 传输与网络生命周期

## 现状

ROADMAP P4 W-23–28；基线 `843af935`（P3 最终生产提交）。P0–P3 已通过阶段门禁。既有机器 smoke 验证 cube→cable→chamber 放电与产出，以及部分物品／流体／化学连接；这些证据不替代本阶段的完整边界验收。

## 下一步

W-23：核对 `FabricEnergyLookup`、`FabricEnergyStorage`、`FabricStrictEnergyHandler` 与真实 cable/机器 owner；在隔离世界通过 mcdebug 读取两端 Joules 和 Team Reborn Energy 的外部读数，验证 FE/J 转换。复用已有 conversion、lookup、storage focused tests；不新增测试或外部机器框架。

随后依次执行 W-24 pipe→tank 满载／断开／事务回滚，W-25 transporter+sorter+vanilla hopper，W-26 tube 与 conductor，W-27 unload/reload、拆除及存读档网络重建；W-28 统一门禁收尾。

## 未决项

外部 Team Reborn API 的真实 provider/consumer 路径须先核对；纯 Mekanism 网络放电不能单独证明外部转换。若实际外部机器需要引入新模块，记录具体调用链与最小边界，继续其它已确定验收。

## 验收证据

P3 最终 `fabric-machine-smoke: PASSED` 可作本阶段已存在链路的基线，不写作 W-23 完成。尚未执行 P4 专属真机验收。
