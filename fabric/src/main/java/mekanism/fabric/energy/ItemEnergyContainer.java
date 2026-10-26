package mekanism.fabric.energy;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.energy.IStrictEnergyHandler;
import mekanism.common.attachments.containers.ContainerType;
import mekanism.common.util.UnitDisplayUtils.EnergyUnit;
import mekanism.fabric.item.DetachedItemContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import team.reborn.energy.api.EnergyStorage;

/**
 * One detached machine-slot energy operation. The registered Mekanism handler keeps original strict-Joule priority;
 * external items use their native Team Energy inventory context and the already-verified exact conversion boundary.
 * The caller must install getContainer() after accepting a transfer. Neither lookup nor simulation mutates its input.
 */
public final class ItemEnergyContainer {
    private ItemStack ownedStack;
    private record TransferSnapshot(long energy, ItemStack stack) {
    }
    @Nullable
    private final DetachedItemContext nativeContext;

    private ItemEnergyContainer(ItemStack stack) {
        ownedStack = stack.copy();
        //Do not round Mekanism's own sub-FE Joules or bypass its real count/registration/config provider guard.
        nativeContext = ContainerType.ENERGY.getRegisteredHandler(ownedStack) == null ? new DetachedItemContext(ownedStack) : null;
    }

    @Nullable
    public static ItemEnergyContainer find(ItemStack stack) {
        if (stack.isEmpty()) return null;
        ItemEnergyContainer container = new ItemEnergyContainer(stack);
        return container.handler() == null ? null : container;
    }

    public static boolean hasHandler(ItemStack stack) {
        return find(stack) != null;
    }

    @Nullable
    private IStrictEnergyHandler handler() {
        if (nativeContext == null) {
            return ContainerType.ENERGY.getRegisteredHandler(ownedStack);
        }
        if (!FabricEnergyConversions.isUsable(EnergyUnit.FORGE_ENERGY)) return null;
        //Re-resolve after native replacement; never retain the old item's provider between operations.
        EnergyStorage storage = nativeContext.context().find(EnergyStorage.ITEM);
        return storage == null ? null : new FabricStrictEnergyHandler(storage, EnergyUnit.FORGE_ENERGY);
    }

    public ItemStack getContainer() {
        return nativeContext == null ? ownedStack.copy() : nativeContext.getStack();
    }

    public boolean hasSpace() {
        IStrictEnergyHandler handler = handler();
        if (handler != null) {
            for (int i = 0; i < handler.getEnergyContainerCount(); i++) {
                if (handler.getNeededEnergy(i) > 0) return true;
            }
        }
        return false;
    }

    /**
     * Perform the actual machine-slot transfer. The machine container and any native external storage commit
     * together; item replacement stays detached until the owning slot installs getContainer(). This ordinary
     * machine-tick operation cannot be called inside another transfer, whose later rollback could otherwise
     * outlive the slot installation. No physical-side exposure is forged for this internal machine container.
     */
    public long transfer(IEnergyContainer machine, boolean fromItem) {
        return transfer(machine, fromItem, Long.MAX_VALUE, AutomationType.INTERNAL);
    }

    /** Same owned transfer with the caller's actual per-tick limit and domain automation policy. */
    public long transfer(IEnergyContainer machine, boolean fromItem, long limit, AutomationType automation) {
        if (limit <= 0 || Transaction.getLifecycle() != Transaction.Lifecycle.NONE) return 0;
        SnapshotParticipant<TransferSnapshot> snapshot = new SnapshotParticipant<>() {
            @Override
            protected TransferSnapshot createSnapshot() {
                return new TransferSnapshot(machine.getEnergy(), ownedStack.copy());
            }

            @Override
            protected void readSnapshot(TransferSnapshot state) {
                ownedStack = state.stack();
                machine.setEnergy(state.energy());
                if (machine.getEnergy() != state.energy()) {
                    throw new IllegalStateException("Machine energy container could not restore its item-transfer snapshot");
                }
            }
        };
        long moved;
        try (Transaction transaction = Transaction.openOuter()) {
            if (fromItem) {
                long available = extract(Math.min(limit, machine.getNeeded()), Action.SIMULATE);
                if (available <= 0) return 0;
                long remainder = machine.insert(available, Action.SIMULATE, automation);
                if (remainder < 0 || remainder >= available) return 0;
                moved = available - remainder;
                if (machine.insert(moved, Action.SIMULATE, automation) != 0) return 0;
                //The native strict adapter joins this transaction, so an execute mismatch rolls it back as well.
                snapshot.updateSnapshots(transaction);
                if (extract(moved, Action.EXECUTE) != moved) return 0;
                if (machine.insert(moved, Action.EXECUTE, automation) != 0) return 0;
            } else {
                long available = machine.extract(Math.min(limit, machine.getEnergy()), Action.SIMULATE, automation);
                if (available <= 0) return 0;
                long remainder = insert(available, Action.SIMULATE);
                if (remainder < 0 || remainder >= available) return 0;
                moved = available - remainder;
                if (insert(moved, Action.SIMULATE) != 0 || machine.extract(moved, Action.SIMULATE, automation) != moved) return 0;
                //Do not drain the machine before knowing that the native item accepted the exact quantity.
                snapshot.updateSnapshots(transaction);
                if (insert(moved, Action.EXECUTE) != 0) return 0;
                if (machine.extract(moved, Action.EXECUTE, automation) != moved) return 0;
            }
            transaction.commit();
        }
        return moved;
    }

    /** Returns unaccepted Joules, as in the original strict handler contract. */
    public long insert(long amount, Action action) {
        IStrictEnergyHandler handler = handler();
        return handler == null ? amount : handler.insertEnergy(amount, action);
    }

    public long extract(long amount, Action action) {
        IStrictEnergyHandler handler = handler();
        return handler == null ? 0 : handler.extractEnergy(amount, action);
    }
}
