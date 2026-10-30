package mekanism.fabric.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
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
        server.freeRunnerMaxEnergy.set(90_000L);
        var firstSnapshot = server.getConfigSpec().scalarSnapshot();
        server.freeRunnerMaxEnergy.set(123_456L);
        var secondSnapshot = server.getConfigSpec().scalarSnapshot();
        Connection first = new Connection(PacketFlow.CLIENTBOUND);
        Connection second = new Connection(PacketFlow.CLIENTBOUND);
        var cached = new ConfigBasedCachedLongSupplier(MekanismConfig.gear.freeRunnerMaxEnergy::get,
              MekanismConfig.gear.freeRunnerMaxEnergy);
        assertEquals(64_000L, cached.getAsLong());
        try {
            FabricGearConfig.attachClient(first, null, firstSnapshot);
            assertTrue(MekanismConfig.gear.isLoaded());
            assertEquals(90_000L, cached.getAsLong());
            assertThrows(IllegalStateException.class, () -> FabricGearConfig.attachClient(first, null, firstSnapshot));
            assertEquals(90_000L, cached.getAsLong());
            FabricGearConfig.attachClient(second, null, secondSnapshot);
            assertEquals(123_456L, cached.getAsLong());
            FabricGearConfig.detachClient(first);
            assertTrue(MekanismConfig.gear.isLoaded());
            assertEquals(123_456L, cached.getAsLong());
            FabricGearConfig.detachClient(second);
            assertFalse(MekanismConfig.gear.isLoaded());
            assertEquals(64_000L, cached.getAsLong());
        } finally {
            FabricGearConfig.detachClient(first);
            FabricGearConfig.detachClient(second);
            server.getConfigSpec().unload();
        }
    }
}
