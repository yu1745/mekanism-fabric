package mekanism.common.config.value;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import mekanism.common.config.BaseMekanismConfig;
import mekanism.common.config.IConfigTranslation.ConfigTranslation;
import mekanism.common.config.MekanismConfigType;
import mekanism.fabric.config.MekanismConfigSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CachedLongValueTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void cachedLongValuePreservesExactLoadedLazySetAndSupplierValues() throws IOException {
        long defaultValue = 9_007_199_254_740_993L;
        TestConfig config = new TestConfig();
        MekanismConfigSpec.Builder builder = new MekanismConfigSpec.Builder();
        MekanismConfigSpec.ConfigValue<Long> internal = builder.defineInRange("energy", defaultValue, Long.MIN_VALUE, Long.MAX_VALUE);
        config.spec = builder.build();
        CachedLongValue cached = CachedLongValue.wrap(config, internal);
        assertEquals(defaultValue, cached.getOrDefault());

        Path file = temporaryDirectory.resolve("cached-long.toml");
        Files.writeString(file, "energy = 9007199254740995\n");
        config.spec.load(file);
        assertEquals(9_007_199_254_740_995L, cached.getOrDefault());
        internal.set(9_007_199_254_740_997L);
        assertEquals(9_007_199_254_740_995L, cached.get());
        cached.clearCache(false);
        assertEquals(9_007_199_254_740_997L, cached.get());
        cached.set(Long.MAX_VALUE - 1);
        assertEquals(Long.MAX_VALUE - 1, internal.get());
        assertEquals(Long.MAX_VALUE - 1, cached.get());
        assertEquals(Long.MAX_VALUE - 1, cached.getAsLong());
    }

    @Test
    void cachedLongInvalidationAndDefinitionHelpersPreserveOfficialSemantics() throws IOException {
        TestConfig config = new TestConfig();
        MekanismConfigSpec.Builder builder = new MekanismConfigSpec.Builder();
        ConfigTranslation positiveTranslation = new ConfigTranslation("test.positive", "Positive", "Positive comment");
        CachedLongValue positive = CachedLongValue.definePositive(config, builder, positiveTranslation, "positive", 9_007_199_254_740_993L);
        CachedLongValue minimum = CachedLongValue.definedMin(config, builder,
              new ConfigTranslation("test.minimum", "Minimum", "Minimum comment"), "minimum", -5L, -10L);
        CachedLongValue bounded = CachedLongValue.define(config, builder,
              new ConfigTranslation("test.bounded", "Bounded", "Bounded comment"), "bounded", 3L, -4L, 6L);
        config.spec = builder.build();
        assertEquals(Set.of("positive", "minimum", "bounded"), config.spec.paths());
        assertEquals("test.positive", config.spec.translation("positive"));
        assertFalse(config.spec.isValidValue("positive", -1L));
        assertTrue(config.spec.isValidValue("positive", Long.MAX_VALUE));
        assertFalse(config.spec.isValidValue("minimum", -11L));
        assertTrue(config.spec.isValidValue("minimum", Long.MAX_VALUE));
        assertFalse(config.spec.isValidValue("bounded", 7L));

        Path file = temporaryDirectory.resolve("helpers.toml");
        config.spec.load(file);
        String contents = Files.readString(file);
        assertTrue(contents.contains("Positive comment"));
        assertTrue(contents.contains("Minimum comment"));
        assertTrue(contents.contains("Bounded comment"));
        assertEquals(9_007_199_254_740_993L, positive.get());
        AtomicInteger invalidations = new AtomicInteger();
        positive.addInvalidationListener(invalidations::incrementAndGet);
        positive.clearCache(false);
        assertEquals(0, invalidations.get());
        positive.internal.set(9_007_199_254_740_995L);
        positive.clearCache(false);
        assertEquals(1, invalidations.get());
        assertEquals(9_007_199_254_740_995L, positive.get());
        config.spec.unload();
        positive.internal.set(9_007_199_254_740_997L);
        positive.clearCache(false);
        positive.clearCache(true);
        assertEquals(1, invalidations.get());
        assertEquals(9_007_199_254_740_995L, positive.get());
        assertEquals(-5L, minimum.getOrDefault());
        assertEquals(3L, bounded.getOrDefault());
    }

    private static final class TestConfig extends BaseMekanismConfig {
        private MekanismConfigSpec spec;

        @Override public String getFileName() { return "cached-long-test"; }
        @Override public String getTranslation() { return "Cached Long Test"; }
        @Override public MekanismConfigSpec getConfigSpec() { return spec; }
        @Override public MekanismConfigType getConfigType() { return MekanismConfigType.SERVER; }
    }
}
