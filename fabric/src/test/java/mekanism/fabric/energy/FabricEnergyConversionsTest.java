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
