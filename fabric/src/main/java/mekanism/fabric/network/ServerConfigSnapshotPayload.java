package mekanism.fabric.network;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Bounded scalar/string-list server-config transport. Schema and ranges are validated by the receiving config owner. */
public record ServerConfigSnapshotPayload(String session, Map<String, Object> values) implements CustomPacketPayload {
    public static final int MAX_VALUES = 512;
    public static final int MAX_LIST_VALUES = 16_384;
    public static final int MAX_STRING_LENGTH = 32_767;
    public static final int MAX_PATH_LENGTH = 128;
    public static final int MAX_SESSION_LENGTH = 64;
    public static final Type<ServerConfigSnapshotPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("mekanism", "server_config"));
    public static final StreamCodec<FriendlyByteBuf, ServerConfigSnapshotPayload> CODEC = new StreamCodec<>() {
        @Override
        public ServerConfigSnapshotPayload decode(FriendlyByteBuf buffer) {
            String session = buffer.readUtf(MAX_SESSION_LENGTH);
            int count = buffer.readVarInt();
            if (count < 0 || count > MAX_VALUES) throw new IllegalArgumentException("Invalid server config entry count");
            Map<String, Object> values = new LinkedHashMap<>();
            for (int i = 0; i < count; i++) {
                String path = buffer.readUtf(MAX_PATH_LENGTH);
                Object value = switch (buffer.readUnsignedByte()) {
                    case 0 -> {
                        int bit = buffer.readUnsignedByte();
                        if (bit > 1) throw new IllegalArgumentException("Invalid server config boolean");
                        yield bit == 1;
                    }
                    case 1 -> buffer.readInt();
                    case 2 -> buffer.readLong();
                    case 3 -> buffer.readDouble();
                    case 4 -> {
                        int size = buffer.readVarInt();
                        if (size < 0 || size > MAX_LIST_VALUES) throw new IllegalArgumentException("Invalid config list size");
                        List<String> strings = new ArrayList<>(size);
                        for (int j = 0; j < size; j++) strings.add(buffer.readUtf(MAX_STRING_LENGTH));
                        yield List.copyOf(strings);
                    }
                    default -> throw new IllegalArgumentException("Unknown server config value type");
                };
                if (values.putIfAbsent(path, value) != null) throw new IllegalArgumentException("Duplicate server config path");
            }
            return new ServerConfigSnapshotPayload(session, values);
        }

        @Override
        public void encode(FriendlyByteBuf buffer, ServerConfigSnapshotPayload payload) {
            buffer.writeUtf(payload.session, MAX_SESSION_LENGTH);
            buffer.writeVarInt(payload.values.size());
            payload.values.forEach((path, value) -> {
                buffer.writeUtf(path, MAX_PATH_LENGTH);
                if (value instanceof Boolean flag) {
                    buffer.writeByte(0);
                    buffer.writeByte(flag ? 1 : 0);
                } else if (value instanceof Integer number) {
                    buffer.writeByte(1);
                    buffer.writeInt(number);
                } else if (value instanceof Long number) {
                    buffer.writeByte(2);
                    buffer.writeLong(number);
                } else if (value instanceof Double number) {
                    buffer.writeByte(3);
                    buffer.writeDouble(number);
                } else if (value instanceof List<?> list) {
                    buffer.writeByte(4);
                    buffer.writeVarInt(list.size());
                    for (Object element : list) buffer.writeUtf((String) element, MAX_STRING_LENGTH);
                } else {
                    throw new IllegalArgumentException("Unsupported server config value");
                }
            });
        }
    };

    public ServerConfigSnapshotPayload {
        validateSession(session);
        Objects.requireNonNull(values);
        if (values.size() > MAX_VALUES) throw new IllegalArgumentException("Too many server config entries");
        Map<String, Object> immutable = new LinkedHashMap<>();
        values.forEach((path, value) -> {
            if (path == null || path.isEmpty() || path.length() > MAX_PATH_LENGTH || !path.matches("[a-zA-Z0-9_.-]+")) {
                throw new IllegalArgumentException("Invalid server config path");
            }
            boolean stringList = value instanceof List<?> list && list.size() <= MAX_LIST_VALUES
                  && list.stream().allMatch(element -> element instanceof String string && string.length() <= MAX_STRING_LENGTH);
            if (!(value instanceof Boolean || value instanceof Integer || value instanceof Long || value instanceof Double || stringList)
                  || value instanceof Double number && !Double.isFinite(number)) {
                throw new IllegalArgumentException("Invalid server config scalar");
            }
            immutable.put(path, value instanceof List<?> list ? List.copyOf(list) : value);
        });
        values = Map.copyOf(immutable);
    }

    private static void validateSession(String session) {
        if (session == null || session.isEmpty() || session.length() > MAX_SESSION_LENGTH
              || !session.matches("[a-zA-Z0-9-]+")) throw new IllegalArgumentException("Invalid server config session");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record Ack(String session) implements CustomPacketPayload {
        public static final Type<Ack> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("mekanism", "server_config_ack"));
        public static final StreamCodec<FriendlyByteBuf, Ack> CODEC = new StreamCodec<>() {
            @Override
            public Ack decode(FriendlyByteBuf buffer) {
                return new Ack(buffer.readUtf(MAX_SESSION_LENGTH));
            }

            @Override
            public void encode(FriendlyByteBuf buffer, Ack value) {
                buffer.writeUtf(value.session, MAX_SESSION_LENGTH);
            }
        };

        public Ack {
            validateSession(session);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
