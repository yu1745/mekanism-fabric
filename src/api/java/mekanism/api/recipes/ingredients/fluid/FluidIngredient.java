package mekanism.api.recipes.ingredients.fluid;

import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import mekanism.api.SerializationConstants;
import mekanism.api.annotations.NothingNullByDefault;
import mekanism.api.fluid.FluidStack;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPredicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.Nullable;

/**
 * Layer-neutral immutable predicate over {@link FluidStack}s, playing the role the platform's fluid ingredient plays on NeoForge.
 *
 * <p>Supported forms are a single fluid holder, a fluid tag, a compound union of child ingredients, and a data component predicate over a set of fluids. Amounts are
 * owned by {@link mekanism.api.recipes.ingredients.FluidStackIngredient} and use integer Mekanism mB; this predicate ignores amounts entirely.</p>
 *
 * @see mekanism.api.recipes.ingredients.FluidStackIngredient
 * @since 10.7.12
 */
@NothingNullByDefault
public abstract class FluidIngredient implements Predicate<FluidStack> {

    private static final String COMPONENTS_TYPE = "neoforge:components";
    private static final String COMPOUND_TYPE = "neoforge:compound";
    private static final String FLUIDS = "fluids";

    /**
     * Map codec representing a {@link FluidIngredient} in map form: a single fluid ({@code {"fluid": id}}), a tag ({@code {"tag": id}}), or an explicitly typed
     * component predicate ({@code {"type": "neoforge:components", "fluids": [...], "components": {...}}}).
     */
    public static final MapCodec<FluidIngredient> MAP_CODEC = new FluidMapCodec();
    /**
     * Full codec representing a {@link FluidIngredient} in all supported forms. Arrays are read as compound unions of their elements.
     */
    public static final Codec<FluidIngredient> CODEC = Codec.either(MAP_CODEC.codec().listOf(), MAP_CODEC.codec())
          .flatComapMap(
                either -> either.map(FluidIngredient::ofIngredients, ingredient -> ingredient),
                ingredient -> {
                    if (ingredient instanceof CompoundFluidIngredient compound) {
                        return DataResult.success(Either.left(compound.children()));
                    } else if (ingredient instanceof SingleFluidIngredient single) {
                        return DataResult.success(Either.right(single));
                    } else if (ingredient instanceof TagFluidIngredient tagged) {
                        return DataResult.success(Either.right(tagged));
                    } else if (ingredient instanceof ComponentFluidIngredient) {
                        return DataResult.success(Either.right(ingredient));
                    } else if (ingredient instanceof EmptyFluidIngredient) {
                        return DataResult.success(Either.left(List.of()));
                    }
                    return DataResult.error(() -> "Unable to serialize fluid ingredient " + ingredient);
                });
    /**
     * Stream codec for syncing fluid ingredients over the network.
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, FluidIngredient> STREAM_CODEC = new FluidNetworkCodec();

    /**
     * Creates a fluid ingredient matching a single fluid holder.
     */
    public static FluidIngredient of(Holder<Fluid> fluid) {
        Objects.requireNonNull(fluid, "Fluid cannot be null");
        if (fluid.value() == Fluids.EMPTY) {
            throw new IllegalArgumentException("Cannot create a single fluid ingredient for the empty fluid");
        }
        return new SingleFluidIngredient(fluid);
    }

    /**
     * Creates a fluid ingredient matching the given tag.
     */
    public static FluidIngredient of(TagKey<Fluid> tag) {
        Objects.requireNonNull(tag, "Tag cannot be null");
        return new TagFluidIngredient(tag);
    }

    /**
     * Creates a fluid ingredient matching the union of the given children.
     *
     * @implNote If there is no element this method will return an empty matching ingredient, and if there is one element, this will return that element.
     */
    public static FluidIngredient ofIngredients(List<FluidIngredient> children) {
        Objects.requireNonNull(children, "Children cannot be null");
        if (children.isEmpty()) {
            return empty();
        } else if (children.size() == 1) {
            return children.getFirst();
        }
        return new CompoundFluidIngredient(List.copyOf(children));
    }

    /**
     * Creates a fluid ingredient matching the given fluids when they also satisfy the given data component predicate.
     */
    public static FluidIngredient of(DataComponentPredicate components, List<Holder<Fluid>> fluids) {
        Objects.requireNonNull(components, "Components cannot be null");
        Objects.requireNonNull(fluids, "Fluids cannot be null");
        return new ComponentFluidIngredient(components, List.copyOf(fluids));
    }

    /**
     * Retrieves the explicitly empty ingredient, which matches nothing but empty stacks of the empty fluid.
     */
    public static FluidIngredient empty() {
        return EmptyFluidIngredient.INSTANCE;
    }

    /**
     * Checks if the given stack's fluid and components match this ingredient, ignoring any amount data.
     */
    @Override
    public abstract boolean test(FluidStack stack);

    /**
     * {@return a list of one-mB stacks representing the fluids this ingredient currently accepts}
     *
     * @apiNote Do not modify any of the returned values.
     */
    public abstract List<FluidStack> getStacks();

    /**
     * Checks if this ingredient matches no fluids.
     */
    public boolean hasNoFluids() {
        return getStacks().isEmpty();
    }

    /**
     * Checks if this is the explicitly empty ingredient. Dynamic tags that currently resolve to no fluids are not explicitly empty.
     */
    public boolean isEmpty() {
        return this == EmptyFluidIngredient.INSTANCE;
    }

    /**
     * Checks if this ingredient applies a data component predicate, which makes quick per-fluid lookup invalid.
     */
    public boolean hasComponentPredicate() {
        return false;
    }

    @Override
    public abstract int hashCode();

    @Override
    public abstract boolean equals(@Nullable Object obj);

    private static final class SingleFluidIngredient extends FluidIngredient {

        private final Holder<Fluid> fluid;

        private SingleFluidIngredient(Holder<Fluid> fluid) {
            this.fluid = fluid;
        }

        @Override
        public boolean test(FluidStack stack) {
            return stack.is(fluid);
        }

        @Override
        public List<FluidStack> getStacks() {
            return List.of(new FluidStack(fluid, 1));
        }

        @Override
        public int hashCode() {
            return fluid.hashCode();
        }

        @Override
        public boolean equals(@Nullable Object obj) {
            return obj == this || obj instanceof SingleFluidIngredient other && fluid.equals(other.fluid);
        }

        private Holder<Fluid> fluid() {
            return fluid;
        }

        @Override
        public String toString() {
            return "fluid " + fluid.getRegisteredName();
        }
    }

    private static final class TagFluidIngredient extends FluidIngredient {

        private final TagKey<Fluid> tag;

        private TagFluidIngredient(TagKey<Fluid> tag) {
            this.tag = tag;
        }

        @Override
        public boolean test(FluidStack stack) {
            return stack.is(tag);
        }

        @Override
        public List<FluidStack> getStacks() {
            List<FluidStack> stacks = new ArrayList<>();
            for (Holder<Fluid> holder : BuiltInRegistries.FLUID.getTagOrEmpty(tag)) {
                stacks.add(new FluidStack(holder, 1));
            }
            return stacks;
        }

        @Override
        public int hashCode() {
            return tag.hashCode();
        }

        @Override
        public boolean equals(@Nullable Object obj) {
            return obj == this || obj instanceof TagFluidIngredient other && tag.equals(other.tag);
        }

        private TagKey<Fluid> tag() {
            return tag;
        }

        @Override
        public String toString() {
            return "tag " + tag.location();
        }
    }

    private static final class CompoundFluidIngredient extends FluidIngredient {

        private final List<FluidIngredient> children;

        private CompoundFluidIngredient(List<FluidIngredient> children) {
            this.children = children;
        }

        @Override
        public boolean test(FluidStack stack) {
            for (FluidIngredient child : children) {
                if (child.test(stack)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public List<FluidStack> getStacks() {
            //Note: Deduplicate by fluid and components, the same way the official fluid ingredient does
            List<FluidStack> stacks = new ArrayList<>();
            for (FluidIngredient child : children) {
                for (FluidStack stack : child.getStacks()) {
                    boolean duplicate = false;
                    for (FluidStack existing : stacks) {
                        if (FluidStack.isSameFluidSameComponents(existing, stack)) {
                            duplicate = true;
                            break;
                        }
                    }
                    if (!duplicate) {
                        stacks.add(stack);
                    }
                }
            }
            return stacks;
        }

        @Override
        public boolean hasComponentPredicate() {
            return children.stream().anyMatch(FluidIngredient::hasComponentPredicate);
        }

        private List<FluidIngredient> children() {
            return children;
        }

        @Override
        public int hashCode() {
            return children.hashCode();
        }

        @Override
        public boolean equals(@Nullable Object obj) {
            return obj == this || obj instanceof CompoundFluidIngredient other && children.equals(other.children);
        }

        @Override
        public String toString() {
            return "compound " + children;
        }
    }

    private static final class ComponentFluidIngredient extends FluidIngredient {

        private final DataComponentPredicate components;
        private final List<Holder<Fluid>> fluids;

        private ComponentFluidIngredient(DataComponentPredicate components, List<Holder<Fluid>> fluids) {
            this.components = components;
            this.fluids = fluids;
        }

        @Override
        public boolean test(FluidStack stack) {
            for (Holder<Fluid> fluid : fluids) {
                if (stack.is(fluid)) {
                    return components.test(stack);
                }
            }
            return false;
        }

        @Override
        public List<FluidStack> getStacks() {
            List<FluidStack> stacks = new ArrayList<>(fluids.size());
            for (Holder<Fluid> fluid : fluids) {
                FluidStack stack = new FluidStack(fluid, 1);
                stack.applyComponents(components.asPatch());
                stacks.add(stack);
            }
            return stacks;
        }

        @Override
        public boolean hasComponentPredicate() {
            return true;
        }

        private DataComponentPredicate components() {
            return components;
        }

        private List<Holder<Fluid>> fluids() {
            return fluids;
        }

        @Override
        public int hashCode() {
            return 31 * components.hashCode() + fluids.hashCode();
        }

        @Override
        public boolean equals(@Nullable Object obj) {
            return obj == this || obj instanceof ComponentFluidIngredient other && components.equals(other.components) && fluids.equals(other.fluids);
        }

        @Override
        public String toString() {
            return "components " + components + " of " + fluids;
        }
    }

    private static final class EmptyFluidIngredient extends FluidIngredient {

        private static final EmptyFluidIngredient INSTANCE = new EmptyFluidIngredient();

        @Override
        public boolean test(FluidStack stack) {
            return stack.isEmpty();
        }

        @Override
        public List<FluidStack> getStacks() {
            return List.of();
        }

        @Override
        public int hashCode() {
            return 0;
        }

        @Override
        public boolean equals(@Nullable Object obj) {
            return obj == this;
        }

        @Override
        public String toString() {
            return "empty fluid ingredient";
        }
    }

    private static final class FluidMapCodec extends MapCodec<FluidIngredient> {

        @Override
        public <T> DataResult<FluidIngredient> decode(DynamicOps<T> ops, MapLike<T> input) {
            T typeValue = input.get(SerializationConstants.TYPE);
            if (typeValue != null) {
                return ops.getStringValue(typeValue).flatMap(type -> {
                    if (COMPONENTS_TYPE.equals(type)) {
                        return decodeComponents(ops, input);
                    } else if (COMPOUND_TYPE.equals(type)) {
                        return decodeCompound(ops, input);
                    }
                    return DataResult.error(() -> "Unknown fluid ingredient type: '" + type + "' is not supported");
                });
            }
            if (input.get(SerializationConstants.FLUID) != null) {
                return BuiltInRegistries.FLUID.holderByNameCodec().decode(ops, Objects.requireNonNull(input.get(SerializationConstants.FLUID)))
                      .map(Pair::getFirst).flatMap(fluid -> fluid.value() == Fluids.EMPTY
                            ? DataResult.error(() -> "Fluid ingredient cannot use the empty fluid") : DataResult.success(FluidIngredient.of(fluid)));
            }
            if (input.get(SerializationConstants.TAG) != null) {
                return TagKey.codec(Registries.FLUID).decode(ops, Objects.requireNonNull(input.get(SerializationConstants.TAG)))
                      .map(Pair::getFirst).map(FluidIngredient::of);
            }
            return DataResult.error(() -> "Fluid ingredient must specify one of 'fluid' or 'tag'");
        }

        private <T> DataResult<FluidIngredient> decodeCompound(DynamicOps<T> ops, MapLike<T> input) {
            T childrenValue = input.get(SerializationConstants.CHILDREN);
            if (childrenValue == null) {
                childrenValue = input.get(SerializationConstants.INGREDIENTS);
            }
            if (childrenValue == null) {
                return DataResult.error(() -> "Compound fluid ingredients must specify 'children'");
            }
            return CODEC.listOf().decode(ops, childrenValue).map(Pair::getFirst).flatMap(children -> children.isEmpty()
                  ? DataResult.error(() -> "Compound fluid ingredients must have at least one child")
                  : DataResult.success(FluidIngredient.ofIngredients(children)));
        }

        private <T> DataResult<FluidIngredient> decodeComponents(DynamicOps<T> ops, MapLike<T> input) {
            T fluidsValue = input.get(FLUIDS);
            if (fluidsValue == null) {
                return DataResult.error(() -> "Component fluid ingredients must specify 'fluids'");
            }
            T componentsValue = input.get(SerializationConstants.COMPONENTS);
            if (componentsValue == null) {
                return DataResult.error(() -> "Component fluid ingredients must specify 'components'");
            }
            return BuiltInRegistries.FLUID.holderByNameCodec().listOf().decode(ops, fluidsValue).map(Pair::getFirst)
                  .flatMap(fluids -> DataComponentPredicate.CODEC.decode(ops, componentsValue).map(Pair::getFirst)
                        .map(components -> FluidIngredient.of(components, fluids)));
        }

        @Override
        public <T> java.util.stream.Stream<T> keys(DynamicOps<T> ops) {
            return java.util.stream.Stream.of(ops.createString(SerializationConstants.FLUID), ops.createString(SerializationConstants.TAG),
                  ops.createString(SerializationConstants.TYPE), ops.createString(SerializationConstants.CHILDREN),
                  ops.createString(SerializationConstants.INGREDIENTS), ops.createString(FLUIDS), ops.createString(SerializationConstants.COMPONENTS));
        }

        @Override
        public <T> RecordBuilder<T> encode(FluidIngredient input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
            if (input instanceof SingleFluidIngredient single) {
                prefix.add(SerializationConstants.FLUID, BuiltInRegistries.FLUID.holderByNameCodec().encodeStart(ops, single.fluid()));
            } else if (input instanceof TagFluidIngredient tagged) {
                prefix.add(SerializationConstants.TAG, TagKey.codec(Registries.FLUID).encodeStart(ops, tagged.tag()));
            } else if (input instanceof ComponentFluidIngredient component) {
                prefix.add(SerializationConstants.TYPE, ops.createString(COMPONENTS_TYPE));
                prefix.add(FLUIDS, BuiltInRegistries.FLUID.holderByNameCodec().listOf().encodeStart(ops, component.fluids()));
                prefix.add(SerializationConstants.COMPONENTS, DataComponentPredicate.CODEC.encodeStart(ops, component.components()));
            } else if (input instanceof CompoundFluidIngredient compound) {
                prefix.add(SerializationConstants.TYPE, ops.createString(COMPOUND_TYPE));
                prefix.add(SerializationConstants.CHILDREN, CODEC.listOf().encodeStart(ops, compound.children()));
            } else {
                prefix.withErrorsFrom(DataResult.error(() -> "Unable to serialize fluid ingredient " + input));
            }
            return prefix;
        }
    }

    private static final class FluidNetworkCodec implements StreamCodec<RegistryFriendlyByteBuf, FluidIngredient> {

        private static final StreamCodec<RegistryFriendlyByteBuf, Holder<Fluid>> HOLDER_STREAM_CODEC = ByteBufCodecs.holderRegistry(Registries.FLUID);

        @Override
        public FluidIngredient decode(RegistryFriendlyByteBuf buffer) {
            return decodeIngredient(buffer);
        }

        private FluidIngredient decodeIngredient(RegistryFriendlyByteBuf buffer) {
            return switch (buffer.readByte()) {
                case 0 -> FluidIngredient.of(HOLDER_STREAM_CODEC.decode(buffer));
                case 1 -> new TagFluidIngredient(TagKey.create(Registries.FLUID, ResourceLocation.STREAM_CODEC.decode(buffer)));
                case 2 -> {
                    int size = ByteBufCodecs.VAR_INT.decode(buffer);
                    if (size < 1) {
                        throw new IllegalStateException("Compound fluid ingredient wire payload must have at least one child");
                    }
                    List<FluidIngredient> children = new ArrayList<>(size);
                    for (int i = 0; i < size; i++) {
                        children.add(decodeIngredient(buffer));
                    }
                    yield new CompoundFluidIngredient(List.copyOf(children));
                }
                case 3 -> {
                    DataComponentPredicate components = DataComponentPredicate.STREAM_CODEC.decode(buffer);
                    int size = ByteBufCodecs.VAR_INT.decode(buffer);
                    List<Holder<Fluid>> fluids = new ArrayList<>(size);
                    for (int i = 0; i < size; i++) {
                        fluids.add(HOLDER_STREAM_CODEC.decode(buffer));
                    }
                    yield new ComponentFluidIngredient(components, List.copyOf(fluids));
                }
                case 4 -> EmptyFluidIngredient.INSTANCE;
                default -> throw new IllegalStateException("Unknown fluid ingredient wire kind");
            };
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, FluidIngredient value) {
            encodeIngredient(buffer, value);
        }

        private void encodeIngredient(RegistryFriendlyByteBuf buffer, FluidIngredient value) {
            if (value instanceof SingleFluidIngredient single) {
                buffer.writeByte(0);
                HOLDER_STREAM_CODEC.encode(buffer, single.fluid());
            } else if (value instanceof TagFluidIngredient tagged) {
                buffer.writeByte(1);
                ResourceLocation.STREAM_CODEC.encode(buffer, tagged.tag().location());
            } else if (value instanceof CompoundFluidIngredient compound) {
                buffer.writeByte(2);
                ByteBufCodecs.VAR_INT.encode(buffer, compound.children().size());
                for (FluidIngredient child : compound.children()) {
                    encodeIngredient(buffer, child);
                }
            } else if (value instanceof ComponentFluidIngredient component) {
                buffer.writeByte(3);
                DataComponentPredicate.STREAM_CODEC.encode(buffer, component.components());
                ByteBufCodecs.VAR_INT.encode(buffer, component.fluids().size());
                for (Holder<Fluid> fluid : component.fluids()) {
                    HOLDER_STREAM_CODEC.encode(buffer, fluid);
                }
            } else if (value instanceof EmptyFluidIngredient) {
                buffer.writeByte(4);
            } else {
                throw new IllegalStateException("Unable to serialize fluid ingredient " + value);
            }
        }
    }
}
