package mekanism.api.recipes.basic;

import mekanism.api.annotations.NothingNullByDefault;
import mekanism.api.recipes.MekanismKeyedHolder;
import mekanism.api.recipes.MekanismRecipeSerializers;
import mekanism.api.recipes.MekanismRecipeTypes;
import mekanism.api.recipes.ingredients.ChemicalStackIngredient;
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
public class BasicInjectingRecipe extends BasicItemStackChemicalToItemStackRecipe {

    private static final Holder<Item> CHEMICAL_INJECTION_CHAMBER = MekanismKeyedHolder.registryKeyed(ResourceKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("mekanism", "chemical_injection_chamber")), () -> BuiltInRegistries.ITEM);

    public BasicInjectingRecipe(ItemStackIngredient itemInput, ChemicalStackIngredient chemicalInput, ItemStack output, boolean perTickUsage) {
        super(itemInput, chemicalInput, output, perTickUsage, MekanismRecipeTypes.TYPE_INJECTING.value());
    }

    @Override
    public RecipeSerializer<BasicInjectingRecipe> getSerializer() {
        return MekanismRecipeSerializers.INJECTING.value();
    }

    @Override
    public String getGroup() {
        return "chemical_injection_chamber";
    }

    @Override
    public ItemStack getToastSymbol() {
        return new ItemStack(CHEMICAL_INJECTION_CHAMBER);
    }

}