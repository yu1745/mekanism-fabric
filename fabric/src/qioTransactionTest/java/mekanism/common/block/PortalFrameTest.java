package mekanism.common.block;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import mekanism.common.block.basic.BlockResource;
import mekanism.common.resource.BlockResourceInfo;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.PortalShape;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PortalFrameTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void refinedObsidianCanFormAFrameWithoutBroadeningOtherResources() {
        BlockState refined = new BlockResource(BlockResourceInfo.REFINED_OBSIDIAN).defaultBlockState();
        BlockState steel = new BlockResource(BlockResourceInfo.STEEL).defaultBlockState();
        assertTrue(validFrame(refined, refined));
        assertTrue(validFrame(Blocks.OBSIDIAN.defaultBlockState(), refined));
        assertTrue(validFrame(Blocks.OBSIDIAN.defaultBlockState(), Blocks.OBSIDIAN.defaultBlockState()));
        assertFalse(validFrame(refined, steel));
    }

    private static boolean validFrame(BlockState first, BlockState second) {
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        for (int x = 0; x < 4; x++) {
            for (int y = 0; y < 5; y++) {
                if (x == 0 || x == 3 || y == 0 || y == 4) {
                    blocks.put(new BlockPos(x, y, 0), (x + y) % 2 == 0 ? first : second);
                }
            }
        }
        LevelAccessor level = (LevelAccessor) Proxy.newProxyInstance(LevelAccessor.class.getClassLoader(), new Class<?>[]{LevelAccessor.class},
              (proxy, method, args) -> switch (method.getName()) {
                  case "getBlockState" -> blocks.getOrDefault(args[0], Blocks.AIR.defaultBlockState());
                  case "getMinBuildHeight" -> -64;
                  default -> throw new UnsupportedOperationException(method.toString());
              });
        return PortalShape.findEmptyPortalShape(level, new BlockPos(1, 1, 0), Direction.Axis.X).isPresent();
    }
}
