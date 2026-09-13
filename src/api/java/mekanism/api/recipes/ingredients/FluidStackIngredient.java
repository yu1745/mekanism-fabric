package mekanism.api.recipes.ingredients;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.RecordBuilder;
import java.util.List;
import java.util.Objects;
import mekanism.api.SerializationConstants;
import mekanism.api.annotations.NothingNullByDefault;
import mekanism.api.fluid.FluidStack;
import mekanism.api.recipes.ingredients.fluid.FluidIngredient;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation for how Mekanism handle's FluidStack Ingredients.
 * <p>
 * Create instances of this using {@link mekanism.api.recipes.ingredients.creator.IngredientCreatorAccess#fluid()}.
 *
 * @implNote Unlike the NeoForge original which wraps {@code SizedFluidIngredient}, the Fabric port pairs the layer-neutral {@link FluidIngredient} predicate with a
 * positive amount in integer Mekanism mB. There is no 81,000-droplet platform unit involved anywhere in this ingredient.
 */
@NothingNullByDefault
public final class FluidStackIngredient implements InputIngredient<@NotNull FluidStack> {

    private static final Logger LOGGER = LoggerFactory.getLogger("mekanism.api.MekanismAPI");

    /**
     * A codec which can (de)encode fluid stack ingredients.
     *
     * @implNote Flat composition matching the official sized fluid ingredient format: the fluid/tag fields (or a typed component predicate) live inline next to the
     * {@code amount} field, which defaults to 1,000 mB when absent and is always written.
     *
     * @since 10.6.0
     */
    public static final Codec<FluidStackIngredient> CODEC = new FlatCodec();
    /**
     * A stream codec which can be used to encode and decode fluid stack ingredients over the network.
     *
     * @since 10.6.0
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, FluidStackIngredient> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public FluidStackIngredient decode(RegistryFriendlyByteBuf buffer) {
            return FluidStackIngredient.of(FluidIngredient.STREAM_CODEC.decode(buffer), ByteBufCodecs.VAR_INT.decode(buffer));
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, FluidStackIngredient value) {
            FluidIngredient.STREAM_CODEC.encode(buffer, value.ingredient);
            ByteBufCodecs.VAR_INT.encode(buffer, value.amount);
        }
    };

    /**
     * Creates a Fluid Stack Ingredient that matches a given ingredient and amount. Prefer calling via
     * {@link mekanism.api.recipes.ingredients.creator.IngredientCreatorAccess#fluid()} and
     * {@link mekanism.api.recipes.ingredients.creator.IFluidStackIngredientCreator#from(FluidIngredient, int)}.
     *
     * @param ingredient Ingredient to match.
     * @param amount     Amount to match.
     *
     * @throws NullPointerException     if the given instance is null.
     * @throws IllegalArgumentException if the given instance is empty or the amount is not positive.
     * @since 10.6.0
     */
    public static FluidStackIngredient of(FluidIngredient ingredient, int amount) {
        Objects.requireNonNull(ingredient, "FluidStackIngredients cannot be created from a null ingredient.");
        if (ingredient.isEmpty()) {
            throw new IllegalArgumentException("FluidStackIngredients cannot be created using the empty ingredient.");
        }
        if (amount < 1) {
            throw new IllegalArgumentException("FluidStackIngredients require a positive amount.");
        }
        return new FluidStackIngredient(ingredient, amount);
    }

    private final FluidIngredient ingredient;
    private final int amount;
    @Nullable
    private List<FluidStack> representations;

    private FluidStackIngredient(FluidIngredient ingredient, int amount) {
        this.ingredient = ingredient;
        this.amount = amount;
    }

    @Override
    public boolean test(FluidStack stack) {
        Objects.requireNonNull(stack);
        return testType(stack) && stack.getAmount() >= amount;
    }

    @Override
    public boolean testType(FluidStack stack) {
        Objects.requireNonNull(stack);
        return ingredient.test(stack);
    }

    @Override
    public FluidStack getMatchingInstance(FluidStack stack) {
        return test(stack) ? stack.copyWithAmount(amount) : FluidStack.EMPTY;
    }

    @Override
    public long getNeededAmount(FluidStack stack) {
        return testType(stack) ? amount : 0;
    }

    @Override
    public boolean hasNoMatchingInstances() {
        return ingredient.hasNoFluids();
    }

    @Override
    public void logMissingTags() {
        if (hasNoMatchingInstances()) {
            LOGGER.error("Empty FluidStackIngredient: {}", ingredient);
        }
    }

    @Override
    public List<@NotNull FluidStack> getRepresentations() {
        if (this.representations == null) {
            this.representations = ingredient.getStacks().stream()
                  .map(stack -> stack.copyWithAmount(amount))
                  .toList();
        }
        return representations;
    }

    /**
     * For use in recipe input caching. Gets the internal fluid ingredient.
     *
     * @since 10.6.0
     */
    @Internal
    public FluidIngredient ingredient() {
        return ingredient;
    }

    /**
     * For use in recipe input caching. Gets the internal amount this ingredient represents.
     *
     * @since 10.6.0
     */
    @Internal
    public int amount() {
        return amount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (o == null || getClass() != o.getClass()) {
            return false;
        }
        FluidStackIngredient other = (FluidStackIngredient) o;
        return amount == other.amount && ingredient.equals(other.ingredient);
    }

    @Override
    public int hashCode() {
        return 31 * ingredient.hashCode() + amount;
    }

    @Override
    public String toString() {
        return amount + "mB " + ingredient;
    }

    private static final class FlatCodec implements Codec<FluidStackIngredient> {

        @Override
        public <T> DataResult<com.mojang.datafixers.util.Pair<FluidStackIngredient, T>> decode(DynamicOps<T> ops, T input) {
            return ops.getMap(input).flatMap(map -> FluidIngredient.MAP_CODEC.decode(ops, map)
                  .flatMap(ingredient -> Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf(SerializationConstants.AMOUNT, 1_000).codec().decode(ops, input)
                        .map(amount -> new FluidStackIngredient(ingredient, amount.getFirst()))))
                  .map(ingredient -> com.mojang.datafixers.util.Pair.of(ingredient, input));
        }

        @Override
        public <T> DataResult<T> encode(FluidStackIngredient value, DynamicOps<T> ops, T prefix) {
            RecordBuilder<T> builder = ops.mapBuilder();
            FluidIngredient.MAP_CODEC.encode(value.ingredient, ops, builder);
            builder.add(SerializationConstants.AMOUNT, ops.createInt(value.amount));
            return builder.build(prefix);
        }
    }
}
