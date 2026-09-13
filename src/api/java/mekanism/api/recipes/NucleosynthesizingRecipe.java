package mekanism.api.recipes;

import mekanism.api.annotations.NothingNullByDefault;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * Extension of {@link ItemStackChemicalToItemStackRecipe} with a defined amount of ticks needed to process. Input: ItemStack
 * <br>
 * Input: Chemical (Base value, will be multiplied by a per tick amount)
 * <br>
 * Output: ItemStack
 *
 * @apiNote Nucleosynthesizers can process this recipe type.
 */
@NothingNullByDefault
public abstract class NucleosynthesizingRecipe extends ItemStackChemicalToItemStackRecipe {

    private static final Holder<Item> ANTIPROTONIC_NUCLEOSYNTHESIZER = MekanismKeyedHolder.<Item, Item>registryKeyed(ResourceKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("mekanism", "antiprotonic_nucleosynthesizer")), () -> BuiltInRegistries.ITEM);

    @Override
    public final RecipeType<NucleosynthesizingRecipe> getType() {
        return MekanismRecipeTypes.TYPE_NUCLEOSYNTHESIZING.value();
    }

    /**
     * Gets the duration in ticks this recipe takes to complete.
     */
    public abstract int getDuration();

    @Override
    public String getGroup() {
        return "antiprotonic_nucleosynthesizer";
    }

    @Override
    public ItemStack getToastSymbol() {
        return new ItemStack(ANTIPROTONIC_NUCLEOSYNTHESIZER);
    }
}
