package mekanism.fabric.client.model;

import net.minecraft.client.renderer.block.model.BlockModel;
import org.jetbrains.annotations.Nullable;

/** Internal view of the resolved vanilla parent chain for composite geometry inheritance. */
public interface CompositeModelParentAccess {
    @Nullable BlockModel mekanism$parentModel();
    boolean mekanism$hasOwnElements();
}
