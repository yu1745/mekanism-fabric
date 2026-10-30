package mekanism.additions.common.world.modifier;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import mekanism.additions.common.config.AdditionsConfig;
import mekanism.additions.common.config.MekanismAdditionsConfig;
import mekanism.additions.common.entity.baby.BabyType;
import mekanism.additions.common.registries.AdditionsBiomeModifierSerializers;
import mekanism.api.SerializationConstants;
import mekanism.api.MekanismAPI;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;

public record BabyEntitySpawnBiomeModifier(BabyType babyType, AdditionsConfig.SpawnConfig spawnConfig) {

    public BabyEntitySpawnBiomeModifier(BabyType babyType) {
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

    public MapCodec<BabyEntitySpawnBiomeModifier> codec() {
        return AdditionsBiomeModifierSerializers.SPAWN_BABIES;
    }

    public static MapCodec<BabyEntitySpawnBiomeModifier> makeCodec() {
        return RecordCodecBuilder.mapCodec(builder -> builder.group(
              BabyType.CODEC.fieldOf(SerializationConstants.BABY_TYPE).forGetter(BabyEntitySpawnBiomeModifier::babyType)
        ).apply(builder, BabyEntitySpawnBiomeModifier::new));
    }
}