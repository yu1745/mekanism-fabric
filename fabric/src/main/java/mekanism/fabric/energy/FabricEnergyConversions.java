package mekanism.fabric.energy;

import java.lang.ref.WeakReference;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import mekanism.api.energy.IEnergyConversion;
import org.jetbrains.annotations.Nullable;

/**
 * Shared validation and conservative fixed-point quantization for the Team Reborn Energy boundary.
 * Reportable pairs always survive both official {@link IEnergyConversion} conversions.
 */
final class FabricEnergyConversions {

    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);
    // This fixed 10-bit dyadic mesh covers the positive-long range at logarithmic resolution. Verification may
    // leave gaps, so the mesh is a coverage strategy rather than a maximality guarantee.
    private static final int DYADIC_MANTISSA_BITS = 10;
    private static final long DYADIC_MIN_MANTISSA = 1L << (DYADIC_MANTISSA_BITS - 1);
    private static final long DYADIC_MAX_MANTISSA = (1L << DYADIC_MANTISSA_BITS) - 1;
    private static final int DYADIC_MAX_SHIFT = 53;
    private static final int DYADIC_CANDIDATE_OCCURRENCES = 1_023 + DYADIC_MAX_SHIFT * 512;
    private static final int DECIMAL_MULTIPLIER_LIMIT = 64;
    private static final int POSITIVE_LONG_SHIFT_COUNT = 63;
    // Positive continued-fraction denominators grow at least as quickly as Fibonacci numbers after the first two;
    // F(92) fits in a positive long and F(93) does not.
    private static final int MAX_REPRESENTABLE_CONVERGENTS = 92;
    private static final int MAX_BUILD_CANDIDATE_OCCURRENCES = DYADIC_CANDIDATE_OCCURRENCES
          + MAX_REPRESENTABLE_CONVERGENTS * DECIMAL_MULTIPLIER_LIMIT * POSITIVE_LONG_SHIFT_COUNT;

    // A snapshot is one primitive-only immutable array: rate bits, pair count, optional range-minimum tree base,
    // sorted other values, corresponding Joules, then (only for a non-monotone custom conversion) the tree.
    private static final int SNAPSHOT_RATE_BITS = 0;
    private static final int SNAPSHOT_PAIR_COUNT = 1;
    private static final int SNAPSHOT_TREE_BASE = 2;
    private static final int SNAPSHOT_HEADER_SIZE = 3;
    private static final long RETRY_WITH_CURRENT_RATE = Long.MIN_VALUE;
    private static final int RATE_STABILITY_ATTEMPTS = 3;

    // Four weak identity slots cover the small stable enum set used in production without retaining an owner or
    // growing with transient adapters. Access is synchronized; snapshots are never mutated after publication.
    private static final int CACHE_SLOT_COUNT = 4;
    private static final Object CACHE_LOCK = new Object();
    private static final WeakReference<?>[] CACHE_IDENTITIES = new WeakReference<?>[CACHE_SLOT_COUNT];
    private static final long[][] CACHE_SNAPSHOTS = new long[CACHE_SLOT_COUNT][];
    private static int nextReplacementSlot;

    private FabricEnergyConversions() {
    }

    static boolean isUsable(@Nullable IEnergyConversion conversion) {
        return !Double.isNaN(usableRate(conversion));
    }

    static long fromOther(IEnergyConversion conversion, long otherUnits) {
        if (otherUnits <= 0) {
            return 0;
        }
        double rate = usableRate(conversion);
        if (Double.isNaN(rate)) {
            return 0;
        }
        // IEnergyConversion necessarily passes a long through double. Preserve the exact identity case at this
        // adapter boundary so odd values above 2^53 are not rounded before they reach either storage domain.
        if (rate == 1.0D) {
            return otherUnits;
        }
        return Math.max(0, conversion.convertFrom(otherUnits));
    }

    static long toOther(IEnergyConversion conversion, long joules) {
        if (joules <= 0) {
            return 0;
        }
        double rate = usableRate(conversion);
        if (Double.isNaN(rate)) {
            return 0;
        }
        if (rate == 1.0D) {
            return joules;
        }
        return Math.max(0, conversion.convertTo(joules));
    }

    /**
     * Finds Team Energy units in a verified fixed-point pair not exceeding the supplied Joule bound.
     */
    static long otherForJoules(IEnergyConversion conversion, long maxJoules) {
        if (maxJoules <= 0) {
            return 0;
        }
        for (int attempt = 0; attempt < RATE_STABILITY_ATTEMPTS; attempt++) {
            double rate = usableRate(conversion);
            if (Double.isNaN(rate)) {
                return 0;
            }
            long rateBits = Double.doubleToRawLongBits(rate);
            if (rate == 1.0D) {
                if (hasCurrentRate(conversion, rateBits)) {
                    return maxJoules;
                }
                continue;
            }
            long maxOther = Math.max(0, conversion.convertTo(maxJoules));
            if (maxOther <= 0) {
                if (hasCurrentRate(conversion, rateBits)) {
                    return 0;
                }
                continue;
            }
            long result = selectCachedPair(conversion, rate, rateBits, maxOther, maxJoules, false);
            if (result != RETRY_WITH_CURRENT_RATE) {
                return result;
            }
        }
        // A continuously changing conversion has no coherent pair to report for this invocation.
        return 0;
    }

    /**
     * Finds Joules in a verified fixed-point pair not exceeding the supplied Team Energy bound.
     */
    static long joulesForOther(IEnergyConversion conversion, long maxOther) {
        if (maxOther <= 0) {
            return 0;
        }
        for (int attempt = 0; attempt < RATE_STABILITY_ATTEMPTS; attempt++) {
            double rate = usableRate(conversion);
            if (Double.isNaN(rate)) {
                return 0;
            }
            long rateBits = Double.doubleToRawLongBits(rate);
            if (rate == 1.0D) {
                if (hasCurrentRate(conversion, rateBits)) {
                    return maxOther;
                }
                continue;
            }
            long maxJoules = Math.max(0, conversion.convertFrom(maxOther));
            if (maxJoules <= 0) {
                if (hasCurrentRate(conversion, rateBits)) {
                    return 0;
                }
                continue;
            }
            // Preserve the prior projection filter as well as both explicit caller bounds.
            long projectedOther = Math.max(0, conversion.convertTo(maxJoules));
            long effectiveMaxOther = Math.min(maxOther, projectedOther);
            if (effectiveMaxOther <= 0) {
                if (hasCurrentRate(conversion, rateBits)) {
                    return 0;
                }
                continue;
            }
            long result = selectCachedPair(
                  conversion, rate, rateBits, effectiveMaxOther, maxJoules, true);
            if (result != RETRY_WITH_CURRENT_RATE) {
                return result;
            }
        }
        return 0;
    }

    static boolean isExactPair(IEnergyConversion conversion, long otherUnits, long joules) {
        if (otherUnits <= 0 || joules <= 0) {
            return false;
        }
        double rate = usableRate(conversion);
        if (Double.isNaN(rate)) {
            return false;
        }
        long rateBits = Double.doubleToRawLongBits(rate);
        if (rate == 1.0D) {
            return otherUnits == joules && hasCurrentRate(conversion, rateBits);
        }
        return conversion.convertFrom(otherUnits) == joules
              && conversion.convertTo(joules) == otherUnits
              && hasCurrentRate(conversion, rateBits);
    }

    /**
     * Selects the largest verified member of one finite, bound-independent candidate family. The first cold lookup
     * for one conversion identity/rate-bit pair materializes every representable family member, sorts/deduplicates
     * it, and verifies each remaining candidate through the conversion identity that owns the snapshot. There are
     * exactly 28,159 dyadic occurrences and at most 92 * 64 * 63 decimal occurrences: at most 399,103 before
     * deduplication, and at most twice that many direct conversion calls for verification. The family remains all
     * 1..1,023 integers plus 512..1,023 at every representable binary shift, union every canonical-decimal
     * convergent denominator times 1..64 at every representable binary shift. Caller bounds only filter it.
     * Choosing the largest eligible member is conservative and makes no globally maximal/utilization claim.
     *
     * <p>Warm lookups scan four weak identity slots and perform logarithmic predecessor/range-minimum selection,
     * followed by a fresh official two-way verification. Exact rate bits are checked before publication and before
     * return. A changed rate therefore rebuilds rather than reusing a stale snapshot, disabled state is still read
     * live, and two conversion implementations reporting the same rate never share behavior-sensitive pairs.
     */
    private static long selectCachedPair(IEnergyConversion conversion, double rate, long rateBits, long maxOther,
          long maxJoules, boolean returnJoules) {
        long[] snapshot = snapshotFor(conversion, rate, rateBits);
        if (snapshot == null) {
            return RETRY_WITH_CURRENT_RATE;
        }
        int pairCount = Math.toIntExact(snapshot[SNAPSHOT_PAIR_COUNT]);
        int otherOffset = SNAPSHOT_HEADER_SIZE;
        int upperOtherIndex = upperBound(snapshot, otherOffset, pairCount, maxOther) - 1;
        if (upperOtherIndex < 0) {
            return hasCurrentRate(conversion, rateBits) ? 0 : RETRY_WITH_CURRENT_RATE;
        }

        int jouleOffset = otherOffset + pairCount;
        int treeBase = Math.toIntExact(snapshot[SNAPSHOT_TREE_BASE]);
        int selectedIndex;
        if (treeBase == 0) {
            selectedIndex = Math.min(
                  upperOtherIndex, upperBound(snapshot, jouleOffset, pairCount, maxJoules) - 1);
        } else {
            int treeOffset = jouleOffset + pairCount;
            selectedIndex = findRightmostAtMost(
                  snapshot, treeOffset, 1, 0, treeBase - 1, upperOtherIndex, maxJoules);
        }
        if (selectedIndex < 0) {
            return hasCurrentRate(conversion, rateBits) ? 0 : RETRY_WITH_CURRENT_RATE;
        }

        long other = snapshot[otherOffset + selectedIndex];
        long cachedJoules = snapshot[jouleOffset + selectedIndex];
        long currentJoules = conversion.convertFrom(other);
        boolean verified = currentJoules == cachedJoules && currentJoules > 0 && currentJoules <= maxJoules
              && conversion.convertTo(currentJoules) == other;
        if (!hasCurrentRate(conversion, rateBits)) {
            return RETRY_WITH_CURRENT_RATE;
        }
        if (!verified) {
            // Supports a stable identity whose conversion methods changed behavior without changing the advertised
            // rate: never return its stale pair, and give the next attempt a freshly verified snapshot.
            invalidateSnapshot(conversion, snapshot);
            return RETRY_WITH_CURRENT_RATE;
        }
        return returnJoules ? currentJoules : other;
    }

    @Nullable
    private static long[] snapshotFor(IEnergyConversion conversion, double rate, long rateBits) {
        long[] cached = findCachedSnapshot(conversion, rateBits);
        if (cached != null) {
            return cached;
        }

        long[] built = buildSnapshot(conversion, rate, rateBits);
        if (!hasCurrentRate(conversion, rateBits)) {
            return null;
        }
        synchronized (CACHE_LOCK) {
            int availableSlot = -1;
            for (int slot = 0; slot < CACHE_SLOT_COUNT; slot++) {
                WeakReference<?> reference = CACHE_IDENTITIES[slot];
                Object cachedIdentity = reference == null ? null : reference.get();
                if (cachedIdentity == conversion) {
                    long[] concurrentlyBuilt = CACHE_SNAPSHOTS[slot];
                    if (concurrentlyBuilt != null && concurrentlyBuilt[SNAPSHOT_RATE_BITS] == rateBits) {
                        return concurrentlyBuilt;
                    }
                    CACHE_SNAPSHOTS[slot] = built;
                    return built;
                }
                if (cachedIdentity == null) {
                    CACHE_IDENTITIES[slot] = null;
                    CACHE_SNAPSHOTS[slot] = null;
                    if (availableSlot < 0) {
                        availableSlot = slot;
                    }
                }
            }
            int slot = availableSlot >= 0 ? availableSlot : nextReplacementSlot;
            nextReplacementSlot = (slot + 1) % CACHE_SLOT_COUNT;
            CACHE_IDENTITIES[slot] = new WeakReference<>(conversion);
            CACHE_SNAPSHOTS[slot] = built;
            return built;
        }
    }

    @Nullable
    private static long[] findCachedSnapshot(IEnergyConversion conversion, long rateBits) {
        synchronized (CACHE_LOCK) {
            for (int slot = 0; slot < CACHE_SLOT_COUNT; slot++) {
                WeakReference<?> reference = CACHE_IDENTITIES[slot];
                if (reference == null) {
                    continue;
                }
                Object cachedIdentity = reference.get();
                if (cachedIdentity == null) {
                    CACHE_IDENTITIES[slot] = null;
                    CACHE_SNAPSHOTS[slot] = null;
                } else if (cachedIdentity == conversion) {
                    long[] snapshot = CACHE_SNAPSHOTS[slot];
                    return snapshot != null && snapshot[SNAPSHOT_RATE_BITS] == rateBits ? snapshot : null;
                }
            }
        }
        return null;
    }

    private static void invalidateSnapshot(IEnergyConversion conversion, long[] snapshot) {
        synchronized (CACHE_LOCK) {
            for (int slot = 0; slot < CACHE_SLOT_COUNT; slot++) {
                WeakReference<?> reference = CACHE_IDENTITIES[slot];
                if (reference != null && reference.get() == conversion && CACHE_SNAPSHOTS[slot] == snapshot) {
                    CACHE_SNAPSHOTS[slot] = null;
                    return;
                }
            }
        }
    }

    private static long[] buildSnapshot(IEnergyConversion conversion, double rate, long rateBits) {
        long[] convergentDenominators = decimalConvergentDenominators(rate);
        int candidateCount = candidateOccurrenceCount(convergentDenominators);
        long[] candidates = new long[candidateCount];
        int candidateIndex = addDyadicCandidates(candidates, 0);
        candidateIndex = addDecimalCandidates(candidates, candidateIndex, convergentDenominators);
        if (candidateIndex != candidateCount || candidateCount > MAX_BUILD_CANDIDATE_OCCURRENCES) {
            throw new IllegalStateException("Fixed energy candidate envelope was miscomputed");
        }
        Arrays.sort(candidates);

        int uniqueCount = 0;
        for (long candidate : candidates) {
            if (uniqueCount == 0 || candidates[uniqueCount - 1] != candidate) {
                candidates[uniqueCount++] = candidate;
            }
        }
        long[] verifiedJoules = new long[uniqueCount];
        int verifiedCount = 0;
        for (int candidate = 0; candidate < uniqueCount; candidate++) {
            long other = candidates[candidate];
            long joules = conversion.convertFrom(other);
            if (joules > 0 && conversion.convertTo(joules) == other) {
                candidates[verifiedCount] = other;
                verifiedJoules[verifiedCount] = joules;
                verifiedCount++;
            }
        }

        boolean monotoneJoules = true;
        for (int pair = 1; pair < verifiedCount; pair++) {
            if (verifiedJoules[pair] <= verifiedJoules[pair - 1]) {
                monotoneJoules = false;
                break;
            }
        }
        int treeBase = 0;
        int treeLength = 0;
        if (!monotoneJoules) {
            treeBase = 1;
            while (treeBase < verifiedCount) {
                treeBase <<= 1;
            }
            treeLength = treeBase * 2;
        }

        long[] snapshot = new long[SNAPSHOT_HEADER_SIZE + verifiedCount * 2 + treeLength];
        snapshot[SNAPSHOT_RATE_BITS] = rateBits;
        snapshot[SNAPSHOT_PAIR_COUNT] = verifiedCount;
        snapshot[SNAPSHOT_TREE_BASE] = treeBase;
        System.arraycopy(candidates, 0, snapshot, SNAPSHOT_HEADER_SIZE, verifiedCount);
        int jouleOffset = SNAPSHOT_HEADER_SIZE + verifiedCount;
        System.arraycopy(verifiedJoules, 0, snapshot, jouleOffset, verifiedCount);
        if (treeBase != 0) {
            int treeOffset = jouleOffset + verifiedCount;
            Arrays.fill(snapshot, treeOffset, snapshot.length, Long.MAX_VALUE);
            for (int pair = 0; pair < verifiedCount; pair++) {
                snapshot[treeOffset + treeBase + pair] = verifiedJoules[pair];
            }
            for (int node = treeBase - 1; node > 0; node--) {
                snapshot[treeOffset + node] = Math.min(
                      snapshot[treeOffset + node * 2], snapshot[treeOffset + node * 2 + 1]);
            }
        }
        return snapshot;
    }

    private static long[] decimalConvergentDenominators(double rate) {
        BigDecimal decimalRate = BigDecimal.valueOf(rate).stripTrailingZeros();
        BigInteger numerator = decimalRate.unscaledValue();
        BigInteger denominator = BigInteger.ONE;
        int scale = decimalRate.scale();
        if (scale > 0) {
            denominator = BigInteger.TEN.pow(scale);
        } else if (scale < 0) {
            numerator = numerator.multiply(BigInteger.TEN.pow(-scale));
        }
        BigInteger gcd = numerator.gcd(denominator);
        numerator = numerator.divide(gcd);
        denominator = denominator.divide(gcd);

        long[] convergents = new long[16];
        int convergentCount = 0;
        BigInteger remainderNumerator = numerator;
        BigInteger remainderDenominator = denominator;
        BigInteger denominatorMinusTwo = BigInteger.ONE;
        BigInteger denominatorMinusOne = BigInteger.ZERO;
        while (remainderDenominator.signum() != 0) {
            BigInteger[] division = remainderNumerator.divideAndRemainder(remainderDenominator);
            BigInteger convergentDenominator = division[0].multiply(denominatorMinusOne).add(denominatorMinusTwo);
            if (convergentDenominator.signum() > 0 && convergentDenominator.compareTo(LONG_MAX) <= 0) {
                if (convergentCount == convergents.length) {
                    convergents = Arrays.copyOf(convergents, convergents.length * 2);
                }
                convergents[convergentCount++] = convergentDenominator.longValueExact();
            }
            denominatorMinusTwo = denominatorMinusOne;
            denominatorMinusOne = convergentDenominator;
            remainderNumerator = remainderDenominator;
            remainderDenominator = division[1];
        }
        if (convergentCount > MAX_REPRESENTABLE_CONVERGENTS) {
            throw new IllegalStateException("Too many positive-long continued-fraction denominators");
        }
        return Arrays.copyOf(convergents, convergentCount);
    }

    private static int candidateOccurrenceCount(long[] convergentDenominators) {
        int count = DYADIC_CANDIDATE_OCCURRENCES;
        for (long denominator : convergentDenominators) {
            int maxMultiplier = (int) Math.min(DECIMAL_MULTIPLIER_LIMIT, Long.MAX_VALUE / denominator);
            for (int multiplier = 1; multiplier <= maxMultiplier; multiplier++) {
                long base = denominator * multiplier;
                long scaleLimit = Long.MAX_VALUE / base;
                count += Long.SIZE - Long.numberOfLeadingZeros(scaleLimit);
            }
        }
        return count;
    }

    private static int addDyadicCandidates(long[] candidates, int candidateIndex) {
        for (int shift = 0; shift <= DYADIC_MAX_SHIFT; shift++) {
            long lowerMantissa = shift == 0 ? 1 : DYADIC_MIN_MANTISSA;
            long upperMantissa = Math.min(DYADIC_MAX_MANTISSA, Long.MAX_VALUE >> shift);
            for (long mantissa = lowerMantissa; mantissa <= upperMantissa; mantissa++) {
                candidates[candidateIndex++] = mantissa << shift;
            }
        }
        return candidateIndex;
    }

    private static int addDecimalCandidates(long[] candidates, int candidateIndex,
          long[] convergentDenominators) {
        for (long denominator : convergentDenominators) {
            int maxMultiplier = (int) Math.min(DECIMAL_MULTIPLIER_LIMIT, Long.MAX_VALUE / denominator);
            for (int multiplier = 1; multiplier <= maxMultiplier; multiplier++) {
                long base = denominator * multiplier;
                long scaleLimit = Long.MAX_VALUE / base;
                int maxShift = Long.SIZE - 1 - Long.numberOfLeadingZeros(scaleLimit);
                for (int shift = 0; shift <= maxShift; shift++) {
                    candidates[candidateIndex++] = base << shift;
                }
            }
        }
        return candidateIndex;
    }

    private static int upperBound(long[] values, int offset, int length, long bound) {
        int low = 0;
        int high = length;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (values[offset + middle] <= bound) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return low;
    }

    private static int findRightmostAtMost(long[] snapshot, int treeOffset, int node, int left, int right,
          int queryRight, long maxJoules) {
        if (left > queryRight || snapshot[treeOffset + node] > maxJoules) {
            return -1;
        }
        if (left == right) {
            return left;
        }
        int middle = (left + right) >>> 1;
        int rightResult = findRightmostAtMost(
              snapshot, treeOffset, node * 2 + 1, middle + 1, right, queryRight, maxJoules);
        return rightResult >= 0 ? rightResult : findRightmostAtMost(
              snapshot, treeOffset, node * 2, left, middle, queryRight, maxJoules);
    }

    private static double usableRate(@Nullable IEnergyConversion conversion) {
        if (conversion == null || !conversion.isEnabled()) {
            return Double.NaN;
        }
        double rate = conversion.getConversion();
        return Double.isFinite(rate) && rate > 0 ? rate : Double.NaN;
    }

    private static boolean hasCurrentRate(IEnergyConversion conversion, long rateBits) {
        if (!conversion.isEnabled()) {
            return false;
        }
        double currentRate = conversion.getConversion();
        return Double.isFinite(currentRate) && currentRate > 0
              && Double.doubleToRawLongBits(currentRate) == rateBits;
    }
}
