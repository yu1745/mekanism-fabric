package mekanism.api.recipes;

import java.util.List;
import mekanism.api.annotations.NothingNullByDefault;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * Layer-neutral service owner for the canonical Mekanism recipe types.
 *
 * <p>The common declaration owner creates each concrete {@link RecipeType} exactly once and registers it under its official id. This narrow seam exists so that
 * {@link MekanismRecipeTypes} can obtain those <strong>same</strong> instances without importing any common implementation type, and without ever constructing a
 * second {@code RecipeType} per id. A wrapper that created its own anonymous instance would split recipe identity, because every Mekanism recipe reports
 * {@code getType()} from the canonical object and vanilla {@code RecipeManager} groups recipes by identity.</p>
 *
 * <p>The implementation is discovered exactly once through {@link mekanism.api.MekanismServices}. A missing provider fails honestly instead of falling back to a
 * second identity.</p>
 *
 * @since 10.7.12
 */
@NothingNullByDefault
public interface IRecipeTypeRegistry {

    /**
     * Resolves the one canonical recipe type declared for the given official id.
     *
     * @param id Official recipe type id.
     *
     * @return the canonical recipe type instance, never a fresh wrapper.
     *
     * @throws IllegalArgumentException if this owner declares no recipe type under that id.
     */
    RecipeType<?> canonical(ResourceLocation id);

    /**
     * Resolves the canonical registry holder for the given official id.
     *
     * <p>Used after {@link #registerAll()} to attach the canonical holder back onto the API's keyed holder over the exact same recipe type instance.</p>
     *
     * @param id Official recipe type id.
     *
     * @return the canonical holder for that id.
     *
     * @throws IllegalArgumentException if this owner declares no recipe type under that id.
     */
    Holder<RecipeType<?>> canonicalHolder(ResourceLocation id);

    /**
     * @return Every official recipe type id this owner declares, in declaration order.
     */
    List<ResourceLocation> recipeTypeIds();

    /**
     * Writes every declared recipe type into the vanilla recipe type registry under its official id.
     *
     * <p>The owner's registration lifecycle rejects a duplicate or occupied key, so this is not silently repeatable; callers memoize the single successful write.
     */
    void registerAll();
}
