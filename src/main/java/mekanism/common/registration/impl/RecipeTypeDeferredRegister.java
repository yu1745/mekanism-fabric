package mekanism.common.registration.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import mekanism.api.recipes.MekanismRecipe;
import mekanism.common.recipe.MekanismRecipeType;
import mekanism.common.recipe.lookup.cache.IInputRecipeCache;
import mekanism.common.registration.MekanismDeferredRegister;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * Deferred register for the canonical Mekanism recipe types.
 *
 * <p>Each declaration is created <strong>eagerly and exactly once</strong> by the common owner, installed on its specialized
 * {@link RecipeTypeRegistryObject} as that holder's prebuilt value, and only then handed to the existing deferred registration machinery as a supplier that returns
 * that same instance. Registration therefore cannot create a second recipe type: {@code Recipe#getType()} identity survives the registry write unchanged.</p>
 *
 * <p>The generic deferred-registration lifecycle is used unchanged, so an occupied key, a repeated write, or a duplicate name still fails loudly.</p>
 */
public class RecipeTypeDeferredRegister extends MekanismDeferredRegister<RecipeType<?>> {

    private final List<RecipeTypeRegistryObject<?, ?, ?>> entries = new ArrayList<>();
    private final String namespace;

    public RecipeTypeDeferredRegister(String modid) {
        super(Registries.RECIPE_TYPE, modid, RecipeTypeRegistryObject::new);
        namespace = modid;
    }

    /** Retains the official factory-taking registration surface; the Fabric owner resolves the factory exactly once before registration. */
    public <VANILLA_INPUT extends RecipeInput, RECIPE extends MekanismRecipe<VANILLA_INPUT>, INPUT_CACHE extends IInputRecipeCache>
    RecipeTypeRegistryObject<VANILLA_INPUT, RECIPE, INPUT_CACHE> registerMek(String name,
          Function<ResourceLocation, ? extends MekanismRecipeType<VANILLA_INPUT, RECIPE, INPUT_CACHE>> factory) {
        Objects.requireNonNull(factory, "factory");
        return registerMek(name, factory.apply(ResourceLocation.fromNamespaceAndPath(namespace, name)));
    }

    /**
     * Declares one prebuilt recipe type: installs the single concrete instance on a fresh specialized holder, registers a supplier returning exactly that instance,
     * and records it for owner enumeration.
     *
     * @param name       Path of the official recipe type id.
     * @param recipeType The one concrete recipe type created for that id.
     *
     * @return the specialized holder over the prebuilt recipe type.
     */
    @SuppressWarnings("unchecked")
    public <VANILLA_INPUT extends RecipeInput, RECIPE extends MekanismRecipe<VANILLA_INPUT>, INPUT_CACHE extends IInputRecipeCache>
    RecipeTypeRegistryObject<VANILLA_INPUT, RECIPE, INPUT_CACHE> registerMek(String name, MekanismRecipeType<VANILLA_INPUT, RECIPE, INPUT_CACHE> recipeType) {
        Objects.requireNonNull(recipeType, "recipeType");
        RecipeTypeRegistryObject<VANILLA_INPUT, RECIPE, INPUT_CACHE> holder =
              (RecipeTypeRegistryObject<VANILLA_INPUT, RECIPE, INPUT_CACHE>) super.register(name, () -> recipeType);
        holder.setPrebuilt(recipeType);
        entries.add(holder);
        return holder;
    }

    /**
     * @return The declared recipe types in declaration order, as an immutable list.
     */
    public List<RecipeTypeRegistryObject<?, ?, ?>> getEntries() {
        return Collections.unmodifiableList(entries);
    }
}
