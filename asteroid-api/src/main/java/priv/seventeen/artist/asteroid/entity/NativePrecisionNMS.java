package priv.seventeen.artist.asteroid.entity;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/** Optional NMS capability. Consumers must feature-detect, never assume it from a version string. */
public interface NativePrecisionNMS {
    /** Excludes a hidden binding's coarse body from prepared arrow queries until the lease closes.
     * Other physics and explicit damage are unchanged. Owner-thread only, including close. */
    AutoCloseable excludeProjectileCandidate(Entity entity);
    PrecisionHitbox createPrecisionHitbox(LivingEntity owner, BoundingBox bounds, PrecisionHitbox.Trace trace);
    /** Caller validates reach, visibility, game mode and rate limit before entering vanilla attack. */
    void attack(Player player, LivingEntity target);
    /**
     * Install arrow-family candidate routing before its next native movement. Exact proxies replace
     * registered owners in candidate selection; ordinary entities and physics remain native.
     * Returns false for non-arrow projectiles. Reapply after loading or a block impact resets state.
     */
    boolean prepareProjectile(Projectile projectile);
    /** Vanilla projectile eligibility, including piercing exclusions and shooter/vehicle checks. */
    boolean canHit(Projectile projectile, Entity target);
    /** Run vanilla impact against the real owner, not its proxy. Does not cancel the caller's event. */
    void hit(Projectile projectile, LivingEntity target, Vector point);
}
