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
import priv.seventeen.artist.asteroid.entity.NativeDriveProgram;
import priv.seventeen.artist.asteroid.entity.NativeMovementNMS;
import priv.seventeen.artist.asteroid.entity.NativeMovementNMS.*;
import priv.seventeen.artist.asteroid.util.PaperCompat;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Shared production phase/lifecycle with a recording native port; not a six-version physics oracle. */
class NativeDriveProgramTest {
    @Test void programOrdersImmediateWritesBeforeFifoPostPhaseAndDirection() { try(var r=new Rig()) {
        var p=new Port();var lease=r.open(p,phase->{
            phase.clearHorizontalInput();phase.groundedTravel(true);phase.suppressWaterJump();
            phase.move(input(1));phase.enqueue(input(2),()->true);phase.jump();
            phase.enqueue(input(3),()->true);phase.resetFallDistance();phase.direction(90,20);
        });
        lease.movementTick();lease.directionTick();
        assertEquals(List.of("clear","ground","water","input:1.0","jump","input:2.0","input:3.0","look:90.0"),p.calls);
        assertEquals(1,p.noFalls);assertEquals(1,lease.executed());
    }}
    @Test void samplesHaveOriginalContactButLiveSpeedAndVelocity() { try(var r=new Rig()) {
        var p=new Port();p.ground=false;
        var lease=r.open(p,phase->{
            assertFalse(phase.sample().onGround());phase.groundedTravel(true);phase.velocity(1,.5,-2);p.speed=.875f;
            var live=phase.sample();assertFalse(live.onGround());assertEquals(.875f,live.movementSpeed());
            assertEquals(1,live.velocityX());assertEquals(.5,live.velocityY());assertEquals(-2,live.velocityZ());
        });
        lease.movementTick();assertTrue(lease.active());assertTrue(p.ground);
    }}
    @Test void callbackMoveAndDirectionEachRunOncePerNativeTick() { try(var r=new Rig()) {
        var p=new Port();var count=new AtomicInteger();var lease=r.open(p,phase->{count.incrementAndGet();phase.move(input(1));phase.direction(1,0);});
        lease.movementTick();lease.movementTick();lease.directionTick();lease.directionTick();
        assertEquals(1,count.get());assertEquals(1,p.inputs.size());assertEquals(1,p.looks);
        p.tick++;lease.movementTick();lease.directionTick();assertEquals(2,count.get());assertEquals(2,lease.executed());
    }}
    @Test void noDirectionReceiptFallsBackToOriginalLookNeverReplaysOlderReceipt() { try(var r=new Rig()) {
        var p=new Port();var lease=r.open(p,phase->{if(phase.nativeTick()==0)phase.direction(30,5);else phase.defaultMovement();});
        lease.movementTick();lease.directionTick();p.tick++;lease.movementTick();lease.directionTick();lease.directionTick();
        assertEquals(1,p.looks);assertEquals(1,p.defaultLooks);assertEquals(1,p.defaults);
    }}
    @Test void defaultMovementMustBeFirstAndSingleUseButCaughtRejectionDoesNotPoisonPhase() { try(var r=new Rig()) {
        var p=new Port();var lease=r.open(p,phase->{
            phase.sample();phase.defaultMovement();assertThrows(IllegalStateException.class,phase::defaultMovement);phase.jump();
        });lease.movementTick();assertTrue(lease.active());assertEquals(1,p.defaults);assertEquals(1,p.jumps);
        p.tick++;lease.movementTick();assertEquals(2,p.defaults);
    }}
    @Test void exactSharedOperationBudgetCannotBeResetByReadsOrDifferentCallers() { try(var r=new Rig()) {
        var p=new Port();var lease=r.open(p,phase->{
            for(int caller=0;caller<2;caller++)for(int i=0;i<128;i++){assertTrue(phase.active());phase.nativeTick();phase.sample();}
            assertThrows(IllegalStateException.class,phase::sample);assertThrows(IllegalStateException.class,phase::jump);
        });lease.movementTick();assertTrue(lease.active());assertEquals(256,p.samples);assertEquals(0,p.jumps);
        p.tick++;lease.movementTick();assertEquals(512,p.samples);assertEquals(2,lease.executed());
    }}
    @Test void queue64AcceptedAnd65RejectedWithoutDiscardingAcceptedCommands() { try(var r=new Rig()) {
        var p=new Port();var lease=r.open(p,phase->{
            for(int i=0;i<64;i++)phase.enqueue(input(i),()->true);
            assertThrows(IllegalStateException.class,()->phase.enqueue(input(999),()->true));
        });lease.movementTick();assertTrue(lease.active());assertEquals(64,p.inputs.size());
        for(int i=0;i<64;i++)assertEquals(i,p.inputs.get(i).front());
    }}
    @Test void queueDrainAlsoConsumesTheSameBudgetAndCannotExceed256() { try(var r=new Rig()) {
        var p=new Port();var lease=r.open(p,phase->{for(int i=0;i<64;i++)phase.enqueue(input(i),()->true);for(int i=0;i<192;i++)phase.sample();});
        lease.movementTick();assertFalse(lease.active());assertEquals(192,p.samples);assertTrue(p.inputs.isEmpty());assertEquals(1,r.failures.size());
    }}
    @Test void bindingGuardFalseDiscardsOnlyItsResolvedCommand() { try(var r=new Rig()) {
        var p=new Port();var lease=r.open(p,phase->{phase.enqueue(input(1),()->false);phase.enqueue(input(2),()->true);});
        lease.movementTick();assertTrue(lease.active());assertEquals(List.of(input(2)),p.inputs);
    }}
    @Test void guardCannotReenterNativeOperationAndCaughtRejectionCleansUpGuard() { try(var r=new Rig()) {
        var p=new Port();var lease=r.open(p,phase->{
            phase.enqueue(input(1),()->{assertThrows(IllegalStateException.class,phase::jump);return true;});
            phase.enqueue(input(2),()->true);
        });lease.movementTick();assertTrue(lease.active());assertEquals(List.of(input(1),input(2)),p.inputs);assertEquals(0,p.jumps);
    }}
    @Test void uncaughtGuardReentryRevokesLeaseAndRemainder() { try(var r=new Rig()) {
        var p=new Port();var lease=r.open(p,phase->{phase.enqueue(input(1),()->{phase.jump();return true;});phase.enqueue(input(2),()->true);});
        lease.movementTick();assertFalse(lease.active());assertTrue(p.inputs.isEmpty());assertEquals(0,p.jumps);assertEquals(1,r.failures.size());
    }}
    @Test void guardExceptionReleasesOwnerAndLaterLeaseStartsClean() { try(var r=new Rig()) {
        var p=new Port();var old=r.open(p,phase->phase.enqueue(input(1),()->{throw new IllegalArgumentException("guard");}));
        old.movementTick();assertFalse(old.active());assertTrue(p.inputs.isEmpty());
        var nextPort=new Port();var next=r.open(nextPort,phase->phase.jump());next.movementTick();
        assertTrue(next.active());assertEquals(1,nextPort.jumps);old.close();assertTrue(next.active());
    }}
    @Test void guardClosingAndReplacingOwnerCannotWriteThroughOldPhase() { try(var r=new Rig()) {
        var p=new Port();var oldRef=new AtomicReference<NativeDriveLease>();var replacement=new AtomicReference<NativeDriveLease>();var nextPort=new Port();
        var old=r.open(p,phase->phase.enqueue(input(1),()->{
            oldRef.get().close();replacement.set(r.open(nextPort,next->next.jump()));
            assertThrows(IllegalStateException.class,phase::jump);return true;
        }));oldRef.set(old);old.movementTick();
        assertFalse(old.active());assertTrue(p.inputs.isEmpty());assertEquals(0,p.jumps);
        replacement.get().movementTick();assertEquals(1,nextPort.jumps);old.close();assertTrue(replacement.get().active());
    }}
    @Test void retainedPhaseNeverReactivatesOnNextTickOrReplacement() { try(var r=new Rig()) {
        var p=new Port();var retained=new AtomicReference<NativeDriveProgram.Phase>();
        var old=r.open(p,phase->{if(retained.get()!=null)assertThrows(IllegalStateException.class,()->retained.get().jump());retained.set(phase);phase.jump();});
        old.movementTick();assertFalse(retained.get().active());assertThrows(IllegalStateException.class,()->retained.get().jump());
        p.tick++;old.movementTick();old.close();var next=r.open(new Port(),phase->phase.jump());
        assertThrows(IllegalStateException.class,()->retained.get().enqueue(input(3),()->true));assertTrue(next.active());
    }}
    @Test void exceptionAfterImmediateWriteDoesNotPretendRollbackAndDoesNotDrainRemainder() { try(var r=new Rig()) {
        var p=new Port();var lease=r.open(p,phase->{phase.move(input(1));phase.enqueue(input(2),()->true);throw new IllegalStateException("skill");});
        lease.movementTick();assertFalse(lease.active());assertEquals(List.of(input(1)),p.inputs);assertEquals(0,lease.executed());assertEquals(1,p.restores);
    }}
    @Test void closingInsideSourceDiscardsPendingInputAndDirection() { try(var r=new Rig()) {
        var p=new Port();var ref=new AtomicReference<NativeDriveLease>();
        var lease=r.open(p,phase->{phase.enqueue(input(1),()->true);phase.direction(90,0);ref.get().close();assertThrows(IllegalStateException.class,phase::jump);});ref.set(lease);
        lease.movementTick();lease.directionTick();assertTrue(p.inputs.isEmpty());assertEquals(0,p.looks);assertEquals(1,p.restores);
    }}
    @Test void foreignControllerAndChangedWorldFenceEveryOperation() { try(var r=new Rig()) {
        var p=new Port();var lease=r.open(p,phase->{p.owned=false;phase.move(input(1));});lease.movementTick();
        assertFalse(lease.active());assertTrue(p.inputs.isEmpty());
        var q=new Port();var next=r.open(q,phase->{var world=mock(World.class);when(world.getUID()).thenReturn(UUID.randomUUID());when(r.entity.getWorld()).thenReturn(world);phase.jump();});
        next.movementTick();assertFalse(next.active());assertEquals(0,q.jumps);
    }}
    @Test void pluginDisableEventClosesProgramWithoutAnotherNativeTick() { try(var r=new Rig()) {
        var p=new Port();var lease=r.open(p,phase->phase.jump());when(r.plugin.isEnabled()).thenReturn(false);
        lease.disabled(new PluginDisableEvent(r.plugin));assertFalse(lease.active());assertEquals(1,p.restores);assertEquals(0,p.jumps);
    }}
    @Test void frameAndProgramApisShareOwnerAndCapacityRatherThanCreatingSeparateRegistries() { try(var r=new Rig()) {
        var p=new Port();var program=r.open(p,phase->phase.jump());var rejected=new Port();
        assertThrows(IllegalStateException.class,()->NativeDriveLease.open(r.plugin,r.entity,rejected,c->null,r.failures::add));assertEquals(0,rejected.installs);
        program.close();var frame=NativeDriveLease.open(r.plugin,r.entity,rejected,c->null,r.failures::add);r.leases.add(frame);
        assertThrows(IllegalStateException.class,()->r.open(new Port(),phase->phase.jump()));assertTrue(frame.active());
    }}
    @Test void invalidNumbersRejectBeforeNativeWritesAndDoNotPoisonOperationGuard() { try(var r=new Rig()) {
        var p=new Port();var lease=r.open(p,phase->{
            for(double bad:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.MAX_VALUE})
                assertThrows(IllegalArgumentException.class,()->phase.velocity(0,bad,0));
            assertThrows(IllegalArgumentException.class,()->phase.direction(Float.NaN,0));
            assertThrows(IllegalArgumentException.class,()->phase.direction(361,0));
            assertThrows(IllegalArgumentException.class,()->phase.direction(0,91));phase.jump();
        });lease.movementTick();assertTrue(lease.active());assertEquals(1,p.jumps);assertEquals(0,p.velocityWrites);
    }}
    @Test void invalidLiveSampleAndNativeTickChangeFailClosed() { try(var r=new Rig()) {
        var p=new Port();p.vy=Double.NaN;var first=r.open(p,phase->phase.sample());first.movementTick();assertFalse(first.active());
        var q=new Port();var next=r.open(q,phase->{q.tick++;phase.jump();});next.movementTick();assertFalse(next.active());assertEquals(0,q.jumps);
    }}
    @Test void optionalCapabilityDoesNotPretendOlderProviderSupportsPrograms() {
        NativeMovementNMS old=new NativeMovementNMS(){public void requestJump(Entity entity){}public void resetFallDistance(Entity entity){}};
        assertFalse(old.supportsDrivePrograms());assertThrows(UnsupportedOperationException.class,()->old.controlDriveProgram(null,null,phase->{},error->{}));
    }

    private static Input input(float value){return new Input(0,0,value,1,false);}
    private static final class Port implements NativeDriveLease.ProgramPort {
        long tick;boolean owned,ground=true;float speed=.3f;double vx,vy,vz;
        int installs,restores,looks,defaultLooks,defaults,jumps,noFalls,samples,velocityWrites;
        final List<Input> inputs=new ArrayList<>();final List<String> calls=new ArrayList<>();
        public void install(NativeDriveLease lease){owned=true;installs++;}
        public boolean owned(){return owned;}
        public DriveContext context(){return new DriveContext(tick,ground,false);}
        public void move(DriveFrame frame){throw new AssertionError("Frame path called for a program");}
        public void look(DriveFrame frame){looks++;calls.add("look:"+frame.yaw());}
        public void neutral(){}
        public void restore(){owned=false;restores++;}
        public NativeDriveProgram.Sample sample(DriveContext contact){samples++;return new NativeDriveProgram.Sample(contact.nativeTick(),contact.onGround(),contact.inWater(),speed,vx,vy,vz);}
        public void defaultMovement(){defaults++;}
        public void clearHorizontalInput(){calls.add("clear");}
        public void groundedTravel(boolean value){ground=value;calls.add("ground");}
        public void suppressWaterJump(){calls.add("water");}
        public void input(Input value){inputs.add(value);calls.add("input:"+value.front());}
        public void jump(){jumps++;calls.add("jump");}
        public void resetFallDistance(){noFalls++;}
        public void velocity(double x,double y,double z){velocityWrites++;vx=x;vy=y;vz=z;}
        public void defaultDirection(){defaultLooks++;}
    }
    private static final class Rig implements AutoCloseable {
        final MockedStatic<Bukkit> bukkit=mockStatic(Bukkit.class);final MockedStatic<PaperCompat> paper=mockStatic(PaperCompat.class);
        final Plugin plugin=mock(Plugin.class);final PluginManager manager=mock(PluginManager.class);final World world=mock(World.class);final Entity entity=mock(Entity.class);
        final List<NativeDriveLease> leases=new ArrayList<>();final List<Throwable> failures=new ArrayList<>();final List<Listener> listeners=new ArrayList<>();
        Rig(){
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);bukkit.when(Bukkit::getPluginManager).thenReturn(manager);
            when(plugin.isEnabled()).thenReturn(true);when(world.getUID()).thenReturn(UUID.randomUUID());
            when(entity.getUniqueId()).thenReturn(UUID.randomUUID());when(entity.getEntityId()).thenReturn(1);when(entity.getWorld()).thenReturn(world);when(entity.isValid()).thenReturn(true);
            doAnswer(call->{listeners.add(call.getArgument(0));return null;}).when(manager).registerEvents(any(),eq(plugin));
        }
        NativeDriveLease open(Port port,NativeDriveProgram program){var lease=NativeDriveLease.openProgram(plugin,entity,port,program,failures::add);leases.add(lease);return lease;}
        public void close(){try{for(var lease:leases)lease.close();}finally{paper.close();bukkit.close();}}
    }
}
