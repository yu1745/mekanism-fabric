package mekanism.fabric.registry;

import mekanism.api.security.IBlockSecurityUtils;
import mekanism.common.block.BlockBounding;
import mekanism.common.capabilities.resolver.manager.ChemicalHandlerManager;
import mekanism.common.capabilities.resolver.manager.HeatHandlerManager;
import mekanism.common.integration.computer.ComputerCapabilityHelper;
import mekanism.common.registration.impl.BlockDeferredRegister;
import mekanism.common.registration.impl.BlockRegistryObject;
import mekanism.common.registration.impl.TileEntityTypeDeferredRegister;
import mekanism.common.registration.impl.TileEntityTypeRegistryObject;
import mekanism.common.tile.TileEntityBoundingBlock;
import mekanism.fabric.tile.MekanismBlockLookups;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import team.reborn.energy.api.EnergyStorage;

/** Shared block/item/type declarations. Original catalogs alias these same registers and bounding holders. */
public final class FabricMekanismBlockRegistries {

    public static final BlockDeferredRegister BLOCKS = new BlockDeferredRegister("mekanism");
    public static final TileEntityTypeDeferredRegister TILE_ENTITY_TYPES = new TileEntityTypeDeferredRegister("mekanism");
    public static final BlockRegistryObject<BlockBounding, BlockItem> BOUNDING_BLOCK = BLOCKS.register("bounding_block", BlockBounding::new);
    public static final TileEntityTypeRegistryObject<TileEntityBoundingBlock> BOUNDING_BLOCK_ENTITY =
          TILE_ENTITY_TYPES.builder(BOUNDING_BLOCK, TileEntityBoundingBlock::new).build();
    private static boolean initialized;
    private static boolean boundingProxiesRegistered;

    private FabricMekanismBlockRegistries() {
    }

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        //Applicable content catalogs declare into these registers before this single native commit/freeze window.
        ComputerCapabilityHelper.initialize();
        BLOCKS.registerAll(BuiltInRegistries.BLOCK, BuiltInRegistries.ITEM);
        TILE_ENTITY_TYPES.registerAll(BuiltInRegistries.BLOCK_ENTITY_TYPE);
        registerBoundingCapabilities();
        initialized = true;
    }

    /** Also used by the original capability catalog; registrations remain one-shot on the same native lookup identities. */
    public static synchronized void registerBoundingCapabilities() {
        if (boundingProxiesRegistered) {
            return;
        }
        TileEntityBoundingBlock.alwaysProxyCapability(MekanismBlockLookups.CONFIG_CARD);
        TileEntityBoundingBlock.alwaysProxyCapability(IBlockSecurityUtils.INSTANCE.ownerCapability());
        TileEntityBoundingBlock.alwaysProxyCapability(IBlockSecurityUtils.INSTANCE.securityCapability());
        ComputerCapabilityHelper.addBoundingComputerCapabilities();
        TileEntityBoundingBlock.proxyCapability(ItemStorage.SIDED);
        TileEntityBoundingBlock.proxyCapability(EnergyStorage.SIDED);
        TileEntityBoundingBlock.proxyCapability(FluidStorage.SIDED);
        TileEntityBoundingBlock.proxyCapability(ChemicalHandlerManager.CHEMICAL_HANDLER);
        TileEntityBoundingBlock.proxyCapability(HeatHandlerManager.HEAT_HANDLER);
        boundingProxiesRegistered = true;
    }
}
