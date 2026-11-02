# 发布清单
本文件是**发布前最后一遍**的可执行清单。全部步骤只用仓库内脚本与 `mdb`，不上传、不 push、不打 tag（打 tag 与上传由用户执行）。

- 版本命名与四 JAR 能力范围见 [`CHANGELOG.md`](../CHANGELOG.md)、安装步骤见 [`INSTALL.md`](INSTALL.md)、许可见 [`LICENSES.md`](LICENSES.md)。
- 当前版本：`10.7.19.85+fabric.1`（四个 JAR 共用一个版本号）。

## 0. 前置：干净的 HEAD

```bash
git status --short          # 必须为空
git log --oneline -1        # 记下 commit，后面写进发布说明
```

## 1. 全量门禁（约 14 分钟）

```bash
scripts/release-check.sh
```

期望：`release-check: PASSED (7 steps)`；`--extended` 为11步，在默认7步后追加四项既有配置／存档／传输场景。默认步骤为

| 步骤 | 内容 | 参考耗时 |
|---|---|---|
| 1 | clean four-JAR build | ~280 s |
| 2 | packaged static four-JAR gates | ~15 s |
| 3 | packaged server (50 mods, 458 values) | ~55 s |
| 4 | paired network (two clients, handshake) | ~80 s |
| 5 | configuration rejection (three clients rejected) | ~105 s |
| 6 | machine runtime smoke | ~65 s |
| 7 | third-party energy interop (Tech Reborn) | ~68 s |

第 2 步同时检查：四 JAR 内容/元数据/access-widener 契约、`fabric.mod.json` 的 entrypoints（`main`、`client`、可选 `emi`/`jade`）、以及**发布 JAR 内不得出现 `dev/emi/`、`mezz/jei/`、`snownee/jade/` 类**。

## 2. 可复现性校验（C-3）

```bash
./build.sh --clean && sha256sum dist/*.jar > /tmp/run1.txt
./build.sh --clean && sha256sum dist/*.jar > /tmp/run2.txt
diff /tmp/run1.txt /tmp/run2.txt && echo REPRODUCIBLE
```

期望：`REPRODUCIBLE`，四个 JAR 哈希逐个相同。

把 `dist/build-info.json` 的 `source_commit` 与第 0 步记下的 commit 对齐；build-info 里的 `jars[].sha256` 必须与 `sha256sum` 一致。

## 3. 干净目录安装演练

```bash
rm -rf /tmp/release-rehearsal && mkdir -p /tmp/release-rehearsal/mods
cp dist/*.jar /tmp/release-rehearsal/mods/
cp fabric/run/../run/mods/fabric-api-*.jar /tmp/release-rehearsal/mods/ 2>/dev/null || true   # Fabric API
# 另需 Fabric Loader 0.19.5 + Java 21；依赖版本见 docs/INSTALL.md
```

按 [`INSTALL.md`](INSTALL.md) 起服，检查日志：

- `Loading 4 mods:` 里出现 `mekanism`、`mekanismtools`、`mekanismadditions`、`mekanismgenerators`
- 无 `Missing or unsupported mandatory dependencies`、无 `NoSuchMethodError`

## 4. 存档往返
用官方样本世界副本（不要改动原件）：

1. 起服加载副本世界，确认区块与机器正常加载、无 registry 缺失报错；
2. 读 （运行内容）与 （库存/侧配置/能量）记录的对照点，确认读出的数值与记录一致；
3. 关服，确认 `level.dat` 与 region 文件无异常增长、日志无异常。

## 5. 客户端抽查（可选，`mdb` owned session）

```bash
mdb session start dev --project fabric --game-dir fabric/run --world "Mekanism F60 Debug"
mdb --session dev gui status --brief
mdb --session dev gui screenshot --mode window --out /tmp/release-client.png
mdb session stop dev
```

若 `fabric/run/mods/` 里放了 JEI / EMI / Jade / WTHIT（WTHIT 还需 `badpackets`），打开物品栏与准星对准机器应分别看到配方分类与 tooltip 读数b 的证据口径）。

## 6. 产物清单与校验和（写进发布说明）

```bash
ls -1 dist/*.jar
sha256sum dist/*.jar
```

当前 HEAD 的产物（示例，实际以第 1/2 步输出为准）：

| 文件 | sha256（前 16 位） |
|---|---|
| `Mekanism-Fabric-Bootstrap-10.7.19.85+fabric.1.jar` | `81cbda9f3405a1a7` |
| `MekanismTools-10.7.19.85+fabric.1-fabric.jar` | `b74c23a5b504a236` |
| `MekanismAdditions-10.7.19.85+fabric.1-fabric.jar` | `e2019d55e61bab1a` |
| `MekanismGenerators-10.7.19.85+fabric.1-fabric.jar` | `3ee7497be5b4375a` |

## 7. 用户侧动作（worker 不执行）

- 打 tag、推送、上传（Modrinth/CurseForge/GitHub Release）
- wip 提交的 squash、submodule 指针更新
- 对外发布说明的最终措辞（可基于 `CHANGELOG.md`）

## 已知限制（发布说明里应如实带上）

- 未覆盖：Sodium/Iris/光影、真实第三方科技模组的能量互通样本
- 可选集成为 JEI / EMI / Jade / WTHIT 四个（编译期依赖、不打包）；未做的集成是 REI / TOP / CraftTweaker / ProjectE / Curios
- 注册表差异：items 1064 vs 1066、tags 444 vs 448
