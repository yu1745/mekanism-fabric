package mekanism.fabric.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import mekanism.common.config.GearConfig;
import mekanism.common.config.MekanismConfig;
import mekanism.common.config.listener.ConfigBasedCachedLongSupplier;
import net.minecraft.SharedConstants;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FabricGearConfigLifecycleTest {
    @TempDir Path temporaryDirectory;

    @Test
    void remoteOwnersRefreshCachesAndIgnoreStaleDisconnects() throws ReflectiveOperationException {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var constructor = GearConfig.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        GearConfig server = constructor.newInstance();
        server.getConfigSpec().load(temporaryDirectory.resolve("serverconfig/Mekanism/gear.toml"));
        Map<String, Object> machineSnapshot = new LinkedHashMap<>();
        var manager = new FabricMekanismConfigManager(temporaryDirectory.resolve("serverconfig"));
        try {
            for (var config : List.of(MekanismConfig.storage, MekanismConfig.usage, MekanismConfig.tiers)) {
                manager.registerAndLoad(config);
                config.getConfigSpec().scalarSnapshot().forEach((path, value) -> machineSnapshot.put(config.getFileName() + "." + path, value));
            }
        } finally {
            manager.unloadAll();
        }
        server.freeRunnerMaxEnergy.set(90_000L);
        var firstSnapshot = new LinkedHashMap<>(machineSnapshot);
        server.getConfigSpec().scalarSnapshot().forEach((path, value) -> firstSnapshot.put("gear." + path, value));
        server.freeRunnerMaxEnergy.set(123_456L);
        var secondSnapshot = new LinkedHashMap<>(machineSnapshot);
        server.getConfigSpec().scalarSnapshot().forEach((path, value) -> secondSnapshot.put("gear." + path, value));
        Connection first = new Connection(PacketFlow.CLIENTBOUND);
        Connection second = new Connection(PacketFlow.CLIENTBOUND);
        var cached = new ConfigBasedCachedLongSupplier(MekanismConfig.gear.freeRunnerMaxEnergy::get,
              MekanismConfig.gear.freeRunnerMaxEnergy);
        assertEquals(64_000L, cached.getAsLong());
        try {
            FabricServerConfig.attachClient(first, null, firstSnapshot);
            assertTrue(MekanismConfig.gear.isLoaded());
            assertEquals(90_000L, cached.getAsLong());
            assertThrows(IllegalStateException.class, () -> FabricServerConfig.attachClient(first, null, firstSnapshot));
            assertEquals(90_000L, cached.getAsLong());
            FabricServerConfig.attachClient(second, null, secondSnapshot);
            assertEquals(123_456L, cached.getAsLong());
            FabricServerConfig.detachClient(first);
            assertTrue(MekanismConfig.gear.isLoaded());
            assertEquals(123_456L, cached.getAsLong());
            FabricServerConfig.detachClient(second);
            assertFalse(MekanismConfig.gear.isLoaded());
            assertEquals(64_000L, cached.getAsLong());
        } finally {
            FabricServerConfig.detachClient(first);
            FabricServerConfig.detachClient(second);
            server.getConfigSpec().unload();
        }
    }
}
