package mekanism.additions.common.voice;

import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Set;
import mekanism.additions.common.config.MekanismAdditionsConfig;
import mekanism.api.MekanismAPI;

public class VoiceServerManager {

    private final net.minecraft.server.MinecraftServer server;

    public VoiceServerManager(net.minecraft.server.MinecraftServer server) {
        this.server = server;
    }

    private final Set<VoiceConnection> connections = new java.util.concurrent.CopyOnWriteArraySet<>();
    private ServerSocket serverSocket;
    private Thread listenThread;
    private boolean foundLocal = false;
    private volatile boolean running;

    public void start() {
        MekanismAPI.logger.info("VoiceServer: Starting up server...");
        try {
            running = true;
            serverSocket = new ServerSocket(MekanismAdditionsConfig.additions.voicePort.get());
            (listenThread = new ListenThread()).start();
        } catch (Exception failure) {
            running = false;
            MekanismAPI.logger.error("VoiceServer: Failed to start server.", failure);
        }
    }

    public boolean isRunning() {
        return running;
    }

    public void stop() {
        running = false;
        for (VoiceConnection connection : connections) connection.close();
        connections.clear();
        try {
            MekanismAPI.logger.info("VoiceServer: Shutting down server...");
            try {
                listenThread.interrupt();
            } catch (Exception ignored) {
            }
            foundLocal = false;
            try {
                serverSocket.close();
                serverSocket = null;
            } catch (Exception ignored) {
            }
        } catch (Exception e) {
            MekanismAPI.logger.error("VoiceServer: Error while shutting down server.", e);
        }
        running = false;
    }

    public void removeConnection(VoiceConnection connection) {
        connections.remove(connection);
    }

    public boolean isFoundLocal() {
        return foundLocal;
    }

    public void setFoundLocal(boolean found) {
        foundLocal = found;
    }

    public void sendToPlayers(short byteCount, byte[] audioData, VoiceConnection connection) {
        // World/player state is read on the owning server thread; socket writes remain on the connection thread.
        java.util.List<VoiceConnection> recipients;
        try {
            recipients = server.submit(() -> {
            java.util.List<VoiceConnection> result = new java.util.ArrayList<>();
            if (!running || connection.getPlayer() == null) return result;
            int channel = connection.getCurrentChannel();
            if (channel == 0) return result;
            for (VoiceConnection target : connections) {
                if (target != connection && target.getPlayer() != null && target.canListen(channel)) result.add(target);
            }
            return result;
            }).get();
        } catch (InterruptedException cancelled) {
            Thread.currentThread().interrupt();
            return;
        } catch (java.util.concurrent.ExecutionException failure) {
            throw new IllegalStateException("VoiceServer: Unable to read channel state on the server thread.", failure.getCause());
        }
        for (VoiceConnection recipient : recipients) recipient.sendToPlayer(byteCount, audioData, connection);
    }

    private class ListenThread extends Thread {

        private ListenThread() {
            super("VoiceServer Listen Thread");
            setDaemon(true);
        }

        @Override
        public void run() {
            while (running) {
                try {
                    Socket s = serverSocket.accept();
                    VoiceConnection connection = new VoiceConnection(server, VoiceServerManager.this, s);
                    connections.add(connection);
                    if (running) connection.start();
                    else connection.close();
                    MekanismAPI.logger.info("VoiceServer: Accepted new connection.");
                } catch (SocketException | NullPointerException ignored) {
                } catch (Exception e) {
                    MekanismAPI.logger.error("VoiceServer: Error while accepting connection.", e);
                }
            }
        }
    }
}