package mekanism.api.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.common.capabilities.energy.BasicEnergyContainer;
import org.junit.jupiter.api.Test;

class IStrictEnergyHandlerTest {

    @Test
    void aggregateDefaultsPreserveIndexedOrderValuesAndAction() {
        TrackingStrict insert = handler(50, 100, 100);
        insert.containers.get(1).setEnergy(80);
        insert.events.clear();

        assertEquals(30, insert.insertEnergy(200, Action.SIMULATE));
        assertEquals(List.of(
              "count", "get:0", "get:1", "insert:1:200:SIMULATE:INTERNAL", "get:2",
              "insert:0:180:SIMULATE:INTERNAL", "insert:2:130:SIMULATE:INTERNAL"), insert.events);
        assertEquals(List.of(0L, 80L, 0L), insert.energy());

        TrackingStrict extract = handler(100, 100, 100);
        extract.containers.get(0).setEnergy(20);
        extract.containers.get(1).setEnergy(40);
        extract.containers.get(2).setEnergy(70);
        extract.events.clear();

        assertEquals(50, extract.extractEnergy(50, Action.EXECUTE));
        assertEquals(List.of(
              "count", "extract:0:50:EXECUTE:INTERNAL", "extract:1:30:EXECUTE:INTERNAL"), extract.events);
        assertEquals(List.of(0L, 10L, 70L), extract.energy());
    }

    @Test
    void aggregateDefaultsKeepOfficialNonPositiveAndEmptyBehavior() {
        TrackingStrict handler = handler(100);
        assertEquals(0, handler.insertEnergy(0, Action.EXECUTE));
        assertEquals(0, handler.insertEnergy(-5, Action.SIMULATE));
        assertEquals(0, handler.extractEnergy(0, Action.EXECUTE));
        assertEquals(0, handler.extractEnergy(-5, Action.SIMULATE));
        assertEquals(List.of(), handler.events);

        TrackingStrict empty = handler();
        assertEquals(42, empty.insertEnergy(42, Action.EXECUTE));
        assertEquals(0, empty.extractEnergy(42, Action.SIMULATE));
        assertEquals(List.of("count", "count"), empty.events);
    }

    private static TrackingStrict handler(long... capacities) {
        List<BasicEnergyContainer> containers = new ArrayList<>();
        for (long capacity : capacities) {
            containers.add(BasicEnergyContainer.create(capacity, null));
        }
        return new TrackingStrict(containers);
    }

    private static final class TrackingStrict implements IStrictEnergyHandler {
        private final List<BasicEnergyContainer> containers;
        private final List<String> events = new ArrayList<>();

        private TrackingStrict(List<BasicEnergyContainer> containers) {
            this.containers = containers;
        }

        private List<Long> energy() {
            return containers.stream().map(BasicEnergyContainer::getEnergy).toList();
        }

        @Override
        public int getEnergyContainerCount() {
            events.add("count");
            return containers.size();
        }

        @Override
        public long getEnergy(int container) {
            events.add("get:" + container);
            return containers.get(container).getEnergy();
        }

        @Override
        public void setEnergy(int container, long energy) {
            containers.get(container).setEnergy(energy);
        }

        @Override
        public long getMaxEnergy(int container) {
            return containers.get(container).getMaxEnergy();
        }

        @Override
        public long getNeededEnergy(int container) {
            return containers.get(container).getNeeded();
        }

        @Override
        public long insertEnergy(int container, long amount, Action action) {
            events.add("insert:" + container + ":" + amount + ":" + action + ":INTERNAL");
            return containers.get(container).insert(amount, action, AutomationType.INTERNAL);
        }

        @Override
        public long extractEnergy(int container, long amount, Action action) {
            events.add("extract:" + container + ":" + amount + ":" + action + ":INTERNAL");
            return containers.get(container).extract(amount, action, AutomationType.INTERNAL);
        }
    }
}
