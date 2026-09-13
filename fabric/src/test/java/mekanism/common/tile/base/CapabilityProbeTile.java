package mekanism.common.tile.base;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import mekanism.api.Action;
import mekanism.api.inventory.IInventorySlot;
import mekanism.api.inventory.IMekanismInventory;
import mekanism.common.capabilities.holder.slot.IInventorySlotHolder;
import mekanism.common.capabilities.resolver.manager.ItemHandlerManager;
import mekanism.common.inventory.slot.BasicInventorySlot;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Focused-test instrumentation subclass of the real capability base tile: a real {@link ItemHandlerManager} over real inventory slots, bound through the real
 * {@link CapabilityTileEntity#addCapabilityResolvers} constructor path. It is never registered production machine content; any block entity type token works
 * because the base lifecycle under test never validates the block state.
 */
class CapabilityProbeTile extends CapabilityTileEntity implements IMekanismInventory, IInventorySlotHolder {

    static final BlockState STATE = Blocks.FURNACE.defaultBlockState();

    private final List<IInventorySlot> slots = List.of(BasicInventorySlot.at(null, 0, 0), BasicInventorySlot.at(null, 1, 1));
    private final Set<Direction> exposedSides = EnumSet.of(Direction.NORTH, Direction.UP);

    CapabilityProbeTile(Holder<? extends BlockEntityType<?>> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        addCapabilityResolvers(List.of(new ItemHandlerManager(this, this)));
    }

    static Holder<? extends BlockEntityType<?>> furnaceType() {
        return Holder.direct(BlockEntityType.FURNACE);
    }

    /** Binds the real-component-style disabled-side policy: the item identity is disabled on {@code NORTH}, nullable sides never are. */
    void bindItemNorthDisabledPolicy() {
        addConfigComponent((capability, side) -> capability == ItemStorage.SIDED && side == Direction.NORTH);
    }

    @Override
    public boolean hasInventory() {
        return true;
    }

    @Override
    public List<IInventorySlot> getInventorySlots(@Nullable Direction side) {
        //Internal null-side access sees everything, matching real tile semantics
        return side == null || exposedSides.contains(side) ? slots : List.of();
    }

    @Override
    public void onContentsChanged() {
    }

    //The remaining ISidedItemHandler surface backs the sided domain handlers; the probe keeps it inert
    @Override
    public void setStackInSlot(int slot, ItemStack stack, @Nullable Direction side) {
    }

    @Override
    public int getSlots(@Nullable Direction side) {
        return getInventorySlots(side).size();
    }

    @Override
    public ItemStack getStackInSlot(int slot, @Nullable Direction side) {
        return getInventorySlots(side).get(slot).getStack();
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, @Nullable Direction side, Action action) {
        return stack;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, @Nullable Direction side, Action action) {
        return ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(int slot, @Nullable Direction side) {
        return getInventorySlots(side).get(slot).getLimit(ItemStack.EMPTY);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack, @Nullable Direction side) {
        return true;
    }
}
