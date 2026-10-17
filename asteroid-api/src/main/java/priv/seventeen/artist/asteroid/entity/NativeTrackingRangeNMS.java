package priv.seventeen.artist.asteroid.entity;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import java.util.Objects;
import java.util.UUID;

/** Optional Paper owner-thread capability. Changes the real entity tracker, not a virtual host. */
public interface NativeTrackingRangeNMS {
    enum State { WAITING, ACTIVE, LOST, CLOSING, CLOSED }

    /** Nonnegative raw block distance, before native passenger and server-percentage processing.
     * Zero is an actual native value, not absence and not a guarantee that no viewer can track it.
     * No clamp, multiplication, packet, player enumeration or forced pairing is performed here.
     * At most one lease owns a UUID. Consumers must close their leases; NMS shutdown also closes them.
     */
    RangeLease controlTrackingRange(Entity host, int blocks);

    /** O(1) membership of the exact current native connection in the actual tracker's seenBy set. */
    boolean isTrackedBy(Entity host, Player viewer);

    interface RangeLease extends AutoCloseable {
        UUID host();
        int requestedBlocks();
        long epoch();
        State state();
        /** Checks current identity/value and advances a bounded tracker-replacement recovery. */
        State refresh();
        /** No writes: exact tracker/world/entity and owned raw range must still match. */
        boolean current();
        /** Nonnegative owned transaction; WAITING updates only the intent, without renewing its timeout. */
        RangeChange change(int blocks);
        /** Restore only an unchanged value in the exact tracker epoch last written by this lease. */
        @Override void close();
    }

    interface RangeChange {
        /** Same owned revision only; a WAITING change may undo its first exact ensuing epoch, never a later one. */
        boolean rollback();
    }

    /** A failed open can retain only an exact close receipt, never admission or a mutable range handle. */
    interface CleanupReceipt extends AutoCloseable {
        UUID host();
        @Override void close();
    }

    /** Suppressed on the outgoing RuntimeException/Error while failed open still owns cleanup.
     * Normally the original error is rethrown. If it disables suppression, a same-category wrapper
     * retains that original as its cause so this close receipt cannot be silently discarded.
     */
    final class PendingCleanup extends RuntimeException {
        private final CleanupReceipt receipt;
        public PendingCleanup(CleanupReceipt receipt) {
            super("Native tracking range open retained cleanup", null, false, false);
            this.receipt = Objects.requireNonNull(receipt);
        }
        public CleanupReceipt receipt() { return receipt; }
    }

    /** Lifecycle seam used only by the NMS loader, not by an arbitrary range consumer. */
    default void shutdownTrackingRanges() { }
}
