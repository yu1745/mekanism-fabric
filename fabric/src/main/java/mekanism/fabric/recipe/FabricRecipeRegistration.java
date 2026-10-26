package mekanism.fabric.recipe;

import mekanism.common.registries.MekanismRecipeSerializersInternal;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * Commits the official Mekanism recipe type and recipe serializer catalogs into the vanilla built-in registries. It runs in the same startup window as the other
 * game event registration, where the Fabric loader has already bootstrapped vanilla but the built-in registries are still writable.
 *
 * <p>The recipe types are registered first, because both share the same startup window and recipe type identity must be established before any recipe can be
 * decoded or served.
 */
public final class FabricRecipeRegistration {

    private FabricRecipeRegistration() {
    }

    /**
     * Registers every Mekanism recipe type and recipe serializer into the vanilla built-in registries.
     *
     * <p>The deferred register rejects a duplicate or occupied key, so a second call fails loudly instead of silently doubling.
     */
    public static void register() {
        mekanism.api.recipes.MekanismRecipeTypes.ensureRegistered();
        MekanismRecipeSerializersInternal.RECIPE_SERIALIZERS.registerAll(BuiltInRegistries.RECIPE_SERIALIZER);
    }
}
