package mekanism.common.content.qio;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import mekanism.common.attachments.qio.DriveContents;
import mekanism.common.inventory.slot.QIODriveSlot;
import mekanism.common.registries.MekanismDataComponents;
import net.minecraft.world.item.ItemStack;

public interface IQIODriveHolder extends IQIOFrequencyHolder {

    Logger LOGGER = LoggerFactory.getLogger("Mekanism");

    List<QIODriveSlot> getDriveSlots();

    void onDataUpdate();

    default void save(int slot, QIODriveData data) {
        ItemStack stack = getDriveSlots().get(slot).getStack();
        if (stack.getItem() instanceof IQIODriveItem) {
            stack.set(MekanismDataComponents.DRIVE_CONTENTS.get(), DriveContents.create(data));
        } else {
            LOGGER.error("Tried to save data map to an invalid item ({}). Something has gone very wrong!", stack.getItem());
        }
    }
}
