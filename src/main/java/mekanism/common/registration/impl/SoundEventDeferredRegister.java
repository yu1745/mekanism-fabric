package mekanism.common.registration.impl;

import java.util.function.Function;
import java.util.function.Supplier;
import mekanism.api.annotations.NothingNullByDefault;
import mekanism.common.registration.MekanismDeferredRegister;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

@NothingNullByDefault
public class SoundEventDeferredRegister extends MekanismDeferredRegister<SoundEvent> {

    public SoundEventDeferredRegister(String modid) {
        super(Registries.SOUND_EVENT, modid, SoundEventRegistryObject::new);
    }

    public SoundEventRegistryObject<SoundEvent> register(String name) {
        return register(name, SoundEvent::createVariableRangeEvent);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <SOUND extends SoundEvent> SoundEventRegistryObject<SOUND> register(String name, Function<ResourceLocation, ? extends SOUND> func) {
        return (SoundEventRegistryObject<SOUND>) super.register(name, func);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <SOUND extends SoundEvent> SoundEventRegistryObject<SOUND> register(String name, Supplier<? extends SOUND> sup) {
        return (SoundEventRegistryObject<SOUND>) super.register(name, sup);
    }
}