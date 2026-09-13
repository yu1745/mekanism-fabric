package mekanism.api.recipes;

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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;

/**
 * Key resolved holders for every Mekanism recipe serializer. The serializers themselves are created and written into
 * {@link BuiltInRegistries#RECIPE_SERIALIZER} by the platform recipe registration owner; these holders resolve lazily by their official ids and stay unbound until
 * that registration has run.
 */
public class MekanismRecipeSerializers {

    public static MekanismKeyedHolder<RecipeSerializer<BasicCrushingRecipe>> CRUSHING = registryKeyed("crushing");
    public static MekanismKeyedHolder<RecipeSerializer<BasicEnrichingRecipe>> ENRICHING = registryKeyed("enriching");
    public static MekanismKeyedHolder<RecipeSerializer<BasicSmeltingRecipe>> SMELTING = registryKeyed("smelting");
    public static MekanismKeyedHolder<RecipeSerializer<BasicChemicalInfuserRecipe>> CHEMICAL_INFUSING = registryKeyed("chemical_infusing");
    public static MekanismKeyedHolder<RecipeSerializer<BasicCombinerRecipe>> COMBINING = registryKeyed("combining");
    public static MekanismKeyedHolder<RecipeSerializer<BasicElectrolysisRecipe>> SEPARATING = registryKeyed("separating");
    public static MekanismKeyedHolder<RecipeSerializer<BasicWashingRecipe>> WASHING = registryKeyed("washing");
    public static MekanismKeyedHolder<RecipeSerializer<BasicFluidToFluidRecipe>> EVAPORATING = registryKeyed("evaporating");
    public static MekanismKeyedHolder<RecipeSerializer<BasicActivatingRecipe>> ACTIVATING = registryKeyed("activating");
    public static MekanismKeyedHolder<RecipeSerializer<BasicCentrifugingRecipe>> CENTRIFUGING = registryKeyed("centrifuging");
    public static MekanismKeyedHolder<RecipeSerializer<BasicChemicalCrystallizerRecipe>> CRYSTALLIZING = registryKeyed("crystallizing");
    public static MekanismKeyedHolder<RecipeSerializer<BasicChemicalDissolutionRecipe>> DISSOLUTION = registryKeyed("dissolution");
    public static MekanismKeyedHolder<RecipeSerializer<BasicCompressingRecipe>> COMPRESSING = registryKeyed("compressing");
    public static MekanismKeyedHolder<RecipeSerializer<BasicPurifyingRecipe>> PURIFYING = registryKeyed("purifying");
    public static MekanismKeyedHolder<RecipeSerializer<BasicInjectingRecipe>> INJECTING = registryKeyed("injecting");
    public static MekanismKeyedHolder<RecipeSerializer<BasicNucleosynthesizingRecipe>> NUCLEOSYNTHESIZING = registryKeyed("nucleosynthesizing");
    public static MekanismKeyedHolder<RecipeSerializer<BasicItemStackToEnergyRecipe>> ENERGY_CONVERSION = registryKeyed("energy_conversion");
    public static MekanismKeyedHolder<RecipeSerializer<BasicChemicalConversionRecipe>> CHEMICAL_CONVERSION = registryKeyed("chemical_conversion");
    public static MekanismKeyedHolder<RecipeSerializer<BasicChemicalOxidizerRecipe>> OXIDIZING = registryKeyed("oxidizing");
    public static MekanismKeyedHolder<RecipeSerializer<BasicPigmentExtractingRecipe>> PIGMENT_EXTRACTING = registryKeyed("pigment_extracting");
    public static MekanismKeyedHolder<RecipeSerializer<BasicPigmentMixingRecipe>> PIGMENT_MIXING = registryKeyed("pigment_mixing");
    public static MekanismKeyedHolder<RecipeSerializer<BasicMetallurgicInfuserRecipe>> METALLURGIC_INFUSING = registryKeyed("metallurgic_infusing");
    public static MekanismKeyedHolder<RecipeSerializer<BasicPaintingRecipe>> PAINTING = registryKeyed("painting");
    public static MekanismKeyedHolder<RecipeSerializer<BasicPressurizedReactionRecipe>> REACTION = registryKeyed("reaction");
    public static MekanismKeyedHolder<RecipeSerializer<BasicRotaryRecipe>> ROTARY = registryKeyed("rotary");
    public static MekanismKeyedHolder<RecipeSerializer<BasicSawmillRecipe>> SAWING = registryKeyed("sawing");

    private static <RECIPE extends net.minecraft.world.item.crafting.Recipe<?>> MekanismKeyedHolder<RecipeSerializer<RECIPE>> registryKeyed(String path) {
        return MekanismKeyedHolder.registryKeyed(Registries.RECIPE_SERIALIZER, ResourceLocation.fromNamespaceAndPath("mekanism", path),
              () -> BuiltInRegistries.RECIPE_SERIALIZER);
    }
}
