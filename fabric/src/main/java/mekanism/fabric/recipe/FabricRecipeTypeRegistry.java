package mekanism.fabric.recipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import mekanism.api.recipes.IRecipeTypeRegistry;
import mekanism.common.recipe.MekanismRecipeType;
import mekanism.common.registration.impl.RecipeTypeRegistryObject;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * Service implementation exposing the common canonical recipe-type owner to the layer-neutral API.
 *
 * <p>Resolution and registration both delegate to {@link MekanismRecipeType}, so the API never creates a recipe type of its own and there is exactly one instance per
 * official id. The registry write is the common owner's own deferred registration, so the key/occupied/duplicate discipline is the existing one.</p>
 */
public final class FabricRecipeTypeRegistry implements IRecipeTypeRegistry {

    @Override
    public RecipeType<?> canonical(ResourceLocation id) {
        return entry(id).getRecipeType();
    }

    @Override
    public Holder<RecipeType<?>> canonicalHolder(ResourceLocation id) {
        return BuiltInRegistries.RECIPE_TYPE.getHolderOrThrow(entry(id).getKey());
    }

    @Override
    public List<ResourceLocation> recipeTypeIds() {
        List<RecipeTypeRegistryObject<?, ?, ?>> entries = MekanismRecipeType.RECIPE_TYPES.getEntries();
        List<ResourceLocation> ids = new ArrayList<>(entries.size());
        for (RecipeTypeRegistryObject<?, ?, ?> entry : entries) {
            ids.add(entry.getId());
        }
        return List.copyOf(ids);
    }

    @Override
    public void registerAll() {
        MekanismRecipeType.RECIPE_TYPES.registerAll(BuiltInRegistries.RECIPE_TYPE);
    }

    private static RecipeTypeRegistryObject<?, ?, ?> entry(ResourceLocation id) {
        Objects.requireNonNull(id, "id");
        for (RecipeTypeRegistryObject<?, ?, ?> entry : MekanismRecipeType.RECIPE_TYPES.getEntries()) {
            if (id.equals(entry.getId())) {
                return entry;
            }
        }
        throw new IllegalArgumentException("No Mekanism recipe type is declared for " + id);
    }
}
