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
 * species, custom/flying/water/climbing controller. Strict ownership rejects competing MOVE/TARGET
 * goals; the separate preserving-target entry rejects MOVE but leaves TARGET ownership upstream.
 * Neither entry removes goals. AI remains enabled and aware. Leashed, mounted, dirty or externally
 * driven hosts reject.
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
    /** Explicit MOVE-only navigation ownership; old providers reject rather than clearing TARGET goals. */
    default boolean supportsTargetPreservingNavigation(){return false;}
    /**
     * Same retained Husk handoff as above, but reacquired ground/facing ownership preserves TARGET.
     * Ground still rejects every MOVE goal in either selector; facing/flight must also reject LOOK
     * and JUMP writers. This capability never admits a host or removes upstream goals by itself.
     */
    default boolean supportsTargetPreservingRetainedHuskFlight(){return false;}
    /**
     * Read-only, owner-thread check of the exact live native Husk and BOTH current native selector
     * flag sets, each bounded to 64 goals. Reject MOVE/LOOK/JUMP and foreign non-TARGET-only goals.
     * Does not acquire a lease, inspect ordinary ground controllers, clear goals or mutate entities;
     * it remains usable after ground navigation has been replaced by the retained flight driver.
     * Driving controller identity, physical contact and model ownership remain the caller's existing
     * flight contract. Paper's typed goal query/cached Goal.getTypes are not a substitute for this.
     */
    default void requireTargetPreservingFlightGoals(Plugin owner,Mob host){
        throw new UnsupportedOperationException("Target-preserving native flight goal inspection is unavailable");
    }
    NavigationLease controlNavigation(Plugin owner, Mob host);
    /**
     * Explicit MOVE-only variant. Both selectors remain intact and may select targets normally.
     * Every MOVE flag rejects, including TARGET+MOVE. Foreign goals are accepted only when their
     * declared flags are exactly TARGET; no flags do not prove absence of movement side effects.
     * Native controllers, route/settings/species checks and exact restoration remain mandatory.
     * Goal flags are the upstream coordination contract, not a proof against misdeclared code.
     */
    default NavigationLease controlNavigationPreservingTargets(Plugin owner,Mob host){
        throw new UnsupportedOperationException("Target-preserving native navigation is unavailable");
    }
    /** Same MOVE-only policy; publish the exact receipt before native writes, never fall back. */
    default NavigationLease controlNavigationPreservingTargetsRetained(Plugin owner,Mob host,
            Consumer<NavigationLease> retain){
        throw new UnsupportedOperationException("Retained target-preserving native navigation is unavailable");
    }
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
    /**
     * Same explicit cold Skeleton configuration and partial-failure contract as prepareSkeleton,
     * using the MOVE-only policy without clearing either selector. Not automatic spawn takeover.
     */
    default void prepareSkeletonPreservingTargets(Plugin owner,Mob host){
        throw new UnsupportedOperationException("Target-preserving native Skeleton preparation is unavailable");
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
     * On preserving-target navigation, both selectors must remain free of MOVE/LOOK/JUMP;
     * TARGET-only goals remain installed. A strict navigation lease still requires empty selectors.
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
