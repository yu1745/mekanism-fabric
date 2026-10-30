package mekanism.additions.client;

import mekanism.additions.common.AdditionsLang;
import mekanism.additions.common.config.MekanismAdditionsConfig;
import net.minecraft.client.KeyMapping;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import org.lwjgl.glfw.GLFW;

public class AdditionsKeyHandler {

    public static final KeyMapping voiceKey = new net.minecraft.client.ToggleKeyMapping(AdditionsLang.KEY_VOICE.getTranslationKey(), GLFW.GLFW_KEY_U,
          mekanism.common.MekanismLang.MEKANISM.getTranslationKey(), () -> !MekanismAdditionsConfig.client.pushToTalk.getOrDefault());

    public static void registerKeybindings() {
        KeyBindingHelper.registerKeyBinding(voiceKey);
    }
}