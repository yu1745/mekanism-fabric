package mekanism.common.lib.multiblock;

import mekanism.common.content.boiler.BoilerMultiblockData;
import mekanism.common.content.boiler.BoilerValidator;
import mekanism.common.content.evaporation.EvaporationMultiblockData;
import mekanism.common.content.evaporation.EvaporationValidator;
import mekanism.common.content.matrix.MatrixMultiblockData;
import mekanism.common.content.matrix.MatrixValidator;
import mekanism.common.content.sps.SPSMultiblockData;
import mekanism.common.content.sps.SPSValidator;
import mekanism.common.content.sps.SPSCache;
import mekanism.common.content.tank.TankCache;
import mekanism.common.content.tank.TankMultiblockData;
import mekanism.common.content.tank.TankValidator;

/**
 * Fabric port delta: owner of the five official multiblock manager instances that official Mekanism held as static fields
 * ({@code boilerManager}, {@code tankManager}, {@code matrixManager}, {@code spsManager}, {@code evaporationManager}) on the
 * excluded {@code mekanism.common.Mekanism} entrypoint. Each instance keeps its exact original name key, cache supplier, and
 * validator supplier; registration into {@link MultiblockManager#reset()}'s manager set happens through the
 * {@link MultiblockManager} constructor as before, and the instances are cleared server-wide by
 * {@code MekanismServerState#clearServerState()} in the server-stopped domain cleanup, matching the original entrypoint
 * lifetime.
 */
public final class MekanismMultiblockManagers {

    private MekanismMultiblockManagers() {
    }

    public static final MultiblockManager<TankMultiblockData> tankManager = new MultiblockManager<>("dynamicTank", TankCache::new, TankValidator::new);
    public static final MultiblockManager<MatrixMultiblockData> matrixManager = new MultiblockManager<>("inductionMatrix", MultiblockCache::new, MatrixValidator::new);
    public static final MultiblockManager<BoilerMultiblockData> boilerManager = new MultiblockManager<>("thermoelectricBoiler", MultiblockCache::new, BoilerValidator::new);
    public static final MultiblockManager<EvaporationMultiblockData> evaporationManager = new MultiblockManager<>("evaporation", MultiblockCache::new, EvaporationValidator::new);
    public static final MultiblockManager<SPSMultiblockData> spsManager = new MultiblockManager<>("sps", SPSCache::new, SPSValidator::new);
}
