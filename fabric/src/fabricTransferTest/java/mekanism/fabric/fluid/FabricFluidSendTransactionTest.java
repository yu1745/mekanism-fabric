package mekanism.fabric.fluid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Iterator;
import java.util.List;
import mekanism.api.fluid.FluidStack;
import mekanism.common.capabilities.fluid.BasicFluidTank;
import mekanism.common.content.network.distribution.FluidHandlerTarget;
import mekanism.common.content.network.distribution.FluidSendTransaction;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Focused platform-difference checks for the sender-owned fluid send transaction: fractional (sub-millibucket) acceptor abort/floor retry, fair distribution with
 * shared-destination capacity observed through the enclosing transaction, and source-mismatch rollback.
 */
class FabricFluidSendTransactionTest {

    @BeforeAll
    static void bootstrapVanilla() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static FluidVariant water() {
        return FluidVariant.of(Fluids.WATER);
    }

    /**
     * A minimal real Storage over droplet-denominated contents; capacity is expressed in droplets so fractional (non-whole-millibucket) acceptances can be produced.
     */
    private static final class FakeFluidStorage implements Storage<FluidVariant> {

        private final long capacityDroplets;
        private FluidVariant resource = FluidVariant.blank();
        private long amount;

        private FakeFluidStorage(long capacityDroplets) {
            this.capacityDroplets = capacityDroplets;
        }

        long amount() {
            return amount;
        }

        @Override
        public boolean supportsInsertion() {
            return true;
        }

        @Override
        public long insert(FluidVariant resource, long maxAmount, TransactionContext transaction) {
            if (resource.isBlank() || maxAmount <= 0) {
                return 0;
            }
            if (!this.resource.isBlank() && !this.resource.equals(resource)) {
                return 0;
            }
            long inserted = Math.min(maxAmount, capacityDroplets - amount);
            if (inserted <= 0) {
                return 0;
            }
            new SnapshotParticipant<>() {
                final FluidVariant priorResource = FakeFluidStorage.this.resource;
                final long priorAmount = amount;

                @Override
                protected Object createSnapshot() {
                    return this;
                }

                @Override
                protected void readSnapshot(Object snapshot) {
                    FakeFluidStorage.this.resource = priorResource;
                    amount = priorAmount;
                }
            }.updateSnapshots(transaction);
            this.resource = resource;
            amount += inserted;
            return inserted;
        }

        @Override
        public boolean supportsExtraction() {
            return false;
        }

        @Override
        public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
            return 0;
        }

        @Override
        public Iterator<StorageView<FluidVariant>> iterator() {
            return List.<StorageView<FluidVariant>>of(new StorageView<>() {
                @Override
                public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
                    return 0;
                }

                @Override
                public boolean isResourceBlank() {
                    return FakeFluidStorage.this.resource.isBlank();
                }

                @Override
                public FluidVariant getResource() {
                    return FakeFluidStorage.this.resource;
                }

                @Override
                public long getAmount() {
                    return amount;
                }

                @Override
                public long getCapacity() {
                    return capacityDroplets;
                }
            }).iterator();
        }
    }

    @Test
    void fractionalOnlyAcceptorNeverCausesFractionalCommitOrDebit() {
        //Capacity of 40 droplets cannot hold a whole millibucket (81 droplets)
        FakeFluidStorage destination = new FakeFluidStorage(40);
        BasicFluidTank source = BasicFluidTank.create(1_000, null);
        source.setStackUnchecked(new FluidStack(Fluids.WATER, 100));

        int sent = FluidSendTransaction.send(List.of(destination), source, 100);

        assertEquals(0, sent, "A storage unable to accept an exact whole millibucket must make no progress by design");
        assertEquals(100, source.getFluidAmount(), "The source must not be debited when nothing could be sent");
        assertEquals(0, destination.amount(), "No fractional destination commit may survive the aborted send");

        //A 130-droplet capacity reports a fractional 130-droplet acceptance for a large request; the bounded floor retry must land on 81 droplets (1mB)
        FakeFluidStorage fractional = new FakeFluidStorage(130);
        BasicFluidTank fullSource = BasicFluidTank.create(1_000, null);
        fullSource.setStackUnchecked(new FluidStack(Fluids.WATER, 100));

        assertEquals(1, FluidSendTransaction.send(List.of(fractional), fullSource, 100), "The whole-millibucket floor retry should commit exactly 1mB");
        assertEquals(81, fractional.amount(), "Exactly the whole-millibucket amount may be committed");
        assertEquals(99, fullSource.getFluidAmount(), "The source debit must exactly match the committed whole millibuckets");
    }

    @Test
    void sharedDestinationCapacityIsObservedAndSourceDebitMatchesFairSplit() {
        //Two destination views share one backing fake storage with room for exactly 3mB total; each view must see the capacity
        // consumed by the other through the enclosing transaction during simulation and retry
        FakeFluidStorage sharedBacking = new FakeFluidStorage(3 * FabricFluidConversions.TRANSFER_UNITS_PER_MILLIBUCKET);
        Storage<FluidVariant> first = sharedBacking;
        Storage<FluidVariant> second = new Storage<>() {
            @Override
            public boolean supportsInsertion() {
                return true;
            }

            @Override
            public long insert(FluidVariant resource, long maxAmount, TransactionContext transaction) {
                return sharedBacking.insert(resource, maxAmount, transaction);
            }

            @Override
            public boolean supportsExtraction() {
                return false;
            }

            @Override
            public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
                return 0;
            }

            @Override
            public Iterator<StorageView<FluidVariant>> iterator() {
                return sharedBacking.iterator();
            }
        };
        BasicFluidTank source = BasicFluidTank.create(1_000, null);
        source.setStackUnchecked(new FluidStack(Fluids.WATER, 10));

        int sent = FluidSendTransaction.send(List.of(first, second), source, 10);

        assertEquals(3, sent, "Only the actually available whole millibuckets may be sent");
        assertEquals(3 * FabricFluidConversions.TRANSFER_UNITS_PER_MILLIBUCKET, sharedBacking.amount(), "The shared destination holds exactly the committed amount");
        assertEquals(7, source.getFluidAmount(), "The source must be debited by exactly the amount distributed");
        assertTrue(FluidStack.matches(source.getFluid(), new FluidStack(Fluids.WATER, 7)), "Source contents after debit must match exactly");
    }

    @Test
    void sourceMutationDuringDistributionAbortsEverything() {
        BasicFluidTank source = BasicFluidTank.create(1_000, null);
        source.setStackUnchecked(new FluidStack(Fluids.WATER, 100));
        //A misbehaving destination mutates the source during insertion (alias/self-target style reentrancy)
        Storage<FluidVariant> reentrant = new Storage<>() {
            @Override
            public boolean supportsInsertion() {
                return true;
            }

            @Override
            public long insert(FluidVariant resource, long maxAmount, TransactionContext transaction) {
                //Corrupt the source outside any transaction; the sender must detect this before committing
                source.setStackUnchecked(new FluidStack(Fluids.WATER, 1));
                return FabricFluidConversions.TRANSFER_UNITS_PER_MILLIBUCKET;
            }

            @Override
            public boolean supportsExtraction() {
                return false;
            }

            @Override
            public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
                return 0;
            }

            @Override
            public @Nullable Iterator<StorageView<FluidVariant>> iterator() {
                return List.<StorageView<FluidVariant>>of().iterator();
            }
        };

        int sent = FluidSendTransaction.send(List.of(reentrant), source, 100);

        assertEquals(0, sent, "A source mutated during distribution must abort the whole send");
        assertEquals(100, source.getFluidAmount(), "The snapshot rollback must restore the mutated source to its pre-send contents");
    }

    @Test
    void offeredStackOverloadActuallyCommitsTheDistribution() {
        //the caller-owned offered-stack overload previously returned a positive count while every target write aborted on close,
        // losing fluid if the caller debited the returned amount. It must commit the distribution.
        FakeFluidStorage destination = new FakeFluidStorage(10 * FabricFluidConversions.TRANSFER_UNITS_PER_MILLIBUCKET);
        FluidStack offered = new FluidStack(Fluids.WATER, 5);

        int sent = FluidSendTransaction.send(List.of(destination), offered);

        assertEquals(5, sent);
        assertEquals(5 * FabricFluidConversions.TRANSFER_UNITS_PER_MILLIBUCKET, destination.amount(), "The destination must durably hold the reported amount");
    }

    /**
     * A hostile storage that always reports request-minus-one droplet: under the old amount-dependent decreasing loop this caused unbounded probes. The approved fixed
     * bound allows the initial probe plus at most ONE floor retry, and never commits a fractional response.
     */
    @Test
    void requestMinusOneStorageTerminatesAfterFixedRetryAndNeverCommitsFractional() {
        int[] insertCalls = {0};
        Storage<FluidVariant> hostile = new Storage<>() {
            @Override
            public boolean supportsInsertion() {
                return true;
            }

            @Override
            public long insert(FluidVariant resource, long maxAmount, TransactionContext transaction) {
                insertCalls[0]++;
                return Math.max(0, maxAmount - 1);
            }

            @Override
            public boolean supportsExtraction() {
                return false;
            }

            @Override
            public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
                return 0;
            }

            @Override
            public Iterator<StorageView<FluidVariant>> iterator() {
                return List.<StorageView<FluidVariant>>of().iterator();
            }
        };
        BasicFluidTank source = BasicFluidTank.create(1_000, null);
        source.setStackUnchecked(new FluidStack(Fluids.WATER, 100));

        int sent = FluidSendTransaction.send(List.of(hostile), source, 100);

        assertEquals(0, sent, "A fractional retry response must abort, never committing a fractional destination amount");
        assertEquals(100, source.getFluidAmount(), "Nothing sent means nothing debited");
        //of the previous wrong <=2 assumption: a FULL send takes the collectAcceptors simulation plus the Target.sendPossible simulation
        // BEFORE the target's initial execute probe and its single bounded retry, so this one-destination case performs exactly 4 inserts — the bounded bound
        // itself (initial probe + one retry) is asserted directly below. Target fairness is unchanged.
        assertEquals(4, insertCalls[0], "Full send: 2 fair-distribution simulations + initial execute probe + one bounded floor retry");
    }

    /**
     * Direct bounded-helper bound: even a hostile request-minus-one storage gets at most the initial probe plus ONE floor retry inside one enclosing transaction,
     * never an amount-dependent loop, and a fractional response is never committed.
     */
    @Test
    void boundedInsertHelperTerminatesAfterInitialProbePlusOneRetry() {
        int[] insertCalls = {0};
        Storage<FluidVariant> hostile = hostileRequestMinusOneStorage(insertCalls);
        BasicFluidTank source = BasicFluidTank.create(1_000, null);
        source.setStackUnchecked(new FluidStack(Fluids.WATER, 100));

        long accepted;
        try (Transaction outer = Transaction.openOuter()) {
            accepted = FluidHandlerTarget.insertWholeMillibuckets(hostile, new FluidStack(Fluids.WATER, 100), 100, outer);
        }

        assertEquals(0, accepted, "A fractional retry response must abort, never committing a fractional amount");
        assertEquals(2, insertCalls[0], "The bounded helper itself must be exactly the initial probe plus one floor retry");
    }

    private static Storage<FluidVariant> hostileRequestMinusOneStorage(int[] insertCalls) {
        return new Storage<>() {
            @Override
            public boolean supportsInsertion() {
                return true;
            }

            @Override
            public long insert(FluidVariant resource, long maxAmount, TransactionContext transaction) {
                insertCalls[0]++;
                return Math.max(0, maxAmount - 1);
            }

            @Override
            public boolean supportsExtraction() {
                return false;
            }

            @Override
            public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
                return 0;
            }

            @Override
            public Iterator<StorageView<FluidVariant>> iterator() {
                return List.<StorageView<FluidVariant>>of().iterator();
            }
        };
    }
}
