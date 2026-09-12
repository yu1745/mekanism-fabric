package mekanism.common.block.states;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BlockStateDescriptorTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void fluidLogTypeDefaultEmptinessTracksTheLiveExactFluidIdentity() {
        Fluid[] current = {Fluids.EMPTY};
        IFluidLogType mutable = () -> current[0];

        assertTrue(mutable.isEmpty());
        current[0] = Fluids.WATER;
        assertFalse(mutable.isEmpty());
        current[0] = Fluids.LAVA;
        assertFalse(mutable.isEmpty());
        current[0] = Fluids.EMPTY;
        assertTrue(mutable.isEmpty());
    }

    @Test
    void fluidLogTypeHasExactOrderNamesFluidsAndEnumConsistency() {
        FluidLogType[] values = FluidLogType.values();
        assertArrayEquals(new FluidLogType[]{FluidLogType.EMPTY, FluidLogType.WATER, FluidLogType.LAVA}, values);
        assertArrayEquals(new String[]{"empty", "water", "lava"},
              Arrays.stream(values).map(FluidLogType::getSerializedName).toArray(String[]::new));
        assertSame(Fluids.EMPTY, FluidLogType.EMPTY.getFluid());
        assertSame(Fluids.WATER, FluidLogType.WATER.getFluid());
        assertSame(Fluids.LAVA, FluidLogType.LAVA.getFluid());
        assertTrue(FluidLogType.EMPTY.isEmpty());
        assertFalse(FluidLogType.WATER.isEmpty());
        assertFalse(FluidLogType.LAVA.isEmpty());
        for (FluidLogType value : values) {
            assertSame(value, FluidLogType.valueOf(value.name()));
        }
    }

    @Test
    void transmitterTypeHasExactOrderSizesCentersAndEnumConsistency() {
        TransmitterType[] values = TransmitterType.values();
        assertArrayEquals(new TransmitterType[]{
              TransmitterType.UNIVERSAL_CABLE,
              TransmitterType.MECHANICAL_PIPE,
              TransmitterType.PRESSURIZED_TUBE,
              TransmitterType.LOGISTICAL_TRANSPORTER,
              TransmitterType.RESTRICTIVE_TRANSPORTER,
              TransmitterType.DIVERSION_TRANSPORTER,
              TransmitterType.THERMODYNAMIC_CONDUCTOR
        }, values);
        assertArrayEquals(new TransmitterType.Size[]{
              TransmitterType.Size.SMALL,
              TransmitterType.Size.LARGE,
              TransmitterType.Size.SMALL,
              TransmitterType.Size.LARGE,
              TransmitterType.Size.LARGE,
              TransmitterType.Size.LARGE,
              TransmitterType.Size.SMALL
        }, Arrays.stream(values).map(TransmitterType::getSize).toArray(TransmitterType.Size[]::new));
        assertEquals(6, TransmitterType.Size.SMALL.centerSize);
        assertEquals(8, TransmitterType.Size.LARGE.centerSize);
        assertArrayEquals(new TransmitterType.Size[]{TransmitterType.Size.SMALL, TransmitterType.Size.LARGE},
              TransmitterType.Size.values());
        for (TransmitterType value : values) {
            assertSame(value, TransmitterType.valueOf(value.name()));
        }
        for (TransmitterType.Size value : TransmitterType.Size.values()) {
            assertSame(value, TransmitterType.Size.valueOf(value.name()));
        }
    }
}
