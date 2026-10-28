package mekanism.fabric.client.model;

import java.util.List;
import java.util.function.BiConsumer;
import mekanism.api.inventory.IInventorySlot;
import mekanism.client.model.MekanismModelCache;
import mekanism.client.render.lib.Quad;
import mekanism.client.render.lib.QuadTransformation;
import mekanism.common.attachments.FrequencyAware;
import mekanism.common.attachments.containers.ContainerType;
import mekanism.common.attachments.qio.DriveMetadata;
import mekanism.common.block.attribute.Attribute;
import mekanism.common.content.qio.IQIODriveItem;
import mekanism.common.content.qio.QIOFrequency;
import mekanism.common.lib.frequency.FrequencyType;
import mekanism.common.lib.frequency.IFrequencyItem;
import mekanism.common.registries.MekanismDataComponents;
import mekanism.common.tile.qio.TileEntityQIODriveArray;
import mekanism.common.tile.qio.TileEntityQIODriveArray.DriveStatus;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/** Shared geometry and item-state selection from the official DriveArrayBakedModel. */
public final class FabricQIODriveGeometry {
    private static final float[][] PLACEMENTS = {
          {0, 6F / 16}, {-2F / 16, 6F / 16}, {-4F / 16, 6F / 16}, {-7F / 16, 6F / 16}, {-9F / 16, 6F / 16}, {-11F / 16, 6F / 16},
          {0, 0}, {-2F / 16, 0}, {-4F / 16, 0}, {-7F / 16, 0}, {-9F / 16, 0}, {-11F / 16, 0}
    };

    private FabricQIODriveGeometry() {
    }

    public static byte[] itemStatus(ItemStack stack) {
        List<IInventorySlot> slots = ContainerType.ITEM.getAttachmentContainersIfPresent(stack);
        byte[] statuses = new byte[TileEntityQIODriveArray.DRIVE_SLOTS];
        boolean hasFrequency = false;
        if (stack.getItem() instanceof IFrequencyItem frequencyItem && frequencyItem.getFrequencyType() == FrequencyType.QIO) {
            FrequencyAware<QIOFrequency> frequency = stack.getOrDefault(MekanismDataComponents.QIO_FREQUENCY.get(), FrequencyAware.none());
            hasFrequency = frequency.identity().isPresent() && frequency.getOwner() != null;
        }
        for (int i = 0; i < statuses.length; i++) {
            ItemStack drive = i < slots.size() ? slots.get(i).getStack() : ItemStack.EMPTY;
            DriveStatus status = DriveStatus.NONE;
            if (!drive.isEmpty() && drive.getItem() instanceof IQIODriveItem driveItem) {
                DriveMetadata metadata = drive.getOrDefault(MekanismDataComponents.DRIVE_METADATA.get(), DriveMetadata.EMPTY);
                if (!metadata.isEmpty()) {
                    if (!hasFrequency) {
                        status = DriveStatus.OFFLINE;
                    } else {
                        long capacity = driveItem.getCountCapacity(drive);
                        if (metadata.count() == capacity) {
                            status = DriveStatus.FULL;
                        } else if (metadata.types() == driveItem.getTypeCapacity(drive) || metadata.count() >= capacity * 0.75) {
                            status = DriveStatus.NEAR_FULL;
                        } else {
                            status = DriveStatus.READY;
                        }
                    }
                }
            }
            statuses[i] = status.status();
        }
        return statuses;
    }

    /** Supplies transformed quads and their source model's minimum baked light. */
    public static void forEach(byte[] statuses, BlockState state, RandomSource random, BiConsumer<BakedQuad, Integer> consumer) {
        QuadTransformation rotation = QuadTransformation.rotate(Attribute.getFacing(state));
        for (int i = 0; i < Math.min(statuses.length, PLACEMENTS.length); i++) {
            DriveStatus status = DriveStatus.BY_ID.apply(statuses[i]);
            if (status == DriveStatus.NONE) {
                continue;
            }
            BakedModel model = MekanismModelCache.INSTANCE.QIO_DRIVES[status.ordinal()].getBakedModel();
            float[] placement = PLACEMENTS[i];
            QuadTransformation translation = QuadTransformation.translate(placement[0], placement[1], 0);
            for (BakedQuad source : model.getQuads(state, null, random)) {
                int light = FabricQIOModelPlugin.lightOf(model, source);
                Quad quad = new Quad(source);
                consumer.accept(quad.transform(translation, rotation) ? quad.bake() : source, light);
            }
        }
    }
}
