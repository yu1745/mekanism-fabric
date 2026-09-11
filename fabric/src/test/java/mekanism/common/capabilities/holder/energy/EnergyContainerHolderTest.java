package mekanism.common.capabilities.holder.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import mekanism.api.RelativeSide;
import mekanism.api.energy.IEnergyContainer;
import mekanism.common.capabilities.energy.BasicEnergyContainer;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

class EnergyContainerHolderTest {
    @Test
    void unconfiguredHolderExposesAllContainersWithoutConsultingFacing() {
        AtomicInteger facingReads = new AtomicInteger();
        EnergyContainerHolder holder = new EnergyContainerHolder(() -> {
            facingReads.incrementAndGet();
            return Direction.NORTH;
        });
        IEnergyContainer first = BasicEnergyContainer.create(100, null);
        IEnergyContainer second = BasicEnergyContainer.create(200, null);
        holder.addContainer(first);
        holder.addContainer(second);

        List<IEnergyContainer> internal = holder.getEnergyContainers(null);
        assertEquals(List.of(first, second), internal);
        for (Direction direction : Direction.values()) {
            assertSame(internal, holder.getEnergyContainers(direction));
            assertEquals(List.of(first, second), holder.getEnergyContainers(direction));
            assertTrue(holder.canInsert(direction));
            assertTrue(holder.canExtract(direction));
        }
        assertTrue(holder.canInsert(null));
        assertTrue(holder.canExtract(null));
        assertEquals(0, facingReads.get());

        internal.remove(first);
        assertEquals(List.of(second), holder.getEnergyContainers(null));
        for (Direction direction : Direction.values()) {
            assertEquals(List.of(second), holder.getEnergyContainers(direction));
        }
    }

    @Test
    void directionalHolderFiltersExternalAccessButKeepsInternalInsertionOrder() {
        EnergyContainerHolder holder = new EnergyContainerHolder(() -> Direction.NORTH);
        IEnergyContainer front = BasicEnergyContainer.create(100, null);
        IEnergyContainer left = BasicEnergyContainer.create(100, null);
        IEnergyContainer shared = BasicEnergyContainer.create(100, null);
        IEnergyContainer internalOnly = BasicEnergyContainer.create(100, null);
        holder.addContainer(front, RelativeSide.FRONT);
        holder.addContainer(left, RelativeSide.LEFT);
        holder.addContainer(shared, RelativeSide.FRONT, RelativeSide.TOP);
        holder.addContainer(internalOnly);

        assertEquals(List.of(front, left, shared, internalOnly), holder.getEnergyContainers(null));
        assertEquals(List.of(front, shared), holder.getEnergyContainers(Direction.NORTH));
        assertEquals(List.of(left), holder.getEnergyContainers(Direction.EAST));
        assertEquals(List.of(shared), holder.getEnergyContainers(Direction.UP));
        List<IEnergyContainer> empty = holder.getEnergyContainers(Direction.SOUTH);
        assertTrue(empty.isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> empty.add(front));
        assertTrue(holder.getEnergyContainers(Direction.WEST).isEmpty());
        assertTrue(holder.getEnergyContainers(Direction.DOWN).isEmpty());
    }

    @Test
    void sideVisibilityTracksFacingSupplierAtLookupTime() {
        AtomicReference<Direction> facing = new AtomicReference<>(Direction.NORTH);
        EnergyContainerHolder holder = new EnergyContainerHolder(facing::get);
        IEnergyContainer front = BasicEnergyContainer.create(100, null);
        IEnergyContainer right = BasicEnergyContainer.create(100, null);
        holder.addContainer(front, RelativeSide.FRONT);
        holder.addContainer(right, RelativeSide.RIGHT);

        for (Direction horizontal : Direction.Plane.HORIZONTAL) {
            facing.set(horizontal);
            assertEquals(List.of(front), holder.getEnergyContainers(horizontal));
            assertEquals(List.of(right), holder.getEnergyContainers(horizontal.getCounterClockWise()));
            assertTrue(holder.getEnergyContainers(horizontal.getOpposite()).isEmpty());
        }
    }

    @Test
    void duplicateMappingsAndLiveListsPreserveOfficialHolderBehavior() {
        EnergyContainerHolder holder = new EnergyContainerHolder(() -> Direction.NORTH);
        IEnergyContainer repeated = BasicEnergyContainer.create(100, null);
        holder.addContainer(repeated, RelativeSide.FRONT, RelativeSide.FRONT);

        List<IEnergyContainer> front = holder.getEnergyContainers(Direction.NORTH);
        assertEquals(List.of(repeated, repeated), front);
        front.remove(0);
        assertEquals(List.of(repeated), holder.getEnergyContainers(Direction.NORTH));
        assertEquals(List.of(repeated), holder.getEnergyContainers(null));
    }
}
