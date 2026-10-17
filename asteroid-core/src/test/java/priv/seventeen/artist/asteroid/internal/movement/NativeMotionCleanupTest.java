package priv.seventeen.artist.asteroid.internal.movement;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;
import priv.seventeen.artist.asteroid.entity.NativeDriveProgram;
import priv.seventeen.artist.asteroid.entity.NativeMovementNMS;
import priv.seventeen.artist.asteroid.entity.NativeNavigationNMS;
import priv.seventeen.artist.asteroid.internal.navigation.NativeNavigationLease;
import priv.seventeen.artist.asteroid.util.PaperCompat;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real shared lease classes; recording ports are NOT native/Paper physics evidence. */
class NativeMotionCleanupTest {
    enum Kind { DRIVE, NAVIGATION }
    static void raise(Throwable failure) { if(failure instanceof Error e)throw e;if(failure instanceof RuntimeException e)throw e;if(failure!=null)throw new AssertionError(failure); }
    static Object invoke(Class<?> type,String method,Class<?>[] signature,Object... args) {
        try{return type.getMethod(method,signature).invoke(null,args);}
        catch(InvocationTargetException e){raise(e.getCause());throw new AssertionError(e);}
        catch(ReflectiveOperationException e){throw new AssertionError("Missing retained native cleanup contract: "+method,e);}
    }
    static void shutdown(Plugin plugin) {
        try{invoke(Class.forName("priv.seventeen.artist.asteroid.internal.movement.NativeMotionCleanup"),"closeAll",new Class<?>[]{Plugin.class},plugin);}
        catch(ClassNotFoundException e){throw new AssertionError("Missing native cleanup registry",e);}
    }
    static void close(Object value){if(value instanceof NativeMovementNMS.DriveLease d)d.close();else ((NativeNavigationNMS.NavigationLease)value).close();}
    static boolean active(Object value){return value instanceof NativeMovementNMS.DriveLease d?d.active():((NativeNavigationNMS.NavigationLease)value).active();}
    static boolean contains(Throwable root,Throwable expected){
        var seen=Collections.newSetFromMap(new IdentityHashMap<Throwable,Boolean>());var pending=new ArrayDeque<Throwable>();pending.add(root);
        while(!pending.isEmpty()){var item=pending.removeFirst();if(item==expected)return true;if(!seen.add(item))continue;
            assertTrue(seen.size()<=2048,"bounded failure tree");if(item.getCause()!=null)pending.add(item.getCause());for(var s:item.getSuppressed())pending.add(s);}
        return false;
    }
    static final class NoSuppression extends RuntimeException { NoSuppression(String text){super(text,null,false,true);} }
    static final class Port implements NativeDriveLease.ProgramPort,NativeNavigationLease.Port {
        final Rig rig;Object lease;long tick;int installs,restores,writes;boolean owned;
        Throwable installFailure,restoreFailure;Runnable installing,restoring,checking,ticking;
        Port(Rig rig){this.rig=rig;tick=rig.tick;rig.ports.add(this);}
        void installValue(Object value){lease=value;rig.track(value);installs++;owned=true;if(installing!=null)installing.run();raise(installFailure);}
        public void install(NativeDriveLease value){installValue(value);}
        public void install(NativeNavigationLease value){installValue(value);}
        public boolean owned(){if(checking!=null)checking.run();return owned;}
        public long tick(){if(ticking!=null)ticking.run();return tick;}
        public void restore(){restores++;if(restoring!=null)restoring.run();raise(restoreFailure);owned=false;}
        public NativeMovementNMS.DriveContext context(){return new NativeMovementNMS.DriveContext(tick,true,false);}
        public NativeDriveProgram.Sample sample(NativeMovementNMS.DriveContext c){return new NativeDriveProgram.Sample(c.nativeTick(),true,false,.2f,0,0,0);}
        public void move(NativeMovementNMS.DriveFrame f){writes++;}public void look(NativeMovementNMS.DriveFrame f){writes++;}
        public void neutral(){}public void defaultMovement(){}public void clearHorizontalInput(){}public void groundedTravel(boolean value){}
        public void suppressWaterJump(){}public void input(NativeMovementNMS.Input value){writes++;}public void jump(){writes++;}
        public void resetFallDistance(){}public void velocity(double x,double y,double z){writes++;}public void defaultDirection(){}
        public Object query(NativeNavigationNMS.Point point){return null;}public boolean reachable(Object path){return false;}
        public List<NativeNavigationNMS.Point> points(Object path,boolean remaining){return List.of();}
        public Object currentPath(){return null;}public boolean commit(Object path,double speed){writes++;return true;}public void stop(){}
    }
    static final class Rig implements AutoCloseable {
        final MockedStatic<Bukkit> bukkit=mockStatic(Bukkit.class);final MockedStatic<PaperCompat> paper=mockStatic(PaperCompat.class);
        final MockedStatic<HandlerList> handlers=mockStatic(HandlerList.class);
        final Plugin owner=plugin();final PluginManager manager=mock(PluginManager.class);final World world=mock(World.class);
        final List<Port> ports=new ArrayList<>();final List<Object> receipts=new ArrayList<>();final List<Throwable> failures=new ArrayList<>();
        final Map<Listener,Integer> unregisters=new IdentityHashMap<>();final Map<String,Object> oldBudget=new LinkedHashMap<>();
        long tick;final Mob entity;Throwable registrationFailure,unregisterFailure;Runnable registering,unregistering;
        Rig(){
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);bukkit.when(Bukkit::getPluginManager).thenReturn(manager);
            when(world.getUID()).thenReturn(UUID.randomUUID());entity=entity(UUID.randomUUID());
            try{for(String name:List.of("budgetTick","queries","reads","opens","depth")){var f=NativeNavigationLease.class.getDeclaredField(name);f.setAccessible(true);oldBudget.put(name,f.get(null));}
                tick=Math.max(0,(Long)oldBudget.get("budgetTick"))+1000;
            }catch(ReflectiveOperationException e){throw new AssertionError(e);}
            doAnswer(c->{if(registering!=null)registering.run();raise(registrationFailure);return null;}).when(manager).registerEvents(any(Listener.class),any(Plugin.class));
            handlers.when(()->HandlerList.unregisterAll(any(Listener.class))).thenAnswer(c->{var l=(Listener)c.getArgument(0);unregisters.merge(l,1,Integer::sum);
                if(unregistering!=null)unregistering.run();raise(unregisterFailure);return null;});
        }
        Plugin plugin(){var p=mock(Plugin.class);when(p.isEnabled()).thenReturn(true);return p;}
        Mob entity(UUID id){var e=mock(Mob.class);when(e.getUniqueId()).thenReturn(id);when(e.getWorld()).thenReturn(world);
            when(e.getEntityId()).thenReturn(7);when(e.isValid()).thenReturn(true);when(e.hasAI()).thenReturn(true);return e;}
        void track(Object lease){if(receipts.stream().noneMatch(l->l==lease))receipts.add(lease);}
        Object open(Kind kind,Port p){return open(kind,owner,entity,p,null);}
        Object retained(Kind kind,Port p,Consumer<Object> consumer){return open(kind,owner,entity,p,consumer);}
        Object open(Kind kind,Plugin plugin,Mob host,Port p,Consumer<Object> consumer){
            Consumer<Object> retained=consumer==null?null:value->{track(value);consumer.accept(value);};
            Object lease;
            if(kind==Kind.DRIVE){NativeDriveProgram program=phase->phase.velocity(.1,0,0);
                lease=retained==null?NativeDriveLease.openProgram(plugin,host,p,program,failures::add)
                    :invoke(NativeDriveLease.class,"openProgramRetained",new Class<?>[]{Plugin.class,Entity.class,NativeDriveLease.ProgramPort.class,NativeDriveProgram.class,Consumer.class,Consumer.class},plugin,host,p,program,(Consumer<Throwable>)failures::add,retained);
            }else{lease=retained==null?NativeNavigationLease.open(plugin,host,p)
                    :invoke(NativeNavigationLease.class,"openRetained",new Class<?>[]{Plugin.class,Mob.class,long.class,Supplier.class,Consumer.class},plugin,host,p.tick,(Supplier<NativeNavigationLease.Port>)()->p,retained);}
            track(lease);return lease;
        }
        @Override public void close(){
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);paper.when(PaperCompat::isFolia).thenReturn(false);
            unregisterFailure=null;unregistering=null;registrationFailure=null;registering=null;
            for(var p:ports){p.restoreFailure=null;p.restoring=null;p.checking=null;p.ticking=null;}
            Throwable failed=null;
            try{for(var receipt:List.copyOf(receipts))try{NativeMotionCleanupTest.close(receipt);}catch(RuntimeException|Error e){if(failed==null)failed=e;}}
            finally{try{for(var entry:oldBudget.entrySet()){var f=NativeNavigationLease.class.getDeclaredField(entry.getKey());f.setAccessible(true);f.set(null,entry.getValue());}}
                catch(ReflectiveOperationException e){throw new AssertionError(e);}
                finally{handlers.close();paper.close();bukkit.close();}}
            raise(failed);
        }
    }

    @ParameterizedTest @EnumSource(Kind.class) void restoreFailureRevokesButExactRetryRemainsRequired(Kind kind){try(var r=new Rig()){
        var p=new Port(r);var lease=r.open(kind,p);var error=new IllegalStateException("restore");p.restoreFailure=error;
        assertSame(error,assertThrows(IllegalStateException.class,()->close(lease)));assertFalse(active(lease));assertEquals(1,p.restores);
        var blocked=new Port(r);assertThrows(IllegalStateException.class,()->r.open(kind,blocked));assertEquals(0,blocked.installs);
        p.restoreFailure=null;close(lease);assertEquals(2,p.restores);assertEquals(1,r.unregisters.get((Listener)lease));
        var nextPort=new Port(r);var next=r.open(kind,nextPort);close(lease);assertTrue(active(next));assertEquals(0,nextPort.restores);
    }}
    @ParameterizedTest @EnumSource(Kind.class) void unregisterFailureDoesNotRepeatSuccessfulNativeRestore(Kind kind){try(var r=new Rig()){
        var p=new Port(r);var lease=r.open(kind,p);var error=new IllegalStateException("unregister");r.unregisterFailure=error;
        assertSame(error,assertThrows(IllegalStateException.class,()->close(lease)));assertEquals(1,p.restores);assertFalse(active(lease));
        assertThrows(IllegalStateException.class,()->r.open(kind,new Port(r)));
        r.unregisterFailure=null;close(lease);assertEquals(1,p.restores);assertEquals(2,r.unregisters.get((Listener)lease));close(lease);assertEquals(2,r.unregisters.get((Listener)lease));
    }}
    @ParameterizedTest @EnumSource(Kind.class) void restoreErrorAndUnregisterRuntimeBothSurviveWithoutMutatingOriginals(Kind kind){try(var r=new Rig()){
        var p=new Port(r);var lease=r.open(kind,p);var first=new AssertionError("native");var second=new NoSuppression("listener");
        p.restoreFailure=first;r.unregisterFailure=second;var thrown=assertThrows(Error.class,()->close(lease));
        assertTrue(contains(thrown,first));assertTrue(contains(thrown,second));assertEquals(0,first.getSuppressed().length);assertEquals(0,second.getSuppressed().length);
        assertEquals(1,r.unregisters.get((Listener)lease));p.restoreFailure=null;r.unregisterFailure=null;close(lease);assertEquals(2,p.restores);
    }}
    @ParameterizedTest @EnumSource(Kind.class) void sameFailureObjectPreservedWithoutSelfSuppression(Kind kind){try(var r=new Rig()){
        var p=new Port(r);var lease=r.open(kind,p);var shared=new NoSuppression("same");p.restoreFailure=shared;r.unregisterFailure=shared;
        assertSame(shared,assertThrows(NoSuppression.class,()->close(lease)));assertEquals(0,shared.getSuppressed().length);assertEquals(1,r.unregisters.get((Listener)lease));
    }}
    @ParameterizedTest @EnumSource(Kind.class) void partialInstallFailureRetainsCleanupRatherThanDrivingOrReacquiring(Kind kind){try(var r=new Rig()){
        var p=new Port(r);p.installFailure=new NoSuppression("install");p.restoreFailure=new AssertionError("rollback");
        var failed=assertThrows(Error.class,()->r.open(kind,p));assertTrue(contains(failed,p.installFailure));assertTrue(contains(failed,p.restoreFailure));
        assertNotNull(p.lease);assertFalse(active(p.lease));assertThrows(IllegalStateException.class,()->r.open(kind,new Port(r)));
        if(p.lease instanceof NativeDriveLease d){d.movementTick();d.directionTick();assertEquals(0,p.writes);}
        p.restoreFailure=null;close(p.lease);assertEquals(2,p.restores);assertTrue(active(r.open(kind,new Port(r))));
    }}
    @ParameterizedTest @EnumSource(Kind.class) void partialListenerRegistrationAlwaysHasExactUnregisterResponsibility(Kind kind){try(var r=new Rig()){
        var p=new Port(r);r.registrationFailure=new NoSuppression("partially registered");r.unregisterFailure=new AssertionError("listener cleanup");
        var thrown=assertThrows(Error.class,()->r.open(kind,p));assertTrue(contains(thrown,r.registrationFailure));assertTrue(contains(thrown,r.unregisterFailure));
        assertEquals(1,p.restores);assertEquals(1,r.unregisters.get((Listener)p.lease));assertFalse(active(p.lease));
        r.unregisterFailure=null;r.registrationFailure=null;close(p.lease);assertEquals(1,p.restores);assertEquals(2,r.unregisters.get((Listener)p.lease));
    }}
    @ParameterizedTest @EnumSource(Kind.class) void retainedReceiptPrecedesEveryNativeInstall(Kind kind){try(var r=new Rig()){
        var p=new Port(r);var seen=new ArrayList<Object>();var lease=r.retained(kind,p,value->{assertEquals(0,p.installs);seen.add(value);});
        assertEquals(List.of(lease),seen);assertSame(lease,p.lease);assertTrue(active(lease));assertEquals(1,p.installs);
    }}
    @ParameterizedTest @EnumSource(Kind.class) void closeInsideRetainerCannotInstallAfterCallbackEvenWhenExceptionCaught(Kind kind){try(var r=new Rig()){
        var p=new Port(r);var captured=new ArrayList<Object>();
        assertThrows(IllegalStateException.class,()->r.retained(kind,p,value->{captured.add(value);assertThrows(IllegalStateException.class,()->close(value));}));
        assertEquals(0,p.installs);assertEquals(1,p.restores);assertFalse(active(captured.get(0)));close(captured.get(0));assertEquals(1,p.restores);
        assertTrue(active(r.open(kind,new Port(r))));
    }}
    @ParameterizedTest @EnumSource(Kind.class) void retainerThrowPreservesOriginalAndNeverInstalls(Kind kind){try(var r=new Rig()){
        var p=new Port(r);var error=new AssertionError("receiver");assertSame(error,assertThrows(AssertionError.class,()->r.retained(kind,p,value->{throw error;})));
        assertEquals(0,p.installs);assertEquals(1,p.restores);assertTrue(active(r.open(kind,new Port(r))));
    }}
    @ParameterizedTest @EnumSource(Kind.class) void recursiveSameEntityAcquireCannotReplaceRetainedReceipt(Kind kind){try(var r=new Rig()){
        var p=new Port(r);var nested=new Port(r);var lease=r.retained(kind,p,value->{assertThrows(IllegalStateException.class,()->r.open(kind,nested));});
        assertTrue(active(lease));assertEquals(0,nested.installs);assertEquals(1,p.installs);
    }}
    @ParameterizedTest @EnumSource(Kind.class) void ownerDisabledInRetainerNeverInstalls(Kind kind){try(var r=new Rig()){
        var p=new Port(r);assertThrows(IllegalStateException.class,()->r.retained(kind,p,value->when(r.owner.isEnabled()).thenReturn(false)));
        assertEquals(0,p.installs);assertEquals(1,p.restores);
    }}
    @ParameterizedTest @EnumSource(Kind.class) void hostWorldChangesInRetainerNeverInstalls(Kind kind){try(var r=new Rig()){
        var p=new Port(r);World other=mock(World.class);when(other.getUID()).thenReturn(UUID.randomUUID());
        assertThrows(IllegalStateException.class,()->r.retained(kind,p,value->when(r.entity.getWorld()).thenReturn(other)));
        assertEquals(0,p.installs);assertEquals(1,p.restores);
    }}
    @ParameterizedTest @EnumSource(Kind.class) void registrationCallbackCloseNeverReturnsAnActiveLease(Kind kind){try(var r=new Rig()){
        var p=new Port(r);r.registering=()->assertThrows(IllegalStateException.class,()->close(p.lease));
        assertThrows(IllegalStateException.class,()->r.open(kind,p));assertFalse(active(p.lease));assertEquals(1,p.restores);assertEquals(1,r.unregisters.get((Listener)p.lease));
    }}
    @ParameterizedTest @EnumSource(Kind.class) void installCallbackCloseDoesNotRegisterOrReturnAnActiveLease(Kind kind){try(var r=new Rig()){
        var p=new Port(r);p.installing=()->assertThrows(IllegalStateException.class,()->close(p.lease));
        assertThrows(IllegalStateException.class,()->r.open(kind,p));assertFalse(active(p.lease));assertEquals(1,p.restores);
        verify(r.manager,never()).registerEvents(any(Listener.class),any(Plugin.class));
    }}
    @ParameterizedTest @EnumSource(Kind.class) void provisionalLeaseCannotExecuteBeforeAdmissionUnwinds(Kind kind){try(var r=new Rig()){
        var p=new Port(r);p.installing=()->{if(kind==Kind.DRIVE)assertFalse(active(p.lease));else assertThrows(IllegalStateException.class,()->active(p.lease));};
        assertThrows(IllegalStateException.class,()->r.open(kind,p));assertEquals(0,p.writes);assertFalse(active(p.lease));
    }}
    @ParameterizedTest @EnumSource(Kind.class) void ownedCallbackRevocationCannotPassPostInstallValidation(Kind kind){try(var r=new Rig()){
        var p=new Port(r);p.checking=()->assertThrows(IllegalStateException.class,()->close(p.lease));
        assertThrows(IllegalStateException.class,()->r.open(kind,p));assertFalse(active(p.lease));assertEquals(1,p.restores);
    }}
    @ParameterizedTest @EnumSource(Kind.class) void liveGetterCallbackCannotRevokeThenInstall(Kind kind){try(var r=new Rig()){
        var p=new Port(r);assertThrows(IllegalStateException.class,()->r.retained(kind,p,value->{
            when(r.entity.isValid()).thenAnswer(c->{assertThrows(IllegalStateException.class,()->close(value));return true;});
        }));assertEquals(0,p.installs);assertEquals(1,p.restores);
    }}
    @ParameterizedTest @EnumSource(Kind.class) void reentrantCloseFromRestoreDoesNotDuplicateOrSkipUnregister(Kind kind){try(var r=new Rig()){
        var p=new Port(r);var lease=r.open(kind,p);p.restoring=()->assertThrows(IllegalStateException.class,()->close(lease));
        close(lease);assertEquals(1,p.restores);assertEquals(1,r.unregisters.get((Listener)lease));assertFalse(active(lease));
    }}
    @ParameterizedTest @EnumSource(Kind.class) void oldReceiptCannotCloseNewSameUuidEntity(Kind kind){try(var r=new Rig()){
        var p=new Port(r);var old=r.open(kind,p);close(old);Mob replacement=r.entity(r.entity.getUniqueId());var nextPort=new Port(r);
        var next=r.open(kind,r.owner,replacement,nextPort,null);close(old);assertTrue(active(next));assertEquals(0,nextPort.restores);assertEquals(1,p.restores);
    }}
    @ParameterizedTest @EnumSource(Kind.class) void failedLeaseOccupiesHardCapacityUntilActualRelease(Kind kind){try(var r=new Rig()){
        var receipts=new ArrayList<Object>();
        for(int i=0;i<256;i++){r.tick++;var p=new Port(r);var lease=r.open(kind,r.owner,r.entity(UUID.randomUUID()),p,null);receipts.add(lease);p.restoreFailure=new NoSuppression("retained "+i);assertThrows(NoSuppression.class,()->close(lease));}
        r.tick++;var blocked=new Port(r);assertThrows(IllegalStateException.class,()->r.open(kind,blocked));assertEquals(0,blocked.installs);
        r.ports.get(0).restoreFailure=null;close(receipts.get(0));assertTrue(active(r.open(kind,new Port(r))));
    }}
    @Test void shutdownRevokesBothRegistriesBeforeAnyCallbackAndBlocksOwnerResurrection(){try(var r=new Rig()){
        var drive=new Port(r);var nav=new Port(r);Object d=r.open(Kind.DRIVE,drive),n=r.open(Kind.NAVIGATION,r.owner,r.entity(UUID.randomUUID()),nav,null);
        Plugin other=r.plugin();var foreignPort=new Port(r);Object foreign=r.open(Kind.DRIVE,other,r.entity(UUID.randomUUID()),foreignPort,null);
        Runnable callback=()->{assertFalse(active(d));assertFalse(active(n));assertTrue(active(foreign));
            var attempted=new Port(r);assertThrows(IllegalStateException.class,()->r.open(Kind.DRIVE,r.owner,r.entity(UUID.randomUUID()),attempted,null));assertEquals(0,attempted.installs);
            assertThrows(IllegalStateException.class,()->shutdown(r.owner));};
        drive.restoring=callback;nav.restoring=callback;shutdown(r.owner);assertEquals(1,drive.restores);assertEquals(1,nav.restores);assertEquals(0,foreignPort.restores);
        assertTrue(active(r.open(Kind.DRIVE,new Port(r))),"shutdown gate released after success");
    }}
    @Test void shutdownAggregatesAllFailuresRetainsOnlyPendingReceiptsAndReleasesGate(){try(var r=new Rig()){
        var drive=new Port(r);var nav=new Port(r);r.open(Kind.DRIVE,drive);r.open(Kind.NAVIGATION,r.owner,r.entity(UUID.randomUUID()),nav,null);
        drive.restoreFailure=new NoSuppression("drive");nav.restoreFailure=new AssertionError("nav");
        var thrown=assertThrows(Error.class,()->shutdown(r.owner));assertTrue(contains(thrown,drive.restoreFailure));assertTrue(contains(thrown,nav.restoreFailure));
        assertEquals(1,drive.restores);assertEquals(1,nav.restores);
        Object independent=r.open(Kind.DRIVE,r.owner,r.entity(UUID.randomUUID()),new Port(r),null);assertTrue(active(independent),"gate must release even after failure");
        drive.restoreFailure=null;nav.restoreFailure=null;shutdown(r.owner);assertEquals(2,drive.restores);assertEquals(2,nav.restores);assertFalse(active(independent));
    }}
    @ParameterizedTest @EnumSource(Kind.class) void shutdownInsideRetainerRevokesPendingInstallAndUnwindsGate(Kind kind){try(var r=new Rig()){
        var p=new Port(r);assertThrows(IllegalStateException.class,()->r.retained(kind,p,value->assertThrows(IllegalStateException.class,()->shutdown(r.owner))));
        assertEquals(0,p.installs);assertEquals(1,p.restores);assertTrue(active(r.open(kind,new Port(r))));
    }}
    @Test void failedProgramCallbackAndCleanupErrorAreReportedTogetherAndCannotDrive(){try(var r=new Rig()){
        var p=new Port(r);var body=new NoSuppression("body");var cleanup=new AssertionError("cleanup");
        var lease=NativeDriveLease.openProgram(r.owner,r.entity,p,phase->{throw body;},r.failures::add);r.track(lease);p.restoreFailure=cleanup;
        var thrown=assertThrows(Error.class,lease::movementTick);assertTrue(contains(thrown,body));assertTrue(contains(thrown,cleanup));assertEquals(List.of(thrown),r.failures);
        assertFalse(lease.active());lease.movementTick();assertEquals(0,p.writes);p.restoreFailure=null;lease.close();assertEquals(2,p.restores);
    }}
    @Test void errorAfterProgramSelfCloseIsNotSwallowed(){try(var r=new Rig()){
        var p=new Port(r);var error=new AssertionError("after close");
        var lease=NativeDriveLease.openProgram(r.owner,r.entity,p,phase->{close(p.lease);throw error;},r.failures::add);r.track(lease);
        assertSame(error,assertThrows(AssertionError.class,lease::movementTick));assertFalse(lease.active());assertEquals(1,p.restores);assertEquals(0,p.writes);
    }}
    @Test void crossOwnerShutdownDepthIsFourAndCannotLeakAClosingGate(){try(var r=new Rig()){
        Plugin[] owners={r.owner,r.plugin(),r.plugin(),r.plugin(),r.plugin()};Port[] ports=new Port[5];Object[] receipts=new Object[5];
        for(int i=0;i<5;i++){ports[i]=new Port(r);receipts[i]=r.open(Kind.DRIVE,owners[i],r.entity(UUID.randomUUID()),ports[i],null);}
        for(int i=0;i<3;i++){int next=i+1;ports[i].restoring=()->shutdown(owners[next]);}
        ports[3].restoring=()->assertThrows(IllegalStateException.class,()->shutdown(owners[4]));shutdown(owners[0]);
        for(int i=0;i<4;i++)assertFalse(active(receipts[i]));assertTrue(active(receipts[4]));shutdown(owners[4]);assertFalse(active(receipts[4]));
        for(Plugin owner:owners)assertTrue(active(r.open(Kind.DRIVE,owner,r.entity(UUID.randomUUID()),new Port(r),null)));
    }}
}
