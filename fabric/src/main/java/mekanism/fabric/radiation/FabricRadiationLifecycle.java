package mekanism.fabric.radiation;

import mekanism.common.lib.radiation.PlayerExposure;
import mekanism.common.lib.radiation.RadiationManager;
import mekanism.common.network.to_client.radiation.PacketEnvironmentalRadiationData;
import mekanism.common.network.to_client.radiation.PacketPlayerRadiationData;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

/** Radiation's native server lifecycle and packet registration. Entity/player ticking is at the actual original tick sites. */
public final class FabricRadiationLifecycle {
    private static boolean initialized;

    private FabricRadiationLifecycle() {
    }

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        FabricRadiationAttachments.initialize();
        PayloadTypeRegistry.playS2C().register(PacketEnvironmentalRadiationData.TYPE, PacketEnvironmentalRadiationData.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(PacketPlayerRadiationData.TYPE, PacketPlayerRadiationData.STREAM_CODEC);
        ServerTickEvents.START_SERVER_TICK.register(server -> RadiationManager.get().onTickPre(server));
        ServerTickEvents.END_WORLD_TICK.register(world -> RadiationManager.get().tickServerWorld(world));
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> {
            PlayerExposure.resetPlayer(player.getUUID());
            PlayerExposure.syncPlayer(player);
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            //Both Fabric's transfer and our sync use AFTER_RESPAWN; don't rely on mod entrypoint/listener order.
            //Install this one immutable domain value before its packet. The native transfer later copies the
            //identical value when alive, and does not copy this attachment on death.
            FabricRadiationAttachments.setRadiation(newPlayer, alive ? FabricRadiationAttachments.getRadiation(oldPlayer)
                  : RadiationManager.get().baselineRadiation());
            PlayerExposure.syncPlayer(newPlayer);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> PlayerExposure.resetPlayer(handler.player.getUUID()));
        initialized = true;
    }
}
