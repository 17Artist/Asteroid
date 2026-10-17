package priv.seventeen.artist.asteroid.entity;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Optional, owner-scoped virtual passenger transport for an EXISTING native host.
 * No server-world seat is spawned and Bukkit vehicle relationships are not fabricated.
 * All API calls require the ordinary Paper owner thread; Folia is explicitly unsupported.
 * The caller owns model events, seat selection, animation poses, visibility and driving physics.
 */
public interface NativeModelMountNMS {
    OwnerLease openModelMount(Plugin owner, Entity originalHost, UUID ownerEpoch,
                             Consumer<Throwable> failure);

    record Anchor(double x, double y, double z) {
        public Anchor {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                    || Math.abs(x) > 30_000_000 || Math.abs(z) > 30_000_000
                    || Math.abs(y) > 20_000_000) throw new IllegalArgumentException("Invalid passenger anchor");
        }
    }

    enum Encoding { NONE, LEGACY_ANALOG, MODERN_FLAGS }

    /** Server-observed intent. Old protocol sprint is sampled on the owner thread. */
    record Input(long sequence, Encoding encoding, float side, float front,
                 boolean forward, boolean backward, boolean left, boolean right,
                 boolean jump, boolean sneak, boolean sprint) {
        public static final Input EMPTY = new Input(0, Encoding.NONE, 0, 0,
                false, false, false, false, false, false, false);
        public Input {
            Objects.requireNonNull(encoding);
            if (sequence < 0 || (encoding != Encoding.NONE && sequence == 0)
                    || !Float.isFinite(side) || !Float.isFinite(front)
                    || Math.abs(side) > 1 || Math.abs(front) > 1) throw new IllegalArgumentException("Invalid passenger input");
            if (encoding == Encoding.MODERN_FLAGS && (side != (left ? 1 : 0) - (right ? 1 : 0)
                    || front != (forward ? 1 : 0) - (backward ? 1 : 0))) throw new IllegalArgumentException("Input flags disagree");
            if (encoding == Encoding.LEGACY_ANALOG && (forward != (front > 0) || backward != (front < 0)
                    || left != (side > 0) || right != (side < 0))) throw new IllegalArgumentException("Input axes disagree");
            if (encoding == Encoding.NONE && (sequence != 0 || side != 0 || front != 0 || forward
                    || backward || left || right || jump || sneak || sprint)) throw new IllegalArgumentException("Empty input has data");
        }
        public static Input legacy(long sequence, float side, float front, boolean jump, boolean sneak, boolean sprint) {
            return new Input(sequence, Encoding.LEGACY_ANALOG, side, front,
                    front > 0, front < 0, side > 0, side < 0, jump, sneak, sprint);
        }
        public static Input modern(long sequence, boolean forward, boolean backward, boolean left,
                                   boolean right, boolean jump, boolean sneak, boolean sprint) {
            return new Input(sequence, Encoding.MODERN_FLAGS, (left ? 1 : 0) - (right ? 1 : 0),
                    (forward ? 1 : 0) - (backward ? 1 : 0), forward, backward, left, right, jump, sneak, sprint);
        }
        public Input withNativeSprint(boolean value) {
            return encoding == Encoding.LEGACY_ANALOG && value != sprint
                    ? legacy(sequence, side, front, jump, sneak, value) : this;
        }
    }

    /** Read-only identities for an owned client-only pair; they grant no raw removal permission. */
    record TransportIds(int pivotId, UUID pivotUuid, int vehicleId, UUID vehicleUuid, double pivotOffsetY) {
        public TransportIds {
            Objects.requireNonNull(pivotUuid);
            Objects.requireNonNull(vehicleUuid);
            if (pivotId < 0 || vehicleId < 0 || pivotId == vehicleId || pivotUuid.equals(vehicleUuid)
                    || pivotOffsetY != -0.375 && pivotOffsetY != -0.5) {
                throw new IllegalArgumentException("Invalid virtual transport identities");
            }
        }
    }

    interface OwnerLease extends AutoCloseable {
        Entity host();
        UUID ownerEpoch();
        boolean active();

        /**
         * Claims a same-world rider exclusively and creates a client-only pivot/vehicle pair.
         * A player rider is shown its own vehicle before this returns. Packets are submitted, not
         * acknowledged by the client; asynchronous rejection invalidates the lease and is reported.
         * Foreign native vehicles/transports, self/nested hosts and exhausted budgets are rejected.
         * No cancellable business event is dispatched; perform it and revalidate before calling.
         * Before binding a player, the caller MUST verify support for its model-seat presentation
         * protocol. This native capability does not know AX capabilities and has no vanilla fallback.
         */
        RiderLease bind(Entity rider, UUID bindingToken, Anchor initialAnchor);

        /** Unconditionally revoke all owned riders; cancellation cannot keep a stale updater alive. */
        @Override void close();
    }

    interface RiderLease extends AutoCloseable {
        Entity rider();
        UUID token();
        TransportIds transportIds();
        boolean active();

        /** Held, connection-fenced latest input; no packet TTL and no client world coordinates. */
        Input latestInput();

        /**
         * Sets ONLY authoritative native passenger feet/velocity/fall/floating state using the
         * version-specific attachment. Never sends movement packets or drives the original host.
         * The anchor is retained for a later sparse show/retrack snapshot; a compatible client
         * must animate the owned pivot/vehicle itself from the caller's model-seat protocol.
         */
        void follow(Anchor attachmentAnchor);

        /**
         * Idempotent sparse spawn/metadata/passenger relationship publication. The caller MUST
         * verify compatible model-seat presentation, visibility/culling and viewer authorization.
         * No continuous-coordinate fallback is provided for an incompatible viewer.
         */
        void show(Player viewer);

        /**
         * Explicit sparse recovery for an ALREADY owned same-world/current-channel view whose
         * native rider is visible. Submits one ordered batch: owned removal, spawn/metadata,
         * pivot/seat/rider passenger edges. The caller must resend its seat binding AFTER this
         * returns; success is packet submission, not client acknowledgement. Does not rebind,
         * move, clear input or authorize an unshown/foreign observer. Normal show stays idempotent.
         * Old providers fail closed instead of silently pretending that recovery succeeded.
         */
        default void replay(Player viewer) { throw new UnsupportedOperationException("Native model mount replay is unsupported"); }

        /** Removes only this owned pair. Hiding the rider's own active transport is not permitted. */
        void hide(Player viewer);

        /** Idempotent, cannot eject a foreign native vehicle or restore stale position/velocity. */
        @Override void close();
    }
}
