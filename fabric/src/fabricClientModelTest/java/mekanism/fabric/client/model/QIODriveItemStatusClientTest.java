package mekanism.fabric.client.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import mekanism.api.security.SecurityMode;
import mekanism.common.attachments.FrequencyAware;
import mekanism.common.attachments.containers.item.AttachedItems;
import mekanism.common.attachments.qio.DriveMetadata;
import mekanism.common.item.ItemQIODrive;
import mekanism.common.lib.frequency.Frequency.FrequencyIdentity;
import mekanism.common.lib.frequency.FrequencyType;
import mekanism.common.lib.frequency.IFrequencyItem;
import mekanism.common.registries.MekanismDataComponents;
import mekanism.common.tier.QIODriveTier;
import mekanism.common.tile.qio.TileEntityQIODriveArray.DriveStatus;
import mekanism.fabric.chemical.FabricChemicalRegistryLifecycle;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

class QIODriveItemStatusClientTest {
    @Test
    void realItemComponentsSelectOfficialOfflineAndCapacityStates() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        FabricChemicalRegistryLifecycle.initialize();
        MekanismDataComponents.DATA_COMPONENTS.registerAll(BuiltInRegistries.DATA_COMPONENT_TYPE);
        ItemQIODrive item = new ItemQIODrive(QIODriveTier.BASE, new Item.Properties());
        ItemStack array = new ItemStack(new FrequencyItem());
        mekanism.common.attachments.containers.ContainerType.ITEM.addDefaultCreators(false, array.getItem(),
              () -> mekanism.common.attachments.containers.item.ItemSlotsBuilder.builder().addQIODriveSlots(12).build());
        array.set(MekanismDataComponents.ATTACHED_ITEMS.get(), new AttachedItems(List.of(
              ItemStack.EMPTY, drive(item, 0, 0), drive(item, 1, 1), drive(item, 12_000, 1),
              drive(item, 16_000, 1), drive(item, 100, 128))));
        byte none = DriveStatus.NONE.status(), offline = DriveStatus.OFFLINE.status();
        assertArrayEquals(new byte[]{none, none, offline, offline, offline, offline, none, none, none, none, none, none},
              FabricQIODriveGeometry.itemStatus(array));
        var identity = new FrequencyIdentity("test", SecurityMode.PUBLIC, UUID.randomUUID());
        array.set(MekanismDataComponents.QIO_FREQUENCY.get(), new FrequencyAware<>(Optional.of(identity), Optional.empty()));
        assertArrayEquals(new byte[]{none, none, DriveStatus.READY.status(), DriveStatus.NEAR_FULL.status(),
              DriveStatus.FULL.status(), DriveStatus.NEAR_FULL.status(), none, none, none, none, none, none},
              FabricQIODriveGeometry.itemStatus(array));
    }

    private static ItemStack drive(ItemQIODrive item, long count, int types) {
        ItemStack stack = new ItemStack(item);
        stack.set(MekanismDataComponents.DRIVE_METADATA.get(), new DriveMetadata(count, types));
        return stack;
    }

    private static final class FrequencyItem extends Item implements IFrequencyItem {
        FrequencyItem() { super(new Properties()); }
        @Override public FrequencyType<?> getFrequencyType() { return FrequencyType.QIO; }
    }
}
