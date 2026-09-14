package mekanism.common.lib.inventory;

import mekanism.api.annotations.NothingNullByDefault;
import mekanism.common.content.transporter.TransporterPathfinder.Destination;
import mekanism.common.lib.SidedBlockPos;
import mekanism.common.lib.inventory.TransitRequest.TransitResponse;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

@NothingNullByDefault
public interface IAdvancedTransportEjector {

    @Nullable
    SidedBlockPos getRoundRobinTarget();

    void setRoundRobinTarget(@Nullable SidedBlockPos target);

    default void setRoundRobinTarget(Destination destination) {
        setRoundRobinTarget(SidedBlockPos.get(destination));
    }

    boolean getRoundRobin();

    void toggleRoundRobin();

    boolean canSendHome(ItemStack stack);

    TransitResponse sendHome(TransitRequest request);

    /**
     * Transaction-aware sendHome (approved platform delta): stages the home insertion into the caller transaction so
     * it participates in the existing-flight caller transaction. The default implementation falls back to the legacy
     * immediate behavior; real owners override it.
     */
    default TransitResponse sendHome(TransitRequest request, Transaction tx) {
        return sendHome(request);
    }
}
