package priv.seventeen.artist.asteroid.internal.tracking;

import org.junit.jupiter.api.Test;
import priv.seventeen.artist.asteroid.entity.NativeTrackingRangeNMS;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class TrackingRangeControlsTest {
    static final class Tracker { int value; Tracker(int value) { this.value = value; } }
    static final class Host {
        final UUID id = UUID.randomUUID(); UUID world = UUID.randomUUID(); int entityId = 19;
        Object nativeEntity = new Object(); boolean valid = true; Tracker tracker = new Tracker(32);
    }
    static final class Rig implements TrackingRangeControls.Port<Host,Tracker> {
        long tick; int reads, writes, identities, startsBeforeThrow, canonical = 32;
        boolean owner = true, ignoreWrite, throwBefore, throwAfter;
        Runnable duringWrite;
        final Host host = new Host();
        final TrackingRangeControls<Host,Tracker> controls;
        Rig() { this(4096, 32, 200); }
        Rig(int capacity, int starts, int waiting) { controls = new TrackingRangeControls<>(this, capacity, starts, waiting); }
        @Override public void checkOwner() { if (!owner) throw new IllegalStateException("thread"); }
        @Override public long tick() { return tick; }
        @Override public TrackingRangeControls.Identity identity(Host host) {
            identities++; return host.valid ? new TrackingRangeControls.Identity(host.id, host.world, host.entityId, host.nativeEntity) : null;
        }
        @Override public Tracker tracker(Host host, TrackingRangeControls.Identity identity) { return host.tracker; }
        @Override public int read(Tracker tracker) { reads++; return tracker.value; }
        @Override public boolean compareAndSet(Tracker tracker, int expected, int value) {
            if (tracker.value != expected) return false;
            if (throwBefore) { throwBefore = false; throw new IllegalStateException("before"); }
            writes++;
            if (!ignoreWrite) tracker.value = value;
            Runnable callback = duringWrite; duringWrite = null; if (callback != null) callback.run();
            if (throwAfter) { throwAfter = false; throw new IllegalStateException("after"); }
            return tracker.value == value;
        }
        @Override public int canonicalRange(Host host, TrackingRangeControls.Identity identity) { return canonical; }
        NativeTrackingRangeNMS.RangeLease open(int blocks) { return controls.open(host, blocks); }
    }

    @Test void rawBlocksAreNotChunksOrEffectiveRange() { Rig r=new Rig(); var l=r.open(64); assertEquals(64,r.host.tracker.value); assertEquals(64,l.requestedBlocks()); assertEquals(1,l.epoch()); }
    @Test void integerMaximumIsPassedUnmodifiedToNative() { Rig r=new Rig(); var l=r.open(Integer.MAX_VALUE); assertTrue(l.current()); assertEquals(Integer.MAX_VALUE,r.host.tracker.value); }
    @Test void negativeRejectedBeforeHostLookup() { Rig r=new Rig(); assertThrows(IllegalArgumentException.class,()->r.open(-1)); assertThrows(IllegalArgumentException.class,()->r.open(Integer.MIN_VALUE)); assertEquals(0,r.identities); }
    @Test void minimumIntegerRejected() { Rig r=new Rig(); assertThrows(IllegalArgumentException.class,()->r.open(Integer.MIN_VALUE)); assertEquals(0,r.writes); }
    @Test void closeRestoresExactOriginal() { Rig r=new Rig(); r.host.tracker.value=71; var l=r.open(64); l.close(); assertEquals(71,r.host.tracker.value); assertEquals(0,r.controls.size()); }
    @Test void doubleCloseDoesNotWriteAgain() { Rig r=new Rig(); var l=r.open(64); l.close(); int n=r.writes; l.close(); assertEquals(n,r.writes); }
    @Test void foreignRangeIsNeverRestored() { Rig r=new Rig(); var l=r.open(64); r.host.tracker.value=91; l.close(); assertEquals(91,r.host.tracker.value); }
    @Test void foreignEditIsLossNotRepeatedOverwrite() { Rig r=new Rig(); var l=r.open(64); r.host.tracker.value=91; assertFalse(l.current()); assertEquals(NativeTrackingRangeNMS.State.LOST,l.refresh()); assertEquals(91,r.host.tracker.value); }
    @Test void removedEntityNeverReceivesRestore() { Rig r=new Rig(); var l=r.open(64); r.host.valid=false; l.close(); assertEquals(64,r.host.tracker.value); }
    @Test void initialMissingTrackerWaitsWithoutWrite() { Rig r=new Rig(); r.host.tracker=null; var l=r.open(64); assertEquals(NativeTrackingRangeNMS.State.WAITING,l.state()); assertFalse(l.current()); assertEquals(0,r.writes); }
    @Test void trackerAt199TicksCanBecomeActive() { Rig r=new Rig(); r.host.tracker=null; var l=r.open(64); r.tick=199;r.host.tracker=new Tracker(32);assertEquals(NativeTrackingRangeNMS.State.ACTIVE,l.refresh()); }
    @Test void trackerAtTimeoutDoesNotResurrect() { Rig r=new Rig(); r.host.tracker=null; var l=r.open(64); r.tick=200;r.host.tracker=new Tracker(32);assertEquals(NativeTrackingRangeNMS.State.LOST,l.refresh());assertEquals(32,r.host.tracker.value); }
    @Test void trackerGapDoesNotWriteDetachedPredecessor() { Rig r=new Rig(); var l=r.open(64); Tracker old=r.host.tracker;r.host.tracker=null;r.tick++;l.refresh();l.close();assertEquals(64,old.value); }
    @Test void canonicalReplacementCreatesNewEpochAndNewRestoreBase() { Rig r=new Rig();r.host.tracker.value=81;var l=r.open(64);Tracker old=r.host.tracker;r.host.tracker=new Tracker(32);r.tick++;l.refresh();assertEquals(2,l.epoch());l.close();assertEquals(32,r.host.tracker.value);assertEquals(64,old.value); }
    @Test void foreignReplacementIsNotClaimed() { Rig r=new Rig();var l=r.open(64);Tracker old=r.host.tracker;r.host.tracker=new Tracker(77);r.tick++;assertEquals(NativeTrackingRangeNMS.State.LOST,l.refresh());l.close();assertEquals(77,r.host.tracker.value);assertEquals(64,old.value); }
    @Test void worldAndNativeIdentityChangeRequireNewCanonicalTracker() { Rig r=new Rig();var l=r.open(64);Tracker old=r.host.tracker;r.host.world=UUID.randomUUID();r.host.nativeEntity=new Object();r.host.entityId++;r.host.tracker=new Tracker(32);r.tick++;assertEquals(NativeTrackingRangeNMS.State.ACTIVE,l.refresh());l.close();assertEquals(64,old.value);assertEquals(32,r.host.tracker.value); }
    @Test void currentQueryDoesNotTransferWorldOwnership() { Rig r=new Rig();var l=r.open(64);r.host.world=UUID.randomUUID();assertFalse(l.current());assertEquals(1,r.writes); }
    @Test void clockRegressionCannotResetStartBudget() { Rig r=new Rig();r.tick=5;r.open(64);r.tick=4;assertThrows(IllegalStateException.class,()->r.controls.open(new Host(),64)); }
    @Test void invalidAttemptsConsumeStartBudget() { Rig r=new Rig(2,2,200);assertThrows(IllegalArgumentException.class,()->r.open(-2));assertThrows(IllegalArgumentException.class,()->r.open(-1));assertThrows(IllegalStateException.class,()->r.open(64));r.tick++;assertTrue(r.open(64).current()); }
    @Test void capacityIsCheckedBeforeSecondHostInspection() { Rig r=new Rig(1,32,200);r.open(64);int n=r.identities;assertThrows(IllegalStateException.class,()->r.controls.open(new Host(),64));assertEquals(n,r.identities); }
    @Test void oneUuidCannotHaveTwoOwners() { Rig r=new Rig();r.open(64);assertThrows(IllegalStateException.class,()->r.open(96));assertEquals(64,r.host.tracker.value); }
    @Test void unconfirmedNativeWriteFailsWithoutAlternativePackets() { Rig r=new Rig();r.ignoreWrite=true;var l=r.open(64);assertEquals(NativeTrackingRangeNMS.State.LOST,l.state());assertEquals(32,r.host.tracker.value);assertEquals(0,r.controls.size()); }
    @Test void writeThenThrowRestoresConfirmedOwnPostimage() { Rig r=new Rig();r.throwAfter=true;assertThrows(IllegalStateException.class,()->r.open(64));assertEquals(32,r.host.tracker.value);assertEquals(0,r.controls.size()); }
    @Test void restoreFailureRetainsExactReceiptForRetry() { Rig r=new Rig();var l=r.open(64);r.throwBefore=true;assertThrows(IllegalStateException.class,l::close);assertEquals(NativeTrackingRangeNMS.State.CLOSING,l.state());assertEquals(1,r.controls.size());l.close();assertEquals(32,r.host.tracker.value);assertEquals(0,r.controls.size()); }
    @Test void reentrantShutdownDuringFirstWriteCannotLeakRange() { Rig r=new Rig();r.duringWrite=r.controls::close;var l=r.open(64);assertEquals(NativeTrackingRangeNMS.State.CLOSED,l.state());assertEquals(32,r.host.tracker.value);assertEquals(0,r.controls.size()); }
    @Test void changeReceiptRestoresPriorSettingNotInitialRange() { Rig r=new Rig();var l=r.open(64);var c=l.change(96);assertEquals(96,r.host.tracker.value);assertTrue(c.rollback());assertEquals(64,r.host.tracker.value);l.close();assertEquals(32,r.host.tracker.value); }
    @Test void receiptCannotUndoLaterChange() { Rig r=new Rig();var l=r.open(64);var c=l.change(96);l.change(128);assertFalse(c.rollback());assertEquals(128,r.host.tracker.value); }
    @Test void sameNumericLaterChangeStillInvalidatesOldReceipt() { Rig r=new Rig();var l=r.open(64);var c=l.change(96);l.change(128);l.change(96);assertFalse(c.rollback());assertEquals(96,r.host.tracker.value); }
    @Test void changeReceiptCannotCrossTrackerEpoch() { Rig r=new Rig();var l=r.open(64);var c=l.change(96);r.host.tracker=new Tracker(32);r.tick++;l.refresh();assertFalse(c.rollback());assertEquals(96,r.host.tracker.value); }
    @Test void waitingLeaseChangesOnlyStoredIntent() { Rig r=new Rig();r.host.tracker=null;var l=r.open(64);l.change(96);assertEquals(96,l.requestedBlocks());assertFalse(l.current());assertEquals(0,r.writes);assertEquals(0,l.epoch()); }
    @Test void changeWriteThenThrowRestoresPreviousSetting() { Rig r=new Rig();var l=r.open(64);r.throwAfter=true;assertThrows(IllegalStateException.class,()->l.change(96));assertEquals(64,r.host.tracker.value);assertTrue(l.current()); }
    @Test void shutdownRetainsAndRetriesFailedCleanup() { Rig r=new Rig();r.open(64);r.throwBefore=true;assertThrows(IllegalStateException.class,r.controls::close);assertEquals(1,r.controls.size());r.controls.close();assertEquals(32,r.host.tracker.value);assertThrows(IllegalStateException.class,()->r.open(96)); }
    @Test void ownerThreadRequiredEvenForObservation() { Rig r=new Rig();var l=r.open(64);r.owner=false;assertThrows(IllegalStateException.class,l::current);assertThrows(IllegalStateException.class,l::close); }
    @Test void deniedChangeWhichDidNotWriteKeepsOriginalReceipt() { Rig r=new Rig();var l=r.open(64);r.ignoreWrite=true;assertThrows(IllegalStateException.class,()->l.change(96));assertTrue(l.current());r.ignoreWrite=false;l.close();assertEquals(32,r.host.tracker.value); }
    @Test void rollbackWriteThenThrowDoesNotLoseInitialRestore() { Rig r=new Rig();var l=r.open(64);var c=l.change(96);r.throwAfter=true;assertThrows(IllegalStateException.class,c::rollback);assertEquals(64,r.host.tracker.value);assertTrue(l.current());l.close();assertEquals(32,r.host.tracker.value); }
    @Test void rollbackDeniedWithoutWriteCanRetry() { Rig r=new Rig();var l=r.open(64);var c=l.change(96);r.ignoreWrite=true;assertThrows(IllegalStateException.class,c::rollback);assertTrue(l.current());r.ignoreWrite=false;assertTrue(c.rollback());assertEquals(64,r.host.tracker.value); }
    @Test void reentrantCloseDuringChangeRestoresFirstOriginal() { Rig r=new Rig();var l=r.open(64);r.duringWrite=l::close;var c=l.change(96);assertEquals(NativeTrackingRangeNMS.State.CLOSED,l.state());assertEquals(32,r.host.tracker.value);assertFalse(c.rollback()); }
    @Test void reentrantCloseDuringRollbackRestoresFirstOriginal() { Rig r=new Rig();var l=r.open(64);var c=l.change(96);r.duringWrite=l::close;assertTrue(c.rollback());assertEquals(NativeTrackingRangeNMS.State.CLOSED,l.state());assertEquals(32,r.host.tracker.value); }
    @Test void writeCallbackCannotStartNestedChange() { Rig r=new Rig();var l=r.open(64);r.duringWrite=()->assertThrows(IllegalStateException.class,()->l.change(128));l.change(96);assertEquals(96,r.host.tracker.value); }
}
