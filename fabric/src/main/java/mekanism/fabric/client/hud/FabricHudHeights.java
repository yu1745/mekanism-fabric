package mekanism.fabric.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Fabric stand-in for the NeoForge-patched {@code Gui.leftHeight} stack: vanilla 1.21.1 has no persistent HUD column
 * height (renderPlayerHealth computes its rows locally), so the left-column occupancy the MekaSuit energy bar positions
 * against is replicated from the same public inputs the vanilla renderer reads. This mirrors the
 * {@code nativeHudHeight} helper already used by MekanismStatusOverlay - the port's chosen resolution for the missing
 * mutable global GUI heights, per-layer instead of a shared accumulator.
 */
public final class FabricHudHeights {

    private FabricHudHeights() {
    }

    /**
     * Height in pixels the vanilla left HUD column (health/food/hunger/armor rows) occupies above the hotbar - the
     * quantity NeoForge's {@code leftHeight} tracks.
     */
    public static int leftColumnHeight(Minecraft minecraft) {
        if (!(minecraft.getCameraEntity() instanceof Player player)) {
            return 39;
        }
        int left = 39;
        if (minecraft.gameMode.canHurtPlayer()) {
            float visibleHealth = Math.max(player.getMaxHealth(), Math.max(Mth.ceil(player.getHealth()), minecraft.gui.displayHealth));
            int rows = Mth.ceil((visibleHealth + Mth.ceil(player.getAbsorptionAmount())) / 20F);
            left += 10 + (rows - 1) * Math.max(3, 12 - rows);
            if (player.getArmorValue() > 0) {
                left += 10;
            }
        }
        LivingEntity vehicle = player.getVehicle() instanceof LivingEntity living ? living : null;
        int vehicleHearts = minecraft.gui.getVehicleMaxHearts(vehicle);
        if (vehicleHearts > 0) {
            //Vehicle hearts render on the left column and push the jump bar/health stack up in vanilla's layout.
            left += 10;
        }
        return left;
    }
}
