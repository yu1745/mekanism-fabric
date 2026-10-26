package mekanism.fabric.client.hud;

import java.util.concurrent.atomic.AtomicBoolean;
import mekanism.client.ClientTickHandler;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fabric port of the official ClientTickHandler registrations. NeoForge's {@code ClientTickEvent.Pre} maps to
 * fabric's start-of-tick callback; the mouse-scroll, fog and living-render seams ride their own mixins
 * ({@code ClientMouseScrollMixin}, {@code FogColorMixin}, {@code FogSetupMixin}, {@code LivingEntityVisibilityMixin}),
 * and the recipe-cache reset rides {@code ClientRecipeCacheMixin} on the recipe-sync packet.
 *
 * <p>The connection-lifecycle handlers (MekanismClient.launchClient/reset), the flamethrower/geiger sounds
 * (SoundHandler), the holiday notifications (HolidayManager) and the KeySync update stay deferred with their lanes;
 * the deferral points are documented in place in ClientTickHandler.</p>
 */
public final class FabricClientTickRegistration {

    private static final Logger LOGGER = LoggerFactory.getLogger("Mekanism/FabricClientTick");
    private static final ClientTickHandler HANDLER = ClientTickHandler.INSTANCE;
    private static final AtomicBoolean PROOF = new AtomicBoolean();

    private FabricClientTickRegistration() {
    }

    public static void register() {
        ClientTickEvents.START_CLIENT_TICK.register(minecraft -> HANDLER.onTick());
        if (PROOF.compareAndSet(false, true)) {
            LOGGER.info("MEKANISM_FABRIC_CLIENT_TICK_PROOF handler=registered seams=mouse,fog_color,fog_setup,living_visibility,recipe_cache");
        }
    }
}
