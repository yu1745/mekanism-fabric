package mekanism.client.key;

import mekanism.client.MekanismClient;
import mekanism.client.sound.SoundHandler;
import mekanism.common.MekanismLang;
import mekanism.common.base.KeySync;
import mekanism.common.config.MekanismConfig;
import mekanism.common.integration.curios.CuriosIntegration;
import mekanism.common.item.interfaces.IChemicalItem;
import mekanism.common.inventory.container.ModuleTweakerContainer;
import mekanism.common.item.interfaces.IModeItem;
import mekanism.common.network.PacketUtils;
import mekanism.common.network.to_server.PacketModeChange;
import mekanism.common.network.to_server.PacketModeChangeCurios;
import mekanism.common.network.to_server.PacketOpenGui;
import mekanism.common.registries.MekanismSounds;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import org.lwjgl.glfw.GLFW;

/**
 * Fabric key mappings and the Mekanism-owned keyboard/mouse dispatch for the official in-game key actions.
 * Optional accessory mode changes use the direct Accessories slot when the mod is present. Vanilla bindings do not encode
 * the official description key's SHIFT modifier; tooltip polling handles that default separately.
 */
public class MekanismKeyHandler {

    private static final String CATEGORY = MekanismLang.MEKANISM.getTranslationKey();

    public static final KeyMapping handModeSwitchKey = new KeyMapping(MekanismLang.KEY_HAND_MODE.getTranslationKey(), GLFW.GLFW_KEY_N, CATEGORY);
    public static final KeyMapping headModeSwitchKey = new KeyMapping(MekanismLang.KEY_HEAD_MODE.getTranslationKey(), GLFW.GLFW_KEY_V, CATEGORY);
    public static final KeyMapping chestModeSwitchKey = new KeyMapping(MekanismLang.KEY_CHEST_MODE.getTranslationKey(), GLFW.GLFW_KEY_G, CATEGORY);
    public static final KeyMapping legsModeSwitchKey = new KeyMapping(MekanismLang.KEY_LEGS_MODE.getTranslationKey(), GLFW.GLFW_KEY_J, CATEGORY);
    public static final KeyMapping feetModeSwitchKey = new KeyMapping(MekanismLang.KEY_FEET_MODE.getTranslationKey(), GLFW.GLFW_KEY_B, CATEGORY);
    public static final KeyMapping detailsKey = new KeyMapping(MekanismLang.KEY_DETAILS_MODE.getTranslationKey(), GLFW.GLFW_KEY_LEFT_SHIFT, CATEGORY);
    public static final KeyMapping descriptionKey = new KeyMapping(MekanismLang.KEY_DESCRIPTION_MODE.getTranslationKey(), GLFW.GLFW_KEY_N, CATEGORY);
    public static final KeyMapping moduleTweakerKey = new KeyMapping(MekanismLang.KEY_MODULE_TWEAKER.getTranslationKey(), GLFW.GLFW_KEY_BACKSLASH, CATEGORY);
    public static final KeyMapping boostKey = new KeyMapping(MekanismLang.KEY_BOOST.getTranslationKey(), GLFW.GLFW_KEY_LEFT_CONTROL, CATEGORY);
    public static final KeyMapping hudKey = new KeyMapping(MekanismLang.KEY_HUD.getTranslationKey(), GLFW.GLFW_KEY_H, CATEGORY);

    /** Dispatch raw input instead of vanilla's single-key map so GUI-only and in-game bindings can share N. */
    public static void onInput(int key, int scanCode, int action, boolean mouse) {
        if (!mouse) {
            MekKeyHandler.recordScanCode(scanCode, action);
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (action == GLFW.GLFW_RELEASE && matches(boostKey, key, scanCode, mouse)) {
            MekanismClient.updateKey(false, KeySync.BOOST);
        }
        if (action != GLFW.GLFW_PRESS || minecraft.screen != null || minecraft.getOverlay() != null) {
            return;
        }
        Player player = minecraft.player;
        if (player == null) {
            return;
        }
        if (matches(handModeSwitchKey, key, scanCode, mouse)) {
            if (IModeItem.isModeItem(player, EquipmentSlot.MAINHAND, false)) {
                PacketUtils.sendToServer(new PacketModeChange(EquipmentSlot.MAINHAND, player.isShiftKeyDown()));
            } else if (!IModeItem.isModeItem(player, EquipmentSlot.MAINHAND) && IModeItem.isModeItem(player, EquipmentSlot.OFFHAND, false)) {
                PacketUtils.sendToServer(new PacketModeChange(EquipmentSlot.OFFHAND, player.isShiftKeyDown()));
            }
        }
        if (matches(headModeSwitchKey, key, scanCode, mouse)) {
            handlePotentialModeItem(player, EquipmentSlot.HEAD);
        }
        if (matches(chestModeSwitchKey, key, scanCode, mouse)) {
            handlePotentialModeItem(player, EquipmentSlot.CHEST);
        }
        if (matches(legsModeSwitchKey, key, scanCode, mouse)) {
            handlePotentialModeItem(player, EquipmentSlot.LEGS);
        }
        if (matches(feetModeSwitchKey, key, scanCode, mouse)) {
            handlePotentialModeItem(player, EquipmentSlot.FEET);
        }
        if (matches(moduleTweakerKey, key, scanCode, mouse) && ModuleTweakerContainer.hasTweakableItem(player)) {
            PacketUtils.sendToServer(new PacketOpenGui(PacketOpenGui.GuiType.MODULE_TWEAKER));
        }
        if (matches(boostKey, key, scanCode, mouse)) {
            MekanismClient.updateKey(true, KeySync.BOOST);
        }
        if (matches(hudKey, key, scanCode, mouse)) {
            MekanismConfig.client.enableHUD.set(!MekanismConfig.client.enableHUD.get());
            MekanismConfig.client.save();
        }
    }

    static boolean matches(KeyMapping mapping, int key, int scanCode, boolean mouse) {
        return mouse ? mapping.matchesMouse(key) : mapping.matches(key, scanCode);
    }

    private static void handlePotentialModeItem(Player player, EquipmentSlot slot) {
        if (IModeItem.isModeItem(player, slot)) {
            PacketUtils.sendToServer(new PacketModeChange(slot, player.isShiftKeyDown()));
            SoundHandler.playSound(MekanismSounds.HYDRAULIC);
        } else if (CuriosIntegration.isLoaded()) {
            CuriosIntegration.EquippedSlot accessory = CuriosIntegration.findFirstSlot(player, stack ->
                  player.getEquipmentSlotForItem(stack) == slot && IModeItem.isModeItem(stack, slot)
                        && (!(stack.getItem() instanceof IChemicalItem item) || item.hasChemical(stack)));
            if (accessory != null) {
                PacketUtils.sendToServer(new PacketModeChangeCurios(accessory.slotType(), accessory.index(), player.isShiftKeyDown()));
                SoundHandler.playSound(MekanismSounds.HYDRAULIC);
            }
        }
    }

    public static void registerKeybindings() {
        KeyBindingHelper.registerKeyBinding(handModeSwitchKey);
        KeyBindingHelper.registerKeyBinding(headModeSwitchKey);
        KeyBindingHelper.registerKeyBinding(chestModeSwitchKey);
        KeyBindingHelper.registerKeyBinding(legsModeSwitchKey);
        KeyBindingHelper.registerKeyBinding(feetModeSwitchKey);
        KeyBindingHelper.registerKeyBinding(detailsKey);
        KeyBindingHelper.registerKeyBinding(descriptionKey);
        KeyBindingHelper.registerKeyBinding(moduleTweakerKey);
        KeyBindingHelper.registerKeyBinding(boostKey);
        KeyBindingHelper.registerKeyBinding(hudKey);
    }
}
