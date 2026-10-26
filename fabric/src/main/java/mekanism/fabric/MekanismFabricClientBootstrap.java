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
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((client, world) ->
              mekanism.common.lib.transmitter.TransmitterNetworkRegistry.clearClientNetworks());
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
              mekanism.common.lib.transmitter.TransmitterNetworkRegistry.clearClientNetworks());
        LOGGER.info("MEKANISM_FABRIC_NETWORK_CLIENT_PROOF protocol={} receiver=true recipe_mixin_client_only=true", MekanismNetworkProtocol.CURRENT);
    }
}
