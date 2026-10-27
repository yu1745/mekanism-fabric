package mekanism.fabric.recipe;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import mekanism.test.ChemicalTestBootstrap;
import net.fabricmc.fabric.impl.resource.conditions.ResourceConditionsImpl;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagManager;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

class FabricTagEmptyConditionTest {
    @Test
    void distinguishesAbsentEmptyAndPopulatedTagsAndClearsScope() {
        ChemicalTestBootstrap.initialize();
        ResourceLocation empty = ResourceLocation.parse("example:empty");
        ResourceLocation populated = ResourceLocation.parse("example:populated");
        ResourceLocation missing = ResourceLocation.parse("example:missing");
        TagManager.LoadResult<net.minecraft.world.item.Item> itemTags = new TagManager.LoadResult<>(Registries.ITEM,
              Map.of(empty, List.of(), populated, List.of(Holder.direct(Items.STONE))));
        ResourceConditionsImpl.LOADED_TAGS.set(Map.of());
        try {
            FabricTagEmptyCondition.captureTags(List.of(itemTags));
            assertTrue(new FabricTagEmptyCondition(empty).test(null));
            assertTrue(new FabricTagEmptyCondition(missing).test(null));
            assertFalse(new FabricTagEmptyCondition(populated).test(null));
        } finally {
            FabricTagEmptyCondition.clearTags();
            ResourceConditionsImpl.LOADED_TAGS.remove();
        }
        assertThrows(IllegalStateException.class, () -> new FabricTagEmptyCondition(populated).test(null));
    }
}
