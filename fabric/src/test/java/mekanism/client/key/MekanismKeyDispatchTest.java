package mekanism.client.key;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

class MekanismKeyDispatchTest {

    @Test
    void keyboardBindingsMatchKeyAndScancodeWithoutDependingOnVanillaDispatchMap() {
        KeyMapping key = new KeyMapping("test.mekanism.keyboard", GLFW.GLFW_KEY_N, "test.mekanism");
        assertTrue(MekanismKeyHandler.matches(key, GLFW.GLFW_KEY_N, 0, false));
        assertFalse(MekanismKeyHandler.matches(key, GLFW.GLFW_KEY_H, 0, false));
        key.setKey(InputConstants.Type.SCANCODE.getOrCreate(42));
        assertTrue(MekanismKeyHandler.matches(key, GLFW.GLFW_KEY_UNKNOWN, 42, false));
        assertFalse(MekanismKeyHandler.matches(key, GLFW.GLFW_KEY_UNKNOWN, 43, false));
    }

    @Test
    void scancodeEventsTrackPressRepeatReleaseAndFocusClear() {
        MekKeyHandler.clearScanCodes();
        MekKeyHandler.recordScanCode(42, GLFW.GLFW_PRESS);
        assertTrue(MekKeyHandler.isScanCodeDown(42));
        assertFalse(MekKeyHandler.isScanCodeDown(43));
        MekKeyHandler.recordScanCode(42, GLFW.GLFW_REPEAT);
        assertTrue(MekKeyHandler.isScanCodeDown(42));
        MekKeyHandler.recordScanCode(42, GLFW.GLFW_RELEASE);
        assertFalse(MekKeyHandler.isScanCodeDown(42));
        MekKeyHandler.recordScanCode(43, GLFW.GLFW_PRESS);
        MekKeyHandler.clearScanCodes();
        assertFalse(MekKeyHandler.isScanCodeDown(43));
    }

    @Test
    void mouseRebindingIsDisjointFromKeyboardInput() {
        KeyMapping key = new KeyMapping("test.mekanism.mouse", InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_4, "test.mekanism");
        assertTrue(MekanismKeyHandler.matches(key, GLFW.GLFW_MOUSE_BUTTON_4, 0, true));
        assertFalse(MekanismKeyHandler.matches(key, GLFW.GLFW_MOUSE_BUTTON_3, 0, true));
        assertFalse(MekanismKeyHandler.matches(key, GLFW.GLFW_MOUSE_BUTTON_4, 0, false));
    }
}
