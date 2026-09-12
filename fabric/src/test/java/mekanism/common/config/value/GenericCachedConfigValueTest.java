package mekanism.common.config.value;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.function.Supplier;
import mekanism.common.config.BaseMekanismConfig;
import mekanism.common.config.MekanismConfigType;
import mekanism.fabric.config.MekanismConfigSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GenericCachedConfigValueTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void cachedConfigValueUsesStrictImmutableListSchemaAndAtomicLifecycle() throws IOException {
        Supplier<List<? extends String>> validDefault = () -> List.of("default");
        Supplier<String> validNewElement = () -> "new";
        Predicate<Object> validElement = element -> element instanceof String string && !string.isBlank();
        assertThrows(NullPointerException.class, () -> new MekanismConfigSpec.Builder()
              .defineListAllowEmpty("entries", null, validNewElement, validElement));
        assertThrows(NullPointerException.class, () -> new MekanismConfigSpec.Builder()
              .defineListAllowEmpty("entries", validDefault, null, validElement));
        assertThrows(NullPointerException.class, () -> new MekanismConfigSpec.Builder()
              .defineListAllowEmpty("entries", validDefault, validNewElement, null));
        MekanismConfigSpec.Builder invalidBuilder = new MekanismConfigSpec.Builder();
        AtomicInteger invalidDefaultCalls = new AtomicInteger();
        assertThrows(IllegalArgumentException.class, () -> invalidBuilder.defineListAllowEmpty("entries", () -> {
            invalidDefaultCalls.incrementAndGet();
            return Arrays.asList("valid", null);
        }, validNewElement, validElement));
        assertEquals(1, invalidDefaultCalls.get());
        assertEquals(0, invalidBuilder.build().size());
        MekanismConfigSpec.Builder nullDefaultBuilder = new MekanismConfigSpec.Builder();
        AtomicInteger nullDefaultCalls = new AtomicInteger();
        assertThrows(IllegalArgumentException.class, () -> nullDefaultBuilder.<String>defineListAllowEmpty("entries", () -> {
            nullDefaultCalls.incrementAndGet();
            return null;
        }, validNewElement, validElement));
        assertEquals(1, nullDefaultCalls.get());
        assertEquals(0, nullDefaultBuilder.build().size());

        ArrayList<String> suppliedDefault = new ArrayList<>(List.of("default"));
        AtomicInteger defaultCalls = new AtomicInteger();
        AtomicInteger newElementCalls = new AtomicInteger();
        MekanismConfigSpec.Builder builder = new MekanismConfigSpec.Builder();
        builder.translation("test.lists").comment("List category").push("lists");
        MekanismConfigSpec.ConfigValue<List<? extends String>> internal = builder.translation("test.entries")
              .comment("List entries").gameRestart().defineListAllowEmpty("entries", () -> {
                  defaultCalls.incrementAndGet();
                  return suppliedDefault;
              }, () -> {
                  newElementCalls.incrementAndGet();
                  return "editor-only";
              }, validElement);
        assertThrows(IllegalStateException.class, internal::get);
        builder.pop();
        MekanismConfigSpec spec = builder.build();
        TestConfig config = new TestConfig(spec);
        CachedConfigValue<List<? extends String>> cached = CachedConfigValue.wrap(config, internal);

        assertEquals(1, defaultCalls.get());
        assertEquals(0, newElementCalls.get());
        suppliedDefault.add("caller-mutation");
        assertEquals(List.of("default"), internal.getDefault());
        assertEquals(List.of("default"), internal.get());
        assertThrows(UnsupportedOperationException.class, internal.getDefault()::clear);
        assertEquals(Set.of("lists.entries"), spec.paths());
        assertEquals("test.entries", spec.translation("lists.entries"));
        assertEquals(MekanismConfigSpec.RestartScope.GAME, spec.restartScope("lists.entries"));
        AtomicInteger frozenSupplierCalls = new AtomicInteger();
        assertThrows(IllegalStateException.class, () -> builder.defineListAllowEmpty("late", () -> {
            frozenSupplierCalls.incrementAndGet();
            return List.of("late");
        }, validNewElement, validElement));
        assertEquals(0, frozenSupplierCalls.get());

        Path file = temporaryDirectory.resolve("generic-list.toml");
        Files.writeString(file, "[lists]\nentries = []\n");
        MekanismConfigSpec.LoadResult emptyLoad = spec.load(file);
        assertEquals(0, emptyLoad.correctedValues());
        assertEquals(List.of(), internal.get());
        assertEquals(List.of(), cached.getOrDefault());
        assertThrows(UnsupportedOperationException.class, internal.get()::clear);

        Files.writeString(file, "[lists]\nentries = [\"loaded\", \"values\"]\n");
        MekanismConfigSpec.LoadResult validLoad = spec.load(file);
        assertEquals(0, validLoad.correctedValues());
        config.clearCache(false);
        assertEquals(List.of("loaded", "values"), cached.get());
        assertThrows(UnsupportedOperationException.class, internal.get()::clear);

        ArrayList<String> mutableSet = new ArrayList<>(List.of("set-value"));
        cached.set(mutableSet);
        assertSame(mutableSet, cached.get());
        mutableSet.add("mutated-after-set");
        assertEquals(List.of("set-value"), internal.get());
        assertEquals(List.of("set-value", "mutated-after-set"), cached.get());
        assertThrows(UnsupportedOperationException.class, internal.get()::clear);
        cached.clearCache(false);
        assertEquals(List.of("set-value"), cached.get());
        assertThrows(IllegalArgumentException.class, () -> cached.set(Arrays.asList("valid", null)));
        assertEquals(List.of("set-value"), internal.get());
        assertEquals(List.of("set-value"), cached.get());
        spec.save();
        String afterFailedSet = Files.readString(file);
        assertTrue(afterFailedSet.contains("\"set-value\""));
        assertFalse(afterFailedSet.contains("mutated-after-set"));

        config.clearCache(false);
        assertEquals(List.of("set-value"), cached.get());
        Path blockingParent = temporaryDirectory.resolve("not-a-directory");
        Files.writeString(blockingParent, "blocking file");
        assertThrows(UncheckedIOException.class, () -> spec.load(blockingParent.resolve("cannot-publish.toml")));
        assertEquals(List.of("set-value"), internal.get());
        assertEquals(List.of("set-value"), cached.get());

        Files.writeString(file, "[lists]\nentries = [\"valid\", \"\"]\n");
        MekanismConfigSpec.LoadResult invalidElement = spec.load(file);
        assertEquals(1, invalidElement.correctedValues());
        assertEquals(List.of("default"), internal.get());
        String correctedElement = Files.readString(file);
        assertTrue(correctedElement.contains("\"default\""));
        assertFalse(correctedElement.contains("\"valid\""));
        assertTrue(correctedElement.contains("List category"));
        assertTrue(correctedElement.contains("List entries"));

        Files.writeString(file, "[lists]\nentries = 7\n");
        MekanismConfigSpec.LoadResult nonList = spec.load(file);
        assertEquals(1, nonList.correctedValues());
        assertEquals(List.of("default"), internal.get());
        assertTrue(Files.readString(file).contains("\"default\""));
        assertEquals(1, defaultCalls.get());
        assertEquals(0, newElementCalls.get());

        config.clearCache(false);
        assertEquals(List.of("default"), cached.get());
        AtomicInteger invalidations = new AtomicInteger();
        cached.addInvalidationListener(invalidations::incrementAndGet);
        cached.clearCache(false);
        assertEquals(0, invalidations.get());
        internal.set(List.of("changed"));
        cached.clearCache(false);
        assertEquals(1, invalidations.get());
        assertEquals(List.of("changed"), cached.get());
        internal.set(new ArrayList<>(List.of("changed")));
        cached.clearCache(false);
        assertEquals(1, invalidations.get());
    }

    @Test
    void cachedResolvableListValueRetriesNullAndPreservesOfficialCacheInvalidation() throws IOException {
        MekanismConfigSpec.Builder builder = new MekanismConfigSpec.Builder();
        MekanismConfigSpec.ConfigValue<List<? extends String>> internal = builder.defineListAllowEmpty("encoded",
              () -> List.of("default"), () -> "new", element -> element instanceof String);
        MekanismConfigSpec spec = builder.build();
        TestConfig config = new TestConfig(spec);
        ResolvingListValue cached = new ResolvingListValue(config, internal);

        assertEquals("default", cached.getOrDefault());
        internal.set(List.of("unloaded-current"));
        assertEquals("default", cached.getOrDefault());
        assertEquals(2, cached.resolveCalls.get());
        assertEquals("unloaded-current", cached.get());
        assertEquals(3, cached.resolveCalls.get());
        assertEquals("unloaded-current", cached.get());
        assertEquals(3, cached.resolveCalls.get());

        internal.set(List.of("null"));
        cached.clearCache(false);
        assertNull(cached.get());
        assertNull(cached.get());
        assertEquals(5, cached.resolveCalls.get());

        Path file = temporaryDirectory.resolve("resolvable-list.toml");
        Files.writeString(file, "encoded = [\"loaded\", \"state\"]\n");
        assertEquals(0, spec.load(file).correctedValues());
        assertEquals("loaded|state", cached.getOrDefault());
        assertEquals(6, cached.resolveCalls.get());
        assertEquals("loaded|state", cached.get());
        assertEquals(6, cached.resolveCalls.get());

        String callerValue = new String("zeta|alpha");
        cached.set(callerValue);
        assertEquals(1, cached.encodeCalls.get());
        assertSame(callerValue, cached.get());
        assertEquals(List.of("zeta", "alpha"), internal.get());
        assertThrows(UnsupportedOperationException.class, internal.get()::clear);

        AtomicInteger invalidations = new AtomicInteger();
        cached.addInvalidationListener(invalidations::incrementAndGet);
        cached.clearCache(false);
        assertEquals(0, invalidations.get());
        assertEquals("zeta|alpha", cached.get());
        assertEquals(7, cached.resolveCalls.get());
        internal.set(List.of("changed"));
        cached.clearCache(false);
        assertEquals(1, invalidations.get());
        assertEquals("changed", cached.get());
        assertEquals(8, cached.resolveCalls.get());
        internal.set(new ArrayList<>(List.of("changed")));
        cached.clearCache(false);
        assertEquals(1, invalidations.get());
        assertEquals(9, cached.resolveCalls.get());
    }

    @Test
    void cachedMapValueResolvesInputOrderAndEncodesGloballySortedImmutableLists() throws IOException {
        MekanismConfigSpec.Builder builder = new MekanismConfigSpec.Builder();
        MekanismConfigSpec.ConfigValue<List<? extends String>> internal = builder.defineListAllowEmpty("encoded",
              () -> List.of("default=1"), () -> "new=0", element -> element instanceof String);
        MekanismConfigSpec spec = builder.build();
        TestConfig config = new TestConfig(spec);
        ParsingMapValue cached = new ParsingMapValue(config, internal);
        Path file = temporaryDirectory.resolve("map-list.toml");
        Files.writeString(file, "encoded = [\"duplicate=1\", \"malformed\", \"ignore=7\", "
              + "\"duplicate=2\", \"bad=NaN\", \"other=3\", \"duplicate=4\"]\n");

        MekanismConfigSpec.LoadResult loaded = spec.load(file);
        assertEquals(0, loaded.correctedValues());
        Map<String, Integer> first = cached.get();
        assertEquals(Map.of("duplicate", 4, "other", 3), first);
        assertSame(first, cached.get());
        internal.set(List.of("duplicate=8", "duplicate=9", "other=10"));
        assertSame(first, cached.get());
        cached.clearCache(false);
        assertEquals(Map.of("duplicate", 9, "other", 10), cached.get());
        assertNotSame(first, cached.get());

        LinkedHashMap<String, Integer> callerMap = new LinkedHashMap<>();
        callerMap.put("zeta", 9);
        callerMap.put("alpha", 2);
        callerMap.put("middle", 5);
        cached.set(callerMap);
        assertSame(callerMap, cached.get());
        assertEquals(List.of("alpha=2", "middle=5", "zeta=9"), internal.get());
        assertThrows(UnsupportedOperationException.class, internal.get()::clear);
        spec.save();
        String saved = Files.readString(file);
        assertTrue(saved.indexOf("alpha=2") < saved.indexOf("middle=5"));
        assertTrue(saved.indexOf("middle=5") < saved.indexOf("zeta=9"));
        cached.clearCache(false);
        assertEquals(Map.of("alpha", 2, "middle", 5, "zeta", 9), cached.get());

        cached.set(Map.of());
        assertEquals(List.of(), internal.get());
        cached.clearCache(false);
        assertEquals(Map.of(), cached.get());
    }

    @Test
    void oredictionificatorValueNormalizesRootLocaleCorrectionOrderingAndSetAliasing() throws IOException {
        Locale previousLocale = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        try {
            AtomicInteger defaultCalls = new AtomicInteger();
            LinkedHashMap<String, List<String>> defaults = new LinkedHashMap<>();
            defaults.put("TESTI", new ArrayList<>(List.of("BETA/", "bad space", "ALPHA/")));
            defaults.put("C", List.of("INGOTS/", "ingots/"));
            TestConfig config = new TestConfig();
            MekanismConfigSpec.Builder builder = new MekanismConfigSpec.Builder();
            builder.push("oredictionificator");
            CachedOredictionificatorConfigValue cached = CachedOredictionificatorConfigValue.define(config, builder,
                  "validItemFilters", () -> {
                      defaultCalls.incrementAndGet();
                      return defaults;
                  });
            builder.pop();
            MekanismConfigSpec spec = builder.build();
            config.spec = spec;
            List<String> expectedDefault = List.of("c:ingots/", "c:ingots/", "testi:alpha/", "testi:beta/");

            assertEquals(1, defaultCalls.get());
            assertEquals(expectedDefault, cached.internal.getDefault());
            assertThrows(UnsupportedOperationException.class, cached.internal.getDefault()::clear);
            Map<String, List<String>> defaultResolved = cached.getOrDefault();
            assertEquals(List.of("alpha/", "beta/"), defaultResolved.get("testi"));
            assertEquals(List.of("ingots/", "ingots/"), defaultResolved.get("c"));
            assertFalse(defaultResolved.containsKey("testı"));
            assertEquals(1, defaultCalls.get());

            Path file = temporaryDirectory.resolve("oredictionificator.toml");
            Files.writeString(file, "[oredictionificator]\nvalidItemFilters = [\"VALID:path\", \"bad space\"]\n");
            MekanismConfigSpec.LoadResult corrected = spec.load(file);
            assertEquals(1, corrected.correctedValues());
            assertEquals(expectedDefault, cached.internal.get());
            String correctedToml = Files.readString(file);
            assertFalse(correctedToml.contains("bad space"));
            assertTrue(correctedToml.indexOf("c:ingots/") < correctedToml.indexOf("testi:alpha/"));
            cached.clearCache(false);
            assertEquals(defaultResolved, cached.get());

            Files.writeString(file, "[oredictionificator]\nvalidItemFilters = [\"B:SECOND\", \"A:first\", "
                  + "\"B:SECOND\", \"b:first\"]\n");
            assertEquals(0, spec.load(file).correctedValues());
            cached.clearCache(false);
            Map<String, List<String>> ordered = cached.get();
            assertEquals(List.of("second", "second", "first"), ordered.get("b"));
            assertEquals(List.of("first"), ordered.get("a"));

            Files.writeString(file, "[oredictionificator]\nvalidItemFilters = []\n");
            assertEquals(0, spec.load(file).correctedValues());
            cached.clearCache(false);
            assertEquals(Map.of(), cached.get());
            assertEquals(List.of(), cached.internal.get());
            assertThrows(UnsupportedOperationException.class, cached.internal.get()::clear);

            ArrayList<String> zetaPaths = new ArrayList<>(List.of("SECOND", "bad space", "FIRST", "FIRST"));
            LinkedHashMap<String, List<String>> callerMap = new LinkedHashMap<>();
            callerMap.put("ZETA", zetaPaths);
            callerMap.put("ALPHA", new ArrayList<>(List.of("BETA", "alpha")));
            cached.set(callerMap);
            List<String> expectedEncoded = List.of("alpha:alpha", "alpha:beta", "zeta:first", "zeta:first", "zeta:second");
            assertSame(callerMap, cached.get());
            assertEquals(expectedEncoded, cached.internal.get());
            assertThrows(UnsupportedOperationException.class, cached.internal.get()::clear);
            zetaPaths.add("THIRD");
            callerMap.put("MUTATED", List.of("later"));
            assertSame(callerMap, cached.get());
            assertTrue(cached.get().containsKey("MUTATED"));
            assertEquals(expectedEncoded, cached.internal.get());

            spec.save();
            String saved = Files.readString(file);
            int previous = -1;
            for (String encoded : expectedEncoded) {
                int index = saved.indexOf(encoded, previous + 1);
                assertTrue(index > previous, () -> "missing or unsorted encoded entry " + encoded + " in " + saved);
                previous = index;
            }
            assertFalse(saved.contains("zeta:third"));
            assertFalse(saved.contains("mutated:later"));

            cached.clearCache(false);
            Map<String, List<String>> normalized = cached.get();
            assertNotSame(callerMap, normalized);
            assertEquals(List.of("alpha", "beta"), normalized.get("alpha"));
            assertEquals(List.of("first", "first", "second"), normalized.get("zeta"));
            assertFalse(normalized.containsKey("MUTATED"));
            spec.unload();
            MekanismConfigSpec.LoadResult reloaded = spec.load(file);
            assertEquals(0, reloaded.correctedValues());
            cached.clearCache(false);
            assertEquals(normalized, cached.get());
            assertEquals(expectedEncoded, cached.internal.get());
            assertEquals(1, defaultCalls.get());
        } finally {
            Locale.setDefault(previousLocale);
        }
    }

    private static final class TestConfig extends BaseMekanismConfig {
        private MekanismConfigSpec spec;

        private TestConfig() {
        }

        private TestConfig(MekanismConfigSpec spec) {
            this.spec = spec;
        }

        @Override public String getFileName() { return "generic-cached-value-test"; }
        @Override public String getTranslation() { return "Generic Cached Value Test"; }
        @Override public MekanismConfigSpec getConfigSpec() { return spec; }
        @Override public MekanismConfigType getConfigType() { return MekanismConfigType.SERVER; }
    }

    private static final class ResolvingListValue extends CachedResolvableConfigValue<String, List<? extends String>> {
        private final AtomicInteger resolveCalls = new AtomicInteger();
        private final AtomicInteger encodeCalls = new AtomicInteger();

        private ResolvingListValue(TestConfig config, MekanismConfigSpec.ConfigValue<List<? extends String>> internal) {
            super(config, internal);
        }

        @Override
        protected String resolve(List<? extends String> encoded) {
            resolveCalls.incrementAndGet();
            return encoded.equals(List.of("null")) ? null : String.join("|", encoded);
        }

        @Override
        protected List<? extends String> encode(String value) {
            encodeCalls.incrementAndGet();
            return Arrays.asList(value.split("\\|", -1));
        }
    }

    private static final class ParsingMapValue extends CachedMapConfigValue<String, Integer> {
        private ParsingMapValue(TestConfig config, MekanismConfigSpec.ConfigValue<List<? extends String>> internal) {
            super(config, internal);
        }

        @Override
        protected void resolve(String encoded, Map<String, Integer> resolved) {
            int separator = encoded.indexOf('=');
            if (separator <= 0 || encoded.startsWith("ignore=")) {
                return;
            }
            try {
                resolved.put(encoded.substring(0, separator), Integer.parseInt(encoded.substring(separator + 1)));
            } catch (NumberFormatException ignored) {
                // This schema accepts strings; malformed map entries are deliberately ignored by this test owner.
            }
        }

        @Override
        protected void encode(String key, Integer value, java.util.function.Consumer<String> adder) {
            adder.accept(key + "=" + value);
        }
    }
}
