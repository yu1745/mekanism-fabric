package mekanism.fabric.mixin.recipe;

import mekanism.fabric.recipe.FabricTagEmptyCondition;
import net.minecraft.server.ReloadableServerResources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Release the snapshot at Fabric's existing tag-condition cleanup boundary. */
@Mixin(ReloadableServerResources.class)
abstract class RecipeTagScopeCleanupMixin {
    @Inject(method = "updateRegistryTags()V", at = @At("HEAD"))
    private void mekanism$clearTagContents(CallbackInfo ci) {
        FabricTagEmptyCondition.clearTags();
    }
}
