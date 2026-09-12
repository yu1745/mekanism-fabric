package mekanism.fabric.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.event.registry.RegistryAttribute;
import net.fabricmc.fabric.api.event.registry.RegistryAttributeHolder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;

class FabricRegistryRootIntegrationTest {
    private static final String NAMESPACE = "mekanism_registry_root_test";
    private static final ResourceKey<Registry<String>> REGISTRY_KEY = ResourceKey.createRegistryKey(id("synced_values"));
    private static final ResourceKey<String> DEFAULT_KEY = ResourceKey.create(REGISTRY_KEY, id("default"));

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void realFabricRootRegistrationAndAutomaticVanillaFreeze() {
        assertEquals(EnvType.SERVER, FabricLoader.getInstance().getEnvironmentType());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();

        FabricDefaultedRegistry<String> registry = FabricDefaultedRegistry.create(REGISTRY_KEY, DEFAULT_KEY, true);
        assertSame(registry, registry.registerRoot());
        assertEquals(FabricCustomRegistryLifecycle.ROOT_REGISTERED, registry.lifecycle());
        Registry<Registry<?>> root = (Registry) BuiltInRegistries.REGISTRY;
        assertSame(registry, root.get(REGISTRY_KEY.location()));
        assertSame(REGISTRY_KEY, root.getResourceKey(registry).orElseThrow());
        RegistryAttributeHolder attributes = RegistryAttributeHolder.get(registry);
        assertTrue(attributes.hasAttribute(RegistryAttribute.MODDED));
        assertTrue(attributes.hasAttribute(RegistryAttribute.SYNCED));

        FabricDefaultedRegistry<String> conflict = FabricDefaultedRegistry.create(REGISTRY_KEY, DEFAULT_KEY, true);
        assertThrows(IllegalStateException.class, conflict::registerRoot);
        assertEquals(FabricCustomRegistryLifecycle.CREATED, conflict.lifecycle());
        assertSame(registry, BuiltInRegistries.REGISTRY.get(REGISTRY_KEY.location()));

        String defaultValue = new String("default");
        String secondValue = new String("second");
        Holder.Reference<String> defaultIntrusiveHolder = registry.createIntrusiveHolder(defaultValue);
        registry.createIntrusiveHolder(secondValue);
        FabricDeferredRegister<String> deferred = new FabricDeferredRegister<>(REGISTRY_KEY, NAMESPACE);
        FabricDeferredHolder<String> defaultHolder = deferred.register("default", () -> defaultValue);
        deferred.register("second", () -> secondValue);
        deferred.registerAll(registry);
        assertSame(defaultIntrusiveHolder, defaultHolder.canonicalHolder());
        assertSame(defaultHolder.canonicalHolder(), registry.canonicalDefaultHolder());
        assertSame(defaultHolder.canonicalHolder(), registry.getHolderOrThrow(DEFAULT_KEY));

        BuiltInRegistries.bootStrap();

        assertEquals(FabricCustomRegistryLifecycle.FROZEN, registry.lifecycle());
        assertSame(registry, registry.freeze());
        assertThrows(IllegalStateException.class,
              () -> registry.register(ResourceKey.create(REGISTRY_KEY, id("late")), new String("late"), RegistrationInfo.BUILT_IN));
        assertThrows(IllegalStateException.class, () -> registry.createIntrusiveHolder(new String("late")));
        assertThrows(IllegalStateException.class, registry::registerRoot);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(NAMESPACE, path);
    }
}
