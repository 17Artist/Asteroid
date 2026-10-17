package priv.seventeen.artist.asteroid.nms.v1_20_4;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.phys.Vec3;
import org.bukkit.craftbukkit.v1_20_R3.entity.CraftEntity;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import priv.seventeen.artist.asteroid.entity.NativeDriveProgram;
import priv.seventeen.artist.asteroid.entity.NativeMovementNMS;
import priv.seventeen.artist.asteroid.internal.movement.NativeDriveFields;
import priv.seventeen.artist.asteroid.internal.movement.NativeDriveLease;
import priv.seventeen.artist.asteroid.internal.movement.NativeMotionRestore;

import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/** One owner-thread broker for queued travel input and animation displacement; no scheduler or packet. */
final class NativeMoveBridge {
    private NativeMoveBridge(){}
    private static Mob mob(Entity entity){
        EntityPoseBridge.check();
        if(!(entity instanceof CraftEntity craft)||!entity.isValid())throw new IllegalArgumentException("Movement host is not loaded");
        return craft.getHandle() instanceof Mob mob?mob:null;
    }
    static void move(Entity entity,NativeMovementNMS.Input input){Objects.requireNonNull(input);Mob mob=mob(entity);if(mob!=null){uncontrolled(mob);apply(mob,input);}}
    static void uncontrolled(Entity entity){Mob host=mob(entity);if(host!=null)uncontrolled(host);}
    private static void uncontrolled(Mob host){if(Access.get(host) instanceof Control control&&(control.queue!=null||control.root!=null||control.drive!=null))throw new IllegalStateException("Native movement already has an owner");}
    private static Control control(Entity entity){
        Mob host=mob(entity);if(host==null)throw new IllegalArgumentException("Post-tick movement requires a Mob");
        var previous=Access.get(host);return previous instanceof Control active&&!active.closed?active:new Control(host,previous);
    }
    static NativeMovementNMS.MovementLease control(Entity entity,Consumer<Throwable> failure){
        Objects.requireNonNull(failure);var control=control(entity);
        if(control.queue!=null||control.root!=null||control.drive!=null)throw new IllegalStateException("Movement controller is already owned");
        return control.queue=new QueueLease(control,failure);
    }
    static NativeMovementNMS.RootMotionLease root(Entity entity,Supplier<NativeMovementNMS.RootMotionFrame> source,Consumer<Throwable> failure){
        Objects.requireNonNull(source);Objects.requireNonNull(failure);var control=control(entity);
        if(control.root!=null||control.queue!=null||control.drive!=null)throw new IllegalStateException("Root motion controller is already owned");
        return control.root=new RootLease(control,source,failure);
    }
    static NativeMovementNMS.DriveLease drive(Plugin owner,Entity entity,
            Function<NativeMovementNMS.DriveContext,NativeMovementNMS.DriveFrame> source,Consumer<Throwable> failure){
        Objects.requireNonNull(owner);Objects.requireNonNull(source);Objects.requireNonNull(failure);
        var port=drivePort(entity);
        return NativeDriveLease.open(owner,entity,port,source,failure);
    }
    static NativeMovementNMS.DriveLease program(Plugin owner,Entity entity,NativeDriveProgram program,Consumer<Throwable> failure){
        Objects.requireNonNull(owner);Objects.requireNonNull(program);Objects.requireNonNull(failure);
        var port=drivePort(entity);
        return NativeDriveLease.openProgram(owner,entity,port,program,failure);
    }
    static NativeMovementNMS.DriveLease programRetained(Plugin owner,Entity entity,NativeDriveProgram program,Consumer<Throwable> failure,Consumer<NativeMovementNMS.DriveLease> retain){
        Objects.requireNonNull(owner);Objects.requireNonNull(program);Objects.requireNonNull(failure);Objects.requireNonNull(retain);
        return NativeDriveLease.openProgramRetained(owner,entity,drivePort(entity),program,failure,retain);
    }
    private static DrivePort drivePort(Entity entity){
        Mob host=mob(entity);if(host==null||host.isNoAi())throw new IllegalArgumentException("Native driving requires an AI-enabled Mob");
        var previous=Access.get(host);var previousLook=LookAccess.get(host);
        // Cold preparation never repairs a leftover wrapper before its exact receipt exists.
        if(previous instanceof Control)throw new IllegalStateException("Native movement controller has not been released");
        // Do not wrap another plugin's controller or disable AI to conceal a conflict.
        if(!vanilla(previous.getClass())||!vanilla(previousLook.getClass()))throw new IllegalStateException("Foreign native movement/look controller");
        DriveAccess.JUMP_DELAY.get(host); // Resolve exact mapped field before installing anything.
        var control=new Control(host,previous,false);
        return new DrivePort(control,previousLook);
    }
    private static boolean vanilla(Class<?> type){return type.getClassLoader()==Mob.class.getClassLoader()&&type.getName().startsWith("net.minecraft.");}
    private static void apply(Mob mob,NativeMovementNMS.Input input){
        float side=input.side(),up=input.up(),front=input.front();
        float speed=(float)mob.getAttributeValue(Attributes.MOVEMENT_SPEED)*input.speed();
        if(input.global()){var local=new Vec3(side,up,front).yRot(-mob.getYRot()*0.017453292F);side=(float)local.x;up=(float)local.y;front=(float)local.z;}
        if(!Float.isFinite(speed)||!Float.isFinite(side)||!Float.isFinite(up)||!Float.isFinite(front))throw new IllegalArgumentException("Non-finite native movement result");
        mob.setSpeed(speed);
        if(input.global()){mob.setXxa(side);mob.setYya(up);mob.setZza(front);}else{mob.setZza(front);mob.setYya(up);mob.setXxa(side);}
    }
    private static void applyRoot(Mob mob,NativeMovementNMS.RootMotionFrame frame){
        // Only movement INPUT is weighted. Existing horizontal knockback is not overwritten.
        float base=(float)frame.baseMultiplier();
        mob.setXxa(Float.isFinite(mob.xxa)?mob.xxa*base:0);mob.setYya(Float.isFinite(mob.yya)?mob.yya*base:0);mob.setZza(Float.isFinite(mob.zza)?mob.zza*base:0);
        if(frame.x()==0&&frame.y()==0&&frame.z()==0&&frame.onGround())return;
        mob.resetFallDistance();
        if(Math.abs((float)frame.y())>=9.999999747378752E-6F||!frame.onGround())mob.setDeltaMovement(mob.getDeltaMovement().multiply(1,0,1));
        // Collision, step-up and entity/world hooks remain native. Never teleport or store the root
        // displacement in deltaMovement (which would apply it again during ordinary travel).
        mob.move(MoverType.SELF,new Vec3(frame.x(),frame.y(),frame.z()));
    }
    private static final class Access {
        static final Field FIELD=find();
        private static Field find(){
            Field selected=null;for(Field candidate:Mob.class.getDeclaredFields())if(candidate.getType()==MoveControl.class){if(selected!=null)throw new IllegalStateException("Ambiguous native movement controller");selected=candidate;}
            if(selected==null)throw new IllegalStateException("Native movement controller is unavailable");selected.setAccessible(true);return selected;
        }
        static MoveControl get(Mob mob){try{return (MoveControl)FIELD.get(mob);}catch(IllegalAccessException error){throw new IllegalStateException(error);}}
        static void set(Mob mob,MoveControl value){try{FIELD.set(mob,value);}catch(IllegalAccessException error){throw new IllegalStateException(error);}}
    }
    private static final class Control extends MoveControl {
        final Mob host;final MoveControl previous;QueueLease queue;RootLease root;NativeDriveLease drive;boolean closed;
        Control(Mob host,MoveControl previous){this(host,previous,true);}
        Control(Mob host,MoveControl previous,boolean install){super(host);this.host=host;this.previous=previous;if(install)Access.set(host,this);}
        boolean active(){return !closed&&!host.isRemoved()&&Access.get(host)==this;}
        void release(){if(queue==null&&root==null&&drive==null){closed=true;if(Access.get(host)==this)Access.set(host,previous);}}
        @Override public void tick(){
            EntityPoseBridge.check();if(drive!=null){drive.movementTick();return;}
            if(!active()){if(queue!=null)queue.close();if(root!=null)root.close();return;}
            try{previous.tick();}catch(VirtualMachineError fatal){throw fatal;}catch(Throwable error){if(queue!=null)queue.fail(error);if(root!=null)root.fail(error);return;}
            if(!active()){if(queue!=null)queue.close();if(root!=null)root.close();return;}
            QueueLease queued=queue;if(queued!=null)queued.drain();
            RootLease motion=root;if(active()&&motion!=null)motion.tick();
            release();
        }
        @Override public boolean hasWanted(){return previous.hasWanted();}
        @Override public double getSpeedModifier(){return previous.getSpeedModifier();}
        @Override public void setWantedPosition(double x,double y,double z,double speed){if(drive!=null)drive.fail(new IllegalStateException("Navigation replaced native driving"));previous.setWantedPosition(x,y,z,speed);}
        @Override public void strafe(float front,float side){if(drive!=null)drive.fail(new IllegalStateException("Strafe replaced native driving"));previous.strafe(front,side);}
        @Override public double getWantedX(){return previous.getWantedX();}
        @Override public double getWantedY(){return previous.getWantedY();}
        @Override public double getWantedZ(){return previous.getWantedZ();}
        
    }
    private static final class LookAccess {
        static final Field FIELD=find();
        private static Field find(){
            Field selected=null;for(Field field:Mob.class.getDeclaredFields())if(field.getType()==LookControl.class){if(selected!=null)throw new IllegalStateException("Ambiguous native look controller");selected=field;}
            if(selected==null)throw new IllegalStateException("Native look controller is unavailable");selected.setAccessible(true);return selected;
        }
        static LookControl get(Mob host){try{return (LookControl)FIELD.get(host);}catch(IllegalAccessException error){throw new IllegalStateException(error);}}
        static void set(Mob host,LookControl value){try{FIELD.set(host,value);}catch(IllegalAccessException error){throw new IllegalStateException(error);}}
    }
    private static final class DriveAccess {
        static final VarHandle JUMP_DELAY=NativeDriveFields.integer(LivingEntity.class,"noJumpDelay","bZ");
    }
    private static final class DrivePort implements NativeDriveLease.ProgramPort {
        final Control control;final Mob host;final LookControl previous;DrivingLook look;NativeDriveLease lease;
        final NativeMotionRestore restoration=new NativeMotionRestore();
        DrivePort(Control control,LookControl previous){this.control=control;host=control.host;this.previous=previous;}
        public void install(NativeDriveLease value){
            if(Access.get(host)!=control.previous||LookAccess.get(host)!=previous)throw new IllegalStateException("Native controllers changed before driving installation");
            lease=value;look=new DrivingLook(host,this);control.drive=value;Access.set(host,control);LookAccess.set(host,look);
        }
        public boolean owned(){return control.active()&&control.drive==lease&&LookAccess.get(host)==look&&!host.isNoAi();}
        public NativeMovementNMS.DriveContext context(){return new NativeMovementNMS.DriveContext(Integer.toUnsignedLong(host.tickCount),host.onGround(),host.isInWater());}
        public NativeDriveProgram.Sample sample(NativeMovementNMS.DriveContext contact){
            var velocity=host.getDeltaMovement();
            return new NativeDriveProgram.Sample(contact.nativeTick(),contact.onGround(),contact.inWater(),
                    (float)host.getAttributeValue(Attributes.MOVEMENT_SPEED),velocity.x,velocity.y,velocity.z);
        }
        public void defaultMovement(){control.previous.tick();}
        public void clearHorizontalInput(){host.setZza(0);host.setXxa(0);}
        public void groundedTravel(boolean value){host.setOnGround(value);}
        public void suppressWaterJump(){if(host.isInWater())DriveAccess.JUMP_DELAY.set(host,1);}
        public void input(NativeMovementNMS.Input input){apply(host,input);}
        public void jump(){host.getJumpControl().jump();}
        public void resetFallDistance(){host.resetFallDistance();}
        public void velocity(double x,double y,double z){host.setDeltaMovement(x,y,z);}
        public void defaultDirection(){previous.tick();}
        public void move(NativeMovementNMS.DriveFrame frame){
            float speed=(float)host.getAttributeValue(Attributes.MOVEMENT_SPEED);
            if(!Float.isFinite(speed)||speed<0||speed>4)throw new IllegalArgumentException("Native driving speed exceeds the safety bound");
            var velocity=host.getDeltaMovement();
            if(!Double.isFinite(velocity.x)||!Double.isFinite(velocity.y)||!Double.isFinite(velocity.z))throw new IllegalArgumentException("Non-finite native driving velocity");
            if(frame.groundedTravel())host.setOnGround(true);
            if(frame.suppressWaterJump()&&host.isInWater())DriveAccess.JUMP_DELAY.set(host,1);
            if(frame.resetFall())host.resetFallDistance();
            if(frame.zeroVertical()||frame.verticalSpeedFactor()!=0)
                host.setDeltaMovement(velocity.x,(frame.zeroVertical()?0:velocity.y)+speed*frame.verticalSpeedFactor(),velocity.z);
            if(frame.jump())host.getJumpControl().jump();
            apply(host,new NativeMovementNMS.Input(frame.side(),0,frame.front(),frame.speed(),false));
        }
        public void look(NativeMovementNMS.DriveFrame frame){host.setYRot(frame.yaw());host.yHeadRot=frame.yaw();host.setXRot(frame.pitch());}
        public void neutral(){if(Access.get(host)==control){host.setXxa(0);host.setYya(0);host.setZza(0);host.setSpeed(0);}}
        public void restore(){
            restoration.restore(
                ()->{if(Access.get(host)==control&&LookAccess.get(host)==look)neutral();},
                ()->{if(look!=null&&LookAccess.get(host)==look)LookAccess.set(host,previous);},
                ()->{if(Access.get(host)==control)Access.set(host,control.previous);if(control.drive==lease)control.drive=null;control.closed=true;});
        }
    }
    private static final class DrivingLook extends LookControl {
        final DrivePort port;DrivingLook(Mob host,DrivePort port){super(host);this.port=port;}
        @Override public void tick(){port.lease.directionTick();}
        @Override public void setLookAt(Vec3 target){port.previous.setLookAt(target);}
        @Override public void setLookAt(net.minecraft.world.entity.Entity target){port.previous.setLookAt(target);}
        @Override public void setLookAt(net.minecraft.world.entity.Entity target,float yaw,float pitch){port.previous.setLookAt(target,yaw,pitch);}
        @Override public void setLookAt(double x,double y,double z){port.previous.setLookAt(x,y,z);}
        @Override public void setLookAt(double x,double y,double z,float yaw,float pitch){port.previous.setLookAt(x,y,z,yaw,pitch);}
        @Override public boolean isLookingAtTarget(){return port.previous.isLookingAtTarget();}
        @Override public double getWantedX(){return port.previous.getWantedX();}
        @Override public double getWantedY(){return port.previous.getWantedY();}
        @Override public double getWantedZ(){return port.previous.getWantedZ();}
    }
    private static void report(Consumer<Throwable> failure,Throwable error){try{failure.accept(error);}catch(VirtualMachineError fatal){throw fatal;}catch(Throwable ignored){}}
    private static final class QueueLease implements NativeMovementNMS.MovementLease {
        final Control owner;final Consumer<Throwable> failure;final ArrayDeque<NativeMovementNMS.Input> queue=new ArrayDeque<>();boolean closed;long executed;
        QueueLease(Control owner,Consumer<Throwable> failure){this.owner=owner;this.failure=failure;}
        @Override public void enqueue(NativeMovementNMS.Input input){EntityPoseBridge.check();Objects.requireNonNull(input);if(!active())throw new IllegalStateException("Movement controller is closed or replaced");if(queue.size()>=MAX_PENDING)throw new IllegalStateException("Movement post-tick queue is full");queue.addLast(input);}
        void drain(){try{while(active()&&!queue.isEmpty()){apply(owner.host,queue.removeFirst());executed++;}close();}catch(VirtualMachineError fatal){throw fatal;}catch(Throwable error){fail(error);}}
        void fail(Throwable error){close();report(failure,error);}
        @Override public boolean active(){EntityPoseBridge.check();return !closed&&owner.active()&&owner.queue==this;}
        @Override public int pending(){EntityPoseBridge.check();return queue.size();}
        @Override public long executed(){EntityPoseBridge.check();return executed;}
        @Override public void close(){EntityPoseBridge.check();if(closed)return;closed=true;queue.clear();if(owner.queue==this)owner.queue=null;owner.release();}
    }
    private static final class RootLease implements NativeMovementNMS.RootMotionLease {
        final Control owner;final Supplier<NativeMovementNMS.RootMotionFrame> source;final Consumer<Throwable> failure;boolean closed;long last=-1,executed;
        RootLease(Control owner,Supplier<NativeMovementNMS.RootMotionFrame> source,Consumer<Throwable> failure){this.owner=owner;this.source=source;this.failure=failure;}
        void tick(){try{
            var frame=source.get();if(!active()||frame==null||frame.sequence()<=last)return;
            last=frame.sequence();applyRoot(owner.host,frame);executed++;
        }catch(VirtualMachineError fatal){throw fatal;}catch(Throwable error){fail(error);}}
        void fail(Throwable error){close();report(failure,error);}
        @Override public boolean active(){EntityPoseBridge.check();return !closed&&owner.active()&&owner.root==this;}
        @Override public long executed(){EntityPoseBridge.check();return executed;}
        @Override public void close(){EntityPoseBridge.check();if(closed)return;closed=true;if(owner.root==this)owner.root=null;owner.release();}
    }
}
