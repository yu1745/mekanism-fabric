package mekanism.api.recipes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import mekanism.api.MekanismServices;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * Public owner of the official Mekanism recipe type ids and their canonical holders.
 *
 * <p>The concrete {@link RecipeType} instances belong to the common declaration owner, which creates each of them exactly once before any registry write. This class
 * obtains those same instances through the narrow {@link IRecipeTypeRegistry} service, so it never creates a second {@code RecipeType} per id: recipe
 * {@code getType()} identity and vanilla {@code RecipeManager}'s identity-keyed grouping stay intact across registration.</p>
 *
 * <p>Service registration then attaches the canonical registry holder back onto these holders over the identical instance. Both load orders converge on the same
 * objects, because this class reads no common state at construction time beyond the service handshake, and the common owner reads no API static field.</p>
 */
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

    private static final IRecipeTypeRegistry REGISTRY = MekanismServices.load(IRecipeTypeRegistry.class);

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
     * Registers every recipe type in this class into {@code BuiltInRegistries.RECIPE_TYPE} under its official id.
     *
     * <p>The common declaration owner performs the write, because it owns the 26 canonical instances; this class then attaches each canonical registry holder back
     * onto the holder over that exact instance. Because there is only ever one instance per recipe type, recipe {@code getType()} identity and vanilla
     * {@code RecipeManager}'s identity-keyed {@code byType} grouping survive registration unchanged, and no second registry write happens here.</p>
     *
     * <p>Calling it more than once is a no-op; the registry write is not repeated.</p>
     */
    public static void ensureRegistered() {
        if (registered) {
            return;
        }
        synchronized (MekanismRecipeTypes.class) {
            if (registered) {
                return;
            }
            REGISTRY.registerAll();
            for (MekanismKeyedHolder<RecipeType<?>, ? extends RecipeType<?>> holder : RECIPE_TYPES) {
                holder.attach(canonicalHolder(holder.getId()));
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

    private static Holder<RecipeType<?>> canonicalHolder(ResourceLocation id) {
        return REGISTRY.canonicalHolder(id);
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
     * Obtains the canonical recipe type the common owner declared for the given official id.
     *
     * <p>The returned holder is key-correct and identity stable but not yet registry bound; {@link #ensureRegistered()} performs the one write and attaches the
     * canonical holder back onto this same instance.</p>
     *
     * @param name Official id of the recipe type.
     *
     * @return Keyed holder over the canonical recipe type.
     */
    @SuppressWarnings("unchecked")
    private static <RECIPE extends Recipe<?>> MekanismKeyedHolder<RecipeType<?>, RecipeType<RECIPE>> newRecipeType(ResourceLocation name) {
        return MekanismKeyedHolder.keyed(Registries.RECIPE_TYPE, name, (RecipeType<RECIPE>) REGISTRY.canonical(name));
    }
}
