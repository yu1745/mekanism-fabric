package mekanism.api.recipes;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;

public class MekanismRecipeTypes {

    public static final ResourceLocation NAME_CRUSHING = ResourceLocation.fromNamespaceAndPath("mekanism", "crushing");
    public static final ResourceLocation NAME_ENRICHING = ResourceLocation.fromNamespaceAndPath("mekanism", "enriching");
    public static final ResourceLocation NAME_SMELTING = ResourceLocation.fromNamespaceAndPath("mekanism", "smelting");
    public static final ResourceLocation NAME_CHEMICAL_INFUSING = ResourceLocation.fromNamespaceAndPath("mekanism", "chemical_infusing");
    public static final ResourceLocation NAME_COMBINING = ResourceLocation.fromNamespaceAndPath("mekanism", "combining");
    public static final ResourceLocation NAME_SEPARATING = ResourceLocation.fromNamespaceAndPath("mekanism", "separating");
    public static final ResourceLocation NAME_WASHING = ResourceLocation.fromNamespaceAndPath("mekanism", "washing");
    public static final ResourceLocation NAME_EVAPORATING = ResourceLocation.fromNamespaceAndPath("mekanism", "evaporating");
    public static final ResourceLocation NAME_ACTIVATING = ResourceLocation.fromNamespaceAndPath("mekanism", "activating");
    public static final ResourceLocation NAME_CENTRIFUGING = ResourceLocation.fromNamespaceAndPath("mekanism", "centrifuging");
    public static final ResourceLocation NAME_CRYSTALLIZING = ResourceLocation.fromNamespaceAndPath("mekanism", "crystallizing");
    public static final ResourceLocation NAME_DISSOLUTION = ResourceLocation.fromNamespaceAndPath("mekanism", "dissolution");
    public static final ResourceLocation NAME_COMPRESSING = ResourceLocation.fromNamespaceAndPath("mekanism", "compressing");
    public static final ResourceLocation NAME_PURIFYING = ResourceLocation.fromNamespaceAndPath("mekanism", "purifying");
    public static final ResourceLocation NAME_INJECTING = ResourceLocation.fromNamespaceAndPath("mekanism", "injecting");
    public static final ResourceLocation NAME_NUCLEOSYNTHESIZING = ResourceLocation.fromNamespaceAndPath("mekanism", "nucleosynthesizing");
    public static final ResourceLocation NAME_ENERGY_CONVERSION = ResourceLocation.fromNamespaceAndPath("mekanism", "energy_conversion");
    public static final ResourceLocation NAME_CHEMICAL_CONVERSION = ResourceLocation.fromNamespaceAndPath("mekanism", "chemical_conversion");
    public static final ResourceLocation NAME_OXIDIZING = ResourceLocation.fromNamespaceAndPath("mekanism", "oxidizing");
    public static final ResourceLocation NAME_PIGMENT_EXTRACTING = ResourceLocation.fromNamespaceAndPath("mekanism", "pigment_extracting");
    public static final ResourceLocation NAME_PIGMENT_MIXING = ResourceLocation.fromNamespaceAndPath("mekanism", "pigment_mixing");
    public static final ResourceLocation NAME_METALLURGIC_INFUSING = ResourceLocation.fromNamespaceAndPath("mekanism", "metallurgic_infusing");
    public static final ResourceLocation NAME_PAINTING = ResourceLocation.fromNamespaceAndPath("mekanism", "painting");
    public static final ResourceLocation NAME_REACTION = ResourceLocation.fromNamespaceAndPath("mekanism", "reaction");
    public static final ResourceLocation NAME_ROTARY = ResourceLocation.fromNamespaceAndPath("mekanism", "rotary");
    public static final ResourceLocation NAME_SAWING = ResourceLocation.fromNamespaceAndPath("mekanism", "sawing");

    public static final MekanismKeyedHolder<RecipeType<ItemStackToItemStackRecipe>> TYPE_CRUSHING = newRecipeType(NAME_CRUSHING);
    public static final MekanismKeyedHolder<RecipeType<ItemStackToItemStackRecipe>> TYPE_ENRICHING = newRecipeType(NAME_ENRICHING);
    public static final MekanismKeyedHolder<RecipeType<ItemStackToItemStackRecipe>> TYPE_SMELTING = newRecipeType(NAME_SMELTING);
    public static final MekanismKeyedHolder<RecipeType<ChemicalChemicalToChemicalRecipe>> TYPE_CHEMICAL_INFUSING = newRecipeType(NAME_CHEMICAL_INFUSING);
    public static final MekanismKeyedHolder<RecipeType<CombinerRecipe>> TYPE_COMBINING = newRecipeType(NAME_COMBINING);
    public static final MekanismKeyedHolder<RecipeType<ElectrolysisRecipe>> TYPE_SEPARATING = newRecipeType(NAME_SEPARATING);
    public static final MekanismKeyedHolder<RecipeType<FluidChemicalToChemicalRecipe>> TYPE_WASHING = newRecipeType(NAME_WASHING);
    public static final MekanismKeyedHolder<RecipeType<FluidToFluidRecipe>> TYPE_EVAPORATING = newRecipeType(NAME_EVAPORATING);
    public static final MekanismKeyedHolder<RecipeType<ChemicalToChemicalRecipe>> TYPE_ACTIVATING = newRecipeType(NAME_ACTIVATING);
    public static final MekanismKeyedHolder<RecipeType<ChemicalToChemicalRecipe>> TYPE_CENTRIFUGING = newRecipeType(NAME_CENTRIFUGING);
    public static final MekanismKeyedHolder<RecipeType<ChemicalCrystallizerRecipe>> TYPE_CRYSTALLIZING = newRecipeType(NAME_CRYSTALLIZING);
    public static final MekanismKeyedHolder<RecipeType<ChemicalDissolutionRecipe>> TYPE_DISSOLUTION = newRecipeType(NAME_DISSOLUTION);
    public static final MekanismKeyedHolder<RecipeType<ItemStackChemicalToItemStackRecipe>> TYPE_COMPRESSING = newRecipeType(NAME_COMPRESSING);
    public static final MekanismKeyedHolder<RecipeType<ItemStackChemicalToItemStackRecipe>> TYPE_PURIFYING = newRecipeType(NAME_PURIFYING);
    public static final MekanismKeyedHolder<RecipeType<ItemStackChemicalToItemStackRecipe>> TYPE_INJECTING = newRecipeType(NAME_INJECTING);
    public static final MekanismKeyedHolder<RecipeType<NucleosynthesizingRecipe>> TYPE_NUCLEOSYNTHESIZING = newRecipeType(NAME_NUCLEOSYNTHESIZING);
    public static final MekanismKeyedHolder<RecipeType<ItemStackToEnergyRecipe>> TYPE_ENERGY_CONVERSION = newRecipeType(NAME_ENERGY_CONVERSION);
    public static final MekanismKeyedHolder<RecipeType<ItemStackToChemicalRecipe>> TYPE_CHEMICAL_CONVERSION = newRecipeType(NAME_CHEMICAL_CONVERSION);
    public static final MekanismKeyedHolder<RecipeType<ItemStackToChemicalRecipe>> TYPE_OXIDIZING = newRecipeType(NAME_OXIDIZING);
    public static final MekanismKeyedHolder<RecipeType<ItemStackToChemicalRecipe>> TYPE_PIGMENT_EXTRACTING = newRecipeType(NAME_PIGMENT_EXTRACTING);
    public static final MekanismKeyedHolder<RecipeType<ChemicalChemicalToChemicalRecipe>> TYPE_PIGMENT_MIXING = newRecipeType(NAME_PIGMENT_MIXING);
    public static final MekanismKeyedHolder<RecipeType<ItemStackChemicalToItemStackRecipe>> TYPE_METALLURGIC_INFUSING = newRecipeType(NAME_METALLURGIC_INFUSING);
    public static final MekanismKeyedHolder<RecipeType<ItemStackChemicalToItemStackRecipe>> TYPE_PAINTING = newRecipeType(NAME_PAINTING);
    public static final MekanismKeyedHolder<RecipeType<PressurizedReactionRecipe>> TYPE_REACTION = newRecipeType(NAME_REACTION);
    public static final MekanismKeyedHolder<RecipeType<RotaryRecipe>> TYPE_ROTARY = newRecipeType(NAME_ROTARY);
    public static final MekanismKeyedHolder<RecipeType<SawmillRecipe>> TYPE_SAWING = newRecipeType(NAME_SAWING);

    /**
     * Creates a non-registered, identity stable {@link RecipeType} keyed by the given name. Recipe type registration, and therefore a registry bound holder, is
     * owned by a later task; until then in-memory recipes still group correctly because vanilla indexes recipes by {@link Recipe#getType()} identity.
     *
     * @param name Name of the recipe type.
     *
     * @return Identity stable keyed holder over a concrete unregistered recipe type.
     */
    private static <RECIPE extends Recipe<?>> MekanismKeyedHolder<RecipeType<RECIPE>> newRecipeType(ResourceLocation name) {
        RecipeType<RECIPE> type = new RecipeType<RECIPE>() {
            @Override
            public String toString() {
                return name.toString();
            }
        };
        return MekanismKeyedHolder.keyed(Registries.RECIPE_TYPE, name, type);
    }
}
