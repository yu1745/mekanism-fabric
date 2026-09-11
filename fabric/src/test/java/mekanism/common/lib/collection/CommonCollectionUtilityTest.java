package mekanism.common.lib.collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.SequencedMap;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CommonCollectionUtilityTest {

    @Test
    void biMultimapAddsExposeSymmetricLiveViewsAndReportChanges() {
        BiMultimap<String, Integer> map = new BiMultimap<>();
        Set<Integer> aValues = map.getValues("a");
        Set<String> oneKeys = map.getKeys(1);
        assertTrue(map.put("a", 1));
        assertFalse(map.put("a", 1));
        assertTrue(map.putAll(List.of("a", "b"), 2));
        assertFalse(map.putAll(List.of("a", "b"), 2));
        assertEquals(Set.of(1, 2), aValues);
        assertEquals(Set.of("a"), oneKeys);
        assertEquals(Set.of(Map.entry("a", 1), Map.entry("a", 2), Map.entry("b", 2)), map.getEntries());
        assertEquals(Set.of(Map.entry(1, "a"), Map.entry(2, "a"), Map.entry(2, "b")), map.getReverseEntries());
        assertTrue(map.hasAllKeys(List.of("a", "b")));
        assertFalse(map.hasAllKeys(List.of("a", "missing")));
    }

    @Test
    void biMultimapRemovalPathsKeepIndexesSymmetric() {
        BiMultimap<String, Integer> map = new BiMultimap<>();
        map.putAll(List.of("a", "b"), 1);
        map.putAll(List.of("a", "c"), 2);
        assertTrue(map.remove("a", 1));
        assertFalse(map.remove("a", 1));
        assertTrue(map.removeKey("b"));
        assertFalse(map.removeKey("missing"));
        assertTrue(map.removeValue(2));
        assertFalse(map.removeValue(2));
        assertEquals(Set.of(), map.getEntries());
        assertEquals(Set.of(), map.getReverseEntries());
        map.put("z", 9);
        map.clear();
        assertTrue(map.getAllKeys().isEmpty());
        assertTrue(map.getKeys(9).isEmpty());
    }

    @Test
    void hashListMaintainsOrderedUniquenessAndDistinctSet() {
        HashList<String> values = new HashList<>();
        assertTrue(values.add("b"));
        assertFalse(values.add("b"));
        values.add(0, "a");
        values.add(1, "b");
        values.add("c");
        assertIterableEquals(List.of("a", "b", "c"), values);
        assertEquals("b", values.get(1));
        assertNull(values.getOrNull(-1));
        assertNull(values.getOrNull(3));
        assertEquals("b", values.set(1, "d"));
        assertEquals(List.of("a", "d", "c"), values.elements());
        assertEquals("d", values.remove(1));
        assertTrue(values.remove("c"));
        assertFalse(values.remove("missing"));
        assertEquals(List.of("a"), values.elements());
    }

    @Test
    void hashListReplaceSwapAndCloneRespectOfficialTransitions() {
        HashList<String> values = new HashList<>(List.of("a", "b", "c"));
        assertTrue(values.replace("b", "d"));
        assertTrue(values.replace("d", "c"));
        assertTrue(values.replace("a", null));
        assertFalse(values.replace("c", "c"));
        assertFalse(values.replace("missing", "x"));
        assertEquals(List.of("c"), values.elements());

        values.addAll(List.of("d", "e"));
        List<String> callbacks = new ArrayList<>();
        values.swap(0, 2, (source, target) -> callbacks.add(source + ":" + target));
        values.swap(0, 0, (source, target) -> callbacks.add("invalid"));
        values.swap(-1, 1, (source, target) -> callbacks.add("invalid"));
        values.swap(0, 3, (source, target) -> callbacks.add("invalid"));
        assertEquals(List.of("e", "d", "c"), values.elements());
        assertEquals(List.of("c:e"), callbacks);

        @SuppressWarnings("unchecked")
        HashList<String> clone = (HashList<String>) values.clone();
        clone.add("f");
        assertEquals(List.of("e", "d", "c"), values.elements());
        assertEquals(List.of("e", "d", "c", "f"), clone.elements());
        assertEquals(values, List.of("e", "d", "c"));
        assertFalse(values.equals(new HashList<>(List.of("c", "d", "e"))));
        assertEquals(List.of("e", "d", "c").hashCode(), values.hashCode());
    }

    @Test
    void lruOrdersMovesRemovesAndIteratesBothDirections() {
        LRU<String> lru = new LRU<>();
        lru.add("a");
        lru.add("b");
        lru.add("c");
        assertEquals("c", lru.peek());
        assertEquals("c", lru.head());
        assertIterableEquals(List.of("c", "b", "a"), lru);
        List<String> descending = new ArrayList<>();
        lru.descendingIterator().forEachRemaining(descending::add);
        assertEquals(List.of("a", "b", "c"), descending);
        List<String> reverse = new ArrayList<>();
        lru.reverseIterate(reverse::add);
        assertEquals(List.of("a", "b", "c"), reverse);
        lru.moveUp("a");
        lru.moveUp("missing");
        assertIterableEquals(List.of("a", "c", "b"), lru);
        assertTrue(lru.remove("c"));
        assertFalse(lru.remove("missing"));
        assertTrue(lru.contains("a"));
        assertFalse(lru.contains("c"));
        assertEquals(2, lru.size());
    }

    @Test
    void emptyLruPeekHeadAndIteratorsHaveQueueSemantics() {
        LRU<String> lru = new LRU<>();
        assertNull(lru.peek());
        assertThrows(NoSuchElementException.class, lru::head);
        Iterator<String> forward = lru.iterator();
        Iterator<String> descending = lru.descendingIterator();
        assertFalse(forward.hasNext());
        assertFalse(descending.hasNext());
        assertThrows(NoSuchElementException.class, forward::next);
        assertThrows(NoSuchElementException.class, descending::next);
    }

    @Test
    void emptySequencedMapIsSingletonWithEmptySequencedViews() {
        SequencedMap<String, Integer> map = EmptySequencedMap.emptyMap();
        assertSame(map, EmptySequencedMap.emptyMap());
        assertSame(map, map.reversed());
        assertTrue(map.sequencedKeySet().isEmpty());
        assertTrue(map.sequencedValues().isEmpty());
        assertTrue(map.sequencedEntrySet().isEmpty());
        assertNull(map.firstEntry());
        assertNull(map.lastEntry());
        assertNull(map.pollFirstEntry());
        assertNull(map.pollLastEntry());
        assertEquals(7, map.getOrDefault("missing", 7));
        assertTrue(map.equals(new LinkedHashMap<>()));
        assertFalse(map.equals(new HashMap<>()));
        assertEquals(0, map.hashCode());
    }

    @Test
    void emptySequencedMapNoOpsValidateCallbacksAndRejectsMutation() {
        SequencedMap<String, Integer> map = EmptySequencedMap.emptyMap();
        AtomicInteger calls = new AtomicInteger();
        map.clear();
        map.forEach((key, value) -> calls.incrementAndGet());
        map.replaceAll((key, value) -> { calls.incrementAndGet(); return value; });
        assertEquals(0, calls.get());
        assertThrows(NullPointerException.class, () -> map.forEach(null));
        assertThrows(NullPointerException.class, () -> map.replaceAll(null));
        assertThrows(UnsupportedOperationException.class, () -> map.put("a", 1));
        assertThrows(UnsupportedOperationException.class, () -> map.putIfAbsent("a", 1));
        assertThrows(UnsupportedOperationException.class, () -> map.remove("a", 1));
        assertThrows(UnsupportedOperationException.class, () -> map.replace("a", 1));
        assertThrows(UnsupportedOperationException.class, () -> map.compute("a", (key, value) -> 1));
        assertThrows(UnsupportedOperationException.class, () -> map.merge("a", 1, Integer::sum));
        assertThrows(UnsupportedOperationException.class, () -> map.putFirst("a", 1));
        assertThrows(UnsupportedOperationException.class, () -> map.putLast("a", 1));
    }
}
