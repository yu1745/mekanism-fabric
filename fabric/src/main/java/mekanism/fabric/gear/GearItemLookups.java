package mekanism.fabric.gear;

import mekanism.api.lasers.ILaserDissipation;
import mekanism.api.radiation.capability.IRadiationShielding;
import net.fabricmc.fabric.api.lookup.v1.item.ItemApiLookup;
import net.minecraft.resources.ResourceLocation;

/** Canonical read-only gear providers shared by equipment registration and damage consumers. */
public final class GearItemLookups {
    public static final ItemApiLookup<ILaserDissipation, Void> LASER_DISSIPATION = ItemApiLookup.get(
          ResourceLocation.fromNamespaceAndPath("mekanism", "laser_dissipation"), ILaserDissipation.class, Void.class);
    public static final ItemApiLookup<IRadiationShielding, Void> RADIATION_SHIELDING = ItemApiLookup.get(
          ResourceLocation.fromNamespaceAndPath("mekanism", "radiation_shielding"), IRadiationShielding.class, Void.class);

    private GearItemLookups() {
    }
}
