package priv.seventeen.artist.asteroid.nms.v1_20_4;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import org.bukkit.Bukkit;
import priv.seventeen.artist.asteroid.entity.NativePoseNMS;
import priv.seventeen.artist.asteroid.internal.movement.NativeMotionCleanup;

import java.lang.reflect.Field;
import java.util.Objects;
import java.util.function.Consumer;

/** Version-local native access; the generic consumer never needs NMS classes. */
final class EntityPoseBridge {
    private EntityPoseBridge(){}
    static void check(){if(!Bukkit.isPrimaryThread())throw new IllegalStateException("Native pose requires the entity thread");}
    static void read(Entity e,NativePoseNMS.Pose out){
        out.yaw=e.getYRot();out.pitch=e.getXRot();out.alive=e.isAlive();
        out.headYaw=e instanceof LivingEntity living?living.yHeadRot:out.yaw;
        out.bodyYaw=e instanceof LivingEntity living?living.yBodyRot:out.yaw;
        out.maxHeadAngle=e instanceof Mob mob?mob.getMaxHeadYRot():75;
        // Native Mob controllers use xo/zo; MEG's non-Mob default controller uses xOld/zOld after its first tick.
        double dx=e.getX()-(e instanceof Mob?e.xo:e.xOld),dz=e.getZ()-(e instanceof Mob?e.zo:e.zOld);
        out.walking=(e instanceof Mob||e.tickCount>0)&&dx*dx+dz*dz>(double)2.5000003E-7f;
        out.mobPassenger=e.getFirstPassenger() instanceof Mob;
        if(!(e instanceof Mob)&&!out.mobPassenger)for(Entity passenger:e.getPassengers())if(passenger instanceof Mob){out.mobPassenger=true;break;}
    }
    private static Field field(){
        try{
            Field selected=null;
            for(var field:Mob.class.getDeclaredFields())if(field.getType()==BodyRotationControl.class){
                if(selected!=null)throw new IllegalStateException("Ambiguous native body controller");selected=field;
            }
            if(selected==null)throw new IllegalStateException("Native body controller is unavailable");
            selected.setAccessible(true);return selected;
        }catch(RuntimeException ex){throw new IllegalStateException("Cannot acquire native body controller",ex);}
    }
    static final class Control extends BodyRotationControl implements NativePoseNMS.BodyLease {
        private final Mob mob;
        private final Field field;
        private final BodyRotationControl previous;
        private final Consumer<NativePoseNMS.Pose> controller;
        private final Consumer<Throwable> failure;
        private final NativePoseNMS.Pose pose=new NativePoseNMS.Pose();
        private boolean closed,released;
        Control(Mob mob,Consumer<NativePoseNMS.Pose> controller,Consumer<Throwable> failure){
            super(mob);check();this.mob=mob;this.controller=Objects.requireNonNull(controller);this.failure=Objects.requireNonNull(failure);
            field=field();previous=current();
            if(previous instanceof Control)throw new IllegalStateException("Body controller is already leased");
            replace(this);
        }
        @Override public void clientTick(){
            if(closed){if(!released&&current()==this)previous.clientTick();return;}
            try{
                // A saved wrapper may still be invoked after a foreign controller replaced the field.
                if(current()!=this){closed=true;released=true;return;}
                read(mob,pose);if(!pose.alive)return;
                controller.accept(pose);
                // The callback may close this lease and/or acquire another exact controller.
                if(closed||current()!=this)return;
                if(!Float.isFinite(pose.headYaw)||!Float.isFinite(pose.bodyYaw))throw new IllegalArgumentException("Non-finite native body result");
                mob.yHeadRot=pose.headYaw;mob.yBodyRot=pose.bodyYaw;
            }catch(VirtualMachineError fatal){throw fatal;}
            catch(Throwable ex){
                Throwable combined=ex;
                try{close();}catch(Throwable cleanup){combined=NativeMotionCleanup.combine(combined,cleanup);}
                try{var owner=current();if(owner==this||owner==previous)previous.clientTick();}catch(Throwable fallback){combined=NativeMotionCleanup.combine(combined,fallback);}
                try{failure.accept(combined);}catch(Throwable reporting){combined=NativeMotionCleanup.combine(combined,reporting);NativeMotionCleanup.rethrow(combined);}
                if(combined instanceof Error fatal)throw fatal;
            }
        }
        private BodyRotationControl current(){try{return (BodyRotationControl)field.get(mob);}catch(IllegalAccessException ex){throw new IllegalStateException(ex);}}
        private void replace(BodyRotationControl value){try{field.set(mob,value);}catch(IllegalAccessException ex){throw new IllegalStateException(ex);}}
        @Override public boolean active(){check();return !closed&&current()==this;}
        @Override public void close(){
            check();closed=true;if(released)return;
            // A failed read/restore retains this exact cleanup obligation for a later explicit close.
            if(current()==this)replace(previous);
            released=true; // Foreign control is preserved; this does not claim a vanilla controller was restored.
        }
    }
}
