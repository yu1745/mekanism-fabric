package mekanism.api.recipes.ingredients.creator;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import mekanism.api.annotations.NothingNullByDefault;
import mekanism.api.fluid.FluidStack;
import mekanism.api.providers.IFluidProvider;
import mekanism.api.recipes.ingredients.FluidStackIngredient;
import mekanism.api.recipes.ingredients.fluid.FluidIngredient;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPredicate;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;

@NothingNullByDefault
public interface IFluidStackIngredientCreator extends IIngredientCreator<Fluid, FluidStack, FluidStackIngredient> {

    /**
     * Creates a Fluid Stack Ingredient that matches a provided fluid and amount.
     *
     * @param provider Fluid provider that provides the fluid to match.
     * @param amount   Amount needed.
     *
     * @throws NullPointerException     if the given instance is null.
     * @throws IllegalArgumentException if the given instance is empty or an amount smaller than one.
     * @implNote This wraps via {@link #from(FluidStack)} so if there are any non-default data components, a non-strict component predicate will be included in the
     * ingredient.
     */
    @SuppressWarnings("removal")
    @Deprecated(forRemoval = true, since = "10.7.11")
    default FluidStackIngredient from(IFluidProvider provider, int amount) {
        Objects.requireNonNull(provider, "FluidStackIngredients cannot be created from a null fluid provider.");
        return from(provider.getFluidStack(amount));
    }

    /**
     * Creates a Fluid Stack Ingredient that matches a provided fluids and amount.
     *
     * @param amount Amount needed.
     * @param fluids Fluid providers that provides the fluids to match.
     *
     * @throws IllegalArgumentException if no fluids are passed.
     * @implNote This wraps via {@link #from(FluidIngredient, int)} so if there are any default components it will <strong>NOT</strong> be included in the
     * ingredient.
     * @since 10.6.0
     */
    @SuppressWarnings("removal")
    @Deprecated(forRemoval = true, since = "10.7.11")
    default FluidStackIngredient from(int amount, IFluidProvider... fluids) {
        return from(FluidIngredient.ofIngredients(Arrays.stream(fluids).map(IFluidProvider::getFluid).map(fluid -> FluidIngredient.of(fluid.builtInRegistryHolder())).toList()), amount);
    }

    /**
     * @implNote This wraps via {@link #from(FluidIngredient, int)} so if there are any default components it will <strong>NOT</strong> be included in the ingredient.
     * @since 10.7.11
     */
    @Override
    @SuppressWarnings("unchecked")
    default FluidStackIngredient fromHolders(int amount, Holder<Fluid>... fluids) {
        if (fluids.length == 0) {
            throw new IllegalArgumentException("Attempted to create a FluidStackIngredient with no fluids.");
        }
        return from(FluidIngredient.ofIngredients(Arrays.stream(fluids).<FluidIngredient>map(FluidIngredient::of).toList()), amount);
    }

    /**
     * @implNote This wraps via {@link #from(FluidIngredient, int)} so if there are any default components it will <strong>NOT</strong> be included in the ingredient.
     */
    @Override
    @SuppressWarnings("removal")
    @Deprecated(forRemoval = true, since = "10.7.11")
    default FluidStackIngredient from(Fluid instance, int amount) {
        return from(FluidIngredient.of(instance.builtInRegistryHolder()), amount);
    }

    /**
     * @implNote This wraps via {@link #from(FluidIngredient, int)} so if there are any default components it will <strong>NOT</strong> be included in the ingredient.
     */
    @Override
    default FluidStackIngredient fromHolder(Holder<Fluid> instance, int amount) {
        return from(FluidIngredient.of(instance), amount);
    }

    /**
     * @implNote This wraps via {@link #from(FluidIngredient, int)} so if there are any default components it will <strong>NOT</strong> be included in the ingredient.
     * @since 10.6.0
     */
    @Override
    @SuppressWarnings("removal")
    @Deprecated(forRemoval = true, since = "10.7.11")
    default FluidStackIngredient from(int amount, Fluid... fluids) {
        if (fluids.length == 0) {
            throw new IllegalArgumentException("Attempted to create an FluidStackIngredient with no fluids.");
        }
        return from(FluidIngredient.ofIngredients(Arrays.stream(fluids).map(fluid -> FluidIngredient.of(fluid.builtInRegistryHolder())).toList()), amount);
    }

    /**
     * @implNote If the stack has any non-default data components, a non-strict component matching those additions will be used.
     */
    @Override
    default FluidStackIngredient from(FluidStack instance) {
        Objects.requireNonNull(instance, "FluidStackIngredients cannot be created from a null FluidStack.");
        if (instance.isEmpty()) {
            throw new IllegalArgumentException("FluidStackIngredients cannot be created using the empty stack.");
        }
        //Copy the stack to ensure it doesn't get modified afterward
        instance = instance.copy();
        //Support Components that are on the stack in case it matters
        // Note: Only bother making it a component ingredient if the stack has non-default data, otherwise there is no point in doing the extra checks
        DataComponentPredicate predicate = IngredientCreatorAccess.getComponentPatchPredicate(instance.getComponentsPatch());
        if (predicate != null) {
            return from(FluidIngredient.of(predicate, List.of(instance.getFluidHolder())), instance.getAmount());
        }
        return from(FluidIngredient.of(instance.getFluidHolder()), instance.getAmount());
    }

    @Override
    default FluidStackIngredient from(TagKey<Fluid> tag, int amount) {
        Objects.requireNonNull(tag, "FluidStackIngredients cannot be created from a null tag.");
        return from(FluidIngredient.of(tag), amount);
    }

    /**
     * Creates a Fluid Stack Ingredient that matches a given ingredient and amount.
     *
     * @param ingredient Ingredient to match.
     * @param amount     Amount needed.
     *
     * @throws NullPointerException     if the given instance is null.
     * @throws IllegalArgumentException if the given instance is empty or an amount smaller than one.
     * @since 10.6.0
     */
    default FluidStackIngredient from(FluidIngredient ingredient, int amount) {
        Objects.requireNonNull(ingredient, "FluidStackIngredients cannot be created from a null fluid ingredient.");
        return FluidStackIngredient.of(ingredient, amount);
    }
}
