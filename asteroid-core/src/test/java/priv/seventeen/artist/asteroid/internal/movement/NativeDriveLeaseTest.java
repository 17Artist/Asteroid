package priv.seventeen.artist.asteroid.internal.movement;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import priv.seventeen.artist.asteroid.entity.NativeMovementNMS.*;
import priv.seventeen.artist.asteroid.util.PaperCompat;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Shared lifecycle tests with a recording native port; not a six-version native physics oracle. */
class NativeDriveLeaseTest {
    @Test void exactlyOneSourceAndMoveAndLookPerNativeTick(){try(var r=new Rig()){
        Port p=new Port();var lease=r.open(p,context->frame(context.nativeTick()));
        lease.movementTick();lease.movementTick();lease.directionTick();lease.directionTick();
        assertEquals(1,p.moves);assertEquals(1,p.looks);assertEquals(1,lease.executed());
        p.tick++;lease.movementTick();lease.directionTick();assertEquals(2,p.moves);assertEquals(2,p.looks);assertEquals(2,lease.executed());
    }}
    @Test void noOldFrameReplayWhenNewSourceIsNull(){try(var r=new Rig()){
        Port p=new Port();var lease=r.open(p,context->context.nativeTick()==0?frame(0):null);
        lease.movementTick();lease.directionTick();p.tick++;lease.movementTick();lease.directionTick();
        assertEquals(1,p.moves);assertEquals(1,p.looks);assertEquals(1,p.neutral);assertTrue(lease.active());
    }}
    @Test void futureFrameRejectedBeforeAnyNativeWrite(){try(var r=new Rig()){
        Port p=new Port();var lease=r.open(p,context->frame(context.nativeTick()+1));lease.movementTick();
        assertFalse(lease.active());assertEquals(0,p.moves);assertEquals(1,p.restores);assertEquals(1,r.failures.size());
    }}
    @Test void backwardsNativeTickIsFailureNotReplay(){try(var r=new Rig()){
        Port p=new Port();p.tick=10;var lease=r.open(p,context->frame(context.nativeTick()));lease.movementTick();p.tick=9;lease.movementTick();
        assertFalse(lease.active());assertEquals(1,p.moves);assertEquals(1,r.failures.size());
    }}
    @Test void sourceExceptionClosesBeforeRecoveryCanOpen(){try(var r=new Rig()){
        Port p=new Port();var lease=r.open(p,context->{throw new IllegalArgumentException("source");});lease.movementTick();
        assertFalse(lease.active());assertEquals(0,p.moves);assertEquals(1,p.restores);
        Port recovered=new Port();var replacement=r.open(recovered,context->frame(context.nativeTick()));replacement.movementTick();assertTrue(replacement.active());assertEquals(1,recovered.moves);
    }}
    @Test void callbackMayCloseButCannotCommitAfterClose(){try(var r=new Rig()){
        Port p=new Port();var reference=new AtomicReference<NativeDriveLease>();
        var lease=r.open(p,context->{reference.get().close();return frame(context.nativeTick());});reference.set(lease);lease.movementTick();lease.directionTick();
        assertFalse(lease.active());assertEquals(0,p.moves);assertEquals(0,p.looks);assertEquals(1,p.restores);
    }}
    @Test void nestedTickIsStableRejectionWithNoDoubleCommit(){try(var r=new Rig()){
        Port p=new Port();var reference=new AtomicReference<NativeDriveLease>();
        var lease=r.open(p,context->{assertThrows(IllegalStateException.class,()->reference.get().movementTick());assertThrows(IllegalStateException.class,()->reference.get().directionTick());return frame(context.nativeTick());});reference.set(lease);
        lease.movementTick();p.tick++;lease.movementTick();assertEquals(2,p.moves);assertTrue(lease.active());assertTrue(r.failures.isEmpty());
    }}
    @Test void swallowedReentryDoesNotPoisonLaterFramesAndUncaughtReentryCloses(){try(var r=new Rig()){
        Port p=new Port();var reference=new AtomicReference<NativeDriveLease>();
        var lease=r.open(p,context->{reference.get().movementTick();return frame(context.nativeTick());});reference.set(lease);lease.movementTick();
        assertFalse(lease.active());assertEquals(0,p.moves);assertEquals(1,r.failures.size());
    }}
    @Test void controllerReplacementInsideSourceCannotApplyOldFrame(){try(var r=new Rig()){
        Port p=new Port();var lease=r.open(p,context->{p.owned=false;return frame(context.nativeTick());});lease.movementTick();
        assertFalse(lease.active());assertEquals(0,p.moves);assertEquals(1,p.restores);assertFalse(p.owned);
    }}
    @Test void equalUuidCannotReplaceLiveOwnerAndOldCloseCannotCloseSuccessor(){try(var r=new Rig()){
        Port first=new Port();var old=r.open(first,context->frame(context.nativeTick()));
        Entity duplicate=r.entity(r.entity.getUniqueId());Port next=new Port();
        assertThrows(IllegalStateException.class,()->r.open(duplicate,next,context->frame(context.nativeTick())));assertEquals(0,next.installs);
        old.close();var current=r.open(duplicate,next,context->frame(context.nativeTick()));old.close();assertTrue(current.active());assertEquals(0,next.restores);
    }}
    @Test void worldChangeRevokesControllerBeforeInput(){try(var r=new Rig()){
        Port p=new Port();var lease=r.open(p,context->frame(context.nativeTick()));var world=mock(World.class);when(world.getUID()).thenReturn(UUID.randomUUID());when(r.entity.getWorld()).thenReturn(world);
        lease.movementTick();assertFalse(lease.active());assertEquals(0,p.moves);assertEquals(1,p.restores);
    }}
    @Test void nativeIdReuseAndDeadHostAreFenced(){try(var r=new Rig()){
        Port p=new Port();var lease=r.open(p,context->frame(context.nativeTick()));when(r.entity.getEntityId()).thenReturn(999);
        assertFalse(lease.active());assertEquals(1,p.restores);
        when(r.entity.getEntityId()).thenReturn(1);Port dead=new Port();var deadLease=r.open(dead,context->frame(context.nativeTick()));when(r.entity.isDead()).thenReturn(true);
        deadLease.directionTick();assertFalse(deadLease.active());assertEquals(0,dead.looks);
    }}
    @Test void registeredPluginDisableHookClosesForgottenLeaseWithoutAnotherMobTick(){try(var r=new Rig()){
        Port p=new Port();var lease=r.open(p,context->frame(context.nativeTick()));assertEquals(List.of(lease),r.listeners);
        lease.disabled(new PluginDisableEvent(mock(Plugin.class)));assertTrue(lease.active());
        when(r.plugin.isEnabled()).thenReturn(false);
        for(Listener listener:List.copyOf(r.listeners))((NativeDriveLease)listener).disabled(new PluginDisableEvent(r.plugin));
        assertEquals(1,p.restores);assertFalse(lease.active());assertEquals(0,p.moves);
    }}
    @Test void disabledOwnerAlsoFailsClosedIfDisableEventWasMissed(){try(var r=new Rig()){
        Port p=new Port();var lease=r.open(p,context->frame(context.nativeTick()));when(r.plugin.isEnabled()).thenReturn(false);
        lease.movementTick();assertFalse(lease.active());assertEquals(0,p.moves);assertEquals(1,p.restores);
    }}
    @Test void installationFailureReleasesCapacityAndRestoresOwnPartialInstall(){try(var r=new Rig()){
        Port p=new Port();p.reject=true;assertThrows(IllegalStateException.class,()->r.open(p,context->frame(context.nativeTick())));assertEquals(1,p.restores);
        Port next=new Port();assertTrue(r.open(next,context->frame(context.nativeTick())).active());
    }}
    @Test void hardGlobalLimitRejectsBeforeInstallingAnotherController(){try(var r=new Rig()){
        for(int i=0;i<256;i++)r.open(r.entity(UUID.randomUUID()),new Port(),context->frame(context.nativeTick()));
        Port rejected=new Port();assertThrows(IllegalStateException.class,()->r.open(r.entity(UUID.randomUUID()),rejected,context->frame(context.nativeTick())));assertEquals(0,rejected.installs);
        r.leases.get(0).close();assertTrue(r.open(rejected,context->frame(context.nativeTick())).active());
    }}
    @Test void invalidThreadAndFoliaRejectBeforeInstall(){try(var r=new Rig()){
        Port p=new Port();r.bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);assertThrows(IllegalStateException.class,()->r.open(p,context->frame(0)));assertEquals(0,p.installs);
        r.bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);r.paper.when(PaperCompat::isFolia).thenReturn(true);assertThrows(UnsupportedOperationException.class,()->r.open(p,context->frame(0)));assertEquals(0,p.installs);
    }}
    @Test void typedSignalsRejectNanInfinityAndExcessiveInput(){
        for(float value:new float[]{Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY,1.01f}){
            assertThrows(IllegalArgumentException.class,()->new DriveFrame(0,value,0,1,0,0,false,false,0,false,false,false));
            assertThrows(IllegalArgumentException.class,()->new DriveFrame(0,0,value,1,0,0,false,false,0,false,false,false));
            assertThrows(IllegalArgumentException.class,()->new DriveFrame(0,0,0,1,0,0,false,false,value,false,false,false));
        }
        assertThrows(IllegalArgumentException.class,()->frame(-1));assertThrows(IllegalArgumentException.class,()->new DriveContext(-1,false,false));
        assertThrows(IllegalArgumentException.class,()->new DriveFrame(0,0,0,5,0,0,false,false,0,false,false,false));
        assertThrows(IllegalArgumentException.class,()->new DriveFrame(0,0,0,1,361,0,false,false,0,false,false,false));
        assertThrows(IllegalArgumentException.class,()->new DriveFrame(0,0,0,1,0,91,false,false,0,false,false,false));
    }
    private static DriveFrame frame(long tick){return new DriveFrame(tick,.5f,1,1,90,20,true,true,1,false,true,true);}
    private static final class Port implements NativeDriveLease.Port {
        long tick;boolean owned,reject;int installs,moves,looks,neutral,restores;
        public void install(NativeDriveLease lease){installs++;owned=true;if(reject)throw new IllegalStateException("install");}
        public boolean owned(){return owned;}
        public DriveContext context(){return new DriveContext(tick,true,false);}
        public void move(DriveFrame frame){moves++;}
        public void look(DriveFrame frame){looks++;}
        public void neutral(){neutral++;}
        public void restore(){restores++;owned=false;}
    }
    private static final class Rig implements AutoCloseable {
        final MockedStatic<Bukkit> bukkit=mockStatic(Bukkit.class);
        final MockedStatic<PaperCompat> paper=mockStatic(PaperCompat.class);
        final Plugin plugin=mock(Plugin.class);final PluginManager manager=mock(PluginManager.class);final World world=mock(World.class);
        final List<Listener> listeners=new ArrayList<>();final List<NativeDriveLease> leases=new ArrayList<>();final List<Throwable> failures=new ArrayList<>();
        final Entity entity;
        Rig(){bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);bukkit.when(Bukkit::getPluginManager).thenReturn(manager);when(plugin.isEnabled()).thenReturn(true);when(world.getUID()).thenReturn(UUID.randomUUID());entity=entity(UUID.randomUUID());
            doAnswer(call->{listeners.add(call.getArgument(0));return null;}).when(manager).registerEvents(any(),eq(plugin));}
        Entity entity(UUID id){var value=mock(Entity.class);when(value.getUniqueId()).thenReturn(id);when(value.getEntityId()).thenReturn(1);when(value.getWorld()).thenReturn(world);when(value.isValid()).thenReturn(true);return value;}
        NativeDriveLease open(Port port,Function<DriveContext,DriveFrame> source){return open(entity,port,source);}
        NativeDriveLease open(Entity entity,Port port,Function<DriveContext,DriveFrame> source){var lease=NativeDriveLease.open(plugin,entity,port,source,failures::add);leases.add(lease);return lease;}
        public void close(){bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);paper.when(PaperCompat::isFolia).thenReturn(false);try{for(var lease:leases)lease.close();}finally{paper.close();bukkit.close();}}
    }
}
