package priv.seventeen.artist.asteroid.internal.entity;

import org.junit.jupiter.api.Test;
import priv.seventeen.artist.asteroid.entity.NativePrecisionQuery;
import priv.seventeen.artist.asteroid.entity.PrecisionHitbox;
import static org.junit.jupiter.api.Assertions.*;

/** API/metadata only: does not construct an NMS host, launch a server or manufacture a Dummy. */
class NativePrecisionQueryTest {
    static NativePrecisionQuery clip(double x,double y,double z,double owner){
        return new NativePrecisionQuery(0,0,0,1,2,3,x,y,z,NativePrecisionQuery.Mode.CLIP,owner,-1,-2,-3,1,2,3);
    }
    @Test void contextTraceCannotSilentlyLoseItsContextThroughTheOldSixDoubleMethod(){
        PrecisionHitbox.ContextTrace trace=q->q.ownerParameter();
        assertThrows(UnsupportedOperationException.class,()->trace.intersect(0,0,0,1,0,0));
        assertEquals(.25,trace.intersect(clip(.3,.4,.5,.25)));
    }
    @Test void actualAsymmetricAndNegativeInflationIsMetadataNotAGuessedRadius(){
        var q=clip(.125,-.03125,2,.75);assertEquals(.125,q.inflateX());assertEquals(-.03125,q.inflateY());assertEquals(2,q.inflateZ());
    }
    @Test void nativeMissRemainsNanAndBothClosedEndpointsAreValid(){
        assertTrue(Double.isNaN(clip(0,0,0,Double.NaN).ownerParameter()));
        assertEquals(0,clip(0,0,0,0).ownerParameter());assertEquals(1,clip(0,0,0,1).ownerParameter());
    }
    @Test void invalidNativeParametersAndMissingModesAreRejected(){
        for(double x:new double[]{-1,1.0001,Double.NEGATIVE_INFINITY,Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class,()->clip(0,0,0,x));
        assertThrows(IllegalArgumentException.class,()->new NativePrecisionQuery(0,0,0,1,0,0,0,0,0,null,Double.NaN,-1,-2,-3,1,2,3));
    }
    @Test void everyCoordinateAndInflationMustRemainFinite(){
        for(int axis=0;axis<15;axis++)for(double invalid:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY}){
            double[] v={0,0,0,1,2,3,0,0,0,-1,-2,-3,1,2,3};v[axis]=invalid;
            assertThrows(IllegalArgumentException.class,()->new NativePrecisionQuery(v[0],v[1],v[2],v[3],v[4],v[5],v[6],v[7],v[8],NativePrecisionQuery.Mode.CLIP,Double.NaN,v[9],v[10],v[11],v[12],v[13],v[14]));
        }
    }
    @Test void containsIsAnExplicitPointQueryNotAnInventedZeroLengthClip(){
        var q=new NativePrecisionQuery(1,2,3,1,2,3,.1,.2,.3,NativePrecisionQuery.Mode.CONTAINS,0,-1,-2,-3,1,2,3);
        assertEquals(NativePrecisionQuery.Mode.CONTAINS,q.mode());
        assertThrows(IllegalArgumentException.class,()->new NativePrecisionQuery(1,2,3,1,2,4,.1,.2,.3,NativePrecisionQuery.Mode.CONTAINS,0,-1,-2,-3,1,2,3));
    }
    @Test void rawNativeOwnerBoundsRemainUninflatedAndInvertedBoundsAreRejected(){
        var q=clip(5,6,7,.5);assertEquals(-1,q.ownerMinX());assertEquals(-2,q.ownerMinY());assertEquals(-3,q.ownerMinZ());
        assertEquals(1,q.ownerMaxX());assertEquals(2,q.ownerMaxY());assertEquals(3,q.ownerMaxZ());
        assertThrows(IllegalArgumentException.class,()->new NativePrecisionQuery(0,0,0,1,2,3,0,0,0,NativePrecisionQuery.Mode.CLIP,Double.NaN,2,0,0,1,1,1));
        assertThrows(IllegalArgumentException.class,()->new NativePrecisionQuery(0,0,0,1,2,3,0,0,0,NativePrecisionQuery.Mode.CLIP,Double.NaN,0,2,0,1,1,1));
        assertThrows(IllegalArgumentException.class,()->new NativePrecisionQuery(0,0,0,1,2,3,0,0,0,NativePrecisionQuery.Mode.CLIP,Double.NaN,0,0,2,1,1,1));
    }
    @Test void nativeClipParameterUsesTheLargestNonzeroComponentInEitherDirection(){
        assertEquals(.25,NativePrecisionParameters.clipped(0,0,0,8,1,1,2,.25,.25));
        assertEquals(.5,NativePrecisionParameters.clipped(0,0,0,1,-8,1,.5,-4,.5));
        assertEquals(.75,NativePrecisionParameters.clipped(0,0,0,1,1,8,.75,.75,6));
    }
    @Test void tinyUnusedComponentsCannotDominateTheParameter(){
        assertEquals(.5,NativePrecisionParameters.clipped(1,2,3,1+1e-12,2,11,1,2,7));
    }
    @Test void successfulNativeEndpointRoundoffRemainsWithinTheClosedSegment(){
        assertEquals(0,NativePrecisionParameters.clipped(0,0,0,1,0,0,-Math.ulp(1d),0,0));
        assertEquals(1,NativePrecisionParameters.clipped(0,0,0,1,0,0,1+Math.ulp(1d),0,0));
    }
    @Test void zeroLengthNativeResultNeedsTheSamePointAndNanResultCannotBecomeAHit(){
        assertEquals(0,NativePrecisionParameters.clipped(1,2,3,1,2,3,1,2,3));
        assertTrue(Double.isNaN(NativePrecisionParameters.clipped(1,2,3,1,2,3,2,2,3)));
        assertTrue(Double.isNaN(NativePrecisionParameters.clipped(0,0,0,1,0,0,Double.NaN,0,0)));
    }
}
