package mekanism.common.lib;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LastEnergyTrackerTest {

    @Test
    void sameGameTimeAccumulatesWithoutPublishing() {
        LastEnergyTracker tracker = new LastEnergyTracker();
        tracker.received(4, 12);
        tracker.received(4, 7);
        assertEquals(0, tracker.getLastEnergyReceived());

        tracker.received(5, 1);
        assertEquals(19, tracker.getLastEnergyReceived());
    }

    @Test
    void everyDifferentTimestampPublishesIncludingSkippedAndDecreasingTimes() {
        LastEnergyTracker tracker = new LastEnergyTracker();
        tracker.received(8, 3);
        tracker.received(20, 5);
        assertEquals(3, tracker.getLastEnergyReceived());

        tracker.received(2, 7);
        assertEquals(5, tracker.getLastEnergyReceived());
    }

    @Test
    void initialTickZeroIsTheInitialCurrentBucketAndUsesRawLongArithmetic() {
        LastEnergyTracker tracker = new LastEnergyTracker();
        assertEquals(0, tracker.getLastEnergyReceived());

        tracker.received(0, (long) Integer.MAX_VALUE + 9);
        tracker.received(0, 0);
        tracker.received(0, -4);
        assertEquals(0, tracker.getLastEnergyReceived());

        tracker.received(1, -6);
        assertEquals((long) Integer.MAX_VALUE + 5, tracker.getLastEnergyReceived());
        tracker.received(2, 0);
        assertEquals(-6, tracker.getLastEnergyReceived());
    }

    @Test
    void setterChangesOnlyVisibleLastValueUntilTheNextTransition() {
        LastEnergyTracker tracker = new LastEnergyTracker();
        tracker.received(3, 10);
        tracker.setLastEnergyReceived(99);
        tracker.received(3, 2);
        assertEquals(99, tracker.getLastEnergyReceived());

        tracker.received(4, 1);
        assertEquals(12, tracker.getLastEnergyReceived());
    }
}
