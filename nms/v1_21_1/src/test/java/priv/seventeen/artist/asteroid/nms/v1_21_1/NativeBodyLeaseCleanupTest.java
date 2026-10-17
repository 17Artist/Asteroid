package priv.seventeen.artist.asteroid.nms.v1_21_1;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import org.bukkit.Bukkit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import priv.seventeen.artist.asteroid.entity.NativePoseNMS;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Runs the real mapped Control. Field replacement below is explicit read/restore fault injection only. */
class NativeBodyLeaseCleanupTest {
    @BeforeAll static void registries(){SharedConstants.tryDetectVersion();Bootstrap.bootStrap();}
    static final class Rig implements AutoCloseable {
        final MockedStatic<Bukkit> bukkit=mockStatic(Bukkit.class);
        final Mob mob;final BodyRotationControl previous;
        final AtomicReference<Throwable> failure=new AtomicReference<>();final Field slot;
        Rig()throws Exception{
            try{
            mob=mock(Mob.class);previous=mock(BodyRotationControl.class);
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);when(mob.isAlive()).thenReturn(true);
            when(mob.getMaxHeadYRot()).thenReturn(75);mob.yHeadRot=12;mob.yBodyRot=34;
            slot=Arrays.stream(Mob.class.getDeclaredFields()).filter(f->f.getType()==BodyRotationControl.class).findFirst().orElseThrow();
            slot.setAccessible(true);slot.set(mob,previous);
            }catch(Exception|Error failure){bukkit.close();throw failure;}
        }
        EntityPoseBridge.Control open(Consumer<NativePoseNMS.Pose> callback){return new EntityPoseBridge.Control(mob,callback,failure::set);}
        BodyRotationControl current()throws Exception{return (BodyRotationControl)slot.get(mob);}
        void slot(BodyRotationControl value)throws Exception{slot.set(mob,value);}
        void inject(EntityPoseBridge.Control control,Field value)throws Exception{
            var field=EntityPoseBridge.Control.class.getDeclaredField("field");field.setAccessible(true);value.setAccessible(true);field.set(control,value);
        }
        @Override public void close(){bukkit.close();}
    }
    static final class Alien {BodyRotationControl value;}
    static BodyRotationControl writeFailureValue;
    static final class FinalSlot {static final BodyRotationControl VALUE=writeFailureValue;}
    static final Error readError=new AssertionError("read failed before restore");
    static BodyRotationControl failRead(){throw readError;}
    static final class ErrorSlot {static final BodyRotationControl VALUE=failRead();}
    static boolean contains(Throwable root,Throwable wanted){
        if(root==wanted)return true;if(root==null)return false;
        if(root.getCause()!=root&&contains(root.getCause(),wanted))return true;
        for(var next:root.getSuppressed())if(contains(next,wanted))return true;return false;
    }
    @Test void closeRestoresExactlyAndIsIdempotent()throws Exception{try(var r=new Rig()){
        var lease=r.open(p->{});assertTrue(lease.active());assertSame(lease,r.current());lease.close();lease.close();
        assertFalse(lease.active());assertSame(r.previous,r.current());
    }}
    @Test void failedReadRevokesImmediatelyButRetryStillRestores()throws Exception{try(var r=new Rig()){
        var calls=new AtomicInteger();var lease=r.open(p->calls.incrementAndGet());r.inject(lease,Alien.class.getDeclaredField("value"));
        assertThrows(IllegalArgumentException.class,lease::close);assertFalse(lease.active());
        r.inject(lease,r.slot);lease.clientTick();assertEquals(0,calls.get());lease.close();
        assertSame(r.previous,r.current());assertFalse(lease.active());
    }}
    @Test void failedActualFieldWriteRetainsReceiptUntilRetry()throws Exception{try(var r=new Rig()){
        var lease=r.open(p->{});writeFailureValue=lease;r.inject(lease,FinalSlot.class.getDeclaredField("VALUE"));
        assertThrows(IllegalStateException.class,lease::close);assertFalse(lease.active());
        r.inject(lease,r.slot);assertSame(lease,r.current());lease.close();assertSame(r.previous,r.current());
    }}
    @Test void oldCloseCannotClobberForeignOrLaterLease()throws Exception{try(var r=new Rig()){
        var old=r.open(p->{});var foreign=mock(BodyRotationControl.class);r.slot(foreign);old.close();assertSame(foreign,r.current());
        var next=r.open(p->{});old.close();assertSame(next,r.current());next.close();assertSame(foreign,r.current());
    }}
    @Test void readErrorDoesNotMarkCleanupAsReleased()throws Exception{try(var r=new Rig()){
        var lease=r.open(p->{});r.inject(lease,ErrorSlot.class.getDeclaredField("VALUE"));
        assertSame(readError,assertThrows(Error.class,lease::close));assertFalse(lease.active());
        r.inject(lease,r.slot);lease.close();assertSame(r.previous,r.current());
    }}
    @Test void directOwnedNestingIsRejectedBeforeInstallation()throws Exception{try(var r=new Rig()){
        var old=r.open(p->{});assertThrows(IllegalStateException.class,()->r.open(p->{}));assertSame(old,r.current());old.close();assertSame(r.previous,r.current());
    }}
    @Test void callbackClosingItselfCannotPublishStaleHeadOrBody()throws Exception{try(var r=new Rig()){
        var box=new AtomicReference<EntityPoseBridge.Control>();var lease=r.open(p->{p.headYaw=101;p.bodyYaw=202;box.get().close();});box.set(lease);
        lease.clientTick();assertEquals(12,r.mob.yHeadRot);assertEquals(34,r.mob.yBodyRot);assertSame(r.previous,r.current());
    }}
    @Test void callbackCloseThenAcquireDoesNotGiveOldResultToSuccessor()throws Exception{try(var r=new Rig()){
        var box=new AtomicReference<EntityPoseBridge.Control>();var next=new AtomicReference<EntityPoseBridge.Control>();
        var lease=r.open(p->{p.headYaw=101;p.bodyYaw=202;box.get().close();next.set(r.open(q->{}));});box.set(lease);
        lease.clientTick();assertEquals(12,r.mob.yHeadRot);assertEquals(34,r.mob.yBodyRot);assertSame(next.get(),r.current());next.get().close();
    }}
    @Test void callbackForeignReplacementCannotPublishOldResult()throws Exception{try(var r=new Rig()){
        var foreign=mock(BodyRotationControl.class);var lease=r.open(p->{p.headYaw=101;p.bodyYaw=202;try{r.slot(foreign);}catch(Exception e){throw new AssertionError(e);}});
        lease.clientTick();assertEquals(12,r.mob.yHeadRot);assertEquals(34,r.mob.yBodyRot);assertSame(foreign,r.current());lease.close();
    }}
    @Test void callbackCloseAcquireThenThrowDoesNotRunPreviousOverSuccessor()throws Exception{try(var r=new Rig()){
        var old=new AtomicReference<EntityPoseBridge.Control>();var next=new AtomicReference<EntityPoseBridge.Control>();var error=new IllegalArgumentException("after replacement");
        var lease=r.open(p->{old.get().close();next.set(r.open(q->{}));throw error;});old.set(lease);lease.clientTick();
        assertSame(error,r.failure.get());assertSame(next.get(),r.current());verifyNoInteractions(r.previous);next.get().close();
    }}
    @Test void savedWrapperCalledAfterForeignReplacementDoesNotRunConsumer()throws Exception{try(var r=new Rig()){
        var calls=new AtomicInteger();var lease=r.open(p->calls.incrementAndGet());var foreign=mock(BodyRotationControl.class);r.slot(foreign);
        lease.clientTick();assertEquals(0,calls.get());assertSame(foreign,r.current());verifyNoInteractions(r.previous);lease.close();
    }}
    @Test void revokedSavedWrapperDoesNotAdvanceVanillaAfterNewLease()throws Exception{try(var r=new Rig()){
        var old=r.open(p->{});old.close();var next=r.open(p->{});old.clientTick();verifyNoInteractions(r.previous);assertSame(next,r.current());next.close();
    }}
    @Test void callbackFailureAndCleanupFailureBothReachReporterAndCanRetry()throws Exception{try(var r=new Rig()){
        var error=new IllegalArgumentException("original");var box=new AtomicReference<EntityPoseBridge.Control>();
        var lease=r.open(p->{try{r.inject(box.get(),Alien.class.getDeclaredField("value"));}catch(Exception e){throw new AssertionError(e);}throw error;});box.set(lease);
        lease.clientTick();assertNotNull(r.failure.get());assertTrue(contains(r.failure.get(),error));assertFalse(lease.active());
        r.inject(lease,r.slot);lease.close();assertSame(r.previous,r.current());
    }}
    @Test void controllerErrorKeepsItsCategoryAndRestores()throws Exception{try(var r=new Rig()){
        var error=new AssertionError("callback");var lease=r.open(p->{throw error;});
        assertSame(error,assertThrows(AssertionError.class,lease::clientTick));assertSame(error,r.failure.get());assertSame(r.previous,r.current());
    }}
    @Test void suppressionDisabledErrorAndFallbackFailureAreNotLost()throws Exception{try(var r=new Rig()){
        var error=new Error("no suppression",null,false,false){};var fallback=new IllegalArgumentException("fallback");
        doThrow(fallback).when(r.previous).clientTick();var lease=r.open(p->{throw error;});
        var thrown=assertThrows(Error.class,lease::clientTick);assertTrue(contains(thrown,error));assertTrue(contains(thrown,fallback));assertSame(thrown,r.failure.get());
    }}
    @Test void wrongThreadCloseDoesNotRevokeAnOwnedController()throws Exception{try(var r=new Rig()){
        var lease=r.open(p->{});r.bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);assertThrows(IllegalStateException.class,lease::close);
        r.bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);assertTrue(lease.active());lease.close();assertSame(r.previous,r.current());
    }}
    @Test void deadMobNeverInvokesConsumer()throws Exception{try(var r=new Rig()){
        var calls=new AtomicInteger();var lease=r.open(p->calls.incrementAndGet());when(r.mob.isAlive()).thenReturn(false);
        lease.clientTick();assertEquals(0,calls.get());assertEquals(12,r.mob.yHeadRot);lease.close();
    }}
}
