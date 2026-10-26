package mekanism.common.registries;

import mekanism.api.MekanismAPI;
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
import mekanism.common.recipe.ClearConfigurationRecipe;
import mekanism.common.recipe.bin.BinExtractRecipe;
import mekanism.common.recipe.bin.BinInsertRecipe;
import mekanism.common.recipe.serializer.MekanismRecipeSerializer;
import mekanism.common.recipe.serializer.RotaryRecipeSerializer;
import mekanism.common.recipe.serializer.SawmillRecipeSerializer;
import mekanism.common.recipe.upgrade.MekanismShapedRecipe;
import mekanism.common.registration.MekanismDeferredHolder;
import mekanism.common.registration.MekanismDeferredRegister;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;

/**
 * Catalog of every Mekanism recipe serializer. The official class used NeoForge's deferred register and also assigned each created holder into the matching
 * {@code MekanismRecipeSerializers} api field; the api fields are keyed holders that resolve from the registry by id, so this port only declares the catalog.
 */
public class MekanismRecipeSerializersInternal {

    public static final MekanismDeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = new MekanismDeferredRegister<>(Registries.RECIPE_SERIALIZER,
          MekanismAPI.MEKANISM_MODID);

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

    public static final MekanismDeferredHolder<RecipeSerializer<?>, RecipeSerializer<MekanismShapedRecipe>> MEK_DATA = RECIPE_SERIALIZERS.register("mek_data",
          () -> MekanismRecipeSerializer.wrapped(MekanismShapedRecipe::new));
    public static final MekanismDeferredHolder<RecipeSerializer<?>, RecipeSerializer<ClearConfigurationRecipe>> CLEAR_CONFIGURATION = RECIPE_SERIALIZERS.register(
          "clear_configuration", () -> new SimpleCraftingRecipeSerializer<>(ClearConfigurationRecipe::new));
    public static final MekanismDeferredHolder<RecipeSerializer<?>, RecipeSerializer<BinInsertRecipe>> BIN_INSERT = RECIPE_SERIALIZERS.register("bin_insert",
          () -> new SimpleCraftingRecipeSerializer<>(BinInsertRecipe::new));
    public static final MekanismDeferredHolder<RecipeSerializer<?>, RecipeSerializer<BinExtractRecipe>> BIN_EXTRACT = RECIPE_SERIALIZERS.register("bin_extract",
          () -> new SimpleCraftingRecipeSerializer<>(BinExtractRecipe::new));

    private MekanismRecipeSerializersInternal() {
    }
}
