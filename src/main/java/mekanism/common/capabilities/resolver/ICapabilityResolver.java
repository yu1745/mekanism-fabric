package mekanism.common.capabilities.resolver;

import java.util.List;
import mekanism.api.annotations.NothingNullByDefault;
import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

@NothingNullByDefault
public interface ICapabilityResolver<CONTEXT> {

    /**
     * Gets the list of capabilities this resolver is able to resolve.
     *
     * @return List of capabilities this resolver can resolve.
     */
    List<BlockApiLookup<?, CONTEXT>> getSupportedCapabilities();

    /**
     * Resolves a given capability from a given side. This value should be cached for later invalidation, as well as quicker re-lookup.
     *
     * @param capability Capability
     * @param side       Side
     *
     * @return The exposed view for the given capability, or {@code null} if this resolver deliberately exposes nothing for the given side (for example the
     *         internal null side, a side without visible containers, or an identity that is not one of {@link #getSupportedCapabilities()}).
     *
     * @apiNote This method should only be called with capabilities that are in {@link #getSupportedCapabilities()}
     * @implNote The result should be cached
     */
    @Nullable
    <T> T resolve(BlockApiLookup<T, CONTEXT> capability, @UnknownNullability CONTEXT side);

    /**
     * Invalidates the given capability on the given side.
     *
     * @param capability Capability
     * @param context    Context
     */
    void invalidate(BlockApiLookup<?, CONTEXT> capability, @UnknownNullability CONTEXT context);

    /**
     * Invalidates all cached capabilities.
     */
    void invalidateAll();
}
