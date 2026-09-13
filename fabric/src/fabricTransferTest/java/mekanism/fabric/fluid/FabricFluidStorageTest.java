package mekanism.fabric.fluid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import mekanism.api.fluid.FluidStack;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.fluid.IMekanismFluidHandler;
import mekanism.common.capabilities.fluid.BasicFluidTank;
import mekanism.common.capabilities.holder.IHolder;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class FabricFluidStorageTest {

    //Resolved lazily per test: Fabric variant and vanilla fluid registries must not initialize before Bootstrap
    private static FluidVariant water() {
        return FluidVariant.of(Fluids.WATER);
    }

    private static FluidVariant lava() {
        return FluidVariant.of(Fluids.LAVA);
    }

    @BeforeAll
    static void bootstrapVanilla() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void exposureViewsPreserveVariantBlankZeroCapacityAndRejectInvalidBoundaries() {
        BasicFluidTank water = BasicFluidTank.create(1_000, null);
        water.setStackUnchecked(new FluidStack(Fluids.WATER, 250));
        BasicFluidTank empty = BasicFluidTank.create(2_000, null);
        MutableFluidHandler handler = new MutableFluidHandler(List.of(water, empty));
        FabricFluidStorage storage = FabricFluidLookup.expose(handler, Direction.NORTH, null);
        assertNotNull(storage);
        assertEquals(2, storage.getSlotCount());
        assertSame(storage.getSlot(0), storage.getSlots().get(0));
        assertSame(storage.getSlot(1), storage.getSlots().get(1));

        SingleSlotStorage<FluidVariant> waterView = storage.getSlot(0);
        assertEquals(water(), waterView.getResource());
        assertEquals(250L * 81, waterView.getAmount());
        assertEquals(1_000L * 81, waterView.getCapacity());
        assertFalse(waterView.isResourceBlank());
        SingleSlotStorage<FluidVariant> emptyView = storage.getSlot(1);
        assertTrue(emptyView.isResourceBlank());
        assertEquals(FluidVariant.blank(), emptyView.getResource());
        assertEquals(0, emptyView.getAmount());
        assertEquals(2_000L * 81, emptyView.getCapacity());

        Iterator<StorageView<FluidVariant>> iterator = storage.iterator();
        assertSame(waterView, iterator.next());
        assertSame(emptyView, iterator.next());
        assertFalse(iterator.hasNext());
        assertThrows(UnsupportedOperationException.class, iterator::remove);
        assertThrows(IndexOutOfBoundsException.class, () -> storage.getSlot(2));

        try (Transaction transaction = Transaction.openOuter()) {
            assertEquals(0, storage.insert(FluidVariant.blank(), Long.MAX_VALUE, transaction));
            assertEquals(0, storage.extract(FluidVariant.blank(), Long.MAX_VALUE, transaction));
            assertEquals(0, storage.insert(water(), 0, transaction));
            assertEquals(0, storage.extract(water(), 0, transaction));
            assertThrows(IllegalArgumentException.class, () -> storage.insert(water(), -1, transaction));
            assertThrows(IllegalArgumentException.class, () -> waterView.extract(water(), -1, transaction));
            transaction.commit();
        }
        assertEquals(250, water.getFluidAmount());
        assertEquals(0, empty.getFluidAmount());

        assertEquals(null, FabricFluidLookup.expose(null, Direction.NORTH, null));
        assertEquals(null, FabricFluidLookup.expose(handler, null, null));
        handler.canHandleFluid = false;
        assertEquals(null, FabricFluidLookup.expose(handler, Direction.NORTH, null));
        handler.canHandleFluid = true;
        handler.onlySide = Direction.NORTH;
        assertEquals(null, FabricFluidLookup.expose(handler, Direction.SOUTH, null));
        handler.onlySide = null;
        assertEquals(null, FabricFluidLookup.expose(new MutableFluidHandler(List.of()), Direction.NORTH, null));
    }

    @Test
    void wholeMillibucketMaximaFloorRequestsAndNeverAccumulateFractions() {
        BasicFluidTank tank = BasicFluidTank.create(1_000, null);
        FabricFluidStorage storage = FabricFluidLookup.expose(new MutableFluidHandler(List.of(tank)), Direction.NORTH, null);
        assertNotNull(storage);

        try (Transaction transaction = Transaction.openOuter()) {
            //Sub-millibucket requests move nothing, repeatedly, and never accumulate fractions
            for (int attempt = 0; attempt < 3; attempt++) {
                assertEquals(0, storage.insert(water(), 80, transaction));
                assertEquals(0, storage.extract(water(), 80, transaction));
            }
            assertEquals(0, tank.getFluidAmount());
            //Exactly one millibucket
            assertEquals(81, storage.insert(water(), 81, transaction));
            assertEquals(1, tank.getFluidAmount());
            //A request past a whole millibucket floors to the whole multiple
            assertEquals(162, storage.insert(water(), 202, transaction));
            assertEquals(3, tank.getFluidAmount());
            //Extraction floors as well and cannot round up
            assertEquals(0, storage.extract(water(), 80, transaction));
            assertEquals(3, tank.getFluidAmount());
            assertEquals(243, storage.extract(water(), 500, transaction));
            assertEquals(0, tank.getFluidAmount());
            //Huge maxima cap at tank capacity and return whole units
            assertEquals(1_000L * 81, storage.insert(water(), Long.MAX_VALUE, transaction));
            assertEquals(1_000, tank.getFluidAmount());
            assertEquals(81_000, storage.extract(water(), Long.MAX_VALUE, transaction));
            assertEquals(0, tank.getFluidAmount());
            transaction.commit();
        }
    }

    @Test
    void insertionUsesSameFluidBeforeEmptyDistributionAndExtractionFiltersVariant() {
        BasicFluidTank emptyFirst = BasicFluidTank.create(1_000, null);
        BasicFluidTank partialWater = BasicFluidTank.create(1_000, null);
        partialWater.setStackUnchecked(new FluidStack(Fluids.WATER, 800));
        BasicFluidTank lava = BasicFluidTank.create(1_000, null);
        lava.setStackUnchecked(new FluidStack(Fluids.LAVA, 600));
        FabricFluidStorage storage = FabricFluidLookup.expose(
              new MutableFluidHandler(List.of(emptyFirst, partialWater, lava)), Direction.NORTH, null);
        assertNotNull(storage);

        try (Transaction transaction = Transaction.openOuter()) {
            //The same-type tank is filled before the earlier empty tank, matching official distribution
            assertEquals(200L * 81, storage.insert(water(), 200L * 81, transaction));
            assertEquals(0, emptyFirst.getFluidAmount());
            assertEquals(1_000, partialWater.getFluidAmount());
            //Once the same-type tank is full, the remainder goes to the empty tank
            assertEquals(300L * 81, storage.insert(water(), 300L * 81, transaction));
            assertEquals(300, emptyFirst.getFluidAmount());
            assertEquals(1_000, partialWater.getFluidAmount());
            //Aggregate extraction only drains tanks holding the requested variant
            assertEquals(100L * 81, storage.extract(lava(), 100L * 81, transaction));
            assertEquals(500, lava.getFluidAmount());
            assertEquals(300, emptyFirst.getFluidAmount());
            assertEquals(500L * 81, storage.extract(lava(), Long.MAX_VALUE, transaction));
            assertEquals(0, lava.getFluidAmount());
            transaction.commit();
        }
    }

    @Test
    void outerAbortRestoresTanksEvenAfterViewInvalidation() {
        BasicFluidTank first = BasicFluidTank.create(1_000, null);
        BasicFluidTank second = BasicFluidTank.create(1_000, null);
        second.setStackUnchecked(new FluidStack(Fluids.LAVA, 400));
        MutableFluidHandler handler = new MutableFluidHandler(List.of(first, second));
        FabricFluidStorage storage = FabricFluidLookup.expose(handler, Direction.NORTH, null);
        assertNotNull(storage);

        try (Transaction outer = Transaction.openOuter()) {
            assertEquals(500L * 81, storage.insert(water(), 500L * 81, outer));
            assertEquals(100L * 81, storage.extract(lava(), 100L * 81, outer));
            assertEquals(500, first.getFluidAmount());
            assertEquals(300, second.getFluidAmount());
            //Invalidate the view mid-transaction; abort must still restore the real tanks
            storage.invalidate();
            assertFalse(storage.supportsInsertion());
            outer.abort();
        }
        assertEquals(0, first.getFluidAmount());
        assertEquals(400, second.getFluidAmount());
        assertTrue(FluidStack.isSameFluidSameComponents(second.getFluid(), new FluidStack(Fluids.LAVA, 1)));
        //Invalidated views fail closed and stop accessing the owner's live tank list
        assertTrue(storage.iterator().next().isResourceBlank());
        assertEquals(0, storage.getSlot(1).getAmount());
        try (Transaction transaction = Transaction.openOuter()) {
            assertEquals(0, storage.insert(water(), 81, transaction));
            assertEquals(0, storage.extract(lava(), 81, transaction));
            transaction.commit();
        }
        assertEquals(0, first.getFluidAmount());
        assertEquals(400, second.getFluidAmount());
    }

    @Test
    void sideViewsShareBackingTanksAndConvergeOnSharedAbort() {
        BasicFluidTank shared = BasicFluidTank.create(1_000, null);
        MutableFluidHandler handler = new MutableFluidHandler(List.of(shared));
        FabricFluidStorage north = FabricFluidLookup.expose(handler, Direction.NORTH, null);
        FabricFluidStorage south = FabricFluidLookup.expose(handler, Direction.SOUTH, null);
        assertNotNull(north);
        assertNotNull(south);
        assertTrue(north != south);

        try (Transaction outer = Transaction.openOuter()) {
            assertEquals(300L * 81, north.insert(water(), 300L * 81, outer));
            assertEquals(200L * 81, south.insert(water(), 200L * 81, outer));
            assertEquals(500, shared.getFluidAmount());
            //The south view observes the north view's uncommitted execution through the real tank
            assertEquals(500L * 81, south.getSlot(0).getAmount());
            assertEquals(500L * 81, south.extract(water(), 500L * 81, outer));
            assertEquals(0, shared.getFluidAmount());
            outer.abort();
        }
        //Both participants restore their own snapshot of the same tank in reverse participation order
        assertEquals(0, shared.getFluidAmount());

        try (Transaction outer = Transaction.openOuter()) {
            assertEquals(100L * 81, north.insert(water(), 100L * 81, outer));
            assertEquals(50L * 81, south.extract(water(), 50L * 81, outer));
            outer.commit();
        }
        assertEquals(50, shared.getFluidAmount());
    }

    @Test
    void realTanksEnforceAutomationDirectionHolderPolicyAndTopologyDriftFailsClosed() {
        BasicFluidTank input = BasicFluidTank.input(1_000, stack -> true, null);
        BasicFluidTank output = BasicFluidTank.output(1_000, null);
        output.setStackUnchecked(new FluidStack(Fluids.LAVA, 500));
        MutablePolicy policy = new MutablePolicy();
        MutableFluidHandler handler = new MutableFluidHandler(List.of(input, output));
        FabricFluidStorage storage = FabricFluidLookup.expose(handler, Direction.NORTH, policy);
        assertNotNull(storage);

        try (Transaction transaction = Transaction.openOuter()) {
            //Handler-side insertion lands in the input tank; the output tank rejects external insertion
            assertEquals(700L * 81, storage.insert(water(), 700L * 81, transaction));
            assertEquals(700, input.getFluidAmount());
            assertEquals(500, output.getFluidAmount());
            //Handler-side extraction only drains the output tank; the input tank rejects external extraction
            assertEquals(200L * 81, storage.extract(lava(), 200L * 81, transaction));
            assertEquals(300, output.getFluidAmount());
            assertEquals(700, input.getFluidAmount());
            assertEquals(0, storage.extract(water(), Long.MAX_VALUE, transaction));
            transaction.commit();
        }
        assertEquals(700, input.getFluidAmount());
        assertEquals(300, output.getFluidAmount());

        //Holder side policy blocks a direction entirely
        policy.insertAllowed = false;
        assertFalse(storage.supportsInsertion());
        try (Transaction transaction = Transaction.openOuter()) {
            assertEquals(0, storage.insert(water(), 81, transaction));
            transaction.commit();
        }
        policy.insertAllowed = true;
        assertTrue(storage.supportsInsertion());

        //A different tank identity exposed on the same side permanently fails the view closed
        handler.tanks = new ArrayList<>(List.of(BasicFluidTank.create(1_000, null), output));
        assertFalse(storage.supportsInsertion());
        assertFalse(storage.supportsExtraction());
        assertTrue(storage.getSlot(0).isResourceBlank());
        try (Transaction transaction = Transaction.openOuter()) {
            assertEquals(0, storage.insert(water(), 81, transaction));
            assertEquals(0, storage.extract(lava(), 81, transaction));
            transaction.commit();
        }
        //The owner turning off fluid handling entirely fails every retained view closed
        FabricFluidStorage fresh = FabricFluidLookup.expose(handler, Direction.NORTH, null);
        assertNotNull(fresh);
        handler.canHandleFluid = false;
        assertFalse(fresh.supportsExtraction());
        try (Transaction transaction = Transaction.openOuter()) {
            assertEquals(0, fresh.extract(lava(), 81, transaction));
            transaction.commit();
        }
    }

    private static final class MutablePolicy implements IHolder {

        private boolean insertAllowed = true;
        private boolean extractAllowed = true;

        @Override
        public boolean canInsert(@Nullable Direction direction) {
            return insertAllowed;
        }

        @Override
        public boolean canExtract(@Nullable Direction direction) {
            return extractAllowed;
        }
    }

    private static final class MutableFluidHandler implements IMekanismFluidHandler {

        private List<IExtendedFluidTank> tanks;
        private boolean canHandleFluid = true;
        @Nullable
        private Direction onlySide;

        private MutableFluidHandler(List<IExtendedFluidTank> tanks) {
            this.tanks = tanks;
        }

        @Override
        public boolean canHandleFluid() {
            return canHandleFluid;
        }

        @Override
        public List<IExtendedFluidTank> getFluidTanks(@Nullable Direction side) {
            return onlySide != null && side != onlySide ? List.of() : tanks;
        }

        @Override
        public void onContentsChanged() {
        }
    }
}
