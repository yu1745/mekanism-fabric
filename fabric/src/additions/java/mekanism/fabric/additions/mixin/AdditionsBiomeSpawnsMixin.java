package mekanism.fabric.additions.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import java.util.List;
import java.util.Map;
import mekanism.additions.common.world.modifier.BabyEntitySpawnBiomeModifier;
import mekanism.fabric.additions.AdditionsSpawnModifiers;
import net.minecraft.util.random.WeightedRandomList;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.MobSpawnSettings;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MobSpawnSettings.class)
abstract class AdditionsBiomeSpawnsMixin implements AdditionsSpawnModifiers.BiomeSpawns {
    @Shadow @Final private Map<MobCategory, WeightedRandomList<MobSpawnSettings.SpawnerData>> spawners;
    @Shadow @Final private Map<EntityType<?>, MobSpawnSettings.MobSpawnCost> mobSpawnCosts;
    @Unique private List<BabyEntitySpawnBiomeModifier> mekanismadditions$modifiers = List.of();

    @Override
    public void mekanismadditions$setModifiers(List<BabyEntitySpawnBiomeModifier> modifiers) {
        mekanismadditions$modifiers = List.copyOf(modifiers);
    }

    @ModifyReturnValue(method = "getMobs", at = @At("RETURN"))
    private WeightedRandomList<MobSpawnSettings.SpawnerData> mekanismadditions$spawns(
          WeightedRandomList<MobSpawnSettings.SpawnerData> original, MobCategory category) {
        if (category != MobCategory.MONSTER || mekanismadditions$modifiers.isEmpty()) return original;
        var spawns = original.unwrap();
        for (var modifier : mekanismadditions$modifiers) spawns = modifier.modify(spawns);
        return spawns == original.unwrap() ? original : WeightedRandomList.create(spawns);
    }

    @ModifyReturnValue(method = "getMobSpawnCost", at = @At("RETURN"))
    private MobSpawnSettings.MobSpawnCost mekanismadditions$cost(MobSpawnSettings.MobSpawnCost original, EntityType<?> type) {
        var monsters = spawners.getOrDefault(MobCategory.MONSTER, MobSpawnSettings.EMPTY_MOB_LIST).unwrap();
        for (var modifier : mekanismadditions$modifiers) {
            var config = modifier.spawnConfig();
            if (type == config.entityType.value() && config.shouldSpawn.get() && !config.getSpawnersToAdd(monsters).isEmpty()) {
                var parentCost = mobSpawnCosts.get(config.parentType);
                if (parentCost != null) return new MobSpawnSettings.MobSpawnCost(
                      parentCost.energyBudget() * config.maxSpawnCostPercentage.get(), parentCost.charge() * config.spawnCostPerEntityPercentage.get());
            }
        }
        return original;
    }
}
