package mekanism.api.recipes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
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

    public static final MekanismKeyedHolder<RecipeType<?>, RecipeType<ItemStackToItemStackRecipe>> TYPE_CRUSHING = newRecipeType(NAME_CRUSHING);
    public static final MekanismKeyedHolder<RecipeType<?>, RecipeType<ItemStackToItemStackRecipe>> TYPE_ENRICHING = newRecipeType(NAME_ENRICHING);
    public static final MekanismKeyedHolder<RecipeType<?>, RecipeType<ItemStackToItemStackRecipe>> TYPE_SMELTING = newRecipeType(NAME_SMELTING);
    public static final MekanismKeyedHolder<RecipeType<?>, RecipeType<ChemicalChemicalToChemicalRecipe>> TYPE_CHEMICAL_INFUSING = newRecipeType(NAME_CHEMICAL_INFUSING);
    public static final MekanismKeyedHolder<RecipeType<?>, RecipeType<CombinerRecipe>> TYPE_COMBINING = newRecipeType(NAME_COMBINING);
    public static final MekanismKeyedHolder<RecipeType<?>, RecipeType<ElectrolysisRecipe>> TYPE_SEPARATING = newRecipeType(NAME_SEPARATING);
    public static final MekanismKeyedHolder<RecipeType<?>, RecipeType<FluidChemicalToChemicalRecipe>> TYPE_WASHING = newRecipeType(NAME_WASHING);
    public static final MekanismKeyedHolder<RecipeType<?>, RecipeType<FluidToFluidRecipe>> TYPE_EVAPORATING = newRecipeType(NAME_EVAPORATING);
    public static final MekanismKeyedHolder<RecipeType<?>, RecipeType<ChemicalToChemicalRecipe>> TYPE_ACTIVATING = newRecipeType(NAME_ACTIVATING);
    public static final MekanismKeyedHolder<RecipeType<?>, RecipeType<ChemicalToChemicalRecipe>> TYPE_CENTRIFUGING = newRecipeType(NAME_CENTRIFUGING);
    public static final MekanismKeyedHolder<RecipeType<?>, RecipeType<ChemicalCrystallizerRecipe>> TYPE_CRYSTALLIZING = newRecipeType(NAME_CRYSTALLIZING);
    public static final MekanismKeyedHolder<RecipeType<?>, RecipeType<ChemicalDissolutionRecipe>> TYPE_DISSOLUTION = newRecipeType(NAME_DISSOLUTION);
    public static final MekanismKeyedHolder<RecipeType<?>, RecipeType<ItemStackChemicalToItemStackRecipe>> TYPE_COMPRESSING = newRecipeType(NAME_COMPRESSING);
    public static final MekanismKeyedHolder<RecipeType<?>, RecipeType<ItemStackChemicalToItemStackRecipe>> TYPE_PURIFYING = newRecipeType(NAME_PURIFYING);
    public static final MekanismKeyedHolder<RecipeType<?>, RecipeType<ItemStackChemicalToItemStackRecipe>> TYPE_INJECTING = newRecipeType(NAME_INJECTING);
    public static final MekanismKeyedHolder<RecipeType<?>, RecipeType<NucleosynthesizingRecipe>> TYPE_NUCLEOSYNTHESIZING = newRecipeType(NAME_NUCLEOSYNTHESIZING);
    public static final MekanismKeyedHolder<RecipeType<?>, RecipeType<ItemStackToEnergyRecipe>> TYPE_ENERGY_CONVERSION = newRecipeType(NAME_ENERGY_CONVERSION);
    public static final MekanismKeyedHolder<RecipeType<?>, RecipeType<ItemStackToChemicalRecipe>> TYPE_CHEMICAL_CONVERSION = newRecipeType(NAME_CHEMICAL_CONVERSION);
    public static final MekanismKeyedHolder<RecipeType<?>, RecipeType<ItemStackToChemicalRecipe>> TYPE_OXIDIZING = newRecipeType(NAME_OXIDIZING);
    public static final MekanismKeyedHolder<RecipeType<?>, RecipeType<ItemStackToChemicalRecipe>> TYPE_PIGMENT_EXTRACTING = newRecipeType(NAME_PIGMENT_EXTRACTING);
    public static final MekanismKeyedHolder<RecipeType<?>, RecipeType<ChemicalChemicalToChemicalRecipe>> TYPE_PIGMENT_MIXING = newRecipeType(NAME_PIGMENT_MIXING);
    public static final MekanismKeyedHolder<RecipeType<?>, RecipeType<ItemStackChemicalToItemStackRecipe>> TYPE_METALLURGIC_INFUSING = newRecipeType(NAME_METALLURGIC_INFUSING);
    public static final MekanismKeyedHolder<RecipeType<?>, RecipeType<ItemStackChemicalToItemStackRecipe>> TYPE_PAINTING = newRecipeType(NAME_PAINTING);
    public static final MekanismKeyedHolder<RecipeType<?>, RecipeType<PressurizedReactionRecipe>> TYPE_REACTION = newRecipeType(NAME_REACTION);
    public static final MekanismKeyedHolder<RecipeType<?>, RecipeType<RotaryRecipe>> TYPE_ROTARY = newRecipeType(NAME_ROTARY);
    public static final MekanismKeyedHolder<RecipeType<?>, RecipeType<SawmillRecipe>> TYPE_SAWING = newRecipeType(NAME_SAWING);

    private static final List<MekanismKeyedHolder<RecipeType<?>, ? extends RecipeType<?>>> RECIPE_TYPES = recipeTypes();
    private static volatile boolean registered;

    /**
     * Registers every recipe type in this class into {@link BuiltInRegistries#RECIPE_TYPE} under its official id.
     *
     * <p>Each concrete {@link RecipeType} instance is created once in the static initializer and the canonical registry holder is attached back onto the holder over
     * that exact same instance. Recipe {@code getType()} identity and vanilla {@code RecipeManager}'s identity keyed {@code byType} grouping therefore survive
     * registration unchanged, because there is only ever one instance per recipe type and no second creation site to diverge from it.
     *
     * <p>This runs in the same startup window as the existing game event registration, where the Fabric loader has already bootstrapped vanilla but the built-in
     * registries are still writable. Calling it more than once is a no-op: the registry write is not repeated.
     */
    public static void ensureRegistered() {
        if (registered) {
            return;
        }
        synchronized (MekanismRecipeTypes.class) {
            if (registered) {
                return;
            }
            for (MekanismKeyedHolder<RecipeType<?>, ? extends RecipeType<?>> holder : RECIPE_TYPES) {
                register(holder);
            }
            registered = true;
        }
    }

    /**
     * @return {@code true} once {@link #ensureRegistered()} has written every recipe type into the built-in recipe type registry.
     */
    public static boolean isRegistered() {
        return registered;
    }

    /**
     * @return The 26 recipe type holders in official declaration order.
     */
    public static List<MekanismKeyedHolder<RecipeType<?>, ? extends RecipeType<?>>> recipeTypeHolders() {
        return RECIPE_TYPES;
    }

    private static void register(MekanismKeyedHolder<RecipeType<?>, ? extends RecipeType<?>> holder) {
        RecipeType<?> type = holder.value();
        ResourceKey<RecipeType<?>> key = holder.getKey();
        //Note: registerForHolder returns the canonical reference for exactly this instance; attaching it back is what binds the prebuilt type without recreating it.
        Registry.registerForHolder(BuiltInRegistries.RECIPE_TYPE, key, type);
        holder.attach(BuiltInRegistries.RECIPE_TYPE.getHolderOrThrow(key));
    }

    private static List<MekanismKeyedHolder<RecipeType<?>, ? extends RecipeType<?>>> recipeTypes() {
        List<MekanismKeyedHolder<RecipeType<?>, ? extends RecipeType<?>>> types = new ArrayList<>(26);
        Consumer<MekanismKeyedHolder<RecipeType<?>, ? extends RecipeType<?>>> add = types::add;
        add.accept(TYPE_CRUSHING);
        add.accept(TYPE_ENRICHING);
        add.accept(TYPE_SMELTING);
        add.accept(TYPE_CHEMICAL_INFUSING);
        add.accept(TYPE_COMBINING);
        add.accept(TYPE_SEPARATING);
        add.accept(TYPE_WASHING);
        add.accept(TYPE_EVAPORATING);
        add.accept(TYPE_ACTIVATING);
        add.accept(TYPE_CENTRIFUGING);
        add.accept(TYPE_CRYSTALLIZING);
        add.accept(TYPE_DISSOLUTION);
        add.accept(TYPE_COMPRESSING);
        add.accept(TYPE_PURIFYING);
        add.accept(TYPE_INJECTING);
        add.accept(TYPE_NUCLEOSYNTHESIZING);
        add.accept(TYPE_ENERGY_CONVERSION);
        add.accept(TYPE_CHEMICAL_CONVERSION);
        add.accept(TYPE_OXIDIZING);
        add.accept(TYPE_PIGMENT_EXTRACTING);
        add.accept(TYPE_PIGMENT_MIXING);
        add.accept(TYPE_METALLURGIC_INFUSING);
        add.accept(TYPE_PAINTING);
        add.accept(TYPE_REACTION);
        add.accept(TYPE_ROTARY);
        add.accept(TYPE_SAWING);
        return Collections.unmodifiableList(types);
    }

    /**
     * Creates an identity stable, still unregistered {@link RecipeType} keyed by the given name. Registration is performed by {@link #ensureRegistered()} so that
     * the exact same instance is preserved.
     *
     * @param name Name of the recipe type.
     *
     * @return Keyed holder over a concrete unregistered recipe type.
     */
    private static <RECIPE extends Recipe<?>> MekanismKeyedHolder<RecipeType<?>, RecipeType<RECIPE>> newRecipeType(ResourceLocation name) {
        RecipeType<RECIPE> type = new RecipeType<RECIPE>() {
            @Override
            public String toString() {
                return name.toString();
            }
        };
        return MekanismKeyedHolder.keyed(Registries.RECIPE_TYPE, name, type);
    }
}
