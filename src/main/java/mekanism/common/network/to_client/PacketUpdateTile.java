package mekanism.common.network.to_client;

import io.netty.buffer.ByteBuf;
import java.util.Objects;
import mekanism.common.tile.base.TileEntityUpdateable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mekanism's reduced tile update payload: the tile position plus the reduced update tag.
 *
 * <p>Fabric adaptation of the official packet: the identifier, field order, block position codec, and trusted compound-tag codec are unchanged, while NeoForge's
 * {@code IMekanismPacket}/{@code IPayloadContext} dispatch is replaced by the vanilla {@link CustomPacketPayload} contract plus a Fabric play S2C registration. The
 * server-side recipient resolution lives in {@link FabricTileUpdateNetworking}; handling is deliberately split out of this record so it stays a plain payload.</p>
 */
public record PacketUpdateTile(BlockPos pos, CompoundTag updateTag) implements CustomPacketPayload {

    public static final Logger LOGGER = LoggerFactory.getLogger("Mekanism/TileUpdate");

    public static final CustomPacketPayload.Type<PacketUpdateTile> TYPE = new CustomPacketPayload.Type<>(
          ResourceLocation.fromNamespaceAndPath("mekanism", "update_tile"));
    public static final StreamCodec<ByteBuf, PacketUpdateTile> STREAM_CODEC = StreamCodec.composite(
          BlockPos.STREAM_CODEC, PacketUpdateTile::pos,
          ByteBufCodecs.TRUSTED_COMPOUND_TAG, PacketUpdateTile::updateTag,
          PacketUpdateTile::new
    );

    public PacketUpdateTile(TileEntityUpdateable tile) {
        this(tile.getBlockPos(), tile.getReducedUpdateTag(tile.getLevel().registryAccess()));
    }

    @NotNull
    @Override
    public CustomPacketPayload.Type<PacketUpdateTile> type() {
        return TYPE;
    }

    /**
     * Applies this reduced update on the logical client.
     *
     * <p>Adaptation of the official {@code handle}: the world and registry access come from the current connection/handling call instead of a retained context, an
     * unloaded position is skipped without a warning, and a missing or wrong-class tile keeps the official warning. The caller is responsible for running this on the
     * owner thread.</p>
     */
    public void handle(Level world, HolderLookup.Provider registries) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(registries, "registries");
        //Only handle the update packet if the block is currently loaded (otherwise we would have the warning get logged in cases we don't want it to)
        if (world.isLoaded(pos)) {
            TileEntityUpdateable tile = getTileEntity(world, pos);
            if (tile == null) {
                LOGGER.warn("Update tile packet received for position: {} in world: {}, but no valid tile was found.", pos, world.dimension().location());
            } else {
                tile.handleUpdateTag(updateTag, registries);
            }
        }
    }

    private static TileEntityUpdateable getTileEntity(Level world, BlockPos pos) {
        //Non-loading lookup: an unloaded chunk must never be created by a received update packet
        if (!world.isLoaded(pos)) {
            return null;
        }
        return world.getBlockEntity(pos) instanceof TileEntityUpdateable tile ? tile : null;
    }
}
