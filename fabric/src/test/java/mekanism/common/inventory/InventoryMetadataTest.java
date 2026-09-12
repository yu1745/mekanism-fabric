package mekanism.common.inventory;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import mekanism.common.inventory.container.slot.ContainerSlotType;
import mekanism.common.inventory.container.slot.SlotOverlay;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class InventoryMetadataTest {

    private static final Component DEFAULT_SHORT_NAME = Component.literal("default short");
    private static final Component DEFAULT_TOOLTIP = Component.literal("default tooltip");
    private static final Component CUSTOM_SHORT_NAME = Component.literal("custom short");
    private static final Component CUSTOM_TOOLTIP = Component.literal("custom tooltip");
    private static final ResourceLocation DROPDOWN_ICON = ResourceLocation.fromNamespaceAndPath("test", "dropdown");
    private static final Component TOGGLE_TOOLTIP = Component.literal("toggle tooltip");
    private static final ResourceLocation TOGGLE_ICON = ResourceLocation.fromNamespaceAndPath("test", "toggle");

    @Test
    void containerSlotTypesHaveExactOfficialOrderOrdinalsAndLookup() {
        ContainerSlotType[] expected = {
              ContainerSlotType.IGNORED,
              ContainerSlotType.NORMAL,
              ContainerSlotType.POWER,
              ContainerSlotType.INPUT,
              ContainerSlotType.EXTRA,
              ContainerSlotType.OUTPUT,
              ContainerSlotType.VALIDITY
        };

        assertArrayEquals(expected, ContainerSlotType.values());
        for (int ordinal = 0; ordinal < expected.length; ordinal++) {
            ContainerSlotType type = expected[ordinal];
            assertEquals(ordinal, type.ordinal());
            assertSame(type, ContainerSlotType.valueOf(type.name()));
        }
    }

    @Test
    void slotOverlaysHaveExactOfficialOrderAndResourceIds() {
        SlotOverlay[] expected = {
              SlotOverlay.MINUS,
              SlotOverlay.PLUS,
              SlotOverlay.POWER,
              SlotOverlay.INPUT,
              SlotOverlay.OUTPUT,
              SlotOverlay.CHECK,
              SlotOverlay.X,
              SlotOverlay.FORMULA,
              SlotOverlay.UPGRADE,
              SlotOverlay.SELECT,
              SlotOverlay.MODULE
        };
        String[] expectedIds = {
              "mekanism:gui/slot/overlay_minus.png",
              "mekanism:gui/slot/overlay_plus.png",
              "mekanism:gui/slot/overlay_power.png",
              "mekanism:gui/slot/overlay_input.png",
              "mekanism:gui/slot/overlay_output.png",
              "mekanism:gui/slot/overlay_check.png",
              "mekanism:gui/slot/overlay_x.png",
              "mekanism:gui/slot/overlay_formula.png",
              "mekanism:gui/slot/overlay_upgrade.png",
              "mekanism:gui/slot/overlay_select.png",
              "mekanism:gui/slot/overlay_module.png"
        };

        assertArrayEquals(expected, SlotOverlay.values());
        for (int index = 0; index < expected.length; index++) {
            assertEquals(expectedIds[index], expected[index].getTexture().toString());
        }
    }

    @Test
    void slotOverlayDimensionsLookupAndValuesArraysRemainExact() {
        SlotOverlay[] first = SlotOverlay.values();
        SlotOverlay[] second = SlotOverlay.values();
        assertNotSame(first, second);
        assertEquals(first.length, second.length);
        for (int index = 0; index < first.length; index++) {
            SlotOverlay overlay = second[index];
            assertSame(first[index], overlay);
            assertEquals(18, overlay.getWidth());
            assertEquals(18, overlay.getHeight());
            assertSame(overlay, SlotOverlay.valueOf(overlay.name()));
        }
        first[0] = SlotOverlay.MODULE;
        assertSame(SlotOverlay.MINUS, second[0]);
    }

    @Test
    void guiComponentEnumsForwardExactMetadataIdentities() {
        assertSame(DEFAULT_SHORT_NAME, DropdownChoice.DEFAULT.getShortName());
        assertSame(DEFAULT_TOOLTIP, DropdownChoice.DEFAULT.getTooltip());
        assertNull(DropdownChoice.DEFAULT.getIcon());
        assertSame(CUSTOM_SHORT_NAME, DropdownChoice.CUSTOM.getShortName());
        assertSame(CUSTOM_TOOLTIP, DropdownChoice.CUSTOM.getTooltip());
        assertSame(DROPDOWN_ICON, DropdownChoice.CUSTOM.getIcon());
        assertSame(TOGGLE_TOOLTIP, ToggleChoice.ENABLED.getTooltip());
        assertSame(TOGGLE_ICON, ToggleChoice.ENABLED.getIcon());
    }

    private enum DropdownChoice implements GuiComponents.IDropdownEnum<DropdownChoice> {
        DEFAULT(DEFAULT_SHORT_NAME, DEFAULT_TOOLTIP),
        CUSTOM(CUSTOM_SHORT_NAME, CUSTOM_TOOLTIP) {
            @Override
            public ResourceLocation getIcon() {
                return DROPDOWN_ICON;
            }
        };

        private final Component shortName;
        private final Component tooltip;

        DropdownChoice(Component shortName, Component tooltip) {
            this.shortName = shortName;
            this.tooltip = tooltip;
        }

        @Override
        public Component getShortName() {
            return shortName;
        }

        @Override
        public Component getTooltip() {
            return tooltip;
        }
    }

    private enum ToggleChoice implements GuiComponents.IToggleEnum<ToggleChoice> {
        ENABLED(TOGGLE_TOOLTIP, TOGGLE_ICON);

        private final Component tooltip;
        private final ResourceLocation icon;

        ToggleChoice(Component tooltip, ResourceLocation icon) {
            this.tooltip = tooltip;
            this.icon = icon;
        }

        @Override
        public Component getTooltip() {
            return tooltip;
        }

        @Override
        public ResourceLocation getIcon() {
            return icon;
        }
    }
}
