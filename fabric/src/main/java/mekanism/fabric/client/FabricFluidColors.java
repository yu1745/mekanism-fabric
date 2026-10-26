package mekanism.fabric.client;

import mekanism.api.fluid.FluidStack;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandler;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry;

/**
 * Client-only fluid tint lookup. NeoForge exposes per-fluid client extensions ({@code IClientFluidTypeExtensions}) that the
 * official code queries with a bare {@code FluidStack}; Fabric's equivalent is the registered render handler, whose
 * {@code getFluidColor} default returns {@code -1} when the fluid has no color. Callers must only reach this class behind a
 * physical-client check so dedicated servers never load the client rendering API.
 */
public final class FabricFluidColors {

    /** Moonlight/material tint value meaning "no color supplied", matched to the officially absent client extension. */
    public static final int NO_TINT = -1;

    private FabricFluidColors() {
    }

    public static int getTintColor(FluidStack stack) {
        FluidRenderHandler handler = FluidRenderHandlerRegistry.INSTANCE.get(stack.getFluid());
        if (handler == null) {
            return NO_TINT;
        }
        //No world/position: the officially queried stack-only extension also had no level context, so biome-tinted
        // fluids fall back to their handler's context-free default color.
        return handler.getFluidColor(null, null, stack.getFluid().defaultFluidState());
    }
}
