package priv.seventeen.artist.asteroid.entity;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.BoundingBox;

/** Owner-thread-only child hitbox with no AI, equipment ticking or network tracking. */
public interface NativeDamageRelay extends AutoCloseable {
    @FunctionalInterface interface Handler { boolean hurt(Damage damage); }
    /** A synchronous, single-callback capability; forwarding after the callback returns is rejected. */
    interface Damage {
        float amount();
        Entity attacker();
        Entity directSource();
        /**
         * Runs the target's native hurt method with the identical DamageSource, not synthetic damage.
         * A target can be attempted only once per callback; cancelled damage also consumes its attempt.
         * At most 65 targets, finite nonnegative damage <= 1,000,000, same world, no relay recursion.
         */
        boolean forward(Entity target,float amount);
    }
    LivingEntity entity();
    void update(BoundingBox worldBounds);
    boolean valid();
    @Override void close();
}
