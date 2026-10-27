package mekanism.fabric.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.netty.buffer.Unpooled;
import mekanism.common.network.to_client.PacketHitBlockEffect;
import mekanism.common.network.to_client.PacketPortalFX;
import mekanism.common.network.to_client.PacketSetDeltaMovement;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class PortalNetworkingTest {
    @Test
    void s2cTypeIsRegisteredAndOriginalPayloadRoundTrips() {
        FabricPortalNetworking.initialize();
        FabricPortalNetworking.initialize();
        assertThrows(IllegalArgumentException.class, () -> PayloadTypeRegistry.playS2C().register(PacketPortalFX.TYPE, PacketPortalFX.STREAM_CODEC));
        assertThrows(IllegalArgumentException.class, () -> PayloadTypeRegistry.playS2C().register(PacketSetDeltaMovement.TYPE, PacketSetDeltaMovement.STREAM_CODEC));
        assertThrows(IllegalArgumentException.class, () -> PayloadTypeRegistry.playS2C().register(PacketHitBlockEffect.TYPE, PacketHitBlockEffect.STREAM_CODEC));
        PacketPortalFX packet = new PacketPortalFX(new BlockPos(-15, 82, 63), Direction.NORTH);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            PacketPortalFX.STREAM_CODEC.encode(buffer, packet);
            assertEquals(packet, PacketPortalFX.STREAM_CODEC.decode(buffer));
            PacketSetDeltaMovement motion = new PacketSetDeltaMovement(new Vec3(0.25, -1.5, 3));
            PacketSetDeltaMovement.STREAM_CODEC.encode(buffer, motion);
            assertEquals(motion, PacketSetDeltaMovement.STREAM_CODEC.decode(buffer));
            BlockHitResult hit = new BlockHitResult(new Vec3(-14.5, 82.25, 63.75), Direction.NORTH, new BlockPos(-15, 82, 63), false);
            PacketHitBlockEffect.STREAM_CODEC.encode(buffer, new PacketHitBlockEffect(hit));
            BlockHitResult decoded = PacketHitBlockEffect.STREAM_CODEC.decode(buffer).result();
            assertEquals(hit.getBlockPos(), decoded.getBlockPos());
            assertEquals(hit.getDirection(), decoded.getDirection());
            assertEquals(hit.getLocation(), decoded.getLocation());
        } finally {
            buffer.release();
        }
    }
}
