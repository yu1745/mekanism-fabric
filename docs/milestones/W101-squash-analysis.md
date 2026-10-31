# W-101 wip squash：现状分析与待确认项

用户 2026-10-31 已批准 squash 且指定**由 worker 执行**。本文件记录执行前的精确现状、计划与一处必须先澄清的歧义。**尚未改写任何历史**。

## 1. 实测的 wip 提交集合

在 `java-port` 分支（848 个提交）上按不同口径统计：

| 口径 | 数量 | 说明 |
|---|---|---|
| 主题以 `wip:` 开头 | **15** | 形如 `wip: validate release smokes with all four Mekanism modules` |
| 主题以 `WIP` 开头（大写，多为 `WIP Task052 …`） | **7** | 形如 `WIP task052: native menu opening and connected owner admission` |
| 两个口径合计（真正的 wip 提交） | **22** | 建议的 squash 目标集 |
| 主题中出现 `wip` 字样（不分大小写，含 `Record … WIP review` 等评审记录） | 63 | 其中大部分是**评审/记录**提交，不该被 squash |

路线图写的是"约 62 个 `wip:` 提交"，与实测的 22（或 63 的全部含 wip 字样）都不完全一致，因此**目标集需要确认**（见第 4 节）。

## 2. 分布特点

22 个 wip 提交分散在历史中段（新→旧第 90 到 360 位之间），彼此之间夹着大量非 wip 提交（评审记录、文档、功能提交）。这意味着：

- 按"功能主题"合并必须**改写它们之间的所有提交的父链**（约 270 个提交的 parent 变，tree 不变）。
- 不能用交互式 rebase（本环境不支持），需用脚本 + `git commit-tree` 逐个重放。

## 3. 计划（获确认后执行）

1. **备份**：`git branch backup/w101-pre-squash` + `git tag w101-pre-squash`，并把原始 HEAD 写入本文件。
2. **重放**：从根提交开始，按旧→新顺序遍历；非 wip 提交用原 tree + 新 parent 重建（`git commit-tree`，保留作者/时间/信息）；wip 提交按主题归入分组，每组只保留最后一个 wip 的 tree，生成一条有意义的提交信息。
3. **不变量校验**：
   - `git diff <旧HEAD> <新HEAD>` 必须为空（树完全一致）；
   - `git rev-parse <旧HEAD>^{tree} == <新HEAD>^{tree}`；
   - 非 wip 提交数量与内容集合与旧历史一一对应（可用 patch-id 集合比对）；
   - 22 个 wip 提交消失，分组后的新提交数 ≤ 分组数。
4. **切换**：校验通过后在备份分支存在的前提下把 `java-port` 指向新 HEAD。
5. **不推送**：历史已推送到 origin（`2ad7858a`），改写后需要 `--force-with-lease`，**必须由用户确认**；确认项已写入 [`DECISIONS_PENDING.md`](DECISIONS_PENDING.md) 第 1 节。

## 4. 必须先确认的两点

1. **目标集**：squash 22 个显式 wip 提交（`wip:` + `WIP …`），还是连同 63 个"含 wip 字样"的评审记录一起处理？后者会把评审历史也压掉，通常不该做。
2. **分组粒度**：按主题分组（建议 5–7 组，例如 accessory / gear-config / qio-transaction / config-owner / recipe-viewer / release-smoke），还是把所有 wip 合成**单条**提交？单条最省事但丢失主题可读性。

在这两点确认前不执行改写，以免产出与预期不符的历史。
