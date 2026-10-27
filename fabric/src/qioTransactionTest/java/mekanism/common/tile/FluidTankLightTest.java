package mekanism.common.tile;

import static org.junit.jupiter.api.Assertions.assertEquals;

import mekanism.api.fluid.FluidStack;
import com.mojang.serialization.Lifecycle;
import mekanism.common.block.basic.BlockFluidTank;
import mekanism.common.registration.impl.FluidDeferredRegister;
import mekanism.common.registries.MekanismBlockTypes;
import net.minecraft.SharedConstants;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class FluidTankLightTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void fluidLightFeedsTheActualVanillaBlockState() {
        BlockFluidTank block = new BlockFluidTank(MekanismBlockTypes.BASIC_FLUID_TANK);
        assertEquals(0, BlockFluidTank.fluidLight(FluidStack.EMPTY));
        assertEquals(0, BlockFluidTank.fluidLight(new FluidStack(Fluids.WATER, 1)));
        assertEquals(15, BlockFluidTank.fluidLight(new FluidStack(Fluids.LAVA, 1)));
        FluidDeferredRegister register = new FluidDeferredRegister("light_test");
        var custom = register.register("luminous", properties -> properties.lightLevel(9), properties -> properties);
        register.registerAll(new MappedRegistry<Fluid>(Registries.FLUID, Lifecycle.stable()),
              new MappedRegistry<Block>(Registries.BLOCK, Lifecycle.stable()),
              new MappedRegistry<Item>(Registries.ITEM, Lifecycle.stable()));
        assertEquals(9, BlockFluidTank.fluidLight(custom.asStack(1)));
        assertEquals(0, block.defaultBlockState().getLightEmission());
        assertEquals(15, block.defaultBlockState().setValue(BlockFluidTank.FLUID_LIGHT, 15).getLightEmission());
        assertEquals(7, block.defaultBlockState().setValue(BlockFluidTank.FLUID_LIGHT, 7).getLightEmission());
    }
}
