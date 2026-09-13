package mekanism.fabric.mixin.recipe;

import mekanism.fabric.recipe.FabricRecipeCacheClientLifecycle;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Client-only tail hook after {@code recipeManager.replaceRecipes}, recipe-book rebuilding, and search-tree refresh in
 * {@code ClientPacketListener.handleUpdateRecipes}. Fabric has no sufficient high-level callback for every recipe packet, so this mixin publishes the matching client
 * recipe cache context. The mixin config declaring it is client-only and a dedicated server never resolves this class.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {

    @Shadow
    @Final
    private RecipeManager recipeManager;

    @Inject(method = "handleUpdateRecipes", at = @At("TAIL"), require = 1)
    private void mekanism$publishRecipeCacheContext(ClientboundUpdateRecipesPacket packet, CallbackInfo ci) {
        //Note: The packet listener's own registry access is passed along, so the published context never borrows access from a foreign connection
        FabricRecipeCacheClientLifecycle.onRecipesUpdated(this.recipeManager, ((ClientPacketListener) (Object) this).registryAccess());
    }
}
