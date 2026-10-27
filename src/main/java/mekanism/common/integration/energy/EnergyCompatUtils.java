package mekanism.common.integration.energy;

import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import mekanism.api.energy.IStrictEnergyHandler;
import mekanism.common.attachments.containers.ContainerType;
import mekanism.common.capabilities.resolver.manager.EnergyHandlerManager;
import mekanism.common.entity.EntityRobit;
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
 * Fabric port delta: the external compat catalog uses the Fabric Team Reborn energy identity; Mekanism's own strict-energy lookup is resolved first. The
 * NeoForge {@code RegisterCapabilitiesEvent} registration helpers ({@code registerItemCapabilities}, {@code registerEntityCapabilities},
 * {@code addBlockCapabilities}, {@code getProviderAs}-based wrapping) are superseded by the platform exposure seam ({@code FabricEnergyLookup.expose})
 * together with the deferred provider registration; they are removed rather than stubbed. {@link #initLoadedCache()} is invoked by the Fabric bootstrap
 * after mods have loaded instead of from {@code MekanismHooks}.
 */
public class EnergyCompatUtils {

    private EnergyCompatUtils() {
    }

    private static final List<IEnergyCompat> energyCompats = List.of(new ForgeEnergyCompat());

    private static List<BlockApiLookup<?, @Nullable Direction>> LOADED_ENERGY_CAPS = List.of(EnergyHandlerManager.STRICT_ENERGY, new ForgeEnergyCompat().getCapability());

    /**
     * @apiNote For internal uses, only call this after mods have loaded so that we can properly assume all {@link IEnergyCompat#capabilityExists()} are present.
     */
    public static void initLoadedCache() {
        LOADED_ENERGY_CAPS = Stream.concat(Stream.of(EnergyHandlerManager.STRICT_ENERGY),
              energyCompats.stream().filter(IEnergyCompat::capabilityExists).map(IEnergyCompat::getCapability)).toList();
    }

    public static List<IEnergyCompat> getCompats() {
        return energyCompats;
    }

    /**
     * Checks if it is a known and enabled energy capability
     */
    public static boolean isEnergyCapability(@NotNull BlockApiLookup<?, @Nullable Direction> capability) {
        if (capability == EnergyHandlerManager.STRICT_ENERGY) {
            return true;
        }
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
        return mekanism.fabric.energy.ItemEnergyContainer.hasHandler(stack);
    }

    /** Internal access for an owned Mekanism stack. External replacement needs the context overload or ItemEnergyContainer. */
    @Nullable
    public static IStrictEnergyHandler getStrictEnergyHandler(@NotNull ItemStack stack) {
        return ContainerType.ENERGY.getRegisteredHandler(stack);
    }

    /** Resolves external native storage against its real inventory owner. */
    @Nullable
    public static IStrictEnergyHandler getStrictEnergyHandler(net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext context) {
        for (IEnergyCompat energyCompat : energyCompats) {
            if (energyCompat.isUsable()) {
                IStrictEnergyHandler handler = energyCompat.getStrictEnergyHandler(context);
                if (handler != null) {
                    return handler;
                }
            }
        }
        return null;
    }

    @Nullable
    public static IStrictEnergyHandler getStrictEnergyHandler(@Nullable Entity entity) {
        // Upstream registers the Robit itself as its strict-energy entity capability and checks that capability
        // before external energy integrations. Preserve its real handler without routing it through an optional
        // external conversion or an unregistered Fabric entity lookup.
        if (entity instanceof EntityRobit robit && robit.canHandleEnergy()) {
            return robit;
        }
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
        //Owned strict handlers preserve exact Joules and do not depend on an external conversion being enabled.
        IStrictEnergyHandler strict = EnergyHandlerManager.STRICT_ENERGY.find(level, pos, state, tile, side);
        if (strict != null) {
            return strict;
        }
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
