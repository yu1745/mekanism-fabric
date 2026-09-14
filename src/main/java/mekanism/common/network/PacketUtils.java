package mekanism.common.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.function.Consumer;
import java.util.function.Function;
import mekanism.api.MekanismServerLifecycle;
import mekanism.common.config.MekanismConfig;
import mekanism.common.content.filter.FilterManager;
import mekanism.common.lib.math.Range3D;
import mekanism.common.lib.transmitter.DynamicBufferedNetwork;
import mekanism.common.tile.component.TileComponentConfig;
import mekanism.common.tile.component.TileComponentEjector;
import mekanism.common.tile.interfaces.ISideConfiguration;
import mekanism.common.tile.interfaces.ITileFilterHolder;
import mekanism.common.util.WorldUtils;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ByIdMap;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fabric port of the official dispatch utility.
 *
 * <p>Platform deltas (all documented call-site substitutions, no behavioral fake): {@code Mekanism.logger} is replaced by the established {@code Mekanism} SLF4J
 * category; {@code PacketDistributor.sendToServer} becomes {@link ClientPlayNetworking#send}, which (like its NeoForge counterpart) is only ever invoked on the
 * physical client and is never linked on a dedicated server; {@code PacketDistributor.sendToPlayersTrackingChunk} becomes {@link PlayerLookup#tracking(ServerLevel,
 * ChunkPos)} with per-player sends; {@code ServerLifecycleHooks.getCurrentServer()} becomes the lifecycle-bound {@link MekanismServerLifecycle} owner; the
 * {@code IPayloadContext} helper parameter becomes the plain {@link Player} that Fabric's play receiver context provides. The multi-payload overload keeps
 * vanilla bundle grouping: each recipient receives a {@link ClientboundBundlePacket} of the payloads (built through {@code ServerPlayNetworking.createS2CPacket}),
 * preserving the original within-one-tick processing guarantee that per-payload sends could split across client ticks; recipients and payload ordering
 * are unchanged.</p>
 */
public class PacketUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger("Mekanism");

    private PacketUtils() {
    }

    //Note: This might be longer as if the name can't be found we have two characters plus an uuid (36)
    public static final int LAST_USERNAME_LENGTH = Math.max(SharedConstants.MAX_PLAYER_NAME_LENGTH, 38);

    public static final StreamCodec<FriendlyByteBuf, BlockHitResult> BLOCK_HIT_RESULT_STREAM_CODEC = StreamCodec.of(FriendlyByteBuf::writeBlockHitResult, FriendlyByteBuf::readBlockHitResult);
    public static final StreamCodec<ByteBuf, InteractionHand> INTERACTION_HAND_STREAM_CODEC = enumCodec(InteractionHand.class);
    public static final StreamCodec<ByteBuf, EquipmentSlot> EQUIPMENT_SLOT_STREAM_CODEC = enumCodec(EquipmentSlot.class);
    public static final StreamCodec<ByteBuf, Vec3> VEC3_STREAM_CODEC = StreamCodec.composite(
          ByteBufCodecs.DOUBLE, Vec3::x,
          ByteBufCodecs.DOUBLE, Vec3::y,
          ByteBufCodecs.DOUBLE, Vec3::z,
          Vec3::new
    );
    public static final StreamCodec<ByteBuf, OptionalDouble> OPTIONAL_DOUBLE_STREAM_CODEC = new StreamCodec<>() {
        @NotNull
        @Override
        public OptionalDouble decode(@NotNull ByteBuf buffer) {
            return buffer.readBoolean() ? OptionalDouble.of(buffer.readDouble()) : OptionalDouble.empty();
        }

        @Override
        public void encode(@NotNull ByteBuf buffer, @NotNull OptionalDouble value) {
            if (value.isPresent()) {
                buffer.writeBoolean(true);
                buffer.writeDouble(value.getAsDouble());
            } else {
                buffer.writeBoolean(false);
            }
        }
    };

    //Similar to NeoForgeStreamCodecs#enumCodec but allows for keeping it as a ByteBuf and wrapping the value
    public static <V extends Enum<V>> StreamCodec<ByteBuf, V> enumCodec(Class<V> enumClass) {
        return ByteBufCodecs.idMapper(ByIdMap.continuous(Enum::ordinal, enumClass.getEnumConstants(), ByIdMap.OutOfBoundsStrategy.WRAP), Enum::ordinal);
    }

    public static void log(String logFormat, Object... params) {
        //TODO: Add more logging for packets using this
        if (MekanismConfig.general.logPackets.get()) {
            LOGGER.info(logFormat, params);
        }
    }

    public static <OBJ> OBJ read(RegistryAccess registryAccess, byte[] rawData, Function<RegistryFriendlyByteBuf, OBJ> deserializer) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(rawData), registryAccess);
        try {
            return deserializer.apply(buffer);
        } finally {
            buffer.release();
        }
    }

    /**
     * Serializes custom registry-aware data to a freshly allocated heap buffer and returns the raw bytes.
     *
     * <p>Fabric port delta: inline equivalent of NeoForge's {@code FriendlyByteBufUtil.writeCustomData(Consumer<RegistryFriendlyByteBuf>, RegistryAccess)} — a
     * {@link RegistryFriendlyByteBuf} over a heap buffer, the writer applied, and the readable bytes copied out. The produced byte array carries no length prefix,
     * matching the official wire format consumed by {@link #read}.</p>
     */
    public static byte[] writeCustomData(Consumer<RegistryFriendlyByteBuf> writer, RegistryAccess registryAccess) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registryAccess);
        try {
            writer.accept(buffer);
            byte[] data = new byte[buffer.readableBytes()];
            buffer.readBytes(data);
            return data;
        } finally {
            buffer.release();
        }
    }

    @Nullable
    public static TileComponentEjector ejector(Player player, BlockPos pos) {
        if (blockEntity(player, pos) instanceof ISideConfiguration sideConfig) {
            return sideConfig.getEjector();
        }
        return null;
    }

    @Nullable
    public static TileComponentConfig config(Player player, BlockPos pos) {
        if (blockEntity(player, pos) instanceof ISideConfiguration sideConfig) {
            return sideConfig.getConfig();
        }
        return null;
    }

    @Nullable
    public static FilterManager<?> filterManager(Player player, BlockPos pos) {
        if (blockEntity(player, pos) instanceof ITileFilterHolder<?> filterHolder) {
            return filterHolder.getFilterManager();
        }
        return null;
    }

    @Nullable
    public static BlockEntity blockEntity(Player player, BlockPos pos) {
        return WorldUtils.getTileEntity(player.level(), pos);
    }

    @Nullable
    public static BlockEntity blockEntity(Player player, long pos) {
        return WorldUtils.getTileEntity(player.level(), pos);
    }

    /**
     * Send this message to the server.
     *
     * @param message - the message to send
     *
     * @apiNote Only ever invoked on the physical client; {@link ClientPlayNetworking} is a client-only class and is never linked on a dedicated server.
     */
    public static <MSG extends CustomPacketPayload> boolean sendToServer(MSG message) {
        ClientPlayNetworking.send(message);
        return true;
    }

    public static <MSG extends CustomPacketPayload> void sendToAllTracking(MSG message, BlockEntity tile) {
        sendToAllTracking(message, tile.getLevel(), tile.getBlockPos());
    }

    public static <MSG extends CustomPacketPayload> void sendToAllTracking(MSG message, Level world, BlockPos pos) {
        if (world instanceof ServerLevel serverLevel) {
            //Fabric port delta: PacketDistributor.sendToPlayersTrackingChunk becomes PlayerLookup-based recipient resolution;
            // only a loaded server level has tracking players, any other level sends nothing (official client-side behavior).
            for (ServerPlayer player : PlayerLookup.tracking(serverLevel, new ChunkPos(pos))) {
                ServerPlayNetworking.send(player, message);
            }
        }
    }

    /**
     * Based on how {@code PacketDistributor#sendToPlayersTrackingChunk} finds players to send to.
     */
    public static boolean hasPlayersTracking(ServerLevel level, BlockPos pos) {
        return !PlayerLookup.tracking(level, new ChunkPos(pos)).isEmpty();
    }

    //TODO: Evaluate moving various network related packets over to this (and making it support non buffered networks)
    public static void sendToAllTracking(DynamicBufferedNetwork<?, ?, ?, ?> network, CustomPacketPayload... packets) {
        Range3D range = network.getPacketRange();
        //TODO: Create a method in DynamicNetwork to get all players that are "tracking" the network
        // Also evaluate moving various network packet things over to using this at that point
        //TODO - 1.20.4: If we just make the packet range instead keep track of the ChunkPositions that then we check if the player is tracking
        // it will allow us to have it hopefully work a bit more accurately and more cleanly?
        // and that way we can do a check that the player is tracking one of the chunks maybe instead of doing the weird radius check
        List<ServerPlayer> players = new ArrayList<>();
        int radius = MekanismServerLifecycle.getServer().getPlayerList().getViewDistance() * 16;
        for (ServerPlayer player : MekanismServerLifecycle.getServer().getPlayerList().getPlayers()) {
            if (range.dimension() == player.level().dimension()) {
                //TODO - 1.20.4: Should this use proper position instead of the block position (if we switch to tracked chunks then this becomes irrelevant)
                BlockPos playerPosition = player.blockPosition();
                int playerX = playerPosition.getX();
                int playerZ = playerPosition.getZ();
                //playerX/Z + radius is the max, so to stay in line with how it was before, it has an extra + 1 added to it
                if (playerX + radius + 1.99999 > range.xMin() && range.xMax() + 0.99999 > playerX - radius &&
                    playerZ + radius + 1.99999 > range.zMin() && range.zMax() + 0.99999 > playerZ - radius) {
                    players.add(player);
                }
            }
        }
        //Fabric port correction (Task052 REVIEW_03 finding 5): keep vanilla bundle grouping — a ClientboundBundlePacket preserves the original
        // within-one-tick processing of the payloads; per-payload sends could split processing across client ticks
        List<Packet<? super net.minecraft.network.protocol.game.ClientGamePacketListener>> bundled = new ArrayList<>(packets.length);
        for (CustomPacketPayload payload : packets) {
            bundled.add((Packet<? super net.minecraft.network.protocol.game.ClientGamePacketListener>) (Packet<?>) ServerPlayNetworking.createS2CPacket(payload));
        }
        for (ServerPlayer player : players) {
            player.connection.send(new ClientboundBundlePacket(bundled));
        }
    }
}
