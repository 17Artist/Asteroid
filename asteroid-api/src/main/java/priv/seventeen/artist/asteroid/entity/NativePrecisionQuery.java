package priv.seventeen.artist.asteroid.entity;

/**
 * One native bounding-box query, captured by the precision adapter on the owning thread.
 * Inflation is the actual accumulated native AABB inflation, not a guessed arrow radius.
 * The native owner's parameter uses its ordinary AABB implementation with that inflation;
 * it is NaN for a miss. Raw owner bounds are captured from the SAME native box before any
 * consumer callback, so later trusted reads can reject size-only changes as well as movement.
 * Authored part geometry may deliberately ignore the native margin. This value is local only:
 * it is neither a packet nor a persistent collision history or an authorization receipt.
 */
public record NativePrecisionQuery(double ax,double ay,double az,double bx,double by,double bz,
                                   double inflateX,double inflateY,double inflateZ,Mode mode,
                                   double ownerParameter,double ownerMinX,double ownerMinY,double ownerMinZ,
                                   double ownerMaxX,double ownerMaxY,double ownerMaxZ) {
    public enum Mode { CLIP, CONTAINS }
    public NativePrecisionQuery {
        if(mode==null)throw new IllegalArgumentException("Native query mode is missing");
        if(!Double.isFinite(ax)||!Double.isFinite(ay)||!Double.isFinite(az)
            ||!Double.isFinite(bx)||!Double.isFinite(by)||!Double.isFinite(bz)
            ||!Double.isFinite(inflateX)||!Double.isFinite(inflateY)||!Double.isFinite(inflateZ)
            ||!Double.isFinite(ownerMinX)||!Double.isFinite(ownerMinY)||!Double.isFinite(ownerMinZ)
            ||!Double.isFinite(ownerMaxX)||!Double.isFinite(ownerMaxY)||!Double.isFinite(ownerMaxZ))
            throw new IllegalArgumentException("Non-finite native query");
        if(ownerMinX>ownerMaxX||ownerMinY>ownerMaxY||ownerMinZ>ownerMaxZ)
            throw new IllegalArgumentException("Inverted native owner bounds");
        if(!Double.isNaN(ownerParameter)&&(!Double.isFinite(ownerParameter)||ownerParameter<0||ownerParameter>1))
            throw new IllegalArgumentException("Invalid native owner intersection");
        if(mode==Mode.CONTAINS&&(ax!=bx||ay!=by||az!=bz))
            throw new IllegalArgumentException("Native contains requires a point query");
    }
}
