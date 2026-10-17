package priv.seventeen.artist.asteroid.entity;

import org.bukkit.entity.Entity;

/** Owner-thread model dimensions. The native coarse box stays square, independently of detailed combat boxes. */
public interface NativeDimensionsNMS {
    record Dimensions(float width,float height,float eyeHeight) {
        public Dimensions {
            if(!Float.isFinite(width)||!Float.isFinite(height)||!Float.isFinite(eyeHeight)
                ||width<0||height<0||width>1024||height>1024||Math.abs(eyeHeight)>1024)
                throw new IllegalArgumentException("Invalid native model dimensions");
        }
    }
    /** Exclusive per entity. Acquiring does not mutate dimensions until the first update. */
    DimensionsLease controlDimensions(Entity entity);
    interface DimensionsLease extends AutoCloseable {
        void update(Dimensions dimensions);
        /** Reasserts a vanilla pose refresh without rewriting unchanged fields or sending a packet. */
        boolean refresh();
        boolean active();
        /** Diagnostics for bounded tests; unchanged refresh calls do not increase this value. */
        long nativeWrites();
        /** Restore the captured dimensions only while this lease still owns the native fields. */
        @Override void close();
    }
}

