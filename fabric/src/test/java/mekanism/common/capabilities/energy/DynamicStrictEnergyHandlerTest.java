package mekanism.common.capabilities.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.energy.IEnergyContainer;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

class DynamicStrictEnergyHandlerTest {

    @Test
    void deniedOperationsPreservePolicyResultsWithoutLookingUpContainers() {
        List<Direction> inserts = new ArrayList<>();
        List<Direction> extracts = new ArrayList<>();
        AtomicInteger lookups = new AtomicInteger();
        DynamicStrictEnergyHandler handler = new DynamicStrictEnergyHandler(side -> {
            lookups.incrementAndGet();
            return List.of();
        }, side -> {
            extracts.add(side);
            return false;
        }, side -> {
            inserts.add(side);
            return false;
        }, null);

        assertEquals(31, handler.insertEnergy(0, 31, null, Action.SIMULATE));
        assertEquals(32, handler.insertEnergy(32, Direction.NORTH, Action.EXECUTE));
        assertEquals(0, handler.extractEnergy(0, 33, Direction.SOUTH, Action.SIMULATE));
        assertEquals(0, handler.extractEnergy(34, null, Action.EXECUTE));
        assertEquals(Arrays.asList(null, Direction.NORTH), inserts);
        assertEquals(Arrays.asList(Direction.SOUTH, null), extracts);
        assertEquals(0, lookups.get());
    }

    @Test
    void allowedOperationsDelegateOnceWithExactBridgeArguments() {
        RecordingContainer container = new RecordingContainer();
        List<Direction> lookups = new ArrayList<>();
        List<Direction> inserts = new ArrayList<>();
        List<Direction> extracts = new ArrayList<>();
        DynamicStrictEnergyHandler handler = new DynamicStrictEnergyHandler(side -> {
            lookups.add(side);
            return List.of(container);
        }, side -> {
            extracts.add(side);
            return side == Direction.SOUTH;
        }, side -> {
            inserts.add(side);
            return side == Direction.NORTH;
        }, null);

        assertEquals(0, handler.insertEnergy(0, 11, Direction.NORTH, Action.SIMULATE));
        assertEquals(7, handler.extractEnergy(0, 7, Direction.SOUTH, Action.EXECUTE));
        assertEquals(0, handler.insertEnergy(13, Direction.NORTH, Action.EXECUTE));
        assertEquals(5, handler.extractEnergy(5, Direction.SOUTH, Action.SIMULATE));
        assertEquals(List.of(Direction.NORTH, Direction.SOUTH, Direction.NORTH, Direction.SOUTH), lookups);
        assertEquals(List.of(Direction.NORTH, Direction.NORTH), inserts);
        assertEquals(List.of(Direction.SOUTH, Direction.SOUTH), extracts);
        assertEquals(List.of(
              new Call("insert", 11, Action.SIMULATE, AutomationType.EXTERNAL),
              new Call("extract", 7, Action.EXECUTE, AutomationType.EXTERNAL),
              new Call("insert", 13, Action.EXECUTE, AutomationType.EXTERNAL),
              new Call("extract", 5, Action.SIMULATE, AutomationType.EXTERNAL)), container.calls);
    }

    @Test
    void containerLookupForwardsNullableSideUnchanged() {
        List<Direction> sides = new ArrayList<>();
        List<IEnergyContainer> containers = List.of(new RecordingContainer());
        DynamicStrictEnergyHandler handler = new DynamicStrictEnergyHandler(side -> {
            sides.add(side);
            return containers;
        }, side -> true, side -> true, null);

        assertSame(containers, handler.getEnergyContainers(null));
        assertSame(containers, handler.getEnergyContainers(Direction.WEST));
        assertEquals(Arrays.asList(null, Direction.WEST), sides);
    }

    @Test
    void contentsListenerIsOptionalAndForwardedOnce() {
        AtomicInteger changes = new AtomicInteger();
        DynamicStrictEnergyHandler present = new DynamicStrictEnergyHandler(side -> List.of(), side -> true, side -> true, changes::incrementAndGet);
        DynamicStrictEnergyHandler absent = new DynamicStrictEnergyHandler(side -> List.of(), side -> true, side -> true, null);

        present.onContentsChanged();
        absent.onContentsChanged();
        assertEquals(1, changes.get());
    }

    private record Call(String operation, long amount, Action action, AutomationType automation) {
    }

    private static final class RecordingContainer extends BasicEnergyContainer {
        private final List<Call> calls = new ArrayList<>();

        private RecordingContainer() {
            super(100, automation -> true, automation -> true, null);
            setEnergy(20);
        }

        @Override
        public long insert(long amount, Action action, AutomationType automation) {
            calls.add(new Call("insert", amount, action, automation));
            return super.insert(amount, action, automation);
        }

        @Override
        public long extract(long amount, Action action, AutomationType automation) {
            calls.add(new Call("extract", amount, action, automation));
            return super.extract(amount, action, automation);
        }
    }
}
