package mekanism.additions.common.registries;

import com.mojang.serialization.MapCodec;
import mekanism.additions.common.MekanismAdditions;
import mekanism.additions.common.world.modifier.BabyEntitySpawnBiomeModifier;
import net.minecraft.resources.ResourceLocation;

/** Codec owned by the Additions datapack loader; the original resource type ID is retained. */
public class AdditionsBiomeModifierSerializers {
    public static final ResourceLocation ID = MekanismAdditions.rl("baby_mob_spawn");
    public static final MapCodec<BabyEntitySpawnBiomeModifier> SPAWN_BABIES = BabyEntitySpawnBiomeModifier.makeCodec();
}
