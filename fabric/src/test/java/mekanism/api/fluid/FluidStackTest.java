package mekanism.api.fluid;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@SuppressWarnings("removal")
class FluidStackTest {

    private static HolderLookup.Provider provider;
    private static RegistryAccess registryAccess;

    @BeforeAll
    static void bootstrapVanilla() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        registryAccess = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        provider = registryAccess;
    }

    @Test
    void emptyObservationConstructorsAndCopiesKeepCanonicalBoundaries() {
        assertTrue(FluidStack.EMPTY.isEmpty());
        assertSame(Fluids.EMPTY, FluidStack.EMPTY.getFluid());
        assertSame(Fluids.EMPTY.builtInRegistryHolder(), FluidStack.EMPTY.getFluidHolder());
        assertEquals(0, FluidStack.EMPTY.getAmount());
        assertSame(DataComponentPatch.EMPTY, FluidStack.EMPTY.getComponentsPatch());
        assertTrue(FluidStack.EMPTY.isComponentsPatchEmpty());
        assertSame(FluidStack.EMPTY, FluidStack.EMPTY.copy());
        assertSame(FluidStack.EMPTY, FluidStack.EMPTY.copyWithAmount(10));
        assertSame(FluidStack.EMPTY, FluidStack.EMPTY.copyAndClear());

        FluidStack fluidEmpty = new FluidStack(Fluids.EMPTY, 1_000);
        FluidStack zeroWater = new FluidStack(Fluids.WATER, 0);
        FluidStack negativeWater = new FluidStack(Fluids.WATER.builtInRegistryHolder(), -4);
        for (FluidStack empty : List.of(fluidEmpty, zeroWater, negativeWater)) {
            assertTrue(empty.isEmpty());
            assertNotSame(FluidStack.EMPTY, empty);
            assertSame(Fluids.EMPTY, empty.getFluid());
            assertEquals(0, empty.getAmount());
            assertSame(FluidStack.EMPTY, empty.copy());
            assertSame(FluidStack.EMPTY, empty.copyWithAmount(20));
        }

        DataComponentPatch patch = namedPatch("holder-constructor");
        FluidStack held = new FluidStack(Fluids.WATER.builtInRegistryHolder(), 25, patch);
        assertSame(Fluids.WATER, held.getFluid());
        assertSame(Fluids.WATER.builtInRegistryHolder(), held.getFluidHolder());
        assertEquals(25, held.getAmount());
        assertEquals(patch, held.getComponentsPatch());
        FluidStack copy = held.copy();
        assertNotSame(held, copy);
        assertTrue(FluidStack.matches(held, copy));
        FluidStack resized = held.copyWithAmount(7);
        assertEquals(7, resized.getAmount());
        assertTrue(FluidStack.isSameFluidSameComponents(held, resized));
    }

    @Test
    void rawAmountOperationsPreserveSplitClearLimitAndIntOverflowSemantics() {
        FluidStack stack = new FluidStack(Fluids.WATER, 10);
        FluidStack firstSplit = stack.split(4);
        assertEquals(4, firstSplit.getAmount());
        assertEquals(6, stack.getAmount());

        FluidStack negativeSplit = stack.split(-3);
        assertTrue(negativeSplit.isEmpty());
        assertNotSame(FluidStack.EMPTY, negativeSplit);
        assertEquals(9, stack.getAmount());

        FluidStack clearedCopy = stack.copyAndClear();
        assertEquals(9, clearedCopy.getAmount());
        assertTrue(stack.isEmpty());
        assertNotSame(FluidStack.EMPTY, stack);
        assertSame(FluidStack.EMPTY, stack.copyAndClear());

        FluidStack fullSplitSource = new FluidStack(Fluids.LAVA, 12);
        assertEquals(12, fullSplitSource.split(50).getAmount());
        assertTrue(fullSplitSource.isEmpty());

        FluidStack nonCanonical = new FluidStack(Fluids.WATER, 3).copyWithAmount(0);
        assertTrue(nonCanonical.isEmpty());
        assertNotSame(FluidStack.EMPTY, nonCanonical);
        assertSame(FluidStack.EMPTY, nonCanonical.copy());

        FluidStack overflow = new FluidStack(Fluids.WATER, Integer.MAX_VALUE);
        overflow.grow(1);
        assertTrue(overflow.isEmpty());
        overflow.grow(7);
        assertEquals(7, overflow.getAmount());
        overflow.setAmount(5);
        overflow.shrink(Integer.MIN_VALUE);
        assertTrue(overflow.isEmpty());
        overflow.shrink(-2);
        assertEquals(2, overflow.getAmount());

        FluidStack limited = new FluidStack(Fluids.LAVA, 20);
        limited.limitSize(5);
        assertEquals(5, limited.getAmount());
        limited.limitSize(10);
        assertEquals(5, limited.getAmount());
        limited.limitSize(-1);
        assertTrue(limited.isEmpty());
        limited.limitSize(100);
        assertTrue(limited.isEmpty());
    }

    @Test
    void componentsSupportExplicitMutationHiddenEmptyObservationAndDefensiveCopies() {
        FluidStack stack = new FluidStack(Fluids.WATER, 1_000);
        Component first = Component.literal("first");
        assertEquals(null, stack.set(DataComponents.CUSTOM_NAME, first));
        assertSame(first, stack.get(DataComponents.CUSTOM_NAME));
        assertFalse(stack.getComponentsPatch().isEmpty());
        assertFalse(stack.isComponentsPatchEmpty());
        assertTrue(stack.getComponents() instanceof PatchedDataComponentMap);

        FluidStack copy = stack.copy();
        assertNotSame(stack.getComponents(), copy.getComponents());
        assertEquals(stack.getComponentsPatch(), copy.getComponentsPatch());
        Component replacement = Component.literal("replacement");
        assertSame(first, stack.set(DataComponents.CUSTOM_NAME, replacement));
        assertSame(first, copy.get(DataComponents.CUSTOM_NAME));
        assertSame(replacement, stack.remove(DataComponents.CUSTOM_NAME));
        assertEquals(null, stack.get(DataComponents.CUSTOM_NAME));
        assertSame(first, copy.get(DataComponents.CUSTOM_NAME));

        DataComponentPatch patch = namedPatch("patch-applied");
        stack.applyComponents(patch);
        assertEquals(Component.literal("patch-applied"), stack.get(DataComponents.CUSTOM_NAME));
        PatchedDataComponentMap componentMap = PatchedDataComponentMap.fromPatch(
              DataComponentMap.EMPTY, namedPatch("map-applied"));
        stack.applyComponents(componentMap);
        assertEquals(Component.literal("map-applied"), stack.get(DataComponents.CUSTOM_NAME));

        stack.setAmount(0);
        assertSame(DataComponentPatch.EMPTY, stack.getComponentsPatch());
        assertTrue(stack.isComponentsPatchEmpty());
        assertEquals(Component.literal("map-applied"), stack.getComponents().get(DataComponents.CUSTOM_NAME));
        stack.setAmount(1);
        assertEquals(Component.literal("map-applied"), stack.get(DataComponents.CUSTOM_NAME));
        assertFalse(stack.getComponentsPatch().isEmpty());
    }

    @Test
    void mutableComponentConvenienceOverloadsPreserveDefaultsReturnsAndAbsentCopyClearing() {
        FluidStack stack = new FluidStack(Fluids.WATER, 1_000);
        Supplier<DataComponentType<Component>> customName = () -> DataComponents.CUSTOM_NAME;
        Supplier<DataComponentType<Integer>> maxStackSize = () -> DataComponents.MAX_STACK_SIZE;

        Component suppliedName = Component.literal("supplier-set");
        assertEquals(null, stack.set(customName, suppliedName));
        assertSame(suppliedName, stack.get(DataComponents.CUSTOM_NAME));
        assertSame(suppliedName, stack.remove(customName));
        assertEquals(null, stack.get(DataComponents.CUSTOM_NAME));

        assertEquals(null, stack.update(DataComponents.MAX_STACK_SIZE, 4, 3, Integer::sum));
        assertEquals(7, stack.get(DataComponents.MAX_STACK_SIZE));
        assertEquals(7, stack.update(maxStackSize, 100, 2, (current, factor) -> current * factor));
        assertEquals(14, stack.get(DataComponents.MAX_STACK_SIZE));
        assertEquals(14, stack.remove(DataComponents.MAX_STACK_SIZE));
        assertEquals(null, stack.update(DataComponents.MAX_STACK_SIZE, 5, current -> current + 1));
        assertEquals(6, stack.get(DataComponents.MAX_STACK_SIZE));
        assertEquals(6, stack.update(maxStackSize, 100, current -> current * 2));
        assertEquals(12, stack.get(DataComponents.MAX_STACK_SIZE));

        FluidStack directSource = new FluidStack(Fluids.LAVA, 1);
        Component directName = Component.literal("direct-copy");
        directSource.set(DataComponents.CUSTOM_NAME, directName);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("stale"));
        stack.copyFrom(directSource, DataComponents.CUSTOM_NAME, DataComponents.MAX_STACK_SIZE);
        assertSame(directName, stack.get(DataComponents.CUSTOM_NAME));
        assertEquals(null, stack.get(DataComponents.MAX_STACK_SIZE));

        FluidStack supplierSource = new FluidStack(Fluids.LAVA, 1);
        supplierSource.set(DataComponents.MAX_STACK_SIZE, 48);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("stale-again"));
        stack.set(DataComponents.MAX_STACK_SIZE, 16);
        stack.copyFrom(supplierSource, customName, maxStackSize);
        assertEquals(null, stack.get(DataComponents.CUSTOM_NAME));
        assertEquals(48, stack.get(DataComponents.MAX_STACK_SIZE));
    }

    @Test
    void fluidHolderTagPredicateAndIdentityHelpersKeepTheirDistinctContracts() throws Exception {
        Holder<Fluid> water = Fluids.WATER.builtInRegistryHolder();
        Holder<Fluid> lava = Fluids.LAVA.builtInRegistryHolder();
        FluidStack stack = new FluidStack(water, 10, namedPatch("same"));
        FluidStack sameType = new FluidStack(Fluids.WATER, 99);
        sameType.applyComponents(namedPatch("same"));
        FluidStack otherComponents = new FluidStack(Fluids.WATER, 10);
        FluidStack lavaStack = new FluidStack(lava, 10);

        assertTrue(stack.is(Fluids.WATER));
        assertFalse(stack.is(Fluids.LAVA));
        assertTrue(stack.is(water));
        assertFalse(stack.is(lava));
        assertTrue(stack.is(HolderSet.direct(water, lava)));
        assertFalse(lavaStack.is(HolderSet.direct(water)));
        AtomicReference<Holder<Fluid>> observed = new AtomicReference<>();
        assertTrue(stack.is(holder -> {
            observed.set(holder);
            return holder == water;
        }));
        assertSame(water, observed.get());

        TagKey<Fluid> unloadedTag = TagKey.create(Registries.FLUID,
              ResourceLocation.fromNamespaceAndPath("mekanism", "task034_unloaded"));
        assertEquals(water.is(unloadedTag), stack.is(unloadedTag));
        assertEquals(water.tags().toList(), stack.getTags().toList());

        assertTrue(FluidStack.isSameFluid(stack, sameType));
        assertTrue(FluidStack.isSameFluidSameComponents(stack, sameType));
        assertFalse(FluidStack.isSameFluidSameComponents(stack, otherComponents));
        assertFalse(FluidStack.isSameFluid(stack, lavaStack));
        assertFalse(FluidStack.matches(stack, sameType));
        assertTrue(FluidStack.matches(stack, stack));
        assertEquals(FluidStack.hashFluidAndComponents(stack), FluidStack.hashFluidAndComponents(sameType));
        assertNotEquals(FluidStack.hashFluidAndComponents(stack), FluidStack.hashFluidAndComponents(otherComponents));
        assertEquals(0, FluidStack.hashFluidAndComponents(null));

        FluidStack independent = stack.copy();
        assertNotEquals(stack, independent);
        assertEquals(System.identityHashCode(stack), stack.hashCode());
        assertSame(Object.class, FluidStack.class.getMethod("equals", Object.class).getDeclaringClass());
        assertSame(Object.class, FluidStack.class.getMethod("hashCode").getDeclaringClass());
    }

    @Test
    void jsonCodecsKeepRequiredFixedOptionalLenientFormatsAndErrors() {
        FluidStack water = new FluidStack(Fluids.WATER, 1_000);
        JsonElement golden = json("{\"id\":\"minecraft:water\",\"amount\":1000}");
        assertEquals(golden, FluidStack.CODEC.encodeStart(JsonOps.INSTANCE, water).getOrThrow());
        FluidStack decoded = FluidStack.CODEC.parse(JsonOps.INSTANCE, golden).getOrThrow();
        assertTrue(FluidStack.matches(water, decoded));

        FluidStack named = new FluidStack(Fluids.LAVA, 250);
        named.applyComponents(namedPatch("json-component"));
        JsonElement encodedNamed = FluidStack.CODEC.encodeStart(JsonOps.INSTANCE, named).getOrThrow();
        assertTrue(encodedNamed.getAsJsonObject().getAsJsonObject("components").has("minecraft:custom_name"));
        assertTrue(FluidStack.matches(named, FluidStack.CODEC.parse(JsonOps.INSTANCE, encodedNamed).getOrThrow()));

        Codec<FluidStack> fixed = FluidStack.fixedAmountCodec(333);
        JsonElement fixedGolden = json("{\"id\":\"minecraft:water\"}");
        assertEquals(fixedGolden, fixed.encodeStart(JsonOps.INSTANCE, water).getOrThrow());
        assertEquals(333, fixed.parse(JsonOps.INSTANCE, fixedGolden).getOrThrow().getAmount());

        assertEquals(json("{}"), FluidStack.OPTIONAL_CODEC.encodeStart(JsonOps.INSTANCE, FluidStack.EMPTY).getOrThrow());
        assertSame(FluidStack.EMPTY, FluidStack.OPTIONAL_CODEC.parse(JsonOps.INSTANCE, json("{}")).getOrThrow());
        Codec<FluidStack> lenientField = FluidStack.lenientOtionalFieldOf("fluid").codec();
        assertEquals(json("{}"), lenientField.encodeStart(JsonOps.INSTANCE, FluidStack.EMPTY).getOrThrow());
        assertEquals(json("{\"fluid\":{\"id\":\"minecraft:water\",\"amount\":1000}}"),
              lenientField.encodeStart(JsonOps.INSTANCE, water).getOrThrow());
        assertSame(FluidStack.EMPTY, lenientField.parse(JsonOps.INSTANCE,
              json("{\"fluid\":{\"id\":\"minecraft:water\",\"amount\":0}}")).getOrThrow());

        assertTrue(FluidStack.CODEC.parse(JsonOps.INSTANCE,
              json("{\"id\":\"minecraft:water\"}")).error().orElseThrow().message().contains("amount"));
        assertTrue(FluidStack.CODEC.parse(JsonOps.INSTANCE,
              json("{\"id\":\"minecraft:water\",\"amount\":0}")).error().isPresent());
        DataResult<Holder<Fluid>> emptyFluid = FluidStack.FLUID_NON_EMPTY_CODEC.parse(
              JsonOps.INSTANCE, json("\"minecraft:empty\""));
        assertEquals("Fluid must not be minecraft:empty", emptyFluid.error().orElseThrow().message());
        assertTrue(FluidStack.CODEC.encodeStart(JsonOps.INSTANCE, FluidStack.EMPTY).error().isPresent());
    }

    @Test
    void registryAwareNbtParseSavePrefixOptionalAndErrorsKeepGoldenData() {
        FluidStack water = new FluidStack(Fluids.WATER, 1_000);
        CompoundTag golden = new CompoundTag();
        golden.putString("id", "minecraft:water");
        golden.putInt("amount", 1_000);
        assertEquals(golden, water.save(provider));
        assertTrue(FluidStack.matches(water, FluidStack.parse(provider, golden).orElseThrow()));

        CompoundTag prefix = new CompoundTag();
        prefix.putString("kept", "prefix-data");
        Tag mergedTag = water.save(provider, prefix);
        assertTrue(mergedTag instanceof CompoundTag);
        CompoundTag merged = (CompoundTag) mergedTag;
        assertEquals("prefix-data", merged.getString("kept"));
        assertEquals("minecraft:water", merged.getString("id"));
        assertEquals(1_000, merged.getInt("amount"));
        assertTrue(FluidStack.matches(water, FluidStack.parse(provider, merged).orElseThrow()));

        FluidStack named = new FluidStack(Fluids.LAVA, 81);
        named.applyComponents(namedPatch("nbt-component"));
        CompoundTag namedTag = (CompoundTag) named.save(provider);
        assertTrue(namedTag.getCompound("components").contains("minecraft:custom_name"));
        assertTrue(FluidStack.matches(named, FluidStack.parse(provider, namedTag).orElseThrow()));

        CompoundTag firstEmptySave = (CompoundTag) FluidStack.EMPTY.saveOptional(provider);
        CompoundTag secondEmptySave = (CompoundTag) FluidStack.EMPTY.saveOptional(provider);
        assertTrue(firstEmptySave.isEmpty());
        assertNotSame(firstEmptySave, secondEmptySave);
        assertSame(FluidStack.EMPTY, FluidStack.parseOptional(provider, new CompoundTag()));

        CompoundTag missingAmount = new CompoundTag();
        missingAmount.putString("id", "minecraft:water");
        assertTrue(FluidStack.parse(provider, missingAmount).isEmpty());
        assertSame(FluidStack.EMPTY, FluidStack.parseOptional(provider, missingAmount));
        CompoundTag emptyFluid = new CompoundTag();
        emptyFluid.putString("id", "minecraft:empty");
        emptyFluid.putInt("amount", 1);
        assertTrue(FluidStack.parse(provider, emptyFluid).isEmpty());
        IllegalStateException saveFailure = assertThrows(IllegalStateException.class,
              () -> FluidStack.EMPTY.save(provider));
        assertEquals("Cannot encode empty FluidStack", saveFailure.getMessage());
        IllegalStateException prefixFailure = assertThrows(IllegalStateException.class,
              () -> new FluidStack(Fluids.WATER, -1).save(provider, new CompoundTag()));
        assertEquals("Cannot encode empty FluidStack", prefixFailure.getMessage());
    }

    @Test
    void registryFriendlyStreamCodecsKeepExactBytesCanonicalEmptyAndStrictMessages() {
        FluidStack water = new FluidStack(Fluids.WATER, 1_000);
        assertEquals(2, BuiltInRegistries.FLUID.getId(Fluids.WATER));
        byte[] golden = new byte[]{(byte) 0xE8, 0x07, 0x02, 0x00, 0x00};
        assertArrayEquals(golden, encode(FluidStack.OPTIONAL_STREAM_CODEC, water));
        assertTrue(FluidStack.matches(water, decode(FluidStack.OPTIONAL_STREAM_CODEC, golden)));
        assertArrayEquals(golden, encode(FluidStack.STREAM_CODEC, water));
        assertTrue(FluidStack.matches(water, decode(FluidStack.STREAM_CODEC, golden)));

        assertArrayEquals(new byte[]{0}, encode(FluidStack.OPTIONAL_STREAM_CODEC, FluidStack.EMPTY));
        assertSame(FluidStack.EMPTY, decode(FluidStack.OPTIONAL_STREAM_CODEC, new byte[]{0}));
        assertSame(FluidStack.EMPTY, decode(FluidStack.OPTIONAL_STREAM_CODEC,
              new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x0F}));

        EncoderException encodeFailure = assertThrows(EncoderException.class,
              () -> encode(FluidStack.STREAM_CODEC, FluidStack.EMPTY));
        assertEquals("Empty FluidStack not allowed", encodeFailure.getMessage());
        DecoderException decodeFailure = assertThrows(DecoderException.class,
              () -> decode(FluidStack.STREAM_CODEC, new byte[]{0}));
        assertEquals("Empty FluidStack not allowed", decodeFailure.getMessage());
    }

    @Test
    void vanillaDescriptionsDeprecatedBehaviorToStringAndOmittedNeoForgeSurfaceAreExplicit() throws Exception {
        FluidStack water = new FluidStack(Fluids.WATER, 1_000);
        FluidStack lava = new FluidStack(Fluids.LAVA, 1_000);
        assertEquals("block.minecraft.water", water.getDescriptionId());
        assertEquals("block.minecraft.lava", lava.getDescriptionId());
        assertEquals("block.minecraft.air", FluidStack.EMPTY.getDescriptionId());
        assertEquals("block.minecraft.water", new FluidStack(Fluids.FLOWING_WATER, 1).getDescriptionId());
        assertEquals(Component.translatable("block.minecraft.water"), water.getHoverName());
        assertEquals(water.getHoverName(), water.getDisplayName());
        assertEquals("block.minecraft.water", water.getTranslationKey());
        assertEquals("1000 " + Fluids.WATER, water.toString());
        assertTrue(water.toString().startsWith("1000 net.minecraft.world.level.material.WaterFluid$Source@"));

        FluidStack small = new FluidStack(Fluids.WATER, 400);
        FluidStack same = new FluidStack(Fluids.WATER, 400);
        FluidStack larger = new FluidStack(Fluids.WATER, 900);
        assertTrue(water.isFluidEqual(small));
        assertTrue(FluidStack.areFluidStackTagsEqual(water, small));
        assertTrue(water.containsFluid(small));
        assertFalse(small.containsFluid(water));
        assertTrue(small.isFluidStackIdentical(same));
        assertFalse(small.isFluidStackIdentical(larger));
        small.applyComponents(namedPatch("different"));
        assertFalse(water.isFluidEqual(small));
        assertFalse(FluidStack.areFluidStackTagsEqual(water, small));

        List<Method> methods = Arrays.asList(FluidStack.class.getDeclaredMethods());
        assertFalse(methods.stream().anyMatch(method -> method.getName().equals("getFluidType")));
        assertEquals(1, methods.stream().filter(method -> method.getName().equals("isFluidEqual")).count());
        assertTrue(methods.stream().filter(method -> method.getName().equals("isFluidEqual"))
              .allMatch(method -> Arrays.equals(method.getParameterTypes(), new Class<?>[]{FluidStack.class})));
        assertTrue(methods.stream().filter(method -> method.getName().equals("is"))
              .flatMap(method -> Arrays.stream(method.getParameterTypes()))
              .noneMatch(type -> type.getName().startsWith("net.neoforged.")));
    }

    private static DataComponentPatch namedPatch(String name) {
        PatchedDataComponentMap components = new PatchedDataComponentMap(DataComponentMap.EMPTY);
        components.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return components.asPatch();
    }

    private static JsonElement json(String value) {
        return JsonParser.parseString(value);
    }

    private static byte[] encode(net.minecraft.network.codec.StreamCodec<RegistryFriendlyByteBuf, FluidStack> codec,
          FluidStack stack) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registryAccess);
        try {
            codec.encode(buffer, stack);
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.getBytes(buffer.readerIndex(), bytes);
            return bytes;
        } finally {
            buffer.release();
        }
    }

    private static FluidStack decode(net.minecraft.network.codec.StreamCodec<RegistryFriendlyByteBuf, FluidStack> codec,
          byte[] bytes) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(bytes), registryAccess);
        try {
            FluidStack stack = codec.decode(buffer);
            assertEquals(0, buffer.readableBytes());
            return stack;
        } finally {
            buffer.release();
        }
    }
}
