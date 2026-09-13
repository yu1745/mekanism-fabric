package mekanism.api.chemical;

import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.common.registries.MekanismChemicals;
import mekanism.fabric.registry.FabricDefaultedRegistry;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChemicalCodecTankConnectionTest {
    private static RegistryAccess access;
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        FabricDefaultedRegistry<Chemical> registry = FabricDefaultedRegistry.create(ChemicalRegistry.REGISTRY_KEY, ChemicalRegistry.EMPTY_KEY, true);
        ChemicalRegistry.install(registry);
        Chemical empty = new Chemical(ChemicalBuilder.builder());
        Holder.Reference<Chemical> holder = registry.register(ChemicalRegistry.EMPTY_KEY, empty, RegistrationInfo.BUILT_IN);
        ChemicalRegistry.publishEmpty(empty, holder);
        MekanismChemicals.CHEMICALS.registerAll(registry);
        access = new RegistryAccess.ImmutableRegistryAccess(List.of(registry));
    }

    @Test
    void optionalStreamRoundTripsOddTrueLongExactly() {
        long amount = (1L << 60) + 123;
        ChemicalStack decoded = roundTrip(ChemicalStack.OPTIONAL_STREAM_CODEC, new ChemicalStack(MekanismChemicals.HYDROGEN, amount));
        assertEquals(amount, decoded.getAmount());
        assertSame(MekanismChemicals.HYDROGEN.get(), decoded.getChemical());
    }

    @Test
    void optionalStreamCanonicalizesNonpositiveAmounts() {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), access);
        buffer.writeVarLong(0);
        assertSame(ChemicalStack.EMPTY, ChemicalStack.OPTIONAL_STREAM_CODEC.decode(buffer));
    }

    @Test
    void strictStreamRejectsEmptyInBothDirections() {
        RegistryFriendlyByteBuf encoded = new RegistryFriendlyByteBuf(Unpooled.buffer(), access);
        assertThrows(io.netty.handler.codec.EncoderException.class, () -> ChemicalStack.STREAM_CODEC.encode(encoded, ChemicalStack.EMPTY));
        RegistryFriendlyByteBuf decoded = new RegistryFriendlyByteBuf(Unpooled.buffer(), access); decoded.writeVarLong(0);
        assertThrows(io.netty.handler.codec.DecoderException.class, () -> ChemicalStack.STREAM_CODEC.decode(decoded));
    }

    @Test
    void nbtRoundTripKeepsRegistryNameAndLongAmount() {
        long amount = Long.MAX_VALUE - 9;
        ChemicalStack source = new ChemicalStack(MekanismChemicals.OXYGEN, amount);
        CompoundTag tag = (CompoundTag) source.save(access);
        ChemicalStack decoded = ChemicalStack.parse(access, tag).orElseThrow();
        assertEquals(amount, decoded.getAmount());
        assertSame(source.getChemicalHolder(), decoded.getChemicalHolder());
    }

    @Test
    void tankUsesChemicalUnitsWithoutFluidFactor() {
        BasicChemicalTank tank = (BasicChemicalTank) BasicChemicalTank.createAllValid(Long.MAX_VALUE, null);
        long amount = 81_001;
        tank.insert(new ChemicalStack(MekanismChemicals.STEAM, amount), Action.EXECUTE, AutomationType.INTERNAL);
        assertEquals(amount, tank.getStored());
        assertEquals(amount, tank.serializeNBT(access).getCompound("stored").getLong("amount"));
    }

    @Test
    void simulateAndExecuteKeepListenerBoundary() {
        AtomicInteger calls = new AtomicInteger();
        BasicChemicalTank tank = (BasicChemicalTank) BasicChemicalTank.createAllValid(10, calls::incrementAndGet);
        ChemicalStack stack = new ChemicalStack(MekanismChemicals.BRINE, 11);
        assertEquals(1, tank.insert(stack, Action.SIMULATE, AutomationType.EXTERNAL).getAmount());
        assertEquals(0, calls.get());
        assertEquals(1, tank.insert(stack, Action.EXECUTE, AutomationType.EXTERNAL).getAmount());
        assertEquals(1, calls.get());
    }

    private static ChemicalStack roundTrip(net.minecraft.network.codec.StreamCodec<RegistryFriendlyByteBuf, ChemicalStack> codec, ChemicalStack value) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), access);
        codec.encode(buffer, value);
        return codec.decode(buffer);
    }
}
