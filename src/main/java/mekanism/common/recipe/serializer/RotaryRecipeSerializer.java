package mekanism.common.recipe.serializer;

import com.mojang.datafixers.util.Function4;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.handler.codec.DecoderException;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import mekanism.api.SerializationConstants;
import mekanism.api.annotations.NothingNullByDefault;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.fluid.FluidStack;
import mekanism.api.recipes.basic.BasicRotaryRecipe;
import mekanism.api.recipes.ingredients.ChemicalStackIngredient;
import mekanism.api.recipes.ingredients.FluidStackIngredient;
import mekanism.api.recipes.ingredients.creator.IngredientCreatorAccess;
import mekanism.common.recipe.ingredients.creator.ChemicalStackIngredientCreator;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.RecipeSerializer;

@NothingNullByDefault
public class RotaryRecipeSerializer implements RecipeSerializer<BasicRotaryRecipe> {

    private final RecordCodecBuilder<BasicRotaryRecipe, FluidStackIngredient> FLUID_INPUT_FIELD = FluidStackIngredient.CODEC.validate(
          ingredient -> ingredient == null ? DataResult.error(() -> "Fluid input may not be empty") : DataResult.success(ingredient)
    ).fieldOf(SerializationConstants.FLUID_INPUT).forGetter(BasicRotaryRecipe::getFluidInputRaw);
    private final RecordCodecBuilder<BasicRotaryRecipe, FluidStack> FLUID_OUTPUT_FIELD = FluidStack.CODEC.fieldOf(SerializationConstants.FLUID_OUTPUT).forGetter(BasicRotaryRecipe::getFluidOutputRaw);
    private final RecordCodecBuilder<BasicRotaryRecipe, ChemicalStackIngredient> CHEMICAL_INPUT_FIELD = ChemicalStackIngredientCreator.INSTANCE.codec().validate(
          ingredient -> ingredient == null ? DataResult.error(() -> "Chemical input may not be empty") : DataResult.success(ingredient)
    ).fieldOf(SerializationConstants.CHEMICAL_INPUT).forGetter(BasicRotaryRecipe::getChemicalInputRaw);
    private final RecordCodecBuilder<BasicRotaryRecipe, ChemicalStack> CHEMICAL_OUTPUT_FIELD = ChemicalStack.CODEC.fieldOf(SerializationConstants.CHEMICAL_OUTPUT).forGetter(BasicRotaryRecipe::getChemicalOutputRaw);

    private final StreamCodec<RegistryFriendlyByteBuf, BasicRotaryRecipe> streamCodec;
    private final MapCodec<BasicRotaryRecipe> codec;

    public RotaryRecipeSerializer(Function4<FluidStackIngredient, ChemicalStackIngredient, ChemicalStack, FluidStack, BasicRotaryRecipe> bothWaysFactory,
          BiFunction<FluidStackIngredient, ChemicalStack, BasicRotaryRecipe> toChemicalFactory,
          BiFunction<ChemicalStackIngredient, FluidStack, BasicRotaryRecipe> toFluidFactory) {
        this.codec = withAlternative(
              RecordCodecBuilder.mapCodec(i -> i.group(
                    FLUID_INPUT_FIELD,
                    CHEMICAL_INPUT_FIELD,
                    CHEMICAL_OUTPUT_FIELD,
                    FLUID_OUTPUT_FIELD
              ).apply(i, bothWaysFactory)),
              withAlternative(
                    RecordCodecBuilder.mapCodec(i -> i.group(
                          FLUID_INPUT_FIELD,
                          CHEMICAL_OUTPUT_FIELD
                    ).apply(i, toChemicalFactory)),
                    RecordCodecBuilder.mapCodec(i -> i.group(
                          CHEMICAL_INPUT_FIELD,
                          FLUID_OUTPUT_FIELD
                    ).apply(i, toFluidFactory))
              )
        );
        this.streamCodec = StreamCodec.composite(
              ByteBufCodecs.optional(FluidToChemical.STREAM_CODEC), recipe -> recipe.hasFluidToChemical() ? Optional.of(new FluidToChemical(recipe)) : Optional.empty(),
              ByteBufCodecs.optional(ChemicalToFluid.STREAM_CODEC), recipe -> recipe.hasChemicalToFluid() ? Optional.of(new ChemicalToFluid(recipe)) : Optional.empty(),
              (toGas, toFluid) -> {
                  if (toGas.isPresent()) {
                      FluidToChemical fluidToChemical = toGas.get();
                      if (toFluid.isPresent()) {
                          ChemicalToFluid chemicalToFluid = toFluid.get();
                          return bothWaysFactory.apply(fluidToChemical.input(), chemicalToFluid.input(), fluidToChemical.output(), chemicalToFluid.output());
                      }
                      return toChemicalFactory.apply(fluidToChemical.input(), fluidToChemical.output());
                  } else if (toFluid.isPresent()) {
                      ChemicalToFluid chemicalToFluid = toFluid.get();
                      return toFluidFactory.apply(chemicalToFluid.input(), chemicalToFluid.output());
                  }
                  throw new DecoderException("A recipe got sent with no conversion in either direction.");
              }
        );
    }

    /**
     * Map codec with two alternatives. Both alternatives are tried for decoding, and encoding falls through to the second only when the first errors.
     *
     * <p>This is an exact reimplementation of the NeoForge helper of the same name, kept here so the recipe serializers do not depend on a NeoForge-only util class.
     * The declared {@code keys} are the distinct union of both alternatives, because either shape may legitimately appear in a serialized recipe.
     */
    private static <T> MapCodec<T> withAlternative(MapCodec<T> codec, MapCodec<T> alternative) {
        return new AlternativeMapCodec<>(codec, alternative);
    }

    private static class AlternativeMapCodec<T> extends MapCodec<T> {

        private final MapCodec<T> codec;
        private final MapCodec<T> alternative;

        private AlternativeMapCodec(MapCodec<T> codec, MapCodec<T> alternative) {
            this.codec = codec;
            this.alternative = alternative;
        }

        @Override
        public <K> Stream<K> keys(DynamicOps<K> ops) {
            return Stream.concat(codec.keys(ops), alternative.keys(ops)).distinct();
        }

        @Override
        public <K> DataResult<T> decode(DynamicOps<K> ops, MapLike<K> input) {
            DataResult<T> result = codec.decode(ops, input);
            return result.error().isEmpty() ? result : alternative.decode(ops, input);
        }

        @Override
        public <K> RecordBuilder<K> encode(T input, DynamicOps<K> ops, RecordBuilder<K> prefix) {
            //Note: The first encode is only a probe and its builder is consumed, so the successful codec is encoded a second time to apply it for real
            DataResult<K> probe = codec.encode(input, ops, prefix).build(ops.empty());
            return probe.error().isEmpty() ? codec.encode(input, ops, prefix) : alternative.encode(input, ops, prefix);
        }

        @Override
        public String toString() {
            return "AlternativeMapCodec[" + codec + ", " + alternative + "]";
        }
    }

    @Override
    public MapCodec<BasicRotaryRecipe> codec() {
        return codec;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, BasicRotaryRecipe> streamCodec() {
        return streamCodec;
    }

    private record FluidToChemical(FluidStackIngredient input, ChemicalStack output) {

        //Note: This doesn't need to be optional gas, as we only use this if we have a fluid to gas recipe
        public static final StreamCodec<RegistryFriendlyByteBuf, FluidToChemical> STREAM_CODEC = StreamCodec.composite(
              FluidStackIngredient.STREAM_CODEC, FluidToChemical::input,
              ChemicalStack.STREAM_CODEC, FluidToChemical::output,
              FluidToChemical::new
        );

        private FluidToChemical(BasicRotaryRecipe recipe) {
            this(recipe.getFluidInput(), recipe.getChemicalOutputRaw());
        }
    }

    private record ChemicalToFluid(ChemicalStackIngredient input, FluidStack output) {

        //Note: This doesn't need to be optional fluid, as we only use this if we have a gas to fluid recipe
        public static final StreamCodec<RegistryFriendlyByteBuf, ChemicalToFluid> STREAM_CODEC = StreamCodec.composite(
              IngredientCreatorAccess.chemicalStack().streamCodec(), ChemicalToFluid::input,
              FluidStack.STREAM_CODEC, ChemicalToFluid::output,
              ChemicalToFluid::new
        );

        private ChemicalToFluid(BasicRotaryRecipe recipe) {
            this(recipe.getChemicalInput(), recipe.getFluidOutputRaw());
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            } else if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ChemicalToFluid other = (ChemicalToFluid) o;
            return FluidStack.matches(output, other.output) && input.equals(other.input);
        }

        @Override
        public int hashCode() {
            int hash = input.hashCode();
            hash = 31 * hash + FluidStack.hashFluidAndComponents(output);
            hash = 31 * hash + output.getAmount();
            return hash;
        }
    }
}