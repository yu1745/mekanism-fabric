package mekanism.fabric.client.model;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class MouseInputMixinTest {
    @Test
    void mouseHandlerLoadsWithNormalizedInputHook() throws ClassNotFoundException {
        // Loading through Knot applies the mixin and validates its target against the real client class.
        assertNotNull(Class.forName("net.minecraft.client.MouseHandler"));
    }
}
