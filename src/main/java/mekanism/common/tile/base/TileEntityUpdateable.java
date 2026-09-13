package mekanism.common.tile.base;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import mekanism.api.Chunk3D;
import mekanism.common.network.to_client.PacketUpdateTile;
import mekanism.common.tile.interfaces.ITileWrapper;
import mekanism.fabric.network.FabricTileUpdateNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.SectionPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extension of TileEntity that adds various helpers we use across the majority of our Tiles even those that are not an instance of TileEntityMekanism. Additionally, we
 * improve the performance of markDirty by not firing neighbor updates unless the markDirtyComparator method is overridden.
 */
public abstract class TileEntityUpdateable extends BlockEntity implements ITileWrapper {

    private static final Logger LOGGER = LoggerFactory.getLogger("Mekanism/TileUpdateable");
    /**
     * Exact vanilla {@code BlockEntity.ComponentHelper.COMPONENTS_CODEC} shape. The vanilla helper is package-private, so the platform-neutral expression is spelled
     * out here instead of widening Minecraft's access; semantically it is the same optional {@code components} field defaulting to {@link DataComponentMap#EMPTY}.
     */
    private static final Codec<DataComponentMap> COMPONENTS_CODEC = DataComponentMap.CODEC.optionalFieldOf("components", DataComponentMap.EMPTY).codec();

    @Nullable
    private GlobalPos cachedCoord;
    private boolean cacheCoord;
    private long lastSave;
    private final long worldPositionLong;

    /**
     * Fabric adaptation of the official signature: the platform-neutral vanilla {@link Holder} replaces NeoForge's {@code TileEntityTypeRegistryObject}, so a deferred
     * block entity type holder (including the future official two-parameter registry object) is accepted through its vanilla {@code Holder} supertype and resolved with
     * {@link Holder#value()}. No capability-provider responsibility is admitted or truncated here.
     */
    public TileEntityUpdateable(Holder<? extends BlockEntityType<?>> type, BlockPos pos, BlockState state) {
        super(type.value(), pos, state);
        this.worldPositionLong = pos.asLong();
    }

    /**
     * Collects all data component types that should be persisted to the dropped item. Override this for any conditionally applied component types.
     */
    public List<DataComponentType<?>> getRemapEntries() {
        return new ArrayList<>(collectComponents().keySet());
    }

    /**
     * Called when block is placed in world
     */
    public void onAdded() {
    }

    /**
     * Call this for tiles that we may call {@link #getTileGlobalPos()} a fair amount on to cache the coord when position/world information changes.
     */
    protected void cacheCoord() {
        //Mark that we want to cache the coord and then update the coord if needed
        cacheCoord = true;
        updateCoord();
    }

    /**
     * Like getWorld(), but for when you _know_ world won't be null
     *
     * @return The world!
     */
    @NotNull
    protected Level getWorldNN() {
        return Objects.requireNonNull(getLevel(), "getWorldNN called before world set");
    }

    public boolean isRemote() {
        return getWorldNN().isClientSide();
    }

    /**
     * Called when the tile is permanently removed
     *
     * @implNote We only need to handle logic that happens when removed and not unloaded as if it happens for both then setRemoved will handle it
     */
    public void blockRemoved() {
    }

    /**
     * Used for checking if we need to update comparators.
     *
     * @apiNote Only call on the server
     */
    public void markDirtyComparator() {
    }

    @Override
    public final void setChanged() {
        setChanged(true);
    }

    public final void markForSave() {
        setChanged(false);
    }

    protected void setChanged(boolean updateComparator) {
        //Copy of the base impl of markDirty in TileEntity, except only updates comparator state when something changed
        // and if our block supports having a comparator signal, instead of always doing it
        if (level != null) {
            long time = level.getGameTime();
            if (lastSave != time) {
                //Only mark the chunk as dirty at most once per tick
                markChunkDirty(level, worldPosition);
                lastSave = time;
            }
            if (updateComparator && !isRemote()) {
                markDirtyComparator();
            }
        }
    }

    /**
     * Marks a chunk as dirty if it is currently loaded. Fabric adaptation of {@code WorldUtils.markChunkDirty}: the chunk is requested with {@code create=false}, so an
     * unloaded chunk is never created, and only a present chunk gets {@code setUnsaved}. Comparator updates and save-only marking stay separate.
     */
    private static void markChunkDirty(Level world, BlockPos pos) {
        ChunkAccess chunk = world.getChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()), ChunkStatus.FULL, false);
        if (chunk != null) {
            chunk.setUnsaved(true);
        }
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /**
     * Reduced-update entry point. NeoForge adds this method to {@code BlockEntity} and patches both the initial chunk-tag consumer and the block-entity packet consumer to
     * reach it; vanilla has no such member, so the method is retained without the platform's {@code @Override} and is reached through our client-only dispatch hooks. The
     * reduced load deliberately calls the non-virtual {@code super.loadAdditional} so a subclass's full saved-NBT reader never runs for a reduced tag.
     */
    public void handleUpdateTag(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider provider) {
        //We don't want to do a full read from NBT so simply call the super's read method to let the platform do whatever
        // it wants, but don't treat this as if it was the full saved NBT data as not everything has to be synced to the client.
        // Note: this super call must stay non-virtual so subclasses that override loadAdditional keep their save-only sentinel out of reduced updates.
        super.loadAdditional(tag, provider);
        //Copy of logic from BlockEntity#loadWithComponents which we can't just call directly as we don't want to call sub-implementations of loadAdditional
        COMPONENTS_CODEC.parse(provider.createSerializationContext(NbtOps.INSTANCE), tag)
              .resultOrPartial(error -> LOGGER.warn("Failed to load components: {}", error))
              .ifPresent(this::setComponents);
    }

    @NotNull
    @Override
    public CompoundTag getUpdateTag(@NotNull HolderLookup.Provider provider) {
        return getReducedUpdateTag(provider);
    }

    /**
     * Similar to {@link #getUpdateTag(HolderLookup.Provider)} but with reduced information for when we are doing our own syncing.
     */
    @NotNull
    public CompoundTag getReducedUpdateTag(@NotNull HolderLookup.Provider provider) {
        //Add the base update tag information
        return super.getUpdateTag(provider);
    }

    /**
     * Client-side reduced-update entry point for the vanilla {@link ClientboundBlockEntityDataPacket} path. NeoForge adds this method to {@code BlockEntity} and patches
     * {@code ClientPacketListener} to call it; vanilla has no such member, so the method is retained here without the platform's {@code @Override} and is reached through
     * our client-only dispatch hook. The empty-tag contract stays: an empty vanilla packet performs no reduced load.
     */
    public void onDataPacket(@NotNull Connection net, @NotNull ClientboundBlockEntityDataPacket pkt, @NotNull HolderLookup.Provider provider) {
        //Handle the update tag when we are on the client
        CompoundTag tag = pkt.getTag();
        if (!tag.isEmpty()) {
            handleUpdateTag(tag, provider);
        }
    }

    public void sendUpdatePacket() {
        sendUpdatePacket(this);
    }

    public void sendUpdatePacket(BlockEntity tracking) {
        if (isRemote()) {
            LOGGER.warn("Update packet call requested from client side", new IllegalStateException());
        } else if (isRemoved()) {
            LOGGER.warn("Update packet call requested for removed tile", new IllegalStateException());
        } else if (hasPlayersTracking((ServerLevel) tracking.getLevel(), tracking.getBlockPos())) {
            //Note: We use our own update packet/channel to avoid chunk trashing and minecraft attempting to rerender
            // the entire chunk when most often we are just updating a TileEntityRenderer, so the chunk itself
            // does not need to and should not be redrawn.
            // The payload is only built when at least one player tracks the tile, so an untracked tile never allocates a reduced tag.
            PacketUpdateTile payload = new PacketUpdateTile(this);
            for (var player : FabricTileUpdateNetworking.tracking((ServerLevel) tracking.getLevel(), tracking.getBlockPos())) {
                FabricTileUpdateNetworking.send(player, payload);
            }
        }
    }

    protected void updateModelData() {
        //Note: NeoForge's render-data-manager {@code requestModelDataUpdate()} has no vanilla/Fabric equivalent, and this base declares no model-data producer,
        // so the call is intentionally absent rather than faked. What remains is the real loaded-position rerender notification.
        if (level != null && level.isLoaded(worldPosition)) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    public void loadAdditional(@NotNull CompoundTag nbt, @NotNull HolderLookup.Provider provider) {
        super.loadAdditional(nbt, provider);
        updateCoord();
    }

    @Override
    public void setLevel(@NotNull Level world) {
        super.setLevel(world);
        updateCoord();
        //TODO: Do we need to clear the BlockCapabilityCaches we are storing if the level changes?
        // The level changing is not really an expected thing to have happen, but might be worth
        // considering if we run into mods that the caches are causing problems for
    }

    private void updateCoord() {
        if (cacheCoord && level != null) {
            cachedCoord = GlobalPos.of(level.dimension(), worldPosition);
        }
    }

    @Override
    public GlobalPos getTileGlobalPos() {
        return cacheCoord && cachedCoord != null ? cachedCoord : ITileWrapper.super.getTileGlobalPos();
    }

    public long getWorldPositionLong() {
        return worldPositionLong;
    }

    @Override
    public Chunk3D getTileChunk() {
        if (cacheCoord && cachedCoord != null) {
            return new Chunk3D(cachedCoord);
        }
        BlockPos pos = this.getBlockPos();
        return new Chunk3D(this.getLevel().dimension(), SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
    }

    /**
     * Fabric adaptation of {@code PacketUtils.hasPlayersTracking}: true when at least one player currently tracks the chunk containing the position. Keeping this
     * predicate on the tile preserves the official "tracking tile chooses recipients" contract and avoids building a payload nobody would receive.
     */
    private static boolean hasPlayersTracking(ServerLevel level, BlockPos pos) {
        return !FabricTileUpdateNetworking.tracking(level, pos).isEmpty();
    }
}
