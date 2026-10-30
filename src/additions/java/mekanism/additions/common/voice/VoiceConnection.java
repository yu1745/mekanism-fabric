package mekanism.additions.common.voice;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;
import java.util.UUID;
import mekanism.additions.common.item.ItemWalkieTalkie.WalkieData;
import mekanism.additions.common.registries.AdditionsDataComponents;
import mekanism.api.MekanismAPI;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public class VoiceConnection extends Thread {

    private final MinecraftServer server;
    private DataOutputStream output;
    private DataInputStream input;
    private volatile boolean open = true;
    private final Socket socket;
    private final VoiceServerManager manager;
    private UUID uuid;

    VoiceConnection(MinecraftServer server, VoiceServerManager manager, Socket s) {
        super("VoiceServer Connection Thread");
        this.server = server;
        this.manager = manager;
        socket = s;
        //Note: This is set as a Daemon thread because the parent thread is a daemon thread
    }

    @Override
    public void run() {
        try {
            input = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            output = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            synchronized (manager) {
                int retryCount = 0;
                while (uuid == null && retryCount <= 100 && open && manager.isRunning()) {
                    uuid = server.submit(() -> {
                        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                            String playerIP = player.getIpAddress();
                            if (!server.isDedicatedServer() && player.connection.getRemoteAddress() instanceof io.netty.channel.local.LocalAddress && !manager.isFoundLocal()) {
                                manager.setFoundLocal(true);
                                return player.getUUID();
                            } else if (playerIP.equals(socket.getInetAddress().getHostAddress())) {
                                return player.getUUID();
                            }
                        }
                        return (UUID) null;
                    }).get();
                    retryCount++;
                    sleep(50);
                }
                if (uuid == null) {
                    if (open && manager.isRunning()) MekanismAPI.logger.error("VoiceServer: Unable to trace connection's IP address.");
                    return;
                }
                MekanismAPI.logger.info("VoiceServer: Traced IP for {} in {} attempts.", uuid, retryCount);
            }
            while (open && manager.isRunning()) {
                short byteCount = input.readShort();
                byte[] audioData = new byte[byteCount];
                input.readFully(audioData);
                if (byteCount > 0) manager.sendToPlayers(byteCount, audioData, this);
            }
        } catch (Exception failure) {
            if (open && manager.isRunning() && !(failure instanceof java.io.EOFException)) {
                MekanismAPI.logger.debug("VoiceServer: Connection stopped.", failure);
            }
        } finally {
            close();
        }
    }

    public void close() {
        open = false;
        interrupt();
        try {
            socket.close();
        } catch (java.io.IOException failure) {
            MekanismAPI.logger.debug("VoiceServer: Socket close failed.", failure);
        }
        for (java.io.Closeable stream : new java.io.Closeable[]{input, output}) {
            if (stream != null) {
                try {
                    stream.close();
                } catch (java.io.IOException failure) {
                    MekanismAPI.logger.debug("VoiceServer: Stream close failed.", failure);
                }
            }
        }
        manager.removeConnection(this);
    }

    public synchronized void sendToPlayer(short byteCount, byte[] audioData, VoiceConnection connection) {
        if (!open) {
            return;
        }
        try {
            output.writeShort(byteCount);
            output.write(audioData);
            output.flush();
        } catch (Exception e) {
            MekanismAPI.logger.error("VoiceServer: Error while sending data to player.", e);
        }
    }

    public boolean canListen(int channel) {
        ServerPlayer player = getPlayer();
        for (ItemStack itemStack : player.getInventory().items) {
            if (canListen(channel, itemStack)) {
                return true;
            }
        }
        return canListen(channel, player.getOffhandItem());
    }

    private boolean canListen(int channel, ItemStack stack) {
        WalkieData data = stack.getOrDefault(AdditionsDataComponents.WALKIE_DATA.get(), WalkieData.DEFAULT);
        return data.running() && data.channel() == channel;
    }

    public int getCurrentChannel() {
        ServerPlayer player = getPlayer();
        int channel = getCurrentChannel(player.getMainHandItem());
        if (channel == 0) {
            channel = getCurrentChannel(player.getOffhandItem());
        }
        return channel;
    }

    private int getCurrentChannel(ItemStack stack) {
        WalkieData data = stack.getOrDefault(AdditionsDataComponents.WALKIE_DATA.get(), WalkieData.DEFAULT);
        return data.running() ? data.channel() : 0;
    }

    public ServerPlayer getPlayer() {
        return server.getPlayerList().getPlayer(uuid);
    }
}