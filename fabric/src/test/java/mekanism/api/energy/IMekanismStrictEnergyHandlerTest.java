package mekanism.api.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.common.capabilities.energy.BasicEnergyContainer;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

class IMekanismStrictEnergyHandlerTest {

    @Test
    void containerBackedDefaultsCoverIndexesCountsValuesAndClampedSetting() {
        TrackingContainer container = new TrackingContainer("only", 100);
        container.setEnergy(40);
        TestHandler handler = new TestHandler(List.of(container), Map.of());

        assertTrue(handler.canHandleEnergy());
        assertSame(container, handler.getEnergyContainer(0, null));
        assertNull(handler.getEnergyContainer(-1, null));
        assertNull(handler.getEnergyContainer(1, null));
        assertEquals(1, handler.getEnergyContainerCount(null));
        assertEquals(40, handler.getEnergy(0, null));
        assertEquals(100, handler.getMaxEnergy(0, null));
        assertEquals(60, handler.getNeededEnergy(0, null));
        assertEquals(0, handler.getEnergy(-1, null));
        assertEquals(0, handler.getMaxEnergy(2, null));
        assertEquals(0, handler.getNeededEnergy(2, null));

        handler.setEnergy(9, 75, null);
        assertEquals(40, container.getEnergy());
        handler.setEnergy(0, -20, null);
        assertEquals(0, container.getEnergy());
    }

    @Test
    void perContainerDefaultsChooseAutomationFromNullableSide() {
        TrackingContainer container = new TrackingContainer("only", 200);
        container.setEnergy(100);
        TestHandler handler = new TestHandler(List.of(container), Map.of(Direction.EAST, List.of(container)));
        container.transfers.clear();

        assertEquals(0, handler.insertEnergy(0, 25, null, Action.SIMULATE));
        assertEquals(0, handler.insertEnergy(0, 25, Direction.EAST, Action.EXECUTE));
        assertEquals(30, handler.extractEnergy(0, 30, null, Action.SIMULATE));
        assertEquals(30, handler.extractEnergy(0, 30, Direction.EAST, Action.EXECUTE));
        assertEquals(List.of(
              new Transfer("insert", "only", 25, Action.SIMULATE, AutomationType.INTERNAL),
              new Transfer("insert", "only", 25, Action.EXECUTE, AutomationType.EXTERNAL),
              new Transfer("extract", "only", 30, Action.SIMULATE, AutomationType.INTERNAL),
              new Transfer("extract", "only", 30, Action.EXECUTE, AutomationType.EXTERNAL)), normalized(container.transfers));
        assertEquals(95, container.getEnergy());

        assertEquals(17, handler.insertEnergy(5, 17, null, Action.EXECUTE));
        assertEquals(0, handler.extractEnergy(-1, 17, Direction.EAST, Action.EXECUTE));
    }

    @Test
    void aggregateDefaultsLookupOnceAndPreserveSequentialSimulationAndExecution() {
        TrackingContainer empty = new TrackingContainer("empty", 50);
        TrackingContainer partial = new TrackingContainer("partial", 100);
        partial.setEnergy(80);
        TestHandler handler = new TestHandler(List.of(empty, partial), Map.of());
        handler.lookups.clear();
        empty.transfers.clear();
        partial.transfers.clear();

        assertEquals(0, handler.insertEnergy(60, null, Action.SIMULATE));
        assertEquals(1, handler.lookups.size());
        assertNull(handler.lookups.getFirst());
        assertEquals(List.of(
              new Transfer("insert", "partial", 60, Action.SIMULATE, AutomationType.INTERNAL),
              new Transfer("insert", "empty", 40, Action.SIMULATE, AutomationType.INTERNAL)),
              joinedTransfers(empty, partial));
        assertEquals(List.of(0L, 80L), energy(empty, partial));

        handler.lookups.clear();
        empty.transfers.clear();
        partial.transfers.clear();
        assertEquals(0, handler.insertEnergy(60, null, Action.EXECUTE));
        assertEquals(1, handler.lookups.size());
        assertNull(handler.lookups.getFirst());
        assertEquals(List.of(
              new Transfer("insert", "partial", 60, Action.EXECUTE, AutomationType.INTERNAL),
              new Transfer("insert", "empty", 40, Action.EXECUTE, AutomationType.INTERNAL)),
              joinedTransfers(empty, partial));
        assertEquals(List.of(40L, 100L), energy(empty, partial));
    }

    @Test
    void sidedAggregateExtractionKeepsTrueLongValuesSingleLookupAndAction() {
        long request = (long) Integer.MAX_VALUE + 100L;
        TrackingContainer first = new TrackingContainer("first", 100);
        TrackingContainer second = new TrackingContainer("second", request + 100L);
        first.setEnergy(25);
        second.setEnergy(request);
        TestHandler handler = new TestHandler(List.of(), Map.of(Direction.SOUTH, List.of(first, second)));
        handler.lookups.clear();
        first.transfers.clear();
        second.transfers.clear();

        assertEquals(request, handler.extractEnergy(request, Direction.SOUTH, Action.SIMULATE));
        assertEquals(List.of(Direction.SOUTH), handler.lookups);
        assertEquals(List.of(
              new Transfer("extract", "first", request, Action.SIMULATE, AutomationType.EXTERNAL),
              new Transfer("extract", "second", request - 25, Action.SIMULATE, AutomationType.EXTERNAL)),
              joinedTransfers(first, second));
        assertEquals(List.of(25L, request), energy(first, second));

        handler.lookups.clear();
        first.transfers.clear();
        second.transfers.clear();
        assertEquals(request, handler.extractEnergy(request, Direction.SOUTH, Action.EXECUTE));
        assertEquals(List.of(Direction.SOUTH), handler.lookups);
        assertEquals(List.of(
              new Transfer("extract", "first", request, Action.EXECUTE, AutomationType.EXTERNAL),
              new Transfer("extract", "second", request - 25, Action.EXECUTE, AutomationType.EXTERNAL)),
              joinedTransfers(first, second));
        assertEquals(List.of(0L, 25L), energy(first, second));
    }

    private static List<Long> energy(TrackingContainer... containers) {
        List<Long> energy = new ArrayList<>();
        for (TrackingContainer container : containers) {
            energy.add(container.getEnergy());
        }
        return energy;
    }

    private static List<Transfer> joinedTransfers(TrackingContainer... containers) {
        List<Transfer> transfers = new ArrayList<>();
        for (TrackingContainer container : containers) {
            transfers.addAll(container.transfers);
        }
        transfers.sort((first, second) -> Long.compare(first.sequence(), second.sequence()));
        return normalized(transfers);
    }

    private static List<Transfer> normalized(List<Transfer> transfers) {
        return transfers.stream()
              .map(transfer -> new Transfer(transfer.operation(), transfer.container(), transfer.amount(), transfer.action(), transfer.automation()))
              .toList();
    }

    private record Transfer(String operation, String container, long amount, Action action, AutomationType automation, long sequence) {
        private Transfer(String operation, String container, long amount, Action action, AutomationType automation) {
            this(operation, container, amount, action, automation, -1);
        }
    }

    private static final class TrackingContainer extends BasicEnergyContainer {
        private static long nextSequence;
        private final String name;
        private final List<Transfer> transfers = new ArrayList<>();

        private TrackingContainer(String name, long capacity) {
            super(capacity, automation -> true, automation -> true, null);
            this.name = name;
        }

        @Override
        public long insert(long amount, Action action, AutomationType automationType) {
            transfers.add(new Transfer("insert", name, amount, action, automationType, nextSequence++));
            return super.insert(amount, action, automationType);
        }

        @Override
        public long extract(long amount, Action action, AutomationType automationType) {
            transfers.add(new Transfer("extract", name, amount, action, automationType, nextSequence++));
            return super.extract(amount, action, automationType);
        }
    }

    private static final class TestHandler implements IMekanismStrictEnergyHandler {
        private final List<IEnergyContainer> internal;
        private final Map<Direction, List<IEnergyContainer>> sided;
        private final List<Direction> lookups = new ArrayList<>();

        private TestHandler(List<? extends IEnergyContainer> internal, Map<Direction, ? extends List<? extends IEnergyContainer>> sided) {
            this.internal = List.copyOf(internal);
            this.sided = new java.util.EnumMap<>(Direction.class);
            sided.forEach((side, containers) -> this.sided.put(side, List.copyOf(containers)));
        }

        @Override
        public List<IEnergyContainer> getEnergyContainers(@Nullable Direction side) {
            lookups.add(side);
            return side == null ? internal : sided.getOrDefault(side, List.of());
        }

        @Override
        public void onContentsChanged() {
        }
    }
}
