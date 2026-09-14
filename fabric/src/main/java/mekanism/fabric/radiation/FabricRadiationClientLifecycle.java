package mekanism.fabric.radiation;

import mekanism.client.particle.RadiationParticle;
import mekanism.client.render.hud.RadiationOverlay;
import mekanism.common.lib.radiation.ClientRadiation;
import mekanism.common.network.to_client.radiation.PacketEnvironmentalRadiationData;
import mekanism.common.network.to_client.radiation.PacketPlayerRadiationData;
import mekanism.common.registries.MekanismParticleTypes;
import mekanism.common.util.MekanismUtils;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;

/** Physical-client owner: native main-thread receivers, the original pre-tick particles, and disconnect cleanup. */
public final class FabricRadiationClientLifecycle {
    private static boolean initialized;

    private FabricRadiationClientLifecycle() {
    }

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        if (!ClientPlayNetworking.registerGlobalReceiver(PacketEnvironmentalRadiationData.TYPE, (payload, context) -> payload.handle())) {
            throw new IllegalStateException("Duplicate receiver for " + PacketEnvironmentalRadiationData.TYPE.id());
        }
        if (!ClientPlayNetworking.registerGlobalReceiver(PacketPlayerRadiationData.TYPE, (payload, context) -> payload.handle(context.player()))) {
            throw new IllegalStateException("Duplicate receiver for " + PacketPlayerRadiationData.TYPE.id());
        }
        ParticleFactoryRegistry.getInstance().register(MekanismParticleTypes.RADIATION.get(), RadiationParticle.Factory::new);
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (client.level != null && client.player != null && !client.isPaused() && MekanismUtils.isTickingNormally(client.level)) {
                ClientRadiation.tickClient(client.player);
            }
        });
        //Native setLevel tail, before subsequent radiation packets; same-world respawn does not reset the environmental dose.
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((client, world) -> ClientRadiation.resetClient());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientRadiation.resetClient();
            RadiationOverlay.INSTANCE.resetRadiation();
        });
        initialized = true;
    }
}
