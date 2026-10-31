# W-68 第三方集成边界页

状态：**已批准（2026-10-31）：先 EMI（W-74a）再 Jade（W-74b）**；JEI、WTHIT 未批准，REI/TOP 不做。本页只给边界、依赖、工作量与验证方式，不含代码改动。

## 现状（仓库事实）

- 配方查看器总规模：`src/main` 120 个 `client/recipe_viewer/**` 文件，三个附加模块另有 14 个。其中 **JEI 适配 41 个**、**EMI 适配 39 个**（两者有少量共享工具）。
- Fabric 构建当前只纳入**与 loader 无关的抽象层** 14 个文件（`fabric/common-slice.txt` 266–279 行）：`RecipeViewerUtils`、`INamedRVRecipe`、三个 `IRecipeViewer*` 接口、两个 recipe 视图（Boiler/SPS）、五个 `type/*`（`FakeRVRecipeType`、`RVRecipeTypeWrapper`、`RecipeViewerRecipeType`、`RotaryRVRecipeType`、`SimpleRVRecipeType`、`VanillaRVRecipeType`）。也就是说"配方分类 + 配方条目"这层已经是平台无关的，缺的是**第三方查看器的桥**。
- Looking-at（对准方块显示信息）：`common/integration/lookingat/**` 共 27 个文件，按目标分成三组——Jade 7 个、WTHIT 5 个、TOP 6 个，另有 9 个共享元素类（`ChemicalElement`/`EnergyElement`/`FluidElement`/`TextElement`/`LookingAtUtils` 等）。

## 候选与边界

| 目标 | 上游文件 | Fabric 可用性 | 需要的适配 | 估算 | 备注 |
|---|---|---|---|---|---|
| **EMI** | 39（`MekanismEmi`、`MekanismEmiHelper`、`ChemicalEmiStack`、`ChemicalEmiIngredientSerializer`、`EmiExclusionHandler`、`EmiGhostIngredientHandler`、`EmiStackUnderMouseProvider`、`MekanismEmiRecipeCategory`、`emi/recipe/**` 26、`emi/transfer/**`） | 原生 Fabric 模组，MIT | 直接编译 + 注册 `emi` entrypoint；化学品需要自定义 `EmiIngredient` 序列化（上游已有实现） | 最小，1–2 天 | 与现有抽象最贴近，风险最低 |
| **JEI** | 41（`MekanismJEI`、`MekanismJEIHelper`、`ChemicalStackHelper`/`Renderer`、`MekanismSubtypeInterpreter`、`jei/machine/**` 24 个分类、`JeiGhostIngredientHandler`、`JeiGuiElementHandler`、`JeiQIOCraftingTransferHandler`、`CatalystRegistryHelper`、`RecipeRegistryHelper`、`MekJeiWidget`） | 官方有 Fabric 构建 | 插件注册走 Fabric 的 `jei_mod_plugin` entrypoint；API 基本同名 | 中，2–4 天 | 分类最多，QIO 合成转移需要单独核对 |
| **REI** | 无上游实现 | 原生 Fabric，但上游 Mekanism 未支持 | 需要从零写分类与渲染 | 大，≥1 周 | 建议不做，除非用户明确要求 |
| **Jade** | 7（`MekanismJadePlugin`、`JadeDataProvider`、`JadeEntityDataProvider`、`JadeTooltipRenderer`、`JadeLookingAtHelper`、`JadeConstants`、`JadeBuiltinRemover`） | 原生 Fabric，MIT | 插件注册 + 现有共享元素类编译纳入 | 最小，0.5–1 天 | 与 EMI 同一档收益/风险 |
| **WTHIT** | 5（`MekanismWTHITPlugin`、`WTHITDataProvider`、`WTHITEntityDataProvider`、`WTHITTooltipRenderer`、`WTHITLookingAtHelper`） | 原生 Fabric，MIT | 同上 | 最小，0.5–1 天 | 与 Jade 二选一即可先做 |
| **TOP** | 6 | NeoForge 专用 | 不适用 | — | 不做，Fabric 上用 Jade/WTHIT 取代 |

## EMI 依赖事实（W-74a，2026-10-31）

- 坐标：`maven.modrinth:emi:1.1.24+1.21.1+fabric`，来自 Modrinth Maven（`https://api.modrinth.com/maven`），仓库块只放行 `maven.modrinth` 组。
- 许可证：**MIT**。只作 `modCompileOnly`（`transitive = false`）参与编译，另由 `-PemiDev` 开关挂 `modLocalRuntime` 供客户端门禁使用；**不随发布 JAR 分发**。
- 断言：`scripts/fabric-packaged-smoke.sh` 静态阶段检查发布 JAR 内不得出现 `dev/emi/`、`mezz/jei/`、`snownee/jade/` 类，防止集成代码泄漏进发布物。

## 依赖与许可证

- EMI（MIT）、Jade（MIT）、WTHIT（MIT）、JEI（MIT，Fabric 构建与 NeoForge 同 API）都可作为**客户端可选依赖**（`suggests`/运行期探测），不得进入发布必需依赖或源码切片之外。
- 化学品作为自定义 ingredient 的序列化是三家共有的难点：EMI 有现成 `ChemicalEmiIngredientSerializer`，JEI 有 `ChemicalStackHelper`+`MekanismSubtypeInterpreter`，大改点在**配方查看器的数据来源**（`type/*` 已平台无关，直接复用）。

## 建议顺序（若获批）

1. **EMI**（风险最低，与抽象最贴合，含化学品序列化现成实现）。
2. **Jade**（收益/成本比最高，7 个文件 + 共享元素类）。
3. **JEI**（分类多，需逐类核对渲染与转移）。
4. REI 与 TOP 不做。

## 验收方式（若获批）

- 客户端验证：用 `mdb session start` 起的开发客户端（装入选定查看器）打开机器界面，断言查看器能列出该机器配方（mdb `gui screen` 读窗口）；不自行启动裸客户端。
- 化学品显示：至少一种化学品（乙烯）作为 ingredient 出现在分类中，且鼠标悬停时 `status`/`gui problems` 无异常。
- 回归：`fabric-client-window-smoke.sh` 与既有 GUI 门禁保持通过，化学品/能量条仍正常渲染。

## 非目标

- 不做 REI；不做 TOP；不改配方数据本身；不为第三方查看器引入 Kotlin 或复制上游源码到切片之外。
