package mekanism.fabric.worldgen;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.io.Reader;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import mekanism.api.MekanismAPI;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Applies Mekanism-namespace add_features/none resources from the active world's datapacks through one Fabric modifier.
 * Other modifier types are rejected; this is not a general NeoForge biome-modifier registry.
 */
public final class FabricBiomeModifiers {
    private static final Logger LOGGER = LoggerFactory.getLogger("Mekanism/Fabric");
    private static final List<java.util.function.BiConsumer<ResourceManager, RegistryAccess>> MODULE_FINALIZERS = new ArrayList<>();

    /** Optional Mekanism modules finish their own worldgen data in this world's real resource scope. */
    public static void registerModuleFinalizer(java.util.function.BiConsumer<ResourceManager, RegistryAccess> finalizer) {
        MODULE_FINALIZERS.add(finalizer);
    }

    private static final FileToIdConverter MODIFIERS = FileToIdConverter.json("neoforge/biome_modifier");
    // Do not retain a resource manager if server construction fails before Fabric's worldgen finalization.
    private static final ThreadLocal<WeakReference<ResourceManager>> PENDING_RESOURCES = new ThreadLocal<>();
    private static final ThreadLocal<List<OreModifier>> ACTIVE_MODIFIERS = new ThreadLocal<>();

    private FabricBiomeModifiers() {
    }

    record OreModifier(HolderSet<Biome> biomes, GenerationStep.Decoration step, HolderSet<PlacedFeature> features) {
    }

    /** Extension of Fabric's current generation context, preserving its freeze and feature-rebuild lifecycle. */
    public interface FeatureAppender {
        void mekanism$appendFeature(GenerationStep.Decoration step, Holder<PlacedFeature> feature);
    }

    public static void initialize() {
        BiomeModifications.create(ResourceLocation.fromNamespaceAndPath(MekanismAPI.MEKANISM_MODID, "datapack_ore_features"))
              .add(ModificationPhase.ADDITIONS,
                    context -> activeModifiers().stream().anyMatch(modifier -> modifier.biomes().contains(context.getBiomeRegistryEntry())),
                    (selection, modification) -> {
                        FeatureAppender generation = (FeatureAppender) modification.getGenerationSettings();
                        for (OreModifier modifier : activeModifiers()) {
                            if (modifier.biomes().contains(selection.getBiomeRegistryEntry())) {
                                modifier.features().forEach(feature -> generation.mekanism$appendFeature(modifier.step(), feature));
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

    public static void captureWorldResources(ResourceManager resources) {
        PENDING_RESOURCES.set(new WeakReference<>(resources));
    }

    /** Runs Fabric's synchronous worldgen finalization in the current world's resource and registry scope. */
    public static void withWorldModifiers(RegistryAccess registries, Runnable finalizeWorldGen) {
        WeakReference<ResourceManager> pending = PENDING_RESOURCES.get();
        ResourceManager resources = pending == null ? null : pending.get();
        PENDING_RESOURCES.remove();
        if (resources == null || ACTIVE_MODIFIERS.get() != null) {
            throw new IllegalStateException("Missing or nested Mekanism worldgen resource scope");
        }
        ACTIVE_MODIFIERS.set(load(resources, registries));
        try {
            finalizeWorldGen.run();
            MODULE_FINALIZERS.forEach(finalizer -> finalizer.accept(resources, registries));
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

    /** Decode the same registry-aware holder-set codecs used by NeoForge's add_features modifier. */
    static OreModifier parse(JsonObject json, RegistryAccess registries) {
        String type = json.get("type").getAsString();
        if (!"neoforge:add_features".equals(type)) {
            throw new IllegalStateException("Unsupported official biome modifier type: " + type);
        }
        var ops = registries.createSerializationContext(JsonOps.INSTANCE);
        return new OreModifier(Biome.LIST_CODEC.parse(ops, json.get("biomes")).getOrThrow(),
              GenerationStep.Decoration.CODEC.parse(ops, json.get("step")).getOrThrow(),
              PlacedFeature.LIST_CODEC.parse(ops, json.get("features")).getOrThrow());
    }
}
