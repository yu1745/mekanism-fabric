package mekanism.common.tile.transmitter;

import java.util.Collections;
import java.util.List;
import mekanism.api.heat.IHeatCapacitor;
import mekanism.api.heat.IMekanismHeatHandler;
import mekanism.api.tier.BaseTier;
import mekanism.common.block.states.BlockStateHelper;
import mekanism.common.block.states.TransmitterType;
import mekanism.common.capabilities.resolver.manager.HeatHandlerManager;
import mekanism.common.content.network.transmitter.ThermodynamicConductor;
import mekanism.common.lib.transmitter.ConnectionType;
import mekanism.common.registries.MekanismBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TileEntityThermodynamicConductor extends TileEntityTransmitter {

    private final HeatHandlerManager heatHandlerManager;

    public TileEntityThermodynamicConductor(Holder<Block> blockProvider, BlockPos pos, BlockState state) {
        super(blockProvider, pos, state);
        addCapabilityResolver(heatHandlerManager = new HeatHandlerManager(direction -> {
            ThermodynamicConductor conductor = getTransmitter();
            if (direction != null && (conductor.getConnectionTypeRaw(direction) == ConnectionType.NONE) || conductor.isRedstoneActivated()) {
                //If we actually have a side, and our connection type on that side is none, or we are currently activated by redstone,
                // then return that we have no capacitors
                return Collections.emptyList();
            }
            return conductor.getHeatCapacitors(direction);
        }, new IMekanismHeatHandler() {
            @NotNull
            @Override
            public List<IHeatCapacitor> getHeatCapacitors(@Nullable Direction side) {
                return heatHandlerManager.getContainers(side);
            }

            @Override
            public void onContentsChanged() {
            }
        }));
    }

    @Override
    protected ThermodynamicConductor createTransmitter(Holder<Block> blockProvider) {
        return new ThermodynamicConductor(blockProvider, this);
    }

    @Override
    public ThermodynamicConductor getTransmitter() {
        return (ThermodynamicConductor) super.getTransmitter();
    }

    @Override
    public TransmitterType getTransmitterType() {
        return TransmitterType.THERMODYNAMIC_CONDUCTOR;
    }

    @NotNull
    @Override
    protected BlockState upgradeResult(@NotNull BlockState current, @NotNull BaseTier tier) {
        return BlockStateHelper.copyStateData(current, switch (tier) {
            case BASIC -> MekanismBlocks.BASIC_THERMODYNAMIC_CONDUCTOR;
            case ADVANCED -> MekanismBlocks.ADVANCED_THERMODYNAMIC_CONDUCTOR;
            case ELITE -> MekanismBlocks.ELITE_THERMODYNAMIC_CONDUCTOR;
            case ULTIMATE -> MekanismBlocks.ULTIMATE_THERMODYNAMIC_CONDUCTOR;
            default -> null;
        });
    }

    @Override
    public void sideChanged(@NotNull Direction side, @NotNull ConnectionType old, @NotNull ConnectionType type) {
        super.sideChanged(side, old, type);
        if (type == ConnectionType.NONE || old == ConnectionType.NONE) {
            //Native callers re-query; revoke old domain proxies rather than sending a platform invalidation notification.
            invalidateCapability(HeatHandlerManager.HEAT_HANDLER, side);
        }
    }

    @Override
    public void redstoneChanged(boolean powered) {
        super.redstoneChanged(powered);
        //Re-enabling exposes fresh providers, never revives a previously revoked proxy.
        invalidateCapabilityAll(HeatHandlerManager.HEAT_HANDLER);
    }
}