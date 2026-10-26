package mekanism.fabric.client.render;

import java.util.concurrent.atomic.AtomicBoolean;
import mekanism.client.render.entity.RenderFlame;
import mekanism.common.registries.MekanismEntityTypes;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fabric port of the official entity renderer registrations. Vanilla's {@link EntityRenderers#register} is the
 * loader-neutral surface, mirroring how the tile entity renderers register through {@code BlockEntityRenderers}.
 *
 * <p>ROBIT stays deferred with its slice: the renderer is baked-model driven and needs the robit skin model family
 * (the two deferred robit cache entries) plus {@code RobitSpriteUploader}.</p>
 */
public final class FabricEntityRendererRegistration {

    private static final Logger LOGGER = LoggerFactory.getLogger("Mekanism/FabricEntityRenderers");
    private static final AtomicBoolean PROOF = new AtomicBoolean();

    private FabricEntityRendererRegistration() {
    }

    public static void register() {
        EntityRendererRegistry.register(MekanismEntityTypes.FLAME.get(), RenderFlame::new);
        if (PROOF.compareAndSet(false, true)) {
            LOGGER.info("MEKANISM_FABRIC_ENTITY_RENDERER_PROOF entities=1 (flame; robit deferred with its model family)");
        }
    }
}
