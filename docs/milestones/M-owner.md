# M-owner — TileEntityMekanism 中枢 owner

原始计划：`docs/archive/TASK052_PLAN.md`。相关历史文档按 `docs/milestones/README.md` 的归类表查找。

**验收（唯一）**：真机上一台 Crusher 放置 → 通电 → 加工 → 存档读档后状态一致。通过前状态保持"未核实"，不得声明机器可用。

## 现状

未核实。已知：配置、传输、渲染等相邻系统已拆为独立里程碑；本里程碑仅含 `TileEntityMekanism`、`TileComponentConfig`、频率/`FrequencyManager`、`ContainerType`、`MekanismContainer` 同步、有效侧线程语义、bounding。

## 下一步（交给 worker）

1. 核实：读 `git log` 与 `docs/archive/TASK052_{CONVERGENCE_14,CONTINUATION_14,ASTRA14_PROGRESS,RESUME_2026-10-26}.md`，列出 `TileEntityMekanism` 及上述各 owner 的实际接通状态（已接通 / 已纳入未接通 / 未纳入）。
2. 按验收用例用 mcdebug 在 `fabric/run` 的隔离调试世界实测，记录通过/失败点；失败时抓首条错误原文。
3. 结果写入本文件"验收证据"，并改 `docs/milestones/README.md` 状态表；通过后在 `PORT_MATRIX`/`FABRIC_PORT` 补一行结论。

## 未决项（协调者待裁决）

- 若核实发现某必需模块会明显扩大边界：worker 记录调用链与最小边界后暂停该分支，不自行扩题。
- 历史文档归类仅按标题，worker 发现归错的在 README 归类表更正。

## 非目标

配置（M-config）、传输（M-transfer）、渲染（M-render）、注册收尾（M-registry）、装备（M-gear）；新测试矩阵。

## 验收证据

（空）
