package mekanism.common.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import mekanism.common.registries.MekanismDataSerializers;
import mekanism.fabric.chemical.FabricChemicalRegistryLifecycle;
import mekanism.test.TileLevelStub;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EntityType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EntityFlamePersistenceTest {
    private static final String ATTACHMENTS = "neoforge:attachments";
    private static final String MODE = "mekanism:flamethrower_mode";

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        FabricChemicalRegistryLifecycle.initialize();
        MekanismDataSerializers.DATA_SERIALIZERS.registerAll();
    }

    @SuppressWarnings("unchecked")
    private static EntityFlame flame() {
        //The vanilla entity type is only a construction token; save/read and synced data belong to the real EntityFlame.
        return new EntityFlame((EntityType<EntityFlame>) (EntityType<?>) EntityType.ARROW, TileLevelStub.create());
    }

    @Test
    void officialAttachmentModeRoundTripsAndPreservesOtherAttachments() {
        CompoundTag original = new CompoundTag();
        CompoundTag attachments = new CompoundTag();
        attachments.putString(MODE, "heat");
        attachments.putString("example:other", "keep");
        original.put(ATTACHMENTS, attachments);
        EntityFlame flame = flame();
        flame.readAdditionalSaveData(original);
        CompoundTag saved = original.copy();
        flame.addAdditionalSaveData(saved);
        assertEquals("heat", saved.getCompound(ATTACHMENTS).getString(MODE));
        assertEquals("keep", saved.getCompound(ATTACHMENTS).getString("example:other"));

        EntityFlame restored = flame();
        restored.readAdditionalSaveData(saved);
        CompoundTag again = new CompoundTag();
        restored.addAdditionalSaveData(again);
        assertEquals("heat", again.getCompound(ATTACHMENTS).getString(MODE));
    }

    @Test
    void defaultAndInvalidModeDoNotLeaveAStaleAttachment() {
        EntityFlame flame = flame();
        CompoundTag tag = new CompoundTag();
        CompoundTag attachments = new CompoundTag();
        attachments.putString(MODE, "unknown");
        attachments.putString("example:other", "keep");
        tag.put(ATTACHMENTS, attachments);
        flame.readAdditionalSaveData(tag);
        flame.addAdditionalSaveData(tag);
        assertFalse(tag.getCompound(ATTACHMENTS).contains(MODE));
        assertEquals("keep", tag.getCompound(ATTACHMENTS).getString("example:other"));
        CompoundTag fresh = new CompoundTag();
        flame.addAdditionalSaveData(fresh);
        assertFalse(fresh.contains(ATTACHMENTS));

        CompoundTag inferno = new CompoundTag();
        CompoundTag infernoAttachments = new CompoundTag();
        infernoAttachments.putString(MODE, "inferno");
        inferno.put(ATTACHMENTS, infernoAttachments);
        flame.readAdditionalSaveData(inferno);
        flame.readAdditionalSaveData(new CompoundTag());
        CompoundTag reset = new CompoundTag();
        flame.addAdditionalSaveData(reset);
        assertFalse(reset.contains(ATTACHMENTS));
        assertTrue(tag.contains(ATTACHMENTS));
    }
}
