package mekanism.common.block.basic;

import mekanism.api.security.IBlockSecurityUtils;
import mekanism.common.block.prefab.BlockTile.BlockTileModel;
import mekanism.common.content.blocktype.Machine;
import mekanism.common.resource.BlockResourceInfo;
import mekanism.common.registration.impl.FluidDeferredRegister.MekanismFlowingFluid;
import mekanism.common.tile.TileEntityFluidTank;
import mekanism.common.util.FluidUtils;
import mekanism.common.util.WorldUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import mekanism.api.fluid.FluidStack;
import org.jetbrains.annotations.NotNull;

public class BlockFluidTank extends BlockTileModel<TileEntityFluidTank, Machine<TileEntityFluidTank>> {

    //Vanilla's light engine indexes sources by block state, unlike NeoForge's positional light hook.
    public static final IntegerProperty FLUID_LIGHT = IntegerProperty.create("fluid_light", 0, 15);

    public BlockFluidTank(Machine<TileEntityFluidTank> type) {
        super(type, properties -> properties.mapColor(BlockResourceInfo.STEEL.getMapColor()).lightLevel(state -> state.getValue(FLUID_LIGHT)));
    }

    @Override
    protected void createBlockStateDefinition(@NotNull StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FLUID_LIGHT);
    }

    /** The official tank uses the stored fluid's luminosity, not the amount stored. */
    public static int fluidLight(FluidStack fluid) {
        if (fluid.isEmpty()) {
            return 0;
        }
        if (fluid.getFluid() instanceof MekanismFlowingFluid mekanismFluid) {
            return Math.clamp(mekanismFluid.getMekanismFluidType().getLightLevel(fluid), 0, 15);
        }
        return Math.clamp(fluid.getFluid().defaultFluidState().createLegacyBlock().getLightEmission(), 0, 15);
    }

    @NotNull
    @Override
    protected ItemInteractionResult useItemOn(@NotNull ItemStack stack, @NotNull BlockState state, @NotNull Level world, @NotNull BlockPos pos, @NotNull Player player,
          @NotNull InteractionHand hand, @NotNull BlockHitResult hit) {
        if (stack.isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        TileEntityFluidTank tile = WorldUtils.getTileEntity(TileEntityFluidTank.class, world, pos, true);
        if (tile == null) {
            //No tile, we can just skip trying to use without an item
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        } else if (world.isClientSide) {
            return genericClientActivated(stack, tile);
        }
        ItemInteractionResult wrenchResult = tile.tryWrench(state, player, stack).getInteractionResult();
        if (wrenchResult.result() != InteractionResult.PASS) {
            return wrenchResult;
        }
        //Handle filling fluid tank
        if (!player.isShiftKeyDown()) {
            if (!IBlockSecurityUtils.INSTANCE.canAccessOrDisplayError(player, world, pos, tile)) {
                return ItemInteractionResult.FAIL;
            } else if (FluidUtils.handleTankInteraction(player, hand, stack, tile.fluidTank)) {
                player.getInventory().setChanged();
                return ItemInteractionResult.SUCCESS;
            }
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
}