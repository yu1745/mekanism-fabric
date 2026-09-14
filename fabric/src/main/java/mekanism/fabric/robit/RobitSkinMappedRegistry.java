package mekanism.fabric.robit;

import com.mojang.serialization.Lifecycle;
import mekanism.api.robit.RobitSkin;
import mekanism.api.robit.RobitSkinRegistry;
import net.minecraft.core.DefaultedMappedRegistry;
import net.minecraft.core.Registry;

/** Fresh per-load native defaulted registry, shared by the disk and network RegistryDataLoader paths. */
public final class RobitSkinMappedRegistry extends DefaultedMappedRegistry<RobitSkin> {
    public RobitSkinMappedRegistry(Lifecycle lifecycle) {
        super(RobitSkinRegistry.BASE.location().toString(), RobitSkinRegistry.SKIN_KEY, lifecycle, false);
    }

    @Override
    public Registry<RobitSkin> freeze() {
        if (getOptional(RobitSkinRegistry.BASE.location()).isEmpty()) {
            throw new IllegalStateException("Missing default robit skin " + RobitSkinRegistry.BASE.location());
        }
        return super.freeze();
    }
}
