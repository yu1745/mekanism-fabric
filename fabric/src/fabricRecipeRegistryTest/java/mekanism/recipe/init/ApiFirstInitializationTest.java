package mekanism.recipe.init;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Proves that initializing the <strong>API</strong> declaration class before the common owner still converges on one canonical instance per id.
 *
 * <p>This runs in its own fresh JVM (the source set forks per test class). Loading {@code MekanismRecipeTypes} first must pull the common owner in through the service,
 * so the API holds the canonical instances rather than anonymous ones, and the single registration must preserve them.</p>
 */
class ApiFirstInitializationTest {

    @BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void apiDeclarationLoadedBeforeCommonOwnerStillYieldsOneCanonicalInstancePerId() throws Exception {
        //Force API-first explicitly, so the service handshake has to create the common owner.
        Class<?> api = Class.forName("mekanism.api.recipes.MekanismRecipeTypes");
        Class<?> owner = Class.forName("mekanism.common.recipe.MekanismRecipeType");

        List<?> apiHolders = holders(api);
        List<?> ownerEntries = ownerEntries(owner);
        assertEquals(26, apiHolders.size(), "the API must declare 26 recipe type holders");
        assertEquals(26, ownerEntries.size(), "the common owner must declare 26 recipe types");

        for (int index = 0; index < 26; index++) {
            Object apiHolder = apiHolders.get(index);
            Object ownerEntry = ownerEntries.get(index);
            ResourceLocation apiId = (ResourceLocation) invoke(apiHolder, "getId");
            ResourceLocation ownerId = (ResourceLocation) invoke(ownerEntry, "getId");
            assertEquals(ownerId, apiId, "API and common declaration order must agree at index " + index);
            Object apiValue = ((Holder<?>) apiHolder).value();
            Object ownerValue = invoke(ownerEntry, "getRecipeType");
            assertSame(ownerValue, apiValue, "the API holder must expose the canonical common instance for " + apiId);
            //Pre-registration the holder is already non-null over the canonical instance, but the registry must not yet contain the id.
            assertNotNull(((Holder<?>) apiHolder).value(), "the API holder must expose its canonical instance before registration: " + apiId);
            assertFalse(BuiltInRegistries.RECIPE_TYPE.containsKey(apiId), "no recipe type may be registry bound before registration: " + apiId);
        }
        assertFalse((boolean) api.getMethod("isRegistered").invoke(null), "registration must not have happened yet");

        api.getMethod("ensureRegistered").invoke(null);
        assertTrue((boolean) api.getMethod("isRegistered").invoke(null), "registration must report complete");
        java.util.Set<Object> registered = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (Object apiHolder : apiHolders) {
            ResourceLocation id = (ResourceLocation) invoke(apiHolder, "getId");
            assertTrue(((Holder<?>) apiHolder).isBound(), "a registered recipe type holder must be bound: " + id);
            Object value = ((Holder<?>) apiHolder).value();
            assertSame(value, BuiltInRegistries.RECIPE_TYPE.get(id), "the registry entry must be the canonical declared instance: " + id);
            assertSame(value,
                  BuiltInRegistries.RECIPE_TYPE.getHolderOrThrow(ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE_TYPE, id)).value(),
                  "the canonical holder must resolve to that same instance: " + id);
            assertTrue(registered.add(value), "every recipe type must be a distinct instance: " + id);
        }
        for (Object ownerEntry : ownerEntries) {
            ResourceLocation id = (ResourceLocation) invoke(ownerEntry, "getId");
            Object canonical = BuiltInRegistries.RECIPE_TYPE.get(id);
            assertSame(canonical, invoke(ownerEntry, "get"), "registered common Supplier must remain usable: " + id);
            assertSame(canonical, ((Holder<?>) ownerEntry).value(), "registered common Holder must remain usable: " + id);
            assertSame(canonical, invoke(ownerEntry, "getRecipeType"), "registered common provider must remain usable: " + id);
        }
        assertEquals(26, registered.size(), "there must be 26 distinct registered recipe type instances");
        //The API-side nominal surface must still be a real vanilla recipe type for consumers.
        assertTrue(((Holder<?>) apiHolders.getFirst()).value() instanceof RecipeType,
              "the canonical value must be a nominal vanilla RecipeType");
    }

    @SuppressWarnings("unchecked")
    private static List<?> holders(Class<?> api) throws Exception {
        return (List<?>) api.getMethod("recipeTypeHolders").invoke(null);
    }

    private static List<?> ownerEntries(Class<?> owner) throws Exception {
        Field recipeTypes = owner.getField("RECIPE_TYPES");
        Object deferred = recipeTypes.get(null);
        return (List<?>) invoke(deferred, "getEntries");
    }

    private static Object invoke(Object target, String name) throws Exception {
        Method method = target.getClass().getMethod(name);
        return method.invoke(target);
    }
}
