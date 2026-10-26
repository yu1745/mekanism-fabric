package mekanism.common.base;

import com.mojang.authlib.GameProfile;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.Function;
import mekanism.common.util.MekanismUtils;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketSendListener;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.stats.Stat;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.MenuProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Global, shared FakePlayer for Mekanism-specific uses
 * <p>
 * This was introduced to fix <a href="https://github.com/dizzyd/Mekanism/issues/2">https://github.com/dizzyd/Mekanism/issues/2</a>. In that issue, another mod was trying
 * to apply a potion to the fake player and causing the whole system to crash due to essential potion related structures not being initialized for a fake player.
 * <p>
 * The broader problem is that the FakePlayer in Forge 14.23.5.2768 isn't really complete and short of patching Forge and requiring everyone in the world to upgrade,
 * there's no easy fix -- so we introduce our own FakePlayer that will let us override other methods as necessary.
 * <p>
 * Use of the fake player is via a consumer type lambda, where usage is only valid inside the lambda. Afterwards it may be garbage collected at any point.
 * <p>
 * Supports emulating a specific UUID, for use with TileComponentSecurity
 * <p>
 * Fabric port delta: the upstream base was NeoForge's {@code FakePlayer}; Fabric has no fake-player utility, so this port carries the same semantics directly
 * on a {@link ServerPlayer} — inert ticking/messages/stats/death, invulnerable and non-PvP, riding refused, menus refused, and a null-channel connection whose
 * sends are no-ops, mirroring the upstream dummy connection. Advancement state is the vanilla in-constructor object: it never persists, because the shared
 * instance never ticks or logs out. The shared profile derives from the same UUID seed ("mekanism.common") and display name ("Mekanism", the upstream
 * {@code LOG_TAG}) as the excluded entrypoint's {@code gameProfile} constant.
 */
public class MekFakePlayer extends ServerPlayer {

    /**
     * The GameProfile used by the dummy Mekanism player
     */
    public static final GameProfile GAME_PROFILE = new GameProfile(UUID.nameUUIDFromBytes("mekanism.common".getBytes(StandardCharsets.UTF_8)), "Mekanism");

    private static WeakReference<MekFakePlayer> INSTANCE;

    /**
     * UUID of a player we are pretending to be, null to use the default Mek one
     */
    private UUID emulatingUUID = null;

    private MekFakePlayer(ServerLevel world) {
        super(world.getServer(), world, new FakeGameProfile(), ClientInformation.createDefault());
        ((FakeGameProfile) this.getGameProfile()).setMyFakePlayer(this);
        this.connection = new MekFakePlayerNetHandler(world.getServer(), this);
    }

    @Override
    public boolean canBeAffected(@NotNull MobEffectInstance effect) {
        return false;
    }

    public void setEmulatingUUID(UUID uuid) {
        this.emulatingUUID = uuid;
    }

    @NotNull
    @Override
    public UUID getUUID() {
        return this.emulatingUUID == null ? super.getUUID() : this.emulatingUUID;
    }

    public void cleanupFakePlayer(ServerLevel world) {
        emulatingUUID = null;
        //don't keep reference to the World, note we set it to the overworld to avoid any potential null pointers
        setServerLevel(world.getServer().overworld());
    }

    @SuppressWarnings("WeakerAccess")
    public static MekFakePlayer setupFakePlayer(ServerLevel world) {
        MekFakePlayer actual = INSTANCE == null ? null : INSTANCE.get();
        if (actual == null) {
            actual = new MekFakePlayer(world);
            INSTANCE = new WeakReference<>(actual);
        }
        MekFakePlayer player = actual;
        player.setServerLevel(world);
        return player;
    }

    public static MekFakePlayer setupFakePlayer(ServerLevel world, double x, double y, double z) {
        MekFakePlayer player = setupFakePlayer(world);
        player.setPosRaw(x, y, z);
        return player;
    }

    /**
     * Acquire a Fake Player and call a function which makes use of the player. Afterwards, the Fake Player's world is nulled out to prevent GC issues. Emulated UUID is
     * also reset.
     * <br>
     * Do NOT store a reference to the Fake Player, so that it may be Garbage Collected. A fake player _should_ only need to be short-lived
     *
     * @param world              World to set on the fake player
     * @param fakePlayerConsumer consumer of the fake player
     * @param <R>                Result of a computation, etc
     *
     * @return the return value of fakePlayerConsumer
     */
    @SuppressWarnings("WeakerAccess")
    public static <R> R withFakePlayer(ServerLevel world, Function<MekFakePlayer, R> fakePlayerConsumer) {
        MekFakePlayer player = setupFakePlayer(world);
        R result = fakePlayerConsumer.apply(player);
        player.cleanupFakePlayer(world);
        return result;
    }

    /**
     * Same as {@link MekFakePlayer#withFakePlayer(ServerLevel, java.util.function.Function)} but sets the Fake Player's position. Use when you think the entity position
     * is relevant.
     *
     * @param world              World to set on the fake player
     * @param fakePlayerConsumer consumer of the fake player
     * @param x                  X pos to set
     * @param y                  Y pos to set
     * @param z                  Z pos to set
     * @param <R>                Result of a computation, etc
     *
     * @return the return value of fakePlayerConsumer
     */
    public static <R> R withFakePlayer(ServerLevel world, double x, double y, double z, Function<MekFakePlayer, R> fakePlayerConsumer) {
        MekFakePlayer player = setupFakePlayer(world, x, y, z);
        R result = fakePlayerConsumer.apply(player);
        player.cleanupFakePlayer(world);
        return result;
    }

    public static void releaseInstance(ServerLevel world) {
        // If the fake player has a reference to the world getting unloaded,
        // null out the fake player so that the world can unload
        MekFakePlayer actual = INSTANCE == null ? null : INSTANCE.get();
        if (actual != null && actual.serverLevel() == world) {
            //don't keep reference to the World, note we set it to the overworld to avoid any potential null pointers
            actual.setServerLevel(world.getServer().overworld());
        }
    }

    /**
     * Fabric port replacement for the NeoForge-injected {@code Entity#isFakePlayer()} check used by the teleporter and personal-storage guards.
     */
    public static boolean isFakePlayer(@Nullable Entity entity) {
        return entity instanceof MekFakePlayer;
    }

    //Carrier overrides mirroring the upstream NeoForge FakePlayer base: the fake player never ticks, never shows messages or stats,
    // never dies from damage, and never rides or opens menus.
    @Override
    public void tick() {
    }

    @Override
    public void displayClientMessage(Component chatComponent, boolean actionBar) {
    }

    @Override
    public void awardStat(Stat<?> stat, int amount) {
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return true;
    }

    @Override
    public boolean canHarmPlayer(Player player) {
        return false;
    }

    @Override
    public void die(DamageSource source) {
    }

    @Override
    public void updateOptions(ClientInformation information) {
    }

    @Override
    public OptionalInt openMenu(@Nullable MenuProvider menuProvider) {
        return OptionalInt.empty();
    }

    @Override
    public boolean startRiding(Entity entity, boolean force) {
        return false;
    }

    /**
     * Game profile supporting our UUID emulation
     */
    private static class FakeGameProfile extends GameProfile {

        private MekFakePlayer myFakePlayer = null;

        public FakeGameProfile() {
            super(GAME_PROFILE.getId(), GAME_PROFILE.getName());
        }

        private UUID getEmulatingUUID() {
            return myFakePlayer == null ? null : myFakePlayer.emulatingUUID;
        }

        private void setMyFakePlayer(MekFakePlayer myFakePlayer) {
            this.myFakePlayer = myFakePlayer;
        }

        @Override
        public UUID getId() {
            UUID emulatingUUID = getEmulatingUUID();
            return emulatingUUID == null ? super.getId() : emulatingUUID;
        }

        @Override
        public String getName() {
            UUID emulatingUUID = getEmulatingUUID();
            return emulatingUUID == null ? super.getName() : MekanismUtils.getLastKnownUsername(emulatingUUID);
        }

        //NB: super check they're the same class, we only check that name & id match
        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof GameProfile that)) {
                return false;
            }
            return Objects.equals(getId(), that.getId()) && Objects.equals(getName(), that.getName());
        }

        @Override
        public int hashCode() {
            UUID id = getId();
            String name = getName();
            int result = id == null ? 0 : id.hashCode();
            result = 31 * result + (name == null ? 0 : name.hashCode());
            return result;
        }
    }

    /**
     * Null-channel connection: sends are dropped, matching the upstream dummy connection so retained fake-player internals never touch a real socket.
     */
    private static class MekFakeConnection extends Connection {

        public MekFakeConnection() {
            super(PacketFlow.SERVERBOUND);
        }

        @Override
        public void setListenerForServerboundHandshake(PacketListener listener) {
        }

        @Override
        public void send(Packet<?> packet) {
        }

        @Override
        public void send(Packet<?> packet, @Nullable PacketSendListener packetSendListener) {
        }

        @Override
        public void send(Packet<?> packet, @Nullable PacketSendListener packetSendListener, boolean ensureRunning) {
        }
    }

    /**
     * Inert packet handler mirroring the upstream {@code FakePlayerNetHandler}: constructed against the dummy connection, never ticks, never disconnects.
     */
    private static class MekFakePlayerNetHandler extends ServerGamePacketListenerImpl {

        private static final Connection DUMMY_CONNECTION = new MekFakeConnection();

        public MekFakePlayerNetHandler(MinecraftServer server, ServerPlayer player) {
            super(server, DUMMY_CONNECTION, player, CommonListenerCookie.createInitial(player.getGameProfile(), false));
        }

        @Override
        public void tick() {
        }

        @Override
        public void resetPosition() {
        }

        @Override
        public void disconnect(Component disconnectionMessage) {
        }
    }
}
