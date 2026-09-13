package mekanism.fabric.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import mekanism.api.Action;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.energy.IEnergyConversion;
import mekanism.api.energy.IMekanismStrictEnergyHandler;
import mekanism.api.energy.IStrictEnergyHandler;
import mekanism.common.capabilities.energy.BasicEnergyContainer;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import team.reborn.energy.api.EnergyStorage;

/** Lifetime revocation semantics of {@link FabricEnergyStorage}: fail closed everywhere except captured rollback. */
class FabricEnergyLifetimeTest {

    @Test
    void invalidatedStorageFailsClosedForQueriesTransfersAndReverseUnwrap() {
        IEnergyConversion conversion = conversion(true, 1);
        SideHandler handler = new SideHandler();
        FabricEnergyStorage exposed = (FabricEnergyStorage) FabricEnergyLookup.expose(handler, Direction.NORTH, null, conversion);
        assertNotNull(exposed);
        try (Transaction transaction = Transaction.openOuter()) {
            assertEquals(10, exposed.insert(10, transaction));
            transaction.commit();
        }
        assertEquals(10, handler.container.getEnergy());
        assertTrue(exposed.supportsInsertion());
        assertTrue(exposed.supportsExtraction());

        //Retain the reverse Joules-domain handler BEFORE invalidation: repeated unwraps hand out the same stable facade
        IStrictEnergyHandler retainedReverse = FabricEnergyLookup.wrap(exposed, Direction.NORTH, conversion);
        assertNotNull(retainedReverse);
        assertSame(retainedReverse, FabricEnergyLookup.wrap(exposed, Direction.NORTH, conversion));
        //Exact Joules semantics with no conversion roundtrip while live
        assertEquals(10, retainedReverse.getEnergy(0));
        assertEquals(100, retainedReverse.getMaxEnergy(0));
        assertEquals(90, retainedReverse.getNeededEnergy(0));
        assertEquals(1, retainedReverse.getEnergyContainerCount());

        exposed.invalidate();

        assertFalse(exposed.supportsInsertion());
        assertFalse(exposed.supportsExtraction());
        try (Transaction transaction = Transaction.openOuter()) {
            assertEquals(0, exposed.insert(5, transaction));
            assertEquals(0, exposed.extract(5, transaction));
            transaction.commit();
        }
        assertEquals(0, exposed.getAmount());
        assertEquals(0, exposed.getCapacity());

        //The retained reverse facade no longer reads or mutates its old owner in exact Joules terms
        assertEquals(0, retainedReverse.getEnergy(0));
        assertEquals(0, retainedReverse.getMaxEnergy(0));
        assertEquals(0, retainedReverse.getNeededEnergy(0));
        assertEquals(0, retainedReverse.getEnergyContainerCount());
        retainedReverse.setEnergy(0, 5);
        assertEquals(5, retainedReverse.insertEnergy(0, 5, Action.EXECUTE));
        assertEquals(5, retainedReverse.insertEnergy(5, Action.EXECUTE));
        assertEquals(0, retainedReverse.extractEnergy(0, 5, Action.EXECUTE));
        assertEquals(0, retainedReverse.extractEnergy(5, Action.EXECUTE));
        //The owner's energy is untouched by fail-closed reads, setters, and transfers
        assertEquals(10, handler.container.getEnergy());

        //The reverse stableHandler unwrap route is closed for the revoked view, including fresh conversion wrappers
        assertNull(FabricEnergyLookup.wrap(exposed, Direction.NORTH, conversion));
        assertNull(FabricEnergyLookup.wrap(exposed, Direction.NORTH, conversion(true, 1)));
        assertNull(FabricEnergyLookup.wrap(exposed, Direction.SOUTH, conversion));

        //A freshly exposed view over the still-live owner is unaffected
        EnergyStorage fresh = FabricEnergyLookup.expose(handler, Direction.NORTH, null, conversion);
        assertNotNull(fresh);
        assertEquals(10, fresh.getAmount());
    }

    @Test
    void rollbackCapturedBeforeInvalidationStillRestoresEnergyAfterInvalidation() {
        IEnergyConversion conversion = conversion(true, 1);
        SideHandler handler = new SideHandler();
        FabricEnergyStorage exposed = (FabricEnergyStorage) FabricEnergyLookup.expose(handler, Direction.NORTH, null, conversion);
        assertNotNull(exposed);
        try (Transaction transaction = Transaction.openOuter()) {
            assertEquals(10, exposed.insert(10, transaction));
            transaction.commit();
        }

        Transaction transaction = Transaction.openOuter();
        assertEquals(4, exposed.extract(4, transaction));
        assertEquals(6, handler.container.getEnergy());
        //Owner lifetime is revoked while the transaction is still open, including the retained reverse facade
        IStrictEnergyHandler retainedReverse = FabricEnergyLookup.wrap(exposed, Direction.NORTH, conversion);
        assertNotNull(retainedReverse);
        exposed.invalidate();
        assertFalse(exposed.supportsExtraction());
        //The retained facade is already fail closed and cannot touch the owner's mid-transaction energy
        assertEquals(0, retainedReverse.getEnergy(0));
        assertEquals(5, retainedReverse.insertEnergy(0, 5, Action.EXECUTE));
        assertEquals(6, handler.container.getEnergy());
        transaction.abort();
        //The captured pre-invalidation snapshot still restores, even though the view itself is now fail closed
        assertEquals(10, handler.container.getEnergy());
        assertEquals(0, exposed.getAmount());
        //The raw backing handler used by rollback stays valid: a fresh view reads the restored energy exactly
        assertEquals(0, retainedReverse.getEnergy(0));
        assertEquals(10, ((FabricEnergyStorage) FabricEnergyLookup.expose(handler, Direction.NORTH, null, conversion)).getAmount());
    }

    private static IEnergyConversion conversion(boolean enabled, double rate) {
        return new IEnergyConversion() {
            @Override
            public boolean isEnabled() {
                return enabled;
            }

            @Override
            public double getConversion() {
                return rate;
            }
        };
    }

    private static final class SideHandler implements IMekanismStrictEnergyHandler {
        private final IEnergyContainer container = BasicEnergyContainer.create(100, null);

        @Override
        public boolean canHandleEnergy() {
            return true;
        }

        @Override
        public List<IEnergyContainer> getEnergyContainers(@Nullable Direction side) {
            return side == Direction.NORTH ? List.of(container) : List.of();
        }

        @Override
        public void onContentsChanged() {
        }
    }
}
