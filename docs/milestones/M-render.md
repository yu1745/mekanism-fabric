# M-render — 客户端模型与交互验收

## 现状

ROADMAP P5 W-29–33。P4 已验证能量／流体／物品／化学／热量及网络生命周期；其中 Contents 无贴图材质已在 `d10def07` 修复，W-24 实机确认 contentModelCache 已成功 bake。P4 的流动和无 crash 证据不能代替本阶段外观、纹理、连接状态与 GUI 完整验收。

## 下一步

W-29：逐项对照原 `client/render/obj/*` 与现有 FabricTransmitterAssets／TransmitterObjGeometry／TransmitterQuadBaker／FabricTransmitterBlockModel，以及 BaseModelCache/RenderTransmitterBase 内容 bake。用隔离世界截图核对 cable/pipe/tube 的实际贴图、连接与内容状态；先确认客户端位置、朝向和真实 framebuffer，不以截图文件存在当作外观通过。

之后 W-30 baked/data 模型及四种粒子，W-31 各类 GUI 打开与 problems 巡检，W-32 原按键／客户端注册及 accessory 渲染 owner 映射，W-33 统一门禁收尾。

## 未决项

P3 未纳入 ledger 中的客户端类须逐行为确认替代，不能因 Fabric 类名存在就核销。混合 JEI/EMI 公共 API 继续留 P8 发布限制汇总，第三方集成不在规划内。重大新模块边界按既有协议记录并跳到独立项，不增加占位实现。

## 验收证据

本阶段尚无完成声明。已有前置：P4 W-23 Contents 修复、W-24 实际水内容模型 bake；原始与运行证据见 `../archive/M-transfer.md` 及 `evidence/p4-fluid/contents-bake-cache.json`。
