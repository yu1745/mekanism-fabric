package mekanism.common.util;

import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import org.jetbrains.annotations.Nullable;

/**
 * Helpers for correct use of the real Fabric Transfer {@link TransactionContext} API (Task052 correction, REVIEW11 R1).
 *
 * <p>The pinned Fabric API (transfer 5.4.4+7b3d111d19) has no {@code openCurrent}/{@code onAbort}/{@code onRootAbort}/{@code onRootCommit}. It has
 * {@link Transaction#openNested(TransactionContext)}, {@link TransactionContext#addCloseCallback} and
 * {@link TransactionContext#addOuterCloseCallback}. Crucially, <b>outer close callbacks are held by the per-thread transaction manager, not by the
 * transaction they were registered on</b>: a callback registered inside an aborted nested transaction still runs after the outermost transaction closes,
 * with the outermost result. Undo actions therefore need a once-guard so a nested abort followed by a root abort cannot run the undo twice, and
 * commit-only actions must be skipped when the registering nested transaction was aborted first.
 *
 * <p>These helpers implement exactly that semantics on top of the real callbacks:
 * <ul>
 *     <li>{@link #onAbort}: runs {@code undo} exactly once if the given transaction aborts <b>or</b> any enclosing transaction later aborts.</li>
 *     <li>{@link #onAbortAndCommit}: additionally runs {@code onRootCommit} only when the outermost transaction commits <b>and</b> this operation was not
 *     already rolled back by an earlier nested abort (surviving-commit-only notification).</li>
 *     <li>{@link #simulation}: opens a nested transaction of the current one (or an outer transaction if none is active) for abort-only simulation.
 *     Uses the real, deprecated {@link Transaction#getCurrentUnsafe()} because the calling seam cannot carry an explicit context; the transaction is
 *     always aborted by the caller, so nothing it staged can survive.</li>
 * </ul>
 */
public final class TxOps {

    private TxOps() {
    }

    /**
     * Opens a transaction for abort-only simulation that composes with any transaction active on this thread.
     */
    @SuppressWarnings("deprecation")
    public static Transaction simulation() {
        return Transaction.openNested(Transaction.getCurrentUnsafe());
    }

    /**
     * Registers an undo that runs exactly once when the given transaction aborts, or when any enclosing transaction aborts after the given one committed.
     */
    public static void onAbort(TransactionContext tx, Runnable undo) {
        onAbortAndCommit(tx, undo, null);
    }

    /**
     * Registers rollback/commit handling with correct nested semantics: {@code undo} runs exactly once on own abort or later root abort;
     * {@code onRootCommit} runs only after the outermost transaction commits, and only if this operation was not rolled back by a nested abort first.
     */
    public static void onAbortAndCommit(TransactionContext tx, Runnable undo, @Nullable Runnable onRootCommit) {
        AtomicBoolean rolledBack = new AtomicBoolean();
        tx.addCloseCallback((transaction, result) -> {
            if (result.wasAborted() && rolledBack.compareAndSet(false, true)) {
                undo.run();
            }
        });
        tx.addOuterCloseCallback(result -> {
            if (result.wasAborted()) {
                if (rolledBack.compareAndSet(false, true)) {
                    undo.run();
                }
            } else if (onRootCommit != null && !rolledBack.get()) {
                onRootCommit.run();
            }
        });
    }
}
