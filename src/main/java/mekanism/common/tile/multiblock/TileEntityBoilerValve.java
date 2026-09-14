package mekanism.common.tile.multiblock;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import mekanism.api.Action;
import mekanism.api.IContentsListener;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.api.text.EnumColor;
import mekanism.common.MekanismLang;
import mekanism.common.attachments.containers.ContainerType;
import mekanism.common.block.attribute.AttributeStateBoilerValveMode;
import mekanism.common.block.attribute.AttributeStateBoilerValveMode.BoilerValveMode;
import mekanism.common.capabilities.resolver.manager.ChemicalHandlerManager;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.lookup.v1.block.BlockApiCache;
import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import mekanism.common.capabilities.holder.chemical.IChemicalTankHolder;
import mekanism.common.capabilities.holder.fluid.IFluidTankHolder;
import mekanism.common.integration.computer.annotation.ComputerMethod;
import mekanism.common.lib.multiblock.MultiblockData.AdvancedCapabilityOutputTarget;
import mekanism.common.registries.MekanismBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import mekanism.api.fluid.FluidStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TileEntityBoilerValve extends TileEntityBoilerCasing {

    private final Map<Direction, Supplier<@Nullable IChemicalHandler>> capabilityCaches = new EnumMap<>(Direction.class);
    private final List<BlockApiLookup<?, @Nullable Direction>> portCapabilities = List.of(
          ChemicalHandlerManager.CHEMICAL_HANDLER,
          FluidStorage.SIDED
    );
    private final Predicate<BoilerValveMode> MODE_MATCHES = mode -> mode == getMode();

    public TileEntityBoilerValve(BlockPos pos, BlockState state) {
        super(MekanismBlocks.BOILER_VALVE, pos, state);
        delaySupplier = NO_DELAY;
    }

    @NotNull
    @Override
    public IChemicalTankHolder getInitialChemicalTanks(IContentsListener listener) {
        return side -> getMultiblock().getChemicalTanks(getMode());
    }

    @NotNull
    @Override
    protected IFluidTankHolder getInitialFluidTanks(IContentsListener listener) {
        return side -> getMode() == BoilerValveMode.INPUT ? getMultiblock().getFluidTanks(side) : Collections.emptyList();
    }

    @Override
    public boolean persists(ContainerType<?, ?, ?> type) {
        //Do not handle fluid or gas when it comes to syncing it/saving this tile to disk
        if (type == ContainerType.FLUID || type == ContainerType.CHEMICAL) {
            return false;
        }
        return super.persists(type);
    }

    public void addChemicalTargetCapability(List<AdvancedCapabilityOutputTarget<IChemicalHandler, BoilerValveMode>> outputTargets, Direction side) {
        Supplier<@Nullable IChemicalHandler> cache = capabilityCaches.computeIfAbsent(side, direction -> {
            var nativeCache = BlockApiCache.create(ChemicalHandlerManager.CHEMICAL_HANDLER, (ServerLevel) level, worldPosition.relative(direction));
            return () -> nativeCache.find(direction.getOpposite());
        });
        outputTargets.add(new AdvancedCapabilityOutputTarget<>(cache, MODE_MATCHES));
    }

    @Override
    public int getRedstoneLevel() {
        return getMultiblock().getCurrentRedstoneLevel();
    }

    @ComputerMethod(methodDescription = "Get the current configuration of this valve")
    BoilerValveMode getMode() {
        return getBlockState().getValue(AttributeStateBoilerValveMode.modeProperty);
    }

    @ComputerMethod(methodDescription = "Change the configuration of this valve")
    void setMode(BoilerValveMode mode) {
        if (mode != getMode()) {
            level.setBlockAndUpdate(worldPosition, getBlockState().setValue(AttributeStateBoilerValveMode.modeProperty, mode));
            invalidateCapabilitiesAll(portCapabilities);
        }
    }

    @Override
    public InteractionResult onSneakRightClick(Player player) {
        if (!isRemote()) {
            BoilerValveMode mode = getMode().getNext();
            setMode(mode);
            player.displayClientMessage(MekanismLang.BOILER_VALVE_MODE_CHANGE.translateColored(EnumColor.GRAY, mode), true);
        }
        return InteractionResult.SUCCESS;
    }

    @NotNull
    @Override
    public FluidStack insertFluid(int tank, @NotNull FluidStack stack, Direction side, @NotNull Action action) {
        return handleValves(stack, action, super.insertFluid(tank, stack, side, action));
    }

    @NotNull
    @Override
    public FluidStack insertFluid(@NotNull FluidStack stack, Direction side, @NotNull Action action) {
        return handleValves(stack, action, super.insertFluid(stack, side, action));
    }

    private FluidStack handleValves(@NotNull FluidStack stack, @NotNull Action action, @NotNull FluidStack remainder) {
        if (action.execute() && remainder.getAmount() < stack.getAmount()) {
            getMultiblock().triggerValveTransfer(this);
        }
        return remainder;
    }

    //Methods relating to IComputerTile
    @ComputerMethod(methodDescription = "Toggle the current valve configuration to the next option in the list")
    void incrementMode() {
        setMode(getMode().getNext());
    }

    @ComputerMethod(methodDescription = "Toggle the current valve configuration to the previous option in the list")
    void decrementMode() {
        setMode(getMode().getPrevious());
    }
    //End methods IComputerTile
}
