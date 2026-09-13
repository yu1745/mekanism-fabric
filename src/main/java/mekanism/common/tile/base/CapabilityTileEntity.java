package mekanism.common.tile.base;

import java.util.Collection;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.api.heat.IHeatHandler;
import mekanism.common.capabilities.CapabilityCache;
import mekanism.common.capabilities.resolver.ICapabilityResolver;
import mekanism.common.capabilities.resolver.manager.ChemicalHandlerManager;
import mekanism.common.capabilities.resolver.manager.HeatHandlerManager;
import mekanism.common.capabilities.resolver.manager.ICapabilityHandlerManager;
import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Capability-aware base tile owning the real {@link CapabilityCache} and the provider surface through which Fabric's
 * {@link BlockApiLookup#registerForBlockEntity(BiFunction, BlockEntityType)} exposes machine capabilities.
 *
 * <p>Fabric port notes on the invalidation contract: NeoForge's {@code invalidateCapabilities()} world notification has no public Fabric equivalent, and the
 * pinned Fabric lookup implementation caches only the block entity and provider, never the returned API, invoking the provider again on every find. This base
 * therefore reaches the same freshness by revoking its internal cached views on every invalidation transition: retained views fail closed permanently, and the
 * next provider call resolves against current container visibility and side configuration. The official bounding-position walk
 * ({@code AttributeHasBounding} + {@code world.invalidateCapabilities}) is intentionally absent from this notification path for the same reason — bounding
 * providers resolve their current master on each query — and no bounding gameplay behavior is changed by that omission.</p>
 *
 * <p>Lifecycle: providers refuse removed or unloaded owners, so a stale block entity handed over by any foreign cache exposes nothing. Server load opens a fresh
 * exposure generation, server unload (including chunk unload) and removal close it and revoke every retained view. {@link #clearRemoved()} does not reopen an
 * unloaded generation; only the real server load path does.</p>
 */
public abstract class CapabilityTileEntity extends TileEntityUpdateable {

    //Note: The below providers assume that the capability if supported has been added by either addCapabilityResolver or addCapabilityResolvers
    /**
     * Eager chemical provider on the Mekanism-owned chemical lookup identity. The exposed API is the Mekanism domain handler itself.
     */
    public static final BiFunction<CapabilityTileEntity, @Nullable Direction, IChemicalHandler> CHEMICAL_HANDLER_PROVIDER = basicCapabilityProvider(ChemicalHandlerManager.CHEMICAL_HANDLER);
    /**
     * Eager heat provider on the Mekanism-owned heat lookup identity. The exposed API is the Mekanism domain handler itself.
     */
    public static final BiFunction<CapabilityTileEntity, @Nullable Direction, IHeatHandler> HEAT_HANDLER_PROVIDER = basicCapabilityProvider(HeatHandlerManager.HEAT_HANDLER);
    /**
     * Eager item provider on Fabric's {@code ItemStorage.SIDED}; the exposed API is the transaction {@link Storage} view, not a Mekanism domain handler.
     */
    public static final BiFunction<CapabilityTileEntity, @Nullable Direction, Storage<ItemVariant>> ITEM_HANDLER_PROVIDER = basicCapabilityProvider(ItemStorage.SIDED);
    /**
     * Eager fluid provider on Fabric's {@code FluidStorage.SIDED}; the exposed API is the transaction {@link Storage} view, not a Mekanism domain handler.
     */
    public static final BiFunction<CapabilityTileEntity, @Nullable Direction, Storage<FluidVariant>> FLUID_HANDLER_PROVIDER = basicCapabilityProvider(FluidStorage.SIDED);

    /**
     * Eager provider factory for a capability whose resolver was registered by the tile constructor ({@code addCapabilityResolver}/{@code addCapabilityResolvers}).
     * Typed as the {@link BiFunction} shape accepted by {@link BlockApiLookup#registerForBlockEntity(BiFunction, BlockEntityType)}, so the deferred machine registry
     * owner registers it without any adapter.
     */
    public static <CAP> BiFunction<CapabilityTileEntity, @Nullable Direction, CAP> basicCapabilityProvider(BlockApiLookup<CAP, @Nullable Direction> capability) {
        return (tile, context) -> {
            if (!tile.canExposeCapabilities()) {
                //Fail closed for a removed or unloaded owner, even one still held by a foreign cache
                return null;
            }
            if (tile.capabilityCache.isCapabilityDisabled(capability, context)) {
                return null;
            }
            ICapabilityResolver<@Nullable Direction> resolver = tile.capabilityCache.getResolver(capability);
            return resolver == null ? null : resolver.resolve(capability, context);
        };
    }

    /**
     * Lazy provider factory registering the resolver on first successful query. Typed as the {@link BiFunction} shape accepted by
     * {@link BlockApiLookup#registerForBlockEntity(BiFunction, BlockEntityType)}. Energy uses this generic factory on the single loaded external energy identity.
     */
    public static <TILE extends CapabilityTileEntity, CAP> BiFunction<TILE, @Nullable Direction, CAP> capabilityProvider(
          BlockApiLookup<CAP, @Nullable Direction> capability, BiFunction<TILE, BlockApiLookup<CAP, @Nullable Direction>, ICapabilityResolver<@Nullable Direction>> resolverGetter) {
        return (tile, context) -> {
            if (!tile.canExposeCapabilities()) {
                //Fail closed for a removed or unloaded owner, even one still held by a foreign cache
                return null;
            }
            CapabilityCache capabilityCache = ((CapabilityTileEntity) tile).capabilityCache;
            if (capabilityCache.isCapabilityDisabled(capability, context)) {
                return null;
            }
            return capabilityCache.getResolver(capability, () -> resolverGetter.apply(tile, capability))
                  .resolve(capability, context);
        };
    }

    private final CapabilityCache capabilityCache = new CapabilityCache();
    /**
     * Fabric port generation guard: {@code true} only while a server-loaded generation of this tile may expose capabilities. Opened by the server block-entity load
     * event, closed by removal and by server (block-entity or chunk) unload; {@link #clearRemoved()} deliberately never reopens it.
     */
    private boolean capabilityExposureOpen;

    /**
     * Fabric adaptation of the official signature: the vanilla {@link Holder} replaces NeoForge's {@code TileEntityTypeRegistryObject}, matching the accepted
     * {@link TileEntityUpdateable} constructor.
     */
    public CapabilityTileEntity(Holder<? extends BlockEntityType<?>> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /**
     * Whether this tile's current generation is allowed to answer provider queries at all: it must not be removed and its exposure must be open (server loaded).
     */
    public final boolean canExposeCapabilities() {
        return !isRemoved() && capabilityExposureOpen;
    }

    /**
     * Opens a fresh capability exposure generation. Called by the Mekanism server load lifecycle when this block entity actually enters a server world, which is the
     * only path that reopens exposure after removal or unload. Idempotent for a generation that is already open.
     */
    public final void openCapabilityExposure() {
        capabilityExposureOpen = true;
    }

    /**
     * Closes capability exposure and permanently revokes every cached view, so already-retained views fail closed instead of keeping access to their old owner.
     * Called by the Mekanism server unload lifecycle (block-entity unload, which the pinned Fabric lifecycle also fires for every block entity on chunk and world
     * unload) and by {@link #setRemoved()}; idempotent.
     */
    public final void closeCapabilityExposure() {
        capabilityExposureOpen = false;
        capabilityCache.invalidateAll();
    }

    protected final void addCapabilityResolvers(List<ICapabilityHandlerManager<?>> capabilityHandlerManagers) {
        for (ICapabilityHandlerManager<?> capabilityHandlerManager : capabilityHandlerManagers) {
            //Add all managers that we support in our tile, as capability resolvers
            if (capabilityHandlerManager.canHandle()) {
                capabilityCache.addCapabilityResolver(capabilityHandlerManager);
            }
        }
    }

    protected final void addCapabilityResolver(ICapabilityResolver<@Nullable Direction> resolver) {
        capabilityCache.addCapabilityResolver(resolver);
    }

    /**
     * Binds the disabled-side policy used by provider queries. Fabric adaptation of the official signature: instead of the concrete {@code TileComponentConfig}
     * component (which stays outside the compiled slice), the real component's bound {@code isCapabilityDisabled} predicate is forwarded here by the actual caller.
     *
     * @param configDisabled Bound predicate: lookup identity and nullable physical side in, disabled out.
     */
    protected final void addConfigComponent(BiPredicate<BlockApiLookup<?, @Nullable Direction>, @Nullable Direction> configDisabled) {
        capabilityCache.addConfigComponent(configDisabled);
    }

    /**
     * Invalidates our backing internal representations for all capabilities. Fabric adaptation: there is no public world-level capability invalidation to notify,
     * so this revocation of the internal cached views (retained views fail closed) is the whole transition; the next provider query resolves fresh state. The
     * official bounding-position notification walk is intentionally absent from this path — bounding providers resolve their current master on every query.
     */
    public void invalidateCapabilitiesFull() {
        //Clear our internal cached capability instances so that any later query provides the up to date instance
        capabilityCache.invalidateAll();
    }

    @Override
    public void setRemoved() {
        //Note: Close the exposure and revoke the backing caps before letting super run, as then if anything somehow queries us during invalidation handling
        // they will get the proper non cached data
        closeCapabilityExposure();
        super.setRemoved();
    }

    @Override
    public void clearRemoved() {
        //Note: Revoke the backing caps before letting super run, as then if anything somehow queries us during invalidation handling
        // they will get the proper non cached data
        capabilityCache.invalidateAll();
        super.clearRemoved();
        //Fabric port: reopening a closed generation is deliberately NOT done here. Only the real server load path
        // (openCapabilityExposure) opens a fresh generation, so a mere clearRemoved never resurrects an unloaded owner.
    }

    /**
     * Invalidates the given capability on the given side. Fabric adaptation: revokes the internal cached views for that identity and side; there is no world
     * notification to perform.
     */
    public final void invalidateCapability(BlockApiLookup<?, @Nullable Direction> capability, @Nullable Direction side) {
        capabilityCache.invalidate(capability, side);
    }

    /**
     * Invalidates the given capability on all sides. Fabric adaptation: revokes the internal cached views for that identity; there is no world notification.
     */
    public final void invalidateCapabilityAll(BlockApiLookup<?, @Nullable Direction> capability) {
        capabilityCache.invalidateAll(capability);
    }

    /**
     * Invalidates the given capabilities on the given side. Fabric adaptation: revokes the internal cached views for those identities and side; there is no world
     * notification.
     */
    public final void invalidateCapabilities(Collection<BlockApiLookup<?, @Nullable Direction>> capabilities, @Nullable Direction side) {
        for (BlockApiLookup<?, @Nullable Direction> capability : capabilities) {
            capabilityCache.invalidate(capability, side);
        }
    }

    /**
     * Invalidates the given capabilities on all sides. Fabric adaptation: revokes the internal cached views for those identities; there is no world notification.
     */
    public final void invalidateCapabilitiesAll(Collection<BlockApiLookup<?, @Nullable Direction>> capabilities) {
        for (BlockApiLookup<?, @Nullable Direction> capability : capabilities) {
            capabilityCache.invalidateAll(capability);
        }
    }
}
