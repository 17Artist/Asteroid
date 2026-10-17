package priv.seventeen.artist.asteroid.internal.navigation;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.*;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import priv.seventeen.artist.asteroid.entity.NativeNavigationNMS;
import priv.seventeen.artist.asteroid.util.PaperCompat;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real core registry/owner/budget/retained lifecycle; recording native port, NOT Paper execution. */
class NativeNavigationFacingTest {
    static void raise(Throwable t){if(t instanceof Error e)throw e;if(t instanceof RuntimeException e)throw e;}
    static Object invoke(Object target,String name,Class<?>[] types,Object...args){
        try{Method m=(target instanceof Class<?> c?c:target.getClass()).getMethod(name,types);m.trySetAccessible();return m.invoke(target instanceof Class<?>?null:target,args);}
        catch(InvocationTargetException e){raise(e.getCause());throw new AssertionError(e.getCause());}
        catch(ReflectiveOperationException e){throw new AssertionError("Missing fixed-facing capability: "+name,e);}
    }
    static Object face(NativeNavigationLease parent,float yaw,float pitch,int ticks,Consumer<Object> keep){
        return invoke(parent,"freezeFacingRetained",new Class<?>[]{float.class,float.class,int.class,Consumer.class},yaw,pitch,ticks,keep);
    }
    static boolean current(Object f){return (boolean)invoke(f,"current",new Class<?>[0]);}
    static void close(Object f){invoke(f,"close",new Class<?>[0]);}
    static boolean contains(Throwable root,Throwable expected){
        var seen=Collections.newSetFromMap(new IdentityHashMap<Throwable,Boolean>());var todo=new ArrayDeque<Throwable>();todo.add(root);
        while(!todo.isEmpty()){var t=todo.remove();if(t==expected)return true;if(!seen.add(t))continue;assertTrue(seen.size()<1000);if(t.getCause()!=null)todo.add(t.getCause());todo.addAll(Arrays.asList(t.getSuppressed()));}return false;
    }
    static final class NoSuppression extends RuntimeException{NoSuppression(){super("original",null,false,true);}}
    @Test void legacyProviderDefaultRejectsWithoutCallingRetainer(){
        NativeNavigationNMS.NavigationLease legacy=new NativeNavigationNMS.NavigationLease(){
            public boolean active(){return true;}public long epoch(){return 0;}
            public boolean move(Location d,double s,NativeNavigationNMS.RouteGuard g){return false;}
            public boolean currentSafe(Predicate<List<NativeNavigationNMS.Point>> p){return true;}
            public void stop(){}public void close(){}
        };
        assertThrows(UnsupportedOperationException.class,()->invoke(legacy,"freezeFacingRetained",new Class<?>[]{float.class,float.class,int.class,Consumer.class},0f,0f,6,(Consumer<Object>)x->fail("legacy callback")));
        assertTrue(legacy.active());
    }
    static final class Rig implements AutoCloseable {
        final MockedStatic<Bukkit> bukkit=mockStatic(Bukkit.class);final MockedStatic<PaperCompat> paper=mockStatic(PaperCompat.class);
        final MockedStatic<HandlerList> handlers=mockStatic(HandlerList.class);final Plugin plugin=mock(Plugin.class);
        final World world=mock(World.class);final Mob host=mock(Mob.class);final UUID id=UUID.randomUUID();
        final Map<String,Object> budget=new LinkedHashMap<>();final List<NativeNavigationLease> parents=new ArrayList<>();
        final List<FacePort> faces=new ArrayList<>();Entity exact=host;long tick;boolean navOwned;int navRestores,queries,unregisters;
        Runnable preparing,installing,restoring;Throwable prepareFailure,installFailure,restoreFailure,navFailure;FacePort last;
        final NativeNavigationLease.Port port=(NativeNavigationLease.Port)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{NativeNavigationLease.Port.class},(p,m,a)->switch(m.getName()){
            case "install"->{navOwned=true;yield null;}case "owned"->navOwned;case "tick"->tick;
            case "prepareFacing"->{if(preparing!=null)preparing.run();raise(prepareFailure);last=new FacePort(this);faces.add(last);yield last.proxy();}
            case "restore"->{navRestores++;raise(navFailure);navOwned=false;yield null;}
            case "query"->{queries++;yield new Object();}case "reachable","commit"->true;
            case "points"->List.of(new NativeNavigationNMS.Point(1,64,1));case "currentPath"->null;case "stop"->null;
            case "toString"->"recording-nav";case "equals"->p==a[0];case "hashCode"->System.identityHashCode(p);
            default->throw new AssertionError(m);
        });
        Rig(){
            try{for(String n:List.of("budgetTick","queries","reads","opens","depth")){Field f=NativeNavigationLease.class.getDeclaredField(n);f.setAccessible(true);budget.put(n,f.get(null));}tick=Math.max(0,(Long)budget.get("budgetTick"))+1000;}
            catch(ReflectiveOperationException e){throw new AssertionError(e);}
            when(plugin.isEnabled()).thenReturn(true);when(host.getUniqueId()).thenReturn(id);when(host.getWorld()).thenReturn(world);
            when(host.getEntityId()).thenReturn(17);when(host.isValid()).thenReturn(true);when(host.hasAI()).thenReturn(true);
            when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
            bukkit.when(()->Bukkit.getEntity(id)).thenAnswer(c->exact);paper.when(PaperCompat::isFolia).thenReturn(false);
            handlers.when(()->HandlerList.unregisterAll(any(Listener.class))).thenAnswer(c->{unregisters++;return null;});
        }
        NativeNavigationLease open(){var n=NativeNavigationLease.open(plugin,host,port);parents.add(n);return n;}
        Object freeze(NativeNavigationLease n){return face(n,11.96725f,0,6,x->{});}
        @Override public void close(){
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);paper.when(PaperCompat::isFolia).thenReturn(false);
            installing=restoring=preparing=null;restoreFailure=navFailure=installFailure=prepareFailure=null;
            for(var f:faces){f.checking=null;f.readFailure=null;}
            try{for(var p:parents)p.close();}
            finally{try{for(var e:budget.entrySet()){Field f=NativeNavigationLease.class.getDeclaredField(e.getKey());f.setAccessible(true);f.set(null,e.getValue());}}
                catch(ReflectiveOperationException e){throw new AssertionError(e);}finally{handlers.close();paper.close();bukkit.close();}}
        }
    }
    static final class FacePort {
        final Rig r;Object receipt,installed=new Object(),controller;boolean owned;int installs,restores,reads;float yaw,pitch;
        Runnable checking;Throwable readFailure;
        FacePort(Rig r){this.r=r;}
        Object proxy(){try{Class<?> api=Class.forName("priv.seventeen.artist.asteroid.internal.navigation.NativeNavigationLease$FacingPort");
            return Proxy.newProxyInstance(api.getClassLoader(),new Class<?>[]{api},(p,m,a)->switch(m.getName()){
                case "install"->{receipt=a[0];yaw=(float)a[1];pitch=(float)a[2];installs++;controller=installed;owned=true;if(r.installing!=null)r.installing.run();raise(r.installFailure);yield null;}
                case "owned"->{reads++;if(checking!=null)checking.run();raise(readFailure);yield owned&&controller==installed&&yaw==(float)a[0]&&pitch==(float)a[1];}
                case "restore"->{restores++;if(r.restoring!=null)r.restoring.run();raise(r.restoreFailure);if(controller==installed)controller=null;owned=false;yield null;}
                default->throw new AssertionError(m);
            });}catch(ClassNotFoundException e){throw new AssertionError("Missing native facing port",e);}}
    }
    @Test void exactReceiptRetainedBeforeAnyNativeWriteAndInactiveDuringAdmission(){try(var r=new Rig()){
        var n=r.open();Object[] saved={null};Object f=face(n,11.96725f,5,6,x->{saved[0]=x;assertFalse(current(x));assertEquals(0,r.last.installs);});
        assertSame(saved[0],f);assertTrue(current(f));assertEquals(1,r.last.installs);assertEquals(11.96725f,r.last.yaw);assertEquals(5,r.last.pitch);
    }}
    @Test void everyInvalidInputRejectsBeforeColdFactoryAndRetention(){try(var r=new Rig()){
        var n=r.open();for(float yaw:new float[]{Float.NaN,Float.POSITIVE_INFINITY,-181,181})assertThrows(IllegalArgumentException.class,()->face(n,yaw,0,6,x->fail()));
        for(float pitch:new float[]{Float.NaN,-91,91})assertThrows(IllegalArgumentException.class,()->face(n,0,pitch,6,x->fail()));
        for(int ticks:new int[]{-1,0,5,201,Integer.MAX_VALUE})assertThrows(IllegalArgumentException.class,()->face(n,0,0,ticks,x->fail()));assertTrue(r.faces.isEmpty());
    }}
    @Test void inclusiveAngleAndDurationBoundariesRemainAccepted(){try(var r=new Rig()){
        var n=r.open();for(float yaw:new float[]{-180,180})for(float pitch:new float[]{-90,90})for(int ticks:new int[]{6,200}){Object f=face(n,yaw,pitch,ticks,x->{});assertTrue(current(f));close(f);}assertEquals(8,r.faces.size());
    }}
    @Test void nativeExpiryIsExclusiveAndDoesNotEndNavigation(){try(var r=new Rig()){
        var n=r.open();Object f=r.freeze(n);r.tick+=5;assertTrue(current(f));r.tick++;assertFalse(current(f));assertTrue(n.active());assertEquals(1,r.last.restores);assertEquals(0,r.navRestores);
    }}
    @Test void nativePhaseMaintainsExpiryWithoutAnotherTimer(){try(var r=new Rig()){
        var n=r.open();Object f=r.freeze(n);r.tick+=6;assertTrue(n.maintain());assertFalse(current(f));assertEquals(1,r.last.restores);
    }}
    @Test void backwardsTickRevokesInsteadOfExtendingDeadline(){try(var r=new Rig()){
        var n=r.open();Object f=r.freeze(n);r.tick--;assertFalse(current(f));assertEquals(1,r.last.restores);
    }}
    @Test void overflowingDeadlineRejectsBeforeNativeFactory(){try(var r=new Rig()){
        var n=r.open();r.tick=Long.MAX_VALUE-2;assertThrows(ArithmeticException.class,()->r.freeze(n));assertTrue(r.faces.isEmpty());
    }}
    @Test void exactUuidReplacementCannotBorrowCurrentReceipt(){try(var r=new Rig()){
        var n=r.open();Object f=r.freeze(n);r.exact=mock(Mob.class);assertFalse(current(f));assertTrue(n.active());assertEquals(1,r.last.restores);
    }}
    @Test void worldDeathAiAndPluginDriftEachRevoke(){for(int mode=0;mode<4;mode++)try(var r=new Rig()){
        var n=r.open();Object f=r.freeze(n);switch(mode){case 0->when(r.host.getWorld()).thenReturn(mock(World.class));case 1->when(r.host.isDead()).thenReturn(true);case 2->when(r.host.hasAI()).thenReturn(false);default->when(r.plugin.isEnabled()).thenReturn(false);}
        assertFalse(current(f));assertEquals(1,r.last.restores);
    }}
    @Test void unavailablePreflightNeverCallsRetainerOrWrites(){try(var r=new Rig()){
        var n=r.open();r.prepareFailure=new UnsupportedOperationException("look not vanilla");assertThrows(UnsupportedOperationException.class,()->face(n,0,0,6,x->fail()));assertTrue(r.faces.isEmpty());assertTrue(n.active());
    }}
    @Test void preflightTickDriftRejectedBeforeRetain(){try(var r=new Rig()){
        var n=r.open();r.preparing=()->r.tick++;assertThrows(IllegalStateException.class,()->face(n,0,0,6,x->fail()));assertEquals(0,r.last.installs);
    }}
    @Test void oneChildCapacityRejectsSecondBeforeFactory(){try(var r=new Rig()){
        var n=r.open();Object old=r.freeze(n);assertThrows(IllegalStateException.class,()->r.freeze(n));assertEquals(1,r.faces.size());close(old);assertTrue(current(r.freeze(n)));
    }}
    @Test void facingAndNavigationShare64ColdAdmissionBudget(){try(var r=new Rig()){
        var n=r.open();for(int i=0;i<63;i++)close(r.freeze(n));assertThrows(IllegalStateException.class,()->r.freeze(n));assertEquals(63,r.faces.size());r.tick++;assertTrue(current(r.freeze(n)));
    }}
    @Test void currentReadsDoNotRefillColdBudget(){try(var r=new Rig()){
        var n=r.open();for(int i=0;i<62;i++)close(r.freeze(n));Object f=r.freeze(n);for(int i=0;i<300;i++)assertTrue(current(f));close(f);assertThrows(IllegalStateException.class,()->r.freeze(n));
    }}
    @Test void retainedCloseDuringAdmissionRejectsWithoutNativeWrites(){try(var r=new Rig()){
        var n=r.open();Object[] saved={null};assertThrows(IllegalStateException.class,()->face(n,0,0,6,x->{saved[0]=x;close(x);}));assertFalse(current(saved[0]));assertEquals(0,r.last.installs);assertEquals(1,r.last.restores);
    }}
    @Test void nestedAdmissionIsRejectedBeforeSecondFactory(){try(var r=new Rig()){
        var n=r.open();Object f=face(n,0,0,6,x->assertThrows(IllegalStateException.class,()->r.freeze(n)));assertTrue(current(f));assertEquals(1,r.faces.size());
    }}
    @Test void preflightReentryIsRejectedBeforeSecondFactory(){try(var r=new Rig()){
        var n=r.open();r.preparing=()->assertThrows(IllegalStateException.class,()->r.freeze(n));assertTrue(current(r.freeze(n)));assertEquals(1,r.faces.size());
    }}
    @Test void retainedCallbackTickDriftHasZeroWrites(){try(var r=new Rig()){
        var n=r.open();assertThrows(IllegalStateException.class,()->face(n,0,0,6,x->r.tick++));assertEquals(0,r.last.installs);assertEquals(1,r.last.restores);
    }}
    @Test void installationPostcheckDriftClosesWithoutAngleRollback(){try(var r=new Rig()){
        var n=r.open();r.installing=()->r.last.yaw=45;assertThrows(IllegalStateException.class,()->r.freeze(n));assertEquals(1,r.last.installs);assertEquals(45,r.last.yaw);assertEquals(1,r.last.restores);
    }}
    @Test void closePreservesFinalAngleAndCannotOverwriteForeignController(){try(var r=new Rig()){
        var n=r.open();Object f=r.freeze(n);Object foreign=new Object();r.last.controller=foreign;r.last.yaw=90;assertFalse(current(f));assertSame(foreign,r.last.controller);assertEquals(90,r.last.yaw);
    }}
    @Test void oldClosedChildCannotCloseNewChild(){try(var r=new Rig()){
        var n=r.open();Object old=r.freeze(n);close(old);Object next=r.freeze(n);close(old);assertTrue(current(next));assertEquals(0,r.last.restores);
    }}
    @Test void movementRejectedBeforeQueryWhileFacingOwned(){try(var r=new Rig()){
        var n=r.open();Object f=r.freeze(n);assertFalse(n.move(new Location(r.world,1,64,1),1,new NativeNavigationNMS.RouteGuard(){public boolean current(){return true;}public boolean accept(List<NativeNavigationNMS.Point> p){return true;}}));
        assertEquals(0,r.queries);assertTrue(current(f));
    }}
    @Test void failedRestoreIsNotRetriedByNativePhaseButExplicitCloseCanRetry(){try(var r=new Rig()){
        var n=r.open();Object f=r.freeze(n);r.restoreFailure=new IllegalStateException("restore");r.last.yaw=50;
        assertThrows(IllegalStateException.class,()->current(f));assertFalse(current(f));for(int i=0;i<10;i++)assertTrue(n.maintain());assertEquals(1,r.last.restores);
        assertThrows(IllegalStateException.class,()->r.freeze(n));r.restoreFailure=null;close(f);assertTrue(current(r.freeze(n)));
    }}
    @Test void parentCloseStillAttemptsAllOtherStagesAndRetainsExactChildFailure(){try(var r=new Rig()){
        var n=r.open();Object f=r.freeze(n);r.restoreFailure=new IllegalStateException("look restore");assertThrows(IllegalStateException.class,n::close);
        assertFalse(n.active());assertFalse(current(f));assertEquals(1,r.navRestores);assertEquals(1,r.unregisters);assertThrows(IllegalStateException.class,r::open);
        r.restoreFailure=null;n.close();assertEquals(1,r.navRestores);assertEquals(1,r.unregisters);assertTrue(r.open().active());
    }}
    @Test void parentCloseInsideRetainCannotReturnSuccessfulAdmission(){try(var r=new Rig()){
        var n=r.open();assertThrows(IllegalStateException.class,()->face(n,0,0,6,x->n.close()));assertEquals(0,r.last.installs);assertFalse(n.active());n.close();assertTrue(r.open().active());
    }}
    @Test void disableEventDedupAlsoCoversRetainedFacingFailure(){try(var r=new Rig()){
        var n=r.open();Object f=r.freeze(n);r.restoreFailure=new IllegalStateException("look restore");
        var event=new PluginDisableEvent(r.plugin);assertThrows(IllegalStateException.class,()->n.disabled(event));
        assertFalse(current(f));n.disabled(event);assertEquals(1,r.last.restores);r.restoreFailure=null;n.close();assertEquals(2,r.last.restores);
    }}
    @Test void recursiveRestoreCannotReportFalseSuccess(){try(var r=new Rig()){
        var n=r.open();Object f=r.freeze(n);r.restoring=()->close(f);assertThrows(IllegalStateException.class,()->close(f));assertFalse(current(f));r.restoring=null;close(f);assertTrue(current(r.freeze(n)));
    }}
    @Test void recursionInReadbackIsBoundedAndRevokes(){try(var r=new Rig()){
        var n=r.open();Object f=r.freeze(n);r.last.checking=()->current(f);assertThrows(IllegalStateException.class,()->current(f));assertFalse(current(f));assertEquals(1,r.last.restores);
    }}
    @Test void readbackErrorIsPreservedAndNotRethrownEveryNativePhase(){try(var r=new Rig()){
        var n=r.open();Object f=r.freeze(n);Error boom=new AssertionError("native getter");r.last.readFailure=boom;
        assertSame(boom,assertThrows(AssertionError.class,()->current(f)));assertFalse(current(f));for(int i=0;i<5;i++)assertTrue(n.maintain());assertEquals(1,r.last.restores);
    }}
    @Test void suppressionDisabledOriginalAndCleanupErrorBothRemainTraceable(){try(var r=new Rig()){
        var n=r.open();Throwable original=new NoSuppression(),cleanup=new AssertionError("cleanup");r.installFailure=original;r.restoreFailure=cleanup;
        Error error=assertThrows(Error.class,()->r.freeze(n));assertTrue(contains(error,original));assertTrue(contains(error,cleanup));assertEquals(1,r.last.installs);assertEquals(1,r.last.restores);
    }}
    @Test void sameErrorFromInstallAndRestoreDoesNotSelfSuppress(){try(var r=new Rig()){
        var n=r.open();Error error=new AssertionError("same");r.installFailure=r.restoreFailure=error;assertSame(error,assertThrows(Error.class,()->r.freeze(n)));
    }}
    @Test void offThreadAndFoliaCannotAcquireOrReleaseNativeOwnership(){try(var r=new Rig()){
        var n=r.open();Object f=r.freeze(n);r.bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);assertThrows(IllegalStateException.class,()->current(f));assertThrows(IllegalStateException.class,()->close(f));
        r.bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);r.paper.when(PaperCompat::isFolia).thenReturn(true);assertThrows(UnsupportedOperationException.class,()->current(f));
    }}
}
