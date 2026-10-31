package mekanism.client.gui.element.bar;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Fabric port delta: the official nested {@code IBarInfoHandler} was extracted to a top-level interface so viewer-neutral
 * code (the synthetic recipe helpers) can type bar progress handlers without pulling in the GUI framework while the GUI vertical
 * is unported. {@code GuiBar} keeps implementing/accepting this same shape when it is admitted.
 */
public interface IBarInfoHandler {

    @Nullable
    default Component getTooltip() {
        return null;
    }

    double getLevel();
}
