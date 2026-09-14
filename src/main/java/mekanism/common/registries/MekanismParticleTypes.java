package mekanism.common.registries;

import mekanism.api.MekanismAPI;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import mekanism.common.particle.LaserParticleType;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import mekanism.common.registration.MekanismDeferredHolder;
import mekanism.common.registration.MekanismDeferredRegister;

public class MekanismParticleTypes {

    private MekanismParticleTypes() {
    }

    public static final MekanismDeferredRegister<ParticleType<?>> PARTICLE_TYPES = new MekanismDeferredRegister<>(Registries.PARTICLE_TYPE, MekanismAPI.MEKANISM_MODID);

    public static final MekanismDeferredHolder<ParticleType<?>, LaserParticleType> LASER = PARTICLE_TYPES.register("laser", LaserParticleType::new);
    public static final MekanismDeferredHolder<ParticleType<?>, SimpleParticleType> JETPACK_FLAME = PARTICLE_TYPES.register("jetpack_flame", () -> FabricParticleTypes.simple(false));
    public static final MekanismDeferredHolder<ParticleType<?>, SimpleParticleType> JETPACK_SMOKE = PARTICLE_TYPES.register("jetpack_smoke", () -> FabricParticleTypes.simple(false));
    public static final MekanismDeferredHolder<ParticleType<?>, SimpleParticleType> SCUBA_BUBBLE = PARTICLE_TYPES.register("scuba_bubble", () -> FabricParticleTypes.simple(false));
    public static final MekanismDeferredHolder<ParticleType<?>, SimpleParticleType> RADIATION = PARTICLE_TYPES.register("radiation", () -> FabricParticleTypes.simple(false));
}