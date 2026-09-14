package mekanism.common.network.to_client.configuration;

import java.util.function.Consumer;
import mekanism.common.network.to_client.security.PacketBatchSecurityUpdate;
import net.fabricmc.fabric.api.networking.v1.FabricServerConfigurationNetworkHandler;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import org.jetbrains.annotations.NotNull;

/** Original complete security snapshot task, sent during native configuration before the player enters play. */
public record SyncAllSecurityData(ServerConfigurationPacketListenerImpl listener, MinecraftServer server) implements ConfigurationTask {

    private static final Type TYPE = new Type("mekanism:sync_security");

    public void run(Consumer<CustomPacketPayload> sender) {
        //FrequencyManager is server-owned; a configuration task can be started from the connection thread.
        server.execute(() -> {
            if (!listener.isAcceptingMessages()) {
                return;
            }
            sender.accept(new PacketBatchSecurityUpdate());
            ((FabricServerConfigurationNetworkHandler) listener).completeTask(type());
        });
    }

    @Override
    public void start(Consumer<Packet<?>> sender) {
        run(payload -> sender.accept(ServerConfigurationNetworking.createS2CPacket(payload)));
    }

    @NotNull
    @Override
    public Type type() {
        return TYPE;
    }
}
