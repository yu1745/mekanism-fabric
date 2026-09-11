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
     * Selects from one conversion-defined, bound-independent family rather than searching down from each bound.
     * The seed is the smallest verified continued-fraction convergent of the canonical decimal rate (the exact
     * decimal numerator/denominator quantum is the final convergent). The descending powers-of-two basis has at
     * most 63 members. A member and every cumulative sum are independently verified through the official methods;
     * a failed candidate is simply skipped while the best verified sum is retained.
     *
     * <p>The fixed basis makes selection deterministic and monotone for component-wise increasing bounds: a newly
     * eligible power is larger than the sum of all lower powers. This is deliberately conservative and does not
     * claim the globally largest fixed point for arbitrary double rates.
     */
    private static long quantizedOther(IEnergyConversion conversion, long maxOther, long maxJoules) {
        if (maxOther <= 0 || maxJoules <= 0) {
            return 0;
        }
        long seedOther = findVerifiedSeedOther(conversion);
        if (seedOther == 0) {
            return 0;
        }
        long seedJoules = fromOther(conversion, seedOther);
        long bestOther = 0;
        long bestJoules = 0;
        for (int shift = 62; shift >= 0; shift--) {
            if (seedOther > (maxOther >> shift) || seedJoules > (maxJoules >> shift)) {
                continue;
            }
            long chunkOther = seedOther << shift;
            long chunkJoules = seedJoules << shift;
            if (!isExactPair(conversion, chunkOther, chunkJoules)
                  || chunkOther > maxOther - bestOther || chunkJoules > maxJoules - bestJoules) {
                continue;
            }
            long candidateOther = bestOther + chunkOther;
            long candidateJoules = bestJoules + chunkJoules;
            if (isExactPair(conversion, candidateOther, candidateJoules)) {
                bestOther = candidateOther;
                bestJoules = candidateJoules;
            }
        }
        return bestOther;
    }

    /**
     * Euclid's algorithm is finite and its convergent denominators increase, so this fallback has no attempt cap
     * or linear decrement. A verified early convergent can provide a useful exact pair even when the full decimal
     * denominator is larger than the caller's bound.
     */
    private static long findVerifiedSeedOther(IEnergyConversion conversion) {
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
        long bestSeed = 0;
        while (remainderDenominator.signum() != 0) {
            BigInteger[] division = remainderNumerator.divideAndRemainder(remainderDenominator);
            BigInteger convergentDenominator = division[0].multiply(denominatorMinusOne).add(denominatorMinusTwo);
            if (convergentDenominator.signum() > 0 && convergentDenominator.compareTo(LONG_MAX) <= 0) {
                long candidateOther = convergentDenominator.longValueExact();
                long candidateJoules = fromOther(conversion, candidateOther);
                if (isExactPair(conversion, candidateOther, candidateJoules)
                      && (bestSeed == 0 || candidateOther < bestSeed)) {
                    bestSeed = candidateOther;
                }
            }
            denominatorMinusTwo = denominatorMinusOne;
            denominatorMinusOne = convergentDenominator;
            remainderNumerator = remainderDenominator;
            remainderDenominator = division[1];
        }
        return bestSeed;
    }

    private static boolean isExactIdentity(IEnergyConversion conversion) {
        return conversion.getConversion() == 1.0D;
    }
}
