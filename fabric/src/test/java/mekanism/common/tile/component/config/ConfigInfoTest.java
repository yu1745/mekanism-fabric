package mekanism.common.tile.component.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import mekanism.api.RelativeSide;
import mekanism.api.chemical.IChemicalTank;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.heat.IHeatCapacitor;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.capabilities.fluid.BasicFluidTank;
import mekanism.common.inventory.slot.BasicInventorySlot;
import mekanism.common.tile.component.config.slot.FluidSlotInfo;
import mekanism.common.tile.component.config.slot.IProxiedSlotInfo;
import mekanism.common.tile.component.config.slot.InventorySlotInfo;
import net.minecraft.SharedConstants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ConfigInfoTest {

    @BeforeAll
    static void bootstrapVanillaRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void sideDataTypeStateSupportedTypesAndDisabledSidePolicyPreserveOfficialSemantics() {
        ConfigInfo config = new ConfigInfo();
        // Upstream constructor seeds every RelativeSide with DataType.NONE (via SIDES == values()).
        for (RelativeSide side : RelativeSide.values()) {
            assertEquals(DataType.NONE, config.getDataType(side));
            assertTrue(config.isSideEnabled(side));
        }
        assertEquals(RelativeSide.values().length, config.getSideConfig().size());
        // Default eject state and IPersistentConfigInfo surface.
        assertTrue(config.canEject());
        assertFalse(config.isEjecting());
        config.setCanEject(false);
        config.setEjecting(true);
        assertFalse(config.canEject());
        assertTrue(config.isEjecting());

        // Before any slot info only NONE is supported; the lazy supported set is cached and grows incrementally.
        assertEquals(1, config.getSupportedDataTypes().size());
        assertTrue(config.supports(DataType.NONE));
        assertFalse(config.supports(DataType.INPUT));
        config.addSlotInfo(DataType.INPUT, new InventorySlotInfo(true, false));
        assertTrue(config.supports(DataType.INPUT));
        assertTrue(config.getSupportedDataTypes().contains(DataType.INPUT));

        // setDataType reports true only for a real change on an enabled side.
        assertTrue(config.setDataType(DataType.INPUT, RelativeSide.TOP));
        assertEquals(DataType.INPUT, config.getDataType(RelativeSide.TOP));
        assertFalse(config.setDataType(DataType.INPUT, RelativeSide.TOP));
        assertTrue(config.setDataType(DataType.OUTPUT, RelativeSide.TOP));
        assertEquals(DataType.OUTPUT, config.getDataType(RelativeSide.TOP));
        assertNull(config.getSlotInfo(RelativeSide.TOP));

        // Disabled sides are forced to NONE, reject updates, and stay disabled; other sides stay enabled.
        config.addDisabledSides(RelativeSide.BOTTOM);
        assertEquals(DataType.NONE, config.getDataType(RelativeSide.BOTTOM));
        assertFalse(config.isSideEnabled(RelativeSide.BOTTOM));
        assertFalse(config.setDataType(DataType.INPUT, RelativeSide.BOTTOM));
        assertEquals(DataType.NONE, config.getDataType(RelativeSide.BOTTOM));
        assertTrue(config.isSideEnabled(RelativeSide.TOP));

        // Increment/decrement move enabled sides only, and skip unsupported data types when wrapping.
        assertEquals(DataType.INPUT, config.incrementDataType(RelativeSide.LEFT));
        assertEquals(DataType.INPUT, config.getDataType(RelativeSide.LEFT));
        assertEquals(DataType.NONE, config.decrementDataType(RelativeSide.LEFT));
        assertEquals(DataType.NONE, config.getDataType(RelativeSide.LEFT));
        // From NONE decrement wraps backwards past every unsupported type straight to INPUT.
        assertEquals(DataType.INPUT, config.decrementDataType(RelativeSide.LEFT));
        // Disabled side keeps its current type and reports no change.
        assertEquals(DataType.NONE, config.decrementDataType(RelativeSide.BOTTOM));
        assertEquals(DataType.NONE, config.getDataType(RelativeSide.BOTTOM));
    }

    @Test
    void containerMappingsExposeConcreteInventorySlotsAndFluidTanksInInsertionOrder() {
        BasicInventorySlot firstSlot = BasicInventorySlot.at(null, 0, 0);
        BasicInventorySlot secondSlot = BasicInventorySlot.at(null, 1, 1);
        BasicFluidTank firstTank = BasicFluidTank.create(1_000, null);
        BasicFluidTank secondTank = BasicFluidTank.create(2_000, null);

        ConfigInfo config = new ConfigInfo();
        InventorySlotInfo inputInfo = new InventorySlotInfo(true, false, firstSlot, secondSlot);
        FluidSlotInfo outputInfo = new FluidSlotInfo(true, true, List.of(firstTank, secondTank));
        config.addSlotInfo(DataType.INPUT, inputInfo);
        config.addSlotInfo(DataType.OUTPUT, outputInfo);

        assertSame(inputInfo, config.getSlotInfo(DataType.INPUT));
        assertNull(config.getSlotInfo(RelativeSide.TOP));
        config.setDataType(DataType.INPUT, RelativeSide.TOP);
        assertSame(inputInfo, config.getSlotInfo(RelativeSide.TOP));
        assertEquals(List.of(DataType.INPUT), config.getDataTypeForContainer(firstSlot));
        assertEquals(List.of(DataType.INPUT), config.getDataTypeForContainer(secondSlot));
        assertEquals(List.of(DataType.OUTPUT), config.getDataTypeForContainer(firstTank));
        assertEquals(List.of(DataType.OUTPUT), config.getDataTypeForContainer(secondTank));

        // One container may back multiple data types; mapping keeps insertion order.
        config.addSlotInfo(DataType.INPUT_OUTPUT, new InventorySlotInfo(true, true, firstSlot));
        assertEquals(List.of(DataType.INPUT, DataType.INPUT_OUTPUT), config.getDataTypeForContainer(firstSlot));

        // Unknown containers get a fresh empty (mutable) list every call.
        Object unknown = new Object();
        List<DataType> empty = config.getDataTypeForContainer(unknown);
        assertTrue(empty.isEmpty());
        empty.add(DataType.OUTPUT);
        assertEquals(0, config.getDataTypeForContainer(unknown).size());
    }

    @Test
    void proxiedSlotSuppliersResolveLiveAndDataTypeWireFormatRoundTrips() {
        // Every domain proxy delegates to its supplier on each call; the backing list stays live.
        ArrayList<IInventorySlot> slots = new ArrayList<>();
        IProxiedSlotInfo.InventoryProxy inventoryProxy = new IProxiedSlotInfo.InventoryProxy(true, false, () -> slots);
        assertTrue(inventoryProxy.isEmpty());
        assertSame(slots, inventoryProxy.getSlots());
        slots.add(BasicInventorySlot.at(null, 0, 0));
        // A slot holding an EMPTY stack still counts as nothing-to-output.
        assertTrue(inventoryProxy.isEmpty());
        slots.get(0).setStack(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 2));
        assertFalse(inventoryProxy.isEmpty());
        slots.clear();
        assertTrue(inventoryProxy.isEmpty());
        assertTrue(inventoryProxy.canInput());
        assertFalse(inventoryProxy.canOutput());

        List<IExtendedFluidTank> tanks = new ArrayList<>();
        IProxiedSlotInfo.FluidProxy fluidProxy = new IProxiedSlotInfo.FluidProxy(false, true, () -> tanks);
        assertSame(tanks, fluidProxy.getTanks());
        assertTrue(fluidProxy.isEmpty());
        assertFalse(fluidProxy.canInput());
        assertTrue(fluidProxy.canOutput());

        List<IChemicalTank> chemicalTanks = new ArrayList<>();
        IProxiedSlotInfo.ChemicalProxy chemicalProxy = new IProxiedSlotInfo.ChemicalProxy(true, true, () -> chemicalTanks);
        assertSame(chemicalTanks, chemicalProxy.getTanks());

        List<IHeatCapacitor> capacitors = new ArrayList<>();
        IProxiedSlotInfo.HeatProxy heatProxy = new IProxiedSlotInfo.HeatProxy(false, false, () -> capacitors);
        assertSame(capacitors, heatProxy.getHeatCapacitors());
        assertFalse(heatProxy.isEnabled());

        List<IEnergyContainer> containers = new ArrayList<>();
        IProxiedSlotInfo.EnergyProxy energyProxy = new IProxiedSlotInfo.EnergyProxy(true, false, () -> containers);
        assertSame(containers, energyProxy.getContainers());
        assertTrue(energyProxy.isEmpty());

        // DataType stays wire-compatible: stream codec id round-trip, serialized-name codec, wrapped BY_ID, canOutput.
        for (DataType type : DataType.values()) {
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                DataType.STREAM_CODEC.encode(buffer, type);
                assertSame(type, DataType.STREAM_CODEC.decode(buffer));
            } finally {
                buffer.release();
            }
            assertEquals(new JsonPrimitive(type.getSerializedName()), DataType.CODEC.encodeStart(JsonOps.INSTANCE, type).getOrThrow());
            assertSame(type, DataType.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive(type.getSerializedName())).getOrThrow());
            assertSame(type, DataType.BY_ID.apply(type.ordinal()));
            assertEquals(type == DataType.OUTPUT || type == DataType.OUTPUT_1
                  || type == DataType.OUTPUT_2 || type == DataType.INPUT_OUTPUT, type.canOutput());
        }
        // BY_ID uses the WRAP strategy: out-of-range indexes fold back to the first constant.
        assertSame(DataType.NONE, DataType.BY_ID.apply(DataType.values().length));
    }
}
