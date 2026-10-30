package mekanism.fabric.client;

import mekanism.api.fluid.FluidStack;
import mekanism.common.registration.impl.FluidDeferredRegister;
import mekanism.common.registration.impl.FluidDeferredRegister.MekanismFlowingFluid;
import mekanism.common.registration.impl.FluidDeferredRegister.MekanismFluidType;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandler;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry;
import net.fabricmc.fabric.api.client.render.fluid.v1.SimpleFluidRenderHandler;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.FastColor;
import org.jetbrains.annotations.Nullable;

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

    /** Registers the official textures, tint and translucent layer for each still/flowing fluid pair. */
    public static void register(FluidDeferredRegister fluids) {
        for (var fluid : fluids.getFluidRegistryObjects()) {
            var type = fluid.getFluidType();
            var still = fluid.get();
            var flowing = fluid.getFlowingFluid().get();
            FluidRenderHandlerRegistry.INSTANCE.register(still, flowing,
                  new SimpleFluidRenderHandler(type.stillTexture, type.flowingTexture, type.overlayTexture, type.color));
            BlockRenderLayerMap.INSTANCE.putFluids(RenderType.translucent(), still, flowing);
        }
    }

    /** The camera/eye fluid-height check used by the official client hooks, limited to Mekanism fluids. */
    @Nullable
    public static MekanismFluidType getSubmergedType(BlockGetter level, Vec3 eye) {
        BlockPos pos = BlockPos.containing(eye);
        var state = level.getFluidState(pos);
        if (state.getType() instanceof MekanismFlowingFluid fluid && eye.y < (double) ((float) pos.getY() + state.getHeight(level, pos))) {
            return fluid.getMekanismFluidType();
        }
        return null;
    }

    /** Mirrors the original eye-fluid owner, including a boat that keeps the rider's eyes dry. */
    @Nullable
    public static MekanismFluidType getEyeType(Player player) {
        double eyeY = player.getEyeY();
        if (player.getVehicle() instanceof Boat boat && !boat.isUnderWater()
              && boat.getBoundingBox().maxY >= eyeY && boat.getBoundingBox().minY <= eyeY) {
            return null;
        }
        return getSubmergedType(player.level(), new Vec3(player.getX(), eyeY, player.getZ()));
    }

    public static int getTintColor(FluidStack stack) {
        FluidRenderHandler handler = FluidRenderHandlerRegistry.INSTANCE.get(stack.getFluid());
        if (handler == null) {
            return NO_TINT;
        }
        //No world/position: the officially queried stack-only extension also had no level context, so biome-tinted
        // fluids fall back to their handler's context-free default color.
        //Fabric's fluid tint is RGB: its alpha is ignored by the native fluid renderer. Mekanism's
        //stack rendering consumes ARGB, so use opaque alpha for native fluids and retain the original
        //Mekanism FluidType's alpha for our own fluids.
        int color = handler.getFluidColor(null, null, stack.getFluid().defaultFluidState());
        int alpha = stack.getFluid() instanceof MekanismFlowingFluid fluid
              ? FastColor.ARGB32.alpha(fluid.getMekanismFluidType().color) : 255;
        return FastColor.ARGB32.color(alpha, color);
    }
}
