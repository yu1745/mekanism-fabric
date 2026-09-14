package mekanism.api.recipes.ingredients;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import java.util.List;
import mekanism.api.MekanismServices;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalBuilder;
import mekanism.api.chemical.ChemicalRegistry;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.fluid.FluidStack;
import mekanism.api.recipes.ingredients.chemical.ChemicalIngredient;
import mekanism.api.recipes.ingredients.chemical.ChemicalIngredientTypeRegistry;
import mekanism.api.recipes.ingredients.chemical.CompoundChemicalIngredient;
import mekanism.api.recipes.ingredients.creator.IIngredientCreatorProvider;
import mekanism.api.recipes.ingredients.creator.IngredientCreatorAccess;
import mekanism.api.recipes.ingredients.fluid.FluidIngredient;
import mekanism.common.registries.MekanismChemicals;
import mekanism.common.registries.MekanismChemicalIngredientTypes;
import mekanism.fabric.registry.FabricDefaultedRegistry;
import mekanism.fabric.recipe.FabricIngredientCreatorProvider;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IngredientServiceCodecClosureTest {
    private static RegistryAccess registries;

    @BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        FabricDefaultedRegistry<Chemical> registry = installedChemicalRegistry();
        //A composite access: every built-in registry plus the isolated chemical registry, for the item/fluid/chemical stream codecs
        List<net.minecraft.core.Registry<?>> entries = new java.util.ArrayList<>();
        BuiltInRegistries.REGISTRY.holders().forEach(registryHolder -> entries.add(registryHolder.value()));
        entries.add(registry);
        registries = new RegistryAccess.ImmutableRegistryAccess(entries);
        //Bind the tags under test the way a real datapack would; plain unit bootstrap leaves them unbound
        BuiltInRegistries.ITEM.bindTags(java.util.Map.of(
              net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, ResourceLocation.fromNamespaceAndPath("minecraft", "planks")),
              List.of(BuiltInRegistries.ITEM.getHolderOrThrow(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ITEM,
                    ResourceLocation.fromNamespaceAndPath("minecraft", "oak_planks"))))));
        BuiltInRegistries.FLUID.bindTags(java.util.Map.of(
              net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.FLUID, ResourceLocation.fromNamespaceAndPath("minecraft", "water")),
              List.of(Fluids.WATER.builtInRegistryHolder())));
        registry.bindTags(java.util.Map.of(
              net.minecraft.tags.TagKey.create(ChemicalRegistry.REGISTRY_KEY, ResourceLocation.fromNamespaceAndPath("mekanism", "gaseous")),
              List.of(MekanismChemicals.STEAM.get().getAsHolder())));
        //The ingredient-type registry is created, root-published (SYNCED) and frozen by its construction owner, exactly as in production.
        mekanism.fabric.chemical.FabricChemicalIngredientTypeRegistryLifecycle.initialize();
    }

    @SuppressWarnings("unchecked")
    private static FabricDefaultedRegistry<Chemical> installedChemicalRegistry() {
        try {
            return (FabricDefaultedRegistry<Chemical>) ChemicalRegistry.mutable();
        } catch (IllegalStateException notInstalled) {
            FabricDefaultedRegistry<Chemical> registry = FabricDefaultedRegistry.create(ChemicalRegistry.REGISTRY_KEY, ChemicalRegistry.EMPTY_KEY, true);
            ChemicalRegistry.install(registry);
            Chemical empty = new Chemical(ChemicalBuilder.builder());
            Holder.Reference<Chemical> holder = registry.register(ChemicalRegistry.EMPTY_KEY, empty, RegistrationInfo.BUILT_IN);
            ChemicalRegistry.publishEmpty(empty, holder);
            MekanismChemicals.CHEMICALS.registerAll(registry);
            return registry;
        }
    }

    @Test
    void itemIngredientRoundTripsItemTagAndCountForms() {
        ItemStackIngredient itemForm = IngredientCreatorAccess.item().from(Items.IRON_INGOT, 3);
        JsonElement encoded = encode(ItemStackIngredient.CODEC, itemForm);
        assertEquals("{\"item\":\"minecraft:iron_ingot\",\"count\":3}", encoded.toString());
        assertEquals(itemForm, decode(ItemStackIngredient.CODEC, encoded));
        assertEquals(3, itemForm.getNeededAmount(new ItemStack(Items.IRON_INGOT)));
        assertTrue(itemForm.test(new ItemStack(Items.IRON_INGOT, 3)));
        assertFalse(itemForm.test(new ItemStack(Items.IRON_INGOT, 2)));
        assertFalse(itemForm.testType(new ItemStack(Items.GOLD_INGOT)));

        ItemStackIngredient tagForm = IngredientCreatorAccess.item().from(TagKeyOf.itemStorageBlocks());
        JsonElement tagJson = encode(ItemStackIngredient.CODEC, tagForm);
        assertEquals("{\"tag\":\"minecraft:planks\",\"count\":1}", tagJson.toString());
        assertEquals(tagForm, decode(ItemStackIngredient.CODEC, tagJson));
        assertTrue(tagForm.testType(new ItemStack(Items.OAK_PLANKS)));
        assertEquals(1, tagForm.getNeededAmount(new ItemStack(Items.OAK_PLANKS)));
        //Count one is always written and still defaults to one when decoding legacy input
        assertEquals(1, decode(ItemStackIngredient.CODEC, parse("{\"tag\":\"minecraft:planks\"}")).count());

        ItemStackIngredient streamForm = IngredientCreatorAccess.item().from(Items.DIAMOND, 2);
        assertEquals(streamForm, roundTrip(ItemStackIngredient.STREAM_CODEC, streamForm));
        //A component-bearing stack constructs a non-strict component ingredient
        ItemStack named = new ItemStack(Items.DIAMOND);
        named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Named"));
        ItemStackIngredient componentForm = IngredientCreatorAccess.item().from(named);
        assertTrue(componentForm.hasComponentPredicate());
        assertTrue(componentForm.testType(named));
        ItemStack plain = new ItemStack(Items.DIAMOND);
        assertFalse(componentForm.testType(plain), "a stack missing an expected component does not match");
        assertFalse(componentForm.testType(new ItemStack(Items.IRON_INGOT)));
    }

    @Test
    void itemIngredientDecodesOfficialCompoundUnionWithLegacyAlias() {
        String officialForm = "{\"type\":\"neoforge:compound\",\"children\":[{\"tag\":\"minecraft:planks\"},{\"item\":\"minecraft:stick\"}],\"count\":2}";
        ItemStackIngredient compound = decode(ItemStackIngredient.CODEC, parse(officialForm));
        assertTrue(compound.testType(new ItemStack(Items.OAK_PLANKS)));
        assertTrue(compound.testType(new ItemStack(Items.STICK)));
        assertFalse(compound.testType(new ItemStack(Items.IRON_INGOT)));
        assertEquals(2, compound.getNeededAmount(new ItemStack(Items.STICK)));
        assertEquals(2, compound.ingredients().size(), "union forms keep their compound children");
        assertEquals(2, compound.getRepresentations().size());
        assertEquals(officialForm, encode(ItemStackIngredient.CODEC, compound).toString());

        //The documented legacy child-field alias is accepted
        ItemStackIngredient aliased = decode(ItemStackIngredient.CODEC, parse("{\"type\":\"neoforge:compound\",\"ingredients\":[{\"item\":\"minecraft:stick\"},{\"item\":\"minecraft:iron_ingot\"}]}"));
        assertTrue(aliased.testType(new ItemStack(Items.IRON_INGOT)));
        assertEquals("{\"type\":\"neoforge:compound\",\"children\":[{\"item\":\"minecraft:stick\"},{\"item\":\"minecraft:iron_ingot\"}],\"count\":1}", encode(ItemStackIngredient.CODEC, aliased).toString());

        //Unknown custom ingredient types fail decoding rather than becoming permissive predicates
        assertTrue(ItemStackIngredient.CODEC.decode(JsonOps.INSTANCE, parse("{\"type\":\"neoforge:mystery\"}")).error().isPresent());
        assertTrue(ItemStackIngredient.CODEC.decode(JsonOps.INSTANCE, parse("{\"type\":5,\"item\":\"minecraft:stick\"}")).error().isPresent(),
              "a malformed custom type cannot fall through to a permissive vanilla ingredient");
        String malformedChildrenError = ItemStackIngredient.CODEC.decode(JsonOps.INSTANCE,
              parse("{\"type\":\"neoforge:compound\",\"children\":{}}")).error().orElseThrow().message();
        assertFalse(malformedChildrenError.contains("No ingredients field found"),
              "a malformed canonical children field retains its decode error rather than falling back to the alias");
        assertTrue(ItemStackIngredient.CODEC.decode(JsonOps.INSTANCE, parse("{\"type\":\"neoforge:compound\",\"children\":[]}")).error().isPresent());
        assertTrue(ItemStackIngredient.CODEC.decode(JsonOps.INSTANCE, parse("{\"count\":1}")).error().isPresent(), "an empty vanilla ingredient is rejected");
        assertThrows(IllegalArgumentException.class, () -> ItemStackIngredient.of(Ingredient.EMPTY, 1));
        assertThrows(IllegalArgumentException.class, () -> ItemStackIngredient.of(Ingredient.of(Items.STICK), 0));
        //Compound children with concrete items survive the wire exactly; tag children flatten to their current stacks, the platform wire behavior
        ItemStackIngredient concreteCompound = ItemStackIngredient.of(List.of(Ingredient.of(Items.STICK), Ingredient.of(Items.IRON_INGOT)), 2);
        assertEquals(concreteCompound, roundTrip(ItemStackIngredient.STREAM_CODEC, concreteCompound));
        ItemStackIngredient wireForm = roundTrip(ItemStackIngredient.STREAM_CODEC, compound);
        assertEquals(2, wireForm.count());
        assertTrue(wireForm.testType(new ItemStack(Items.OAK_PLANKS)));
        assertTrue(wireForm.testType(new ItemStack(Items.STICK)));
        assertFalse(wireForm.testType(new ItemStack(Items.IRON_INGOT)));
    }

    @Test
    void fluidIngredientRoundTripsSingleTagCompoundAndComponentsInMb() {
        FluidStackIngredient single = IngredientCreatorAccess.fluid().fromHolder(Fluids.WATER.builtInRegistryHolder(), 250);
        assertEquals("{\"fluid\":\"minecraft:water\",\"amount\":250}", encode(FluidStackIngredient.CODEC, single).toString());
        assertEquals(single, decode(FluidStackIngredient.CODEC, encode(FluidStackIngredient.CODEC, single)));
        assertEquals(250, single.amount());
        assertEquals(250, single.getNeededAmount(new FluidStack(Fluids.WATER, 1_000)));
        assertTrue(single.test(new FluidStack(Fluids.WATER, 250)));
        assertFalse(single.test(new FluidStack(Fluids.WATER, 249)));
        assertEquals(250, single.getRepresentations().getFirst().getAmount(), "representations carry the mB amount, never a platform droplet unit");

        FluidIngredient tag = FluidIngredient.of(TagKeyOf.water());
        assertEquals("{\"tag\":\"minecraft:water\"}", encode(FluidIngredient.MAP_CODEC.codec(), tag).toString());
        assertTrue(tag.test(new FluidStack(Fluids.WATER, 1)));
        assertFalse(tag.test(new FluidStack(Fluids.LAVA, 1)));

        FluidIngredient compound = FluidIngredient.ofIngredients(List.of(FluidIngredient.of(Fluids.WATER.builtInRegistryHolder()), FluidIngredient.of(Fluids.LAVA.builtInRegistryHolder())));
        assertEquals("[{\"fluid\":\"minecraft:water\"},{\"fluid\":\"minecraft:lava\"}]", encode(FluidIngredient.CODEC, compound).toString());
        assertEquals(compound, decode(FluidIngredient.CODEC, encode(FluidIngredient.CODEC, compound)));
        assertTrue(compound.test(new FluidStack(Fluids.LAVA, 5)));
        FluidStackIngredient sizedCompound = FluidStackIngredient.of(compound, 500);
        String typedCompound = "{\"type\":\"neoforge:compound\",\"children\":[{\"fluid\":\"minecraft:water\"},{\"fluid\":\"minecraft:lava\"}],\"amount\":500}";
        assertEquals(typedCompound, encode(FluidStackIngredient.CODEC, sizedCompound).toString());
        assertEquals(sizedCompound, decode(FluidStackIngredient.CODEC, parse(typedCompound)));
        assertEquals(sizedCompound, decode(FluidStackIngredient.CODEC,
              parse("{\"type\":\"neoforge:compound\",\"ingredients\":[{\"fluid\":\"minecraft:water\"},{\"fluid\":\"minecraft:lava\"}],\"amount\":500}")));

        FluidStack tainted = new FluidStack(Fluids.WATER, 1);
        tainted.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Tainted"));
        FluidIngredient components = IngredientCreatorAccess.fluid().from(tainted).ingredient();
        assertTrue(components.hasComponentPredicate());
        assertTrue(components.test(tainted));
        assertFalse(components.test(new FluidStack(Fluids.WATER, 1)), "a stack missing an expected component does not match");
        assertTrue(FluidIngredient.CODEC.decode(JsonOps.INSTANCE,
              parse("{\"type\":\"neoforge:components\",\"fluids\":[\"minecraft:water\"],\"components\":{}}")).error().isEmpty());
        assertEquals(1_000, decode(FluidStackIngredient.CODEC, parse("{\"fluid\":\"minecraft:water\"}")).amount(),
              "missing amount defaults to one bucket");
        FluidIngredient empty = FluidIngredient.empty();
        assertTrue(empty.test(FluidStack.EMPTY));
        assertFalse(empty.test(new FluidStack(Fluids.WATER, 1)));
        assertEquals(empty, decode(FluidIngredient.CODEC, encode(FluidIngredient.CODEC, empty)));
        assertEquals(empty, roundTrip(FluidIngredient.STREAM_CODEC, empty));
        assertThrows(IllegalArgumentException.class, () -> FluidIngredient.of(Fluids.EMPTY.builtInRegistryHolder()));
        assertDoesNotThrow(() -> assertTrue(FluidIngredient.CODEC.decode(JsonOps.INSTANCE, parse("{\"fluid\":\"minecraft:empty\"}")).error().isPresent()));
        assertDoesNotThrow(() -> assertTrue(FluidIngredient.CODEC.decode(JsonOps.INSTANCE,
              parse("{\"type\":\"neoforge:components\",\"fluids\":[],\"components\":{}}")).error().isEmpty()));
        var absentTag = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.FLUID,
              ResourceLocation.fromNamespaceAndPath("mekanism", "definitely_absent"));
        assertDoesNotThrow(() -> IngredientCreatorAccess.fluid().from(absentTag, 100),
              "dynamic empty tags remain constructible and are filtered as incomplete later");
        RegistryFriendlyByteBuf invalidAmount = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        FluidIngredient.STREAM_CODEC.encode(invalidAmount, FluidIngredient.of(Fluids.WATER.builtInRegistryHolder()));
        ByteBufCodecs.VAR_INT.encode(invalidAmount, 0);
        assertThrows(IllegalArgumentException.class, () -> FluidStackIngredient.STREAM_CODEC.decode(invalidAmount));
        assertTrue(FluidIngredient.MAP_CODEC.decode(JsonOps.INSTANCE, JsonOps.INSTANCE.getMap(parse("{\"type\":\"mekanism:other\"}")).result().orElseThrow()).error().isPresent(),
              "unknown fluid ingredient types fail explicitly");
        assertEquals(single, roundTrip(FluidStackIngredient.STREAM_CODEC, single));
        assertEquals(compound, roundTrip(FluidIngredient.STREAM_CODEC, compound));
        assertEquals(components, roundTrip(FluidIngredient.STREAM_CODEC, components));
    }

    @Test
    void chemicalIngredientCodecFamilySupportsAllSixTypesAndAliases() {
        var creator = IngredientCreatorAccess.chemical();
        //Type-less single form
        ChemicalIngredient single = creator.of(MekanismChemicals.HYDROGEN.get().getAsHolder());
        assertEquals("{\"chemical\":\"mekanism:hydrogen\"}", encode(creator.mapCodecNonEmpty().codec(), single).toString());
        assertEquals(single, decode(creator.mapCodecNonEmpty().codec(), parse("{\"chemical\":\"mekanism:hydrogen\"}")));
        assertTrue(creator.mapCodecNonEmpty().codec().decode(JsonOps.INSTANCE,
              parse("{\"chemical\":\"mekanism:hydrogen\",\"tag\":\"mekanism:gaseous\"}")).error().isPresent(),
              "the single/tag fallback is exclusive-or");
        //Type-less tag form
        ChemicalIngredient tag = creator.tag(TagKeyOf.chemicalGaseous());
        assertEquals("{\"tag\":\"mekanism:gaseous\"}", encode(creator.mapCodecNonEmpty().codec(), tag).toString());
        assertTrue(tag.test(MekanismChemicals.STEAM.get().getAsHolder()));
        //Typed difference
        ChemicalIngredient difference = creator.difference(creator.ofHolders(MekanismChemicals.HYDROGEN.get().getAsHolder(), MekanismChemicals.OXYGEN.get().getAsHolder()),
              creator.of(MekanismChemicals.OXYGEN.get().getAsHolder()));
        JsonElement differenceJson = encode(creator.mapCodecNonEmpty().codec(), difference);
        assertTrue(differenceJson.toString().contains("\"type\":\"mekanism:difference\""), differenceJson.toString());
        assertEquals(difference, decode(creator.mapCodecNonEmpty().codec(), differenceJson));
        assertTrue(difference.test(MekanismChemicals.HYDROGEN.get().getAsHolder()));
        assertFalse(difference.test(MekanismChemicals.OXYGEN.get().getAsHolder()));
        //Typed intersection
        ChemicalIngredient intersection = creator.intersection(creator.ofHolders(MekanismChemicals.HYDROGEN.get().getAsHolder(), MekanismChemicals.OXYGEN.get().getAsHolder()),
              creator.tag(TagKeyOf.chemicalGaseous()));
        assertEquals(intersection, decode(creator.mapCodecNonEmpty().codec(), encode(creator.mapCodecNonEmpty().codec(), intersection)));
        //Typed empty is registered but rejected by the non-empty map validation, exactly like the original
        assertTrue(creator.mapCodecNonEmpty().codec().decode(JsonOps.INSTANCE, parse("{\"type\":\"mekanism:empty\"}")).error().isPresent(),
              "explicitly empty ingredients fail the non-empty validation");
        assertTrue(creator.mapCodecNonEmpty().codec().decode(JsonOps.INSTANCE, parse("{}")).error().isPresent(), "empty ingredients are not valid non-empty map forms");
        //Typed compound with the legacy ingredients alias, and the array form
        JsonElement compoundAliased = parse("{\"type\":\"mekanism:compound\",\"ingredients\":[{\"chemical\":\"mekanism:hydrogen\"},{\"chemical\":\"mekanism:oxygen\"}]}");
        ChemicalIngredient aliasedCompound = decode(creator.mapCodecNonEmpty().codec(), compoundAliased);
        assertTrue(aliasedCompound instanceof CompoundChemicalIngredient);
        assertTrue(aliasedCompound.test(MekanismChemicals.OXYGEN.get().getAsHolder()));
        String canonicalCompound = encode(creator.mapCodecNonEmpty().codec(), aliasedCompound).toString();
        assertTrue(canonicalCompound.contains("\"children\":"), canonicalCompound);
        ChemicalIngredient arrayForm = decode(creator.codec(), parse("[{\"chemical\":\"mekanism:hydrogen\"},{\"chemical\":\"mekanism:oxygen\"}]"));
        assertTrue(arrayForm instanceof CompoundChemicalIngredient);
        assertTrue(creator.listCodecMultipleElements().decode(JsonOps.INSTANCE, parse("[{\"chemical\":\"mekanism:hydrogen\"}]")).error().isPresent(),
              "compound lists require multiple elements");
        assertTrue(creator.mapCodecNonEmpty().codec().decode(JsonOps.INSTANCE, parse("{\"type\":\"mekanism:unknown\"}")).error().isPresent(),
              "unknown chemical ingredient types fail");
    }

    @Test
    void chemicalIngredientStreamUsesExpandedHolderListsAndSizedWireOrder() {
        var creator = IngredientCreatorAccess.chemical();
        ChemicalIngredient union = creator.ofHolders(MekanismChemicals.HYDROGEN.get().getAsHolder(), MekanismChemicals.OXYGEN.get().getAsHolder());
        ChemicalIngredient decoded = roundTrip(creator.streamCodec(), union);
        assertEquals(2, decoded.getChemicalHolders().size(), "stream ingredients sync as expanded holder lists");
        assertTrue(decoded.test(MekanismChemicals.HYDROGEN.get().getAsHolder()));
        assertTrue(decoded.test(MekanismChemicals.OXYGEN.get().getAsHolder()));

        long amount = (1L << 40) + 7;
        ChemicalStackIngredient sized = IngredientCreatorAccess.chemicalStack().from(union, amount);
        ChemicalStackIngredient roundTripped = roundTrip(ChemicalStackIngredient.STREAM_CODEC, sized);
        assertEquals(amount, roundTripped.amount(), "true-long amounts survive the sized ingredient wire order");
        assertTrue(roundTripped.test(new ChemicalStack(MekanismChemicals.HYDROGEN.get(), amount)));
        assertFalse(roundTripped.test(new ChemicalStack(MekanismChemicals.HYDROGEN.get(), amount - 1)));
        assertEquals(amount, sized.getNeededAmount(new ChemicalStack(MekanismChemicals.OXYGEN.get(), 1)));
        assertEquals(2, sized.getRepresentations().size());
        assertEquals(amount, sized.getRepresentations().getFirst().getAmount());
        assertThrows(IllegalArgumentException.class, () -> new ChemicalStackIngredient(union, 0));
        JsonElement sizedJson = encode(ChemicalStackIngredient.CODEC, sized);
        assertTrue(sizedJson.toString().contains("\"amount\":" + amount), sizedJson.toString());
        assertEquals(sized, decode(ChemicalStackIngredient.CODEC, sizedJson));
    }

    @Test
    void creatorServiceAndChemicalTypeRegistryOwnership() {
        IIngredientCreatorProvider provider = MekanismServices.load(IIngredientCreatorProvider.class);
        assertEquals(FabricIngredientCreatorProvider.class, provider.getClass(), "the service descriptor supplies the single Fabric provider");
        assertEquals(provider.getClass(), MekanismServices.load(IIngredientCreatorProvider.class).getClass(), "loading the service again discovers the same single provider");
        assertSame(IngredientCreatorAccess.item(), provider.itemStackIngredientCreator());
        assertSame(IngredientCreatorAccess.fluid(), provider.fluidStackIngredientCreator());
        assertSame(IngredientCreatorAccess.chemicalStack(), provider.chemicalStackIngredientCreator());
        assertSame(IngredientCreatorAccess.chemical(), provider.chemicalIngredientCreator());

        assertTrue(ChemicalIngredientTypeRegistry.isFrozen());
        for (ResourceLocation id : new ResourceLocation[]{ChemicalIngredientTypeRegistry.COMPOUND, ChemicalIngredientTypeRegistry.DIFFERENCE,
              ChemicalIngredientTypeRegistry.EMPTY, ChemicalIngredientTypeRegistry.INTERSECTION, ChemicalIngredientTypeRegistry.SINGLE, ChemicalIngredientTypeRegistry.TAG}) {
            assertNotNull(ChemicalIngredientTypeRegistry.codec(id), id.toString());
        }
        assertEquals(ChemicalIngredientTypeRegistry.EMPTY, ChemicalIngredientTypeRegistry.idOf(mekanism.api.recipes.ingredients.chemical.EmptyChemicalIngredient.CODEC));
        assertEquals(ChemicalIngredientTypeRegistry.TAG, ChemicalIngredientTypeRegistry.idOf(mekanism.api.recipes.ingredients.chemical.TagChemicalIngredient.CODEC));
        assertThrows(IllegalStateException.class, () -> ChemicalIngredientTypeRegistry.register(ChemicalIngredientTypeRegistry.COMPOUND,
              mekanism.api.recipes.ingredients.chemical.CompoundChemicalIngredient.CODEC), "late registration after the freeze fails");
        assertThrows(IllegalArgumentException.class, () -> ChemicalIngredientTypeRegistry.codec(ResourceLocation.fromNamespaceAndPath("mekanism", "unknown")));
    }

    private static <T> T decode(Codec<T> codec, JsonElement json) {
        return codec.decode(JsonOps.INSTANCE, json).getOrThrow().getFirst();
    }

    private static <T> JsonElement encode(Codec<T> codec, T value) {
        return codec.encodeStart(JsonOps.INSTANCE, value).getOrThrow();
    }

    private static JsonElement parse(String json) {
        return JsonParser.parseString(json);
    }

    private <T> T roundTrip(net.minecraft.network.codec.StreamCodec<RegistryFriendlyByteBuf, T> codec, T value) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        codec.encode(buffer, value);
        return codec.decode(buffer);
    }

    /** Small local tag key factories keeping the test self-contained. */
    private static final class TagKeyOf {
        static net.minecraft.tags.TagKey<net.minecraft.world.item.Item> itemStorageBlocks() {
            return net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, ResourceLocation.fromNamespaceAndPath("minecraft", "planks"));
        }

        static net.minecraft.tags.TagKey<net.minecraft.world.level.material.Fluid> water() {
            return net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.FLUID, ResourceLocation.fromNamespaceAndPath("minecraft", "water"));
        }

        static net.minecraft.tags.TagKey<Chemical> chemicalGaseous() {
            return net.minecraft.tags.TagKey.create(ChemicalRegistry.REGISTRY_KEY, ResourceLocation.fromNamespaceAndPath("mekanism", "gaseous"));
        }
    }
}
