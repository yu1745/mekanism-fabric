package mekanism.common.capabilities.proxy;

import mekanism.api.Action;
import mekanism.api.annotations.NothingNullByDefault;
import mekanism.api.inventory.ISidedItemHandler;
import mekanism.common.capabilities.holder.IHolder;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Fabric port: implements the admitted {@link ISidedItemHandler} domain surface (there is no {@code IExtendedItemHandler} in this port). Every sided method
 * delegates to the wrapped handler using the proxy's bound side, and {@link #getInventorySideFor()} returns that bound side so the unsided default methods
 * also observe the same side and read-only gating instead of the base handler's own default side.
 */
@NothingNullByDefault
public class ProxyItemHandler extends ProxyHandler implements ISidedItemHandler {

    private final ISidedItemHandler inventory;

    public ProxyItemHandler(ISidedItemHandler inventory, @Nullable Direction side, @Nullable IHolder holder) {
        super(side, holder);
        this.inventory = inventory;
    }

    @Nullable
    @Override
    public Direction getInventorySideFor() {
        return side;
    }

    @Override
    public int getSlots(@Nullable Direction side) {
        return inventory.getSlots(this.side);
    }

    @Override
    public ItemStack getStackInSlot(int slot, @Nullable Direction side) {
        return inventory.getStackInSlot(slot, this.side);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, @Nullable Direction side, Action action) {
        return readOnlyInsert() ? stack : inventory.insertItem(slot, stack, this.side, action);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, @Nullable Direction side, Action action) {
        return readOnlyExtract() ? ItemStack.EMPTY : inventory.extractItem(slot, amount, this.side, action);
    }

    @Override
    public int getSlotLimit(int slot, @Nullable Direction side) {
        return inventory.getSlotLimit(slot, this.side);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack, @Nullable Direction side) {
        return !readOnly || inventory.isItemValid(slot, stack, this.side);
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack, @Nullable Direction side) {
        if (!readOnly) {
            inventory.setStackInSlot(slot, stack, this.side);
        }
    }
}
