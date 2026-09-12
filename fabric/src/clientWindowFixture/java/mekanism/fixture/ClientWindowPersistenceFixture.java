package mekanism.fixture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import mekanism.common.inventory.container.SelectedWindowData;
import mekanism.common.inventory.container.SelectedWindowData.WindowPosition;
import mekanism.common.inventory.container.SelectedWindowData.WindowType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Isolated real-client probe; this class is never included in the production artifact. */
public final class ClientWindowPersistenceFixture implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("Mekanism/ClientWindowFixture");

    @Override
    public void onInitializeClient() {
        int phase = Integer.parseInt(System.getProperty("mekanism.window.fixturePhase", "0"));
        SelectedWindowData side = new SelectedWindowData(WindowType.SIDE_CONFIG);
        SelectedWindowData color = new SelectedWindowData(WindowType.COLOR);
        WindowPosition initialPosition = side.getLastPosition();
        if (phase == 1) {
            require(initialPosition.equals(new WindowPosition(Integer.MAX_VALUE, Integer.MAX_VALUE, false)), "side defaults");
            side.updateLastPosition(-39, 117, true);
            color.updateLastPosition(7, -8, true);
            require(color.getLastPosition().equals(new WindowPosition(7, -8, false)), "unpinnable color position");
        } else if (phase == 2) {
            require(initialPosition.equals(new WindowPosition(-39, 117, true)), "persisted side position");
            require(color.getLastPosition().equals(new WindowPosition(7, -8, false)), "persisted color position");
            side.updateLastPosition(-39, 117, true);
            color.updateLastPosition(7, -8, true);
        } else {
            throw new IllegalArgumentException("mekanism.window.fixturePhase must be 1 or 2");
        }
        require(readConfig().contains("fixture_unknown = \"retained\""), "unknown key must survive load");
        LOGGER.info("MEKANISM_CLIENT_WINDOW_FIXTURE phase={} owner_available_for_physical_client_lifetime=true", phase);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            boolean rejectedAfterClose = false;
            try {
                side.getLastPosition();
            } catch (IllegalStateException expected) {
                rejectedAfterClose = true;
            }
            require(rejectedAfterClose, "production shutdown callback must remove the exact owner first");
            require(readConfig().contains("fixture_unknown = \"retained\""), "shutdown must retain unknown key");
            require(readConfig().contains("x = -39") && readConfig().contains("y = 117") && readConfig().contains("pinned = true"),
                  "physical shutdown must flush accepted saves");
            LOGGER.info("MEKANISM_CLIENT_WINDOW_FIXTURE_SHUTDOWN phase={} flushed=true owner_closed=true", phase);
        });
        Minecraft.getInstance().execute(Minecraft.getInstance()::stop);
    }

    private static String readConfig() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("Mekanism/client.toml");
        try {
            return Files.readString(file);
        } catch (IOException failure) {
            throw new IllegalStateException("Failed to read " + file, failure);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("Client window fixture assertion failed: " + message);
        }
    }
}
