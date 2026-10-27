package mekanism.api.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MekanismEventBusTest {

    private static class Parent extends MekanismEvent {
    }

    private static class Child extends Parent {
    }

    private static class Sibling extends Parent {
    }

    @Test
    void parentAndConcreteListenersReceiveChildInRegistrationOrder() {
        List<String> calls = new ArrayList<>();
        MekanismEventBus.addListener(Parent.class, event -> {
            calls.add("parent");
            event.setCanceled(true);
        });
        MekanismEventBus.addListener(Child.class, event -> calls.add("child"));
        MekanismEventBus.addListener(Sibling.class, event -> calls.add("sibling"));
        Child child = new Child();
        assertSame(child, MekanismEventBus.post(child));
        assertTrue(child.isCanceled());
        assertEquals(List.of("parent", "child"), calls);
    }
}
