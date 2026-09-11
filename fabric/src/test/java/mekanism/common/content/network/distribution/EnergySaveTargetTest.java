package mekanism.common.content.network.distribution;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import mekanism.api.energy.IEnergyContainer;
import mekanism.common.util.EmitUtils;
import org.junit.jupiter.api.Test;

class EnergySaveTargetTest {

    @Test
    void saveHandlersSimulateCapacityDistributeFairlyAndPublishOnlyOnSave() {
        RecordingSaveHandler three = new RecordingSaveHandler(3, 31);
        RecordingSaveHandler seven = new RecordingSaveHandler(7, 71);
        RecordingSaveHandler zero = new RecordingSaveHandler(0, 1);
        EnergySaveTarget<RecordingSaveHandler> target = new EnergySaveTarget<>(List.of(three, seven, zero));

        assertEquals(10, EmitUtils.sendToAcceptors(target, 20L, null));
        assertEquals(List.of(), three.saved);
        assertEquals(List.of(), seven.saved);
        assertEquals(List.of(), zero.saved);
        assertEquals(List.of(20L), three.simulations);
        assertEquals(List.of(17L), seven.simulations);
        assertEquals(List.of(10L), zero.simulations);
        assertEquals(103, target.getStored());

        target.save();
        target.save();
        assertEquals(List.of(3L, 3L), three.saved);
        assertEquals(List.of(7L, 7L), seven.saved);
        assertEquals(List.of(0L, 0L), zero.saved);
    }

    @Test
    void delegateSnapshotsCapacityPublishesStorageAndTotalsClamp() {
        RecordingContainer delegate = new RecordingContainer(Long.MAX_VALUE - 2, 20);
        EnergySaveTarget.DelegateSaveHandler delegateHandler = new EnergySaveTarget.DelegateSaveHandler(delegate);
        delegate.maxEnergy = 999;
        RecordingSaveHandler extra = new RecordingSaveHandler(5, 10);
        EnergySaveTarget<EnergySaveTarget.SaveHandler> target = new EnergySaveTarget<>(List.of(delegateHandler, extra));

        assertEquals(Long.MAX_VALUE, target.getStored());
        assertEquals(7, EmitUtils.sendToAcceptors(target, 7L, null));
        assertEquals(List.of(), delegate.setValues);
        target.save();
        assertEquals(List.of(4L), delegate.setValues);
        assertEquals(4, delegate.getEnergy());
        assertEquals(1, delegate.maxReads);
    }

    private static final class RecordingSaveHandler extends EnergySaveTarget.SaveHandler {
        private final List<Long> simulations = new ArrayList<>();
        private final List<Long> saved = new ArrayList<>();
        private final long stored;

        private RecordingSaveHandler(long capacity, long stored) {
            super(capacity);
            this.stored = stored;
        }

        @Override
        protected long simulate(long amount) {
            simulations.add(amount);
            return super.simulate(amount);
        }

        @Override protected void save(long currentStored) { saved.add(currentStored); }
        @Override protected long getStored() { return stored; }
    }

    private static final class RecordingContainer implements IEnergyContainer {
        private long energy;
        private long maxEnergy;
        private int maxReads;
        private final List<Long> setValues = new ArrayList<>();

        private RecordingContainer(long energy, long maxEnergy) {
            this.energy = energy;
            this.maxEnergy = maxEnergy;
        }

        @Override public long getEnergy() { return energy; }
        @Override public void setEnergy(long energy) { this.energy = energy; setValues.add(energy); }
        @Override public long getMaxEnergy() { maxReads++; return maxEnergy; }
        @Override public void deserializeNBT(net.minecraft.core.HolderLookup.Provider provider, net.minecraft.nbt.CompoundTag nbt) { }
        @Override public void onContentsChanged() { }
    }
}
