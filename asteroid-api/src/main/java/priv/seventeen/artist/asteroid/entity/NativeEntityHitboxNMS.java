package priv.seventeen.artist.asteroid.entity;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.function.Consumer;

/**
 * Optional exact-owner extension. The owner is the actual native Entity, never a synthetic
 * LivingEntity stand-in. Consumers must detect this capability; old Living-only APIs keep
 * their existing behavior. This does not grant arbitrary bindings or create an inert Dummy.
 */
public interface NativeEntityHitboxNMS {
    /** Replay the original native attack on the ACTUAL entity; never use a Living proxy stand-in. */
    void attackEntity(Player player,Entity target);
    /** Native projectile impact, preserving real-owner piercing identity and cancellation scope. */
    void hitEntity(Projectile projectile,Entity target,Vector point);
    NativeDamageRelay createEntityDamageRelay(Entity owner,BoundingBox bounds,
                                             NativeDamageRelay.Handler handler,Consumer<Throwable> failure);
    /** Includes actual native clip/contains margins so parent-only domains can preserve the owner's coarse box. */
    PrecisionHitbox createEntityPrecisionHitbox(Entity owner,BoundingBox bounds,PrecisionHitbox.ContextTrace trace);
}
