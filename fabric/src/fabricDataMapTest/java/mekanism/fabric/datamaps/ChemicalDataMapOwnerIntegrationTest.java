package mekanism.fabric.datamaps;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalBuilder;
import mekanism.api.chemical.ChemicalDataContext;
import mekanism.api.chemical.ChemicalRegistry;
import mekanism.api.datamaps.IMekanismDataMapTypes;
import mekanism.api.datamaps.MekanismDataMapType;
import mekanism.api.datamaps.chemical.attribute.ChemicalRadioactivity;
import mekanism.common.registries.MekanismChemicals;
import mekanism.fabric.registry.FabricDefaultedRegistry;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChemicalDataMapOwnerIntegrationTest {
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        FabricDefaultedRegistry<Chemical> registry = FabricDefaultedRegistry.create(ChemicalRegistry.REGISTRY_KEY, ChemicalRegistry.EMPTY_KEY, true);
        ChemicalRegistry.install(registry);
        Chemical empty = new Chemical(ChemicalBuilder.builder());
        Holder.Reference<Chemical> holder = registry.register(ChemicalRegistry.EMPTY_KEY, empty, RegistrationInfo.BUILT_IN);
        ChemicalRegistry.publishEmpty(empty, holder);
        MekanismChemicals.CHEMICALS.registerAll(registry);
    }
    @AfterEach void clear() { ChemicalDataContext.clear(); }

    @Test
    void publishedEpochFeedsRealChemicalQueries() {
        FabricChemicalDataMapSession session = new FabricChemicalDataMapSession(Thread.currentThread());
        session.publishSnapshot(context(1, 1.0E-5, true));
        assertEquals(1.0E-5, MekanismChemicals.NUCLEAR_WASTE.get().getRadioactivity());
        assertTrue(MekanismChemicals.NUCLEAR_WASTE.get().hasAttributesWithValidation());
        session.close();
    }

    @Test
    void integratedServerAndClientViewsDoNotMutateSharedChemical() {
        Chemical shared = MekanismChemicals.NUCLEAR_WASTE.get();
        ChemicalDataContext server = context(2, 2.0E-5, true);
        ChemicalDataContext client = context(7, 0, false);
        ChemicalDataContext.bind(server); assertEquals(2.0E-5, shared.getRadioactivity());
        ChemicalDataContext.bind(client); assertEquals(0, shared.getRadioactivity());
        ChemicalDataContext.bind(server); assertEquals(2.0E-5, shared.getRadioactivity());
    }

    @Test
    void regressingEpochCloseAndOffThreadUseFailClosed() throws Exception {
        FabricChemicalDataMapSession session = new FabricChemicalDataMapSession(Thread.currentThread());
        session.publishSnapshot(context(3, 1.0E-5, true));
        assertThrows(IllegalArgumentException.class, () -> session.publishSnapshot(context(3, 2.0E-5, true)));
        java.util.concurrent.atomic.AtomicReference<Throwable> failure = new java.util.concurrent.atomic.AtomicReference<>();
        Thread thread = new Thread(() -> { try { session.context(); } catch (Throwable e) { failure.set(e); } });
        thread.start(); thread.join();
        assertInstanceOf(IllegalStateException.class, failure.get());
        session.close();
        assertThrows(IllegalStateException.class, session::context);
    }

    private static ChemicalDataContext context(long epoch, double radiation, boolean enabled) {
        Map<MekanismDataMapType<Chemical, ?>, Map<ResourceKey<Chemical>, ?>> maps = new IdentityHashMap<>();
        if (radiation > 0) maps.put(IMekanismDataMapTypes.INSTANCE.chemicalRadioactivity(),
              Map.of(MekanismChemicals.NUCLEAR_WASTE.getKey(), new ChemicalRadioactivity(radiation)));
        return new ChemicalDataContext(epoch, maps, enabled, 2.5, false);
    }
}
