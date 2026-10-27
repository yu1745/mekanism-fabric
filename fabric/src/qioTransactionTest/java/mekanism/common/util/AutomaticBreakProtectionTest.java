package mekanism.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import mekanism.fabric.chemical.FabricChemicalRegistryLifecycle;
import mekanism.test.TileLevelStub;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AutomaticBreakProtectionTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        FabricChemicalRegistryLifecycle.initialize();
    }

    @Test
    void fabricBeforeBreakVetoDeniesTheSharedWorldChangeProbe() {
        TileLevelStub level = TileLevelStub.create();
        level.installChunk(0, 0);
        Player player = new Player(level, BlockPos.ZERO, 0, new GameProfile(UUID.randomUUID(), "test")) {
            @Override public boolean isSpectator() { return false; }
            @Override public boolean isCreative() { return false; }
        };
        AtomicInteger invocations = new AtomicInteger();
        PlayerBlockBreakEvents.BEFORE.register((world, actor, pos, state, blockEntity) -> {
            assertEquals(level, world);
            assertEquals(player, actor);
            assertEquals(BlockPos.ZERO, pos);
            invocations.incrementAndGet();
            return false;
        });
        assertFalse(WorldUtils.canBreakBlock(level, player, BlockPos.ZERO, Blocks.STONE.defaultBlockState()));
        assertEquals(1, invocations.get());
    }
}
