package mekanism.fabric.datamaps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mojang.serialization.Lifecycle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import mekanism.api.datamaps.MekaSuitAbsorption;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
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
import net.minecraft.world.damagesource.DamageType;
import org.junit.jupiter.api.Test;

class FabricDataMapResourceStackIntegrationTest {
    @Test
    void realStacksPublishImmutableOwnerSessionAndSupportReferenceAndDirectQueries() throws Exception {
        assertEquals(EnvType.SERVER, FabricLoader.getInstance().getEnvironmentType());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        MappedRegistry<DamageType> damageTypes = new MappedRegistry<>(Registries.DAMAGE_TYPE, Lifecycle.stable());
        ResourceKey<DamageType> alphaKey = ResourceKey.create(Registries.DAMAGE_TYPE, id("alpha"));
        ResourceKey<DamageType> betaKey = ResourceKey.create(Registries.DAMAGE_TYPE, id("beta"));
        DamageType alpha = new DamageType("alpha", 0.1F);
        DamageType beta = new DamageType("beta", 0.1F);
        Holder.Reference<DamageType> alphaHolder = damageTypes.register(alphaKey, alpha, RegistrationInfo.BUILT_IN);
        Holder.Reference<DamageType> betaHolder = damageTypes.register(betaKey, beta, RegistrationInfo.BUILT_IN);
        damageTypes.bindTags(Map.of(TagKey.create(Registries.DAMAGE_TYPE,
              ResourceLocation.fromNamespaceAndPath("mekanism", "mekasuit_always_supported")), List.of(alphaHolder, betaHolder)));
        RegistryAccess.Frozen access = new RegistryAccess.Frozen() {
            @Override
            @SuppressWarnings("unchecked")
            public <E> Optional<Registry<E>> registry(ResourceKey<? extends Registry<? extends E>> key) {
                return key.equals(Registries.DAMAGE_TYPE) ? Optional.of((Registry<E>) damageTypes) : Optional.empty();
            }

            @Override
            public Stream<RegistryAccess.RegistryEntry<?>> registries() {
                return Stream.of(new RegistryAccess.RegistryEntry<>(Registries.DAMAGE_TYPE, damageTypes));
            }
        };

        Path root = Files.createTempDirectory("mekanism-datamap-knot-");
        Path low = root.resolve("low");
        Path high = root.resolve("high");
        write(low, "{\"values\":{\"#mekanism:mekasuit_always_supported\":{\"absorption\":1.0}}}");
        write(high, "{\"values\":{\"mekanism_datamap_knot:beta\":0.75},\"remove\":[\"mekanism_datamap_knot:alpha\"]}");
        List<PackResources> packs = List.of(pack("low", low), pack("high", high));
        FabricMekanismDataMaps.Session session = new FabricMekanismDataMaps.Session(Thread.currentThread());
        try (MultiPackResourceManager resources = new MultiPackResourceManager(PackType.SERVER_DATA, packs)) {
            session.load(resources, access, "knot-test");
            assertEquals(1, session.snapshot().epoch());
            assertEquals(Map.of(betaKey, new MekaSuitAbsorption(0.75F)), session.snapshot().values());
            assertNull(session.query(damageTypes, alphaHolder));
            assertEquals(new MekaSuitAbsorption(0.75F), session.query(damageTypes, betaHolder));
            assertEquals(new MekaSuitAbsorption(0.75F), session.query(damageTypes, Holder.direct(beta)));
            assertThrows(UnsupportedOperationException.class,
                  () -> session.snapshot().values().put(alphaKey, new MekaSuitAbsorption(0.2F)));
        }
        try (MultiPackResourceManager empty = new MultiPackResourceManager(PackType.SERVER_DATA, List.of())) {
            session.load(empty, access, "knot-disappearance");
        }
        assertEquals(2, session.snapshot().epoch());
        assertEquals(Map.of(), session.snapshot().values());
        session.close();
        assertThrows(IllegalStateException.class, session::snapshot);
        Files.walk(root).sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
            try {
                Files.deleteIfExists(path);
            } catch (java.io.IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static void write(Path root, String json) throws Exception {
        Path file = root.resolve("data/mekanism/data_maps/damage_type/mekasuit_absorption.json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, json);
    }

    private static PackResources pack(String id, Path root) {
        return new PathPackResources(new PackLocationInfo(id, Component.literal(id), PackSource.DEFAULT, Optional.empty()), root);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("mekanism_datamap_knot", path);
    }
}
