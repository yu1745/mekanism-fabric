package mekanism.common.recipe.lookup.cache;

import java.util.List;
import java.util.Objects;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Supplies the immutable list of usable {@link RecipeHolder}s of one recipe kind from a bound {@link RecipeManager}, replacing the deferred recipe-type owner as the
 * generic caches' recipe source.
 *
 * <p>The default wrapper serves one vanilla {@link RecipeType} through {@link RecipeManager#getAllRecipesFor(Object)} and filters incomplete recipes exactly as the
 * official owner does. It does not add synthetic smelting recipes, which only the canonical Mekanism owner can do because only it knows the matching registry
 * access.</p>
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
     * Gets the immutable list of usable recipe holders from the given manager, using the given registry access when the implementation needs it.
     *
     * <p>The default implementation has no use for registry access and delegates to {@link #recipes(RecipeManager)}, so the plain recipe-type wrapper never gains a
     * synthetic-recipe path by accident.</p>
     *
     * @param manager        Manager to query.
     * @param registryAccess Registry access published with that manager, or {@code null} when the caller has none.
     */
    @NotNull
    default List<RecipeHolder<RECIPE>> recipes(RecipeManager manager, @Nullable RegistryAccess registryAccess) {
        return recipes(manager);
    }

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
