package mekanism.fabric.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.GenerationStep;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Focused contracts for the official {@code neoforge:add_features} biome modifier adapter: the six packaged official
 * files must parse into their exact placed-feature sets in official order, and the {@code HolderSet<Biome>} selector
 * must keep official tag/biome-ID match semantics.
 */
class FabricBiomeModifiersTest {

    @TempDir Path temporary;

    @Test
    void activePackPriorityAndWorldScopesDoNotLeak() throws IOException {
        Path low = temporary.resolve("low");
        Path high = temporary.resolve("high");
        Path lowFile = low.resolve("data/mekanism/neoforge/biome_modifier/fluorite.json");
        Path highFile = high.resolve("data/mekanism/neoforge/biome_modifier/fluorite.json");
        Files.createDirectories(lowFile.getParent());
        Files.createDirectories(highFile.getParent());
        Files.writeString(lowFile, official("fluorite"));
        Files.writeString(highFile, "{\"type\":\"neoforge:none\"}");
        try (MultiPackResourceManager defaults = resources(low); MultiPackResourceManager overridden = resources(low, high)) {
            assertEquals(1, FabricBiomeModifiers.load(defaults).size());
            assertTrue(FabricBiomeModifiers.load(overridden).isEmpty());
            FabricBiomeModifiers.captureWorldResources(overridden);
            assertThrows(IllegalArgumentException.class, () -> FabricBiomeModifiers.withWorldModifiers(() -> {
                throw new IllegalArgumentException("fixture");
            }));
            FabricBiomeModifiers.captureWorldResources(defaults);
            FabricBiomeModifiers.withWorldModifiers(() -> assertEquals(1, FabricBiomeModifiers.load(defaults).size()));
        }
    }

    private static MultiPackResourceManager resources(Path... roots) {
        List<PackResources> packs = Stream.of(roots).map(root -> (PackResources) new PathPackResources(
              new PackLocationInfo(root.getFileName().toString(), Component.literal(root.getFileName().toString()), PackSource.DEFAULT, Optional.empty()), root)).toList();
        return new MultiPackResourceManager(PackType.SERVER_DATA, packs);
    }

    private static String official(String name) {
        try (InputStream input = FabricBiomeModifiersTest.class.getResourceAsStream("/mekanism/fabric/worldgen/" + name + ".json")) {
            if (input == null) {
                throw new IllegalStateException("Missing official biome modifier oracle: " + name);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static FabricBiomeModifiers.OreModifier parseOfficial(String name) {
        return FabricBiomeModifiers.parse(JsonParser.parseString(official(name)).getAsJsonObject());
    }

    private static FabricBiomeModifiers.OreModifier parse(String json) {
        return FabricBiomeModifiers.parse(JsonParser.parseString(json).getAsJsonObject());
    }

    private static ResourceLocation id(String value) {
        return ResourceLocation.parse(value);
    }

    @Test
    void parsesOfficialOreModifiersToExactFeatureSets() {
        //The tag selector, step and feature sets must mirror the official files exactly (they are the source of truth).
        List<String> names = List.of("fluorite", "lead", "osmium", "salt", "tin", "uranium");
        List<List<String>> features = List.of(
              List.of("ore_fluorite_normal", "ore_fluorite_buried"),
              List.of("ore_lead_normal"),
              List.of("ore_osmium_upper", "ore_osmium_middle", "ore_osmium_small"),
              List.of("salt"),
              List.of("ore_tin_small", "ore_tin_large"),
              List.of("ore_uranium_small", "ore_uranium_buried")
        );
        for (int index = 0; index < names.size(); index++) {
            FabricBiomeModifiers.OreModifier modifier = parseOfficial(names.get(index));
            assertEquals(GenerationStep.Decoration.UNDERGROUND_ORES, modifier.step(), names.get(index));
            assertEquals(List.of(id("mekanism:spawn_ores")), modifier.biomes().tags(), names.get(index));
            assertTrue(modifier.biomes().biomes().isEmpty(), names.get(index));
            assertEquals(features.get(index).stream().map(f -> id("mekanism:" + f)).toList(), modifier.features(),
                  names.get(index));
        }
    }

    @Test
    void parsesSingleValueAndExplicitBiomeIdForms() {
        //Official HolderSet<Biome> JSON also allows a bare biome ID (single or list) instead of a tag reference.
        FabricBiomeModifiers.OreModifier single = parse("""
              {"type":"neoforge:add_features","biomes":"minecraft:plains","features":"mekanism:ore_lead_normal","step":"underground_ores"}
              """);
        assertEquals(List.of(), single.biomes().tags());
        assertEquals(List.of(id("minecraft:plains")), single.biomes().biomes());

        FabricBiomeModifiers.OreModifier listed = parse("""
              {"type":"neoforge:add_features","biomes":["#minecraft:is_overworld","minecraft:badlands"],
               "features":["mekanism:salt"],"step":"underground_ores"}
              """);
        assertEquals(List.of(id("minecraft:is_overworld")), listed.biomes().tags());
        assertEquals(List.of(id("minecraft:badlands")), listed.biomes().biomes());
    }

    @Test
    void selectorKeepsOfficialTagAndBiomeMatchSemantics() {
        FabricBiomeModifiers.BiomeSelector selector = new FabricBiomeModifiers.BiomeSelector(
              List.of(id("mekanism:spawn_ores")), List.of(id("minecraft:badlands")));
        //Any listed tag hit matches, independent of explicit biome IDs; no hit and no ID match fails.
        assertTrue(FabricBiomeModifiers.matches(selector, tag -> tag.equals(id("mekanism:spawn_ores")), biome -> false));
        assertTrue(FabricBiomeModifiers.matches(selector, tag -> false, biome -> biome.equals(id("minecraft:badlands"))));
        assertFalse(FabricBiomeModifiers.matches(selector, tag -> false, biome -> false));
        //A biome that merely lives inside a matched tag must NOT match through the explicit-ID lane.
        assertFalse(FabricBiomeModifiers.matches(selector, tag -> false, biome -> biome.equals(id("minecraft:plains"))));
    }

    @Test
    void rejectsUnknownTypeStepAndEmptySets() {
        assertThrows(IllegalStateException.class, () -> parse(
              """ 
              {"type":"mekanismadditions:baby_mob_spawn","baby_type":"creeper"}"""));
        assertThrows(IllegalStateException.class, () -> parse("""
              {"type":"neoforge:add_features","biomes":"#c:is_overworld","features":"mekanism:salt","step":"not_a_decoration_step"}
              """));
        assertThrows(IllegalStateException.class, () -> parse("""
              {"type":"neoforge:add_features","biomes":[],"features":"mekanism:salt","step":"underground_ores"}
              """));
        assertThrows(IllegalStateException.class, () -> parse("""
              {"type":"neoforge:add_features","biomes":"#c:is_overworld","features":[],"step":"underground_ores"}
              """));
    }
}
