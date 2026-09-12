package mekanism.api.gear.config;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.List;
import mekanism.api.text.IHasTextComponent;
import mekanism.api.text.IHasTextComponent.IHasEnumNameTextComponent;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.StringRepresentable;
import org.junit.jupiter.api.Test;

class ModuleConfigTest {

    private static final ResourceLocation NAME = id("test", "config");
    private static final Codec<TestMode> MODE_CODEC = StringRepresentable.fromEnum(TestMode::values);
    private static final StreamCodec<ByteBuf, TestMode> MODE_STREAM_CODEC = ByteBufCodecs.idMapper(
          ByIdMap.continuous(TestMode::ordinal, TestMode.values(), ByIdMap.OutOfBoundsStrategy.WRAP), TestMode::ordinal
    );

    private enum TestMode implements IHasTextComponent, StringRepresentable {
        ALPHA("alpha"),
        BETA("beta"),
        GAMMA("gamma");

        private final String serializedName;
        private final Component text = Component.literal(name().toLowerCase());

        TestMode(String serializedName) {
            this.serializedName = serializedName;
        }

        @Override
        public Component getTextComponent() {
            return text;
        }

        @Override
        public String getSerializedName() {
            return serializedName;
        }
    }

    private record TextValue(Component text) implements IHasTextComponent {
        @Override
        public Component getTextComponent() {
            return text;
        }
    }

    private record EnumTextValue(Component text) implements IHasEnumNameTextComponent {
        @Override
        public Component getTextComponent() {
            return text;
        }
    }

    private record IconValue(Component text, ResourceLocation icon) implements IHasModeIcon {
        @Override
        public Component getTextComponent() {
            return text;
        }

        @Override
        public ResourceLocation getModeIcon() {
            return icon;
        }
    }

    @Test
    void moduleConfigKeysAndLegacyDataNamesStayExact() {
        assertEquals(id("mekanism", "enabled"), ModuleConfig.ENABLED_KEY);
        assertEquals(id("mekanism", "handle_mode_change"), ModuleConfig.HANDLES_MODE_CHANGE_KEY);
        assertEquals(id("mekanism", "render_hud"), ModuleConfig.RENDER_HUD_KEY);

        ModuleBooleanConfig legacy = ModuleBooleanConfig.CODEC.parse(JsonOps.INSTANCE,
              json("{\"name\":\"minecraft:legacy\",\"value\":true}")).getOrThrow();
        assertEquals(id("mekanism", "legacy"), legacy.name());
        ModuleBooleanConfig external = ModuleBooleanConfig.CODEC.parse(JsonOps.INSTANCE,
              json("{\"name\":\"addon:kept\",\"value\":false}")).getOrThrow();
        assertEquals(id("addon", "kept"), external.name());

        ModuleBooleanConfig explicitMinecraft = ModuleBooleanConfig.create(id("minecraft", "encoded"), true);
        assertEquals(json("{\"name\":\"minecraft:encoded\",\"value\":true}"),
              ModuleBooleanConfig.CODEC.encodeStart(JsonOps.INSTANCE, explicitMinecraft).getOrThrow());
        assertEquals(json("{\"name\":\"mekanism:legacy\",\"value\":true}"),
              ModuleBooleanConfig.CODEC.encodeStart(JsonOps.INSTANCE, legacy).getOrThrow());
    }

    @Test
    void booleanCodecsAndImmutableUpdatesPreserveNameValueAndWireShape() {
        ResourceLocation name = id("test", "b");
        ModuleBooleanConfig config = ModuleBooleanConfig.create(name, true);
        assertSame(name, config.name());
        assertEquals(Boolean.TRUE, config.get());
        assertEquals(json("{\"name\":\"test:b\",\"value\":true}"),
              ModuleBooleanConfig.CODEC.encodeStart(JsonOps.INSTANCE, config).getOrThrow());
        assertEquals(config, ModuleBooleanConfig.CODEC.parse(JsonOps.INSTANCE,
              json("{\"name\":\"test:b\",\"value\":true}")).getOrThrow());

        ByteBuf full = Unpooled.buffer();
        try {
            ModuleBooleanConfig.STREAM_CODEC.encode(full, config);
            assertArrayEquals(new byte[]{6, 't', 'e', 's', 't', ':', 'b', 1}, bytes(full));
            assertEquals(config, ModuleBooleanConfig.STREAM_CODEC.decode(full));
        } finally {
            full.release();
        }

        ResourceLocation boundName = id("bound", "boolean");
        StreamCodec<ByteBuf, ModuleConfig<Boolean>> named = config.namedStreamCodec(boundName);
        ByteBuf bound = Unpooled.buffer();
        try {
            named.encode(bound, config);
            assertArrayEquals(new byte[]{1}, bytes(bound));
            ModuleConfig<Boolean> decoded = named.decode(bound);
            assertSame(boundName, decoded.name());
            assertEquals(Boolean.TRUE, decoded.get());
        } finally {
            bound.release();
        }

        assertSame(config, config.with(Boolean.TRUE));
        ModuleBooleanConfig changed = config.with(Boolean.FALSE);
        assertNotSame(config, changed);
        assertSame(name, changed.name());
        assertEquals(Boolean.FALSE, changed.get());
        assertThrows(NullPointerException.class, () -> config.with(null));
    }

    @Test
    void colorRgbArgbCodecsSanitizationAndUpdatesStayExact() {
        ResourceLocation name = id("test", "c");
        ModuleColorConfig argb = ModuleColorConfig.argb(name, 0x80123456);
        ModuleColorConfig rgb = ModuleColorConfig.rgb(name, 0x00123456);
        assertTrue(argb.supportsAlpha());
        assertFalse(rgb.supportsAlpha());
        assertEquals(0x80123456, argb.get());
        assertEquals(0xFF123456, rgb.get());
        assertEquals(-1, ModuleColorConfig.argb(name).get());
        assertEquals(-1, ModuleColorConfig.rgb(name).get());

        JsonObject argbJson = ModuleColorConfig.ARGB_CODEC.encodeStart(JsonOps.INSTANCE, argb).getOrThrow().getAsJsonObject();
        assertEquals("test:c", argbJson.get("name").getAsString());
        assertEquals(0x80123456, argbJson.get("value").getAsInt());
        assertEquals(argb, ModuleColorConfig.ARGB_CODEC.parse(JsonOps.INSTANCE, argbJson).getOrThrow());
        ModuleColorConfig decodedRgb = ModuleColorConfig.RGB_CODEC.parse(JsonOps.INSTANCE,
              json("{\"name\":\"test:c\",\"value\":1193046}")).getOrThrow();
        assertEquals(rgb, decodedRgb);
        assertEquals(0xFF123456, decodedRgb.get());
        JsonElement rgbJson = ModuleColorConfig.RGB_CODEC.encodeStart(JsonOps.INSTANCE, rgb).getOrThrow();
        assertEquals(json("{\"name\":\"test:c\",\"value\":-15584170}"), rgbJson);
        assertEquals(rgb, ModuleColorConfig.RGB_CODEC.parse(JsonOps.INSTANCE, rgbJson).getOrThrow());

        ByteBuf argbFull = Unpooled.buffer();
        try {
            ModuleColorConfig.ARGB_STREAM_CODEC.encode(argbFull, argb);
            assertArrayEquals(new byte[]{6, 't', 'e', 's', 't', ':', 'c', (byte) 0x80, 0x12, 0x34, 0x56}, bytes(argbFull));
            assertEquals(argb, ModuleColorConfig.ARGB_STREAM_CODEC.decode(argbFull));
        } finally {
            argbFull.release();
        }
        ByteBuf rgbFull = Unpooled.buffer();
        try {
            ModuleColorConfig.RGB_STREAM_CODEC.encode(rgbFull, rgb);
            assertArrayEquals(new byte[]{6, 't', 'e', 's', 't', ':', 'c', (byte) 0xD6, (byte) 0xE8, 0x48}, bytes(rgbFull));
            assertEquals(rgb, ModuleColorConfig.RGB_STREAM_CODEC.decode(rgbFull));
        } finally {
            rgbFull.release();
        }

        ResourceLocation boundName = id("bound", "color");
        StreamCodec<ByteBuf, ModuleConfig<Integer>> argbNamed = argb.namedStreamCodec(boundName);
        ByteBuf argbBound = Unpooled.buffer();
        try {
            argbNamed.encode(argbBound, argb);
            assertArrayEquals(new byte[]{(byte) 0x80, 0x12, 0x34, 0x56}, bytes(argbBound));
            ModuleColorConfig decoded = (ModuleColorConfig) argbNamed.decode(argbBound);
            assertSame(boundName, decoded.name());
            assertTrue(decoded.supportsAlpha());
            assertEquals(0x80123456, decoded.get());
        } finally {
            argbBound.release();
        }
        StreamCodec<ByteBuf, ModuleConfig<Integer>> rgbNamed = rgb.namedStreamCodec(boundName);
        ByteBuf rgbBound = Unpooled.buffer();
        try {
            rgbNamed.encode(rgbBound, rgb);
            assertArrayEquals(new byte[]{(byte) 0xD6, (byte) 0xE8, 0x48}, bytes(rgbBound));
            ModuleColorConfig decoded = (ModuleColorConfig) rgbNamed.decode(rgbBound);
            assertSame(boundName, decoded.name());
            assertFalse(decoded.supportsAlpha());
            assertEquals(0xFF123456, decoded.get());
        } finally {
            rgbBound.release();
        }

        assertSame(argb, argb.with(0x80123456));
        assertSame(rgb, rgb.with(0x80123456));
        ModuleColorConfig changedArgb = argb.with(-1);
        ModuleColorConfig changedRgb = rgb.with(0x00010203);
        assertTrue(changedArgb.supportsAlpha());
        assertEquals(-1, changedArgb.get());
        assertFalse(changedRgb.supportsAlpha());
        assertEquals(0xFF010203, changedRgb.get());
        assertThrows(NullPointerException.class, () -> rgb.with(null));
    }

    @Test
    void enumSelectableCountsAndBoundedCreationKeepOfficialBoundaries() {
        ModuleEnumConfig<TestMode> unlimited = ModuleEnumConfig.create(NAME, TestMode.ALPHA);
        assertEquals(List.of(TestMode.values()), unlimited.getEnumConstants());
        ModuleEnumConfig<TestMode> equal = ModuleEnumConfig.createBounded(NAME, TestMode.GAMMA, TestMode.values().length);
        assertEquals(List.of(TestMode.values()), equal.getEnumConstants());
        ModuleEnumConfig<TestMode> subset = ModuleEnumConfig.createBounded(NAME, TestMode.BETA, 2);
        assertEquals(List.of(TestMode.ALPHA, TestMode.BETA), subset.getEnumConstants());
        assertThrows(UnsupportedOperationException.class, () -> subset.getEnumConstants().add(TestMode.GAMMA));

        assertThrows(IllegalArgumentException.class, () -> ModuleEnumConfig.createBounded(NAME, TestMode.ALPHA, 0));
        assertThrows(IllegalArgumentException.class, () -> ModuleEnumConfig.createBounded(NAME, TestMode.ALPHA, -1));
        assertThrows(IllegalArgumentException.class, () -> ModuleEnumConfig.createBounded(NAME, TestMode.ALPHA, 4));
        assertThrows(IllegalArgumentException.class, () -> ModuleEnumConfig.createBounded(NAME, TestMode.BETA, 1));
        assertThrows(IllegalArgumentException.class, () -> ModuleEnumConfig.codec(MODE_CODEC, TestMode.class, 0));
        assertThrows(IllegalArgumentException.class, () -> ModuleEnumConfig.streamCodec(MODE_STREAM_CODEC, TestMode.class, 4));
    }

    @Test
    void enumDataAndSuppliedStreamCodecsRetainLimitedAndUnlimitedSemantics() {
        Codec<ModuleEnumConfig<TestMode>> unlimitedCodec = ModuleEnumConfig.codec(MODE_CODEC);
        ModuleEnumConfig<TestMode> legacy = unlimitedCodec.parse(JsonOps.INSTANCE,
              json("{\"name\":\"minecraft:mode\",\"value\":\"gamma\"}")).getOrThrow();
        assertEquals(id("mekanism", "mode"), legacy.name());
        assertSame(TestMode.GAMMA, legacy.get());
        assertEquals(List.of(TestMode.values()), legacy.getEnumConstants());
        assertEquals(json("{\"name\":\"mekanism:mode\",\"value\":\"gamma\"}"),
              unlimitedCodec.encodeStart(JsonOps.INSTANCE, legacy).getOrThrow());

        Codec<ModuleEnumConfig<TestMode>> boundedCodec = ModuleEnumConfig.codec(MODE_CODEC, TestMode.class, 2);
        ModuleEnumConfig<TestMode> bounded = boundedCodec.parse(JsonOps.INSTANCE,
              json("{\"name\":\"addon:mode\",\"value\":\"beta\"}")).getOrThrow();
        assertEquals(List.of(TestMode.ALPHA, TestMode.BETA), bounded.getEnumConstants());
        assertSame(TestMode.BETA, bounded.get());
        DataResult<ModuleEnumConfig<TestMode>> invalidDecode = boundedCodec.parse(JsonOps.INSTANCE,
              json("{\"name\":\"addon:mode\",\"value\":\"gamma\"}"));
        assertTrue(invalidDecode.error().orElseThrow().message().contains(
              "Invalid valueGAMMA, it is out of range of the selectable values."));
        assertTrue(boundedCodec.encodeStart(JsonOps.INSTANCE, ModuleEnumConfig.create(NAME, TestMode.GAMMA)).error().isPresent());

        StreamCodec<ByteBuf, ModuleEnumConfig<TestMode>> unlimitedStream = ModuleEnumConfig.streamCodec(MODE_STREAM_CODEC);
        ByteBuf full = Unpooled.buffer();
        try {
            unlimitedStream.encode(full, ModuleEnumConfig.create(NAME, TestMode.GAMMA));
            ModuleEnumConfig<TestMode> decoded = unlimitedStream.decode(full);
            assertEquals(NAME, decoded.name());
            assertSame(TestMode.GAMMA, decoded.get());
            assertEquals(List.of(TestMode.values()), decoded.getEnumConstants());
        } finally {
            full.release();
        }

        StreamCodec<ByteBuf, ModuleEnumConfig<TestMode>> boundedStream = ModuleEnumConfig.streamCodec(MODE_STREAM_CODEC, TestMode.class, 2);
        ByteBuf limited = Unpooled.buffer();
        try {
            boundedStream.encode(limited, ModuleEnumConfig.create(NAME, TestMode.BETA));
            ModuleEnumConfig<TestMode> decoded = boundedStream.decode(limited);
            assertSame(TestMode.BETA, decoded.get());
            assertEquals(List.of(TestMode.ALPHA, TestMode.BETA), decoded.getEnumConstants());
        } finally {
            limited.release();
        }
        ByteBuf officiallyUnchecked = Unpooled.buffer();
        try {
            boundedStream.encode(officiallyUnchecked, ModuleEnumConfig.create(NAME, TestMode.GAMMA));
            ModuleEnumConfig<TestMode> decoded = boundedStream.decode(officiallyUnchecked);
            assertSame(TestMode.GAMMA, decoded.get());
            assertEquals(List.of(TestMode.ALPHA, TestMode.BETA), decoded.getEnumConstants());
        } finally {
            officiallyUnchecked.release();
        }
    }

    @Test
    void namedEnumVarIntsFailuresAndBoundedWithBehaviorStayExact() {
        ResourceLocation boundName = id("bound", "enum");
        for (TestMode mode : TestMode.values()) {
            ModuleEnumConfig<TestMode> config = ModuleEnumConfig.create(NAME, mode);
            StreamCodec<FriendlyByteBuf, ModuleConfig<TestMode>> named = config.namedStreamCodec(boundName);
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                named.encode(buffer, config);
                assertArrayEquals(new byte[]{(byte) mode.ordinal()}, bytes(buffer));
                ModuleConfig<TestMode> decoded = named.decode(buffer);
                assertSame(boundName, decoded.name());
                assertSame(mode, decoded.get());
            } finally {
                buffer.release();
            }
        }

        StreamCodec<FriendlyByteBuf, ModuleConfig<TestMode>> named =
              ModuleEnumConfig.create(NAME, TestMode.ALPHA).namedStreamCodec(boundName);
        FriendlyByteBuf negative = new FriendlyByteBuf(Unpooled.wrappedBuffer(
              new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x0F}));
        try {
            assertArrayEquals(new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x0F}, bytes(negative));
            assertThrows(ArrayIndexOutOfBoundsException.class, () -> named.decode(negative));
        } finally {
            negative.release();
        }
        FriendlyByteBuf high = new FriendlyByteBuf(Unpooled.wrappedBuffer(new byte[]{(byte) 0x80, 0x01}));
        try {
            assertArrayEquals(new byte[]{(byte) 0x80, 0x01}, bytes(high));
            assertThrows(ArrayIndexOutOfBoundsException.class, () -> named.decode(high));
        } finally {
            high.release();
        }
        FriendlyByteBuf nullEncode = new FriendlyByteBuf(Unpooled.buffer());
        try {
            assertThrows(NullPointerException.class, () -> named.encode(nullEncode, null));
        } finally {
            nullEncode.release();
        }

        ModuleEnumConfig<TestMode> bounded = ModuleEnumConfig.createBounded(NAME, TestMode.ALPHA, 2);
        assertSame(bounded, bounded.with(TestMode.ALPHA));
        assertSame(bounded, bounded.with(0));
        ModuleEnumConfig<TestMode> byValue = bounded.with(TestMode.BETA);
        ModuleEnumConfig<TestMode> byIndex = bounded.with(1);
        assertNotSame(bounded, byValue);
        assertNotSame(bounded, byIndex);
        assertEquals(NAME, byValue.name());
        assertEquals(NAME, byIndex.name());
        assertSame(TestMode.BETA, byValue.get());
        assertSame(TestMode.BETA, byIndex.get());
        assertEquals(List.of(TestMode.values()), byValue.getEnumConstants());
        assertEquals(List.of(TestMode.values()), byIndex.getEnumConstants());
        assertThrows(IllegalArgumentException.class, () -> bounded.with(TestMode.GAMMA));
        assertThrows(IllegalArgumentException.class, () -> bounded.with(-1));
        assertThrows(IllegalArgumentException.class, () -> bounded.with(2));
    }

    @Test
    void equalityHashClassAndTextIconForwardingStayExact() {
        ModuleBooleanConfig first = ModuleBooleanConfig.create(NAME, true);
        ModuleBooleanConfig equal = ModuleBooleanConfig.create(NAME, true);
        assertEquals(ModuleBooleanConfig.class, first.getClass());
        assertEquals(NAME, first.name());
        assertEquals(Boolean.TRUE, first.get());
        assertEquals(first, equal);
        assertEquals(first.hashCode(), equal.hashCode());
        assertNotEquals(first, ModuleBooleanConfig.create(NAME, false));
        assertNotEquals(first, ModuleBooleanConfig.create(id("test", "other"), true));
        assertNotEquals(first, ModuleColorConfig.argb(NAME, 1));

        ModuleEnumConfig<TestMode> two = ModuleEnumConfig.createBounded(NAME, TestMode.ALPHA, 2);
        ModuleEnumConfig<TestMode> sameTwo = ModuleEnumConfig.createBounded(NAME, TestMode.ALPHA, 2);
        ModuleEnumConfig<TestMode> three = ModuleEnumConfig.create(NAME, TestMode.ALPHA);
        assertEquals(two, sameTwo);
        assertEquals(two.hashCode(), sameTwo.hashCode());
        assertNotEquals(two, three);
        assertEquals(2, two.getEnumConstants().size());
        assertSame(TestMode.ALPHA, two.get());

        Component directComponent = Component.literal("direct");
        TextValue direct = new TextValue(directComponent);
        assertSame(directComponent, direct.getTextComponent());
        Component enumComponent = Component.literal("enum");
        EnumTextValue enumText = new EnumTextValue(enumComponent);
        assertSame(enumComponent, enumText.getTextComponent());
        assertSame(enumComponent, enumText.getTranslatedName());
        Component iconComponent = Component.literal("icon");
        ResourceLocation icon = id("test", "mode_icon");
        IconValue iconValue = new IconValue(iconComponent, icon);
        assertSame(iconComponent, iconValue.getTextComponent());
        assertSame(icon, iconValue.getModeIcon());
    }

    private static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    private static JsonElement json(String value) {
        return JsonParser.parseString(value);
    }

    private static byte[] bytes(ByteBuf buffer) {
        byte[] bytes = new byte[buffer.readableBytes()];
        buffer.getBytes(buffer.readerIndex(), bytes);
        return bytes;
    }
}
