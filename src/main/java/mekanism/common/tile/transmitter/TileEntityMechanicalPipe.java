package mekanism.common.tile.transmitter;

import java.util.Collections;
import java.util.List;
import mekanism.api.SerializationConstants;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.tier.BaseTier;
import mekanism.common.block.states.BlockStateHelper;
import mekanism.common.block.states.TransmitterType;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import mekanism.common.capabilities.fluid.DynamicFluidHandler;
import mekanism.common.capabilities.resolver.manager.FluidHandlerManager;
import mekanism.common.content.network.FluidNetwork;
import mekanism.common.content.network.transmitter.MechanicalPipe;
import mekanism.common.integration.computer.IComputerTile;
import mekanism.common.integration.computer.annotation.ComputerMethod;
import mekanism.common.lib.transmitter.ConnectionType;
import mekanism.common.registries.MekanismBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import mekanism.api.fluid.FluidStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TileEntityMechanicalPipe extends TileEntityTransmitter implements IComputerTile {

    private final FluidHandlerManager fluidHandlerManager;

    public TileEntityMechanicalPipe(Holder<Block> blockProvider, BlockPos pos, BlockState state) {
        super(blockProvider, pos, state);
        addCapabilityResolver(fluidHandlerManager = new FluidHandlerManager(direction -> {
            MechanicalPipe pipe = getTransmitter();
            if (direction != null && (pipe.getConnectionTypeRaw(direction) == ConnectionType.NONE) || pipe.isRedstoneActivated()) {
                //If we actually have a side, and our connection type on that side is none, or we are currently activated by redstone,
                // then return that we have no tanks
                return Collections.emptyList();
            }
            return pipe.getFluidTanks(direction);
        }, new DynamicFluidHandler(this::getFluidTanks, getExtractPredicate(), getInsertPredicate(), null)));
    }

    @Override
    protected MechanicalPipe createTransmitter(Holder<Block> blockProvider) {
        return new MechanicalPipe(blockProvider, this);
    }

    @Override
    public MechanicalPipe getTransmitter() {
        return (MechanicalPipe) super.getTransmitter();
    }

    @Override
    protected void onUpdateServer() {
        getTransmitter().pullFromAcceptors();
        super.onUpdateServer();
    }

    @Override
    public TransmitterType getTransmitterType() {
        return TransmitterType.MECHANICAL_PIPE;
    }

    @NotNull
    @Override
    protected BlockState upgradeResult(@NotNull BlockState current, @NotNull BaseTier tier) {
        return BlockStateHelper.copyStateData(current, switch (tier) {
            case BASIC -> MekanismBlocks.BASIC_MECHANICAL_PIPE;
            case ADVANCED -> MekanismBlocks.ADVANCED_MECHANICAL_PIPE;
            case ELITE -> MekanismBlocks.ELITE_MECHANICAL_PIPE;
            case ULTIMATE -> MekanismBlocks.ULTIMATE_MECHANICAL_PIPE;
            default -> null;
        });
    }

    @NotNull
    @Override
    public CompoundTag getUpdateTag(@NotNull HolderLookup.Provider provider) {
        //Note: We add the stored information to the initial update tag and not to the one we sync on side changes which uses getReducedUpdateTag
        CompoundTag updateTag = super.getUpdateTag(provider);
        if (getTransmitter().hasTransmitterNetwork()) {
            FluidNetwork network = getTransmitter().getTransmitterNetwork();
            updateTag.put(SerializationConstants.FLUID, network.lastFluid.saveOptional(provider));
            updateTag.putFloat(SerializationConstants.SCALE, network.currentScale);
        }
        return updateTag;
    }

    private List<IExtendedFluidTank> getFluidTanks(@Nullable Direction side) {
        return fluidHandlerManager.getContainers(side);
    }

    @Override
    public void sideChanged(@NotNull Direction side, @NotNull ConnectionType old, @NotNull ConnectionType type) {
        super.sideChanged(side, old, type);
        if (type == ConnectionType.NONE || old == ConnectionType.NONE) {
            //Revoke the old view on disable/re-enable; native callers re-query providers rather than receive notifications.
            invalidateCapability(FluidStorage.SIDED, side);
        }
    }

    @Override
    public void redstoneChanged(boolean powered) {
        super.redstoneChanged(powered);
        //Native providers are resolved afresh after either transition; old transaction views stay revoked.
        invalidateCapabilityAll(FluidStorage.SIDED);
    }

    //Methods relating to IComputerTile
    @Override
    public String getComputerName() {
        return getTransmitter().getTier().getBaseTier().getLowerName() + "MechanicalPipe";
    }

    @ComputerMethod
    FluidStack getBuffer() {
        return getTransmitter().getBufferWithFallback();
    }

    @ComputerMethod
    long getCapacity() {
        MechanicalPipe pipe = getTransmitter();
        return pipe.hasTransmitterNetwork() ? pipe.getTransmitterNetwork().getCapacity() : pipe.getCapacity();
    }

    @ComputerMethod
    long getNeeded() {
        return getCapacity() - getBuffer().getAmount();
    }

    @ComputerMethod
    double getFilledPercentage() {
        return getBuffer().getAmount() / (double) getCapacity();
    }
    //End methods IComputerTile
}