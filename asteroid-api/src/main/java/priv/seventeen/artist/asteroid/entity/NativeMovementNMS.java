package priv.seventeen.artist.asteroid.entity;

import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/** Owner-thread native movement. Immediate signals do not install a controller or schedule ticks. */
public interface NativeMovementNMS {
    /** Requests the mob's existing JumpControl; does not set velocity, teleport, or force an airborne jump. */
    void requestJump(Entity entity);
    /** Clears the mob's accumulated fall distance once, not future falling damage. */
    void resetFallDistance(Entity entity);
    /** Writes travel inputs, not velocity. World input uses yaw and movement speed at execution time. */
    default void move(Entity entity, Input input){throw new UnsupportedOperationException("Native move is unavailable");}
    /** Temporarily delegates the native MoveControl and drains a bounded queue after its tick. */
    default MovementLease controlMovement(Entity entity, Consumer<Throwable> failure){throw new UnsupportedOperationException("Post-tick move is unavailable");}
    /** Persistent post-MoveControl animation displacement; no callback while vanilla AI is disabled. */
    default RootMotionLease controlRootMotion(Entity entity,Supplier<RootMotionFrame> source,Consumer<Throwable> failure){throw new UnsupportedOperationException("Root motion is unavailable");}
    /** Explicit optional capability; older implementations must not silently accept a driving mode. */
    default boolean supportsDriving(){return false;}
    /** Ordered custom programs require the same exclusive native driving owner; no fallback. */
    default boolean supportsDrivePrograms(){return false;}
    /** Retryable cleanup for this provider's supported drive/navigation kinds; not a species capability. */
    default boolean supportsMotionCleanup(){return false;}
    /**
     * Publishes the exact lease before native installation. The receiver must store the receipt,
     * not drive or replace the host. Reentrant close revokes immediately but can report admission
     * still in progress; retry that SAME receipt after the admission stack unwinds. A thrown
     * acquisition never proves release. Old providers explicitly reject this stronger contract.
     */
    default DriveLease controlDriveProgramRetained(Plugin owner,Entity entity,
            NativeDriveProgram program,Consumer<Throwable> failure,
            Consumer<DriveLease> retain){
        throw new UnsupportedOperationException("Retained native driving admission is unavailable");
    }
    /**
     * Explicit plugin shutdown ONLY, not ordinary runtime replacement or exact-entity recovery.
     * Revokes all this plugin's captured navigation/drive owners before callbacks, then attempts
     * each once. Failed exact receipts remain bounded and retryable. Same-plugin acquisition
     * during this call rejects; no foreign plugin owner is closed. This does not close root/queue
     * movement, mounts or global Asteroid services.
     */
    default void closeMotionOwned(Plugin owner){
        throw new UnsupportedOperationException("Retryable native motion shutdown is unavailable");
    }
    default DriveLease controlDriveProgram(Plugin owner,Entity entity,
            NativeDriveProgram program,Consumer<Throwable> failure){
        throw new UnsupportedOperationException("Native driving programs are unavailable");
    }
    /**
     * Exclusive native MoveControl + LookControl ownership. Does not disable AI, teleport, schedule
     * coordinate packets or change the body controller. Rejects foreign controllers and active
     * queued movement/root motion. Source runs once per native movement tick on the owner thread.
     * Null means neutral input, not permission to replay an older frame. Closing restores only
     * controllers still owned by this lease. Owner plugin disable also closes forgotten leases.
     */
    default DriveLease controlDriving(Plugin owner,Entity entity,
            Function<DriveContext,DriveFrame> source,Consumer<Throwable> failure){
        throw new UnsupportedOperationException("Native driving is unavailable");
    }
    /** Contact state sampled before this tick's resolved native signals are applied. */
    record DriveContext(long nativeTick,boolean onGround,boolean inWater){
        public DriveContext{if(nativeTick<0)throw new IllegalArgumentException("Invalid native tick");}
    }
    /**
     * Resolved low-level signals, not walking/flying business modes. Angles are degrees; yaw writes
     * native yaw and head yaw, pitch writes XRot; body yaw remains with its existing controller.
     * Vertical factor scales the native MOVEMENT_SPEED attribute before the travel input write.
     * groundedTravel and suppressWaterJump are explicit native controls, not fabricated contact
     * observations. Callers decide their use from DriveContext. Finite bounds reject, never clamp.
     */
    record DriveFrame(long nativeTick,float side,float front,float speed,float yaw,float pitch,
                      boolean resetFall,boolean zeroVertical,float verticalSpeedFactor,boolean jump,
                      boolean groundedTravel,boolean suppressWaterJump){
        public DriveFrame{
            if(nativeTick<0||!Float.isFinite(side)||Math.abs(side)>1||!Float.isFinite(front)||Math.abs(front)>1
                    ||!Float.isFinite(speed)||speed<0||speed>4||!Float.isFinite(yaw)||Math.abs(yaw)>360
                    ||!Float.isFinite(pitch)||Math.abs(pitch)>90||!Float.isFinite(verticalSpeedFactor)||Math.abs(verticalSpeedFactor)>1)
                throw new IllegalArgumentException("Invalid or excessive driving frame");
        }
    }
    interface DriveLease extends AutoCloseable {
        boolean active();
        long executed();
        /** Revokes first. On failure retain THIS receipt and retry close; active=false is not release. */
        @Override void close();
    }
    /** Resolved world-space displacement. A sequence is consumed at most once by each lease. */
    record RootMotionFrame(long sequence,double x,double y,double z,double baseMultiplier,boolean onGround){
        public RootMotionFrame{
            if(sequence<0||!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||Math.abs(x)>4||Math.abs(y)>4||Math.abs(z)>4||!Double.isFinite(baseMultiplier)||baseMultiplier<0||baseMultiplier>1)
                throw new IllegalArgumentException("Invalid or excessive root motion frame");
        }
    }
    interface RootMotionLease extends AutoCloseable {
        boolean active();
        long executed();
        /** Revokes the callback; restores the original controller when no queued-movement lease remains. */
        @Override void close();
    }
    record Input(float side,float up,float front,float speed,boolean global){
        public Input{
            if(!Float.isFinite(side)||!Float.isFinite(up)||!Float.isFinite(front)||!Float.isFinite(speed))
                throw new IllegalArgumentException("Non-finite movement input");
        }
    }
    interface MovementLease extends AutoCloseable {
        int MAX_PENDING=64;
        /** Resolved values only. Overflow rejects the new command without discarding accepted commands. */
        void enqueue(Input input);
        boolean active();
        int pending();
        long executed();
        /** Restores the previous controller only if still owned; repeated close is safe. */
        @Override void close();
    }
}
