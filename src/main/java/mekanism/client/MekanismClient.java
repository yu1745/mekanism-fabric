package mekanism.client;

import java.util.Map;
import java.util.UUID;
import mekanism.client.render.RenderTickHandler;
import mekanism.client.render.tileentity.RenderSPS;
import mekanism.client.sound.SoundHandler;
import mekanism.common.base.IModModule;
import mekanism.common.base.PlayerState;
import mekanism.common.base.KeySync;
import mekanism.common.base.ModModuleState;
import mekanism.common.lib.radiation.ClientRadiation;
import mekanism.common.lib.security.ClientSecurityData;
import mekanism.common.lib.security.SecurityData;
import mekanism.common.lib.MekanismServerState;
import mekanism.common.lib.transmitter.TransmitterNetworkRegistry;
import mekanism.common.network.PacketUtils;
import mekanism.common.network.to_server.PacketKey;
import mekanism.common.recipe.MekanismRecipeType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public class MekanismClient {

    //Task052 alias (same pattern as Mekanism.activeVibrators → MekanismServerState): the excluded client entrypoint's public field
    // now aliases the exact same domain map owned by mekanism.common.lib.security.ClientSecurityData, so every original writer/reset
    // (PacketSyncSecurity.put, PacketBatchSecurityUpdate.replaceAll, MekanismClient.reset clear) reaches the real owner map.
    public static final Map<UUID, SecurityData> clientSecurityMap = ClientSecurityData.securityMap();
    public static final Map<UUID, String> clientUUIDMap = ClientSecurityData.usernameMap();

    public static void updateKey(KeyMapping key, int type) {
        updateKey(key.isDown(), type);
    }

    public static void updateKey(boolean pressed, int type) {
        if (Minecraft.getInstance().player != null) {
            UUID playerUUID = Minecraft.getInstance().player.getUUID();
            boolean down = Minecraft.getInstance().screen == null && pressed;
            if (down != KeySync.INSTANCE.has(playerUUID, type)) {
                PacketUtils.sendToServer(new PacketKey(type, down));
                KeySync.INSTANCE.update(playerUUID, type, down);
            }
        }
    }

    /**
     * Reset things that aren't needed between levels or would leak
     */
    public static void resetDimensionChange() {
        PlayerState.INSTANCE.clear(true);
        MekanismServerState.activeVibrators().clear();
        ClientRadiation.resetClient();
        RenderSPS.clearBoltRenderers();
        TransmitterNetworkRegistry.clearClientNetworks();
        RenderTickHandler.clearQueued();

        for (IModModule module : ModModuleState.modulesLoaded()) {
            module.resetClientDimensionChanged();
        }
    }

    public static void reset() {
        ClientSecurityData.clear();

        ClientTickHandler.reset();
        SoundHandler.radiationSoundMap.clear();
        MekanismRecipeType.clearCache();

        resetDimensionChange();

        for (IModModule module : ModModuleState.modulesLoaded()) {
            module.resetClient();
        }
    }

    public static void launchClient(Connection connection) {
        for (IModModule module : ModModuleState.modulesLoaded()) {
            module.launchClient(connection);
        }
    }

    @Nullable
    public static Level tryGetClientWorld() {
        return Minecraft.getInstance().level;
    }

    @Nullable
    public static Player tryGetClientPlayer() {
        return Minecraft.getInstance().player;
    }

}