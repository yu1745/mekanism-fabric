package mekanism.additions.client.voice;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.ConnectException;
import java.net.Socket;
import javax.sound.sampled.AudioFormat;
import mekanism.additions.common.config.MekanismAdditionsConfig;
import mekanism.api.MekanismAPI;

public class VoiceClient extends Thread {

    private final AudioFormat format = new AudioFormat(16_000F, 16, 1, true, true);
    private VoiceOutput outputThread;
    private VoiceInput inputThread;
    private DataOutputStream output;
    private DataInputStream input;
    private volatile boolean running;
    private volatile boolean disconnected;
    private volatile Socket socket;
    private final String ip;

    public VoiceClient(String ip) {
        super("VoiceServer Client Thread " + ip);
        this.ip = ip;
        setDaemon(true);
    }

    @Override
    public void run() {
        MekanismAPI.logger.info("VoiceServer: Starting client connection...");

        try {
            socket = new Socket();
            if (disconnected) {
                socket.close();
                return;
            }
            socket.connect(new java.net.InetSocketAddress(ip, MekanismAdditionsConfig.additions.voicePort.get()));

            input = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            output = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));

            synchronized (this) {
                if (disconnected) {
                    socket.close();
                    return;
                }
                running = true;
                (outputThread = new VoiceOutput(this)).start();
                (inputThread = new VoiceInput(this)).start();
            }

            MekanismAPI.logger.info("VoiceServer: Successfully connected to server.");
        } catch (ConnectException e) {
            if (!disconnected) MekanismAPI.logger.error("VoiceServer: Server's VoiceServer is disabled.");
            disconnect();
        } catch (Exception e) {
            if (!disconnected) MekanismAPI.logger.error("VoiceServer: Error while starting client connection.", e);
            disconnect();
        }
    }

    public synchronized void disconnect() {
        disconnected = true;
        running = false;
        interrupt();
        if (inputThread != null) {
            inputThread.interrupt();
            inputThread.close();
        }
        if (outputThread != null) {
            outputThread.interrupt();
            outputThread.close();
        }
        //Closing the socket first also cancels an in-flight connect/read/write owned by this connection.
        for (java.io.Closeable resource : new java.io.Closeable[]{socket, input, output}) {
            if (resource != null) {
                try {
                    resource.close();
                } catch (java.io.IOException failure) {
                    MekanismAPI.logger.debug("VoiceServer: Connection resource close failed.", failure);
                }
            }
        }
        MekanismAPI.logger.info("VoiceServer: Stopped client connection.");
    }

    public boolean isRunning() {
        return running;
    }

    public DataOutputStream getOutputStream() {
        return output;
    }

    public DataInputStream getInputStream() {
        return input;
    }

    public AudioFormat getAudioFormat() {
        return format;
    }
}