package priv.seventeen.artist.asteroid.nms.v1_21_1;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.damage.CraftDamageSource;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.Plugin;
import priv.seventeen.artist.asteroid.AsteroidAPI;
import priv.seventeen.artist.asteroid.entity.IEntityNMS;
import priv.seventeen.artist.asteroid.entity.NativeArrowDamageEvidenceNMS;
import priv.seventeen.artist.asteroid.internal.damage.NativeDamageTagEpoch;

import java.lang.invoke.VarHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;

/** Fixed Paper original-BASE/source evidence only; no alternate native impact implementation. */
@SuppressWarnings("deprecation")
final class NativeArrowDamageSourceBridge {
    private NativeArrowDamageSourceBridge() {}
    private static final class Binding {
        static final VarHandle TAGS=bind();
        private static VarHandle bind(){
            VarHandle tags=NativeDamageSourceBridge.bindTags();
            // Original BASE belongs to the API JAR, not the already-pinned Craft/NMS JAR.
            try{
                Path jar=Path.of(EntityDamageEvent.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
                if(!Files.isRegularFile(jar)||Files.size(jar)>16L*1024*1024)
                    throw new UnsupportedOperationException("Native arrow originals implementation proof unavailable");
                MessageDigest digest=MessageDigest.getInstance("SHA-256");
                try(var in=Files.newInputStream(jar)){
                    byte[] buffer=new byte[65536];for(int n;(n=in.read(buffer))!=-1;)digest.update(buffer,0,n);
                }
                if(!"b8df3e7f2739e21072a5263e41b307bd30cfa8d8f72258ce27973167f8ad07c0"
                    .equals(HexFormat.of().formatHex(digest.digest())))
                    throw new UnsupportedOperationException("Unreviewed native arrow originals implementation");
                return tags;
            }catch(RuntimeException failure){throw failure;}
            catch(Exception failure){throw new UnsupportedOperationException("Cannot bind native arrow originals proof",failure);}
        }
    }
    static NativeArrowDamageEvidenceNMS.ArrowSources open(IEntityNMS provider,Plugin consumer,Plugin nativeOwner){
        Objects.requireNonNull(provider);Objects.requireNonNull(consumer);Objects.requireNonNull(nativeOwner);
        checkThread();
        if(!owned(provider,consumer,nativeOwner))throw new IllegalStateException("Native arrow evidence owner is not current");
        VarHandle tags=Binding.TAGS;
        var registry=((CraftServer)Bukkit.getServer()).getServer().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
        return new Frozen(provider,consumer,nativeOwner,tags,registry.getHolderOrThrow(DamageTypes.ARROW));
    }
    private static boolean owned(IEntityNMS provider,Plugin consumer,Plugin nativeOwner){
        return consumer.isEnabled()&&nativeOwner.isEnabled()
            &&Bukkit.getPluginManager().getPlugin(consumer.getName())==consumer
            &&Bukkit.getPluginManager().getPlugin(nativeOwner.getName())==nativeOwner
            &&AsteroidAPI.getEntityNMS()==provider;
    }
    private static void checkThread(){
        if(!Bukkit.isPrimaryThread())throw new IllegalStateException("Native arrow evidence requires the primary thread");
    }
    /** Package-visible numeric contract permits an independent unit test without bootstrapping Paper. */
    static float originalRaw(double value){
        if(!Double.isFinite(value)||value<0||value>1_000_000||Double.compare(value,(double)(float)value)!=0)
            throw new UnsupportedOperationException("Unsupported native arrow original BASE");
        return (float)value;
    }
    private static final class Frozen implements NativeArrowDamageEvidenceNMS.ArrowSources {
        private final Thread thread=Thread.currentThread();
        private final IEntityNMS provider;private final Plugin consumer,nativeOwner;private final VarHandle tags;
        private final Holder.Reference<DamageType> holder;private final NativeDamageTagEpoch epoch;private final boolean bypass;
        Frozen(IEntityNMS provider,Plugin consumer,Plugin nativeOwner,VarHandle tags,Holder.Reference<DamageType> holder){
            this.provider=provider;this.consumer=consumer;this.nativeOwner=nativeOwner;this.tags=tags;this.holder=holder;
            // Two actual identities: the holder's current tag set and the exact arrow holder itself.
            epoch=new NativeDamageTagEpoch(tags.get(holder),holder);bypass=holder.is(DamageTypeTags.BYPASSES_COOLDOWN);
            if(!current())throw new IllegalStateException("Native arrow tag epoch changed while binding");
        }
        private void check(){checkThread();if(Thread.currentThread()!=thread)throw new IllegalStateException("Wrong native arrow owner thread");}
        @Override public boolean current(){check();return epoch.current(owned(provider,consumer,nativeOwner),tags.get(holder),holder);}
        @Override public void close(){check();epoch.close();}
        @Override public NativeArrowDamageEvidenceNMS.ArrowInvocation bind(Arrow arrow,LivingEntity target){
            check();
            if(!current()||!(arrow instanceof CraftEntity direct)||!(target instanceof CraftEntity recipient)
                ||!arrow.isValid()||arrow.isDead()||!target.isValid()||target.isDead()||arrow.getWorld()!=target.getWorld()
                ||direct.getHandle().getClass()!=net.minecraft.world.entity.projectile.Arrow.class)
                throw new UnsupportedOperationException("Unsupported native arrow invocation");
            var handle=(net.minecraft.world.entity.projectile.Arrow)direct.getHandle();
            var shooter=handle.getOwner();
            if(!(shooter instanceof net.minecraft.world.entity.LivingEntity)||!(arrow.getShooter() instanceof CraftEntity causing)
                ||causing.getHandle()!=shooter||shooter.level()!=handle.level())
                throw new UnsupportedOperationException("Native arrow requires the exact living shooter");
            return new Invocation(arrow,target,handle,recipient.getHandle(),shooter);
        }
        private final class Invocation implements NativeArrowDamageEvidenceNMS.ArrowInvocation {
            private final Arrow arrow;private final LivingEntity target;
            private final net.minecraft.world.entity.projectile.Arrow direct;
            private final Entity recipient,shooter;
            private final Level world;
            private EntityDamageByEntityEvent primary;private boolean lost;
            Invocation(Arrow arrow,LivingEntity target,net.minecraft.world.entity.projectile.Arrow direct,
                       Entity recipient,Entity shooter){
                this.arrow=arrow;this.target=target;this.direct=direct;this.recipient=recipient;this.shooter=shooter;world=direct.level();
            }
            @Override public boolean current(){
                check();
                if(!Frozen.this.current()||direct.getOwner()!=shooter||direct.level()!=world||recipient.level()!=world
                    ||shooter.level()!=world||((CraftEntity)arrow).getHandle()!=direct||((CraftEntity)target).getHandle()!=recipient)lost=true;
                return !lost;
            }
            @Override public boolean bypassesCooldown(){if(!current())throw new UnsupportedOperationException("Stale native arrow evidence");return bypass;}
            @Override public boolean matches(EntityDamageByEntityEvent event){
                if(!current()||event==null||event.getEntity()!=target||event.getDamager()!=arrow
                    ||event.getCause()!=EntityDamageEvent.DamageCause.PROJECTILE||primary!=null&&primary!=event
                    ||!(event.getDamageSource() instanceof CraftDamageSource source)) {lost=true;return false;}
                var actual=source.getHandle();
                if(actual.typeHolder()!=holder||actual.getDirectEntity()!=direct||actual.getEntity()!=shooter){lost=true;return false;}
                return current();
            }
            @Override public float originalRaw(EntityDamageByEntityEvent event){
                if(!matches(event))throw new UnsupportedOperationException("Native arrow source/event mismatch");
                try{
                    float raw=NativeArrowDamageSourceBridge.originalRaw(event.getOriginalDamage(EntityDamageEvent.DamageModifier.BASE));
                    primary=event;return raw;
                }catch(RuntimeException failure){lost=true;throw failure;}
            }
            @Override public void close(){check();lost=true;}
        }
    }
}
