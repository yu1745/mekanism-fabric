package mekanism.api.recipes.basic;

import mekanism.api.annotations.NothingNullByDefault;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.recipes.MekanismKeyedHolder;
import mekanism.api.recipes.MekanismRecipeSerializers;
import mekanism.api.recipes.MekanismRecipeTypes;
import mekanism.api.recipes.ingredients.ChemicalStackIngredient;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;

@NothingNullByDefault
public class BasicPigmentMixingRecipe extends BasicChemicalChemicalToChemicalRecipe {

    private static final Holder<Item> PIGMENT_MIXER = MekanismKeyedHolder.registryKeyed(ResourceKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("mekanism", "pigment_mixer")), () -> BuiltInRegistries.ITEM);

    /**
     * @param leftInput  Left input.
     * @param rightInput Right input.
     * @param output     Output.
     *
     * @apiNote The order of the inputs does not matter.
     */
    public BasicPigmentMixingRecipe(ChemicalStackIngredient leftInput, ChemicalStackIngredient rightInput, ChemicalStack output) {
        super(leftInput, rightInput, output, MekanismRecipeTypes.TYPE_PIGMENT_MIXING.value());
    }

    @Override
    public String getGroup() {
        return "pigment_mixer";
    }

    @Override
    public ItemStack getToastSymbol() {
        return new ItemStack(PIGMENT_MIXER);
    }

    @Override
    public RecipeSerializer<BasicPigmentMixingRecipe> getSerializer() {
        return MekanismRecipeSerializers.PIGMENT_MIXING.get();
    }
}