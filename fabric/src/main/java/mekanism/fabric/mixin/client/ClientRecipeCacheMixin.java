package mekanism.fabric.mixin.client;

import mekanism.client.ClientTickHandler;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fabric port of the official {@code RecipesUpdatedEvent} handler: the client recipe caches clear when the sync
 * packet lands (no fabric event exists for it on 1.21.1).
 */
@Mixin(ClientPacketListener.class)
abstract class ClientRecipeCacheMixin {

    @Inject(method = "handleUpdateRecipes", at = @At("TAIL"))
    private void mekanism$recipesUpdated(CallbackInfo ci) {
        ClientTickHandler.onRecipesUpdated();
    }
}
