package mekanism.fabric.datamaps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Lifecycle;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import mekanism.api.datamaps.MekaSuitAbsorption;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.tags.TagKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FabricDataMapSemanticsTest {
    private static final String NS = "datamap_test";
    private static final ResourceKey<Registry<String>> REGISTRY = ResourceKey.createRegistryKey(id("values"));
    private static final FabricDataMapType<String, Float> TYPE = new FabricDataMapType<>(REGISTRY, id("ordinary"), Codec.floatRange(0, 1));

    @TempDir
    Path temporary;

    @Test
    void descriptorIdentityAndCatalogCollisionsUseRegistryAndIdTuple() {
        FabricDataMapType<String, Float> sameTuple = new FabricDataMapType<>(REGISTRY, TYPE.id(), Codec.FLOAT);
        assertNotEquals(TYPE, sameTuple);
        assertEquals(TYPE, TYPE);
        assertThrows(IllegalStateException.class, () -> FabricDataMapLoader.validateCatalog(List.of(TYPE, sameTuple)));

        ResourceKey<Registry<String>> otherRegistry = ResourceKey.createRegistryKey(id("other_values"));
        FabricDataMapType<String, Float> otherTuple = new FabricDataMapType<>(otherRegistry, TYPE.id(), Codec.FLOAT);
        FabricDataMapLoader.validateCatalog(List.of(TYPE, otherTuple));
        assertEquals(ResourceLocation.fromNamespaceAndPath(NS, "data_maps/datamap_test/values/ordinary.json"),
              FabricDataMapLoader.fileLocation(TYPE));
    }

    @Test
    void valueAlternativesPreserveWrapperReplaceAndObjectFirstCodec() {
        FabricDataMapType<String, MekaSuitAbsorption> meka = new FabricDataMapType<>(REGISTRY, id("meka"), MekaSuitAbsorption.CODEC);
        assertEquals(new MekaSuitAbsorption(0.75F), FabricDataMapLoader.decodeEntry(com.google.gson.JsonParser.parseString("0.75"), meka).value());
        assertEquals(new MekaSuitAbsorption(0.75F), FabricDataMapLoader.decodeEntry(com.google.gson.JsonParser.parseString("{\"absorption\":0.75}"), meka).value());
        FabricDataMapLoader.Entry<MekaSuitAbsorption> wrapped = FabricDataMapLoader.decodeEntry(
              com.google.gson.JsonParser.parseString("{\"value\":{\"absorption\":0.75},\"replace\":true}"), meka);
        assertTrue(wrapped.replace());
        FabricDataMapLoader.Entry<MekaSuitAbsorption> objectFirst = FabricDataMapLoader.decodeEntry(
              com.google.gson.JsonParser.parseString("{\"absorption\":0.75,\"replace\":true}"), meka);
        assertFalse(objectFirst.replace());
        assertThrows(IllegalArgumentException.class,
              () -> FabricDataMapLoader.decodeEntry(com.google.gson.JsonParser.parseString("1.01"), meka));
    }

    @Test
    void lowToHighLayersExpandTagsReplaceThenWriteThenRemove() throws Exception {
        MappedRegistry<String> registry = registry("a", "b", "c");
        bind(registry, "group", "a", "b");
        Path low = pack("low", """
              {"values":{"#datamap_test:group":0.2,"datamap_test:c":0.3}}
              """);
        Path high = pack("high", """
              {"replace":true,"values":{"#datamap_test:group":0.5,"datamap_test:b":{"value":0.7,"replace":true}},"remove":["datamap_test:a"]}
              """);
        try (MultiPackResourceManager resources = resources(low, high)) {
            FabricDataMapLoader.Result<String, Float> result = FabricDataMapLoader.load(resources, provider(registry), TYPE);
            assertEquals(Map.of(key("b"), 0.7F), result.values());
            assertEquals(0, result.skippedLayers());
        }
    }

    @Test
    void badLayersAreAtomicWhileUnknownTargetsAndConditionsAreRejected() throws Exception {
        MappedRegistry<String> registry = registry("known");
        Path low = pack("low", """
              {"extra":"ignored","values":{"datamap_test:known":0.4,"datamap_test:missing":0.8},"remove":["datamap_test:absent","#datamap_test:empty"]}
              """);
        Path malformed = pack("malformed", "{\"replace\":true,\"values\":{\"datamap_test:known\":0.9,\"bad id\":0.2}}");
        Path conditional = pack("conditional", """
              {"replace":true,"values":{"datamap_test:known":{"value":0.9,"neoforge:conditions":[]}}}
              """);
        try (MultiPackResourceManager resources = resources(low, malformed, conditional)) {
            FabricDataMapLoader.Result<String, Float> result = FabricDataMapLoader.load(resources, provider(registry), TYPE);
            assertEquals(Map.of(key("known"), 0.4F), result.values());
            assertEquals(2, result.skippedLayers());
        }
    }

    private MappedRegistry<String> registry(String... names) {
        MappedRegistry<String> registry = new MappedRegistry<>(REGISTRY, Lifecycle.stable());
        for (String name : names) {
            registry.register(key(name), name, RegistrationInfo.BUILT_IN);
        }
        return registry;
    }

    private static void bind(MappedRegistry<String> registry, String tag, String... names) {
        TagKey<String> key = TagKey.create(REGISTRY, id(tag));
        List<Holder<String>> holders = Stream.of(names).map(name -> (Holder<String>) registry.getHolderOrThrow(key(name))).toList();
        registry.bindTags(Map.of(key, holders));
    }

    private static HolderLookup.Provider provider(MappedRegistry<String> registry) {
        return HolderLookup.Provider.create(Stream.of(registry.asLookup()));
    }

    private Path pack(String name, String json) throws IOException {
        Path root = temporary.resolve(name);
        Path file = root.resolve("data/datamap_test/data_maps/datamap_test/values/ordinary.json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, json);
        return root;
    }

    private static MultiPackResourceManager resources(Path... roots) {
        List<PackResources> packs = Stream.of(roots).map(root -> (PackResources) new PathPackResources(
              new PackLocationInfo(root.getFileName().toString(), Component.literal(root.getFileName().toString()), PackSource.DEFAULT, Optional.empty()), root)).toList();
        return new MultiPackResourceManager(PackType.SERVER_DATA, packs);
    }

    private static ResourceKey<String> key(String path) {
        return ResourceKey.create(REGISTRY, id(path));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(NS, path);
    }
}
