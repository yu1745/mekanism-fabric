package mekanism.client.render.hud;

import com.mojang.blaze3d.vertex.PoseStack;
import mekanism.common.item.interfaces.IModeItem;
import mekanism.common.lib.Color;
import net.minecraft.SharedConstants;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.util.Mth;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import org.jetbrains.annotations.NotNull;

public class MekanismStatusOverlay implements LayeredDraw.Layer {

    public static final MekanismStatusOverlay INSTANCE = new MekanismStatusOverlay();
    private static final int BASE_TIMER = 5 * SharedConstants.TICKS_PER_SECOND;

    private int modeSwitchTimer = 0;
    private int lastTick;

    private MekanismStatusOverlay() {
    }

    public void setTimer() {
        modeSwitchTimer = BASE_TIMER;
    }

    /** Native HUD row occupancy for this overlay, not an emulation of NeoForge's mutable global GUI heights. */
    private static int nativeHudHeight(Minecraft minecraft) {
        if (!(minecraft.getCameraEntity() instanceof Player player)) {
            return 39;
        }
        LivingEntity vehicle = player.getVehicle() instanceof LivingEntity living ? living : null;
        int vehicleHearts = minecraft.gui.getVehicleMaxHearts(vehicle);
        int left = 39;
        int right = 39 + minecraft.gui.getVisibleVehicleHeartRows(vehicleHearts) * 10;
        if (minecraft.gameMode.canHurtPlayer()) {
            float visibleHealth = Math.max(player.getMaxHealth(), Math.max(Mth.ceil(player.getHealth()), minecraft.gui.displayHealth));
            int rows = Mth.ceil((visibleHealth + Mth.ceil(player.getAbsorptionAmount())) / 20F);
            left += 10 + (rows - 1) * Math.max(3, 12 - rows);
            if (player.getArmorValue() > 0) left += 10;
            if (vehicleHearts == 0) right += 10;
            if (player.isEyeInFluid(FluidTags.WATER) || player.getAirSupply() < player.getMaxAirSupply()) right += 10;
        }
        return Math.max(left, right);
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, @NotNull DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (modeSwitchTimer > 1 && minecraft.player != null && minecraft.gameMode.getPlayerMode() != GameType.SPECTATOR && !minecraft.options.hideGui) {
            ItemStack stack = minecraft.player.getMainHandItem();
            if (IModeItem.isModeItem(stack, EquipmentSlot.MAINHAND)) {
                Component scrollTextComponent = ((IModeItem) stack.getItem()).getScrollTextComponent(stack);
                if (scrollTextComponent != null) {
                    Color color = Color.rgbad(1, 1, 1, modeSwitchTimer / (float) BASE_TIMER);
                    Font font = minecraft.gui.getFont();
                    int componentWidth = font.width(scrollTextComponent);
                    int targetShift = Math.max(59, nativeHudHeight(minecraft));
                    if (minecraft.gameMode != null && !minecraft.gameMode.canHurtPlayer()) {
                        //Same shift as done in Gui#renderSelectedItemName
                        targetShift -= 14;
                    } else if (minecraft.gui.overlayMessageTime > 0) {
                        //If we are in survival though that means our thing will end up intersecting the subtitle text if there is any,
                        // so we need to check if there is, and if so shift our target further
                        targetShift += 14;
                    }
                    //Shift the rendering to be above the previous line
                    targetShift += 13;
                    PoseStack pose = graphics.pose();
                    pose.pushPose();
                    pose.translate((graphics.guiWidth() - componentWidth) / 2F, graphics.guiHeight() - targetShift, 0);
                    graphics.drawStringWithBackdrop(font, scrollTextComponent, 0, 0, componentWidth, color.argb());
                    pose.popPose();
                }
            }
            //Only decrement the switch timer once a tick
            if (lastTick != minecraft.gui.getGuiTicks()) {
                lastTick = minecraft.gui.getGuiTicks();
                modeSwitchTimer--;
            }
        }
    }
}