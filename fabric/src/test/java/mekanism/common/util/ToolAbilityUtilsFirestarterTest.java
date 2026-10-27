package mekanism.common.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import mekanism.common.item.ItemAbilities;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ToolAbilityUtilsFirestarterTest {

    private static Map<TagKey<Block>, List<Holder<Block>>> previousTags;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        // Unit bootstrap has no datapack tags; install the vanilla tag membership used by canLight.
        previousTags = BuiltInRegistries.BLOCK.getTags().collect(Collectors.toMap(pair -> pair.getFirst(), pair -> pair.getSecond().stream().toList()));
        BuiltInRegistries.BLOCK.bindTags(Map.of(BlockTags.CAMPFIRES, List.of(Blocks.CAMPFIRE.builtInRegistryHolder()),
              BlockTags.CANDLES, List.of(Blocks.CANDLE.builtInRegistryHolder())));
    }

    @AfterAll
    static void restoreTags() {
        BuiltInRegistries.BLOCK.bindTags(previousTags);
    }

    // The light-state calculation reads no level or player; null here is not a simulated world.
    private static UseOnContext context(ItemStack stack) {
        return new UseOnContext(null, null, InteractionHand.MAIN_HAND, stack,
              new BlockHitResult(Vec3.ZERO, Direction.UP, BlockPos.ZERO, false));
    }

    @Test
    void fireChargeLightsAnUnlitCampfire() {
        BlockState unlit = Blocks.CAMPFIRE.defaultBlockState().setValue(BlockStateProperties.LIT, false);
        BlockState result = ToolAbilityUtils.getToolModifiedState(unlit, context(new ItemStack(Items.FIRE_CHARGE)),
              ItemAbilities.FIRESTARTER_LIGHT, false);
        assertTrue(result.getValue(BlockStateProperties.LIT));
        assertFalse(unlit.getValue(BlockStateProperties.LIT));
    }

    @Test
    void flintAndSteelLightsAnUnlitCandleButNotAnAlreadyLitOne() {
        BlockState unlit = Blocks.CANDLE.defaultBlockState().setValue(BlockStateProperties.LIT, false);
        UseOnContext context = context(new ItemStack(Items.FLINT_AND_STEEL));
        BlockState lit = ToolAbilityUtils.getToolModifiedState(unlit, context, ItemAbilities.FIRESTARTER_LIGHT, true);
        assertTrue(lit.getValue(BlockStateProperties.LIT));
        assertNull(ToolAbilityUtils.getToolModifiedState(lit, context, ItemAbilities.FIRESTARTER_LIGHT, true));
    }

    @Test
    void unrelatedAndEmptyStacksCannotUseFirestarterAction() {
        BlockState unlit = Blocks.CAMPFIRE.defaultBlockState().setValue(BlockStateProperties.LIT, false);
        assertNull(ToolAbilityUtils.getToolModifiedState(unlit, context(new ItemStack(Items.STICK)), ItemAbilities.FIRESTARTER_LIGHT, false));
        assertNull(ToolAbilityUtils.getToolModifiedState(unlit, context(ItemStack.EMPTY), ItemAbilities.FIRESTARTER_LIGHT, false));
        assertNull(ToolAbilityUtils.getToolModifiedState(unlit, context(new ItemStack(Items.FIRE_CHARGE)), ItemAbilities.AXE_STRIP, false));
    }
}
