package mekanism.fabric.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.Unpooled;
import java.util.UUID;
import mekanism.common.base.PlayerState;
import mekanism.common.network.to_client.player_data.PacketPlayerData;
import mekanism.common.network.to_client.player_data.PacketResetPlayerClient;
import mekanism.common.network.to_server.PacketGearStateUpdate;
import mekanism.common.network.to_server.PacketGearStateUpdate.GearType;
import mekanism.test.TileLevelStub;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PlayerStateNetworkingTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        mekanism.fabric.chemical.FabricChemicalRegistryLifecycle.initialize();
    }

    @Test
    void stateOwnersAreIndependentAndServerCleanupDoesNotClearClient() {
        UUID uuid = UUID.randomUUID();
        var level = TileLevelStub.create();
        PlayerState.INSTANCE.init(level);
        PlayerState.CLIENT.init(level);
        try {
            PlayerState.CLIENT.setJetpackState(uuid, true, false);
            assertTrue(PlayerState.CLIENT.isJetpackOn(uuid));
            assertFalse(PlayerState.INSTANCE.isJetpackOn(uuid));
            PlayerState.INSTANCE.setJetpackState(uuid, true, false);
            PlayerState.INSTANCE.setScubaMaskState(uuid, true, false);
            PlayerState.INSTANCE.setGravitationalModulationState(uuid, true, false);
            PacketPlayerData snapshot = new PacketPlayerData(uuid);
            assertTrue(snapshot.activeJetpack());
            assertTrue(snapshot.activeScubaMask());
            assertTrue(snapshot.activeModulator());
            PlayerState.INSTANCE.clear(false);
            assertFalse(PlayerState.INSTANCE.isJetpackOn(uuid));
            assertTrue(PlayerState.CLIENT.isJetpackOn(uuid));
        } finally {
            PlayerState.INSTANCE.clear(false);
            PlayerState.CLIENT.clear(false);
        }
    }

    @Test
    void senderCannotUpdateAnotherPlayersState() {
        var level = TileLevelStub.create();
        UUID senderId = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        var sender = new net.minecraft.world.entity.player.Player(level, net.minecraft.core.BlockPos.ZERO, 0,
              new com.mojang.authlib.GameProfile(senderId, "sender")) {
            @Override public boolean isSpectator() { return false; }
            @Override public boolean isCreative() { return false; }
        };
        PlayerState.INSTANCE.clear(false);
        new PacketGearStateUpdate(GearType.JETPACK, target, true).handle(sender);
        assertFalse(PlayerState.INSTANCE.isJetpackOn(target));
    }

    @Test
    void registeredPayloadsPreserveOfficialWireData() {
        FabricServerPlayNetworking.initialize();
        FabricGearEffectsNetworking.initialize();
        assertThrows(IllegalArgumentException.class, () -> PayloadTypeRegistry.playC2S().register(PacketGearStateUpdate.TYPE, PacketGearStateUpdate.STREAM_CODEC));
        assertThrows(IllegalArgumentException.class, () -> PayloadTypeRegistry.playS2C().register(PacketPlayerData.TYPE, PacketPlayerData.STREAM_CODEC));
        assertThrows(IllegalArgumentException.class, () -> PayloadTypeRegistry.playS2C().register(PacketResetPlayerClient.TYPE, PacketResetPlayerClient.STREAM_CODEC));
        UUID uuid = UUID.randomUUID();
        var data = new PacketPlayerData(uuid, true, false, true);
        var gear = new PacketGearStateUpdate(GearType.GRAVITATIONAL_MODULATOR, uuid, true);
        var reset = new PacketResetPlayerClient(uuid);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            PacketGearStateUpdate.STREAM_CODEC.encode(buffer, gear);
            assertEquals(gear, PacketGearStateUpdate.STREAM_CODEC.decode(buffer));
            PacketPlayerData.STREAM_CODEC.encode(buffer, data);
            assertEquals(data, PacketPlayerData.STREAM_CODEC.decode(buffer));
            PacketResetPlayerClient.STREAM_CODEC.encode(buffer, reset);
            assertEquals(reset, PacketResetPlayerClient.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }
}
