package mekanism.fabric.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.energy.IEnergyConversion;
import mekanism.api.energy.IMekanismStrictEnergyHandler;
import mekanism.api.energy.IStrictEnergyHandler;
import mekanism.common.capabilities.energy.BasicEnergyContainer;
import mekanism.common.capabilities.holder.IHolder;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import team.reborn.energy.api.EnergyStorage;

class FabricEnergyLookupTest {

    @Test
    void directLookupAndExposureRequirePhysicalLiveStableBoundariesAndAvoidSelfRecursion() {
        IEnergyConversion conversion = conversion(true, 1);
        SideHandler handler = new SideHandler(Direction.NORTH, true);
        MutableHolder holder = new MutableHolder(Direction.NORTH);
        EnergyStorage exposed = FabricEnergyLookup.expose(handler, Direction.NORTH, holder, conversion);
        assertTrue(exposed instanceof FabricEnergyStorage);
        assertTrue(exposed.supportsInsertion());
        assertTrue(exposed.supportsExtraction());

        try (Transaction transaction = Transaction.openOuter()) {
            assertEquals(10, exposed.insert(10, transaction));
            transaction.commit();
        }
        assertEquals(10, handler.container.getEnergy());
        holder.insert = false;
        assertFalse(exposed.supportsInsertion());
        assertTrue(exposed.supportsExtraction());
        try (Transaction transaction = Transaction.openOuter()) {
            assertEquals(0, exposed.insert(10, transaction));
            transaction.commit();
        }
        assertEquals(10, handler.container.getEnergy());
        holder.insert = true;
        assertTrue(exposed.supportsInsertion());
        holder.extract = false;
        assertTrue(exposed.supportsInsertion());
        assertFalse(exposed.supportsExtraction());
        try (Transaction transaction = Transaction.openOuter()) {
            assertEquals(0, exposed.extract(5, transaction));
            transaction.commit();
        }
        assertEquals(10, handler.container.getEnergy());
        holder.extract = true;
        assertTrue(exposed.supportsExtraction());
        try (Transaction transaction = Transaction.openOuter()) {
            assertEquals(5, exposed.extract(5, transaction));
            transaction.commit();
        }
        assertEquals(5, handler.container.getEnergy());
        assertTrue(handler.seenSides.stream().allMatch(side -> side == Direction.NORTH));
        assertFalse(holder.seenSides.isEmpty());
        assertTrue(holder.seenSides.stream().allMatch(side -> side == Direction.NORTH));

        IStrictEnergyHandler unwrapped = FabricEnergyLookup.wrap(exposed, Direction.NORTH, conversion);
        assertSame(unwrapped, FabricEnergyLookup.wrap(exposed, Direction.NORTH, conversion));
        IEnergyConversion differentConversion = conversion(true, 1);
        IStrictEnergyHandler differentlyConverted = FabricEnergyLookup.wrap(exposed, Direction.NORTH, differentConversion);
        assertInstanceOf(FabricStrictEnergyHandler.class, differentlyConverted);
        assertNotSame(unwrapped, differentlyConverted);
        assertNull(FabricEnergyLookup.wrap(exposed, Direction.SOUTH, conversion));
        assertNull(FabricEnergyLookup.wrap(exposed, Direction.SOUTH, differentConversion));
        assertNull(FabricEnergyLookup.wrap(exposed, Direction.SOUTH, conversion(false, 1)));

        assertNull(FabricEnergyLookup.wrap(null, Direction.NORTH, conversion));
        assertNull(FabricEnergyLookup.wrap(exposed, null, conversion));
        assertNull(FabricEnergyLookup.wrap(exposed, Direction.NORTH, conversion(false, 1)));
        assertNull(FabricEnergyLookup.wrap(exposed, Direction.NORTH, null));
        assertNull(FabricEnergyLookup.find(null, null, Direction.NORTH, conversion));
        assertNull(FabricEnergyLookup.find(null, null, null, conversion));
        assertNull(FabricEnergyLookup.expose(handler, null, holder, conversion));
        assertNull(FabricEnergyLookup.expose(handler, Direction.SOUTH, holder, conversion));
        assertNull(FabricEnergyLookup.expose(handler, Direction.NORTH, holder, conversion(false, 1)));
        assertNull(FabricEnergyLookup.expose(new SideHandler(Direction.NORTH, false), Direction.NORTH, null, conversion));
        assertNull(FabricEnergyLookup.expose(null, Direction.NORTH, null, conversion));
    }

    private static IEnergyConversion conversion(boolean enabled, double rate) {
        return new IEnergyConversion() {
            @Override
            public boolean isEnabled() {
                return enabled;
            }

            @Override
            public double getConversion() {
                return rate;
            }
        };
    }

    private static final class MutableHolder implements IHolder {
        private final Direction allowedSide;
        private final List<Direction> seenSides = new ArrayList<>();
        private boolean insert = true;
        private boolean extract = true;

        private MutableHolder(Direction allowedSide) {
            this.allowedSide = allowedSide;
        }

        @Override
        public boolean canInsert(@Nullable Direction side) {
            seenSides.add(side);
            return insert && side == allowedSide;
        }

        @Override
        public boolean canExtract(@Nullable Direction side) {
            seenSides.add(side);
            return extract && side == allowedSide;
        }
    }

    private static final class SideHandler implements IMekanismStrictEnergyHandler {
        private final Direction exposedSide;
        private final boolean canHandle;
        private final IEnergyContainer container = BasicEnergyContainer.create(100, null);
        private final List<Direction> seenSides = new ArrayList<>();

        private SideHandler(Direction exposedSide, boolean canHandle) {
            this.exposedSide = exposedSide;
            this.canHandle = canHandle;
        }

        @Override
        public boolean canHandleEnergy() {
            return canHandle;
        }

        @Override
        public List<IEnergyContainer> getEnergyContainers(@Nullable Direction side) {
            seenSides.add(side);
            return canHandle && side == exposedSide ? List.of(container) : List.of();
        }

        @Override
        public void onContentsChanged() {
        }
    }
}
