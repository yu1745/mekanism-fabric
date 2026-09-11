package mekanism.common.lib.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SequencedCollection;
import mekanism.api.codec.DependentMapCodec;
import org.junit.jupiter.api.Test;

class CodecBoundaryTest {

    private record Choice(String kind, Object value) {
    }

    private static JsonElement json(String value) {
        return JsonParser.parseString(value);
    }

    @Test
    void dependentCodecSelectsAfterDependencyAndRoundTripsBothShapes() {
        List<String> events = new ArrayList<>();
        Codec<String> dependency = Codec.STRING.xmap(value -> {
            events.add("dependency:" + value);
            return value;
        }, value -> value);
        DependentMapCodec<Choice, String> codec = new DependentMapCodec<>("element", kind -> {
            events.add("selector:" + kind);
            return switch (kind) {
                case "int" -> Codec.INT.xmap(value -> new Choice("int", value), value -> (Integer) value.value());
                case "text" -> Codec.STRING.xmap(value -> new Choice("text", value), value -> (String) value.value());
                default -> Codec.unit(new Choice(kind, "unused"));
            };
        }, dependency.fieldOf("kind"), Choice::kind);

        for (String encoded : List.of("{\"kind\":\"int\",\"element\":7}", "{\"kind\":\"text\",\"element\":\"seven\"}")) {
            events.clear();
            Choice decoded = codec.codec().parse(JsonOps.INSTANCE, json(encoded)).getOrThrow();
            assertEquals(List.of("dependency:" + decoded.kind(), "selector:" + decoded.kind()), events);
            assertEquals(json(encoded), codec.codec().encodeStart(JsonOps.INSTANCE, decoded).getOrThrow());
        }
    }

    @Test
    void dependentCodecReportsMissingOrInvalidFields() {
        DependentMapCodec<Integer, String> codec = new DependentMapCodec<>("element", ignored -> Codec.INT,
              Codec.STRING.fieldOf("kind"), ignored -> "int");
        for (String malformed : List.of("{\"kind\":\"int\"}", "{\"element\":1}",
              "{\"kind\":1,\"element\":1}")) {
            assertTrue(codec.codec().parse(JsonOps.INSTANCE, json(malformed)).error().isPresent(), malformed);
        }
    }

    @Test
    void sequencedCodecEnforcesEncodeBoundsAndReturnsOrderedImmutableList() {
        SequencedCollectionCodec<Integer> codec = new SequencedCollectionCodec<>(Codec.INT, 1, 2);
        assertTrue(codec.encodeStart(JsonOps.INSTANCE, List.<Integer>of()).error().isPresent());
        assertTrue(codec.encodeStart(JsonOps.INSTANCE, List.of(1, 2, 3)).error().isPresent());
        assertEquals(json("[1,2]"), codec.encodeStart(JsonOps.INSTANCE, List.of(1, 2)).getOrThrow());
        SequencedCollection<Integer> decoded = codec.parse(JsonOps.INSTANCE, json("[2,1]")).getOrThrow();
        assertEquals(List.of(2, 1), decoded);
        assertThrows(UnsupportedOperationException.class, () -> decoded.add(3));
    }

    @Test
    void sequencedCodecRetainsPartialElementsAndUsesDistinctDecodeCounts() {
        DataResult<Pair<SequencedCollection<Integer>, JsonElement>> partial =
              new SequencedCollectionCodec<>(Codec.INT, 0, 3).decode(JsonOps.INSTANCE, json("[1,\"bad\",2]"));
        assertTrue(partial.error().isPresent());
        Pair<SequencedCollection<Integer>, JsonElement> retained = partial.resultOrPartial().orElseThrow();
        assertEquals(List.of(1, 2), retained.getFirst());
        assertEquals(json("[\"bad\"]"), retained.getSecond());

        assertTrue(new SequencedCollectionCodec<>(Codec.INT, 3, 4)
              .decode(JsonOps.INSTANCE, json("[1,\"bad\",2]")).error().isPresent());
        DataResult<Pair<SequencedCollection<Integer>, JsonElement>> tooMany =
              new SequencedCollectionCodec<>(Codec.INT, 0, 2).decode(JsonOps.INSTANCE, json("[1,\"bad\",2]"));
        assertTrue(tooMany.error().orElseThrow().message().contains("List is too long: 3"));
        assertEquals(List.of(1, 2), tooMany.resultOrPartial().orElseThrow().getFirst());
    }

    @Test
    void droppingMapKeepsFirstDuplicateAndReportsOnlyRelevantFailures() {
        Codec<String> foldedKey = Codec.STRING.xmap(String::toLowerCase, value -> value);
        Codec<Integer> partialValue = Codec.STRING.comapFlatMap(value -> switch (value) {
            case "partial" -> DataResult.error(() -> "partial value", 99);
            case "bad" -> DataResult.error(() -> "bad value");
            default -> DataResult.success(Integer.parseInt(value));
        }, String::valueOf);
        DroppingUnboundedMapCodec<String, Integer> codec = new DroppingUnboundedMapCodec<>(foldedKey, partialValue);

        DataResult<Pair<Map<String, Integer>, JsonElement>> malformed =
              codec.decode(JsonOps.INSTANCE, json("{\"ok\":\"1\",\"broken\":\"bad\"}"));
        assertTrue(malformed.error().orElseThrow().message().contains("broken"));
        assertEquals(Map.of("ok", 1), malformed.resultOrPartial().orElseThrow().getFirst());

        Map<String, Integer> duplicate = codec.parse(JsonOps.INSTANCE,
              json("{\"A\":\"1\",\"a\":\"partial\"}")).getOrThrow();
        assertEquals(Map.of("a", 1), duplicate);
        JsonElement encoded = codec.encodeStart(JsonOps.INSTANCE, Map.of("one", 1)).getOrThrow();
        assertEquals(Map.of("one", 1), codec.parse(JsonOps.INSTANCE, encoded).getOrThrow());
    }
}
