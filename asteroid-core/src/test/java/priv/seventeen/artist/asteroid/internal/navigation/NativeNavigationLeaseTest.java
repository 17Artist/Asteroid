package priv.seventeen.artist.asteroid.internal.navigation;

import org.bukkit.*;
import org.bukkit.entity.Mob;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.*;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import priv.seventeen.artist.asteroid.entity.NativeNavigationNMS.*;
import priv.seventeen.artist.asteroid.util.PaperCompat;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Recording native port only. No mock is represented as a real Paper event or NMS tick. */
class NativeNavigationLeaseTest {
    private static long clock=10_000;
    private static final Point POINT=new Point(4,64,4);
    private static final RouteGuard ACCEPT=new RouteGuard() {
        public boolean current(){return true;}
        public boolean accept(List<Point> path){return true;}
    };
    private static final class PathValue {
        List<Point> points=List.of(POINT); boolean reachable=true;
    }
    private static final class Port implements NativeNavigationLease.Port {
        long tick=clock; boolean owned,rejectInstall; int installs,queries,commits,stops,restores,detachedWrites,reads;
        Runnable callback,installed,committing;
        NativeNavigationLease lease;
        PathValue result=new PathValue(); Object current;
        public void install(NativeNavigationLease value){lease=value;installs++;owned=true;if(installed!=null)installed.run();if(rejectInstall)throw new IllegalStateException("install");}
        public boolean owned(){return owned;}
        public long tick(){return tick;}
        public Object query(Point target){queries++;try{if(callback!=null)callback.run();return result;}finally{detachedWrites++;}}
        public boolean reachable(Object path){return ((PathValue)path).reachable;}
        public List<Point> points(Object path,boolean remaining){reads++;return ((PathValue)path).points;}
        public Object currentPath(){return current;}
        public boolean commit(Object path,double speed){if(committing!=null)committing.run();commits++;current=path;return true;}
        public void stop(){if(owned){stops++;current=null;}}
        public void restore(){restores++;if(owned){current=null;owned=false;}}
    }
    private static final class Rig implements AutoCloseable {
        final MockedStatic<Bukkit> bukkit=mockStatic(Bukkit.class);
        final MockedStatic<PaperCompat> paper=mockStatic(PaperCompat.class);
        final Plugin plugin=mock(Plugin.class);
        final PluginManager manager=mock(PluginManager.class);
        final World world=mock(World.class);
        final List<NativeNavigationLease> leases=new ArrayList<>();
        final List<Listener> listeners=new ArrayList<>();
        final Mob entity;
        Rig(){
            clock+=1000;
            try{
                bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
                bukkit.when(Bukkit::getPluginManager).thenReturn(manager);
                when(plugin.isEnabled()).thenReturn(true);
                when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);
                doAnswer(call->{listeners.add(call.getArgument(0));return null;}).when(manager).registerEvents(any(),eq(plugin));
                entity=entity(UUID.randomUUID());
            }catch(RuntimeException|Error error){paper.close();bukkit.close();throw error;}
        }
        Mob entity(UUID id){
            var mob=mock(Mob.class);when(mob.getUniqueId()).thenReturn(id);when(mob.getWorld()).thenReturn(world);
            when(mob.getEntityId()).thenReturn(1);when(mob.isValid()).thenReturn(true);when(mob.hasAI()).thenReturn(true);return mob;
        }
        NativeNavigationLease open(Port port){return open(entity,port);}
        NativeNavigationLease open(Mob host,Port port){var value=NativeNavigationLease.open(plugin,host,port);leases.add(value);return value;}
        boolean move(NativeNavigationLease lease){return lease.move(new Location(world,4,64,4),1,ACCEPT);}
        @Override public void close(){
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);paper.when(PaperCompat::isFolia).thenReturn(false);
            try{for(var lease:leases)lease.close();}finally{paper.close();bukkit.close();}
        }
    }
    @Test void oneQueryOneImmutableValidationOneCommit(){try(var r=new Rig()){
        Port p=new Port();var l=r.open(p);AtomicInteger callbacks=new AtomicInteger();
        assertTrue(l.move(new Location(r.world,4,64,4),1,new RouteGuard(){
            public boolean current(){return true;}
            public boolean accept(List<Point> path){callbacks.incrementAndGet();assertThrows(UnsupportedOperationException.class,()->path.add(POINT));return path.equals(List.of(POINT));}
        }));
        assertEquals(1,p.queries);assertEquals(1,p.commits);assertEquals(1,callbacks.get());assertSame(p.result,p.current);
    }}
    @Test void resetInsideActualPortQueryCannotWriteBack(){try(var r=new Rig()){
        Port p=new Port();var l=r.open(p);p.callback=l::stop;
        assertFalse(r.move(l));assertEquals(1,p.detachedWrites);assertEquals(0,p.commits);assertNull(p.current);assertEquals(1,l.epoch());assertTrue(l.active());
    }}
    @Test void blockChangeRecomputeDoesNotQueryOrRefundTicket(){try(var r=new Rig()){
        Port p=new Port();var l=r.open(p);assertTrue(r.move(l));l.recomputeRequested();
        assertNull(p.current);assertEquals(1,p.queries);assertEquals(1,l.epoch());
        for(int i=1;i<NativeNavigationLease.MAX_QUERIES;i++)assertTrue(r.move(l));
        assertFalse(r.move(l));assertEquals(NativeNavigationLease.MAX_QUERIES,p.queries);assertNull(p.current);
    }}
    @Test void closedQueryAndLateDetachedWritesCannotClearSuccessor(){try(var r=new Rig()){
        Port old=new Port(),next=new Port();var l=r.open(old);AtomicReference<NativeNavigationLease> replacement=new AtomicReference<>();
        old.callback=()->{l.close();var current=r.open(next);replacement.set(current);assertTrue(r.move(current));};
        assertFalse(r.move(l));assertEquals(0,old.commits);assertEquals(1,old.detachedWrites);
        assertEquals(1,next.commits);assertSame(next.result,next.current);assertTrue(replacement.get().active());
        l.close();assertEquals(0,next.restores);assertSame(next.result,next.current);
    }}
    @Test void lateExceptionCannotClearSuccessor(){try(var r=new Rig()){
        Port old=new Port(),next=new Port();var l=r.open(old);
        old.callback=()->{l.close();var current=r.open(next);assertTrue(r.move(current));throw new IllegalStateException("retired");};
        assertThrows(IllegalStateException.class,()->r.move(l));assertSame(next.result,next.current);assertEquals(0,next.stops);
    }}
    @Test void oldOwnerPredicateCannotBorrowNewOwner(){try(var r=new Rig()){
        Port p=new Port(),next=new Port();var l=r.open(p);
        assertFalse(l.move(new Location(r.world,4,64,4),1,new RouteGuard(){
            public boolean current(){l.close();var n=r.open(next);assertTrue(r.move(n));return true;}
            public boolean accept(List<Point> path){fail("retired predicate");return true;}
        }));
        assertEquals(0,p.queries);assertSame(next.result,next.current);
    }}
    @Test void resetInsideRouteValidationPreventsCommit(){try(var r=new Rig()){
        Port p=new Port();var l=r.open(p);
        assertFalse(l.move(new Location(r.world,4,64,4),1,new RouteGuard(){
            public boolean current(){return true;}
            public boolean accept(List<Point> path){l.stop();return true;}
        }));assertEquals(0,p.commits);assertNull(p.current);
    }}
    @Test void sameLeaseQueryReentryIsExplicitAndDoesNotPoisonNextCall(){try(var r=new Rig()){
        Port p=new Port();var l=r.open(p);
        p.callback=()->assertThrows(IllegalStateException.class,()->r.move(l));
        assertTrue(r.move(l));assertEquals(1,p.queries);p.callback=null;assertTrue(r.move(l));assertEquals(2,p.commits);
    }}
    @Test void sameLeaseReadReentryIsExplicit(){try(var r=new Rig()){
        Port p=new Port();var l=r.open(p);p.callback=()->assertThrows(IllegalStateException.class,()->l.currentSafe(path->true));
        assertTrue(r.move(l));assertEquals(1,p.commits);
    }}
    @Test void queryExceptionReleasesGuardAndRetiresOnlyOwnRoute(){try(var r=new Rig()){
        Port p=new Port();var l=r.open(p);assertTrue(r.move(l));p.callback=()->{throw new IllegalArgumentException("query");};
        assertThrows(IllegalArgumentException.class,()->r.move(l));assertNull(p.current);p.callback=null;assertTrue(r.move(l));
    }}
    @Test void invalidAndRejectedRequestsCannotRefillGlobalQueryBudget(){try(var r=new Rig()){
        Port p=new Port();var l=r.open(p);
        for(int i=0;i<NativeNavigationLease.MAX_QUERIES;i++)
            assertThrows(IllegalArgumentException.class,()->l.move(new Location(r.world,4,64,4),Double.NaN,ACCEPT));
        assertFalse(r.move(l));assertEquals(0,p.queries);p.tick++;assertTrue(r.move(l));
    }}
    @Test void budgetIsGlobalAcrossOwnersAndSurvivesCloseReopen(){try(var r=new Rig()){
        Port first=new Port();var l=r.open(first);
        for(int i=0;i<NativeNavigationLease.MAX_QUERIES;i++)assertTrue(r.move(l));l.close();
        Port next=new Port();var n=r.open(next);assertFalse(r.move(n));assertEquals(0,next.queries);
        next.tick++;assertTrue(r.move(n));
    }}
    @Test void fakeNextTickInsideCallbackCannotRefillBudget(){try(var r=new Rig()){
        Port first=new Port(),second=new Port();var a=r.open(first);var b=r.open(r.entity(UUID.randomUUID()),second);
        first.callback=()->{second.tick++;assertThrows(IllegalStateException.class,()->r.move(b));second.tick--;};
        assertTrue(r.move(a));assertEquals(0,second.queries);first.callback=null;assertTrue(r.move(b));
    }}
    @Test void queryTickChangeRejectsCommitWithoutPretendingItIsAnotherFrame(){try(var r=new Rig()){
        Port p=new Port();var l=r.open(p);p.callback=()->p.tick++;
        assertFalse(r.move(l));assertEquals(0,p.commits);assertNull(p.current);
    }}
    @Test void backwardsTickIsExplicitlyRejected(){try(var r=new Rig()){
        Port p=new Port();var l=r.open(p);assertTrue(r.move(l));p.tick--;
        assertThrows(IllegalStateException.class,()->r.move(l));assertEquals(1,p.queries);
    }}
    @Test void boundedReadBudgetDoesNotRefillOnStop(){try(var r=new Rig()){
        Port p=new Port();var l=r.open(p);assertTrue(r.move(l));
        for(int i=0;i<NativeNavigationLease.MAX_READS;i++)assertTrue(l.currentSafe(path->true));
        l.stop();assertFalse(l.currentSafe(path->true));p.tick++;assertTrue(l.currentSafe(path->true));
    }}
    @Test void routeRejectionStopsOwnedRoute(){try(var r=new Rig()){
        Port p=new Port();var l=r.open(p);assertTrue(r.move(l));
        assertFalse(l.move(new Location(r.world,4,64,4),1,new RouteGuard(){public boolean current(){return true;}public boolean accept(List<Point> route){return false;}}));
        assertNull(p.current);assertEquals(1,p.commits);
    }}
    @Test void unreachableAndNullPathsDoNotCommit(){try(var r=new Rig()){
        Port p=new Port();var l=r.open(p);p.result.reachable=false;assertFalse(r.move(l));p.result=null;
        assertFalse(r.move(l));assertEquals(0,p.commits);
    }}
    @Test void exact256PointsPassAnd257RejectsBeforeTraversal(){try(var r=new Rig()){
        Port p=new Port();var l=r.open(p);p.result.points=Collections.nCopies(256,POINT);assertTrue(r.move(l));
        p.result.points=new AbstractList<>(){public int size(){return 257;}public Point get(int i){throw new AssertionError("must reject before traversal");}};
        assertFalse(r.move(l));assertEquals(1,p.commits);assertNull(p.current);
    }}
    @Test void lyingCollectionCannotOverrunTheSnapshotBoundOrUseToArray(){try(var r=new Rig()){
        Port p=new Port();var l=r.open(p);AtomicInteger reads=new AtomicInteger();
        p.result.points=new AbstractList<>(){
            public int size(){return 1;} public Point get(int index){reads.incrementAndGet();return POINT;}
            @Override public Iterator<Point> iterator(){return new Iterator<>(){public boolean hasNext(){return true;}public Point next(){reads.incrementAndGet();return POINT;}};}
            @Override public Object[] toArray(){throw new AssertionError("unbounded copy");}
        };
        assertFalse(r.move(l));assertEquals(257,reads.get());assertEquals(0,p.commits);
    }}
    @Test void nativeControllerReplacementCannotCommitOrClearForeignState(){try(var r=new Rig()){
        Port p=new Port();var l=r.open(p);Object foreign=new Object();
        p.callback=()->{p.owned=false;p.current=foreign;};
        assertFalse(r.move(l));assertEquals(0,p.commits);assertFalse(l.active());assertSame(foreign,p.current);assertEquals(0,p.stops);
    }}
    @Test void worldIdentityNotJustUuidIsPinned(){try(var r=new Rig()){
        Port p=new Port();var l=r.open(p);var other=mock(World.class);when(r.entity.getWorld()).thenReturn(other);
        assertFalse(l.active());assertEquals(1,p.restores);assertEquals(0,p.queries);
    }}
    @Test void entityIdReuseAndDeadEntityAreRejected(){try(var r=new Rig()){
        Port p=new Port();var l=r.open(p);when(r.entity.getEntityId()).thenReturn(2);assertFalse(l.active());
        when(r.entity.getEntityId()).thenReturn(1);Port q=new Port();var next=r.open(q);when(r.entity.isDead()).thenReturn(true);assertFalse(next.active());
    }}
    @Test void duplicateUuidCannotDisplaceExistingEntity(){try(var r=new Rig()){
        Port p=new Port();var l=r.open(p);UUID id=r.entity.getUniqueId();var duplicate=r.entity(id);Port q=new Port();
        assertThrows(IllegalStateException.class,()->r.open(duplicate,q));assertEquals(0,q.installs);assertTrue(l.active());
    }}
    @Test void ownerDisableClosesWithoutAnotherNavigationTick(){try(var r=new Rig()){
        Port p=new Port();var l=r.open(p);assertTrue(r.move(l));when(r.plugin.isEnabled()).thenReturn(false);
        for(Listener listener:r.listeners)((NativeNavigationLease)listener).disabled(new PluginDisableEvent(r.plugin));
        assertFalse(l.active());assertNull(p.current);assertEquals(1,p.restores);
    }}
    @Test void missedDisableStillClosesOnMaintenance(){try(var r=new Rig()){
        Port p=new Port();var l=r.open(p);when(r.plugin.isEnabled()).thenReturn(false);
        assertFalse(l.maintain());assertEquals(1,p.restores);
    }}
    @Test void installationFailureRollsBackAndDoesNotConsumeLiveCapacity(){try(var r=new Rig()){
        Port p=new Port();p.rejectInstall=true;assertThrows(IllegalStateException.class,()->r.open(p));assertEquals(1,p.restores);
        Port next=new Port();assertTrue(r.open(next).active());
    }}
    @Test void acquireBudgetIsNotRefundedByImmediateClose(){try(var r=new Rig()){
        for(int i=0;i<NativeNavigationLease.MAX_OPENS;i++){Port p=new Port();r.open(p).close();}
        Port rejected=new Port();assertThrows(IllegalStateException.class,()->r.open(rejected));assertEquals(0,rejected.installs);
        rejected.tick++;assertTrue(r.open(rejected).active());
    }}
    @Test void rejectedColdFactoriesConsumeAdmissionBeforeRepeatedNativeInspection(){try(var r=new Rig()){
        AtomicInteger factories=new AtomicInteger();
        for(int i=0;i<NativeNavigationLease.MAX_OPENS;i++)
            assertThrows(UnsupportedOperationException.class,()->NativeNavigationLease.open(r.plugin,r.entity,clock,()->{
                factories.incrementAndGet();throw new UnsupportedOperationException("dirty original");
            }));
        assertThrows(IllegalStateException.class,()->NativeNavigationLease.open(r.plugin,r.entity,clock,()->{
            fail("must charge before native factory");return new Port();
        }));
        assertEquals(NativeNavigationLease.MAX_OPENS,factories.get());
        Port next=new Port();next.tick++;assertTrue(r.open(next).active());
    }}
    @Test void coldFactoryCannotForgeNextTickAndExceptionReleasesAdmissionGuard(){try(var r=new Rig()){
        assertThrows(IllegalStateException.class,()->NativeNavigationLease.open(r.plugin,r.entity,clock,()->{
            NativeNavigationLease.open(r.plugin,r.entity,clock+1,Port::new);return new Port();
        }));
        Port next=new Port();assertTrue(r.open(next).active());
    }}
    @Test void exact256LiveOwnersAnd257thRejectWithoutInstalling(){try(var r=new Rig()){
        for(int i=0;i<256;i++){Port p=new Port();p.tick=clock+i/64;r.open(r.entity(UUID.randomUUID()),p);}
        Port rejected=new Port();rejected.tick=clock+4;
        assertThrows(IllegalStateException.class,()->r.open(r.entity(UUID.randomUUID()),rejected));assertEquals(0,rejected.installs);
        r.leases.get(0).close();assertTrue(r.open(r.entity(UUID.randomUUID()),rejected).active());
    }}
    @Test void callbackDepthFourAllowsHandoffButFifthRejects(){try(var r=new Rig()){
        Port[] ports=new Port[5];NativeNavigationLease[] leases=new NativeNavigationLease[5];
        for(int i=0;i<5;i++){ports[i]=new Port();leases[i]=r.open(r.entity(UUID.randomUUID()),ports[i]);}
        for(int i=0;i<3;i++){int next=i+1;ports[i].callback=()->assertTrue(r.move(leases[next]));}
        ports[3].callback=()->assertThrows(IllegalStateException.class,()->r.move(leases[4]));
        assertTrue(r.move(leases[0]));assertEquals(0,ports[4].queries);
    }}
    @Test void unloadedGoalAndInvalidCoordinatesNeverQuery(){try(var r=new Rig()){
        Port p=new Port();var l=r.open(p);when(r.world.isChunkLoaded(anyInt(),anyInt())).thenReturn(false);
        assertFalse(r.move(l));assertEquals(0,p.queries);
        assertThrows(IllegalArgumentException.class,()->l.move(new Location(r.world,Double.NaN,64,0),1,ACCEPT));assertEquals(0,p.queries);
    }}
    @Test void foliaAndWrongThreadRejectBeforeAnyNativeInstall(){try(var r=new Rig()){
        Port p=new Port();r.paper.when(PaperCompat::isFolia).thenReturn(true);
        assertThrows(UnsupportedOperationException.class,()->r.open(p));r.paper.when(PaperCompat::isFolia).thenReturn(false);
        r.bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
        assertThrows(IllegalStateException.class,()->r.open(p));assertEquals(0,p.installs);
    }}
}
