package mekanism.common.capabilities.laser.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import mekanism.api.lasers.ILaserDissipation;
import org.junit.jupiter.api.Test;

class LaserDissipationHandlerTest {

    @Test
    void createRetainsValidValuesAndValidatesFieldsIndependentlyWithRawComparisons() {
        for (double[] values : new double[][]{{0, 1}, {1, 0}, {0.25, 0.75}}) {
            ILaserDissipation handler = LaserDissipationHandler.create(values[0], values[1]);
            assertEquals(values[0], handler.getDissipationPercent());
            assertEquals(values[1], handler.getRefractionPercent());
        }

        assertEquals("Dissipation percent must be between zero and one inclusive",
              assertThrows(IllegalArgumentException.class, () -> LaserDissipationHandler.create(-0.01, 0.5)).getMessage());
        assertEquals("Dissipation percent must be between zero and one inclusive",
              assertThrows(IllegalArgumentException.class, () -> LaserDissipationHandler.create(Double.POSITIVE_INFINITY, 0.5)).getMessage());
        assertEquals("Refraction percent must be between zero and one inclusive",
              assertThrows(IllegalArgumentException.class, () -> LaserDissipationHandler.create(0.5, 1.01)).getMessage());
        assertEquals("Refraction percent must be between zero and one inclusive",
              assertThrows(IllegalArgumentException.class, () -> LaserDissipationHandler.create(0.5, Double.NEGATIVE_INFINITY)).getMessage());

        ILaserDissipation nanDissipation = LaserDissipationHandler.create(Double.NaN, 0.4);
        ILaserDissipation nanRefraction = LaserDissipationHandler.create(0.6, Double.NaN);
        assertTrue(Double.isNaN(nanDissipation.getDissipationPercent()));
        assertTrue(Double.isNaN(nanRefraction.getRefractionPercent()));
    }
}
