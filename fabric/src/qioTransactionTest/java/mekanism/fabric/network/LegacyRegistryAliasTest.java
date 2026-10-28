package mekanism.fabric.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import mekanism.common.registries.MekanismDataComponents;
import net.minecraft.SharedConstants;
import net.minecraft.core.DefaultedMappedRegistry;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LegacyRegistryAliasTest {
    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("mekanism", path);
    }

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        mekanism.fabric.chemical.FabricChemicalRegistryLifecycle.initialize();
        MekanismDataComponents.DATA_COMPONENTS.registerAll(BuiltInRegistries.DATA_COMPONENT_TYPE);
    }

    @Test
    void defaultedItemRegistryResolvesLegacyWithoutChangingCanonicalIdentity() {
        var registry = new DefaultedMappedRegistry<Item>("minecraft:air", Registries.ITEM, Lifecycle.stable(), false);
        var air = new Item(new Item.Properties());
        var upgrade = new Item(new Item.Properties());
        registry.register(ResourceKey.create(Registries.ITEM, ResourceLocation.withDefaultNamespace("air")), air, RegistrationInfo.BUILT_IN);
        registry.register(ResourceKey.create(Registries.ITEM, id("upgrade_chemical")), upgrade, RegistrationInfo.BUILT_IN);
        registry.freeze();
        assertSame(upgrade, registry.get(id("upgrade_gas")));
        assertSame(upgrade, registry.getOptional(id("upgrade_gas")).orElseThrow());
        assertSame(upgrade, registry.get(ResourceKey.create(Registries.ITEM, id("upgrade_gas"))));
        assertSame(upgrade, registry.getHolder(id("upgrade_gas")).orElseThrow().value());
        assertSame(upgrade, registry.asLookup().get(ResourceKey.create(Registries.ITEM, id("upgrade_gas"))).orElseThrow().value());
        assertEquals(id("upgrade_chemical"), registry.getKey(upgrade));
        assertFalse(registry.keySet().contains(id("upgrade_gas")));
        assertSame(air, registry.get(id("unrelated")));
    }

    @Test
    void realComponentPatchReadsOldIdsAndWritesCanonicalName() {
        String[][] aliases = {{"gases", "gas_tanks"}, {"infuse_types", "infusion_tanks"},
              {"pigments", "pigment_tanks"}, {"slurries", "slurry_tanks"}};
        for (String[] alias : aliases) {
            JsonObject tanks = new JsonObject();
            tanks.add(alias[1], new com.google.gson.JsonArray());
            JsonObject input = new JsonObject();
            input.add("mekanism:" + alias[0], tanks);
            var patch = DataComponentPatch.CODEC.parse(JsonOps.INSTANCE, input).getOrThrow();
            assertTrue(patch.get(MekanismDataComponents.ATTACHED_CHEMICALS.get()).isPresent());
            var encoded = DataComponentPatch.CODEC.encodeStart(JsonOps.INSTANCE, patch).getOrThrow().getAsJsonObject();
            assertTrue(encoded.has("mekanism:chemicals"));
            assertFalse(encoded.has("mekanism:" + alias[0]));
        }
        JsonObject removed = new JsonObject();
        removed.add("!mekanism:gases", new JsonObject());
        var patch = DataComponentPatch.CODEC.parse(JsonOps.INSTANCE, removed).getOrThrow();
        assertEquals(java.util.Optional.empty(), patch.get(MekanismDataComponents.ATTACHED_CHEMICALS.get()));
    }

    @Test
    void actualItemStackCodecReadsOldItemAndWritesCanonicalId() {
        var upgrade = net.minecraft.core.Registry.register(BuiltInRegistries.ITEM, id("upgrade_chemical"), new Item(new Item.Properties()));
        JsonObject input = new JsonObject();
        input.addProperty("id", "mekanism:upgrade_gas");
        input.addProperty("count", 2);
        var stack = net.minecraft.world.item.ItemStack.CODEC.parse(JsonOps.INSTANCE, input).getOrThrow();
        assertSame(upgrade, stack.getItem());
        assertEquals(2, stack.getCount());
        var encoded = net.minecraft.world.item.ItemStack.CODEC.encodeStart(JsonOps.INSTANCE, stack).getOrThrow().getAsJsonObject();
        assertEquals("mekanism:upgrade_chemical", encoded.get("id").getAsString());
    }

    @Test
    void realEntriesWinAndOtherRegistriesAreUntouched() {
        var registry = new MappedRegistry<Item>(Registries.ITEM, Lifecycle.stable());
        var old = new Item(new Item.Properties());
        var canonical = new Item(new Item.Properties());
        registry.register(ResourceKey.create(Registries.ITEM, id("upgrade_gas")), old, RegistrationInfo.BUILT_IN);
        registry.register(ResourceKey.create(Registries.ITEM, id("upgrade_chemical")), canonical, RegistrationInfo.BUILT_IN);
        registry.freeze();
        assertSame(old, registry.get(id("upgrade_gas")));
        assertSame(old, registry.getHolder(id("upgrade_gas")).orElseThrow().value());
        assertTrue(BuiltInRegistries.BLOCK.getHolder(id("upgrade_gas")).isEmpty());
    }
}
