package mekanism.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Client-player access point for common code.
 *
 * <p>Fabric port seam (Task052): the original {@code MekanismUtils.tryGetClientPlayer} checks the NeoForge distribution flag and then calls
 * {@code MekanismClient.tryGetClientPlayer()} (which just returns {@code Minecraft.getInstance().player}). {@code MekanismClient} is the full client-mod
 * entrypoint and is not admitted yet, so this small access point carries exactly that responsibility: an environment-guarded window into the physical client
 * player. {@link Minecraft} is only linked when this method actually executes on the physical client, so common callers never force client initialization on a
 * dedicated server.</p>
 */
public final class ClientPlayerAccess {

    private ClientPlayerAccess() {
    }

    @Nullable
    public static Player tryGetClientPlayer() {
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            return Minecraft.getInstance().player;
        }
        //Note: Ideally we would have some way to get which player is in question on the server
        // as this is mostly used in tooltips, but odds are it won't end up being called
        return null;
    }
}
