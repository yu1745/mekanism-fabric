package mekanism.common.tile.interfaces;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TileInterfaceContractTest {

    @Test
    void modeAndActionCallbacksDispatchExactlyWithoutTransformingTankIndexes() {
        AtomicInteger dumpCalls = new AtomicInteger();
        IHasDumpButton dumpButton = dumpCalls::incrementAndGet;
        dumpButton.dump();
        assertEquals(1, dumpCalls.get());

        List<Integer> tankIndexes = new ArrayList<>();
        IHasGasMode gasMode = tankIndexes::add;
        gasMode.nextMode(0);
        gasMode.nextMode(-3);
        assertEquals(List.of(0, -3), tankIndexes);

        class ModeProbe implements IHasMode {

            private int nextCalls;
            private int previousCalls;

            @Override
            public void nextMode() {
                nextCalls++;
            }

            @Override
            public void previousMode() {
                previousCalls++;
            }
        }
        ModeProbe mode = new ModeProbe();
        mode.nextMode();
        assertEquals(1, mode.nextCalls);
        assertEquals(0, mode.previousCalls);
        mode.previousMode();
        assertEquals(1, mode.nextCalls);
        assertEquals(1, mode.previousCalls);
    }

    @Test
    void visualizationDefaultsOverridesAndToggleStateStayLocal() {
        class VisualizationProbe implements IHasVisualization {

            private boolean rendering;
            private int toggleCalls;

            @Override
            public boolean isClientRendering() {
                return rendering;
            }

            @Override
            public void toggleClientRendering() {
                toggleCalls++;
                rendering = !rendering;
            }
        }
        class HiddenVisualizationProbe extends VisualizationProbe {

            @Override
            public boolean canDisplayVisuals() {
                return false;
            }
        }

        VisualizationProbe visible = new VisualizationProbe();
        assertTrue(visible.canDisplayVisuals());
        assertFalse(visible.isClientRendering());
        visible.toggleClientRendering();
        assertTrue(visible.isClientRendering());
        assertEquals(1, visible.toggleCalls);
        visible.toggleClientRendering();
        assertFalse(visible.isClientRendering());
        assertEquals(2, visible.toggleCalls);
        assertFalse(new HiddenVisualizationProbe().canDisplayVisuals());
    }

    @Test
    void activeContractPreservesDefaultsDelayAndRawStateRoundTrip() {
        class ActiveProbe implements ITileActive {

            private boolean active;

            @Override
            public boolean getActive() {
                return active;
            }

            @Override
            public void setActive(boolean active) {
                this.active = active;
            }
        }
        class NonActivatableProbe extends ActiveProbe {

            @Override
            public boolean isActivatable() {
                return false;
            }
        }

        assertSame(ITileActive.NO_DELAY, ITileActive.NO_DELAY);
        assertEquals(0, ITileActive.NO_DELAY.getAsInt());
        assertEquals(0, ITileActive.NO_DELAY.getAsInt());

        ActiveProbe active = new ActiveProbe();
        assertTrue(active.isActivatable());
        assertFalse(active.getActive());
        active.setActive(true);
        assertTrue(active.getActive());
        active.setActive(false);
        assertFalse(active.getActive());
        assertFalse(new NonActivatableProbe().isActivatable());
    }

    @Test
    void comparatorSupportDefaultAndLevelsRemainIndependent() {
        class ComparatorProbe implements IComparatorSupport {

            private final int redstoneLevel;
            private final int currentRedstoneLevel;

            private ComparatorProbe(int redstoneLevel, int currentRedstoneLevel) {
                this.redstoneLevel = redstoneLevel;
                this.currentRedstoneLevel = currentRedstoneLevel;
            }

            @Override
            public int getRedstoneLevel() {
                return redstoneLevel;
            }

            @Override
            public int getCurrentRedstoneLevel() {
                return currentRedstoneLevel;
            }
        }
        class UnsupportedComparatorProbe extends ComparatorProbe {

            private UnsupportedComparatorProbe(int redstoneLevel, int currentRedstoneLevel) {
                super(redstoneLevel, currentRedstoneLevel);
            }

            @Override
            public boolean supportsComparator() {
                return false;
            }
        }

        ComparatorProbe supported = new ComparatorProbe(Integer.MIN_VALUE, Integer.MAX_VALUE);
        assertTrue(supported.supportsComparator());
        assertEquals(Integer.MIN_VALUE, supported.getRedstoneLevel());
        assertEquals(Integer.MAX_VALUE, supported.getCurrentRedstoneLevel());

        ComparatorProbe unsupported = new UnsupportedComparatorProbe(Integer.MAX_VALUE, Integer.MIN_VALUE);
        assertFalse(unsupported.supportsComparator());
        assertEquals(Integer.MAX_VALUE, unsupported.getRedstoneLevel());
        assertEquals(Integer.MIN_VALUE, unsupported.getCurrentRedstoneLevel());
    }
}
