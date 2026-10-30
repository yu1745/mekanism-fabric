package mekanism.additions.common.world.modifier;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import mekanism.additions.common.config.AdditionsConfig;
import mekanism.additions.common.config.MekanismAdditionsConfig;
import mekanism.additions.common.entity.baby.BabyType;
import mekanism.additions.common.registries.AdditionsStructureModifierSerializers;
import mekanism.api.SerializationConstants;
import mekanism.api.MekanismAPI;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.levelgen.structure.Structure;

public record BabyEntitySpawnStructureModifier(BabyType babyType, AdditionsConfig.SpawnConfig spawnConfig) {

    public BabyEntitySpawnStructureModifier(BabyType babyType) {
        this(babyType, MekanismAdditionsConfig.additions.getConfig(babyType));
    }

    public java.util.List<net.minecraft.world.level.biome.MobSpawnSettings.SpawnerData> modify(
          java.util.List<net.minecraft.world.level.biome.MobSpawnSettings.SpawnerData> spawns) {
        if (!spawnConfig.shouldSpawn.get()) {
            return spawns;
        }
        var additions = spawnConfig.getSpawnersToAdd(spawns);
        if (additions.isEmpty()) {
            return spawns;
        }
        var result = new java.util.ArrayList<>(spawns);
        result.addAll(additions);
        return result;
    }

    public MapCodec<BabyEntitySpawnStructureModifier> codec() {
        return AdditionsStructureModifierSerializers.SPAWN_BABIES;
    }

    public static MapCodec<BabyEntitySpawnStructureModifier> makeCodec() {
        return RecordCodecBuilder.mapCodec(builder -> builder.group(
              BabyType.CODEC.fieldOf(SerializationConstants.BABY_TYPE).forGetter(BabyEntitySpawnStructureModifier::babyType)
        ).apply(builder, BabyEntitySpawnStructureModifier::new));
    }
}