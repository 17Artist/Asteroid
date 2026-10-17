package priv.seventeen.artist.asteroid.entity;

import org.bukkit.entity.Entity;
import org.bukkit.util.BoundingBox;

/** Server-only broad phase with an exact, side-effect-free segment query. Owner-thread only. */
public interface PrecisionHitbox extends AutoCloseable {
    @FunctionalInterface
    interface Trace {
        /** Return the first segment parameter in [0,1], or NaN for a miss. A zero segment is a point query. */
        double intersect(double ax, double ay, double az, double bx, double by, double bz);
    }
    Entity entity();
    void update(BoundingBox worldBounds, Trace trace);
    boolean valid();
    @Override void close();
}
