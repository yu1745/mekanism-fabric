package mekanism.common.integration.energy.forgeenergy;

import mekanism.api.annotations.NothingNullByDefault;
import mekanism.api.energy.IStrictEnergyHandler;
import mekanism.common.integration.energy.IEnergyCompat;
import mekanism.common.util.UnitDisplayUtils.EnergyUnit;
import mekanism.fabric.energy.FabricEnergyLookup;
import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup;
import net.fabricmc.fabric.api.lookup.v1.entity.EntityApiLookup;
import net.fabricmc.fabric.api.lookup.v1.item.ItemApiLookup;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;
import team.reborn.energy.api.EnergyStorage;

/**
 * Fabric port delta: the Forge Energy integration identity is Team Reborn's {@link EnergyStorage}, the Fabric ecosystem's Forge-energy analog that
 * this port bridges through the configured conversion. The Mekanism strict-energy identity is resolved separately before this external adapter;
 * Grand Power/Flux Networks have no pinned 1.21.1 Fabric bindings here.
 */
@NothingNullByDefault
public class ForgeEnergyCompat implements IEnergyCompat {

    @Override
    public BlockApiLookup<?, @Nullable Direction> getCapability() {
        return EnergyStorage.SIDED;
    }

    @Override
    public ItemApiLookup<?, ContainerItemContext> getItemCapability() {
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
        //Team Reborn Energy is an external runtime dependency of this port, not a bundled one. Its mod id is
        //team_reborn_energy; a wrong id silently disables every energy acceptor and leaves transmitter networks unconnected.
        return FabricLoader.getInstance().isModLoaded("team_reborn_energy");
    }

    @Override
    public IStrictEnergyHandler wrapAsStrictEnergyHandler(Object handler) {
        return FabricEnergyLookup.wrapAsStrictEnergyHandler((EnergyStorage) handler, EnergyUnit.FORGE_ENERGY);
    }
}
