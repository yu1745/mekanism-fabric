package mekanism.fabric.worldgen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import mekanism.api.MekanismAPI;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectionContext;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
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
 * <p>Fabric has no NeoForge biome-modifier registry. A single Fabric biome modification consumes the active world's
 * winning Mekanism-namespace modifier resources during Fabric's synchronous worldgen finalization, rather than reading
 * the mod jar during initialization or registering callbacks again for every world. Supports the official ore
 * {@code neoforge:add_features} form and {@code neoforge:none}; other types are rejected, not silently discarded.
 * Biome tag matching occurs against the world's resolved tags.</p>
 */
public final class FabricBiomeModifiers {
    private static final Logger LOGGER = LoggerFactory.getLogger("Mekanism/Fabric");
    private static final FileToIdConverter MODIFIERS = FileToIdConverter.json("neoforge/biome_modifier");
    private static final String TYPE_ADD_FEATURES = "neoforge:add_features";
    // A constructor that fails before Fabric's finalization must not retain its resource manager on the launch thread.
    private static final ThreadLocal<WeakReference<ResourceManager>> PENDING_RESOURCES = new ThreadLocal<>();
    private static final ThreadLocal<List<OreModifier>> ACTIVE_MODIFIERS = new ThreadLocal<>();

    private FabricBiomeModifiers() {
    }

    /** One parsed official biome modifier: selection, step and the placed features it admits. */
    record OreModifier(BiomeSelector biomes, GenerationStep.Decoration step, List<ResourceLocation> features) {
    }

    /** Official {@code HolderSet<Biome>} JSON form: either {@code #tag} references or explicit biome IDs. */
    record BiomeSelector(List<ResourceLocation> tags, List<ResourceLocation> biomes) {
    }

    public static void initialize() {
        BiomeModifications.create(ResourceLocation.fromNamespaceAndPath(MekanismAPI.MEKANISM_MODID, "datapack_ore_features"))
              .add(ModificationPhase.ADDITIONS, context -> activeModifiers().stream().anyMatch(modifier -> matches(context, modifier.biomes())),
                    (selection, modification) -> {
                  for (OreModifier modifier : activeModifiers()) {
                      if (matches(selection, modifier.biomes())) {
                          for (ResourceLocation feature : modifier.features()) {
                              modification.getGenerationSettings().addFeature(modifier.step(), ResourceKey.create(Registries.PLACED_FEATURE, feature));
                          }
                      }
                  }
              });
    }

    private static List<OreModifier> activeModifiers() {
        List<OreModifier> modifiers = ACTIVE_MODIFIERS.get();
        if (modifiers == null) {
            throw new IllegalStateException("Mekanism biome modification ran outside world resource scope");
        }
        return modifiers;
    }

    /** Called when the server has obtained this world's active pack resources. */
    public static void captureWorldResources(ResourceManager resources) {
        PENDING_RESOURCES.set(new WeakReference<>(resources));
    }

    /** Runs Fabric's synchronous worldgen finalization with this world's resources, never a process-wide callback list. */
    public static void withWorldModifiers(RegistryAccess registries, Runnable finalizeWorldGen) {
        WeakReference<ResourceManager> pending = PENDING_RESOURCES.get();
        ResourceManager resources = pending == null ? null : pending.get();
        PENDING_RESOURCES.remove();
        if (resources == null || ACTIVE_MODIFIERS.get() != null) {
            throw new IllegalStateException("Missing or nested Mekanism worldgen resource scope");
        }
        List<OreModifier> modifiers = load(resources, registries);
        ACTIVE_MODIFIERS.set(modifiers);
        try {
            finalizeWorldGen.run();
        } finally {
            ACTIVE_MODIFIERS.remove();
        }
    }

    static List<OreModifier> load(ResourceManager resources, RegistryAccess registries) {
        List<OreModifier> modifiers = new ArrayList<>();
        Map<ResourceLocation, Resource> files = MODIFIERS.listMatchingResources(resources);
        for (var entry : files.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            ResourceLocation id = MODIFIERS.fileToId(entry.getKey());
            if (!id.getNamespace().equals(MekanismAPI.MEKANISM_MODID)) {
                continue;
            }
            try (Reader reader = entry.getValue().openAsReader()) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                if (!"neoforge:none".equals(json.get("type").getAsString())) {
                    modifiers.add(parse(json, registries));
                }
            } catch (IOException | RuntimeException failure) {
                throw new IllegalStateException("Failed to load biome modifier " + id, failure);
            }
        }
        int featureCount = modifiers.stream().mapToInt(modifier -> modifier.features().size()).sum();
        LOGGER.info("MEKANISM_FABRIC_WORLDGEN_PROOF modifiers={} features={} source=active_datapacks", modifiers.size(), featureCount);
        return List.copyOf(modifiers);
    }

    /** Strict parser for one official {@code neoforge:add_features} JSON object. */
    static OreModifier parse(JsonObject json, RegistryAccess registries) {
        String type = json.get("type").getAsString();
        if (!TYPE_ADD_FEATURES.equals(type)) {
            throw new IllegalStateException("Unsupported official biome modifier type: " + type);
        }
        return new OreModifier(parseBiomes(json.get("biomes")), parseStep(json.get("step").getAsString()),
              parseLocations(json.get("features"), registries));
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

    private static List<ResourceLocation> parseLocations(JsonElement element, RegistryAccess registries) {
        if (element.isJsonPrimitive() && element.getAsString().startsWith("#")) {
            ResourceLocation id = ResourceLocation.parse(element.getAsString().substring(1));
            var registry = registries.registryOrThrow(Registries.PLACED_FEATURE);
            var tag = TagKey.create(Registries.PLACED_FEATURE, id);
            return registry.getTag(tag).orElseThrow(() -> new IllegalStateException("Missing placed feature tag: " + id))
                  .stream().map(holder -> holder.unwrapKey().orElseThrow(() -> new IllegalStateException("Unregistered placed feature in tag " + id)).location())
                  .toList();
        }
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
