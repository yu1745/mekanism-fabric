package mekanism.fabric.mixin.recipe;

import java.util.List;
import mekanism.fabric.recipe.FabricTagEmptyCondition;
import net.fabricmc.fabric.impl.resource.conditions.ResourceConditionsImpl;
import net.minecraft.tags.TagManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Capture tag contents at the same reload boundary used by Fabric resource conditions. */
@Mixin(value = ResourceConditionsImpl.class, remap = false)
abstract class FabricConditionTagCaptureMixin {
    @Inject(method = "setTags", at = @At("RETURN"), remap = false)
    private static void mekanism$captureTagContents(List<TagManager.LoadResult<?>> results, CallbackInfo ci) {
        FabricTagEmptyCondition.captureTags(results);
    }
}
