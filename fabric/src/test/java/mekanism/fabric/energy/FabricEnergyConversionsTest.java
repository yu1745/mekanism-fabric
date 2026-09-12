package mekanism.fabric.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import mekanism.api.energy.IEnergyConversion;
import org.junit.jupiter.api.Test;

class FabricEnergyConversionsTest {

    @Test
    void validationAndFixedPointsRejectUnusableAndUnreportablePartialUnits() {
        assertFalse(FabricEnergyConversions.isUsable(conversion(false, 1)));
        assertFalse(FabricEnergyConversions.isUsable(conversion(true, Double.NaN)));
        assertFalse(FabricEnergyConversions.isUsable(conversion(true, Double.POSITIVE_INFINITY)));
        assertFalse(FabricEnergyConversions.isUsable(conversion(true, Double.NEGATIVE_INFINITY)));
        assertFalse(FabricEnergyConversions.isUsable(conversion(true, 0)));
        assertFalse(FabricEnergyConversions.isUsable(conversion(true, -1)));

        IEnergyConversion twoPointFive = conversion(true, 2.5);
        assertEquals(0, FabricEnergyConversions.otherForJoules(twoPointFive, 4));
        assertEquals(2, FabricEnergyConversions.otherForJoules(twoPointFive, 5));
        assertEquals(2, FabricEnergyConversions.otherForJoules(twoPointFive, 7));
        assertEquals(0, FabricEnergyConversions.joulesForOther(twoPointFive, 1));
        assertEquals(5, FabricEnergyConversions.joulesForOther(twoPointFive, 2));
        assertEquals(5, FabricEnergyConversions.joulesForOther(twoPointFive, 3));
        assertTrue(FabricEnergyConversions.isExactPair(twoPointFive, 2, 5));
        assertFalse(FabricEnergyConversions.isExactPair(twoPointFive, 1, 2));

        IEnergyConversion pointFour = conversion(true, 0.4);
        assertEquals(0, FabricEnergyConversions.otherForJoules(pointFour, 1));
        assertEquals(5, FabricEnergyConversions.otherForJoules(pointFour, 2));
        assertEquals(5, FabricEnergyConversions.otherForJoules(pointFour, 3));
        assertEquals(0, FabricEnergyConversions.joulesForOther(pointFour, 4));
        assertEquals(2, FabricEnergyConversions.joulesForOther(pointFour, 5));
        assertEquals(2, FabricEnergyConversions.joulesForOther(pointFour, 9));
        assertEquals(4, FabricEnergyConversions.joulesForOther(pointFour, 10));
        assertTrue(FabricEnergyConversions.isExactPair(pointFour, 5, 2));

        IEnergyConversion identity = conversion(true, 1.0D);
        long[] identityBounds = {
              1, 3, (1L << 53) - 1, 1L << 53, (1L << 53) + 1, (1L << 53) + 3,
              (1L << 59) - 1, (1L << 59) + 1, (1L << 60) - 1, (1L << 60) + 1,
              (1L << 61) - 1, (1L << 61) + 1, (1L << 62) - 1, (1L << 62) + 1,
              Long.MAX_VALUE - 2, Long.MAX_VALUE - 1, Long.MAX_VALUE
        };
        for (long bound : identityBounds) {
            assertEquals(bound, FabricEnergyConversions.fromOther(identity, bound));
            assertEquals(bound, FabricEnergyConversions.toOther(identity, bound));
            assertEquals(bound, FabricEnergyConversions.otherForJoules(identity, bound));
            assertEquals(bound, FabricEnergyConversions.joulesForOther(identity, bound));
            assertTrue(FabricEnergyConversions.isExactPair(identity, bound, bound));
        }

        IEnergyConversion pointSeven = conversion(true, 0.7);
        long[] largeBounds = {
              1, 2, 4, 5, 7, 10, 11, 31, 63, 127, 255, 511, 1_023, 4_096,
              (1L << 20) - 1, 1L << 20, (1L << 31) + 1, (1L << 53) - 1,
              (1L << 53) + 1, (1L << 59) + 3, (1L << 60) + 5, (1L << 61) + 7,
              (1L << 62) + 9, Long.MAX_VALUE
        };
        assertMonotoneAndBounded(twoPointFive, largeBounds);
        assertMonotoneAndBounded(pointFour, largeBounds);
        assertMonotoneAndBounded(pointSeven, largeBounds);
        long largeUtilizationBound = (1L << 62) + 9;
        assertAtLeastNinetyNinePercentOfProjectedBounds(twoPointFive, largeUtilizationBound);
        assertAtLeastNinetyNinePercentOfProjectedBounds(pointFour, largeUtilizationBound);
        assertAtLeastNinetyNinePercentOfProjectedBounds(pointSeven, largeUtilizationBound);

        // This canonical decimal rate has a denominator far above the bound. Its 682nd-denominator continued-
        // fraction convergent is nevertheless an official-double fixed pair and must not be missed by a capped
        // decrement/projection search.
        IEnergyConversion reviewerNonDecimal = conversion(true, 1.4127416996526392E10);
        long reviewerBound = 27_251_905_869_490L;
        long reviewerOther = FabricEnergyConversions.otherForJoules(reviewerNonDecimal, reviewerBound);
        long reviewerJoules = FabricEnergyConversions.fromOther(reviewerNonDecimal, reviewerOther);
        assertTrue(reviewerOther >= 682, "must retain the known 682-unit fixed pair or a larger verified pair");
        assertTrue(reviewerJoules >= 9_634_898_391_631L);
        assertTrue(reviewerJoules <= reviewerBound);
        assertTrue(FabricEnergyConversions.isExactPair(reviewerNonDecimal, reviewerOther, reviewerJoules));
        assertMonotoneAndBounded(reviewerNonDecimal, new long[]{
              1, 14_127_416_996L, 268_420_922_934L, 9_634_898_391_630L,
              9_634_898_391_631L, reviewerBound, 1L << 60, Long.MAX_VALUE
        });

        // None of this decimal rate's bare convergent denominators (1, 2, 13, 15, 28, 43, 200) verifies, but the
        // fixed mesh/multiplier family must still discover exact pairs rather than returning zero for every bound.
        IEnergyConversion missedByBareConvergents = conversion(true, 372_965_497_689.465);
        long firstMissedJoules = 90_630_615_938_540L;
        assertEquals(0, FabricEnergyConversions.otherForJoules(
              missedByBareConvergents, firstMissedJoules - 1));
        assertEquals(243, FabricEnergyConversions.otherForJoules(
              missedByBareConvergents, firstMissedJoules));
        assertEquals(firstMissedJoules, FabricEnergyConversions.joulesForOther(missedByBareConvergents, 243));
        assertEquals(600, FabricEnergyConversions.otherForJoules(
              missedByBareConvergents, 223_779_298_613_679L));
        assertEquals(447_558_597_227_358L,
              FabricEnergyConversions.joulesForOther(missedByBareConvergents, 1_200));
        IEnergyConversion ordinaryMissedConvergents = conversion(true, 0.07);
        assertEquals(4_100, FabricEnergyConversions.otherForJoules(ordinaryMissedConvergents, 287));
        assertEquals(287, FabricEnergyConversions.joulesForOther(ordinaryMissedConvergents, 4_100));
        assertTrue(FabricEnergyConversions.isExactPair(ordinaryMissedConvergents, 4_100, 287));
        assertMonotoneAndBounded(missedByBareConvergents, new long[]{
              1, 200, 242, 243, 599, 600, 1_199, 1_200, 1L << 20, 1L << 40, Long.MAX_VALUE
        });

        // These rates exposed permanent high-bound collapse when one verified seed was represented only by
        // power-of-two chunks. Both directions must now retain at least 99% of their official projection, and the
        // same fixed family must stay monotone over independently increasing Joule and external-unit bounds.
        IEnergyConversion nonPowerOfTwo = conversion(true, 10.906989658246147);
        IEnergyConversion threeDecimalPlaces = conversion(true, 575.403);
        assertAtLeastNinetyNinePercentOfProjectedBounds(nonPowerOfTwo, Long.MAX_VALUE);
        assertAtLeastNinetyNinePercentOfProjectedBounds(threeDecimalPlaces, Long.MAX_VALUE);
        assertMonotoneAndBounded(nonPowerOfTwo, largeBounds);
        assertMonotoneAndBounded(threeDecimalPlaces, largeBounds);

        // Dense deterministic bounds catch regressions in monotonic selection between adjacent values without
        // adding another JUnit method.
        for (IEnergyConversion conversion : new IEnergyConversion[]{twoPointFive, pointFour, pointSeven}) {
            long previousOther = 0;
            long previousJoules = 0;
            for (long bound = 1; bound <= 1_024; bound++) {
                long other = FabricEnergyConversions.otherForJoules(conversion, bound);
                long joules = FabricEnergyConversions.joulesForOther(conversion, bound);
                assertTrue(other >= previousOther, "other-domain result decreased at bound " + bound);
                assertTrue(joules >= previousJoules, "joule-domain result decreased at bound " + bound);
                assertForwardBoundedPair(conversion, bound, other);
                assertReverseBoundedPair(conversion, bound, joules);
                previousOther = other;
                previousJoules = joules;
            }
        }

        // A cold identity/rate pair may enumerate the complete family once. Every later pair of real searches is a
        // cache lookup plus projections and final verification: 11 getConversion, three convertFrom, and four
        // convertTo calls here, with no BigDecimal/Euclid/family rebuild. Exact rate-bit changes must rebuild, and a
        // different identity advertising the same rate must build and verify its own behavior-sensitive snapshot.
        boolean[] mutableEnabled = {true};
        double[] mutableRate = {2.5D};
        int[] rateReads = {0};
        int[] fromCalls = {0};
        int[] toCalls = {0};
        IEnergyConversion mutable = new IEnergyConversion() {
            @Override
            public boolean isEnabled() {
                return mutableEnabled[0];
            }

            @Override
            public double getConversion() {
                rateReads[0]++;
                return mutableRate[0];
            }

            @Override
            public long convertFrom(long energy) {
                fromCalls[0]++;
                return IEnergyConversion.super.convertFrom(energy);
            }

            @Override
            public long convertTo(long joules) {
                toCalls[0]++;
                return IEnergyConversion.super.convertTo(joules);
            }
        };
        assertEquals(2, FabricEnergyConversions.otherForJoules(mutable, 5));
        assertTrue(fromCalls[0] > 20_000, "the first identity/rate lookup must build its verified snapshot");
        assertTrue(toCalls[0] > 20_000, "the cold build must verify candidates in both directions");

        rateReads[0] = 0;
        fromCalls[0] = 0;
        toCalls[0] = 0;
        for (int repetition = 0; repetition < 16; repetition++) {
            assertEquals(2, FabricEnergyConversions.otherForJoules(mutable, 5));
            assertEquals(5, FabricEnergyConversions.joulesForOther(mutable, 2));
        }
        assertEquals(176, rateReads[0], "warm paired searches have eleven exact rate reads each");
        assertEquals(48, fromCalls[0], "warm paired searches have three forward conversions each");
        assertEquals(64, toCalls[0], "warm paired searches have four reverse conversions each");

        mutableEnabled[0] = false;
        rateReads[0] = 0;
        fromCalls[0] = 0;
        toCalls[0] = 0;
        assertEquals(0, FabricEnergyConversions.otherForJoules(mutable, 5));
        assertEquals(0, FabricEnergyConversions.joulesForOther(mutable, 2));
        assertEquals(0, rateReads[0], "disabled state must be consulted before a cached rate is read");
        assertEquals(0, fromCalls[0]);
        assertEquals(0, toCalls[0]);

        mutableEnabled[0] = true;
        mutableRate[0] = 0.4D;
        rateReads[0] = 0;
        fromCalls[0] = 0;
        toCalls[0] = 0;
        assertEquals(5, FabricEnergyConversions.otherForJoules(mutable, 2));
        assertEquals(2, FabricEnergyConversions.joulesForOther(mutable, 5));
        assertTrue(fromCalls[0] > 20_000, "changed exact rate bits must rebuild the identity snapshot");
        assertTrue(toCalls[0] > 20_000, "the rebuilt snapshot must be verified under the new rate");
        assertTrue(FabricEnergyConversions.isExactPair(mutable, 5, 2));

        int[] distinctFromCalls = {0};
        int[] distinctToCalls = {0};
        IEnergyConversion sameAdvertisedRateDifferentBehavior = new IEnergyConversion() {
            @Override
            public boolean isEnabled() {
                return true;
            }

            @Override
            public double getConversion() {
                return 0.4D;
            }

            @Override
            public long convertFrom(long energy) {
                distinctFromCalls[0]++;
                return energy == 4 ? 6 : energy == 6 ? 4 : energy;
            }

            @Override
            public long convertTo(long joules) {
                distinctToCalls[0]++;
                return joules == 4 ? 6 : joules == 6 ? 4 : joules;
            }
        };
        assertEquals(6, FabricEnergyConversions.otherForJoules(sameAdvertisedRateDifferentBehavior, 4));
        assertTrue(distinctFromCalls[0] > 20_000,
              "a distinct identity must not reuse another conversion's same-rate candidates");
        assertTrue(distinctToCalls[0] > 20_000,
              "the distinct identity must verify its snapshot with its own conversion methods");
        assertTrue(FabricEnergyConversions.isExactPair(sameAdvertisedRateDifferentBehavior, 6, 4));

        // A behavior change with unchanged advertised rate bits must also invalidate when the old snapshot has no
        // pair inside the new projection window; otherwise this lookup would remain zero forever.
        double[] effectiveRate = {2.5D};
        IEnergyConversion sameBitsMutableBehavior = new IEnergyConversion() {
            @Override
            public boolean isEnabled() {
                return true;
            }

            @Override
            public double getConversion() {
                return 2.5D;
            }

            @Override
            public long convertFrom(long energy) {
                return (long) (energy * effectiveRate[0]);
            }

            @Override
            public long convertTo(long joules) {
                return (long) (joules / effectiveRate[0]);
            }
        };
        assertEquals(2, FabricEnergyConversions.otherForJoules(sameBitsMutableBehavior, 5));
        effectiveRate[0] = 5.0D;
        assertEquals(1, FabricEnergyConversions.otherForJoules(sameBitsMutableBehavior, 5));
        assertTrue(FabricEnergyConversions.isExactPair(sameBitsMutableBehavior, 1, 5));
    }

    private static void assertMonotoneAndBounded(IEnergyConversion conversion, long[] increasingBounds) {
        long previousOther = 0;
        long previousJoules = 0;
        for (long bound : increasingBounds) {
            long other = FabricEnergyConversions.otherForJoules(conversion, bound);
            long joules = FabricEnergyConversions.joulesForOther(conversion, bound);
            assertTrue(other >= previousOther, "other-domain result decreased at bound " + bound);
            assertTrue(joules >= previousJoules, "joule-domain result decreased at bound " + bound);
            assertForwardBoundedPair(conversion, bound, other);
            assertReverseBoundedPair(conversion, bound, joules);
            previousOther = other;
            previousJoules = joules;
        }
    }

    private static void assertAtLeastNinetyNinePercentOfProjectedBounds(IEnergyConversion conversion, long bound) {
        long projectedOther = FabricEnergyConversions.toOther(conversion, bound);
        assertTrue(projectedOther > 0, "forward utilization projection must be non-vacuous");
        long selectedOther = FabricEnergyConversions.otherForJoules(conversion, bound);
        assertTrue(selectedOther >= projectedOther - projectedOther / 100,
              "large forward selection must retain at least 99% of the official projection");
        long projectedJoules = FabricEnergyConversions.fromOther(conversion, bound);
        assertTrue(projectedJoules > 0, "reverse utilization projection must be non-vacuous");
        long selectedJoules = FabricEnergyConversions.joulesForOther(conversion, bound);
        assertTrue(selectedJoules >= projectedJoules - projectedJoules / 100,
              "large reverse selection must retain at least 99% of the official projection");
    }

    private static void assertForwardBoundedPair(IEnergyConversion conversion, long maxJoules, long other) {
        assertTrue(other <= FabricEnergyConversions.toOther(conversion, maxJoules));
        if (other == 0) {
            return;
        }
        long joules = FabricEnergyConversions.fromOther(conversion, other);
        assertTrue(joules <= maxJoules);
        assertTrue(FabricEnergyConversions.isExactPair(conversion, other, joules));
    }

    private static void assertReverseBoundedPair(IEnergyConversion conversion, long maxOther, long joules) {
        assertTrue(joules <= FabricEnergyConversions.fromOther(conversion, maxOther));
        if (joules == 0) {
            return;
        }
        long other = FabricEnergyConversions.toOther(conversion, joules);
        assertTrue(other <= maxOther);
        assertTrue(FabricEnergyConversions.isExactPair(conversion, other, joules));
    }

    private static IEnergyConversion conversion(boolean enabled, double rate) {
        return new IEnergyConversion() {
            @Override
            public boolean isEnabled() {
                return enabled;
            }

            @Override
            public double getConversion() {
                return rate;
            }
        };
    }
}
