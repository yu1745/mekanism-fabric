package mekanism.client.model;

import mekanism.api.MekanismAPI;
import mekanism.common.registries.MekanismRobitSkins;
import net.minecraft.client.resources.model.BakedModel;
import mekanism.common.tile.qio.TileEntityQIODriveArray.DriveStatus;
import net.minecraft.resources.ResourceLocation;
import mekanism.common.util.EnumUtils;

/**
 * Tile-lane slice of the official model cache: the JSON entries the tile entity renderers consume. The Meka-Suit and
 * Meka-Tool OBJ entries stay deferred with the armor lane, the transmitter contents entry with the transmitter BER
 * lane, and the robit skin cache with the robit lane; the mod id is the literal admitted by the registry precedent.
 */
public class MekanismModelCache extends BaseModelCache {

    public static final MekanismModelCache INSTANCE = new MekanismModelCache();

    public final JSONModelData LIQUIFIER_BLADE = registerJSON("block/liquifier_blade");
    public final JSONModelData VIBRATOR_SHAFT = registerJSON("block/vibrator_shaft");
    public final JSONModelData PIGMENT_MIXER_SHAFT = registerJSON("block/pigment_mixer_shaft");
    public final JSONModelData[] QIO_DRIVES = new JSONModelData[EnumUtils.DRIVE_STATUSES.length];

    /**
     * Fabric port of the robit skin cache: resolves the skin's model (custom model, else the default per skin family)
     * through the model manager - the models are baked by the fabric model plugin's robit case.
     */
    public BakedModel getRobitSkin(MekanismRobitSkins.SkinLookup skinLookup) {
        ResourceLocation customModel = skinLookup.skin().customModel();
        if (customModel != null) {
            return BaseModelCache.getBakedModel(customModel);
        }
        String fallback = skinLookup.name().location().getPath().equals("allay") ? "item/robit_allay" : "item/robit";
        return BaseModelCache.getBakedModel(ResourceLocation.fromNamespaceAndPath(MekanismAPI.MEKANISM_MODID, fallback));
    }

    private MekanismModelCache() {
        super("mekanism");
        for (DriveStatus status : EnumUtils.DRIVE_STATUSES) {
            if (status != DriveStatus.NONE) {
                QIO_DRIVES[status.ordinal()] = registerJSON(status.getModel());
            }
        }
    }
}
