package mekanism.client.gui.item;

import static org.junit.jupiter.api.Assertions.assertSame;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.Test;

class SeismicReaderFluidClientTest {
    @Test
    void sourceAndFlowingVariantsShareTheSameFrequencyKey() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        assertSame(Fluids.WATER, GuiSeismicReader.sourceFluid(Fluids.WATER));
        assertSame(Fluids.WATER, GuiSeismicReader.sourceFluid(Fluids.FLOWING_WATER));
        assertSame(Fluids.LAVA, GuiSeismicReader.sourceFluid(Fluids.LAVA));
        assertSame(Fluids.LAVA, GuiSeismicReader.sourceFluid(Fluids.FLOWING_LAVA));
        assertSame(Fluids.EMPTY, GuiSeismicReader.sourceFluid(Fluids.EMPTY));
    }
}
