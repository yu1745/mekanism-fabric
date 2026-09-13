package mekanism.api.recipes.basic;

import mekanism.api.annotations.NothingNullByDefault;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.recipes.MekanismKeyedHolder;
import mekanism.api.recipes.MekanismRecipeSerializers;
import mekanism.api.recipes.MekanismRecipeTypes;
import mekanism.api.recipes.ingredients.ItemStackIngredient;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;

@NothingNullByDefault
public class BasicChemicalConversionRecipe extends BasicItemStackToChemicalRecipe {

    private static final Holder<Item> CREATIVE_CHEMICAL_TANK = MekanismKeyedHolder.registryKeyed(ResourceKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("mekanism", "creative_chemical_tank")), () -> BuiltInRegistries.ITEM);

    public BasicChemicalConversionRecipe(ItemStackIngredient input, ChemicalStack output) {
        super(input, output, MekanismRecipeTypes.TYPE_CHEMICAL_CONVERSION.value());
    }

    @Override
    public RecipeSerializer<BasicChemicalConversionRecipe> getSerializer() {
        return MekanismRecipeSerializers.CHEMICAL_CONVERSION.value();
    }

    @Override
    public String getGroup() {
        return "chemical_conversion";
    }

    @Override
    public ItemStack getToastSymbol() {
        return new ItemStack(CREATIVE_CHEMICAL_TANK);
    }

}