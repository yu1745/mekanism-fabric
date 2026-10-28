package mekanism.fabric.client;

import java.util.List;
import java.util.Locale;
import mekanism.api.annotations.NothingNullByDefault;
import net.minecraft.ChatFormatting;
import net.minecraft.client.searchtree.FullTextSearchTree;
import net.minecraft.client.searchtree.IdSearchTree;
import net.minecraft.client.searchtree.SearchTree;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** The same tooltip, resource-id and tag indexes vanilla builds, scoped to one tab's displayed stacks. */
@NothingNullByDefault
public final class CreativeTabSearchIndex {
    private final SearchTree<ItemStack> names;
    private final SearchTree<ItemStack> tags;

    public CreativeTabSearchIndex(List<ItemStack> displayed, HolderLookup.Provider registries) {
        List<ItemStack> entries = List.copyOf(displayed);
        Item.TooltipContext context = Item.TooltipContext.of(registries);
        TooltipFlag flag = TooltipFlag.Default.NORMAL.asCreative();
        names = new FullTextSearchTree<>(stack -> stack.getTooltipLines(context, null, flag).stream()
              .map(component -> ChatFormatting.stripFormatting(component.getString()).trim()).filter(line -> !line.isEmpty()),
              stack -> stack.getItemHolder().unwrapKey().map(ResourceKey::location).stream(), entries);
        tags = new IdSearchTree<>(stack -> stack.getTags().map(TagKey::location), entries);
    }

    public List<ItemStack> search(String query, boolean byTag) {
        return (byTag ? tags : names).search(query.toLowerCase(Locale.ROOT));
    }
}
