package mekanism.fabric.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import mekanism.common.config.ClientWindowConfigAccess;
import mekanism.fabric.client.render.obj.TransmitterModelConfig;
import mekanism.common.inventory.container.SelectedWindowData;
import mekanism.common.inventory.container.SelectedWindowData.WindowType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FabricWindowConfigTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void schemaOwnsWindowRecordsAndRestartScopedTransmitterSetting() {
        FabricWindowConfig config = new FabricWindowConfig();
        MekanismConfigSpec spec = config.getConfigSpec();
        Set<String> expected = new LinkedHashSet<>();
        for (WindowType type : WindowType.values()) {
            for (WindowType.ConfigSaveData save : type.getSavePaths()) {
                expected.add("window." + save.savePath() + ".x");
                expected.add("window." + save.savePath() + ".y");
                if (type.canPin()) {
                    expected.add("window." + save.savePath() + ".pinned");
                }
            }
        }
        assertEquals(11, expected.stream().filter(path -> path.endsWith(".x")).count());
        assertEquals(28, expected.size());
        Set<String> allExpected = new LinkedHashSet<>(expected);
        allExpected.add("accessibility.opaqueTransmitters");
        assertEquals(allExpected, spec.paths());
        assertEquals(MekanismConfigSpec.RestartScope.GAME, spec.restartScope("accessibility.opaqueTransmitters"));
        assertFalse(config.opaqueTransmitters());
        for (String path : expected) {
            assertEquals(MekanismConfigSpec.RestartScope.NONE, spec.restartScope(path));
            if (path.endsWith(".pinned")) {
                assertTrue(spec.isValidValue(path, false));
                assertFalse(spec.isValidValue(path, "false"));
            } else {
                assertTrue(spec.isValidValue(path, Integer.MIN_VALUE));
                assertTrue(spec.isValidValue(path, Integer.MAX_VALUE));
                assertFalse(spec.isValidValue(path, 1.0D));
                assertFalse(spec.isValidValue(path, 1.0F));
            }
        }
        config.stopAndDrain();
    }

    @Test
    void strictCorrectionRetainsOwnedAndUnownedUnknownKeys() throws IOException {
        Path file = clientFile();
        Files.createDirectories(file.getParent());
        Files.writeString(file, "[window.color]\nx = 1.0\ny = -17\nfuture = 'retained'\n"
              + "[window.side_config]\nx = 4\ny = 5\npinned = 'true'\n"
              + "[future_section]\nanswer = 42\n[accessibility]\nopaqueTransmitters = true\nwhiteRadialText = true\n");
        try (FabricClientConfigLifecycle lifecycle = FabricClientConfigLifecycle.create(temporaryDirectory)) {
            assertTrue(TransmitterModelConfig.opaqueTransmitters());
            assertTrue(lifecycle.windowConfig().opaqueTransmitters());
            assertEquals(Integer.MAX_VALUE, new SelectedWindowData(WindowType.COLOR).getLastPosition().x());
            assertEquals(-17, new SelectedWindowData(WindowType.COLOR).getLastPosition().y());
            assertFalse(new SelectedWindowData(WindowType.SIDE_CONFIG).wasPinned());
        }
        assertThrows(IllegalStateException.class, TransmitterModelConfig::opaqueTransmitters);
        String corrected = Files.readString(file);
        assertTrue(corrected.contains("opaqueTransmitters = true"));
        assertTrue(corrected.contains("whiteRadialText = true"));
        assertTrue(corrected.contains("future = \"retained\""));
        assertTrue(corrected.contains("[future_section]"));
        assertTrue(corrected.contains("answer = 42"));
        assertTrue(corrected.contains("x = 2147483647"));
        assertTrue(corrected.contains("pinned = false"));
    }

    @Test
    void scalarTableCollisionIsCorrectedAndFailedPublicationRollsBackClaim() throws IOException {
        Path file = clientFile();
        Files.createDirectories(file.getParent());
        Files.writeString(file, "window = 9\n[unowned]\nkeep = true\n");
        try (FabricClientConfigLifecycle ignored = FabricClientConfigLifecycle.create(temporaryDirectory)) {
            assertEquals(Integer.MAX_VALUE, new SelectedWindowData(WindowType.RENAME).getLastPosition().x());
        }
        String corrected = Files.readString(file);
        assertTrue(corrected.contains("[window.rename]"));
        assertTrue(corrected.contains("[unowned]"));
        assertTrue(corrected.contains("keep = true"));

        Path blockedRoot = temporaryDirectory.resolve("blocked");
        Files.writeString(blockedRoot, "not a directory");
        assertThrows(RuntimeException.class, () -> FabricClientConfigLifecycle.create(blockedRoot));
        Files.delete(blockedRoot);
        try (FabricClientConfigLifecycle recovered = FabricClientConfigLifecycle.create(blockedRoot)) {
            assertTrue(recovered.windowConfig().getConfigSpec().isLoaded());
        }
    }

    @Test
    void changedUpdatesSerializeOneLogicalJobEachAndShutdownDrainsAcceptedWork() throws IOException {
        FabricClientConfigLifecycle lifecycle = FabricClientConfigLifecycle.create(temporaryDirectory);
        FabricWindowConfig config = lifecycle.windowConfig();
        SelectedWindowData side = new SelectedWindowData(WindowType.SIDE_CONFIG);
        side.updateLastPosition(2, 3, true);
        assertEquals(1, config.logicalSaveJobs());
        side.updateLastPosition(2, 3, true);
        assertEquals(1, config.logicalSaveJobs());
        side.updateLastPosition(9, 3, true);
        assertEquals(2, config.logicalSaveJobs());
        lifecycle.close();
        assertEquals(2, config.completedSaveJobs());
        assertEquals(9, readPersisted(WindowType.SIDE_CONFIG).x());
        assertThrows(IllegalStateException.class, () -> config.update("side_config", 1, 1, false, true));

        AtomicInteger attempts = new AtomicInteger();
        FabricWindowConfig retrying = new FabricWindowConfig(spec -> {
            if (attempts.incrementAndGet() < 3) {
                throw new IllegalStateException("injected save failure");
            }
            spec.save();
        });
        FabricMekanismConfigManager retryManager = new FabricMekanismConfigManager(temporaryDirectory.resolve("retry"));
        retryManager.registerAndLoad(retrying);
        retrying.update("side_config", 5, 6, true, true);
        retrying.stopAndDrain();
        assertEquals(3, attempts.get());
        assertEquals(1, retrying.logicalSaveJobs());
        assertEquals(1, retrying.completedSaveJobs());
        retryManager.unregister(retrying);

        AtomicInteger failedAttempts = new AtomicInteger();
        FabricWindowConfig failing = new FabricWindowConfig(spec -> {
            failedAttempts.incrementAndGet();
            throw new IllegalStateException("injected terminal save failure");
        });
        Path failureRoot = temporaryDirectory.resolve("terminal-failure");
        FabricMekanismConfigManager failureManager = new FabricMekanismConfigManager(failureRoot);
        Path failureFile = failureManager.registerAndLoad(failing).file();
        String priorDisk = Files.readString(failureFile);
        failing.update("side_config", 77, 88, true, true);
        failing.stopAndDrain();
        assertEquals(4, failedAttempts.get());
        assertEquals(priorDisk, Files.readString(failureFile));
        assertEquals(new ClientWindowConfigAccess.Position(77, 88, true), failing.get("side_config"));
        failureManager.unregister(failing);
    }

    @Test
    void normalizedPathClaimIsProcessWideAcrossManagersAndReleasedOnUnload() {
        FabricMekanismConfigManager firstManager = new FabricMekanismConfigManager(temporaryDirectory.resolve("a/.."));
        FabricMekanismConfigManager secondManager = new FabricMekanismConfigManager(temporaryDirectory);
        FabricWindowConfig first = new FabricWindowConfig();
        FabricWindowConfig second = new FabricWindowConfig();
        firstManager.registerAndLoad(first);
        assertThrows(IllegalStateException.class, () -> secondManager.registerAndLoad(second));
        firstManager.unregister(first);
        first.stopAndDrain();
        secondManager.registerAndLoad(second);
        secondManager.unregister(second);
        second.stopAndDrain();
    }

    @Test
    void ownerThreadAndLifetimeAreExplicitAcrossConnectDisconnectAndShutdown() throws InterruptedException {
        assertThrows(IllegalStateException.class, () -> new SelectedWindowData(WindowType.COLOR).getLastPosition());
        FabricClientConfigLifecycle lifecycle = FabricClientConfigLifecycle.create(temporaryDirectory);
        FabricWindowConfig owner = lifecycle.windowConfig();
        // No connection event participates in this lifetime; repeated reads represent disconnect/reconnect boundaries.
        assertTrue(ClientWindowConfigAccess.isInstalled(owner));
        assertEquals(Integer.MAX_VALUE, new SelectedWindowData(WindowType.COLOR).getLastPosition().x());
        assertEquals(Integer.MAX_VALUE, new SelectedWindowData(WindowType.COLOR).getLastPosition().x());
        AtomicReference<Throwable> wrongThread = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try {
                new SelectedWindowData(WindowType.COLOR).getLastPosition();
            } catch (Throwable failure) {
                wrongThread.set(failure);
            }
        });
        thread.start();
        thread.join();
        assertTrue(wrongThread.get() instanceof IllegalStateException);
        wrongThread.set(null);
        Thread wrongCloser = new Thread(() -> {
            try {
                lifecycle.close();
            } catch (Throwable failure) {
                wrongThread.set(failure);
            }
        });
        wrongCloser.start();
        wrongCloser.join();
        assertTrue(wrongThread.get() instanceof IllegalStateException);
        assertEquals(Integer.MAX_VALUE, new SelectedWindowData(WindowType.COLOR).getLastPosition().x());
        lifecycle.close();
        assertFalse(ClientWindowConfigAccess.isInstalled(owner));
        assertThrows(IllegalStateException.class, () -> new SelectedWindowData(WindowType.COLOR).getLastPosition());
    }

    private Path clientFile() {
        return temporaryDirectory.resolve("Mekanism/client.toml");
    }

    private SelectedWindowData.WindowPosition readPersisted(WindowType type) {
        try (FabricClientConfigLifecycle ignored = FabricClientConfigLifecycle.create(temporaryDirectory)) {
            return new SelectedWindowData(type).getLastPosition();
        }
    }
}
