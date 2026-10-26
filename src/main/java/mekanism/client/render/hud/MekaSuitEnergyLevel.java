package mekanism.client.render.hud;

import mekanism.api.energy.IEnergyContainer;
import mekanism.api.math.MathUtils;
import mekanism.client.gui.GuiUtils;
import mekanism.client.gui.element.bar.GuiBar;
import mekanism.common.config.MekanismConfig;
import mekanism.common.item.gear.ItemMekaSuitArmor;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.MekanismUtils.ResourceType;
import mekanism.common.util.StorageUtils;
import mekanism.fabric.client.hud.FabricHudHeights;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public class MekaSuitEnergyLevel implements LayeredDraw.Layer {

    public static final MekaSuitEnergyLevel INSTANCE = new MekaSuitEnergyLevel();
    private static final ResourceLocation POWER_BAR = MekanismUtils.getResource(ResourceType.GUI_BAR, "horizontal_power_long.png");

    private MekaSuitEnergyLevel() {
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, @NotNull DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.gameMode.canHurtPlayer() || minecraft.options.hideGui || !MekanismConfig.client.hudRenderMekaSuitEnergyBar.get()) {
            //canHurtPlayer is a copy of vanilla check for if the armor level can render
            return;
        }
        long capacity = 0L, stored = 0L;
        for (ItemStack stack : minecraft.player.getArmorSlots()) {
            if (stack.getItem() instanceof ItemMekaSuitArmor) {
                IEnergyContainer container = StorageUtils.getEnergyContainer(stack, 0);
                if (container != null) {
                    capacity = MathUtils.addClamped(capacity, container.getMaxEnergy());
                    stored = MathUtils.addClamped(stored, container.getEnergy());
                }
            }
        }
        if (capacity != 0L) {
            //Fabric port: vanilla has no NeoForge leftHeight stack, so the bar positions against the replicated
            //left-column height (FabricHudHeights). The official `leftHeight += 8` mutation has no downstream
            //in-family consumer here - MekanismStatusOverlay computes its own native height - which can cost an
            //8px overlap between this bar and the mode text while both show (recorded delta).
            int x = graphics.guiWidth() / 2 - 91;
            int y = graphics.guiHeight() - FabricHudHeights.leftColumnHeight(minecraft) + 2;
            int length = (int) Math.round(((double) stored / capacity) * 79);
            GuiUtils.renderExtendedTexture(graphics, GuiBar.BAR, 2, 2, x, y, 81, 6);
            graphics.blit(POWER_BAR, x + 1, y + 1, length, 4, 0, 0, length, 4, 79, 4);
        }
    }
}