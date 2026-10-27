package mekanism.common.integration.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import mekanism.api.Action;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.energy.IMekanismStrictEnergyHandler;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.capabilities.energy.BasicEnergyContainer;
import mekanism.common.capabilities.holder.energy.IEnergyContainerHolder;
import mekanism.common.capabilities.resolver.manager.EnergyHandlerManager;
import mekanism.common.config.ChemicalConfigAccess;
import mekanism.test.TileLevelStub;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class StrictEnergyLookupTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void realFabricLookupPrioritizesExactJoulesWithFeDisabled() {
        var owner = new EnergyOwner();
        var manager = new EnergyHandlerManager(owner, owner);
        assertSame(Capabilities.STRICT_ENERGY.block(), EnergyHandlerManager.STRICT_ENERGY);
        EnergyHandlerManager.STRICT_ENERGY.registerForBlockEntity(
              (blockEntity, side) -> manager.resolve(EnergyHandlerManager.STRICT_ENERGY, side), BlockEntityType.FURNACE);
        var level = TileLevelStub.create();
        var pos = BlockPos.ZERO;
        var state = Blocks.FURNACE.defaultBlockState();
        var tile = new FurnaceBlockEntity(pos, state);
        var previous = ChemicalConfigAccess.serverPolicy();
        try {
            ChemicalConfigAccess.publishServerPolicy(new ChemicalConfigAccess.ServerPolicy(true, 2.5, true));
            var strict = EnergyCompatUtils.getStrictEnergyHandler(level, pos, state, tile, Direction.NORTH);
            assertSame(manager.resolve(EnergyHandlerManager.STRICT_ENERGY, Direction.NORTH), strict);
            assertEquals(0, strict.insertEnergy(1, Action.EXECUTE));
            assertEquals(1, owner.container.getEnergy());
            assertNull(EnergyCompatUtils.getStrictEnergyHandler(level, pos, state, tile, Direction.SOUTH));
            manager.invalidate(EnergyHandlerManager.STRICT_ENERGY, Direction.NORTH);
            assertEquals(1, strict.insertEnergy(1, Action.EXECUTE));
            assertEquals(1, owner.container.getEnergy());
        } finally {
            ChemicalConfigAccess.publishServerPolicy(previous);
        }
    }

    private static final class EnergyOwner implements IMekanismStrictEnergyHandler, IEnergyContainerHolder {
        private final BasicEnergyContainer container = BasicEnergyContainer.create(100, null);
        @Override public boolean canHandleEnergy() { return true; }
        @Override public List<IEnergyContainer> getEnergyContainers(@Nullable Direction side) {
            return side == null || side == Direction.NORTH ? List.of(container) : List.of();
        }
        @Override public void onContentsChanged() { }
    }
}
