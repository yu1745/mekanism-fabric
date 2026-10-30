package mekanism.additions.client;

import io.netty.channel.local.LocalAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import mekanism.additions.client.voice.VoiceClient;
import mekanism.additions.common.MekanismAdditions;
import mekanism.additions.common.config.MekanismAdditionsConfig;
import mekanism.api.MekanismAPI;
import net.minecraft.network.Connection;

public class AdditionsClient implements net.fabricmc.api.ClientModInitializer {

    private static VoiceClient voiceClient;

    @Override
    public void onInitializeClient() {
        MekanismAdditionsConfig.loadClient();
        AdditionsClientRegistration.initialize();
    }

    public static void reset() {
        if (voiceClient != null) {
            voiceClient.disconnect();
            voiceClient = null;
        }
    }

    public static void launch(Connection connection) {
        if (MekanismAdditionsConfig.additions.voiceServerEnabled.get()) {
            SocketAddress address = connection.getRemoteAddress();
            //local connection
            if (address instanceof LocalAddress) {
                voiceClient = new VoiceClient("127.0.0.1");
                voiceClient.start();
                //remote connection
            } else if (address instanceof InetSocketAddress socketAddress) {
                voiceClient = new VoiceClient(socketAddress.getHostString());
                voiceClient.start();
            } else {
                MekanismAPI.logger.error("Unknown connection address detected, voice client will not launch.");
            }
        }
    }
}