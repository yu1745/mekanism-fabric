package mekanism.common.content.gear.mekasuit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import mekanism.test.TileLevelStub;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class HydrostaticFluidContactTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void checksIntersectingFluidSurfaceAndDoesNotLoadChunks() {
        TileLevelStub level = TileLevelStub.create();
        level.installChunk(0, 0);
        Player player = new Player(level, new BlockPos(8, 64, 8), 0, new GameProfile(UUID.randomUUID(), "test")) {
            @Override public boolean isSpectator() { return false; }
            @Override public boolean isCreative() { return false; }
        };
        player.setPos(8.5, 64, 8.5);
        BlockPos upper = new BlockPos(8, 65, 8);
        level.getChunk(0, 0).getSection(level.getSectionIndex(upper.getY())).setBlockState(8, 1, 8, Blocks.WATER.defaultBlockState());
        assertTrue(ModuleHydrostaticRepulsorUnit.intersectsFluid(player));
        level.getChunk(0, 0).getSection(level.getSectionIndex(upper.getY())).setBlockState(8, 1, 8, Blocks.AIR.defaultBlockState());
        level.getChunk(0, 0).getSection(level.getSectionIndex(64)).setBlockState(8, 0, 8, Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 7));
        player.setPos(8.5, 64.8, 8.5);
        assertFalse(ModuleHydrostaticRepulsorUnit.intersectsFluid(player));
        player.setPos(15.8, 64, 8.5);
        assertFalse(ModuleHydrostaticRepulsorUnit.intersectsFluid(player));
        assertFalse(level.hasChunkInstalled(1, 0));
    }
}
