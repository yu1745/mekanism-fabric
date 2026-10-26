package mekanism.client.key;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * Fabric port delta: NeoForge's {@code IKeyConflictContext}/{@code KeyModifier} tracking has no Fabric equivalent, and vanilla {@link KeyMapping} state
 * does not update while a screen is open — which is exactly where tooltips (and the radial menu) query pressed keys. The context/modifier branches are
 * therefore replaced by an unconditional raw-input poll of the bound key, which keeps the observable behavior (the key answers while held, in game and
 * in screens) at the cost of vanilla's flat binding model without modifier-qualified defaults.
 */
public class MekKeyHandler {

    private MekKeyHandler() {
    }

    public static boolean isKeyPressed(KeyMapping keyBinding) {
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
        InputConstants.Key key = keyBinding.getKey();
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
