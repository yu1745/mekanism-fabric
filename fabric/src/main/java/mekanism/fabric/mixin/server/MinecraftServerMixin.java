package mekanism.fabric.mixin.server;

import mekanism.common.util.EffectiveSide;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Places the real server thread inside the Mekanism-owned marker group at the vanilla creation entry, so {@link EffectiveSide} classifies it and every
 * thread inheriting its group as the logical server (see {@code docs/TASK052_ADJUDICATION_EFFECTIVE_SIDE.md}). All other statements of {@code spin}
 * (uncaught handler, optional priority, factory-apply order, start) keep vanilla behavior.
 */
@Mixin(MinecraftServer.class)
abstract class MinecraftServerMixin {

    @Redirect(method = "spin",
          at = @At(value = "NEW", target = "(Ljava/lang/Runnable;Ljava/lang/String;)Ljava/lang/Thread;"))
    private static Thread mekanism$createServerThreadInMarkerGroup(Runnable runnable, String name) {
        return new Thread(EffectiveSide.serverThreadGroup(), runnable, name);
    }
}
