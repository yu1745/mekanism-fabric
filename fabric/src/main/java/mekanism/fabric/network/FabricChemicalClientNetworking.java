package mekanism.fabric.network;

import mekanism.fabric.chemical.FabricChemicalClientLifecycle;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;

/** Physical-client receivers with explicit executor handoff and generation rejection. */
public final class FabricChemicalClientNetworking {
    private static final Component INVALID_SNAPSHOT = Component.literal("Invalid Mekanism chemical snapshot");
    private static boolean initialized;

    private FabricChemicalClientNetworking() {
    }

    public static synchronized void initialize() {
        if (initialized) return;
        if (!ClientConfigurationNetworking.registerGlobalReceiver(ChemicalSnapshotPayload.TYPE, (payload, context) -> {
            long generation = FabricChemicalClientLifecycle.beginConfiguration();
            context.client().execute(() -> accept(payload, true, generation,
                  () -> context.responseSender().sendPacket(new ChemicalSnapshotAckPayload(payload.sessionId(), payload.epoch())),
                  () -> {
                      var listener = context.client().getConnection();
                      if (listener != null) listener.getConnection().disconnect(INVALID_SNAPSHOT);
                  }));
        })) throw new IllegalStateException("Chemical configuration receiver already registered");
        if (!ClientPlayNetworking.registerGlobalReceiver(ChemicalSnapshotPayload.TYPE, (payload, context) -> {
            long generation = FabricChemicalClientLifecycle.generation();
            Connection connection = context.player().connection.getConnection();
            context.client().execute(() -> accept(payload, false, generation, () -> {}, () -> connection.disconnect(INVALID_SNAPSHOT)));
        })) throw new IllegalStateException("Chemical play receiver already registered");
        initialized = true;
    }

    /** Fail closed for current-session failures, but never let a queued old-session task disconnect a replacement connection. */
    static void accept(ChemicalSnapshotPayload payload, boolean initial, long generation, Runnable accepted, Runnable rejected) {
        if (!FabricChemicalClientLifecycle.isCurrentGeneration(generation)) return;
        try {
            FabricChemicalClientLifecycle.publish(payload.sessionId(), payload.resolve(), initial, generation);
            accepted.run();
        } catch (RuntimeException failure) {
            if (FabricChemicalClientLifecycle.isCurrentGeneration(generation)) rejected.run();
        }
    }
}
