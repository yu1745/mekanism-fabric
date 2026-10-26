package mekanism.fabric.worldgen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;
import mekanism.api.MekanismAPI;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectionContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.GenerationStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Applies the official NeoForge biome modifiers that decide where Mekanism's ores generate.
 *
 * <p>Fabric has no counterpart for NeoForge's {@code neoforge/biome_modifier} datapack registry, so the official
 * {@code data/mekanism/neoforge/biome_modifier/*.json} files stay packaged unchanged (they remain the source of truth)
 * and this adapter parses exactly the {@code neoforge:add_features} entries at startup, re-registering every referenced
 * placed feature for its declared decoration step through Fabric's sanctioned {@link BiomeModifications} path. The
 * {@code biomes} field keeps official {@code HolderSet<Biome>} semantics: a {@code #tag} reference or a list of biome
 * IDs, matched lazily against the selection context (biome tags are only resolvable when the worlds load).</p>
 *
 * <p>Other official modifier types (currently the Additions-only {@code mekanismadditions:baby_mob_spawn}) are a
 * separate vertical and are rejected here rather than silently ignored, so a future type cannot be dropped by accident.</p>
 */
public final class FabricBiomeModifiers {
    private static final Logger LOGGER = LoggerFactory.getLogger("Mekanism/Fabric");
    private static final String RESOURCE_DIR = "data/mekanism/neoforge/biome_modifier";
    private static final String TYPE_ADD_FEATURES = "neoforge:add_features";

    private FabricBiomeModifiers() {
    }

    /** One parsed official biome modifier: selection, step and the placed features it admits. */
    record OreModifier(BiomeSelector biomes, GenerationStep.Decoration step, List<ResourceLocation> features) {
    }

    /** Official {@code HolderSet<Biome>} JSON form: either {@code #tag} references or explicit biome IDs. */
    record BiomeSelector(List<ResourceLocation> tags, List<ResourceLocation> biomes) {
    }

    public static void initialize() {
        Path dir = FabricLoader.getInstance().getModContainer(MekanismAPI.MEKANISM_MODID)
              .orElseThrow(() -> new IllegalStateException("Mekanism mod container is missing"))
              .findPath(RESOURCE_DIR)
              .orElseThrow(() -> new IllegalStateException("Packaged Mekanism jar is missing " + RESOURCE_DIR));
        List<Path> files;
        try (Stream<Path> stream = Files.list(dir)) {
            files = stream.filter(path -> path.getFileName().toString().endsWith(".json")).sorted().toList();
        } catch (IOException failure) {
            throw new UncheckedIOException("Failed to enumerate " + RESOURCE_DIR, failure);
        }
        if (files.isEmpty()) {
            throw new IllegalStateException("No official biome modifiers found in " + RESOURCE_DIR);
        }
        int features = 0;
        for (Path file : files) {
            OreModifier modifier;
            try {
                modifier = parse(JsonParser.parseString(Files.readString(file)).getAsJsonObject());
            } catch (IOException failure) {
                throw new UncheckedIOException("Failed to read official biome modifier " + file, failure);
            }
            for (ResourceLocation feature : modifier.features()) {
                BiomeModifications.addFeature(
                      context -> matches(context, modifier.biomes()),
                      modifier.step(), ResourceKey.create(Registries.PLACED_FEATURE, feature));
                features++;
            }
        }
        LOGGER.info("MEKANISM_FABRIC_WORLDGEN_PROOF modifiers={} features={}", files.size(), features);
    }

    /** Strict parser for one official {@code neoforge:add_features} JSON object. */
    static OreModifier parse(JsonObject json) {
        String type = json.get("type").getAsString();
        if (!TYPE_ADD_FEATURES.equals(type)) {
            throw new IllegalStateException("Unsupported official biome modifier type: " + type);
        }
        return new OreModifier(parseBiomes(json.get("biomes")), parseStep(json.get("step").getAsString()),
              parseLocations(json.get("features")));
    }

    private static BiomeSelector parseBiomes(JsonElement element) {
        List<ResourceLocation> tags = new ArrayList<>();
        List<ResourceLocation> biomes = new ArrayList<>();
        for (String raw : rawStrings(element)) {
            //A leading '#' is the official tag-reference form of a HolderSet; everything else is a biome ID.
            //ResourceLocation itself cannot contain '#', so the prefix must be stripped before parsing.
            if (raw.startsWith("#")) {
                tags.add(ResourceLocation.parse(raw.substring(1)));
            } else {
                biomes.add(ResourceLocation.parse(raw));
            }
        }
        if (tags.isEmpty() && biomes.isEmpty()) {
            throw new IllegalStateException("Official biome modifier declares an empty biome set");
        }
        return new BiomeSelector(List.copyOf(tags), List.copyOf(biomes));
    }

    private static GenerationStep.Decoration parseStep(String step) {
        for (GenerationStep.Decoration decoration : GenerationStep.Decoration.values()) {
            if (decoration.getSerializedName().equals(step)) {
                return decoration;
            }
        }
        throw new IllegalStateException("Unknown official biome modifier decoration step: " + step);
    }

    private static List<ResourceLocation> parseLocations(JsonElement element) {
        List<String> raw = rawStrings(element);
        List<ResourceLocation> locations = new ArrayList<>(raw.size());
        for (String entry : raw) {
            locations.add(ResourceLocation.parse(entry));
        }
        return List.copyOf(locations);
    }

    private static List<String> rawStrings(JsonElement element) {
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            if (array.isEmpty()) {
                throw new IllegalStateException("Official biome modifier declares an empty location list");
            }
            List<String> entries = new ArrayList<>(array.size());
            for (JsonElement entry : array) {
                entries.add(entry.getAsString());
            }
            return entries;
        }
        return List.of(element.getAsString());
    }

    /** Applies the official {@code HolderSet<Biome>} match semantics against lazily-resolved lookups. */
    static boolean matches(BiomeSelector selector, Predicate<ResourceLocation> tagMatch, Predicate<ResourceLocation> biomeMatch) {
        for (ResourceLocation tag : selector.tags()) {
            if (tagMatch.test(tag)) {
                return true;
            }
        }
        for (ResourceLocation biome : selector.biomes()) {
            if (biomeMatch.test(biome)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matches(BiomeSelectionContext context, BiomeSelector selector) {
        return matches(selector,
              tag -> context.hasTag(TagKey.create(Registries.BIOME, tag)),
              biome -> context.getBiomeKey().location().equals(biome));
    }
}
