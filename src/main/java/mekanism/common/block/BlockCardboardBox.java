package mekanism.common.block;

import java.util.Optional;
import mekanism.common.advancements.MekanismCriteriaTriggers;
import mekanism.common.attachments.BlockData;
import mekanism.common.block.interfaces.IHasTileEntity;
import mekanism.common.block.states.BlockStateHelper;
import mekanism.common.block.states.IStateStorage;
import mekanism.common.registration.impl.TileEntityTypeRegistryObject;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.registries.MekanismDataComponents;
import mekanism.common.registries.MekanismTileEntityTypes;
import mekanism.common.tile.TileEntityCardboardBox;
import mekanism.common.util.WorldUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BlockCardboardBox extends BlockMekanism implements IStateStorage, IHasTileEntity<TileEntityCardboardBox> {

    public BlockCardboardBox() {
        super(BlockBehaviour.Properties.of().strength(0.5F, 0.6F).mapColor(MapColor.WOOD));
    }

    @NotNull
    @Override
    protected InteractionResult useWithoutItem(@NotNull BlockState state, @NotNull Level world, @NotNull BlockPos pos, @NotNull Player player, @NotNull BlockHitResult hit) {
        if (!player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        } else if (!canReplace(world, player, pos, state)) {
            return InteractionResult.FAIL;
        }
        if (!world.isClientSide) {
            Optional<BlockData> blockData = Optional.ofNullable(WorldUtils.getTileEntity(TileEntityCardboardBox.class, world, pos))
                  .map(box -> box.components().get(MekanismDataComponents.BLOCK_DATA.value()));
            if (blockData.isPresent()) {
                if (!blockData.get().tryPlaceIntoWorld(world, pos, player)) {
                    //Can't place it into the world, skip
                    return InteractionResult.PASS;
                }
                popResource(world, pos, new ItemStack(MekanismBlocks.CARDBOARD_BOX));
                MekanismCriteriaTriggers.UNBOX_CARDBOARD_BOX.value().trigger((ServerPlayer) player);
            }
        }
        return InteractionResult.sidedSuccess(world.isClientSide);
    }

    private static boolean canReplace(Level world, Player player, BlockPos pos, BlockState state) {
        return world.mayInteract(player, pos) && WorldUtils.canBreakBlock(world, player, pos, state);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(@NotNull BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        if (state != null && context.getItemInHand().has(MekanismDataComponents.BLOCK_DATA.get())) {
            return state.setValue(BlockStateHelper.storageProperty, true);
        }
        return state;
    }

    @Override
    public TileEntityTypeRegistryObject<TileEntityCardboardBox> getTileType() {
        return MekanismTileEntityTypes.CARDBOARD_BOX;
    }
}