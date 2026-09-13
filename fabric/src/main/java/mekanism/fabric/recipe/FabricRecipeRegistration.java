package mekanism.fabric.recipe;

import mekanism.api.chemical.ChemicalStack;
import mekanism.api.recipes.basic.BasicActivatingRecipe;
import mekanism.api.recipes.basic.BasicCentrifugingRecipe;
import mekanism.api.recipes.basic.BasicChemicalConversionRecipe;
import mekanism.api.recipes.basic.BasicChemicalCrystallizerRecipe;
import mekanism.api.recipes.basic.BasicChemicalDissolutionRecipe;
import mekanism.api.recipes.basic.BasicChemicalInfuserRecipe;
import mekanism.api.recipes.basic.BasicChemicalOxidizerRecipe;
import mekanism.api.recipes.basic.BasicCombinerRecipe;
import mekanism.api.recipes.basic.BasicCompressingRecipe;
import mekanism.api.recipes.basic.BasicCrushingRecipe;
import mekanism.api.recipes.basic.BasicElectrolysisRecipe;
import mekanism.api.recipes.basic.BasicEnrichingRecipe;
import mekanism.api.recipes.basic.BasicFluidToFluidRecipe;
import mekanism.api.recipes.basic.BasicInjectingRecipe;
import mekanism.api.recipes.basic.BasicItemStackToEnergyRecipe;
import mekanism.api.recipes.basic.BasicMetallurgicInfuserRecipe;
import mekanism.api.recipes.basic.BasicNucleosynthesizingRecipe;
import mekanism.api.recipes.basic.BasicPaintingRecipe;
import mekanism.api.recipes.basic.BasicPigmentExtractingRecipe;
import mekanism.api.recipes.basic.BasicPigmentMixingRecipe;
import mekanism.api.recipes.basic.BasicPressurizedReactionRecipe;
import mekanism.api.recipes.basic.BasicPurifyingRecipe;
import mekanism.api.recipes.basic.BasicRotaryRecipe;
import mekanism.api.recipes.basic.BasicSawmillRecipe;
import mekanism.api.recipes.basic.BasicSmeltingRecipe;
import mekanism.api.recipes.basic.BasicWashingRecipe;
import mekanism.api.recipes.ingredients.creator.IngredientCreatorAccess;
import mekanism.common.recipe.serializer.MekanismRecipeSerializer;
import mekanism.common.recipe.serializer.RotaryRecipeSerializer;
import mekanism.common.recipe.serializer.SawmillRecipeSerializer;
import mekanism.fabric.registry.FabricDeferredRegister;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;

/**
 * Real registration of every Mekanism recipe serializer into {@link BuiltInRegistries#RECIPE_SERIALIZER}, plus the recipe type registration owed by
 * {@link mekanism.api.recipes.MekanismRecipeTypes}. It is written by the common mod initializer in the same startup window as the existing game event registration,
 * where the Fabric loader has already bootstrapped vanilla but the built-in registries are still writable.
 *
 * <p>The official ids, factory order, and per-entry codecs mirror the official {@code MekanismRecipeSerializersInternal}. The four crafting-special and
 * recipe-upgrade serializers of that class ({@code mek_data}, {@code clear_configuration}, {@code bin_insert}, {@code bin_extract}) belong to a different owner and
 * are deliberately not registered here; see {@code docs/TASK043_PLAN.md}.
 *
 * <p>The recipe types are registered first, because both share the same startup window and recipe type identity must be established before any recipe can be
 * decoded or served.
 */
public final class FabricRecipeRegistration {

    private static final FabricDeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
          new FabricDeferredRegister<>(Registries.RECIPE_SERIALIZER, "mekanism");

    static {
        RECIPE_SERIALIZERS.register("crushing", () -> MekanismRecipeSerializer.itemToItem(BasicCrushingRecipe::new));
        RECIPE_SERIALIZERS.register("enriching", () -> MekanismRecipeSerializer.itemToItem(BasicEnrichingRecipe::new));
        RECIPE_SERIALIZERS.register("smelting", () -> MekanismRecipeSerializer.itemToItem(BasicSmeltingRecipe::new));

        RECIPE_SERIALIZERS.register("chemical_infusing", () -> MekanismRecipeSerializer.chemicalChemicalToChemical(BasicChemicalInfuserRecipe::new,
              IngredientCreatorAccess.chemicalStack(), ChemicalStack.MAP_CODEC, ChemicalStack.STREAM_CODEC));

        RECIPE_SERIALIZERS.register("combining", () -> MekanismRecipeSerializer.combining(BasicCombinerRecipe::new));

        RECIPE_SERIALIZERS.register("separating", () -> MekanismRecipeSerializer.separating(BasicElectrolysisRecipe::new));

        RECIPE_SERIALIZERS.register("washing", () -> MekanismRecipeSerializer.fluidChemicalToChemical(BasicWashingRecipe::new));

        RECIPE_SERIALIZERS.register("evaporating", () -> MekanismRecipeSerializer.fluidToFluid(BasicFluidToFluidRecipe::new));

        RECIPE_SERIALIZERS.register("activating", () -> MekanismRecipeSerializer.chemicalToChemical(BasicActivatingRecipe::new));
        RECIPE_SERIALIZERS.register("centrifuging", () -> MekanismRecipeSerializer.chemicalToChemical(BasicCentrifugingRecipe::new));

        RECIPE_SERIALIZERS.register("crystallizing", () -> MekanismRecipeSerializer.crystallizing(BasicChemicalCrystallizerRecipe::new));

        RECIPE_SERIALIZERS.register("dissolution", () -> MekanismRecipeSerializer.dissolution(BasicChemicalDissolutionRecipe::new));

        RECIPE_SERIALIZERS.register("compressing", () -> MekanismRecipeSerializer.itemChemicalToItem(BasicCompressingRecipe::new));
        RECIPE_SERIALIZERS.register("purifying", () -> MekanismRecipeSerializer.itemChemicalToItem(BasicPurifyingRecipe::new));
        RECIPE_SERIALIZERS.register("injecting", () -> MekanismRecipeSerializer.itemChemicalToItem(BasicInjectingRecipe::new));

        RECIPE_SERIALIZERS.register("nucleosynthesizing", () -> MekanismRecipeSerializer.nucleosynthesizing(BasicNucleosynthesizingRecipe::new));

        RECIPE_SERIALIZERS.register("energy_conversion", () -> MekanismRecipeSerializer.itemToEnergy(BasicItemStackToEnergyRecipe::new));

        RECIPE_SERIALIZERS.register("chemical_conversion", () -> MekanismRecipeSerializer.itemToChemical(BasicChemicalConversionRecipe::new,
              ChemicalStack.MAP_CODEC, ChemicalStack.STREAM_CODEC));
        RECIPE_SERIALIZERS.register("oxidizing", () -> MekanismRecipeSerializer.itemToChemical(BasicChemicalOxidizerRecipe::new,
              ChemicalStack.MAP_CODEC, ChemicalStack.STREAM_CODEC));

        RECIPE_SERIALIZERS.register("pigment_extracting", () -> MekanismRecipeSerializer.itemToChemical(BasicPigmentExtractingRecipe::new,
              ChemicalStack.MAP_CODEC, ChemicalStack.STREAM_CODEC));

        RECIPE_SERIALIZERS.register("pigment_mixing", () -> MekanismRecipeSerializer.chemicalChemicalToChemical(BasicPigmentMixingRecipe::new,
              IngredientCreatorAccess.chemicalStack(), ChemicalStack.MAP_CODEC, ChemicalStack.STREAM_CODEC));

        RECIPE_SERIALIZERS.register("metallurgic_infusing", () -> MekanismRecipeSerializer.itemChemicalToItem(BasicMetallurgicInfuserRecipe::new));

        RECIPE_SERIALIZERS.register("painting", () -> MekanismRecipeSerializer.itemChemicalToItem(BasicPaintingRecipe::new));

        RECIPE_SERIALIZERS.register("reaction", () -> MekanismRecipeSerializer.reaction(BasicPressurizedReactionRecipe::new));

        RECIPE_SERIALIZERS.register("rotary", () -> new RotaryRecipeSerializer(BasicRotaryRecipe::new, BasicRotaryRecipe::new, BasicRotaryRecipe::new));

        RECIPE_SERIALIZERS.register("sawing", () -> new SawmillRecipeSerializer(BasicSawmillRecipe::new));
    }

    private FabricRecipeRegistration() {
    }

    /**
     * Registers every Mekanism recipe type and recipe serializer into the vanilla built-in registries.
     *
     * <p>Runs in the same startup window as the existing game event registration. The deferred register rejects a duplicate or occupied key, so a second call fails
     * loudly instead of silently doubling.
     */
    public static void register() {
        mekanism.api.recipes.MekanismRecipeTypes.ensureRegistered();
        RECIPE_SERIALIZERS.registerAll(BuiltInRegistries.RECIPE_SERIALIZER);
    }
}
