package mekanism.fabric.chemical.fixture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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
import mekanism.fabric.datamaps.FabricChemicalDataMaps;
import mekanism.fabric.network.ChemicalSnapshotPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Isolated real-client probe that disconnects and reconnects in the same physical JVM. */
public final class ChemicalRuntimeClientFixture implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("Mekanism/ChemicalRuntimeClientFixture");
    private static final Path ROOT = Path.of(System.getProperty("mekanism.chemical.runtimeRoot")).toAbsolutePath().normalize();
    private static final String ADDRESS = System.getProperty("mekanism.chemical.address", "127.0.0.1:25568");
    private static int joins;
    private static boolean waitingForCleanup;
    private static boolean cleanupObserved;
    private static boolean reconnectStarted;

    @Override
    public void onInitializeClient() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(() -> joined(client)));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> waitingForCleanup = joins == 1);
        ClientTickEvents.END_CLIENT_TICK.register(ChemicalRuntimeClientFixture::tick);
    }

    private static void joined(Minecraft client) {
        joins++;
        ChemicalDataContext context = ChemicalDataContext.current();
        Chemical waste = MekanismChemicals.NUCLEAR_WASTE.get();
        require(ChemicalRegistry.registry().size() == 65 && ChemicalRegistry.registry().getKey(waste).equals(
              ResourceLocation.fromNamespaceAndPath("mekanism", "nuclear_waste")), "client chemical registry name remap failed");
        if (joins == 1) {
            require(context.epoch() == 1 && Double.compare(waste.getRadioactivity(), 0.01) == 0,
                  "initial client snapshot differs from server epoch one");
            require(rejects(waste) && tooltipCount(waste) == 1, "initial client tank/tooltip view differs");
            rejectUnknownSnapshot();
            LOGGER.info("MEKANISM_CHEMICAL_RUNTIME_CLIENT phase=initial epoch=1 registry_name=true tank_rejected=true "
                  + "tooltip=true unknown_snapshot_rejected=true");
            marker("client-initial.complete");
            client.execute(() -> {
                if (client.getConnection() != null) {
                    client.getConnection().getConnection().disconnect(net.minecraft.network.chat.Component.literal("Chemical runtime reconnect probe"));
                }
                client.disconnect();
            });
        } else if (joins == 2) {
            require(context.epoch() == 2 && Double.compare(waste.getRadioactivity(), 0) == 0,
                  "reconnected client did not receive removed epoch two");
            IChemicalTank tank = BasicChemicalTank.create(1_000, null);
            require(tank.insert(new ChemicalStack(ChemicalRegistry.registry().wrapAsHolder(waste), 17), Action.EXECUTE, AutomationType.INTERNAL).isEmpty()
                  && tank.getStored() == 17, "reconnected client tank still rejects removed radioactivity");
            require(tooltipCount(waste) == 0, "reconnected client retained removed tooltip");
            sameJvmOwnerIsolation(context);
            LOGGER.info("MEKANISM_CHEMICAL_RUNTIME_CLIENT phase=reconnect epoch=2 same_process=true cleanup=true "
                  + "tank_accepted=true tooltip_removed=true owner_thread_isolated=true");
            marker("client-reconnect.complete");
        } else {
            throw new IllegalStateException("chemical runtime client joined more than twice");
        }
    }

    private static void tick(Minecraft client) {
        if (waitingForCleanup && client.getConnection() == null && ChemicalDataContext.current().epoch() == 0) {
            waitingForCleanup = false;
            cleanupObserved = true;
            LOGGER.info("MEKANISM_CHEMICAL_RUNTIME_CLIENT phase=disconnect context_cleared=true");
            marker("client-disconnect.complete");
        }
        if (cleanupObserved && !reconnectStarted && Files.isRegularFile(ROOT.resolve("reconnect.signal"))) {
            reconnectStarted = true;
            try {
                Files.deleteIfExists(ROOT.resolve("reconnect.signal"));
            } catch (IOException failure) {
                throw new IllegalStateException("Unable to consume reconnect signal", failure);
            }
            ServerData data = new ServerData("Mekanism chemical runtime", ADDRESS, ServerData.Type.OTHER);
            ConnectScreen.startConnecting(new TitleScreen(), client, ServerAddress.parseString(ADDRESS), data, true, null);
        }
    }

    private static boolean rejects(Chemical chemical) {
        IChemicalTank tank = BasicChemicalTank.create(1_000, null);
        ChemicalStack remainder = tank.insert(new ChemicalStack(ChemicalRegistry.registry().wrapAsHolder(chemical), 17), Action.EXECUTE, AutomationType.INTERNAL);
        return remainder.getAmount() == 17 && tank.isEmpty();
    }

    private static int tooltipCount(Chemical chemical) {
        ChemicalRadioactivity attribute = FabricChemicalDataMaps.CHEMICAL_RADIOACTIVITY == null ? null
              : ChemicalDataContext.current().get(ResourceKey.create(ChemicalRegistry.REGISTRY_KEY,
                    ResourceLocation.fromNamespaceAndPath("mekanism", "nuclear_waste")), FabricChemicalDataMaps.CHEMICAL_RADIOACTIVITY);
        if (attribute == null) return 0;
        List<net.minecraft.network.chat.Component> tooltips = new ArrayList<>();
        attribute.collectTooltips((Item.TooltipContext) null, tooltips, TooltipFlag.NORMAL);
        return tooltips.size();
    }

    private static void rejectUnknownSnapshot() {
        ResourceLocation missing = ResourceLocation.fromNamespaceAndPath("missing", "chemical");
        ChemicalSnapshotPayload payload = new ChemicalSnapshotPayload("client-runtime", 1, true, 2.5, false, List.of(),
              List.of(new ChemicalSnapshotPayload.Fuel(missing, 1, 1)), List.of(), List.of(), List.of());
        try {
            payload.resolve();
            throw new IllegalStateException("client accepted unknown chemical snapshot entry");
        } catch (IllegalArgumentException expected) {
            // Expected on the physical-client owner executor.
        }
    }

    private static void sameJvmOwnerIsolation(ChemicalDataContext clientContext) {
        CountDownLatch bound = new CountDownLatch(1);
        CountDownLatch checked = new CountDownLatch(1);
        Throwable[] failure = new Throwable[1];
        Thread integratedOwner = new Thread(() -> {
            try {
                ChemicalDataContext.bind(new ChemicalDataContext(777, java.util.Map.of(), true, 2.5, false));
                bound.countDown();
                require(checked.await(5, TimeUnit.SECONDS), "timed out waiting for client isolation check");
                require(ChemicalDataContext.current().epoch() == 777, "client publication contaminated same-JVM server owner");
            } catch (Throwable t) {
                failure[0] = t;
            } finally {
                ChemicalDataContext.clear();
            }
        }, "chemical-runtime-integrated-owner");
        integratedOwner.start();
        try {
            require(bound.await(5, TimeUnit.SECONDS), "same-JVM owner did not bind context");
            require(ChemicalDataContext.current() == clientContext && clientContext.epoch() == 2,
                  "same-JVM server owner contaminated physical client context");
            checked.countDown();
            integratedOwner.join(5_000);
            require(!integratedOwner.isAlive() && failure[0] == null, "same-JVM owner isolation probe failed");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("same-JVM isolation probe interrupted", interrupted);
        } finally {
            checked.countDown();
        }
    }

    private static void marker(String name) {
        try {
            Files.createDirectories(ROOT);
            Files.writeString(ROOT.resolve(name), "ok\n");
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to write client runtime marker " + name, failure);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
