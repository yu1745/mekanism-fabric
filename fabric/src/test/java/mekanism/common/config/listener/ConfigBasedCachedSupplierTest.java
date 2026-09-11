package mekanism.common.config.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import mekanism.common.config.BaseMekanismConfig;
import mekanism.common.config.MekanismConfigType;
import mekanism.common.config.value.CachedFloatValue;
import mekanism.common.config.value.CachedIntValue;
import mekanism.fabric.config.MekanismConfigSpec;
import org.junit.jupiter.api.Test;

class ConfigBasedCachedSupplierTest {

    @Test
    void genericSupplierIsLazyCachesNonNullAndRetriesNullSentinelBeforeInvalidationRefresh() {
        TestConfig config = new TestConfig();
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<String> value = new AtomicReference<>();
        ConfigBasedCachedSupplier<String> supplier = new ConfigBasedCachedSupplier<>(() -> {
            calls.incrementAndGet();
            return value.get();
        }, config.integer);
        assertTrue(config.integer.hasInvalidationListeners());
        assertEquals(0, calls.get());
        assertNull(supplier.get());
        assertNull(supplier.get());
        assertEquals(2, calls.get());
        value.set("cached");
        assertEquals("cached", supplier.get());
        assertEquals("cached", supplier.get());
        assertEquals(3, calls.get());
        config.integer.get();
        value.set("refreshed");
        config.integerValue.set(2);
        config.integer.clearCache(false);
        assertEquals(4, calls.get());
        assertEquals("refreshed", supplier.get());
    }

    @Test
    void floatSupplierPreservesNegativeZeroAndEagerlyRefreshesOnClearCache() {
        TestConfig config = new TestConfig();
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<Float> value = new AtomicReference<>(1F);
        ConfigBasedCachedFloatSupplier supplier = new ConfigBasedCachedFloatSupplier(() -> {
            calls.incrementAndGet();
            return value.get();
        }, config.floating);
        assertTrue(config.floating.hasInvalidationListeners());
        assertEquals(0, calls.get());
        assertEquals(1F, supplier.getAsFloat());
        assertEquals(1F, supplier.getAsFloat());
        assertEquals(1, calls.get());
        config.floating.get();
        value.set(-0.0F);
        config.floatValue.set(-0.0D);
        config.floating.clearCache(false);
        assertEquals(2, calls.get());
        assertEquals(Float.floatToRawIntBits(-0.0F), Float.floatToRawIntBits(supplier.getAsFloat()));
    }

    @Test
    void intSupplierResolvesOnceAndRefreshesExactValueFromDependencyCallback() {
        TestConfig config = new TestConfig();
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger value = new AtomicInteger(41);
        ConfigBasedCachedIntSupplier supplier = new ConfigBasedCachedIntSupplier(() -> {
            calls.incrementAndGet();
            return value.get();
        }, config.integer);
        assertEquals(0, calls.get());
        assertEquals(41, supplier.getAsInt());
        assertEquals(41, supplier.getAsInt());
        assertEquals(1, calls.get());
        config.integer.get();
        value.set(-73);
        config.integerValue.set(2);
        config.integer.clearCache(false);
        assertEquals(2, calls.get());
        assertEquals(-73, supplier.getAsInt());
    }

    @Test
    void longSupplierCachesLargeValueAndRefreshesEagerlyFromDependencyCallback() {
        TestConfig config = new TestConfig();
        AtomicInteger calls = new AtomicInteger();
        AtomicLong value = new AtomicLong(Long.MAX_VALUE - 7);
        ConfigBasedCachedLongSupplier supplier = new ConfigBasedCachedLongSupplier(() -> {
            calls.incrementAndGet();
            return value.get();
        }, config.integer);
        assertEquals(0, calls.get());
        assertEquals(Long.MAX_VALUE - 7, supplier.getAsLong());
        assertEquals(Long.MAX_VALUE - 7, supplier.getAsLong());
        assertEquals(1, calls.get());
        config.integer.get();
        value.set(Long.MIN_VALUE + 9);
        config.integerValue.set(2);
        config.integer.clearCache(false);
        assertEquals(2, calls.get());
        assertEquals(Long.MIN_VALUE + 9, supplier.getAsLong());
    }

    private static final class TestConfig extends BaseMekanismConfig {
        final MekanismConfigSpec.ConfigValue<Integer> integerValue;
        final MekanismConfigSpec.ConfigValue<Double> floatValue;
        final MekanismConfigSpec spec;
        final CachedIntValue integer;
        final CachedFloatValue floating;

        TestConfig() {
            MekanismConfigSpec.Builder builder = new MekanismConfigSpec.Builder();
            integerValue = builder.defineInRange("integer", 1, -100, 100);
            floatValue = builder.defineInRange("floating", 1D, -100D, 100D);
            spec = builder.build();
            integer = CachedIntValue.wrap(this, integerValue);
            floating = CachedFloatValue.wrap(this, floatValue);
        }
        @Override public String getFileName() { return "supplier-test"; }
        @Override public String getTranslation() { return "supplier-test"; }
        @Override public MekanismConfigSpec getConfigSpec() { return spec; }
        @Override public MekanismConfigType getConfigType() { return MekanismConfigType.STARTUP; }
        @Override public boolean isLoaded() { return true; }
    }
}
