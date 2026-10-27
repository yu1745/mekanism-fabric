package mekanism.common.content.sps;

import static org.junit.jupiter.api.Assertions.assertEquals;

import mekanism.test.TileLevelStub;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SPSLightningDamageTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void onlySyntheticSpsLightningUsesZeroDamage() {
        Level level = TileLevelStub.create();
        DamageProbe entity = new DamageProbe(level);
        entity.setRemainingFireTicks(10);
        entity.thunderHit(null, new SPSMultiblockData.ExperimentLightning(level));
        assertEquals(0, entity.damage);
        entity.thunderHit(null, new LightningBolt(EntityType.LIGHTNING_BOLT, level));
        assertEquals(5, entity.damage);
    }

    private static final class DamageProbe extends Entity {
        float damage = -1;

        @SuppressWarnings("unchecked")
        DamageProbe(Level level) {
            super((EntityType<? extends Entity>) (EntityType<?>) EntityType.ARROW, level);
        }

        @Override
        public boolean hurt(DamageSource source, float amount) {
            damage = amount;
            return true;
        }

        @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { }
        @Override protected void readAdditionalSaveData(CompoundTag tag) { }
        @Override protected void addAdditionalSaveData(CompoundTag tag) { }
    }
}
