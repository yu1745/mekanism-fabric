package mekanism.common.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import mekanism.api.MekanismAPI;
import mekanism.api.robit.RobitSkin;
import mekanism.api.security.SecurityMode;
import mekanism.fabric.chemical.FabricChemicalRegistryLifecycle;
import mekanism.test.TileLevelStub;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EntityType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EntitySynchedDataWireTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        FabricChemicalRegistryLifecycle.initialize();
        // Another mod can consume an arbitrary local serializer id before our entities are initialized.
        EntityDataSerializers.registerSerializer(EntityDataSerializer.forValueType(ByteBufCodecs.VAR_INT));
    }

    @SuppressWarnings("unchecked")
    private static EntityRobit robit() {
        return new EntityRobit((EntityType<EntityRobit>) (EntityType<?>) EntityType.ZOMBIE, TileLevelStub.create());
    }

    @SuppressWarnings("unchecked")
    private static EntityFlame flame() {
        return new EntityFlame((EntityType<EntityFlame>) (EntityType<?>) EntityType.ARROW, TileLevelStub.create());
    }

    private static void transfer(SynchedEntityData source, SynchedEntityData target) {
        List<SynchedEntityData.DataValue<?>> values = source.packDirty();
        assertTrue(values != null && !values.isEmpty());
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            for (var value : values) {
                int serializerId = EntityDataSerializers.getSerializedId(value.serializer());
                assertTrue(serializerId >= 0 && serializerId <= EntityDataSerializers.getSerializedId(EntityDataSerializers.QUATERNION));
                value.write(buffer);
            }
            List<SynchedEntityData.DataValue<?>> decoded = new ArrayList<>();
            while (buffer.isReadable()) decoded.add(SynchedEntityData.DataValue.read(buffer, buffer.readUnsignedByte()));
            target.assignValues(decoded);
        } finally {
            buffer.release();
        }
    }

    @Test
    void robitAndFlameUseStableBuiltinsAndPreserveTheirValues() {
        EntityRobit sender = robit();
        EntityRobit receiver = robit();
        UUID owner = UUID.fromString("c8ba33e0-94a4-4b8c-85f1-649cfaa019eb");
        ResourceKey<RobitSkin> skin = ResourceKey.create(MekanismAPI.ROBIT_SKIN_REGISTRY_NAME, ResourceLocation.fromNamespaceAndPath("example", "test"));
        sender.setOwnerUUID(owner);
        sender.setSecurityMode(SecurityMode.TRUSTED);
        sender.setSkin(skin, null);
        transfer(sender.getEntityData(), receiver.getEntityData());
        assertEquals(owner, receiver.getOwnerUUID());
        assertEquals(SecurityMode.TRUSTED, receiver.getSecurityMode());
        assertSame(skin, receiver.getSkin());
        CompoundTag robitSaved = new CompoundTag();
        sender.addAdditionalSaveData(robitSaved);
        EntityRobit restored = robit();
        restored.readAdditionalSaveData(robitSaved);
        assertEquals(owner, restored.getOwnerUUID());
        assertEquals(SecurityMode.TRUSTED, restored.getSecurityMode());
        assertEquals(skin, restored.getSkin());

        EntityFlame sentFlame = flame();
        EntityFlame receivedFlame = flame();
        CompoundTag tag = new CompoundTag();
        CompoundTag attachments = new CompoundTag();
        attachments.putString("mekanism:flamethrower_mode", "heat");
        tag.put("neoforge:attachments", attachments);
        sentFlame.readAdditionalSaveData(tag);
        transfer(sentFlame.getEntityData(), receivedFlame.getEntityData());
        CompoundTag saved = new CompoundTag();
        receivedFlame.addAdditionalSaveData(saved);
        assertEquals("heat", saved.getCompound("neoforge:attachments").getString("mekanism:flamethrower_mode"));
    }
}
