package mekanism.common.capabilities;

import mekanism.common.util.WorldUtils;
import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fabric port of the official multi-type capability catalog surface. The stable accessor subset is preserved verbatim
 * ({@code getCapability(ItemStack)}, {@code hasCapability(ItemStack)}, both {@code getCapabilityIfLoaded} overloads and the
 * {@link BlockApiLookup} block leg); the three NeoForge token accessors collapse into the single Fabric block identity plus
 * one item identity whose context type is owned by the concrete catalog (see {@link MultiTypeCapability} and
 * {@link ExternalStorageCapability}).
 *
 * <p>This interface deliberately exposes no entity leg and no cache factory: the official {@code getCapability(Entity)} and
 * {@code createCache(...)} defaults had no callers outside the interface itself, and Fabric block caches are created
 * through {@code BlockApiCache} by their real owners rather than through a capability token.</p>
 */
public interface IMultiTypeCapability<HANDLER> {

    BlockApiLookup<HANDLER, Direction> block();

    /**
     * Queries the item-level identity for this domain API and returns {@code null} when the stack does not expose it.
     */
    @Nullable
    HANDLER getCapability(ItemStack stack);

    /**
     * @apiNote Only use this helper if you don't actually need the capability, otherwise prefer {@link #getCapability(ItemStack)} and null checking.
     */
    default boolean hasCapability(ItemStack stack) {
        return getCapability(stack) != null;
    }

    @Nullable
    default HANDLER getCapabilityIfLoaded(@Nullable Level level, @NotNull BlockPos pos, @Nullable Direction side) {
        return getCapabilityIfLoaded(level, pos, null, null, side);
    }

    @Nullable
    default HANDLER getCapabilityIfLoaded(@Nullable Level level, @NotNull BlockPos pos, @Nullable BlockState state, @Nullable BlockEntity blockEntity,
          @Nullable Direction side) {
        return WorldUtils.getCapability(level, block(), pos, state, blockEntity, side);
    }
}
