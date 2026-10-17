package priv.seventeen.artist.asteroid.entity;

import org.bukkit.entity.Entity;
import java.util.List;

/** Optional owner-thread prefix of Bukkit Entity.getNearbyEntities, before consumer filtering. */
public interface NativeNearbyEntitiesNMS {
    /** False is a supported compatibility fallback, not a promise of bounded native visits. */
    default boolean supportsOrderedNearbyPrefix() { return false; }

    /**
     * Same host bounding-box expansion, exclusion, encounter order and dragon-part expansion as
     * Entity.getNearbyEntities(x,y,z), truncated to the first limit entries (1..64).
     * No player/alive/range/visibility sorting or filtering may precede this prefix.
     * Limits accepted results and wrapper creation, not rejected/outside-AABB visits.
     */
    default List<Entity> nearbyEntityPrefix(Entity host,double x,double y,double z,int limit) {
        throw new UnsupportedOperationException("Ordered native nearby prefix is unavailable");
    }
}
