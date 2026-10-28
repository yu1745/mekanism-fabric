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
 * <p>Connection lifecycle and entity sounds are wired by their Fabric event owners. Boost synchronization is
 * refreshed here, including when a GUI opens without a physical release event.</p>
 */
public final class FabricClientTickRegistration {

    private static final Logger LOGGER = LoggerFactory.getLogger("Mekanism/FabricClientTick");
    private static final ClientTickHandler HANDLER = ClientTickHandler.INSTANCE;
    private static final AtomicBoolean PROOF = new AtomicBoolean();

    private FabricClientTickRegistration() {
    }

    public static void register() {
        ClientTickEvents.START_CLIENT_TICK.register(minecraft -> {
            // Also release boost when a screen opens, even if no physical key-release event is delivered.
            mekanism.client.MekanismClient.updateKey(mekanism.client.key.MekKeyHandler.isKeyPressed(mekanism.client.key.MekanismKeyHandler.boostKey),
                  mekanism.common.base.KeySync.BOOST);
            HANDLER.onTick();
        });
        if (PROOF.compareAndSet(false, true)) {
            //The official ClientTickHandler.onStartTracking rode EntityJoinLevelEvent(client side): an entity that
        //joins the client level - including newly tracked players - restarts their flamethrower sound.
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof net.minecraft.world.entity.player.Player player
                  && mekanism.common.config.MekanismConfig.client.enablePlayerSounds.get()) {
                mekanism.client.sound.SoundHandler.startFlamethrowerSound(player);
                var state = mekanism.common.base.PlayerState.CLIENT;
                var uuid = player.getUUID();
                if (state.isJetpackOn(uuid)) {
                    mekanism.client.sound.SoundHandler.startSound(world, uuid, mekanism.client.sound.PlayerSound.SoundType.JETPACK);
                }
                if (state.isScubaMaskOn(uuid)) {
                    mekanism.client.sound.SoundHandler.startSound(world, uuid, mekanism.client.sound.PlayerSound.SoundType.SCUBA_MASK);
                }
                if (state.isGravitationalModulationOn(uuid)) {
                    mekanism.client.sound.SoundHandler.startSound(world, uuid, mekanism.client.sound.PlayerSound.SoundType.GRAVITATIONAL_MODULATOR);
                }
            }
        });
        LOGGER.info("MEKANISM_FABRIC_CLIENT_TICK_PROOF handler=registered seams=mouse,fog_color,fog_setup,living_visibility,recipe_cache,entity_tracking");
        }
    }
}
