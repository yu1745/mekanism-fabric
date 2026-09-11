package mekanism.fabric.energy;

import java.math.BigDecimal;
import java.math.BigInteger;
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
    private static final int DECIMAL_MULTIPLIER_LIMIT = 64;

    private FabricEnergyConversions() {
    }

    static boolean isUsable(@Nullable IEnergyConversion conversion) {
        if (conversion == null || !conversion.isEnabled()) {
            return false;
        }
        double rate = conversion.getConversion();
        return Double.isFinite(rate) && rate > 0;
    }

    static long fromOther(IEnergyConversion conversion, long otherUnits) {
        if (otherUnits <= 0 || !isUsable(conversion)) {
            return 0;
        }
        // IEnergyConversion necessarily passes a long through double. Preserve the exact identity case at this
        // adapter boundary so odd values above 2^53 are not rounded before they reach either storage domain.
        if (isExactIdentity(conversion)) {
            return otherUnits;
        }
        return Math.max(0, conversion.convertFrom(otherUnits));
    }

    static long toOther(IEnergyConversion conversion, long joules) {
        if (joules <= 0 || !isUsable(conversion)) {
            return 0;
        }
        if (isExactIdentity(conversion)) {
            return joules;
        }
        return Math.max(0, conversion.convertTo(joules));
    }

    /**
     * Finds Team Energy units in a verified fixed-point pair not exceeding the supplied Joule bound.
     */
    static long otherForJoules(IEnergyConversion conversion, long maxJoules) {
        if (maxJoules <= 0 || !isUsable(conversion)) {
            return 0;
        }
        if (isExactIdentity(conversion)) {
            return maxJoules;
        }
        long maxOther = toOther(conversion, maxJoules);
        return quantizedOther(conversion, maxOther, maxJoules);
    }

    /**
     * Finds Joules in a verified fixed-point pair not exceeding the supplied Team Energy bound.
     */
    static long joulesForOther(IEnergyConversion conversion, long maxOther) {
        if (maxOther <= 0 || !isUsable(conversion)) {
            return 0;
        }
        if (isExactIdentity(conversion)) {
            return maxOther;
        }
        long maxJoules = fromOther(conversion, maxOther);
        long other = quantizedOther(conversion, maxOther, maxJoules);
        return other == 0 ? 0 : fromOther(conversion, other);
    }

    static boolean isExactPair(IEnergyConversion conversion, long otherUnits, long joules) {
        return otherUnits > 0 && joules > 0 && isUsable(conversion)
              && fromOther(conversion, otherUnits) == joules
              && toOther(conversion, joules) == otherUnits;
    }

    /**
     * Selects the largest verified member of one finite, bound-independent candidate family. The first part is a
     * global 10-bit dyadic mesh: every integer through 1,023, followed by 512..1,023 times each fitting power of
     * two. The second part uses every canonical-decimal continued-fraction denominator, multiplied by 1..64 and
     * then by every fitting power of two. The bounded multiplier closure discovers useful decimal quanta that are
     * not bare convergent denominators, while the global mesh prevents high-bound selection from depending on one
     * seed's shift behavior. Bounds only filter this fixed family; they never generate a decrement scan.
     *
     * <p>Every returned pair is independently checked through the official conversions. Selecting the largest
     * eligible member makes the result deterministic and monotone for component-wise increasing bounds. This is a
     * conservative finite family, not a claim that the globally largest fixed point is found for every double.
     */
    private static long quantizedOther(IEnergyConversion conversion, long maxOther, long maxJoules) {
        if (maxOther <= 0 || maxJoules <= 0) {
            return 0;
        }
        // For any exact pair with joules <= maxJoules, monotonic convertTo requires the other side to be no larger
        // than this projection. Taking the minimum also makes every subsequent shift and multiply overflow-safe.
        long effectiveMaxOther = Math.min(maxOther, toOther(conversion, maxJoules));
        if (effectiveMaxOther <= 0) {
            return 0;
        }
        long bestOther = findDyadicMeshOther(conversion, effectiveMaxOther, maxJoules);
        return findDecimalFamilyOther(conversion, effectiveMaxOther, maxJoules, bestOther);
    }

    /**
     * Walks the fixed dyadic mesh in strict descending numeric order and stops at its largest eligible member.
     * There are at most 1,023 exact-integer probes plus 53 bands of 512 probes, independent of caller bounds.
     */
    private static long findDyadicMeshOther(IEnergyConversion conversion, long maxOther, long maxJoules) {
        int bitLength = Long.SIZE - Long.numberOfLeadingZeros(maxOther);
        int maxShift = Math.max(0, bitLength - DYADIC_MANTISSA_BITS);
        for (int shift = maxShift; shift >= 0; shift--) {
            long lowerMantissa = shift == 0 ? 1 : DYADIC_MIN_MANTISSA;
            long upperMantissa = Math.min(DYADIC_MAX_MANTISSA, maxOther >> shift);
            for (long mantissa = upperMantissa; mantissa >= lowerMantissa; mantissa--) {
                long candidateOther = mantissa << shift;
                if (isVerifiedWithinJouleBound(conversion, candidateOther, maxJoules)) {
                    return candidateOther;
                }
            }
        }
        return 0;
    }

    /**
     * Euclid's algorithm yields a finite set of convergent denominators. For each denominator, a fixed 1..64
     * multiplier closure and at most 63 dyadic shifts are considered in descending order. This includes nearby
     * rational lattice points such as three times the final denominator even when the denominator itself does not
     * survive the official double round trip.
     */
    private static long findDecimalFamilyOther(IEnergyConversion conversion, long maxOther, long maxJoules,
          long bestOther) {
        BigDecimal decimalRate = BigDecimal.valueOf(conversion.getConversion()).stripTrailingZeros();
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

        BigInteger remainderNumerator = numerator;
        BigInteger remainderDenominator = denominator;
        BigInteger denominatorMinusTwo = BigInteger.ONE;
        BigInteger denominatorMinusOne = BigInteger.ZERO;
        while (remainderDenominator.signum() != 0) {
            BigInteger[] division = remainderNumerator.divideAndRemainder(remainderDenominator);
            BigInteger convergentDenominator = division[0].multiply(denominatorMinusOne).add(denominatorMinusTwo);
            if (convergentDenominator.signum() > 0 && convergentDenominator.compareTo(LONG_MAX) <= 0) {
                long candidateDenominator = convergentDenominator.longValueExact();
                if (candidateDenominator <= maxOther) {
                    bestOther = findScaledDenominatorOther(
                          conversion, candidateDenominator, maxOther, maxJoules, bestOther);
                }
            }
            denominatorMinusTwo = denominatorMinusOne;
            denominatorMinusOne = convergentDenominator;
            remainderNumerator = remainderDenominator;
            remainderDenominator = division[1];
        }
        return bestOther;
    }

    private static long findScaledDenominatorOther(IEnergyConversion conversion, long denominator, long maxOther,
          long maxJoules, long bestOther) {
        int maxMultiplier = (int) Math.min(DECIMAL_MULTIPLIER_LIMIT, maxOther / denominator);
        for (int multiplier = 1; multiplier <= maxMultiplier; multiplier++) {
            // maxMultiplier was derived by division, so this product cannot overflow and is no larger than maxOther.
            long base = denominator * multiplier;
            long scaleLimit = maxOther / base;
            int maxShift = Long.SIZE - 1 - Long.numberOfLeadingZeros(scaleLimit);
            for (int shift = maxShift; shift >= 0; shift--) {
                // This guard documents the shift invariant and protects it if maxShift derivation changes later.
                if (base > (maxOther >> shift)) {
                    continue;
                }
                long candidateOther = base << shift;
                if (candidateOther <= bestOther) {
                    break;
                }
                if (isVerifiedWithinJouleBound(conversion, candidateOther, maxJoules)) {
                    bestOther = candidateOther;
                    break;
                }
            }
        }
        return bestOther;
    }

    private static boolean isVerifiedWithinJouleBound(IEnergyConversion conversion, long otherUnits,
          long maxJoules) {
        long joules = fromOther(conversion, otherUnits);
        return joules > 0 && joules <= maxJoules && toOther(conversion, joules) == otherUnits;
    }

    private static boolean isExactIdentity(IEnergyConversion conversion) {
        return conversion.getConversion() == 1.0D;
    }
}
