package mekanism.api.container;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import mekanism.api.Action;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

/** Fabric-local forwarding tests for the unchanged official container callback contracts. */
class ContainerInteractionTest {

    @Test
    void genericInteractionForwardsIdentityIndexSideAndActionAndReturnsExactResult() {
        int[] indexes = {-37, 901};
        Object[] stacks = {new Object(), new Object()};
        Direction[] sides = {null, Direction.WEST};
        Action[] actions = {Action.SIMULATE, Action.EXECUTE};
        Object[] results = {new Object(), new Object()};
        int[] call = {0};
        ContainerInteraction<Object> interaction = (container, stack, side, action) -> {
            int current = call[0]++;
            assertEquals(indexes[current], container);
            assertSame(stacks[current], stack);
            assertSame(sides[current], side);
            assertSame(actions[current], action);
            return results[current];
        };

        for (int i = 0; i < indexes.length; i++) {
            assertSame(results[i], interaction.interact(indexes[i], stacks[i], sides[i], actions[i]));
        }
        assertEquals(indexes.length, call[0]);
    }

    @Test
    void getterForwardsArbitraryIndexAndNullableOrPhysicalSideAndReturnsExactObject() {
        int[] indexes = {-83, 4_096};
        Direction[] sides = {null, Direction.UP};
        Object[] stored = {new Object(), new Object()};
        int[] call = {0};
        InContainerGetter<Object> getter = (container, side) -> {
            int current = call[0]++;
            assertEquals(indexes[current], container);
            assertSame(sides[current], side);
            return stored[current];
        };

        for (int i = 0; i < indexes.length; i++) {
            assertSame(stored[i], getter.getStored(indexes[i], sides[i]));
        }
        assertEquals(indexes.length, call[0]);
    }

    @Test
    void intInteractionForwardsRawBoundariesWithoutNormalization() {
        int[] indexes = {-19, 0, 73, Integer.MAX_VALUE};
        int[] amounts = {Integer.MIN_VALUE, -1, 0, Integer.MAX_VALUE};
        Direction[] sides = {null, Direction.NORTH, Direction.DOWN, null};
        Action[] actions = {Action.SIMULATE, Action.EXECUTE, Action.SIMULATE, Action.EXECUTE};
        Object[] results = {new Object(), new Object(), new Object(), new Object()};
        int[] call = {0};
        IntContainerInteraction<Object> interaction = (container, amount, side, action) -> {
            int current = call[0]++;
            assertEquals(indexes[current], container);
            assertEquals(amounts[current], amount);
            assertSame(sides[current], side);
            assertSame(actions[current], action);
            return results[current];
        };

        for (int i = 0; i < amounts.length; i++) {
            assertSame(results[i], interaction.interact(indexes[i], amounts[i], sides[i], actions[i]));
        }
        assertEquals(amounts.length, call[0]);
    }

    @Test
    void longInteractionForwardsTrueLongAndSignedBoundariesWithoutNormalization() {
        int[] indexes = {Integer.MIN_VALUE, -5, 0, 81, Integer.MAX_VALUE};
        long[] amounts = {Long.MIN_VALUE, -1L, 0L, (1L << 53) + 1L, Long.MAX_VALUE};
        Direction[] sides = {null, Direction.SOUTH, Direction.EAST, null, Direction.UP};
        Action[] actions = {Action.SIMULATE, Action.EXECUTE, Action.SIMULATE, Action.EXECUTE, Action.SIMULATE};
        Object[] results = {new Object(), new Object(), new Object(), new Object(), new Object()};
        int[] call = {0};
        LongContainerInteraction<Object> interaction = (container, amount, side, action) -> {
            int current = call[0]++;
            assertEquals(indexes[current], container);
            assertEquals(amounts[current], amount);
            assertSame(sides[current], side);
            assertSame(actions[current], action);
            return results[current];
        };

        for (int i = 0; i < amounts.length; i++) {
            assertSame(results[i], interaction.interact(indexes[i], amounts[i], sides[i], actions[i]));
        }
        assertEquals(amounts.length, call[0]);
    }
}
