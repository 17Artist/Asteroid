package priv.seventeen.artist.asteroid.entity;

import org.bukkit.Location;
import org.bukkit.entity.Mob;
import org.bukkit.plugin.Plugin;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Optional server-native navigation ownership. Consumers must not substitute Paper's unowned
 * findPath/stop calls when this capability is absent. Version-local Paper providers admit exact native
 * Zombie/Husk and explicitly prepared exact Skeleton with Ground navigation and ordinary MoveControl.
 * Ground/facing support does not imply the optional retained Husk flight-handoff contract below.
 * Skeleton requires native daylight burning and loot pickup disabled, no powder-snow exposure or
 * freeze conversion. These conditions and goal ownership are rechecked throughout the lease.
 * No Folia, modern Brain-type
 * species, custom/flying/water/climbing controller. Owner must first remove competing MOVE/TARGET
 * goals; AI remains enabled and aware. Leashed, mounted, dirty or externally driven hosts reject.
 */
public interface NativeNavigationNMS {
    /**
     * Explicit provider opt-in for an exact native Husk ground -> retained drive-program -> ground
     * handoff. Default false preserves old providers: ordinary navigation, supportsDrivePrograms or
     * supportsMotionCleanup alone must never imply this stronger combination. A true answer is not
     * host admission, a flight route or proof of released ownership. Callers must still hold the exact
     * live ground receipt, prove exclusive goals/entity/world/model/owner, retain before native writes,
     * close the old controller successfully before the next claim, and bound/verify real contact and
     * native program operations. Unsupported platforms reject; no Paper-pathfinder fallback.
     */
    default boolean supportsRetainedHuskFlight(){return false;}
    NavigationLease controlNavigation(Plugin owner, Mob host);
    /** Exact receipt is published before native writes. Same retained-admission rules as driving. */
    default NavigationLease controlNavigationRetained(Plugin owner,Mob host,
            Consumer<NavigationLease> retain){
        throw new UnsupportedOperationException("Retained native navigation admission is unavailable");
    }

    /**
     * Explicit cold setup for a version-local exact Paper Skeleton provider, not lease acquisition.
     * The caller must prove no Brain owns this entity; the provider proves no native navigation or
     * movement owner, ordinary idle controllers, cleared MOVE/TARGET goals and a stable same-tick host.
     * Preflight rejection writes nothing. Only daylight burning and loot pickup are set false.
     * No NBT reload, AI toggle, goal removal, timer or implicit restoration. A post-write exception
     * may leave either configuration flag false: retain the exact entity and retire/retry explicitly.
     * Successful setup alone confers no navigation or Encounter authority.
     */
    default void prepareSkeleton(Plugin owner, Mob host) {
        throw new UnsupportedOperationException("Explicit native Skeleton preparation is unavailable");
    }

    /** Immutable, world-local block/path point. The lease pins the actual World object. */
    record Point(double x, double y, double z) {
        public Point {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                    || Math.abs(x) > 30_000_000 || Math.abs(z) > 30_000_000 || Math.abs(y) > 4096)
                throw new IllegalArgumentException("Invalid native navigation point");
        }
    }
    interface RouteGuard {
        /** May synchronously close/reset/switch owners; rechecked after every callback. */
        boolean current();
        boolean accept(List<Point> route);
    }
    /**
     * One fixed native angle on an existing exact navigation owner, not target tracking.
     * Version-local providers may implement this optional capability; unsupported providers reject.
     * The 6..200 ticks are a maximum lifetime [admissionTick, admissionTick+ticks).
     * current=false is revocation, not proof that physical restoration succeeded. Retain the
     * exact receipt before any native writes; retry close on that same object after failures.
     * A changed angle/controller/host revokes instead of being overwritten. Close restores only
     * the original still-owned control, leaves the final angle, and never rewinds external state.
     */
    interface FacingLease extends AutoCloseable {
        boolean current();
        @Override void close();
    }
    interface NavigationLease extends AutoCloseable {
        boolean active();
        long epoch();
        /** Optional retained fixed-facing capability; unsupported providers fail before any write. */
        default FacingLease freezeFacingRetained(float yaw,float pitch,int ticks,Consumer<FacingLease> retain){
            throw new UnsupportedOperationException("Native fixed facing is unavailable");
        }
        /** One real native query, charged before dispatching its real Paper event. */
        boolean move(Location destination, double speed, RouteGuard guard);
        /** Bounded immutable readback, not a new path query. */
        boolean currentSafe(Predicate<List<Point>> accept);
        /** Revokes in-flight queries and clears only this lease's installed route/input. */
        void stop();
        /** Exact restore. If it throws, retain THIS receipt and retry; active=false is not release. */
        @Override void close();
    }
}
