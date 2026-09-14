package mekanism.common.content.network.transmitter;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.SerializationConstants;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.fluid.IMekanismFluidHandler;
import mekanism.api.functions.ConstantPredicates;
import mekanism.api.math.MathUtils;
import mekanism.common.block.attribute.Attribute;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.capabilities.fluid.BasicFluidTank;
import mekanism.common.content.network.FluidNetwork;
import mekanism.common.lib.transmitter.CompatibleTransmitterValidator;
import mekanism.common.lib.transmitter.CompatibleTransmitterValidator.CompatibleFluidTransmitterValidator;
import mekanism.common.lib.transmitter.ConnectionType;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.lib.transmitter.acceptor.AbstractAcceptorCache;
import mekanism.common.lib.transmitter.acceptor.AcceptorCache;
import mekanism.common.tier.PipeTier;
import mekanism.common.tile.transmitter.TileEntityTransmitter;
import mekanism.common.upgrade.transmitter.MechanicalPipeUpgradeData;
import mekanism.common.upgrade.transmitter.TransmitterUpgradeData;
import mekanism.common.util.EnumUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.NBTUtils;
import mekanism.api.fluid.FluidStack;
import mekanism.fabric.fluid.FabricFluidConversions;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MechanicalPipe extends BufferedTransmitter<Storage<FluidVariant>, FluidNetwork, FluidStack, MechanicalPipe> implements IMekanismFluidHandler,
      IUpgradeableTransmitter<MechanicalPipeUpgradeData> {

    public final PipeTier tier;
    @NotNull
    public FluidStack saveShare = FluidStack.EMPTY;
    private final List<IExtendedFluidTank> tanks;
    public final BasicFluidTank buffer;

    public MechanicalPipe(Holder<Block> blockProvider, TileEntityTransmitter tile) {
        super(tile, TransmissionType.FLUID);
        this.tier = Attribute.getTier(blockProvider, PipeTier.class);
        //TODO: If we make fluids support longs then adjust this
        buffer = BasicFluidTank.create(MathUtils.clampToInt(getCapacity()), ConstantPredicates.alwaysFalse(), ConstantPredicates.alwaysTrue(), this);
        tanks = Collections.singletonList(buffer);
    }

    @Override
    protected AbstractAcceptorCache<Storage<FluidVariant>, ?> createAcceptorCache() {
        return new AcceptorCache<>(getTransmitterTile(), Capabilities.FLUID.block());
    }

    @Override
    @SuppressWarnings("unchecked")
    public AcceptorCache<Storage<FluidVariant>> getAcceptorCache() {
        return (AcceptorCache<Storage<FluidVariant>>) super.getAcceptorCache();
    }

    @Override
    public PipeTier getTier() {
        return tier;
    }

    @Override
    public void pullFromAcceptors() {
        if (!hasPullSide || getAvailablePull() <= 0) {
            return;
        }
        AcceptorCache<Storage<FluidVariant>> acceptorCache = getAcceptorCache();
        for (Direction side : EnumUtils.DIRECTIONS) {
            if (!isConnectionType(side, ConnectionType.PULL)) {
                continue;
            }
            Storage<FluidVariant> connectedAcceptor = acceptorCache.getConnectedAcceptor(side);
            if (connectedAcceptor != null && connectedAcceptor.iterator().hasNext()) {
                //Note: We recheck the buffer each time in case we ended up accepting fluid somewhere
                // and our buffer changed and is no longer empty
                FluidStack bufferWithFallback = getBufferWithFallback();
                int availablePull = getAvailablePull();
                try (Transaction pull = Transaction.openOuter()) {
                    FluidStack received = null;
                    if (bufferWithFallback.isEmpty()) {
                        //If we don't have a fluid stored try pulling whatever resource the acceptor exposes
                        for (StorageView<FluidVariant> view : connectedAcceptor) {
                            if (!view.isResourceBlank()) {
                                received = extractWholeMillibuckets(connectedAcceptor, view.getResource(), availablePull, pull);
                                if (received != null) {
                                    break;
                                }
                            }
                        }
                    } else {
                        //Otherwise, try pulling the same type of fluid we have stored requesting up to as much as we are able to pull
                        FluidVariant stored = FabricFluidConversions.toFabricVariant(bufferWithFallback);
                        received = extractWholeMillibuckets(connectedAcceptor, stored, availablePull, pull);
                    }
                    if (received != null && takeFluid(received, Action.SIMULATE).isEmpty()) {
                        //If we received some fluid and are able to insert it all, then actually take it into our buffer and commit the
                        // external extraction. The extraction is still inside the open transaction, so a failed simulation here aborts
                        // it and nothing is removed from the acceptor (no dupe window).
                        takeFluid(received, Action.EXECUTE);
                        pull.commit();
                    }
                    //Otherwise: abort, leaving the acceptor untouched
                }
            }
        }
    }

    /**
     * Extracts up to {@code maxMillibuckets} of {@code variant} from {@code storage} inside {@code transaction}, accepting only an exact whole-millibucket result. A
     * fractional probe result is aborted and retried at its whole-millibucket floor (bounded); the extraction only stays when the transaction is committed by the caller.
     *
     * @return the extracted stack, or {@code null} if the acceptor cannot provide an exact whole-millibucket amount.
     */
    @Nullable
    private static FluidStack extractWholeMillibuckets(Storage<FluidVariant> storage, FluidVariant variant, int maxMillibuckets, Transaction transaction) {
        long request = (long) maxMillibuckets * FabricFluidConversions.TRANSFER_UNITS_PER_MILLIBUCKET;
        if (request <= 0 || request > FabricFluidConversions.MAX_EXACT_TRANSFER_UNITS) {
            return null;
        }
        long reported;
        try (Transaction probe = Transaction.openNested(transaction)) {
            reported = storage.extract(variant, request, probe);
            if (reported < 0 || reported > request) {
                return null;
            }
            if (reported == request) {
                probe.commit();
                return FabricFluidConversions.toFluidStack(variant, reported);
            }
            //Fractional or partial: abort the probe and fall through to the bounded floor retry
        }
        long floorMillibuckets = reported / FabricFluidConversions.TRANSFER_UNITS_PER_MILLIBUCKET;
        while (floorMillibuckets >= 1) {
            long retryRequest = floorMillibuckets * FabricFluidConversions.TRANSFER_UNITS_PER_MILLIBUCKET;
            try (Transaction nested = Transaction.openNested(transaction)) {
                long extracted = storage.extract(variant, retryRequest, nested);
                if (extracted < 0 || extracted > retryRequest) {
                    return null;
                }
                if (extracted == retryRequest) {
                    nested.commit();
                    return FabricFluidConversions.toFluidStack(variant, extracted);
                }
                long nextFloor = extracted / FabricFluidConversions.TRANSFER_UNITS_PER_MILLIBUCKET;
                if (nextFloor >= floorMillibuckets) {
                    return null;
                }
                floorMillibuckets = nextFloor;
            }
        }
        return null;
    }

    private int getAvailablePull() {
        if (hasTransmitterNetwork()) {
            return Math.min(tier.getPipePullAmount(), getTransmitterNetwork().fluidTank.getNeeded());
        }
        return Math.min(tier.getPipePullAmount(), buffer.getNeeded());
    }

    @Nullable
    @Override
    public MechanicalPipeUpgradeData getUpgradeData() {
        return new MechanicalPipeUpgradeData(redstoneReactive, getConnectionTypesRaw(), getShare());
    }

    @Override
    public boolean dataTypeMatches(@NotNull TransmitterUpgradeData data) {
        return data instanceof MechanicalPipeUpgradeData;
    }

    @Override
    public void parseUpgradeData(@NotNull MechanicalPipeUpgradeData data) {
        redstoneReactive = data.redstoneReactive;
        setConnectionTypesRaw(data.connectionTypes);
        takeFluid(data.contents, Action.EXECUTE);
    }

    @Override
    public void read(HolderLookup.Provider provider, @NotNull CompoundTag nbtTags) {
        super.read(provider, nbtTags);
        saveShare = FluidStack.parseOptional(provider, nbtTags.getCompound(SerializationConstants.FLUID));
        buffer.setStack(saveShare);
    }

    @NotNull
    @Override
    public CompoundTag write(HolderLookup.Provider provider, @NotNull CompoundTag nbtTags) {
        super.write(provider, nbtTags);
        if (hasTransmitterNetwork()) {
            getTransmitterNetwork().validateSaveShares(this);
        }
        if (saveShare.isEmpty()) {
            nbtTags.remove(SerializationConstants.FLUID);
        } else {
            nbtTags.put(SerializationConstants.FLUID, saveShare.save(provider));
        }
        return nbtTags;
    }

    @Override
    public CompatibleTransmitterValidator<Storage<FluidVariant>, FluidNetwork, MechanicalPipe> getNewOrphanValidator() {
        return new CompatibleFluidTransmitterValidator(this);
    }

    @Override
    public boolean isValidTransmitter(TileEntityTransmitter transmitter, Direction side) {
        if (super.isValidTransmitter(transmitter, side) && transmitter.getTransmitter() instanceof MechanicalPipe other) {
            FluidStack buffer = getBufferWithFallback();
            if (buffer.isEmpty() && hasTransmitterNetwork() && getTransmitterNetwork().getPrevTransferAmount() > 0) {
                buffer = getTransmitterNetwork().lastFluid;
            }
            FluidStack otherBuffer = other.getBufferWithFallback();
            if (otherBuffer.isEmpty() && other.hasTransmitterNetwork() && other.getTransmitterNetwork().getPrevTransferAmount() > 0) {
                otherBuffer = other.getTransmitterNetwork().lastFluid;
            }
            return buffer.isEmpty() || otherBuffer.isEmpty() || FluidStack.isSameFluidSameComponents(buffer, otherBuffer);
        }
        return false;
    }

    @Override
    public FluidNetwork createEmptyNetworkWithID(UUID networkID) {
        return new FluidNetwork(networkID);
    }

    @Override
    public FluidNetwork createNetworkByMerging(Collection<FluidNetwork> networks) {
        return new FluidNetwork(networks);
    }

    @Override
    protected boolean canHaveIncompatibleNetworks() {
        return true;
    }

    @Override
    public long getCapacity() {
        return tier.getPipeCapacity();
    }

    @NotNull
    @Override
    public FluidStack releaseShare() {
        FluidStack ret = buffer.getFluid();
        buffer.setEmpty();
        return ret;
    }

    @Override
    public boolean noBufferOrFallback() {
        return getBufferWithFallback().isEmpty();
    }

    @NotNull
    @Override
    public FluidStack getBufferWithFallback() {
        FluidStack buffer = getShare();
        //If we don't have a buffer try falling back to the network's buffer
        if (buffer.isEmpty() && hasTransmitterNetwork()) {
            return getTransmitterNetwork().getBuffer();
        }
        return buffer;
    }

    @NotNull
    @Override
    public FluidStack getShare() {
        return buffer.getFluid();
    }

    @Override
    public void takeShare() {
        if (hasTransmitterNetwork()) {
            FluidNetwork network = getTransmitterNetwork();
            if (!network.fluidTank.isEmpty() && !saveShare.isEmpty()) {
                int amount = saveShare.getAmount();
                MekanismUtils.logMismatchedStackSize(network.fluidTank.shrinkStack(amount, Action.EXECUTE), amount);
                buffer.setStack(saveShare);
            }
        }
    }

    @NotNull
    @Override
    public List<IExtendedFluidTank> getFluidTanks(@Nullable Direction side) {
        if (hasTransmitterNetwork()) {
            return getTransmitterNetwork().getFluidTanks(side);
        }
        return tanks;
    }

    @Override
    public void onContentsChanged() {
        getTransmitterTile().setChanged();
    }

    /**
     * @return remainder
     */
    @NotNull
    public FluidStack takeFluid(@NotNull FluidStack fluid, Action action) {
        if (hasTransmitterNetwork()) {
            return getTransmitterNetwork().fluidTank.insert(fluid, action, AutomationType.INTERNAL);
        }
        return buffer.insert(fluid, action, AutomationType.INTERNAL);
    }

    @Override
    protected void handleContentsUpdateTag(@NotNull FluidNetwork network, @NotNull CompoundTag tag, @NotNull HolderLookup.Provider provider) {
        super.handleContentsUpdateTag(network, tag, provider);
        NBTUtils.setFluidStackIfPresent(provider, tag, SerializationConstants.FLUID, network::setLastFluid);
        NBTUtils.setFloatIfPresent(tag, SerializationConstants.SCALE, scale -> network.currentScale = scale);
    }
}