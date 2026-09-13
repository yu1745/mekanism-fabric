package mekanism.common.recipe.lookup.cache;

import java.util.List;
import java.util.Objects;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * Supplies the immutable list of usable {@link RecipeHolder}s of one recipe kind from a bound {@link RecipeManager}, replacing the deferred recipe-type owner as the
 * generic caches' recipe source.
 *
 * <p>The default wrapper serves one vanilla {@link RecipeType} through {@link RecipeManager#getAllRecipesFor(Object)} and filters incomplete recipes exactly as the
 * official owner does. It does not add synthetic smelting recipes.</p>
 *
 * @since 10.7.12
 */
@FunctionalInterface
public interface RecipeCacheSource<RECIPE extends Recipe<?>> {

    /**
     * Gets the immutable list of usable recipe holders from the given manager.
     */
    List<RecipeHolder<RECIPE>> recipes(RecipeManager manager);

    /**
     * Creates a source wrapping one vanilla recipe type, filtering incomplete recipes.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static <RECIPE extends Recipe<?>> RecipeCacheSource<RECIPE> forType(RecipeType<RECIPE> type) {
        Objects.requireNonNull(type, "type");
        RecipeType raw = type;
        return manager -> {
            List<RecipeHolder<RECIPE>> recipes = manager.getAllRecipesFor(raw);
            return recipes.stream()
                  .filter(recipe -> !recipe.value().isIncomplete())
                  .toList();
        };
    }
}
