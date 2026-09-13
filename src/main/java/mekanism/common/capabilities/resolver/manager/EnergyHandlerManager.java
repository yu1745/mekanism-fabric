package mekanism.common.capabilities.resolver.manager;

import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import mekanism.api.annotations.NothingNullByDefault;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.energy.IMekanismStrictEnergyHandler;
import mekanism.common.capabilities.holder.energy.IEnergyContainerHolder;
import mekanism.common.util.UnitDisplayUtils;
import mekanism.fabric.energy.FabricEnergyLookup;
import mekanism.fabric.energy.FabricEnergyStorage;
import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;
import team.reborn.energy.api.EnergyStorage;

/**
 * Fabric port notes:
 * <ul>
 * <li>The only loaded external energy identity is Team Reborn's {@link EnergyStorage#SIDED}; Joules-domain strict-energy exposure has no external consumer or
 * provider in this port yet, so no strict-energy lookup identity is invented here. The Joules domain remains authoritative in {@link #getContainers(Direction)}
 * and on the internal base handler, and the external view converts through the configured
 * {@link UnitDisplayUtils.EnergyUnit#FORGE_ENERGY FORGE_ENERGY} conversion.</li>
 * <li>The base handler surface is narrowed from the official {@link ISidedStrictEnergyHandler} to {@link IMekanismStrictEnergyHandler}, which the only current
 * consumer ({@code TileEntityMekanism}) implements.</li>
 * <li>The null side is internal and deliberately exposes no external EnergyStorage, so the official null-side read-only cache has no port counterpart; internal
 * Joules access stays on the base handler itself.</li>
 * <li>Cached external views are revoked (permanently fail closed, including the reverse stableHandler unwrap route) before their cache entries are removed;
 * transaction rollback captured before invalidation still restores through the view itself.</li>
 * </ul>
 */
@NothingNullByDefault
public class EnergyHandlerManager implements ICapabilityHandlerManager<IEnergyContainer> {

    private final Map<Direction, Map<BlockApiLookup<?, @Nullable Direction>, Object>> cachedCapabilities;
    private final IMekanismStrictEnergyHandler baseHandler;
    private final boolean canHandle;
    @Nullable
    private final IEnergyContainerHolder holder;

    public EnergyHandlerManager(@Nullable IEnergyContainerHolder holder, IMekanismStrictEnergyHandler baseHandler) {
        this.holder = holder;
        this.canHandle = this.holder != null;
        this.baseHandler = baseHandler;
        if (this.canHandle) {
            cachedCapabilities = new EnumMap<>(Direction.class);
        } else {
            cachedCapabilities = Collections.emptyMap();
        }
    }

    @Override
    public boolean canHandle() {
        return canHandle;
    }

    @Override
    public List<IEnergyContainer> getContainers(@Nullable Direction side) {
        return canHandle() ? holder.getEnergyContainers(side) : Collections.emptyList();
    }

    @Override
    public List<BlockApiLookup<?, @Nullable Direction>> getSupportedCapabilities() {
        //Return all loaded energy caps so that we get attached and then just handle config checks in the actual lookup.
        // On Fabric, Team Reborn's EnergyStorage.SIDED is the single loaded external energy identity.
        return List.of(EnergyStorage.SIDED);
    }

    /**
     * Lazily get and cache an external energy view for the given physical side.
     *
     * @apiNote Assumes that {@link #canHandle} has been called before this and that it was {@code true}.
     */
    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T> T resolve(BlockApiLookup<T, @Nullable Direction> capability, @Nullable Direction side) {
        if (capability != EnergyStorage.SIDED) {
            //An unsupported lookup identity must never be cast into a different API
            return null;
        }
        if (side == null) {
            //The null side is internal Mekanism access and deliberately never exposes an external converted EnergyStorage
            return null;
        }
        if (getContainers(side).isEmpty()) {
            //If we don't have any containers accessible from that side, don't return a handler
            return null;
        }
        //If we already contain a cached object for this instance then just use it, otherwise expose a stable side view through the energy seam
        Map<BlockApiLookup<?, @Nullable Direction>, Object> cache = cachedCapabilities.computeIfAbsent(side, key -> new IdentityHashMap<>());
        Object result = cache.get(capability);
        if (result == null) {
            result = FabricEnergyLookup.expose(baseHandler, side, holder, UnitDisplayUtils.EnergyUnit.FORGE_ENERGY);
            if (result == null) {
                //No external view (for example the conversion is disabled by config), don't cache null
                return null;
            }
            cache.put(capability, result);
        }
        return (T) result;
    }

    @Override
    public void invalidate(BlockApiLookup<?, @Nullable Direction> capability, @Nullable Direction side) {
        //Note: We don't invalidate the base handler as it is still valid regardless and is just a wrapper with the holder
        // removing containers when they shouldn't be accessible, so we only revoke the cached exposed views
        if (side != null) {
            Map<BlockApiLookup<?, @Nullable Direction>, Object> cachedSide = cachedCapabilities.get(side);
            if (cachedSide != null) {
                revokeCached(cachedSide.remove(capability));
            }
        }
    }

    @Override
    public void invalidateAll() {
        //Note: We don't invalidate the base handler as it is still valid regardless and is just a wrapper with the holder
        // removing containers when they shouldn't be accessible, so we only revoke the cached exposed views
        cachedCapabilities.values().forEach(cache -> {
            cache.values().forEach(EnergyHandlerManager::revokeCached);
            cache.clear();
        });
        cachedCapabilities.clear();
    }

    private static void revokeCached(@Nullable Object view) {
        if (view instanceof FabricEnergyStorage storage) {
            storage.invalidate();
        }
    }
}
