package mekanism.fabric.additions;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import mekanism.additions.common.registries.AdditionsBiomeModifierSerializers;
import mekanism.additions.common.registries.AdditionsStructureModifierSerializers;
import mekanism.additions.common.world.modifier.BabyEntitySpawnBiomeModifier;
import mekanism.additions.common.world.modifier.BabyEntitySpawnStructureModifier;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.server.packs.resources.ResourceManager;

/** Keep datapack selection in the world resource owner, and evaluate SERVER spawn policy at the actual spawn owner. */
public final class AdditionsSpawnModifiers {
    private AdditionsSpawnModifiers() {
    }

    public interface BiomeSpawns {
        void mekanismadditions$setModifiers(List<BabyEntitySpawnBiomeModifier> modifiers);
    }

    public interface StructureSpawns {
        void mekanismadditions$setModifiers(List<BabyEntitySpawnStructureModifier> modifiers);
    }

    public static void load(ResourceManager resources, RegistryAccess registries) {
        List<BabyEntitySpawnBiomeModifier> biomes = load(resources, registries, "neoforge/biome_modifier",
              AdditionsBiomeModifierSerializers.ID.toString(), AdditionsBiomeModifierSerializers.SPAWN_BABIES);
        List<BabyEntitySpawnStructureModifier> structures = load(resources, registries, "neoforge/structure_modifier",
              AdditionsStructureModifierSerializers.ID.toString(), AdditionsStructureModifierSerializers.SPAWN_BABIES);
        registries.registryOrThrow(Registries.BIOME).holders().forEach(biome ->
              ((BiomeSpawns) biome.value().getMobSettings()).mekanismadditions$setModifiers(biomes.stream()
                    .filter(modifier -> !biome.is(modifier.babyType().biomeBlacklist)).toList()));
        registries.registryOrThrow(Registries.STRUCTURE).holders().forEach(structure ->
              ((StructureSpawns) structure.value()).mekanismadditions$setModifiers(structures.stream()
                    .filter(modifier -> !structure.is(modifier.babyType().structureBlacklist)).toList()));
    }

    private static <T> List<T> load(ResourceManager resources, RegistryAccess registries, String directory, String type,
          com.mojang.serialization.MapCodec<T> codec) {
        var converter = FileToIdConverter.json(directory);
        List<T> modifiers = new ArrayList<>();
        for (var entry : converter.listMatchingResources(resources).entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).toList()) {
            var id = converter.fileToId(entry.getKey());
            if (!id.getNamespace().equals("mekanismadditions")) continue;
            try (var reader = entry.getValue().openAsReader()) {
                var json = JsonParser.parseReader(reader).getAsJsonObject();
                String resourceType = json.get("type").getAsString();
                if (resourceType.equals("neoforge:none")) continue;
                if (!resourceType.equals(type)) throw new IllegalArgumentException("Unsupported Additions modifier " + resourceType);
                modifiers.add(codec.codec().parse(registries.createSerializationContext(JsonOps.INSTANCE), json).getOrThrow());
            } catch (IOException | RuntimeException failure) {
                throw new IllegalStateException("Failed to load Additions modifier " + id, failure);
            }
        }
        return List.copyOf(modifiers);
    }
}
