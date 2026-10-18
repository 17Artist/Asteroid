package priv.seventeen.artist.asteroid.internal.entity;

/** Pure metadata shared by mapped adapters; no guessed projectile size, NMS copy or allocation. */
public final class NativePrecisionParameters {
    private NativePrecisionParameters() {}

    /**
     * Convert an actual successful native AABB.clip result to its segment parameter. Use the
     * largest component to avoid division by a nearly zero axis. Endpoint round-off is clamped
     * because the native clip has already established that the returned point is on the segment.
     */
    public static double clipped(double ax,double ay,double az,double bx,double by,double bz,
                                 double hitX,double hitY,double hitZ) {
        double x=bx-ax,y=by-ay,z=bz-az;
        double dx=Math.abs(x),dy=Math.abs(y),dz=Math.abs(z),parameter;
        if(dx>=dy&&dx>=dz&&dx>0)parameter=(hitX-ax)/x;
        else if(dy>=dz&&dy>0)parameter=(hitY-ay)/y;
        else if(dz>0)parameter=(hitZ-az)/z;
        else return hitX==ax&&hitY==ay&&hitZ==az?0:Double.NaN;
        return Double.isFinite(parameter)?Math.max(0,Math.min(1,parameter)):Double.NaN;
    }
}
