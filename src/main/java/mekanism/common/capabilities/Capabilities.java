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
import mekanism.api.security.IBlockSecurityUtils;
import mekanism.api.security.IEntitySecurityUtils;
import mekanism.common.Mekanism;
import mekanism.common.entity.EntityRobit;
import mekanism.common.integration.computer.ComputerCapabilityHelper;
import mekanism.common.integration.energy.EnergyCompatUtils;
import mekanism.common.lib.radiation.capability.RadiationEntity;
import mekanism.common.registries.MekanismEntityTypes;
import mekanism.common.tile.TileEntityBoundingBlock;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage;
import net.neoforged.neoforge.capabilities.Capabilities.FluidHandler;
import net.neoforged.neoforge.capabilities.Capabilities.ItemHandler;
import net.neoforged.neoforge.capabilities.EntityCapability;
import net.neoforged.neoforge.capabilities.ICapabilityProvider;
import net.neoforged.neoforge.capabilities.ItemCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

public class Capabilities {

    private Capabilities() {
    }

    public static final ICapabilityProvider<?, ?, ?> SIMPLE_PROVIDER = (obj, context) -> obj;

    private record FluidCapability(BlockCapability<IFluidHandler, @Nullable Direction> block,
                                   ItemCapability<IFluidHandlerItem, Void> item,
                                   EntityCapability<IFluidHandler, @Nullable Direction> entity) implements IMultiTypeCapability<IFluidHandler, IFluidHandlerItem> {
    }

    public static final MultiTypeCapability<IEnergyStorage> ENERGY = new MultiTypeCapability<>(EnergyStorage.BLOCK, EnergyStorage.ITEM, EnergyStorage.ENTITY);
    public static final IMultiTypeCapability<IFluidHandler, IFluidHandlerItem> FLUID = new FluidCapability(FluidHandler.BLOCK, FluidHandler.ITEM, FluidHandler.ENTITY);
    //Note: We intentionally don't use the entity automation capability, as we want to be able to target player inventories and the like
    public static final MultiTypeCapability<IItemHandler> ITEM = new MultiTypeCapability<>(ItemHandler.BLOCK, ItemHandler.ITEM, ItemHandler.ENTITY);

    public static final MultiTypeCapability<IChemicalHandler> CHEMICAL = new MultiTypeCapability<>(Mekanism.rl("chemical_handler"), IChemicalHandler.class);

    public static final net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup<IHeatHandler, Direction> HEAT = mekanism.common.capabilities.resolver.manager.HeatHandlerManager.HEAT_HANDLER;

    public static final MultiTypeCapability<IStrictEnergyHandler> STRICT_ENERGY = new MultiTypeCapability<>(Mekanism.rl("strict_energy_handler"), IStrictEnergyHandler.class);

    public static final net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup<IConfigurable, Direction> CONFIGURABLE = mekanism.fabric.tile.MekanismBlockLookups.CONFIGURABLE;

    public static final BlockCapability<IAlloyInteraction, @Nullable Direction> ALLOY_INTERACTION = BlockCapability.createSided(Mekanism.rl("alloy_interaction"), IAlloyInteraction.class);

    public static final net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup<IConfigCardAccess, Direction> CONFIG_CARD = mekanism.fabric.tile.MekanismBlockLookups.CONFIG_CARD;

    public static final BlockCapability<IEvaporationSolar, Void> EVAPORATION_SOLAR = BlockCapability.createVoid(Mekanism.rl("evaporation_solar"), IEvaporationSolar.class);

    public static final BlockCapability<ILaserReceptor, @Nullable Direction> LASER_RECEPTOR = BlockCapability.createSided(Mekanism.rl("laser_receptor"), ILaserReceptor.class);

    public static final net.fabricmc.fabric.api.lookup.v1.item.ItemApiLookup<ILaserDissipation, Void> LASER_DISSIPATION = mekanism.fabric.gear.GearItemLookups.LASER_DISSIPATION;

    public static final net.fabricmc.fabric.api.lookup.v1.item.ItemApiLookup<IRadiationShielding, Void> RADIATION_SHIELDING = mekanism.fabric.gear.GearItemLookups.RADIATION_SHIELDING;

    public static final net.fabricmc.fabric.api.lookup.v1.entity.EntityApiLookup<IRadiationEntity, Void> RADIATION_ENTITY = mekanism.fabric.radiation.FabricRadiationAttachments.RADIATION_ENTITY;

    public static final ResourceLocation OWNER_OBJECT_NAME = Mekanism.rl("owner_object");
    public static final ResourceLocation SECURITY_OBJECT_NAME = Mekanism.rl("security_object");

    public static void registerProxyableCapabilities(RegisterCapabilitiesEvent event) {
        event.setProxyable(CHEMICAL.block());
        event.setProxyable(STRICT_ENERGY.block());
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        Mekanism.hooks.hookCapabilityRegistration(event);

        EntityType<EntityRobit> robitEntityType = MekanismEntityTypes.ROBIT.get();
        event.registerEntity(IEntitySecurityUtils.INSTANCE.ownerCapability(), robitEntityType, (robit, ctx) -> robit);
        event.registerEntity(IEntitySecurityUtils.INSTANCE.securityCapability(), robitEntityType, (robit, ctx) -> robit);
        EnergyCompatUtils.registerEntityCapabilities(event, robitEntityType, (robit, ctx) -> robit);

        mekanism.fabric.radiation.FabricRadiationAttachments.initialize();

        //The original public catalog forwards to the canonical native bounding provider owner.
        mekanism.fabric.registry.FabricMekanismBlockRegistries.registerBoundingCapabilities();
    }
}