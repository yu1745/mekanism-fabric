package mekanism.fabric.network;

import io.netty.buffer.Unpooled;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.chemical.BasicChemicalTank;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalDataContext;
import mekanism.api.chemical.ChemicalRegistry;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalTank;
import mekanism.api.datamaps.chemical.attribute.ChemicalRadioactivity;
import mekanism.common.registries.MekanismChemicals;
import mekanism.common.resource.PrimaryResource;
import mekanism.fabric.datamaps.FabricChemicalDataMaps;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.FabricServerConfigurationNetworkHandler;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Isolated real-server probe for the chemical vertical. */
public final class ChemicalRuntimeServerFixture implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("Mekanism/ChemicalRuntimeServerFixture");
    private static final Path ROOT = Path.of(System.getProperty("mekanism.chemical.runtimeRoot")).toAbsolutePath().normalize();
    private static final ConfigurationTask.Type ABORT_TASK = new ConfigurationTask.Type("mekanism_chemical_runtime_fixture:abort_after_chemical");
    private static final AtomicBoolean ABORT_FIRST_CONFIGURATION = new AtomicBoolean();
    private static volatile ServerConfigurationPacketListenerImpl abortAfterAck;
    private static boolean reloadStarted;

    @Override
    public void onInitialize() {
        ServerConfigurationConnectionEvents.CONFIGURE.register((handler, server) -> {
            if (ABORT_FIRST_CONFIGURATION.compareAndSet(false, true)) {
                ((FabricServerConfigurationNetworkHandler) handler).addTask(new AbortAfterChemicalTask(handler));
            }
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            initialProbe(server);
            marker("server-initial.complete");
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            ServerConfigurationPacketListenerImpl abort = abortAfterAck;
            if (abort != null && FabricChemicalNetworking.hasPlaySession(abort.getOwner().getId())) {
                abortAfterAck = null;
                LOGGER.info("MEKANISM_CHEMICAL_RUNTIME_SERVER phase=configuration_abort after_snapshot_ack=true");
                marker("server-config-abort.complete");
                abort.disconnect(Component.literal("Chemical runtime deliberate post-snapshot configuration abort"));
            }
            if (!reloadStarted && Files.isRegularFile(ROOT.resolve("reload.trigger"))) {
                reloadStarted = true;
                startReload(server);
            }
        });
    }

    private record AbortAfterChemicalTask(ServerConfigurationPacketListenerImpl handler) implements ConfigurationTask {
        @Override
        public void start(Consumer<Packet<?>> sender) {
            if (abortAfterAck != null) throw new IllegalStateException("duplicate pending configuration abort fixture");
            // Keep this later fixture task open. The server tick terminates it only after production's
            // exact Chemical ACK has inserted the pre-play UUID/session.
            abortAfterAck = handler;
        }

        @Override
        public Type type() {
            return ABORT_TASK;
        }
    }

    private static void initialProbe(MinecraftServer server) {
        ChemicalDataContext context = FabricChemicalDataMaps.session(server).context();
        require(context.epoch() == 1, "initial chemical epoch must be one");
        require(registrationOrder(), "chemical registry registration order or canonical holders differ");
        Chemical waste = MekanismChemicals.NUCLEAR_WASTE.get();
        require(Double.compare(waste.getRadioactivity(), 0.01) == 0, "initial nuclear-waste radioactivity differs");
        require(rejects(waste), "default tank must reject radioactive nuclear waste");
        require(tooltipCount(waste) == 1, "initial radioactive tooltip must be visible");
        roundTripTrueLong();
        rejectUnknownSnapshot();
        LOGGER.info("MEKANISM_CHEMICAL_RUNTIME_SERVER phase=initial epoch=1 entries=65 registration_order=true "
              + "tank_rejected=true nbt_long=true stream_long=true tooltip=true unknown_snapshot_rejected=true");
    }

    private static void startReload(MinecraftServer server) {
        try {
            Files.deleteIfExists(ROOT.resolve("reload.trigger"));
            server.getPackRepository().reload();
            List<String> selected = new ArrayList<>(server.getPackRepository().getSelectedIds());
            String fixturePack = "file/chemical-runtime";
            require(server.getPackRepository().getAvailableIds().contains(fixturePack), "chemical runtime data pack was not discovered");
            if (!selected.contains(fixturePack)) selected.add(fixturePack);
            server.reloadResources(selected).whenComplete((ignored, failure) -> server.execute(() -> {
                if (failure != null) throw new IllegalStateException("chemical runtime data-pack reload failed", failure);
                reloadedProbe(server);
                marker("reload.complete");
            }));
        } catch (RuntimeException | IOException failure) {
            throw new IllegalStateException("Unable to begin chemical runtime reload", failure);
        }
    }

    private static void reloadedProbe(MinecraftServer server) {
        ChemicalDataContext context = FabricChemicalDataMaps.session(server).context();
        Chemical waste = MekanismChemicals.NUCLEAR_WASTE.get();
        require(context.epoch() == 2, "successful removal reload must publish epoch two");
        require(Double.compare(waste.getRadioactivity(), 0) == 0, "radioactivity removal was not published");
        IChemicalTank tank = BasicChemicalTank.create(1_000, null);
        ChemicalStack remainder = tank.insert(new ChemicalStack(MekanismChemicals.NUCLEAR_WASTE, 17), Action.EXECUTE, AutomationType.INTERNAL);
        require(remainder.isEmpty() && tank.getStored() == 17, "default tank did not accept chemical after authoritative removal");
        require(tooltipCount(waste) == 0, "removed radioactive attribute still produced a tooltip");
        LOGGER.info("MEKANISM_CHEMICAL_RUNTIME_SERVER phase=reload epoch=2 removal=true tank_accepted=true tooltip_removed=true");
    }

    private static boolean registrationOrder() {
        var registry = ChemicalRegistry.registry();
        if (registry.size() != 65 || registry.getId(ChemicalRegistry.empty()) != 0
              || registry.getHolder(ChemicalRegistry.EMPTY_KEY).orElse(null) != ChemicalRegistry.emptyHolder()) return false;
        List<String> expected = new ArrayList<>(List.of("bio", "fungi", "tin", "gold", "refined_obsidian", "diamond", "redstone", "carbon",
              "hydrogen", "oxygen", "steam", "water_vapor", "chlorine", "sulfur_dioxide", "sulfur_trioxide", "sulfuric_acid",
              "hydrogen_chloride", "hydrofluoric_acid", "uranium_oxide", "uranium_hexafluoride", "ethene", "sodium",
              "superheated_sodium", "brine", "lithium", "osmium", "fissile_fuel", "nuclear_waste", "spent_nuclear_waste",
              "plutonium", "polonium", "antimatter"));
        for (mekanism.api.text.EnumColor color : mekanism.api.text.EnumColor.values()) expected.add(color.getRegistryPrefix());
        for (PrimaryResource resource : PrimaryResource.values()) {
            expected.add("dirty_" + resource.getRegistrySuffix());
            expected.add("clean_" + resource.getRegistrySuffix());
        }
        for (int index = 0; index < expected.size(); index++) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("mekanism", expected.get(index));
            Chemical value = registry.get(id);
            if (value == null || registry.getId(value) != index + 1 || !registry.getKey(value).equals(id)) return false;
        }
        return true;
    }

    private static boolean rejects(Chemical chemical) {
        IChemicalTank tank = BasicChemicalTank.create(1_000, null);
        ChemicalStack source = new ChemicalStack(ChemicalRegistry.registry().wrapAsHolder(chemical), 17);
        ChemicalStack remainder = tank.insert(source, Action.EXECUTE, AutomationType.INTERNAL);
        return remainder.getAmount() == 17 && tank.isEmpty();
    }

    private static int tooltipCount(Chemical chemical) {
        List<net.minecraft.network.chat.Component> tooltips = new ArrayList<>();
        ResourceKey<Chemical> key = ChemicalRegistry.registry().getResourceKey(chemical).orElseThrow();
        ChemicalRadioactivity attribute = ChemicalDataContext.current().get(key, FabricChemicalDataMaps.CHEMICAL_RADIOACTIVITY);
        if (attribute != null) attribute.collectTooltips((Item.TooltipContext) null, tooltips, TooltipFlag.NORMAL);
        return tooltips.size();
    }

    private static void roundTripTrueLong() {
        long amount = (1L << 60) + 123;
        RegistryAccess access = new RegistryAccess.ImmutableRegistryAccess(List.of(ChemicalRegistry.registry()));
        BasicChemicalTank source = (BasicChemicalTank) BasicChemicalTank.createAllValid(Long.MAX_VALUE, null);
        require(source.insert(new ChemicalStack(MekanismChemicals.HYDROGEN, amount), Action.EXECUTE, AutomationType.INTERNAL).isEmpty(),
              "true-long source tank rejected stack");
        CompoundTag nbt = source.serializeNBT(access);
        BasicChemicalTank decodedTank = (BasicChemicalTank) BasicChemicalTank.createAllValid(Long.MAX_VALUE, null);
        decodedTank.deserializeNBT(access, nbt);
        require(decodedTank.getStored() == amount && decodedTank.getStack().getChemical() == MekanismChemicals.HYDROGEN.get(),
              "tank NBT true-long round trip differed");
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), access);
        try {
            ChemicalStack.STREAM_CODEC.encode(buffer, source.getStack());
            ChemicalStack decoded = ChemicalStack.STREAM_CODEC.decode(buffer);
            require(decoded.getAmount() == amount && decoded.getChemicalHolder() == source.getStack().getChemicalHolder(),
                  "stack stream true-long round trip differed");
        } finally {
            buffer.release();
        }
    }

    private static void rejectUnknownSnapshot() {
        ResourceLocation missing = ResourceLocation.fromNamespaceAndPath("missing", "chemical");
        ChemicalSnapshotPayload payload = new ChemicalSnapshotPayload("runtime", 1, true, 2.5, false, List.of(),
              List.of(new ChemicalSnapshotPayload.Fuel(missing, 1, 1)), List.of(), List.of(), List.of());
        try {
            payload.resolve();
            throw new IllegalStateException("unknown chemical snapshot entry was accepted");
        } catch (IllegalArgumentException expected) {
            // Expected owner-executor resolution rejection.
        }
    }

    private static void marker(String name) {
        try {
            Files.createDirectories(ROOT);
            Files.writeString(ROOT.resolve(name), "ok\n");
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to write chemical runtime marker " + name, failure);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
