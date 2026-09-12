package mekanism.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import mekanism.api.fluid.FluidStack;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@SuppressWarnings("removal")
class SerializerHelperTest {

    private static final String API_LOGGER_NAME = "mekanism.api.MekanismAPI";
    private static HolderLookup.Provider provider;

    @BeforeAll
    static void bootstrapVanillaBeforeSerializerHelperInitialization() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        provider = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }

    @Test
    void longCodecsPreservePositiveNonzeroAndLegacyFloatingBoundaries() {
        assertEquals(0L, parseLong(SerializerHelper.POSITIVE_LONG_CODEC, "0"));
        assertEquals(Long.MAX_VALUE, parseLong(SerializerHelper.POSITIVE_LONG_CODEC, Long.toString(Long.MAX_VALUE)));
        assertTrue(SerializerHelper.POSITIVE_LONG_CODEC.parse(JsonOps.INSTANCE, json("-1")).error().isPresent());
        assertEquals(1L, parseLong(SerializerHelper.POSITIVE_NONZERO_LONG_CODEC, "1"));
        assertEquals(Long.MAX_VALUE, parseLong(SerializerHelper.POSITIVE_NONZERO_LONG_CODEC, Long.toString(Long.MAX_VALUE)));
        assertTrue(SerializerHelper.POSITIVE_NONZERO_LONG_CODEC.parse(JsonOps.INSTANCE, json("0")).error().isPresent());

        Codec<Long> legacyPositive = SerializerHelper.POSITIVE_LONG_CODEC_LEGACY;
        assertEquals(0L, parseLong(legacyPositive, "\"0.0\""));
        assertEquals(1L, parseLong(legacyPositive, "\"0.1\""));
        assertEquals(42L, parseLong(legacyPositive, "\"42.999\""));
        assertEquals(1L, parseLong(legacyPositive, "\"1.malformed-tail-is-ignored\""));
        assertEquals(Long.MAX_VALUE, parseLong(legacyPositive, "\"9223372036854775808\""));
        assertEquals(Long.MAX_VALUE, parseLong(legacyPositive, "\"18446744073709551615\""));
        assertTrue(legacyPositive.parse(JsonOps.INSTANCE, json("\"18446744073709551616\"")).error().isPresent());
        assertTrue(legacyPositive.parse(JsonOps.INSTANCE, json("\"-1\"")).error().isPresent());
        assertTrue(legacyPositive.parse(JsonOps.INSTANCE, json("\"0.1.2\"")).error().isPresent());

        Codec<Long> legacyNonzero = SerializerHelper.POSITIVE_NONZERO_LONG_CODEC_LEGACY;
        assertEquals(1L, parseLong(legacyNonzero, "\"0.1\""));
        assertEquals("Failed to parse either. First: Not a number: \"0.0\"; Second: Value must be greater than zero",
              legacyNonzero.parse(JsonOps.INSTANCE, json("\"0.0\"")).error().orElseThrow().message());
        assertEquals(json("0"), legacyPositive.encodeStart(JsonOps.INSTANCE, 0L).getOrThrow());
        assertEquals(json(Long.toString(Long.MAX_VALUE)),
              legacyPositive.encodeStart(JsonOps.INSTANCE, Long.MAX_VALUE).getOrThrow());
        assertEquals(json("1"), legacyNonzero.encodeStart(JsonOps.INSTANCE, 1L).getOrThrow());
        assertTrue(legacyNonzero.encodeStart(JsonOps.INSTANCE, 0L).error().isPresent());
    }

    @Test
    void itemCodecsPreserveStrictOptionalSingleLenientAndExactDiagnostics() {
        ItemStack diamonds = new ItemStack(Items.DIAMOND, 4);
        JsonElement strictJson = ItemStack.CODEC.encodeStart(JsonOps.INSTANCE, diamonds).getOrThrow();
        ItemStack strictRoundTrip = ItemStack.CODEC.parse(JsonOps.INSTANCE, strictJson).getOrThrow();
        assertEquals(4, strictRoundTrip.getCount());
        assertSame(Items.DIAMOND, strictRoundTrip.getItem());

        assertEquals(json("{}"), ItemStack.OPTIONAL_CODEC.encodeStart(JsonOps.INSTANCE, ItemStack.EMPTY).getOrThrow());
        assertSame(ItemStack.EMPTY, ItemStack.OPTIONAL_CODEC.parse(JsonOps.INSTANCE, json("{}")).getOrThrow());
        assertEquals(json("{}"), SerializerHelper.OPTIONAL_SINGLE_ITEM_CODEC.encodeStart(JsonOps.INSTANCE, ItemStack.EMPTY).getOrThrow());
        assertSame(ItemStack.EMPTY, SerializerHelper.OPTIONAL_SINGLE_ITEM_CODEC.parse(JsonOps.INSTANCE, json("{}")).getOrThrow());
        ItemStack single = SerializerHelper.OPTIONAL_SINGLE_ITEM_CODEC.parse(
              JsonOps.INSTANCE, json("{\"id\":\"minecraft:diamond\"}")).getOrThrow();
        assertEquals(1, single.getCount());
        assertSame(Items.DIAMOND, single.getItem());
        ItemStack ignoredCount = SerializerHelper.OPTIONAL_SINGLE_ITEM_CODEC.parse(
              JsonOps.INSTANCE, json("{\"id\":\"minecraft:diamond\",\"count\":2}")).getOrThrow();
        assertEquals(1, ignoredCount.getCount());
        assertEquals(json("{\"id\":\"minecraft:diamond\"}"),
              SerializerHelper.OPTIONAL_SINGLE_ITEM_CODEC.encodeStart(JsonOps.INSTANCE, diamonds).getOrThrow());
        assertEquals(4, diamonds.getCount());

        List<LogEvent> events = captureApiErrors(() -> {
            assertSame(ItemStack.EMPTY, SerializerHelper.LENIENT_OPTIONAL_STACK_CODEC.parse(
                  JsonOps.INSTANCE, json("{\"id\":\"missing:stack\"}")).getOrThrow());
            assertSame(ItemStack.EMPTY, SerializerHelper.LENIENT_OPTIONAL_SINGLE_ITEM_CODEC.parse(
                  JsonOps.INSTANCE, json("{\"id\":\"missing:single\"}")).getOrThrow());
            assertSame(ItemStack.EMPTY, SerializerHelper.LENIENT_OVERSIZED_ITEM_OPTIONAL_CODEC.parse(
                  JsonOps.INSTANCE, json("{\"id\":\"missing:oversized\"}")).getOrThrow());
        });
        assertEquals(3, events.size());
        assertApiError(events.get(0), "Tried to load invalid item: 'Unknown registry key in ResourceKey[minecraft:root / minecraft:item]: missing:stack'");
        assertApiError(events.get(1), "Tried to load invalid item: 'Unknown registry key in ResourceKey[minecraft:root / minecraft:item]: missing:single'");
        assertApiError(events.get(2), "Tried to load invalid item: 'Unknown registry key in ResourceKey[minecraft:root / minecraft:item]: missing:oversized'");
    }

    @Test
    void oversizedItemPersistencePreservesCountsComponentsDefaultsCopiesAndFailures() {
        ItemStack oversized = new ItemStack(Items.DIAMOND, 500);
        oversized.set(DataComponents.CUSTOM_NAME, Component.literal("task035-oversized"));
        CompoundTag saved = (CompoundTag) SerializerHelper.saveOversized(provider, oversized);
        assertEquals("minecraft:diamond", saved.getString(SerializationConstants.ID));
        assertEquals(500, saved.getInt(SerializationConstants.COUNT));
        assertTrue(saved.getCompound(SerializationConstants.COMPONENTS).contains("minecraft:custom_name"));

        ItemStack parsed = SerializerHelper.parseOversized(provider, saved).orElseThrow();
        assertEquals(500, parsed.getCount());
        assertEquals(Component.literal("task035-oversized"), parsed.get(DataComponents.CUSTOM_NAME));
        assertNotSame(oversized, parsed);
        parsed.setCount(3);
        parsed.set(DataComponents.CUSTOM_NAME, Component.literal("mutated-copy"));
        assertEquals(500, oversized.getCount());
        assertEquals(Component.literal("task035-oversized"), oversized.get(DataComponents.CUSTOM_NAME));

        CompoundTag defaults = new CompoundTag();
        defaults.putString(SerializationConstants.ID, "minecraft:gold_ingot");
        ItemStack defaulted = SerializerHelper.parseOversized(provider, defaults).orElseThrow();
        assertSame(Items.GOLD_INGOT, defaulted.getItem());
        assertEquals(1, defaulted.getCount());
        assertTrue(defaulted.getComponentsPatch().isEmpty());
        assertSame(ItemStack.EMPTY, SerializerHelper.parseOversizedOptional(provider, new CompoundTag()));
        assertEquals(json("{}"), SerializerHelper.OVERSIZED_ITEM_OPTIONAL_CODEC.encodeStart(
              JsonOps.INSTANCE, ItemStack.EMPTY).getOrThrow());
        assertSame(ItemStack.EMPTY, SerializerHelper.OVERSIZED_ITEM_OPTIONAL_CODEC.parse(
              JsonOps.INSTANCE, json("{}")).getOrThrow());

        IllegalStateException saveFailure = assertThrows(IllegalStateException.class,
              () -> SerializerHelper.saveOversized(provider, ItemStack.EMPTY));
        assertEquals("Cannot encode empty ItemStack", saveFailure.getMessage());
        assertTrue(SerializerHelper.OVERSIZED_ITEM_CODEC.encodeStart(JsonOps.INSTANCE, ItemStack.EMPTY).error().isPresent());
        CompoundTag invalidCount = new CompoundTag();
        invalidCount.putString(SerializationConstants.ID, "minecraft:diamond");
        invalidCount.putInt(SerializationConstants.COUNT, 0);
        assertEquals(1, SerializerHelper.parseOversized(provider, invalidCount).orElseThrow().getCount());
        assertEquals(1, SerializerHelper.parseOversizedOptional(provider, invalidCount).getCount());

        CompoundTag malformed = new CompoundTag();
        malformed.putString(SerializationConstants.ID, "missing:oversized_nbt");
        List<LogEvent> events = captureApiErrors(() -> {
            assertTrue(SerializerHelper.parseOversized(provider, malformed).isEmpty());
            assertSame(ItemStack.EMPTY, SerializerHelper.parseOversizedOptional(provider, malformed));
        });
        assertEquals(2, events.size());
        for (LogEvent event : events) {
            assertApiError(event, "Tried to load invalid item: 'Unknown registry key in ResourceKey[minecraft:root / minecraft:item]: missing:oversized_nbt'");
        }
    }

    @Test
    void fluidLenientCodecUsesProjectValueComponentsCanonicalEmptyAndExactDiagnostic() {
        FluidStack namedWater = new FluidStack(Fluids.WATER.builtInRegistryHolder(), 1_000, namedPatch("task035-fluid"));
        JsonElement encoded = SerializerHelper.LENIENT_OPTIONAL_FLUID_CODEC.encodeStart(
              JsonOps.INSTANCE, namedWater).getOrThrow();
        assertTrue(encoded.getAsJsonObject().getAsJsonObject(SerializationConstants.COMPONENTS)
              .has("minecraft:custom_name"));
        FluidStack decoded = SerializerHelper.LENIENT_OPTIONAL_FLUID_CODEC.parse(
              JsonOps.INSTANCE, encoded).getOrThrow();
        assertTrue(FluidStack.matches(namedWater, decoded));
        assertNotSame(namedWater, decoded);
        assertSame(FluidStack.EMPTY, SerializerHelper.LENIENT_OPTIONAL_FLUID_CODEC.parse(
              JsonOps.INSTANCE, json("{}")).getOrThrow());

        List<LogEvent> events = captureApiErrors(() -> assertSame(FluidStack.EMPTY,
              SerializerHelper.LENIENT_OPTIONAL_FLUID_CODEC.parse(JsonOps.INSTANCE,
                    json("{\"id\":\"minecraft:water\",\"amount\":0}")).getOrThrow()));
        assertEquals(1, events.size());
        assertApiError(events.getFirst(), "Tried to load invalid fluid: 'Value must be positive: 0'");
    }

    @Test
    void conditionalMapCodecsKeepPresenceTruthTablesErrorsAndStringifyFallback() {
        MapCodec<Optional<Integer>> dependentField = Codec.INT.optionalFieldOf("dependent");
        Codec<Pair<Optional<String>, Optional<Integer>>> dependentCodec = RecordCodecBuilder.create(instance -> {
            RecordCodecBuilder<Pair<Optional<String>, Optional<Integer>>, Optional<String>> primary =
                  Codec.STRING.optionalFieldOf("primary").forGetter(Pair::getFirst);
            return instance.group(primary, SerializerHelper.dependentOptionality(primary, dependentField, Pair::getSecond))
                  .apply(instance, Pair::of);
        });
        assertEquals(Pair.of(Optional.empty(), Optional.empty()), parsePair(dependentCodec, "{}"));
        assertEquals(Pair.of(Optional.empty(), Optional.of(2)), parsePair(dependentCodec, "{\"dependent\":2}"));
        assertEquals(Pair.of(Optional.of("p"), Optional.of(2)),
              parsePair(dependentCodec, "{\"primary\":\"p\",\"dependent\":2}"));
        assertEquals("Missing value", error(dependentCodec, "{\"primary\":\"p\"}"));
        assertTrue(error(dependentCodec, "{\"primary\":\"p\",\"dependent\":\"bad\"}")
              .contains("Not a number"));

        Codec<Pair<Optional<String>, Optional<Integer>>> oneRequiredCodec = RecordCodecBuilder.create(instance -> {
            RecordCodecBuilder<Pair<Optional<String>, Optional<Integer>>, Optional<String>> other =
                  Codec.STRING.optionalFieldOf("other").forGetter(Pair::getFirst);
            return instance.group(other, SerializerHelper.oneRequired(other, dependentField, Pair::getSecond))
                  .apply(instance, Pair::of);
        });
        assertEquals("dependent is required", error(oneRequiredCodec, "{}"));
        assertEquals(Pair.of(Optional.of("o"), Optional.empty()),
              parsePair(oneRequiredCodec, "{\"other\":\"o\"}"));
        assertEquals(Pair.of(Optional.empty(), Optional.of(2)), parsePair(oneRequiredCodec, "{\"dependent\":2}"));
        assertEquals(Pair.of(Optional.of("o"), Optional.of(2)),
              parsePair(oneRequiredCodec, "{\"other\":\"o\",\"dependent\":2}"));
        assertTrue(error(oneRequiredCodec, "{\"other\":\"o\",\"dependent\":\"bad\"}")
              .contains("Not a number"));

        assertEquals("\"codec-success\"", SerializerHelper.stringify(Codec.STRING, "codec-success"));
        Codec<String> rejecting = Codec.STRING.validate(value -> DataResult.error(() -> "encode rejected"));
        assertEquals("fallback-value", SerializerHelper.stringify(rejecting, "fallback-value"));
    }

    private static long parseLong(Codec<Long> codec, String json) {
        return codec.parse(JsonOps.INSTANCE, json(json)).getOrThrow();
    }

    private static Pair<Optional<String>, Optional<Integer>> parsePair(
          Codec<Pair<Optional<String>, Optional<Integer>>> codec, String json) {
        return codec.parse(JsonOps.INSTANCE, json(json)).getOrThrow();
    }

    private static String error(Codec<?> codec, String json) {
        return codec.parse(JsonOps.INSTANCE, json(json)).error().orElseThrow().message();
    }

    private static DataComponentPatch namedPatch(String name) {
        PatchedDataComponentMap components = new PatchedDataComponentMap(DataComponentMap.EMPTY);
        components.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return components.asPatch();
    }

    private static JsonElement json(String value) {
        return JsonParser.parseString(value);
    }

    private static List<LogEvent> captureApiErrors(Runnable action) {
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        Logger logger = context.getLogger(API_LOGGER_NAME);
        List<LogEvent> events = new ArrayList<>();
        AbstractAppender appender = new AbstractAppender("SerializerHelperTest-" + System.nanoTime(), null, null,
              false, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                events.add(event.toImmutable());
            }
        };
        appender.start();
        logger.addAppender(appender);
        try {
            action.run();
        } finally {
            logger.removeAppender(appender);
            appender.stop();
        }
        return events;
    }

    private static void assertApiError(LogEvent event, String message) {
        assertEquals(API_LOGGER_NAME, event.getLoggerName());
        assertEquals(Level.ERROR, event.getLevel());
        assertEquals(message, event.getMessage().getFormattedMessage());
    }
}
