package priv.seventeen.artist.asteroid.nms.v1_21_1;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Numeric original-BASE boundary only; not a native event/source/holder live acceptance claim. */
class NativeArrowOriginalRawTest {
    @Test void exactNativeFloatOriginalIsRetained(){for(float raw:new float[]{0,1,8,999999,1000000,0.5f})
        assertEquals(raw,NativeArrowDamageSourceBridge.originalRaw((double)raw));}
    @Test void nonFloatDoubleCannotMasqueradeAsNativeRaw(){assertThrows(UnsupportedOperationException.class,()->NativeArrowDamageSourceBridge.originalRaw(8.00000001));}
    @Test void invalidAndUnboundedOriginalsReject(){for(double raw:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,-1,1000001})
        assertThrows(UnsupportedOperationException.class,()->NativeArrowDamageSourceBridge.originalRaw(raw));}
}
