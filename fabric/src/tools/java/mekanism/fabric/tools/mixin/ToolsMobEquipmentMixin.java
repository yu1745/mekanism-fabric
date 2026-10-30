package mekanism.fabric.tools.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import mekanism.tools.common.MobEquipmentHelper;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;

/** Run the Tools spawn owner before the most-derived eligible finalizer, once across superclass calls. */
@Mixin({Zombie.class, ZombieVillager.class, AbstractSkeleton.class, Piglin.class})
abstract class ToolsMobEquipmentMixin {
    @WrapMethod(method = "finalizeSpawn")
    private SpawnGroupData mekanismtools$equipment(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType type,
          SpawnGroupData group, Operation<SpawnGroupData> original) {
        LivingEntity entity = (LivingEntity) (Object) this;
        boolean first = MobEquipmentHelper.enterSpawn(entity);
        try {
            if (first) {
                MobEquipmentHelper.onLivingSpecialSpawn(entity, level, difficulty);
            }
            return original.call(level, difficulty, type, group);
        } finally {
            if (first) {
                MobEquipmentHelper.leaveSpawn(entity);
            }
        }
    }
}
