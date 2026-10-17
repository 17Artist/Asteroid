package priv.seventeen.artist.asteroid.entity;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import java.util.function.Consumer;

/** Owner-thread native orientation access. The caller reuses Pose; no per-tick records or reflection lookup. */
public interface NativePoseNMS {
    final class Pose {
        public float yaw,pitch,headYaw,bodyYaw,maxHeadAngle;
        public boolean alive,walking,mobPassenger;
    }
    void readPose(Entity entity,Pose output);
    enum YawAxis { YAW, BODY, HEAD }
    enum YawTarget { YAW, BODY, HEAD, ALL, NONE }
    /** One-shot axis copy through native virtual setters, including subtype side effects. Returns whether any current yaw axis changed. */
    default boolean syncYaw(Entity entity,YawAxis source,YawTarget target){throw new UnsupportedOperationException("Yaw synchronization is unavailable");}
    /** One-shot body yaw synchronization without changing look yaw, pitch or head yaw. */
    default void alignBody(Entity entity,float yaw){throw new UnsupportedOperationException("Body yaw synchronization is unavailable");}
    /** Owns exactly one body controller, restores the previous controller only while this lease still owns the field. */
    BodyLease controlBody(Mob mob,Consumer<Pose> controller,Consumer<Throwable> failure);
    interface BodyLease extends AutoCloseable {
        boolean active();
        @Override void close();
    }
}
