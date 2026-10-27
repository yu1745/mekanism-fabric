package mekanism.common.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import mekanism.fabric.fluid.ItemFluidContainer;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class FluidTankInteractionEligibilityTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void interactionChecksTheOriginalStackBeforeCopyingOneItem() {
        ItemStack bucket = new ItemStack(Items.BUCKET);
        var realBucketStorage = FluidStorage.ITEM.find(bucket, ContainerItemContext.withConstant(bucket));
        assertNotNull(realBucketStorage);
        assertNotNull(ItemFluidContainer.find(new ItemStack(Items.BUCKET, 2)));
        Item singleOnly = new Item(new Item.Properties());
        FluidStorage.ITEM.registerForItems((stack, context) -> context.getAmount() == 1 ? realBucketStorage : null, singleOnly);
        ItemStack stack = new ItemStack(singleOnly, 2);
        assertNull(ItemFluidContainer.find(stack));
        assertNotNull(ItemFluidContainer.find(stack.copyWithCount(1)));
        //The original stack is ineligible, so the handler must return before touching player or tank.
        assertFalse(FluidUtils.handleTankInteraction(null, null, stack, null));
    }
}
