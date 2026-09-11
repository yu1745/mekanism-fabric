package mekanism.common.capabilities.holder.heat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import mekanism.api.RelativeSide;
import mekanism.api.heat.IHeatCapacitor;
import mekanism.common.capabilities.heat.BasicHeatCapacitor;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

class HeatCapacitorHolderTest {

    @Test
    void unconfiguredHolderExposesAllCapacitorsWithoutConsultingFacing() {
        AtomicInteger facingReads = new AtomicInteger();
        HeatCapacitorHolder holder = new HeatCapacitorHolder(() -> {
            facingReads.incrementAndGet();
            return Direction.NORTH;
        });
        IHeatCapacitor first = capacitor(1);
        IHeatCapacitor second = capacitor(2);
        holder.addCapacitor(first);
        holder.addCapacitor(second);

        List<IHeatCapacitor> internal = holder.getHeatCapacitors(null);
        assertEquals(List.of(first, second), internal);
        for (Direction direction : Direction.values()) {
            assertSame(internal, holder.getHeatCapacitors(direction));
        }
        assertEquals(0, facingReads.get());

        internal.remove(first);
        assertEquals(List.of(second), holder.getHeatCapacitors(null));
        for (Direction direction : Direction.values()) {
            assertEquals(List.of(second), holder.getHeatCapacitors(direction));
        }
    }

    @Test
    void directionalHolderFiltersExternalAccessButKeepsInternalOrder() {
        HeatCapacitorHolder holder = new HeatCapacitorHolder(() -> Direction.NORTH);
        IHeatCapacitor front = capacitor(1);
        IHeatCapacitor left = capacitor(1);
        IHeatCapacitor shared = capacitor(1);
        IHeatCapacitor internalOnly = capacitor(1);
        holder.addCapacitor(front, RelativeSide.FRONT);
        holder.addCapacitor(left, RelativeSide.LEFT);
        holder.addCapacitor(shared, RelativeSide.FRONT, RelativeSide.TOP);
        holder.addCapacitor(internalOnly);

        assertEquals(List.of(front, left, shared, internalOnly), holder.getHeatCapacitors(null));
        assertEquals(List.of(front, shared), holder.getHeatCapacitors(Direction.NORTH));
        assertEquals(List.of(left), holder.getHeatCapacitors(Direction.EAST));
        assertEquals(List.of(shared), holder.getHeatCapacitors(Direction.UP));
        List<IHeatCapacitor> empty = holder.getHeatCapacitors(Direction.SOUTH);
        assertTrue(empty.isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> empty.add(front));
        assertTrue(holder.getHeatCapacitors(Direction.WEST).isEmpty());
        assertTrue(holder.getHeatCapacitors(Direction.DOWN).isEmpty());
    }

    @Test
    void sideVisibilityTracksFacingSupplierAtLookupTime() {
        AtomicReference<Direction> facing = new AtomicReference<>(Direction.NORTH);
        HeatCapacitorHolder holder = new HeatCapacitorHolder(facing::get);
        IHeatCapacitor front = capacitor(1);
        IHeatCapacitor right = capacitor(1);
        holder.addCapacitor(front, RelativeSide.FRONT);
        holder.addCapacitor(right, RelativeSide.RIGHT);

        for (Direction horizontal : Direction.Plane.HORIZONTAL) {
            facing.set(horizontal);
            assertEquals(List.of(front), holder.getHeatCapacitors(horizontal));
            assertEquals(List.of(right), holder.getHeatCapacitors(horizontal.getCounterClockWise()));
            assertTrue(holder.getHeatCapacitors(horizontal.getOpposite()).isEmpty());
        }
    }

    @Test
    void verticalFacingsUseOfficialRelativeProjection() {
        AtomicReference<Direction> facing = new AtomicReference<>(Direction.UP);
        HeatCapacitorHolder holder = new HeatCapacitorHolder(facing::get);
        List<IHeatCapacitor> capacitors = List.of(capacitor(1), capacitor(1), capacitor(1), capacitor(1), capacitor(1), capacitor(1));
        for (int i = 0; i < RelativeSide.values().length; i++) {
            holder.addCapacitor(capacitors.get(i), RelativeSide.values()[i]);
        }
        Map<Direction, List<Direction>> expected = Map.of(
              Direction.UP, List.of(Direction.UP, Direction.EAST, Direction.WEST, Direction.DOWN, Direction.SOUTH, Direction.NORTH),
              Direction.DOWN, List.of(Direction.DOWN, Direction.EAST, Direction.WEST, Direction.UP, Direction.NORTH, Direction.SOUTH)
        );
        for (Direction vertical : List.of(Direction.UP, Direction.DOWN)) {
            facing.set(vertical);
            List<Direction> physicalSides = expected.get(vertical);
            for (int i = 0; i < physicalSides.size(); i++) {
                assertEquals(List.of(capacitors.get(i)), holder.getHeatCapacitors(physicalSides.get(i)));
            }
        }
    }

    @Test
    void duplicateMappingsAndLiveListsPreserveOfficialBehavior() {
        HeatCapacitorHolder holder = new HeatCapacitorHolder(() -> Direction.NORTH);
        IHeatCapacitor repeated = capacitor(1);
        holder.addCapacitor(repeated, RelativeSide.FRONT, RelativeSide.FRONT);

        List<IHeatCapacitor> front = holder.getHeatCapacitors(Direction.NORTH);
        assertEquals(List.of(repeated, repeated), front);
        front.remove(0);
        assertEquals(List.of(repeated), holder.getHeatCapacitors(Direction.NORTH));
        assertEquals(List.of(repeated), holder.getHeatCapacitors(null));
    }

    private static IHeatCapacitor capacitor(double capacity) {
        return BasicHeatCapacitor.create(capacity, null, null);
    }
}
