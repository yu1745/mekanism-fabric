package mekanism.fabric.fluid;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import mekanism.api.Action;
import mekanism.api.fluid.FluidStack;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.fluid.IMekanismFluidHandler;
import mekanism.common.capabilities.holder.IHolder;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * Physical-side-bound transactional Fabric view of one stable Mekanism fluid handler.
 *
 * <p>The exact tank objects and their order are captured at construction from
 * {@link IMekanismFluidHandler#getFluidTanks(Direction)}. A later size, identity, order, or {@link IMekanismFluidHandler#canHandleFluid()} change permanently invalidates
 * this view. The owner must additionally call {@link #invalidate()} on removal, reload, facing or side-configuration changes, and every known topology change. Retained
 * aggregate and single-tank views then fail closed and stop accessing the owner's live tank list.
 *
 * <p>Each mutation first obtains a deterministic per-tank simulation plan that follows the official insertion distribution order (tanks holding the same fluid first,
 * then empty tanks) and the official extraction rule (after the first drained fluid is found, only tanks holding that same fluid contribute). Backing
 * {@link IExtendedFluidTank} implementations must honor {@link Action#SIMULATE} by neither mutating nor notifying; this adapter relies on that API precondition and does
 * not snapshot around simulation. It then executes the plan in a nested Fabric transaction after taking one participant snapshot containing defensive copies of every
 * captured tank's contents. Execution commits only when returned and complete read-back state exactly match the simulated whole-millibucket plan; abort restores every
 * captured tank even if the view was invalidated in the meantime. Multiple side views over the same backing tanks participate independently in the same transaction;
 * Fabric aborts them in reverse participation order, so every view restores its own snapshot and the shared tanks converge to their original contents. Immediate dirty,
 * listener, save, or synchronization effects caused by execute or restore are observable and cannot be rolled back by this adapter.
 *
 * <p>Mekanism stores whole int millibuckets while Fabric amounts are {@code long} droplets. Transfer requests are maxima: only whole millibuckets are ever moved and the
 * returned amount is a multiple of {@link FabricFluidConversions#TRANSFER_UNITS_PER_MILLIBUCKET} no greater than the request, with no fractional accumulation, rounding
 * up, overflow, or component loss. Variant identity is delegated to {@link FabricFluidConversions}, including its documented rejection of flowing fluids.
 */
public final class FabricFluidStorage extends SnapshotParticipant<List<FluidStack>> implements SlottedStorage<FluidVariant> {

    private final IMekanismFluidHandler handler;
    private final Direction exposureSide;
    @Nullable
    private final IHolder holder;
    private final List<IExtendedFluidTank> capturedTanks;
    private final List<SingleSlotStorage<FluidVariant>> views;
    private volatile boolean invalidated;

    FabricFluidStorage(IMekanismFluidHandler handler, Direction exposureSide, @Nullable IHolder holder,
          List<IExtendedFluidTank> exposedTanks) {
        this.handler = Objects.requireNonNull(handler, "handler");
        this.exposureSide = Objects.requireNonNull(exposureSide, "exposureSide");
        this.holder = holder;
        capturedTanks = List.copyOf(exposedTanks);
        if (capturedTanks.isEmpty()) {
            throw new IllegalArgumentException("At least one exposed fluid tank is required");
        }
        List<SingleSlotStorage<FluidVariant>> tankViews = new ArrayList<>(capturedTanks.size());
        for (int tank = 0; tank < capturedTanks.size(); tank++) {
            tankViews.add(new TankView(tank));
        }
        views = List.copyOf(tankViews);
    }

    /**
     * Permanently invalidates this side view. Existing iterators and tank views remain safe, stable objects but no longer expose contents or permit transfer.
     */
    public void invalidate() {
        invalidated = true;
    }

    @Override
    public boolean supportsInsertion() {
        return canTransfer(true);
    }

    @Override
    public long insert(FluidVariant resource, long maxAmount, TransactionContext transaction) {
        return transfer(resource, maxAmount, transaction, true, 0, capturedTanks.size());
    }

    @Override
    public boolean supportsExtraction() {
        return canTransfer(false);
    }

    @Override
    public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
        return transfer(resource, maxAmount, transaction, false, 0, capturedTanks.size());
    }

    @Override
    public int getSlotCount() {
        return capturedTanks.size();
    }

    @Override
    public SingleSlotStorage<FluidVariant> getSlot(int slot) {
        return views.get(slot);
    }

    @Override
    public List<SingleSlotStorage<FluidVariant>> getSlots() {
        return views;
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public Iterator<StorageView<FluidVariant>> iterator() {
        // Every element implements StorageView<FluidVariant>; the immutable list also forbids iterator removal.
        return (Iterator) views.iterator();
    }

    @Override
    protected List<FluidStack> createSnapshot() {
        return copyCapturedStacks();
    }

    @Override
    protected void readSnapshot(List<FluidStack> snapshot) {
        Throwable failure = null;
        for (int tank = 0; tank < capturedTanks.size(); tank++) {
            IExtendedFluidTank fluidTank = capturedTanks.get(tank);
            FluidStack expected = snapshot.get(tank);
            try {
                //Restore captured state even if the owner's current validation policy changed.
                fluidTank.setStackUnchecked(copyStack(expected));
            } catch (Throwable setterFailure) {
                failure = collectRestoreFailure(failure, setterFailure);
            }
            try {
                FluidStack restored = copyTankStack(fluidTank, tank);
                if (!FluidStack.matches(expected, restored)) {
                    failure = collectRestoreFailure(failure, new IllegalStateException(
                          "Fluid tank " + tank + " restored " + describe(restored) + " instead of " + describe(expected)));
                }
            } catch (Throwable readFailure) {
                failure = collectRestoreFailure(failure, readFailure);
            }
        }
        if (failure != null) {
            throw new IllegalStateException("Failed to restore one or more captured fluid tanks", failure);
        }
    }

    private long transfer(FluidVariant resource, long maxAmount, TransactionContext transaction, boolean insertion,
          int firstTank, int lastTank) {
        Objects.requireNonNull(resource, "resource");
        StoragePreconditions.notNegative(maxAmount);
        Objects.requireNonNull(transaction, "transaction");
        if (resource.isBlank() || maxAmount == 0 || !canTransfer(insertion)) {
            return 0;
        }
        //Transfer requests are maxima: only whole millibuckets can ever be moved, so floor the request once up front
        long maxMillibuckets = maxAmount / FabricFluidConversions.TRANSFER_UNITS_PER_MILLIBUCKET;
        if (maxMillibuckets == 0) {
            return 0;
        }
        FluidStack typeProbe = FabricFluidConversions.toFluidStack(resource, FabricFluidConversions.TRANSFER_UNITS_PER_MILLIBUCKET);

        int[] plan = new int[capturedTanks.size()];
        long simulated = simulatePlan(typeProbe, maxMillibuckets, insertion, firstTank, lastTank, plan);
        if (simulated <= 0) {
            return 0;
        }

        try (Transaction nested = Transaction.openNested(transaction)) {
            if (!canTransfer(insertion)) {
                return 0;
            }
            List<FluidStack> expected = copyCapturedStacks();
            if (!canTransfer(insertion)) {
                return 0;
            }
            updateSnapshots(nested);
            if (!canTransfer(insertion)) {
                return 0;
            }

            long executed = 0;
            //Execute each captured tank exactly once; the plan already encodes the official two-pass
            // distribution order from the simulation
            for (int tank = firstTank; tank < lastTank; tank++) {
                int planned = plan[tank];
                if (planned == 0) {
                    continue;
                }
                if (!canTransfer(insertion)) {
                    return 0;
                }
                FluidStack before = expected.get(tank);
                FluidStack expectedAfter = insertion
                      ? expectedAfterInsertion(before, typeProbe, planned)
                      : expectedAfterExtraction(before, typeProbe, planned);
                if (expectedAfter == null) {
                    return 0;
                }

                int moved;
                if (insertion) {
                    FluidStack offer = offerStack(typeProbe, planned);
                    FluidStack remainder = Objects.requireNonNull(handler.insertFluid(
                          tank, offer, exposureSide, Action.EXECUTE),
                          "Fluid tank returned a null insertion remainder");
                    moved = insertedAmount(offer, remainder);
                } else {
                    FluidStack extracted = Objects.requireNonNull(handler.extractFluid(
                          tank, planned, exposureSide, Action.EXECUTE),
                          "Fluid tank returned a null extracted stack");
                    moved = extractedAmount(typeProbe, planned, extracted);
                }
                if (moved != planned) {
                    return 0;
                }
                expected.set(tank, expectedAfter);
                executed = addClamped(executed, moved);
                if (executed > simulated || !canTransfer(insertion)) {
                    return 0;
                }
            }

            if (executed != simulated || !canTransfer(insertion) || !matchesCurrentState(expected)) {
                return 0;
            }
            nested.commit();
            //executed <= maxAmount/81 by construction, so this exact long multiplication cannot overflow and
            // returns a whole-millibucket multiple no greater than the request
            return executed * FabricFluidConversions.TRANSFER_UNITS_PER_MILLIBUCKET;
        }
    }

    private long simulatePlan(FluidStack typeProbe, long maxMillibuckets, boolean insertion, int firstTank, int lastTank,
          int[] plan) {
        long simulated = 0;
        for (int visit = 0; visit < visitCount(firstTank, lastTank, insertion) && simulated < maxMillibuckets; visit++) {
            int tank = tankIndex(firstTank, lastTank, insertion, visit);
            if (!canTransfer(insertion)) {
                return -1;
            }
            boolean eligible;
            if (insertion) {
                //Classify by live contents so the official distribution order is preserved: same-fluid tanks are
                // offered in the first pass and empty tanks only in the second pass
                FluidStack current = copyTankStack(capturedTanks.get(tank), tank);
                if (!hasStableTopology()) {
                    return -1;
                }
                boolean sameType = !current.isEmpty() && FluidStack.isSameFluidSameComponents(current, typeProbe);
                eligible = visit < lastTank - firstTank ? sameType : current.isEmpty();
            } else {
                FluidStack current = copyTankStack(capturedTanks.get(tank), tank);
                if (!hasStableTopology()) {
                    return -1;
                }
                eligible = !current.isEmpty() && FluidStack.isSameFluidSameComponents(current, typeProbe);
            }
            if (!eligible) {
                continue;
            }
            int offered = (int) Math.min((long) Integer.MAX_VALUE, maxMillibuckets - simulated);
            int moved;
            if (insertion) {
                FluidStack offer = offerStack(typeProbe, offered);
                FluidStack remainder = Objects.requireNonNull(handler.insertFluid(
                      tank, offer, exposureSide, Action.SIMULATE),
                      "Fluid tank returned a null insertion remainder");
                moved = insertedAmount(offer, remainder);
            } else {
                FluidStack extracted = Objects.requireNonNull(handler.extractFluid(
                      tank, offered, exposureSide, Action.SIMULATE),
                      "Fluid tank returned a null extracted stack");
                moved = extractedAmount(typeProbe, offered, extracted);
            }
            if (moved < 0) {
                return -1;
            }
            plan[tank] = moved;
            simulated = addClamped(simulated, moved);
            if (simulated > maxMillibuckets || !canTransfer(insertion)) {
                return -1;
            }
        }
        return simulated;
    }

    /**
     * Number of captured tank visits for the given range. Insertion considers every tank in each of two passes; distribution eligibility is determined alongside
     * {@link #tankIndex(int, int, boolean, int)}.
     */
    private static int visitCount(int firstTank, int lastTank, boolean insertion) {
        return insertion ? lastTank - firstTank + lastTank - firstTank : lastTank - firstTank;
    }

    /**
     * Visits tanks in the official distribution order: for insertion, tanks holding the same fluid first and then empty tanks; for extraction, plain stable order
     * (the simulation already skips tanks holding other fluids, matching the official same-type extraction rule).
     */
    private int tankIndex(int firstTank, int lastTank, boolean insertion, int visit) {
        if (!insertion) {
            return firstTank + visit;
        }
        int count = lastTank - firstTank;
        if (visit < count) {
            return firstTank + visit;
        }
        return firstTank + visit - count;
    }

    private boolean canTransfer(boolean insertion) {
        if (!hasStableTopology()) {
            return false;
        }
        boolean allowed = holder == null || (insertion ? holder.canInsert(exposureSide) : holder.canExtract(exposureSide));
        return allowed && hasStableTopology();
    }

    private boolean hasStableTopology() {
        if (invalidated) {
            return false;
        }
        if (!handler.canHandleFluid()) {
            invalidated = true;
            return false;
        }
        List<IExtendedFluidTank> current = handler.getFluidTanks(exposureSide);
        if (current.size() != capturedTanks.size()) {
            invalidated = true;
            return false;
        }
        for (int tank = 0; tank < capturedTanks.size(); tank++) {
            if (current.get(tank) != capturedTanks.get(tank)) {
                invalidated = true;
                return false;
            }
        }
        return !invalidated;
    }

    private List<FluidStack> copyCapturedStacks() {
        List<FluidStack> copies = new ArrayList<>(capturedTanks.size());
        for (int tank = 0; tank < capturedTanks.size(); tank++) {
            copies.add(copyTankStack(capturedTanks.get(tank), tank));
        }
        return copies;
    }

    private boolean matchesCurrentState(List<FluidStack> expected) {
        boolean matches = true;
        for (int tank = 0; tank < capturedTanks.size(); tank++) {
            FluidStack current = copyTankStack(capturedTanks.get(tank), tank);
            matches &= FluidStack.matches(expected.get(tank), current);
            if (!hasStableTopology()) {
                return false;
            }
        }
        return matches;
    }

    private static FluidStack offerStack(FluidStack typeProbe, int plannedMillibuckets) {
        return typeProbe.copyWithAmount(plannedMillibuckets);
    }

    private static int insertedAmount(FluidStack offer, FluidStack remainder) {
        if (remainder.isEmpty()) {
            return remainder.getAmount() == 0 ? offer.getAmount() : -1;
        }
        int remaining = remainder.getAmount();
        if (remaining < 0 || remaining > offer.getAmount() || !FluidStack.isSameFluidSameComponents(remainder, offer)) {
            return -1;
        }
        return offer.getAmount() - remaining;
    }

    private static int extractedAmount(FluidStack typeProbe, int offered, FluidStack extracted) {
        int amount = extracted.getAmount();
        if (extracted.isEmpty()) {
            return amount == 0 ? 0 : -1;
        }
        if (amount < 1 || amount > offered || !FluidStack.isSameFluidSameComponents(extracted, typeProbe)) {
            return -1;
        }
        return amount;
    }

    @Nullable
    private static FluidStack expectedAfterInsertion(FluidStack before, FluidStack typeProbe, int amount) {
        if (before.isEmpty()) {
            return typeProbe.copyWithAmount(amount);
        }
        if (!FluidStack.isSameFluidSameComponents(before, typeProbe)) {
            return null;
        }
        long total = (long) before.getAmount() + amount;
        return total <= Integer.MAX_VALUE ? before.copyWithAmount((int) total) : null;
    }

    @Nullable
    private static FluidStack expectedAfterExtraction(FluidStack before, FluidStack typeProbe, int amount) {
        if (before.isEmpty() || !FluidStack.isSameFluidSameComponents(before, typeProbe) || amount > before.getAmount()) {
            return null;
        }
        int remaining = before.getAmount() - amount;
        return remaining == 0 ? FluidStack.EMPTY : before.copyWithAmount(remaining);
    }

    private static FluidStack copyTankStack(IExtendedFluidTank tank, int index) {
        return copyStack(Objects.requireNonNull(tank.getFluid(), "Fluid tank " + index + " returned a null stack"));
    }

    private static FluidStack copyStack(FluidStack stack) {
        return stack.isEmpty() ? FluidStack.EMPTY : stack.copy();
    }

    private static String describe(FluidStack stack) {
        return stack.isEmpty() ? "empty" : stack.getFluid() + " x " + stack.getAmount();
    }

    private static long addClamped(long total, int amount) {
        return amount <= 0 ? total : amount > Long.MAX_VALUE - total ? Long.MAX_VALUE : total + amount;
    }

    private static Throwable collectRestoreFailure(@Nullable Throwable failure, Throwable restoreFailure) {
        if (failure == null) {
            return restoreFailure;
        }
        if (failure != restoreFailure) {
            failure.addSuppressed(restoreFailure);
        }
        return failure;
    }

    private final class TankView implements SingleSlotStorage<FluidVariant> {

        private final int tank;

        private TankView(int tank) {
            this.tank = tank;
        }

        @Override
        public boolean supportsInsertion() {
            return FabricFluidStorage.this.supportsInsertion();
        }

        @Override
        public long insert(FluidVariant resource, long maxAmount, TransactionContext transaction) {
            return transfer(resource, maxAmount, transaction, true, tank, tank + 1);
        }

        @Override
        public boolean supportsExtraction() {
            return FabricFluidStorage.this.supportsExtraction();
        }

        @Override
        public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
            return transfer(resource, maxAmount, transaction, false, tank, tank + 1);
        }

        @Override
        public boolean isResourceBlank() {
            return getResource().isBlank();
        }

        @Override
        public FluidVariant getResource() {
            if (!hasStableTopology()) {
                return FluidVariant.blank();
            }
            FluidStack stack = copyTankStack(capturedTanks.get(tank), tank);
            return hasStableTopology() && !stack.isEmpty() ? FabricFluidConversions.toFabricVariant(stack) : FluidVariant.blank();
        }

        @Override
        public long getAmount() {
            if (!hasStableTopology()) {
                return 0;
            }
            int amount = copyTankStack(capturedTanks.get(tank), tank).getAmount();
            return hasStableTopology() ? FabricFluidConversions.millibucketsToTransferUnits(Math.max(0, amount)) : 0;
        }

        @Override
        public long getCapacity() {
            if (!hasStableTopology()) {
                return 0;
            }
            int capacity = capturedTanks.get(tank).getCapacity();
            return hasStableTopology() ? FabricFluidConversions.millibucketsToTransferUnits(Math.max(0, capacity)) : 0;
        }
    }
}
