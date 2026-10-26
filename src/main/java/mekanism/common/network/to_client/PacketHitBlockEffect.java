package mekanism.common.network.to_client;

import mekanism.common.network.IMekanismPacket;
import mekanism.common.network.PacketUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;

public record PacketHitBlockEffect(BlockHitResult result) implements IMekanismPacket {

    public static final CustomPacketPayload.Type<PacketHitBlockEffect> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("mekanism", "hit_block"));
    public static final StreamCodec<FriendlyByteBuf, PacketHitBlockEffect> STREAM_CODEC = PacketUtils.BLOCK_HIT_RESULT_STREAM_CODEC.map(
          PacketHitBlockEffect::new, PacketHitBlockEffect::result
    );

    @NotNull
    @Override
    public CustomPacketPayload.Type<PacketHitBlockEffect> type() {
        return TYPE;
    }

    @Override
    public void handle(Player player) {
        //Fabric port delta: NeoForge adds a positional addBlockHitEffects overload; vanilla exposes the same particle
        // burst through ParticleEngine#destroy with the local block state.
        if (Minecraft.getInstance().level != null) {
            Minecraft.getInstance().particleEngine.destroy(result.getBlockPos(), Minecraft.getInstance().level.getBlockState(result.getBlockPos()));
        }
    }
}