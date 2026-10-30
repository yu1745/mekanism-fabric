package mekanism.fabric.additions.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import mekanism.additions.common.world.modifier.BabyEntitySpawnStructureModifier;
import mekanism.fabric.additions.AdditionsSpawnModifiers;
import net.minecraft.util.random.WeightedRandomList;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSpawnOverride;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Structure.class)
abstract class AdditionsStructureSpawnsMixin implements AdditionsSpawnModifiers.StructureSpawns {
    @Unique private List<BabyEntitySpawnStructureModifier> mekanismadditions$modifiers = List.of();

    @Override
    public void mekanismadditions$setModifiers(List<BabyEntitySpawnStructureModifier> modifiers) {
        mekanismadditions$modifiers = List.copyOf(modifiers);
    }

    @ModifyReturnValue(method = "spawnOverrides", at = @At("RETURN"))
    private Map<MobCategory, StructureSpawnOverride> mekanismadditions$spawns(Map<MobCategory, StructureSpawnOverride> original) {
        var monsters = original.get(MobCategory.MONSTER);
        if (monsters == null || mekanismadditions$modifiers.isEmpty()) return original;
        var spawns = monsters.spawns().unwrap();
        for (var modifier : mekanismadditions$modifiers) spawns = modifier.modify(spawns);
        if (spawns == monsters.spawns().unwrap()) return original;
        var result = new EnumMap<MobCategory, StructureSpawnOverride>(MobCategory.class);
        result.putAll(original);
        result.put(MobCategory.MONSTER, new StructureSpawnOverride(monsters.boundingBox(), WeightedRandomList.create(spawns)));
        return result;
    }
}
