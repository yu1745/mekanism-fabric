package mekanism.common.tile.base;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import mekanism.common.capabilities.CapabilityCache;
import mekanism.common.capabilities.resolver.ICapabilityResolver;
import mekanism.common.capabilities.resolver.manager.ChemicalHandlerManager;
import mekanism.common.capabilities.resolver.manager.HeatHandlerManager;
import mekanism.fabric.item.FabricItemStorage;
import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Focused contract tests for the real capability-cache and capability-tile owners: exact identity dispatch with lazy resolver registration and unique-resolver
 * invalidation accounting, the bound disabled-side policy gating the eager/lazy providers, and the exposure-generation guard across removal, clear-removed and
 * server load/unload transitions, with retained views staying revoked. The genuine Fabric registration path lives in the Knot transfer source set.
 */
class CapabilityTileLifecycleTest {

    @BeforeAll
    static void bootstrapVanilla() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** Test-local counting resolver: instrumentation for exact identity/side dispatch and unique-resolver lifetime accounting. */
    private static final class CountingResolver implements ICapabilityResolver<Direction> {

        private final List<BlockApiLookup<?, @Nullable Direction>> supported;
        private final List<String> invalidations = new ArrayList<>();
        private final Object marker = new Object();
        private int invalidateAllCalls;

        CountingResolver(List<BlockApiLookup<?, @Nullable Direction>> supported) {
            this.supported = supported;
        }

        @Override
        public List<BlockApiLookup<?, @Nullable Direction>> getSupportedCapabilities() {
            return supported;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> @Nullable T resolve(BlockApiLookup<T, Direction> capability, @Nullable Direction side) {
            return supported.contains(capability) ? (T) marker : null;
        }

        @Override
        public void invalidate(BlockApiLookup<?, Direction> capability, @Nullable Direction side) {
            invalidations.add(capability.getId() + ":" + side);
        }

        @Override
        public void invalidateAll() {
            invalidateAllCalls++;
        }
    }

    @Test
    void cacheDispatchesByIdentityRegistersLazilyOnceAndDelegatesBoundPolicy() {
        CapabilityCache cache = new CapabilityCache();
        CountingResolver resolver = new CountingResolver(List.of(ItemStorage.SIDED, FluidStorage.SIDED));
        cache.addCapabilityResolver(resolver);

        //Exact identity dispatch: a resolver is found only for the identities it declared
        assertSame(resolver, cache.getResolver(ItemStorage.SIDED));
        assertSame(resolver, cache.getResolver(FluidStorage.SIDED));
        assertNull(cache.getResolver(HeatHandlerManager.HEAT_HANDLER));

        //Lazy registration never runs the supplier for a known identity, and registers an unknown identity exactly once
        int[] creations = {0};
        assertSame(resolver, cache.getResolver(ItemStorage.SIDED, () -> {
            creations[0]++;
            return new CountingResolver(List.of());
        }));
        assertEquals(0, creations[0]);
        ICapabilityResolver<Direction> lazy = cache.getResolver(ChemicalHandlerManager.CHEMICAL_HANDLER, () -> {
            creations[0]++;
            return new CountingResolver(List.of(ChemicalHandlerManager.CHEMICAL_HANDLER));
        });
        assertEquals(1, creations[0]);
        assertSame(lazy, cache.getResolver(ChemicalHandlerManager.CHEMICAL_HANDLER, () -> {
            creations[0]++;
            return new CountingResolver(List.of());
        }));
        assertEquals(1, creations[0]);

        //Per identity+side invalidation dispatches the exact identity and side, nothing else
        cache.invalidate(ItemStorage.SIDED, Direction.NORTH);
        assertEquals(List.of(ItemStorage.SIDED.getId() + ":" + Direction.NORTH), resolver.invalidations);
        cache.invalidateAll(FluidStorage.SIDED);
        assertEquals(1, resolver.invalidateAllCalls);

        //Without a bound policy nothing is disabled; the bound component predicate decides, with a nullable side allowed
        assertFalse(cache.isCapabilityDisabled(ItemStorage.SIDED, Direction.NORTH));
        cache.addConfigComponent((capability, side) -> capability == ItemStorage.SIDED && side == Direction.NORTH);
        assertTrue(cache.isCapabilityDisabled(ItemStorage.SIDED, Direction.NORTH));
        assertFalse(cache.isCapabilityDisabled(ItemStorage.SIDED, null));
        assertFalse(cache.isCapabilityDisabled(FluidStorage.SIDED, Direction.NORTH));

        //Global invalidation hits each unique resolver once even when one resolver declared two identities
        cache.invalidateAll();
        assertEquals(2, resolver.invalidateAllCalls);
        assertEquals(1, ((CountingResolver) lazy).invalidateAllCalls);
    }

    @Test
    void boundPolicyGatesEagerProvidersAndTileInvalidationRevokesRealManagerViews() {
        CapabilityProbeTile tile = new CapabilityProbeTile(CapabilityProbeTile.furnaceType(), BlockPos.ZERO, CapabilityProbeTile.STATE);
        tile.bindItemNorthDisabledPolicy();
        tile.openCapabilityExposure();

        //The eager item provider consults the bound policy: NORTH is disabled, SOUTH exposes no containers, UP resolves a real transaction view
        assertNull(CapabilityTileEntity.ITEM_HANDLER_PROVIDER.apply(tile, Direction.NORTH));
        assertNull(CapabilityTileEntity.ITEM_HANDLER_PROVIDER.apply(tile, Direction.SOUTH));
        Storage<ItemVariant> exposed = CapabilityTileEntity.ITEM_HANDLER_PROVIDER.apply(tile, Direction.UP);
        assertInstanceOf(FabricItemStorage.class, exposed);
        assertTrue(exposed.supportsInsertion());

        //Per identity+side revocation through the tile surface: the retained view fails closed and the next resolution is a fresh view
        Storage<ItemVariant> retained = exposed;
        tile.invalidateCapability(ItemStorage.SIDED, Direction.UP);
        assertFalse(retained.supportsInsertion());
        Storage<ItemVariant> recreated = CapabilityTileEntity.ITEM_HANDLER_PROVIDER.apply(tile, Direction.UP);
        assertInstanceOf(FabricItemStorage.class, recreated);
        assertNotSame(retained, recreated);

        //The collection variant revokes only the matching identity+side and leaves other sides cached
        Storage<ItemVariant> otherSide = CapabilityTileEntity.ITEM_HANDLER_PROVIDER.apply(tile, Direction.NORTH);
        assertNull(otherSide, "the disabled policy still wins for NORTH");
        Storage<ItemVariant> up = CapabilityTileEntity.ITEM_HANDLER_PROVIDER.apply(tile, Direction.UP);
        tile.invalidateCapabilities(List.of(ItemStorage.SIDED, FluidStorage.SIDED), Direction.UP);
        assertFalse(up.supportsInsertion());

        //Identity-scoped whole-capability and whole-cache transitions revoke every retained view
        Storage<ItemVariant> upAgain = CapabilityTileEntity.ITEM_HANDLER_PROVIDER.apply(tile, Direction.UP);
        tile.invalidateCapabilitiesAll(List.of(ItemStorage.SIDED));
        assertFalse(upAgain.supportsInsertion());
        Storage<ItemVariant> upThird = CapabilityTileEntity.ITEM_HANDLER_PROVIDER.apply(tile, Direction.UP);
        tile.invalidateCapabilitiesFull();
        assertFalse(upThird.supportsInsertion());
        assertTrue(CapabilityTileEntity.ITEM_HANDLER_PROVIDER.apply(tile, Direction.UP).supportsInsertion(),
              "a live fresh view after full invalidation keeps working through the per-find provider");
    }

    @Test
    void exposureGenerationsGuardEagerAndLazyProvidersAcrossRemovalClearAndReopen() {
        //Eager provider path: a fresh generation is closed until the server load opens it
        CapabilityProbeTile tile = new CapabilityProbeTile(CapabilityProbeTile.furnaceType(), BlockPos.ZERO, CapabilityProbeTile.STATE);
        assertFalse(tile.canExposeCapabilities());
        assertNull(CapabilityTileEntity.ITEM_HANDLER_PROVIDER.apply(tile, Direction.UP));

        //Lazy provider path: the closed generation must not even create the lazy resolver
        int[] creations = {0};
        var lazyProvider = CapabilityTileEntity.capabilityProvider(HeatHandlerManager.HEAT_HANDLER, (probe, lookup) -> {
            creations[0]++;
            return new CountingResolver(List.of(HeatHandlerManager.HEAT_HANDLER));
        });
        assertNull(lazyProvider.apply(tile, Direction.UP));
        assertEquals(0, creations[0]);

        //The server load opens a fresh generation: both providers resolve, and the lazy resolver is created exactly once for repeated queries
        tile.openCapabilityExposure();
        assertTrue(tile.canExposeCapabilities());
        Storage<ItemVariant> exposed = CapabilityTileEntity.ITEM_HANDLER_PROVIDER.apply(tile, Direction.UP);
        assertTrue(exposed.supportsInsertion());
        Object lazyView = lazyProvider.apply(tile, Direction.UP);
        assertNotNull(lazyView, "the lazy provider must resolve through the lazily registered resolver");
        assertSame(lazyView, lazyProvider.apply(tile, Direction.UP));
        assertEquals(1, creations[0]);

        //Removal closes the generation before the super transition: providers fail closed and the retained alias stays revoked
        Storage<ItemVariant> retained = exposed;
        tile.setRemoved();
        assertFalse(tile.canExposeCapabilities());
        assertNull(CapabilityTileEntity.ITEM_HANDLER_PROVIDER.apply(tile, Direction.UP));
        assertNull(lazyProvider.apply(tile, Direction.UP));
        assertFalse(retained.supportsInsertion());

        //clearRemoved alone never reopens an unloaded generation; only the real server load path does
        tile.clearRemoved();
        assertFalse(tile.canExposeCapabilities(), "clearRemoved must not reopen a closed exposure generation");
        assertNull(CapabilityTileEntity.ITEM_HANDLER_PROVIDER.apply(tile, Direction.UP));
        tile.openCapabilityExposure();
        Storage<ItemVariant> fresh = CapabilityTileEntity.ITEM_HANDLER_PROVIDER.apply(tile, Direction.UP);
        assertInstanceOf(FabricItemStorage.class, fresh);
        assertTrue(fresh.supportsInsertion());
        assertFalse(retained.supportsInsertion(), "the old generation's retained alias must not rebound to the fresh owner");
    }
}
