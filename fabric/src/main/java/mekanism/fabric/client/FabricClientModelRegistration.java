package mekanism.fabric.client;

import java.util.HashMap;
import mekanism.api.text.EnumColor;
import mekanism.common.attachments.FormulaAttachment;
import mekanism.common.block.attribute.Attribute;
import mekanism.common.item.ItemConfigurationCard;
import mekanism.common.item.ItemConfigurator;
import mekanism.common.item.ItemConfigurator.ConfiguratorMode;
import mekanism.common.item.block.machine.ItemBlockFluidTank;
import mekanism.common.lib.radiation.ClientRadiation;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.registries.MekanismItems;
import mekanism.common.registries.MekanismDataComponents;
import mekanism.common.resource.PrimaryResource;
import mekanism.common.tile.qio.TileEntityQIOComponent;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.renderer.item.ItemPropertyFunction;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

/** Native registration of the official item predicates and remaining block/item colors. */
public final class FabricClientModelRegistration {
    private FabricClientModelRegistration() {
    }

    public static void register() {
        registerProperty(MekanismBlocks.CARDBOARD_BOX.getItemHolder(), id("storage"),
              (stack, world, entity, seed) -> stack.has(MekanismDataComponents.BLOCK_DATA.get()) ? 1 : 0);

        registerProperty(MekanismItems.CRAFTING_FORMULA, id("invalid"), (stack, world, entity, seed) -> {
            FormulaAttachment attachment = stack.getOrDefault(MekanismDataComponents.FORMULA_HOLDER.get(), FormulaAttachment.EMPTY);
            return attachment.hasItems() && attachment.invalid() ? 1 : 0;
        });
        registerProperty(MekanismItems.CRAFTING_FORMULA, id("encoded"), (stack, world, entity, seed) -> {
            FormulaAttachment attachment = stack.getOrDefault(MekanismDataComponents.FORMULA_HOLDER.get(), FormulaAttachment.EMPTY);
            return attachment.hasItems() && !attachment.invalid() ? 1 : 0;
        });
        registerProperty(MekanismItems.CONFIGURATION_CARD, id("encoded"),
              (stack, world, entity, seed) -> ((ItemConfigurationCard) stack.getItem()).hasData(stack) ? 1 : 0);
        registerProperty(MekanismItems.CONFIGURATOR, id("mode"), (stack, world, entity, seed) -> {
            ConfiguratorMode mode = ((ItemConfigurator) stack.getItem()).getMode(stack);
            return switch (mode) {
                default -> 0;
                case EMPTY -> 1;
                case ROTATE -> 2;
                case WRENCH -> 3;
            };
        });

        registerProperty(MekanismItems.ELECTRIC_BOW, id("pull"),
              (stack, world, entity, seed) -> entity != null && entity.getUseItem() == stack ? (stack.getUseDuration(entity) - entity.getUseItemRemainingTicks()) / (float) SharedConstants.TICKS_PER_SECOND : 0);
        registerProperty(MekanismItems.ELECTRIC_BOW, id("pulling"),
              (stack, world, entity, seed) -> entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1.0F : 0.0F);

        registerProperty(MekanismItems.GEIGER_COUNTER, id("radiation"), (stack, world, entity, seed) -> {
            if (entity instanceof Player) {
                return ClientRadiation.getClientScale().ordinal();
            }
            return 0;
        });
        //The default NeoForge ElytraItem flight predicate is vanilla's durability check.
        registerProperty(MekanismItems.HDPE_REINFORCED_ELYTRA, id("broken"),
              (stack, world, entity, seed) -> net.minecraft.world.item.ElytraItem.isFlyEnabled(stack) ? 0 : 1);

        ColorProviderRegistry.BLOCK.register((state, world, pos, tint) -> {
            var tier = Attribute.getBaseTier(state.getBlockHolder());
            return tint == 1 && tier != null ? tier.getPackedColor() : -1;
        }, MekanismBlocks.BASIC_FLUID_TANK.get(), MekanismBlocks.ADVANCED_FLUID_TANK.get(), MekanismBlocks.ELITE_FLUID_TANK.get(),
              MekanismBlocks.ULTIMATE_FLUID_TANK.get(), MekanismBlocks.CREATIVE_FLUID_TANK.get());
        ColorProviderRegistry.ITEM.register((stack, tint) -> tint == 1 && stack.getItem() instanceof ItemBlockFluidTank tank
              ? tank.getTier().getBaseTier().getPackedColor() : -1,
              MekanismBlocks.BASIC_FLUID_TANK.asItem(), MekanismBlocks.ADVANCED_FLUID_TANK.asItem(), MekanismBlocks.ELITE_FLUID_TANK.asItem(),
              MekanismBlocks.ULTIMATE_FLUID_TANK.asItem(), MekanismBlocks.CREATIVE_FLUID_TANK.asItem());
        ColorProviderRegistry.BLOCK.register((state, world, pos, tint) -> {
            if (world != null && pos != null && world.getBlockEntity(pos) instanceof TileEntityQIOComponent tile) {
                var color = tile.getColor();
                return color == null ? -1 : color.getPackedColor();
            }
            return -1;
        }, MekanismBlocks.QIO_DRIVE_ARRAY.get(), MekanismBlocks.QIO_DASHBOARD.get(), MekanismBlocks.QIO_IMPORTER.get(),
              MekanismBlocks.QIO_EXPORTER.get(), MekanismBlocks.QIO_REDSTONE_ADAPTER.get());
        ColorProviderRegistry.ITEM.register((stack, tint) -> {
            if (tint != 1) {
                return -1;
            }
            EnumColor color = stack.get(MekanismDataComponents.COLOR.get());
            return color == null ? 0xFF555555 : color.getPackedColor();
        },
              MekanismItems.PORTABLE_QIO_DASHBOARD.asItem(), MekanismBlocks.QIO_DRIVE_ARRAY.asItem(), MekanismBlocks.QIO_DASHBOARD.asItem(),
              MekanismBlocks.QIO_IMPORTER.asItem(), MekanismBlocks.QIO_EXPORTER.asItem(), MekanismBlocks.QIO_REDSTONE_ADAPTER.asItem());
        for (var cell : MekanismItems.PROCESSED_RESOURCES.cellSet()) {
            int color = cell.getColumnKey().getTint();
            ColorProviderRegistry.ITEM.register((stack, tint) -> tint == 1 ? color : -1, cell.getValue().asItem());
        }
        for (var entry : MekanismBlocks.PROCESSED_RESOURCE_BLOCKS.entrySet()) {
            if (entry.getKey() instanceof PrimaryResource resource) {
                int color = resource.getTint();
                ColorProviderRegistry.BLOCK.register((state, world, pos, tint) -> tint == 1 ? color : -1, entry.getValue().get());
                ColorProviderRegistry.ITEM.register((stack, tint) -> tint == 1 ? color : -1, entry.getValue().asItem());
            }
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("mekanism", path);
    }

    private static void registerProperty(Holder<Item> item, ResourceLocation id, ItemPropertyFunction getter) {
        //NeoForge exposes an unbounded overload; vanilla's ClampedItemPropertyFunction would truncate modes/radiation above one.
        ItemProperties.PROPERTIES.computeIfAbsent(item.value(), ignored -> new HashMap<>()).put(id, getter);
    }
}
