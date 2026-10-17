package priv.seventeen.artist.asteroid.internal.movement;

import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import priv.seventeen.artist.asteroid.internal.navigation.NativeNavigationLease;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static priv.seventeen.artist.asteroid.internal.movement.NativeMotionCleanupTest.*;

/** The real shared leases and real event object, with a fixed baked listener list. No Paper run. */
class NativeMotionDisableEventTest {
    static void deliver(Object lease,PluginDisableEvent event){
        if(lease instanceof NativeDriveLease drive)drive.disabled(event);
        else ((NativeNavigationLease)lease).disabled(event);
    }
    static List<Throwable> deliverBaked(List<Object> baked,PluginDisableEvent event){
        var failures=new ArrayList<Throwable>();
        for(var lease:baked)try{deliver(lease,event);}catch(RuntimeException|Error failure){failures.add(failure);}
        return failures;
    }
    @Test void mixedBakedArrayDoesOnlyLinearCleanupWhenAllNativeRestoresFail(){try(var r=new Rig()){
        var baked=new ArrayList<Object>();
        for(int i=0;i<16;i++){
            var port=new Port(r);baked.add(r.open(i%2==0?Kind.DRIVE:Kind.NAVIGATION,r.owner,r.entity(UUID.randomUUID()),port,null));
            port.restoreFailure=new NoSuppression("restore "+i);
        }
        List<Object> fixed=List.copyOf(baked);var event=new PluginDisableEvent(r.owner);
        var failures=deliverBaked(fixed,event);
        assertEquals(1,failures.size(),"one aggregate, not one full-registry retry per baked listener");
        for(var port:r.ports){assertEquals(1,port.restores);assertTrue(contains(failures.get(0),port.restoreFailure));}
        for(var lease:fixed){assertFalse(active(lease));assertEquals(1,r.unregisters.get((Listener)lease));}
        assertEquals(16,r.ports.stream().mapToInt(p->p.restores).sum(),"not 16 x 16 native attempts");
        assertTrue(deliverBaked(fixed,event).isEmpty());
        assertEquals(16,r.ports.stream().mapToInt(p->p.restores).sum());
    }}
    @ParameterizedTest @EnumSource(Kind.class) void sameEventNeverRetriesEitherFailedStage(Kind kind){try(var r=new Rig()){
        var port=new Port(r);var lease=r.open(kind,port);port.restoreFailure=new NoSuppression("restore");
        r.unregisterFailure=new NoSuppression("unregister");var event=new PluginDisableEvent(r.owner);
        assertEquals(1,deliverBaked(List.of(lease,lease,lease),event).size());
        assertEquals(1,port.restores);assertEquals(1,r.unregisters.get((Listener)lease));assertFalse(active(lease));
        assertDoesNotThrow(()->deliver(lease,event));assertEquals(1,port.restores);assertEquals(1,r.unregisters.get((Listener)lease));
    }}
    @ParameterizedTest @EnumSource(Kind.class) void anotherActualEventMayRetryExactlyOnce(Kind kind){try(var r=new Rig()){
        var port=new Port(r);var lease=r.open(kind,port);port.restoreFailure=new NoSuppression("restore");
        r.unregisterFailure=new NoSuppression("unregister");PluginDisableEvent first=new PluginDisableEvent(r.owner),second=new PluginDisableEvent(r.owner);
        assertEquals(1,deliverBaked(List.of(lease,lease),first).size());
        assertEquals(1,deliverBaked(List.of(lease,lease),second).size());
        assertEquals(2,port.restores);assertEquals(2,r.unregisters.get((Listener)lease));
    }}
    @ParameterizedTest @EnumSource(Kind.class) void explicitShutdownMayRetryWithoutANewEvent(Kind kind){try(var r=new Rig()){
        var port=new Port(r);var lease=r.open(kind,port);port.restoreFailure=new NoSuppression("restore");
        r.unregisterFailure=new NoSuppression("unregister");var event=new PluginDisableEvent(r.owner);
        assertEquals(1,deliverBaked(List.of(lease),event).size());
        assertThrows(RuntimeException.class,()->shutdown(r.owner));assertEquals(2,port.restores);assertEquals(2,r.unregisters.get((Listener)lease));
        assertDoesNotThrow(()->deliver(lease,event));assertEquals(2,port.restores);
        port.restoreFailure=null;r.unregisterFailure=null;shutdown(r.owner);
        assertEquals(3,port.restores);assertEquals(3,r.unregisters.get((Listener)lease));
        assertDoesNotThrow(()->deliver(lease,event));assertEquals(3,port.restores);
    }}
    @ParameterizedTest @EnumSource(Kind.class) void releasedBakedListenerCannotCloseSameUuidSuccessor(Kind kind){try(var r=new Rig()){
        var oldPort=new Port(r);var old=r.open(kind,oldPort);var event=new PluginDisableEvent(r.owner);deliver(old,event);
        var nextPort=new Port(r);var next=r.open(kind,r.owner,r.entity(r.entity.getUniqueId()),nextPort,null);
        deliver(old,event);assertTrue(active(next));assertEquals(0,nextPort.restores);assertEquals(1,oldPort.restores);
    }}
    @Test void allKindsAreMarkedAndRevokedBeforeAnyPortCallback(){try(var r=new Rig()){
        var drive=new Port(r);var nav=new Port(r);
        Object d=r.open(Kind.DRIVE,drive),n=r.open(Kind.NAVIGATION,r.owner,r.entity(UUID.randomUUID()),nav,null);
        var event=new PluginDisableEvent(r.owner);
        Runnable callback=()->{
            assertFalse(active(d));assertFalse(active(n));
            assertDoesNotThrow(()->deliver(d,event));assertDoesNotThrow(()->deliver(n,event));
            var rejected=new Port(r);
            assertThrows(IllegalStateException.class,()->r.open(Kind.DRIVE,r.owner,r.entity(UUID.randomUUID()),rejected,null));
            assertEquals(0,rejected.installs);
        };
        drive.restoring=callback;nav.restoring=callback;
        assertTrue(deliverBaked(List.of(d,n),event).isEmpty());assertEquals(1,drive.restores);assertEquals(1,nav.restores);
    }}
    @Test void singleEventAggregateRetainsErrorAndSuppressionDisabledCauses(){try(var r=new Rig()){
        var drive=new Port(r);var nav=new Port(r);Object d=r.open(Kind.DRIVE,drive),n=r.open(Kind.NAVIGATION,r.owner,r.entity(UUID.randomUUID()),nav,null);
        var error=new AssertionError("native drive");var other=new NoSuppression("native nav");var listener=new NoSuppression("listener");
        drive.restoreFailure=error;nav.restoreFailure=other;r.unregisterFailure=listener;
        var failures=deliverBaked(List.of(d,n),new PluginDisableEvent(r.owner));assertEquals(1,failures.size());
        assertInstanceOf(Error.class,failures.get(0));for(var cause:List.of(error,other,listener))assertTrue(contains(failures.get(0),cause));
        assertEquals(0,error.getSuppressed().length);assertEquals(0,other.getSuppressed().length);assertEquals(0,listener.getSuppressed().length);
        assertEquals(1,drive.restores);assertEquals(1,nav.restores);assertEquals(1,r.unregisters.get((Listener)d));assertEquals(1,r.unregisters.get((Listener)n));
    }}
    @ParameterizedTest @EnumSource(Kind.class) void explicitFailureDoesNotConsumeFutureDisablePass(Kind kind){try(var r=new Rig()){
        var port=new Port(r);var lease=r.open(kind,port);port.restoreFailure=new NoSuppression("restore");
        assertThrows(NoSuppression.class,()->shutdown(r.owner));assertEquals(1,port.restores);
        var event=new PluginDisableEvent(r.owner);assertEquals(1,deliverBaked(List.of(lease,lease),event).size());assertEquals(2,port.restores);
        assertEquals(1,r.unregisters.get((Listener)lease),"successful unregistration is not repeated");
    }}
    @Test void anotherOwnersEventDoesNotCloseOrMarkOurReceipt(){try(var r=new Rig()){
        var mine=new Port(r);var foreign=new Port(r);Object a=r.open(Kind.DRIVE,mine);
        var other=r.plugin();Object b=r.open(Kind.NAVIGATION,other,r.entity(UUID.randomUUID()),foreign,null);
        var otherEvent=new PluginDisableEvent(other);deliver(a,otherEvent);assertEquals(0,mine.restores);assertTrue(active(a));
        deliver(b,otherEvent);assertEquals(1,foreign.restores);assertTrue(active(a));
        deliver(a,new PluginDisableEvent(r.owner));assertEquals(1,mine.restores);assertEquals(1,foreign.restores);
    }}
    @ParameterizedTest @EnumSource(Kind.class) void exactReceiptRetryStillWorksAfterDisableError(Kind kind){try(var r=new Rig()){
        var port=new Port(r);var lease=r.open(kind,port);port.restoreFailure=new AssertionError("restore");
        var event=new PluginDisableEvent(r.owner);assertEquals(1,deliverBaked(List.of(lease,lease),event).size());
        port.restoreFailure=null;close(lease);assertEquals(2,port.restores);assertEquals(1,r.unregisters.get((Listener)lease));
        assertDoesNotThrow(()->deliver(lease,event));assertEquals(2,port.restores);
    }}
}
