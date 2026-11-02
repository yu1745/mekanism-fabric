package mekanism.common.content.transporter;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import mekanism.common.lib.inventory.TransitRequest;
import mekanism.common.lib.inventory.TransitRequest.ItemData;
import mekanism.common.lib.inventory.TransitRequest.TransitResponse;
import mekanism.common.util.StackUtils;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Fabric port: the old {@code InventoryInfo} per-slot size guessing is replaced by
 * cumulative real inserts inside one abort-only Fabric transaction. All already-flowing reservations (and any
 * additional flowing stacks, e.g. in-flight predictions from the same tick) are inserted first so later candidates
 * see the reserved state; then the request's own candidate is inserted and the actually accepted amount is measured.
 * The prediction transaction is never committed, so no target mutation survives; callers pass their
 * {@link TransactionContext} explicitly so nested predictions compose with any active caller transaction (R1
 * correction: the real API has no {@code openCurrent}; {@link Transaction#openNested(TransactionContext)} opens an
 * outer transaction when the context is null).
 */
public class TransporterManager {

    private TransporterManager() {
    }

    //todo determine if a custom pos record is better storing the long
    private static final Map<GlobalPos, Set<TransporterStack>> flowingStacks = new Object2ObjectOpenHashMap<>();

    public static void reset() {
        flowingStacks.clear();
    }

    public static void add(Level world, TransporterStack stack) {
        flowingStacks.computeIfAbsent(GlobalPos.of(world.dimension(), BlockPos.of(stack.getDest())), k -> new HashSet<>()).add(stack);
    }

    public static boolean contains(Level world, TransporterStack stack) {
        if (!stack.hasPath() || !stack.getPathType().hasTarget()) {
            return false;
        }
        Set<TransporterStack> stacks = flowingStacks.get(GlobalPos.of(world.dimension(), BlockPos.of(stack.getDest())));
        return stacks != null && stacks.contains(stack);
    }

    public static void remove(Level world, TransporterStack stack) {
        if (stack.hasPath() && stack.getPathType().hasTarget()) {
            GlobalPos pos = GlobalPos.of(world.dimension(), BlockPos.of(stack.getDest()));
            Set<TransporterStack> transporterStacks = flowingStacks.get(pos);
            if (transporterStacks != null && transporterStacks.remove(stack) && transporterStacks.isEmpty()) {
                flowingStacks.remove(pos);
            }
        }
    }

    public static boolean didEmit(ItemStack stack, ItemStack returned) {
        return returned.isEmpty() || returned.getCount() < stack.getCount();
    }

    public static ItemStack getToUse(ItemStack stack, ItemStack returned) {
        return returned.isEmpty() ? stack : StackUtils.size(stack, stack.getCount() - returned.getCount());
    }

    /**
     * Gets the {@link TransitResponse} of what items we expect to be able to insert into the real sided storage at a
     * given position, taking into account any already "in-flight" items being transferred to that destination.
     *
     * @param position                Position of the target
     * @param side                    Side of the target we are connecting to
     * @param handler                 The real sided storage the target exposes
     * @param request                 Transit request
     * @param additionalFlowingStacks Any additional stacks to treat as flowing and in transit for simulation uses.
     *
     * @return {@link TransitResponse} of expected items to use
     */
    public static TransitResponse getPredictedInsert(GlobalPos position, Direction side, Storage<ItemVariant> handler, TransitRequest request,
          Map<GlobalPos, Set<TransporterStack>> additionalFlowingStacks) {
        return getPredictedInsert(position, side, handler, request, additionalFlowingStacks, null);
    }

    /**
     * Transaction-aware prediction: the abort-only simulation is nested in the caller's transaction when one is
     * active, so provisional inserts compose with (and never survive past) the caller's abort semantics.
     */
    public static TransitResponse getPredictedInsert(GlobalPos position, Direction side, Storage<ItemVariant> handler, TransitRequest request,
          Map<GlobalPos, Set<TransporterStack>> additionalFlowingStacks, @Nullable TransactionContext enclosing) {
        try (Transaction prediction = Transaction.openNested(enclosing)) {
            //Before we see if our candidate can fit in the destination, we must first insert the stacks that are
            // en-route. If any in-flight stack is rejected per the destination-face rules, then we can fail fast.
            if (!predictFlowing(position, side, handler, prediction, flowingStacks)
                || !predictFlowing(position, side, handler, prediction, additionalFlowingStacks)) {
                return request.getEmptyResponse();
            }
            //Now for each of the items in the request, insert cumulatively, using the state from all the in-flight
            // items to ensure we have an accurate model of what will happen in the future. We try each stack in the
            // request; it might be possible to not send the first item, but the second could work, etc.
            for (ItemData data : request) {
                ItemStackHolder stack = ItemStackHolder.of(data.getStack());
                if (stack.variant.isBlank() || stack.count <= 0) {
                    continue;
                }
                long numLeftOver = stack.count - handler.insert(stack.variant, stack.count, prediction);
                if (numLeftOver == stack.count) {
                    //Nothing was accepted; there's no room at all; move on to the next stack
                    continue;
                }
                //Otherwise, construct the appropriately sized stack to send and return that
                return request.createResponse(StackUtils.size(data.getStack(), stack.count - (int) Math.min(Integer.MAX_VALUE, numLeftOver)), data);
            }
            return request.getEmptyResponse();
        }//Never committed: every provisional insert (flowing reservations and candidates) is rolled back
    }

    private static boolean predictFlowing(GlobalPos position, Direction side, Storage<ItemVariant> handler, Transaction prediction,
          Map<GlobalPos, Set<TransporterStack>> flowingStacks) {
        Set<TransporterStack> transporterStacks = flowingStacks.get(position);
        if (transporterStacks != null) {
            for (TransporterStack stack : transporterStacks) {
                if (stack != null && stack.getPathType().hasTarget()) {
                    //We start by inserting the stack into the handler, regardless of if we are interacting with the
                    // same side of the target as the stack's path is taking. This is so that in cases where the
                    // storage is shared (chests) or some of the slots may be shared (our machines with multiple sides
                    // set to the same side config) then we want to make sure we insert the in-flight stacks anyway
                    // so that if the slot is the same we fill it.
                    ItemStackHolder item = ItemStackHolder.of(stack.itemStack);
                    long numLeftOver = item.count - handler.insert(item.variant, item.count, prediction);
                    if (numLeftOver > 0) {
                        if (numLeftOver == item.count) {
                            //If none of the stack could be inserted, check if we are attempting to insert it
                            // into the same side of the target as the stack's path is taking.
                            if (side != stack.getSideOfDest()) {
                                //If we are not, then assume that the destination does not contain the slot that the
                                // in-flight stack is en-route to at all, so don't exit early just because of failing
                                // to insert it into the destination, and instead continue checking and then
                                // insert/check our TransitRequest
                                continue;
                            }
                        }
                        // Failed to successfully insert this in-flight item; there's no room for anyone else
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * Gets the {@link TransitResponse} of what items we expect to be able to insert into the real sided storage not
     * taking into account any stacks that are in transit to it.
     */
    public static TransitResponse getPredictedInsert(Storage<ItemVariant> handler, TransitRequest request) {
        //Abort-only local prediction; this overload has no caller transaction context (R1: no ambient-current fallback)
        try (Transaction prediction = Transaction.openOuter()) {
            for (ItemData data : request) {
                ItemStackHolder stack = ItemStackHolder.of(data.getStack());
                if (stack.variant.isBlank() || stack.count <= 0) {
                    continue;
                }
                long numLeftOver = stack.count - handler.insert(stack.variant, stack.count, prediction);
                if (numLeftOver == stack.count) {
                    continue;
                }
                return request.createResponse(StackUtils.size(data.getStack(), stack.count - (int) Math.min(Integer.MAX_VALUE, numLeftOver)), data);
            }
            return request.getEmptyResponse();
        }
    }

    /**
     * Minimal immutable view of an item stack for insertion bookkeeping. Replaces the removed
     * {@code didEmit}/{@code getToUse} ItemStack helpers (their callers now measure accepted counts directly).
     */
    record ItemStackHolder(ItemVariant variant, int count) {

        static ItemStackHolder of(net.minecraft.world.item.ItemStack stack) {
            return new ItemStackHolder(ItemVariant.of(stack), stack.getCount());
        }
    }
}
