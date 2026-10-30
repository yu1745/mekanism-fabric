package mekanism.additions.common.registries;

import com.mojang.serialization.MapCodec;
import mekanism.additions.common.MekanismAdditions;
import mekanism.additions.common.world.modifier.BabyEntitySpawnStructureModifier;
import net.minecraft.resources.ResourceLocation;

/** Codec owned by the Additions datapack loader; the original resource type ID is retained. */
public class AdditionsStructureModifierSerializers {
    public static final ResourceLocation ID = MekanismAdditions.rl("baby_mob_spawn");
    public static final MapCodec<BabyEntitySpawnStructureModifier> SPAWN_BABIES = BabyEntitySpawnStructureModifier.makeCodec();
}
