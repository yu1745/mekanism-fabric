package mekanism.fabric.mixin.server;

import mekanism.common.util.EffectiveSide;
import mekanism.fabric.worldgen.FabricBiomeModifiers;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Places the real server thread inside the Mekanism-owned marker group at the vanilla creation entry, so {@link EffectiveSide} classifies it and every
 * thread inheriting its group as the logical server (see {@code docs/archive/TASK052_ADJUDICATION_EFFECTIVE_SIDE.md}). All other statements of {@code spin}
 * (uncaught handler, optional priority, factory-apply order, start) keep vanilla behavior.
 */
@Mixin(MinecraftServer.class)
abstract class MinecraftServerMixin {

    @Shadow public abstract ResourceManager getResourceManager();

    @Inject(method = "<init>", at = @At(value = "FIELD", target = "Lnet/minecraft/server/MinecraftServer;resources:Lnet/minecraft/server/MinecraftServer$ReloadableResources;", opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER))
    private void mekanism$captureWorldResources(CallbackInfo ci) {
        FabricBiomeModifiers.captureWorldResources(getResourceManager());
    }

    @Redirect(method = "spin",
          at = @At(value = "NEW", target = "(Ljava/lang/Runnable;Ljava/lang/String;)Ljava/lang/Thread;"))
    private static Thread mekanism$createServerThreadInMarkerGroup(Runnable runnable, String name) {
        return new Thread(EffectiveSide.serverThreadGroup(), runnable, name);
    }
}
