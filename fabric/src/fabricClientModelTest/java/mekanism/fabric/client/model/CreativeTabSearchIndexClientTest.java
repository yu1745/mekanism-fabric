package mekanism.fabric.client.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import mekanism.fabric.client.CreativeTabSearchIndex;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

class CreativeTabSearchIndexClientTest {
    @Test
    void searchesOnlyDisplayedVariantsByNameIdAndTag() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var registry = BuiltInRegistries.ITEM;
        var previous = registry.getTags().collect(Collectors.toMap(pair -> pair.getFirst(), pair -> pair.getSecond().stream().toList()));
        var tag = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("fixture", "ores/tin"));
        try {
            registry.bindTags(Map.of(tag, List.of(Items.IRON_INGOT.builtInRegistryHolder())));
            ItemStack alpha = new ItemStack(Items.IRON_INGOT);
            alpha.set(DataComponents.CUSTOM_NAME, Component.literal("Alpha Sample"));
            ItemStack beta = new ItemStack(Items.IRON_INGOT);
            beta.set(DataComponents.CUSTOM_NAME, Component.literal("Beta Sample"));
            ItemStack absent = new ItemStack(Items.DIAMOND);
            absent.set(DataComponents.CUSTOM_NAME, Component.literal("Alpha Not In Tab"));
            var enchantments = new net.minecraft.core.MappedRegistry<>(Registries.ENCHANTMENT, com.mojang.serialization.Lifecycle.stable());
            var registries = new RegistryAccess.ImmutableRegistryAccess(List.of(enchantments));
            var index = new CreativeTabSearchIndex(List.of(alpha, beta), registries);
            assertEquals(1, index.search("ALPHA", false).size());
            assertSame(alpha, index.search("alpha", false).getFirst());
            assertEquals(2, index.search("minecraft:iron_ingot", false).size());
            assertEquals(2, index.search("fixture:ores/tin", true).size());
            assertEquals(0, index.search("diamond", false).size());
        } finally {
            registry.bindTags(previous);
        }
    }
}
