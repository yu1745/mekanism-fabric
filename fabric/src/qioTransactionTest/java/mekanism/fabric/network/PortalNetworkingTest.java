package mekanism.fabric.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.netty.buffer.Unpooled;
import mekanism.common.network.to_client.PacketPortalFX;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

class PortalNetworkingTest {
    @Test
    void s2cTypeIsRegisteredAndOriginalPayloadRoundTrips() {
        FabricPortalNetworking.initialize();
        FabricPortalNetworking.initialize();
        assertThrows(IllegalArgumentException.class, () -> PayloadTypeRegistry.playS2C().register(PacketPortalFX.TYPE, PacketPortalFX.STREAM_CODEC));
        PacketPortalFX packet = new PacketPortalFX(new BlockPos(-15, 82, 63), Direction.NORTH);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            PacketPortalFX.STREAM_CODEC.encode(buffer, packet);
            assertEquals(packet, PacketPortalFX.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }
}
