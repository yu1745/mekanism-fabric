package mekanism.api.recipes;

import mekanism.api.annotations.NothingNullByDefault;
import mekanism.api.chemical.ChemicalStack;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;

@NothingNullByDefault
public abstract class ChemicalDissolutionRecipe extends ItemStackChemicalToObjectRecipe<ChemicalStack> {

    private static final Holder<Item> CHEMICAL_DISSOLUTION_CHAMBER = MekanismKeyedHolder.registryKeyed(ResourceKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("mekanism", "chemical_dissolution_chamber")), () -> BuiltInRegistries.ITEM);

    @Override
    public final RecipeType<ChemicalDissolutionRecipe> getType() {
        return MekanismRecipeTypes.TYPE_DISSOLUTION.value();
    }

    @Override
    public String getGroup() {
        return "chemical_dissolution_chamber";
    }

    @Override
    public ItemStack getToastSymbol() {
        return new ItemStack(CHEMICAL_DISSOLUTION_CHAMBER);
    }
}
