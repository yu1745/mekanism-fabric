package mekanism.common.capabilities.item;

import java.util.Collections;
import java.util.Iterator;
import mekanism.common.content.network.transmitter.LogisticalTransporterBase;
import mekanism.common.lib.inventory.TransitRequest;
import mekanism.common.lib.inventory.TransitRequest.TransitResponse;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.NotNull;

/**
 * Insert-only real Fabric {@link Storage} endpoint of a logistical transporter for one physical adjacent source
 * position/side. Replaces the NeoForge {@code CursedTransporterItemHandler}:
 * there are no fake nine integer slots, no per-instance stack identity tracking and no synthetic slotted framework -
 * the endpoint exposes exactly one operation, insertion, routed through the transporter's own <b>checked</b>
 * transaction-aware routing (current side/canReceiveFrom/physical-source/color/connection validity is re-validated
 * on every actual operation by that path, so a retained endpoint alias can never bypass it).
 *
 * <p>There is deliberately no per-tick rejection memory and no tier pull cap here: speculative insertion behavior is
 * defined entirely by the transaction-visible routing state (transaction-visible real reservations), and arbitrary
 * external insertion is not throttled by the pull tier.
 *
 * <p>All routing (path calculation, transit reservation, packet/save) is staged into the caller's transaction by the
 * transporter; this endpoint never mutates transporter state outside a transaction.
 */
public final class TransporterItemStorage implements Storage<ItemVariant> {

    private final LogisticalTransporterBase transporter;
    private final BlockPos fromPos;
    private boolean valid = true;

    public TransporterItemStorage(LogisticalTransporterBase transporter, BlockPos fromPos) {
        this.transporter = transporter;
        this.fromPos = fromPos;
    }

    public void invalidate() {
        valid = false;
    }

    public boolean isValid() {
        return valid;
    }

    public LogisticalTransporterBase getTransporter() {
        return transporter;
    }

    @Override
    public boolean supportsInsertion() {
        return valid;
    }

    @Override
    public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
        if (!valid || resource.isBlank() || maxAmount <= 0 || !transporter.hasTransmitterNetwork()) {
            return 0;
        }
        TransitRequest request = TransitRequest.simple(resource.toStack((int) Math.min(Integer.MAX_VALUE, maxAmount)));
        //Checked insert: re-validates the current side (canReceiveFrom/connection), the physical adjacent source
        //position and the strict color policy on every actual operation, and stages routing into the caller tx
        TransitResponse response = transporter.insert(null, fromPos, request, transporter.getColor(), true, 1, transaction);
        return response.isEmpty() ? 0 : response.getSendingAmount();
    }

    @Override
    public boolean supportsExtraction() {
        return false;
    }

    @Override
    public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
        return 0;
    }

    @NotNull
    @Override
    public Iterator<StorageView<ItemVariant>> iterator() {
        return Collections.emptyIterator();
    }
}
