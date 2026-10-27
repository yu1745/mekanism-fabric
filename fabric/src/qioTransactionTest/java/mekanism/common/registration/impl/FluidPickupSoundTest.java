package mekanism.common.registration.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mojang.serialization.Lifecycle;
import net.minecraft.SharedConstants;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.Bootstrap;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class FluidPickupSoundTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void bothFluidVariantsExposeTheOfficialBucketFillSound() {
        FluidDeferredRegister register = new FluidDeferredRegister("sound_test");
        var fluid = register.register("test_fluid", properties -> properties);
        register.registerAll(new MappedRegistry<Fluid>(Registries.FLUID, Lifecycle.stable()),
              new MappedRegistry<Block>(Registries.BLOCK, Lifecycle.stable()),
              new MappedRegistry<Item>(Registries.ITEM, Lifecycle.stable()));
        assertEquals(SoundEvents.BUCKET_FILL, fluid.get().getPickupSound().orElseThrow());
        assertEquals(SoundEvents.BUCKET_FILL, fluid.getFlowingFluid().get().getPickupSound().orElseThrow());
    }
}
