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
import mekanism.common.capabilities.fluid.BasicFluidTank;
import mekanism.common.content.network.FluidNetwork;
import mekanism.common.content.network.distribution.FluidSendTransaction;
import mekanism.common.lib.transmitter.CompatibleTransmitterValidator;
import mekanism.common.lib.transmitter.CompatibleTransmitterValidator.CompatibleFluidTransmitterValidator;
import mekanism.common.lib.transmitter.ConnectionType;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.lib.transmitter.acceptor.AbstractAcceptorCache;
import mekanism.common.lib.transmitter.acceptor.FluidAcceptorCache;
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
        //Fresh per-pipe fluid lookup cache: no returned Storage is retained; each resolution is a fresh
        // FluidStorage.SIDED.find on the opposite face, guarded by the loaded neighbor check
        return new FluidAcceptorCache(getTransmitterTile());
    }

    @Override
    public FluidAcceptorCache getAcceptorCache() {
        return (FluidAcceptorCache) super.getAcceptorCache();
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
        FluidAcceptorCache acceptorCache = getAcceptorCache();
        for (Direction side : EnumUtils.DIRECTIONS) {
            //select the RAW configured PULL sides first. Checking the effective (bitmap-based)
            // connection type before refresh would skip a formerly null provider (bitmap bit still clear) and never
            // rediscover it, so the raw configuration is the candidate set.
            if (getConnectionTypeRaw(side) != ConnectionType.PULL) {
                continue;
            }
            //Refresh this PULL side's acceptor-connection validity (official bitmap policy: redstone/mutual/
            // exclusion checks, packet only on bit change) BEFORE the effective check, so null->valid rediscovery
            // updates the bitmap
            refreshAcceptorConnections(side);
            //Effective PULL check now reflects the refreshed bitmap: a side that just became invalid (redstone,
            // mutual transmission, exclusion) is rejected here instead of being queried
            if (!isConnectionType(side, ConnectionType.PULL)) {
                continue;
            }
            //Bitmap-gated lookup (getCachedAcceptor checks currentAcceptorConnections before resolving); the
            // ungated getConnectedAcceptor is deliberately NOT used
            Storage<FluidVariant> connectedAcceptor = acceptorCache.getCachedAcceptor(side);
            if (connectedAcceptor != null && connectedAcceptor.iterator().hasNext()) {
                //Note: We recheck the buffer each time in case we ended up accepting fluid somewhere
                // and our buffer changed and is no longer empty
                FluidStack bufferWithFallback = getBufferWithFallback();
                int availablePull = getAvailablePull();
                //snapshot the ACTUAL captured destination tank (network tank when attached, otherwise our own buffer) BEFORE any
                // external operation, and perform the internal insertion against that exact captured identity inside the transaction. The old code called
                // takeFluid, which re-resolves the destination at call time (an owner can switch between simulation and execute) and ignored the returned
                // remainder and resulting contents, so a listener exception or partial execute could abort the external extraction while the internal
                // insertion stayed, or commit the extraction despite an incomplete insertion.
                IExtendedFluidTank destination = hasTransmitterNetwork() ? getTransmitterNetwork().fluidTank : buffer;
                FluidStack destinationBefore = copyOfTank(destination);
                try (Transaction pull = Transaction.openOuter()) {
                    FluidSendTransaction.SourceTankSnapshot destinationSnapshot = new FluidSendTransaction.SourceTankSnapshot(destination);
                    destinationSnapshot.updateSnapshots(pull);
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
                    if (received != null && tryInsertIntoCapturedDestination(destination, destinationBefore, received)) {
                        //Exact internal insertion into the still-captured destination verified inside the open transaction; commit both sides atomically.
                        //Any mismatch or exception leaves the acceptor untouched (aborted extraction) and the destination restored by the snapshot
                        pull.commit();
                    }
                    //Otherwise: abort, leaving the acceptor untouched and the destination rolled back
                }
            }
        }
    }

    /**
     * Performs and verifies the INTERNAL insertion of {@code received} into the exact destination tank captured before any external operation. Validates stable owner
     * identity before and after the execute, an empty remainder, and an exact expected read-back; any mismatch or exception returns false so the caller aborts the whole
     * transaction (external extraction + internal insertion together).
     */
    private boolean tryInsertIntoCapturedDestination(IExtendedFluidTank destination, FluidStack destinationBefore, @NotNull FluidStack received) {
        if (currentPullDestination() != destination) {
            //Owner identity changed (network topology switch) between extraction and insertion; abort
            return false;
        }
        FluidStack remainder;
        try {
            remainder = destination.insert(received, Action.EXECUTE, AutomationType.INTERNAL);
        } catch (RuntimeException e) {
            //A throwing listener must abort both sides; the destination contents are restored by the snapshot on abort
            return false;
        }
        if (remainder == null || !remainder.isEmpty()) {
            //Null is an invalid response, not an empty remainder; a partial internal insertion must abort either way
            // (the external extraction must not commit)
            return false;
        }
        if (currentPullDestination() != destination) {
            //Owner identity changed during the insertion; abort
            return false;
        }
        FluidStack expectedAfter;
        if (destinationBefore.isEmpty()) {
            expectedAfter = received;
        } else {
            if (!FluidStack.isSameFluidSameComponents(destinationBefore, received)) {
                //Cannot normally happen: we only pull the stored fluid when the buffer is non-empty, but verify anyway
                return false;
            }
            expectedAfter = destinationBefore.copyWithAmount(destinationBefore.getAmount() + received.getAmount());
        }
        return FluidStack.matches(expectedAfter, copyOfTank(destination));
    }

    /**
     * Re-resolves the destination tank exactly the way the pre-correction takeFluid did, so identity comparisons detect a real owner switch rather than
     * pinning a stale object by construction.
     */
    private IExtendedFluidTank currentPullDestination() {
        return hasTransmitterNetwork() ? getTransmitterNetwork().fluidTank : buffer;
    }

    private static FluidStack copyOfTank(IExtendedFluidTank tank) {
        FluidStack stack = tank.getFluid();
        return stack == null || stack.isEmpty() ? FluidStack.EMPTY : stack.copy();
    }

    /**
     * Extracts up to {@code maxMillibuckets} of {@code variant} from {@code storage} inside {@code transaction}, accepting only a whole-millibucket result.
     *
     * <p>a probe response that is already an exact whole-millibucket amount (full OR partial) is accepted immediately; only a fractional (or zero)
     * report gets the single bounded retry at the whole-millibucket floor of the report.
     *
     * @return the extracted stack, or {@code null} if the acceptor cannot provide a whole-millibucket amount.
     */
    @Nullable
    private static FluidStack extractWholeMillibuckets(Storage<FluidVariant> storage, FluidVariant variant, int maxMillibuckets, Transaction transaction) {
        //Range guard BEFORE multiplying so a long request cannot overflow past MAX_EXACT_TRANSFER_UNITS
        if (maxMillibuckets <= 0 || maxMillibuckets > FabricFluidConversions.MAX_EXACT_TRANSFER_UNITS / FabricFluidConversions.TRANSFER_UNITS_PER_MILLIBUCKET) {
            return null;
        }
        long request = (long) maxMillibuckets * FabricFluidConversions.TRANSFER_UNITS_PER_MILLIBUCKET;
        long reported;
        try (Transaction probe = Transaction.openNested(transaction)) {
            reported = storage.extract(variant, request, probe);
            if (reported < 0 || reported > request) {
                return null;
            }
            if (reported > 0 && reported % FabricFluidConversions.TRANSFER_UNITS_PER_MILLIBUCKET == 0) {
                //Exact whole-millibucket response (full or partial): accept it immediately, no retry needed
                probe.commit();
                return FabricFluidConversions.toFluidStack(variant, reported);
            }
            //Zero, or fractional: abort the probe and do ONE bounded floor retry
        }
        if (reported <= 0) {
            return null;
        }
        long floorDroplets = reported / FabricFluidConversions.TRANSFER_UNITS_PER_MILLIBUCKET * FabricFluidConversions.TRANSFER_UNITS_PER_MILLIBUCKET;
        if (floorDroplets <= 0) {
            return null;
        }
        try (Transaction nested = Transaction.openNested(transaction)) {
            long extracted = storage.extract(variant, floorDroplets, nested);
            //Accept any valid whole-millibucket response; abort (never commit) a fractional or invalid one
            if (extracted > 0 && extracted <= floorDroplets && extracted % FabricFluidConversions.TRANSFER_UNITS_PER_MILLIBUCKET == 0) {
                nested.commit();
                return FabricFluidConversions.toFluidStack(variant, extracted);
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