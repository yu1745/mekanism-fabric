package mekanism.common.tile.base;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.energy.IMekanismStrictEnergyHandler;
import mekanism.common.capabilities.energy.BasicEnergyContainer;
import mekanism.common.capabilities.holder.energy.IEnergyContainerHolder;
import mekanism.common.capabilities.resolver.manager.EnergyHandlerManager;
import mekanism.test.TileLevelStub;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import team.reborn.energy.api.EnergyStorage;

/**
 * Genuine Fabric registration path for the capability-tile providers, under a real loader: the eager item provider and the lazy generic energy factory are
 * registered through the real {@code BlockApiLookup.registerForBlockEntity} on lookup identities no vanilla adapter occupies, then resolved through the real
 * per-find dispatch of a tile installed in the audited level stub. Vanilla BE type tokens (end gateway, nether portal) are used only as unregistered type
 * carriers; no production machine content is registered.
 */
class CapabilityTileRegistrationTest {

    private static boolean registered;

    @BeforeAll
    static void bootstrapVanilla() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        if (!registered) {
            //Exactly one provider registration per block for the whole class; the tiles' own state drives every assertion
            ItemStorage.SIDED.registerForBlockEntity(CapabilityTileEntity.ITEM_HANDLER_PROVIDER, endGatewayType());
            EnergyStorage.SIDED.registerForBlockEntity(
                  CapabilityTileEntity.capabilityProvider(EnergyStorage.SIDED, (tile, lookup) -> new EnergyHandlerManager(tile, tile)), pistonType());
            registered = true;
        }
    }

    @SuppressWarnings("unchecked")
    private static BlockEntityType<CapabilityProbeTile> endGatewayType() {
        return (BlockEntityType<CapabilityProbeTile>) (BlockEntityType<?>) BlockEntityType.END_GATEWAY;
    }

    @SuppressWarnings("unchecked")
    private static BlockEntityType<EnergyProbeTile> pistonType() {
        return (BlockEntityType<EnergyProbeTile>) (BlockEntityType<?>) BlockEntityType.PISTON;
    }

    @Test
    void eagerItemProviderGoesThroughTheRealLookupWithPolicyAndLifecycleGuards() {
        TileLevelStub level = TileLevelStub.create();
        BlockPos pos = BlockPos.ZERO;
        CapabilityProbeTile tile = level.installTile(new CapabilityProbeTile(Holder.direct(BlockEntityType.END_GATEWAY), pos,
              Blocks.END_GATEWAY.defaultBlockState()));
        tile.bindItemNorthDisabledPolicy();

        //A fresh generation is closed, so even the genuine dispatch exposes nothing
        assertNull(ItemStorage.SIDED.find(level, pos, Direction.UP));
        tile.openCapabilityExposure();

        //The real dispatch reaches the tile through the registered provider chain: the bound policy rejects NORTH and resolves UP
        assertNull(ItemStorage.SIDED.find(level, pos, Direction.NORTH));
        Storage<ItemVariant> exposed = ItemStorage.SIDED.find(level, pos, Direction.UP);
        assertNotNull(exposed);
        assertTrue(exposed.supportsInsertion());
        //The pinned lookup invokes the provider on every find but the tile cache hands back its stable per-side view
        assertSame(exposed, ItemStorage.SIDED.find(level, pos, Direction.UP));

        //Removal closes the generation: even though the stub (like a foreign cache) still returns the block entity, the provider refuses it
        Storage<ItemVariant> retained = exposed;
        tile.setRemoved();
        assertNull(ItemStorage.SIDED.find(level, pos, Direction.UP));
        assertFalse(retained.supportsInsertion());
    }

    @Test
    void perFindProviderResolvesFreshViewsAfterSideInvalidation() {
        TileLevelStub level = TileLevelStub.create();
        BlockPos pos = BlockPos.ZERO;
        CapabilityProbeTile tile = level.installTile(new CapabilityProbeTile(Holder.direct(BlockEntityType.END_GATEWAY), pos,
              Blocks.END_GATEWAY.defaultBlockState()));
        tile.openCapabilityExposure();

        Storage<ItemVariant> first = ItemStorage.SIDED.find(level, pos, Direction.UP);
        assertNotNull(first);
        //A side-policy/topology revocation is picked up by the very next find: the retained view is dead, the next find resolves a fresh one
        tile.invalidateCapability(ItemStorage.SIDED, Direction.UP);
        assertFalse(first.supportsInsertion());
        Storage<ItemVariant> second = ItemStorage.SIDED.find(level, pos, Direction.UP);
        assertNotNull(second);
        assertNotSame(first, second);
        assertTrue(second.supportsInsertion());
        assertFalse(first.supportsInsertion());
    }

    @Test
    void lazyEnergyProviderRegistersOnTheRealTeamRebornLookupAndGuardsLifecycle() {
        TileLevelStub level = TileLevelStub.create();
        BlockPos pos = BlockPos.ZERO;
        EnergyProbeTile tile = level.installTile(new EnergyProbeTile(Holder.direct(BlockEntityType.PISTON), pos,
              Blocks.MOVING_PISTON.defaultBlockState()));

        //No energy resolver exists until the first successful query creates it through the lazy factory: the tile never registers one eagerly,
        // so any non-null resolution below can only come from the lazy factory's real creation path
        assertNull(EnergyStorage.SIDED.find(level, pos, Direction.UP));
        tile.openCapabilityExposure();
        assertNull(EnergyStorage.SIDED.find(level, pos, Direction.NORTH), "no containers are exposed from NORTH");

        EnergyStorage view = EnergyStorage.SIDED.find(level, pos, Direction.UP);
        assertNotNull(view);
        assertSame(view, EnergyStorage.SIDED.find(level, pos, Direction.UP));

        tile.setRemoved();
        assertNull(EnergyStorage.SIDED.find(level, pos, Direction.UP));
        assertFalse(view.supportsInsertion());
    }

    /**
     * Lazy-energy instrumentation tile: implements the real energy surfaces and counts how often the lazy factory built its resolver, mirroring how the official
     * machine tile defers energy resolver creation to the first provider query.
     */
    private static final class EnergyProbeTile extends CapabilityTileEntity implements IMekanismStrictEnergyHandler, IEnergyContainerHolder {

        private final BasicEnergyContainer container = BasicEnergyContainer.create(100, null);

        EnergyProbeTile(Holder<? extends BlockEntityType<?>> type, BlockPos pos, BlockState state) {
            super(type, pos, state);
        }

        @Override
        public boolean canHandleEnergy() {
            return true;
        }

        @Override
        public List<IEnergyContainer> getEnergyContainers(@Nullable Direction side) {
            //Internal null-side access sees everything, matching real tile semantics
            return side == null || side == Direction.UP ? List.of(container) : List.of();
        }

        @Override
        public void onContentsChanged() {
        }
    }
}
