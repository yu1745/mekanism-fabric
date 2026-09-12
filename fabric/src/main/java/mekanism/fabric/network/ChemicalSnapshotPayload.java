package mekanism.fabric.network;

import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalDataContext;
import mekanism.api.chemical.ChemicalRegistry;
import mekanism.api.datamaps.MekanismDataMapType;
import mekanism.api.datamaps.chemical.ChemicalSolidTag;
import mekanism.api.datamaps.chemical.attribute.ChemicalFuel;
import mekanism.api.datamaps.chemical.attribute.ChemicalRadioactivity;
import mekanism.api.datamaps.chemical.attribute.CooledCoolant;
import mekanism.api.datamaps.chemical.attribute.HeatedCoolant;
import mekanism.fabric.datamaps.FabricChemicalDataMaps;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/**
 * Bounded, name-based chemical map and policy snapshot shared by configuration and play phases.
 * Map values are length-delimited opaque chunks encoded by their descriptor's sole network codec;
 * clients defer descriptor decoding (and therefore registry resolution) to their owning executor.
 */
public record ChemicalSnapshotPayload(String sessionId, long epoch, boolean radiationEnabled, double feConversionRate,
                                     boolean blacklistForge, List<Solid> solids, List<Fuel> fuels,
                                     List<Radioactivity> radioactivities, List<Cooled> cooled, List<Heated> heated)
      implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("mekanism", "chemical_snapshot");
    public static final Type<ChemicalSnapshotPayload> TYPE = new Type<>(ID);
    public static final int MAX_PER_MAP = 65;
    public static final int MAX_TOTAL = 325;
    public static final int MAX_STRING = 128;
    public static final int MAX_VALUE_BYTES = 256;
    public static final int MAX_TOTAL_VALUE_BYTES = 65_536;
    public static final StreamCodec<FriendlyByteBuf, ChemicalSnapshotPayload> CODEC = new StreamCodec<>() {
        @Override
        public ChemicalSnapshotPayload decode(FriendlyByteBuf buffer) {
            String session = buffer.readUtf(64);
            long epoch = buffer.readVarLong();
            boolean radiation = buffer.readBoolean();
            double rate = buffer.readDouble();
            boolean blacklist = buffer.readBoolean();
            Allocation allocation = new Allocation();
            List<Solid> solids = readList(buffer, allocation, Solid::new);
            List<Fuel> fuels = readList(buffer, allocation, Fuel::new);
            List<Radioactivity> radioactivities = readList(buffer, allocation, Radioactivity::new);
            List<Cooled> cooled = readList(buffer, allocation, Cooled::new);
            List<Heated> heated = readList(buffer, allocation, Heated::new);
            return new ChemicalSnapshotPayload(session, epoch, radiation, rate, blacklist, solids, fuels, radioactivities, cooled, heated);
        }

        @Override
        public void encode(FriendlyByteBuf buffer, ChemicalSnapshotPayload value) {
            value.validateEnvelope();
            buffer.writeUtf(value.sessionId, 64);
            buffer.writeVarLong(value.epoch);
            buffer.writeBoolean(value.radiationEnabled);
            buffer.writeDouble(value.feConversionRate);
            buffer.writeBoolean(value.blacklistForge);
            writeList(buffer, value.solids, Solid::chemical, Solid::encoded);
            writeList(buffer, value.fuels, Fuel::chemical, Fuel::encoded);
            writeList(buffer, value.radioactivities, Radioactivity::chemical, Radioactivity::encoded);
            writeList(buffer, value.cooled, Cooled::chemical, Cooled::encoded);
            writeList(buffer, value.heated, Heated::chemical, Heated::encoded);
        }
    };

    public ChemicalSnapshotPayload {
        solids = List.copyOf(solids);
        fuels = List.copyOf(fuels);
        radioactivities = List.copyOf(radioactivities);
        cooled = List.copyOf(cooled);
        heated = List.copyOf(heated);
        validateEnvelope(sessionId, epoch, feConversionRate, List.of(solids, fuels, radioactivities, cooled, heated));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static ChemicalSnapshotPayload from(String session, ChemicalDataContext context) {
        return new ChemicalSnapshotPayload(session, context.epoch(), context.radiationEnabled(), context.feConversionRate(), context.blacklistForge(),
              map(context, FabricChemicalDataMaps.CHEMICAL_SOLID_TAG, Solid::new),
              map(context, FabricChemicalDataMaps.CHEMICAL_FUEL, Fuel::new),
              map(context, FabricChemicalDataMaps.CHEMICAL_RADIOACTIVITY, Radioactivity::new),
              map(context, FabricChemicalDataMaps.COOLED_COOLANT, Cooled::new),
              map(context, FabricChemicalDataMaps.HEATED_COOLANT, Heated::new));
    }

    /** Registry resolution and duplicate checks deliberately run only on the owning executor. */
    public ChemicalDataContext resolve() {
        validateEnvelope();
        Map<MekanismDataMapType<Chemical, ?>, Map<ResourceKey<Chemical>, ?>> maps = new java.util.IdentityHashMap<>();
        maps.put(FabricChemicalDataMaps.CHEMICAL_SOLID_TAG,
              resolve(solids, Solid::chemical, Solid::encoded, FabricChemicalDataMaps.CHEMICAL_SOLID_TAG));
        maps.put(FabricChemicalDataMaps.CHEMICAL_FUEL,
              resolve(fuels, Fuel::chemical, Fuel::encoded, FabricChemicalDataMaps.CHEMICAL_FUEL));
        maps.put(FabricChemicalDataMaps.CHEMICAL_RADIOACTIVITY,
              resolve(radioactivities, Radioactivity::chemical, Radioactivity::encoded, FabricChemicalDataMaps.CHEMICAL_RADIOACTIVITY));
        maps.put(FabricChemicalDataMaps.COOLED_COOLANT,
              resolve(cooled, Cooled::chemical, Cooled::encoded, FabricChemicalDataMaps.COOLED_COOLANT));
        maps.put(FabricChemicalDataMaps.HEATED_COOLANT,
              resolve(heated, Heated::chemical, Heated::encoded, FabricChemicalDataMaps.HEATED_COOLANT));
        return new ChemicalDataContext(epoch, maps, radiationEnabled, feConversionRate, blacklistForge);
    }

    private void validateEnvelope() {
        validateEnvelope(sessionId, epoch, feConversionRate, List.of(solids, fuels, radioactivities, cooled, heated));
    }

    private static void validateEnvelope(String session, long epoch, double rate, List<? extends List<?>> maps) {
        if (session == null || session.isBlank() || session.length() > 64 || epoch <= 0) {
            throw new IllegalArgumentException("Invalid chemical session/epoch");
        }
        if (!Double.isFinite(rate) || rate < 0.0001 || rate > 10_000) {
            throw new IllegalArgumentException("Invalid FE conversion rate");
        }
        int totalEntries = 0;
        int totalBytes = 0;
        for (List<?> map : maps) {
            if (map.size() > MAX_PER_MAP || (totalEntries += map.size()) > MAX_TOTAL) {
                throw new IllegalArgumentException("Chemical snapshot entry bound exceeded");
            }
            for (Object entry : map) {
                byte[] encoded = switch (entry) {
                    case Solid value -> value.encoded;
                    case Fuel value -> value.encoded;
                    case Radioactivity value -> value.encoded;
                    case Cooled value -> value.encoded;
                    case Heated value -> value.encoded;
                    default -> throw new IllegalArgumentException("Unknown chemical snapshot entry");
                };
                if (encoded.length > MAX_VALUE_BYTES || (totalBytes += encoded.length) > MAX_TOTAL_VALUE_BYTES) {
                    throw new IllegalArgumentException("Chemical snapshot value allocation bound exceeded");
                }
            }
        }
    }

    private static <E, V> Map<ResourceKey<Chemical>, V> resolve(List<E> entries, Function<E, ResourceLocation> key,
          Function<E, byte[]> encoded, MekanismDataMapType<Chemical, V> type) {
        Map<ResourceKey<Chemical>, V> values = new HashMap<>();
        for (E entry : entries) {
            ResourceKey<Chemical> chemical = resolveKey(key.apply(entry));
            V value = decodeValue(type, encoded.apply(entry));
            if (values.putIfAbsent(chemical, value) != null) {
                throw new IllegalArgumentException("Duplicate chemical snapshot key " + chemical.location());
            }
        }
        return Map.copyOf(values);
    }

    private static <V, E> List<E> map(ChemicalDataContext context, MekanismDataMapType<Chemical, V> type,
          BiFunction<ResourceLocation, byte[], E> factory) {
        Map<ResourceKey<Chemical>, ?> raw = context.maps().getOrDefault(type, Map.of());
        List<ResourceKey<Chemical>> keys = new ArrayList<>(raw.keySet());
        keys.sort(java.util.Comparator.comparing(key -> key.location().toString()));
        List<E> values = new ArrayList<>(keys.size());
        for (ResourceKey<Chemical> key : keys) {
            @SuppressWarnings("unchecked") V value = (V) raw.get(key);
            values.add(factory.apply(key.location(), encodeValue(type, value)));
        }
        return List.copyOf(values);
    }

    private static ResourceKey<Chemical> resolveKey(ResourceLocation id) {
        ResourceKey<Chemical> key = ResourceKey.create(ChemicalRegistry.REGISTRY_KEY, id);
        if (ChemicalRegistry.registry().getHolder(key).isEmpty()) {
            throw new IllegalArgumentException("Unknown chemical " + id);
        }
        return key;
    }

    private static RegistryAccess registryAccess() {
        return new RegistryAccess.ImmutableRegistryAccess(List.of(ChemicalRegistry.registry()));
    }

    private static <V> byte[] encodeValue(MekanismDataMapType<Chemical, V> type, V value) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registryAccess());
        try {
            type.networkCodec().orElseThrow(() -> new IllegalArgumentException("Unsynchronized chemical data map " + type.id()))
                  .encode(buffer, Objects.requireNonNull(value, "value"));
            int size = buffer.readableBytes();
            if (size > MAX_VALUE_BYTES) {
                throw new IllegalArgumentException("Chemical snapshot value exceeds chunk bound for " + type.id());
            }
            byte[] encoded = new byte[size];
            buffer.getBytes(buffer.readerIndex(), encoded);
            return encoded;
        } finally {
            buffer.release();
        }
    }

    private static <V> V decodeValue(MekanismDataMapType<Chemical, V> type, byte[] encoded) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(encoded), registryAccess());
        try {
            V value = type.networkCodec().orElseThrow(() -> new IllegalArgumentException("Unsynchronized chemical data map " + type.id()))
                  .decode(buffer);
            if (buffer.isReadable()) {
                throw new IllegalArgumentException("Trailing bytes in chemical snapshot value for " + type.id());
            }
            return Objects.requireNonNull(value, "Decoded chemical data-map value");
        } finally {
            buffer.release();
        }
    }

    private interface EntryFactory<E> {
        E create(ResourceLocation id, byte[] encoded);
    }

    private static final class Allocation {
        int entries;
        int bytes;
    }

    private static <E> List<E> readList(FriendlyByteBuf buffer, Allocation allocation, EntryFactory<E> factory) {
        int size = buffer.readVarInt();
        if (size < 0 || size > MAX_PER_MAP || (allocation.entries += size) > MAX_TOTAL) {
            throw new IllegalArgumentException("Chemical snapshot allocation bound exceeded");
        }
        List<E> result = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            ResourceLocation id = readId(buffer);
            byte[] encoded = buffer.readByteArray(MAX_VALUE_BYTES);
            if ((allocation.bytes += encoded.length) > MAX_TOTAL_VALUE_BYTES) {
                throw new IllegalArgumentException("Chemical snapshot value allocation bound exceeded");
            }
            result.add(factory.create(id, encoded));
        }
        return List.copyOf(result);
    }

    private static <E> void writeList(FriendlyByteBuf buffer, List<E> values, Function<E, ResourceLocation> key,
          Function<E, byte[]> encoded) {
        buffer.writeVarInt(values.size());
        for (E value : values) {
            writeId(buffer, key.apply(value));
            buffer.writeByteArray(encoded.apply(value));
        }
    }

    private static ResourceLocation readId(FriendlyByteBuf buffer) {
        ResourceLocation id = ResourceLocation.tryParse(buffer.readUtf(MAX_STRING));
        if (id == null) throw new IllegalArgumentException("Malformed resource location");
        return id;
    }

    private static void writeId(FriendlyByteBuf buffer, ResourceLocation id) {
        buffer.writeUtf(id.toString(), MAX_STRING);
    }

    private static byte[] copyEncoded(byte[] encoded) {
        Objects.requireNonNull(encoded, "encoded");
        if (encoded.length > MAX_VALUE_BYTES) throw new IllegalArgumentException("Chemical snapshot value exceeds chunk bound");
        return encoded.clone();
    }

    public record Solid(ResourceLocation chemical, byte[] encoded) {
        public Solid(ResourceLocation chemical, ResourceLocation representation) {
            this(chemical, encodeValue(FabricChemicalDataMaps.CHEMICAL_SOLID_TAG,
                  new ChemicalSolidTag(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, representation))));
        }
        public Solid { chemical = Objects.requireNonNull(chemical, "chemical"); encoded = copyEncoded(encoded); }
        @Override public byte[] encoded() { return encoded.clone(); }
    }

    public record Fuel(ResourceLocation chemical, byte[] encoded) {
        public Fuel(ResourceLocation chemical, int burnTicks, long energyPerTick) {
            this(chemical, encodeValue(FabricChemicalDataMaps.CHEMICAL_FUEL, new ChemicalFuel(burnTicks, energyPerTick)));
        }
        public Fuel { chemical = Objects.requireNonNull(chemical, "chemical"); encoded = copyEncoded(encoded); }
        @Override public byte[] encoded() { return encoded.clone(); }
    }

    public record Radioactivity(ResourceLocation chemical, byte[] encoded) {
        public Radioactivity(ResourceLocation chemical, double value) {
            this(chemical, encodeValue(FabricChemicalDataMaps.CHEMICAL_RADIOACTIVITY, new ChemicalRadioactivity(value)));
        }
        public Radioactivity { chemical = Objects.requireNonNull(chemical, "chemical"); encoded = copyEncoded(encoded); }
        @Override public byte[] encoded() { return encoded.clone(); }
    }

    public record Cooled(ResourceLocation chemical, byte[] encoded) {
        public Cooled { chemical = Objects.requireNonNull(chemical, "chemical"); encoded = copyEncoded(encoded); }
        @Override public byte[] encoded() { return encoded.clone(); }
    }

    public record Heated(ResourceLocation chemical, byte[] encoded) {
        public Heated { chemical = Objects.requireNonNull(chemical, "chemical"); encoded = copyEncoded(encoded); }
        @Override public byte[] encoded() { return encoded.clone(); }
    }
}
