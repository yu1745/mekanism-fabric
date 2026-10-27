package mekanism.fabric.client.render.item;

import java.util.ArrayList;
import java.util.List;
import mekanism.common.registration.impl.FluidDeferredRegister;
import mekanism.common.registration.impl.FluidDeferredRegister.MekanismFlowingFluid;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.minecraft.client.color.item.ItemColor;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The official {@code DynamicFluidContainerModel.Colors} item color (tint index 1 = the contained fluid's tint) bound
 * to every chemical bucket the fluid register produced.
 */
public final class FabricFluidBucketRegistration {

    private static final Logger LOGGER = LoggerFactory.getLogger("Mekanism/FabricFluidBuckets");

    private FabricFluidBucketRegistration() {
    }

    public static void register(mekanism.common.registration.impl.FluidDeferredRegister fluids) {
        List<net.minecraft.world.item.Item> buckets = new ArrayList<>();
        for (var holder : fluids.getBucketEntries()) {
            buckets.add(holder.value());
        }
        ItemColor colors = (stack, tintIndex) -> {
            if (tintIndex != 1) {
                return 0xFFFFFFFF;
            }
            return tintOf(stack);
        };
        ColorProviderRegistry.ITEM.register(colors, buckets.toArray(net.minecraft.world.item.Item[]::new));
        LOGGER.info("MEKANISM_FABRIC_FLUID_BUCKET_PROOF buckets={}", buckets.size());
    }

    private static int tintOf(ItemStack stack) {
        if (stack.getItem() instanceof BucketItem bucket && bucket.content instanceof MekanismFlowingFluid fluid) {
            return fluid.getMekanismFluidType().color;
        }
        return 0xFFFFFFFF;
    }
}
