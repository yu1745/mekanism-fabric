package mekanism.fabric.network;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Bounded scalar gear-config transport. Schema and ranges are validated by the receiving config owner. */
public record GearConfigSnapshotPayload(String session, Map<String, Object> values) implements CustomPacketPayload {
    public static final int MAX_VALUES = 256;
    public static final int MAX_PATH_LENGTH = 128;
    public static final int MAX_SESSION_LENGTH = 64;
    public static final Type<GearConfigSnapshotPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("mekanism", "gear_config"));
    public static final StreamCodec<FriendlyByteBuf, GearConfigSnapshotPayload> CODEC = new StreamCodec<>() {
        @Override
        public GearConfigSnapshotPayload decode(FriendlyByteBuf buffer) {
            String session = buffer.readUtf(MAX_SESSION_LENGTH);
            int count = buffer.readVarInt();
            if (count < 0 || count > MAX_VALUES) throw new IllegalArgumentException("Invalid gear config entry count");
            Map<String, Object> values = new LinkedHashMap<>();
            for (int i = 0; i < count; i++) {
                String path = buffer.readUtf(MAX_PATH_LENGTH);
                Object value = switch (buffer.readUnsignedByte()) {
                    case 0 -> {
                        int bit = buffer.readUnsignedByte();
                        if (bit > 1) throw new IllegalArgumentException("Invalid gear config boolean");
                        yield bit == 1;
                    }
                    case 1 -> buffer.readInt();
                    case 2 -> buffer.readLong();
                    case 3 -> buffer.readDouble();
                    default -> throw new IllegalArgumentException("Unknown gear config value type");
                };
                if (values.putIfAbsent(path, value) != null) throw new IllegalArgumentException("Duplicate gear config path");
            }
            return new GearConfigSnapshotPayload(session, values);
        }

        @Override
        public void encode(FriendlyByteBuf buffer, GearConfigSnapshotPayload payload) {
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
                } else {
                    throw new IllegalArgumentException("Unsupported gear config value");
                }
            });
        }
    };

    public GearConfigSnapshotPayload {
        validateSession(session);
        Objects.requireNonNull(values);
        if (values.size() > MAX_VALUES) throw new IllegalArgumentException("Too many gear config entries");
        values.forEach((path, value) -> {
            if (path == null || path.isEmpty() || path.length() > MAX_PATH_LENGTH || !path.matches("[a-zA-Z0-9_.-]+")) {
                throw new IllegalArgumentException("Invalid gear config path");
            }
            if (!(value instanceof Boolean || value instanceof Integer || value instanceof Long || value instanceof Double)
                  || value instanceof Double number && !Double.isFinite(number)) {
                throw new IllegalArgumentException("Invalid gear config scalar");
            }
        });
        values = Map.copyOf(values);
    }

    private static void validateSession(String session) {
        if (session == null || session.isEmpty() || session.length() > MAX_SESSION_LENGTH
              || !session.matches("[a-zA-Z0-9-]+")) throw new IllegalArgumentException("Invalid gear config session");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record Ack(String session) implements CustomPacketPayload {
        public static final Type<Ack> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("mekanism", "gear_config_ack"));
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
