package mekanism.fabric.fluid;

import java.util.List;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.fluid.IMekanismFluidHandler;
import mekanism.common.capabilities.holder.IHolder;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * Sole construction seam for physical-side Fabric fluid-storage views of Mekanism fluid handlers.
 *
 * <p>This class deliberately performs no {@code FluidStorage.SIDED} provider registration and exposes no reverse
 * Fabric-to-Mekanism adapter. A future owner must cache exactly one returned view per physical side and call
 * {@link FabricFluidStorage#invalidate()} on removal, reload, facing or side-configuration changes, and any tank
 * topology change.
 */
public final class FabricFluidLookup {

    private FabricFluidLookup() {
    }

    /**
     * Creates a stable, physical-side-bound view, or returns {@code null} when there is no external fluid handling to
     * expose. Null is always an internal Mekanism side and is therefore never exposed through this boundary.
     */
    @Nullable
    public static FabricFluidStorage expose(@Nullable IMekanismFluidHandler handler, @Nullable Direction side,
          @Nullable IHolder holder) {
        if (handler == null || side == null || !handler.canHandleFluid()) {
            return null;
        }
        List<IExtendedFluidTank> tanks = handler.getFluidTanks(side);
        if (tanks == null || tanks.isEmpty()) {
            return null;
        }
        return new FabricFluidStorage(handler, side, holder, tanks);
    }
}
