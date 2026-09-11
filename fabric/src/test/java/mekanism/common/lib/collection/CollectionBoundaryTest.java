package mekanism.common.lib.collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class CollectionBoundaryTest {

    @Test
    void biLongMultimapMutationResultsAndSortedBidirectionalExtremes() {
        BiLongMultimap<String> map = new BiLongMultimap<>();
        assertTrue(map.put(-1, "a"));
        assertFalse(map.put(-1, "a"));
        assertFalse(map.remove(2, "a"));
        assertTrue(map.remove(-1, "a"));
        assertFalse(map.remove(-1, "a"));

        LongArrayList keys = new LongArrayList(new long[]{Long.MAX_VALUE, -16, Long.MIN_VALUE, -1, 0, 16});
        assertTrue(map.putAll(keys, "extreme"));
        assertFalse(map.putAll(keys, "extreme"));
        List<Long> sorted = List.of(Long.MIN_VALUE, -16L, -1L, 0L, 16L, Long.MAX_VALUE);
        assertIterableEquals(sorted, map.getAllKeys());
        assertIterableEquals(sorted, map.getKeys("extreme"));
        for (long key : keys) {
            assertEquals(java.util.Set.of("extreme"), map.getValues(key));
        }
        assertTrue(map.removeKey(Long.MIN_VALUE));
        assertTrue(map.removeValue("extreme"));
        assertTrue(map.isEmpty());
        assertNull(map.getKeys("extreme"));
    }

    @Test
    void filterTransformIteratorFiltersNullsPreservesOrderAndExhausts() {
        Iterator<String> iterator = new FilterTransformIterator<>(List.of(1, 2, 3, 4).iterator()) {
            @Override
            protected String filterTransform(Integer value) {
                return value % 2 == 0 ? "v" + value : null;
            }
        };
        assertTrue(iterator.hasNext());
        assertTrue(iterator.hasNext(), "hasNext must not consume the prefetched element");
        assertEquals("v2", iterator.next());
        assertEquals("v4", iterator.next());
        assertFalse(iterator.hasNext());
        assertThrows(NoSuchElementException.class, iterator::next);
    }

    @Test
    void indexedCuboidMapBoundariesReplacementAndMixedRemovalStayConsistent() {
        IndexedCuboidMap<String> map = new IndexedCuboidMap<>();
        BlockPos negativeCenter = new BlockPos(-16, 200, -1);
        map.track("old", negativeCenter, -32, -5, -16, -1, 400, 15);
        assertEquals(List.of("old"), values(map.find(new BlockPos(-32, -5, -16))));
        assertEquals(List.of("old"), values(map.find(new BlockPos(-1, 400, 15))));
        assertTrue(values(map.find(new BlockPos(-33, 0, 0))).isEmpty());
        assertTrue(values(map.find(new BlockPos(-16, 401, -1))).isEmpty(), "chunk index is X/Z-only but the box filter includes Y");
        assertTrue(values(map.find(new BlockPos(0, 0, 0))).isEmpty());
        assertEquals(List.of("old"), values(map.allCenteredInChunk(-1, -1)));
        assertTrue(values(map.allCenteredInChunk(-2, -1)).isEmpty());

        map.track("replacement", negativeCenter, -32, -5, -16, -1, 400, 15);
        assertEquals(List.of("replacement"), values(map.find(negativeCenter)));
        assertEquals(1, map.values().size());
        assertThrows(IllegalArgumentException.class,
              () -> map.track("bad", new BlockPos(0, 0, 0), 1, -1, -1, 2, 1, 1));

        BlockPos removeByValue = new BlockPos(32, 1, 0);
        BlockPos removeAt = new BlockPos(48, 2, 0);
        BlockPos removeIf = new BlockPos(64, 3, 0);
        BlockPos survivor = new BlockPos(80, 4, 0);
        map.track("value", removeByValue, 0);
        map.track("at", removeAt, 0);
        map.track("predicate", removeIf, 0);
        map.track("survivor", survivor, 0);
        map.remove("value");
        assertTrue(map.removeAt(removeAt));
        assertFalse(map.removeAt(removeAt));
        map.removeIf("predicate"::equals);
        assertEquals(java.util.Set.of("replacement", "survivor"), java.util.Set.copyOf(map.values()));
        assertTrue(values(map.find(removeByValue)).isEmpty());
        assertTrue(values(map.find(removeAt)).isEmpty());
        assertTrue(values(map.find(removeIf)).isEmpty());
        assertEquals(List.of("survivor"), values(map.find(survivor)));
        assertFalse(map.indexIsEmpty());
        map.clear();
        assertTrue(map.isEmpty());
        assertTrue(map.indexIsEmpty());
        assertTrue(values(map.find(negativeCenter)).isEmpty());
        assertTrue(values(map.find(survivor)).isEmpty());
    }

    private static <T> List<T> values(Iterator<T> iterator) {
        List<T> values = new ArrayList<>();
        iterator.forEachRemaining(values::add);
        return values;
    }
}
