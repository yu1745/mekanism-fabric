package mekanism.common.recipe.lookup.cache;

import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.fluid.FluidStack;
import mekanism.api.functions.TriPredicate;
import mekanism.api.recipes.MekanismRecipe;
import mekanism.api.recipes.ingredients.ChemicalStackIngredient;
import mekanism.api.recipes.ingredients.FluidStackIngredient;
import mekanism.api.recipes.ingredients.ItemStackIngredient;
import mekanism.common.recipe.lookup.cache.DoubleInputRecipeCache.DoubleSameInputRecipeCache;
import mekanism.common.recipe.lookup.cache.type.ChemicalInputCache;
import mekanism.common.recipe.lookup.cache.type.FluidInputCache;
import mekanism.common.recipe.lookup.cache.type.ItemInputCache;
import net.minecraft.world.item.ItemStack;

/**
 * @implNote The Fabric port takes a {@link RecipeCacheSource} instead of the deferred recipe-type owner, and {@link EitherSideChemical} is generic over
 * {@code MekanismRecipe<?> & BiPredicate<ChemicalStack, ChemicalStack>} with explicit input extractors instead of importing a concrete chemical recipe abstraction.
 */
public class InputRecipeCache {

    public static class SingleItem<RECIPE extends MekanismRecipe<?> & Predicate<ItemStack>>
          extends SingleInputRecipeCache<ItemStack, ItemStackIngredient, RECIPE, ItemInputCache<RECIPE>> {

        public SingleItem(RecipeCacheSource<RECIPE> source, Function<RECIPE, ItemStackIngredient> inputExtractor) {
            super(source, inputExtractor, ItemInputCache::new);
        }
    }

    public static class SingleFluid<RECIPE extends MekanismRecipe<?> & Predicate<FluidStack>>
          extends SingleInputRecipeCache<FluidStack, FluidStackIngredient, RECIPE, FluidInputCache<RECIPE>> {

        public SingleFluid(RecipeCacheSource<RECIPE> source, Function<RECIPE, FluidStackIngredient> inputExtractor) {
            super(source, inputExtractor, FluidInputCache::new);
        }
    }

    public static class SingleChemical<RECIPE extends MekanismRecipe<?> & Predicate<ChemicalStack>>
          extends SingleInputRecipeCache<ChemicalStack, ChemicalStackIngredient, RECIPE, ChemicalInputCache<RECIPE>> {

        public SingleChemical(RecipeCacheSource<RECIPE> source, Function<RECIPE, ChemicalStackIngredient> inputExtractor) {
            super(source, inputExtractor, ChemicalInputCache::new);
        }
    }

    public static class DoubleItem<RECIPE extends MekanismRecipe<?> & BiPredicate<ItemStack, ItemStack>>
          extends DoubleSameInputRecipeCache<ItemStack, ItemStackIngredient, RECIPE, ItemInputCache<RECIPE>> {

        public DoubleItem(RecipeCacheSource<RECIPE> source, Function<RECIPE, ItemStackIngredient> inputAExtractor,
              Function<RECIPE, ItemStackIngredient> inputBExtractor) {
            super(source, inputAExtractor, inputBExtractor, ItemInputCache::new);
        }
    }

    public static class ItemChemical<RECIPE extends MekanismRecipe<?> & BiPredicate<ItemStack, ChemicalStack>> extends
          DoubleInputRecipeCache<ItemStack, ItemStackIngredient, ChemicalStack, ChemicalStackIngredient, RECIPE, ItemInputCache<RECIPE>, ChemicalInputCache<RECIPE>> {

        public ItemChemical(RecipeCacheSource<RECIPE> source, Function<RECIPE, ItemStackIngredient> inputAExtractor,
              Function<RECIPE, ChemicalStackIngredient> inputBExtractor) {
            super(source, inputAExtractor, ItemInputCache::new, inputBExtractor, ChemicalInputCache::new);
        }
    }

    public static class FluidChemical<RECIPE extends MekanismRecipe<?> & BiPredicate<FluidStack, ChemicalStack>> extends
          DoubleInputRecipeCache<FluidStack, FluidStackIngredient, ChemicalStack, ChemicalStackIngredient, RECIPE, FluidInputCache<RECIPE>, ChemicalInputCache<RECIPE>> {

        public FluidChemical(RecipeCacheSource<RECIPE> source, Function<RECIPE, FluidStackIngredient> inputAExtractor,
              Function<RECIPE, ChemicalStackIngredient> inputBExtractor) {
            super(source, inputAExtractor, FluidInputCache::new, inputBExtractor, ChemicalInputCache::new);
        }
    }

    public static class EitherSideChemical<RECIPE extends MekanismRecipe<?> & BiPredicate<ChemicalStack, ChemicalStack>>
          extends EitherSideInputRecipeCache<ChemicalStack, ChemicalStackIngredient, RECIPE, ChemicalInputCache<RECIPE>> {

        public EitherSideChemical(RecipeCacheSource<RECIPE> source, Function<RECIPE, ChemicalStackIngredient> inputAExtractor,
              Function<RECIPE, ChemicalStackIngredient> inputBExtractor) {
            super(source, inputAExtractor, inputBExtractor, ChemicalInputCache::new);
        }
    }

    public static class ItemFluidChemical<RECIPE extends MekanismRecipe<?> & TriPredicate<ItemStack, FluidStack, ChemicalStack>> extends
          TripleInputRecipeCache<ItemStack, ItemStackIngredient, FluidStack, FluidStackIngredient, ChemicalStack, ChemicalStackIngredient, RECIPE, ItemInputCache<RECIPE>,
                FluidInputCache<RECIPE>, ChemicalInputCache<RECIPE>> {

        public ItemFluidChemical(RecipeCacheSource<RECIPE> source, Function<RECIPE, ItemStackIngredient> inputAExtractor,
              Function<RECIPE, FluidStackIngredient> inputBExtractor, Function<RECIPE, ChemicalStackIngredient> inputCExtractor) {
            super(source, inputAExtractor, ItemInputCache::new, inputBExtractor, FluidInputCache::new, inputCExtractor, ChemicalInputCache::new);
        }
    }
}
