package mekanism.client.model;

import mekanism.common.tile.qio.TileEntityQIODriveArray.DriveStatus;
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

    private MekanismModelCache() {
        super("mekanism");
        for (DriveStatus status : EnumUtils.DRIVE_STATUSES) {
            if (status != DriveStatus.NONE) {
                QIO_DRIVES[status.ordinal()] = registerJSON(status.getModel());
            }
        }
    }
}
