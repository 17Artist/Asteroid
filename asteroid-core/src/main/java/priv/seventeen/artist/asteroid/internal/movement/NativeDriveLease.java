package priv.seventeen.artist.asteroid.internal.movement;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;
import priv.seventeen.artist.asteroid.entity.NativeMovementNMS;
import priv.seventeen.artist.asteroid.entity.NativeDriveProgram;
import priv.seventeen.artist.asteroid.util.PaperCompat;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Shared bounded lifecycle/receipt fence; all physics and controller fields remain version-local. */
public final class NativeDriveLease implements NativeMovementNMS.DriveLease, Listener {
    public interface Port {
        void install(NativeDriveLease lease);
        boolean owned();
        NativeMovementNMS.DriveContext context();
        void move(NativeMovementNMS.DriveFrame frame);
        void look(NativeMovementNMS.DriveFrame frame);
        void neutral();
        /** Restore each controller only if still ours. Never replace a foreign successor. */
        void restore();
    }
    /** Native fields stay in the version adapter; the shared lease owns lifetime and budgets. */
    public interface ProgramPort extends Port {
        NativeDriveProgram.Sample sample(NativeMovementNMS.DriveContext contact);
        void defaultMovement();
        void clearHorizontalInput();
        void groundedTravel(boolean value);
        void suppressWaterJump();
        void input(NativeMovementNMS.Input input);
        void jump();
        void resetFallDistance();
        void velocity(double x,double y,double z);
        void defaultDirection();
    }
    private static final int MAX_LEASES=256;
    private static final Map<UUID,NativeDriveLease> LIVE=new HashMap<>();
    private final Thread thread=Thread.currentThread();
    private final Plugin owner;
    private final Entity entity;
    private final UUID id,world;
    private final int nativeId;
    private final Port port;
    private Function<NativeMovementNMS.DriveContext,NativeMovementNMS.DriveFrame> source;
    private NativeDriveProgram program;
    private final boolean programMode;
    private final Consumer<Throwable> failure;
    private boolean closed,running,registered,released,restored,cleaning,admitting=true;
    private PluginDisableEvent lastDisableEvent;
    private long lastTick=-1,lookTick=-1,executed;
    private NativeMovementNMS.DriveFrame frame;
    private ProgramPhase phase;

    private NativeDriveLease(Plugin owner,Entity entity,Port port,
            Function<NativeMovementNMS.DriveContext,NativeMovementNMS.DriveFrame> source,NativeDriveProgram program,Consumer<Throwable> failure){
        this.owner=Objects.requireNonNull(owner);this.entity=Objects.requireNonNull(entity);
        this.port=Objects.requireNonNull(port);this.source=source;this.program=program;this.programMode=program!=null;this.failure=Objects.requireNonNull(failure);
        id=entity.getUniqueId();world=entity.getWorld().getUID();nativeId=entity.getEntityId();
    }
    public static NativeDriveLease open(Plugin owner,Entity entity,Port port,
            Function<NativeMovementNMS.DriveContext,NativeMovementNMS.DriveFrame> source,Consumer<Throwable> failure){
        checkThread();return install(new NativeDriveLease(owner,entity,port,Objects.requireNonNull(source),null,failure));
    }
    public static NativeDriveLease openProgram(Plugin owner,Entity entity,ProgramPort port,
            NativeDriveProgram program,Consumer<Throwable> failure){
        checkThread();return install(new NativeDriveLease(owner,entity,port,null,Objects.requireNonNull(program),failure));
    }
    public static NativeDriveLease openProgramRetained(Plugin owner,Entity entity,ProgramPort port,
            NativeDriveProgram program,Consumer<Throwable> failure,Consumer<NativeMovementNMS.DriveLease> retain){
        checkThread();return install(new NativeDriveLease(owner,entity,port,null,Objects.requireNonNull(program),failure),Objects.requireNonNull(retain));
    }
    private static NativeDriveLease install(NativeDriveLease lease){return install(lease,ignored->{});}
    private static NativeDriveLease install(NativeDriveLease lease,Consumer<NativeMovementNMS.DriveLease> retain){
        NativeMotionCleanup.requireAdmission(lease.owner);
        if(!lease.liveEntity())throw new IllegalStateException("Driving owner/host is unavailable");
        if(LIVE.size()>=MAX_LEASES||LIVE.containsKey(lease.id))throw new IllegalStateException("Native driving capacity/owner conflict");
        LIVE.put(lease.id,lease);
        Throwable rejected=null;
        try{
            retain.accept(lease);
            NativeMotionCleanup.requireAdmission(lease.owner);
            if(!lease.liveEntity()||lease.closed||LIVE.get(lease.id)!=lease)throw new IllegalStateException("Driving receipt was revoked before installation");
            lease.port.install(lease);
            if(lease.closed)throw new IllegalStateException("Driving owner closed during installation");
            lease.registered=true; // unregister even if registerEvents partially registers then throws
            Bukkit.getPluginManager().registerEvents(lease,lease.owner);
            NativeMotionCleanup.requireAdmission(lease.owner);
            if(!lease.installedCurrent()||lease.closed||LIVE.get(lease.id)!=lease)throw new IllegalStateException("Driving owner changed during installation");
        }catch(RuntimeException|Error error){rejected=error;}
        finally{lease.admitting=false;}
        if(rejected!=null){
            try{lease.close();}catch(RuntimeException|Error cleanup){rejected=NativeMotionCleanup.combine(rejected,cleanup);}
            NativeMotionCleanup.rethrow(rejected);
        }
        return lease;
    }
    private static void checkThread(){
        if(PaperCompat.isFolia())throw new UnsupportedOperationException("Native driving does not support Folia regions yet");
        if(!Bukkit.isPrimaryThread())throw new IllegalStateException("Native driving requires the entity owner thread");
    }
    private void ownerThread(){checkThread();if(Thread.currentThread()!=thread)throw new IllegalStateException("Native driving thread changed");}
    private boolean liveEntity(){return owner.isEnabled()&&entity.isValid()&&!entity.isDead()
            &&entity.getEntityId()==nativeId&&entity.getUniqueId().equals(id)&&entity.getWorld().getUID().equals(world);}
    private boolean installedCurrent(){return !closed&&LIVE.get(id)==this&&liveEntity()&&port.owned()&&!closed&&LIVE.get(id)==this;}
    private boolean valid(){return !admitting&&installedCurrent();}
    private boolean ensure(){
        if(closed)return false;
        if(valid())return true;
        fail(new IllegalStateException("Native driving owner, entity or controller was replaced"));return false;
    }
    /** Called by the version-local MoveControl, not a Bukkit timer. */
    public void movementTick(){
        ownerThread();if(!ensure())return;
        if(running)throw new IllegalStateException("Reentrant native driving callback");
        running=true;
        try{
            var context=Objects.requireNonNull(port.context());
            if(context.nativeTick()==lastTick)return;
            if(context.nativeTick()<lastTick)throw new IllegalStateException("Native driving tick moved backwards");
            lastTick=context.nativeTick();frame=null;
            if(programMode){
                var current=new ProgramPhase(context,(ProgramPort)port);phase=current;
                try{
                    program.tick(current);
                    if(!ensure())return;
                    current.drain();
                    if(ensure())executed++;
                }finally{current.end();if(phase==current)phase=null;}
                return;
            }
            var next=source.apply(context);
            if(!ensure())return;
            if(next!=null&&next.nativeTick()!=context.nativeTick())throw new IllegalArgumentException("Driving frame does not belong to the current native tick");
            if(next==null){port.neutral();return;}
            frame=next;port.move(next);
            if(ensure())executed++;
        }catch(VirtualMachineError fatal){throw fatal;}
        catch(Throwable error){fail(error);}
        finally{running=false;}
    }
    /** Direction runs in the paired native LookControl phase with the same single-use frame. */
    public void directionTick(){
        ownerThread();if(!ensure())return;
        if(running)throw new IllegalStateException("Reentrant native driving direction");
        running=true;
        try{
            var context=Objects.requireNonNull(port.context());
            if(context.nativeTick()==lookTick)return;
            lookTick=context.nativeTick();
            if(frame!=null&&frame.nativeTick()==context.nativeTick())port.look(frame);
            else if(programMode)((ProgramPort)port).defaultDirection();
        }catch(VirtualMachineError fatal){throw fatal;}
        catch(Throwable error){fail(error);}
        finally{running=false;}
    }

    /** One revocable object per native tick; no callback can reset its shared budget. */
    private final class ProgramPhase implements NativeDriveProgram.Phase {
        private record Queued(NativeMovementNMS.Input input,BooleanSupplier current){}
        final NativeMovementNMS.DriveContext contact;
        final ProgramPort target;
        final ArrayDeque<Queued> queue=new ArrayDeque<>();
        boolean ended,operation,movementUsed;
        int used;
        ProgramPhase(NativeMovementNMS.DriveContext contact,ProgramPort target){this.contact=contact;this.target=target;}
        @Override public long nativeTick(){ownerThread();return contact.nativeTick();}
        @Override public boolean active(){
            ownerThread();return !ended&&phase==this&&running&&valid()&&lastTick==contact.nativeTick()
                    &&target.context().nativeTick()==contact.nativeTick();
        }
        void requireCurrent(){if(!active())throw new IllegalStateException("Native driving phase is stale");}
        <T>T operate(Supplier<T> action){
            ownerThread();
            if(operation)throw new IllegalStateException("Reentrant native driving phase operation");
            requireCurrent();
            if(used>=MAX_OPERATIONS)throw new IllegalStateException("Native driving phase operation budget exceeded");
            used++;operation=true;
            try{T result=action.get();requireCurrent();return result;}
            finally{operation=false;}
        }
        void write(Runnable action){operate(()->{movementUsed=true;action.run();return null;});}
        @Override public NativeDriveProgram.Sample sample(){return operate(()->{
            var value=Objects.requireNonNull(target.sample(contact));
            if(value.nativeTick()!=contact.nativeTick())throw new IllegalStateException("Native sample changed tick");
            return value;
        });}
        @Override public void defaultMovement(){
            operate(()->{if(movementUsed)throw new IllegalStateException("Default movement must be first and single-use");movementUsed=true;target.defaultMovement();return null;});
        }
        @Override public void clearHorizontalInput(){write(target::clearHorizontalInput);}
        @Override public void groundedTravel(boolean value){write(()->target.groundedTravel(value));}
        @Override public void suppressWaterJump(){write(target::suppressWaterJump);}
        @Override public void move(NativeMovementNMS.Input value){Objects.requireNonNull(value);write(()->target.input(value));}
        @Override public void enqueue(NativeMovementNMS.Input value,BooleanSupplier current){
            Objects.requireNonNull(value);Objects.requireNonNull(current);
            operate(()->{
                if(queue.size()>=MAX_POST_INPUTS)throw new IllegalStateException("Native driving post queue is full");
                movementUsed=true;queue.addLast(new Queued(value,current));return null;
            });
        }
        @Override public void jump(){write(target::jump);}
        @Override public void resetFallDistance(){write(target::resetFallDistance);}
        @Override public void velocity(double x,double y,double z){
            if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)
                    ||!Float.isFinite((float)x)||!Float.isFinite((float)y)||!Float.isFinite((float)z))
                throw new IllegalArgumentException("Non-finite or unrepresentable native velocity");
            write(()->target.velocity(x,y,z));
        }
        @Override public void direction(float yaw,float pitch){
            var value=new NativeMovementNMS.DriveFrame(contact.nativeTick(),0,0,0,yaw,pitch,false,false,0,false,false,false);
            operate(()->{frame=value;return null;});
        }
        void drain(){
            while(!queue.isEmpty())operate(()->{
                var command=queue.removeFirst();boolean current=command.current.getAsBoolean();
                requireCurrent();if(current)target.input(command.input);return null;
            });
        }
        void end(){ended=true;queue.clear();}
    }
    public void fail(Throwable error){
        ownerThread();if(closed){if(error instanceof Error fatal)throw fatal;return;}
        Throwable report=error;
        try{close();}catch(RuntimeException|Error cleanup){report=NativeMotionCleanup.combine(report,cleanup);}
        boolean reportFailed=false;
        try{failure.accept(report);}catch(RuntimeException|Error callback){report=NativeMotionCleanup.combine(report,callback);reportFailed=true;}
        if(report instanceof Error||reportFailed)NativeMotionCleanup.rethrow(report);
    }
    @Override public boolean active(){ownerThread();return ensure();}
    @Override public long executed(){ownerThread();return executed;}
    @EventHandler(priority=EventPriority.LOWEST)
    public void disabled(PluginDisableEvent event){
        if(event.getPlugin()==owner&&lastDisableEvent!=event)NativeMotionCleanup.closeForDisable(event);
    }
    @Override public void close(){
        ownerThread();if(released)return;
        revokeForCleanup();
        if(admitting)throw new IllegalStateException("Native driving cleanup awaits the admission stack");
        if(cleaning)throw new IllegalStateException("Native driving cleanup is already in progress");
        cleaning=true;Throwable error=null;
        try{
            if(!restored)try{port.restore();restored=true;}catch(RuntimeException|Error failure){error=NativeMotionCleanup.combine(error,failure);}
            if(registered)try{HandlerList.unregisterAll(this);registered=false;}catch(RuntimeException|Error failure){error=NativeMotionCleanup.combine(error,failure);}
            if(restored&&!registered){released=true;LIVE.remove(id,this);}
        }finally{cleaning=false;}
        NativeMotionCleanup.rethrow(error);
    }
    public void revokeForCleanup(){
        ownerThread();if(closed)return;closed=true;frame=null;source=null;program=null;
        if(phase!=null){phase.end();phase=null;}
    }
    public static List<NativeDriveLease> ownedForCleanup(Plugin owner){return ownedForCleanup(owner,null);}
    public static List<NativeDriveLease> ownedForCleanup(Plugin owner,PluginDisableEvent event){
        checkThread();Objects.requireNonNull(owner);
        if(event!=null&&event.getPlugin()!=owner)throw new IllegalArgumentException("Native driving disable owner changed");
        var result=new ArrayList<NativeDriveLease>();
        for(var lease:LIVE.values())if(lease.owner==owner&&(event==null||lease.lastDisableEvent!=event)){
            // Kept on the existing bounded receipt, including failed release; not an event history.
            if(event!=null)lease.lastDisableEvent=event;
            result.add(lease);
        }
        return List.copyOf(result);
    }
}
