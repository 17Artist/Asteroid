package priv.seventeen.artist.asteroid.internal.tracking;

import org.junit.jupiter.api.Test;
import priv.seventeen.artist.asteroid.entity.NativeTrackingRangeNMS;
import static org.junit.jupiter.api.Assertions.*;

/** Existing native owner algorithm, with zero as an actual raw field value rather than no-op. */
class TrackingRangeZeroTest {
    @Test void zeroIsWrittenAndExactNondefaultOriginalIsRestored() {
        var r=new TrackingRangeControlsTest.Rig();r.host.tracker.value=71;var lease=r.open(0);
        assertTrue(lease.current());assertEquals(0,lease.requestedBlocks());assertEquals(0,r.host.tracker.value);
        lease.close();assertEquals(71,r.host.tracker.value);assertEquals(0,r.controls.size());
    }
    @Test void zeroOriginalCanBeOwnedWithoutConfusingMissingTracker() {
        var r=new TrackingRangeControlsTest.Rig();r.host.tracker.value=0;var lease=r.open(0);
        assertEquals(NativeTrackingRangeNMS.State.ACTIVE,lease.state());assertTrue(lease.current());lease.close();assertEquals(0,r.host.tracker.value);
    }
    @Test void changingToZeroRollsBackPreviousSettingBeforeFinalInitialRestore() {
        var r=new TrackingRangeControlsTest.Rig();var lease=r.open(64);var change=lease.change(0);
        assertEquals(0,r.host.tracker.value);assertTrue(lease.current());assertTrue(change.rollback());assertEquals(64,r.host.tracker.value);
        lease.close();assertEquals(32,r.host.tracker.value);
    }
    @Test void changingFromZeroCanRollBackToActualZero() {
        var r=new TrackingRangeControlsTest.Rig();var lease=r.open(0);var change=lease.change(96);
        assertTrue(change.rollback());assertEquals(0,r.host.tracker.value);assertTrue(lease.current());lease.close();assertEquals(32,r.host.tracker.value);
    }
    @Test void zeroDoesNotAuthorizeOverwritingLaterForeignValue() {
        var r=new TrackingRangeControlsTest.Rig();var lease=r.open(0);r.host.tracker.value=91;assertFalse(lease.current());
        lease.close();assertEquals(91,r.host.tracker.value);assertEquals(0,r.controls.size());
    }
    @Test void laterZeroRevisionStillInvalidatesEarlierRollback() {
        var r=new TrackingRangeControlsTest.Rig();var lease=r.open(64);var first=lease.change(0);lease.change(96);lease.change(0);
        assertFalse(first.rollback());assertEquals(0,r.host.tracker.value);lease.close();assertEquals(32,r.host.tracker.value);
    }
    @Test void missingTrackerKeepsZeroWaitingAndWritesOnlyActualReplacement() {
        var r=new TrackingRangeControlsTest.Rig();r.host.tracker=null;var lease=r.open(0);
        assertEquals(NativeTrackingRangeNMS.State.WAITING,lease.state());assertEquals(0,r.writes);
        r.host.tracker=new TrackingRangeControlsTest.Tracker(32);r.tick++;
        assertEquals(NativeTrackingRangeNMS.State.ACTIVE,lease.refresh());assertEquals(0,r.host.tracker.value);lease.close();assertEquals(32,r.host.tracker.value);
    }
    @Test void failedZeroCloseRetainsExactReceiptForExplicitRetry() {
        var r=new TrackingRangeControlsTest.Rig();var lease=r.open(0);r.throwBefore=true;
        assertThrows(IllegalStateException.class,lease::close);assertEquals(1,r.controls.size());assertEquals(0,r.host.tracker.value);
        lease.close();assertEquals(32,r.host.tracker.value);assertEquals(0,r.controls.size());
    }
    @Test void zeroConsumesSameStartBudgetAndDoesNotOpenSecondOwner() {
        var r=new TrackingRangeControlsTest.Rig(2,1,200);r.open(0);
        assertThrows(IllegalStateException.class,()->r.controls.open(new TrackingRangeControlsTest.Host(),0));assertEquals(1,r.controls.size());
    }
    @Test void rawNegativeChangeIsStillRejectedWithoutNativeMutation() {
        var r=new TrackingRangeControlsTest.Rig();var lease=r.open(0);int writes=r.writes;
        assertThrows(IllegalArgumentException.class,()->lease.change(-1));assertEquals(writes,r.writes);assertEquals(0,r.host.tracker.value);assertTrue(lease.current());
    }
}
