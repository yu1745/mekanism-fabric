package mekanism.common.content.network.distribution;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import mekanism.api.Action;
import mekanism.api.energy.IStrictEnergyHandler;
import mekanism.common.util.EmitUtils;
import org.junit.jupiter.api.Test;

class EnergyAcceptorTargetTest {

    @Test
    void distributionPreservesSimulationExecutionOrderActionsAndTrueLongResults() {
        List<String> events = new ArrayList<>();
        RecordingHandler first = new RecordingHandler("first", 3_000_000_000L, 3_000_000_000L, events);
        RecordingHandler second = new RecordingHandler("second", 2_000_000_000L, 2_000_000_000L, events);
        EnergyAcceptorTarget target = new EnergyAcceptorTarget(List.of(first, second));

        assertEquals(4_000_000_000L, EmitUtils.sendToAcceptors(target, 4_000_000_000L, null));
        assertEquals(List.of(
              "first:4000000000:SIMULATE", "second:4000000000:SIMULATE",
              "second:2000000000:EXECUTE", "first:2000000000:EXECUTE"), events);
        assertEquals(2_000_000_000L, first.executed);
        assertEquals(2_000_000_000L, second.executed);
    }

    @Test
    void executionShortfallUsesActualRemainderInsteadOfSimulatedClaim() {
        List<String> events = new ArrayList<>();
        RecordingHandler handler = new RecordingHandler("changing", 100, 30, events);

        assertEquals(30, EmitUtils.sendToAcceptors(new EnergyAcceptorTarget(List.of(handler)), 100L, null));
        assertEquals(List.of("changing:100:SIMULATE", "changing:100:EXECUTE"), events);
        assertEquals(30, handler.executed);
    }

    private static final class RecordingHandler implements IStrictEnergyHandler {
        private final String name;
        private final long simulatedAcceptance;
        private final long executionAcceptance;
        private final List<String> events;
        private long executed;

        private RecordingHandler(String name, long simulatedAcceptance, long executionAcceptance, List<String> events) {
            this.name = name;
            this.simulatedAcceptance = simulatedAcceptance;
            this.executionAcceptance = executionAcceptance;
            this.events = events;
        }

        @Override
        public long insertEnergy(long amount, Action action) {
            events.add(name + ":" + amount + ":" + action);
            long accepted = Math.min(amount, action == Action.SIMULATE ? simulatedAcceptance : executionAcceptance);
            if (action == Action.EXECUTE) {
                executed += accepted;
            }
            return amount - accepted;
        }

        @Override public int getEnergyContainerCount() { return 0; }
        @Override public long getEnergy(int container) { return 0; }
        @Override public void setEnergy(int container, long energy) { }
        @Override public long getMaxEnergy(int container) { return 0; }
        @Override public long getNeededEnergy(int container) { return 0; }
        @Override public long insertEnergy(int container, long amount, Action action) { return amount; }
        @Override public long extractEnergy(int container, long amount, Action action) { return 0; }
    }
}
