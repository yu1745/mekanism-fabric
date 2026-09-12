package mekanism.api.text;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import mekanism.api.IIncrementalEnum;
import mekanism.api.RelativeSide;
import mekanism.api.fluid.FluidStack;
import mekanism.api.inventory.IHashedItem;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.FastColor;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PortableTextColorTest {

    @BeforeAll
    static void bootstrapVanillaBeforeTextInitialization() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void incrementalEnumWrapsSkipsInvalidValuesAndAdjustsSignedOffsets() {
        assertSame(TestIncrement.ALPHA, TestIncrement.DELTA.getNext());
        assertSame(TestIncrement.DELTA, TestIncrement.ALPHA.getPrevious());
        assertSame(TestIncrement.ALPHA, TestIncrement.ALPHA.byIndex(8));
        assertSame(TestIncrement.DELTA, TestIncrement.ALPHA.byIndex(-1));

        assertSame(TestIncrement.DELTA,
              TestIncrement.ALPHA.getNext(value -> value != TestIncrement.BETA && value != TestIncrement.GAMMA));
        assertSame(TestIncrement.BETA,
              TestIncrement.ALPHA.getPrevious(value -> value != TestIncrement.DELTA && value != TestIncrement.GAMMA));
        assertSame(TestIncrement.GAMMA, TestIncrement.GAMMA.getNext(value -> false));
        assertSame(TestIncrement.GAMMA, TestIncrement.GAMMA.getPrevious(value -> false));

        assertSame(TestIncrement.ALPHA, TestIncrement.BETA.adjust(7));
        assertSame(TestIncrement.DELTA, TestIncrement.BETA.adjust(-6));
        assertSame(TestIncrement.BETA, TestIncrement.BETA.adjust(0));
        assertSame(TestIncrement.ALPHA,
              TestIncrement.BETA.adjust(2, value -> value == TestIncrement.ALPHA || value == TestIncrement.GAMMA));
        assertSame(TestIncrement.GAMMA,
              TestIncrement.BETA.adjust(-2, value -> value == TestIncrement.ALPHA || value == TestIncrement.GAMMA));
        assertSame(TestIncrement.BETA, TestIncrement.BETA.adjust(5, value -> false));
        assertSame(TestIncrement.BETA, TestIncrement.BETA.adjust(-5, value -> false));
    }

    @Test
    void apiLangAndRelativeSidesRetainExactKeysDelegationAndOwnedTranslations() {
        List<String> expectedKeys = List.of(
              "upgrade.mekanism.speed",
              "upgrade.mekanism.speed.description",
              "upgrade.mekanism.energy",
              "upgrade.mekanism.energy.description",
              "upgrade.mekanism.filter",
              "upgrade.mekanism.filter.description",
              "upgrade.mekanism.chemical",
              "upgrade.mekanism.chemical.description",
              "upgrade.mekanism.muffling",
              "upgrade.mekanism.muffling.description",
              "upgrade.mekanism.anchor",
              "upgrade.mekanism.anchor.description",
              "upgrade.mekanism.stone_generator",
              "upgrade.mekanism.stone_generator.description",
              "upgrade.mekanism.max_installed",
              "gui.mekanism.true_lower",
              "gui.mekanism.false_lower",
              "direction.mekanism.down",
              "direction.mekanism.up",
              "direction.mekanism.north",
              "direction.mekanism.south",
              "direction.mekanism.west",
              "direction.mekanism.east",
              "side.mekanism.front",
              "side.mekanism.left",
              "side.mekanism.right",
              "side.mekanism.back",
              "side.mekanism.top",
              "side.mekanism.bottom",
              "color.mekanism.black",
              "color.mekanism.dark_blue",
              "color.mekanism.dark_green",
              "color.mekanism.dark_aqua",
              "color.mekanism.dark_red",
              "color.mekanism.purple",
              "color.mekanism.orange",
              "color.mekanism.gray",
              "color.mekanism.dark_gray",
              "color.mekanism.indigo",
              "color.mekanism.bright_green",
              "color.mekanism.aqua",
              "color.mekanism.red",
              "color.mekanism.pink",
              "color.mekanism.yellow",
              "color.mekanism.white",
              "color.mekanism.brown",
              "color.mekanism.bright_pink",
              "chemical.mekanism.attribute.radiation",
              "chemical.mekanism.attribute.coolant.efficiency",
              "chemical.mekanism.attribute.coolant.heat_capacity",
              "chemical.mekanism.attribute.coolant.temperature",
              "chemical.mekanism.attribute.fuel.burn_ticks",
              "chemical.mekanism.attribute.fuel.energy_density",
              "security.mekanism.public",
              "security.mekanism.trusted",
              "security.mekanism.private",
              "tooltip.mekanism.decay_immune"
        );
        assertEquals(expectedKeys, Arrays.stream(APILang.values()).map(APILang::getTranslationKey).toList());
        for (APILang lang : APILang.values()) {
            MutableComponent first = lang.translate();
            MutableComponent second = lang.translate();
            assertEquals(lang.getTranslationKey(), translation(first).getKey());
            assertNotSame(first, second);
            first.append("caller mutation").withStyle(ChatFormatting.BOLD);
            assertEquals(Component.translatable(lang.getTranslationKey()), second);
        }

        RelativeSide[] sides = RelativeSide.values();
        APILang[] sideLang = {APILang.FRONT, APILang.LEFT, APILang.RIGHT, APILang.BACK, APILang.TOP, APILang.BOTTOM};
        String[] serialized = {"front", "left", "right", "back", "top", "bottom"};
        assertEquals(sideLang.length, sides.length);
        for (int i = 0; i < sides.length; i++) {
            RelativeSide side = sides[i];
            assertEquals(serialized[i], side.getSerializedName());
            assertSame(side, RelativeSide.valueOf(side.name()));
            assertSame(sideLang[i].getTranslationKey(), side.getTranslationKey());
            Component translated = side.getTranslatedName();
            assertEquals(side.getTranslationKey(), translation(translated).getKey());
            assertEquals(Component.translatable("side.mekanism." + serialized[i]), translated);
            assertNotSame(translated, side.getTranslatedName());
        }
    }

    @Test
    void enumColorRetainsMetadataCodecsWrappingAndRestorableAtlasState() {
        EnumColor[] colors = EnumColor.values();
        String[] codes = {"§0", "§1", "§2", "§3", "§4", "§5", "§6", "§7", "§8", "§9", "§a", "§b", "§c", "§d", "§e", "§f", "§6", "§d"};
        String[] englishNames = {"Black", "Blue", "Green", "Cyan", "Dark Red", "Purple", "Orange", "Light Gray", "Gray", "Light Blue", "Lime", "Aqua", "Red", "Magenta", "Yellow", "White", "Brown", "Pink"};
        String[] serializedNames = {"black", "blue", "green", "cyan", "dark_red", "purple", "orange", "light_gray", "gray", "light_blue", "lime", "aqua", "red", "magenta", "yellow", "white", "brown", "pink"};
        DyeColor[] dyes = {DyeColor.BLACK, DyeColor.BLUE, DyeColor.GREEN, DyeColor.CYAN, null, DyeColor.PURPLE,
              DyeColor.ORANGE, DyeColor.LIGHT_GRAY, DyeColor.GRAY, DyeColor.LIGHT_BLUE, DyeColor.LIME, null,
              DyeColor.RED, DyeColor.MAGENTA, DyeColor.YELLOW, DyeColor.WHITE, DyeColor.BROWN, DyeColor.PINK};
        MapColor[] mapColors = {DyeColor.BLACK.getMapColor(), DyeColor.BLUE.getMapColor(), DyeColor.GREEN.getMapColor(),
              DyeColor.CYAN.getMapColor(), MapColor.NETHER, DyeColor.PURPLE.getMapColor(), DyeColor.ORANGE.getMapColor(),
              DyeColor.LIGHT_GRAY.getMapColor(), DyeColor.GRAY.getMapColor(), DyeColor.LIGHT_BLUE.getMapColor(),
              DyeColor.LIME.getMapColor(), MapColor.COLOR_LIGHT_BLUE, DyeColor.RED.getMapColor(), DyeColor.MAGENTA.getMapColor(),
              DyeColor.YELLOW.getMapColor(), DyeColor.WHITE.getMapColor(), DyeColor.BROWN.getMapColor(), DyeColor.PINK.getMapColor()};
        APILang[] colorLang = {APILang.COLOR_BLACK, APILang.COLOR_DARK_BLUE, APILang.COLOR_DARK_GREEN,
              APILang.COLOR_DARK_AQUA, APILang.COLOR_DARK_RED, APILang.COLOR_PURPLE, APILang.COLOR_ORANGE,
              APILang.COLOR_GRAY, APILang.COLOR_DARK_GRAY, APILang.COLOR_INDIGO, APILang.COLOR_BRIGHT_GREEN,
              APILang.COLOR_AQUA, APILang.COLOR_RED, APILang.COLOR_PINK, APILang.COLOR_YELLOW, APILang.COLOR_WHITE,
              APILang.COLOR_BROWN, APILang.COLOR_BRIGHT_PINK};

        assertEquals(18, colors.length);
        for (int i = 0; i < colors.length; i++) {
            EnumColor color = colors[i];
            assertEquals(codes[i], color.code);
            assertEquals(codes[i], color.toString());
            assertEquals(englishNames[i], color.getEnglishName());
            assertEquals(serializedNames[i], color.getRegistryPrefix());
            assertEquals(serializedNames[i], color.getSerializedName());
            assertSame(dyes[i], color.getDyeColor());
            assertSame(mapColors[i], color.getMapColor());
            assertSame(colorLang[i], color.getLangEntry());
            assertEquals(Component.translatable(colorLang[i].getTranslationKey()), color.getName());
            assertSame(color, EnumColor.BY_ID.apply(i));
            assertSame(color, EnumColor.BY_ID.apply(i + colors.length));
            assertSame(color, EnumColor.BY_ID.apply(i - colors.length));
            assertEquals(new JsonPrimitive(serializedNames[i]),
                  EnumColor.CODEC.encodeStart(JsonOps.INSTANCE, color).getOrThrow());
            assertSame(color, EnumColor.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive(serializedNames[i])).getOrThrow());
            assertSame(color, roundTrip(EnumColor.STREAM_CODEC, color));
            assertEquals(Optional.of(color), roundTrip(EnumColor.OPTIONAL_STREAM_CODEC, Optional.of(color)));
        }
        assertSame(EnumColor.WHITE, EnumColor.BY_ID.apply(-3));
        assertSame(EnumColor.BLACK, EnumColor.BY_ID.apply(colors.length));
        assertEquals(Optional.empty(), roundTrip(EnumColor.OPTIONAL_STREAM_CODEC, Optional.empty()));

        EnumColor mutable = EnumColor.ORANGE;
        int[] originalRgb = mutable.getRgbCode();
        int originalPacked = mutable.getPackedColor();
        TextColor originalText = mutable.getColor();
        int[] replacement = {7, 129, 251};
        try {
            mutable.setColorFromAtlas(replacement);
            int expectedPacked = FastColor.ARGB32.color(7, 129, 251);
            assertSame(replacement, mutable.getRgbCode());
            assertEquals(expectedPacked, mutable.getPackedColor());
            assertEquals(TextColor.fromRgb(expectedPacked), mutable.getColor());
            assertArrayEquals(new float[]{7 / 255F, 129 / 255F, 251 / 255F}, mutable.getRgbCodeFloat());
            assertEquals(FastColor.ARGB32.color(63, expectedPacked), mutable.getPackedColor(63));
        } finally {
            mutable.setColorFromAtlas(originalRgb);
        }
        assertSame(originalRgb, mutable.getRgbCode());
        assertEquals(originalPacked, mutable.getPackedColor());
        assertEquals(originalText, mutable.getColor());
    }

    @Test
    void buildUnwrapsOwnedValuesPreservesOrderAndConsumesStylesOnce() {
        Holder<net.minecraft.world.item.Item> diamondHolder = Items.DIAMOND.builtInRegistryHolder();
        assertTrue(diamondHolder.isBound());
        assertEquals(Items.DIAMOND.getDescription(), TextComponentUtil.build(diamondHolder));

        ItemStack namedStack = new ItemStack(Items.PAPER, 3);
        namedStack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("hashed paper"));
        IHashedItem hashed = new IHashedItem() {
            @Override
            public ItemStack getInternalStack() {
                return namedStack;
            }

            @Override
            public ItemStack createStack(int size) {
                ItemStack copy = namedStack.copy();
                copy.setCount(size);
                return copy;
            }
        };
        assertEquals(namedStack.getHoverName(), TextComponentUtil.build(hashed));

        FluidStack ownedWater = new FluidStack(Fluids.WATER, 1_000);
        String waterKey = Fluids.WATER.defaultFluidState().createLegacyBlock().getBlock().getDescriptionId();
        MutableComponent expectedFluid = Component.translatable(waterKey);
        assertEquals(expectedFluid, TextComponentUtil.build(ownedWater));
        assertEquals(expectedFluid, TextComponentUtil.build(Fluids.WATER));
        assertEquals(TextComponentUtil.build(ownedWater), TextComponentUtil.build(Fluids.WATER));

        MutableComponent ordered = TextComponentUtil.build(null, diamondHolder, " | ", hashed, null, " | ", ownedWater, " | ", Fluids.WATER);
        MutableComponent expectedOrder = Items.DIAMOND.getDescription().copy()
              .append(Component.literal(" | "))
              .append(namedStack.getHoverName().copy())
              .append(Component.literal(" | "))
              .append(Component.translatable(waterKey))
              .append(Component.literal(" | "))
              .append(Component.translatable(waterKey));
        assertEquals(expectedOrder, ordered);
        assertNull(TextComponentUtil.build());
        assertNull(TextComponentUtil.build(null, null, null));

        MutableComponent styled = TextComponentUtil.build(EnumColor.RED, ChatFormatting.BOLD, "styled", "plain");
        assertNotNull(styled);
        assertEquals(EnumColor.RED.getColor(), styled.getStyle().getColor());
        assertTrue(styled.getStyle().isBold());
        assertEquals(1, styled.getSiblings().size());
        assertSame(Style.EMPTY, styled.getSiblings().getFirst().getStyle());
        assertEquals(Component.literal("styled").withStyle(styled.getStyle()).append(Component.literal("plain")), styled);
        assertEquals(Component.literal("reset"), TextComponentUtil.build(ChatFormatting.BOLD, ChatFormatting.RESET, "reset"));
        assertEquals(Component.literal("trailing"), TextComponentUtil.build("trailing", EnumColor.DARK_BLUE));
    }

    @Test
    void smartTranslateRetainsArgumentTypesFormattingOrderAndMutationIsolation() {
        Integer integer = 4_321;
        Long longValue = 9_007_199_254_740_993L;
        Double decimal = 2.5D;
        Boolean bool = Boolean.TRUE;
        MutableComponent translated = TextComponentUtil.smartTranslate("task036.arguments",
              null, "wide\u00A0space\u202Fnarrow", integer, longValue, decimal, bool);
        Object[] args = translation(translated).getArgs();
        assertEquals(6, args.length);
        assertEquals(Component.literal("null"), args[0]);
        assertEquals("wide space narrow", args[1]);
        assertSame(integer, args[2]);
        assertSame(longValue, args[3]);
        assertSame(decimal, args[4]);
        Component translatedBoolean = assertInstanceOf(Component.class, args[5]);
        assertEquals(APILang.TRUE_LOWER.getTranslationKey(), translation(translatedBoolean).getKey());

        Object[] firstStyleWins = translation(TextComponentUtil.smartTranslate("task036.first_style",
              EnumColor.RED, EnumColor.DARK_BLUE, "payload")).getArgs();
        assertEquals(2, firstStyleWins.length);
        Component rejectedSecondColor = assertInstanceOf(Component.class, firstStyleWins[0]);
        assertEquals(EnumColor.DARK_BLUE.getLangEntry().getTranslationKey(), translation(rejectedSecondColor).getKey());
        assertEquals(EnumColor.RED.getColor(), rejectedSecondColor.getStyle().getColor());
        assertEquals("payload", firstStyleWins[1]);

        Object[] reset = translation(TextComponentUtil.smartTranslate("task036.reset",
              EnumColor.AQUA, "colored", "plain")).getArgs();
        assertEquals(2, reset.length);
        Component colored = assertInstanceOf(Component.class, reset[0]);
        assertEquals(Component.literal("colored").withStyle(Style.EMPTY.withColor(EnumColor.AQUA.getColor())), colored);
        assertEquals("plain", reset[1]);
        Object[] trailing = translation(TextComponentUtil.smartTranslate("task036.trailing", "value", EnumColor.YELLOW)).getArgs();
        assertEquals(2, trailing.length);
        assertEquals("value", trailing[0]);
        Component trailingName = assertInstanceOf(Component.class, trailing[1]);
        assertEquals(EnumColor.YELLOW.getLangEntry().getTranslationKey(), translation(trailingName).getKey());
        assertSame(Style.EMPTY, trailingName.getStyle());

        MutableComponent coloredEntry = APILang.UPGRADE_MAX_INSTALLED.translateColored(
              EnumColor.BRIGHT_GREEN, EnumColor.PURPLE, APILang.TRUE_LOWER);
        assertEquals(EnumColor.BRIGHT_GREEN.getColor(), coloredEntry.getStyle().getColor());
        Object[] coloredEntryArgs = translation(coloredEntry).getArgs();
        assertEquals(1, coloredEntryArgs.length);
        Component innerEntry = assertInstanceOf(Component.class, coloredEntryArgs[0]);
        assertEquals(APILang.TRUE_LOWER.getTranslationKey(), translation(innerEntry).getKey());
        assertEquals(EnumColor.PURPLE.getColor(), innerEntry.getStyle().getColor());

        MutableComponent source = Component.literal("before");
        StringBuilder fallback = new StringBuilder("snapshot");
        MutableComponent mutationSensitive = TextComponentUtil.smartTranslate("task036.mutation", source, fallback);
        Object[] captured = translation(mutationSensitive).getArgs();
        Component capturedSource = assertInstanceOf(Component.class, captured[0]);
        assertNotSame(source, capturedSource);
        source.append(" after").withStyle(ChatFormatting.ITALIC);
        fallback.append(" changed");
        assertEquals(Component.literal("before"), capturedSource);
        assertEquals("snapshot", captured[1]);
    }

    private static TranslatableContents translation(Component component) {
        return assertInstanceOf(TranslatableContents.class, component.getContents());
    }

    private static <TYPE> TYPE roundTrip(StreamCodec<ByteBuf, TYPE> codec, TYPE value) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            codec.encode(buffer, value);
            TYPE decoded = codec.decode(buffer);
            assertEquals(0, buffer.readableBytes());
            return decoded;
        } finally {
            buffer.release();
        }
    }

    private enum TestIncrement implements IIncrementalEnum<TestIncrement> {
        ALPHA,
        BETA,
        GAMMA,
        DELTA;

        @Override
        public TestIncrement byIndex(int index) {
            return values()[Math.floorMod(index, values().length)];
        }
    }
}
