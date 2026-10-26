package mekanism.common.util;

import mekanism.api.ItemAbility;
import mekanism.api.MekanismItemAbilities;
import mekanism.common.item.ItemAbilities;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CandleCakeBlock;
import net.minecraft.world.level.block.GrowingPlantHeadBlock;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

/**
 * Fabric port delta: replaces the NeoForge-patched {@code BlockState#getToolModifiedState(UseOnContext, ItemAbility, boolean)} dispatch together with the
 * default semantics of {@code IBlockExtension.getToolModifiedState}, replicated exactly over vanilla data — {@code AxeItem.STRIPPABLES},
 * {@code ShovelItem.FLATTENABLES} (both access-widened), {@link WeatheringCopper#getPrevious} and the {@code HoneycombItem.WAX_OFF_BY_BLOCK} bimap — with
 * the tool gate answered through {@link MekanismItemAbilities#canPerformAction}. The {@code BlockToolModificationEvent} override hook has no Fabric
 * equivalent and is not fired, and NeoForge's data-map extensions of the strip/unwax maps are outside vanilla parity (vanilla-map behavior only).
 */
public final class ToolAbilityUtils {

    private ToolAbilityUtils() {
    }

    @Nullable
    public static BlockState getToolModifiedState(BlockState state, UseOnContext context, ItemAbility itemAbility, boolean simulate) {
        ItemStack itemStack = context.getItemInHand();
        if (!MekanismItemAbilities.canPerformAction(itemStack, itemAbility)) {
            return null;
        }
        if (ItemAbilities.AXE_STRIP == itemAbility) {
            return getAxeStrippingState(state);
        } else if (ItemAbilities.AXE_SCRAPE == itemAbility) {
            return WeatheringCopper.getPrevious(state).orElse(null);
        } else if (ItemAbilities.AXE_WAX_OFF == itemAbility) {
            Block waxOffBlock = HoneycombItem.WAX_OFF_BY_BLOCK.get().get(state.getBlock());
            return waxOffBlock == null ? null : waxOffBlock.withPropertiesOf(state);
        } else if (ItemAbilities.SHOVEL_FLATTEN == itemAbility) {
            return ShovelItem.FLATTENABLES.get(state.getBlock());
        } else if (ItemAbilities.HOE_TILL == itemAbility) {
            // Logic copied from the upstream default (itself copied from HoeItem#TILLABLES); kept in sync during updating
            Block block = state.getBlock();
            if (block == Blocks.ROOTED_DIRT) {
                if (!simulate && !context.getLevel().isClientSide()) {
                    Block.popResourceFromFace(context.getLevel(), context.getClickedPos(), context.getClickedFace(), new ItemStack(Items.HANGING_ROOTS));
                }
                return Blocks.DIRT.defaultBlockState();
            } else if ((block == Blocks.GRASS_BLOCK || block == Blocks.DIRT_PATH || block == Blocks.DIRT || block == Blocks.COARSE_DIRT)
                  && context.getLevel().getBlockState(context.getClickedPos().above()).isAir()) {
                return block == Blocks.COARSE_DIRT ? Blocks.DIRT.defaultBlockState() : Blocks.FARMLAND.defaultBlockState();
            }
        } else if (ItemAbilities.SHEARS_TRIM == itemAbility) {
            if (state.getBlock() instanceof GrowingPlantHeadBlock growingPlant && !growingPlant.isMaxAge(state)) {
                if (!simulate) {
                    context.getLevel().playSound(context.getPlayer(), context.getClickedPos(), SoundEvents.GROWING_PLANT_CROP, SoundSource.BLOCKS, 1.0F, 1.0F);
                }
                return growingPlant.getMaxAgeState(state);
            }
        } else if (ItemAbilities.SHOVEL_DOUSE == itemAbility) {
            if (state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT)) {
                if (!simulate) {
                    CampfireBlock.dowse(context.getPlayer(), context.getLevel(), context.getClickedPos(), state);
                }
                return state.setValue(CampfireBlock.LIT, Boolean.valueOf(false));
            }
        } else if (ItemAbilities.FIRESTARTER_LIGHT == itemAbility) {
            if (CampfireBlock.canLight(state) || CandleBlock.canLight(state) || CandleCakeBlock.canLight(state)) {
                return state.setValue(BlockStateProperties.LIT, Boolean.valueOf(true));
            }
        }
        return null;
    }

    @Nullable
    private static BlockState getAxeStrippingState(BlockState state) {
        Block stripped = AxeItem.STRIPPABLES.get(state.getBlock());
        return stripped == null ? null
              : stripped.defaultBlockState().setValue(RotatedPillarBlock.AXIS, state.getValue(RotatedPillarBlock.AXIS));
    }
}
