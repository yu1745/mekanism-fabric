package mekanism.fabric.client.model;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;

class CreativeSearchMixinClientTest {
    @Test
    void creativeScreenTransformsWithLocalSearchMixin() throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        assertNotNull(Class.forName("net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen"));
    }
}
