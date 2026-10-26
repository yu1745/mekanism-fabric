package mekanism.common.item.block;

import java.util.List;
import mekanism.api.security.IBlockSecurityUtils;
import mekanism.api.text.EnumColor;
import mekanism.common.CommonWorldTickHandler;
import mekanism.common.MekanismLang;
import mekanism.common.attachments.BlockData;
import mekanism.common.block.BlockCardboardBox;
import mekanism.common.block.states.BlockStateHelper;
import mekanism.common.config.MekanismConfig;
import mekanism.common.item.interfaces.IItemUseFirst;
import mekanism.common.registries.MekanismDataComponents;
import mekanism.common.tags.MekanismTags;
import mekanism.common.tile.TileEntityCardboardBox;
import mekanism.common.util.RegistryUtils;
import mekanism.common.util.WorldUtils;
import mekanism.common.util.text.BooleanStateDisplay.YesNo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

public class ItemBlockCardboardBox extends ItemBlockMekanism<BlockCardboardBox> implements IItemUseFirst {

    public ItemBlockCardboardBox(BlockCardboardBox block, Item.Properties properties) {
        super(block, properties.stacksTo(16));
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull Item.TooltipContext context, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        BlockData existingData = stack.get(MekanismDataComponents.BLOCK_DATA.get());
        tooltip.add(MekanismLang.BLOCK_DATA.translateColored(EnumColor.INDIGO, YesNo.of(existingData != null, true)));
        if (existingData != null) {
            existingData.addToTooltip(tooltip::add);
        }
    }

    private static boolean canReplace(Level world, Player player, BlockPos pos, Direction sideClicked, BlockState state, ItemStack stack) {
        //Check if the player is allowed to use the cardboard box in the given position
        //Fabric port delta: the upstream probe posted a cancellable BreakEvent so protection mods could veto the box; Fabric's
        // protection pipeline runs on real player break events upstream of this path, and no postable equivalent exists.
        //Only the vanilla interaction checks remain (see docs/TASK052_RESUME_2026-10-26.md).
        return world.mayInteract(player, pos) && player.mayUseItemAt(pos.relative(sideClicked), sideClicked, stack);
    }

    @NotNull
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Player player = context.getPlayer();
        if (stack.isEmpty() || player == null) {
            return InteractionResult.PASS;
        }
        Level world = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!stack.has(MekanismDataComponents.BLOCK_DATA.get()) && !player.isShiftKeyDown()) {
            BlockState state = world.getBlockState(pos);
            if (!state.isAir() && state.getDestroySpeed(world, pos) != Block.INDESTRUCTIBLE) {
                if (state.is(MekanismTags.Blocks.CARDBOARD_BLACKLIST)) {
                    return InteractionResult.FAIL;
                }
                ResourceLocation stateName = RegistryUtils.getName(state.getBlockHolder());
                if (stateName == null || MekanismConfig.general.cardboardModBlacklist.get().contains(stateName.getNamespace()) ||
                    !canReplace(world, player, pos, context.getClickedFace(), state, stack)) {
                    return InteractionResult.FAIL;
                }
                BlockEntity tile = WorldUtils.getTileEntity(world, pos);
                if (!IBlockSecurityUtils.INSTANCE.canAccessOrDisplayError(player, world, pos, tile)) {
                    //If the tile is in the tile entity type blacklist or the player cannot access the tile
                    // don't allow them to pick it up with a cardboard box
                    return InteractionResult.FAIL;
                }
                if (!world.isClientSide) {
                    BlockData data = new BlockData(world.registryAccess(), state, tile);
                    if (!player.isCreative()) {
                        stack.shrink(1);
                    }
                    //Mark that we are monitoring item drops that might have been created due to using the cardboard box
                    // and then replace the block with the cardboard box, which will cause items to drop and then get
                    // cancelled by our listener in CommonWorldTickHandler
                    CommonWorldTickHandler.monitoringCardboardBox = true;
                    world.setBlock(pos, getBlock().defaultBlockState().setValue(BlockStateHelper.storageProperty, true), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                    CommonWorldTickHandler.monitoringCardboardBox = false;
                    TileEntityCardboardBox box = WorldUtils.getTileEntity(TileEntityCardboardBox.class, world, pos);
                    if (box != null) {
                        box.setComponents(DataComponentMap.builder()
                              .addAll(box.components())
                              .set(MekanismDataComponents.BLOCK_DATA.get(), data)
                              .build());
                    }
                }
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.PASS;
    }
}