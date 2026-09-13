package mekanism.api.chemical;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.functions.ConstantPredicates;
import mekanism.api.functions.TriPredicate;
import mekanism.api.chemical.attribute.ChemicalAttribute;
import mekanism.api.chemical.attribute.ChemicalAttributes.Radiation;
import mekanism.common.registries.MekanismChemicals;
import mekanism.test.ChemicalTestBootstrap;
import mekanism.test.ChemicalTestBootstrap.TrackingChemical;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChemicalDomainTest {
    private static RegistryAccess access;
    @BeforeAll static void bootstrap() { access = ChemicalTestBootstrap.initialize(); }
    @AfterEach void clearHotCache() { ChemicalDataContext.clear(); }

    @Test
    void registryHasCanonicalEmptyAndAllOfficialDeclarations() {
        assertEquals(65, ChemicalRegistry.registry().size());
        assertSame(ChemicalRegistry.empty(), ChemicalRegistry.emptyHolder().value());
        assertSame(MekanismChemicals.HYDROGEN.get(), ChemicalRegistry.registry().get(MekanismChemicals.HYDROGEN.getId()));
        assertSame(MekanismChemicals.HYDROGEN.get(), ChemicalRegistry.registry().getHolder(MekanismChemicals.HYDROGEN.getKey()).orElseThrow().value());
    }

    @Test
    void chemicalStackPreservesTrueLongAndCanonicalEmpty() {
        long amount = (1L << 60) + 17;
        ChemicalStack stack = new ChemicalStack(MekanismChemicals.HYDROGEN, amount);
        assertEquals(amount, stack.getAmount());
        assertSame(ChemicalRegistry.registry().getHolder(MekanismChemicals.HYDROGEN.getKey()).orElseThrow(), stack.getChemicalHolder());
        assertSame(ChemicalStack.EMPTY, stack.copyWithAmount(0));
        assertEquals(amount, stack.copy().getAmount());
    }

    @Test
    void tankSimulationExecutionAndListenerAreExact() {
        AtomicInteger changes = new AtomicInteger();
        BasicChemicalTank tank = (BasicChemicalTank) BasicChemicalTank.createAllValid(Long.MAX_VALUE, changes::incrementAndGet);
        ChemicalStack input = new ChemicalStack(MekanismChemicals.HYDROGEN, (1L << 55) + 3);
        assertTrue(tank.insert(input, Action.SIMULATE, AutomationType.EXTERNAL).isEmpty());
        assertEquals(0, tank.getStored());
        assertTrue(tank.insert(input, Action.EXECUTE, AutomationType.EXTERNAL).isEmpty());
        assertEquals(input.getAmount(), tank.getStored());
        assertEquals(1, changes.get());
        assertEquals(9, tank.extract(9, Action.SIMULATE, AutomationType.EXTERNAL).getAmount());
        assertEquals(input.getAmount(), tank.getStored());
    }

    @Test
    void tankNbtRoundTripRetainsLongAmount() {
        BasicChemicalTank first = (BasicChemicalTank) BasicChemicalTank.createAllValid(Long.MAX_VALUE, null);
        long amount = Long.MAX_VALUE - 31;
        first.setStack(new ChemicalStack(MekanismChemicals.OXYGEN, amount));
        CompoundTag encoded = first.serializeNBT(access);
        BasicChemicalTank second = (BasicChemicalTank) BasicChemicalTank.createAllValid(Long.MAX_VALUE, null);
        second.deserializeNBT(access, encoded);
        assertEquals(amount, second.getStored());
        assertSame(MekanismChemicals.OXYGEN.get(), second.getType());
    }

    @Test
    void handlerDefaultsRejectInvalidIndexesAndPreserveRemainder() {
        BasicChemicalTank tank = (BasicChemicalTank) BasicChemicalTank.createAllValid(100, null);
        ChemicalStack stack = new ChemicalStack(MekanismChemicals.STEAM, 20);
        assertSame(stack, tank.insertChemical(4, stack, Action.EXECUTE));
        assertSame(ChemicalStack.EMPTY, tank.extractChemical(-1, 20, Action.EXECUTE));
        assertEquals(0, tank.getChemicalTankCapacity(2));
    }

    @Test
    void contextQueriesInvokeVirtualDerivationAndSameChemicalReentrancySeesPartialView() {
        TrackingChemical empty = (TrackingChemical) ChemicalRegistry.empty();
        empty.resetTracking();
        assertEquals(0, empty.getRadioactivity());
        assertEquals(1, empty.invocations(), "each top-level query must participate through the virtual override");
        assertEquals(0, empty.reentrantBeforeSuper());
        assertEquals(0, empty.nestedOtherChemical(), "a nested different-chemical derivation must restore the outer frame");
        assertEquals(0, empty.reentrantAfterSuper());
        //The hot cache amortizes the repeat top-level query: no re-invocation, no descriptor lookup, no builder allocation
        assertEquals(0, empty.getRadioactivity(), "the outer chemical must remain derivable after nested frame restoration");
        assertEquals(1, empty.invocations(), "a repeat query under the same context and legacy state is a cache hit");
        //Binding a different context instance (even with an equal epoch) installs a fresh cache and re-derives exactly once
        ChemicalDataContext.bind(new ChemicalDataContext(1, Map.of()));
        assertEquals(0, empty.getRadioactivity());
        assertEquals(2, empty.invocations(), "a different bound context invalidates the completed entry");
        assertThrows(IllegalStateException.class,
              () -> MekanismChemicals.HYDROGEN.get().updateFromDataMap(MekanismChemicals.HYDROGEN.get().getAsHolder()),
              "direct external invocation is outside the context-local derivation boundary");
    }

    @Test
    void hotCacheAmortizesRepeatTopLevelQueries() {
        TrackingChemical empty = (TrackingChemical) ChemicalRegistry.empty();
        empty.resetTracking();
        for (int i = 0; i < 50; i++) {
            assertEquals(0, empty.getRadioactivity());
            assertFalse(empty.hasAttributesWithValidation());
            assertEquals(0, empty.getModernAttributes().size());
        }
        assertEquals(1, empty.invocations(), "fifty repeat queries share one derivation per exact context, chemical, and legacy state");
        //A different chemical derives and caches independently under the same binding
        double hydrogenBefore = MekanismChemicals.HYDROGEN.get().getRadioactivity();
        assertEquals(hydrogenBefore, MekanismChemicals.HYDROGEN.get().getRadioactivity());
        assertEquals(1, empty.invocations(), "deriving another chemical does not touch this chemical's entry or frame");
    }

    @Test
    void equalEpochDifferentContextInvalidatesHotCache() {
        TrackingChemical empty = (TrackingChemical) ChemicalRegistry.empty();
        empty.resetTracking();
        assertEquals(0, empty.getRadioactivity());
        assertEquals(1, empty.invocations());
        //Two distinct context instances with equal numeric epochs: the fresh identity must not reuse the previous cache
        ChemicalDataContext equalEpoch = new ChemicalDataContext(0, Map.of());
        ChemicalDataContext.bind(equalEpoch);
        assertEquals(0, empty.getRadioactivity());
        assertEquals(2, empty.invocations(), "equal-epoch context replacement installs a fresh cache");
        ChemicalDataContext.bind(equalEpoch);
        assertEquals(0, empty.getRadioactivity());
        assertEquals(2, empty.invocations(), "rebinding the exact same context instance retains the cache");
    }

    @Test
    void sameAndDifferentChemicalReentrancyUnderHotCache() {
        TrackingChemical empty = (TrackingChemical) ChemicalRegistry.empty();
        empty.resetTracking();
        //Warm the nested chemical's cache first so the nested query during derivation is itself a hit
        assertEquals(0.0, MekanismChemicals.HYDROGEN.get().getRadioactivity());
        assertEquals(0, empty.getRadioactivity());
        assertEquals(1, empty.invocations());
        assertEquals(0.0, empty.nestedOtherChemical(), "a cached different chemical is observed without a nested frame");
        assertEquals(0, empty.reentrantBeforeSuper());
        assertEquals(0, empty.reentrantAfterSuper());
        //Same-chemical reentrancy still observes the immutable partial view rather than a completed value
        empty.resetTracking();
        ChemicalDataContext.clear();
        assertEquals(0, empty.getRadioactivity());
        assertEquals(1, empty.invocations());
        assertEquals(0, empty.reentrantBeforeSuper());
        assertEquals(0, empty.reentrantAfterSuper());
        //A throwing override must not publish a completed entry; the next query retries and then becomes hot.
        ChemicalDataContext.bind(new ChemicalDataContext(4, Map.of()));
        empty.armDerivationFailure();
        assertThrows(IllegalStateException.class, empty::getRadioactivity);
        assertEquals(2, empty.invocations());
        assertEquals(0, empty.getRadioactivity());
        assertEquals(3, empty.invocations());
        assertEquals(0, empty.getRadioactivity());
        assertEquals(3, empty.invocations(), "only the successful retry is cached");
    }

    @Test
    void addAttributeInvalidatesOnlyThatChemicalsHotEntry() throws ReflectiveOperationException {
        TrackingChemical empty = (TrackingChemical) ChemicalRegistry.empty();
        Chemical chlorine = MekanismChemicals.CHLORINE.get();
        java.lang.reflect.Field legacyState = Chemical.class.getDeclaredField("legacyState");
        legacyState.setAccessible(true);
        Object emptyState = legacyState.get(empty);
        Object chlorineState = legacyState.get(chlorine);
        try {
            empty.resetTracking();
            assertEquals(0, empty.getRadioactivity());
            assertEquals(1, empty.invocations());
            //A legacy attribute that is neither radiation nor validation-needing only advances the legacy state identity
            empty.addAttribute(new NoOpLegacyAttribute());
            assertEquals(0, empty.getRadioactivity());
            assertEquals(2, empty.invocations(), "publishing a new legacy state invalidates this chemical's entry on its next query");
            assertEquals(0, empty.getRadioactivity());
            assertEquals(2, empty.invocations(), "the replacement entry is cached again");
            //A radiation attribute publishes both the new state and the new derived value
            assertEquals(0, chlorine.getRadioactivity());
            assertFalse(chlorine.isRadioactive());
            chlorine.addAttribute(new Radiation(0.5));
            assertEquals(0.5, chlorine.getRadioactivity(), "the copy-on-write legacy state is visible on the next query");
            assertTrue(chlorine.isRadioactive());
            assertEquals(0.5, chlorine.getRadioactivity(), "the derived radiation is stable across further queries");
        } finally {
            //Canonical test chemicals are shared across methods; restore their exact immutable snapshots.
            legacyState.set(empty, emptyState);
            legacyState.set(chlorine, chlorineState);
        }
    }

    @Test
    void threadClearAndRebindIsolationOfHotCache() throws InterruptedException {
        TrackingChemical empty = (TrackingChemical) ChemicalRegistry.empty();
        empty.resetTracking();
        assertEquals(0, empty.getRadioactivity());
        assertEquals(1, empty.invocations());
        AtomicInteger otherThreadInvocations = new AtomicInteger(-1);
        Thread other = new Thread(() -> {
            //A different thread has its own binding and its own cache even for the same default context content
            assertEquals(0, ((TrackingChemical) ChemicalRegistry.empty()).getRadioactivity());
            otherThreadInvocations.set(((TrackingChemical) ChemicalRegistry.empty()).invocations());
        });
        other.start();
        other.join();
        assertEquals(2, empty.invocations(), "the other thread derived independently instead of reusing this thread's cache");
        assertEquals(2, otherThreadInvocations.get());
        ChemicalDataContext.clear();
        assertEquals(0, empty.getRadioactivity());
        assertEquals(3, empty.invocations(), "clearing the binding removes the entire entry map and re-derives once");
        ChemicalDataContext rebound = new ChemicalDataContext(3, Map.of());
        ChemicalDataContext.bind(rebound);
        assertEquals(0, empty.getRadioactivity());
        assertEquals(4, empty.invocations());
        ChemicalDataContext.bind(rebound);
        assertEquals(0, empty.getRadioactivity());
        assertEquals(4, empty.invocations(), "retaining the exact context identity keeps the replacement entry");
    }

    @Test
    void predicatesKeepSingletonIdentityAndShortCircuitComposition() {
        assertSame(ConstantPredicates.alwaysTrue(), BasicChemicalTank.alwaysTrue);
        assertSame(ConstantPredicates.alwaysTrueBi(), BasicChemicalTank.alwaysTrueBi);
        AtomicInteger right = new AtomicInteger();
        TriPredicate<String, Integer, Long> leftFalse = (a, b, c) -> false;
        assertFalse(leftFalse.and((a, b, c) -> { right.incrementAndGet(); return true; }).test("x", 1, 2L));
        assertEquals(0, right.get());
        assertThrows(NullPointerException.class, () -> leftFalse.and(null));
        assertTrue(leftFalse.negate().test("x", 1, 2L));
    }

    /** Legacy attribute that changes no observable derived value; used to advance the legacy state identity. */
    private static final class NoOpLegacyAttribute extends ChemicalAttribute {
        @Override
        public void collectTooltips(java.util.function.Consumer<Component> adder) {
        }
    }
}
