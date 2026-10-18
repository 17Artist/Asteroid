package priv.seventeen.artist.asteroid.nms.v1_21_1;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.phys.EntityHitResult;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.entity.CraftArrow;
import org.bukkit.craftbukkit.entity.CraftItem;
import org.bukkit.craftbukkit.entity.CraftLivingEntity;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.mockito.MockedStatic;
import priv.seventeen.artist.asteroid.entity.NativeEntityHitboxNMS;
import java.lang.invoke.VarHandle;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real adapter/native metadata calls in the existing exact mapped fixture. Native attack/impact
 * is an observed port, not a live server or proof of Bukkit event/damage plugin behavior. */
@ResourceLock("bukkit-server")
class NativeEntityReplayTest {
    @BeforeAll static void registries(){SharedConstants.tryDetectVersion();Bootstrap.bootStrap();}
    @Test void nonLivingNativeAttackReceivesTheOriginalItemEntityHandle(){probe("attackEntity");}
    @Test void oldLivingAttackDescriptorDelegatesToTheSameEntityImplementation(){probe("livingAttack");}
    @Test void nonLivingProjectileImpactCarriesTheRealEntityHitResultAndOwnerId(){probe("hitEntity");}
    @Test void cancelledNativeImpactIsOneOriginalOwnerAttemptAndRestoresPreviousCancellation(){probe("cancelled");}
    @Test void nativeRuntimeAndErrorBothRestoreCancellationAndRememberTheCapturedOwner(){probe("exceptionFinally");}
    @Test void oldLivingHitDescriptorDelegatesWithoutASecondImpactAlgorithm(){probe("livingHit");}
    @Test void invalidWorldAndNonFinitePointAreRejectedBeforeNativeAttackOrImpact(){probe("invalidContext");}
    @Test void nativeCallbackCannotReplaceTheOriginalPiercingIdWithALaterBukkitId(){probe("capturedOwnerId");}
    @Test void aThrowingTargetIdGetterCannotEnterReplayAndLeaveCancellationModified(){probe("idGetterBeforeBegin");}
    @Test void optionalInterfaceAndOldPublicDescriptorsRemainAvailable()throws Exception{
        assertNotNull(NativeEntityHitboxNMS.class.getMethod("attackEntity",org.bukkit.entity.Player.class,org.bukkit.entity.Entity.class));
        assertNotNull(NativeEntityHitboxNMS.class.getMethod("hitEntity",org.bukkit.entity.Projectile.class,org.bukkit.entity.Entity.class,Vector.class));
        assertNotNull(EntityNMSImpl.class.getMethod("attack",org.bukkit.entity.Player.class,org.bukkit.entity.LivingEntity.class));
        assertNotNull(EntityNMSImpl.class.getMethod("hit",org.bukkit.entity.Projectile.class,org.bukkit.entity.LivingEntity.class,Vector.class));
    }
    private static void probe(String name){
        try{
            // Reuse the already-audited THREE literal translations. Do not drop clinit or rewrite
            // attack/hit/PrecisionPiercing; original mapped-test environment caveat remains.
            var fixture=NativePrecisionContextTest.class.getDeclaredMethod("fixture");fixture.setAccessible(true);
            var loader=(ClassLoader)fixture.invoke(null);var type=Class.forName(NativeEntityReplayTest.class.getName()+"$Probes",true,loader);
            var method=type.getDeclaredMethod(name);method.setAccessible(true);method.invoke(null);
        }catch(InvocationTargetException wrapped){var cause=wrapped.getCause();if(cause instanceof Error error)throw error;if(cause instanceof RuntimeException error)throw error;throw new AssertionError(cause);}
        catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
    }
    public static final class Probes {
        private static final class Rig implements AutoCloseable {
            final MockedStatic<Bukkit> thread=mockStatic(Bukkit.class);
            final World world=mock(World.class);final CraftPlayer player=mock(CraftPlayer.class);
            final ServerPlayer nativePlayer=mock(ServerPlayer.class);final CraftItem target=mock(CraftItem.class);
            final ItemEntity nativeTarget=mock(ItemEntity.class);final CraftArrow projectile=mock(CraftArrow.class);
            final Arrow nativeArrow=mock(Arrow.class);final EntityNMSImpl adapter=mock(EntityNMSImpl.class,CALLS_REAL_METHODS);
            final Vector point=new Vector(1.25,2.5,3.75);final AtomicInteger id=new AtomicInteger(2100123);
            Rig(){
                thread.when(Bukkit::isPrimaryThread).thenReturn(true);
                when(player.getWorld()).thenReturn(world);when(player.getHandle()).thenReturn(nativePlayer);
                when(target.getWorld()).thenReturn(world);when(target.getHandle()).thenReturn(nativeTarget);
                when(target.getEntityId()).thenAnswer(i->id.get());when(nativeTarget.getId()).thenReturn(id.get());
                when(projectile.getWorld()).thenReturn(world);when(projectile.getHandle()).thenReturn(nativeArrow);
                when(nativeArrow.getBukkitEntity()).thenReturn(projectile);
            }
            void cancelled(boolean value)throws ReflectiveOperationException{cancellation().set(nativeArrow,value);}
            boolean cancelled()throws ReflectiveOperationException{return (boolean)cancellation().get(nativeArrow);}
            static VarHandle cancellation()throws ReflectiveOperationException{var field=PrecisionPiercing.class.getDeclaredField("HIT_CANCELLED");field.setAccessible(true);return (VarHandle)field.get(null);}
            IntOpenHashSet ids()throws ReflectiveOperationException{var field=PrecisionPiercing.class.getDeclaredField("IDS");field.setAccessible(true);return (IntOpenHashSet)((VarHandle)field.get(null)).get(nativeArrow);}
            void impact(Runnable callback){doAnswer(i->{
                var hit=i.getArgument(0,EntityHitResult.class);assertSame(nativeTarget,hit.getEntity());assertFalse(hit.getEntity() instanceof LivingEntity);
                assertEquals(1.25,hit.getLocation().x);assertEquals(2.5,hit.getLocation().y);assertEquals(3.75,hit.getLocation().z);callback.run();return null;
            }).when(nativeArrow).preHitTargetOrDeflectSelf(any(EntityHitResult.class));}
            public void close(){thread.close();}
        }
        public static void attackEntity(){try(var r=new Rig()){
            r.adapter.attackEntity(r.player,r.target);verify(r.nativePlayer,times(1)).attack(r.nativeTarget);assertFalse(LivingEntity.class.isInstance(r.nativeTarget));assertEquals(2100123,r.nativeTarget.getId());
        }}
        public static void livingAttack(){try(var r=new Rig()){
            var target=mock(CraftLivingEntity.class);var handle=mock(LivingEntity.class);when(target.getWorld()).thenReturn(r.world);when(target.getHandle()).thenReturn(handle);
            r.adapter.attack(r.player,target);verify(r.adapter,times(1)).attackEntity(r.player,target);verify(r.nativePlayer,times(1)).attack(handle);
        }}
        public static void hitEntity()throws Exception{try(var r=new Rig()){
            r.cancelled(false);r.impact(()->{});r.adapter.hitEntity(r.projectile,r.target,r.point);
            verify(r.nativeArrow,times(1)).preHitTargetOrDeflectSelf(any(EntityHitResult.class));assertTrue(r.ids().contains(2100123));assertEquals(1,r.ids().size());assertFalse(r.cancelled());
        }}
        public static void cancelled()throws Exception{for(boolean previous:new boolean[]{false,true})try(var r=new Rig();var events=mockStatic(CraftEventFactory.class)){
            r.cancelled(previous);var event=mock(ProjectileHitEvent.class);when(event.isCancelled()).thenReturn(true);
            events.when(()->CraftEventFactory.callProjectileHitEvent(eq(r.nativeArrow),any(EntityHitResult.class))).thenAnswer(i->{
                var hit=i.getArgument(1,EntityHitResult.class);assertSame(r.nativeTarget,hit.getEntity());assertFalse(r.cancelled());return event;
            });
            // Execute Paper's ACTUAL preHit method: the event factory is the controlled port,
            // its cancellation sets the native flag and skips the non-cancelled hurt branch.
            doCallRealMethod().when(r.nativeArrow).preHitTargetOrDeflectSelf(any(EntityHitResult.class));
            r.adapter.hitEntity(r.projectile,r.target,r.point);assertEquals(previous,r.cancelled());assertTrue(r.ids().contains(2100123));assertEquals(1,r.ids().size());
            events.verify(()->CraftEventFactory.callProjectileHitEvent(eq(r.nativeArrow),any(EntityHitResult.class)),times(1));
        }}
        public static void exceptionFinally()throws Exception{for(boolean fatal:new boolean[]{false,true})try(var r=new Rig()){
            r.cancelled(true);r.impact(()->{if(fatal)throw new AssertionError("native impact");throw new IllegalStateException("native impact");});
            if(fatal)assertThrows(AssertionError.class,()->r.adapter.hitEntity(r.projectile,r.target,r.point));else assertThrows(IllegalStateException.class,()->r.adapter.hitEntity(r.projectile,r.target,r.point));
            assertTrue(r.cancelled());assertTrue(r.ids().contains(2100123));assertEquals(1,r.ids().size());
        }}
        public static void livingHit()throws Exception{try(var r=new Rig()){
            var target=mock(CraftLivingEntity.class);var handle=mock(LivingEntity.class);when(target.getWorld()).thenReturn(r.world);when(target.getHandle()).thenReturn(handle);when(target.getEntityId()).thenReturn(3100123);
            doAnswer(i->{assertSame(handle,i.getArgument(0,EntityHitResult.class).getEntity());return null;}).when(r.nativeArrow).preHitTargetOrDeflectSelf(any(EntityHitResult.class));
            r.adapter.hit(r.projectile,target,r.point);verify(r.adapter,times(1)).hitEntity(r.projectile,target,r.point);verify(r.nativeArrow,times(1)).preHitTargetOrDeflectSelf(any(EntityHitResult.class));assertTrue(r.ids().contains(3100123));
        }}
        public static void invalidContext(){try(var r=new Rig()){
            var other=mock(World.class);when(r.target.getWorld()).thenReturn(other);
            assertThrows(IllegalStateException.class,()->r.adapter.attackEntity(r.player,r.target));assertThrows(IllegalStateException.class,()->r.adapter.hitEntity(r.projectile,r.target,r.point));
            when(r.target.getWorld()).thenReturn(r.world);assertThrows(IllegalArgumentException.class,()->r.adapter.hitEntity(r.projectile,r.target,new Vector(Double.NaN,0,0)));
            verify(r.nativePlayer,never()).attack(any());verify(r.nativeArrow,never()).preHitTargetOrDeflectSelf(any());
        }}
        public static void capturedOwnerId()throws Exception{try(var r=new Rig()){
            r.impact(()->r.id.set(4100123));r.adapter.hitEntity(r.projectile,r.target,r.point);assertTrue(r.ids().contains(2100123));assertFalse(r.ids().contains(4100123));assertEquals(1,r.ids().size());
        }}
        public static void idGetterBeforeBegin()throws Exception{try(var r=new Rig()){
            r.cancelled(true);when(r.target.getEntityId()).thenThrow(new IllegalStateException("native ID read"));
            assertThrows(IllegalStateException.class,()->r.adapter.hitEntity(r.projectile,r.target,r.point));assertTrue(r.cancelled());assertNull(r.ids());verify(r.nativeArrow,never()).preHitTargetOrDeflectSelf(any());
        }}
    }
}
