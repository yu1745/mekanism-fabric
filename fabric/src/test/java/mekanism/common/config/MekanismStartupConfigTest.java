package mekanism.common.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import mekanism.fabric.config.FabricMekanismConfigManager;
import mekanism.fabric.config.MekanismConfigSpec;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MekanismStartupConfigTest {
    private static final Set<String> EXPECTED_PATHS = Set.of(
          "gear.armored_free_runner.armor",
          "gear.armored_free_runner.toughness",
          "gear.armored_free_runner.knockbackResistance",
          "gear.armored_jetpack.armor",
          "gear.armored_jetpack.toughness",
          "gear.armored_jetpack.knockbackResistance",
          "gear.mekasuit.helmetArmor",
          "gear.mekasuit.bodyArmorArmor",
          "gear.mekasuit.pantsArmor",
          "gear.mekasuit.bootsArmor",
          "gear.mekasuit.toughness",
          "gear.mekasuit.knockbackResistance"
    );
    private static final Set<String> INTEGER_PATHS = Set.of(
          "gear.armored_free_runner.armor",
          "gear.armored_jetpack.armor",
          "gear.mekasuit.helmetArmor",
          "gear.mekasuit.bodyArmorArmor",
          "gear.mekasuit.pantsArmor",
          "gear.mekasuit.bootsArmor"
    );
    private static final Map<String, String> TRANSLATIONS = Map.ofEntries(
          Map.entry("gear", "startup.gear"),
          Map.entry("gear.armored_free_runner", "gear.free_runners.armored"),
          Map.entry("gear.armored_jetpack", "gear.jetpack.armored"),
          Map.entry("gear.mekasuit", "gear.meka_suit"),
          Map.entry("gear.armored_free_runner.armor", "gear.free_runners.armored.armor"),
          Map.entry("gear.armored_free_runner.toughness", "gear.free_runners.armored.toughness"),
          Map.entry("gear.armored_free_runner.knockbackResistance", "gear.free_runners.armored.knockback_resistance"),
          Map.entry("gear.armored_jetpack.armor", "gear.jetpack.armored.armor"),
          Map.entry("gear.armored_jetpack.toughness", "gear.jetpack.armored.toughness"),
          Map.entry("gear.armored_jetpack.knockbackResistance", "gear.jetpack.armored.knockback_resistance"),
          Map.entry("gear.mekasuit.helmetArmor", "gear.meka_suit.armor.helmet"),
          Map.entry("gear.mekasuit.bodyArmorArmor", "gear.meka_suit.armor.chestplate"),
          Map.entry("gear.mekasuit.pantsArmor", "gear.meka_suit.armor.leggings"),
          Map.entry("gear.mekasuit.bootsArmor", "gear.meka_suit.armor.boots"),
          Map.entry("gear.mekasuit.toughness", ".gear.meka_suit.toughness"),
          Map.entry("gear.mekasuit.knockbackResistance", "gear.meka_suit.knockback_resistance")
    );

    @TempDir
    Path temporaryDirectory;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void officialStartupSchemaKeepsPathsDefaultsRangesAndRestartScope() {
        MekanismStartupConfig config = new MekanismStartupConfig();

        assertEquals("startup", config.getFileName());
        assertEquals(MekanismConfigType.STARTUP, config.getConfigType());
        assertEquals(EXPECTED_PATHS, config.getConfigSpec().paths());
        assertEquals(12, config.getConfigSpec().size());
        TRANSLATIONS.forEach((path, translation) -> assertEquals(translation, config.getConfigSpec().translation(path)));
        EXPECTED_PATHS.forEach(path -> {
            assertEquals(MekanismConfigSpec.RestartScope.GAME, config.getConfigSpec().restartScope(path));
            assertTrue(config.getConfigSpec().isValidValue(path, 0));
            assertFalse(config.getConfigSpec().isValidValue(path, -1));
        });
        INTEGER_PATHS.forEach(path -> {
            assertTrue(config.getConfigSpec().isValidValue(path, Integer.MAX_VALUE));
            assertFalse(config.getConfigSpec().isValidValue(path, (long) Integer.MAX_VALUE + 1));
        });
        EXPECTED_PATHS.stream().filter(path -> !INTEGER_PATHS.contains(path)).forEach(path -> {
            assertTrue(config.getConfigSpec().isValidValue(path, (double) Float.MAX_VALUE));
            assertFalse(config.getConfigSpec().isValidValue(path, Double.MAX_VALUE));
            assertFalse(config.getConfigSpec().isValidValue(path, Double.NaN));
        });

        assertEquals(3, config.armoredFreeRunnerArmor.getOrDefault());
        assertEquals(2, config.armoredFreeRunnerToughness.getOrDefault());
        assertEquals(0, config.armoredFreeRunnerKnockbackResistance.getOrDefault());
        assertEquals(8, config.armoredJetpackArmor.getOrDefault());
        assertEquals(2, config.armoredJetpackToughness.getOrDefault());
        assertEquals(0, config.armoredJetpackKnockbackResistance.getOrDefault());
        assertEquals(3, config.mekaSuitHelmetArmor.getOrDefault());
        assertEquals(8, config.mekaSuitBodyArmorArmor.getOrDefault());
        assertEquals(6, config.mekaSuitPantsArmor.getOrDefault());
        assertEquals(3, config.mekaSuitBootsArmor.getOrDefault());
        assertEquals(3, config.mekaSuitToughness.getOrDefault());
        assertEquals(0.1F, config.mekaSuitKnockbackResistance.getOrDefault(), 0.000_001F);
    }

    @Test
    void managerRejectsDistinctSpecsOwningTheSameFile() {
        FabricMekanismConfigManager manager = new FabricMekanismConfigManager(temporaryDirectory);
        TestConfig first = new TestConfig("duplicate");
        TestConfig second = new TestConfig("duplicate");

        manager.registerAndLoad(first);
        assertThrows(IllegalStateException.class, () -> manager.registerAndLoad(second));
        assertThrows(IllegalArgumentException.class, () -> manager.registerAndLoad(new TestConfig("../escape")));
        TestConfig failingCallback = new TestConfig("failing", true);
        assertThrows(IllegalStateException.class, () -> manager.registerAndLoad(failingCallback));
        assertFalse(failingCallback.isLoaded());
        manager.unloadAll();
    }

    @Test
    void managerLoadsRealStartupFileAndInvalidatesResolvedCache() throws IOException {
        Path configDirectory = temporaryDirectory.resolve("Mekanism");
        Files.createDirectories(configDirectory);
        Path startupFile = configDirectory.resolve("startup.toml");
        Files.writeString(startupFile, "[gear.armored_free_runner]\narmor = 4\n");
        MekanismStartupConfig config = new MekanismStartupConfig();
        AtomicInteger invalidations = new AtomicInteger();
        config.armoredFreeRunnerArmor.addInvalidationListener(invalidations::incrementAndGet);
        assertEquals(3, config.armoredFreeRunnerArmor.get());
        FabricMekanismConfigManager manager = new FabricMekanismConfigManager(temporaryDirectory);

        FabricMekanismConfigManager.Registration registration = manager.registerAndLoad(config);

        assertEquals(startupFile.toAbsolutePath(), registration.file());
        assertFalse(registration.loadResult().created());
        assertEquals(11, registration.loadResult().correctedValues());
        assertEquals(4, config.armoredFreeRunnerArmor.get());
        assertEquals(1, invalidations.get());
        assertTrue(config.isLoaded());
        assertThrows(IllegalStateException.class, () -> manager.registerAndLoad(config));

        config.armoredFreeRunnerArmor.set(5);
        config.getConfigSpec().save();
        assertTrue(Files.readString(startupFile).contains("armor = 5"));

        manager.unloadAll();
        assertFalse(config.isLoaded());
        assertEquals(1, invalidations.get());
    }

    private static final class TestConfig extends BaseMekanismConfig {
        private final String fileName;
        private final MekanismConfigSpec spec;
        private final boolean failCacheClear;

        private TestConfig(String fileName) {
            this(fileName, false);
        }

        private TestConfig(String fileName, boolean failCacheClear) {
            this.fileName = fileName;
            this.failCacheClear = failCacheClear;
            MekanismConfigSpec.Builder builder = new MekanismConfigSpec.Builder();
            builder.defineInRange("value", 1, 0, 2);
            spec = builder.build();
        }

        @Override
        public String getFileName() {
            return fileName;
        }

        @Override
        public String getTranslation() {
            return "Test";
        }

        @Override
        public MekanismConfigSpec getConfigSpec() {
            return spec;
        }

        @Override
        public MekanismConfigType getConfigType() {
            return MekanismConfigType.STARTUP;
        }

        @Override
        public void clearCache(boolean unloading) {
            if (failCacheClear) {
                throw new IllegalStateException("test callback failure");
            }
            super.clearCache(unloading);
        }
    }
}
