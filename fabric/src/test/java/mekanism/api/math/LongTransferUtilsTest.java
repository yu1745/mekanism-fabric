package mekanism.api.math;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.api.container.InContainerGetterLong;
import mekanism.api.container.LongToLongContainerInteraction;
import mekanism.api.energy.IEnergyContainer;
import mekanism.common.capabilities.energy.BasicEnergyContainer;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

/**
 * Fabric-local semantic tests for the unchanged official {@link LongTransferUtils} closure.
 *
 * <p>The six public overload families are exercised through observable call/order/value events. The tests do not assert any behavior for
 * caller-contract violations: offers are nonnegative, the insert remainder is expected to stay in {@code [0, offer]}, the extract result in
 * {@code [0, request]}, and the explicit overloads receive a nonnegative {@code containerCount} that matches a fresh, consistent container list.
 * Undefined overflow and deliberately mismatched counts are never exercised.
 */
class LongTransferUtilsTest {

    @Test
    void insertShortCircuitsNonPositiveOffersBeforeAnyCountGetterOrInteraction() {
        List<String> events = new ArrayList<>();
        ToIntFunction<Direction> count = side -> {
            events.add("count:" + sideName(side));
            return 3;
        };
        InContainerGetterLong getter = (container, side) -> {
            events.add("getStored:" + container);
            return 5L;
        };
        LongToLongContainerInteraction interaction = (container, amount, side, action) -> {
            events.add("interact:" + container);
            return 0L;
        };

        assertEquals(0L, LongTransferUtils.insert(0L, Direction.NORTH, Action.EXECUTE, count, getter, interaction));
        assertEquals(0L, LongTransferUtils.insert(-12L, null, Action.SIMULATE, count, getter, interaction));
        assertEquals(List.of(), events);

        AtomicInteger supplierCalls = new AtomicInteger();
        Function<Direction, List<IEnergyContainer>> supplier = side -> {
            supplierCalls.incrementAndGet();
            return List.of();
        };
        assertEquals(0L, LongTransferUtils.insert(0L, Direction.DOWN, supplier, Action.EXECUTE, AutomationType.INTERNAL));
        assertEquals(0L, LongTransferUtils.insert(-7L, null, supplier, Action.SIMULATE, AutomationType.EXTERNAL));
        assertEquals(0, supplierCalls.get());

        List<String> containerEvents = new ArrayList<>();
        TrackingEnergyContainer first = new TrackingEnergyContainer("first", 100, containerEvents, null);
        TrackingEnergyContainer second = new TrackingEnergyContainer("second", 100, containerEvents, null);
        first.setEnergy(25);
        containerEvents.clear();
        List<IEnergyContainer> containers = List.of(first, second);
        assertEquals(0L, LongTransferUtils.insert(0L, Action.EXECUTE, AutomationType.INTERNAL, containers.size(), containers));
        assertEquals(0L, LongTransferUtils.insert(-9L, Action.SIMULATE, AutomationType.EXTERNAL, containers.size(), containers));
        assertEquals(List.of(), containerEvents);
        assertEquals(25L, first.getEnergy());
    }

    @Test
    void insertShortCircuitsZeroContainerCountsBeforeGetterOrInteraction() {
        List<String> events = new ArrayList<>();
        ToIntFunction<Direction> count = side -> {
            events.add("count:" + sideName(side));
            return 0;
        };
        InContainerGetterLong getter = (container, side) -> {
            events.add("getStored");
            return 0L;
        };
        LongToLongContainerInteraction interaction = (container, amount, side, action) -> {
            events.add("interact");
            return 0L;
        };

        assertEquals(64L, LongTransferUtils.insert(64L, Direction.UP, Action.EXECUTE, count, getter, interaction));
        assertEquals(List.of("count:up"), events);

        List<String> containerEvents = new ArrayList<>();
        TrackingEnergyContainer unused = new TrackingEnergyContainer("unused", 100, containerEvents, null);
        assertEquals(64L, LongTransferUtils.insert(64L, Action.EXECUTE, AutomationType.INTERNAL, 0, List.of()));
        assertEquals(List.of(), containerEvents);

        AtomicInteger supplierCalls = new AtomicInteger();
        assertEquals(64L, LongTransferUtils.insert(64L, Direction.DOWN, side -> {
            supplierCalls.incrementAndGet();
            return List.of();
        }, Action.EXECUTE, AutomationType.INTERNAL));
        assertEquals(1, supplierCalls.get());
        assertTrue(unused.inserts.isEmpty());
    }

    @Test
    void extractShortCircuitsNonPositiveAmountsBeforeAnyCountGetterOrInteraction() {
        List<String> events = new ArrayList<>();
        ToIntFunction<Direction> count = side -> {
            events.add("count:" + sideName(side));
            return 3;
        };
        LongToLongContainerInteraction interaction = (container, amount, side, action) -> {
            events.add("interact:" + container);
            return 5L;
        };

        assertEquals(0L, LongTransferUtils.extract(0L, Direction.NORTH, Action.EXECUTE, count, interaction));
        assertEquals(0L, LongTransferUtils.extract(-12L, null, Action.SIMULATE, count, interaction));
        assertEquals(List.of(), events);

        ToIntFunction<Direction> zeroCount = side -> {
            events.add("zeroCount:" + sideName(side));
            return 0;
        };
        assertEquals(0L, LongTransferUtils.extract(64L, Direction.DOWN, Action.EXECUTE, zeroCount, interaction));
        assertEquals(List.of("zeroCount:down"), events);

        List<String> containerEvents = new ArrayList<>();
        TrackingEnergyContainer first = new TrackingEnergyContainer("first", 100, containerEvents, null);
        first.setEnergy(40);
        containerEvents.clear();
        List<IEnergyContainer> containers = List.of(first);
        assertEquals(0L, LongTransferUtils.extract(0L, Action.EXECUTE, AutomationType.INTERNAL, containers.size(), containers));
        assertEquals(0L, LongTransferUtils.extract(-5L, Action.SIMULATE, AutomationType.EXTERNAL, containers.size(), containers));
        assertEquals(0L, LongTransferUtils.extract(50L, Action.EXECUTE, AutomationType.INTERNAL, 0, List.of()));
        assertEquals(List.of(), containerEvents);
        assertEquals(40L, first.getEnergy());
    }

    @Test
    void extractEnergySupplierShortCircuitsExactlyZeroButForwardsNegativeAmounts() {
        List<String> supplierSides = new ArrayList<>();
        List<String> containerEvents = new ArrayList<>();
        TrackingEnergyContainer target = new TrackingEnergyContainer("target", 100, containerEvents, null);
        target.setEnergy(40);
        containerEvents.clear();
        List<IEnergyContainer> containers = List.of(target);
        Function<Direction, List<IEnergyContainer>> supplier = side -> {
            supplierSides.add(sideName(side));
            return containers;
        };

        assertEquals(0L, LongTransferUtils.extract(0L, Direction.EAST, supplier, Action.EXECUTE, AutomationType.INTERNAL));
        assertEquals(List.of(), supplierSides);

        assertEquals(0L, LongTransferUtils.extract(-12L, Direction.EAST, supplier, Action.EXECUTE, AutomationType.INTERNAL));
        assertEquals(List.of("east"), supplierSides);
        assertEquals(List.of(), containerEvents);
        assertEquals(40L, target.getEnergy());

        assertEquals(12L, LongTransferUtils.extract(12L, Direction.EAST, supplier, Action.EXECUTE, AutomationType.INTERNAL));
        assertEquals(List.of("east", "east"), supplierSides);
        assertEquals(List.of("extract target 12 EXECUTE INTERNAL"), containerEvents);
        assertEquals(28L, target.getEnergy());
    }

    @Test
    void singleContainerForwardingPreservesIndexSideActionAndAutomationType() {
        List<CallbackCall> calls = new ArrayList<>();
        AtomicInteger getterCalls = new AtomicInteger();
        LongToLongContainerInteraction interaction = (container, amount, side, action) -> {
            calls.add(new CallbackCall(container, amount, side, action));
            return 7L;
        };
        ToIntFunction<Direction> one = side -> 1;
        InContainerGetterLong getter = (container, side) -> {
            getterCalls.incrementAndGet();
            return 1L;
        };

        assertEquals(7L, LongTransferUtils.insert(42L, Direction.NORTH, Action.SIMULATE, one, getter, interaction));
        assertEquals(7L, LongTransferUtils.insert(42L, null, Action.EXECUTE, one, getter, interaction));
        assertEquals(0, getterCalls.get());
        assertEquals(List.of(
              new CallbackCall(0, 42L, Direction.NORTH, Action.SIMULATE),
              new CallbackCall(0, 42L, null, Action.EXECUTE)), calls);

        calls.clear();
        assertEquals(7L, LongTransferUtils.extract(42L, Direction.SOUTH, Action.EXECUTE, one, interaction));
        assertEquals(7L, LongTransferUtils.extract(42L, null, Action.SIMULATE, one, interaction));
        assertEquals(List.of(
              new CallbackCall(0, 42L, Direction.SOUTH, Action.EXECUTE),
              new CallbackCall(0, 42L, null, Action.SIMULATE)), calls);
        assertEquals(0, getterCalls.get());
    }

    @Test
    void supplierOverloadsForwardTheNullableSideAndSingleContainerArguments() {
        List<String> sides = new ArrayList<>();
        List<String> containerEvents = new ArrayList<>();
        TrackingEnergyContainer only = new TrackingEnergyContainer("only", 1_000, containerEvents, null);
        List<IEnergyContainer> containers = List.of(only);
        Function<Direction, List<IEnergyContainer>> supplier = side -> {
            sides.add(sideName(side));
            return containers;
        };

        assertEquals(0L, LongTransferUtils.insert(250L, Direction.NORTH, supplier, Action.EXECUTE, AutomationType.INTERNAL));
        assertEquals(250L, only.getEnergy());
        assertEquals(0L, LongTransferUtils.insert(250L, null, supplier, Action.EXECUTE, AutomationType.EXTERNAL));
        assertEquals(500L, only.getEnergy());
        assertEquals(List.of("north", "null"), sides);

        sides.clear();
        assertEquals(300L, LongTransferUtils.extract(300L, Direction.SOUTH, supplier, Action.EXECUTE, AutomationType.EXTERNAL));
        assertEquals(200L, only.getEnergy());
        assertEquals(200L, LongTransferUtils.extract(200L, null, supplier, Action.SIMULATE, AutomationType.INTERNAL));
        assertEquals(200L, only.getEnergy());
        assertEquals(List.of("south", "null"), sides);

        assertEquals(List.of(
              new ContainerCall(250L, Action.EXECUTE, AutomationType.INTERNAL),
              new ContainerCall(250L, Action.EXECUTE, AutomationType.EXTERNAL)), only.inserts);
        assertEquals(List.of(
              new ContainerCall(300L, Action.EXECUTE, AutomationType.EXTERNAL),
              new ContainerCall(200L, Action.SIMULATE, AutomationType.INTERNAL)), only.extracts);
        assertEquals(List.of(
              "insert only 250 EXECUTE INTERNAL",
              "insert only 250 EXECUTE EXTERNAL",
              "extract only 300 EXECUTE EXTERNAL",
              "extract only 200 SIMULATE INTERNAL"), containerEvents);
    }

    @Test
    void multiContainerInsertionVisitsNonEmptyContainersBeforeDeferredEmptiesInSourceOrder() {
        List<String> events = new ArrayList<>();
        long[] stored = {0L, 5L, 0L, 9L};
        long[] remainders = {10L, 30L, 0L, 12L};
        ToIntFunction<Direction> count = side -> 4;
        InContainerGetterLong getter = (container, side) -> {
            events.add("getStored(" + container + ")");
            return stored[container];
        };
        LongToLongContainerInteraction interaction = (container, amount, side, action) -> {
            events.add("interact(" + container + "," + amount + ")");
            return remainders[container];
        };

        assertEquals(0L, LongTransferUtils.insert(50L, Direction.NORTH, Action.EXECUTE, count, getter, interaction));
        assertEquals(List.of(
              "getStored(0)", "getStored(1)", "interact(1,50)", "getStored(2)", "getStored(3)", "interact(3,30)",
              "interact(0,12)", "interact(2,10)"), events);
    }

    @Test
    void multiContainerInsertionStopsOnceRemainderIsNonPositive() {
        for (long stoppingRemainder : new long[]{0L, -5L}) {
            List<String> events = new ArrayList<>();
            long[] stored = {1L, 1L, 1L};
            ToIntFunction<Direction> count = side -> 3;
            InContainerGetterLong getter = (container, side) -> {
                events.add("getStored(" + container + ")");
                return stored[container];
            };
            LongToLongContainerInteraction interaction = (container, amount, side, action) -> {
                events.add("interact(" + container + "," + amount + ")");
                return stoppingRemainder;
            };

            assertEquals(0L, LongTransferUtils.insert(100L, Direction.DOWN, Action.SIMULATE, count, getter, interaction),
                  "remainder " + stoppingRemainder);
            assertEquals(List.of("getStored(0)", "interact(0,100)"), events, "remainder " + stoppingRemainder);
        }
    }

    @Test
    void multiContainerExtractionVisitsSourceOrderWithShrinkingRequestAndStopsWhenSatisfied() {
        List<String> events = new ArrayList<>();
        long[] drains = {0L, 5L, 7L, 99L};
        ToIntFunction<Direction> count = side -> 4;
        LongToLongContainerInteraction interaction = (container, amount, side, action) -> {
            events.add("interact(" + container + "," + amount + ")");
            return drains[container];
        };

        assertEquals(12L, LongTransferUtils.extract(12L, Direction.WEST, Action.EXECUTE, count, interaction));
        assertEquals(List.of("interact(0,12)", "interact(1,12)", "interact(2,7)"), events);

        events.clear();
        long[] ignoredDrains = {0L, -4L, 0L};
        ToIntFunction<Direction> three = side -> 3;
        LongToLongContainerInteraction idle = (container, amount, side, action) -> {
            events.add("interact(" + container + "," + amount + ")");
            return ignoredDrains[container];
        };
        assertEquals(0L, LongTransferUtils.extract(64L, null, Action.SIMULATE, three, idle));
        assertEquals(List.of("interact(0,64)", "interact(1,64)", "interact(2,64)"), events);
    }

    @Test
    void realContainerInsertionOrdersNonEmptyFirstAndSeparatesSimulateFromExecute() {
        long capacity = 100L;
        AtomicInteger notifications = new AtomicInteger();
        List<String> simulateEvents = new ArrayList<>();
        TrackingEnergyContainer simulateEmpty = new TrackingEnergyContainer("empty", capacity, simulateEvents, null);
        TrackingEnergyContainer simulatePartial = new TrackingEnergyContainer("partial", capacity, simulateEvents, null);
        simulatePartial.setEnergy(30L);
        simulateEvents.clear();
        List<IEnergyContainer> simulateContainers = List.of(simulateEmpty, simulatePartial);

        assertEquals(0L, LongTransferUtils.insert(85L, Direction.NORTH, side -> simulateContainers, Action.SIMULATE, AutomationType.INTERNAL));
        assertEquals(List.of(
              "insert partial 85 SIMULATE INTERNAL",
              "insert empty 15 SIMULATE INTERNAL"), simulateEvents);
        assertEquals(0L, simulateEmpty.getEnergy());
        assertEquals(30L, simulatePartial.getEnergy());
        assertEquals(0, notifications.get());

        List<String> executeEvents = new ArrayList<>();
        TrackingEnergyContainer executeEmpty = new TrackingEnergyContainer("empty", capacity, executeEvents, notifications::incrementAndGet);
        TrackingEnergyContainer executePartial = new TrackingEnergyContainer("partial", capacity, executeEvents, notifications::incrementAndGet);
        executePartial.setEnergy(30L);
        executeEvents.clear();
        notifications.set(0);
        List<IEnergyContainer> executeContainers = List.of(executeEmpty, executePartial);

        assertEquals(0L, LongTransferUtils.insert(85L, Direction.NORTH, side -> executeContainers, Action.EXECUTE, AutomationType.INTERNAL));
        assertEquals(List.of(
              "insert partial 85 EXECUTE INTERNAL",
              "insert empty 15 EXECUTE INTERNAL"), executeEvents);
        assertEquals(15L, executeEmpty.getEnergy());
        assertEquals(100L, executePartial.getEnergy());
        assertEquals(2, notifications.get());
    }

    @Test
    void realContainerExtractionAccumulatesTrueLongAmountsAndSeparatesSimulateFromExecute() {
        long capacity = (long) Integer.MAX_VALUE + 30_000L;
        long trueLongStored = (long) Integer.MAX_VALUE + 5_000L;
        long request = (long) Integer.MAX_VALUE + 2_000L;

        AtomicInteger simulateNotifications = new AtomicInteger();
        List<String> simulateEvents = new ArrayList<>();
        TrackingEnergyContainer simulateFirst = new TrackingEnergyContainer("first", capacity, simulateEvents, null);
        TrackingEnergyContainer simulateSecond = new TrackingEnergyContainer("second", capacity, simulateEvents, simulateNotifications::incrementAndGet);
        TrackingEnergyContainer simulateThird = new TrackingEnergyContainer("third", capacity, simulateEvents, null);
        simulateFirst.setEnergy(100L);
        simulateSecond.setEnergy(trueLongStored);
        simulateThird.setEnergy(500L);
        simulateEvents.clear();
        simulateNotifications.set(0);
        List<IEnergyContainer> simulateContainers = List.of(simulateFirst, simulateSecond, simulateThird);

        assertEquals(request, LongTransferUtils.extract(request, null, side -> simulateContainers, Action.SIMULATE, AutomationType.EXTERNAL));
        assertEquals(List.of(
              "extract first " + request + " SIMULATE EXTERNAL",
              "extract second " + (request - 100L) + " SIMULATE EXTERNAL"), simulateEvents);
        assertEquals(100L, simulateFirst.getEnergy());
        assertEquals(trueLongStored, simulateSecond.getEnergy());
        assertEquals(500L, simulateThird.getEnergy());
        assertEquals(0, simulateNotifications.get());

        AtomicInteger executeNotifications = new AtomicInteger();
        List<String> executeEvents = new ArrayList<>();
        TrackingEnergyContainer executeFirst = new TrackingEnergyContainer("first", capacity, executeEvents, null);
        TrackingEnergyContainer executeSecond = new TrackingEnergyContainer("second", capacity, executeEvents, executeNotifications::incrementAndGet);
        TrackingEnergyContainer executeThird = new TrackingEnergyContainer("third", capacity, executeEvents, null);
        executeFirst.setEnergy(100L);
        executeSecond.setEnergy(trueLongStored);
        executeThird.setEnergy(500L);
        executeEvents.clear();
        executeNotifications.set(0);
        List<IEnergyContainer> executeContainers = List.of(executeFirst, executeSecond, executeThird);

        long extracted = LongTransferUtils.extract(request, null, side -> executeContainers, Action.EXECUTE, AutomationType.EXTERNAL);
        assertEquals(request, extracted);
        assertTrue(extracted > Integer.MAX_VALUE);
        assertEquals(List.of(
              "extract first " + request + " EXECUTE EXTERNAL",
              "extract second " + (request - 100L) + " EXECUTE EXTERNAL"), executeEvents);
        assertEquals(0L, executeFirst.getEnergy());
        assertEquals(trueLongStored - (request - 100L), executeSecond.getEnergy());
        assertEquals(500L, executeThird.getEnergy());
        assertEquals(1, executeNotifications.get());
    }

    @Test
    void realContainerInsertionCarriesTrueLongAmountsAboveIntegerMax() {
        long capacity = (long) Integer.MAX_VALUE + 10_000L;
        long offer = capacity + 20_000L;
        List<String> events = new ArrayList<>();
        TrackingEnergyContainer target = new TrackingEnergyContainer("target", capacity, events, null);
        List<IEnergyContainer> containers = List.of(target);

        assertEquals(20_000L, LongTransferUtils.insert(offer, null, side -> containers, Action.EXECUTE, AutomationType.INTERNAL));
        assertEquals(capacity, target.getEnergy());
        assertTrue(target.getEnergy() > Integer.MAX_VALUE);
        assertEquals(List.of("insert target " + offer + " EXECUTE INTERNAL"), events);

        assertEquals(20_000L, LongTransferUtils.extract(20_000L, null, side -> containers, Action.SIMULATE, AutomationType.EXTERNAL));
        assertEquals(capacity, target.getEnergy());
    }

    @Test
    void sequentialTransferStaysWithinDocumentedCallerPreconditions() {
        // Caller contract: offers/requests are nonnegative; the insert remainder stays in [0, offer]; the extract
        // result stays in [0, request]; containerCount is nonnegative and matches a fresh, consistent list. The
        // unchanged utility adds no policy for overflow or mismatched counts, so those are never exercised here.
        long[] amounts = {0L, 1L, 150L, (long) Integer.MAX_VALUE + 1L, Long.MAX_VALUE / 2};
        for (long offer : amounts) {
            BasicEnergyContainer first = BasicEnergyContainer.create(200L, null);
            BasicEnergyContainer second = BasicEnergyContainer.create(200L, null);
            first.setEnergy(50L);
            second.setEnergy(120L);
            List<IEnergyContainer> containers = List.of(first, second);

            long remainder = LongTransferUtils.insert(offer, null, side -> containers, Action.EXECUTE, AutomationType.INTERNAL);
            assertTrue(remainder >= 0L, "insert remainder must be nonnegative for offer " + offer);
            assertTrue(remainder <= offer, "insert remainder must not exceed offer " + offer);

            long simulated = LongTransferUtils.insert(offer, Action.SIMULATE, AutomationType.INTERNAL, containers.size(), containers);
            assertTrue(simulated >= 0L && simulated <= offer, "simulated insert remainder out of range for offer " + offer);
        }
        for (long request : amounts) {
            BasicEnergyContainer first = BasicEnergyContainer.create(Long.MAX_VALUE, null);
            BasicEnergyContainer second = BasicEnergyContainer.create(Long.MAX_VALUE, null);
            first.setEnergy(50L);
            second.setEnergy(120L);
            List<IEnergyContainer> containers = List.of(first, second);

            long extracted = LongTransferUtils.extract(request, null, side -> containers, Action.EXECUTE, AutomationType.EXTERNAL);
            assertTrue(extracted >= 0L, "extract result must be nonnegative for request " + request);
            assertTrue(extracted <= request, "extract result must not exceed request " + request);

            long simulated = LongTransferUtils.extract(request, Action.SIMULATE, AutomationType.EXTERNAL, containers.size(), containers);
            assertTrue(simulated >= 0L && simulated <= request, "simulated extract result out of range for request " + request);
        }
    }

    private static String sideName(Direction side) {
        return side == null ? "null" : side.getName();
    }

    private record CallbackCall(int container, long amount, Direction side, Action action) {
    }

    private record ContainerCall(long amount, Action action, AutomationType automationType) {
    }

    private static final class TrackingEnergyContainer extends BasicEnergyContainer {

        private final String name;
        private final List<String> events;
        private final List<ContainerCall> inserts = new ArrayList<>();
        private final List<ContainerCall> extracts = new ArrayList<>();

        private TrackingEnergyContainer(String name, long capacity, List<String> events, IContentsListener listener) {
            super(capacity, automation -> true, automation -> true, listener);
            this.name = name;
            this.events = events;
        }

        @Override
        public long insert(long amount, Action action, AutomationType automationType) {
            inserts.add(new ContainerCall(amount, action, automationType));
            events.add("insert " + name + " " + amount + " " + action + " " + automationType);
            return super.insert(amount, action, automationType);
        }

        @Override
        public long extract(long amount, Action action, AutomationType automationType) {
            extracts.add(new ContainerCall(amount, action, automationType));
            events.add("extract " + name + " " + amount + " " + action + " " + automationType);
            return super.extract(amount, action, automationType);
        }
    }
}
