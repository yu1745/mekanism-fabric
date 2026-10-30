# M-registry — 注册表与世界生成

## 现状

P7 W-38–39与阶段门禁已完成。核对固定官方五条legacy alias、化学ingredient六类codec、Robit静态serializer与同步datapack registry、biome modifier实际owner；不移植NeoForge通用注册层。

## 下一步

W-40前提交packaged smoke检查对照供协调者复核；之后再进入P8全套发布验证。

## 未决项

retrogen脚本已修正phase日志轮询并实跑通过，验收断言未改。W-38已分开记录Robit领域probe与实际服务端／客户端动态加载。W-40前另提交packaged smoke旧新检查对照，等协调者只读复核后才可声明发布门禁。

## 验收证据

W-38：官方五条alias、ingredient六codec、Robit二serializer与双端13皮肤冻结身份通过；biome实际加载6 modifier／11 feature，20项既有focused checks通过，无生产改动。[owner映射](../milestones/evidence/p7-registry/w38-owner-map.csv)、[实机读数](../milestones/evidence/p7-registry/w38-native-summary.json)、[范围](../milestones/evidence/p7-registry/w38-validation.json)。
完整世界81文件恢复，自有JVM与临时依赖清理；P7阶段门禁已通过；不宣布P8发布门禁通过。

W-39：worldgen在2465个保存区块找到五类官方矿石与盐；retrogen同世界版本升级后实际再生成850区块，既有检查全部通过。[worldgen](../milestones/evidence/p7-registry/w39-worldgen.json)、[retrogen与脚本修正](../milestones/evidence/p7-registry/w39-retrogen.json)。
两项隔离服务端与世界已清理；palette证据不等同于逐方块产量或全部分布语义验证。

P7收尾：build285.40秒（七个测试任务全部重跑，578项零失败）、API0.64秒、machine98.45秒PASS；[门禁](../milestones/evidence/p7-registry/p7-gates.json)、[测试](../milestones/evidence/p7-registry/p7-tests.json)。无新增测试／slice变更，W-39仅一行脚本轮询修正；后续为W-40前对照与协调者复核。
