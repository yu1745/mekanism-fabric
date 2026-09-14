package mekanism.fabric.item;

import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.item.base.SingleItemStorage;
import net.minecraft.world.item.ItemStack;

/**
 * A detached single-slot inventory for machine-owned item operations. Native providers may replace the item under
 * their transaction. Only the actual machine slot can install the returned stack after accepting the operation.
 */
public final class DetachedItemContext {
    private final SingleItemStorage slot;
    private final ContainerItemContext context;

    public DetachedItemContext(ItemStack stack) {
        slot = new SingleItemStorage() {
            @Override
            protected long getCapacity(ItemVariant variant) {
                return variant.isBlank() ? 64 : variant.toStack().getMaxStackSize();
            }
        };
        slot.variant = ItemVariant.of(stack);
        slot.amount = stack.getCount();
        context = ContainerItemContext.ofSingleSlot(slot);
    }

    public ContainerItemContext context() {
        return context;
    }

    public ItemStack getStack() {
        return slot.variant.toStack(Math.toIntExact(slot.amount));
    }
}
