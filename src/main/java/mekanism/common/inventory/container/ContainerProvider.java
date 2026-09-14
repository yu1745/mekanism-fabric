package mekanism.common.inventory.container;

import mekanism.api.text.ILangEntry;
import java.util.OptionalInt;
import java.util.function.Consumer;
import mekanism.common.inventory.container.type.MenuOpeningData;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.fabricmc.fabric.api.screenhandler.v1.FabricScreenHandlerFactory;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ContainerProvider implements MenuProvider, FabricScreenHandlerFactory {

    private final Component displayName;
    private final MenuConstructor provider;
    private final boolean resetMousePosition;

    public ContainerProvider(ILangEntry translationHelper, MenuConstructor provider) {
        this(translationHelper.translate(), provider, true);
    }

    public ContainerProvider(Component displayName, MenuConstructor provider, boolean resetMousePosition) {
        this.displayName = displayName;
        this.provider = provider;
        this.resetMousePosition = resetMousePosition;
    }

    @Override
    public boolean shouldCloseCurrentScreen() {
        return resetMousePosition;
    }

    /** Retained public Mekanism query; the actual Fabric open lifecycle calls shouldCloseCurrentScreen. */
    public boolean shouldTriggerClientSideContainerClosingOnOpen() {
        return shouldCloseCurrentScreen();
    }

    /** Open the real menu using Fabric's typed extended factory, preserving the existing writer's wire order and registry context. */
    public static OptionalInt open(ServerPlayer player, @Nullable MenuProvider provider, Consumer<RegistryFriendlyByteBuf> writer) {
        if (provider == null) return OptionalInt.empty();
        return player.openMenu(new ExtendedScreenHandlerFactory<MenuOpeningData>() {
            @Override
            public MenuOpeningData getScreenOpeningData(ServerPlayer openingPlayer) {
                return MenuOpeningData.encode(openingPlayer, writer);
            }

            @Override
            public boolean shouldCloseCurrentScreen() {
                //The native Fabric server redirect uses doCloseContainer when false, without sending a client close packet.
                return provider.shouldCloseCurrentScreen();
            }

            @Override
            public Component getDisplayName() {
                return provider.getDisplayName();
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inventory, Player openingPlayer) {
                return provider.createMenu(id, inventory, openingPlayer);
            }
        });
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int i, @NotNull Inventory inv, @NotNull Player player) {
        return provider.createMenu(i, inv, player);
    }

    @NotNull
    @Override
    public Component getDisplayName() {
        return displayName;
    }
}