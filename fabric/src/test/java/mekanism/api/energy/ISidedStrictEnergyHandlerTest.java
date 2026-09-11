package mekanism.api.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import mekanism.api.Action;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

class ISidedStrictEnergyHandlerTest {

    @Test
    void everyUnsidedBridgeUsesTheConfiguredNonNullSide() {
        TrackingSided handler = new FixedSideTrackingSided(Direction.NORTH, 100, 100);
        handler.energy[0] = 20;
        handler.energy[1] = 40;

        assertEquals(2, handler.getEnergyContainerCount());
        assertEquals(20, handler.getEnergy(0));
        handler.setEnergy(0, 25);
        assertEquals(100, handler.getMaxEnergy(0));
        assertEquals(75, handler.getNeededEnergy(0));
        assertEquals(0, handler.insertEnergy(0, 10, Action.SIMULATE));
        assertEquals(10, handler.extractEnergy(0, 10, Action.EXECUTE));
        assertEquals(List.of("count", "get", "set", "max", "needed", "insert", "extract"), handler.operations());
        assertTrue(handler.calls.stream().allMatch(call -> call.side() == Direction.NORTH));
        assertEquals(Action.SIMULATE, handler.calls.stream().filter(call -> call.operation().equals("insert")).findFirst().orElseThrow().action());
        assertEquals(Action.EXECUTE, handler.calls.stream().filter(call -> call.operation().equals("extract")).findFirst().orElseThrow().action());

        handler.energy[0] = 0;
        handler.energy[1] = 30;
        handler.calls.clear();
        assertEquals(0, handler.insertEnergy(80, Action.SIMULATE));
        assertEquals(List.of("count", "get", "get", "insert", "insert"), handler.operations());
        assertTrue(handler.calls.stream().allMatch(call -> call.side() == Direction.NORTH));
        assertTrue(handler.calls.stream().filter(call -> call.action() != null).allMatch(call -> call.action() == Action.SIMULATE));

        handler.energy[0] = 25;
        handler.energy[1] = 30;
        handler.calls.clear();
        assertEquals(40, handler.extractEnergy(40, Action.EXECUTE));
        assertEquals(List.of("count", "extract", "extract"), handler.operations());
        assertTrue(handler.calls.stream().allMatch(call -> call.side() == Direction.NORTH));
        assertTrue(handler.calls.stream().filter(call -> call.action() != null).allMatch(call -> call.action() == Action.EXECUTE));
        assertEquals(List.of(0L, 15L), handler.energy());
    }

    @Test
    void sidedAggregatesPropagateExactNullableSideAndAction() {
        TrackingSided handler = new FixedSideTrackingSided(Direction.NORTH, 40, 100);
        handler.energy[1] = 70;

        assertEquals(0, handler.insertEnergy(50, null, Action.SIMULATE));
        assertEquals(List.of("count", "get", "get", "insert", "insert"), handler.operations());
        assertTrue(handler.calls.stream().allMatch(call -> call.side() == null));
        assertTrue(handler.calls.stream().filter(call -> call.action() != null).allMatch(call -> call.action() == Action.SIMULATE));
        assertEquals(List.of(0L, 70L), handler.energy());

        handler.calls.clear();
        assertEquals(70, handler.extractEnergy(70, Direction.SOUTH, Action.EXECUTE));
        assertEquals(List.of("count", "extract", "extract"), handler.operations());
        assertTrue(handler.calls.stream().allMatch(call -> call.side() == Direction.SOUTH));
        assertEquals(Action.EXECUTE, handler.calls.get(1).action());
        assertEquals(List.of(0L, 0L), handler.energy());
    }

    @Test
    void defaultEnergySideIsNullAndUnsidedAggregateForwardsIt() {
        TrackingSided handler = new TrackingSided(100);

        assertNull(handler.getEnergySideFor());
        assertEquals(0, handler.insertEnergy(5, Action.SIMULATE));
        assertEquals(List.of("count", "insert"), handler.operations());
        assertTrue(handler.calls.stream().allMatch(call -> call.side() == null));
    }

    private record Call(String operation, int container, long amount, @Nullable Direction side, @Nullable Action action) {
    }

    private static class TrackingSided implements ISidedStrictEnergyHandler {
        private final long[] max;
        private final long[] energy;
        private final List<Call> calls = new ArrayList<>();

        private TrackingSided(long... max) {
            this.max = max;
            energy = new long[max.length];
        }

        private List<String> operations() {
            return calls.stream().map(Call::operation).toList();
        }

        private List<Long> energy() {
            List<Long> values = new ArrayList<>();
            for (long stored : energy) {
                values.add(stored);
            }
            return values;
        }

        @Override
        public int getEnergyContainerCount(@Nullable Direction side) {
            calls.add(new Call("count", -1, 0, side, null));
            return energy.length;
        }

        @Override
        public long getEnergy(int container, @Nullable Direction side) {
            calls.add(new Call("get", container, 0, side, null));
            return energy[container];
        }

        @Override
        public void setEnergy(int container, long amount, @Nullable Direction side) {
            calls.add(new Call("set", container, amount, side, null));
            energy[container] = Math.min(max[container], Math.max(0, amount));
        }

        @Override
        public long getMaxEnergy(int container, @Nullable Direction side) {
            calls.add(new Call("max", container, 0, side, null));
            return max[container];
        }

        @Override
        public long getNeededEnergy(int container, @Nullable Direction side) {
            calls.add(new Call("needed", container, 0, side, null));
            return max[container] - energy[container];
        }

        @Override
        public long insertEnergy(int container, long amount, @Nullable Direction side, Action action) {
            calls.add(new Call("insert", container, amount, side, action));
            long accepted = Math.min(amount, max[container] - energy[container]);
            if (action.execute()) {
                energy[container] += accepted;
            }
            return amount - accepted;
        }

        @Override
        public long extractEnergy(int container, long amount, @Nullable Direction side, Action action) {
            calls.add(new Call("extract", container, amount, side, action));
            long extracted = Math.min(amount, energy[container]);
            if (action.execute()) {
                energy[container] -= extracted;
            }
            return extracted;
        }
    }

    private static final class FixedSideTrackingSided extends TrackingSided {
        private final Direction side;

        private FixedSideTrackingSided(Direction side, long... max) {
            super(max);
            this.side = side;
        }

        @Override
        public Direction getEnergySideFor() {
            return side;
        }
    }
}
