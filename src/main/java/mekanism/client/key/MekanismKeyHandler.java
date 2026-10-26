package mekanism.client.key;

import mekanism.common.MekanismLang;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/**
 * Fabric port delta, following the Task031 declaration-first precedent: all ten official keys keep their names, translation keys, categories and default
 * GLFW codes and are registered for real, but the NeoForge {@code MekKeyBindingBuilder} press/release callback system has no vanilla equivalent —
 * vanilla {@link KeyMapping} is declarative only, has no key-conflict-context system, and cannot encode modifier-qualified defaults ({@code SHIFT+N}
 * registers as plain {@code N}, user-rebindable). The original press behaviors — mode-change and curios packets for the five equipment keys and the
 * module tweaker GUI, {@code MekanismClient} boost-key sync, and the HUD config toggle — are deferred to the client input vertical with their exact
 * call chains intact, and are not stubbed here.
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
