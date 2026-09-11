package mekanism.common.capabilities.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import mekanism.api.Action;
import mekanism.api.energy.ISidedStrictEnergyHandler;
import mekanism.api.heat.ISidedHeatHandler;
import mekanism.common.capabilities.holder.IHolder;
import mekanism.common.capabilities.holder.ProxiedHolder;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

class ProxyHandlerTest {

    @Test
    void nullSideForwardsReadsButSuppressesEveryMutation() {
        RecordingEnergy energy = new RecordingEnergy();
        ProxyStrictEnergyHandler energyProxy = new ProxyStrictEnergyHandler(energy, null, null);
        assertEquals(2, energyProxy.getEnergyContainerCount());
        assertEquals(101, energyProxy.getEnergy(3));
        assertEquals(202, energyProxy.getMaxEnergy(4));
        assertEquals(303, energyProxy.getNeededEnergy(5));
        assertNull(energy.lastSide);
        energyProxy.setEnergy(0, 9);
        assertEquals(17, energyProxy.insertEnergy(1, 17, Action.EXECUTE));
        assertEquals(18, energyProxy.insertEnergy(18, Action.SIMULATE));
        assertEquals(0, energyProxy.extractEnergy(2, 19, Action.EXECUTE));
        assertEquals(0, energyProxy.extractEnergy(20, Action.SIMULATE));
        assertTrue(energy.mutations.isEmpty());

        RecordingHeat heat = new RecordingHeat();
        ProxyHeatHandler heatProxy = new ProxyHeatHandler(heat, null, null);
        assertEquals(2, heatProxy.getHeatCapacitorCount());
        assertEquals(11.5, heatProxy.getTemperature(1));
        assertEquals(12.5, heatProxy.getInverseConduction(2));
        assertEquals(13.5, heatProxy.getHeatCapacity(3));
        assertEquals(14.5, heatProxy.getTotalTemperature());
        assertEquals(15.5, heatProxy.getTotalInverseConduction());
        assertEquals(16.5, heatProxy.getTotalHeatCapacity());
        assertNull(heat.lastSide);
        heatProxy.handleHeat(4, 21.5);
        heatProxy.handleHeat(22.5);
        assertTrue(heat.mutations.isEmpty());
    }

    @Test
    void sidedEnergyForwardsAllowedCallsAndIndependentlyDeniesInsertAndExtract() {
        RecordingEnergy energy = new RecordingEnergy();
        MutableHolder holder = new MutableHolder();
        ProxyStrictEnergyHandler proxy = new ProxyStrictEnergyHandler(energy, Direction.WEST, holder);
        assertEquals(2, proxy.getEnergyContainerCount());
        assertEquals(101, proxy.getEnergy(7));
        assertEquals(202, proxy.getMaxEnergy(8));
        assertEquals(303, proxy.getNeededEnergy(9));
        assertEquals(Direction.WEST, energy.lastSide);
        proxy.setEnergy(4, 40);
        assertEquals(5, proxy.insertEnergy(6, 60, Action.SIMULATE));
        assertEquals(6, proxy.insertEnergy(61, Action.EXECUTE));
        assertEquals(7, proxy.extractEnergy(8, 70, Action.EXECUTE));
        assertEquals(8, proxy.extractEnergy(71, Action.SIMULATE));
        assertEquals(List.of("set:4:40:west", "insert:6:60:west:SIMULATE", "insertAll:61:west:EXECUTE",
              "extract:8:70:west:EXECUTE", "extractAll:71:west:SIMULATE"), energy.mutations);

        holder.insert = false;
        assertEquals(80, proxy.insertEnergy(1, 80, Action.EXECUTE));
        assertEquals(81, proxy.insertEnergy(81, Action.SIMULATE));
        holder.insert = true;
        holder.extract = false;
        assertEquals(0, proxy.extractEnergy(2, 82, Action.EXECUTE));
        assertEquals(0, proxy.extractEnergy(83, Action.SIMULATE));
        assertEquals(5, energy.mutations.size());
    }

    @Test
    void sidedHeatForwardsIndexedAndAggregateReadsAndMutations() {
        RecordingHeat heat = new RecordingHeat();
        ProxyHeatHandler proxy = new ProxyHeatHandler(heat, Direction.UP, null);
        assertEquals(2, proxy.getHeatCapacitorCount());
        assertEquals(11.5, proxy.getTemperature(1));
        assertEquals(12.5, proxy.getInverseConduction(2));
        assertEquals(13.5, proxy.getHeatCapacity(3));
        assertEquals(14.5, proxy.getTotalTemperature());
        assertEquals(15.5, proxy.getTotalInverseConduction());
        assertEquals(16.5, proxy.getTotalHeatCapacity());
        proxy.handleHeat(4, -2.25);
        proxy.handleHeat(7.75);
        assertEquals(Direction.UP, heat.lastSide);
        assertEquals(List.of("indexed:4:-2.25:up", "aggregate:7.75:up"), heat.mutations);
    }

    @Test
    void proxiedHolderAppliesNullableInsertAndExtractPredicatesIndependently() {
        List<Direction> inserts = new ArrayList<>();
        List<Direction> extracts = new ArrayList<>();
        ProxiedHolder holder = new ProxiedHolder(side -> { inserts.add(side); return side == Direction.NORTH; },
              side -> { extracts.add(side); return side == null; }) { };
        assertFalse(holder.canInsert(null));
        assertTrue(holder.canInsert(Direction.NORTH));
        assertTrue(holder.canExtract(null));
        assertFalse(holder.canExtract(Direction.NORTH));
        assertEquals(java.util.Arrays.asList(null, Direction.NORTH), inserts);
        assertEquals(java.util.Arrays.asList(null, Direction.NORTH), extracts);
    }

    private static final class MutableHolder implements IHolder {
        boolean insert = true;
        boolean extract = true;
        @Override public boolean canInsert(Direction side) { return insert; }
        @Override public boolean canExtract(Direction side) { return extract; }
    }

    private static final class RecordingEnergy implements ISidedStrictEnergyHandler {
        Direction lastSide;
        final List<String> mutations = new ArrayList<>();
        @Override public int getEnergyContainerCount(Direction side) { lastSide = side; return 2; }
        @Override public long getEnergy(int container, Direction side) { lastSide = side; return 101; }
        @Override public void setEnergy(int container, long value, Direction side) { lastSide = side; mutations.add("set:" + container + ':' + value + ':' + side); }
        @Override public long getMaxEnergy(int container, Direction side) { lastSide = side; return 202; }
        @Override public long getNeededEnergy(int container, Direction side) { lastSide = side; return 303; }
        @Override public long insertEnergy(int container, long amount, Direction side, Action action) { lastSide = side; mutations.add("insert:" + container + ':' + amount + ':' + side + ':' + action); return 5; }
        @Override public long extractEnergy(int container, long amount, Direction side, Action action) { lastSide = side; mutations.add("extract:" + container + ':' + amount + ':' + side + ':' + action); return 7; }
        @Override public long insertEnergy(long amount, Direction side, Action action) { lastSide = side; mutations.add("insertAll:" + amount + ':' + side + ':' + action); return 6; }
        @Override public long extractEnergy(long amount, Direction side, Action action) { lastSide = side; mutations.add("extractAll:" + amount + ':' + side + ':' + action); return 8; }
    }

    private static final class RecordingHeat implements ISidedHeatHandler {
        Direction lastSide;
        final List<String> mutations = new ArrayList<>();
        @Override public int getHeatCapacitorCount(Direction side) { lastSide = side; return 2; }
        @Override public double getTemperature(int capacitor, Direction side) { lastSide = side; return 11.5; }
        @Override public double getInverseConduction(int capacitor, Direction side) { lastSide = side; return 12.5; }
        @Override public double getHeatCapacity(int capacitor, Direction side) { lastSide = side; return 13.5; }
        @Override public void handleHeat(int capacitor, double transfer, Direction side) { lastSide = side; mutations.add("indexed:" + capacitor + ':' + transfer + ':' + side); }
        @Override public double getTotalTemperature(Direction side) { lastSide = side; return 14.5; }
        @Override public double getTotalInverseConductionCoefficient(Direction side) { lastSide = side; return 15.5; }
        @Override public double getTotalHeatCapacity(Direction side) { lastSide = side; return 16.5; }
        @Override public void handleHeat(double transfer, Direction side) { lastSide = side; mutations.add("aggregate:" + transfer + ':' + side); }
    }
}
