package priv.seventeen.artist.asteroid.internal.tracking;

import org.junit.jupiter.api.Test;
import priv.seventeen.artist.asteroid.entity.NativeTrackingRangeNMS;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

/** Compiles unchanged against the frozen API: a missing typed receipt is an assertion failure, not a compile failure. */
class TrackingRangeOpenCleanupTest {
    private static AutoCloseable receipt(Throwable failure) throws Exception {
        var marker = Arrays.stream(failure.getSuppressed())
                .filter(t -> t.getClass().getName().equals(NativeTrackingRangeNMS.class.getName() + "$PendingCleanup"))
                .findFirst().orElse(null);
        assertNotNull(marker, "failed first write must export its exact cleanup-only receipt");
        return (AutoCloseable) marker.getClass().getMethod("receipt").invoke(marker);
    }
    private static Throwable fail(TrackingRangeControlsTest.Rig r, Throwable failure) {
        r.duringWrite = () -> {
            r.ignoreWrite = true;
            if (failure instanceof Error error) throw error;
            throw (RuntimeException) failure;
        };
        return assertThrows(failure.getClass(), () -> r.open(64));
    }
    @Test void runtimeFailureIdentityAndBothRestoreFailuresArePreserved() throws Exception {
        var r = new TrackingRangeControlsTest.Rig();
        var original = new IllegalStateException("write completed then failed");
        var actual = fail(r, original);
        assertSame(original, actual);
        assertEquals(64, r.host.tracker.value);
        assertEquals(1, r.controls.size());
        assertEquals(2, Arrays.stream(actual.getSuppressed()).filter(t -> t.getMessage().contains("restore")).count());
        var cleanup = receipt(actual);
        assertFalse(cleanup instanceof NativeTrackingRangeNMS.RangeLease, "receipt must not regain admission/change");
        r.ignoreWrite = false;
        cleanup.close();
        assertEquals(32, r.host.tracker.value);
        assertEquals(0, r.controls.size());
    }
    @Test void originalErrorAlsoExportsRetryableCleanupWithoutBeingWrapped() throws Exception {
        var r = new TrackingRangeControlsTest.Rig();
        var original = new AssertionError("native write failed");
        assertSame(original, fail(r, original));
        var cleanup = receipt(original);
        assertThrows(IllegalStateException.class, cleanup::close);
        assertEquals(1, r.controls.size());
        r.ignoreWrite = false;
        cleanup.close();
        assertEquals(32, r.host.tracker.value);
        assertEquals(0, r.controls.size());
    }
    @Test void successfulImmediateRestoreHasNoPendingReceipt() {
        var r = new TrackingRangeControlsTest.Rig();
        r.throwAfter = true;
        var failure = assertThrows(IllegalStateException.class, () -> r.open(64));
        assertTrue(Arrays.stream(failure.getSuppressed()).noneMatch(t -> t.getClass().getSimpleName().equals("PendingCleanup")));
        assertEquals(32, r.host.tracker.value);
        assertEquals(0, r.controls.size());
    }
    @Test void failedOpenReceiptCannotRestoreForeignRange() throws Exception {
        var r = new TrackingRangeControlsTest.Rig();
        var cleanup = receipt(fail(r, new IllegalStateException("first write")));
        r.host.tracker.value = 91;
        r.ignoreWrite = false;
        cleanup.close();
        assertEquals(91, r.host.tracker.value);
        assertEquals(0, r.controls.size());
    }
    @Test void failedOpenReceiptCannotWriteReplacementTracker() throws Exception {
        var r = new TrackingRangeControlsTest.Rig();
        var cleanup = receipt(fail(r, new IllegalStateException("first write")));
        var old = r.host.tracker;
        r.host.tracker = new TrackingRangeControlsTest.Tracker(77);
        r.ignoreWrite = false;
        cleanup.close();
        assertEquals(64, old.value);
        assertEquals(77, r.host.tracker.value);
        assertEquals(0, r.controls.size());
    }
    private static final class NoSuppressionFailure extends RuntimeException {
        NoSuppressionFailure() { super("suppression disabled", null, false, true); }
    }
    private static final class NoSuppressionError extends Error {
        NoSuppressionError() { super("suppression disabled error", null, false, true); }
    }
    @Test void disabledSuppressionUsesRuntimeWrapperWithOriginalCauseAndExactReceipt() throws Exception {
        var r = new TrackingRangeControlsTest.Rig();
        var original = new NoSuppressionFailure();
        r.duringWrite = () -> { r.ignoreWrite = true; throw original; };
        var failure = assertThrows(RuntimeException.class, () -> r.open(64));
        assertNotSame(original, failure);
        assertSame(original, failure.getCause());
        assertEquals(0, original.getSuppressed().length);
        assertEquals(2, Arrays.stream(failure.getSuppressed()).filter(t -> t.getMessage().contains("restore")).count());
        var cleanup = receipt(failure);
        r.ignoreWrite = false; cleanup.close();
        assertEquals(32, r.host.tracker.value);
        assertEquals(0, r.controls.size());
    }
    @Test void disabledSuppressionPreservesErrorCategoryAndOriginalCause() throws Exception {
        var r = new TrackingRangeControlsTest.Rig();
        var original = new NoSuppressionError();
        r.duringWrite = () -> { r.ignoreWrite = true; throw original; };
        var failure = assertThrows(Error.class, () -> r.open(64));
        assertNotSame(original, failure);
        assertSame(original, failure.getCause());
        assertEquals(0, original.getSuppressed().length);
        var cleanup = receipt(failure);
        r.ignoreWrite = false; cleanup.close();
        assertEquals(32, r.host.tracker.value);
        assertEquals(0, r.controls.size());
    }
    @Test void oldCleanupCannotCloseAReplacementOwner() throws Exception {
        var r = new TrackingRangeControlsTest.Rig();
        var cleanup = receipt(fail(r, new IllegalStateException("first write")));
        r.ignoreWrite = false;
        cleanup.close();
        var next = r.open(96);
        cleanup.close();
        assertTrue(next.current());
        assertEquals(96, r.host.tracker.value);
        assertEquals(1, r.controls.size());
    }
}
