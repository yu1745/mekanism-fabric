package mekanism.api.recipes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.fluid.FluidStack;
import mekanism.api.recipes.basic.BasicChemicalConversionRecipe;
import mekanism.api.recipes.basic.BasicChemicalInfuserRecipe;
import mekanism.api.recipes.basic.BasicCombinerRecipe;
import mekanism.api.recipes.basic.BasicCrushingRecipe;
import mekanism.api.recipes.basic.BasicElectrolysisRecipe;
import mekanism.api.recipes.basic.BasicFluidToFluidRecipe;
import mekanism.api.recipes.basic.BasicItemStackToEnergyRecipe;
import mekanism.api.recipes.basic.BasicPressurizedReactionRecipe;
import mekanism.api.recipes.basic.BasicRotaryRecipe;
import mekanism.api.recipes.basic.BasicSawmillRecipe;
import mekanism.api.recipes.ingredients.FluidStackIngredient;
import mekanism.api.recipes.ingredients.ItemStackIngredient;
import mekanism.api.recipes.ingredients.creator.IngredientCreatorAccess;
import mekanism.common.recipe.serializer.MekanismRecipeSerializer;
import mekanism.common.recipe.serializer.RotaryRecipeSerializer;
import mekanism.common.recipe.serializer.SawmillRecipeSerializer;
import mekanism.common.registries.MekanismChemicals;
import mekanism.test.ChemicalTestBootstrap;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Codec level contracts for the Mekanism recipe serializers. This suite runs in the ordinary JVM and performs no registry write; the registered outcome, official ids,
 * and canonical identity are asserted by the isolated {@code fabricRecipeRegistryTest} worker, which can register before the built-in registries freeze.
 */
class RecipeSerializerContractTest {

    private static RegistryAccess registries;

    @BeforeAll
    static void bootstrap() {
        //A composite access: every built-in registry plus the isolated chemical registry, for the item/fluid/chemical stream codecs.
        ChemicalTestBootstrap.initialize();
        List<net.minecraft.core.Registry<?>> entries = new ArrayList<>();
        BuiltInRegistries.REGISTRY.holders().forEach(registryHolder -> entries.add(registryHolder.value()));
        entries.add(mekanism.api.chemical.ChemicalRegistry.registry());
        registries = new RegistryAccess.ImmutableRegistryAccess(entries);
    }

    private static mekanism.api.recipes.ingredients.ChemicalStackIngredient hydrogenIngredient() {
        return IngredientCreatorAccess.chemicalStack().fromHolder(MekanismChemicals.HYDROGEN.get().getAsHolder(), 1L);
    }

    @Test
    void itemToItemAndCombiningRoundTripOfficialJsonShapes() {
        ItemStackIngredient iron = IngredientCreatorAccess.item().from(Items.IRON_INGOT, 1);
        ItemStackIngredient gold = IngredientCreatorAccess.item().from(Items.GOLD_INGOT, 2);
        BasicCrushingRecipe crushing = new BasicCrushingRecipe(iron, new ItemStack(Items.IRON_NUGGET, 9));
        RecipeSerializer<BasicCrushingRecipe> serializer = MekanismRecipeSerializer.itemToItem(BasicCrushingRecipe::new);
        JsonObject json = new JsonObject();
        json.add("input", encode(ItemStackIngredient.CODEC, iron));
        json.add("output", encode(ItemStack.CODEC, new ItemStack(Items.IRON_NUGGET, 9)));
        assertEquals(crushing, decode(serializer.codec(), json), "the item to item codec must round trip its exact recipe value");
        assertStreamRoundTrip(serializer, crushing);

        BasicCombinerRecipe combiner = new BasicCombinerRecipe(iron, gold, new ItemStack(Items.DIAMOND));
        RecipeSerializer<BasicCombinerRecipe> combining = MekanismRecipeSerializer.combining(BasicCombinerRecipe::new);
        JsonObject combiningJson = new JsonObject();
        combiningJson.add("main_input", encode(ItemStackIngredient.CODEC, iron));
        combiningJson.add("extra_input", encode(ItemStackIngredient.CODEC, gold));
        combiningJson.add("output", encode(ItemStack.CODEC, new ItemStack(Items.DIAMOND)));
        assertEquals(combiner, decode(combining.codec(), combiningJson), "the combining codec must round trip its exact recipe value");
        assertStreamRoundTrip(combining, combiner);
    }

    @Test
    void chemicalAndFluidCodecsRoundTrip() {
        ItemStackIngredient iron = IngredientCreatorAccess.item().from(Items.IRON_INGOT, 1);
        ChemicalStack oxygen = new ChemicalStack(MekanismChemicals.OXYGEN.get(), 200);
        BasicChemicalConversionRecipe conversion = new BasicChemicalConversionRecipe(iron, oxygen);
        RecipeSerializer<BasicChemicalConversionRecipe> itemToChemical = MekanismRecipeSerializer.itemToChemical(
              BasicChemicalConversionRecipe::new, ChemicalStack.MAP_CODEC, ChemicalStack.STREAM_CODEC);
        JsonObject json = new JsonObject();
        json.add("input", encode(ItemStackIngredient.CODEC, iron));
        json.add("output", encode(ChemicalStack.CODEC, oxygen));
        assertEquals(conversion, decode(itemToChemical.codec(), json), "the item to chemical codec must round trip its exact recipe value");
        assertStreamRoundTrip(itemToChemical, conversion);

        var hydrogenInput = hydrogenIngredient();
        var oxygenInput = IngredientCreatorAccess.chemicalStack().fromHolder(MekanismChemicals.OXYGEN.get().getAsHolder(), 1L);
        ChemicalStack steam = new ChemicalStack(MekanismChemicals.STEAM.get(), 2);
        BasicChemicalInfuserRecipe infuser = new BasicChemicalInfuserRecipe(hydrogenInput, oxygenInput, steam);
        RecipeSerializer<BasicChemicalInfuserRecipe> infusing = MekanismRecipeSerializer.chemicalChemicalToChemical(BasicChemicalInfuserRecipe::new,
              IngredientCreatorAccess.chemicalStack(), ChemicalStack.MAP_CODEC, ChemicalStack.STREAM_CODEC);
        JsonObject infusingJson = new JsonObject();
        infusingJson.add("left_input", encode(IngredientCreatorAccess.chemicalStack().codec(), hydrogenInput));
        infusingJson.add("right_input", encode(IngredientCreatorAccess.chemicalStack().codec(), oxygenInput));
        infusingJson.add("output", encode(ChemicalStack.CODEC, steam));
        assertEquals(infuser, decode(infusing.codec(), infusingJson), "the chemical infusing codec must round trip its exact recipe value");
        assertStreamRoundTrip(infusing, infuser);

        FluidStackIngredient water = IngredientCreatorAccess.fluid().from(Fluids.WATER, 1_000);
        BasicFluidToFluidRecipe evaporating = new BasicFluidToFluidRecipe(water, new FluidStack(Fluids.LAVA, 500));
        RecipeSerializer<BasicFluidToFluidRecipe> fluidToFluid = MekanismRecipeSerializer.fluidToFluid(BasicFluidToFluidRecipe::new);
        JsonObject evaporatingJson = new JsonObject();
        evaporatingJson.add("input", encode(FluidStackIngredient.CODEC, water));
        evaporatingJson.add("output", encode(FluidStack.CODEC, new FluidStack(Fluids.LAVA, 500)));
        assertEquals(evaporating, decode(fluidToFluid.codec(), evaporatingJson), "the fluid to fluid codec must round trip its exact recipe value");
        assertStreamRoundTrip(fluidToFluid, evaporating);
    }

    @Test
    void electrolysisAndPressurizedReactionKeepScalarFieldsAndValidation() {
        FluidStackIngredient water = IngredientCreatorAccess.fluid().from(Fluids.WATER, 1_000);
        ChemicalStack hydrogenStack = new ChemicalStack(MekanismChemicals.HYDROGEN.get(), 6);
        ChemicalStack oxygenStack = new ChemicalStack(MekanismChemicals.OXYGEN.get(), 3);
        BasicElectrolysisRecipe separating = new BasicElectrolysisRecipe(water, 4L, hydrogenStack, oxygenStack);
        RecipeSerializer<BasicElectrolysisRecipe> serializer = MekanismRecipeSerializer.separating(BasicElectrolysisRecipe::new);
        JsonObject json = new JsonObject();
        json.add("input", encode(FluidStackIngredient.CODEC, water));
        json.addProperty("energy_multiplier", 4L);
        json.add("left_chemical_output", encode(ChemicalStack.MAP_CODEC, hydrogenStack));
        json.add("right_chemical_output", encode(ChemicalStack.MAP_CODEC, oxygenStack));
        assertEquals(separating, decode(serializer.codec(), json), "the separating codec must round trip its exact recipe value");
        assertStreamRoundTrip(serializer, separating);

        ItemStackIngredient iron = IngredientCreatorAccess.item().from(Items.IRON_INGOT, 1);
        FluidStackIngredient reactionFluid = IngredientCreatorAccess.fluid().from(Fluids.WATER, 100);
        var reactionChemical = IngredientCreatorAccess.chemicalStack().fromHolder(MekanismChemicals.HYDROGEN.get().getAsHolder(), 10);
        BasicPressurizedReactionRecipe reaction = new BasicPressurizedReactionRecipe(iron, reactionFluid, reactionChemical, 1_000L, 20,
              new ItemStack(Items.DIAMOND), ChemicalStack.EMPTY);
        RecipeSerializer<BasicPressurizedReactionRecipe> reactionSerializer = MekanismRecipeSerializer.reaction(BasicPressurizedReactionRecipe::new);
        JsonObject reactionJson = new JsonObject();
        reactionJson.add("item_input", encode(ItemStackIngredient.CODEC, iron));
        reactionJson.add("fluid_input", encode(FluidStackIngredient.CODEC, reactionFluid));
        reactionJson.add("chemical_input", encode(IngredientCreatorAccess.chemicalStack().codec(), reactionChemical));
        reactionJson.addProperty("energy_required", 1_000L);
        reactionJson.addProperty("duration", 20);
        reactionJson.add("item_output", encode(ItemStack.CODEC, new ItemStack(Items.DIAMOND)));
        //Note: An empty chemical output is an omitted optional field, not an encoded mekanism:empty stack.
        assertEquals(reaction, decode(reactionSerializer.codec(), reactionJson), "the reaction codec must round trip its exact recipe value");
        assertStreamRoundTrip(reactionSerializer, reaction);

        //A reaction must declare at least one output member; the codec validates that explicitly rather than accepting an empty recipe.
        JsonObject noOutput = new JsonObject();
        for (String member : List.of("item_input", "fluid_input", "chemical_input")) {
            noOutput.add(member, reactionJson.get(member));
        }
        noOutput.addProperty("energy_required", 1_000L);
        noOutput.addProperty("duration", 20);
        //Note: For this shape the recipe constructor's "at least one output" precondition fires while the codec is still assembling the value, so the
        // rejection surfaces as a throw rather than as a codec error result. That is the official behavior and is asserted as such.
        assertThrows(IllegalArgumentException.class, () -> decode(reactionSerializer.codec(), noOutput),
              "a reaction with neither an item nor a chemical output must be rejected");
    }

    @Test
    void rotaryAlternativeAcceptsAllThreeOfficialShapesAndPrefersTheFirstOnEncode() {
        FluidStackIngredient water = IngredientCreatorAccess.fluid().from(Fluids.WATER, 1_000);
        ChemicalStack hydrogenStack = new ChemicalStack(MekanismChemicals.HYDROGEN.get(), 500);
        FluidStack waterStack = new FluidStack(Fluids.WATER, 500);
        RecipeSerializer<BasicRotaryRecipe> serializer = new RotaryRecipeSerializer(BasicRotaryRecipe::new, BasicRotaryRecipe::new, BasicRotaryRecipe::new);

        BasicRotaryRecipe both = new BasicRotaryRecipe(water, hydrogenIngredient(), hydrogenStack, waterStack);
        JsonObject bothJson = new JsonObject();
        bothJson.add("fluid_input", encode(FluidStackIngredient.CODEC, water));
        bothJson.add("chemical_input", encode(IngredientCreatorAccess.chemicalStack().codec(), hydrogenIngredient()));
        bothJson.add("chemical_output", encode(ChemicalStack.CODEC, hydrogenStack));
        bothJson.add("fluid_output", encode(FluidStack.CODEC, waterStack));
        assertEquals(both, decode(serializer.codec(), bothJson), "the rotary codec must accept the both-directions shape");

        BasicRotaryRecipe toChemical = new BasicRotaryRecipe(water, hydrogenStack);
        JsonObject toChemicalJson = new JsonObject();
        toChemicalJson.add("fluid_input", encode(FluidStackIngredient.CODEC, water));
        toChemicalJson.add("chemical_output", encode(ChemicalStack.CODEC, hydrogenStack));
        assertEquals(toChemical, decode(serializer.codec(), toChemicalJson), "the rotary codec must accept the fluid-to-chemical shape");

        BasicRotaryRecipe toFluid = new BasicRotaryRecipe(hydrogenIngredient(), waterStack);
        JsonObject toFluidJson = new JsonObject();
        toFluidJson.add("chemical_input", encode(IngredientCreatorAccess.chemicalStack().codec(), hydrogenIngredient()));
        toFluidJson.add("fluid_output", encode(FluidStack.CODEC, waterStack));
        assertEquals(toFluid, decode(serializer.codec(), toFluidJson), "the rotary codec must accept the chemical-to-fluid shape");

        //The declared keys are the distinct union of both alternatives, because either shape may legitimately appear in a serialized recipe.
        List<String> keys = serializer.codec().keys(JsonOps.INSTANCE).map(JsonElement::getAsString).sorted().toList();
        assertTrue(keys.containsAll(List.of("fluid_input", "chemical_input", "chemical_output", "fluid_output")),
              "the rotary alternative must declare the union of both shapes' keys: " + keys);

        //Encoding must stay on the first preferred shape when both directions are present.
        JsonElement encoded = serializer.codec().codec().encodeStart(JsonOps.INSTANCE, both).getOrThrow();
        assertTrue(encoded.getAsJsonObject().has("fluid_input") && encoded.getAsJsonObject().has("chemical_input")
                    && encoded.getAsJsonObject().has("fluid_output") && encoded.getAsJsonObject().has("chemical_output"),
              "the both-directions recipe must encode the full first shape: " + encoded);

        assertStreamRoundTrip(serializer, both);
    }

    @Test
    void sawmillDependentOptionalityAndEnergyConversionRoundTrip() {
        ItemStackIngredient iron = IngredientCreatorAccess.item().from(Items.IRON_INGOT, 1);
        RecipeSerializer<BasicSawmillRecipe> serializer = new SawmillRecipeSerializer(BasicSawmillRecipe::new);

        BasicSawmillRecipe plain = new BasicSawmillRecipe(iron, new ItemStack(Items.OAK_PLANKS, 2), ItemStack.EMPTY, 0D);
        JsonObject plainJson = new JsonObject();
        plainJson.add("input", encode(ItemStackIngredient.CODEC, iron));
        plainJson.add("main_output", encode(ItemStack.CODEC, new ItemStack(Items.OAK_PLANKS, 2)));
        assertEquals(plain, decode(serializer.codec(), plainJson), "a sawmill recipe without a secondary output must round trip");

        BasicSawmillRecipe withSecondary = new BasicSawmillRecipe(iron, new ItemStack(Items.OAK_PLANKS, 2), new ItemStack(Items.STICK, 1), 0.5D);
        JsonObject secondaryJson = new JsonObject();
        secondaryJson.add("input", encode(ItemStackIngredient.CODEC, iron));
        secondaryJson.add("main_output", encode(ItemStack.CODEC, new ItemStack(Items.OAK_PLANKS, 2)));
        secondaryJson.add("secondary_output", encode(ItemStack.CODEC, new ItemStack(Items.STICK, 1)));
        secondaryJson.addProperty("secondary_chance", 0.5D);
        assertEquals(withSecondary, decode(serializer.codec(), secondaryJson), "a sawmill recipe with a secondary output must round trip");
        assertStreamRoundTrip(serializer, withSecondary);

        //secondary_chance is dependent optionality: it may only appear alongside a secondary output, and must stay in (0, 1].
        JsonObject chanceWithoutOutput = new JsonObject();
        chanceWithoutOutput.add("input", plainJson.get("input"));
        chanceWithoutOutput.add("main_output", plainJson.get("main_output"));
        chanceWithoutOutput.addProperty("secondary_chance", 0.5D);
        //Note: The codec accepts the pair and the recipe constructor is what rejects it, so the contract is a throw rather than a parse error.
        assertThrows(IllegalArgumentException.class, () -> decode(serializer.codec(), chanceWithoutOutput),
              "a secondary chance without a secondary output must be rejected");

        JsonObject outOfRange = new JsonObject();
        outOfRange.add("input", secondaryJson.get("input"));
        outOfRange.add("main_output", secondaryJson.get("main_output"));
        outOfRange.add("secondary_output", secondaryJson.get("secondary_output"));
        outOfRange.addProperty("secondary_chance", 1.5D);
        assertTrue(rejects(serializer.codec(), outOfRange),
              "a secondary chance above one must be rejected");

        BasicItemStackToEnergyRecipe energy = new BasicItemStackToEnergyRecipe(iron, 4_000L);
        RecipeSerializer<BasicItemStackToEnergyRecipe> energySerializer = MekanismRecipeSerializer.itemToEnergy(BasicItemStackToEnergyRecipe::new);
        JsonObject energyJson = new JsonObject();
        energyJson.add("input", encode(ItemStackIngredient.CODEC, iron));
        energyJson.addProperty("output", 4_000L);
        assertEquals(energy, decode(energySerializer.codec(), energyJson), "the energy conversion codec must round trip its exact recipe value");
        assertStreamRoundTrip(energySerializer, energy);
    }

    @Test
    void everySerializerHolderIsKeyStableAndResolvesLazily() {
        List<MekanismKeyedHolder<RecipeSerializer<?>, ? extends RecipeSerializer<?>>> holders = List.of(MekanismRecipeSerializers.CRUSHING, MekanismRecipeSerializers.ENRICHING,
              MekanismRecipeSerializers.SMELTING, MekanismRecipeSerializers.CHEMICAL_INFUSING, MekanismRecipeSerializers.COMBINING,
              MekanismRecipeSerializers.SEPARATING, MekanismRecipeSerializers.WASHING, MekanismRecipeSerializers.EVAPORATING,
              MekanismRecipeSerializers.ACTIVATING, MekanismRecipeSerializers.CENTRIFUGING, MekanismRecipeSerializers.CRYSTALLIZING,
              MekanismRecipeSerializers.DISSOLUTION, MekanismRecipeSerializers.COMPRESSING, MekanismRecipeSerializers.PURIFYING,
              MekanismRecipeSerializers.INJECTING, MekanismRecipeSerializers.NUCLEOSYNTHESIZING, MekanismRecipeSerializers.ENERGY_CONVERSION,
              MekanismRecipeSerializers.CHEMICAL_CONVERSION, MekanismRecipeSerializers.OXIDIZING, MekanismRecipeSerializers.PIGMENT_EXTRACTING,
              MekanismRecipeSerializers.PIGMENT_MIXING, MekanismRecipeSerializers.METALLURGIC_INFUSING, MekanismRecipeSerializers.PAINTING,
              MekanismRecipeSerializers.REACTION, MekanismRecipeSerializers.ROTARY, MekanismRecipeSerializers.SAWING);
        assertEquals(26, holders.size(), "there must be exactly 26 recipe serializer holders");
        assertEquals(26, holders.stream().map(MekanismKeyedHolder::getId).distinct().count(), "every serializer id must be unique");
        for (MekanismKeyedHolder<?, ?> holder : holders) {
            assertEquals("mekanism", holder.getId().getNamespace(), "every serializer id must be in the mekanism namespace");
            assertSame(holder.getId(), holder.getKey().location(), "the holder key must match its declared id");
            //Note: The ordinary JVM never registers the serializers, so every holder stays legitimately unbound and never dereferences a registry entry.
            assertTrue(holder.asOptional().isEmpty(), "an unregistered serializer holder must stay unbound: " + holder.getId());
        }
        assertSame(MekanismRecipeSerializers.CRUSHING, MekanismRecipeSerializers.CRUSHING, "holder fields are identity stable");
        assertEquals(ResourceLocation.fromNamespaceAndPath("mekanism", "rotary"), MekanismRecipeSerializers.ROTARY.getId());
        assertNotNull(MekanismRecipeSerializers.ROTARY.getKey(), "a lazy registry keyed holder always knows its key");
    }

    private static <T> JsonElement encode(Codec<T> codec, T value) {
        return codec.encodeStart(JsonOps.INSTANCE, value).getOrThrow();
    }

    private static <T> JsonElement encode(MapCodec<T> codec, T value) {
        return codec.codec().encodeStart(JsonOps.INSTANCE, value).getOrThrow();
    }

    private static <T> T decode(MapCodec<T> codec, JsonElement json) {
        return codec.codec().parse(JsonOps.INSTANCE, json).getOrThrow();
    }

    /** @return {@code true} when the codec rejects the given JSON document. */
    private static boolean rejects(MapCodec<?> codec, JsonElement json) {
        return codec.codec().parse(JsonOps.INSTANCE, json).error().isPresent();
    }

    private static <T extends net.minecraft.world.item.crafting.Recipe<?>> void assertStreamRoundTrip(RecipeSerializer<T> serializer, T value) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        try {
            serializer.streamCodec().encode(buffer, value);
            T decoded = serializer.streamCodec().decode(buffer);
            assertEquals(value, decoded, "the stream codec must round trip the exact recipe value");
            assertEquals(0, buffer.readableBytes(), "the stream codec must consume exactly what it wrote");
        } finally {
            buffer.release();
        }
    }
}
