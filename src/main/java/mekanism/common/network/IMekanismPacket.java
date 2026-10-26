package mekanism.common.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;

/**
 * Fabric port delta: the official NeoForge {@code IPayloadContext} handler parameter is replaced by the player the port's
 * networking owner already resolved on the correct physical side before dispatch; packets never read the payload context.
 */
public interface IMekanismPacket extends CustomPacketPayload {

    void handle(Player player);
}
