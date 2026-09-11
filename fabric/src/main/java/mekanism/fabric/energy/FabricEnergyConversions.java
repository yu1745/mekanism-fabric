package mekanism.fabric.energy;

import java.math.BigDecimal;
import java.math.BigInteger;
import mekanism.api.energy.IEnergyConversion;
import org.jetbrains.annotations.Nullable;

/**
 * Shared validation and fixed-point quantization for the Team Reborn Energy boundary.
 * Reportable pairs always survive both official {@link IEnergyConversion} conversions.
 */
final class FabricEnergyConversions {

    private static final int NEARBY_ATTEMPTS = 64;
    private static final int PROJECTION_ATTEMPTS = 256;

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
        return Math.max(0, conversion.convertFrom(otherUnits));
    }

    static long toOther(IEnergyConversion conversion, long joules) {
        if (joules <= 0 || !isUsable(conversion)) {
            return 0;
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
        long maxJoules = fromOther(conversion, maxOther);
        long other = quantizedOther(conversion, maxOther, maxJoules);
        return other == 0 ? 0 : fromOther(conversion, other);
    }

    static boolean isExactPair(IEnergyConversion conversion, long otherUnits, long joules) {
        return otherUnits > 0 && joules > 0 && isUsable(conversion)
              && fromOther(conversion, otherUnits) == joules
              && toOther(conversion, joules) == otherUnits;
    }

    private static long quantizedOther(IEnergyConversion conversion, long maxOther, long maxJoules) {
        if (maxOther <= 0 || maxJoules <= 0) {
            return 0;
        }
        // BigDecimal.valueOf uses the canonical decimal representation of the caller's double. Reducing that
        // ratio gives the intended whole-unit quantum, while all candidates are still verified through the
        // official double conversion methods before they can be used or reported.
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
        if (numerator.signum() <= 0 || numerator.bitLength() > 63 || denominator.bitLength() > 63) {
            return projectedFixedPoint(conversion, maxOther, maxJoules);
        }
        long jouleQuantum = numerator.longValueExact();
        long otherQuantum = denominator.longValueExact();
        long multiplier = Math.min(maxOther / otherQuantum, maxJoules / jouleQuantum);
        if (multiplier <= 0) {
            return projectedFixedPoint(conversion, maxOther, maxJoules);
        }
        long candidate = tryMultipliers(conversion, otherQuantum, jouleQuantum, multiplier, maxOther, maxJoules);
        if (candidate != 0) {
            return candidate;
        }
        // Large long values may stop being exactly distinguishable by the official double contract. Halving
        // finds a smaller verified multiple without ever claiming precision the conversion cannot represent.
        while (multiplier > 1) {
            multiplier /= 2;
            candidate = tryMultipliers(conversion, otherQuantum, jouleQuantum, multiplier, maxOther, maxJoules);
            if (candidate != 0) {
                return candidate;
            }
        }
        return projectedFixedPoint(conversion, maxOther, maxJoules);
    }

    private static long tryMultipliers(IEnergyConversion conversion, long otherQuantum, long jouleQuantum,
          long initialMultiplier, long maxOther, long maxJoules) {
        long multiplier = initialMultiplier;
        for (int attempt = 0; attempt < NEARBY_ATTEMPTS && multiplier > 0; attempt++, multiplier--) {
            long other = otherQuantum * multiplier;
            long joules = jouleQuantum * multiplier;
            if (other <= maxOther && joules <= maxJoules && isExactPair(conversion, other, joules)) {
                return other;
            }
        }
        return 0;
    }

    private static long projectedFixedPoint(IEnergyConversion conversion, long initialOther, long maxJoules) {
        long candidate = initialOther;
        for (int attempt = 0; attempt < PROJECTION_ATTEMPTS && candidate > 0; attempt++) {
            long joules = fromOther(conversion, candidate);
            if (joules > 0 && joules <= maxJoules && isExactPair(conversion, candidate, joules)) {
                return candidate;
            }
            long projected = joules <= 0 ? 0 : toOther(conversion, Math.min(joules, maxJoules));
            candidate = projected > 0 && projected < candidate ? projected : candidate - 1;
        }
        return 0;
    }
}
