package priv.seventeen.artist.asteroid.entity;

import org.bukkit.entity.Entity;
import java.util.Objects;

/** Optional cold, read-only native qualification. No coordinates, ownership, task or lease are retained. */
public interface NativeModelPivotNMS {
    enum Family { LEGACY_1201, ATTACHMENT_1204, ATTACHMENTS_121X }
    record PivotHost(Entity host, Family family) {
        public PivotHost { Objects.requireNonNull(host); Objects.requireNonNull(family); }
    }
    /** Owner-thread only. Rejects unsupported exact native classes and non-single-vertical attachments. */
    PivotHost describePivotHost(Entity host);
    /** One cold capture on the owner thread; never a polling service or retained native lease. */
    default PivotSample samplePivotHost(Entity host, boolean camera) {
        throw new UnsupportedOperationException("Static native pivot sampling is unavailable");
    }
    record PivotSample(PivotHost description, double mountY, double displayY) {
        public PivotSample {
            Objects.requireNonNull(description);
            if (!Double.isFinite(mountY) || !Double.isFinite(displayY) || Math.abs(mountY) > 128 || Math.abs(displayY) > 128)
                throw new IllegalArgumentException("Invalid static native pivot sample");
        }
    }
}
