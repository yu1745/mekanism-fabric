package mekanism.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ActionTest {

    @Test
    void exposesExecutionAndSimulationSemantics() {
        assertTrue(Action.EXECUTE.execute());
        assertFalse(Action.EXECUTE.simulate());
        assertFalse(Action.SIMULATE.execute());
        assertTrue(Action.SIMULATE.simulate());
    }

    @Test
    void combinesWithoutLeakingPlatformTransactionTypes() {
        assertEquals(Action.EXECUTE, Action.EXECUTE.combine(true));
        assertEquals(Action.SIMULATE, Action.EXECUTE.combine(false));
        assertEquals(Action.SIMULATE, Action.SIMULATE.combine(true));
        assertEquals(Action.EXECUTE, Action.get(true));
        assertEquals(Action.SIMULATE, Action.get(false));
    }
}
