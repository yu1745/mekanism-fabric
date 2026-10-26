package mekanism.common.content.qio;

import mekanism.api.IContentsListener;
import mekanism.common.base.ILevelProvider;
import org.jetbrains.annotations.Nullable;

public interface IQIOCraftingWindowHolder extends IContentsListener, ILevelProvider {

    byte MAX_CRAFTING_WINDOWS = 3;

    QIOCraftingWindow[] getCraftingWindows();

    /**
     * @apiNote Only should be used on the server, so it is perfectly safe to always just be returning null when on the client.
     */
    @Nullable
    QIOFrequency getFrequency();
}