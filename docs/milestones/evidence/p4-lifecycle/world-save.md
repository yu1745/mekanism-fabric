# W-27 正常关闭存档

集成服未注册 `save-all flush` 命令；实际保存通过 owned session stop 正常关闭完成，返回 state=stopped。对应真实日志：

```text
[13:18:50] [Server thread/INFO] (Minecraft) Saving players
[13:18:50] [Server thread/INFO] (Minecraft) Saving worlds
[13:18:50] [Server thread/INFO] (Minecraft) Saving chunks for level 'ServerLevel[Mekanism F1 isolated debug]'/minecraft:overworld
[13:18:50] [Server thread/INFO] (Minecraft) Saving chunks for level 'ServerLevel[Mekanism F1 isolated debug]'/minecraft:the_nether
[13:18:50] [Server thread/INFO] (Minecraft) Saving chunks for level 'ServerLevel[Mekanism F1 isolated debug]'/minecraft:the_end
[13:18:50] [Server thread/INFO] (Minecraft) ThreadedAnvilChunkStorage (Mekanism F1 isolated debug): All chunks are saved
[13:18:50] [Server thread/INFO] (Minecraft) ThreadedAnvilChunkStorage (DIM-1): All chunks are saved
[13:18:50] [Server thread/INFO] (Minecraft) ThreadedAnvilChunkStorage (DIM1): All chunks are saved
[13:18:50] [Server thread/INFO] (Minecraft) ThreadedAnvilChunkStorage: All dimensions are saved
```
