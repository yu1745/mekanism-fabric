package mekanism.api.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IEnergyConversionTest {

    @SuppressWarnings("removal")
    @Test
    void officialDoubleConversionPreservesZeroRoundingHelpersAndSaturation() {
        IEnergyConversion one = conversion(1);
        assertEquals(0, one.convertFrom(0));
        assertEquals(0, one.convertTo(0));
        assertEquals(3_000_000_000L, one.convertFrom(3_000_000_000L));
        assertEquals(3_000_000_000L, one.convertTo(3_000_000_000L));
        assertEquals(Integer.MAX_VALUE, one.convertToAsInt(3_000_000_000L));
        assertEquals(3_000_000_000L, one.convertToAsLong(3_000_000_000L));
        assertTrue(one.isOneToOne());

        IEnergyConversion twoPointFive = conversion(2.5);
        assertEquals(7, twoPointFive.convertFrom(3));
        assertEquals(3, twoPointFive.convertTo(8));
        assertEquals(3.2, twoPointFive.convertToDouble(8));
        assertEquals(Long.MAX_VALUE, twoPointFive.convertFrom(Long.MAX_VALUE));
        assertFalse(twoPointFive.isOneToOne());

        IEnergyConversion pointFour = conversion(0.4);
        assertEquals(1, pointFour.convertFrom(3));
        assertEquals(2, pointFour.convertTo(1));
        assertEquals(Long.MAX_VALUE, pointFour.convertTo(Long.MAX_VALUE));
        assertFalse(pointFour.isOneToOne());
    }

    private static IEnergyConversion conversion(double rate) {
        return new IEnergyConversion() {
            @Override
            public boolean isEnabled() {
                return true;
            }

            @Override
            public double getConversion() {
                return rate;
            }
        };
    }
}
