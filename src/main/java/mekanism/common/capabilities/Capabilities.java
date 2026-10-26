package mekanism.common.capabilities;

import mekanism.api.IAlloyInteraction;
import mekanism.api.IConfigCardAccess;
import mekanism.api.IConfigurable;
import mekanism.api.IEvaporationSolar;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.api.energy.IStrictEnergyHandler;
import mekanism.api.heat.IHeatHandler;
import mekanism.api.lasers.ILaserDissipation;
import mekanism.api.lasers.ILaserReceptor;
import mekanism.api.radiation.capability.IRadiationEntity;
import mekanism.api.radiation.capability.IRadiationShielding;
import mekanism.common.capabilities.resolver.manager.ChemicalHandlerManager;
import mekanism.common.capabilities.resolver.manager.HeatHandlerManager;
import mekanism.common.content.evaporation.EvaporationMultiblockData;
import mekanism.fabric.gear.GearItemLookups;
import mekanism.fabric.radiation.FabricRadiationAttachments;
import mekanism.fabric.tile.MekanismBlockLookups;
import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup;
import net.fabricmc.fabric.api.lookup.v1.entity.EntityApiLookup;
import net.fabricmc.fabric.api.lookup.v1.item.ItemApiLookup;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

/**
 * Fabric port of the official capability catalog: the single source of the lookup identities used by this port.
 *
 * <p>Official Mekanism declared three NeoForge tokens per multi-type domain ({@code mekanism:chemical_handler} as block,
 * item and entity capability). This port keeps the official domain ids and member names, and composes each member from the
 * real Fabric identities that Task049 established: Mekanism-owned {@link BlockApiLookup}s for the domain APIs that Fabric
 * has no ecosystem-wide equivalent for ({@code chemical_handler}, {@code heat_handler}, {@code alloy_interaction},
 * {@code laser_receptor}, {@code evaporation_solar}, {@code configurable}, {@code config_card}), ecosystem Team Reborn /
 * Fabric transfer identities for the external energy, fluid and item domains ({@code EnergyStorage},
 * {@code FluidStorage}, {@code ItemStorage}), and the already-owned gear and radiation item/entity identities. Fabric
 * interns lookups by {@code (id, apiClass, contextClass)}, so members that name an id owned elsewhere in the port resolve
 * to that exact same instance instead of a duplicate identity.</p>
 *
 * <p>There is no {@code ENERGY} member: the external energy domain is Team Reborn's {@code EnergyStorage} and every
 * consumer resolves it through {@code FabricEnergyLookup}, which also owns the Mekanism entity leg; no strict-energy
 * external identity is invented (see {@code EnergyHandlerManager}). Capability provider registration is owned by the
 * platform bootstrap ({@code FabricMekanismBlockRegistries} and the item/energy lookup owners), not by this catalog.</p>
 */
public final class Capabilities {

    private Capabilities() {
    }

    // ---- Block-identity lookups handed directly to WorldUtils.getCapability and to block-entity type builders ----

    public static final BlockApiLookup<IHeatHandler, Direction> HEAT = HeatHandlerManager.HEAT_HANDLER;

    public static final BlockApiLookup<IConfigurable, Direction> CONFIGURABLE = MekanismBlockLookups.CONFIGURABLE;

    public static final BlockApiLookup<IConfigCardAccess, Direction> CONFIG_CARD = MekanismBlockLookups.CONFIG_CARD;

    public static final BlockApiLookup<IAlloyInteraction, Direction> ALLOY_INTERACTION = BlockApiLookup.get(
          id("alloy_interaction"), IAlloyInteraction.class, Direction.class);

    public static final BlockApiLookup<ILaserReceptor, Direction> LASER_RECEPTOR = BlockApiLookup.get(
          id("laser_receptor"), ILaserReceptor.class, Direction.class);

    public static final BlockApiLookup<IEvaporationSolar, Void> EVAPORATION_SOLAR = EvaporationMultiblockData.EVAPORATION_SOLAR;

    // ---- Item/entity identities owned by the gear and radiation verticals ----

    public static final ItemApiLookup<ILaserDissipation, Void> LASER_DISSIPATION = GearItemLookups.LASER_DISSIPATION;

    public static final ItemApiLookup<IRadiationShielding, Void> RADIATION_SHIELDING = GearItemLookups.RADIATION_SHIELDING;

    public static final EntityApiLookup<IRadiationEntity, Void> RADIATION_ENTITY = FabricRadiationAttachments.RADIATION_ENTITY;

    // ---- External storage domains (Fabric transfer) ----

    public static final ExternalStorageCapability<Storage<FluidVariant>> FLUID = new ExternalStorageCapability<>(FluidStorage.SIDED, FluidStorage.ITEM);

    public static final ExternalStorageCapability<Storage<ItemVariant>> ITEM = new ExternalStorageCapability<>(ItemStorage.SIDED, ItemStorage.ITEM);

    // ---- Mekanism-owned same-id multi-type domains ----

    public static final MultiTypeCapability<IChemicalHandler> CHEMICAL = new MultiTypeCapability<>(
          ChemicalHandlerManager.CHEMICAL_HANDLER,
          ItemApiLookup.get(id("chemical_handler"), IChemicalHandler.class, Void.class));

    public static final MultiTypeCapability<IStrictEnergyHandler> STRICT_ENERGY = new MultiTypeCapability<>(
          id("strict_energy_handler"), IStrictEnergyHandler.class);

    public static final ResourceLocation OWNER_OBJECT_NAME = id("owner_object");

    public static final ResourceLocation SECURITY_OBJECT_NAME = id("security_object");

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("mekanism", path);
    }
}
