package mekanism.fabric.radiation;

import com.mojang.serialization.Codec;
import mekanism.api.radiation.IRadiationManager;
import mekanism.api.radiation.capability.IRadiationEntity;
import mekanism.common.lib.radiation.MeltdownLevelData;
import mekanism.common.lib.radiation.RadiationLevelData;
import mekanism.common.lib.radiation.capability.RadiationEntity;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.lookup.v1.entity.EntityApiLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** Canonical native radiation attachments and the living-entity lookup; no entity/world is retained globally. */
public final class FabricRadiationAttachments {

    //No copyOnDeath: native entity transfer preserves non-death dimension changes, but resets death/conversion.
    //No automatic sync: the original two radiation payloads remain the only client synchronization owner.
    public static final AttachmentType<Double> RADIATION = AttachmentRegistry.create(id("radiation"), builder -> builder
          .initializer(IRadiationManager.INSTANCE::baselineRadiation)
          .persistent(Codec.doubleRange(IRadiationManager.INSTANCE.baselineRadiation(), Double.MAX_VALUE)));
    public static final AttachmentType<RadiationLevelData> RADIATION_LEVEL_DATA = AttachmentRegistry.create(id("radiation_data"), builder -> builder
          .initializer(RadiationLevelData::new).persistent(RadiationLevelData.CODEC));
    public static final AttachmentType<MeltdownLevelData> MELTDOWN_DATA = AttachmentRegistry.create(id("meltdown_data"), builder -> builder
          .initializer(MeltdownLevelData::new).persistent(MeltdownLevelData.CODEC));
    public static final EntityApiLookup<IRadiationEntity, Void> RADIATION_ENTITY = EntityApiLookup.get(id("radiation"), IRadiationEntity.class, Void.class);
    private static boolean initialized;

    private FabricRadiationAttachments() {
    }

    public static synchronized void initialize() {
        if (!initialized) {
            //Native fallback covers all real LivingEntity subclasses, including types registered later by other mods.
            //Explicit native providers may override this default; each result still owns the actual queried entity.
            RADIATION_ENTITY.registerFallback((entity, context) -> entity instanceof LivingEntity living ? new RadiationEntity(living) : null);
            initialized = true;
        }
    }

    public static double getRadiation(LivingEntity entity) {
        return ((AttachmentTarget) entity).getAttachedOrElse(RADIATION, IRadiationManager.INSTANCE.baselineRadiation());
    }

    public static void setRadiation(LivingEntity entity, double radiation) {
        AttachmentTarget target = (AttachmentTarget) entity;
        if (radiation != IRadiationManager.INSTANCE.baselineRadiation()) {
            target.setAttached(RADIATION, radiation);
        } else {
            //Original serializer omitted baseline; do not persist or copy a needless default attachment.
            target.removeAttached(RADIATION);
        }
    }

    @Nullable
    public static RadiationLevelData getData(Level level) {
        return ((AttachmentTarget) level).getAttached(RADIATION_LEVEL_DATA);
    }

    public static RadiationLevelData getOrCreateData(Level level) {
        //Retain the empty object after clearing. In the pinned native implementation wasSerialized is fixed at
        //construction: removing the last attachment after a first save could stop dirty writes and revive old data.
        return ((AttachmentTarget) level).getAttachedOrCreate(RADIATION_LEVEL_DATA);
    }

    @Nullable
    public static MeltdownLevelData getMeltdownData(Level level) {
        return ((AttachmentTarget) level).getAttached(MELTDOWN_DATA);
    }

    public static MeltdownLevelData getOrCreateMeltdownData(Level level) {
        //Same mutable-world persistence rule as radiation: retain an empty attachment after expiry.
        return ((AttachmentTarget) level).getAttachedOrCreate(MELTDOWN_DATA);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("mekanism", path);
    }
}
