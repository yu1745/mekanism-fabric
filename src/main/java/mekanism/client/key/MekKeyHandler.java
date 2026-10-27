package mekanism.client.key;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

/**
 * Fabric port delta: vanilla key state does not update inside screens, so GUI-only tooltip bindings poll raw input.
 * The description binding retains its official SHIFT modifier; vanilla's binding editor cannot encode arbitrary
 * modifiers, so modifier rebinding remains a platform limitation.
 */
public class MekKeyHandler {

    private MekKeyHandler() {
    }

    public static boolean isKeyPressed(KeyMapping keyBinding) {
        if ((keyBinding == MekanismKeyHandler.detailsKey || keyBinding == MekanismKeyHandler.descriptionKey)
              && Minecraft.getInstance().screen == null) {
            return false;
        }
        if (keyBinding == MekanismKeyHandler.descriptionKey && !Screen.hasShiftDown()) {
            return false;
        }
        if (keyBinding.isDown()) {
            return true;
        }
        //Manually check in case keyBinding#pressed just never got a chance to be updated (always the case inside screens)
        return isKeyDown(keyBinding);
    }

    public static boolean isRadialPressed() {
        return isKeyPressed(MekanismKeyHandler.handModeSwitchKey);
    }

    private static boolean isKeyDown(KeyMapping keyBinding) {
        //Fabric port delta: KeyMapping#getKey is NeoForge-only; the access-widened field is the vanilla equivalent.
        InputConstants.Key key = keyBinding.key;
        int keyCode = key.getValue();
        if (keyCode != InputConstants.UNKNOWN.getValue()) {
            long windowHandle = Minecraft.getInstance().getWindow().getWindow();
            try {
                if (key.getType() == InputConstants.Type.KEYSYM) {
                    return InputConstants.isKeyDown(windowHandle, keyCode);
                } else if (key.getType() == InputConstants.Type.MOUSE) {
                    return GLFW.glfwGetMouseButton(windowHandle, keyCode) == GLFW.GLFW_PRESS;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }
}
