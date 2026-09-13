package mekanism.fabric.tile.fixture;

import mekanism.common.tile.base.TileEntityUpdateable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Fixture-only registered block entity used to exercise the production base-tile lifecycle over real network paths.
 *
 * <p>It extends the real {@link TileEntityUpdateable} and wires the same three real code paths a production machine uses: a full persisted reader with a save-only field,
 * a reduced update tag that omits that field, and a reduced reader. Nothing here is a production machine and nothing in it is packaged into the release JAR.</p>
 *
 * <p>The reduced write counter is deliberately separate from the full-read counter: the runtime gate asserts reduced updates never advance the full-read counter, which
 * is what proves the two client dispatch hooks reach {@code handleUpdateTag} instead of the vanilla full {@code loadWithComponents} replay.</p>
 */
public class TileUpdateRuntimeFixture extends TileEntityUpdateable {

    public static final String SAVE_ONLY = "fixture_save_only";
    public static final String REDUCED = "fixture_reduced";
    /** Set when the reduced path sees it; a full load must never observe this key. */
    public static final String FULL_ONLY = "fixture_full_only";

    private int fullLoads;
    private int reducedLoads;
    private int dataPacketCalls;
    private int saveOnly;
    private int reduced;

    public TileUpdateRuntimeFixture(Holder<? extends BlockEntityType<?>> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        fullLoads++;
        super.loadAdditional(tag, provider);
        saveOnly = tag.getInt(SAVE_ONLY);
        //A full persisted load restores the complete state, including the reduced value
        reduced = tag.getInt(REDUCED);
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putInt(SAVE_ONLY, saveOnly);
        tag.putInt(REDUCED, reduced);
        tag.putBoolean(FULL_ONLY, false);
    }

    @Override
    public CompoundTag getReducedUpdateTag(HolderLookup.Provider provider) {
        //A real reduced tag: the reduced value and the components carrier, and deliberately no save-only field
        CompoundTag tag = super.getReducedUpdateTag(provider);
        tag.putInt(REDUCED, reduced);
        return tag;
    }

    /**
     * Records the retained vanilla-packet entry point. The runtime gate asserts this advanced for the block-entity packet path and stayed put for the custom payload
     * path, which is what makes a bypass straight into {@code handleUpdateTag} observable instead of silently passing.
     */
    @Override
    public void onDataPacket(net.minecraft.network.Connection net, net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket pkt,
          HolderLookup.Provider provider) {
        dataPacketCalls++;
        super.onDataPacket(net, pkt, provider);
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider provider) {
        super.handleUpdateTag(tag, provider);
        reducedLoads++;
        if (tag.contains(REDUCED)) {
            reduced = tag.getInt(REDUCED);
        }
    }

    public int fullLoads() {
        return fullLoads;
    }

    public int reducedLoads() {
        return reducedLoads;
    }

    public int dataPacketCalls() {
        return dataPacketCalls;
    }

    public int saveOnly() {
        return saveOnly;
    }

    public int reduced() {
        return reduced;
    }

    public void setSaveOnly(int saveOnly) {
        this.saveOnly = saveOnly;
    }

    public void setReduced(int reduced) {
        this.reduced = reduced;
    }

    @Override
    public String toString() {
        return "TileUpdateRuntimeFixture[pos=" + getBlockPos() + ", saveOnly=" + saveOnly + ", reduced=" + reduced + "]";
    }
}
