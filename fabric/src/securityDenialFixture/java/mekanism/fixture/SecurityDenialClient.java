package mekanism.fixture;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client fixture for the two-player security check (W-120).
 *
 * <p>Both players join the same throwaway server and this fixture makes each of them right-click the same machine once.
 * It then reports whether the server opened the machine screen: a player who may not use the machine gets no screen at
 * all, which is exactly what the access check has to produce on the server side. The two outcomes are logged as
 * {@code SECURITY_FIXTURE_ALLOWED} and {@code SECURITY_FIXTURE_DENIED} so the smoke script can assert on them.
 */
public final class SecurityDenialClient implements ClientModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("Mekanism/SecurityDenialFixture");
    private static final String TARGET_PROPERTY = "mekanism.security.target";
    private static final String EXPECT_PROPERTY = "mekanism.security.expect";
    private static final int SETTLE_TICKS = 40;

    private final BlockPos target = parseTarget(System.getProperty(TARGET_PROPERTY, "0,100,0"));
    private final String expected = System.getProperty(EXPECT_PROPERTY, "allow");
    private int ticks;
    private boolean clicked;
    private boolean reported;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(Minecraft minecraft) {
        if (reported || minecraft.player == null || minecraft.level == null || minecraft.gameMode == null) {
            return;
        }
        ClientLevel level = minecraft.level;
        ticks++;
        // The world has to be joined and the machine has to exist before the click is meaningful.
        if (!clicked) {
            if (ticks < 60 || minecraft.screen != null || !level.isLoaded(target)) {
                return;
            }
            clicked = true;
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(target), net.minecraft.core.Direction.UP, target, false);
            // The interaction packet is what the server's access check sees, so this is the real path a player takes.
            minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, hit);
            LOGGER.info("SECURITY_FIXTURE_CLICKED pos={} expect={}", target, expected);
            return;
        }
        if (ticks < 120) {
            return;
        }
        reported = true;
        boolean opened = minecraft.screen != null;
        String actual = opened ? "allow" : "deny";
        if (actual.equals(expected)) {
            LOGGER.info("SECURITY_FIXTURE_{} pos={} screen={}", opened ? "ALLOWED" : "DENIED", target,
                  minecraft.screen == null ? "none" : minecraft.screen.getClass().getSimpleName());
        } else {
            LOGGER.error("SECURITY_FIXTURE_UNEXPECTED expected={} actual={} pos={} screen={}", expected, actual, target,
                  minecraft.screen == null ? "none" : minecraft.screen.getClass().getSimpleName());
        }
    }

    private static BlockPos parseTarget(String raw) {
        String[] parts = raw.split(",");
        if (parts.length != 3) {
            throw new IllegalArgumentException("mekanism.security.target must be x,y,z but was " + raw);
        }
        return new BlockPos(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()), Integer.parseInt(parts[2].trim()));
    }
}
