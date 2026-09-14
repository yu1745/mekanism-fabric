package mekanism.fabric.item;

import java.util.Objects;
import java.util.function.Function;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Transaction boundary for Mekanism's component-backed item containers. Domain handlers only mutate a detached stack;
 * the real owning inventory is changed exclusively by the supplied Fabric ContainerItemContext, never by a captured ItemStack alias.
 * Each query rechecks the current variant, count and the original domain's live config/stack-count provider guard.
 */
public final class ComponentItemContext<HANDLER> {
    private final ContainerItemContext context;
    private final Item item;
    private final Function<ItemStack, HANDLER> provider;

    public ComponentItemContext(ContainerItemContext context, Item item, Function<ItemStack, HANDLER> provider) {
        this.context = Objects.requireNonNull(context);
        this.item = Objects.requireNonNull(item);
        this.provider = Objects.requireNonNull(provider);
    }

    @Nullable
    public State<HANDLER> read() {
        ItemVariant variant = context.getItemVariant();
        if (variant.isBlank() || !variant.isOf(item)) return null;
        long count = context.getAmount();
        if (count <= 0 || count > Integer.MAX_VALUE) return null;
        ItemStack copy = variant.toStack((int) count);
        HANDLER handler = provider.apply(copy);
        return handler == null ? null : new State<>(variant, count, copy, handler);
    }

    /** Caller owns the nested transaction and commits only after this exact all-or-nothing container exchange succeeds. */
    public boolean exchange(State<HANDLER> state, TransactionContext transaction) {
        // Prevent stale decisions from overwriting a concurrently replaced item or another nested operation's components.
        if (!state.before().equals(context.getItemVariant()) || state.count() != context.getAmount()) return false;
        ItemVariant result = ItemVariant.of(state.stack());
        if (result.isBlank() || !result.isOf(item) || state.stack().getCount() != state.count()) {
            throw new IllegalStateException("Component-backed handler changed the identity or count of its container item");
        }
        return context.exchange(result, state.count(), transaction) == state.count();
    }

    public record State<HANDLER>(ItemVariant before, long count, ItemStack stack, HANDLER handler) {
    }
}
