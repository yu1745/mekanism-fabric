package mekanism.common.registration.impl;

import java.util.Objects;
import mekanism.api.recipes.MekanismRecipe;
import mekanism.common.recipe.IMekanismRecipeTypeProvider;
import mekanism.common.recipe.MekanismRecipeType;
import mekanism.common.recipe.lookup.cache.IInputRecipeCache;
import mekanism.common.registration.MekanismDeferredHolder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeType;
import org.jetbrains.annotations.Nullable;

/**
 * Deferred holder for one canonical {@link MekanismRecipeType}.
 *
 * <p>The concrete recipe type is created <strong>once</strong>, before any registry write, and is installed here as the holder's prebuilt value. Before the
 * deferred registration binds, this holder already reports that exact instance, so {@code getRecipeType()}, {@code getRecipes()} and every recipe's
 * {@code getType()} work with the correct identity during construction. After binding, {@link #value()} additionally proves that the canonical registry entry is
 * the same instance rather than an unrelated one; a mismatch fails instead of silently splitting identity.</p>
 */
public class RecipeTypeRegistryObject<VANILLA_INPUT extends RecipeInput, RECIPE extends MekanismRecipe<VANILLA_INPUT>, INPUT_CACHE extends IInputRecipeCache> extends
      MekanismDeferredHolder<RecipeType<?>, MekanismRecipeType<VANILLA_INPUT, RECIPE, INPUT_CACHE>> implements IMekanismRecipeTypeProvider<VANILLA_INPUT, RECIPE, INPUT_CACHE> {

    @Nullable
    private MekanismRecipeType<VANILLA_INPUT, RECIPE, INPUT_CACHE> prebuilt;

    public RecipeTypeRegistryObject(ResourceKey<RecipeType<?>> key) {
        super(key);
    }

    /**
     * Installs the one concrete recipe type this holder is keyed over. Allowed exactly once.
     *
     * @param recipeType The canonical recipe type instance.
     *
     * @throws NullPointerException  if the given instance is null.
     * @throws IllegalStateException if this holder already has a prebuilt value.
     */
    public void setPrebuilt(MekanismRecipeType<VANILLA_INPUT, RECIPE, INPUT_CACHE> recipeType) {
        Objects.requireNonNull(recipeType, "Recipe type cannot be null.");
        if (prebuilt != null) {
            throw new IllegalStateException("Recipe type holder " + getKey().location() + " already has a prebuilt value");
        }
        prebuilt = recipeType;
    }

    @Override
    public MekanismRecipeType<VANILLA_INPUT, RECIPE, INPUT_CACHE> value() {
        if (super.isBound()) {
            MekanismRecipeType<VANILLA_INPUT, RECIPE, INPUT_CACHE> bound = super.value();
            MekanismRecipeType<VANILLA_INPUT, RECIPE, INPUT_CACHE> current = prebuilt;
            if (current != null && bound != current) {
                throw new IllegalStateException("Canonical recipe type " + getKey().location()
                      + " does not match the instance this holder was built over");
            }
            return bound;
        }
        MekanismRecipeType<VANILLA_INPUT, RECIPE, INPUT_CACHE> current = prebuilt;
        if (current != null) {
            return current;
        }
        //Note: No prebuilt value and no bound registry entry: fail honestly through the parent contract instead of inventing an instance.
        return super.value();
    }

    @Override
    public MekanismRecipeType<VANILLA_INPUT, RECIPE, INPUT_CACHE> get() {
        return value();
    }

    @Override
    public boolean isBound() {
        return super.isBound() || prebuilt != null;
    }

    @Override
    public MekanismRecipeType<VANILLA_INPUT, RECIPE, INPUT_CACHE> getRecipeType() {
        return value();
    }
}
