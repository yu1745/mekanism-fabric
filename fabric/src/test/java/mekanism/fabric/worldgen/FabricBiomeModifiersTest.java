package mekanism.fabric.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.Lifecycle;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FabricBiomeModifiersTest {
    @TempDir Path temporary;
    private static RegistryAccess registries;
    private static Holder<Biome> plains;
    private static Holder<Biome> badlands;

    private static ResourceLocation id(String value) {
        return ResourceLocation.parse(value);
    }

    private static Biome biome() {
        return new Biome.BiomeBuilder().temperature(0.8F).downfall(0.4F)
              .specialEffects(new BiomeSpecialEffects.Builder().fogColor(0).waterColor(0).waterFogColor(0).skyColor(0).build())
              .mobSpawnSettings(MobSpawnSettings.EMPTY).generationSettings(BiomeGenerationSettings.EMPTY).build();
    }

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var biomes = new MappedRegistry<Biome>(Registries.BIOME, Lifecycle.stable());
        plains = biomes.register(ResourceKey.create(Registries.BIOME, id("minecraft:plains")), biome(), RegistrationInfo.BUILT_IN);
        badlands = biomes.register(ResourceKey.create(Registries.BIOME, id("minecraft:badlands")), biome(), RegistrationInfo.BUILT_IN);
        biomes.freeze();
        biomes.bindTags(Map.of(TagKey.create(Registries.BIOME, id("mekanism:spawn_ores")), List.of(plains),
              TagKey.create(Registries.BIOME, id("minecraft:is_overworld")), List.of(plains),
              TagKey.create(Registries.BIOME, id("c:is_overworld")), List.of(plains)));
        var configured = new MappedRegistry<ConfiguredFeature<?, ?>>(Registries.CONFIGURED_FEATURE, Lifecycle.stable());
        var configuredHolder = configured.register(ResourceKey.create(Registries.CONFIGURED_FEATURE, id("mekanism:test")),
              new ConfiguredFeature<>(Feature.NO_OP, NoneFeatureConfiguration.INSTANCE), RegistrationInfo.BUILT_IN);
        configured.freeze();
        var features = new MappedRegistry<PlacedFeature>(Registries.PLACED_FEATURE, Lifecycle.stable());
        for (String path : List.of("ore_fluorite_normal", "ore_fluorite_buried", "ore_lead_normal", "ore_osmium_upper",
              "ore_osmium_middle", "ore_osmium_small", "salt", "ore_tin_small", "ore_tin_large", "ore_uranium_small", "ore_uranium_buried")) {
            features.register(ResourceKey.create(Registries.PLACED_FEATURE, id("mekanism:" + path)),
                  new PlacedFeature(configuredHolder, List.of()), RegistrationInfo.BUILT_IN);
        }
        features.freeze();
        features.bindTags(Map.of(TagKey.create(Registries.PLACED_FEATURE, id("mekanism:test")), List.of(
              features.getHolderOrThrow(ResourceKey.create(Registries.PLACED_FEATURE, id("mekanism:ore_fluorite_buried"))),
              features.getHolderOrThrow(ResourceKey.create(Registries.PLACED_FEATURE, id("mekanism:ore_fluorite_normal"))))));
        registries = new RegistryAccess.ImmutableRegistryAccess(List.of(biomes, configured, features));
    }

    private static String official(String name) {
        try (InputStream input = FabricBiomeModifiersTest.class.getResourceAsStream("/mekanism/fabric/worldgen/" + name + ".json")) {
            if (input == null) throw new IllegalStateException("Missing official biome modifier oracle: " + name);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static FabricBiomeModifiers.OreModifier parse(String json) {
        return FabricBiomeModifiers.parse(JsonParser.parseString(json).getAsJsonObject(), registries);
    }

    private static List<ResourceLocation> names(FabricBiomeModifiers.OreModifier modifier) {
        return modifier.features().stream().map(holder -> holder.unwrapKey().orElseThrow().location()).toList();
    }

    private static MultiPackResourceManager resources(Path... roots) {
        List<PackResources> packs = Stream.of(roots).map(root -> (PackResources) new PathPackResources(
              new PackLocationInfo(root.getFileName().toString(), Component.literal(root.getFileName().toString()), PackSource.DEFAULT, Optional.empty()), root)).toList();
        return new MultiPackResourceManager(PackType.SERVER_DATA, packs);
    }

    @Test
    void activePackPriorityAndWorldScopesDoNotLeak() throws IOException {
        Path low = temporary.resolve("low"), high = temporary.resolve("high");
        Path lowFile = low.resolve("data/mekanism/neoforge/biome_modifier/fluorite.json");
        Path highFile = high.resolve("data/mekanism/neoforge/biome_modifier/fluorite.json");
        Files.createDirectories(lowFile.getParent());
        Files.createDirectories(highFile.getParent());
        Files.writeString(lowFile, official("fluorite"));
        Files.writeString(highFile, "{\"type\":\"neoforge:none\"}");
        try (MultiPackResourceManager defaults = resources(low); MultiPackResourceManager overridden = resources(low, high)) {
            assertEquals(1, FabricBiomeModifiers.load(defaults, registries).size());
            assertTrue(FabricBiomeModifiers.load(overridden, registries).isEmpty());
            FabricBiomeModifiers.captureWorldResources(overridden);
            assertThrows(IllegalArgumentException.class, () -> FabricBiomeModifiers.withWorldModifiers(registries, () -> {
                throw new IllegalArgumentException("fixture");
            }));
            FabricBiomeModifiers.captureWorldResources(defaults);
            FabricBiomeModifiers.withWorldModifiers(registries, () -> assertEquals(1, FabricBiomeModifiers.load(defaults, registries).size()));
        }
    }

    @Test
    void parsesOfficialOreModifiersToExactFeatureSets() {
        List<String> names = List.of("fluorite", "lead", "osmium", "salt", "tin", "uranium");
        List<List<String>> features = List.of(List.of("ore_fluorite_normal", "ore_fluorite_buried"), List.of("ore_lead_normal"),
              List.of("ore_osmium_upper", "ore_osmium_middle", "ore_osmium_small"), List.of("salt"),
              List.of("ore_tin_small", "ore_tin_large"), List.of("ore_uranium_small", "ore_uranium_buried"));
        for (int index = 0; index < names.size(); index++) {
            var modifier = parse(official(names.get(index)));
            assertEquals(GenerationStep.Decoration.UNDERGROUND_ORES, modifier.step());
            assertTrue(modifier.biomes().contains(plains));
            assertFalse(modifier.biomes().contains(badlands));
            assertEquals(features.get(index).stream().map(f -> id("mekanism:" + f)).toList(), names(modifier));
        }
    }

    @Test
    void parsesSingleValueAndExplicitBiomeIdForms() {
        var single = parse("""
              {"type":"neoforge:add_features","biomes":"minecraft:plains","features":"mekanism:ore_lead_normal","step":"underground_ores"}
              """);
        assertTrue(single.biomes().contains(plains));
        assertFalse(single.biomes().contains(badlands));
        var listed = parse("""
              {"type":"neoforge:add_features","biomes":["minecraft:plains","minecraft:badlands"],"features":["mekanism:salt"],"step":"underground_ores"}
              """);
        assertTrue(listed.biomes().contains(plains));
        assertTrue(listed.biomes().contains(badlands));
    }

    @Test
    void selectorUsesRegistryTagMembership() {
        var modifier = parse("""
              {"type":"neoforge:add_features","biomes":"#mekanism:spawn_ores","features":"mekanism:salt","step":"underground_ores"}
              """);
        assertTrue(modifier.biomes().contains(plains));
        assertFalse(modifier.biomes().contains(badlands));
    }

    @Test
    void rejectsUnknownTypeAndStep() {
        assertThrows(IllegalStateException.class, () -> parse("{\"type\":\"mekanismadditions:baby_mob_spawn\"}"));
        assertThrows(IllegalStateException.class, () -> parse("""
              {"type":"neoforge:add_features","biomes":"#c:is_overworld","features":"mekanism:salt","step":"not_a_decoration_step"}
              """));
    }

    @Test
    void resolvesPlacedFeatureTagsFromCurrentRegistry() {
        var modifier = parse("""
              {"type":"neoforge:add_features","biomes":"minecraft:plains","features":"#mekanism:test","step":"underground_ores"}
              """);
        assertEquals(List.of(id("mekanism:ore_fluorite_buried"), id("mekanism:ore_fluorite_normal")), names(modifier));
        assertThrows(IllegalStateException.class, () -> parse("""
              {"type":"neoforge:add_features","biomes":"minecraft:plains","features":"#mekanism:missing","step":"underground_ores"}
              """));
    }

    @Test
    void acceptsEmptyAndInlineHolderSets() {
        var empty = parse("""
              {"type":"neoforge:add_features","biomes":[],"features":[],"step":"underground_ores"}
              """);
        assertFalse(empty.biomes().contains(plains));
        assertEquals(0, empty.features().size());
        var inline = parse("""
              {"type":"neoforge:add_features","biomes":"minecraft:plains","features":{"feature":"mekanism:test","placement":[]},"step":"underground_ores"}
              """);
        assertTrue(inline.features().stream().findFirst().orElseThrow().unwrapKey().isEmpty());
        assertThrows(IllegalStateException.class, () -> parse("""
              {"type":"neoforge:add_features","biomes":"minecraft:plains","features":["mekanism:salt",{"feature":"mekanism:test","placement":[]}],"step":"underground_ores"}
              """));
    }
}
