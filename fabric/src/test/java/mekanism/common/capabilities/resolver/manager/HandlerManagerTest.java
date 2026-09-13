package mekanism.common.capabilities.resolver.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.EnumSet;
import mekanism.api.Action;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.api.chemical.IChemicalTank;
import mekanism.api.chemical.ISidedChemicalHandler;
import mekanism.api.chemical.BasicChemicalTank;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.energy.IMekanismStrictEnergyHandler;
import mekanism.api.fluid.FluidStack;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.fluid.IMekanismFluidHandler;
import mekanism.api.fluid.ISidedFluidHandler;
import mekanism.api.heat.IHeatCapacitor;
import mekanism.api.heat.IHeatHandler;
import mekanism.api.heat.ISidedHeatHandler;
import mekanism.api.inventory.IInventorySlot;
import mekanism.api.inventory.IMekanismInventory;
import mekanism.api.inventory.ISidedItemHandler;
import mekanism.common.capabilities.heat.BasicHeatCapacitor;
import mekanism.common.capabilities.energy.BasicEnergyContainer;
import mekanism.common.capabilities.fluid.BasicFluidTank;
import mekanism.common.capabilities.holder.chemical.IChemicalTankHolder;
import mekanism.common.capabilities.holder.energy.IEnergyContainerHolder;
import mekanism.common.capabilities.holder.fluid.IFluidTankHolder;
import mekanism.common.capabilities.holder.heat.IHeatCapacitorHolder;
import mekanism.common.capabilities.holder.slot.IInventorySlotHolder;
import mekanism.common.capabilities.proxy.ProxyChemicalHandler;
import mekanism.common.capabilities.proxy.ProxyHeatHandler;
import mekanism.common.inventory.slot.BasicInventorySlot;
import mekanism.common.util.UnitDisplayUtils;
import mekanism.fabric.energy.FabricEnergyStorage;
import mekanism.fabric.fluid.FabricFluidStorage;
import mekanism.fabric.item.FabricItemStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import team.reborn.energy.api.EnergyStorage;

class HandlerManagerTest {

    @BeforeAll
    static void bootstrapVanilla() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        mekanism.test.ChemicalTestBootstrap.initialize();
    }

    @Test
    void itemManagerOwnsStableSideStorageViewsAndSeparatesNullInternalWrongIdentityAndEmptySides() {
        ItemTile tile = new ItemTile();
        ItemHandlerManager manager = new ItemHandlerManager(tile, tile);
        assertTrue(manager.canHandle());
        assertSame(tile, manager.getInternal());
        assertEquals(tile.slots, manager.getContainers(Direction.NORTH));
        assertTrue(manager.getContainers(Direction.SOUTH).isEmpty());
        //Internal null-side container access stays live even though no external view exists for it
        assertEquals(tile.slots, manager.getContainers(null));

        Storage<ItemVariant> view = manager.resolve(ItemStorage.SIDED, Direction.NORTH);
        assertInstanceOf(FabricItemStorage.class, view);
        assertSame(view, manager.resolve(ItemStorage.SIDED, Direction.NORTH));
        Storage<ItemVariant> otherSide = manager.resolve(ItemStorage.SIDED, Direction.UP);
        assertInstanceOf(FabricItemStorage.class, otherSide);
        assertNotSame(view, otherSide);
        //No visible containers from this side
        assertNull(manager.resolve(ItemStorage.SIDED, Direction.SOUTH));
        //The internal null side deliberately never exposes an external Storage
        assertNull(manager.resolve(ItemStorage.SIDED, null));
        //Wrong lookup identity is never cast into a different API
        assertNull(manager.resolve(FluidStorage.SIDED, Direction.NORTH));
        assertNull(manager.resolve(null, Direction.NORTH));

        Storage<ItemVariant> retained = view;
        manager.invalidate(ItemStorage.SIDED, Direction.NORTH);
        //The retained stable view is revoked, not merely dropped from the cache
        assertFalse(retained.supportsInsertion());
        assertFalse(retained.supportsExtraction());
        Storage<ItemVariant> recreated = manager.resolve(ItemStorage.SIDED, Direction.NORTH);
        assertInstanceOf(FabricItemStorage.class, recreated);
        assertNotSame(retained, recreated);

        ItemHandlerManager holderless = new ItemHandlerManager(null, tile);
        assertFalse(holderless.canHandle());
        assertTrue(holderless.getContainers(Direction.NORTH).isEmpty());
        assertNull(holderless.resolve(ItemStorage.SIDED, Direction.NORTH));
    }

    @Test
    void fluidManagerResolvesStableSideStorageViewsAndRevokesRetainedViewsOnInvalidationAll() {
        FluidTile tile = new FluidTile();
        FluidHandlerManager manager = new FluidHandlerManager(tile.holder(), tile);
        assertTrue(manager.canHandle());
        Storage<FluidVariant> view = manager.resolve(FluidStorage.SIDED, Direction.NORTH);
        assertInstanceOf(FabricFluidStorage.class, view);
        assertSame(view, manager.resolve(FluidStorage.SIDED, Direction.NORTH));
        assertNull(manager.resolve(FluidStorage.SIDED, null));
        assertNull(manager.resolve(FluidStorage.SIDED, Direction.SOUTH));
        assertNull(manager.resolve(ItemStorage.SIDED, Direction.NORTH));
        assertNull(manager.resolve(null, Direction.NORTH));

        manager.invalidateAll();
        assertFalse(view.supportsInsertion());
        assertFalse(view.supportsExtraction());
        Storage<FluidVariant> recreated = manager.resolve(FluidStorage.SIDED, Direction.NORTH);
        assertNotSame(view, recreated);
    }

    @Test
    void energyManagerWrapsJoulesDomainIntoForgeEnergyViewsAndRevokesRetainedViewsOnInvalidation() {
        EnergyTile tile = new EnergyTile(Direction.NORTH);
        EnergyHandlerManager manager = new EnergyHandlerManager(tile, tile);
        assertTrue(manager.canHandle());
        assertEquals(List.of(tile.container), manager.getContainers(Direction.NORTH));
        assertTrue(manager.getContainers(Direction.SOUTH).isEmpty());

        EnergyStorage view = manager.resolve(EnergyStorage.SIDED, Direction.NORTH);
        assertInstanceOf(FabricEnergyStorage.class, view);
        assertSame(view, manager.resolve(EnergyStorage.SIDED, Direction.NORTH));
        assertNull(manager.resolve(EnergyStorage.SIDED, null));
        assertNull(manager.resolve(EnergyStorage.SIDED, Direction.SOUTH));
        assertNull(manager.resolve(ItemStorage.SIDED, Direction.NORTH));
        assertNull(manager.resolve(null, Direction.NORTH));

        //Joules domain stays authoritative internally; the external view converts through the configured FE conversion
        try (Transaction transaction = Transaction.openOuter()) {
            assertEquals(2, view.insert(2, transaction));
            transaction.commit();
        }
        assertEquals(UnitDisplayUtils.EnergyUnit.FORGE_ENERGY.convertFrom(2), tile.container.getEnergy());
        try (Transaction transaction = Transaction.openOuter()) {
            //The external view reports in converted FE, not Joules
            assertEquals(UnitDisplayUtils.EnergyUnit.FORGE_ENERGY.convertTo(tile.container.getEnergy()), view.getAmount());
            assertEquals(UnitDisplayUtils.EnergyUnit.FORGE_ENERGY.convertTo(tile.container.getMaxEnergy()), view.getCapacity());
        }

        EnergyStorage retained = view;
        manager.invalidate(EnergyStorage.SIDED, Direction.NORTH);
        //Retained views fail closed and stop reading their old owner, but the Joules containers stay live
        assertFalse(retained.supportsInsertion());
        assertFalse(retained.supportsExtraction());
        try (Transaction transaction = Transaction.openOuter()) {
            assertEquals(0, retained.insert(2, transaction));
            assertEquals(0, retained.getAmount());
            transaction.commit();
        }
        assertEquals(List.of(tile.container), manager.getContainers(Direction.NORTH));

        EnergyStorage recreated = manager.resolve(EnergyStorage.SIDED, Direction.NORTH);
        assertInstanceOf(FabricEnergyStorage.class, recreated);
        assertNotSame(retained, recreated);
        try (Transaction transaction = Transaction.openOuter()) {
            assertEquals(2, recreated.insert(2, transaction));
            transaction.commit();
        }
        assertEquals(UnitDisplayUtils.EnergyUnit.FORGE_ENERGY.convertFrom(4), tile.container.getEnergy());

        EnergyHandlerManager holderless = new EnergyHandlerManager(null, tile);
        assertFalse(holderless.canHandle());
        assertTrue(holderless.getContainers(Direction.NORTH).isEmpty());
        assertNull(holderless.resolve(EnergyStorage.SIDED, Direction.NORTH));
    }

    @Test
    void chemicalAndHeatManagersPreserveOfficialDomainProxyShapesIncludingNullSideReadOnlyViews() {
        ChemicalTile chemicalTile = new ChemicalTile();
        ChemicalHandlerManager chemicalManager = new ChemicalHandlerManager(chemicalTile, chemicalTile);
        assertTrue(chemicalManager.canHandle());
        IChemicalHandler sideProxy = chemicalManager.resolve(ChemicalHandlerManager.CHEMICAL_HANDLER, Direction.NORTH);
        assertInstanceOf(ProxyChemicalHandler.class, sideProxy);
        assertSame(sideProxy, chemicalManager.resolve(ChemicalHandlerManager.CHEMICAL_HANDLER, Direction.NORTH));
        //Official null-side semantics preserved for the domain API: a distinct read-only proxy instead of null
        IChemicalHandler readOnlyProxy = chemicalManager.resolve(ChemicalHandlerManager.CHEMICAL_HANDLER, null);
        assertInstanceOf(ProxyChemicalHandler.class, readOnlyProxy);
        assertNotSame(sideProxy, readOnlyProxy);
        assertNull(chemicalManager.resolve(ChemicalHandlerManager.CHEMICAL_HANDLER, Direction.SOUTH));
        assertNull(chemicalManager.resolve(HeatHandlerManager.HEAT_HANDLER, Direction.NORTH));
        assertNull(chemicalManager.resolve(null, Direction.NORTH));
        chemicalManager.invalidateAll();
        assertNotSame(sideProxy, chemicalManager.resolve(ChemicalHandlerManager.CHEMICAL_HANDLER, Direction.NORTH));

        HeatTile heatTile = new HeatTile();
        HeatHandlerManager heatManager = new HeatHandlerManager(heatTile, heatTile);
        assertTrue(heatManager.canHandle());
        IHeatHandler heatProxy = heatManager.resolve(HeatHandlerManager.HEAT_HANDLER, Direction.NORTH);
        assertInstanceOf(ProxyHeatHandler.class, heatProxy);
        //Null-side read-only domain view: reads stay live, writes are gated without reaching the owner
        IHeatHandler readOnlyHeat = heatManager.resolve(HeatHandlerManager.HEAT_HANDLER, null);
        assertInstanceOf(ProxyHeatHandler.class, readOnlyHeat);
        assertEquals(1, readOnlyHeat.getHeatCapacitorCount());
        readOnlyHeat.handleHeat(0, 5);
        assertEquals(0, heatTile.handleHeatCalls);
        heatProxy.handleHeat(0, 5);
        assertEquals(1, heatTile.handleHeatCalls);
        assertNull(heatManager.resolve(ChemicalHandlerManager.CHEMICAL_HANDLER, Direction.NORTH));
        assertNull(heatManager.resolve(null, Direction.NORTH));
    }

    private static final class ItemTile implements IMekanismInventory, IInventorySlotHolder {
        private final List<IInventorySlot> slots = List.of(BasicInventorySlot.at(null, 0, 0), BasicInventorySlot.at(null, 1, 1));
        private final Set<Direction> exposed = EnumSet.of(Direction.NORTH, Direction.UP);

        @Override
        public boolean hasInventory() {
            return true;
        }

        @Override
        public List<IInventorySlot> getInventorySlots(@Nullable Direction side) {
            //Internal null-side access sees everything, matching real tile semantics
            return side == null || exposed.contains(side) ? slots : List.of();
        }

        @Override
        public void onContentsChanged() {
        }

        //The remaining ISidedItemHandler surface is never reached through the manager/expose path
        @Override
        public void setStackInSlot(int slot, ItemStack stack, @Nullable Direction side) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getSlots(@Nullable Direction side) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ItemStack getStackInSlot(int slot, @Nullable Direction side) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, @Nullable Direction side, Action action) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ItemStack extractItem(int slot, int amount, @Nullable Direction side, Action action) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getSlotLimit(int slot, @Nullable Direction side) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack, @Nullable Direction side) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class FluidTile implements IMekanismFluidHandler {
        private final IExtendedFluidTank tank = BasicFluidTank.create(1_000, null);

        @Nullable
        private IFluidTankHolder holder;

        //The real consumer supplies a distinct tank holder object; model that separation here
        IFluidTankHolder holder() {
            if (holder == null) {
                holder = new FluidHolder();
            }
            return holder;
        }

        @Override
        public boolean canHandleFluid() {
            return true;
        }

        @Override
        public List<IExtendedFluidTank> getFluidTanks(@Nullable Direction side) {
            return side == Direction.NORTH ? List.of(tank) : List.of();
        }

        @Override
        public void onContentsChanged() {
        }

        @Override
        public int getTanks(@Nullable Direction side) {
            return getFluidTanks(side).size();
        }

        @Override
        public FluidStack getFluidInTank(int tank, @Nullable Direction side) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setFluidInTank(int tank, FluidStack stack, @Nullable Direction side) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getTankCapacity(int tank, @Nullable Direction side) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack, @Nullable Direction side) {
            throw new UnsupportedOperationException();
        }

        @Override
        public FluidStack insertFluid(int tank, FluidStack stack, @Nullable Direction side, Action action) {
            throw new UnsupportedOperationException();
        }

        @Override
        public FluidStack extractFluid(int tank, int amount, @Nullable Direction side, Action action) {
            throw new UnsupportedOperationException();
        }

        private final class FluidHolder implements IFluidTankHolder {
            @Override
            public List<IExtendedFluidTank> getTanks(@Nullable Direction side) {
                return FluidTile.this.getFluidTanks(side);
            }
        }
    }

    private static final class EnergyTile implements IMekanismStrictEnergyHandler, IEnergyContainerHolder {
        private final BasicEnergyContainer container = BasicEnergyContainer.create(100, null);
        private final Direction exposedSide;

        private EnergyTile(Direction exposedSide) {
            this.exposedSide = exposedSide;
        }

        @Override
        public boolean canHandleEnergy() {
            return true;
        }

        @Override
        public List<IEnergyContainer> getEnergyContainers(@Nullable Direction side) {
            return side == exposedSide ? List.of(container) : List.of();
        }

        @Override
        public void onContentsChanged() {
        }
    }

    private static final class ChemicalTile implements ISidedChemicalHandler, IChemicalTankHolder {
        private final IChemicalTank tank = (IChemicalTank) BasicChemicalTank.createAllValid(1_000, null);

        @Override
        public List<IChemicalTank> getTanks(@Nullable Direction side) {
            return side == null || side == Direction.NORTH ? List.of(tank) : List.of();
        }

        @Override
        public int getCountChemicalTanks(@Nullable Direction side) {
            return getTanks(side).size();
        }

        @Override
        public ChemicalStack getChemicalInTank(int tank, @Nullable Direction side) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setChemicalInTank(int tank, ChemicalStack stack, @Nullable Direction side) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long getChemicalTankCapacity(int tank, @Nullable Direction side) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isValid(int tank, ChemicalStack stack, @Nullable Direction side) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChemicalStack insertChemical(int tank, ChemicalStack stack, @Nullable Direction side, Action action) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChemicalStack extractChemical(int tank, long amount, @Nullable Direction side, Action action) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class HeatTile implements ISidedHeatHandler, IHeatCapacitorHolder {
        private final IHeatCapacitor capacitor = BasicHeatCapacitor.create(100, null, null);
        private int handleHeatCalls;

        @Override
        public List<IHeatCapacitor> getHeatCapacitors(@Nullable Direction side) {
            return side == null || side == Direction.NORTH ? List.of(capacitor) : List.of();
        }

        @Override
        public int getHeatCapacitorCount(@Nullable Direction side) {
            return getHeatCapacitors(side).size();
        }

        @Override
        public double getTemperature(int capacitor, @Nullable Direction side) {
            return 300;
        }

        @Override
        public double getInverseConduction(int capacitor, @Nullable Direction side) {
            return 1;
        }

        @Override
        public double getHeatCapacity(int capacitor, @Nullable Direction side) {
            return 100;
        }

        @Override
        public void handleHeat(int capacitor, double transfer, @Nullable Direction side) {
            handleHeatCalls++;
        }
    }
}
