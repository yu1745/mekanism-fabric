package mekanism.common.registration.impl;

import java.util.Arrays;
import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import mekanism.api.functions.ConstantPredicates;
import mekanism.api.text.ILangEntry;
import mekanism.client.SpecialColors;
import mekanism.common.registration.MekanismDeferredHolder;
import mekanism.common.registration.MekanismDeferredRegister;
import mekanism.common.registries.MekanismBlocks;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroupEntries;
import org.jetbrains.annotations.NotNull;

public class CreativeTabDeferredRegister extends MekanismDeferredRegister<CreativeModeTab> {

    private final ExistingTabContents addToExistingTabs;

    public CreativeTabDeferredRegister(String modid) {
        this(modid, (tabKey, output, parameters) -> {
        });
    }

    public CreativeTabDeferredRegister(String modid, ExistingTabContents addToExistingTabs) {
        super(Registries.CREATIVE_MODE_TAB, modid);
        this.addToExistingTabs = addToExistingTabs;
    }

    /**
     * Fabric port delta: the official class wired {@code addToExistingTabs} through a NeoForge bus listener. The base register is bus-free in this port, so
     * the platform bootstrap registers this callback with Fabric's {@code ItemGroupEvents.modifyEntriesEvent} for the vanilla tabs it targets. The callback
     * receives the tab key and the real display parameters that NeoForge exposed through {@code BuildCreativeModeTabContentsEvent}.
     */
    public ExistingTabContents getAddToExistingTabs() {
        return addToExistingTabs;
    }

    @FunctionalInterface
    public interface ExistingTabContents {

        void addToDisplay(ResourceKey<CreativeModeTab> tabKey, CreativeModeTab.Output output, CreativeModeTab.ItemDisplayParameters parameters);
    }

    /**
     * @apiNote We manually require the title and icon to be passed so that we ensure all tabs have one.
     */
    public MekanismDeferredHolder<CreativeModeTab, CreativeModeTab> registerMain(ILangEntry title, Holder<Item> icon, UnaryOperator<CreativeModeTab.Builder> operator) {
        return register(getNamespace(), title, icon, operator);
    }

    /**
     * @apiNote We manually require the title and icon to be passed so that we ensure all tabs have one.
     */
    public MekanismDeferredHolder<CreativeModeTab, CreativeModeTab> register(String name, ILangEntry title, Holder<Item> icon, UnaryOperator<CreativeModeTab.Builder> operator) {
        return register(name, () -> {
            //Fabric port delta: NeoForge's withTabFactory/custom tab class and the getLabelColor hook have no vanilla
            // counterpart; FabricItemGroup.builder() owns the vanilla row/column assignment.
            CreativeModeTab.Builder builder = FabricItemGroup.builder()
                  .title(title.translate())
                  .icon(() -> new ItemStack(icon));
            return operator.apply(builder).build();
        });
    }

    public static void addToDisplay(CreativeModeTab.Output output, Collection<? extends Holder<Item>> items, Predicate<Holder<Item>> shouldSkip) {
        for (Holder<Item> itemProvider : items) {
            if (!shouldSkip.test(itemProvider)) {
                addToDisplay(output, itemProvider);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static void addToDisplay(CreativeModeTab.Output output, BlockRegistryObject<?, ?>... blocks) {
        addToDisplay(output, Arrays.stream(blocks).map(BlockRegistryObject::getItemHolder).toArray(Holder[]::new));
    }

    @SafeVarargs
    public static void addToDisplay(CreativeModeTab.Output output, Holder<Item>... items) {
        CreativeModeTab.TabVisibility visibility;
        if (output instanceof FabricItemGroupEntries) {
            //If we are added from the event, only add the item to the parent tab, as we will already be contained in the search tab
            // from when we are adding to our tabs
            visibility = CreativeModeTab.TabVisibility.PARENT_TAB_ONLY;
        } else {
            visibility = CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS;
        }
        for (Holder<Item> item : items) {
            Item itemLike = item.value();
            if (itemLike instanceof ICustomCreativeTabContents contents) {
                if (contents.addDefault()) {
                    output.accept(itemLike, visibility);
                }
                contents.addItems(item, stack -> output.accept(stack, visibility));
            } else {
                output.accept(itemLike, visibility);
            }
        }
    }

    public static void addToDisplay(ItemDeferredRegister register, CreativeModeTab.Output output) {
        addToDisplay(output, register.getEntries(), ConstantPredicates.alwaysFalse());
    }

    public static void addToDisplay(BlockDeferredRegister register, CreativeModeTab.Output output) {
        //Don't add bounding blocks to the creative tab
        addToDisplay(output, register.getSecondaryEntries(), MekanismBlocks.BOUNDING_BLOCK::secondaryKeyMatches);
    }

    public static void addToDisplay(FluidDeferredRegister register, CreativeModeTab.Output output) {
        addToDisplay(output, register.getBucketEntries(), ConstantPredicates.alwaysFalse());
    }

    public interface ICustomCreativeTabContents {

        void addItems(Holder<Item> item, Consumer<ItemStack> addToTab);

        default boolean addDefault() {
            return true;
        }
    }
}