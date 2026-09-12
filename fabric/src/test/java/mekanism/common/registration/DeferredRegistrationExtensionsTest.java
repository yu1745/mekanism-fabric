package mekanism.common.registration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import mekanism.common.registration.impl.DeferredCriterionTrigger;
import mekanism.common.registration.impl.LootFunctionDeferredRegister;
import net.minecraft.SharedConstants;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.CriterionTriggerInstance;
import net.minecraft.advancements.critereon.CriterionValidator;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import org.junit.jupiter.api.Test;

class DeferredRegistrationExtensionsTest {
    private static final ResourceKey<Registry<MapCodec<? extends TestValue>>> TEST_CODEC_REGISTRY = ResourceKey.createRegistryKey(
          ResourceLocation.fromNamespaceAndPath("test_registration", "map_codecs"));

    @Test
    void mapCodecFunctionOverloadIsLazyKeyedSubtypeSafeAndCanonicallyBound() {
        DeferredMapCodecRegister<TestValue> register = new DeferredMapCodecRegister<>(TEST_CODEC_REGISTRY, "function_test");
        ResourceLocation expectedId = ResourceLocation.fromNamespaceAndPath("function_test", "subtype");
        ResourceKey<MapCodec<? extends TestValue>> expectedKey = ResourceKey.create(TEST_CODEC_REGISTRY, expectedId);
        AtomicReference<ResourceLocation> factoryId = new AtomicReference<>();
        TestSubtype createdValue = new TestSubtype("created");
        MapCodec<TestSubtype> createdCodec = MapCodec.unit(createdValue);
        MapCodec<TestSubtype> canonicalCodec = MapCodec.unit(new TestSubtype("canonical"));

        DeferredMapCodecHolder<TestValue, TestSubtype> holder = register.registerCodec("subtype", id -> {
            factoryId.set(id);
            return createdCodec;
        });

        assertEquals(expectedId, holder.getId());
        assertEquals(expectedKey, holder.getKey());
        assertFalse(holder.isBound());
        assertNull(factoryId.get());
        assertThrows(IllegalStateException.class, holder::value);

        register.registerAll((registryKey, key, value) -> {
            assertEquals(TEST_CODEC_REGISTRY, registryKey);
            assertEquals(expectedKey, key);
            assertSame(createdCodec, value);
            return canonicalCodec;
        });

        MapCodec<TestSubtype> subtypeCodec = holder.value();
        assertEquals(expectedId, factoryId.get());
        assertTrue(holder.isBound());
        assertSame(canonicalCodec, subtypeCodec);
    }

    @Test
    void mapCodecSupplierUsesCustomHolderAndDelegatesDuplicateAndLifecycleRules() {
        DeferredMapCodecRegister<TestValue> register = new DeferredMapCodecRegister<>(
              TEST_CODEC_REGISTRY, "supplier_test", TestMapCodecHolder::new);
        AtomicInteger supplierCalls = new AtomicInteger();
        MapCodec<TestSubtype> suppliedCodec = MapCodec.unit(new TestSubtype("supplied"));

        DeferredMapCodecHolder<TestValue, TestSubtype> holder = register.registerCodec("custom", () -> {
            supplierCalls.incrementAndGet();
            return suppliedCodec;
        });

        assertInstanceOf(TestMapCodecHolder.class, holder);
        assertEquals(0, supplierCalls.get());
        assertThrows(IllegalArgumentException.class,
              () -> register.registerCodec("custom", () -> MapCodec.unit(new TestSubtype("duplicate"))));
        assertEquals(0, supplierCalls.get());

        register.registerAll((registryKey, key, value) -> value);

        MapCodec<TestSubtype> subtypeCodec = holder.value();
        assertEquals(1, supplierCalls.get());
        assertSame(suppliedCodec, subtypeCodec);
        assertThrows(IllegalStateException.class,
              () -> register.registerCodec("late", () -> MapCodec.unit(new TestSubtype("late"))));
        assertThrows(IllegalStateException.class,
              () -> register.registerAll((registryKey, key, value) -> value));
    }

    @Test
    @SuppressWarnings("unchecked")
    void deferredCriterionTriggerRetainsExactInstanceAndTriggerIdentity() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        MekanismDeferredRegister<CriterionTrigger<?>> register = new MekanismDeferredRegister<>(
              Registries.TRIGGER_TYPE, "criterion_test", DeferredCriterionTrigger::new);
        TestCriterionInstance instance = new TestCriterionInstance();
        TestCriterionTrigger trigger = new TestCriterionTrigger(instance);
        DeferredCriterionTrigger<TestCriterionInstance, TestCriterionTrigger> holder =
              (DeferredCriterionTrigger<TestCriterionInstance, TestCriterionTrigger>) register.register("minimal", () -> trigger);

        register.registerAll((registryKey, key, value) -> {
            assertEquals(Registries.TRIGGER_TYPE, registryKey);
            assertSame(trigger, value);
            return value;
        });

        Criterion<TestCriterionInstance> criterion = holder.createCriterion(instance);
        assertSame(trigger, holder.value());
        assertSame(trigger, criterion.trigger());
        assertSame(instance, criterion.triggerInstance());
    }

    @Test
    void lootFunctionBasicAndCodecRegistrationsAreLazyKeyedAndPreserveCodecIdentity() {
        LootFunctionDeferredRegister register = new LootFunctionDeferredRegister("loot_test");
        AtomicInteger basicSupplierCalls = new AtomicInteger();
        AtomicInteger codecSupplierCalls = new AtomicInteger();
        TestLootFunction basicFunction = new TestLootFunction();
        TestLootFunction codecFunction = new TestLootFunction();
        MapCodec<TestLootFunction> codec = MapCodec.unit(codecFunction);
        MekanismDeferredHolder<LootItemFunctionType<?>, LootItemFunctionType<TestLootFunction>> basic = register.registerBasic("basic", () -> {
            basicSupplierCalls.incrementAndGet();
            return basicFunction;
        });
        MekanismDeferredHolder<LootItemFunctionType<?>, LootItemFunctionType<TestLootFunction>> withCodec = register.registerCodec("codec", () -> {
            codecSupplierCalls.incrementAndGet();
            return codec;
        });
        ResourceKey<LootItemFunctionType<?>> basicKey = ResourceKey.create(Registries.LOOT_FUNCTION_TYPE,
              ResourceLocation.fromNamespaceAndPath("loot_test", "basic"));
        ResourceKey<LootItemFunctionType<?>> codecKey = ResourceKey.create(Registries.LOOT_FUNCTION_TYPE,
              ResourceLocation.fromNamespaceAndPath("loot_test", "codec"));
        Map<ResourceKey<LootItemFunctionType<?>>, LootItemFunctionType<?>> registered = new HashMap<>();

        assertEquals(basicKey, basic.getKey());
        assertEquals(codecKey, withCodec.getKey());
        assertEquals(0, basicSupplierCalls.get());
        assertEquals(0, codecSupplierCalls.get());
        assertFalse(basic.isBound());
        assertFalse(withCodec.isBound());

        ((MekanismDeferredRegister<LootItemFunctionType<?>>) register).registerAll((registryKey, key, value) -> {
            assertEquals(Registries.LOOT_FUNCTION_TYPE, registryKey);
            registered.put(key, value);
            return value;
        });

        LootItemFunctionType<TestLootFunction> basicType = basic.value();
        LootItemFunctionType<TestLootFunction> codecType = withCodec.value();
        basicFunction.bindType(basicType);
        codecFunction.bindType(codecType);
        assertEquals(1, basicSupplierCalls.get());
        assertEquals(1, codecSupplierCalls.get());
        assertSame(basicType, registered.get(basicKey));
        assertSame(codecType, registered.get(codecKey));
        assertSame(codec, codecType.codec());
        assertSame(basicType, basicFunction.getType());
        assertSame(codecType, codecFunction.getType());
    }

    private interface TestValue {
    }

    private record TestSubtype(String name) implements TestValue {
    }

    private static final class TestMapCodecHolder extends DeferredMapCodecHolder<TestValue, TestSubtype> {
        private TestMapCodecHolder(ResourceKey<MapCodec<? extends TestValue>> key) {
            super(key);
        }
    }

    private static final class TestCriterionInstance implements CriterionTriggerInstance {
        @Override
        public void validate(CriterionValidator validator) {
        }
    }

    private static final class TestCriterionTrigger implements CriterionTrigger<TestCriterionInstance> {
        private final Codec<TestCriterionInstance> codec;

        private TestCriterionTrigger(TestCriterionInstance instance) {
            codec = Codec.unit(instance);
        }

        @Override
        public void addPlayerListener(PlayerAdvancements playerAdvancements, Listener<TestCriterionInstance> listener) {
        }

        @Override
        public void removePlayerListener(PlayerAdvancements playerAdvancements, Listener<TestCriterionInstance> listener) {
        }

        @Override
        public void removePlayerListeners(PlayerAdvancements playerAdvancements) {
        }

        @Override
        public Codec<TestCriterionInstance> codec() {
            return codec;
        }
    }

    private static final class TestLootFunction implements LootItemFunction {
        private LootItemFunctionType<TestLootFunction> type;

        private void bindType(LootItemFunctionType<TestLootFunction> type) {
            this.type = type;
        }

        @Override
        public LootItemFunctionType<TestLootFunction> getType() {
            return Objects.requireNonNull(type);
        }

        @Override
        public ItemStack apply(ItemStack stack, LootContext context) {
            return stack;
        }
    }
}
