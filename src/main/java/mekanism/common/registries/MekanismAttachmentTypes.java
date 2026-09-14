package mekanism.common.registries;

import com.mojang.serialization.Codec;
import mekanism.api.annotations.NothingNullByDefault;
import mekanism.api.radiation.IRadiationManager;
import mekanism.common.Mekanism;
import mekanism.common.item.gear.ItemFlamethrower.FlamethrowerMode;
import mekanism.common.lib.radiation.MeltdownLevelData;
import mekanism.common.lib.radiation.RadiationLevelData;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

@NothingNullByDefault
public class MekanismAttachmentTypes {

    private MekanismAttachmentTypes() {
    }

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Mekanism.MODID);

    //Note: We do not specify copy on death as we want radiation to reset to baseline on death
    public static final net.fabricmc.fabric.api.attachment.v1.AttachmentType<Double> RADIATION = mekanism.fabric.radiation.FabricRadiationAttachments.RADIATION;

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<FlamethrowerMode>> FLAMETHROWER_MODE = ATTACHMENT_TYPES.register("flamethrower_mode", () ->
          AttachmentType.builder(() -> FlamethrowerMode.COMBAT)
                .serialize(FlamethrowerMode.CODEC, mode -> mode != FlamethrowerMode.COMBAT)
                .build());

    public static final net.fabricmc.fabric.api.attachment.v1.AttachmentType<MeltdownLevelData> MELTDOWN_DATA = mekanism.fabric.radiation.FabricRadiationAttachments.MELTDOWN_DATA;

    public static final net.fabricmc.fabric.api.attachment.v1.AttachmentType<RadiationLevelData> RADIATION_LEVEL_DATA = mekanism.fabric.radiation.FabricRadiationAttachments.RADIATION_LEVEL_DATA;
}