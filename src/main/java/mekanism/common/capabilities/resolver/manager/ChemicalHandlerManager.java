package mekanism.common.capabilities.resolver.manager;

import mekanism.api.chemical.IChemicalHandler;
import mekanism.api.chemical.IChemicalTank;
import mekanism.api.chemical.ISidedChemicalHandler;
import mekanism.common.capabilities.holder.chemical.IChemicalTankHolder;
import mekanism.common.capabilities.proxy.ProxyChemicalHandler;
import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Helper class to make reading instead of having as messy generics
 *
 * <p>Fabric port: the Mekanism-owned chemical lookup identity replaces the official {@code Capabilities.CHEMICAL.block()} NeoForge capability token, keeping the
 * same {@code mekanism:chemical_handler} id and {@link IChemicalHandler} domain API. The official {@link ProxyChemicalHandler} creator, null-side read-only domain
 * proxy and retained-proxy invalidation semantics are unchanged.</p>
 */
public class ChemicalHandlerManager extends CapabilityHandlerManager<IChemicalTankHolder, IChemicalTank, IChemicalHandler, ISidedChemicalHandler> {

    /**
     * Sole Mekanism-owned lookup identity for the chemical domain API. Provider registration and block-entity attachment remain the obligation of the deferred
     * {@code CapabilityTileEntity}/{@code CapabilityCache} owner; resolving through any other identity returns no handler.
     */
    public static final BlockApiLookup<IChemicalHandler, Direction> CHEMICAL_HANDLER = BlockApiLookup.get(
          ResourceLocation.fromNamespaceAndPath("mekanism", "chemical_handler"), IChemicalHandler.class, Direction.class);

    public ChemicalHandlerManager(@Nullable IChemicalTankHolder holder, ISidedChemicalHandler baseHandler) {
        super(holder, baseHandler, CHEMICAL_HANDLER, ProxyChemicalHandler::new, IChemicalTankHolder::getTanks);
    }
}
