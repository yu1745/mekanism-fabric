package mekanism.fabric.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import mekanism.fabric.client.CreativeTabSearchIndex;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The official Mekanism tab has its own search box and searches its display items, not the global search tab. */
@Mixin(CreativeModeInventoryScreen.class)
abstract class MekanismCreativeSearchMixin extends AbstractContainerScreen<CreativeModeInventoryScreen.ItemPickerMenu> {
    @Shadow private static CreativeModeTab selectedTab;
    @Shadow private EditBox searchBox;
    @Shadow private float scrollOffs;
    @Shadow @Final private Set<TagKey<Item>> visibleTags;
    @Shadow protected abstract void updateVisibleTags(String query);
    @Unique @Nullable private CreativeTabSearchIndex mekanism$searchIndex;

    protected MekanismCreativeSearchMixin(CreativeModeInventoryScreen.ItemPickerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Unique
    private static boolean mekanism$isOwnTab() {
        ResourceLocation id = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(selectedTab);
        return id != null && (id.toString().equals("mekanism:mekanism") || id.toString().equals("mekanismtools:mekanismtools"));
    }

    // Replace only the four SEARCH comparisons controlling the search box/input; leave CATEGORY and inventory logic intact.
    @ModifyExpressionValue(method = {"refreshCurrentTabContents", "charTyped", "keyPressed", "selectTab"},
          at = @At(value = "FIELD", target = "Lnet/minecraft/world/item/CreativeModeTab$Type;SEARCH:Lnet/minecraft/world/item/CreativeModeTab$Type;", opcode = Opcodes.GETSTATIC))
    private CreativeModeTab.Type mekanism$ownTabHasSearch(CreativeModeTab.Type original) {
        return mekanism$isOwnTab() ? CreativeModeTab.Type.CATEGORY : original;
    }

    @Inject(method = {"init", "refreshCurrentTabContents"}, at = @At("HEAD"))
    private void mekanism$invalidateSearch(CallbackInfo ci) {
        mekanism$searchIndex = null;
    }

    @Inject(method = "refreshSearchResults", at = @At("HEAD"), cancellable = true)
    private void mekanism$searchOwnContents(CallbackInfo ci) {
        if (!mekanism$isOwnTab()) {
            return;
        }
        NonNullList<ItemStack> items = menu.items;
        items.clear();
        visibleTags.clear();
        String query = searchBox.getValue();
        if (query.isEmpty()) {
            items.addAll(selectedTab.getDisplayItems());
        } else if (minecraft != null && minecraft.getConnection() != null && minecraft.level != null) {
            if (mekanism$searchIndex == null) {
                mekanism$searchIndex = new CreativeTabSearchIndex(List.copyOf(selectedTab.getDisplayItems()), minecraft.level.registryAccess());
            }
            boolean byTag = query.startsWith("#");
            if (byTag) {
                query = query.substring(1);
                updateVisibleTags(query);
            }
            items.addAll(mekanism$searchIndex.search(query, byTag));
        }
        scrollOffs = 0;
        menu.scrollTo(0);
        ci.cancel();
    }
}
