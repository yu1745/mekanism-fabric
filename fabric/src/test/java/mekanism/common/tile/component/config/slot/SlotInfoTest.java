package mekanism.common.tile.component.config.slot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.heat.IHeatCapacitor;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class SlotInfoTest {

    @Test
    void flagPolicyPreservesEveryFlagCombinationAndUsesOrForEnabled() {
        for (boolean canInput : List.of(false, true)) {
            for (boolean canOutput : List.of(false, true)) {
                BaseSlotInfo info = new TestSlotInfo(canInput, canOutput);
                assertEquals(canInput, info.canInput());
                assertEquals(canOutput, info.canOutput());
                assertEquals(canInput || canOutput, info.isEnabled());
            }
        }
    }

    @Test
    void energyVarargsPreserveOrderAndAreEmptyExactlyWhenAllContainersAreEmpty() {
        EnergySlotInfo zero = new EnergySlotInfo(true, false);
        assertTrue(zero.getContainers().isEmpty());
        assertTrue(zero.isEmpty());

        TestEnergyContainer first = new TestEnergyContainer(0);
        TestEnergyContainer second = new TestEnergyContainer(0);
        TestEnergyContainer third = new TestEnergyContainer(7);
        EnergySlotInfo allEmpty = new EnergySlotInfo(false, true, first, second);
        assertSame(first, allEmpty.getContainers().get(0));
        assertSame(second, allEmpty.getContainers().get(1));
        assertTrue(allEmpty.isEmpty());

        EnergySlotInfo mixed = new EnergySlotInfo(false, true, first, third, second);
        assertSame(first, mixed.getContainers().get(0));
        assertSame(third, mixed.getContainers().get(1));
        assertSame(second, mixed.getContainers().get(2));
        assertFalse(mixed.isEmpty());
    }

    @Test
    void energyEmptyScanUsesSourceOrderAndStopsAtFirstNonEmptyContainer() {
        List<String> reads = new ArrayList<>();
        IEnergyContainer first = new TrackingEnergyContainer("first", 0, reads);
        IEnergyContainer second = new TrackingEnergyContainer("second", 1, reads);
        IEnergyContainer forbidden = new TrackingEnergyContainer("forbidden", 0, reads) {
            @Override
            public long getEnergy() {
                throw new AssertionError("container after the first non-empty entry was consulted");
            }
        };

        assertFalse(new EnergySlotInfo(true, true, first, second, forbidden).isEmpty());
        assertEquals(List.of("first", "second"), reads);
    }

    @Test
    void energyListConstructorRetainsIdentityAndObservesLiveMembershipAndState() {
        List<IEnergyContainer> containers = new ArrayList<>();
        EnergySlotInfo info = new EnergySlotInfo(true, false, containers);
        assertSame(containers, info.getContainers());
        assertTrue(info.isEmpty());

        TestEnergyContainer mutable = new TestEnergyContainer(4);
        containers.add(mutable);
        assertFalse(info.isEmpty());
        mutable.setEnergy(0);
        assertTrue(info.isEmpty());

        TestEnergyContainer later = new TestEnergyContainer(9);
        containers.add(later);
        assertFalse(info.isEmpty());
        containers.remove(later);
        assertTrue(info.isEmpty());
    }

    @Test
    void heatDescriptorPreservesFlagsAndListIdentityWithoutInspectingHeatState() {
        List<IHeatCapacitor> empty = new ArrayList<>();
        HeatSlotInfo emptyInfo = new HeatSlotInfo(true, false, empty);
        assertTrue(emptyInfo.canInput());
        assertFalse(emptyInfo.canOutput());
        assertTrue(emptyInfo.isEnabled());
        assertSame(empty, emptyInfo.getHeatCapacitors());
        assertFalse(emptyInfo.isEmpty());

        List<IHeatCapacitor> populated = new ArrayList<>();
        populated.add(new ExplosiveHeatCapacitor());
        HeatSlotInfo populatedInfo = new HeatSlotInfo(false, true, populated);
        assertFalse(populatedInfo.canInput());
        assertTrue(populatedInfo.canOutput());
        assertTrue(populatedInfo.isEnabled());
        assertSame(populated, populatedInfo.getHeatCapacitors());
        assertFalse(populatedInfo.isEmpty());
    }

    private static final class TestSlotInfo extends BaseSlotInfo {

        private TestSlotInfo(boolean canInput, boolean canOutput) {
            super(canInput, canOutput);
        }

        @Override
        public boolean isEmpty() {
            return true;
        }
    }

    private static class TestEnergyContainer implements IEnergyContainer {

        private long energy;

        private TestEnergyContainer(long energy) {
            this.energy = energy;
        }

        @Override
        public long getEnergy() {
            return energy;
        }

        @Override
        public void setEnergy(long energy) {
            this.energy = energy;
        }

        @Override
        public long getMaxEnergy() {
            return Long.MAX_VALUE;
        }

        @Override
        public void onContentsChanged() {
        }

        @Override
        public void deserializeNBT(HolderLookup.Provider provider, CompoundTag nbt) {
        }
    }

    private static class TrackingEnergyContainer extends TestEnergyContainer {

        private final String name;
        private final List<String> reads;

        private TrackingEnergyContainer(String name, long energy, List<String> reads) {
            super(energy);
            this.name = name;
            this.reads = reads;
        }

        @Override
        public long getEnergy() {
            reads.add(name);
            return super.getEnergy();
        }
    }

    private static final class ExplosiveHeatCapacitor implements IHeatCapacitor {

        @Override
        public double getTemperature() {
            throw new AssertionError("heat state was consulted");
        }

        @Override
        public double getInverseConduction() {
            throw new AssertionError("heat state was consulted");
        }

        @Override
        public double getInverseInsulation() {
            throw new AssertionError("heat state was consulted");
        }

        @Override
        public double getHeatCapacity() {
            throw new AssertionError("heat state was consulted");
        }

        @Override
        public double getHeat() {
            throw new AssertionError("heat state was consulted");
        }

        @Override
        public void setHeat(double heat) {
            throw new AssertionError("heat state was consulted");
        }

        @Override
        public void handleHeat(double transfer) {
            throw new AssertionError("heat state was consulted");
        }

        @Override
        public void onContentsChanged() {
            throw new AssertionError("heat state was consulted");
        }

        @Override
        public void deserializeNBT(HolderLookup.Provider provider, CompoundTag nbt) {
            throw new AssertionError("heat state was consulted");
        }
    }
}
