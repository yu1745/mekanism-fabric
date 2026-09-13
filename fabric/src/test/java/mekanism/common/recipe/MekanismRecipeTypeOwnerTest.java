package mekanism.common.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import mekanism.api.recipes.ItemStackToItemStackRecipe;
import mekanism.api.recipes.MekanismRecipeTypes;
import mekanism.api.recipes.basic.BasicCrushingRecipe;
import mekanism.api.recipes.ingredients.ItemStackIngredient;
import mekanism.api.recipes.ingredients.creator.IngredientCreatorAccess;
import mekanism.common.recipe.lookup.cache.RecipeCacheContext;
import mekanism.common.registries.MekanismChemicalIngredientTypes;
import mekanism.test.ChemicalTestBootstrap;
import mekanism.test.RecipeLevelStub;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Focused contracts for the canonical common recipe-type owner and its provider surface.
 *
 * <p>Every case drives the real production owner over a real {@link RecipeManager}. The generic per-binding cache matrix, category construction and reload
 * ownership stay owned by {@code GenericInputCacheContextTest}, {@code RotaryInputCacheContextTest} and the fixture probes; they are deliberately not repeated
 * here.</p>
 */
class MekanismRecipeTypeOwnerTest {

    private static RegistryAccess access;
    private static RecipeManager manager;

    @BeforeAll
    static void bootstrap() {
        access = ChemicalTestBootstrap.initialize();
        MekanismChemicalIngredientTypes.ensureRegistered();
        manager = new RecipeManager(access);
    }

    @AfterEach
    void clearBinding() {
        RecipeCacheContext.clear();
    }

    @Test
    void canonicalOwnerExposesTwentySixDistinctTypesWithOfficialIdsAndProviderBehaviour() {
        List<RecipeHolder<?>> loaded = new ArrayList<>();
        BasicCrushingRecipe crushing = crushing(Items.IRON_INGOT, new ItemStack(Items.IRON_NUGGET, 9));
        loaded.add(holder("crushing", crushing));
        manager.replaceRecipes(loaded);
        RecipeCacheContext.bind(manager, access, 1);

        //The owner is its own provider: the recipe type it reports is itself, so nominal RecipeType assignability is real rather than redeclared.
        RecipeType<ItemStackToItemStackRecipe> selfTyped = MekanismRecipeType.CRUSHING.getRecipeType();
        assertSame(MekanismRecipeType.CRUSHING.getRecipeType(), selfTyped);
        assertTrue(selfTyped instanceof RecipeType, "the canonical owner must be a nominal vanilla RecipeType");
        assertSame(MekanismRecipeTypes.TYPE_CRUSHING.value(), selfTyped,
              "the API holder must observe the canonical registry-bound instance, not a wrapper");

        //Every recipe-level call site resolves the same instance the owner declares.
        assertSame(selfTyped, crushing.getType(), "a recipe's getType() must be the canonical recipe type");
        assertSame(MekanismRecipeType.CRUSHING.getRecipeType(), crushing.getType());

        //Compile and execute the official factory-taking registration signature without writing another registry entry.
        var declarations = new mekanism.common.registration.impl.RecipeTypeDeferredRegister("mekanism");
        var factoryCalls = new java.util.concurrent.atomic.AtomicInteger();
        var declared = declarations.registerMek("crushing", id -> {
            assertEquals(MekanismRecipeTypes.NAME_CRUSHING, id);
            factoryCalls.incrementAndGet();
            return MekanismRecipeType.CRUSHING.getRecipeType();
        });
        assertSame(selfTyped, declared.getRecipeType());
        assertEquals(1, factoryCalls.get());

        //Provider surface: helper methods all route through the same owner.
        assertSame(crushing, MekanismRecipeType.CRUSHING.findFirst(null, recipe -> true));
        assertTrue(MekanismRecipeType.CRUSHING.contains(null, recipe -> recipe == crushing));
        assertEquals(1, MekanismRecipeType.CRUSHING.stream(null).count());
        assertSame(MekanismRecipeType.CRUSHING.getInputCache(), MekanismRecipeType.CRUSHING.getRecipeType().getInputCache());
        assertEquals(ResourceLocation.fromNamespaceAndPath("mekanism", "crushing"), MekanismRecipeType.CRUSHING.getRegistryName());
        assertEquals(ResourceLocation.fromNamespaceAndPath("mekanism", "crushing"), MekanismRecipeType.CRUSHING.getRecipeType().getRegistryName());

        //The 26 declarations are distinct types with distinct ids, so no two families can merge in the manager's identity-keyed grouping.
        List<ResourceLocation> ids = new ArrayList<>();
        java.util.Set<Object> identities = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (var entry : mekanism.common.recipe.MekanismRecipeType.RECIPE_TYPES.getEntries()) {
            ids.add(entry.getId());
            assertTrue(identities.add(entry.getRecipeType()), "every recipe type must be a distinct instance: " + entry.getId());
            assertNotNull(entry.getRecipeType().getInputCache(), "every recipe type must own an input cache: " + entry.getId());
        }
        assertEquals(26, ids.size(), "the owner must declare exactly 26 recipe types");
        assertEquals(26, ids.stream().distinct().count(), "every declared recipe type id must be unique");
        for (ResourceLocation id : ids) {
            assertEquals("mekanism", id.getNamespace(), "every recipe type id must be in the mekanism namespace");
        }
    }

    @Test
    void bindingOwnsListsAndIndicesWhileForeignExplicitQueriesStayUncached() {
        BasicCrushingRecipe crushing = crushing(Items.COPPER_INGOT, new ItemStack(Items.COPPER_INGOT, 2));
        manager.replaceRecipes(List.of(holder("crushing", crushing)));
        RecipeCacheContext.Binding binding = RecipeCacheContext.bind(manager, access, 1);

        //The owner's own recipe list is served and cached inside this binding.
        List<RecipeHolder<ItemStackToItemStackRecipe>> raw = MekanismRecipeType.CRUSHING.getRecipeType().getRecipes(manager, access);
        assertEquals(1, raw.size());
        assertSame(crushing, raw.getFirst().value());
        assertSame(raw, MekanismRecipeType.CRUSHING.getRecipeType().getRecipes(manager, access),
              "an exact manager/access query must reuse the binding-owned immutable list, including an empty one");

        //The input cache indexes the same owner recipes, so the shared source really feeds the caches.
        assertTrue(MekanismRecipeType.CRUSHING.getInputCache().containsInput(null, new ItemStack(Items.COPPER_INGOT)));

        //A foreign manager/access pair queried explicitly must answer for itself, uncached, without disturbing current state.
        RecipeManager foreign = new RecipeManager(access);
        BasicCrushingRecipe foreignRecipe = crushing(Items.DIAMOND, new ItemStack(Items.DIAMOND_BLOCK, 1));
        foreign.replaceRecipes(List.of(holder("foreign", foreignRecipe)));
        List<RecipeHolder<ItemStackToItemStackRecipe>> foreignList = MekanismRecipeType.CRUSHING.getRecipeType().getRecipes(foreign, access);
        assertEquals(1, foreignList.size());
        assertSame(foreignRecipe, foreignList.getFirst().value());
        assertNotSame(foreignList, MekanismRecipeType.CRUSHING.getRecipeType().getRecipes(foreign, access),
              "a foreign manager/access pair must not be served from the current binding's cache");
        //The current binding is untouched: its own list is still exactly what was published.
        assertSame(raw, MekanismRecipeType.CRUSHING.getRecipeType().getRecipes(manager, access));
        assertSame(crushing, raw.getFirst().value());

        //Swap the manager contents without rebinding: this binding keeps serving its own indexed candidates and list.
        BasicCrushingRecipe replacement = crushing(Items.NETHERITE_INGOT, new ItemStack(Items.NETHERITE_SCRAP, 1));
        manager.replaceRecipes(List.of(holder("replacement", replacement)));
        assertTrue(MekanismRecipeType.CRUSHING.getInputCache().containsInput(null, new ItemStack(Items.COPPER_INGOT)),
              "before clear the bound context still serves the old index");

        //Clearing drops both the recipe list and every input-cache index for this binding, so the new contents become visible.
        MekanismRecipeType.clearCache();
        assertFalse(MekanismRecipeType.CRUSHING.getInputCache().containsInput(null, new ItemStack(Items.COPPER_INGOT)),
              "clear must release the old input-cache index");
        assertTrue(MekanismRecipeType.CRUSHING.getInputCache().containsInput(null, new ItemStack(Items.NETHERITE_INGOT)),
              "the cache must lazily rebuild from the still-bound manager after clear");
        List<RecipeHolder<ItemStackToItemStackRecipe>> recomputed = MekanismRecipeType.CRUSHING.getRecipeType().getRecipes(manager, access);
        assertNotSame(raw, recomputed, "clear must discard the cached recipe list");
        assertSame(replacement, recomputed.getFirst().value());
        assertSame(binding, RecipeCacheContext.currentBinding());
        assertThrows(UnsupportedOperationException.class, () -> recomputed.add(recomputed.getFirst()));

        //A new publication discards both owner lists and indices, and even an empty result belongs to that publication.
        manager.replaceRecipes(List.of());
        RecipeCacheContext.bind(manager, access, 2);
        assertTrue(MekanismRecipeType.CRUSHING.getRecipes().isEmpty());
        assertFalse(MekanismRecipeType.CRUSHING.getInputCache().containsInput(null, new ItemStack(Items.NETHERITE_INGOT)));
        manager.replaceRecipes(List.of(holder("later", crushing)));
        assertTrue(MekanismRecipeType.CRUSHING.getRecipes().isEmpty(), "empty lists must be cached until clear or rebind too");
        MekanismRecipeType.clearCache();
        assertSame(crushing, MekanismRecipeType.CRUSHING.getRecipes().getFirst().value());
    }

    @Test
    void mismatchedNonNullLevelManagerFailsAndNoContextServesNothing() {
        BasicCrushingRecipe crushing = crushing(Items.IRON_INGOT, new ItemStack(Items.IRON_NUGGET, 9));
        manager.replaceRecipes(List.of(holder("crushing", crushing)));
        Level boundLevel = RecipeLevelStub.of(manager);
        Level foreignLevel = RecipeLevelStub.of(new RecipeManager(access));

        //No bound context: the provider serves nothing instead of consulting a stale process-global world.
        assertTrue(MekanismRecipeType.CRUSHING.getRecipeType().getRecipes((Level) null).isEmpty());
        assertTrue(MekanismRecipeType.CRUSHING.getRecipeType().getRecipes(boundLevel).isEmpty());

        RecipeCacheContext.bind(manager, access, 1);
        assertSame(crushing, MekanismRecipeType.CRUSHING.getRecipeType().getRecipes(boundLevel).getFirst().value());
        //A real non-null level whose manager is not the exact bound manager must fail rather than serve stale recipes.
        assertThrows(IllegalStateException.class, () -> MekanismRecipeType.CRUSHING.getRecipeType().getRecipes(foreignLevel),
              "a mismatched non-null level manager must fail");
    }

    @Test
    void syntheticVanillaSmeltingIsIncludedOnlyWithAccessAndKeepsOrderIdAndUnionSemantics() {
        //The owner's own smelting recipe must be a real smelting-typed recipe, otherwise it is not part of this family.
        mekanism.api.recipes.basic.BasicSmeltingRecipe ownSmelting = new mekanism.api.recipes.basic.BasicSmeltingRecipe(
              IngredientCreatorAccess.item().from(Items.RAW_IRON, 1), new ItemStack(Items.IRON_INGOT, 1));
        //A vanilla smelting recipe whose union has two children exercises the audited compound-union path.
        SmeltingRecipe vanilla = smeltingCase(false, false, new ItemStack(Items.COPPER_INGOT),
              List.of(Ingredient.of(Items.RAW_COPPER), Ingredient.of(Items.RAW_GOLD)));
        ResourceLocation vanillaId = ResourceLocation.fromNamespaceAndPath("minecraft", "copper_from_smelting");
        manager.replaceRecipes(List.of(
              smeltingHolder("own_smelting", ownSmelting),
              new RecipeHolder<>(vanillaId, vanilla),
              new RecipeHolder<>(vanillaId.withSuffix("/special"), smeltingCase(true, false, new ItemStack(Items.GOLD_INGOT),
                    List.of(Ingredient.of(Items.RAW_GOLD)))),
              new RecipeHolder<>(vanillaId.withSuffix("/incomplete"), smeltingCase(false, true, new ItemStack(Items.GOLD_INGOT),
                    List.of(Ingredient.of(Items.RAW_GOLD)))),
              new RecipeHolder<>(vanillaId.withSuffix("/empty_output"), smeltingCase(false, false, ItemStack.EMPTY,
                    List.of(Ingredient.of(Items.RAW_GOLD)))),
              new RecipeHolder<>(vanillaId.withSuffix("/no_ingredients"), smeltingCase(false, false, new ItemStack(Items.GOLD_INGOT), List.of()))
        ));

        //Own recipes come first, then synthetic ones; the synthetic id keeps the original namespace with the generated prefix.
        List<RecipeHolder<?>> withAccess = List.copyOf(MekanismRecipeType.SMELTING.getRecipeType().getRecipes(manager, access));
        assertEquals(2, withAccess.size(), "smelting must serve own recipes plus synthetic vanilla smelting when access is known");
        assertSame(ownSmelting, withAccess.getFirst().value(), "own recipes must stay first");
        RecipeHolder<?> synthetic = withAccess.get(1);
        assertEquals(ResourceLocation.fromNamespaceAndPath("minecraft", "/mekanism_generated/" + vanillaId.getPath()), synthetic.id(),
              "the synthetic id must prefix the original path, keeping the original namespace");
        assertTrue(synthetic.value() instanceof mekanism.api.recipes.basic.BasicSmeltingRecipe,
              "the synthetic entry must be the Mekanism smelting recipe");
        //The fixture exposes two separate Ingredient entries, exercising the owner's compound union rather than only vanilla's multi-item Ingredient.
        ItemStackIngredient syntheticInput = ((mekanism.api.recipes.basic.BasicSmeltingRecipe) synthetic.value()).getInput();
        assertTrue(syntheticInput.testType(new ItemStack(Items.RAW_COPPER)) && syntheticInput.testType(new ItemStack(Items.RAW_GOLD)),
              "the union must accept either child");
        assertFalse(syntheticInput.testType(new ItemStack(Items.RAW_IRON)), "the union must reject a foreign input");
        assertSame(MekanismRecipeType.SMELTING.getRecipeType(), synthetic.value().getType(),
              "the synthetic recipe must report the canonical smelting type");
        //ItemStack has no value equality, so the output must be compared by item and count.
        ItemStack syntheticOutput = synthetic.value().getResultItem(access);
        assertEquals(Items.COPPER_INGOT, syntheticOutput.getItem(), "the synthetic recipe must carry the vanilla smelting output item");
        assertEquals(1, syntheticOutput.getCount(), "the synthetic recipe must carry the vanilla smelting output count");

        //Without registry access the owner cannot resolve the vanilla result, so it serves only its own recipes.
        List<RecipeHolder<?>> withoutAccess = List.copyOf(MekanismRecipeType.SMELTING.getRecipeType().getRecipes(manager, null));
        assertEquals(1, withoutAccess.size(), "without access synthetic smelting must be omitted");
        assertSame(ownSmelting, withoutAccess.getFirst().value());

        //The synthetic entries feed the input caches through the same shared source rather than a bypass.
        RecipeCacheContext.bind(manager, access, 1);
        assertTrue(MekanismRecipeType.SMELTING.getInputCache().containsInput(null, new ItemStack(Items.RAW_COPPER)),
              "the smelting input cache must see the synthetic union input");
        assertTrue(MekanismRecipeType.SMELTING.getInputCache().containsInput(null, new ItemStack(Items.RAW_IRON)),
              "the smelting input cache must still see the owner's own recipe");

        RegistryAccess otherAccess = new RegistryAccess.ImmutableRegistryAccess(List.of());
        SmeltingRecipe accessSensitive = new SmeltingRecipe("", CookingBookCategory.MISC, Ingredient.of(Items.RAW_GOLD),
              new ItemStack(Items.GOLD_INGOT), 0, 200) {
            @Override
            public ItemStack getResultItem(net.minecraft.core.HolderLookup.Provider queriedAccess) {
                return new ItemStack(queriedAccess == access ? Items.GOLD_INGOT : Items.GOLD_NUGGET);
            }
        };
        manager.replaceRecipes(List.of(new RecipeHolder<>(vanillaId, accessSensitive)));
        RecipeCacheContext.bind(manager, access, 2);
        var owner = MekanismRecipeType.SMELTING.getRecipeType();
        var published = owner.getRecipes(manager, access);
        var foreignAccess = owner.getRecipes(manager, otherAccess);
        assertEquals(Items.GOLD_INGOT, published.getFirst().value().getResultItem(access).getItem());
        assertEquals(Items.GOLD_NUGGET, foreignAccess.getFirst().value().getResultItem(otherAccess).getItem(),
              "the same manager with different explicit access must not reuse the binding's synthetic output");
        assertSame(published, owner.getRecipes(manager, access), "a foreign access query must not replace the published list");
        assertTrue(owner.getRecipes(manager, null).isEmpty(), "explicit null access must not reuse a populated synthetic list");
        RecipeCacheContext.clear();
        assertTrue(owner.getRecipes(manager).isEmpty(), "manager-only lookup cannot borrow access without a matching binding");
    }

    @Test
    void incompleteRecipesAreReportedAndFilteredOutOfServedLists() {
        //A tag-only ingredient whose tag has no bound contents has no matching instances, so it is incomplete.
        ItemStackIngredient incompleteInput = IngredientCreatorAccess.item().from(Ingredient.of(
              net.minecraft.tags.TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("mekanism_test", "absent"))));
        BasicCrushingRecipe incomplete = new BasicCrushingRecipe(incompleteInput, new ItemStack(Items.IRON_NUGGET, 9));
        BasicCrushingRecipe complete = crushing(Items.DIAMOND, new ItemStack(Items.DIAMOND_BLOCK, 1));
        manager.replaceRecipes(List.of(holder("incomplete", incomplete), holder("complete", complete)));

        assertTrue(incomplete.isIncomplete(), "the fixture recipe must really be incomplete");
        //Served lists drop incomplete recipes so a partially complete recipe is never looked up.
        List<RecipeHolder<?>> served = List.copyOf(MekanismRecipeType.CRUSHING.getRecipeType().getRecipes(manager, access));
        assertEquals(1, served.size());
        assertSame(complete, served.getFirst().value());

        //The explicit incomplete check reports the incomplete recipe from the raw manager contents.
        assertTrue(MekanismRecipeType.checkIncompleteRecipes(manager, access),
              "an incomplete recipe present in the manager must be reported");

        //With only complete recipes nothing is reported.
        manager.replaceRecipes(List.of(holder("complete", complete)));
        assertFalse(MekanismRecipeType.checkIncompleteRecipes(manager, access));

        Level level = RecipeLevelStub.of(manager);
        var input = new net.minecraft.world.item.crafting.SingleRecipeInput(new ItemStack(Items.RAW_GOLD));
        ResourceLocation helperId = ResourceLocation.fromNamespaceAndPath("mekanism_test", "helper_smelting");
        for (boolean special : new boolean[]{false, true}) {
            SmeltingRecipe vanilla = smeltingCase(special, true, new ItemStack(Items.GOLD_INGOT), List.of(Ingredient.of(Items.RAW_GOLD)));
            var vanillaHolder = new RecipeHolder<>(helperId, vanilla);
            manager.replaceRecipes(List.of(vanillaHolder));
            assertEquals(special, MekanismRecipeType.byKey(level, helperId).isPresent(),
                  "byKey rejects ordinary incomplete recipes but retains special recipes");
            assertEquals(special, MekanismRecipeType.getRecipeFor(RecipeType.SMELTING, input, level).isPresent(),
                  "getRecipeFor preserves the same special-recipe exception");
        }
    }

    @Test
    void apiHoldersResolveCanonicalInstancesAndForeignThreadsGetNoBinding() {
        //The API handshake must resolve the common owner rather than fabricate a second identity.
        assertSame(MekanismRecipeType.CRUSHING.getRecipeType(), MekanismRecipeTypes.TYPE_CRUSHING.value());
        assertSame(MekanismRecipeType.SAWING.getRecipeType(), MekanismRecipeTypes.TYPE_SAWING.value());
        //A locally created wrapper would be a different instance; the API must never create one.
        RecipeType<ItemStackToItemStackRecipe> wrong = new RecipeType<>() {
            @Override
            public String toString() {
                return "mekanism:crushing";
            }
        };
        assertNotSame(wrong, MekanismRecipeTypes.TYPE_CRUSHING.value(),
              "the API holder must not expose a locally created recipe type");

        //A fresh thread has no binding, so an installed binding on this thread cannot leak across threads.
        manager.replaceRecipes(List.of(holder("crushing", crushing(Items.IRON_INGOT, new ItemStack(Items.IRON_NUGGET, 9)))));
        RecipeCacheContext.bind(manager, access, 1);
        AtomicReference<List<?>> foreign = new AtomicReference<>();
        AtomicReference<Object> foreignBinding = new AtomicReference<>("unset");
        Thread other = new Thread(() -> {
            foreignBinding.set(RecipeCacheContext.currentBinding());
            foreign.set(MekanismRecipeType.CRUSHING.getRecipeType().getRecipes((Level) null));
        });
        other.start();
        try {
            other.join();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while joining the foreign thread", interrupted);
        }
        assertNull(foreignBinding.get(), "another thread must not inherit this thread's recipe binding");
        assertTrue(foreign.get().isEmpty(), "a foreign thread without a binding must be served no recipes");
        //This thread's binding and cache remain intact.
        assertEquals(1, MekanismRecipeType.CRUSHING.getRecipeType().getRecipes((Level) null).size());
    }

    /** Vanilla subtype exposing independent skip flags and ingredient entries to exercise the owner's adaptation, not vanilla's implementation. */
    private static SmeltingRecipe smeltingCase(boolean special, boolean incomplete, ItemStack output, List<Ingredient> ingredients) {
        return new SmeltingRecipe("", CookingBookCategory.MISC, Ingredient.of(Items.RAW_GOLD), output, 0, 200) {
            @Override
            public boolean isSpecial() {
                return special;
            }

            @Override
            public boolean isIncomplete() {
                return incomplete;
            }

            @Override
            public net.minecraft.core.NonNullList<Ingredient> getIngredients() {
                var result = net.minecraft.core.NonNullList.<Ingredient>create();
                result.addAll(ingredients);
                return result;
            }
        };
    }

    private static RecipeHolder<BasicCrushingRecipe> holder(String path, BasicCrushingRecipe recipe) {
        return new RecipeHolder<>(ResourceLocation.fromNamespaceAndPath("mekanism_test", path), recipe);
    }

    private static RecipeHolder<mekanism.api.recipes.basic.BasicSmeltingRecipe> smeltingHolder(String path,
          mekanism.api.recipes.basic.BasicSmeltingRecipe recipe) {
        return new RecipeHolder<>(ResourceLocation.fromNamespaceAndPath("mekanism_test", path), recipe);
    }

    private static BasicCrushingRecipe crushing(net.minecraft.world.item.Item input, ItemStack output) {
        return new BasicCrushingRecipe(IngredientCreatorAccess.item().from(input, 1), output);
    }
}
