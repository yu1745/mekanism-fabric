package mekanism.common.tile.qio;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.transfer.v1.item.InventoryStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class QIOImporterSlotOrderTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void importsIndexedVanillaSlotsFromLastToFirst() {
        SimpleContainer inventory = new SimpleContainer(3);
        inventory.setItem(0, new ItemStack(Items.STONE));
        inventory.setItem(1, new ItemStack(Items.DIRT));
        inventory.setItem(2, new ItemStack(Items.IRON_INGOT));
        InventoryStorage storage = InventoryStorage.of(inventory, null);
        List<StorageView<ItemVariant>> seen = new ArrayList<>();
        for (StorageView<ItemVariant> view : TileEntityQIOImporter.importViews(storage)) {
            seen.add(view);
        }
        assertSame(storage.getSlot(2), seen.get(0));
        assertSame(storage.getSlot(1), seen.get(1));
        assertSame(storage.getSlot(0), seen.get(2));
    }

    @Test
    void emptySlotRemainsInItsOriginalRelativePosition() {
        SimpleContainer inventory = new SimpleContainer(2);
        inventory.setItem(0, new ItemStack(Items.STONE));
        InventoryStorage storage = InventoryStorage.of(inventory, null);
        var iterator = TileEntityQIOImporter.importViews(storage).iterator();
        assertSame(storage.getSlot(1), iterator.next());
        assertSame(storage.getSlot(0), iterator.next());
        assertFalse(iterator.hasNext());
    }
}
