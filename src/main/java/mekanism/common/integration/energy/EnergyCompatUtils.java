package mekanism.common.integration.energy;

import java.util.List;
import java.util.function.BiFunction;
import mekanism.api.energy.IStrictEnergyHandler;
import mekanism.common.integration.energy.forgeenergy.ForgeEnergyCompat;
import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fabric port delta: the compat catalog collapses to the single Fabric external energy identity (Team Reborn, the Forge-energy analog), and the
 * NeoForge {@code RegisterCapabilitiesEvent} registration helpers ({@code registerItemCapabilities}, {@code registerEntityCapabilities},
 * {@code addBlockCapabilities}, {@code getProviderAs}-based wrapping) are superseded by the platform exposure seam ({@code FabricEnergyLookup.expose})
 * together with the deferred provider registration; they are removed rather than stubbed. {@link #initLoadedCache()} is invoked by the Fabric bootstrap
 * after mods have loaded instead of from {@code MekanismHooks}.
 */
public class EnergyCompatUtils {

    private EnergyCompatUtils() {
    }

    private static final List<IEnergyCompat> energyCompats = List.of(new ForgeEnergyCompat());

    //Default the list of loaded caps to our single external energy identity
    private static List<BlockApiLookup<?, @Nullable Direction>> LOADED_ENERGY_CAPS = List.of(new ForgeEnergyCompat().getCapability());

    /**
     * @apiNote For internal uses, only call this after mods have loaded so that we can properly assume all {@link IEnergyCompat#capabilityExists()} are present.
     */
    public static void initLoadedCache() {
        LOADED_ENERGY_CAPS = energyCompats.stream().filter(IEnergyCompat::capabilityExists).map(IEnergyCompat::getCapability).toList();
    }

    public static List<IEnergyCompat> getCompats() {
        return energyCompats;
    }

    /**
     * Checks if it is a known and enabled energy capability
     */
    public static boolean isEnergyCapability(@NotNull BlockApiLookup<?, @Nullable Direction> capability) {
        for (IEnergyCompat energyCompat : energyCompats) {
            //Note: We check the capability exists and usability states separately, given while it does duplicate
            // the exists check it allows us to skip the more complex usability checks if the capability doesn't actually match
            if (energyCompat.capabilityExists() && energyCompat.getCapability() == capability) {
                return energyCompat.isUsable();
            }
        }
        return false;
    }

    public static List<BlockApiLookup<?, @Nullable Direction>> getLoadedEnergyCapabilities() {
        return LOADED_ENERGY_CAPS;
    }

    public static boolean hasStrictEnergyHandler(@NotNull ItemStack stack) {
        return getStrictEnergyHandler(stack) != null;
    }

    @Nullable
    public static IStrictEnergyHandler getStrictEnergyHandler(@NotNull ItemStack stack) {
        if (!stack.isEmpty()) {
            return getStrictEnergyHandler(stack, IEnergyCompat::getStrictEnergyHandler);
        }
        return null;
    }

    @Nullable
    public static IStrictEnergyHandler getStrictEnergyHandler(@Nullable Entity entity) {
        if (entity != null) {
            return getStrictEnergyHandler(entity, IEnergyCompat::getStrictEnergyHandler);
        }
        return null;
    }

    @Nullable
    private static <OBJECT> IStrictEnergyHandler getStrictEnergyHandler(OBJECT object, BiFunction<IEnergyCompat, OBJECT, IStrictEnergyHandler> getter) {
        for (IEnergyCompat energyCompat : energyCompats) {
            if (energyCompat.isUsable()) {
                IStrictEnergyHandler handler = getter.apply(energyCompat, object);
                if (handler != null) {
                    return handler;
                }
            }
        }
        return null;
    }

    @Nullable
    public static IStrictEnergyHandler getStrictEnergyHandler(Level level, BlockPos pos, @Nullable BlockState state, @Nullable BlockEntity tile, Direction side) {
        //TODO: Eventually look into making it so that we cache the handler we get back. Maybe by passing a listener
        // to this that we can give to the capability as we wrap the result into
        for (IEnergyCompat energyCompat : energyCompats) {
            if (energyCompat.isUsable()) {
                IStrictEnergyHandler handler = energyCompat.getAsStrictEnergyHandler(level, pos, state, tile, side);
                if (handler != null) {
                    return handler;
                }
            }
        }
        return null;
    }
}
