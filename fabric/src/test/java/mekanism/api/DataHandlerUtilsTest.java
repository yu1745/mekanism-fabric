package mekanism.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

class DataHandlerUtilsTest {

    @Test
    void writeContentsPreservesIndexesAndSkipsEmptyTags() {
        TestSerializable empty = new TestSerializable(new CompoundTag());
        CompoundTag value = new CompoundTag();
        value.putInt("value", 42);
        TestSerializable populated = new TestSerializable(value);

        ListTag stored = DataHandlerUtils.writeContents(null, List.of(empty, populated), "slot");

        assertEquals(1, stored.size());
        assertEquals(1, stored.getCompound(0).getByte("slot"));
        assertEquals(42, stored.getCompound(0).getInt("value"));
        assertNull(empty.serializeProvider);
        assertNull(populated.serializeProvider);
    }

    @Test
    void readContentsIgnoresInvalidIndexesAndPassesProviderThrough() {
        TestSerializable first = new TestSerializable(new CompoundTag());
        TestSerializable second = new TestSerializable(new CompoundTag());
        ListTag stored = new ListTag();
        stored.add(entry(-1, 10));
        stored.add(entry(2, 20));
        stored.add(entry(1, 30));

        DataHandlerUtils.readContents(null, List.of(first, second), stored, "slot");

        assertEquals(0, first.deserializeCalls);
        assertEquals(1, second.deserializeCalls);
        assertEquals(30, second.deserialized.getInt("value"));
        assertNull(second.deserializeProvider);
    }

    private static CompoundTag entry(int slot, int value) {
        CompoundTag tag = new CompoundTag();
        tag.putByte("slot", (byte) slot);
        tag.putInt("value", value);
        return tag;
    }

    private static final class TestSerializable implements INBTSerializable<CompoundTag> {
        private final CompoundTag serialized;
        private HolderLookup.Provider serializeProvider;
        private HolderLookup.Provider deserializeProvider;
        private CompoundTag deserialized = new CompoundTag();
        private int deserializeCalls;

        private TestSerializable(CompoundTag serialized) {
            this.serialized = serialized;
        }

        @Override
        public CompoundTag serializeNBT(HolderLookup.Provider provider) {
            serializeProvider = provider;
            return serialized.copy();
        }

        @Override
        public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
            deserializeProvider = provider;
            deserialized = tag.copy();
            deserializeCalls++;
        }
    }
}
