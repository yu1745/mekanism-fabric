package mekanism.fabric.network;

import java.util.function.BiConsumer;
import mekanism.common.network.to_client.transmitter.PacketChemicalNetworkContents;
import mekanism.common.network.to_client.transmitter.PacketFluidNetworkContents;
import mekanism.common.network.to_client.transmitter.PacketNetworkScale;
import mekanism.common.network.to_client.transmitter.PacketTransporterBatch;
import mekanism.common.network.to_client.transmitter.PacketTransporterSync;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Fabric play receivers run on the client main thread; look up the current world and network at handling time. */
public final class FabricTransmitterClientNetworking {
    private static boolean initialized;

    private FabricTransmitterClientNetworking() {
    }

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        dispatch(PacketNetworkScale.TYPE, (payload, context) -> payload.handle());
        dispatch(PacketChemicalNetworkContents.TYPE, (payload, context) -> payload.handle());
        dispatch(PacketFluidNetworkContents.TYPE, (payload, context) -> payload.handle());
        dispatch(PacketTransporterBatch.TYPE, (payload, context) -> payload.handle(context.player()));
        dispatch(PacketTransporterSync.TYPE, (payload, context) -> payload.handle(context.player()));
        initialized = true;
    }

    private static <T extends CustomPacketPayload> void dispatch(CustomPacketPayload.Type<T> type,
          BiConsumer<T, ClientPlayNetworking.Context> handler) {
        if (!ClientPlayNetworking.registerGlobalReceiver(type, handler::accept)) {
            throw new IllegalStateException("A client receiver is already registered for " + type.id());
        }
    }
}
