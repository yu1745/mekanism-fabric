package mekanism.common.tile.transmitter;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import mekanism.api.annotations.NothingNullByDefault;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.capabilities.item.TransporterItemStorage;
import mekanism.common.capabilities.resolver.ICapabilityResolver;
import mekanism.common.content.network.transmitter.LogisticalTransporterBase;
import mekanism.common.content.transporter.TransporterStack;
import mekanism.common.lib.transmitter.ConnectionType;
import mekanism.common.util.TransporterUtils;
import mekanism.common.util.WorldUtils;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapability;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class TileEntityLogisticalTransporterBase extends TileEntityTransmitter {

    protected TileEntityLogisticalTransporterBase(Holder<Block> blockProvider, BlockPos pos, BlockState state) {
        super(blockProvider, pos, state);
        addCapabilityResolver(new TransporterCapabilityResolver());
    }

    @Override
    protected abstract LogisticalTransporterBase createTransmitter(Holder<Block> blockProvider);

    @Override
    public LogisticalTransporterBase getTransmitter() {
        return (LogisticalTransporterBase) super.getTransmitter();
    }

    public static void tickClient(Level level, BlockPos pos, BlockState state, TileEntityLogisticalTransporterBase transmitter) {
        transmitter.getTransmitter().onUpdateClient();
    }

    @Override
    public void onUpdateServer() {
        super.onUpdateServer();
        getTransmitter().onUpdateServer();
    }

    @Override
    public void blockRemoved() {
        super.blockRemoved();
        if (!isRemote()) {
            LogisticalTransporterBase transporter = getTransmitter();
            if (!transporter.isUpgrading()) {
                //If the transporter is not currently being upgraded, drop the contents
                for (TransporterStack stack : transporter.getTransit()) {
                    TransporterUtils.drop(transporter, stack);
                }
            }
        }
    }

    @Override
    public void sideChanged(@NotNull Direction side, @NotNull ConnectionType old, @NotNull ConnectionType type) {
        super.sideChanged(side, old, type);
        //Note: We don't expose a cap for when the connection type is none or push and this method only gets called if type != old,
        // so we can check to ensure that if we are one of the two that the other isn't the other one we don't have a cap for
        if (type == ConnectionType.NONE && old != ConnectionType.PUSH || type == ConnectionType.PUSH && old != ConnectionType.NONE) {
            //We no longer have a capability, invalidate it, which will also notify the level
            invalidateCapability(Capabilities.ITEM.block(), side);
        } else if (old == ConnectionType.NONE && type != ConnectionType.PUSH || old == ConnectionType.PUSH && type != ConnectionType.NONE) {
            //Notify any listeners to our position that we now do have a capability
            //Note: We don't invalidate our impls because we know they are already invalid, so we can short circuit setting them to null from null
            invalidateCapabilities();
        }
    }

    @NothingNullByDefault
    private class TransporterCapabilityResolver implements ICapabilityResolver<@Nullable Direction> {

        private static final List<BlockCapability<?, @Nullable Direction>> SUPPORTED_CAPABILITY = Collections.singletonList(Capabilities.ITEM.block());

        private final Map<Direction, TransporterItemStorage> handlers = new EnumMap<>(Direction.class);

        @Override
        public List<BlockCapability<?, @Nullable Direction>> getSupportedCapabilities() {
            return SUPPORTED_CAPABILITY;
        }

        /**
         * Lazily get and cache the insert-only real Storage endpoint for the given side. The null side is never
         * exposed (no endpoint); only sides with an accepting raw connection expose the transporter endpoint.
         */
        @Nullable
        @Override
        public <T> T resolve(BlockCapability<T, @Nullable Direction> capability, @Nullable Direction side) {
            if (side == null) {
                //We provide no endpoint for the null side
                return null;
            }
            TransporterItemStorage cached = handlers.get(side);
            if (cached == null) {
                LogisticalTransporterBase transporter = getTransmitter();
                //Note: We check here whether it exposes the cap rather than in the endpoint itself as we invalidate
                // the cached endpoint whenever this changes
                if (transporter.exposesInsertCap(side)) {
                    cached = new TransporterItemStorage(transporter, WorldUtils.relativePos(getWorldPositionLong(), side), () -> level == null ? -1 : level.getGameTime());
                    handlers.put(side, cached);
                }
            }
            return (T) cached;
        }

        @Override
        public void invalidate(BlockCapability<?, @Nullable Direction> capability, @Nullable Direction side) {
            if (side != null) {
                handlers.remove(side);
            }
        }

        @Override
        public void invalidateAll() {
            handlers.clear();
        }
    }
}
