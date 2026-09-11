package mekanism.fabric.config;

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
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MekanismConfigSpecTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void missingFileGetsValidatedDefaultsAndMetadata() throws IOException {
        MekanismConfigSpec.Builder builder = new MekanismConfigSpec.Builder();
        builder.translation("test.category").comment("Category comment").push("category");
        MekanismConfigSpec.ConfigValue<Integer> count = builder.translation("test.count").comment("Count comment")
              .gameRestart().defineInRange("count", 3, 0, 10);
        MekanismConfigSpec.ConfigValue<Double> ratio = builder.translation("test.ratio").comment("Ratio comment")
              .defineInRange("ratio", 0.5, 0, 1);
        builder.pop();
        MekanismConfigSpec spec = builder.build();
        Path file = temporaryDirectory.resolve("config").resolve("proof.toml");

        assertFalse(spec.isLoaded());
        assertEquals(3, count.get());
        MekanismConfigSpec.LoadResult result = spec.load(file);

        assertTrue(result.created());
        assertFalse(result.malformed());
        assertEquals(2, result.correctedValues());
        assertTrue(spec.isLoaded());
        assertEquals(Set.of("category.count", "category.ratio"), spec.paths());
        assertEquals(MekanismConfigSpec.RestartScope.GAME, spec.restartScope("category.count"));
        assertEquals("test.count", spec.translation("category.count"));
        String written = Files.readString(file);
        assertTrue(written.contains("Category comment"));
        assertTrue(written.contains("Count comment"));
        assertTrue(written.contains("count = 3"));
        assertTrue(written.contains("ratio = 0.5"));
    }

    @Test
    void validValuesLoadAndInvalidValuesAreCorrectedAtomically() throws IOException {
        MekanismConfigSpec.Builder builder = new MekanismConfigSpec.Builder();
        builder.push("category");
        MekanismConfigSpec.ConfigValue<Integer> count = builder.defineInRange("count", 3, 0, 10);
        MekanismConfigSpec.ConfigValue<Double> ratio = builder.defineInRange("ratio", 0.5, 0, 1);
        builder.pop();
        MekanismConfigSpec spec = builder.build();
        Path file = temporaryDirectory.resolve("proof.toml");
        Files.writeString(file, "[category]\ncount = 7\nratio = 2.0\n");

        MekanismConfigSpec.LoadResult result = spec.load(file);

        assertFalse(result.created());
        assertFalse(result.malformed());
        assertEquals(1, result.correctedValues());
        assertEquals(7, count.get());
        assertEquals(0.5, ratio.get());
        String corrected = Files.readString(file);
        assertTrue(corrected.contains("count = 7"));
        assertTrue(corrected.contains("ratio = 0.5"));
        try (var files = Files.list(temporaryDirectory)) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().endsWith(".tmp")));
        }

        count.set(9);
        ratio.set(0.75);
        spec.save();
        String saved = Files.readString(file);
        assertTrue(saved.contains("count = 9"));
        assertTrue(saved.contains("ratio = 0.75"));
    }

    @Test
    void malformedDocumentPublishesOnlyDefaultsAndIsPreserved() throws IOException {
        MekanismConfigSpec.Builder builder = new MekanismConfigSpec.Builder();
        builder.push("category");
        MekanismConfigSpec.ConfigValue<Integer> count = builder.defineInRange("count", 3, 0, 10);
        builder.pop();
        MekanismConfigSpec spec = builder.build();
        Path file = temporaryDirectory.resolve("proof.toml");
        String malformed = "[category\ncount = 8\n";
        Files.writeString(file, malformed);

        MekanismConfigSpec.LoadResult result = spec.load(file);

        assertTrue(result.malformed());
        assertEquals(3, count.get());
        assertEquals(malformed, Files.readString(temporaryDirectory.resolve("proof.toml.invalid")));
        assertTrue(Files.readString(file).contains("count = 3"));
    }

    @Test
    void failedMalformedBackupDoesNotReplaceOriginal() throws IOException {
        MekanismConfigSpec.Builder builder = new MekanismConfigSpec.Builder();
        builder.defineInRange("count", 3, 0, 10);
        MekanismConfigSpec spec = builder.build();
        Path file = temporaryDirectory.resolve("proof.toml");
        String malformed = "[category\ncount = 8\n";
        Files.writeString(file, malformed);
        Path invalidBackup = Files.createDirectory(temporaryDirectory.resolve("proof.toml.invalid"));
        Files.writeString(invalidBackup.resolve("blocker"), "keep directory non-empty");

        assertThrows(UncheckedIOException.class, () -> spec.load(file));
        assertFalse(spec.isLoaded());
        assertEquals(malformed, Files.readString(file));
    }

    @Test
    void failedCorrectionWriteDoesNotPublishLoadedState() throws IOException {
        MekanismConfigSpec.Builder builder = new MekanismConfigSpec.Builder();
        MekanismConfigSpec.ConfigValue<Integer> count = builder.defineInRange("count", 3, 0, 10);
        MekanismConfigSpec spec = builder.build();
        Path nonDirectory = temporaryDirectory.resolve("not-a-directory");
        Files.writeString(nonDirectory, "blocking file");

        assertThrows(UncheckedIOException.class, () -> spec.load(nonDirectory.resolve("proof.toml")));
        assertFalse(spec.isLoaded());
        assertEquals(3, count.get());
    }

    @Test
    void signedLongTomlRoundTripsExactFullRangeValues() throws IOException {
        MekanismConfigSpec.Builder builder = new MekanismConfigSpec.Builder();
        MekanismConfigSpec.ConfigValue<Long> above53 = builder.defineInRange("above53", 9_007_199_254_740_993L, Long.MIN_VALUE, Long.MAX_VALUE);
        MekanismConfigSpec.ConfigValue<Long> negativeAbove53 = builder.defineInRange("negativeAbove53", -9_007_199_254_740_993L, Long.MIN_VALUE, Long.MAX_VALUE);
        MekanismConfigSpec.ConfigValue<Long> minimum = builder.defineInRange("minimum", Long.MIN_VALUE, Long.MIN_VALUE, Long.MAX_VALUE);
        MekanismConfigSpec.ConfigValue<Long> maximum = builder.defineInRange("maximum", Long.MAX_VALUE, Long.MIN_VALUE, Long.MAX_VALUE);
        MekanismConfigSpec spec = builder.build();
        Path file = temporaryDirectory.resolve("longs.toml");

        MekanismConfigSpec.LoadResult created = spec.load(file);
        assertTrue(created.created());
        assertEquals(4, created.correctedValues());
        assertEquals(9_007_199_254_740_993L, above53.get());
        assertEquals(-9_007_199_254_740_993L, negativeAbove53.get());
        assertEquals(Long.MIN_VALUE, minimum.get());
        assertEquals(Long.MAX_VALUE, maximum.get());
        String exactDefaults = Files.readString(file);
        assertTrue(exactDefaults.contains("above53 = 9007199254740993"));
        assertTrue(exactDefaults.contains("negativeAbove53 = -9007199254740993"));
        assertTrue(exactDefaults.contains("minimum = -9223372036854775808"));
        assertTrue(exactDefaults.contains("maximum = 9223372036854775807"));

        String harmlessHugeText = "above53 = 9007199254740995\n"
              + "negativeAbove53 = -9007199254740995\n"
              + "minimum = -9223372036854775808\n"
              + "maximum = 9223372036854775807\n"
              + "18446744073709551616.key = \"9223372036854775808\"\n"
              + "dotted = { a = 1, 18446744073709551617 . b = 2 }\n"
              + "quoted = '18446744073709551616'\n"
              + "literalQuotes = '''a''''\n"
              + "basicQuotes = \"\"\"a\"\"\"\"\n"
              + "multiline = \"\"\"\n-9223372036854775809\n\"\"\"\n"
              + "# 0x8000000000000000\n";
        Files.writeString(file, harmlessHugeText);
        MekanismConfigSpec.LoadResult loaded = spec.load(file);
        assertFalse(loaded.malformed());
        assertEquals(0, loaded.correctedValues());
        assertEquals(9_007_199_254_740_995L, above53.get());
        assertEquals(-9_007_199_254_740_995L, negativeAbove53.get());
        spec.save();
        String exactSaved = Files.readString(file);
        assertTrue(exactSaved.contains("above53 = 9007199254740995"));
        assertTrue(exactSaved.contains("negativeAbove53 = -9007199254740995"));
        assertTrue(exactSaved.contains("minimum = -9223372036854775808"));
        assertTrue(exactSaved.contains("maximum = 9223372036854775807"));

        for (String overflowAssignment : new String[]{"above53 = 9223372036854775808",
              "above53 = -9223372036854775809", "above53 = 18446744073709551616",
              "above53 = 0x8000_0000_0000_0000", "note = '''a''''\nabove53 = 18446744073709551617",
              "note = \"\"\"a\"\"\"\"\nabove53 = 18446744073709551617",
              "nested = [\n  [18446744073709551616]\n]\nabove53 = 9007199254740995"}) {
            String invalid = overflowAssignment + "\n"
                  + "negativeAbove53 = -9007199254740995\nminimum = -9223372036854775808\nmaximum = 9223372036854775807\n"
                  + "safe = \"18446744073709551616\" # 9223372036854775808\n";
            Files.writeString(file, invalid);
            MekanismConfigSpec.LoadResult rejected = spec.load(file);
            assertTrue(rejected.malformed(), () -> "expected malformed overflow: " + overflowAssignment);
            assertEquals(invalid, Files.readString(temporaryDirectory.resolve("longs.toml.invalid")));
            assertEquals(4, rejected.correctedValues());
            assertEquals(9_007_199_254_740_993L, above53.get());
            assertEquals(-9_007_199_254_740_993L, negativeAbove53.get());
            assertEquals(Long.MIN_VALUE, minimum.get());
            assertEquals(Long.MAX_VALUE, maximum.get());
            String defaults = Files.readString(file);
            assertTrue(defaults.contains("above53 = 9007199254740993"));
            assertTrue(defaults.contains("minimum = -9223372036854775808"));
            assertTrue(defaults.contains("maximum = 9223372036854775807"));
        }
    }

    @Test
    void signedLongBuilderAndNormalizerRejectLossyOrOutOfRangeNumbers() {
        assertThrows(IllegalArgumentException.class,
              () -> new MekanismConfigSpec.Builder().defineInRange("reversed", 0L, 1L, -1L));
        assertThrows(IllegalArgumentException.class,
              () -> new MekanismConfigSpec.Builder().defineInRange("defaultLow", -2L, -1L, 1L));
        assertThrows(IllegalArgumentException.class,
              () -> new MekanismConfigSpec.Builder().defineInRange("defaultHigh", 2L, -1L, 1L));

        MekanismConfigSpec.Builder builder = new MekanismConfigSpec.Builder();
        MekanismConfigSpec.ConfigValue<Long> bounded = builder.defineInRange("bounded", 0L, -10L, 10L);
        builder.defineInRange("full", 0L, Long.MIN_VALUE, Long.MAX_VALUE);
        builder.defineInRange("floatExact", 999_999_984_306_749_440L,
              999_999_984_306_749_440L, 999_999_984_306_749_440L);
        builder.defineInRange("doubleExact", 9_223_372_036_854_774_784L,
              9_223_372_036_854_774_784L, 9_223_372_036_854_774_784L);
        MekanismConfigSpec spec = builder.build();
        for (Object accepted : new Object[]{(byte) 1, (short) -2, 3, 4L, BigInteger.TEN,
              new BigDecimal("-10.000"), 5.0F, -6.0D}) {
            assertTrue(spec.isValidValue("bounded", accepted), () -> "expected accepted: " + accepted);
        }
        for (Object rejected : new Object[]{null, "1", 1.5F, -2.25D, Float.NaN, Float.POSITIVE_INFINITY,
              Double.NaN, Double.NEGATIVE_INFINITY, 11L, -11}) {
            assertFalse(spec.isValidValue("bounded", rejected), () -> "expected rejected: " + rejected);
        }
        assertTrue(spec.isValidValue("full", BigInteger.valueOf(Long.MAX_VALUE)));
        assertTrue(spec.isValidValue("full", new BigDecimal(Long.MIN_VALUE)));
        for (Object rejected : new Object[]{BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE),
              BigInteger.valueOf(Long.MIN_VALUE).subtract(BigInteger.ONE), new BigDecimal("9.223372036854775808E18"),
              new BigDecimal("-9.223372036854775809E18"), new BigDecimal("1.0001"),
              Math.scalb(1.0F, 63), (float) Long.MAX_VALUE, Math.scalb(1.0, 63), (double) Long.MAX_VALUE}) {
            assertFalse(spec.isValidValue("full", rejected), () -> "expected full-range rejection: " + rejected);
        }
        float exactFloatInteger = 1.0E18F;
        assertTrue(spec.isValidValue("full", exactFloatInteger));
        assertTrue(spec.isValidValue("floatExact", exactFloatInteger));
        assertFalse(spec.isValidValue("floatExact", 1_000_000_000_000_000_000L));
        double nearMaximum = Math.nextDown(Math.scalb(1.0, 63));
        assertTrue(spec.isValidValue("full", nearMaximum));
        assertTrue(spec.isValidValue("doubleExact", nearMaximum));
        bounded.set(10L);
        assertEquals(10L, bounded.get());
    }

    @Test
    void builderRejectsInvalidStructureAndDuplicatePaths() {
        MekanismConfigSpec.Builder malformed = new MekanismConfigSpec.Builder();
        assertThrows(IllegalArgumentException.class, () -> malformed.push("bad.path"));
        assertThrows(IllegalStateException.class, malformed::pop);

        MekanismConfigSpec.Builder duplicate = new MekanismConfigSpec.Builder();
        duplicate.push("category");
        duplicate.defineInRange("count", 1, 0, 2);
        assertThrows(IllegalArgumentException.class, () -> duplicate.defineInRange("count", 1, 0, 2));

        MekanismConfigSpec.Builder valueThenCategory = new MekanismConfigSpec.Builder();
        valueThenCategory.defineInRange("scalar", 1, 0, 2);
        assertThrows(IllegalArgumentException.class, () -> valueThenCategory.push("scalar"));

        MekanismConfigSpec.Builder categoryThenValue = new MekanismConfigSpec.Builder();
        categoryThenValue.push("table");
        categoryThenValue.pop();
        assertThrows(IllegalArgumentException.class, () -> categoryThenValue.defineInRange("table", 1, 0, 2));
    }
}
