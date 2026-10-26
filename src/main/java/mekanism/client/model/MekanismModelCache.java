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

    //The OBJ half (Meka-Suit/Meka-Tool + module models): lazily parsed from the shipped obj resources and re-baked
    //per part-visibility set by the port's OBJModelData (the official BakingCompleted capture replaced by the lazy
    //resource-manager read).
    public final OBJModelData MEKASUIT = registerOBJ("models/entity/mekasuit.obj");
    public final OBJModelData MEKATOOL_LEFT_HAND = registerOBJ("models/entity/mekatool_left.obj");
    public final OBJModelData MEKATOOL_RIGHT_HAND = registerOBJ("models/entity/mekatool_right.obj");
    private final java.util.Set<OBJModelData> mekaSuitModules = new java.util.HashSet<>();
    public final java.util.Set<OBJModelData> MEKASUIT_MODULES = java.util.Collections.unmodifiableSet(mekaSuitModules);
    private final java.util.Set<Runnable> callbacks = new java.util.HashSet<>();

    private final java.util.Set<ResourceLocation> retainedModuleModels = new java.util.LinkedHashSet<>();

    /** The renderer-side install of the module render registry seam (retained registrations replay here). */
    public void installMekaSuitModuleModelHandler() {
        mekanism.common.content.gear.MekaSuitModuleRenderRegistry.install(new mekanism.common.content.gear.MekaSuitModuleRenderRegistry.Handler() {
            @Override
            public void addModuleModel(ResourceLocation location) {
                registerMekaSuitModuleModel(location);
            }

            @Override
            public void addModuleModelSpec(mekanism.common.content.gear.MekaSuitModuleRenderRegistry.ModelSpec spec) {
                mekanism.client.render.armor.MekaSuitArmor.registerModule(spec.name(), spec.moduleData(), spec.slotType(), spec.isActive());
            }
        });
        for (Runnable callback : callbacks) {
            callback.run();
        }
        org.slf4j.LoggerFactory.getLogger("Mekanism/FabricMekaSuit").info("MEKANISM_FABRIC_MEKASUIT_PROOF handler=installed");
    }

    public void reloadCallback(Runnable callback) {
        callbacks.add(callback);
    }

    /** Runs on every resource reload: the OBJ geometry re-parses lazily and the part bakes clear. */
    public void onReload() {
        MEKASUIT.clearCached();
        MEKATOOL_LEFT_HAND.clearCached();
        MEKATOOL_RIGHT_HAND.clearCached();
        for (OBJModelData moduleModel : mekaSuitModules) {
            moduleModel.clearCached();
        }
        callbacks.forEach(Runnable::run);
    }

    public OBJModelData registerMekaSuitModuleModel(ResourceLocation rl) {
        retainedModuleModels.add(rl);
        OBJModelData data = mekaSuitModules.stream().filter(m -> m.modelId().equals(rl)).findFirst().orElse(null);
        if (data == null) {
            data = new OBJModelData(rl);
            mekaSuitModules.add(data);
        }
        return data;
    }

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

    protected OBJModelData registerOBJ(String path) {
        ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(modid, path);
        OBJModelData data = new OBJModelData(rl);
        modelMap.put(rl, data);
        return data;
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
