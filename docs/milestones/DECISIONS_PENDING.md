# 待用户决策清单（W-100）

本文件只列**需要用户拍板**的事项。worker 已完成或可自行推进的内容不在此列，见 [`ROADMAP.md`](ROADMAP.md) 与 [`M-release.md`](M-release.md)。

> 说明：主仓库 `server-v2` 的 submodule 指针按用户 2026-10-31 12:20 的裁决**不在本清单内**，不用管。

## 1. 强制推送确认（W-101 收口点）

wip 提交 squash 已获批准且由 worker 执行（见 `ROADMAP.md` W-101）。squash 会改写本地历史，而历史已推送到 origin（`2ad7858a`）。

- **需要用户确认**：是否允许在 squash 完成后执行 `git push --force-with-lease`。
- 在此之前 worker **不会**强制推送；squash 准备好后会在本文件追加「待确认」条目并停在该点。
- 想改为"只在本地 squash、不推送"，也请明确，worker 会保留备份分支与 tag 供你随时切回。

## 2. 版本号与发布形态

当前版本号 `10.7.19.85+fabric.1`（沿用官方 `10.7.19.85` 并加 Fabric 后缀）。

- 是否要以这个版本号正式对外发布（打 tag + 上传 Modrinth/CurseForge/GitHub Release）？
- 若要发布，是否需要在发布前把 `+fabric.1` 改成不带构建元数据的版本（例如 `10.7.19.85-fabric.1`，避免部分平台不接受 `+`）？
- 上传动作按 `docs/RELEASE_CHECKLIST.md` 第 7 节由你执行，worker 不上传、不 push。

## 3. 剩余 W 项的取舍

以下项仍在路线图上，但收益递减或受工具限制，需要你决定是否继续投入：

| 项 | 现状 | 需要你决定 |
|---|---|---|
| W-79 | 预留项，一直未启动 | 还要做吗？具体目标是什么？ |
| W-83 裂变熔毁 | **受阻**：`/mek meltdown` 与 `/mek build` 都要求 `ServerPlayer`，且需要与服务端同模组集的客户端（`runGeneratorsClient` 被 mdb 拒绝启动） | 是否批准用 Gradle 直起 `generatorsClient`（会违反"客户端只能用 mdb 启动"的硬规则，需你明确豁免），或接受该项长期阻塞 |
| W-80/81/82 余项 | 存档往返、规模基线、跨维度已完成主体；余项是涡轮性能、真实区块卸载、跨维度传送 | 是否值得继续深挖，还是记为"已验证范围之外" |
| W-98 Sodium/Iris 抽样 | 未开始；需要把 Sodium/Iris/光影包放进开发客户端 | 要覆盖到哪一档：只装 Sodium？加 Iris？还要不要指定光影包 |
| W-99 第三方能量互通 | 未开始；只在 Team Reborn Energy 自身验证过 | 指定一个真实的 Fabric 科技模组作为互通样本（例如 Modern Industrialization / Tech Reborn / Powah），worker 按其 API 做真实对接验证 |
| W-74c JEI / W-74d WTHIT | **已批准**，未开始（EMI/Jade 已完成） | 是否仍要按原计划做？JEI 41 个文件、WTHIT 5 个文件，成本比 EMI/Jade 高 |

## 4. 已知限制是否接受（发布说明口径）

如果即将发布，这些需要在发布说明里如实写出，请确认口径：

- JEI / WTHIT 尚未实现（若 W-74c/d 不做）；REI / TOP 明确不做。
- `neoforge:` 命名空间的 4 个数据文件未随 Fabric JAR 分发（W-94 判定为 Fabric 上无消费者，若要字节级对齐可补）。
- `GuiBar.IBarInfoHandler` 由官方嵌套接口改为顶层接口（`docs/API_DELTAS.md`），影响直接引用该客户端内部类型的第三方模组。
- 未覆盖：Sodium/Iris（W-98）、真实第三方能量互通（W-99）。

## 5. 其他已记录但未阻塞的事项

- 证据目录体积约 6 MB 以上（首轮预算 4.24 MB），如需精简请说明保留标准。
- `docs/RELEASE_CHECKLIST.md` 的"一次完整演练"仍需在真正发布前跑一遍（W-96 记录）。
