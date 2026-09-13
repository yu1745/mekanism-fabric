package mekanism.common.capabilities.resolver;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import mekanism.api.annotations.NothingNullByDefault;
import mekanism.common.capabilities.holder.IHolder;
import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * Fabric port note: the official NeoForge bound {@code SIDED_HANDLER extends HANDLER} is dropped. On Fabric the externally exposed type for item, fluid and energy is
 * the transaction {@code Storage} view, which is not a Mekanism domain handler, so the sided domain handler and the external view are only connected through
 * {@link ProxyCreator}. Chemical and heat keep the natural domain subtyping because their exposed type is the Mekanism API handler itself.
 */
@NothingNullByDefault
public class BasicSidedCapabilityResolver<HANDLER, SIDED_HANDLER> implements ICapabilityResolver<@Nullable Direction> {

    private final ProxyCreator<HANDLER, SIDED_HANDLER> proxyCreator;
    private final Map<Direction, HANDLER> handlers;
    private final List<BlockApiLookup<?, @Nullable Direction>> supportedCapabilities;
    private final SIDED_HANDLER baseHandler;
    @Nullable
    private HANDLER readOnlyHandler;

    public BasicSidedCapabilityResolver(SIDED_HANDLER baseHandler, BlockApiLookup<HANDLER, @Nullable Direction> supportedCapability,
          BasicProxyCreator<HANDLER, SIDED_HANDLER> proxyCreator) {
        this(baseHandler, supportedCapability, proxyCreator, true);
    }

    protected BasicSidedCapabilityResolver(SIDED_HANDLER baseHandler, BlockApiLookup<HANDLER, @Nullable Direction> supportedCapability,
          ProxyCreator<HANDLER, SIDED_HANDLER> proxyCreator, boolean canHandle) {
        this.supportedCapabilities = List.of(supportedCapability);
        this.baseHandler = baseHandler;
        this.proxyCreator = proxyCreator;
        if (canHandle) {
            handlers = new EnumMap<>(Direction.class);
        } else {
            handlers = Collections.emptyMap();
        }
    }

    public SIDED_HANDLER getInternal() {
        return baseHandler;
    }

    @Override
    public List<BlockApiLookup<?, @Nullable Direction>> getSupportedCapabilities() {
        return supportedCapabilities;
    }

    @Nullable
    protected IHolder getHolder() {
        return null;
    }

    /**
     * Lazily get and cache a handler instance for the given side, and make it be read only if something else is trying to interact with us using the null side
     */
    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T> T resolve(BlockApiLookup<T, @Nullable Direction> capability, @Nullable Direction side) {
        if (capability != supportedCapabilities.get(0)) {
            //Fabric port: an unsupported lookup identity must never be cast into a different API
            return null;
        }
        if (side == null) {
            if (readOnlyHandler == null) {
                //Note: for the Fabric item/fluid/energy external Storage views the creator deliberately returns null on the null side (internal access),
                // in which case nothing is cached and null is exposed, matching the accepted adapters
                readOnlyHandler = proxyCreator.create(baseHandler, null, getHolder());
            }
            return (T) readOnlyHandler;
        }
        HANDLER handler = handlers.get(side);
        if (handler == null) {
            handler = proxyCreator.create(baseHandler, side, getHolder());
            if (handler == null) {
                //No exposed view for this side (for example no containers visible from it), don't cache null
                return null;
            }
            handlers.put(side, handler);
        }
        return (T) handler;
    }

    @Override
    public void invalidate(BlockApiLookup<?, @Nullable Direction> capability, @Nullable Direction side) {
        if (side == null) {
            if (readOnlyHandler != null) {
                revokeHandler(readOnlyHandler);
                readOnlyHandler = null;
            }
        } else {
            HANDLER handler = handlers.remove(side);
            if (handler != null) {
                revokeHandler(handler);
            }
        }
    }

    @Override
    public void invalidateAll() {
        if (readOnlyHandler != null) {
            revokeHandler(readOnlyHandler);
            readOnlyHandler = null;
        }
        if (!handlers.isEmpty()) {
            handlers.values().forEach(this::revokeHandler);
            handlers.clear();
        }
    }

    /**
     * Fabric port hook: invoked on each cached view immediately before it is dropped by {@link #invalidate} or {@link #invalidateAll}, so that owned external views
     * can permanently fail closed instead of keeping access to their old owner. The default implementation does nothing: the retained chemical and heat domain
     * proxies intentionally remain live delegates over the unchanged base handler (consulting holder policy live), exactly as in the official design, and they hold
     * no transaction snapshots or reverse-unwrap route that could outlive invalidation.
     *
     * @param handler Cached view being dropped
     */
    protected void revokeHandler(HANDLER handler) {
    }

    @FunctionalInterface
    public interface ProxyCreator<HANDLER, SIDED_HANDLER> {

        @Nullable
        HANDLER create(SIDED_HANDLER handler, @Nullable Direction side, @Nullable IHolder holder);
    }

    @FunctionalInterface
    public interface BasicProxyCreator<HANDLER, SIDED_HANDLER> extends ProxyCreator<HANDLER, SIDED_HANDLER> {

        @Nullable
        HANDLER create(SIDED_HANDLER handler, @Nullable Direction side);

        @Override
        default HANDLER create(SIDED_HANDLER handler, @Nullable Direction side, @Nullable IHolder holder) {
            return create(handler, side);
        }
    }
}
