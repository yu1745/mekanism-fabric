package mekanism.fabric.fluid;

import java.util.ArrayList;
import java.util.List;
import mekanism.api.Action;
import mekanism.api.fluid.FluidStack;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.item.base.SingleItemStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * A detached item operation used by Mekanism fluid slots. Native providers own all item replacement and transaction
 * semantics. The real inventory slot must explicitly install {@link #getContainer()} after accepting the operation;
 * lookup, simulation, and unsuccessful output-slot movement never mutate the live input ItemStack.
 */
public final class ItemFluidContainer {
    public record Tank(FluidStack fluid, long capacity) {
    }

    private final SingleItemStorage slot;
    private final ContainerItemContext context;

    private ItemFluidContainer(ItemStack stack) {
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

    @Nullable
    public static ItemFluidContainer find(ItemStack stack) {
        if (stack.isEmpty()) return null;
        ItemFluidContainer container = new ItemFluidContainer(stack);
        return container.storage() == null ? null : container;
    }

    @Nullable
    private Storage<FluidVariant> storage() {
        return context.find(FluidStorage.ITEM);
    }

    public ItemStack getContainer() {
        return slot.variant.toStack((int) slot.amount);
    }

    /** Amounts are capped to the domain's int range and floored only for a read-only availability snapshot. */
    public List<Tank> tanks() {
        List<Tank> tanks = new ArrayList<>();
        Storage<FluidVariant> storage = storage();
        if (storage != null) {
            for (var view : storage) {
                long whole = Math.min(Integer.MAX_VALUE, Math.max(0, view.getAmount()) / 81);
                tanks.add(new Tank(FabricFluidConversions.toFluidStack(view.getResource(), whole * 81), Math.max(0, view.getCapacity()) / 81));
            }
        }
        return tanks;
    }

    public int insert(FluidStack fluid, Action action) {
        return transfer(fluid, action, true);
    }

    public FluidStack extract(FluidStack fluid, Action action) {
        return fluid.copyWithAmount(transfer(fluid, action, false));
    }

    public FluidStack extract(int maxAmount, Action action) {
        if (maxAmount <= 0) return FluidStack.EMPTY;
        for (Tank tank : tanks()) {
            if (!tank.fluid().isEmpty()) {
                FluidStack extracted = extract(tank.fluid().copyWithAmount(maxAmount), action);
                if (!extracted.isEmpty()) return extracted;
            }
        }
        return FluidStack.EMPTY;
    }

    private int transfer(FluidStack fluid, Action action, boolean insert) {
        if (fluid.isEmpty()) return 0;
        Storage<FluidVariant> storage = storage();
        if (storage == null) return 0;
        FluidVariant resource = FabricFluidConversions.toFabricVariant(fluid);
        long requested = FabricFluidConversions.millibucketsToTransferUnits(fluid.getAmount());
        // Probe inside a nested transaction. If a native provider accepts sub-mB droplets, negotiate a whole-mB
        // request and require its exact execution; never round a committed native mutation.
        try (Transaction transaction = Transaction.openOuter()) {
            long accepted;
            try (Transaction probe = transaction.openNested()) {
                accepted = insert ? storage.insert(resource, requested, probe) : storage.extract(resource, requested, probe);
            }
            if (accepted <= 0 || accepted > requested) return 0;
            long whole = accepted - accepted % 81;
            if (whole == 0) return 0;
            long moved = insert ? storage.insert(resource, whole, transaction) : storage.extract(resource, whole, transaction);
            if (moved != whole) return 0;
            if (action.execute()) transaction.commit();
            return FabricFluidConversions.transferUnitsToMillibucketsExact(whole);
        }
    }
}
