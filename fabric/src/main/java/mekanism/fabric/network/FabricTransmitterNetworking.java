package mekanism.fabric.network;

import mekanism.common.content.network.ChemicalNetwork.ChemicalTransferEvent;
import mekanism.common.content.network.EnergyNetwork.EnergyTransferEvent;
import mekanism.common.content.network.FluidNetwork.FluidTransferEvent;
import mekanism.common.network.PacketUtils;
import mekanism.common.network.to_client.transmitter.PacketChemicalNetworkContents;
import mekanism.common.network.to_client.transmitter.PacketFluidNetworkContents;
import mekanism.common.network.to_client.transmitter.PacketNetworkScale;
import mekanism.common.network.to_client.transmitter.PacketTransporterBatch;
import mekanism.common.network.to_client.transmitter.PacketTransporterSync;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/** Native registration and original buffered-network transfer listeners; no client classes are loaded here. */
public final class FabricTransmitterNetworking {
    private static boolean initialized;

    private FabricTransmitterNetworking() {
    }

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        PayloadTypeRegistry.playS2C().register(PacketNetworkScale.TYPE, PacketNetworkScale.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(PacketChemicalNetworkContents.TYPE, PacketChemicalNetworkContents.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(PacketFluidNetworkContents.TYPE, PacketFluidNetworkContents.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(PacketTransporterBatch.TYPE, PacketTransporterBatch.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(PacketTransporterSync.TYPE, PacketTransporterSync.STREAM_CODEC);
        EnergyTransferEvent.EVENT.register(event -> PacketUtils.sendToAllTracking(event.network, new PacketNetworkScale(event.network)));
        ChemicalTransferEvent.EVENT.register(event -> {
            var id = event.network.getUUID();
            PacketUtils.log("Sending type '{}' update message for chemical network with id {}", event.transferType.getRegisteredName(), id);
            PacketUtils.sendToAllTracking(event.network, new PacketNetworkScale(event.network), new PacketChemicalNetworkContents(id, event.transferType));
        });
        FluidTransferEvent.EVENT.register(event -> {
            var id = event.network.getUUID();
            PacketUtils.log("Sending type '{}' update message for fluid network with id {}", event.fluidType.getFluidHolder().getRegisteredName(), id);
            PacketUtils.sendToAllTracking(event.network, new PacketNetworkScale(event.network), new PacketFluidNetworkContents(id, event.fluidType));
        });
        initialized = true;
    }
}
