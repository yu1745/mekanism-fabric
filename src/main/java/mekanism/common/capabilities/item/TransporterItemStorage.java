package mekanism.common.capabilities.item;

import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import java.util.Collections;
import java.util.Iterator;
import java.util.function.LongSupplier;
import mekanism.common.content.network.transmitter.LogisticalTransporterBase;
import mekanism.common.lib.inventory.TransitRequest;
import mekanism.common.lib.inventory.TransitRequest.TransitResponse;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import org.jetbrains.annotations.NotNull;

/**
 * Insert-only real Fabric {@link Storage} endpoint of a logistical transporter for one physical adjacent source
 * position/side (Task052 continuation09). Replaces the NeoForge {@code CursedTransporterItemHandler}: there are no
 * fake nine integer slots, no per-instance stack identity tracking, and no synthetic slotted framework - the endpoint
 * exposes exactly one operation, insertion, routed through the transporter's own transaction-aware routing.
 *
 * <p>Offers are capped at the tier's pull amount. Per-tick rejection memory preserves the official behavior of not
 * retrying an offer that was already fully rejected earlier in the same tick: the highest fully-rejected offer amount
 * per variant is remembered until the game tick changes, so a repeat or smaller re-offer short-circuits to zero while
 * a larger one is still routed. Successful amounts clear that memory, matching the official "invalidate simulations
 * once an insertion actually happened" behavior without retaining any simulated per-slot flowing state.
 *
 * <p>All routing (path calculation, transit reservation, packet/save) is staged into the caller's transaction by the
 * transporter; this endpoint never mutates transporter state outside a transaction.
 */
public final class TransporterItemStorage implements Storage<ItemVariant> {

    private final LogisticalTransporterBase transporter;
    private final LongSupplier currentTickSupplier;
    private final long fromPos;
    private final Object2LongMap<ItemVariant> rejectedThisTick = new Object2LongLinkedOpenHashMap<>();
    private long lastTick = -1;

    public TransporterItemStorage(LogisticalTransporterBase transporter, long fromPos, LongSupplier currentTickSupplier) {
        this.transporter = transporter;
        this.fromPos = fromPos;
        this.currentTickSupplier = currentTickSupplier;
    }

    public LogisticalTransporterBase getTransporter() {
        return transporter;
    }

    @Override
    public boolean supportsInsertion() {
        return true;
    }

    @Override
    public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
        if (resource.isBlank() || maxAmount <= 0 || !transporter.hasTransmitterNetwork()) {
            return 0;
        }
        long currentTick = currentTickSupplier.getAsLong();
        if (currentTick != lastTick) {
            rejectedThisTick.clear();
            lastTick = currentTick;
        }
        //Don't retry an offer no larger than one that was already fully rejected earlier this tick
        if (rejectedThisTick.getOrDefault(resource, Long.MIN_VALUE) >= maxAmount) {
            return 0;
        }
        long limit = transporter.tier.getPullAmount();
        long offered = Math.min(maxAmount, limit);
        if (offered <= 0) {
            return 0;
        }
        TransitRequest request = TransitRequest.simple(resource.toStack((int) Math.min(Integer.MAX_VALUE, offered)));
        TransitResponse response = transporter.insertUnchecked(fromPos, request, transporter.getColor(), true, 1, transaction);
        if (response.isEmpty()) {
            rejectedThisTick.put(resource, maxAmount);
            return 0;
        }
        long accepted = response.getSendingAmount();
        if (accepted < offered) {
            //Remember the rejected remainder so a same-or-smaller re-offer short-circuits this tick
            rejectedThisTick.put(resource, Math.max(rejectedThisTick.getOrDefault(resource, 0), offered - accepted));
        } else {
            //A successful insert invalidates any earlier rejection memory for this variant
            rejectedThisTick.remove(resource);
        }
        return accepted;
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
