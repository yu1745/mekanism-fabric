package mekanism.api.heat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import mekanism.common.capabilities.heat.BasicHeatCapacitor;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

class HeatHandlerTest {

    @Test
    void emptyHandlerUsesOfficialAggregateAndInvalidIndexDefaults() {
        TestHandler handler = new TestHandler(Map.of(), List.of(), null);

        assertEquals(0, handler.getHeatCapacitorCount());
        assertEquals(0, handler.getTotalTemperature());
        assertEquals(0, handler.getTotalHeatCapacity());
        assertEquals(HeatAPI.DEFAULT_INVERSE_CONDUCTION, handler.getTotalInverseConduction());
        assertEquals(HeatAPI.DEFAULT_INVERSE_INSULATION, handler.getTotalInverseInsulation(null));
        assertEquals(HeatAPI.AMBIENT_TEMP, handler.getTemperature(-1));
        assertEquals(HeatAPI.DEFAULT_INVERSE_CONDUCTION, handler.getInverseConduction(0));
        assertEquals(HeatAPI.DEFAULT_INVERSE_INSULATION, handler.getInverseInsulation(0, null));
        assertEquals(HeatAPI.DEFAULT_HEAT_CAPACITY, handler.getHeatCapacity(0));
        handler.handleHeat(100);
        handler.handleHeat(0, 100);
    }

    @Test
    void singleCapacitorUsesDirectValuesAndReceivesWholeTransfer() {
        BasicHeatCapacitor capacitor = capacitor(2, 4, 8, 250);
        TestHandler handler = new TestHandler(Map.of(), List.of(capacitor), null);

        assertSame(capacitor, handler.getHeatCapacitor(0, null));
        assertEquals(250, handler.getTotalTemperature());
        assertEquals(2, handler.getTotalHeatCapacity());
        assertEquals(4, handler.getTotalInverseConduction());
        assertEquals(8, handler.getTotalInverseInsulation(null));

        handler.handleHeat(20);
        capacitor.update();
        assertEquals(520, capacitor.getHeat());
    }

    @Test
    void multipleCapacitorsUseCapacityWeightedAggregatesAndDistribution() {
        BasicHeatCapacitor small = capacitor(1, 2, 4, 200);
        BasicHeatCapacitor large = capacitor(3, 6, 8, 400);
        TestHandler handler = new TestHandler(Map.of(), List.of(small, large), null);

        assertEquals(350, handler.getTotalTemperature());
        assertEquals(4, handler.getTotalHeatCapacity());
        assertEquals(5, handler.getTotalInverseConduction());
        assertEquals(7, handler.getTotalInverseInsulation(null));

        handler.handleHeat(40);
        small.update();
        large.update();
        assertEquals(210, small.getHeat());
        assertEquals(1_230, large.getHeat());
    }

    @Test
    void standaloneHandlerDefaultsPreserveEmptyAndWeightedBehavior() {
        PlainHeatHandler empty = new PlainHeatHandler(new double[0], new double[0], new double[0]);
        assertEquals(0, empty.getTotalTemperature());
        assertEquals(0, empty.getTotalHeatCapacity());
        assertEquals(HeatAPI.DEFAULT_INVERSE_CONDUCTION, empty.getTotalInverseConduction());
        empty.handleHeat(50);
        assertEquals(List.of(), empty.handledTransfers);

        PlainHeatHandler weighted = new PlainHeatHandler(new double[]{200, 400}, new double[]{1, 3}, new double[]{2, 6});
        assertEquals(350, weighted.getTotalTemperature());
        assertEquals(4, weighted.getTotalHeatCapacity());
        assertEquals(5, weighted.getTotalInverseConduction());
        weighted.handleHeat(40);
        assertEquals(List.of(10D, 30D), weighted.handledTransfers);
    }

    @Test
    void standaloneSidedDefaultsPropagateTheCurrentSide() {
        AtomicReference<Direction> side = new AtomicReference<>(Direction.NORTH);
        PlainSidedHeatHandler handler = new PlainSidedHeatHandler(side, new double[]{200, 400}, new double[]{1, 3}, new double[]{2, 6});

        assertEquals(350, handler.getTotalTemperature());
        assertTrueOnlySide(handler.observedSides, Direction.NORTH);
        handler.observedSides.clear();
        handler.handleHeat(40);
        assertEquals(List.of(10D, 30D), handler.delegate.handledTransfers);
        assertTrueOnlySide(handler.observedSides, Direction.NORTH);

        handler.observedSides.clear();
        side.set(Direction.SOUTH);
        assertEquals(4, handler.getTotalHeatCapacity());
        assertTrueOnlySide(handler.observedSides, Direction.SOUTH);
    }

    @Test
    void unsidedMekanismMethodsDelegateToConfiguredSide() {
        BasicHeatCapacitor north = capacitor(1, 2, 3, 250);
        BasicHeatCapacitor south = capacitor(1, 4, 5, 350);
        TestHandler handler = new TestHandler(
              Map.of(Direction.NORTH, List.of(north), Direction.SOUTH, List.of(south)),
              List.of(north, south), Direction.NORTH);

        assertEquals(1, handler.getHeatCapacitorCount());
        assertEquals(250, handler.getTotalTemperature());
        assertEquals(2, handler.getTotalInverseConduction());
        assertEquals(3, handler.getTotalInverseInsulation(Direction.NORTH));
        assertEquals(350, handler.getTotalTemperature(Direction.SOUTH));
        assertEquals(0, handler.getTotalTemperature(Direction.EAST));

        handler.handleHeat(10);
        north.update();
        south.update();
        assertEquals(260, north.getHeat());
        assertEquals(350, south.getHeat());
    }

    private static void assertTrueOnlySide(List<Direction> actual, Direction expected) {
        assertEquals(List.of(expected), actual.stream().distinct().toList());
    }

    private static BasicHeatCapacitor capacitor(double capacity, double conduction, double insulation, double ambient) {
        BasicHeatCapacitor capacitor = BasicHeatCapacitor.create(capacity, conduction, insulation, () -> ambient, null);
        capacitor.getHeat();
        return capacitor;
    }

    private static class PlainHeatHandler implements IHeatHandler {
        private final double[] temperatures;
        private final double[] capacities;
        private final double[] conduction;
        private final List<Double> handledTransfers = new ArrayList<>();

        private PlainHeatHandler(double[] temperatures, double[] capacities, double[] conduction) {
            this.temperatures = temperatures;
            this.capacities = capacities;
            this.conduction = conduction;
        }

        @Override
        public int getHeatCapacitorCount() {
            return temperatures.length;
        }

        @Override
        public double getTemperature(int capacitor) {
            return temperatures[capacitor];
        }

        @Override
        public double getInverseConduction(int capacitor) {
            return conduction[capacitor];
        }

        @Override
        public double getHeatCapacity(int capacitor) {
            return capacities[capacitor];
        }

        @Override
        public void handleHeat(int capacitor, double transfer) {
            handledTransfers.add(transfer);
        }
    }

    private static class PlainSidedHeatHandler implements ISidedHeatHandler {
        private final AtomicReference<Direction> defaultSide;
        private final PlainHeatHandler delegate;
        private final List<Direction> observedSides = new ArrayList<>();

        private PlainSidedHeatHandler(AtomicReference<Direction> defaultSide, double[] temperatures, double[] capacities, double[] conduction) {
            this.defaultSide = defaultSide;
            delegate = new PlainHeatHandler(temperatures, capacities, conduction);
        }

        @Override
        public @Nullable Direction getHeatSideFor() {
            return defaultSide.get();
        }

        @Override
        public int getHeatCapacitorCount(@Nullable Direction side) {
            observedSides.add(side);
            return delegate.getHeatCapacitorCount();
        }

        @Override
        public double getTemperature(int capacitor, @Nullable Direction side) {
            observedSides.add(side);
            return delegate.getTemperature(capacitor);
        }

        @Override
        public double getInverseConduction(int capacitor, @Nullable Direction side) {
            observedSides.add(side);
            return delegate.getInverseConduction(capacitor);
        }

        @Override
        public double getHeatCapacity(int capacitor, @Nullable Direction side) {
            observedSides.add(side);
            return delegate.getHeatCapacity(capacitor);
        }

        @Override
        public void handleHeat(int capacitor, double transfer, @Nullable Direction side) {
            observedSides.add(side);
            delegate.handleHeat(capacitor, transfer);
        }
    }

    private record TestHandler(Map<Direction, List<IHeatCapacitor>> sided, List<IHeatCapacitor> internal,
                               @Nullable Direction defaultSide) implements IMekanismHeatHandler {
        @Override
        public @Nullable Direction getHeatSideFor() {
            return defaultSide;
        }

        @Override
        public List<IHeatCapacitor> getHeatCapacitors(@Nullable Direction side) {
            return side == null ? internal : sided.getOrDefault(side, List.of());
        }

        @Override
        public void onContentsChanged() {
        }
    }
}
