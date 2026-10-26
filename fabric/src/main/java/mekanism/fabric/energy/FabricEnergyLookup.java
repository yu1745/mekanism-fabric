package mekanism.fabric.energy;

import mekanism.api.energy.IEnergyConversion;
import mekanism.api.energy.IMekanismStrictEnergyHandler;
import mekanism.api.energy.IStrictEnergyHandler;
import mekanism.common.capabilities.holder.IHolder;
import mekanism.common.capabilities.proxy.ProxyStrictEnergyHandler;
import net.fabricmc.fabric.api.lookup.v1.entity.EntityApiLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import team.reborn.energy.api.EnergyStorage;

/**
 * The sole lookup and exposure seam for the Fabric energy boundary.
 */
public final class FabricEnergyLookup {

    /**
     * Mekanism-owned entity energy dispatch identity. Fabric has no ecosystem-wide entity energy API, so this lookup preserves the official entity-energy
     * query path (chargepad, robit) with a real registered identity instead of pretending a standard external one exists; providers register on it when
     * those owners are admitted.
     */
    public static final EntityApiLookup<EnergyStorage, Void> ENTITY_ENERGY = EntityApiLookup.get(
          ResourceLocation.fromNamespaceAndPath("mekanism", "energy_storage_entity"), EnergyStorage.class, Void.class);

    private FabricEnergyLookup() {
    }

    /**
     * Finds external energy at one physical block side. Null/internal-side access is deliberately never granted.
     */
    @Nullable
    public static IStrictEnergyHandler find(@Nullable Level level, @Nullable BlockPos pos, @Nullable Direction side,
          @Nullable IEnergyConversion conversion) {
        if (level == null || pos == null || side == null || !FabricEnergyConversions.isUsable(conversion)) {
            return null;
        }
        return wrap(EnergyStorage.SIDED.find(level, pos, side), side, conversion);
    }

    /**
     * Direct seam used after a lookup and by local tests; it performs no registry mutation.
     */
    @Nullable
    static IStrictEnergyHandler wrap(@Nullable EnergyStorage storage, @Nullable Direction side,
          @Nullable IEnergyConversion conversion) {
        if (storage == null || side == null) {
            return null;
        }
        if (storage instanceof FabricEnergyStorage fabricStorage && !fabricStorage.isExposedOn(side)) {
            // Never turn a storage bound to one physical side into a handler requested from another side, even
            // when another conversion would otherwise cause an additional adapter layer.
            return null;
        }
        if (storage instanceof FabricEnergyStorage invalidatedStorage && invalidatedStorage.isInvalidated()) {
            // Never hand out any view over a storage whose owner lifetime was revoked, even through a fresh
            // conversion wrapper; rollback of previously captured snapshots still restores inside the storage.
            return null;
        }
        if (!FabricEnergyConversions.isUsable(conversion)) {
            return null;
        }
        if (storage instanceof FabricEnergyStorage fabricStorage && fabricStorage.hasConversion(conversion)) {
            return fabricStorage.stableHandler();
        }
        return new FabricStrictEnergyHandler(storage, conversion);
    }

    /**
     * Wraps an arbitrary (external) Team Reborn storage as a Joules-domain Mekanism handler through the given conversion. No exposure side is bound;
     * such a handler must never back {@link FabricEnergyStorage} rollback (that constructor rejects it).
     */
    public static IStrictEnergyHandler wrapAsStrictEnergyHandler(EnergyStorage storage, IEnergyConversion conversion) {
        return new FabricStrictEnergyHandler(storage, conversion);
    }

    /**
     * Creates one stable external view for a physical side. Every exposed container must support deterministic
     * {@link IStrictEnergyHandler#setEnergy(int, long)} restoration with exact read-back; a restore failure is
     * propagated and breaks transaction atomicity after all captured indices have been attempted. The owner is
     * responsible for caching
     * the view for the stable side-bound handler's lifetime and invalidating it with the owner lifecycle.
     *
     * <p>The nullable holder's insertion and extraction policy is consulted live for support queries and operations.
     * When that support changes, the owner must notify neighboring capability consumers; owner wiring is deferred.
     */
    @Nullable
    public static EnergyStorage expose(@Nullable IMekanismStrictEnergyHandler handler, @Nullable Direction side,
          @Nullable IHolder holder, @Nullable IEnergyConversion conversion) {
        if (handler == null || side == null || !FabricEnergyConversions.isUsable(conversion)
              || !handler.canHandleEnergy() || handler.getEnergyContainerCount(side) <= 0) {
            return null;
        }
        ProxyStrictEnergyHandler proxy = new ProxyStrictEnergyHandler(handler, side, holder);
        return new FabricEnergyStorage(proxy, side, conversion,
              () -> holder == null || holder.canInsert(side),
              () -> holder == null || holder.canExtract(side));
    }
}
