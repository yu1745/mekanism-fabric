package mekanism.fabric.tile;

import mekanism.api.IConfigCardAccess;
import mekanism.api.IConfigurable;
import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

/** Canonical native identity used by configuration-card item and actual block-entity providers. */
public final class MekanismBlockLookups {
    public static final BlockApiLookup<IConfigCardAccess, Direction> CONFIG_CARD = BlockApiLookup.get(
          ResourceLocation.fromNamespaceAndPath("mekanism", "config_card"), IConfigCardAccess.class, Direction.class);

    public static final BlockApiLookup<IConfigurable, Direction> CONFIGURABLE = BlockApiLookup.get(
          ResourceLocation.fromNamespaceAndPath("mekanism", "configurable"), IConfigurable.class, Direction.class);

    private MekanismBlockLookups() {
    }
}
