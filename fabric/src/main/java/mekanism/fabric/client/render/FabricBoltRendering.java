package mekanism.fabric.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import mekanism.client.render.MekanismRenderType;
import mekanism.client.render.MekanismShaders;
import mekanism.client.render.lib.effect.BoltRenderer;
import mekanism.common.lib.effect.BoltEffect;
import mekanism.common.network.to_client.PacketLightningRender;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;

/** Owns the original queued world bolts; the native particle-return mixin preserves the active framebuffer. */
public final class FabricBoltRendering {
    private static final BoltRenderer BOLTS = new BoltRenderer();
    private static boolean initialized;

    private FabricBoltRendering() {
    }

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        if (!ClientPlayNetworking.registerGlobalReceiver(PacketLightningRender.TYPE, (packet, context) -> packet.handle())) {
            throw new IllegalStateException("A client receiver is already registered for " + PacketLightningRender.TYPE.id());
        }
        CoreShaderRegistrationCallback.EVENT.register(MekanismShaders::shaderRegistry);
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((client, world) -> BOLTS.clear());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> BOLTS.clear());
        initialized = true;
    }

    public static void renderBolt(Object owner, BoltEffect effect) {
        BOLTS.update(owner, effect, Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false));
    }

    public static void afterParticles(float partialTick, PoseStack pose, Camera camera) {
        if (BOLTS.hasBoltsToRender()) {
            var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
            BOLTS.render(partialTick, pose, buffers, camera.getPosition());
            buffers.endBatch(MekanismRenderType.MEK_LIGHTNING);
        }
    }
}
