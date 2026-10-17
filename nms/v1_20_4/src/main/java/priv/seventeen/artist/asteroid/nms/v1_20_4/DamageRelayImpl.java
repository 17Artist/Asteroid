package priv.seventeen.artist.asteroid.nms.v1_20_4;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_20_R3.entity.CraftEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.util.BoundingBox;
import priv.seventeen.artist.asteroid.entity.NativeDamageRelay;

import java.util.*;
import java.util.function.Consumer;

/** Real server entity for explicit child-hitbox semantics, never a per-bone network entity. */
final class DamageRelayImpl extends CustomEntityImpl implements NativeDamageRelay {
    private final LivingEntity parent;
    private final Handler handler;
    private final Consumer<Throwable> failure;
    private BoundingBox bounds;
    private boolean registered,closed,dispatching;
    DamageRelayImpl(ServerLevel level,LivingEntity parent,BoundingBox bounds,Handler handler,Consumer<Throwable> failure){
        super(level,parent);this.parent=Objects.requireNonNull(parent);this.handler=Objects.requireNonNull(handler);this.failure=Objects.requireNonNull(failure);
        getBukkitEntity().setPersistent(false);setInvulnerable(false);update(bounds);
    }
    void registered(){registered=true;PrecisionProxyImpl.registerReplayAlias(getId(),parent);}
    private void check(){if(!Bukkit.isPrimaryThread())throw new IllegalStateException("Damage relay requires the entity thread");}
    @Override public void update(BoundingBox next){
        check();Objects.requireNonNull(next);if(closed)throw new IllegalStateException("Damage relay is closed");
        double[] coordinates={next.getMinX(),next.getMinY(),next.getMinZ(),next.getMaxX(),next.getMaxY(),next.getMaxZ()};
        for(double value:coordinates)if(!Double.isFinite(value))throw new IllegalArgumentException("Non-finite relay bounds");
        if(next.getWidthX()>64||next.getWidthZ()>64||next.getHeight()>64)throw new IllegalArgumentException("Damage relay span exceeds 64 blocks");
        if(next.equals(bounds))return;bounds=next.clone();setPos(next.getCenterX(),next.getCenterY(),next.getCenterZ());
    }
    private void install(){if(bounds!=null)setBoundingBox(new AABB(bounds.getMinX(),bounds.getMinY(),bounds.getMinZ(),bounds.getMaxX(),bounds.getMaxY(),bounds.getMaxZ()));}
    @Override public void setPos(double x,double y,double z){super.setPos(x,y,z);install();}
    @Override public void tick(){if(!valid())close();}
    @Override public boolean broadcastToPlayer(ServerPlayer player){return false;}
    @Override public boolean isPickable(){return false;}
    @Override public boolean canBeHitByProjectile(){return false;}
    @Override public boolean canCollideWith(Entity other){return false;}
    @Override public LivingEntity entity(){return getBukkitEntity();}
    @Override public boolean valid(){return registered&&!closed&&!isRemoved()&&parent.isValid()&&!parent.isDead()&&parent.getWorld()==getBukkitEntity().getWorld();}
    private void detach(){closed=true;registered=false;PrecisionProxyImpl.removeReplayAlias(getId());}
    @Override public void close(){check();detach();if(!isRemoved())remove();}
    @Override public void remove(){detach();super.remove();}
    @Override public void remove(Entity.RemovalReason reason){detach();super.remove(reason);}
    @Override public void remove(Entity.RemovalReason reason,EntityRemoveEvent.Cause cause){detach();super.remove(reason,cause);}
    @Override public boolean hurt(DamageSource source,float amount) {
        check();if(!valid()||dispatching||!Float.isFinite(amount)||amount<0||amount>1_000_000)return false;
        Frame frame=new Frame(source,amount);dispatching=true;
        try{return handler.hurt(frame);}
        catch(VirtualMachineError fatal){close();throw fatal;}
        catch(Throwable error){close();try{failure.accept(error);}catch(VirtualMachineError fatal){throw fatal;}catch(Throwable ignored){}return false;}
        finally{frame.active=false;dispatching=false;}
    }
    private final class Frame implements Damage {
        private final DamageSource source;private final float amount;private final Set<UUID> attempted=new HashSet<>();
        private boolean active=true;
        Frame(DamageSource source,float amount){this.source=source;this.amount=amount;}
        @Override public float amount(){return amount;}
        @Override public org.bukkit.entity.Entity attacker(){return source.getEntity()==null?null:source.getEntity().getBukkitEntity();}
        @Override public org.bukkit.entity.Entity directSource(){return source.getDirectEntity()==null?null:source.getDirectEntity().getBukkitEntity();}
        @Override public boolean forward(org.bukkit.entity.Entity target,float damage){
            check();if(!active||!dispatching||!valid())throw new IllegalStateException("Native damage scope has expired");
            Objects.requireNonNull(target);if(!Float.isFinite(damage)||damage<0||damage>1_000_000)throw new IllegalArgumentException("Invalid forwarded damage");
            if(target.getWorld()!=parent.getWorld())throw new IllegalArgumentException("Cross-world damage relay");
            var nativeTarget=((CraftEntity)target).getHandle();
            if(nativeTarget instanceof DamageRelayImpl)throw new IllegalArgumentException("Damage relay recursion is not permitted");
            if(attempted.contains(target.getUniqueId()))return false;
            if(attempted.size()>=65)throw new IllegalStateException("Damage forwarding target budget exceeded");
            attempted.add(target.getUniqueId());if(!target.isValid()||target.isDead())return false;
            return nativeTarget.hurt(source,damage);
        }
    }
}

