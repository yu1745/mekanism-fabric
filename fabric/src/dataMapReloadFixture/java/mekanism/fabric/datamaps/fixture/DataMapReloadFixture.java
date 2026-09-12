package mekanism.fabric.datamaps.fixture;

import com.mojang.brigadier.Command;
import mekanism.api.datamaps.MekaSuitAbsorption;
import mekanism.fabric.datamaps.FabricMekanismDataMaps;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.commands.Commands;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.damagesource.DamageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Test-only packaged-server query and reload-failure probe. It never supplies data maps or invokes their loader. */
public final class DataMapReloadFixture implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("Mekanism/DataMapReloadFixture");
    private static final ResourceLocation FAILURE_LISTENER_ID =
          ResourceLocation.fromNamespaceAndPath("mekanism_datamap_reload_fixture", "failure_listener");
    private static final ResourceLocation FAILURE_MARKER =
          ResourceLocation.fromNamespaceAndPath("mekanism_datamap_reload_fixture", "reload_failure.marker");

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
              Commands.literal("mekanism_datamap_probe").requires(source -> source.hasPermission(4)).executes(context -> probe(context.getSource().getServer()))));
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new FailureReloadListener());
        ServerLifecycleEvents.SERVER_STOPPED.register(server ->
              LOGGER.info("MEKANISM_DATAMAP_FIXTURE_STOP session_removed={}", !FabricMekanismDataMaps.hasSession(server)));
    }

    private static int probe(MinecraftServer server) {
        Registry<DamageType> registry = server.reloadableRegistries().get().registryOrThrow(Registries.DAMAGE_TYPE);
        int mapped = 0;
        int ones = 0;
        Float sonic = null;
        for (Holder.Reference<DamageType> holder : registry.holders().toList()) {
            MekaSuitAbsorption value = FabricMekanismDataMaps.getMekaSuitAbsorption(server, holder);
            if (value != null) {
                mapped++;
                if (Float.compare(value.absorption(), 1F) == 0) {
                    ones++;
                }
                if (holder.key().location().getPath().equals("sonic_boom") && holder.key().location().getNamespace().equals("minecraft")) {
                    sonic = value.absorption();
                }
            }
        }
        int epoch = FabricMekanismDataMaps.epoch(server);
        if (mapped != FabricMekanismDataMaps.size(server)) {
            throw new IllegalStateException("Data-map fixture query count disagrees with published snapshot");
        }
        LOGGER.info("MEKANISM_DATAMAP_FIXTURE_PROBE epoch={} entries={} ones={} sonic={} session=true", epoch, mapped, ones, sonic);
        return Command.SINGLE_SUCCESS;
    }

    private static final class FailureReloadListener implements SimpleSynchronousResourceReloadListener {
        @Override
        public ResourceLocation getFabricId() {
            return FAILURE_LISTENER_ID;
        }

        @Override
        public void onResourceManagerReload(ResourceManager resources) {
            if (resources.getResource(FAILURE_MARKER).isPresent()) {
                LOGGER.info("MEKANISM_DATAMAP_FIXTURE_FAILURE_TRIGGER marker_present=true");
                throw new IllegalStateException("Intentional data-map reload fixture failure marker is present");
            }
        }
    }
}
