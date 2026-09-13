package mekanism.api.chemical;

import java.util.concurrent.atomic.AtomicInteger;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.functions.ConstantPredicates;
import mekanism.api.functions.TriPredicate;
import mekanism.common.registries.MekanismChemicals;
import mekanism.test.ChemicalTestBootstrap;
import mekanism.test.ChemicalTestBootstrap.TrackingChemical;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChemicalDomainTest {
    private static RegistryAccess access;
    @BeforeAll static void bootstrap() { access = ChemicalTestBootstrap.initialize(); }

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
        assertEquals(0, empty.getRadioactivity(), "the outer chemical must remain derivable after nested frame restoration");
        assertEquals(2, empty.invocations());
        assertThrows(IllegalStateException.class,
              () -> MekanismChemicals.HYDROGEN.get().updateFromDataMap(MekanismChemicals.HYDROGEN.get().getAsHolder()),
              "direct external invocation is outside the context-local derivation boundary");
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
}
