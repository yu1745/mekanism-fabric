package mekanism.api.fluid;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;
import mekanism.api.annotations.NothingNullByDefault;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentHolder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.Nullable;

/**
 * Mekanism's platform-neutral fluid value. Amounts are mutable integer millibuckets;
 * Fabric Transfer values and units are intentionally confined to the Fabric gateway.
 */
@NothingNullByDefault
public final class FluidStack implements DataComponentHolder {

    public static final FluidStack EMPTY = new FluidStack();

    public static final Codec<Holder<Fluid>> FLUID_NON_EMPTY_CODEC = BuiltInRegistries.FLUID.holderByNameCodec()
          .validate(holder -> holder.is(Fluids.EMPTY.builtInRegistryHolder())
                ? DataResult.error(() -> "Fluid must not be minecraft:empty")
                : DataResult.success(holder));

    public static final Codec<FluidStack> CODEC = Codec.lazyInitialized(FluidStack::createCodec);

    public static final Codec<FluidStack> OPTIONAL_CODEC = ExtraCodecs.optionalEmptyMap(CODEC)
          .xmap(optional -> optional.orElse(EMPTY), stack -> stack.isEmpty() ? Optional.empty() : Optional.of(stack));

    private static final StreamCodec<RegistryFriendlyByteBuf, Holder<Fluid>> FLUID_STREAM_CODEC =
          ByteBufCodecs.holderRegistry(Registries.FLUID);

    public static final StreamCodec<RegistryFriendlyByteBuf, FluidStack> OPTIONAL_STREAM_CODEC =
          StreamCodec.of(FluidStack::encodeOptional, FluidStack::decodeOptional);

    public static final StreamCodec<RegistryFriendlyByteBuf, FluidStack> STREAM_CODEC =
          StreamCodec.of(FluidStack::encodeStrict, FluidStack::decodeStrict);

    @Nullable
    private final Fluid fluid;
    private int amount;
    private final PatchedDataComponentMap components;

    private FluidStack() {
        fluid = null;
        components = new PatchedDataComponentMap(DataComponentMap.EMPTY);
    }

    public FluidStack(Holder<Fluid> fluid, int amount, DataComponentPatch patch) {
        this(fluid.value(), amount, PatchedDataComponentMap.fromPatch(DataComponentMap.EMPTY, patch));
    }

    public FluidStack(Holder<Fluid> fluid, int amount) {
        this(fluid.value(), amount);
    }

    public FluidStack(Fluid fluid, int amount) {
        this(fluid, amount, new PatchedDataComponentMap(DataComponentMap.EMPTY));
    }

    private FluidStack(Fluid fluid, int amount, PatchedDataComponentMap components) {
        this.fluid = fluid;
        this.amount = amount;
        this.components = components;
    }

    private static Codec<FluidStack> createCodec() {
        return RecordCodecBuilder.create(instance -> instance.group(
              FLUID_NON_EMPTY_CODEC.fieldOf("id").forGetter(FluidStack::getFluidHolder),
              ExtraCodecs.POSITIVE_INT.fieldOf("amount").forGetter(FluidStack::getAmount),
              DataComponentPatch.CODEC.optionalFieldOf("components", DataComponentPatch.EMPTY)
                    .forGetter(stack -> stack.components.asPatch())
        ).apply(instance, FluidStack::new));
    }

    public static Codec<FluidStack> fixedAmountCodec(int amount) {
        return Codec.lazyInitialized(() -> RecordCodecBuilder.create(instance -> instance.group(
              FLUID_NON_EMPTY_CODEC.fieldOf("id").forGetter(FluidStack::getFluidHolder),
              DataComponentPatch.CODEC.optionalFieldOf("components", DataComponentPatch.EMPTY)
                    .forGetter(stack -> stack.components.asPatch())
        ).apply(instance, (holder, patch) -> new FluidStack(holder, amount, patch))));
    }

    private static FluidStack decodeOptional(RegistryFriendlyByteBuf buffer) {
        int decodedAmount = buffer.readVarInt();
        if (decodedAmount <= 0) {
            return EMPTY;
        }
        Holder<Fluid> decodedFluid = FLUID_STREAM_CODEC.decode(buffer);
        DataComponentPatch decodedPatch = DataComponentPatch.STREAM_CODEC.decode(buffer);
        return new FluidStack(decodedFluid, decodedAmount, decodedPatch);
    }

    private static void encodeOptional(RegistryFriendlyByteBuf buffer, FluidStack stack) {
        if (stack.isEmpty()) {
            buffer.writeVarInt(0);
            return;
        }
        buffer.writeVarInt(stack.getAmount());
        FLUID_STREAM_CODEC.encode(buffer, stack.getFluidHolder());
        DataComponentPatch.STREAM_CODEC.encode(buffer, stack.components.asPatch());
    }

    private static FluidStack decodeStrict(RegistryFriendlyByteBuf buffer) {
        FluidStack decoded = decodeOptional(buffer);
        if (decoded.isEmpty()) {
            throw new DecoderException("Empty FluidStack not allowed");
        }
        return decoded;
    }

    private static void encodeStrict(RegistryFriendlyByteBuf buffer, FluidStack stack) {
        if (stack.isEmpty()) {
            throw new EncoderException("Empty FluidStack not allowed");
        }
        encodeOptional(buffer, stack);
    }

    public static Optional<FluidStack> parse(HolderLookup.Provider lookupProvider, Tag tag) {
        return CODEC.parse(lookupProvider.createSerializationContext(NbtOps.INSTANCE), tag)
              .resultOrPartial(ignored -> {
              });
    }

    public static FluidStack parseOptional(HolderLookup.Provider lookupProvider, CompoundTag tag) {
        return tag.isEmpty() ? EMPTY : parse(lookupProvider, tag).orElse(EMPTY);
    }

    public Tag save(HolderLookup.Provider lookupProvider, Tag prefix) {
        if (isEmpty()) {
            throw new IllegalStateException("Cannot encode empty FluidStack");
        }
        return CODEC.encode(this, lookupProvider.createSerializationContext(NbtOps.INSTANCE), prefix).getOrThrow();
    }

    public Tag save(HolderLookup.Provider lookupProvider) {
        if (isEmpty()) {
            throw new IllegalStateException("Cannot encode empty FluidStack");
        }
        return CODEC.encodeStart(lookupProvider.createSerializationContext(NbtOps.INSTANCE), this).getOrThrow();
    }

    public Tag saveOptional(HolderLookup.Provider lookupProvider) {
        return isEmpty() ? new CompoundTag() : save(lookupProvider, new CompoundTag());
    }

    public boolean isEmpty() {
        return this == EMPTY || fluid == Fluids.EMPTY || amount <= 0;
    }

    public Fluid getFluid() {
        return isEmpty() ? Fluids.EMPTY : fluid;
    }

    public Holder<Fluid> getFluidHolder() {
        return getFluid().builtInRegistryHolder();
    }

    public boolean is(TagKey<Fluid> tag) {
        return getFluidHolder().is(tag);
    }

    public boolean is(Fluid otherFluid) {
        return getFluid() == otherFluid;
    }

    public boolean is(Holder<Fluid> holder) {
        return is(holder.value());
    }

    public boolean is(HolderSet<Fluid> holderSet) {
        return holderSet.contains(getFluidHolder());
    }

    public boolean is(Predicate<Holder<Fluid>> holderPredicate) {
        return holderPredicate.test(getFluidHolder());
    }

    public Stream<TagKey<Fluid>> getTags() {
        return getFluidHolder().tags();
    }

    public FluidStack copy() {
        return isEmpty() ? EMPTY : new FluidStack(fluid, amount, components.copy());
    }

    public FluidStack copyWithAmount(int amount) {
        if (isEmpty()) {
            return EMPTY;
        }
        FluidStack copy = copy();
        copy.setAmount(amount);
        return copy;
    }

    public FluidStack copyAndClear() {
        if (isEmpty()) {
            return EMPTY;
        }
        FluidStack copy = copy();
        setAmount(0);
        return copy;
    }

    public FluidStack split(int amount) {
        int splitAmount = Math.min(amount, this.amount);
        FluidStack split = copyWithAmount(splitAmount);
        shrink(splitAmount);
        return split;
    }

    public int getAmount() {
        return isEmpty() ? 0 : amount;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }

    public void limitSize(int amount) {
        if (!isEmpty() && getAmount() > amount) {
            setAmount(amount);
        }
    }

    public void grow(int addedAmount) {
        setAmount(getAmount() + addedAmount);
    }

    public void shrink(int removedAmount) {
        grow(-removedAmount);
    }

    @Override
    public PatchedDataComponentMap getComponents() {
        return components;
    }

    public DataComponentPatch getComponentsPatch() {
        return isEmpty() ? DataComponentPatch.EMPTY : components.asPatch();
    }

    public boolean isComponentsPatchEmpty() {
        return isEmpty() || components.asPatch().isEmpty();
    }

    @Nullable
    public <T> T set(DataComponentType<? super T> type, @Nullable T component) {
        return components.set(type, component);
    }

    @Nullable
    public <T> T remove(DataComponentType<? extends T> type) {
        return components.remove(type);
    }

    public void applyComponents(DataComponentPatch patch) {
        components.applyPatch(patch);
    }

    public void applyComponents(DataComponentMap componentMap) {
        components.setAll(componentMap);
    }

    public static boolean matches(FluidStack first, FluidStack second) {
        return first == second || first.getAmount() == second.getAmount() && isSameFluidSameComponents(first, second);
    }

    public static boolean isSameFluid(FluidStack first, FluidStack second) {
        return first.is(second.getFluid());
    }

    public static boolean isSameFluidSameComponents(FluidStack first, FluidStack second) {
        if (!first.is(second.getFluid())) {
            return false;
        }
        return first.isEmpty() && second.isEmpty() || Objects.equals(first.components, second.components);
    }

    public static MapCodec<FluidStack> lenientOtionalFieldOf(String fieldName) {
        return CODEC.lenientOptionalFieldOf(fieldName)
              .xmap(optional -> optional.orElse(EMPTY), stack -> stack.isEmpty() ? Optional.empty() : Optional.of(stack));
    }

    public static int hashFluidAndComponents(@Nullable FluidStack stack) {
        if (stack == null) {
            return 0;
        }
        int hash = 31 + stack.getFluid().hashCode();
        return 31 * hash + stack.getComponents().hashCode();
    }

    public String getDescriptionId() {
        return Util.makeDescriptionId("fluid", BuiltInRegistries.FLUID.getKey(getFluid()));
    }

    public Component getHoverName() {
        return Component.translatable(getDescriptionId());
    }

    @Override
    public String toString() {
        return getAmount() + " " + getFluid();
    }

    @Deprecated(forRemoval = true, since = "1.20.5")
    public Component getDisplayName() {
        return getHoverName();
    }

    @Deprecated(forRemoval = true, since = "1.20.5")
    public String getTranslationKey() {
        return getDescriptionId();
    }

    @Deprecated(forRemoval = true, since = "1.20.5")
    public boolean isFluidEqual(FluidStack other) {
        return isSameFluidSameComponents(this, other);
    }

    @Deprecated(forRemoval = true, since = "1.20.5")
    public static boolean areFluidStackTagsEqual(FluidStack first, FluidStack second) {
        return Objects.equals(first.components, second.components);
    }

    @Deprecated(forRemoval = true, since = "1.20.5")
    public boolean containsFluid(FluidStack other) {
        return isFluidEqual(other) && amount >= other.amount;
    }

    @Deprecated(forRemoval = true, since = "1.20.5")
    public boolean isFluidStackIdentical(FluidStack other) {
        return matches(this, other);
    }
}
