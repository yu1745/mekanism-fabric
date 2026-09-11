package mekanism.fabric.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
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
