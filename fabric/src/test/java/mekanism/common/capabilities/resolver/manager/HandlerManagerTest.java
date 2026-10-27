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
import mekanism.api.energy.IStrictEnergyHandler;
import mekanism.api.fluid.FluidStack;
import mekanism.api.fluid.IExtendedFluidHandler;
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
import mekanism.common.capabilities.proxy.ProxyFluidHandler;
import mekanism.common.capabilities.proxy.ProxyHeatHandler;
import mekanism.common.capabilities.proxy.ProxyItemHandler;
import mekanism.common.capabilities.proxy.ProxyStrictEnergyHandler;
import mekanism.common.inventory.slot.BasicInventorySlot;
import mekanism.common.registries.MekanismChemicals;
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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
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

        //Explicit internal read-only domain access, separate from the external null-side rejection
        ISidedItemHandler readOnlyDomain = manager.getReadOnlyHandler();
        assertInstanceOf(ProxyItemHandler.class, readOnlyDomain);
        assertSame(readOnlyDomain, manager.getReadOnlyHandler());
        //Live reads go through the base handler on the internal null side
        assertEquals(2, readOnlyDomain.getSlots(null));
        assertEquals(ItemStack.EMPTY, readOnlyDomain.getStackInSlot(0, null));
        assertEquals(tile.slots.get(0).getLimit(ItemStack.EMPTY), readOnlyDomain.getSlotLimit(0, null));
        //Writes and transfers are denied without reaching the base handler
        ItemStack diamonds = new ItemStack(Items.DIAMOND, 4);
        assertSame(diamonds, readOnlyDomain.insertItem(0, diamonds, null, Action.EXECUTE));
        assertEquals(ItemStack.EMPTY, readOnlyDomain.extractItem(0, 4, null, Action.EXECUTE));
        readOnlyDomain.setStackInSlot(0, diamonds, null);
        assertEquals(0, tile.setStackInSlotCalls);

        //Relevant invalidation revokes the cached read-only domain view and replaces it with a fresh live view
        manager.invalidate(ItemStorage.SIDED, null);
        assertEquals(0, readOnlyDomain.getSlots(null));
        assertEquals(0, readOnlyDomain.getSlotLimit(0, null));
        assertSame(diamonds, readOnlyDomain.insertItem(0, diamonds, null, Action.EXECUTE));
        assertEquals(0, tile.setStackInSlotCalls);
        ISidedItemHandler recreatedReadOnly = manager.getReadOnlyHandler();
        assertNotSame(readOnlyDomain, recreatedReadOnly);
        assertEquals(2, recreatedReadOnly.getSlots(null));
        //Wrong lookup identity invalidation must not revoke the unrelated read-only domain view
        manager.invalidate(FluidStorage.SIDED, null);
        assertEquals(2, recreatedReadOnly.getSlots(null));
        //invalidateAll revokes the cached read-only domain view alongside everything else
        manager.invalidateAll();
        assertEquals(0, recreatedReadOnly.getSlots(null));

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

        //Explicit internal read-only domain access, separate from the external null-side rejection
        IExtendedFluidHandler readOnlyDomain = manager.getReadOnlyHandler();
        assertInstanceOf(ProxyFluidHandler.class, readOnlyDomain);
        assertSame(readOnlyDomain, manager.getReadOnlyHandler());
        //Live reads go through the base handler on the internal null side
        assertEquals(1, readOnlyDomain.getTanks());
        assertEquals(FluidStack.EMPTY, readOnlyDomain.getFluidInTank(0));
        assertEquals(1_000, readOnlyDomain.getTankCapacity(0));
        //Writes and transfers are denied without reaching the base handler
        FluidStack water = new FluidStack(Fluids.WATER, 100);
        assertSame(water, readOnlyDomain.insertFluid(water, Action.EXECUTE));
        assertEquals(FluidStack.EMPTY, readOnlyDomain.extractFluid(100, Action.EXECUTE));
        readOnlyDomain.setFluidInTank(0, water);
        assertEquals(0, tile.setFluidInTankCalls);

        manager.invalidateAll();
        assertFalse(view.supportsInsertion());
        assertFalse(view.supportsExtraction());
        //The cached read-only domain view was revoked alongside everything else
        assertEquals(0, readOnlyDomain.getTanks());
        assertEquals(0, readOnlyDomain.getTankCapacity(0));
        assertSame(water, readOnlyDomain.insertFluid(water, Action.EXECUTE));
        assertEquals(0, tile.setFluidInTankCalls);
        Storage<FluidVariant> recreated = manager.resolve(FluidStorage.SIDED, Direction.NORTH);
        assertNotSame(view, recreated);
        IExtendedFluidHandler recreatedReadOnly = manager.getReadOnlyHandler();
        assertNotSame(readOnlyDomain, recreatedReadOnly);
        assertEquals(1, recreatedReadOnly.getTanks());
    }

    @Test
    void energyManagerWrapsJoulesDomainIntoForgeEnergyViewsAndRevokesRetainedViewsOnInvalidation() {
        EnergyTile tile = new EnergyTile(Direction.NORTH);
        EnergyHandlerManager manager = new EnergyHandlerManager(tile, tile);
        assertTrue(manager.canHandle());
        assertEquals(List.of(tile.container), manager.getContainers(Direction.NORTH));
        assertTrue(manager.getContainers(Direction.SOUTH).isEmpty());

        //Native strict transfer must work at exact Joule precision even when external FE is disabled.
        var oldPolicy = mekanism.common.config.ChemicalConfigAccess.serverPolicy();
        try {
            mekanism.common.config.ChemicalConfigAccess.publishServerPolicy(new mekanism.common.config.ChemicalConfigAccess.ServerPolicy(true, 2.5, true));
            assertFalse(UnitDisplayUtils.EnergyUnit.FORGE_ENERGY.isEnabled());
            assertNull(manager.resolve(EnergyStorage.SIDED, Direction.NORTH));
            IStrictEnergyHandler strict = manager.resolve(EnergyHandlerManager.STRICT_ENERGY, Direction.NORTH);
            assertNotSame(null, strict);
            assertSame(strict, manager.resolve(EnergyHandlerManager.STRICT_ENERGY, Direction.NORTH));
            assertNull(manager.resolve(EnergyHandlerManager.STRICT_ENERGY, Direction.SOUTH));
            assertEquals(0, strict.insertEnergy(1, Action.EXECUTE));
            assertEquals(1, tile.container.getEnergy());
            manager.invalidate(EnergyHandlerManager.STRICT_ENERGY, Direction.NORTH);
            assertEquals(1, strict.insertEnergy(1, Action.EXECUTE));
            assertEquals(1, tile.container.getEnergy());
            tile.container.setEnergy(0);
        } finally {
            mekanism.common.config.ChemicalConfigAccess.publishServerPolicy(oldPolicy);
        }

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

        //Explicit internal read-only Joules-domain access, separate from the external null-side rejection
        IStrictEnergyHandler readOnlyDomain = manager.getReadOnlyHandler();
        assertInstanceOf(ProxyStrictEnergyHandler.class, readOnlyDomain);
        assertSame(readOnlyDomain, manager.getReadOnlyHandler());
        //Live reads stay in exact Joules with no FE conversion roundtrip
        assertEquals(1, readOnlyDomain.getEnergyContainerCount());
        assertEquals(tile.container.getEnergy(), readOnlyDomain.getEnergy(0));
        assertEquals(tile.container.getMaxEnergy(), readOnlyDomain.getMaxEnergy(0));
        assertEquals(tile.container.getNeeded(), readOnlyDomain.getNeededEnergy(0));
        //Writes and transfers are denied without reaching the base handler
        readOnlyDomain.setEnergy(0, 50);
        assertEquals(UnitDisplayUtils.EnergyUnit.FORGE_ENERGY.convertFrom(4), tile.container.getEnergy());
        assertEquals(5, readOnlyDomain.insertEnergy(0, 5, Action.EXECUTE));
        assertEquals(5, readOnlyDomain.insertEnergy(5, Action.EXECUTE));
        assertEquals(0, readOnlyDomain.extractEnergy(0, 5, Action.EXECUTE));
        assertEquals(0, readOnlyDomain.extractEnergy(5, Action.EXECUTE));
        assertEquals(UnitDisplayUtils.EnergyUnit.FORGE_ENERGY.convertFrom(4), tile.container.getEnergy());

        //Relevant invalidation revokes the cached read-only domain view and replaces it with a fresh live view
        manager.invalidate(EnergyStorage.SIDED, null);
        assertEquals(0, readOnlyDomain.getEnergyContainerCount());
        assertEquals(0, readOnlyDomain.getEnergy(0));
        assertEquals(5, readOnlyDomain.insertEnergy(0, 5, Action.EXECUTE));
        assertEquals(UnitDisplayUtils.EnergyUnit.FORGE_ENERGY.convertFrom(4), tile.container.getEnergy());
        IStrictEnergyHandler recreatedReadOnly = manager.getReadOnlyHandler();
        assertNotSame(readOnlyDomain, recreatedReadOnly);
        assertEquals(tile.container.getEnergy(), recreatedReadOnly.getEnergy(0));
        //Wrong lookup identity invalidation must not revoke the unrelated read-only domain view
        manager.invalidate(ItemStorage.SIDED, null);
        assertEquals(tile.container.getEnergy(), recreatedReadOnly.getEnergy(0));
        //invalidateAll revokes the cached read-only domain view alongside everything else
        manager.invalidateAll();
        assertEquals(0, recreatedReadOnly.getEnergy(0));
        assertEquals(0, recreatedReadOnly.getEnergyContainerCount());

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

        //Live proxies actually consult the owner for reads and mutations before invalidation
        assertEquals(1, sideProxy.getChemicalTanks());
        assertEquals(ChemicalStack.EMPTY, sideProxy.getChemicalInTank(0));
        assertEquals(1, chemicalTile.getChemicalInTankCalls);
        sideProxy.setChemicalInTank(0, ChemicalStack.EMPTY);
        assertEquals(1, chemicalTile.setChemicalInTankCalls);
        ChemicalStack insertProbe = new ChemicalStack(MekanismChemicals.HYDROGEN.get(), 10);
        assertEquals(ChemicalStack.EMPTY, sideProxy.insertChemical(0, insertProbe, Action.EXECUTE));
        assertEquals(1, chemicalTile.insertChemicalCalls);
        assertEquals(ChemicalStack.EMPTY, sideProxy.extractChemical(0, 10, Action.EXECUTE));
        assertEquals(1, chemicalTile.extractChemicalCalls);

        //Wrong lookup identity invalidation must not revoke the unrelated cached views
        chemicalManager.invalidate(HeatHandlerManager.HEAT_HANDLER, Direction.NORTH);
        chemicalManager.invalidate(ItemStorage.SIDED, null);
        assertSame(sideProxy, chemicalManager.resolve(ChemicalHandlerManager.CHEMICAL_HANDLER, Direction.NORTH));
        assertEquals(ChemicalStack.EMPTY, sideProxy.getChemicalInTank(0));
        assertEquals(2, chemicalTile.getChemicalInTankCalls);

        //A real invalidation revokes the retained side proxy before dropping its cache entry
        chemicalManager.invalidate(ChemicalHandlerManager.CHEMICAL_HANDLER, Direction.NORTH);
        assertEquals(0, sideProxy.getChemicalTanks());
        assertEquals(0, sideProxy.getChemicalTankCapacity(0));
        assertEquals(ChemicalStack.EMPTY, sideProxy.getChemicalInTank(0));
        assertEquals(2, chemicalTile.getChemicalInTankCalls);
        sideProxy.setChemicalInTank(0, ChemicalStack.EMPTY);
        assertEquals(1, chemicalTile.setChemicalInTankCalls);
        assertSame(insertProbe, sideProxy.insertChemical(0, insertProbe, Action.EXECUTE));
        assertEquals(ChemicalStack.EMPTY, sideProxy.extractChemical(0, 10, Action.EXECUTE));
        assertEquals(1, chemicalTile.insertChemicalCalls);
        assertEquals(1, chemicalTile.extractChemicalCalls);
        //The cache entry was dropped and a fresh live proxy replaces the revoked one
        IChemicalHandler freshSideProxy = chemicalManager.resolve(ChemicalHandlerManager.CHEMICAL_HANDLER, Direction.NORTH);
        assertNotSame(sideProxy, freshSideProxy);
        assertEquals(1, freshSideProxy.getChemicalTanks());

        //The null-side read-only proxy is revoked by its own relevant invalidation
        chemicalManager.invalidate(ChemicalHandlerManager.CHEMICAL_HANDLER, null);
        assertEquals(0, readOnlyProxy.getChemicalTanks());
        assertEquals(ChemicalStack.EMPTY, readOnlyProxy.getChemicalInTank(0));
        assertEquals(2, chemicalTile.getChemicalInTankCalls);
        readOnlyProxy.setChemicalInTank(0, ChemicalStack.EMPTY);
        assertEquals(1, chemicalTile.setChemicalInTankCalls);
        assertNotSame(readOnlyProxy, chemicalManager.resolve(ChemicalHandlerManager.CHEMICAL_HANDLER, null));

        chemicalManager.invalidateAll();
        assertEquals(0, freshSideProxy.getChemicalTanks());

        HeatTile heatTile = new HeatTile();
        HeatHandlerManager heatManager = new HeatHandlerManager(heatTile, heatTile);
        assertTrue(heatManager.canHandle());
        IHeatHandler heatProxy = heatManager.resolve(HeatHandlerManager.HEAT_HANDLER, Direction.NORTH);
        assertInstanceOf(ProxyHeatHandler.class, heatProxy);
        //Null-side read-only domain view: reads stay live, writes are gated without reaching the owner
        IHeatHandler readOnlyHeat = heatManager.resolve(HeatHandlerManager.HEAT_HANDLER, null);
        assertInstanceOf(ProxyHeatHandler.class, readOnlyHeat);
        assertEquals(1, readOnlyHeat.getHeatCapacitorCount());
        assertEquals(300, readOnlyHeat.getTemperature(0));
        readOnlyHeat.handleHeat(0, 5);
        assertEquals(0, heatTile.handleHeatCalls);
        //Live side proxy actually consults the owner
        assertEquals(300, heatProxy.getTemperature(0));
        heatProxy.handleHeat(0, 5);
        assertEquals(1, heatTile.handleHeatCalls);
        assertNull(heatManager.resolve(ChemicalHandlerManager.CHEMICAL_HANDLER, Direction.NORTH));
        assertNull(heatManager.resolve(null, Direction.NORTH));

        //Wrong lookup identity invalidation must not revoke the unrelated cached heat views
        heatManager.invalidate(ChemicalHandlerManager.CHEMICAL_HANDLER, Direction.NORTH);
        assertSame(heatProxy, heatManager.resolve(HeatHandlerManager.HEAT_HANDLER, Direction.NORTH));
        assertEquals(300, heatProxy.getTemperature(0));
        //A real invalidation revokes the retained heat proxy: no owner reads, no owner mutations
        heatManager.invalidate(HeatHandlerManager.HEAT_HANDLER, Direction.NORTH);
        assertEquals(0, heatProxy.getHeatCapacitorCount());
        assertEquals(0, heatProxy.getTemperature(0));
        assertEquals(0, heatProxy.getTotalTemperature());
        heatProxy.handleHeat(0, 5);
        heatProxy.handleHeat(5);
        assertEquals(1, heatTile.handleHeatCalls);
        //The null-side read-only proxy is revoked alongside its own invalidation and invalidateAll
        heatManager.invalidate(HeatHandlerManager.HEAT_HANDLER, null);
        assertEquals(0, readOnlyHeat.getHeatCapacitorCount());
        assertEquals(0, readOnlyHeat.getTemperature(0));
        heatManager.invalidateAll();
        IHeatHandler freshHeatProxy = heatManager.resolve(HeatHandlerManager.HEAT_HANDLER, Direction.NORTH);
        assertNotSame(heatProxy, freshHeatProxy);
        assertEquals(300, freshHeatProxy.getTemperature(0));
    }

    private static final class ItemTile implements IMekanismInventory, IInventorySlotHolder {
        private final List<IInventorySlot> slots = List.of(BasicInventorySlot.at(null, 0, 0), BasicInventorySlot.at(null, 1, 1));
        private final Set<Direction> exposed = EnumSet.of(Direction.NORTH, Direction.UP);
        private int setStackInSlotCalls;

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

        //The remaining ISidedItemHandler surface backs the internal read-only domain view
        @Override
        public void setStackInSlot(int slot, ItemStack stack, @Nullable Direction side) {
            setStackInSlotCalls++;
        }

        @Override
        public int getSlots(@Nullable Direction side) {
            return getInventorySlots(side).size();
        }

        @Override
        public ItemStack getStackInSlot(int slot, @Nullable Direction side) {
            return getInventorySlots(side).get(slot).getStack();
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
            return getInventorySlots(side).get(slot).getLimit(ItemStack.EMPTY);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack, @Nullable Direction side) {
            return true;
        }
    }

    private static final class FluidTile implements IMekanismFluidHandler {
        private final IExtendedFluidTank tank = BasicFluidTank.create(1_000, null);
        private int setFluidInTankCalls;

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
            //Internal null-side access sees everything, matching real tile semantics
            return side == null || side == Direction.NORTH ? List.of(tank) : List.of();
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
            return getFluidTanks(side).get(tank).getFluid();
        }

        @Override
        public void setFluidInTank(int tank, FluidStack stack, @Nullable Direction side) {
            setFluidInTankCalls++;
        }

        @Override
        public int getTankCapacity(int tank, @Nullable Direction side) {
            return getFluidTanks(side).get(tank).getCapacity();
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack, @Nullable Direction side) {
            return true;
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
            //Internal null-side access sees everything, matching real tile semantics
            return side == null || side == exposedSide ? List.of(container) : List.of();
        }

        @Override
        public void onContentsChanged() {
        }
    }

    private static final class ChemicalTile implements ISidedChemicalHandler, IChemicalTankHolder {
        private final IChemicalTank tank = (IChemicalTank) BasicChemicalTank.createAllValid(1_000, null);
        private int getChemicalInTankCalls;
        private int setChemicalInTankCalls;
        private int insertChemicalCalls;
        private int extractChemicalCalls;

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
            getChemicalInTankCalls++;
            return ChemicalStack.EMPTY;
        }

        @Override
        public void setChemicalInTank(int tank, ChemicalStack stack, @Nullable Direction side) {
            setChemicalInTankCalls++;
        }

        @Override
        public long getChemicalTankCapacity(int tank, @Nullable Direction side) {
            return getTanks(side).get(tank).getCapacity();
        }

        @Override
        public boolean isValid(int tank, ChemicalStack stack, @Nullable Direction side) {
            return true;
        }

        @Override
        public ChemicalStack insertChemical(int tank, ChemicalStack stack, @Nullable Direction side, Action action) {
            insertChemicalCalls++;
            return ChemicalStack.EMPTY;
        }

        @Override
        public ChemicalStack extractChemical(int tank, long amount, @Nullable Direction side, Action action) {
            extractChemicalCalls++;
            return ChemicalStack.EMPTY;
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
