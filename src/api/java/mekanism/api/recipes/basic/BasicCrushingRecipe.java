package mekanism.api.recipes.basic;

import mekanism.api.annotations.NothingNullByDefault;
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
public class BasicCrushingRecipe extends BasicItemStackToItemStackRecipe {

    private static final Holder<Item> CRUSHER = MekanismKeyedHolder.<Item, Item>registryKeyed(ResourceKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("mekanism", "crusher")), () -> BuiltInRegistries.ITEM);

    public BasicCrushingRecipe(ItemStackIngredient input, ItemStack output) {
        super(input, output, MekanismRecipeTypes.TYPE_CRUSHING.value());
    }

    @Override
    public RecipeSerializer<BasicCrushingRecipe> getSerializer() {
        return MekanismRecipeSerializers.CRUSHING.value();
    }

    @Override
    public String getGroup() {
        return "crusher";
    }

    @Override
    public ItemStack getToastSymbol() {
        return new ItemStack(CRUSHER);
    }

}