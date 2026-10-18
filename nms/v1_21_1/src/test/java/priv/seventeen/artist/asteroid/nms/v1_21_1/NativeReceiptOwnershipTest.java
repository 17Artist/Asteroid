package priv.seventeen.artist.asteroid.nms.v1_21_1;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.entity.CraftLivingEntity;
import org.bukkit.entity.Entity;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.mockito.MockedStatic;
import priv.seventeen.artist.asteroid.entity.NativeReceiptOwnership;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Execute actual receipt methods under the existing mapped fixture. Its THREE existing field
 * literal translations are unchanged; no clinit/guard/method body is removed or reimplemented. */
@ResourceLock("bukkit-server")
class NativeReceiptOwnershipTest {
    @BeforeAll static void registries(){SharedConstants.tryDetectVersion();Bootstrap.bootStrap();}
    @Test void precisionLifeInvalidityDoesNotEraseStillRegisteredPhysicalOwnership(){probe("precisionDeath");}
    @Test void relayLifeInvalidityDoesNotEraseStillOpenPhysicalOwnership(){probe("relayDeath");}
    @Test void exactOwnerWorldRemovalAndOwnEntityValidityRemainMandatory(){probe("exactPhysical");}
    @Test void closeRevokesBothProofsBeforeThrowingPhysicalRemoval(){probe("failedRemoval");}
    @Test void anUnregisteredReceiptCannotProveOwnershipEvenWithAValidBukkitEntity(){probe("unregistered");}
    @Test void terminalOwnershipIsOwnerThreadOnlyWhileOrdinaryValidDescriptorsStayUnchanged(){probe("threadAndDescriptors");}
    private static void probe(String name){
        try{
            var fixture=NativePrecisionContextTest.class.getDeclaredMethod("fixture");fixture.setAccessible(true);
            var loader=(ClassLoader)fixture.invoke(null);var type=Class.forName(NativeReceiptOwnershipTest.class.getName()+"$Probes",true,loader);
            var method=type.getDeclaredMethod(name);method.setAccessible(true);method.invoke(null);
        }catch(InvocationTargetException wrapped){var cause=wrapped.getCause();if(cause instanceof Error error)throw error;if(cause instanceof RuntimeException error)throw error;throw new AssertionError(cause);}
        catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
    }
    public static final class Probes {
        private static final class Rig implements AutoCloseable {
            final MockedStatic<Bukkit> thread=mockStatic(Bukkit.class);
            final World world=mock(World.class);final Entity owner=mock(Entity.class);
            final AtomicBoolean alive=new AtomicBoolean(true),removed=new AtomicBoolean(false);
            final CraftLivingEntity precisionEntity=mock(CraftLivingEntity.class),relayEntity=mock(CraftLivingEntity.class);
            final PrecisionProxyImpl precision=mock(PrecisionProxyImpl.class);
            final DamageRelayImpl relay=mock(DamageRelayImpl.class);
            Rig(boolean register)throws Exception{
                thread.when(Bukkit::isPrimaryThread).thenReturn(true);
                when(owner.getEntityId()).thenReturn(721000);when(owner.getWorld()).thenReturn(world);
                when(owner.isValid()).thenAnswer(i->alive.get());when(owner.isDead()).thenAnswer(i->!alive.get());
                when(precision.getId()).thenReturn(721001);when(relay.getId()).thenReturn(721002);
                when(precision.getBukkitEntity()).thenReturn(precisionEntity);when(relay.getBukkitEntity()).thenReturn(relayEntity);
                when(precisionEntity.getWorld()).thenReturn(world);when(relayEntity.getWorld()).thenReturn(world);
                when(precisionEntity.isValid()).thenReturn(true);when(relayEntity.isValid()).thenReturn(true);
                when(precision.isRemoved()).thenAnswer(i->removed.get());when(relay.isRemoved()).thenAnswer(i->removed.get());
                var target=PrecisionProxyImpl.class.getDeclaredField("target");target.setAccessible(true);target.set(precision,owner);
                var parent=DamageRelayImpl.class.getDeclaredField("parent");parent.setAccessible(true);parent.set(relay,owner);
                doCallRealMethod().when(precision).registered();doCallRealMethod().when(relay).registered();
                doCallRealMethod().when(precision).close();doCallRealMethod().when(relay).close();
                doCallRealMethod().when(precision).valid();doCallRealMethod().when(relay).valid();
                doCallRealMethod().when(precision).retainedOwner(any());doCallRealMethod().when(relay).retainedOwner(any());
                if(register){precision.registered();relay.registered();}
            }
            public void close(){
                try{thread.when(Bukkit::isPrimaryThread).thenReturn(true);doNothing().when(precision).remove();doNothing().when(relay).remove();precision.close();relay.close();}
                finally{thread.close();}
            }
        }
        public static void precisionDeath()throws Exception{try(var r=new Rig(true)){
            assertTrue(r.precision.valid());assertTrue(r.precision.retainedOwner(r.owner));r.alive.set(false);
            assertFalse(r.precision.valid());assertTrue(r.precision.retainedOwner(r.owner));assertSame(r.owner,PrecisionProxyImpl.proxyOwner(721001));
            verify(r.owner,never()).setPersistent(anyBoolean());
        }}
        public static void relayDeath()throws Exception{try(var r=new Rig(true)){
            assertTrue(r.relay.valid());r.alive.set(false);assertFalse(r.relay.valid());assertTrue(r.relay.retainedOwner(r.owner));
            assertSame(r.owner,PrecisionProxyImpl.proxyOwner(721002));
        }}
        public static void exactPhysical()throws Exception{try(var r=new Rig(true)){
            r.alive.set(false);assertTrue(r.precision.retainedOwner(r.owner));assertTrue(r.relay.retainedOwner(r.owner));
            var alias=mock(Entity.class);when(alias.getEntityId()).thenReturn(721000);when(alias.getWorld()).thenReturn(r.world);
            assertFalse(r.precision.retainedOwner(alias));assertFalse(r.relay.retainedOwner(alias));
            when(r.owner.getWorld()).thenReturn(mock(World.class));assertFalse(r.precision.retainedOwner(r.owner));assertFalse(r.relay.retainedOwner(r.owner));
            when(r.owner.getWorld()).thenReturn(r.world);r.removed.set(true);
            assertFalse(r.precision.retainedOwner(r.owner));assertFalse(r.relay.retainedOwner(r.owner));r.removed.set(false);
            when(r.precisionEntity.isValid()).thenReturn(false);when(r.relayEntity.isValid()).thenReturn(false);
            assertFalse(r.precision.retainedOwner(r.owner));assertFalse(r.relay.retainedOwner(r.owner));
        }}
        public static void failedRemoval()throws Exception{try(var r=new Rig(true)){
            var failure=new IllegalStateException("physical remove failed");doThrow(failure).when(r.precision).remove();doThrow(failure).when(r.relay).remove();
            assertSame(failure,assertThrows(IllegalStateException.class,r.precision::close));assertSame(failure,assertThrows(IllegalStateException.class,r.relay::close));
            assertTrue(r.precisionEntity.isValid());assertTrue(r.relayEntity.isValid());assertFalse(r.removed.get());
            assertFalse(r.precision.retainedOwner(r.owner));assertFalse(r.relay.retainedOwner(r.owner));
            assertNull(PrecisionProxyImpl.proxyOwner(721001));assertNull(PrecisionProxyImpl.proxyOwner(721002));
        }}
        public static void unregistered()throws Exception{try(var r=new Rig(false)){
            assertTrue(r.precisionEntity.isValid());assertTrue(r.relayEntity.isValid());
            assertFalse(r.precision.retainedOwner(r.owner));assertFalse(r.relay.retainedOwner(r.owner));
            r.precision.registered();r.relay.registered();assertTrue(r.precision.retainedOwner(r.owner));assertTrue(r.relay.retainedOwner(r.owner));
        }}
        public static void threadAndDescriptors()throws Exception{try(var r=new Rig(true)){
            assertEquals(boolean.class,PrecisionProxyImpl.class.getMethod("valid").getReturnType());
            assertEquals(boolean.class,DamageRelayImpl.class.getMethod("valid").getReturnType());
            assertTrue(NativeReceiptOwnership.class.isInstance(r.precision));
            assertTrue(NativeReceiptOwnership.class.isInstance(r.relay));
            r.thread.when(Bukkit::isPrimaryThread).thenReturn(false);
            assertThrows(IllegalStateException.class,()->r.precision.retainedOwner(r.owner));assertThrows(IllegalStateException.class,()->r.relay.retainedOwner(r.owner));
        }}
    }
}
