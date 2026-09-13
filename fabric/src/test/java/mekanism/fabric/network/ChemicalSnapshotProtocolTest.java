package mekanism.fabric.network;

import io.netty.buffer.Unpooled;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalDataContext;
import mekanism.api.datamaps.IMekanismDataMapTypes;
import mekanism.api.datamaps.MekanismDataMapType;
import mekanism.api.datamaps.chemical.ChemicalSolidTag;
import mekanism.api.datamaps.chemical.attribute.ChemicalFuel;
import mekanism.api.datamaps.chemical.attribute.ChemicalRadioactivity;
import mekanism.api.datamaps.chemical.attribute.CooledCoolant;
import mekanism.api.datamaps.chemical.attribute.HeatedCoolant;
import mekanism.common.registries.MekanismChemicals;
import mekanism.fabric.chemical.FabricChemicalClientLifecycle;
import mekanism.test.ChemicalTestBootstrap;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChemicalSnapshotProtocolTest {
    @BeforeAll static void bootstrap() { ChemicalTestBootstrap.initialize(); }
    @AfterEach void cleanup() { FabricChemicalClientLifecycle.disconnect(); }

    @Test
    void roundTripIsNameBasedAndResolvesOnOwnerThread() {
        ChemicalSnapshotPayload source = ChemicalSnapshotPayload.from("session-a", context(9));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        ChemicalSnapshotPayload.CODEC.encode(buffer, source);
        ChemicalSnapshotPayload decoded = ChemicalSnapshotPayload.CODEC.decode(buffer);
        ChemicalDataContext resolved = decoded.resolve();
        assertEquals(9, resolved.epoch());
        assertEquals(new ChemicalFuel(20, 40), resolved.get(MekanismChemicals.HYDROGEN.getKey(), IMekanismDataMapTypes.INSTANCE.chemicalFuel()));
        assertEquals(new ChemicalSolidTag(TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "ores/test"))),
              resolved.get(MekanismChemicals.HYDROGEN.getKey(), IMekanismDataMapTypes.INSTANCE.chemicalSolidTag()));
        assertEquals(new ChemicalRadioactivity(0.01),
              resolved.get(MekanismChemicals.NUCLEAR_WASTE.getKey(), IMekanismDataMapTypes.INSTANCE.chemicalRadioactivity()));
        assertEquals(MekanismChemicals.SUPERHEATED_SODIUM.getKey(),
              resolved.get(MekanismChemicals.SODIUM.getKey(), IMekanismDataMapTypes.INSTANCE.cooledChemicalCoolant())
                    .otherVariant().unwrapKey().orElseThrow());
        assertEquals(MekanismChemicals.SODIUM.getKey(),
              resolved.get(MekanismChemicals.SUPERHEATED_SODIUM.getKey(), IMekanismDataMapTypes.INSTANCE.heatedChemicalCoolant())
                    .otherVariant().unwrapKey().orElseThrow());
    }

    @Test
    void unknownChemicalIsRejectedOnlyDuringResolution() {
        ResourceLocation unknown = ResourceLocation.fromNamespaceAndPath("other", "missing");
        ChemicalSnapshotPayload payload = new ChemicalSnapshotPayload("s", 1, true, 2.5, false, List.of(),
              List.of(new ChemicalSnapshotPayload.Fuel(unknown, 1, 1)), List.of(), List.of(), List.of());
        assertThrows(IllegalArgumentException.class, payload::resolve);
    }

    @Test
    void duplicateKeysAreRejected() {
        ResourceLocation hydrogen = MekanismChemicals.HYDROGEN.getId();
        ChemicalSnapshotPayload payload = new ChemicalSnapshotPayload("s", 1, true, 2.5, false, List.of(),
              List.of(new ChemicalSnapshotPayload.Fuel(hydrogen, 1, 1), new ChemicalSnapshotPayload.Fuel(hydrogen, 2, 2)),
              List.of(), List.of(), List.of());
        assertThrows(IllegalArgumentException.class, payload::resolve);
    }

    @Test
    void allocationAndEpochBoundsFailBeforeCollectionsAllocate() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeUtf("s", 64); buffer.writeVarLong(1); buffer.writeBoolean(true); buffer.writeDouble(2.5); buffer.writeBoolean(false);
        buffer.writeVarInt(ChemicalSnapshotPayload.MAX_PER_MAP + 1);
        assertThrows(IllegalArgumentException.class, () -> ChemicalSnapshotPayload.CODEC.decode(buffer));
        assertThrows(IllegalArgumentException.class, () -> new ChemicalSnapshotPayload("s", 0, true, 2.5, false,
              List.of(), List.of(), List.of(), List.of(), List.of()));
        ResourceLocation hydrogen = MekanismChemicals.HYDROGEN.getId();
        assertThrows(IllegalArgumentException.class,
              () -> new ChemicalSnapshotPayload.Fuel(hydrogen, new byte[ChemicalSnapshotPayload.MAX_VALUE_BYTES + 1]));
        List<ChemicalSnapshotPayload.Solid> solids = java.util.Collections.nCopies(65,
              new ChemicalSnapshotPayload.Solid(hydrogen, new byte[ChemicalSnapshotPayload.MAX_VALUE_BYTES]));
        List<ChemicalSnapshotPayload.Fuel> fuels = java.util.Collections.nCopies(65,
              new ChemicalSnapshotPayload.Fuel(hydrogen, new byte[ChemicalSnapshotPayload.MAX_VALUE_BYTES]));
        List<ChemicalSnapshotPayload.Radioactivity> radioactivities = java.util.Collections.nCopies(65,
              new ChemicalSnapshotPayload.Radioactivity(hydrogen, new byte[ChemicalSnapshotPayload.MAX_VALUE_BYTES]));
        List<ChemicalSnapshotPayload.Cooled> cooled = java.util.Collections.nCopies(65,
              new ChemicalSnapshotPayload.Cooled(hydrogen, new byte[ChemicalSnapshotPayload.MAX_VALUE_BYTES]));
        List<ChemicalSnapshotPayload.Heated> heated = java.util.Collections.nCopies(65,
              new ChemicalSnapshotPayload.Heated(hydrogen, new byte[ChemicalSnapshotPayload.MAX_VALUE_BYTES]));
        assertThrows(IllegalArgumentException.class, () -> new ChemicalSnapshotPayload("s", 1, true, 2.5, false,
              solids, fuels, radioactivities, cooled, heated));
    }

    @Test
    void sameSessionNewerInitialSnapshotCatchesUpWithoutResettingEpoch() {
        long generation = FabricChemicalClientLifecycle.generation();
        FabricChemicalClientLifecycle.publish("race", context(1), true, generation);
        FabricChemicalClientLifecycle.publish("race", context(2), true, generation);
        assertEquals(2, ChemicalDataContext.current().epoch());
        assertThrows(IllegalArgumentException.class,
              () -> FabricChemicalClientLifecycle.publish("race", context(2), true, generation));
        assertThrows(IllegalArgumentException.class,
              () -> FabricChemicalClientLifecycle.publish("replacement", context(3), true, generation));
        long beforeCleanup = FabricChemicalClientLifecycle.generation();
        FabricChemicalClientLifecycle.disconnect();
        assertEquals(beforeCleanup + 1, FabricChemicalClientLifecycle.generation());
        FabricChemicalClientLifecycle.disconnect();
        assertEquals(beforeCleanup + 1, FabricChemicalClientLifecycle.generation(), "repeated cleanup must be idempotent");
        assertEquals(0, ChemicalDataContext.current().epoch());

        long queuedConfigurationGeneration = FabricChemicalClientLifecycle.beginConfiguration();
        FabricChemicalClientLifecycle.disconnect();
        assertThrows(IllegalStateException.class,
              () -> FabricChemicalClientLifecycle.publish("dead", context(1), true, queuedConfigurationGeneration));
        assertEquals(0, ChemicalDataContext.current().epoch(), "configuration work queued before disconnect cannot revive context");
    }

    @Test
    void configurationAckRechecksAuthoritativeEpochAndRequiresExactCatchUpAck() {
        ChemicalSnapshotAckPayload first = new ChemicalSnapshotAckPayload("race", 1);
        assertEquals(FabricChemicalNetworking.AckDecision.COMPLETE,
              FabricChemicalNetworking.decideAck("race", 1, first, 1));
        assertEquals(FabricChemicalNetworking.AckDecision.RESEND,
              FabricChemicalNetworking.decideAck("race", 1, first, 2));
        assertThrows(IllegalArgumentException.class,
              () -> FabricChemicalNetworking.decideAck("race", 2, first, 2), "old ACK cannot complete the resent epoch");
        assertEquals(FabricChemicalNetworking.AckDecision.COMPLETE,
              FabricChemicalNetworking.decideAck("race", 2, new ChemicalSnapshotAckPayload("race", 2), 2));
        assertThrows(IllegalArgumentException.class,
              () -> FabricChemicalNetworking.decideAck("other", 2, new ChemicalSnapshotAckPayload("race", 2), 2));
        assertThrows(IllegalArgumentException.class,
              () -> FabricChemicalNetworking.decideAck("race", 2, new ChemicalSnapshotAckPayload("race", 2), 1));
    }

    @Test
    void staleSessionRegressingEpochAndLateGenerationCannotReviveClient() {
        long generation = FabricChemicalClientLifecycle.generation();
        FabricChemicalClientLifecycle.publish("one", context(1), true, generation);
        assertThrows(IllegalArgumentException.class, () -> FabricChemicalClientLifecycle.publish("one", context(1), false, generation));
        assertThrows(IllegalArgumentException.class, () -> FabricChemicalClientLifecycle.publish("other", context(2), false, generation));
        ChemicalSnapshotPayload queued = ChemicalSnapshotPayload.from("one", context(3));
        FabricChemicalClientLifecycle.disconnect();
        assertThrows(IllegalStateException.class, () -> FabricChemicalClientLifecycle.publish("one", context(3), false, generation));
        AtomicBoolean staleAccepted = new AtomicBoolean();
        AtomicBoolean staleRejected = new AtomicBoolean();
        assertDoesNotThrow(() -> FabricChemicalClientNetworking.accept(queued, false, generation,
              () -> staleAccepted.set(true), () -> staleRejected.set(true)));
        assertFalse(staleAccepted.get());
        assertFalse(staleRejected.get(), "an old queued task must not disconnect the replacement connection");
        assertEquals(0, ChemicalDataContext.current().epoch());

        ResourceLocation missing = ResourceLocation.fromNamespaceAndPath("other", "missing");
        ChemicalSnapshotPayload malformed = new ChemicalSnapshotPayload("new", 1, true, 2.5, false, List.of(),
              List.of(new ChemicalSnapshotPayload.Fuel(missing, 1, 1)), List.of(), List.of(), List.of());
        AtomicBoolean currentRejected = new AtomicBoolean();
        assertDoesNotThrow(() -> FabricChemicalClientNetworking.accept(malformed, true, FabricChemicalClientLifecycle.generation(),
              () -> fail("malformed snapshot must not publish"), () -> currentRejected.set(true)));
        assertTrue(currentRejected.get());
        assertEquals(0, ChemicalDataContext.current().epoch());
    }

    private static ChemicalDataContext context(long epoch) {
        Map<MekanismDataMapType<Chemical, ?>, Map<ResourceKey<Chemical>, ?>> maps = new IdentityHashMap<>();
        maps.put(IMekanismDataMapTypes.INSTANCE.chemicalSolidTag(), Map.of(MekanismChemicals.HYDROGEN.getKey(),
              new ChemicalSolidTag(TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "ores/test")))));
        maps.put(IMekanismDataMapTypes.INSTANCE.chemicalFuel(), Map.of(MekanismChemicals.HYDROGEN.getKey(), new ChemicalFuel(20, 40)));
        maps.put(IMekanismDataMapTypes.INSTANCE.chemicalRadioactivity(), Map.of(MekanismChemicals.NUCLEAR_WASTE.getKey(), new ChemicalRadioactivity(0.01)));
        maps.put(IMekanismDataMapTypes.INSTANCE.cooledChemicalCoolant(), Map.of(MekanismChemicals.SODIUM.getKey(),
              new CooledCoolant(MekanismChemicals.SUPERHEATED_SODIUM, 5, 1)));
        maps.put(IMekanismDataMapTypes.INSTANCE.heatedChemicalCoolant(), Map.of(MekanismChemicals.SUPERHEATED_SODIUM.getKey(),
              new HeatedCoolant(MekanismChemicals.SODIUM, 5, 1, 1_200)));
        return new ChemicalDataContext(epoch, maps, true, 2.5, false);
    }
}
