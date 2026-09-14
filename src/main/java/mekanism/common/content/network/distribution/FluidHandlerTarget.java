package mekanism.common.content.network.distribution;

import java.util.Collection;
import mekanism.api.fluid.FluidStack;
import mekanism.common.lib.distribution.SplitInfo;
import mekanism.common.lib.distribution.Target;
import mekanism.fabric.fluid.FabricFluidConversions;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import org.jetbrains.annotations.NotNull;

/**
 * Fabric-side fluid distribution target over real {@link Storage Storage&lt;FluidVariant&gt;} acceptors, operating in whole millibuckets inside one sender-owned
 * transaction.
 *
 * <p>The enclosing transaction is supplied by the sending owner (see {@link FluidSendTransaction}) and spans distribution and source debit. Simulations run in aborted
 * nested transactions, so shared destination capacity is observed from the enclosing transaction across retries; successful executions commit their nested transaction
 * into the still-open enclosing send transaction and only become permanent when the sender commits after debiting its source.
 *
 * <p>Amounts given to this target are whole millibuckets. Fabric droplet responses are converted back with {@link FabricFluidConversions#TRANSFER_UNITS_PER_MILLIBUCKET};
 * a storage that reports a fractional (non-whole-millibucket) accepted amount never causes a fractional destination commit: the probe transaction is aborted and retried at
 * the whole-millibucket floor of the reported amount, accepting only an exact whole-millibucket response (bounded retry; otherwise nothing is committed for that handler).
 */
public class FluidHandlerTarget extends Target<Storage<FluidVariant>, @NotNull FluidStack> {

    private final TransactionContext enclosing;

    public FluidHandlerTarget(TransactionContext enclosing, Collection<Storage<FluidVariant>> allHandlers) {
        super(allHandlers);
        this.enclosing = enclosing;
    }

    public FluidHandlerTarget(TransactionContext enclosing, int expectedSize) {
        super(expectedSize);
        this.enclosing = enclosing;
    }

    @Override
    protected void acceptAmount(Storage<FluidVariant> handler, SplitInfo splitInfo, @NotNull FluidStack resource, long amount) {
        splitInfo.send(insertWholeMillibuckets(handler, resource, amount, enclosing));
    }

    @Override
    protected long simulate(Storage<FluidVariant> handler, @NotNull FluidStack resource, long amount) {
        return simulateMillibuckets(handler, resource, amount, enclosing);
    }

    /**
     * Simulates inserting up to {@code millibuckets} by probing in a nested transaction that is always aborted, so enclosing-transaction capacity of shared destinations is
     * observed without committing anything.
     *
     * @return the whole millibuckets the handler would accept (floored), or {@code 0} for an invalid response.
     */
    public static long simulateMillibuckets(Storage<FluidVariant> handler, @NotNull FluidStack resource, long millibuckets, TransactionContext enclosing) {
        long request = dropletRequest(millibuckets);
        if (request <= 0) {
            return 0;
        }
        FluidVariant variant = FabricFluidConversions.toFabricVariant(resource);
        try (Transaction probe = Transaction.openNested(enclosing)) {
            long reported = handler.insert(variant, request, probe);
            //Aborted on close; nothing is committed by a simulation
            return reported >= 0 && reported <= request ? reported / FabricFluidConversions.TRANSFER_UNITS_PER_MILLIBUCKET : 0;
        }
    }

    /**
     * Inserts exactly {@code millibuckets} (at most) as a nested transaction of {@code enclosing}, committing only an exact whole-millibucket result.
     *
     * @return the accepted whole millibuckets, or {@code 0} when the storage cannot accept an exact whole-millibucket amount or returned an invalid response.
     */
    public static long insertWholeMillibuckets(Storage<FluidVariant> handler, @NotNull FluidStack resource, long millibuckets, TransactionContext enclosing) {
        long request = dropletRequest(millibuckets);
        if (request <= 0) {
            return 0;
        }
        FluidVariant variant = FabricFluidConversions.toFabricVariant(resource);
        //Probe in an abortable child transaction first
        long reported;
        try (Transaction probe = Transaction.openNested(enclosing)) {
            reported = handler.insert(variant, request, probe);
            if (reported < 0 || reported > request) {
                //Invalid response, reject entirely
                return 0;
            }
            if (reported == request) {
                //Exact whole-millibucket acceptance of the full request
                probe.commit();
                return millibuckets;
            }
            //Fractional or partial: abort the probe and fall through to the bounded floor retry
        }
        long floorMillibuckets = reported / FabricFluidConversions.TRANSFER_UNITS_PER_MILLIBUCKET;
        while (floorMillibuckets >= 1) {
            long retryRequest = floorMillibuckets * FabricFluidConversions.TRANSFER_UNITS_PER_MILLIBUCKET;
            try (Transaction nested = Transaction.openNested(enclosing)) {
                long accepted = handler.insert(variant, retryRequest, nested);
                if (accepted < 0 || accepted > retryRequest) {
                    return 0;
                }
                if (accepted == retryRequest) {
                    //Exact whole-millibucket acceptance; commit into the enclosing send transaction
                    nested.commit();
                    return floorMillibuckets;
                }
                long nextFloor = accepted / FabricFluidConversions.TRANSFER_UNITS_PER_MILLIBUCKET;
                if (nextFloor >= floorMillibuckets) {
                    //No progress toward an exact whole-millibucket amount; stop instead of retrying unboundedly
                    return 0;
                }
                floorMillibuckets = nextFloor;
            }
        }
        return 0;
    }

    private static long dropletRequest(long millibuckets) {
        if (millibuckets <= 0) {
            return 0;
        }
        long request = millibuckets * FabricFluidConversions.TRANSFER_UNITS_PER_MILLIBUCKET;
        return request > FabricFluidConversions.MAX_EXACT_TRANSFER_UNITS ? 0 : request;
    }
}
