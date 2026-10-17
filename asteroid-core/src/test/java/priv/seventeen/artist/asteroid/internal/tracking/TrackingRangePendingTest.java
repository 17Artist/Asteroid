package priv.seventeen.artist.asteroid.internal.tracking;

import org.junit.jupiter.api.Test;
import priv.seventeen.artist.asteroid.entity.NativeTrackingRangeNMS;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Uses the actual native controller; no JVM/native field proof is claimed by this Port fixture. */
class TrackingRangePendingTest {
    private static TrackingRangeControlsTest.Rig waiting() {
        var r=new TrackingRangeControlsTest.Rig();r.host.tracker=null;return r;
    }
    @Test void pendingLastWriteHasNoIntermediateNativeWrite() {
        var r=waiting();var lease=r.open(64);lease.change(96);lease.change(128);
        assertEquals(0,r.writes);assertEquals(0,lease.epoch());assertEquals(128,lease.requestedBlocks());
        r.host.tracker=new TrackingRangeControlsTest.Tracker(41);assertEquals(NativeTrackingRangeNMS.State.ACTIVE,lease.refresh());
        assertEquals(1,r.writes);assertEquals(128,r.host.tracker.value);lease.close();assertEquals(41,r.host.tracker.value);
    }
    @Test void zeroIsARealPendingValue() {
        var r=waiting();var lease=r.open(64);lease.change(0);r.host.tracker=new TrackingRangeControlsTest.Tracker(32);
        lease.refresh();assertEquals(0,r.host.tracker.value);assertTrue(lease.current());
    }
    @Test void pendingRollbackRestoresPreviousIntentWithoutNativeWrite() {
        var r=waiting();var lease=r.open(64);var change=lease.change(96);
        assertTrue(change.rollback());assertFalse(change.rollback());assertEquals(64,lease.requestedBlocks());assertEquals(0,r.writes);
    }
    @Test void pendingRollbackAfterFirstReadyRestoresPriorSettingNotOriginal() {
        var r=waiting();var lease=r.open(64);var change=lease.change(96);r.host.tracker=new TrackingRangeControlsTest.Tracker(41);lease.refresh();
        assertTrue(change.rollback());assertEquals(64,r.host.tracker.value);lease.close();assertEquals(41,r.host.tracker.value);
    }
    @Test void sameNumericNewRequestFencesOldRollback() {
        var r=waiting();var lease=r.open(64);var old=lease.change(96);lease.change(96);
        assertFalse(old.rollback());assertEquals(96,lease.requestedBlocks());assertEquals(0,r.writes);
    }
    @Test void pendingWritesCannotRenewOriginalTimeout() {
        var r=waiting();var lease=r.open(64);r.tick=199;lease.change(96);r.tick=200;
        r.host.tracker=new TrackingRangeControlsTest.Tracker(32);
        assertEquals(NativeTrackingRangeNMS.State.LOST,lease.refresh());assertEquals(32,r.host.tracker.value);assertEquals(0,r.writes);
    }
    @Test void differentWorldCannotChangePendingRequest() {
        var r=waiting();var lease=r.open(64);r.host.world=UUID.randomUUID();
        assertThrows(IllegalStateException.class,()->lease.change(96));assertEquals(64,lease.requestedBlocks());assertEquals(0,r.writes);
    }
    @Test void oldPendingRollbackCannotCrossWorldEpoch() {
        var r=waiting();var lease=r.open(64);var old=lease.change(96);r.host.world=UUID.randomUUID();
        r.host.tracker=new TrackingRangeControlsTest.Tracker(32);lease.refresh();assertFalse(old.rollback());assertEquals(96,r.host.tracker.value);
    }
    @Test void oldPendingRollbackCannotCrossSecondTrackerEpoch() {
        var r=waiting();var lease=r.open(64);var old=lease.change(96);r.host.tracker=new TrackingRangeControlsTest.Tracker(32);lease.refresh();
        r.host.tracker=new TrackingRangeControlsTest.Tracker(32);lease.refresh();assertEquals(2,lease.epoch());assertFalse(old.rollback());
    }
    @Test void pendingCloseCannotWriteLateTracker() {
        var r=waiting();var lease=r.open(64);lease.change(96);lease.close();r.host.tracker=new TrackingRangeControlsTest.Tracker(32);
        assertEquals(NativeTrackingRangeNMS.State.CLOSED,lease.refresh());assertEquals(0,r.writes);assertEquals(0,r.controls.size());
    }
    @Test void pendingFirstWriteThenThrowRetainsExistingCleanupSemantics() {
        var r=waiting();var lease=r.open(64);lease.change(96);r.host.tracker=new TrackingRangeControlsTest.Tracker(41);r.throwAfter=true;
        assertThrows(IllegalStateException.class,lease::refresh);assertEquals(41,r.host.tracker.value);assertEquals(0,r.controls.size());
    }
    @Test void everyPendingChangeSharesExisting32StartBudget() {
        var r=waiting();var lease=r.open(64);for(int i=0;i<31;i++)lease.change(i);
        assertThrows(IllegalStateException.class,()->lease.change(96));assertEquals(30,lease.requestedBlocks());assertEquals(0,r.writes);
        r.tick++;lease.change(96);assertEquals(96,lease.requestedBlocks());
    }
}
