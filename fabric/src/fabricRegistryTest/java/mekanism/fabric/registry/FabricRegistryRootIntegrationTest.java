package mekanism.fabric.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import mekanism.api.text.EnumColor;
import mekanism.common.resource.PrimaryResource;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.event.registry.RegistryAttribute;
import net.fabricmc.fabric.api.event.registry.RegistryAttributeHolder;
import mekanism.api.chemical.ChemicalRegistry;
import mekanism.api.recipes.ingredients.chemical.ChemicalIngredient;
import mekanism.api.recipes.ingredients.chemical.ChemicalIngredientTypeRegistry;
import mekanism.api.recipes.ingredients.chemical.CompoundChemicalIngredient;
import mekanism.api.recipes.ingredients.chemical.DifferenceChemicalIngredient;
import mekanism.api.recipes.ingredients.chemical.EmptyChemicalIngredient;
import mekanism.api.recipes.ingredients.chemical.IntersectionChemicalIngredient;
import mekanism.api.recipes.ingredients.chemical.SingleChemicalIngredient;
import mekanism.api.recipes.ingredients.chemical.TagChemicalIngredient;
import mekanism.fabric.chemical.FabricChemicalIngredientTypeRegistryLifecycle;
import mekanism.fabric.chemical.FabricChemicalRegistryLifecycle;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;

@TestMethodOrder(OrderAnnotation.class)
class FabricRegistryRootIntegrationTest {
    private static final String NAMESPACE = "mekanism_registry_root_test";
    private static final ResourceKey<Registry<String>> REGISTRY_KEY = ResourceKey.createRegistryKey(id("synced_values"));
    private static final ResourceKey<String> DEFAULT_KEY = ResourceKey.create(REGISTRY_KEY, id("default"));

    @Test
    @Order(1)
    @SuppressWarnings({"unchecked", "rawtypes"})
    void realFabricRootRegistrationAndAutomaticVanillaFreeze() {
        assertEquals(EnvType.SERVER, FabricLoader.getInstance().getEnvironmentType());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        FabricChemicalRegistryLifecycle.initialize();
        //The root is still writable in this Fabric loader window; the real construction owner root-publishes the synchronized ingredient-type registry here.
        //The ordinary JVM freezes the root at Bootstrap.bootStrap, so this genuine root publication is observable only under the loader window proven above.
        FabricChemicalIngredientTypeRegistryLifecycle.initialize();

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

    @Test
    @Order(2)
    void productionChemicalRegistryIsCanonicalSyncedAndAutomaticallyFrozen() {
        FabricDefaultedRegistry<mekanism.api.chemical.Chemical> registry = FabricChemicalRegistryLifecycle.registry();
        assertEquals(65, registry.size());
        assertEquals(FabricCustomRegistryLifecycle.FROZEN, registry.lifecycle());
        assertSame(ChemicalRegistry.empty(), ChemicalRegistry.emptyHolder().value());
        assertSame(ChemicalRegistry.emptyHolder(), registry.getHolder(ChemicalRegistry.EMPTY_KEY).orElseThrow());
        List<String> expected = new ArrayList<>(List.of("bio", "fungi", "tin", "gold", "refined_obsidian", "diamond", "redstone", "carbon",
              "hydrogen", "oxygen", "steam", "water_vapor", "chlorine", "sulfur_dioxide", "sulfur_trioxide", "sulfuric_acid",
              "hydrogen_chloride", "hydrofluoric_acid", "uranium_oxide", "uranium_hexafluoride", "ethene", "sodium",
              "superheated_sodium", "brine", "lithium", "osmium", "fissile_fuel", "nuclear_waste", "spent_nuclear_waste",
              "plutonium", "polonium", "antimatter"));
        for (EnumColor color : EnumColor.values()) expected.add(color.getRegistryPrefix());
        for (PrimaryResource resource : PrimaryResource.values()) {
            expected.add("dirty_" + resource.getRegistrySuffix());
            expected.add("clean_" + resource.getRegistrySuffix());
        }
        assertEquals(ChemicalRegistry.EMPTY_KEY.location(), registry.getKey(registry.byId(0)));
        assertEquals(64, expected.size());
        for (int index = 0; index < expected.size(); index++) {
            assertEquals(ResourceLocation.fromNamespaceAndPath("mekanism", expected.get(index)), registry.getKey(registry.byId(index + 1)),
                  "chemical raw ID declaration order at " + (index + 1));
        }
        RegistryAttributeHolder attributes = RegistryAttributeHolder.get(registry);
        assertTrue(attributes.hasAttribute(RegistryAttribute.MODDED));
        assertTrue(attributes.hasAttribute(RegistryAttribute.SYNCED));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(NAMESPACE, path);
    }

    @Test
    @Order(3)
    @SuppressWarnings({"unchecked", "rawtypes"})
    void productionChemicalIngredientTypeRegistryIsCanonicalRootedSyncedAndFrozen() {
        MappedRegistry<ChemicalIngredient> registry =
              (MappedRegistry) FabricChemicalIngredientTypeRegistryLifecycle.initialize();
        assertSame(registry, FabricChemicalIngredientTypeRegistryLifecycle.registry());
        assertSame(registry, ChemicalIngredientTypeRegistry.registry());
        assertEquals(6, registry.size());
        assertTrue(ChemicalIngredientTypeRegistry.isFrozen());
        //The genuine registry identity is root-published and synchronized, not a private store hidden from root/network access.
        Registry<Registry<?>> root = (Registry) BuiltInRegistries.REGISTRY;
        assertSame(registry, root.get(ChemicalIngredientTypeRegistry.REGISTRY_ID));
        assertSame(ChemicalIngredientTypeRegistry.REGISTRY_KEY, root.getResourceKey(registry).orElseThrow());
        RegistryAttributeHolder attributes = RegistryAttributeHolder.get(registry);
        assertTrue(attributes.hasAttribute(RegistryAttribute.MODDED));
        assertTrue(attributes.hasAttribute(RegistryAttribute.SYNCED));
        //Six built-in codecs in official order, with exact codec identity and ID round trip through the native identity/id maps.
        List<ResourceLocation> ids = List.of(ChemicalIngredientTypeRegistry.COMPOUND, ChemicalIngredientTypeRegistry.DIFFERENCE,
              ChemicalIngredientTypeRegistry.EMPTY, ChemicalIngredientTypeRegistry.INTERSECTION, ChemicalIngredientTypeRegistry.SINGLE,
              ChemicalIngredientTypeRegistry.TAG);
        List<com.mojang.serialization.MapCodec<? extends ChemicalIngredient>> codecs = List.of(CompoundChemicalIngredient.CODEC,
              DifferenceChemicalIngredient.CODEC, EmptyChemicalIngredient.CODEC, IntersectionChemicalIngredient.CODEC,
              SingleChemicalIngredient.CODEC, TagChemicalIngredient.CODEC);
        for (int index = 0; index < ids.size(); index++) {
            ResourceLocation id = ids.get(index);
            com.mojang.serialization.MapCodec<? extends ChemicalIngredient> codec = codecs.get(index);
            assertSame(codec, ChemicalIngredientTypeRegistry.codec(id), id.toString());
            assertEquals(id, ChemicalIngredientTypeRegistry.idOf(codec), id.toString());
            assertSame(codec, registry.get(id), id.toString());
        }
        assertEquals(ids.size(), new java.util.LinkedHashSet<>(ids).size());
        //Unknown IDs must not silently default, and duplicate/late registration must fail rather than overwrite the canonical entries.
        assertThrows(IllegalArgumentException.class,
              () -> ChemicalIngredientTypeRegistry.codec(ResourceLocation.fromNamespaceAndPath("mekanism", "unknown")));
        assertThrows(IllegalStateException.class,
              () -> ChemicalIngredientTypeRegistry.register(ChemicalIngredientTypeRegistry.COMPOUND, CompoundChemicalIngredient.CODEC));
        assertThrows(IllegalStateException.class, () -> ChemicalIngredientTypeRegistry.install(
              new MappedRegistry<>(ChemicalIngredientTypeRegistry.REGISTRY_KEY, com.mojang.serialization.Lifecycle.stable(), false)));
        //already-frozen construction owner calls are idempotent and return the one canonical registry.
        assertSame(registry, FabricChemicalIngredientTypeRegistryLifecycle.initialize());
    }
}
