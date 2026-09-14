package mekanism.common.integration.computer;

import java.util.function.BooleanSupplier;
import mekanism.common.integration.computer.computercraft.CCCapabilityHelper;
import mekanism.common.registration.impl.TileEntityTypeDeferredRegister.BlockEntityTypeBuilder;
import mekanism.common.tile.base.CapabilityTileEntity;
import net.fabricmc.loader.api.FabricLoader;

/** Actual optional Fabric computer integration owner. OC2 has no Fabric 1.21.1 binding and is not advertised. */
public class ComputerCapabilityHelper {

    private static final boolean COMPUTERCRAFT_PRESENT = FabricLoader.getInstance().isModLoaded("computercraft");
    private static volatile boolean initialized;

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        if (COMPUTERCRAFT_PRESENT) {
            FactoryRegistry.load();
            //The first optional implementation linkage is guarded by actual loader presence.
            CCCapabilityHelper.registerApis();
        }
        initialized = true;
    }

    public static boolean isEnabled() {
        return COMPUTERCRAFT_PRESENT && initialized;
    }

    public static <TILE extends CapabilityTileEntity & IComputerTile> void addComputerCapabilities(BlockEntityTypeBuilder<TILE> builder, BooleanSupplier supportsComputer) {
        if (COMPUTERCRAFT_PRESENT) {
            CCCapabilityHelper.addCapability(builder, () -> isEnabled() && supportsComputer.getAsBoolean());
        }
    }

    public static void addBoundingComputerCapabilities() {
        if (isEnabled()) {
            CCCapabilityHelper.addBoundingComputerCapabilities();
        }
    }
}
