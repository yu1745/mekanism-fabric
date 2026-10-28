package mekanism.client.model;

import mekanism.api.MekanismAPI;
import mekanism.client.render.armor.MekaSuitArmor.ModuleOBJModelData;
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
    private final java.util.Set<ModuleOBJModelData> mekaSuitModules = new java.util.HashSet<>();
    public final java.util.Set<ModuleOBJModelData> MEKASUIT_MODULES = java.util.Collections.unmodifiableSet(mekaSuitModules);
    private final java.util.Set<Runnable> callbacks = new java.util.HashSet<>();

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
                mekaSuitModules.forEach(ModuleOBJModelData::clearClassification);
                callbacks.forEach(Runnable::run);
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

    public ModuleOBJModelData registerMekaSuitModuleModel(ResourceLocation rl) {
        var data = mekaSuitModules.stream().filter(m -> m.modelId().equals(rl)).findFirst().orElse(null);
        if (data == null) {
            data = new ModuleOBJModelData(rl);
            mekaSuitModules.add(data);
            callbacks.forEach(Runnable::run);
        }
        return data;
    }

    public final JSONModelData LIQUIFIER_BLADE = registerJSON("block/liquifier_blade");
    public final JSONModelData VIBRATOR_SHAFT = registerJSON("block/vibrator_shaft");
    public final JSONModelData PIGMENT_MIXER_SHAFT = registerJSON("block/pigment_mixer_shaft");
    public final JSONModelData[] QIO_DRIVES = new JSONModelData[EnumUtils.DRIVE_STATUSES.length];

    public static final ResourceLocation BASE_ROBIT_MODEL = ResourceLocation.fromNamespaceAndPath(MekanismAPI.MEKANISM_MODID, "item/robit");
    private final java.util.Map<ResourceLocation, BakedModel> robitModels = new java.util.HashMap<>();
    @org.jetbrains.annotations.Nullable
    private net.minecraft.client.resources.model.ModelBaker robitBaker;
    @org.jetbrains.annotations.Nullable
    private BakedModel baseRobit;

    /** Drop models and their atlas-bound baker before the next model reload. */
    public void clearRobitModels() {
        robitModels.clear();
        robitBaker = null;
        baseRobit = null;
    }

    /** Capture the actual resource-model bake and its baker for later datapack-selected models. */
    public void setRobitModelBaker(BakedModel base, net.minecraft.client.resources.model.ModelBaker baker) {
        baseRobit = base;
        robitBaker = baker;
    }

    /** Matches the official cache: the default model unless the skin specifies a custom model. */
    @org.jetbrains.annotations.Nullable
    public BakedModel getRobitSkin(MekanismRobitSkins.SkinLookup skinLookup) {
        ResourceLocation customModel = skinLookup.skin().customModel();
        if (customModel == null || customModel.equals(BASE_ROBIT_MODEL)) {
            return baseRobit;
        }
        if (robitBaker == null) {
            return null;
        }
        return robitModels.computeIfAbsent(customModel, id -> {
            var unbaked = robitBaker.getModel(id);
            unbaked.resolveParents(robitBaker::getModel);
            return robitBaker.bake(id, net.minecraft.client.resources.model.BlockModelRotation.X0_Y0);
        });
    }

    @org.jetbrains.annotations.Nullable
    public BakedModel getBaseRobit() {
        return baseRobit;
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
