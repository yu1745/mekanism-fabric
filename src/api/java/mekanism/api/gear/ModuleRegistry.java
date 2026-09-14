package mekanism.api.gear;

import java.util.Objects;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/** Canonical module type registry identity, independent of module or item catalog initialization. */
public final class ModuleRegistry {
    public static final ResourceKey<Registry<ModuleData<?>>> KEY = ResourceKey.createRegistryKey(
          ResourceLocation.fromNamespaceAndPath("mekanism", "module"));
    private static MappedRegistry<ModuleData<?>> registry;

    private ModuleRegistry() {
    }

    public static synchronized void install(MappedRegistry<ModuleData<?>> candidate) {
        Objects.requireNonNull(candidate);
        if (candidate.key() != KEY || candidate instanceof net.minecraft.core.DefaultedRegistry<?>) {
            throw new IllegalArgumentException("Not the canonical plain module registry");
        }
        if (registry != null && registry != candidate) throw new IllegalStateException("Module registry already installed");
        registry = candidate;
    }

    public static synchronized MappedRegistry<ModuleData<?>> registry() {
        return Objects.requireNonNull(registry, "Module registry has not been installed");
    }
}
