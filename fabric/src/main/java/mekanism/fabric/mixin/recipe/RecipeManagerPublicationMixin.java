package mekanism.fabric.mixin.recipe;

import mekanism.fabric.recipe.FabricRecipeCacheLifecycle;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Publish after vanilla installs resources/tags and before recipe synchronization can reenter consumers. */
@Mixin(PlayerList.class)
abstract class RecipeManagerPublicationMixin {
    @Shadow @Final private MinecraftServer server;

    @Inject(method = "reloadResources", at = @At("HEAD"))
    private void mekanism$publishRecipeManager(CallbackInfo ci) {
        FabricRecipeCacheLifecycle.resourcesApplied(server);
    }
}
