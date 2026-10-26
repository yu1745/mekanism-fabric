package mekanism.common.capabilities;

import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup;
import net.fabricmc.fabric.api.lookup.v1.item.ItemApiLookup;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Fabric port of a catalog entry whose external identity is an ecosystem-wide Fabric transfer identity
 * ({@code FluidStorage}/{@code ItemStorage}). The block leg is the sided lookup; the item leg uses Fabric's
 * {@link ContainerItemContext} context type, and the {@link #getCapability(ItemStack)} convenience queries through a
 * constant single-stack context exactly like the other Fabric transfer consumers in this port. Read-only checks
 * ({@link #hasCapability(ItemStack)}) therefore observe the same provider set as operational callers.
 */
public record ExternalStorageCapability<STORAGE>(BlockApiLookup<STORAGE, Direction> block, ItemApiLookup<STORAGE, ContainerItemContext> item)
      implements IMultiTypeCapability<STORAGE> {

    @Override
    @Nullable
    public STORAGE getCapability(ItemStack stack) {
        return stack.isEmpty() ? null : item.find(stack, ContainerItemContext.withConstant(stack));
    }
}
