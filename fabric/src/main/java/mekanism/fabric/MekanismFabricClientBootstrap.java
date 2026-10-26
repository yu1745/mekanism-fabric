package mekanism.fabric;

import mekanism.fabric.chemical.FabricChemicalClientLifecycle;
import mekanism.fabric.client.render.obj.FabricTransmitterColorProvider;
import mekanism.fabric.client.render.obj.FabricTransmitterModelPlugin;
import mekanism.fabric.config.FabricClientConfigLifecycle;
import mekanism.fabric.network.FabricChemicalClientNetworking;
import mekanism.fabric.network.FabricMekanismClientNetworking;
import mekanism.fabric.network.FabricContainerClientNetworking;
import mekanism.fabric.network.FabricTileUpdateHandler;
import mekanism.fabric.network.MekanismNetworkProtocol;
import mekanism.fabric.recipe.FabricRecipeCacheClientLifecycle;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Client-only entrypoint kept separate from the dedicated-server-safe common bootstrap. */
public final class MekanismFabricClientBootstrap implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("Mekanism/FabricClient");

    @Override
    public void onInitializeClient() {
        FabricClientConfigLifecycle.initialize();
        //Official RegisterMenuScreensEvent transplant: machine screens register during client init
        mekanism.fabric.client.FabricScreenRegistration.register();
        //Official RegisterKeyMappingsEvent transplant: all declared keys register during client init
        mekanism.client.key.MekanismKeyHandler.registerKeybindings();
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientBlockEntityEvents.BLOCK_ENTITY_LOAD.register((blockEntity, world) -> {
            if (blockEntity instanceof mekanism.common.tile.machine.TileEntityOredictionificator tile) {
                tile.onLoad();
            }
        });
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register((blockEntity, world) -> {
            if (blockEntity instanceof mekanism.common.tile.machine.TileEntityOredictionificator tile) {
                tile.onUnload();
            }
        });
        FabricChemicalClientLifecycle.initialize();
        FabricRecipeCacheClientLifecycle.initialize();
        FabricMekanismClientNetworking.initialize();
        FabricChemicalClientNetworking.initialize();
        FabricTileUpdateHandler.initializeClient();
        FabricContainerClientNetworking.initializeClient();
        mekanism.fabric.network.FabricTransmitterClientNetworking.initialize();
        mekanism.fabric.client.render.FabricBoltRendering.initialize();
        mekanism.fabric.network.FabricSecurityClientNetworking.initialize();
        mekanism.fabric.radiation.FabricRadiationClientLifecycle.initialize();
        //Real model-loading owner for the shipped "mekanism:transmitter" OBJ/MTL models, plus the logistical
        //transporter tint. Model registration needs no block instance: it keys off the model ids in the resource pack.
        FabricTransmitterModelPlugin.initialize();
        FabricTransmitterColorProvider.initialize();
        //Loader-closure owner for the shipped NeoForge-loader models (composite factories, the Meka-Tool OBJs, the
        //QIO dashboard layers); the energy-cube geometry loader stays deferred to the BE-aware render seam.
        mekanism.fabric.client.model.FabricMekanismModelPlugin.initialize();
        //Offline model-cache capture for the tile renderers (the official BakingCompleted/setup pairing).
        net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin.register(new mekanism.fabric.client.model.FabricModelCachePlugin());
        //Block-item ISBERs (energy cube, fluid tank): official item JSONs stay, quads suppressed in favor of the
        //builtin renderers (the official registerItemExtensions wiring).
        mekanism.fabric.client.render.item.FabricItemRendererRegistration.registerRenderers();
        net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin.register(new mekanism.fabric.client.render.item.FabricItemRendererRegistration());
        //Tile entity renderers (the official ClientRegistration.registerRenderers tile half) and the transparent
        //LazyRender queue drained at the after-translucent stage (the official RenderLevelStageEvent handler).
        mekanism.fabric.client.render.FabricTileEntityRendererRegistration.register();
        mekanism.fabric.client.render.FabricEntityRendererRegistration.register();
        mekanism.fabric.client.hud.FabricClientTickRegistration.register();
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.AFTER_TRANSLUCENT.register(context ->
              mekanism.client.render.RenderTickHandler.renderTransparentLayer(context.camera(),
                    context.matrixStack(),
                    mekanism.client.render.RenderTickHandler.minecraft.levelRenderer.ticks,
                    context.tickCounter().getGameTimeDeltaPartialTick(false),
                    context.profiler()));
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((client, world) ->
              mekanism.common.lib.transmitter.TransmitterNetworkRegistry.clearClientNetworks());
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
              mekanism.common.lib.transmitter.TransmitterNetworkRegistry.clearClientNetworks());
        LOGGER.info("MEKANISM_FABRIC_NETWORK_CLIENT_PROOF protocol={} receiver=true recipe_mixin_client_only=true", MekanismNetworkProtocol.CURRENT);
    }
}
