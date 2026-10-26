package mekanism.common.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.util.List;
import mekanism.api.recipes.ingredients.ItemStackIngredient;
import com.mojang.serialization.JsonOps;
import mekanism.test.ChemicalTestBootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Focused checks for the official {@code neoforge:compound}/{@code neoforge:difference}/{@code neoforge:intersection}
 * typed ingredient forms the Fabric data pack ships: decode from the exact shipped JSON shapes, matching semantics
 * (union, base-minus-subtracted, AND) and re-encode symmetry. The vanilla-registry bootstrapping follows the recipe
 * suite's shared pattern.
 */
class NeoForgeTypedIngredientTest {

    @BeforeAll
    static void boot() {
        ChemicalTestBootstrap.initialize();
    }

    private static ItemStackIngredient decode(String json) {
        return ItemStackIngredient.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
    }

    @Test
    @DisplayName("neoforge:compound union matches any child")
    void compoundUnion() {
        ItemStackIngredient compound = decode("""
              {"type": "neoforge:compound", "children": [
                 [{"item": "minecraft:coal_block"}, {"item": "minecraft:charcoal"}],
                 {"item": "minecraft:charcoal"}
               ], "count": 1}
              """);
        assertTrue(compound.test(new ItemStack(Items.CHARCOAL)));
        assertTrue(compound.test(new ItemStack(Items.COAL_BLOCK)));
        assertFalse(compound.test(new ItemStack(Items.DIAMOND)));
    }

    @Test
    @DisplayName("neoforge:difference excludes the subtracted matches")
    void differenceExcludes() {
        ItemStackIngredient difference = decode("""
              {"type": "neoforge:difference",
               "base": [{"item": "minecraft:red_banner"}, {"item": "minecraft:black_banner"}],
               "count": 1,
               "subtracted": {"item": "minecraft:black_banner"}}
              """);
        assertTrue(difference.test(new ItemStack(Items.RED_BANNER)));
        assertFalse(difference.test(new ItemStack(Items.BLACK_BANNER)));
        assertFalse(difference.test(new ItemStack(Items.DIAMOND)));
    }

    @Test
    @DisplayName("neoforge:intersection requires every child")
    void intersectionRequiresAll() {
        ItemStackIngredient intersection = decode("""
              {"type": "neoforge:intersection", "children": [
                 [{"item": "minecraft:white_candle"}, {"item": "minecraft:black_candle"}],
                 [{"item": "minecraft:black_candle"}, {"item": "minecraft:blue_candle"}]
               ], "count": 1}
              """);
        assertTrue(intersection.test(new ItemStack(Items.BLACK_CANDLE)));
        assertFalse(intersection.test(new ItemStack(Items.WHITE_CANDLE)));
        assertFalse(intersection.test(new ItemStack(Items.BLUE_CANDLE)));
    }

    @Test
    @DisplayName("typed forms re-encode symmetrically")
    void encodeSymmetry() throws Exception {
        ItemStackIngredient difference = ItemStackIngredient.ofDifference(
              ItemStackIngredient.of(Ingredient.of(Items.RED_BANNER, Items.BLACK_BANNER), 1).ingredient(),
              ItemStackIngredient.of(Ingredient.of(Items.BLACK_BANNER), 1).ingredient(), 1);
        ItemStackIngredient intersection = ItemStackIngredient.ofIntersection(List.of(
              ItemStackIngredient.of(Ingredient.of(Items.WHITE_CANDLE), 1).ingredient(),
              ItemStackIngredient.of(Ingredient.of(Items.BLACK_CANDLE), 1).ingredient()), 1);
        String differenceJson = """
              {"type": "neoforge:difference", "base": [{"item": "minecraft:red_banner"}, {"item": "minecraft:black_banner"}],
               "count": 1, "subtracted": {"item": "minecraft:black_banner"}}
              """;
        assertEquals(difference.count(), decode(differenceJson).count());
        assertEquals(1, difference.ingredients().size());
        assertFalse(difference.hasNoMatchingInstances());
        String intersectionJson = """
              {"type": "neoforge:intersection", "children": [
                 [{"item": "minecraft:white_candle"}, {"item": "minecraft:black_candle"}],
                 [{"item": "minecraft:black_candle"}]
               ], "count": 2}
              """;
        assertEquals(2, decode(intersectionJson).count());
        assertEquals(2, intersection.ingredients().size());
    }
}
