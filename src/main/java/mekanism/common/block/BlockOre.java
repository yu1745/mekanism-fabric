package mekanism.common.block;

import mekanism.api.text.ILangEntry;

import mekanism.common.block.interfaces.IHasDescription;
import mekanism.common.block.states.BlockStateHelper;
import mekanism.common.resource.ore.OreType;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BlockOre extends Block implements IHasDescription {

    private final OreType ore;
    private String descriptionTranslationKey;

    public BlockOre(OreType ore) {
        this(ore, BlockStateHelper.applyLightLevelAdjustments(BlockBehaviour.Properties.of().strength(3, 3).requiresCorrectToolForDrops()
              .mapColor(MapColor.STONE).instrument(NoteBlockInstrument.BASEDRUM)));
    }

    public BlockOre(OreType ore, BlockBehaviour.Properties properties) {
        super(properties);
        this.ore = ore;
    }

    @NotNull
    public String getDescriptionTranslationKey() {
        if (descriptionTranslationKey == null) {
            descriptionTranslationKey = Util.makeDescriptionId("description", ResourceLocation.fromNamespaceAndPath("mekanism", ore.getResource().getRegistrySuffix() + "_ore"));
        }
        return descriptionTranslationKey;
    }

    @NotNull
    @Override
    public ILangEntry getDescription() {
        return this::getDescriptionTranslationKey;
    }

    /**
     * Fabric port delta: NeoForge's {@code getExpDrop} extension let vanilla award the ore experience. Vanilla only
     * awards experience from an overridden {@code playerDestroy}, so the same range is dropped here directly (no silk
     * touch or fortune processing, matching the official override's contract).
     */
    @Override
    public void playerDestroy(@NotNull Level level, @NotNull Player player, @NotNull BlockPos pos, @NotNull BlockState state,
          @Nullable BlockEntity blockEntity, @NotNull ItemStack tool) {
        super.playerDestroy(level, player, pos, state, blockEntity, tool);
        if (level instanceof ServerLevel serverLevel) {
            popExperience(serverLevel, pos, Mth.nextInt(level.getRandom(), ore.getMinExp(), ore.getMaxExp()));
        }
    }
}