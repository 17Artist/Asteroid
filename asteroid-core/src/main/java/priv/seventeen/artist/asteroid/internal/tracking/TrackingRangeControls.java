package priv.seventeen.artist.asteroid.internal.tracking;

import priv.seventeen.artist.asteroid.entity.NativeTrackingRangeNMS;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** One numeric truth: the native field. No viewer collection, scheduler, packet or spatial maths. */
public final class TrackingRangeControls<H,T> implements AutoCloseable {
    public record Identity(UUID uuid, UUID world, int entityId, Object entity) {
        public Identity { Objects.requireNonNull(uuid); Objects.requireNonNull(world); Objects.requireNonNull(entity); }
        boolean same(Identity other) {
            return other != null && uuid.equals(other.uuid) && world.equals(other.world)
                    && entityId == other.entityId && entity == other.entity;
        }
    }
    public interface Port<H,T> {
        void checkOwner();
        long tick();
        /** Null if removed, invalid, or no longer the exact Bukkit entity for this UUID. */
        Identity identity(H host);
        /** Null during a real native tracker gap. Must verify tracker.entity == identity.entity. */
        T tracker(H host, Identity identity);
        int read(T tracker);
        /** Exact read/compare/write/readback; true only when the requested postimage was read. */
        boolean compareAndSet(T tracker, int expected, int value);
        /** Raw range the current native tracker constructor would use, not effective range. */
        int canonicalRange(H host, Identity identity);
    }
    private final Port<H,T> port;
    private final int capacity, startsPerTick, waitingTicks;
    private final HashMap<UUID, Control> controls = new HashMap<>();
    private long budgetTick = Long.MIN_VALUE;
    private int starts;
    private boolean closed;

    public TrackingRangeControls(Port<H,T> port) { this(port, 4096, 32, 200); }
    public TrackingRangeControls(Port<H,T> port, int capacity, int startsPerTick, int waitingTicks) {
        this.port = Objects.requireNonNull(port);
        if (capacity < 1 || startsPerTick < 1 || waitingTicks < 1) throw new IllegalArgumentException("Invalid range budget");
        this.capacity = capacity; this.startsPerTick = startsPerTick; this.waitingTicks = waitingTicks;
    }
    public NativeTrackingRangeNMS.RangeLease open(H host, int blocks) {
        port.checkOwner();
        if (closed) throw new IllegalStateException("Native tracking range owner is closed");
        long now = clock();
        charge();
        if (blocks < 0) throw new IllegalArgumentException("Native tracking range must be nonnegative blocks");
        if (controls.size() >= capacity) throw new IllegalStateException("Native tracking range capacity exceeded");
        Identity identity = Objects.requireNonNull(port.identity(Objects.requireNonNull(host)), "Native tracking host is not loaded");
        if (controls.containsKey(identity.uuid)) throw new IllegalStateException("Native tracking range already has an owner");
        Control control = new Control(host, identity, blocks, now);
        controls.put(identity.uuid, control);
        try { control.refresh(); return control; }
        catch (RuntimeException | Error failure) {
            Throwable retained = failure;
            try { control.close(); } catch (RuntimeException | Error cleanup) { retained = retain(retained, cleanup); }
            if (control.owned()) {
                retained = retain(retained, new NativeTrackingRangeNMS.PendingCleanup(new NativeTrackingRangeNMS.CleanupReceipt() {
                    @Override public UUID host() { return control.id; }
                    @Override public void close() { control.close(); }
                }));
            }
            throw propagate(retained);
        }
    }
    /** Some third-party throwables disable suppression. Never silently lose a cleanup receipt/error. */
    private static Throwable retain(Throwable primary, Throwable secondary) {
        if (primary == secondary) return primary;
        primary.addSuppressed(secondary);
        for (Throwable recorded : primary.getSuppressed()) if (recorded == secondary) return primary;
        Throwable wrapper = primary instanceof Error
                ? new Error("Native tracking cleanup retained an original error", primary)
                : new IllegalStateException("Native tracking cleanup retained an original failure", primary);
        wrapper.addSuppressed(secondary);
        return wrapper;
    }
    private static RuntimeException propagate(Throwable failure) {
        if (failure instanceof Error error) throw error;
        return (RuntimeException) failure;
    }
    private long clock() {
        long now = port.tick();
        if (now < budgetTick) throw new IllegalStateException("Native tracking clock regressed");
        if (now != budgetTick) { budgetTick = now; starts = 0; }
        return now;
    }
    private void charge() {
        if (starts >= startsPerTick) throw new IllegalStateException("Native tracking range start budget exceeded");
        starts++;
    }
    public int size() { port.checkOwner(); return controls.size(); }
    @Override public void close() {
        port.checkOwner(); closed = true;
        RuntimeException failure = null;
        // Lifecycle only; bounded by capacity, never a regular tick/player scan.
        for (Control control : List.copyOf(controls.values())) {
            try { control.close(); } catch (RuntimeException error) {
                if (failure == null) failure = error; else failure.addSuppressed(error);
            }
        }
        if (failure != null) throw failure;
    }

    private final class Control implements NativeTrackingRangeNMS.RangeLease {
        private final H host;
        private final UUID id;
        private int requested;
        private Identity identity;
        private T tracker;
        private int original;
        private Integer rollbackAlternative;
        private long epoch, waitingSince, revision;
        private boolean hasEpoch, wrote, installing, closeRequested;
        private NativeTrackingRangeNMS.State state = NativeTrackingRangeNMS.State.WAITING;
        Control(H host, Identity identity, int requested, long tick) {
            this.host = host; this.identity = identity; id = identity.uuid;
            this.requested = requested; waitingSince = tick;
        }
        @Override public UUID host() { return id; }
        @Override public int requestedBlocks() { return requested; }
        @Override public long epoch() { port.checkOwner(); return epoch; }
        @Override public NativeTrackingRangeNMS.State state() { port.checkOwner(); return state; }
        private boolean owned() { return controls.get(id) == this; }
        @Override public NativeTrackingRangeNMS.RangeChange change(int blocks) {
            port.checkOwner();
            clock();
            charge();
            if (blocks < 0) throw new IllegalArgumentException("Native tracking range must be nonnegative blocks");
            final boolean waiting = state == NativeTrackingRangeNMS.State.WAITING;
            if (installing || closeRequested || closed || !owned()
                    || (waiting ? !identity.same(port.identity(host)) : !current()))
                throw new IllegalStateException("Native tracking range owner is not active/waiting");
            final Identity changedIdentity = identity;
            final int before = requested;
            final long changedEpoch = epoch;
            if (revision == Long.MAX_VALUE) throw new IllegalStateException("Native range revision exhausted");
            final long changedRevision = ++revision;
            // A TEMP request changes only the stored intent, not a detached/native field.
            // Keep waitingSince and hasEpoch: changing a value cannot renew the 200-tick admission.
            if (waiting) requested = blocks;
            else if (blocks != before) {
                installing = true;
                rollbackAlternative = before;
                requested = blocks;
                Throwable primary = null;
                try {
                    if (!port.compareAndSet(tracker, before, blocks)) throw new IllegalStateException("Native range write was not confirmed");
                    rollbackAlternative = null;
                } catch (RuntimeException | Error failure) {
                    primary = failure;
                    state = NativeTrackingRangeNMS.State.CLOSING;
                    try {
                        if (sameEpoch()) {
                            int actual = port.read(tracker);
                            if (actual == blocks) {
                                // Keep the postimage receipt if rollback fails; never lose it on an exception.
                                requested = blocks; state = NativeTrackingRangeNMS.State.CLOSING;
                                if (port.compareAndSet(tracker, blocks, before)) { requested = before; rollbackAlternative = null; state = NativeTrackingRangeNMS.State.ACTIVE; }
                            } else if (actual == before) { requested = before; rollbackAlternative = null; state = NativeTrackingRangeNMS.State.ACTIVE; }
                            else lost();
                        } else lost();
                    } catch (RuntimeException | Error restore) {
                        // An uncertain read cannot authorize another write. close() will recheck the receipt.
                        failure.addSuppressed(restore);
                    }
                    throw failure;
                } finally {
                    installing = false;
                    if (closeRequested) try { close(); } catch (RuntimeException | Error cleanup) {
                        if (primary != null) primary.addSuppressed(cleanup); else throw cleanup;
                    }
                }
            }
            return new NativeTrackingRangeNMS.RangeChange() {
                boolean consumed;
                @Override public boolean rollback() {
                    port.checkOwner();
                    if (consumed) return false;
                    if (installing || !owned() || revision != changedRevision || requested != blocks) { consumed = true; return false; }
                    if (waiting && state == NativeTrackingRangeNMS.State.WAITING && changedIdentity.same(port.identity(host))) {
                        requested = before; consumed = true; return before != blocks;
                    }
                    // A pending change may be rolled back only in its first exact subsequent epoch.
                    // No world/replacement/retracking epoch is borrowed from a later request.
                    boolean epochMatches = waiting
                            ? changedEpoch != Long.MAX_VALUE && epoch == changedEpoch + 1 && changedIdentity.same(identity)
                            : epoch == changedEpoch;
                    if (!epochMatches || !current()) { consumed = true; return false; }
                    if (before == blocks) { consumed = true; return false; }
                    installing = true;
                    rollbackAlternative = before;
                    Throwable primary = null;
                    try {
                        if (!port.compareAndSet(tracker, blocks, before)) {
                            // A denied write which left our value intact remains retryable.
                            if (!sameEpoch() || port.read(tracker) != blocks) { consumed = true; lost(); }
                            else { rollbackAlternative = null; throw new IllegalStateException("Native range rollback was not confirmed"); }
                            return false;
                        }
                        requested = before; rollbackAlternative = null; consumed = true; return true;
                    } catch (RuntimeException | Error failure) {
                        primary = failure;
                        state = NativeTrackingRangeNMS.State.CLOSING;
                        try {
                            if (sameEpoch()) {
                                int actual = port.read(tracker);
                                if (actual == before) { requested = before; rollbackAlternative = null; consumed = true; state = NativeTrackingRangeNMS.State.ACTIVE; }
                                else if (actual == blocks) { rollbackAlternative = null; state = NativeTrackingRangeNMS.State.ACTIVE; }
                                else { consumed = true; lost(); }
                            } else { consumed = true; lost(); }
                        } catch (RuntimeException | Error observation) { failure.addSuppressed(observation); }
                        throw failure;
                    } finally {
                        installing = false;
                        if (closeRequested) try { close(); } catch (RuntimeException | Error cleanup) {
                            if (primary != null) primary.addSuppressed(cleanup); else throw cleanup;
                        }
                    }
                }
            };
        }
        private boolean sameEpoch() {
            return owned() && identity.same(port.identity(host)) && port.tracker(host, identity) == tracker;
        }
        @Override public boolean current() {
            port.checkOwner();
            if (closed || installing || rollbackAlternative != null || !owned() || state != NativeTrackingRangeNMS.State.ACTIVE) return false;
            Identity current = port.identity(host);
            return identity.same(current) && port.tracker(host, current) == tracker && port.read(tracker) == requested;
        }
        @Override public NativeTrackingRangeNMS.State refresh() {
            port.checkOwner();
            if (!owned() || state == NativeTrackingRangeNMS.State.CLOSING) return state;
            long now = clock();
            if (state == NativeTrackingRangeNMS.State.WAITING && now - waitingSince >= waitingTicks) return lost();
            Identity next = port.identity(host);
            if (next == null || !id.equals(next.uuid)) return lost();
            T nextTracker = port.tracker(host, next);
            if (nextTracker == null) {
                if (state != NativeTrackingRangeNMS.State.WAITING) waitingSince = now;
                // A detached tracker is not a valid rollback target, even if it is still reachable.
                tracker = null; wrote = false; state = NativeTrackingRangeNMS.State.WAITING;
                return now - waitingSince >= waitingTicks ? lost() : state;
            }
            if (tracker == nextTracker && identity.same(next)) {
                if (port.read(tracker) != requested) return lost();
                state = NativeTrackingRangeNMS.State.ACTIVE; return state;
            }
            // Never restore a detached predecessor or treat its old original as this epoch's original.
            tracker = null; wrote = false;
            int previous = port.read(nextTracker);
            if (hasEpoch && previous != port.canonicalRange(host, next)) return lost();
            identity = next; tracker = nextTracker; original = previous;
            if (epoch == Long.MAX_VALUE) return lost();
            hasEpoch = true; epoch++;
            // Store the receipt before the call: a native implementation may write then throw.
            wrote = true;
            installing = true;
            try {
                if (!port.compareAndSet(tracker, previous, requested)) { wrote = false; return lost(); }
                if (!identity.same(port.identity(host)) || port.tracker(host, identity) != tracker) {
                    wrote = false; return lost();
                }
                installing = false;
                if (closeRequested || closed) { close(); return state; }
                state = NativeTrackingRangeNMS.State.ACTIVE; return state;
            } catch (RuntimeException | Error error) {
                installing = false;
                state = NativeTrackingRangeNMS.State.CLOSING;
                Throwable retained = error;
                try { close(); } catch (RuntimeException | Error cleanup) { retained = retain(retained, cleanup); }
                throw propagate(retained);
            } finally { installing = false; }
        }
        private NativeTrackingRangeNMS.State lost() {
            wrote = false; tracker = null; state = NativeTrackingRangeNMS.State.LOST;
            controls.remove(id, this); return state;
        }
        @Override public void close() {
            port.checkOwner();
            if (state == NativeTrackingRangeNMS.State.CLOSED) return;
            if (installing) { closeRequested = true; state = NativeTrackingRangeNMS.State.CLOSING; return; }
            if (!owned()) { state = NativeTrackingRangeNMS.State.CLOSED; return; }
            state = NativeTrackingRangeNMS.State.CLOSING;
            Identity current = port.identity(host);
            if (wrote && identity.same(current) && port.tracker(host, current) == tracker) {
                int actual = port.read(tracker);
                if ((actual == requested || rollbackAlternative != null && actual == rollbackAlternative)
                        && !port.compareAndSet(tracker, actual, original)) {
                    // False means an external value won, not permission to write it a second time.
                    if (port.read(tracker) == actual) throw new IllegalStateException("Native range restore was not confirmed");
                }
            }
            wrote = false; tracker = null; state = NativeTrackingRangeNMS.State.CLOSED;
            controls.remove(id, this);
        }
    }
}
