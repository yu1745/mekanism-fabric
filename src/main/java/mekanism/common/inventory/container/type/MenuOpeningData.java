package mekanism.common.inventory.container.type;

import io.netty.buffer.Unpooled;
import java.util.function.Consumer;
import mekanism.common.inventory.container.type.BaseMekanismContainerType.OpeningFactory;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

/** Immutable opening payload for Mekanism's existing positional/entity/item/bulk-QIO wire data, carried by Fabric's native menu protocol. */
public final class MenuOpeningData {
    public static final StreamCodec<RegistryFriendlyByteBuf, MenuOpeningData> STREAM_CODEC = ByteBufCodecs.BYTE_ARRAY
          .map(MenuOpeningData::new, data -> data.bytes).cast();
    private final byte[] bytes;

    private MenuOpeningData(byte[] bytes) {
        this.bytes = bytes.clone();
    }

    public static MenuOpeningData encode(ServerPlayer player, Consumer<RegistryFriendlyByteBuf> writer) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), player.registryAccess());
        try {
            writer.accept(buffer);
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.readBytes(bytes);
            return new MenuOpeningData(bytes);
        } finally {
            buffer.release();
        }
    }

    public <MENU extends AbstractContainerMenu> MENU create(OpeningFactory<MENU> factory, int id, Inventory inventory) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(bytes.clone()), inventory.player.registryAccess());
        try {
            MENU menu = factory.create(id, inventory, buffer);
            if (buffer.isReadable()) {
                throw new IllegalArgumentException("Unread Mekanism menu opening data: " + buffer.readableBytes());
            }
            return menu;
        } finally {
            buffer.release();
        }
    }
}
