# M-multiblock — 核心多方块生命周期

## 现状

P6 W-34–35待验证，生产基线`8ad14311`；P5已由`4ae6056b`关闭。W-31已验证五种原builder形成结构并打开GUI，证据复用，不重新作搭建／GUI验收。

## 下一步

W-34对Dynamic Tank、Thermoelectric Boiler、Induction Matrix、Evaporation Plant、Spaced-Out SPS分别验证破坏重建、真实区块卸载重载、存读档后结构保持与尺寸失败路径；读真实multiblock owner，不写formed字段。先确认隔离世界的结构前置，只在缺失时恢复既有最小fixture，不扩成重复搭建验收。
W-35修复同一owner边界内的实际失败，再执行阶段build/API/machine门禁，更新状态归档。命令、原始失败与读数仅留结构化JSON/CSV；必要时保存截图，不重复GUI巡检。

## 未决项

尚无产品缺陷结论。需确认W-31结构前置是否仍可复用；此前调试世界已完整恢复，不能从历史截图推断当前仍存在结构。任何必要fixture恢复须与生命周期验收分开记录。
W-36仅做装备／工具功能与模块安装，排除已验证的accessory预览和客户端注册。W-40前先提供旧／新packaged smoke检查对照，协调者只读复核后再讨论发布门禁；不自行push、squash或更新submodule。

## 验收证据

W-34尚未执行，不宣布生命周期通过。前置[W-31覆盖表](evidence/p5-gui/coverage.csv)与[P5门禁](evidence/p5-client-registration/final-gates.json)已核对。
