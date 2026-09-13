package mekanism.common.capabilities.resolver.manager;

import mekanism.api.heat.IHeatCapacitor;
import mekanism.api.heat.IHeatHandler;
import mekanism.api.heat.ISidedHeatHandler;
import mekanism.common.capabilities.holder.heat.IHeatCapacitorHolder;
import mekanism.common.capabilities.proxy.ProxyHeatHandler;
import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Helper class to make reading instead of having as messy generics
 *
 * <p>Fabric port: the Mekanism-owned heat lookup identity replaces the official {@code Capabilities.HEAT} NeoForge capability token, keeping the same
 * {@code mekanism:heat_handler} id and {@link IHeatHandler} domain API. The official {@link ProxyHeatHandler} creator, null-side read-only domain proxy and
 * retained-proxy invalidation semantics are unchanged.</p>
 */
public class HeatHandlerManager extends CapabilityHandlerManager<IHeatCapacitorHolder, IHeatCapacitor, IHeatHandler, ISidedHeatHandler> {

    /**
     * Sole Mekanism-owned lookup identity for the heat domain API. Provider registration and block-entity attachment remain the obligation of the deferred
     * {@code CapabilityTileEntity}/{@code CapabilityCache} owner; resolving through any other identity returns no handler.
     */
    public static final BlockApiLookup<IHeatHandler, Direction> HEAT_HANDLER = BlockApiLookup.get(
          ResourceLocation.fromNamespaceAndPath("mekanism", "heat_handler"), IHeatHandler.class, Direction.class);

    public HeatHandlerManager(@Nullable IHeatCapacitorHolder holder, @NotNull ISidedHeatHandler baseHandler) {
        super(holder, baseHandler, HEAT_HANDLER, ProxyHeatHandler::new, IHeatCapacitorHolder::getHeatCapacitors);
    }
}
