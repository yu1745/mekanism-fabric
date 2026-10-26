package mekanism.fabric.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.concurrent.atomic.AtomicBoolean;
import mekanism.client.render.hud.MekaSuitEnergyLevel;
import mekanism.client.render.hud.MekanismHUD;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.LayeredDraw;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Fabric port of the two official HUD layer registrations whose NeoForge placements have no existing platform seam
 * (the status overlay and the radiation tint ride {@code ModeStatusOverlayMixin}/{@code RadiationOverlayMixin}).
 * The 1.21.1 fabric API has no layered-HUD insertion (only the tail-applied HudRenderCallback), so these two ride the
 * vanilla layer construction, mirroring the NeoForge placement contracts:
 *
 * <ul>
 * <li>MekaSuitEnergyLevel above the armor level (official registerAbove(ARMOR_LEVEL)) - fabric vanilla renders the
 * armor/air levels inside renderHotbarAndDecorations, so the energy bar rides right after that call and stays between
 * the armor level and the experience level, exactly where NeoForge slots it.</li>
 * <li>MekanismHUD above the subtitle overlay (official registerAbove(SUBTITLE_OVERLAY)) - the last group-2 add of the
 * vanilla draw.</li>
 * </ul>
 */
@Mixin(Gui.class)
public class FabricHudLayersMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger("Mekanism/FabricHudLayers");
    private static final AtomicBoolean PROOF = new AtomicBoolean();

    //Single-arg LayeredDraw.add call sites in the Gui constructor, in declaration order (0-based):
    //0 camera overlays, 1 crosshair, 2 hotbar+armor levels, 3 experience level, 4 effects, 5 boss bar,
    //6 demo overlay, 7 debug overlay, 8 scoreboard sidebar, 9 overlay message, 10 title, 11 chat, 12 tab list,
    //13 subtitle overlay, 14 sleep overlay (on the outer draw).
    private static final int ADD_HOTBAR_AND_DECORATIONS = 2;
    private static final int ADD_SUBTITLE_OVERLAY = 13;

    @WrapOperation(method = "<init>", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/client/gui/LayeredDraw;add(Lnet/minecraft/client/gui/LayeredDraw$Layer;)Lnet/minecraft/client/gui/LayeredDraw;",
          ordinal = ADD_HOTBAR_AND_DECORATIONS))
    private LayeredDraw mekanismEnergyLevelAboveArmor(LayeredDraw instance, LayeredDraw.Layer vanilla, Operation<LayeredDraw> operation) {
        logProof();
        return operation.call(instance, (LayeredDraw.Layer) (graphics, delta) -> {
            vanilla.render(graphics, delta);
            MekaSuitEnergyLevel.INSTANCE.render(graphics, delta);
        });
    }

    @WrapOperation(method = "<init>", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/client/gui/LayeredDraw;add(Lnet/minecraft/client/gui/LayeredDraw$Layer;)Lnet/minecraft/client/gui/LayeredDraw;",
          ordinal = ADD_SUBTITLE_OVERLAY))
    private LayeredDraw mekanismHudAboveSubtitles(LayeredDraw instance, LayeredDraw.Layer vanilla, Operation<LayeredDraw> operation) {
        logProof();
        return operation.call(instance, (LayeredDraw.Layer) (graphics, delta) -> {
            vanilla.render(graphics, delta);
            MekanismHUD.INSTANCE.render(graphics, delta);
        });
    }

    private static void logProof() {
        if (PROOF.compareAndSet(false, true)) {
            LOGGER.info("MEKANISM_FABRIC_HUD_PROOF layers=2 (energy_level, hud)");
        }
    }
}
