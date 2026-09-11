package mekanism.common.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ResourceMetadataTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void miscResourcesExposeTheCompleteExactSuffixTable() {
        Map<MiscResource, String> expected = new LinkedHashMap<>();
        expected.put(MiscResource.BRONZE, "bronze");
        expected.put(MiscResource.CARBON, "carbon");
        expected.put(MiscResource.CHARCOAL, "charcoal");
        expected.put(MiscResource.COAL, "coal");
        expected.put(MiscResource.DIAMOND, "diamond");
        expected.put(MiscResource.EMERALD, "emerald");
        expected.put(MiscResource.NETHERITE, "netherite");
        expected.put(MiscResource.LAPIS_LAZULI, "lapis_lazuli");
        expected.put(MiscResource.LITHIUM, "lithium");
        expected.put(MiscResource.OBSIDIAN, "obsidian");
        expected.put(MiscResource.QUARTZ, "quartz");
        expected.put(MiscResource.REDSTONE, "redstone");
        expected.put(MiscResource.REFINED_GLOWSTONE, "refined_glowstone");
        expected.put(MiscResource.REFINED_OBSIDIAN, "refined_obsidian");
        expected.put(MiscResource.STEEL, "steel");
        expected.put(MiscResource.SULFUR, "sulfur");
        expected.put(MiscResource.FLUORITE, "fluorite");

        assertEquals(expected.keySet().stream().toList(), java.util.List.of(MiscResource.values()));
        expected.forEach((resource, suffix) -> assertEquals(suffix, resource.getRegistrySuffix()));
    }

    @Test
    void blockResourcesExposeExactSuffixColorBurningAndPortalMetadata() {
        String[] suffixes = {"osmium", "raw_osmium", "tin", "raw_tin", "lead", "raw_lead", "uranium", "raw_uranium",
              "charcoal", "fluorite", "bronze", "steel", "refined_obsidian", "refined_glowstone"};
        MapColor[] colors = {MapColor.COLOR_CYAN, MapColor.COLOR_CYAN, MapColor.TERRACOTTA_WHITE, MapColor.TERRACOTTA_WHITE,
              MapColor.COLOR_LIGHT_GRAY, MapColor.COLOR_LIGHT_GRAY, MapColor.GRASS, MapColor.GRASS, MapColor.COLOR_BLACK,
              MapColor.SNOW, MapColor.COLOR_ORANGE, MapColor.DEEPSLATE, MapColor.COLOR_PURPLE, MapColor.COLOR_YELLOW};
        BlockResourceInfo[] resources = BlockResourceInfo.values();
        assertEquals(suffixes.length, resources.length);
        for (int i = 0; i < resources.length; i++) {
            assertEquals(suffixes[i], resources[i].getRegistrySuffix());
            assertSame(colors[i], resources[i].getMapColor());
            assertEquals(resources[i] != BlockResourceInfo.REFINED_OBSIDIAN, resources[i].burnsInFire());
            assertEquals(resources[i] == BlockResourceInfo.REFINED_OBSIDIAN, resources[i].isPortalFrame());
        }
    }

    @Test
    void modifyPropertiesAppliesExactOrdinaryRawLuminousAndExceptionalBlockBehavior() throws ReflectiveOperationException {
        assertProperties(BlockResourceInfo.OSMIUM, 7.5F, 12, MapColor.COLOR_CYAN, 0, NoteBlockInstrument.HARP, PushReaction.NORMAL);
        assertProperties(BlockResourceInfo.RAW_TIN, 5, 6, MapColor.TERRACOTTA_WHITE, 0, NoteBlockInstrument.BASEDRUM, PushReaction.NORMAL);
        assertProperties(BlockResourceInfo.REFINED_GLOWSTONE, 5, 6, MapColor.COLOR_YELLOW, 15, NoteBlockInstrument.BASEDRUM, PushReaction.NORMAL);
        assertProperties(BlockResourceInfo.REFINED_OBSIDIAN, 50, 2_400, MapColor.COLOR_PURPLE, 8, NoteBlockInstrument.BASEDRUM, PushReaction.BLOCK);
        assertFalse(BlockResourceInfo.REFINED_OBSIDIAN.burnsInFire());
        assertTrue(BlockResourceInfo.REFINED_OBSIDIAN.isPortalFrame());
    }

    @SuppressWarnings("unchecked")
    private static void assertProperties(BlockResourceInfo resource, float hardness, float resistance, MapColor mapColor,
          int light, NoteBlockInstrument instrument, PushReaction pushReaction) throws ReflectiveOperationException {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of();
        assertSame(properties, resource.modifyProperties(properties));
        // Vanilla freezes the built-in block registry during Bootstrap, so an otherwise observable
        // throwaway Block cannot be constructed without forbidden global registry mutation.
        assertEquals(hardness, property(properties, "destroyTime"));
        assertEquals(resistance, property(properties, "explosionResistance"));
        Function<BlockState, MapColor> mapColorFunction = (Function<BlockState, MapColor>) property(properties, "mapColor");
        ToIntFunction<BlockState> lightFunction = (ToIntFunction<BlockState>) property(properties, "lightEmission");
        assertSame(mapColor, mapColorFunction.apply(null));
        assertEquals(light, lightFunction.applyAsInt(null));
        assertSame(instrument, property(properties, "instrument"));
        assertSame(pushReaction, property(properties, "pushReaction"));
    }

    private static Object property(BlockBehaviour.Properties properties, String name) throws ReflectiveOperationException {
        Field field = BlockBehaviour.Properties.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(properties);
    }
}
