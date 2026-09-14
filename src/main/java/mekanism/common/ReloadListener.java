package mekanism.common;

import mekanism.common.recipe.MekanismRecipeType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import org.jetbrains.annotations.NotNull;

public class ReloadListener implements ResourceManagerReloadListener {

    @Override
    public void onResourceManagerReload(@NotNull ResourceManager resourceManager) {
        //The real recipe binding's full clear also raises its consumer-invalidation signal.
        MekanismRecipeType.clearCache();
    }
}