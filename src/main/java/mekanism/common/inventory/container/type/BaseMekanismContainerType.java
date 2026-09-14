package mekanism.common.inventory.container.type;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

public abstract class BaseMekanismContainerType<T, CONTAINER extends AbstractContainerMenu, FACTORY> extends ExtendedScreenHandlerType<CONTAINER, Object> {

    protected final FACTORY mekanismConstructor;
    protected final Class<T> type;

    protected BaseMekanismContainerType(Class<T> type, FACTORY mekanismConstructor, OpeningFactory<CONTAINER> constructor) {
        //Object preserves the original create(id, inventory, Object domainOwner) overload in MekanismContainerType.
        //Only MenuOpeningData is ever encoded by Fabric; that subclass dispatches native opening data to this implementation.
        super((id, inventory, data) -> ((MenuOpeningData) data).create(constructor, id, inventory),
              MenuOpeningData.STREAM_CODEC.map(data -> (Object) data, data -> (MenuOpeningData) data));
        this.type = type;
        this.mekanismConstructor = mekanismConstructor;
    }

    @FunctionalInterface
    public interface OpeningFactory<MENU extends AbstractContainerMenu> {
        MENU create(int id, Inventory inventory, RegistryFriendlyByteBuf data);
    }
}