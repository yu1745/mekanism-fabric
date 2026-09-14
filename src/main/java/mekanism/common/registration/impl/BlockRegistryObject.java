package mekanism.common.registration.impl;

import java.util.function.Consumer;
import mekanism.api.text.IHasTextComponent;
import mekanism.api.text.IHasTranslationKey;
import mekanism.common.registration.DoubleWrappedRegistryObject;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import mekanism.common.registration.MekanismDeferredHolder;
import org.jetbrains.annotations.NotNull;

public class BlockRegistryObject<BLOCK extends Block, ITEM extends Item> extends DoubleWrappedRegistryObject<Block, BLOCK, Item, ITEM> implements ItemLike,
      IHasTextComponent, IHasTranslationKey {

    public BlockRegistryObject(MekanismDeferredHolder<Block, BLOCK> blockRegistryObject, MekanismDeferredHolder<Item, ITEM> itemRegistryObject) {
        super(blockRegistryObject, itemRegistryObject);
    }

    @NotNull
    public BlockState defaultState() {
        return value().defaultBlockState();
    }

    @NotNull
    @Override
    public ITEM asItem() {
        return getSecondary();
    }

    public BlockRegistryObject<BLOCK, ITEM> forItemHolder(Consumer<ItemRegistryObject<ITEM>> consumer) {
        if (secondaryRO instanceof ItemRegistryObject<ITEM> itemHolder) {
            consumer.accept(itemHolder);
            return this;
        }
        throw new IllegalStateException("Called method requires an ItemRegistryObject");
    }

    @NotNull
    public MekanismDeferredHolder<Item, ITEM> getItemHolder() {
        return secondaryRO;
    }

    @NotNull
    @Override
    public String getTranslationKey() {
        return value().getDescriptionId();
    }

    @NotNull
    @Override
    public Component getTextComponent() {
        return value().getName();
    }
}