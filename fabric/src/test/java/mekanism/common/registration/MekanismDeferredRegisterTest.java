package mekanism.common.registration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicReference;
import mekanism.common.registration.impl.GameEventDeferredRegister;
import mekanism.common.registries.MekanismGameEvents;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.gameevent.GameEvent;
import org.junit.jupiter.api.Test;

class MekanismDeferredRegisterTest {
    private static final ResourceKey<Registry<String>> TEST_REGISTRY = ResourceKey.createRegistryKey(
          ResourceLocation.fromNamespaceAndPath("test", "values"));

    @Test
    void wrapperPreservesLazyFactoryKeyAndCanonicalIdentity() {
        MekanismDeferredRegister<String> register = new MekanismDeferredRegister<>(TEST_REGISTRY, "test");
        ResourceLocation expectedId = ResourceLocation.fromNamespaceAndPath("test", "value");
        AtomicReference<ResourceLocation> factoryId = new AtomicReference<>();
        AtomicReference<String> created = new AtomicReference<>();
        String canonical = new String("canonical");
        MekanismDeferredHolder<String, String> holder = register.register("value", id -> {
            factoryId.set(id);
            String value = new String("created");
            created.set(value);
            return value;
        });

        assertEquals(expectedId, holder.getId());
        assertEquals("value", holder.getName());
        assertEquals(ResourceKey.create(TEST_REGISTRY, expectedId), holder.getKey());
        MekanismDeferredHolder<String, String> equivalent = new MekanismDeferredHolder<>(holder.getKey());
        assertEquals(holder, equivalent);
        assertEquals(equivalent, holder);
        assertEquals(holder.hashCode(), equivalent.hashCode());
        assertFalse(holder.equals(Holder.direct("created")));
        assertEquals(Holder.Kind.REFERENCE, holder.kind());
        assertTrue(holder.unwrapKey().filter(holder.getKey()::equals).isPresent());
        assertTrue(holder.is(expectedId));
        assertTrue(holder.is(holder.getKey()));
        assertTrue(holder.keyMatches(holder));
        assertFalse(holder.isBound());
        assertTrue(holder.asOptional().isEmpty());
        assertNull(factoryId.get());
        assertThrows(IllegalStateException.class, holder::get);

        register.registerAll((registryKey, key, value) -> {
            assertEquals(TEST_REGISTRY, registryKey);
            assertEquals(holder.getKey(), key);
            assertSame(created.get(), value);
            return canonical;
        });

        assertEquals(expectedId, factoryId.get());
        assertTrue(holder.isBound());
        assertSame(canonical, holder.get());
        assertSame(canonical, holder.value());
        assertSame(canonical, holder.asOptional().orElseThrow());
        assertTrue(holder.is(canonical));
    }

    @Test
    void selfReferentialFactoriesCaptureTheRegisteredHolderNotAnUnattachedAlias() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ResourceKey<Registry<LinkedValue>> key = ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath("test", "linked_values"));
        MekanismDeferredRegister<LinkedValue> register = new MekanismDeferredRegister<>(key, "test");
        MekanismDeferredHolder<LinkedValue, LinkedValue> holder = register.registerWithHolder("menu", LinkedValue::new);
        assertSame(holder, register.getEntries().getFirst());
        assertFalse(holder.isBound());
        var registry = new net.minecraft.core.MappedRegistry<>(key, com.mojang.serialization.Lifecycle.stable(), false);
        register.registerAll(registry);
        registry.freeze();
        assertSame(holder, holder.get().type());
        assertSame(registry.get(holder.getId()), holder.get());
        assertSame(holder.get(), holder.get().type().get());
    }

    private record LinkedValue(MekanismDeferredHolder<LinkedValue, LinkedValue> type) {
    }

    @Test
    void pairedWrapperSharesThePrimaryBindingBeforeTheSecondaryFactoryRuns() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ResourceKey<Registry<String>> secondaryKey = ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath("test", "secondary"));
        DoubleDeferredRegister<String, String> register = new DoubleDeferredRegister<>("test", TEST_REGISTRY, secondaryKey);
        DoubleWrappedRegistryObject<String, String, String, String> holder = register.register("paired",
              () -> new String("block"), primary -> primary + " item", DoubleWrappedRegistryObject::new);
        assertFalse(holder.isBound());
        var primary = new net.minecraft.core.MappedRegistry<>(TEST_REGISTRY, com.mojang.serialization.Lifecycle.stable(), false);
        var secondary = new net.minecraft.core.MappedRegistry<>(secondaryKey, com.mojang.serialization.Lifecycle.stable(), false);
        register.registerAll(primary, secondary);
        primary.freeze();
        secondary.freeze();
        assertSame(register.getPrimaryEntries().iterator().next().get(), holder.get());
        assertSame(primary.get(holder.getId()), holder.get());
        assertTrue(holder.is(primary.getHolderOrThrow(holder.getKey())));
        assertTrue(holder.canSerializeIn(primary));
        assertEquals("block item", holder.getSecondary());
        assertSame(secondary.get(holder.getId()), holder.getSecondary());
    }

    @Test
    void gameEventRegisterPreservesDefaultAndExplicitRadii() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        GameEventDeferredRegister register = new GameEventDeferredRegister("test_events");
        MekanismDeferredHolder<GameEvent, GameEvent> defaultRadius = register.register("default");
        MekanismDeferredHolder<GameEvent, GameEvent> explicitRadius = register.register("explicit", 32);

        ((MekanismDeferredRegister<GameEvent>) register).registerAll((registryKey, key, value) -> value);

        assertEquals(16, defaultRadius.get().notificationRadius());
        assertEquals(32, explicitRadius.get().notificationRadius());
    }

    @Test
    void officialGameEventDeclarationHasAllStableIds() {
        assertEquals(ResourceLocation.fromNamespaceAndPath("mekanism", "seismic_vibration"), MekanismGameEvents.SEISMIC_VIBRATION.getId());
        assertEquals(ResourceLocation.fromNamespaceAndPath("mekanism", "jetpack_burn"), MekanismGameEvents.JETPACK_BURN.getId());
        assertEquals(ResourceLocation.fromNamespaceAndPath("mekanism", "gravity_modulate"), MekanismGameEvents.GRAVITY_MODULATE.getId());
        assertEquals(ResourceLocation.fromNamespaceAndPath("mekanism", "gravity_modulate_boosted"), MekanismGameEvents.GRAVITY_MODULATE_BOOSTED.getId());
        assertFalse(MekanismGameEvents.SEISMIC_VIBRATION.isBound());
    }
}
