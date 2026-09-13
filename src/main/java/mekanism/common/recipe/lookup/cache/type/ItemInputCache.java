package mekanism.common.recipe.lookup.cache.type;

import java.util.List;
import mekanism.api.recipes.MekanismRecipe;
import mekanism.api.recipes.ingredients.ItemStackIngredient;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * @implNote The Fabric port introspects {@link ItemStackIngredient}'s vanilla ingredients directly: compound union children are mapped as far as possible the same way
 * the platform's compound ingredient was, and non-strict component predicates are marked complex because quick per-item lookup is not valid for them.
 */
public class ItemInputCache<RECIPE extends MekanismRecipe<?>> extends ComponentSensitiveInputCache<Item, ItemStack, ItemStackIngredient, RECIPE> {

    public ItemInputCache() {
        super(ITEM_AND_COMPONENTS);
    }

    /** Item and components hash strategy equivalent to the platform's type-and-tag stack strategy. */
    private static final it.unimi.dsi.fastutil.Hash.Strategy<ItemStack> ITEM_AND_COMPONENTS = new it.unimi.dsi.fastutil.Hash.Strategy<>() {
        @Override
        public int hashCode(ItemStack stack) {
            return stack == null ? 0 : ItemStack.hashItemAndComponents(stack);
        }

        @Override
        public boolean equals(ItemStack first, ItemStack second) {
            return first == second || first != null && second != null && ItemStack.isSameItemSameComponents(first, second);
        }
    };

    @Override
    public boolean mapInputs(RECIPE recipe, ItemStackIngredient inputIngredient) {
        if (inputIngredient.hasComponentPredicate()) {
            //Special handling for component matching ingredients: there is no valid quick lookup key, so mark it as complex
            return true;
        }
        boolean result = false;
        for (Ingredient ingredient : inputIngredient.ingredients()) {
            result |= mapIngredient(recipe, ingredient);
        }
        return result;
    }

    private boolean mapIngredient(RECIPE recipe, Ingredient input) {
        //Simple ingredients don't actually check anything related to components, so we can add the items to our base/raw input cache directly
        for (ItemStack item : input.getItems()) {
            if (!item.isEmpty()) {
                //Ignore empty stacks as some mods have ingredients that some stacks are empty
                addInputCache(item.getItem(), recipe);
            }
        }
        return false;
    }

    @Override
    protected Item createKey(ItemStack stack) {
        return stack.getItem();
    }

    @Override
    public boolean isEmpty(ItemStack input) {
        return input.isEmpty();
    }
}
