# M-render — 客户端模型与交互验收

## 现状

P5 W-29–33已验证，生产基线`8ad14311`；阶段构建、API边界与机器smoke全部通过。原客户端行为30项已逐一映射，无pending。

## 下一步

进入P6 W-34–35，仅验证五种核心多方块的破坏重建、区块卸载重载、存读档及尺寸失败路径；W-31搭建与GUI证据复用，不重复验收。

## 未决项

任意modifier重绑编辑器、Accessories默认H冲突、第三方集成与未穷举运行组合等限制留W-42发布汇总；W-40前先提交旧／新packaged smoke对照并等待协调者只读审查。P5不代表发布门禁通过。
W-32为4个提交，超过新收紧的3个上限；不自行squash，后续按新约束执行。

## 验收证据

W-29 `be66e52c`：六类OBJ owner映射与连接／断开真机截图；修复原生流体alpha与默认模型光照。映射。

W-30 `44cd7bb1`：四粒子、JSON及直接tick调用方接通；保留DataBased emitting语义，既有focused tests与原生engine验证通过。验收。

W-31 `64fcddea`：全部71个真实menu/screen当时均完成截图，screenFault为空；现保留代表帧和完整JSON读数；未把GUI打开当作P6生命周期完成。覆盖。

W-32 `2f56427a`／`98caf953`／`fa93e072`／`8ad14311`：原属性、颜色、方块粒子、Accessories与流体视觉缺口接通；30项映射无pending。映射、流体验收及限制。

W-33：build275秒、API1.71秒、machine98.59秒全部PASS；578 tests、0 failures/errors。门禁、格式与体积审计。

原命令、哈希、首轮错误与测量纠正移至详细证据及各项JSON；去除日志／完整crash dump／Python traceback，保留首个异常、关键栈与结构化事件。没有修改生产代码或新增测试。
