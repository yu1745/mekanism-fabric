package mekanism.api.robit;

import com.mojang.serialization.MapCodec;
import java.util.Objects;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/** Canonical identities for the static serializers and the reload-owned dynamic skin registry. */
public final class RobitSkinRegistry {
    public static final ResourceKey<Registry<RobitSkin>> SKIN_KEY = ResourceKey.createRegistryKey(
          ResourceLocation.fromNamespaceAndPath("mekanism", "robit_skin"));
    public static final ResourceKey<Registry<MapCodec<? extends RobitSkin>>> SERIALIZER_KEY = ResourceKey.createRegistryKey(
          ResourceLocation.fromNamespaceAndPath("mekanism", "robit_skin_serializer"));
    public static final ResourceKey<RobitSkin> BASE = ResourceKey.create(SKIN_KEY, ResourceLocation.fromNamespaceAndPath("mekanism", "robit"));
    private static MappedRegistry<MapCodec<? extends RobitSkin>> serializers;

    private RobitSkinRegistry() {
    }

    public static synchronized void install(MappedRegistry<MapCodec<? extends RobitSkin>> candidate) {
        Objects.requireNonNull(candidate);
        if (candidate.key() != SERIALIZER_KEY || candidate instanceof net.minecraft.core.DefaultedRegistry<?>) {
            throw new IllegalArgumentException("Not the canonical plain robit serializer registry");
        }
        if (serializers != null && serializers != candidate) {
            throw new IllegalStateException("Robit serializer registry already installed");
        }
        serializers = candidate;
    }

    public static synchronized MappedRegistry<MapCodec<? extends RobitSkin>> serializers() {
        return Objects.requireNonNull(serializers, "Robit serializer registry has not been installed");
    }
}
