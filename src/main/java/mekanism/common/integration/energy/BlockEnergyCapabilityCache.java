package mekanism.common.integration.energy;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import mekanism.api.energy.IStrictEnergyHandler;
import mekanism.api.functions.ConstantPredicates;
import mekanism.common.util.WorldUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * A cache for external energy handlers, to be used to track the energy exposure at a specific position, with a specific context.
 *
 * <p>Fabric port delta: this remains a Mekanism-owned cache, but the NeoForge transport ({@code Level#registerCapabilityListener} driven by
 * {@code Level#invalidateCapabilities}) has no Fabric equivalent — Fabric performs no push invalidation for per-position lookup changes, and the
 * pinned lookup behavior re-invokes the registered provider on every find. The cache therefore <em>requeries the compat lookups on every
 * {@link #getCapability()}</em> and derives invalidation notifications from observed acceptor-presence transitions at query time. That is strictly
 * lazier than the NeoForge push, but the real consumer ({@code RefreshListener} → {@code markDirtyAcceptor}) is already designed to defer
 * re-evaluation to its own tick, and a neighbor toggling its exposure without a block update is detected within one query instead of being missed.
 *
 * <p>A few details regarding the listener system:
 * <ul>
 * <li>Calling {@link #getCapability()} from the invalidation listener is not supported (it throws), matching the original contract that the block
 * being invalidated might not be ready to be queried again yet.</li>
 * <li>In general, do not perform any level access for the listener. The listener itself might be in a chunk that is being unloaded.</li>
 * <li>The listener only observes transitions between "some acceptor present" and "no acceptor present"; identity churn of freshly wrapped handler
 * instances does not fire it.</li>
 * </ul>
 */
public class BlockEnergyCapabilityCache {

    /**
     * Creates a new cache instance.
     *
     * @param level   the level
     * @param pos     the position
     * @param context extra context for the query
     */
    public static BlockEnergyCapabilityCache create(ServerLevel level, BlockPos pos, @Nullable Direction context) {
        return create(level, pos, context, ConstantPredicates.ALWAYS_TRUE, () -> {
        });
    }

    /**
     * Creates a new cache instance with an invalidation listener.
     *
     * @param level                the level
     * @param pos                  the position
     * @param context              extra context for the query
     * @param isValid              a function to check if the listener still wants to receive notifications. A typical example would be {@code () -> !this.isRemoved()}
     *                             for a block entity that should not receive invalidation notifications anymore once it is removed.
     * @param invalidationListener the invalidation listener. Will be called whenever the queried position's acceptor presence is observed to have changed.
     */
    public static BlockEnergyCapabilityCache create(ServerLevel level, BlockPos pos, @Nullable Direction context, BooleanSupplier isValid,
          Runnable invalidationListener) {
        Objects.requireNonNull(isValid);
        Objects.requireNonNull(invalidationListener);
        pos = pos.immutable();

        return new BlockEnergyCapabilityCache(level, pos, context, isValid, invalidationListener);
    }

    private final ServerLevel level;
    private final BlockPos pos;
    @Nullable
    private final Direction context;
    private final BooleanSupplier isValid;
    private final Runnable invalidationListener;

    /**
     * {@code null} until the first {@link #getCapability()} established the presence baseline; afterwards {@code true}/{@code false} tracks whether an
     * acceptor was found. Calling {@link #getCapability()} from the invalidation listener throws, mirroring the original contract.
     */
    @Nullable
    private Boolean cachedPresence;
    @Nullable
    private IStrictEnergyHandler cachedCap = null;

    private boolean canQuery = true;

    private BlockEnergyCapabilityCache(ServerLevel level, BlockPos pos, @Nullable Direction context, BooleanSupplier isValid, Runnable invalidationListener) {
        this.level = level;
        this.pos = pos;
        this.context = context;
        this.isValid = isValid;
        this.invalidationListener = invalidationListener;
    }

    public ServerLevel level() {
        return level;
    }

    public BlockPos pos() {
        return pos;
    }

    @Nullable
    public Direction context() {
        return context;
    }

    /**
     * Gets the capability instance, or {@code null} if the capability is not present.
     *
     * <p>If {@linkplain #pos() the target position} is not loaded, this method will return {@code null}.
     */
    @Nullable
    public IStrictEnergyHandler getCapability() {
        if (!canQuery) {
            throw new IllegalStateException("Do not call getCapability on an invalid cache or from the invalidation listener!");
        }
        if (WorldUtils.isBlockLoaded(level, pos)) {
            BlockState state = level.getBlockState(pos);
            BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
            cachedCap = EnergyCompatUtils.getStrictEnergyHandler(level, pos, state, blockEntity, context);
        } else {
            // If the position is not loaded, return no capability for now. When it loads again, the next query observes the transition.
            cachedCap = null;
        }
        boolean presence = cachedCap != null;
        if (cachedPresence != null && cachedPresence != presence) {
            // Disable queries while notifying, so the listener cannot re-enter getCapability on a possibly stale block
            canQuery = false;
            if (isValid.getAsBoolean()) {
                invalidationListener.run();
                // Re-enable queries for the next observation
                canQuery = true;
            }
            // else: not valid anymore; keep queries disabled, matching the original listener lifecycle
        }
        cachedPresence = presence;
        return cachedCap;
    }
}
