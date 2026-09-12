package mekanism.common.config.value;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import mekanism.common.config.BaseMekanismConfig;
import mekanism.common.config.MekanismConfigType;
import mekanism.fabric.config.MekanismConfigSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PrimitiveCachedValueTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void cachedBooleanUsesStrictTomlValuesLazyCachingAndExactInvalidation() throws IOException {
        MekanismConfigSpec.Builder builder = new MekanismConfigSpec.Builder();
        MekanismConfigSpec.ConfigValue<Boolean> internal = builder.define("flag", false);
        MekanismConfigSpec.ConfigValue<Boolean> companion = builder.define("companion", true);
        MekanismConfigSpec spec = builder.build();
        TestConfig config = new TestConfig(spec);
        CachedBooleanValue cached = CachedBooleanValue.wrap(config, internal);
        Path file = temporaryDirectory.resolve("boolean.toml");

        assertFalse(config.isLoaded());
        assertFalse(cached.getOrDefault());
        assertTrue(spec.isValidValue("flag", true));
        assertTrue(spec.isValidValue("flag", false));
        assertFalse(spec.isValidValue("flag", 1));
        assertFalse(spec.isValidValue("flag", "true"));
        internal.set(true);
        assertFalse(cached.getOrDefault());
        assertTrue(internal.get());
        assertTrue(cached.getAsBoolean());
        internal.set(false);
        assertTrue(cached.get());
        cached.set(false);
        assertFalse(internal.get());
        assertFalse(cached.get());

        AtomicInteger invalidations = new AtomicInteger();
        cached.addInvalidationListener(invalidations::incrementAndGet);
        Files.writeString(file, "flag = true\ncompanion = false\n");
        MekanismConfigSpec.LoadResult trueLoad = spec.load(file);
        assertFalse(trueLoad.malformed());
        assertEquals(0, trueLoad.correctedValues());
        assertTrue(internal.get());
        assertFalse(companion.get());
        assertFalse(cached.get());
        config.clearCache(false);
        assertTrue(cached.getAsBoolean());
        assertEquals(1, invalidations.get());

        Files.writeString(file, "flag = false\ncompanion = true\n");
        spec.load(file);
        config.clearCache(false);
        assertFalse(cached.get());
        assertEquals(2, invalidations.get());
        spec.load(file);
        config.clearCache(false);
        assertEquals(2, invalidations.get());

        cached.set(true);
        assertTrue(internal.get());
        assertTrue(cached.getAsBoolean());
        config.clearCache(false);
        assertEquals(2, invalidations.get());

        for (String rejected : new String[]{"1", "\"false\""}) {
            Files.writeString(file, "flag = " + rejected + "\ncompanion = false\n");
            MekanismConfigSpec.LoadResult corrected = spec.load(file);
            assertFalse(corrected.malformed());
            assertEquals(1, corrected.correctedValues());
            assertFalse(internal.get());
            assertFalse(companion.get());
            assertTrue(Files.readString(file).contains("flag = false"));
            config.clearCache(false);
            assertFalse(cached.get());
        }
        assertEquals(3, invalidations.get());

        cached.set(true);
        companion.set(false);
        Path blockingParent = temporaryDirectory.resolve("boolean-blocking-parent");
        Files.writeString(blockingParent, "not a directory");
        assertThrows(UncheckedIOException.class, () -> spec.load(blockingParent.resolve("cannot-write.toml")));
        assertTrue(spec.isLoaded());
        assertTrue(internal.get());
        assertFalse(companion.get());
        assertTrue(cached.get());
    }

    @Test
    void cachedByteNormalizesExactlyBeforeNarrowingAndRejectsInvalidSet() throws IOException {
        assertThrows(IllegalArgumentException.class, () -> new MekanismConfigSpec.Builder().defineInRange(
              "reversed", (byte) 0, (byte) 1, (byte) -1));
        assertThrows(IllegalArgumentException.class, () -> new MekanismConfigSpec.Builder().defineInRange(
              "defaultLow", (byte) -11, (byte) -10, (byte) 10));
        assertThrows(IllegalArgumentException.class, () -> new MekanismConfigSpec.Builder().defineInRange(
              "defaultHigh", (byte) 11, (byte) -10, (byte) 10));

        MekanismConfigSpec.Builder builder = new MekanismConfigSpec.Builder();
        MekanismConfigSpec.ConfigValue<Byte> full = builder.defineInRange(
              "full", (byte) 0, Byte.MIN_VALUE, Byte.MAX_VALUE);
        MekanismConfigSpec.ConfigValue<Byte> bounded = builder.defineInRange(
              "bounded", (byte) -7, (byte) -100, (byte) 100);
        MekanismConfigSpec spec = builder.build();
        TestConfig config = new TestConfig(spec);
        CachedByteValue cached = CachedByteValue.wrap(config, bounded);
        Path file = temporaryDirectory.resolve("byte.toml");

        assertEquals((byte) -7, cached.getOrDefault());
        for (Object accepted : new Object[]{Byte.MIN_VALUE, Byte.MAX_VALUE, (short) -128, 127,
              -128L, BigInteger.valueOf(127), new BigDecimal("-128.000"), -128.0F, 127.0D}) {
            assertTrue(spec.isValidValue("full", accepted), () -> "expected byte value: " + accepted);
        }
        for (Object rejected : new Object[]{-129, 128L, 1.5F, -2.25D, Float.NaN,
              Double.POSITIVE_INFINITY, BigInteger.valueOf(128), new BigDecimal("-129.000"),
              new BigDecimal("1.0001"), BigInteger.ONE.shiftLeft(100)}) {
            assertFalse(spec.isValidValue("full", rejected), () -> "unexpected byte value: " + rejected);
        }
        assertFalse(spec.isValidValue("bounded", -101));
        assertFalse(spec.isValidValue("bounded", 101L));

        Files.writeString(file, "full = -128\nbounded = -100\n");
        MekanismConfigSpec.LoadResult minimums = spec.load(file);
        assertEquals(0, minimums.correctedValues());
        config.clearCache(false);
        assertEquals(Byte.MIN_VALUE, full.get());
        assertEquals((byte) -100, cached.getAsByte());

        Files.writeString(file, "full = 127\nbounded = 100\n");
        spec.load(file);
        config.clearCache(false);
        assertEquals(Byte.MAX_VALUE, full.get());
        assertEquals((byte) 100, cached.get());
        bounded.set((byte) -73);
        assertEquals((byte) 100, cached.get());
        cached.clearCache(false);
        assertEquals((byte) -73, cached.getAsByte());

        cached.set((byte) -99);
        assertEquals((byte) -99, bounded.get());
        assertEquals((byte) -99, cached.get());
        assertThrows(IllegalArgumentException.class, () -> cached.set((byte) 101));
        assertEquals((byte) -99, bounded.get());
        assertEquals((byte) -99, cached.get());

        for (String rejected : new String[]{"128", "-129", "1.5", "9223372036854775807"}) {
            Files.writeString(file, "full = " + rejected + "\nbounded = 0\n");
            MekanismConfigSpec.LoadResult corrected = spec.load(file);
            assertFalse(corrected.malformed());
            assertEquals(1, corrected.correctedValues());
            assertEquals((byte) 0, full.get());
            assertTrue(Files.readString(file).contains("full = 0"));
            config.clearCache(false);
            assertEquals((byte) 0, cached.get());
        }
        Files.writeString(file, "full = 0\nbounded = 101\n");
        MekanismConfigSpec.LoadResult configuredMiss = spec.load(file);
        assertEquals(1, configuredMiss.correctedValues());
        assertEquals((byte) -7, bounded.get());
        config.clearCache(false);
        assertEquals((byte) -7, cached.get());

        for (String overflow : new String[]{"9223372036854775808", "0x8000_0000_0000_0000"}) {
            String source = "full = " + overflow + "\nbounded = 100\n";
            Files.writeString(file, source);
            MekanismConfigSpec.LoadResult malformed = spec.load(file);
            assertTrue(malformed.malformed());
            assertEquals(2, malformed.correctedValues());
            assertEquals(source, Files.readString(temporaryDirectory.resolve("byte.toml.invalid")));
            assertEquals((byte) 0, full.get());
            assertEquals((byte) -7, bounded.get());
            config.clearCache(false);
            assertEquals((byte) -7, cached.getAsByte());
        }
    }

    @Test
    void cachedShortPreservesSignedEndpointsAndRefreshesWithoutNarrowingWrap() throws IOException {
        assertThrows(IllegalArgumentException.class, () -> new MekanismConfigSpec.Builder().defineInRange(
              "reversed", (short) 0, (short) 1, (short) -1));
        assertThrows(IllegalArgumentException.class, () -> new MekanismConfigSpec.Builder().defineInRange(
              "defaultLow", (short) -101, (short) -100, (short) 100));
        assertThrows(IllegalArgumentException.class, () -> new MekanismConfigSpec.Builder().defineInRange(
              "defaultHigh", (short) 101, (short) -100, (short) 100));

        MekanismConfigSpec.Builder builder = new MekanismConfigSpec.Builder();
        MekanismConfigSpec.ConfigValue<Short> full = builder.defineInRange(
              "full", (short) 0, Short.MIN_VALUE, Short.MAX_VALUE);
        MekanismConfigSpec.ConfigValue<Short> bounded = builder.defineInRange(
              "bounded", (short) 123, (short) -30_000, (short) 30_000);
        MekanismConfigSpec spec = builder.build();
        TestConfig config = new TestConfig(spec);
        CachedShortValue cached = CachedShortValue.wrap(config, bounded);
        Path file = temporaryDirectory.resolve("short.toml");

        assertEquals((short) 123, cached.getOrDefault());
        for (Object accepted : new Object[]{Short.MIN_VALUE, Short.MAX_VALUE, -32_768, 32_767L,
              BigInteger.valueOf(32_767), new BigDecimal("-32768.000"), -32_768.0F, 32_767.0D}) {
            assertTrue(spec.isValidValue("full", accepted), () -> "expected short value: " + accepted);
        }
        for (Object rejected : new Object[]{-32_769, 32_768L, 0.5F, -1.25D, Float.NEGATIVE_INFINITY,
              Double.NaN, BigInteger.valueOf(65_535), new BigDecimal("32768.000"),
              new BigDecimal("-0.0001"), BigInteger.ONE.shiftLeft(100)}) {
            assertFalse(spec.isValidValue("full", rejected), () -> "unexpected short value: " + rejected);
        }
        assertFalse(spec.isValidValue("bounded", -30_001));
        assertFalse(spec.isValidValue("bounded", 30_001L));

        Files.writeString(file, "full = -32768\nbounded = -30000\n");
        spec.load(file);
        config.clearCache(false);
        assertEquals(Short.MIN_VALUE, full.get());
        assertEquals((short) -30_000, cached.getAsShort());
        Files.writeString(file, "full = 32767\nbounded = 30000\n");
        spec.load(file);
        config.clearCache(false);
        assertEquals(Short.MAX_VALUE, full.get());
        assertEquals((short) 30_000, cached.get());

        AtomicInteger invalidations = new AtomicInteger();
        cached.addInvalidationListener(invalidations::incrementAndGet);
        config.clearCache(false);
        assertEquals(0, invalidations.get());
        bounded.set((short) -12_345);
        config.clearCache(false);
        assertEquals(1, invalidations.get());
        assertEquals((short) -12_345, cached.getAsShort());
        bounded.set((short) -12_345);
        config.clearCache(false);
        assertEquals(1, invalidations.get());
        cached.set((short) -29_999);
        assertThrows(IllegalArgumentException.class, () -> cached.set((short) 30_001));
        assertEquals((short) -29_999, bounded.get());
        assertEquals((short) -29_999, cached.get());

        for (String rejected : new String[]{"32768", "-32769", "0x8000", "0xFFFF", "1.25",
              "9223372036854775807"}) {
            Files.writeString(file, "full = " + rejected + "\nbounded = 0\n");
            MekanismConfigSpec.LoadResult corrected = spec.load(file);
            assertFalse(corrected.malformed());
            assertEquals(1, corrected.correctedValues());
            assertEquals((short) 0, full.get());
            assertTrue(Files.readString(file).contains("full = 0"));
            config.clearCache(false);
            assertEquals((short) 0, cached.get());
        }
        Files.writeString(file, "full = 0\nbounded = -30001\n");
        MekanismConfigSpec.LoadResult configuredMiss = spec.load(file);
        assertEquals(1, configuredMiss.correctedValues());
        assertEquals((short) 123, bounded.get());
        config.clearCache(false);
        assertEquals((short) 123, cached.getAsShort());

        for (String overflow : new String[]{"9223372036854775808", "0x8000_0000_0000_0000"}) {
            String source = "full = " + overflow + "\nbounded = -30000\n";
            Files.writeString(file, source);
            MekanismConfigSpec.LoadResult malformed = spec.load(file);
            assertTrue(malformed.malformed());
            assertEquals(2, malformed.correctedValues());
            assertEquals(source, Files.readString(temporaryDirectory.resolve("short.toml.invalid")));
            assertEquals((short) 0, full.get());
            assertEquals((short) 123, bounded.get());
            config.clearCache(false);
            assertEquals((short) 123, cached.getAsShort());
        }
    }

    @Test
    void cachedDoublePreservesNegativeZeroAndFiniteRuntimeSemantics() throws IOException {
        assertThrows(IllegalArgumentException.class,
              () -> new MekanismConfigSpec.Builder().defineInRange("reversed", 0D, 1D, -1D));
        assertThrows(IllegalArgumentException.class,
              () -> new MekanismConfigSpec.Builder().defineInRange("defaultLow", -2D, -1D, 1D));
        assertThrows(IllegalArgumentException.class,
              () -> new MekanismConfigSpec.Builder().defineInRange("nan", Double.NaN, -1D, 1D));
        assertThrows(IllegalArgumentException.class,
              () -> new MekanismConfigSpec.Builder().defineInRange("infinite", 0D, -1D, Double.POSITIVE_INFINITY));

        double widenedFloat = (double) 0.1F;
        MekanismConfigSpec.Builder builder = new MekanismConfigSpec.Builder();
        MekanismConfigSpec.ConfigValue<Double> bounded = builder.defineInRange("bounded", -0.0D, -1D, 1D);
        MekanismConfigSpec.ConfigValue<Double> floatExact = builder.defineInRange(
              "floatExact", widenedFloat, widenedFloat, widenedFloat);
        MekanismConfigSpec spec = builder.build();
        TestConfig config = new TestConfig(spec);
        CachedDoubleValue cached = CachedDoubleValue.wrap(config, bounded);
        Path file = temporaryDirectory.resolve("double.toml");
        String exactFloatToml = Double.toString(widenedFloat);

        assertRawDoubleEquals(-0.0D, cached.getOrDefault());
        assertTrue(spec.isValidValue("bounded", -1D));
        assertTrue(spec.isValidValue("bounded", 1D));
        assertTrue(spec.isValidValue("floatExact", 0.1F));
        assertTrue(spec.isValidValue("floatExact", widenedFloat));
        assertFalse(spec.isValidValue("floatExact", 0.1D));
        for (Object rejected : new Object[]{Double.NaN, Double.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY,
              Math.nextUp(1D), Math.nextDown(-1D), "0.0"}) {
            assertFalse(spec.isValidValue("bounded", rejected), () -> "unexpected double value: " + rejected);
        }

        Files.writeString(file, "bounded = -0.0\nfloatExact = " + exactFloatToml + "\n");
        MekanismConfigSpec.LoadResult negativeZero = spec.load(file);
        assertEquals(0, negativeZero.correctedValues());
        assertRawDoubleEquals(-0.0D, bounded.get());
        config.clearCache(false);
        assertRawDoubleEquals(-0.0D, cached.get());
        assertRawDoubleEquals(-0.0D, cached.getAsDouble());

        AtomicInteger invalidations = new AtomicInteger();
        cached.addInvalidationListener(invalidations::incrementAndGet);
        bounded.set(0.0D);
        config.clearCache(false);
        assertEquals(0, invalidations.get());
        assertRawDoubleEquals(0.0D, cached.get());
        bounded.set(-0.0D);
        config.clearCache(false);
        assertEquals(0, invalidations.get());
        assertRawDoubleEquals(-0.0D, cached.get());
        cached.set(-0.0D);
        assertRawDoubleEquals(-0.0D, bounded.get());
        assertRawDoubleEquals(-0.0D, cached.get());
        bounded.set(0.5D);
        config.clearCache(false);
        assertEquals(1, invalidations.get());
        assertEquals(0.5D, cached.getAsDouble());

        for (double endpoint : new double[]{-1D, 1D}) {
            Files.writeString(file, "bounded = " + endpoint + "\nfloatExact = " + exactFloatToml + "\n");
            MekanismConfigSpec.LoadResult loaded = spec.load(file);
            assertEquals(0, loaded.correctedValues());
            config.clearCache(false);
            assertEquals(endpoint, bounded.get());
            assertEquals(endpoint, cached.get());
        }

        cached.set(0.75D);
        for (double rejected : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
              Math.nextUp(1D), Math.nextDown(-1D)}) {
            assertThrows(IllegalArgumentException.class, () -> cached.set(rejected));
            assertEquals(0.75D, bounded.get());
            assertEquals(0.75D, cached.get());
        }

        for (String rejected : new String[]{"nan", "+inf", "-inf", "1.0000000000000002"}) {
            Files.writeString(file, "bounded = " + rejected + "\nfloatExact = " + exactFloatToml + "\n");
            MekanismConfigSpec.LoadResult corrected = spec.load(file);
            assertFalse(corrected.malformed());
            assertEquals(1, corrected.correctedValues());
            assertRawDoubleEquals(-0.0D, bounded.get());
            assertTrue(Files.readString(file).contains("bounded = -0.0"));
            config.clearCache(false);
            assertRawDoubleEquals(-0.0D, cached.getAsDouble());
        }

        cached.set(0.75D);
        Path blockingParent = temporaryDirectory.resolve("double-blocking-parent");
        Files.writeString(blockingParent, "not a directory");
        assertThrows(UncheckedIOException.class, () -> spec.load(blockingParent.resolve("cannot-write.toml")));
        assertTrue(spec.isLoaded());
        assertEquals(0.75D, bounded.get());
        assertEquals(widenedFloat, floatExact.get());
        assertEquals(0.75D, cached.get());
    }

    private static void assertRawDoubleEquals(double expected, double actual) {
        assertEquals(Double.doubleToRawLongBits(expected), Double.doubleToRawLongBits(actual));
    }

    private static final class TestConfig extends BaseMekanismConfig {
        private final MekanismConfigSpec spec;

        private TestConfig(MekanismConfigSpec spec) {
            this.spec = spec;
        }

        @Override public String getFileName() { return "primitive-cached-value-test"; }
        @Override public String getTranslation() { return "Primitive Cached Value Test"; }
        @Override public MekanismConfigSpec getConfigSpec() { return spec; }
        @Override public MekanismConfigType getConfigType() { return MekanismConfigType.SERVER; }
    }
}
