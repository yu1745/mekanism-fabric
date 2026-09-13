package mekanism.common.capabilities;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Supplier;
import mekanism.api.annotations.NothingNullByDefault;
import mekanism.common.capabilities.resolver.ICapabilityResolver;
import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NothingNullByDefault
public class CapabilityCache {

    /**
     * Fabric port note: this cache dispatches by the identity of the real Fabric {@link BlockApiLookup} (for example {@code ItemStorage.SIDED} or
     * {@code ChemicalHandlerManager.CHEMICAL_HANDLER}) instead of a NeoForge {@code BlockCapability} token, so an {@link IdentityHashMap} keeps the official
     * exact-identity dispatch semantics.
     */
    private final Map<BlockApiLookup<?, @Nullable Direction>, ICapabilityResolver<@Nullable Direction>> capabilityResolvers = new IdentityHashMap<>();
    /**
     * List of unique resolvers to make invalidating all easier as some resolvers (energy) may support multiple capabilities.
     */
    private final List<ICapabilityResolver<?>> uniqueResolvers = new ArrayList<>();
    /**
     * Fabric port adaptation: the official cache stored the concrete {@code TileComponentConfig} component. This port keeps the disabled-side policy owned by the
     * real component and receives it as the bound predicate {@code config::isCapabilityDisabled} (lookup identity and nullable physical side in, disabled out).
     * No configuration state is reimplemented here.
     */
    @Nullable
    private BiPredicate<BlockApiLookup<?, @Nullable Direction>, @Nullable Direction> configDisabled;

    private static final Logger LOGGER = LoggerFactory.getLogger("Mekanism/CapabilityCache");

    /**
     * Adds a capability resolver to the list of resolvers for this cache.
     */
    public void addCapabilityResolver(ICapabilityResolver<@Nullable Direction> resolver) {
        uniqueResolvers.add(resolver);
        List<BlockApiLookup<?, @Nullable Direction>> supportedCapabilities = resolver.getSupportedCapabilities();
        for (BlockApiLookup<?, @Nullable Direction> supportedCapability : supportedCapabilities) {
            //Note: We add the capability regardless of if it is registered as we will just short circuit and always disable the capability
            // if it isn't in use by the time the capability is queried. In theory, we shouldn't ever be getting created before the capabilities
            // have been registered, but just in case we ensure it works properly
            if (capabilityResolvers.put(supportedCapability, resolver) != null) {
                LOGGER.warn("Multiple capability resolvers registered for {}. Overriding", supportedCapability.getId(), new Exception());
            }
        }
    }

    /**
     * Adds the given bound disabled-side check for use in checking if capabilities are disabled on a specific side.
     *
     * @param configDisabled Bound capability-disabled predicate of the real configuration component (or of a tile without one, disabling nothing), never the
     *                       configuration state itself.
     */
    public void addConfigComponent(BiPredicate<BlockApiLookup<?, @Nullable Direction>, @Nullable Direction> configDisabled) {
        if (this.configDisabled != null) {
            LOGGER.warn("Config component already registered. Overriding", new Exception());
        }
        this.configDisabled = configDisabled;
    }

    /**
     * Checks if the given capability is disabled for the specific side.
     *
     * @return {@code true} if the capability is disabled, {@code false} otherwise.
     */
    public boolean isCapabilityDisabled(BlockApiLookup<?, @Nullable Direction> capability, @Nullable Direction side) {
        return configDisabled != null && configDisabled.test(capability, side);
    }

    @Nullable
    public ICapabilityResolver<@Nullable Direction> getResolver(BlockApiLookup<?, @Nullable Direction> capability) {
        return capabilityResolvers.get(capability);
    }

    public ICapabilityResolver<@Nullable Direction> getResolver(BlockApiLookup<?, @Nullable Direction> capability,
          Supplier<ICapabilityResolver<@Nullable Direction>> resolver) {
        ICapabilityResolver<@Nullable Direction> knownResolver = getResolver(capability);
        if (knownResolver == null) {
            knownResolver = resolver.get();
            addCapabilityResolver(knownResolver);
        }
        return knownResolver;
    }

    /**
     * Invalidates the given capability on the given side.
     *
     * @param capability Capability
     * @param side       Side
     */
    public void invalidate(BlockApiLookup<?, @Nullable Direction> capability, @Nullable Direction side) {
        ICapabilityResolver<@Nullable Direction> capabilityResolver = capabilityResolvers.get(capability);
        if (capabilityResolver != null) {
            capabilityResolver.invalidate(capability, side);
        }
    }

    /**
     * Invalidates the given capability on all sides
     *
     * @param capability Capability
     */
    public void invalidateAll(BlockApiLookup<?, @Nullable Direction> capability) {
        ICapabilityResolver<@Nullable Direction> capabilityResolver = capabilityResolvers.get(capability);
        if (capabilityResolver != null) {
            capabilityResolver.invalidateAll();
        }
    }

    /**
     * Invalidates all cached capabilities.
     */
    public void invalidateAll() {
        uniqueResolvers.forEach(ICapabilityResolver::invalidateAll);
    }
}
