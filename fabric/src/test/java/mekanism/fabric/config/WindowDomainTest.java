package mekanism.fabric.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import mekanism.common.config.MekanismConfigTranslations;
import mekanism.common.inventory.container.SelectedWindowData;
import mekanism.common.inventory.container.SelectedWindowData.WindowPosition;
import mekanism.common.inventory.container.SelectedWindowData.WindowType;
import mekanism.common.inventory.warning.ISupportsWarning;
import mekanism.common.inventory.warning.WarningTracker;
import mekanism.common.inventory.warning.WarningTracker.WarningType;
import mekanism.common.util.text.TextUtils;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WindowDomainTest {
    @TempDir
    java.nio.file.Path temporaryDirectory;

    @Test
    void enumPathsExtraDataCodecAndIdentityMatchOfficialDomain() {
        assertEquals(10, WindowType.values().length);
        assertEquals(List.of("crafting0", "crafting1", "crafting2"),
              WindowType.CRAFTING.getSavePaths().stream().map(WindowType.ConfigSaveData::savePath).toList());
        assertEquals(3, WindowType.CRAFTING.getSavePaths().size());
        assertTrue(WindowType.CRAFTING.isValid((byte) 2));
        assertFalse(WindowType.CRAFTING.isValid((byte) 3));
        SelectedWindowData normalized = new SelectedWindowData(WindowType.CRAFTING, (byte) 9);
        assertEquals(0, normalized.extraData);
        assertEquals(new SelectedWindowData(WindowType.CRAFTING), normalized);
        assertNotEquals(normalized, new SelectedWindowData(WindowType.RENAME));
        ByteBuf buffer = Unpooled.buffer();
        WindowType.STREAM_CODEC.encode(buffer, WindowType.UPGRADE);
        assertEquals(WindowType.UPGRADE, WindowType.STREAM_CODEC.decode(buffer));
        buffer.release();
    }

    @Test
    void positionsAreFreshAndEachChangedUpdateCreatesExactlyOneSaveInXYPinOrder() {
        try (FabricClientConfigLifecycle lifecycle = FabricClientConfigLifecycle.create(temporaryDirectory)) {
            SelectedWindowData selected = new SelectedWindowData(WindowType.TRANSPORTER_CONFIG);
            assertEquals(new WindowPosition(Integer.MAX_VALUE, Integer.MAX_VALUE, false), selected.getLastPosition());
            selected.updateLastPosition(-4, 8, true);
            assertEquals(new WindowPosition(-4, 8, true), selected.getLastPosition());
            assertEquals(1, lifecycle.windowConfig().logicalSaveJobs());
            selected.updateLastPosition(-4, 9, true);
            assertEquals(new WindowPosition(-4, 9, true), selected.getLastPosition());
            assertEquals(2, lifecycle.windowConfig().logicalSaveJobs());
            selected.updateLastPosition(-4, 9, true);
            assertEquals(2, lifecycle.windowConfig().logicalSaveJobs());
        }
    }

    @Test
    void unpinnableAndUnspecifiedBehaviorRemainDistinctAndApplicablePreInitFails() {
        SelectedWindowData unspecified = SelectedWindowData.UNSPECIFIED;
        assertEquals(new WindowPosition(Integer.MAX_VALUE, Integer.MAX_VALUE, false), unspecified.getLastPosition());
        unspecified.updateLastPosition(1, 2, true);
        assertThrows(IllegalStateException.class, () -> new SelectedWindowData(WindowType.COLOR).getLastPosition());
        try (FabricClientConfigLifecycle ignored = FabricClientConfigLifecycle.create(temporaryDirectory)) {
            SelectedWindowData color = new SelectedWindowData(WindowType.COLOR);
            color.updateLastPosition(3, 4, true);
            assertEquals(new WindowPosition(3, 4, false), color.getLastPosition());
            assertFalse(color.wasPinned());
        }
    }

    @Test
    void warningRegistrationAndCompoundSupplierShortCircuitExactly() {
        WarningTracker tracker = new WarningTracker();
        BooleanSupplier supplier = () -> true;
        assertSame(supplier, tracker.trackWarning(WarningType.NO_MATCHING_RECIPE, supplier));
        assertTrue(tracker.hasWarning());
        tracker.clearTrackedWarnings();
        assertFalse(tracker.hasWarning());

        AtomicInteger secondCalls = new AtomicInteger();
        BooleanSupplier compound = ISupportsWarning.compound(() -> true, () -> {
            secondCalls.incrementAndGet();
            return true;
        });
        assertTrue(compound.getAsBoolean());
        assertEquals(0, secondCalls.get());
        assertSame(supplier, ISupportsWarning.compound(null, supplier));
    }

    @Test
    void warningsPreserveEnumOrderDeduplicateTypesAndSuppressReducedEnergyTail() {
        WarningTracker tracker = new WarningTracker();
        tracker.trackWarning(WarningType.NO_SPACE_IN_OUTPUT, () -> true);
        tracker.trackWarning(WarningType.NO_SPACE_IN_OUTPUT, () -> true);
        tracker.trackWarning(WarningType.NOT_ENOUGH_ENERGY, () -> true);
        tracker.trackWarning(WarningType.NOT_ENOUGH_ENERGY_REDUCED_RATE, () -> true);
        tracker.trackWarning(WarningType.REDSTONE_SIGNAL_PRESENT, () -> true);
        List<Component> warnings = tracker.getWarnings();
        assertEquals(2, warnings.size());
        WarningTracker outputOnly = new WarningTracker();
        outputOnly.trackWarning(WarningType.NO_SPACE_IN_OUTPUT, () -> true);
        WarningTracker energyOnly = new WarningTracker();
        energyOnly.trackWarning(WarningType.NOT_ENOUGH_ENERGY, () -> true);
        assertEquals(outputOnly.getWarnings().getFirst(), warnings.get(0));
        assertEquals(energyOnly.getWarnings().getFirst(), warnings.get(1));
    }

    @Test
    void translationKeysAndTextUtilitiesRetainOfficialFormatting() {
        assertEquals("Color Window Position", WindowType.COLOR.getSavePaths().getFirst().title());
        assertTrue(WindowType.SIDE_CONFIG.getSavePaths().getFirst().tooltip().contains("whether it was pinned"));
        assertEquals("configuration.mekanism.client.last_window_positions",
              MekanismConfigTranslations.CLIENT_LAST_WINDOW_POSITIONS.getTranslationKey());
        assertEquals("Side Config", TextUtils.formatAndCapitalize("side_config"));
        assertEquals("0x00FF", TextUtils.hex(true, 2, 255));
        assertEquals("FFFF", TextUtils.hex(false, 2, -1));
    }
}
