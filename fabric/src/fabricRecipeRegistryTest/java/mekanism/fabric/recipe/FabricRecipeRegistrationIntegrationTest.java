package mekanism.fabric.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.List;
import mekanism.api.recipes.MekanismKeyedHolder;
import mekanism.api.recipes.MekanismRecipeSerializers;
import mekanism.api.recipes.MekanismRecipeTypes;
import mekanism.api.recipes.ItemStackToItemStackRecipe;
import mekanism.api.recipes.basic.BasicCrushingRecipe;
import mekanism.api.recipes.ingredients.ItemStackIngredient;
import mekanism.api.recipes.ingredients.creator.IngredientCreatorAccess;
import mekanism.common.registries.MekanismChemicalIngredientTypes;
import mekanism.fabric.chemical.FabricChemicalLifecycle;
import mekanism.fabric.chemical.FabricChemicalRegistryLifecycle;
import mekanism.fabric.registry.FabricDefaultedRegistry;
import net.minecraft.SharedConstants;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Real recipe type/serializer registration, canonical identity, and datapack decoding in a fresh server-side Knot worker.
 *
 * <p>Registration must precede {@code Bootstrap.bootStrap()}, because the vanilla built-in registries freeze there. That ordering constraint is why this contract
 * lives in its own source set instead of the shared ordinary JVM, which is bootstrapped once and cannot register afterwards.
 */
class FabricRecipeRegistrationIntegrationTest {

    private static RegistryAccess registries;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        //EXPERIMENT: bootstrap first (as the loader does before mod init), then register.
        //Note: This mirrors the real dedicated-server window. The Fabric loader bootstraps vanilla before mod initializers run, and in this environment the
        // built-in registries stay writable for that window, which is exactly what the existing game event registration already relies on.
        Bootstrap.bootStrap();
        FabricChemicalLifecycle.initialize();
        MekanismChemicalIngredientTypes.ensureRegistered();
        FabricRecipeRegistration.register();
        //Every built-in registry including the custom chemical root, which FabricChemicalLifecycle registers into BuiltInRegistries.REGISTRY.
        List<Registry<?>> entries = new ArrayList<>();
        BuiltInRegistries.REGISTRY.holders().forEach(registryHolder -> entries.add(registryHolder.value()));
        registries = new RegistryAccess.ImmutableRegistryAccess(List.copyOf(entries));
    }

    @Test
    void declaredHoldersStayAssignableToTheOfficialWidenedHolderShape() {
        //Oracle declares DeferredHolder<RecipeType<?>, RecipeType<X>>, so every recipe type holder must be usable as Holder<RecipeType<?>> and Holder<? extends RecipeType<?>>.
        net.minecraft.core.Holder<RecipeType<?>> widened = MekanismRecipeTypes.TYPE_CRUSHING;
        net.minecraft.core.Holder<? extends RecipeType<?>> asExtends = MekanismRecipeTypes.TYPE_SAWING;
        assertNotNull(widened);
        assertNotNull(asExtends);
        //The declared value type must remain precise, so a caller keeps the concrete recipe type without a cast.
        RecipeType<ItemStackToItemStackRecipe> precise = MekanismRecipeTypes.TYPE_CRUSHING.value();
        assertSame(widened.value(), precise, "the widened and precise views must observe the same instance");
        //The serializer holders must be usable as Holder<RecipeSerializer<?>> in the same way.
        net.minecraft.core.Holder<RecipeSerializer<?>> serializer = MekanismRecipeSerializers.CRUSHING;
        assertNotNull(serializer);
    }

    @Test
    void recipeTypesRegisterUnderOfficialIdsAndReuseTheDeclaredInstances() {
        List<MekanismKeyedHolder<RecipeType<?>, ? extends RecipeType<?>>> holders = MekanismRecipeTypes.recipeTypeHolders();
        //Note: An explicit element type is required because Java forbids a 'var' declaration over the wildcard capture here.
        assertEquals(26, holders.size(), "there must be exactly 26 recipe types");
        for (MekanismKeyedHolder<?, ? extends RecipeType<?>> holder : holders) {
            ResourceLocation id = holder.getId();
            assertEquals("mekanism", id.getNamespace(), "every recipe type id must be in the mekanism namespace");
            assertTrue(BuiltInRegistries.RECIPE_TYPE.containsKey(id), "recipe type must be registered: " + id);
            //The decisive identity claim: the registered registry entry is the exact instance the recipe classes call getType() on.
            RecipeType<?> registered = BuiltInRegistries.RECIPE_TYPE.get(id);
            assertSame(holder.value(), registered, "registration must reuse the declared recipe type instance: " + id);
            assertSame(registered, BuiltInRegistries.RECIPE_TYPE.getHolderOrThrow(
                  ResourceKey.create(Registries.RECIPE_TYPE, id)).value(), "canonical holder must resolve to the same instance: " + id);
        }
        assertTrue(MekanismRecipeTypes.isRegistered(), "recipe type registration must report complete");
        assertSame(MekanismRecipeTypes.TYPE_CRUSHING.value(), BuiltInRegistries.RECIPE_TYPE.get(
              ResourceLocation.fromNamespaceAndPath("mekanism", "crushing")), "the crushing type must be the declared instance");
        //Every holder must be bound after registration, and the registry must expose 26 distinct instances, so no two ids can share one type (which would
        //merge two families in RecipeManager's byType map) and no id can be bound to a freshly created unrelated instance.
        java.util.Set<RecipeType<?>> registeredInstances = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (MekanismKeyedHolder<?, ? extends RecipeType<?>> holder : holders) {
            assertTrue(holder.isBound(), "a registered recipe type holder must be bound: " + holder.getId());
            assertSame(holder.value(), holder.get(), "get() and value() must observe the declared instance: " + holder.getId());
            assertTrue(registeredInstances.add(holder.value()), "each recipe type must be a distinct instance: " + holder.getId());
        }
        assertEquals(26, registeredInstances.size(), "there must be 26 distinct registered recipe type instances");
        //Every recipe class returns TYPE_X.value() from getType(), so proving the registry holds that exact instance proves recipe-level identity.
        assertSame(BuiltInRegistries.RECIPE_TYPE.get(MekanismRecipeTypes.NAME_SAWING), MekanismRecipeTypes.TYPE_SAWING.value(),
              "the sawing registry entry must be the instance SawmillRecipe.getType() returns");
    }

    @Test
    void serializersRegisterUnderOfficialIdsAndResolveThroughTheirHolders() {
        for (MekanismKeyedHolder<?, ?> holder : serializerHolders()) {
            ResourceLocation id = holder.getId();
            assertEquals("mekanism", id.getNamespace(), "every serializer id must be in the mekanism namespace");
            assertTrue(BuiltInRegistries.RECIPE_SERIALIZER.containsKey(id), "recipe serializer must be registered: " + id);
            assertTrue(holder.isBound(), "a registered serializer holder must be bound: " + id);
            assertSame(BuiltInRegistries.RECIPE_SERIALIZER.get(id), holder.value(),
                  "the holder must resolve to the registered serializer instance: " + id);
        }
        assertEquals(26, serializerHolders().stream().map(MekanismKeyedHolder::getId).distinct().count(),
              "every serializer id must be unique");
    }

    @Test
    void realRecipeManagerDecodesAndServesARegisteredRecipe() {
        RecipeManager manager = new RecipeManager(registries);
        JsonObject json = new JsonObject();
        json.addProperty("type", "mekanism:crushing");
        json.add("input", ItemStackIngredient.CODEC.encodeStart(JsonOps.INSTANCE,
              IngredientCreatorAccess.item().from(Items.IRON_INGOT, 1)).getOrThrow());
        json.add("output", ItemStack.CODEC.encodeStart(JsonOps.INSTANCE, new ItemStack(Items.IRON_NUGGET, 9)).getOrThrow());
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("mekanism_test", "crushing");
        net.minecraft.world.item.crafting.Recipe<?> decoded = net.minecraft.world.item.crafting.Recipe.CODEC
              .parse(registries.createSerializationContext(JsonOps.INSTANCE), json).getOrThrow();
        RecipeHolder<?> holder = new RecipeHolder<>(id, decoded);
        assertNotNull(holder.value(), "the registered serializer must decode a real mekanism:crushing document");
        assertTrue(holder.value() instanceof BasicCrushingRecipe, "the decoded recipe must be the declared recipe class");

        manager.replaceRecipes(List.of(holder));
        //TYPE_CRUSHING is declared over the official abstraction, so serve through its exact declared type.
        RecipeType<ItemStackToItemStackRecipe> crushing = MekanismRecipeTypes.TYPE_CRUSHING.value();
        List<RecipeHolder<ItemStackToItemStackRecipe>> served = manager.getAllRecipesFor(crushing);
        assertEquals(1, served.size(), "vanilla must serve the decoded recipe under the declared recipe type");
        assertSame(holder.value(), served.getFirst().value(), "the served recipe must be the decoded instance");
        assertSame(crushing, served.getFirst().value().getType(), "the served recipe must report the registered recipe type");
        assertSame(MekanismRecipeSerializers.CRUSHING.value(), served.getFirst().value().getSerializer(),
              "the served recipe must report the registered serializer");
    }

    @Test
    void duplicateRegistrationFailsAndRepeatedEnsureRegisteredIsIdempotent() {
        //Note: ensureRegistered is memoized, so a repeat call is a no-op and must not attempt a second registry write.
        MekanismRecipeTypes.ensureRegistered();
        assertTrue(MekanismRecipeTypes.isRegistered());
        //A second push of the same 26 serializer keys must fail loudly on the occupied keys rather than silently doubling.
        assertThrows(IllegalStateException.class, FabricRecipeRegistration::register,
              "a duplicate serializer registration must fail on the occupied keys");
        assertSame(MekanismRecipeTypes.TYPE_SAWING.value(), BuiltInRegistries.RECIPE_TYPE.get(
              ResourceLocation.fromNamespaceAndPath("mekanism", "sawing")), "a failed duplicate registration must not disturb the registry");
    }

    @SuppressWarnings("unchecked")
    private static List<MekanismKeyedHolder<RecipeSerializer<?>, ? extends RecipeSerializer<?>>> serializerHolders() {
        return List.of(MekanismRecipeSerializers.CRUSHING, MekanismRecipeSerializers.ENRICHING, MekanismRecipeSerializers.SMELTING,
              MekanismRecipeSerializers.CHEMICAL_INFUSING, MekanismRecipeSerializers.COMBINING, MekanismRecipeSerializers.SEPARATING,
              MekanismRecipeSerializers.WASHING, MekanismRecipeSerializers.EVAPORATING, MekanismRecipeSerializers.ACTIVATING,
              MekanismRecipeSerializers.CENTRIFUGING, MekanismRecipeSerializers.CRYSTALLIZING, MekanismRecipeSerializers.DISSOLUTION,
              MekanismRecipeSerializers.COMPRESSING, MekanismRecipeSerializers.PURIFYING, MekanismRecipeSerializers.INJECTING,
              MekanismRecipeSerializers.NUCLEOSYNTHESIZING, MekanismRecipeSerializers.ENERGY_CONVERSION, MekanismRecipeSerializers.CHEMICAL_CONVERSION,
              MekanismRecipeSerializers.OXIDIZING, MekanismRecipeSerializers.PIGMENT_EXTRACTING, MekanismRecipeSerializers.PIGMENT_MIXING,
              MekanismRecipeSerializers.METALLURGIC_INFUSING, MekanismRecipeSerializers.PAINTING, MekanismRecipeSerializers.REACTION,
              MekanismRecipeSerializers.ROTARY, MekanismRecipeSerializers.SAWING);
    }
}
