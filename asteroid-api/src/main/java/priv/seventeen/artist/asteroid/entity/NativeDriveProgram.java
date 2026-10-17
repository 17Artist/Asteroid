package priv.seventeen.artist.asteroid.entity;

import java.util.function.BooleanSupplier;

/**
 * Owner-thread native program using the existing NativeMovement broker and DriveLease.
 * It never installs a second movement owner or a Bukkit scheduler.
 * One program owns the complete native MoveControl phase, including post-callback input draining.
 * There are no model, rider, bone, Mythic or controller-mode objects in this contract.
 */
@FunctionalInterface
public interface NativeDriveProgram {
    void tick(Phase phase);

    /**
     * A revocable, owner-thread capability valid only during this program's current native tick.
     * An adapter invalidates it in finally, even when the callback closes/replaces its lease or
     * throws. No operation may use a foreign controller or a replacement lease. Nested adapter
     * operations from a supplied guard are rejected, with the operation guard cleared in finally.
     *
     * All sample/native command/defaultMovement/drain operations share MAX_OPERATIONS per host
     * tick, not per passenger. Each queued input charges once on acceptance and once on drain.
     * active/nativeTick are constant-time identity checks, not budget-resetting entry points.
     * Exhaustion rejects before that operation writes; already completed native writes are not
     * claimed to be transactionally rolled back. A failed tick revokes the owner and its remainder.
     */
    interface Phase {
        int MAX_OPERATIONS = 256;
        int MAX_POST_INPUTS = 64;

        long nativeTick();
        boolean active();

        /** Contact is captured before forced ground; speed and velocity are read NOW, not cached. */
        Sample sample();

        /** At most once, before custom passenger callbacks when there is no main driver. */
        void defaultMovement();
        /** Clears native Xxa/Zza only; it does not zero velocity or disable AI. */
        void clearHorizontalInput();
        void groundedTravel(boolean onGround);
        void suppressWaterJump();
        void move(NativeMovementNMS.Input input);

        /**
         * FIFO after the entire host callback; resolved scalar values, not arbitrary Runnables.
         * Coordinate conversion and speed are evaluated at drain time. The bounded, read-only
         * guard is rechecked immediately before applying the command; false discards that command.
         * The provider owns this queue and clears all captured guards at phase end. A controller's
         * invocation capability may already be closed: the guard fences its binding, not that
         * invocation. Closing/replacing the native owner always invalidates every pending input.
         */
        void enqueue(NativeMovementNMS.Input input, BooleanSupplier stillCurrent);

        void jump();
        void resetFallDistance();
        /** Finite float-representable native delta; no Bukkit velocity or coordinate broadcast. */
        void velocity(double x, double y, double z);

        /**
         * Records this tick's native yaw/head-yaw/pitch for the owned LookControl phase. If never
         * supplied, the original LookControl ticks normally. No body-controller ownership change.
         */
        void direction(float yaw, float pitch);
    }

    /**
     * Values consumed by a controller. Finite float-representable velocity is required because
     * Mythic's reference variables are floats. Reject invalid samples; never clamp them to zero.
     */
    record Sample(long nativeTick, boolean onGround, boolean inWater, float movementSpeed,
                  double velocityX, double velocityY, double velocityZ) {
        public Sample {
            if (nativeTick < 0 || !Float.isFinite(movementSpeed) || movementSpeed < 0 || movementSpeed > 4
                    || !representable(velocityX) || !representable(velocityY) || !representable(velocityZ))
                throw new IllegalArgumentException("Invalid native driving sample");
        }
        private static boolean representable(double value) {
            return Double.isFinite(value) && Float.isFinite((float) value);
        }
    }
}
