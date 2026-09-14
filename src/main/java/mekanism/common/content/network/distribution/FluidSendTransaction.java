package mekanism.common.content.network.distribution;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.fluid.FluidStack;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.common.util.EmitUtils;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import org.jetbrains.annotations.NotNull;

/**
 * Permanent owner of Mekanism's outward fluid sends over real Fabric {@link Storage Storage&lt;FluidVariant&gt;} acceptors.
 *
 * <p>The sender owns exactly one outer transaction spanning fair distribution AND the source debit. The actual source tank is snapshotted with a Mekanism-specific
 * {@link SnapshotParticipant} BEFORE any target execution; after distribution the source is verified unchanged (including alias/self-target reentrancy), the exact INTERNAL
 * extraction is executed inside the transaction, and its returned fluid, components, amount and resulting contents are verified before the outer commit. Any mismatch or
 * exception aborts both the destinations and the source rollback; destinations are never committed without the source debit having succeeded inside the same transaction,
 * and the source is never debited again after return.
 *
 * <p>Immediate listener/dirty effects of the internal extraction are not rollbackable (matching the accepted {@link mekanism.fabric.fluid.FabricFluidStorage} limitation);
 * this class does not claim listener isolation.
 *
 * <p>Distribution preserves the accepted {@link Target}/{@link EmitUtils} integer-millibucket fairness algorithm; destination capacity is observed through the enclosing
 * transaction, so shared destinations see consistent state across simulations and retries. Storages that cannot accept an exact whole-millibucket amount may make no
 * progress by design; no fractional destination commit ever occurs.
 */
import org.jetbrains.annotations.Nullable;

public final class FluidSendTransaction {

    private FluidSendTransaction() {
    }

    /**
     * Source-owned transactional send: snapshots {@code source}, distributes up to {@code maxOutput} millibuckets of its contents fairly among {@code handlers}, debits the
     * sent amount from the source, and commits everything atomically.
     *
     * @return the millibuckets actually sent and debited (0 when nothing could be sent, in which case neither source nor destinations are durably modified).
     */
    public static int send(Collection<Storage<FluidVariant>> handlers, IExtendedFluidTank source, int maxOutput) {
        Objects.requireNonNull(source, "source");
        if (handlers.isEmpty() || source.isEmpty() || maxOutput <= 0) {
            return 0;
        }
        FluidStack offered = source.extract(maxOutput, Action.SIMULATE, AutomationType.INTERNAL);
        if (offered.isEmpty()) {
            return 0;
        }
        try (Transaction outer = Transaction.openOuter()) {
            //Snapshot the actual source contents before any target execution
            SourceTankSnapshot sourceSnapshot = new SourceTankSnapshot(source);
            sourceSnapshot.updateSnapshots(outer);
            FluidHandlerTarget target = collectAcceptors(handlers, offered, outer);
            if (target == null) {
                //Nothing accepts this fluid; abort (no destination commit, no debit)
                return 0;
            }
            int sent = EmitUtils.sendToAcceptors(target, offered.getAmount(), offered);
            if (sent <= 0) {
                return 0;
            }
            //Verify the source was not unexpectedly altered (alias/self-target reentrancy or third-party mutation) before debiting
            if (!FluidStack.matches(sourceSnapshot.getSnapshot(), copyOf(source))) {
                return 0;
            }
            FluidStack taken = source.extract(sent, Action.EXECUTE, AutomationType.INTERNAL);
            if (taken.getAmount() != sent || !FluidStack.isSameFluidSameComponents(taken, offered)) {
                //Never commit destinations when the debit does not exactly match the distribution
                return 0;
            }
            FluidStack before = sourceSnapshot.getSnapshot();
            int remaining = before.getAmount() - taken.getAmount();
            FluidStack expectedAfter = remaining == 0 ? FluidStack.EMPTY : before.copyWithAmount(remaining);
            if (!FluidStack.matches(expectedAfter, copyOf(source))) {
                return 0;
            }
            outer.commit();
            return sent;
        }
    }

    /**
     * Caller-owned offered-stack send: distributes {@code toSend} fairly among {@code handlers} inside one committed transaction and returns the millibuckets accepted.
     *
     * <p>Explicit ownership distinction: a caller-owned offered stack has no tank this class could snapshot or debit; the caller remains responsible for consuming the
     * returned amount from its actual source. There is deliberately no source snapshot or reentrancy verification here.
     */
    public static int send(Collection<Storage<FluidVariant>> handlers, @NotNull FluidStack toSend) {
        Objects.requireNonNull(toSend, "toSend");
        if (handlers.isEmpty() || toSend.isEmpty()) {
            return 0;
        }
        try (Transaction outer = Transaction.openOuter()) {
            FluidHandlerTarget target = collectAcceptors(handlers, toSend, outer);
            if (target == null) {
                return 0;
            }
            return EmitUtils.sendToAcceptors(target, toSend.getAmount(), toSend);
        }
    }

    @Nullable
    private static FluidHandlerTarget collectAcceptors(Collection<Storage<FluidVariant>> handlers, @NotNull FluidStack toSend, TransactionContext outer) {
        List<Storage<FluidVariant>> acceptors = null;
        for (Storage<FluidVariant> handler : handlers) {
            if (handler != null && FluidHandlerTarget.simulateMillibuckets(handler, toSend, toSend.getAmount(), outer) > 0) {
                if (acceptors == null) {
                    acceptors = new ArrayList<>(handlers.size());
                }
                acceptors.add(handler);
            }
        }
        return acceptors == null ? null : new FluidHandlerTarget(outer, acceptors);
    }

    private static FluidStack copyOf(IExtendedFluidTank tank) {
        FluidStack stack = tank.getFluid();
        return stack == null || stack.isEmpty() ? FluidStack.EMPTY : stack.copy();
    }

    /**
     * Mekanism-specific single-tank snapshot participant: rollback restores defensive content with {@code setStackUnchecked}, exactly as the accepted
     * {@link mekanism.fabric.fluid.FabricFluidStorage} restores captured tanks. It deliberately does not duplicate the side-view exposure/lifecycle machinery; it only
     * snapshots one concrete source tank inside the send transaction.
     */
    private static final class SourceTankSnapshot extends SnapshotParticipant<FluidStack> {

        private final IExtendedFluidTank source;
        private FluidStack snapshot = FluidStack.EMPTY;

        private SourceTankSnapshot(IExtendedFluidTank source) {
            this.source = source;
        }

        FluidStack getSnapshot() {
            return snapshot;
        }

        @Override
        protected FluidStack createSnapshot() {
            FluidStack stack = source.getFluid();
            snapshot = stack == null || stack.isEmpty() ? FluidStack.EMPTY : stack.copy();
            return snapshot;
        }

        @Override
        protected void readSnapshot(FluidStack snapshot) {
            source.setStackUnchecked(snapshot.isEmpty() ? FluidStack.EMPTY : snapshot.copy());
        }
    }
}
