package mekanism.common.integration.energy.forgeenergy;

import mekanism.api.annotations.NothingNullByDefault;
import mekanism.api.energy.IStrictEnergyHandler;
import mekanism.common.integration.energy.IEnergyCompat;
import mekanism.common.util.UnitDisplayUtils.EnergyUnit;
import mekanism.fabric.energy.FabricEnergyLookup;
import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup;
import net.fabricmc.fabric.api.lookup.v1.entity.EntityApiLookup;
import net.fabricmc.fabric.api.lookup.v1.item.ItemApiLookup;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;
import team.reborn.energy.api.EnergyStorage;

/**
 * Fabric port delta: the Forge Energy integration identity is Team Reborn's {@link EnergyStorage}, the Fabric ecosystem's Forge-energy analog that
 * this port already bridges through the exact {@code 1.0D} conversion contract. The official {@code StrictEnergyCompat}/{@code GPEnergyCompat}/
 * {@code FNEnergyCompat} entries have no Fabric counterpart here: Mekanism's strict-energy domain stays internal (no external strict-energy lookup
 * identity is invented, per the {@code EnergyHandlerManager} decision), and Grand Power/Flux Networks have no pinned 1.21.1 Fabric ports, which is a
 * platform support boundary rather than an implemented binding.
 */
@NothingNullByDefault
public class ForgeEnergyCompat implements IEnergyCompat {

    @Override
    public BlockApiLookup<?, @Nullable Direction> getCapability() {
        return EnergyStorage.SIDED;
    }

    @Override
    public ItemApiLookup<?, Void> getItemCapability() {
        return EnergyStorage.ITEM;
    }

    @Override
    public EntityApiLookup<?, Void> getEntityCapability() {
        return FabricEnergyLookup.ENTITY_ENERGY;
    }

    @Override
    public boolean isUsable() {
        return EnergyUnit.FORGE_ENERGY.isEnabled();
    }

    @Override
    public boolean capabilityExists() {
        //Team Reborn Energy is an external runtime dependency of this port, not a bundled one
        return FabricLoader.getInstance().isModLoaded("teamreborn");
    }

    @Override
    public IStrictEnergyHandler wrapAsStrictEnergyHandler(Object handler) {
        return FabricEnergyLookup.wrapAsStrictEnergyHandler((EnergyStorage) handler, EnergyUnit.FORGE_ENERGY);
    }
}
