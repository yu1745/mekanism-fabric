package mekanism.common.network.to_server;

import mekanism.api.MekanismAPI;
import mekanism.api.gear.ModuleData;
import mekanism.common.network.IMekanismPacket;
import mekanism.common.network.PacketUtils;
import mekanism.common.tile.TileEntityModificationStation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;

public record PacketRemoveModule(BlockPos pos, Holder<ModuleData<?>> moduleType, boolean removeAll) implements IMekanismPacket {

    public static final CustomPacketPayload.Type<PacketRemoveModule> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("mekanism", "remove_module"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketRemoveModule> STREAM_CODEC = StreamCodec.composite(
          BlockPos.STREAM_CODEC, PacketRemoveModule::pos,
          ByteBufCodecs.holderRegistry(MekanismAPI.MODULE_REGISTRY_NAME), PacketRemoveModule::moduleType,
          ByteBufCodecs.BOOL, PacketRemoveModule::removeAll,
          PacketRemoveModule::new
    );

    @NotNull
    @Override
    public CustomPacketPayload.Type<PacketRemoveModule> type() {
        return TYPE;
    }

    @Override
    public void handle(Player player) {
        if (PacketUtils.guiTile(player, pos) instanceof TileEntityModificationStation tile) {
            tile.removeModule(player, moduleType, removeAll);
        }
    }
}
