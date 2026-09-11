package mekanism.common.lib.distribution;

import java.util.ArrayList;
import java.util.List;
import mekanism.common.util.EmitUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fabric-slice boundary coverage beyond the unchanged official oracle.
 *
 * <p>These tests intentionally use a true-long handler instead of the official
 * test {@code LongTarget}, whose offers are clamped to {@code int}.</p>
 */
class DistributionBoundaryTest {

    @Test
    void nullAndEmptyTargetsReturnZeroForBothOverloads() {
        assertEquals(0, EmitUtils.sendToAcceptors((TrueLongTarget) null, 1, null));
        assertEquals(0L, EmitUtils.sendToAcceptors((TrueLongTarget) null, 1L, null));

        TrueLongTarget empty = new TrueLongTarget();
        assertEquals(0, EmitUtils.sendToAcceptors(empty, 1, null));
        assertEquals(0L, EmitUtils.sendToAcceptors(empty, 1L, null));
    }

    @Test
    void zeroOffersDoNotMutateHandlers() {
        TrueLongHandler handler = new TrueLongHandler(10);
        TrueLongTarget target = target(handler);

        assertEquals(0, EmitUtils.sendToAcceptors(target, 0, null));
        assertEquals(0L, EmitUtils.sendToAcceptors(target, 0L, null));
        assertEquals(0, handler.accepted());
        assertEquals(0, handler.acceptCalls());
    }

    @Test
    void remainderFollowsDeterministicInsertionOrder() {
        TrueLongTarget target = target(
              new TrueLongHandler(10), new TrueLongHandler(10),
              new TrueLongHandler(10), new TrueLongHandler(10));

        long offered = 10;
        long sent = EmitUtils.sendToAcceptors(target, offered, null);

        assertConserved(offered, sent, target);
        assertEquals(List.of(3L, 3L, 2L, 2L), target.acceptedAmounts());
    }

    @Test
    void integerMaximumIsConserved() {
        int offered = Integer.MAX_VALUE;
        long firstCapacity = offered / 3L;
        TrueLongTarget target = target(
              new TrueLongHandler(firstCapacity),
              new TrueLongHandler(offered / 2L),
              new TrueLongHandler(offered - firstCapacity - offered / 2L));

        long sent = EmitUtils.sendToAcceptors(target, offered, null);

        assertConserved(offered, sent, target);
    }

    @Test
    void trueLongPathConservesAboveIntegerMaximumAndAtLongMaximum() {
        long aboveIntegerMaximum = (long) Integer.MAX_VALUE + 123_456_789L;
        long firstCapacity = aboveIntegerMaximum / 3;
        TrueLongTarget aboveIntTarget = target(
              new TrueLongHandler(firstCapacity),
              new TrueLongHandler(aboveIntegerMaximum - firstCapacity));
        long aboveIntSent = EmitUtils.sendToAcceptors(aboveIntTarget, aboveIntegerMaximum, null);
        assertConserved(aboveIntegerMaximum, aboveIntSent, aboveIntTarget);

        long half = Long.MAX_VALUE / 2;
        TrueLongTarget longMaximumTarget = target(
              new TrueLongHandler(half + 1),
              new TrueLongHandler(half));
        long maximumSent = EmitUtils.sendToAcceptors(longMaximumTarget, Long.MAX_VALUE, null);
        assertConserved(Long.MAX_VALUE, maximumSent, longMaximumTarget);
    }

    private static TrueLongTarget target(TrueLongHandler... handlers) {
        TrueLongTarget target = new TrueLongTarget();
        for (TrueLongHandler handler : handlers) {
            target.addHandler(handler);
        }
        return target;
    }

    private static void assertConserved(long offered, long sent, TrueLongTarget target) {
        long accepted = 0;
        long capacity = 0;
        for (TrueLongHandler handler : target.handlersInOrder()) {
            assertTrue(handler.accepted() >= 0);
            assertTrue(handler.accepted() <= handler.capacity());
            accepted = Math.addExact(accepted, handler.accepted());
            capacity = Math.addExact(capacity, handler.capacity());
        }
        assertEquals(accepted, sent);
        assertTrue(sent >= 0);
        assertTrue(sent <= offered);
        assertTrue(sent <= capacity);
    }

    private static final class TrueLongTarget extends Target<TrueLongHandler, Void> {

        @Override
        protected void acceptAmount(TrueLongHandler handler, SplitInfo splitInfo, Void resource, long amount) {
            splitInfo.send(handler.accept(amount));
        }

        @Override
        protected long simulate(TrueLongHandler handler, Void resource, long amount) {
            return handler.simulate(amount);
        }

        private List<TrueLongHandler> handlersInOrder() {
            return List.copyOf(handlers);
        }

        private List<Long> acceptedAmounts() {
            List<Long> amounts = new ArrayList<>(handlers.size());
            for (TrueLongHandler handler : handlers) {
                amounts.add(handler.accepted());
            }
            return amounts;
        }
    }

    private static final class TrueLongHandler {

        private final long capacity;
        private long accepted;
        private int acceptCalls;

        private TrueLongHandler(long capacity) {
            this.capacity = capacity;
        }

        private long simulate(long offered) {
            return Math.min(offered, capacity - accepted);
        }

        private long accept(long offered) {
            acceptCalls++;
            long amount = simulate(offered);
            accepted = Math.addExact(accepted, amount);
            return amount;
        }

        private long capacity() {
            return capacity;
        }

        private long accepted() {
            return accepted;
        }

        private int acceptCalls() {
            return acceptCalls;
        }
    }
}
