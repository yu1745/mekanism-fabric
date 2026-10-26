package mekanism.common.integration.energy;

import mekanism.api.annotations.NothingNullByDefault;
import mekanism.api.energy.IStrictEnergyHandler;
import mekanism.common.util.WorldUtils;
import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup;
import net.fabricmc.fabric.api.lookup.v1.entity.EntityApiLookup;
import net.fabricmc.fabric.api.lookup.v1.item.ItemApiLookup;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Fabric port delta: the official interface exposed the NeoForge block/item/entity {@code MultiTypeCapability} triple plus the NeoForge
 * {@code ICapabilityProvider} registration bridge. On Fabric the three legs are the real lookup identities ({@link BlockApiLookup},
 * {@link ItemApiLookup}, {@link EntityApiLookup}), and provider registration is owned by the platform exposure seam
 * ({@code FabricEnergyLookup.expose}), so the provider-wrapping members ({@code getProviderAs}, {@code wrapStrictEnergyHandler}) have no
 * equivalent and are removed rather than stubbed.
 */
@NothingNullByDefault
public interface IEnergyCompat {

    /**
     * Whether this energy compat is actually enabled.
     *
     * @return if this energy compat is enabled.
     */
    boolean isUsable();

    /**
     * {@return true if the mods required for the capability this compat acts on are actually loaded}
     */
    default boolean capabilityExists() {
        return true;
    }

    /**
     * Gets the block lookup this compat integrates with.
     *
     * @return The block lookup this compat is integrating with.
     */
    BlockApiLookup<?, @Nullable Direction> getCapability();

    /**
     * Gets the item lookup this compat integrates with.
     */
    ItemApiLookup<?, ContainerItemContext> getItemCapability();

    /**
     * Gets the entity lookup this compat integrates with.
     */
    EntityApiLookup<?, Void> getEntityCapability();

    /**
     * Wraps this compat's capability in a strict energy handler.
     *
     * @param handler Handler to wrap. Can be assumed to be the same type as this energy compat.
     */
    @Nullable
    IStrictEnergyHandler wrapAsStrictEnergyHandler(Object handler);

    /**
     * Wraps the capability implemented in the provider into a lazy {@link IStrictEnergyHandler}, or returns {@code null} if the capability is
     * not implemented.
     *
     * @param level   Level to query.
     * @param pos     Position in level.
     * @param state   The block state, if known, or null if unknown.
     * @param tile    The block entity, if known, or null if unknown.
     * @param context Side
     *
     * @return The capability implemented in the provider into an {@link IStrictEnergyHandler}, or {@code null} if the capability is not implemented.
     */
    @Nullable
    default IStrictEnergyHandler getAsStrictEnergyHandler(Level level, BlockPos pos, @Nullable BlockState state, @Nullable BlockEntity tile, @Nullable Direction context) {
        Object capability = WorldUtils.getCapability(level, getCapability(), pos, state, tile, context);
        return capability == null ? null : wrapAsStrictEnergyHandler(capability);
    }

    /**
     * Gets an exposed capability of this compat's type from a stack and wraps it into a strict energy handler.
     *
     * @param stack ItemStack to check for the capability
     */
    @Nullable
    default IStrictEnergyHandler getStrictEnergyHandler(ItemStack stack) {
        Object capability = getCapability(stack);
        return capability == null ? null : wrapAsStrictEnergyHandler(capability);
    }

    /**
     * Gets an exposed capability of this compat's type from an entity and wraps it into a strict energy handler.
     *
     * @param entity Entity to check for the capability
     */
    @Nullable
    default IStrictEnergyHandler getStrictEnergyHandler(Entity entity) {
        Object capability = getCapability(entity);
        return capability == null ? null : wrapAsStrictEnergyHandler(capability);
    }

    @Nullable
    private Object getCapability(ItemStack stack) {
        //Note: Safety handling of empty stack is done when looking up the provider inside find's implementation
        return getItemCapability().find(stack, ContainerItemContext.withConstant(stack));
    }

    @Nullable
    private Object getCapability(@Nullable Entity entity) {
        return entity == null ? null : getEntityCapability().find(entity, null);
    }
}
