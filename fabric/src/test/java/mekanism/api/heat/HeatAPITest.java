package mekanism.api.heat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class HeatAPITest {

    @Test
    void nullWorldUsesDefaultAmbientTemperature() {
        assertEquals(HeatAPI.AMBIENT_TEMP, HeatAPI.getAmbientTemp(null, BlockPos.ZERO));
    }

    @Test
    void biomeTemperatureUsesPlainsAsBaseline() {
        assertEquals(HeatAPI.AMBIENT_TEMP, HeatAPI.getAmbientTemp(0.8), 1E-12);
        assertEquals(280, HeatAPI.getAmbientTemp(0), 1E-12);
    }

    @Test
    void biomeTemperatureIsClampedToDocumentedRange() {
        assertEquals(155, HeatAPI.getAmbientTemp(-5), 1E-12);
        assertEquals(155, HeatAPI.getAmbientTemp(-100), 1E-12);
        assertEquals(405, HeatAPI.getAmbientTemp(5), 1E-12);
        assertEquals(405, HeatAPI.getAmbientTemp(100), 1E-12);
    }
}
