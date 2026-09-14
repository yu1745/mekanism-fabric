package mekanism.fabric.item;

import java.util.function.Function;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.common.attachments.containers.chemical.ComponentBackedChemicalHandler;
import mekanism.common.attachments.containers.energy.ComponentBackedEnergyHandler;
import mekanism.common.attachments.containers.fluid.ComponentBackedFluidHandler;
import mekanism.common.attachments.containers.item.ComponentBackedItemHandler;
import mekanism.common.util.UnitDisplayUtils;
import mekanism.fabric.energy.FabricComponentEnergyStorage;
import mekanism.fabric.fluid.FabricComponentFluidStorage;
import net.fabricmc.fabric.api.lookup.v1.item.ItemApiLookup;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import team.reborn.energy.api.EnergyStorage;

/** Provider registration for the actual Mekanism item-component domains. No block lookup or physical-side adapter is reused for an item. */
public final class FabricComponentItemLookup {
    public static final ItemApiLookup<IChemicalHandler, Void> CHEMICAL = ItemApiLookup.get(
          ResourceLocation.fromNamespaceAndPath("mekanism", "chemical_handler"), IChemicalHandler.class, Void.class);

    private FabricComponentItemLookup() {
    }

    public static void registerEnergy(Item item, Function<ItemStack, ComponentBackedEnergyHandler> provider) {
        EnergyStorage.ITEM.registerForItems((stack, context) -> new ComponentItemContext<>(context, item, provider).read() == null ? null
              : new FabricComponentEnergyStorage(context, item, provider, UnitDisplayUtils.EnergyUnit.FORGE_ENERGY), item);
    }

    public static void registerItems(Item item, Function<ItemStack, ComponentBackedItemHandler> provider) {
        ItemStorage.ITEM.registerForItems((stack, context) -> new ComponentItemContext<>(context, item, provider).read() == null ? null
              : new FabricComponentItemStorage(context, item, provider), item);
    }

    public static void registerFluid(Item item, Function<ItemStack, ComponentBackedFluidHandler> provider) {
        FluidStorage.ITEM.registerForItems((stack, context) -> new ComponentItemContext<>(context, item, provider).read() == null ? null
              : new FabricComponentFluidStorage(context, item, provider), item);
    }

    public static void registerChemical(Item item, Function<ItemStack, ComponentBackedChemicalHandler> provider) {
        CHEMICAL.registerForItems((stack, ignored) -> provider.apply(stack), item);
    }
}
