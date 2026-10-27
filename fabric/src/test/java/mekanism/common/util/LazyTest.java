package mekanism.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class LazyTest {

    @Test
    void concurrentCallsShareOnePublishedValue() throws Exception {
        int count = 8;
        var callers = new CountDownLatch(count);
        var supplierEntered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var calls = new AtomicInteger();
        Lazy<Object> lazy = Lazy.of(() -> {
            calls.incrementAndGet();
            supplierEntered.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("supplier was not released");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
            return new Object();
        });
        try (var executor = Executors.newFixedThreadPool(count)) {
            var results = new ArrayList<java.util.concurrent.Future<Object>>();
            for (int i = 0; i < count; i++) {
                results.add(executor.submit(() -> {
                    callers.countDown();
                    return lazy.get();
                }));
            }
            try {
                assertTrue(callers.await(5, TimeUnit.SECONDS));
                assertTrue(supplierEntered.await(5, TimeUnit.SECONDS));
            } finally {
                release.countDown();
            }
            Object first = results.getFirst().get(5, TimeUnit.SECONDS);
            for (var result : results) {
                assertSame(first, result.get(5, TimeUnit.SECONDS));
            }
            assertEquals(1, calls.get());
        }
    }

    @Test
    void nullDoesNotBecomeACachedValue() {
        var calls = new AtomicInteger();
        Object expected = new Object();
        Lazy<Object> lazy = Lazy.of(() -> calls.getAndIncrement() == 0 ? null : expected);
        assertThrows(IllegalStateException.class, lazy::get);
        assertSame(expected, lazy.get());
        assertEquals(2, calls.get());
    }

    @Test
    void invalidationRecomputesExactlyOnce() {
        var calls = new AtomicInteger();
        Lazy<Object> lazy = Lazy.of(() -> {
            calls.incrementAndGet();
            return new Object();
        });
        Object before = lazy.get();
        assertSame(before, lazy.getValue());
        lazy.invalidate();
        Object after = lazy.get();
        assertNotSame(before, after);
        assertSame(after, lazy.get());
        assertEquals(2, calls.get());
    }
}
